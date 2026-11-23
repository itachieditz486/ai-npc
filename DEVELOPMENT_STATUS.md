# DEVELOPMENT STATUS

## Milestone 1 - authored 2026-11-22

### Build and test results (honest)
- **Compiled: NO.** The authoring environment had a JRE only (no `javac`, no Gradle) and no network, so NeoForge/Minecraft could not be downloaded.
- **Run in Minecraft: NO.** Nothing here has been executed.
- **Checks actually done:** brace/paren balance and project-internal imports verified by script on all 25 Java files. Minecraft/NeoForge API calls were written from knowledge of 1.21.1 and are **unverified** - expect a few compile errors on first build (most likely: exact NeoForge/ModDevGradle versions, a method name or signature).
- No JAR is included because none could be built.

### Written (untested) in this milestone
- NeoForge 1.21.1 project, Gradle (ModDevGradle), mods.toml, lang, models.
- `NpcEntity` (humanoid, player-style renderer + armor layer), 7 generated placeholder skins (Yassine + 6 random), item textures.
- Two spawn items: Yassine (unique; refused if one exists; duplicate guard on entity join) and Random NPC (random name from pool, skin, personality, small starter kit).
- Chat: name mention detection (whole-word; English/French/Arabic spellings), `/npc <name> <msg>`, `/npc list`, `/npc release_yassine`.
- Real LLM: OpenAI chat-completions, async (`HttpClient.sendAsync`), result applied on the server thread; JSON reply `{reply, action, remember}`; cost controls (token cap, history window, per-NPC request spacing). Provider interface for future Anthropic/Ollama.
- Offline fallback keyword parser (labelled `[offline]`).
- Persistent memory: per-NPC identity + conversation history + facts in world data; Yassine also mirrored to `<world>/ravex_civ/yassine_memory.json` and restored from it if world data lacks it.
- Missions: follow, stop, protect (stay near player; fights monsters), collect_wood (walk to a log, mine for a tool-speed-based time, real drops into inventory).
- Basic combat: melee, auto-equip sword/axe and armor from inventory (items are moved, not duplicated), retaliates when hurt, attacks nearby monsters (not creepers/endermen/wardens).

### NOT implemented yet (do not treat as working)
- Mining ore, crafting, furnaces/chests, cooking, farming, hunting, house building, shelters, returning home/storing items.
- Hunger management, bows/crossbows/shields, Yassine's Mace / Wind Charge / Elytra+Firework combat.
- NPC-to-NPC communication, teams, relationships.
- Nether/End progression.
- Management GUI (only `/npc list` text for now).
- Anthropic and Ollama providers.
- Parts of collect_wood: no tree-top access, no leaf clearing, no stuck recovery beyond skipping a log.

### Known risks / bugs (suspected, unverified)
- Hostile mobs only target NPCs if they are hurt first or the NPC attacks them; NPCs are not yet targeted proactively.
- Pathing to logs uses vanilla navigation; trunks surrounded by leaves may be skipped.
- Conversation memory beyond `historyTurnsSent` turns is only kept via `remember` facts (no summarisation yet).

### Exact next steps
1. Build it (IntelliJ import or `gradle wrapper && ./gradlew build`), fix compile errors, then `runClient`.
2. Verify: both eggs spawn, Yassine uniqueness, skins render, chat triggers, offline commands, collect_wood, save/reload memory.
3. Milestone 2: chests + crafting table/furnace use, crafting tools, return-home and store items, hunger and food.
4. Milestone 3: NPC-to-NPC messaging, teams, GUI screen.
5. Milestone 4: bow/shield combat; Yassine Mace + Wind Charge; then building, Nether, End.

## Update (same day) - cloud build added
- Added `.github/workflows/build.yml` (Java 21 Temurin, Gradle 8.10.2 via setup-gradle, `gradle clean build`, uploads JAR / source ZIP / logs, failure summary). **The workflow itself has not been run yet**, and no GitHub repository access was available to me.
- Still NOT done from the full feature request: everything under "NOT implemented yet" above. The project is Milestone 1 plus CI, not the complete mod. No JAR exists until the first Actions run succeeds.

## Update - release workflow + changelog
- Added `.github/workflows/release.yml` and `CHANGELOG.md`. Neither workflow has run yet. Feature status is unchanged from Milestone 1.

## Milestone 2 (this update) - status legend: IMPL = written, COMPILED = no, TESTED IN MC = no for everything
Nothing below has been compiled or run. The Gradle/GitHub build has NOT been run by me (no GitHub access, no compiler here).
Checks actually done: bracket/import script on all Java files; the offline command phrases (55 samples EN/FR/AR) were run through the same regex order in a Python simulation (all pass after one fix) - this verifies parser logic only, not the Java code.

