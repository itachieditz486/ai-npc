package com.ravex.civilization.npc;

import net.minecraft.nbt.CompoundTag;

/** Fixed identity of an NPC. Stored in world data, so it never changes after creation. */
public record NpcProfile(String key, String name, String skin, String personality) {
    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putString("key", key);
        t.putString("name", name);
        t.putString("skin", skin);
        t.putString("personality", personality);
        return t;
    }

    public static NpcProfile fromNbt(CompoundTag t) {
        return new NpcProfile(t.getString("key"), t.getString("name"), t.getString("skin"), t.getString("personality"));
    }
}
