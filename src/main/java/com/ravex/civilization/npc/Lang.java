package com.ravex.civilization.npc;

import java.util.regex.Pattern;

/** Tiny 3-language helper (en / fr / ar). */
public final class Lang {
    private static final Pattern ARABIC = Pattern.compile("[\\u0600-\\u06FF]");
    private static final Pattern FRENCH = Pattern.compile(
            "(?iu)\\b(bonjour|salut|bois|arbre|suis|viens|arr[êe]te|merci|prot[èe]ge|maison|construis|coupe|peux|veux|fais|moi|s'il|svp|stp|tronc)\\b");

    public static String detect(String text, String fallback) {
        if (ARABIC.matcher(text).find()) return "ar";
        if (FRENCH.matcher(text).find()) return "fr";
        // plain ASCII with no French markers: assume English, but keep a previous non-English choice for very short messages
        if (text.trim().length() < 4 && fallback != null) return fallback;
        return "en";
    }

    public static String t(String lang, String en, String fr, String ar) {
        return switch (lang == null ? "en" : lang) {
            case "fr" -> fr;
            case "ar" -> ar;
            default -> en;
        };
    }

    private Lang() {}
}
