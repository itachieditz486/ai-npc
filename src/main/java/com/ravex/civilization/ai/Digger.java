package com.ravex.civilization.ai;

import com.ravex.civilization.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * Shared "get there, digging if I have to" helper used by mining and by going home.
 * Real mechanics only: tool speed decides dig time, drops come from the block's loot table and go into the NPC's
 * inventory, tools lose durability, blocks without the correct tool drop nothing (like vanilla).
 */
public final class Digger {
    public enum Result { WORKING, BLOCKED }

    private final NpcEntity npc;
    private final Predicate<BlockState> counts;
    private final Runnable onCount;

    private BlockPos digPos;
    private int digTicks;
    private int digNeeded;
    private int stuck;
    private int moveTick;
    private Vec3 lastPos = Vec3.ZERO;

    public Digger(NpcEntity npc, Predicate<BlockState> counts, Runnable onCount) {
        this.npc = npc;
        this.counts = counts;
        this.onCount = onCount;
    }

    public boolean isDigging() { return digPos != null; }

    public void reset() {
        digPos = null;
        stuck = 0;
        npc.getNavigation().stop();
    }

    /** Begin breaking a block. False if it is unsafe (liquid nearby, bedrock, container) or no suitable tool exists. */
    public boolean startDig(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return true;
        if (!isSafe(level, pos, st)) return false;
        if (!npc.equipToolFor(st)) return false;
        ItemStack tool = npc.getMainHandItem();
        boolean correct = !st.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(st);
        float hardness = st.getDestroySpeed(level, pos);
        float speed = Math.max(1.0F, tool.getDestroySpeed(st));
        digPos = pos.immutable();
        digTicks = 0;
        digNeeded = Math.max(4, Math.round(hardness * (correct ? 30.0F : 100.0F) / speed));
        return true;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.getDestroySpeed(level, pos) < 0) return false; // bedrock etc.
        if (level.getBlockEntity(pos) != null) return false;  // never dig chests/furnaces/spawners
        if (!level.getFluidState(pos).isEmpty()) return false;
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) return false; // lava/water pocket behind it
        }
        return true;
    }

    /** Continue the current dig. */
    public void tick(ServerLevel level) {
        if (digPos == null) return;
        BlockState st = level.getBlockState(digPos);
        if (st.isAir()) { digPos = null; return; }
        Vec3 c = Vec3.atCenterOf(digPos);
        if (npc.getEyePosition().distanceTo(c) > 5.0) { digPos = null; return; }
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(c.x, c.y, c.z);
        digTicks++;
        if (digTicks % 6 == 0) npc.swing(InteractionHand.MAIN_HAND);
        if (digTicks < digNeeded) return;

        ItemStack tool = npc.getMainHandItem();
        boolean correct = !st.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(st);
        List<ItemStack> drops = correct
                ? Block.getDrops(st, level, digPos, level.getBlockEntity(digPos), npc, tool)
                : List.of();
        level.destroyBlock(digPos, false, npc);
        for (ItemStack d : drops) {
            ItemStack left = npc.inventory.addItem(d);
            if (!left.isEmpty()) npc.spawnAtLocation(left);
        }
        if (tool.isDamageableItem()) tool.hurtAndBreak(1, npc, EquipmentSlot.MAINHAND);
        if (counts.test(st)) onCount.run();
        digPos = null;
    }

    /** Walk toward goal if a path exists, otherwise tunnel toward it one block at a time. */
    public Result moveToward(ServerLevel level, BlockPos goal) {
        if (digPos != null) {
            tick(level);
            return Result.WORKING;
        }
        moveTick++;
        if (moveTick % 20 == 0) {
            stuck = npc.position().distanceToSqr(lastPos) < 0.5 ? stuck + 1 : 0;
            lastPos = npc.position();
        }
        PathNavigation nav = npc.getNavigation();
        if (stuck < 2) {
            if (!nav.isDone() && moveTick % 10 != 1) return Result.WORKING;
            Path p = nav.createPath(goal, 1);
            if (p != null && p.canReach()) {
                nav.moveTo(p, 1.0);
                return Result.WORKING;
            }
        }
        return tunnel(level, goal);
    }

    private Result tunnel(ServerLevel level, BlockPos goal) {
        BlockPos feet = npc.blockPosition();
        int dx = goal.getX() - feet.getX();
        int dz = goal.getZ() - feet.getZ();
        int dy = goal.getY() - feet.getY();
        int sx = 0, sz = 0;
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) sx = Integer.signum(dx);
        else if (dz != 0) sz = Integer.signum(dz);
        else sx = 1;
        BlockPos front = feet.offset(sx, 0, sz);

        BlockPos[] clear;
        BlockPos dest;
        if (dy <= -1) {                       // go down one stair step
            clear = new BlockPos[]{front.above(), front, front.below()};
            dest = front.below();
        } else if (dy >= 1) {                 // go up one stair step
            clear = new BlockPos[]{feet.above(2), front.above(), front.above(2)};
            dest = front.above();
        } else {                              // straight tunnel
            clear = new BlockPos[]{front, front.above()};
            dest = front;
        }
        for (BlockPos p : clear) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty()) {
                if (!startDig(level, p)) return Result.BLOCKED;
                return Result.WORKING;
            }
        }
        if (!level.getFluidState(dest).isEmpty() || !level.getFluidState(dest.below()).isEmpty()) return Result.BLOCKED;
        npc.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 1.0);
        stuck = 0;
        lastPos = npc.position();
        return Result.WORKING;
    }
}
