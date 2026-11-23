package com.ravex.civilization.ai;

import com.ravex.civilization.npc.Lang;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcMission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Real wood gathering: find a log, walk to it, "mine" it for a realistic number of ticks (based on tool speed),
 * break the block, and put the real drops into the NPC's inventory. Nothing is spawned from thin air.
 *
 * Known limits (milestone 1): only logs within reach height; no leaf clearing; no ladder/tower building.
 */
public class CollectWoodGoal extends Goal {
    private static final int SCAN_RADIUS = 14;

    private final NpcEntity npc;
    private BlockPos target;
    private final Set<BlockPos> blacklist = new HashSet<>();
    private int breakTicks;
    private int breakNeeded;
    private int scanCooldown;
    private int failedScans;
    private int repath;
    private int approachTicks;

    public CollectWoodGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.mission() == NpcMission.COLLECT_WOOD && npc.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        target = null;
        blacklist.clear();
        failedScans = 0;
        scanCooldown = 0;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        target = null;
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;

        if (target != null && !isTargetLog(level.getBlockState(target))) {
            target = null;
        }

        if (target == null) {
            if (scanCooldown-- > 0) return;
            scanCooldown = 20;
            target = findLog(level);
            if (target == null) {
                failedScans++;
                if (failedScans >= 3) {
                    npc.finishAndReturn(String.format(Lang.t(npc.lang(),
                            "I can't find any more reachable trees nearby. I collected %d of %d logs, bringing them to you.",
                            "Je ne trouve plus d'arbres accessibles. J'ai récolté %d bûches sur %d, je te les apporte.",
                            "لا أجد أشجارًا يمكنني الوصول إليها. جمعت %d من %d قطعة خشب وسأحضرها لك."),
                            npc.missionProgress(), npc.missionTarget()));
                }
                return;
            }
            failedScans = 0;
            breakTicks = 0;
            approachTicks = 0;
            BlockState st = level.getBlockState(target);
            float hardness = st.getDestroySpeed(level, target);
            float speed = Math.max(1.0F, npc.getMainHandItem().getDestroySpeed(st));
            breakNeeded = Math.max(5, Math.round(hardness * 30.0F / speed));
        }

        Vec3 center = Vec3.atCenterOf(target);
        npc.getLookControl().setLookAt(center.x, center.y, center.z);

        if (npc.getEyePosition().distanceTo(center) > 4.0) {
            if (npc.getNavigation().isDone() || ++repath % 20 == 0) {
                npc.getNavigation().moveTo(center.x, target.getY(), center.z, 1.0);
            }
            if (++approachTicks > 240) { // could not get there: give up on this log
                blacklist.add(target.immutable());
                target = null;
            }
            return;
        }

        npc.getNavigation().stop();
        breakTicks++;
        if (breakTicks % 6 == 0) npc.swing(InteractionHand.MAIN_HAND);
        if (breakTicks >= breakNeeded) harvest(level);
    }

    private void harvest(ServerLevel level) {
        BlockState st = level.getBlockState(target);
        ItemStack tool = npc.getMainHandItem();
        List<ItemStack> drops = Block.getDrops(st, level, target, level.getBlockEntity(target), npc, tool);
        level.destroyBlock(target, false, npc);
        for (ItemStack d : drops) {
            ItemStack left = npc.inventory.addItem(d);
            if (!left.isEmpty()) npc.spawnAtLocation(left);
        }
        if (tool.isDamageableItem()) tool.hurtAndBreak(1, npc, EquipmentSlot.MAINHAND);
        target = null;
        npc.onLogCollected();
    }

    private boolean isTargetLog(BlockState st) {
        if (!st.is(BlockTags.LOGS)) return false;
        String t = npc.woodType();
        if (t.isEmpty()) return true;
        return BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().startsWith(t + "_");
    }

    private BlockPos findLog(ServerLevel level) {
        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        double eyeY = npc.getEyeY();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-SCAN_RADIUS, -2, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, 6, SCAN_RADIUS))) {
            if (!isTargetLog(level.getBlockState(p))) continue;
            if (blacklist.contains(p)) continue;
            double dy = (p.getY() + 0.5) - eyeY;
            if (dy > 4.0) continue; // too high to reach from the ground
            double score = p.distSqr(origin) + Math.abs(dy) * 2.0;
            if (score < bestScore) {
                bestScore = score;
                best = p.immutable();
            }
        }
        return best;
    }
}
