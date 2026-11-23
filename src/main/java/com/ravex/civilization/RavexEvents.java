package com.ravex.civilization;

import com.ravex.civilization.command.NpcCommand;
import com.ravex.civilization.npc.NpcBrain;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcNames;
import com.ravex.civilization.npc.NpcSpawner;
import com.ravex.civilization.npc.NpcWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Game-bus event wiring: chat, commands, Yassine guard, memory restore, natural spawning, Yassine first-join spawn. */
public final class RavexEvents {
    private static final Pattern RELATIONAL = Pattern.compile("(?iu)(follow|help|aide|suis|suivre|ساعد|اتبع|تبع)");

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RavexEvents::onChat);
        NeoForge.EVENT_BUS.addListener(RavexEvents::onCommands);
        NeoForge.EVENT_BUS.addListener(RavexEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(RavexEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RavexEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(RavexEvents::onPlayerLogin);
    }

    public static List<NpcEntity> allNpcs(MinecraftServer server) {
        List<NpcEntity> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.addAll(level.getEntities(EntityTypeTest.forClass(NpcEntity.class), e -> e.isAlive()));
        }
        return out;
    }

    private static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String text = event.getRawText();
        if (text == null || text.isBlank()) return;
        List<NpcEntity> addressed = new ArrayList<>();
        for (NpcEntity npc : allNpcs(player.server)) {
            if (NpcNames.mentions(npc.npcName(), text)) addressed.add(npc);
        }
        if (addressed.size() > 1 && RELATIONAL.matcher(text).find()) {
            // "Yassine, follow Adam": only the NPC named first is being spoken to; the other is the object.
            addressed.sort(Comparator.comparingInt(n -> NpcNames.firstMentionIndex(n.npcName(), text)));
            addressed = List.of(addressed.get(0));
        }
        for (NpcEntity npc : addressed) NpcBrain.handle(npc, player, text);
    }

    private static void onCommands(RegisterCommandsEvent event) {
        NpcCommand.register(event.getDispatcher());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        NpcWorldData.get(event.getServer()).restoreYassineBackupIfNeeded(event.getServer());
    }

    /** Prevents a second Yassine from ever entering the world (e.g. copied chunks, creative duplication). */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof NpcEntity npc)) return;
        if (!npc.isYassine() || !(event.getLevel() instanceof ServerLevel level)) return;
        NpcWorldData data = NpcWorldData.get(level.getServer());
        UUID registered = data.yassineEntity();
        if (registered == null) {
            data.setYassineEntity(npc.getUUID());
        } else if (!registered.equals(npc.getUUID())) {
            for (ServerLevel l : level.getServer().getAllLevels()) {
                if (l.getEntity(registered) != null) {
                    event.setCanceled(true);
                    RavexCiv.LOGGER.warn("Blocked a duplicate Yassine entity.");
                    return;
                }
            }
        }
    }

    // ---- Yassine: optional one-time appearance near the first player ----------------------------------------------
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !RavexConfig.YASSINE_AUTO_SPAWN.get()) return;
        NpcWorldData data = NpcWorldData.get(p.server);
        if (data.yassineAutoSpawnDone()) return;
        data.setYassineAutoSpawnDone();                 // mark first: this can never happen twice
        if (data.yassineEntity() != null) return;       // he already exists (e.g. spawned with the egg)
        ServerLevel level = p.serverLevel();
        BlockPos near = p.blockPosition().relative(p.getDirection(), 3);
        BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
        NpcSpawner.Result r = NpcSpawner.yassine(level, spot, p.getYRot() + 180.0F);
        if (r.npc() == null) RavexCiv.LOGGER.warn("Yassine auto-spawn skipped: {}", r.error());
    }

    // ---- natural random NPC spawning ------------------------------------------------------------------------------
    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!RavexConfig.NATURAL_SPAWN.get()) return;
        long interval = RavexConfig.SPAWN_INTERVAL_SECONDS.get() * 20L;
        if (server.getTickCount() % interval != 0) return;

        List<NpcEntity> all = allNpcs(server);
        int randoms = 0;
        for (NpcEntity n : all) if (!n.isYassine()) randoms++;
        if (randoms >= RavexConfig.MAX_POPULATION.get()) return;

        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        RandomSource rnd = level.getRandom();
        ServerPlayer p = players.get(rnd.nextInt(players.size()));

        int min = RavexConfig.SPAWN_MIN_DISTANCE.get();
        int max = Math.max(min + 1, RavexConfig.SPAWN_MAX_DISTANCE.get());
        double ang = rnd.nextDouble() * Math.PI * 2;
        int dist = min + rnd.nextInt(max - min + 1);
        BlockPos col = new BlockPos(p.getBlockX() + (int) (Math.cos(ang) * dist), 0, p.getBlockZ() + (int) (Math.sin(ang) * dist));
        if (!level.isLoaded(col)) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
        if (!suitable(level, top)) return;
        NpcSpawner.random(level, top, rnd.nextFloat() * 360.0F);
    }

    private static boolean suitable(ServerLevel level, BlockPos top) {
        BlockPos ground = top.below();
        return level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)
                && level.getFluidState(top).isEmpty() && level.getFluidState(ground).isEmpty()
                && level.getBlockState(top).getCollisionShape(level, top).isEmpty()
                && level.getBlockState(top.above()).getCollisionShape(level, top.above()).isEmpty()
                && level.getMaxLocalRawBrightness(top) >= 8;
    }

    private RavexEvents() {}
}
