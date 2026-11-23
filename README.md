# RAVEX AI Civilization

Minecraft Java **1.21.1**, **NeoForge**, Java 21, singleplayer. Text-chat-only AI NPCs (no microphone/voice).

> **Status: source project, NOT yet compiled or tested in Minecraft.** See `DEVELOPMENT_STATUS.md` for exactly what is implemented vs verified.

## Build in the cloud (nothing to install)
1. On github.com click **+ > New repository**, create it.
2. **uploading an existing file**: unzip this project on your PC and drag ALL its contents (including the hidden `.github` folder) into the page, **Commit changes**. (If the hidden folder is awkward: **Add file > Create new file**, type `.github/workflows/build.yml`, paste the contents of that file; repeat for `release.yml`.)
3. Open the **Actions** tab > latest "Build RAVEX AI Civilization" run.
4. Green = download the **ravex-ai-civilization-jar** artifact. Red = download **build-logs** and send the errors to Claude.

## AI modes (automatic)
- **Online**: if a key exists and the internet works, NPCs use the cloud AI.
- **Offline**: otherwise (no key, no internet, key rejected, rate limit) NPCs silently use the built-in command system. The game stays fully playable; offline replies are prefixed `[offline]`. After a failure the provider is paused (30 s - 10 min depending on the error) and retried automatically.

### In-game settings screen
Main menu or pause menu > **Mods > RAVEX AI Civilization > Config**: provider, masked API-key field (with Show/Hide), model ID, Test connection, Remove saved key, Save, Apply & close, and a live ONLINE/OFFLINE status. The key is saved only to `config/ravex_civ_openrouter_key.txt` (or `..._openai_key.txt`) in your Minecraft folder, is never shown again, never logged, never sent to chat or saved in the world. `Test connection` calls the provider's key-check endpoint and sends no conversation. The `.gitignore` excludes these files.

### OpenRouter key (recommended, manual alternative)
Provide ONE of these (never put it in the config, source or GitHub):
1. Environment variable `RAVEX_OPENROUTER_API_KEY` (or `OPENROUTER_API_KEY`) before starting Minecraft; or
2. A file `config/ravex_civ_openrouter_key.txt` in your Minecraft folder containing only the key on line 1.

Choose the model in `config/ravex_civ-common.toml` -> `openrouterModel` (e.g. `openai/gpt-4o-mini`).
Other providers: `openai` (key file `config/ravex_civ_openai_key.txt`), `ollama` (local, no key; needs Ollama running). `provider = "auto"` picks OpenRouter, then OpenAI, then offline. Anthropic direct is not implemented (use it through OpenRouter).

## Talking to NPCs
Say an NPC's name in normal chat (`Yassine, collect iron`, `ياسين تعال`, `Adam, suis-moi`) or use `/npc Yassine hello` (Arabic names in quotes). `/npc list` shows status. `/npc release_yassine` (op) clears Yassine's location lock if he is lost.

Examples that work offline: `Collect 32 oak logs`, `Bring me 20 birch logs`, `Collect 10 diamonds`, `Go caving`, `Attack zombies`, `Attack the nearest zombie`, `Attack that mob` (look at it), `Come back and give me the resources`. Wood/ore jobs end with the NPC walking to you and handing the items over (full inventory: the rest is dropped at your feet).

Offline commands (EN/FR/AR, many wordings): follow, stop following, stay here, come here/return to me, return home, set home ("this is your home"), guard this location/protect this area, protect me/defend me, attack mobs / zombies / skeletons / creepers / spiders, stop attacking, help <NPC> fight, follow <NPC>, join/leave my team, collect wood / stone / coal / iron / gold / diamonds / ores, go mining / explore caves.

Mining needs a suitable pickaxe in the NPC's inventory (NPCs cannot craft yet) and says honestly if it lacks one.

## Spawning
- Creative tab "RAVEX AI Civilization": **Yassine Spawn Egg** and **Random NPC Spawn Egg** (both still available).
- Random NPCs also appear on their own near players (config: `spawning.*` - on/off, max population, interval, distances).
- Yassine appears next to you once, the first time you join a world (`yassineAutoSpawnOnce`; set false to disable). He never respawns automatically; use his egg if needed. Only one Yassine can exist.

## Yassine's skin
Replace `src/main/resources/assets/ravex_civ/textures/entity/yassine.png` with your own 64x64 skin (current one is a generated placeholder).
