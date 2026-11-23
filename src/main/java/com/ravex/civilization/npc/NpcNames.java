package com.ravex.civilization.npc;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Name pool, aliases (Latin + Arabic spellings) and random personality generation. */
public final class NpcNames {
    public static final String YASSINE_KEY = "yassine";
    public static final String YASSINE_PERSONALITY =
            "Calm, loyal and confident, a little competitive. Loves fast PvP and clever tactics, protective of the player, "
                    + "speaks casually and with dry humour. Fluent in Arabic, French and English.";

    private record Entry(String name, List<String> aliases) {}

    private static final List<Entry> POOL = List.of(
            e("Adam", "آدم"), e("Ali", "علي"), e("Mohamed", "محمد", "mohammed", "muhammad"),
            e("Rayen", "رايان", "rayan"), e("Sami", "سامي"), e("Amir", "أمير", "امير"),
            e("Karim", "كريم"), e("Omar", "عمر"), e("Youssef", "يوسف", "yusuf"),
            e("Hamza", "حمزة"), e("Walid", "وليد"), e("Ilyes", "إلياس", "الياس", "ilyas"),
            e("Mehdi", "مهدي"), e("Bilel", "بلال", "bilal"), e("Anis", "أنيس", "انيس"),
            e("Zied", "زياد", "ziad"), e("Nour", "نور"), e("Lina", "لينا"),
            e("Sarah", "سارة", "sara"), e("Lucas", "لوكاس"), e("Leo", "ليو", "léo"), e("Emma", "إيما", "ايما"));

    private static Entry e(String name, String... aliases) {
        List<String> a = new ArrayList<>();
        a.add(name.toLowerCase(Locale.ROOT));
        for (String s : aliases) a.add(s.toLowerCase(Locale.ROOT));
        return new Entry(name, a);
    }

    private static final String[] SKINS = {"skin_1", "skin_2", "skin_3", "skin_4", "skin_5", "skin_6"};
    private static final String[] TRAITS = {"cheerful", "cautious", "curious", "stubborn", "generous", "sarcastic",
            "shy", "adventurous", "hard-working", "easygoing", "serious", "playful"};
    private static final String[] PROFESSIONS = {"lumberjack", "miner", "farmer", "guard", "builder", "explorer", "cook", "hunter"};
    private static final String[] LIKES = {"collecting wood", "exploring caves", "cooking", "farming", "building cosy houses",
            "fishing", "stargazing", "fighting zombies", "collecting flowers"};

    public static NpcProfile yassine() {
        return new NpcProfile(YASSINE_KEY, "Yassine", "yassine", YASSINE_PERSONALITY);
    }

    public static NpcProfile random(String key, Set<String> usedNames, RandomSource r) {
        List<Entry> free = new ArrayList<>();
        for (Entry en : POOL) if (!usedNames.contains(en.name())) free.add(en);
        String name;
        if (free.isEmpty()) {
            name = POOL.get(r.nextInt(POOL.size())).name() + (2 + r.nextInt(98));
        } else {
            name = free.get(r.nextInt(free.size())).name();
        }
        String t1 = TRAITS[r.nextInt(TRAITS.length)];
        String t2 = TRAITS[r.nextInt(TRAITS.length)];
        String like = LIKES[r.nextInt(LIKES.length)];
        String job = PROFESSIONS[r.nextInt(PROFESSIONS.length)];
        String personality = "A " + t1 + " and " + t2 + " person who enjoys " + like
                + ". Self-described profession/interest: " + job + " (flavour only). Speaks Arabic, French and English casually.";
        return new NpcProfile(key, name, SKINS[r.nextInt(SKINS.length)], personality);
    }

    public static List<String> aliasesFor(String name) {
        for (Entry en : POOL) if (en.name().equalsIgnoreCase(name)) return en.aliases();
        if (name.equalsIgnoreCase("Yassine")) return List.of("yassine", "yassin", "ياسين");
        return List.of(name.toLowerCase(Locale.ROOT));
    }

    /** True if the chat text mentions this NPC by name (whole word, any supported spelling). */
    public static boolean mentions(String npcName, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String alias : aliasesFor(npcName)) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(alias) + "(?![\\p{L}\\p{N}])",
                    Pattern.UNICODE_CASE);
            if (p.matcher(lower).find()) return true;
        }
        return false;
    }

    /** Index of the earliest mention of this NPC in the text, or -1. */
    public static int firstMentionIndex(String npcName, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String alias : aliasesFor(npcName)) {
            java.util.regex.Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(alias) + "(?![\\p{L}\\p{N}])",
                    Pattern.UNICODE_CASE).matcher(lower);
            if (m.find() && (best < 0 || m.start() < best)) best = m.start();
        }
        return best;
    }

    private NpcNames() {}
}
