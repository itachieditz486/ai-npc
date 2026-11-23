package com.ravex.civilization;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Config file: config/ravex_civ-common.toml. API keys are NEVER stored here. */
public final class RavexConfig {
    public static final ModConfigSpec SPEC;
    // ai
    public static final ModConfigSpec.BooleanValue AI_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> PROVIDER;
    public static final ModConfigSpec.ConfigValue<String> OPENROUTER_MODEL;
    public static final ModConfigSpec.ConfigValue<String> OPENROUTER_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> MODEL;
    public static final ModConfigSpec.ConfigValue<String> BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_BASE_URL;
    public static final ModConfigSpec.IntValue MAX_TOKENS;
    public static final ModConfigSpec.IntValue HISTORY_TURNS_SENT;
    public static final ModConfigSpec.IntValue MIN_SECONDS_BETWEEN_REQUESTS;
    public static final ModConfigSpec.IntValue TIMEOUT_SECONDS;
    // performance
    public static final ModConfigSpec.IntValue UPDATE_INTERVAL_TICKS;
    // gameplay
    public static final ModConfigSpec.BooleanValue STARTER_KIT;
    public static final ModConfigSpec.BooleanValue YASSINE_COMBAT_KIT;
    public static final ModConfigSpec.IntValue ATTACK_SEARCH_RADIUS;
    // spawning
    public static final ModConfigSpec.BooleanValue NATURAL_SPAWN;
    public static final ModConfigSpec.IntValue MAX_POPULATION;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue SPAWN_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue SPAWN_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue YASSINE_AUTO_SPAWN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("ai");
        AI_ENABLED = b.comment("Allow cloud/local AI. If false (or if nothing is configured / reachable) the offline command system is used.")
                .define("enabled", true);
        PROVIDER = b.comment("auto = OpenRouter if its key exists, else OpenAI if its key exists, else offline. Or force: openrouter | openai | ollama.",
                        "(anthropic is NOT implemented.)")
                .define("provider", "auto");
        OPENROUTER_MODEL = b.comment("OpenRouter model id, e.g. openai/gpt-4o-mini, anthropic/claude-3.5-haiku, google/gemini-flash-1.5")
                .define("openrouterModel", "openai/gpt-4o-mini");
        OPENROUTER_BASE_URL = b.define("openrouterBaseUrl", "https://openrouter.ai/api/v1");
        MODEL = b.comment("OpenAI model name (used when provider = openai).").define("model", "gpt-4o-mini");
        BASE_URL = b.comment("OpenAI-compatible base URL (no trailing slash).").define("baseUrl", "https://api.openai.com/v1");
        OLLAMA_MODEL = b.comment("Local Ollama model (provider = ollama). Needs Ollama running on this PC.")
                .define("ollamaModel", "llama3.1");
        OLLAMA_BASE_URL = b.define("ollamaBaseUrl", "http://localhost:11434/v1");
        MAX_TOKENS = b.comment("Max tokens per reply (cost control).").defineInRange("maxTokens", 250, 50, 2000);
        HISTORY_TURNS_SENT = b.comment("How many recent chat turns are sent with each request (cost control).")
                .defineInRange("historyTurnsSent", 10, 0, 40);
        MIN_SECONDS_BETWEEN_REQUESTS = b.comment("Minimum seconds between AI requests for one NPC.")
                .defineInRange("minSecondsBetweenRequests", 2, 0, 60);
        TIMEOUT_SECONDS = b.defineInRange("timeoutSeconds", 30, 5, 120);
        b.pop();

        b.push("performance");
        UPDATE_INTERVAL_TICKS = b.comment("How often (ticks) NPCs re-evaluate equipment. 20 ticks = 1 second.")
                .defineInRange("updateIntervalTicks", 40, 10, 400);
        b.pop();

        b.push("gameplay");
        STARTER_KIT = b.comment("Give newly spawned NPCs a small starter kit (bread, basic tools). false = start with nothing.")
                .define("starterKit", true);
        YASSINE_COMBAT_KIT = b.comment("Give Yassine (once) a Mace, Wind Charges and a Shield. These cannot be crafted from scratch, so he starts with them.")
                .define("yassineCombatKit", true);
        ATTACK_SEARCH_RADIUS = b.comment("How far (blocks) an NPC searches for mobs when told to attack.")
                .defineInRange("attackSearchRadius", 32, 8, 64);
        b.pop();

        b.push("spawning");
        NATURAL_SPAWN = b.comment("Random NPCs appear on their own near players.").define("naturalSpawn", true);
        MAX_POPULATION = b.comment("Max number of LOADED random NPCs at once (Yassine not counted).")
                .defineInRange("maxPopulation", 6, 0, 50);
        SPAWN_INTERVAL_SECONDS = b.comment("Seconds between spawn attempts.").defineInRange("spawnIntervalSeconds", 120, 10, 3600);
        SPAWN_MIN_DISTANCE = b.defineInRange("spawnMinDistance", 24, 8, 128);
        SPAWN_MAX_DISTANCE = b.defineInRange("spawnMaxDistance", 48, 16, 160);
        YASSINE_AUTO_SPAWN = b.comment("Spawn Yassine near the first player who joins, exactly once per world.")
                .define("yassineAutoSpawnOnce", true);
        b.pop();
        SPEC = b.build();
    }

    private RavexConfig() {}
}
