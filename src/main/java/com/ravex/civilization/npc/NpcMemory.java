package com.ravex.civilization.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** Conversation history + long-term facts for one NPC. */
public class NpcMemory {
    public static final int STORE_CAP = 40;
    public static final int FACT_CAP = 30;

    public record Turn(String role, String text) {}

    private final List<Turn> history = new ArrayList<>();
    private final List<String> facts = new ArrayList<>();

    public List<Turn> history() { return history; }
    public List<String> facts() { return facts; }

    public void addTurn(String role, String text) {
        history.add(new Turn(role, text));
        while (history.size() > STORE_CAP) history.remove(0);
    }

    public void addFact(String fact) {
        if (fact == null) return;
        fact = fact.trim();
        if (fact.isEmpty() || fact.length() > 200 || facts.contains(fact)) return;
        facts.add(fact);
        while (facts.size() > FACT_CAP) facts.remove(0);
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        ListTag h = new ListTag();
        for (Turn turn : history) {
            CompoundTag c = new CompoundTag();
            c.putString("r", turn.role());
            c.putString("t", turn.text());
            h.add(c);
        }
        t.put("history", h);
        ListTag f = new ListTag();
        for (String s : facts) f.add(StringTag.valueOf(s));
        t.put("facts", f);
        return t;
    }

    public static NpcMemory fromNbt(CompoundTag t) {
        NpcMemory m = new NpcMemory();
        ListTag h = t.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < h.size(); i++) {
            CompoundTag c = h.getCompound(i);
            m.history.add(new Turn(c.getString("r"), c.getString("t")));
        }
        ListTag f = t.getList("facts", Tag.TAG_STRING);
        for (int i = 0; i < f.size(); i++) m.facts.add(f.getString(i));
        return m;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonArray h = new JsonArray();
        for (Turn turn : history) {
            JsonObject c = new JsonObject();
            c.addProperty("role", turn.role());
            c.addProperty("text", turn.text());
            h.add(c);
        }
        o.add("history", h);
        JsonArray f = new JsonArray();
        for (String s : facts) f.add(s);
        o.add("facts", f);
        return o;
    }

    public static NpcMemory fromJson(JsonObject o) {
        NpcMemory m = new NpcMemory();
        if (o.has("history")) {
            for (var e : o.getAsJsonArray("history")) {
                JsonObject c = e.getAsJsonObject();
                m.history.add(new Turn(c.get("role").getAsString(), c.get("text").getAsString()));
            }
        }
        if (o.has("facts")) {
            for (var e : o.getAsJsonArray("facts")) m.facts.add(e.getAsString());
        }
        return m;
    }
}