### Implemented (untested)
- Hybrid AI: OpenRouter, OpenAI, Ollama through one OpenAI-compatible client; `auto` mode; keys only from env/file (never logged, chat or world); failures pause the provider and NPCs use offline mode with no error spam.
- Natural spawning (cap counts LOADED random NPCs only; Yassine excluded); Yassine never in the random pool; one-time first-join spawn flag stored in world data; duplicate guard kept.
- Missions with real world effects: follow, stay, come, guard, protect, attack mobs (filter by zombie/skeleton/creeper/spider), help NPC fight, follow NPC, return home + chest/barrel storage, set home, collect wood, collect stone/coal/iron/gold/diamond/ores (only exposed blocks are targeted; tunnelling/stairs when needed; correct-tool rule: no drops without right tool; durability used), join/leave team (stored label only).
- Shield blocking (all NPCs). Yassine: Mace + Wind Charge smash with vanilla fall-bonus formula, wind charge consumed from inventory.
- Yassine starts (once) with Mace, 16 Wind Charges, Shield, stone tools (configurable) because these cannot be crafted from scratch.

### NOT implemented / known gaps
- Elytra flight and Firework boosts (Yassine). Bows/crossbows, retreat behaviour, PvP strafing.
- Crafting, furnaces/smelting, cooking, farming, hunger, building/houses, GUI, NPC-to-NPC chat, real team logic (teams are only a label; no shared tasks/protection), Nether/End.
- Profession is flavour text only. Population cap ignores NPCs in unloaded chunks.
- Anthropic-direct provider.

### Risks to check first when it builds
- API guesses most likely to need fixing: `WindCharge` constructor, `HopperBlockEntity.addItem(null,...)`, `ItemStack.isCorrectToolForDrops`, `hurtAndBreak` signature, `ServerPlayer.serverLevel()`, `ServerTickEvent.Post`, `PlayerEvent.PlayerLoggedInEvent`, `AttributeModifier` constructor.
- Digging safety is minimal (no lava-above/falling-gravel handling); mining trips are capped at 6000 ticks.

### Exact next steps
1. Push to GitHub, read the Actions result, fix compile errors.
2. Test in Minecraft: spawn both eggs; first-join Yassine; offline commands without any key; OpenRouter with a key and with the key wrong (should fall back silently); mining trip; return home with a chest; Yassine smash.
3. Then: crafting + furnace + cooking, hunger, farming; then teams/GUI; bows; Elytra.

## Milestone 3 - status (NOTHING compiled, NOTHING tested in Minecraft; no Gradle/GitHub build was run by me)
Checks done: bracket/import script over 35 Java files; 56 offline command phrases (EN/FR/AR, quantities, wood types, ores, mobs) run through the same regex order in a Python simulation - 0 mismatches. This verifies parsing logic only.

| Feature | Implemented | Compiled | Tested in MC |
|---|---|---|---|
| Yassine Elytra + Firework flight, dive-smash, landing | yes | no | no |
| Ground Mace + Wind Charge smash (from M2) | yes | no | no |
| In-game OpenRouter/AI config screen | yes | no | no |
| Key stored locally only, masked field, test without chat data | yes | no | no |
| Wood by type + quantity + delivery to player | yes | no | no |
| Targeted attacks / nearest / that mob / radius config | yes | no | no |
| Caving + ore mining without X-ray, pathfound cave exploration | yes | no | no |
| Offline parser expansion | yes (logic simulated in Python) | no | no |

### Honest limitations
- Flight: never run. Elytra physics rely on vanilla `LivingEntity` fall-flying code working for a mob; if a mob cannot glide in 1.21.1 as assumed, the flight will fail and he will just walk. Wind Charge constructor, FireworkRocketEntity constructor and the `travel` override are the riskiest APIs. Dive-smash fall damage mitigation (re-deploy) is untested. Yassine swaps his chestplate for the Elytra.
- Wood: very tall trees (e.g. spruce) leave unreachable upper logs (no pillaring); leaves are not cleared; the NPC reports what it actually collected.
- Mining: no torches, no lava-above or gravel handling, cave scan is a heuristic (it picks pathfindable underground spots); the pathfinder limit (~32 blocks) can make long cave routes fail. Mining trips cap at 9000 ticks.
- Delivery: needs the player in the same dimension and reachable; otherwise the NPC keeps the items and says so.
- Attack that mob: only hostile (Enemy) mobs; attacking creepers/endermen is allowed only when explicitly requested and may be dangerous.
- Still NOT implemented: crafting, smelting, cooking, farming, hunger, building, GUI for NPC management, real teams logic, NPC-to-NPC chat, bows/crossbows, Nether/End, Anthropic-direct provider.
- The config screen's `IConfigScreenFactory` lambda and `ConfigValue.save()` follow NeoForge 21.1 as I know it; verify on the first build.

### Next steps
1. Push to GitHub, read Actions result, send me `build-logs` if red.
2. Test in Minecraft in this order: spawn eggs, offline commands with no key, config screen (key hidden, test), collect oak logs and delivery, attack zombies, caving, then Yassine flight.
