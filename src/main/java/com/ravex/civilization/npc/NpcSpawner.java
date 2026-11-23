package com.ravex.civilization.npc;

import com.ravex.civilization.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Single place that creates NPCs (used by the eggs, natural spawning and Yassine's first-join spawn). */
public final class NpcSpawner {
    public record Result(NpcEntity npc, String error) {}

    /** Spawns THE Yassine, or refuses with an explanation if he already exists. */
    public static Result yassine(ServerLevel sl, BlockPos pos, float yaw) {
        MinecraftServer server = sl.getServer();
        NpcWorldData data = NpcWorldData.get(server);
        UUID existing = data.yassineEntity();
        if (existing != null) {
            boolean loaded = false;
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(existing);
                if (e != null && e.isAlive()) { loaded = true; break; }
            }
            return new Result(null, loaded
                    ? "Yassine already exists in this world - there can only be one."
                    : "Yassine already exists in this world (probably in an unloaded area). Use /npc release_yassine if he is truly lost.");
        }
        NpcProfile profile = data.yassineProfile();
        NpcEntity npc = ModEntities.NPC.get().create(sl);
        if (npc == null) return new Result(null, "Could not create Yassine.");
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        npc.initProfile(profile);
        npc.setHome(pos);
        if (!data.yassineKitGiven()) {
            npc.giveStarterKit(sl.getRandom());
            npc.giveYassineCombatKit();
            data.setYassineKitGiven();
        }
        data.setYassineEntity(npc.getUUID());
        sl.addFreshEntity(npc);
        return new Result(npc, null);
    }

    /** Spawns a brand-new random NPC (never Yassine: his identity is not in the random pool). */
    public static NpcEntity random(ServerLevel sl, BlockPos pos, float yaw) {
        NpcWorldData data = NpcWorldData.get(sl.getServer());
        NpcProfile profile = data.newRandomProfile(sl.getRandom());
        NpcEntity npc = ModEntities.NPC.get().create(sl);
        if (npc == null) return null;
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        npc.initProfile(profile);
        npc.setHome(pos);
        npc.giveStarterKit(sl.getRandom());
        sl.addFreshEntity(npc);
        return npc;
    }

    private NpcSpawner() {}
}
