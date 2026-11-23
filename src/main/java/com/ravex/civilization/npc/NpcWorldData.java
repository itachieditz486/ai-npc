package com.ravex.civilization.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ravex.civilization.RavexCiv;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-world persistent store: identities, memories, Yassine uniqueness registry. */
public class NpcWorldData extends SavedData {
    private static final String NAME = "ravex_civ_npcs";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, NpcProfile> profiles = new LinkedHashMap<>();
    private final Map<String, NpcMemory> memories = new HashMap<>();
    private UUID yassineEntity;
    private boolean yassineKitGiven;
    private boolean yassineAutoSpawnDone;

    public static NpcWorldData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(NpcWorldData::new, NpcWorldData::load, null), NAME);
    }

    public static NpcWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcWorldData d = new NpcWorldData();
        ListTag ps = tag.getList("profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            NpcProfile p = NpcProfile.fromNbt(ps.getCompound(i));
            d.profiles.put(p.key(), p);
        }
        CompoundTag ms = tag.getCompound("memories");
        for (String k : ms.getAllKeys()) d.memories.put(k, NpcMemory.fromNbt(ms.getCompound(k)));
        if (tag.hasUUID("yassineEntity")) d.yassineEntity = tag.getUUID("yassineEntity");
        d.yassineKitGiven = tag.getBoolean("yassineKitGiven");
        d.yassineAutoSpawnDone = tag.getBoolean("yassineAutoSpawnDone");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag ps = new ListTag();
        for (NpcProfile p : profiles.values()) ps.add(p.toNbt());
        tag.put("profiles", ps);
        CompoundTag ms = new CompoundTag();
        for (Map.Entry<String, NpcMemory> e : memories.entrySet()) ms.put(e.getKey(), e.getValue().toNbt());
        tag.put("memories", ms);
        if (yassineEntity != null) tag.putUUID("yassineEntity", yassineEntity);
        tag.putBoolean("yassineKitGiven", yassineKitGiven);
        tag.putBoolean("yassineAutoSpawnDone", yassineAutoSpawnDone);
        return tag;
    }

    // ---- profiles -------------------------------------------------------------------------------------------------
    public NpcProfile yassineProfile() {
        return profiles.computeIfAbsent(NpcNames.YASSINE_KEY, k -> {
            setDirty();
            return NpcNames.yassine();
        });
    }

    public NpcProfile newRandomProfile(net.minecraft.util.RandomSource random) {
        Set<String> used = new java.util.HashSet<>();
        for (NpcProfile p : profiles.values()) used.add(p.name());
        NpcProfile p = NpcNames.random(UUID.randomUUID().toString(), used, random);
        profiles.put(p.key(), p);
        setDirty();
        return p;
    }

    public NpcProfile profile(String key) {
        return profiles.get(key);
    }

    // ---- memory ---------------------------------------------------------------------------------------------------
    public NpcMemory memory(String key) {
        return memories.computeIfAbsent(key, k -> new NpcMemory());
    }

    // ---- Yassine uniqueness ---------------------------------------------------------------------------------------
    public UUID yassineEntity() { return yassineEntity; }
    public void setYassineEntity(UUID id) { this.yassineEntity = id; setDirty(); }
    public boolean yassineKitGiven() { return yassineKitGiven; }
    public void setYassineKitGiven() { this.yassineKitGiven = true; setDirty(); }
    public boolean yassineAutoSpawnDone() { return yassineAutoSpawnDone; }
    public void setYassineAutoSpawnDone() { this.yassineAutoSpawnDone = true; setDirty(); }

    // ---- Yassine redundant backup (plain JSON next to the world) --------------------------------------------------
    private static Path backupPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("ravex_civ").resolve("yassine_memory.json");
    }

    public void writeYassineBackup(MinecraftServer server) {
        try {
            Path target = backupPath(server);
            Files.createDirectories(target.getParent());
            JsonObject o = memory(NpcNames.YASSINE_KEY).toJson();
            Path tmp = target.resolveSibling("yassine_memory.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            RavexCiv.LOGGER.warn("Could not write Yassine backup: {}", ex.toString());
        }
    }

    /** Called on server start: if Yassine's memory is missing from world data but a backup exists, restore it. */
    public void restoreYassineBackupIfNeeded(MinecraftServer server) {
        NpcMemory current = memories.get(NpcNames.YASSINE_KEY);
        if (current != null && !current.history().isEmpty()) return;
        Path p = backupPath(server);
        if (!Files.isRegularFile(p)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            memories.put(NpcNames.YASSINE_KEY, NpcMemory.fromJson(o));
            setDirty();
            RavexCiv.LOGGER.info("Restored Yassine's memory from backup file.");
        } catch (Exception ex) {
            RavexCiv.LOGGER.warn("Could not read Yassine backup: {}", ex.toString());
        }
    }
}
