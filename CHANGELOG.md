# Changelog

## 0.1.0 (Milestone 1, unreleased - not yet compiled)
- NeoForge 1.21.1 project, Yassine + Random NPC spawn eggs, chat/`/npc` commands, OpenAI provider (async), offline parser, persistent memory, follow/stop/protect/collect_wood missions, basic melee combat.
- GitHub Actions: build.yml (clean build, JAR/source/log artifacts) and release.yml (manual release, publishes only after a successful build).

## 0.2.0 (not yet compiled)
- OpenRouter + Ollama providers (OpenAI-compatible client), `auto` provider selection, automatic quiet fallback to offline with timed retry.
- Natural random NPC spawning with population cap; Yassine one-time first-join spawn (config); both eggs kept.
- Offline interpreter rewritten: many EN/FR/AR phrasings for movement, combat, gathering, teams.
- New real behaviours: attack mobs, guard/stay, come, return home + store items in chest/barrel, set home, follow/help another NPC, mine stone/coal/iron/gold/diamond/ores (stair/tunnel digging, tool + durability rules).
- Shield blocking for all NPCs; Yassine-only Mace + Wind Charge smash (experimental).

## 0.3.0 (M3, not yet compiled)
- Yassine: Elytra + Firework flight (takeoff jump, vanilla glide, rocket boosts with rationing, obstacle look-ahead, dive-smash with the Mace, re-deploy before ground, flattened landing). Random NPCs unchanged.
- In-game config screen (Mods menu): provider, masked key field, model, test connection, remove key, save/apply, status. Key stored locally only.
- Wood: wood types and quantities, then real delivery to the player (overflow dropped at feet, nothing deleted).
- Targeted attacks: zombie/skeleton/creeper/spider/enderman/witch/slime, nearest, "that mob" (look target), configurable search radius, "not found" reporting. Separate from protect/guard.
- Caving/mining: line-of-sight only (no X-ray), real cave exploration via pathfinding, strip-mine fallback, ore priority, tool/durability/health/inventory exits, delivery.
- Offline parser: quantities (incl. stacks), wood types, ore types, mob types, bring/give/deliver, EN/FR/AR.
