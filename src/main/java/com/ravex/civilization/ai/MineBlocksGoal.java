package com.ravex.civilization.ai;

import com.ravex.civilization.npc.Lang;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcMission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mining / caving mission.
 *  - Targets only blocks it can actually SEE (line of sight, exposed face): no X-ray through rock.
 *  - With nothing in sight it explores: first real cave/underground space reachable by pathfinding (entering caves,
 *    visiting unvisited areas); if there is none it digs a staircase / branch tunnel at a depth suited to the ore.
 *  - Needs a pickaxe that can harvest the block; durability is consumed; stops when the pickaxe is gone, the
 *    inventory is full, health is low, time runs out or the count is reached; then it delivers to the player or goes home.
 */
public class MineBlocksGoal extends Goal {
    private static final int SCAN_H = 10;
    private static final int SCAN_UP = 6;
    private static final int SCAN_DOWN = 8;
    private static final int MAX_TICKS = 9000;

    private final NpcEntity npc;
    private final Digger digger;
    private final Set<BlockPos> blacklist = new HashSet<>();
    private final Set<Long> visited = new HashSet<>();
    private BlockPos target;
    private BlockPos exploreSpot;
    private int exploreTicks;
    private int scanCooldown;
    private int caveScanCooldown;
    private int blockedCount;
    private int dirX = 1, dirZ = 0;

    public MineBlocksGoal(NpcEntity npc) {
        this.npc = npc;
        this.digger = new Digger(npc, this::matches, npc::onBlockCollected);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.mission() == NpcMission.COLLECT_BLOCKS && npc.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        digger.reset();
        blacklist.clear();
        visited.clear();
        target = null;
        exploreSpot = null;
        scanCooldown = 0;
        caveScanCooldown = 0;
        blockedCount = 0;
        pickDirection();
    }

    @Override
    public void stop() {
        digger.reset();
        target = null;
        exploreSpot = null;
    }

