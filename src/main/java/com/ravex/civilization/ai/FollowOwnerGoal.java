package com.ravex.civilization.ai;

import com.ravex.civilization.npc.Lang;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcMission;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Walks (never teleports) toward the player or another NPC for FOLLOW / PROTECT / COME / FOLLOW_NPC / HELP_FIGHT. */
public class FollowOwnerGoal extends Goal {
    private final NpcEntity npc;
    private Entity followed;
    private int repath;

    public FollowOwnerGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean active() {
        return switch (npc.mission()) {
            case FOLLOW, PROTECT, COME, FOLLOW_NPC, HELP_FIGHT -> true;
            default -> false;
        };
    }

    @Override
    public boolean canUse() {
        if (!active() || npc.getTarget() != null) return false;
        followed = npc.followedEntity();
        if (followed == null || !followed.isAlive() || followed.level() != npc.level()) return false;
        double d = npc.distanceToSqr(followed);
        if (npc.mission() == NpcMission.COME && d <= 16.0) {
            npc.finishMission(Lang.t(npc.lang(), "I'm here.", "Me voilà.", "أنا هنا."));
            return false;
        }
        return d > 16.0;
    }

    @Override
    public boolean canContinueToUse() {
        return active() && npc.getTarget() == null && followed != null && followed.isAlive()
                && followed.level() == npc.level() && npc.distanceToSqr(followed) > 6.25;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        followed = null;
    }

    @Override
    public void tick() {
        npc.getLookControl().setLookAt(followed, 10.0F, npc.getMaxHeadXRot());
        if (--repath <= 0) {
            repath = 10;
            double speed = npc.distanceToSqr(followed) > 144.0 ? 1.3 : 1.0;
            npc.getNavigation().moveTo(followed, speed);
        }
    }
}
