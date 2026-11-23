package com.ravex.civilization.ai;

import com.ravex.civilization.npc.Lang;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcMission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Walks (or digs) home, then stores non-essential items in a chest/barrel near home. */
public class GoHomeGoal extends Goal {
    private static final int MAX_TICKS = 4000;

    private final NpcEntity npc;
    private final Digger digger;
    private int ticks;
    private BlockPos chest;
    private boolean chestSearched;

    public GoHomeGoal(NpcEntity npc) {
        this.npc = npc;
        this.digger = new Digger(npc, s -> false, () -> {});
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.mission() == NpcMission.RETURN_HOME && npc.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        digger.reset();
        ticks = 0;
        chest = null;
        chestSearched = false;
    }

    @Override
    public void stop() {
        digger.reset();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        BlockPos home = npc.home();
        if (home == null) {
            npc.finishMission(Lang.t(npc.lang(), "I don't have a home yet.", "Je n'ai pas encore de maison.",
                    "ليس لدي منزل بعد."));
            return;
        }
        if (++ticks > MAX_TICKS) {
            npc.finishMission(Lang.t(npc.lang(), "I couldn't find a way home.", "Je n'arrive pas à rentrer.",
                    "لا أستطيع إيجاد طريق للمنزل."));
            return;
        }
        if (digger.isDigging()) {
            digger.tick(level);
            return;
        }

        if (npc.distanceToSqr(Vec3.atCenterOf(home)) > 25.0 && chest == null) {
            if (digger.moveToward(level, home) == Digger.Result.BLOCKED) {
                npc.finishMission(Lang.t(npc.lang(), "Something blocks my way home (unsafe to dig).",
                        "Quelque chose bloque mon chemin (creuser n'est pas sûr).",
                        "شيء يمنعني من العودة (الحفر غير آمن)."));
            }
            return;
        }

        // at home: find a chest/barrel and store things
        if (!chestSearched) {
            chestSearched = true;
            chest = findContainer(level, home);
        }
        if (chest != null && npc.distanceToSqr(Vec3.atCenterOf(chest)) > 9.0) {
            npc.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
            return;
        }
        int moved = chest == null ? 0 : npc.depositInto(level, chest);
        if (chest == null) {
            npc.finishMission(Lang.t(npc.lang(), "I'm home. There's no chest or barrel nearby to store things in.",
                    "Je suis à la maison. Pas de coffre ni de tonneau à proximité.",
                    "وصلت للمنزل. لا يوجد صندوق قريب لتخزين الأغراض."));
        } else {
            npc.finishMission(String.format(Lang.t(npc.lang(), "I'm home and stored %d items.",
                    "Je suis à la maison et j'ai rangé %d objets.", "وصلت للمنزل وخزنت %d غرضًا."), moved));
        }
    }

    private static BlockPos findContainer(ServerLevel level, BlockPos home) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-6, -2, -6), home.offset(6, 2, 6))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
                double d = p.distSqr(home);
                if (d < bd) {
                    bd = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }
}
