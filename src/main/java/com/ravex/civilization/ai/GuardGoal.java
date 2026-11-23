package com.ravex.civilization.ai;

import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcMission;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** STAY: hold position. GUARD: hold an area, return to it after fights (fighting itself is handled by targeting). */
public class GuardGoal extends Goal {
    private final NpcEntity npc;

    public GuardGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean active() {
        return (npc.mission() == NpcMission.STAY || npc.mission() == NpcMission.GUARD) && npc.anchor() != null;
    }

    private double radiusSqr() {
        return npc.mission() == NpcMission.STAY ? 4.0 : 16.0;
    }

    @Override
    public boolean canUse() {
        return active() && npc.getTarget() == null && npc.distanceToSqr(Vec3.atCenterOf(npc.anchor())) > radiusSqr();
    }

    @Override
    public boolean canContinueToUse() {
        return active() && npc.getTarget() == null && npc.distanceToSqr(Vec3.atCenterOf(npc.anchor())) > 2.0;
    }

    @Override
    public void tick() {
        BlockPos a = npc.anchor();
        if (npc.getNavigation().isDone()) npc.getNavigation().moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }
}
