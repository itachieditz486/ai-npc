package com.ravex.civilization.ai;

import com.ravex.civilization.npc.Lang;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcMission;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Brings the collected items to the player and hands them over (real inventory transfer; overflow is dropped at the player's feet). */
public class DeliverGoal extends Goal {
    private static final int MAX_TICKS = 4000;

    private final NpcEntity npc;
    private final Digger digger;
    private int ticks;

    public DeliverGoal(NpcEntity npc) {
        this.npc = npc;
        this.digger = new Digger(npc, s -> false, () -> {});
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.mission() == NpcMission.DELIVER && npc.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        digger.reset();
        ticks = 0;
    }

    @Override
    public void stop() {
        digger.reset();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = npc.owner();
        if (owner == null || owner.level() != npc.level() || ++ticks > MAX_TICKS) {
            npc.finishMission(Lang.t(npc.lang(), "I can't reach you right now, so I'm keeping the items for now. Tell me \"give me the resources\" later.",
                    "Je ne peux pas t'atteindre, je garde les objets pour l'instant. Dis-moi « donne-moi les ressources » plus tard.",
                    "لا أستطيع الوصول إليك الآن، سأحتفظ بالأغراض. قل لي \"أعطني الموارد\" لاحقًا."));
            return;
        }
        if (digger.isDigging()) {
            digger.tick(level);
            return;
        }
        npc.getLookControl().setLookAt(owner, 10.0F, npc.getMaxHeadXRot());
        if (npc.distanceToSqr(owner) > 9.0) {
            if (digger.moveToward(level, owner.blockPosition()) == Digger.Result.BLOCKED) {
                npc.finishMission(Lang.t(npc.lang(), "Something blocks my way to you. I'm keeping the items.",
                        "Quelque chose bloque mon chemin. Je garde les objets.", "شيء يمنعني من الوصول إليك. سأحتفظ بالأغراض."));
            }
            return;
        }
        int[] r = npc.giveAllTo(owner);
        String msg = String.format(Lang.t(npc.lang(), "Here you go - I gave you %d items.",
                "Voilà - je t'ai donné %d objets.", "تفضل - أعطيتك %d غرضًا."), r[0]);
        if (r[1] > 0) {
            msg += " " + String.format(Lang.t(npc.lang(), "Your inventory was full, so I dropped %d at your feet.",
                    "Ton inventaire était plein, j'en ai posé %d à tes pieds.", "حقيبتك كانت ممتلئة، فأسقطت %d عند قدميك."), r[1]);
        }
        npc.finishMission(msg);
    }
}