    private void pickDirection() {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] d = dirs[npc.getRandom().nextInt(4)];
        dirX = d[0];
        dirZ = d[1];
    }

    private boolean matches(BlockState st) {
        return switch (npc.collectKind()) {
            case "stone" -> st.is(BlockTags.BASE_STONE_OVERWORLD);
            case "coal" -> st.is(BlockTags.COAL_ORES);
            case "iron" -> st.is(BlockTags.IRON_ORES);
            case "gold" -> st.is(BlockTags.GOLD_ORES);
            case "diamond" -> st.is(BlockTags.DIAMOND_ORES);
            case "ores", "caving" -> isAnyOre(st);
            default -> false;
        };
    }

    private static boolean isAnyOre(BlockState st) {
        return st.is(BlockTags.COAL_ORES) || st.is(BlockTags.IRON_ORES) || st.is(BlockTags.GOLD_ORES)
                || st.is(BlockTags.DIAMOND_ORES) || st.is(BlockTags.COPPER_ORES) || st.is(BlockTags.REDSTONE_ORES)
                || st.is(BlockTags.LAPIS_ORES) || st.is(BlockTags.EMERALD_ORES);
    }

    /** Lower = more wanted (used so "collect ores" prefers diamonds > gold > iron > the rest). */
    private static double priority(BlockState st) {
        if (st.is(BlockTags.DIAMOND_ORES)) return -400;
        if (st.is(BlockTags.GOLD_ORES)) return -300;
        if (st.is(BlockTags.IRON_ORES)) return -150;
        return 0;
    }

    private int depthFor(int currentY) {
        return switch (npc.collectKind()) {
            case "coal" -> 45;
            case "iron" -> 16;
            case "gold" -> -16;
            case "diamond" -> -54;
            case "ores", "caving" -> 0;
            default -> currentY - 6;
        };
    }

    private void finish(String en, String fr, String ar) {
        npc.finishAndReturn(Lang.t(npc.lang(), en, fr, ar));
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        npc.missionTicks++;

        if (digger.isDigging()) {
            digger.tick(level);
            return;
        }
        if (npc.missionProgress() >= npc.missionTarget()) {
            npc.finishAndReturn(String.format(Lang.t(npc.lang(), "Done! I collected %d (%s). Bringing it to you.",
                    "Terminé ! J'en ai récolté %d (%s). Je te l'apporte.", "تم! جمعت %d (%s). سأحضرها لك."),
                    npc.missionProgress(), npc.collectKind()));
            return;
        }
        if (npc.getHealth() < 8.0F) {
            finish("I'm hurt - it's too dangerous, heading back.", "Je suis blessé - trop dangereux, je rentre.",
                    "أنا مصاب - الوضع خطير، سأعود.");
            return;
        }
        if (!npc.hasPickaxe()) {
            finish("I have no usable pickaxe left, heading back.", "Je n'ai plus de pioche utilisable, je rentre.",
                    "لم يعد لدي فأس صالح، سأعود.");
            return;
        }
        if (npc.inventoryFull()) {
            finish("My inventory is full, heading back.", "Mon inventaire est plein, je rentre.", "حقيبتي ممتلئة، سأعود.");
            return;
        }
        if (npc.missionTicks > MAX_TICKS) {
            finish("I've been at it long enough. Heading back with what I have.",
                    "J'y suis depuis assez longtemps. Je rentre avec ce que j'ai.", "قضيت وقتًا كافيًا. سأعود بما لدي.");
            return;
        }

        if (target != null && !matches(level.getBlockState(target))) target = null;
        if (target == null && scanCooldown-- <= 0) {
            scanCooldown = 20;
            target = findVisibleTarget(level);
        }

        if (target != null) {
            workOnTarget(level);
            return;
        }
        explore(level);
    }

    private void workOnTarget(ServerLevel level) {
        Vec3 c = Vec3.atCenterOf(target);
        Vec3 eye = npc.getEyePosition();
        if (eye.distanceTo(c) <= 4.0) {
            BlockHitResult hit = level.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
            BlockPos toBreak = (hit.getType() == HitResult.Type.MISS) ? target : hit.getBlockPos();
            if (!digger.startDig(level, toBreak)) {
                blacklist.add(target.immutable());
                target = null;
            }
        } else if (digger.moveToward(level, target) == Digger.Result.BLOCKED) {
            blacklist.add(target.immutable());
            target = null;
        }
    }

    // ---- exploration ----------------------------------------------------------------------------------------------
    private void explore(ServerLevel level) {
        PathNavigation nav = npc.getNavigation();
        // 1. keep walking to the current cave spot
        if (exploreSpot != null) {
            exploreTicks++;
            Vec3 c = Vec3.atBottomCenterOf(exploreSpot);
            if (npc.position().distanceToSqr(c) <= 6.0 || exploreTicks > 300) {
                visited.add(cell(exploreSpot));
                exploreSpot = null;
            } else {
                if (nav.isDone()) nav.moveTo(c.x, c.y, c.z, 1.0);
                return;
            }
        }
        // 2. look for new underground space that real pathfinding can reach
        if (caveScanCooldown-- <= 0) {
            caveScanCooldown = 30;
            BlockPos spot = findCaveSpot(level);
            if (spot != null) {
                exploreSpot = spot;
                exploreTicks = 0;
                nav.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
                return;
            }
        }
        // 3. no cave to explore: dig down / branch-mine toward a good depth
        BlockPos feet = npc.blockPosition();
        int y = Math.max(level.getMinBuildHeight() + 5, depthFor(feet.getY()));
        BlockPos goal = new BlockPos(feet.getX() + dirX * 12, y, feet.getZ() + dirZ * 12);
        if (digger.moveToward(level, goal) == Digger.Result.BLOCKED) {
            blockedCount++;
            pickDirection();
            if (blockedCount > 8) {
                finish("I can't explore or dig any further safely. Heading back.",
                        "Je ne peux plus explorer ni creuser en sécurité. Je rentre.",
                        "لا أستطيع الاستكشاف أو الحفر بأمان أكثر. سأعود.");
            }
        }
    }

    private static long cell(BlockPos p) {
        return new BlockPos(p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2).asLong();
    }

    private boolean underground(ServerLevel level, BlockPos p) {
        if (level.canSeeSky(p)) return false;
        return p.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) - 2;
    }

    private boolean standable(ServerLevel level, BlockPos p) {
        BlockPos below = p.below();
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getFluidState(p).isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** An unvisited underground standing spot that the pathfinder can actually reach (so no walking through rock). */
    private BlockPos findCaveSpot(ServerLevel level) {
        BlockPos o = npc.blockPosition();
        int depth = depthFor(o.getY());
        List<BlockPos> cands = new ArrayList<>();
        for (int dx = -16; dx <= 16; dx += 2) {
            for (int dz = -16; dz <= 16; dz += 2) {
                int x = o.getX() + dx, z = o.getZ() + dz;
                if (!level.hasChunkAt(new BlockPos(x, o.getY(), z))) continue;
                for (int y = o.getY() + 3; y >= o.getY() - 24 && y > level.getMinBuildHeight() + 2; y -= 2) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (visited.contains(cell(p)) || blacklist.contains(p)) continue;
                    if (!underground(level, p) || !standable(level, p)) continue;
                    if (p.distSqr(o) < 16) continue;
                    cands.add(p);
                }
            }
        }
        cands.sort(Comparator.comparingDouble(p -> p.distSqr(o) + Math.abs(p.getY() - depth) * 6.0));
        PathNavigation nav = npc.getNavigation();
        int tried = 0;
        for (BlockPos p : cands) {
            if (tried++ >= 6) break;
            Path path = nav.createPath(p, 1);
            if (path != null && path.canReach()) return p;
            blacklist.add(p.immutable());
        }
        return null;
    }

    // ---- target search (line of sight only) -----------------------------------------------------------------------
    private BlockPos findVisibleTarget(ServerLevel level) {
        BlockPos o = npc.blockPosition();
        List<BlockPos> cands = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-SCAN_H, -SCAN_DOWN, -SCAN_H), o.offset(SCAN_H, SCAN_UP, SCAN_H))) {
            BlockState st = level.getBlockState(p);
            if (!matches(st) || blacklist.contains(p)) continue;
            if (!npc.canHarvest(st)) continue;   // would drop nothing with my tools
            if (!exposed(level, p)) continue;
            cands.add(p.immutable());
        }
        cands.sort(Comparator.comparingDouble(p -> p.distSqr(o) + priority(level.getBlockState(p))));
        Vec3 eye = npc.getEyePosition();
        int checked = 0;
        for (BlockPos p : cands) {
            if (checked++ >= 8) break;
            if (hasLineOfSight(level, eye, p)) return p;
        }
        return null;
    }

    private boolean hasLineOfSight(ServerLevel level, Vec3 eye, BlockPos p) {
        BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(p), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, npc));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p);
    }

    private static boolean exposed(ServerLevel level, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(p.relative(d)).isAir()) return true;
        }
        return false;
    }
}
