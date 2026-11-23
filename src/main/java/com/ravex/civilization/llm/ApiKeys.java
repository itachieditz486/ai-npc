package com.ravex.civilization.llm;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * API keys live OUTSIDE the source, the mod config and the world, in the Minecraft config folder:
 *   OpenRouter: env RAVEX_OPENROUTER_API_KEY / OPENROUTER_API_KEY, else config/ravex_civ_openrouter_key.txt
 *   OpenAI:     env RAVEX_OPENAI_API_KEY / OPENAI_API_KEY,         else config/ravex_civ_openai_key.txt
 * Keys are never logged, never put in chat, never saved in the world.
 */
public final class ApiKeys {
    public static String openRouter() {
        return lookup("RAVEX_OPENROUTER_API_KEY", "OPENROUTER_API_KEY", "ravex_civ_openrouter_key.txt");
    }

    public static String openAi() {
        return lookup("RAVEX_OPENAI_API_KEY", "OPENAI_API_KEY", "ravex_civ_openai_key.txt");
    }

    /** "environment variable", "config file" or "none" (for display only; never the key itself). */
    public static String source(String provider) {
        String e1 = provider.equals("openai") ? "RAVEX_OPENAI_API_KEY" : "RAVEX_OPENROUTER_API_KEY";
        String e2 = provider.equals("openai") ? "OPENAI_API_KEY" : "OPENROUTER_API_KEY";
        if (notBlank(System.getenv(e1)) || notBlank(System.getenv(e2))) return "environment variable";
        return readFile(fileName(provider)).isEmpty() ? "none" : "config file";
    }

    public static boolean hasFileKey(String provider) {
        return !readFile(fileName(provider)).isEmpty();
    }

    /** Saves the key locally in the Minecraft config folder (owner-only permissions where supported). */
    public static void save(String provider, String key) throws IOException {
        Path p = FMLPaths.CONFIGDIR.get().resolve(fileName(provider));
        Files.writeString(p, key.trim() + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: default user-profile permissions apply
        }
    }

    public static void clear(String provider) throws IOException {
        Files.deleteIfExists(FMLPaths.CONFIGDIR.get().resolve(fileName(provider)));
    }

    private static String fileName(String provider) {
        return provider.equals("openai") ? "ravex_civ_openai_key.txt" : "ravex_civ_openrouter_key.txt";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String readFile(String fileName) {
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve(fileName);
            if (Files.isRegularFile(p)) {
                var lines = Files.readAllLines(p);
                if (!lines.isEmpty()) return lines.get(0).trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String lookup(String env1, String env2, String fileName) {
        String k = System.getenv(env1);
        if (!notBlank(k)) k = System.getenv(env2);
        if (!notBlank(k)) k = readFile(fileName);
        return k == null ? "" : k.trim();
    }

    private ApiKeys() {}
}
