# JUDGE Mod — Full Codebase Audit

**Date:** 2026-07-18
**Scope:** All Java sources, datapack rules (`judge_rules/default.json`), resources, network layer, build configuration.
**Target:** Forge 1.20.1 (47.4.16), mod version 0.0.2

Severity legend:
- **CRITICAL** — broken feature, exploit, or crash risk that affects gameplay right now
- **HIGH** — wrong behavior in common situations, security/multiplayer integrity, data corruption over time
- **MEDIUM** — incorrect edge-case behavior, maintainability traps, doc/code drift
- **LOW** — polish, dead code, minor waste
- **HYGIENE** — build/release/project management

---

## 1. CRITICAL

### C1. Trigger-ID mismatch makes three rules permanently dead
The event handler fires trigger strings that don't match the `trigger` field in the JSON rules, so the rules can never match:

| Rule (default.json) | JSON `trigger` | What the code actually fires |
|---|---|---|
| `hit_villager` (line 61) | `"hit_entity"` | `"hit_villager"` — `JudgeEventHandler.java:222` |
| `attack_golem` (line 58) | `"hit_entity"` | never fired at all (only villagers are checked in `onAttackEntity`) |
| `break_workstation` (line 60) | `"break_block"` | `"break_workstation"` — `JudgeEventHandler.java:276` |

**Effect:** punching villagers, attacking iron golems, and breaking village workstations currently cost **nothing**.
**Fix:** pick one naming convention. Either fire `"hit_entity"` from `onAttackEntity` (and pass the target entity so `target:` matching works, which also revives `attack_golem`), and fire `"break_block"` from `onBlockBreak` — or change the JSON triggers. Note that `#minecraft:job_site_blocks` is a **block** tag but `applyRule`'s target matcher only understands **entity** tags; block-break rules need their own matching path.

### C2. `kill_player` rule is unreachable
`JudgeEventHandler.onLivingDeath` (`JudgeEventHandler.java:131-144`) uses `if (victim is player) {...} else if (killer is player) {...}`. When a player kills another player, the first branch consumes the event (victim sync) and the killer branch never runs. The `kill_player` rule (-400, default.json line 63) can never fire.
**Fix:** make the two branches independent (`if` / `if`), guarding against self-kill.

### C3. `ignore_spawners` is never implemented
`onMobSpawn` tags spawner mobs with `"FromSpawner"` (`JudgeEventHandler.java:66-71`) and the parser reads `ignore_spawners` (`JudgeDataManager.java:141`), but `applyRule` never checks either. The README explicitly advertises spawner anti-cheese.
**Effect:** AFK spawner farms print free score up to the `kill_hostile` cap, every 10 minutes, forever.
**Fix:** in `applyRule`, before applying: `if (rule.ignoreSpawners && target != null && target.getTags().contains("FromSpawner")) return;`

### C4. Infinite score exploit: threshold Hero of the Village triggers `win_raid`
`applyThresholdEffects` grants `minecraft:hero_of_the_village` at score ≥ 2500 (default.json line 102). `onEffectAdded` (`JudgeEventHandler.java:147-159`) treats any *new* Hero of the Village effect as a raid victory (+1200). The `getOldEffectInstance` guard only blocks refreshes — the very first application from the threshold system still awards +1200.
**Effect:** reach 2500 → free +1200. Drop below 2500 (10-second effect expires), climb back over → +1200 again, repeatable indefinitely. It also means a *real* raid win while already above 2500 gives nothing (old instance present).
**Fix options (do at least one):**
- Detect raid wins properly: check `player.level().getRaidAt(pos)` victory state, or listen for the raid's victory processing, instead of inferring from the effect.
- At minimum, tag/mark effect instances applied by `applyThresholdEffects` (e.g., check the effect's duration/ambient signature, or set a transient "applying threshold effect" flag and skip `onEffectAdded` while set).
- Also: pick a non-gameplay-affecting reward effect for the 2500 tier — Hero of the Village changes villager prices, which compounds with `trade_villager` combo scoring.

### C5. Item-use gates are bypassable for "use on block / use on entity" items
`onRightClickItem` only hooks `PlayerInteractEvent.RightClickItem`, which fires for the *use-in-air* path. Items that act through `useOn` (block) or entity interaction never hit it:
- `minecraft:minecart` (placed on rails) — **gate fully bypassed**
- `minecraft:end_crystal` (placed on obsidian/bedrock) — **gate fully bypassed**
- `minecraft:lead`, `minecraft:name_tag` (used on entities) — **gate fully bypassed**
- `minecraft:firework_rocket` when used on the ground (crossbow loading is a separate path too)

**Fix:** also check the held item's gate in `PlayerInteractEvent.RightClickBlock` and `PlayerInteractEvent.EntityInteract` / `EntityInteractSpecific`, cancelling with the same message.

### C6. Action-history pruning (1 hour) silently breaks caps longer than 60 minutes
`JudgeData.recordAction` (`JudgeData.java:109-110`) deletes timestamps older than 1 hour, but `trade_villager` has `cap_duration_minutes: 1440` (24 h, cap 100). After an hour the evidence is gone, so the cap resets.
**Effect:** the daily trade cap is actually a *per-hour* cap of 100 — 24× more generous than configured.
**Fix:** prune per-key using the *longest* window any rule references for that key (compute max window at rule load), or simply prune at `max(1h, largest cap_duration across rules)`.

### C7. Datapack value of `decay_minutes_per_stack: 0` crashes the server
`JudgeEventHandler.java:177` computes `timeSinceLastDeath % (fcConfig.decayMinutes * 60 * 20)`. If a datapack sets `decay_minutes_per_stack` to 0, that's a modulo-by-zero `ArithmeticException` thrown **every player tick** — a hard server crash loop.
**Fix:** clamp all parsed config values to sane minimums in `parseFcConfig` (and validate `windowMinutes`, `triggerDeaths`, `maxStacks` ≥ 0 while you're there). Same defensive posture belongs in `parseLevelCost` (negative Q/A/B produce negative or shrinking level costs).

---

## 2. HIGH

### H1. `PacketTribunalAction` has no server-side context validation
`PacketTribunalAction.handle` (`PacketTribunalAction.java:35-92`) only checks `player != null`. A modified client can send BUY_LEVEL / CLEANSE packets at any time, from anywhere, without a Tribunal Stone in the world at all. Cost is still enforced, so it's not free levels, but it bypasses the intended "must visit a Tribunal Stone" mechanic entirely.
**Fix:** require `player.containerMenu instanceof TribunalMenu menu && menu.stillValid(player)` before processing.

### H2. Tribunal cost display is wrong on dedicated servers with custom `level_cost`
`TribunalMenu.updateData` (`TribunalMenu.java:47-48`) recomputes cost **client-side** from `JudgeDataManager.getLevelCostConfig()`. Judge rules load via `AddReloadListenerEvent` — a *server-side* datapack listener — so on a dedicated server the client's static config still holds defaults (B=100, A=25, Q=6). The initial menu-open value is correct (computed server-side into the extra buf), but the first `PacketUpdateTribunalMenu` after buying a level replaces it with a default-formula value.
**Fix:** compute the cost on the server and include it in `PacketUpdateTribunalMenu` (add a 4th int), and delete the client-side formula.

### H3. `lootedChests` is dimension-blind, unbounded, and punishes the player's own chests
Multiple problems in one structure (`JudgeData.java:31`, used at `JudgeEventHandler.java:323-341`):
1. **No dimension component.** A chest at (100, 64, 100) in the Nether is "already looted" if you opened one at those coordinates in the Overworld.
2. **Never pruned, survives death** (`copyFrom` line 179 carries it forward). Every chest a player ever opens is stored in NBT forever — permanent per-player save bloat on long-lived servers.
3. **The -200 `loot_village_chest` penalty fires for *any* chest inside village bounds — including chests the player placed themselves.** Building a base near a village makes your own storage cost 200 points per chest.
4. Only `minecraft:chest` counts — trapped chests and barrels (the actual village loot container in many structures) are ignored.
5. Fires on right-click even if the chest never opens (blocked by a cat or a solid block above).

**Fix:** key by dimension + pos; only penalize *naturally generated loot* (check for a `LootTable` tag on the `ChestBlockEntity` — generated, unopened loot chests have it; player-placed chests never do). That check also removes the need to store opened positions at all for the penalty case, killing the NBT bloat.

### H4. Dimension entry reward pays out for returning home, and before travel actually succeeds
`onDimensionTravel` (`JudgeEventHandler.java:113-128`):
- The starting dimension is never marked as entered, so first Nether trip = +250, and the return trip to the Overworld = **another +250**. Every new dimension round-trips into double rewards.
- The reward and the `setEnteredDimensionThisLife` mark are applied when the event fires, not when travel completes. Another mod cancelling the event later still leaves the +250 applied.
**Fix:** mark the player's current dimension as "entered" on login/respawn; award in `PlayerChangedDimensionEvent` (post-travel) instead of the pre-travel event, keeping the gate check where it is.

### H5. All timing uses wall-clock `System.currentTimeMillis()`
Combos, cooldowns, caps, death windows, popups (`JudgeData`, `applyRule`, `onDamage`). Consequences:
- Pausing single-player still runs the clock: combos expire, cooldowns tick, the FC death window drains while the game is frozen.
- Timestamps persist in NBT, so a server restart or host clock change shifts everything.
- Logging out for 5 minutes clears the Fractured Composure death window even though no game time passed.
**Fix:** use `level.getGameTime()` (ticks) for all gameplay timing. Keep millis only for the client-side popup animations and the `/judge history` display.

### H6. Killing a tamed animal stacks `kill_pet` + `kill_passive` (-5050)
`applyRule` applies **every** matching rule (`forEach`, `JudgeEventHandler.java:348`). A tamed wolf is both `is_tamed` (kill_pet, -5000) and `#minecraft:animals` (kill_passive, -50). Probably unintended; if more overlapping rules are added later this compounds silently.
**Fix:** add a rule `priority` field and stop at first match per trigger, or an `exclusive` flag — decide the semantics explicitly and document it.

### H7. Client-only mixin declared in the common mixin list
`judgemod.mixins.json` puts `DeathScreenMixin` (targets the client-only `DeathScreen` class) in the `"mixins"` array. On a dedicated server the target class doesn't exist. It usually degrades to a load-time warning, but it's incorrect and version-fragile.
**Fix:** move it to a `"client": ["DeathScreenMixin"]` array.

### H8. Spectator mode is not excluded anywhere
Every handler checks `isCreative()` but never `isSpectator()`. Spectators get blocked by dimension gates (`onDimensionTravel` cancels their travel), tick FC decay messages, and can trip threshold effects.
**Fix:** replace the creative checks with a single helper, e.g. `private static boolean exempt(Player p) { return p.isCreative() || p.isSpectator(); }`.

### H9. `TribunalMenu` client constructor can crash the client
`TribunalMenu.java:24-30`:
- `inv.player.level().getBlockEntity(...)` can return null (chunk not synced yet) → NPE at `entity.getLevel()`, or ClassCastException if a different BE sits there.
- On the server, `TribunalStoneBlock.use` writes the 4 ints **inside** `ifPresent` (`TribunalStoneBlock.java:45-54`). If the capability is ever absent, the client reads 4 ints from an empty buffer → decoder exception → disconnect.
**Fix:** write the ints unconditionally (with fallback zeros) on the server; null/type-check the BE on the client.

### H10. Network channel accepts absent/any versions on both sides
`PacketHandler.register` uses `clientAcceptedVersions(s -> true)` / `serverAcceptedVersions(s -> true)` (`PacketHandler.java:22-23`). Vanilla or un-modded clients can join a server running JUDGE — they'll see none of the gates client-side and desync from cancelled interactions; and the whole mod is server-authoritative gameplay, so both sides genuinely require it.
**Fix:** use `NetworkRegistry.newSimpleChannel` with a strict version predicate (the standard `PROTOCOL_VERSION::equals` pattern).

---

## 3. MEDIUM

### M1. `ClientEventHandler` is registered to the Forge bus twice
Once via `@Mod.EventBusSubscriber(value = Dist.CLIENT)` (`ClientEventHandler.java:21`) and again manually in `ClientSetup.init` (`ClientSetup.java:16`). Modern EventBus dedupes class re-registration, so this is currently harmless, but it's a trap — pick one (drop the manual `register` call).

### M2. Death-screen mixin matches the literal string `"Score:"`
`DeathScreenMixin.judgemod$hideVanillaScore` hides any centered string starting with `"Score:"` — breaks in every non-English locale (the vanilla component is `deathScreen.score` translated). The injected y-positions (115/100) are also hardcoded guesses.
**Fix:** compare against the translation key/contents of the actual score component (`Component` equality with the reconstructed vanilla component), or target the specific `drawCenteredString` call ordinal.

### M3. Client static state never resets
`clientLifeScore` / `clientJudgeLevel` / `clientLastDeathLifeScore` (`ClientEventHandler.java:23-25`) persist across disconnects. Joining a second world briefly shows the previous world's numbers until the login sync lands.
**Fix:** clear them in `ClientPlayerNetworkEvent.LoggingOut`.

### M4. `copyFrom` shares mutable list references
`JudgeData.copyFrom` (`JudgeData.java:181`) does `actionHistory.putAll(other.actionHistory)` — the *same* `List<Long>` objects end up referenced by both capabilities. The old instance is discarded so nothing breaks today, but any future code holding both will corrupt silently. Deep-copy the lists.

### M5. `getActionTimestamps` leaks the internal list
`JudgeData.java:121-123` returns the live internal list when present (a fresh list only on miss). Callers currently only read, but one accidental `.add()`/`.clear()` corrupts history. Return `List.copyOf(...)` or an unmodifiable view.

### M6. Command gaps
`JudgeCommands.java`:
- `/judge set score` doesn't call `player.setScore(...)` — the vanilla score diverges until the next tick fixes it (cosmetic, but inconsistent with every other mutation site).
- `/judge get history` dumps up to 100 chat lines, oldest first. Paginate (`/judge get <p> history [page]`) and show newest first.
- Missing conveniences: `/judge add score <n>`, `/judge get/set composure`, `/judge reset`.
- Handlers return 1 even when the capability is absent; return 0 / send failure so command blocks can react.

### M7. `per_heart_penalty` actually charges per *full* heart
`onDamage` computes `event.getAmount() / 2.0f * rule.perHeartPenalty` (`JudgeEventHandler.java:203`). Damage amount is measured in half-hearts, so `/2` converts to full hearts. The README (line 79) says "per half-heart of damage." Code and doc disagree — fix whichever is wrong (JSON balance values look tuned for per-heart, so probably fix the README and rename the field).

### M8. README / JSON / code triangle drift
- README lists trigger `advancement`; real triggers are `advancement_task|goal|challenge`.
- README documents an `is_negative` field for threshold tiers; the parser ignores it and derives sign from `threshold < 0` (`JudgeDataManager.java:166`). A tier like "debuff while below +100" is impossible; either implement the field (respect it when present) or remove it from docs.
- README says spawner mobs give 0 score (`ignore_spawners`) — not implemented (C3).
- JSON uses trigger `hit_entity` which no code fires (C1); README doesn't mention it.
- README documents a `loot_chest` trigger; the code fires it but no default rule exists — fine, but the code still records the chest position for nothing (ties into H3).

### M9. `player_not_hungry` condition is unused and misleadingly named
`applyRule` skips the rule when `foodLevel < 20` — i.e. the rule only applies at **full** hunger (`JudgeEventHandler.java:364`). No default rule uses it. Rename to `player_food_full` or invert the logic to match the name before someone uses it and gets the opposite behavior.

### M10. `cure_villager` credits an arbitrary nearby player
`onEntityConversion` picks the first non-creative player within 16 blocks (`JudgeEventHandler.java:298`). The wrong player can earn +600, and the actual curer gets nothing if they stepped away. `ZombieVillager` stores the *conversion starter* UUID (the player who fed the golden apple) — use `getConversionStarter()`-equivalent data to credit correctly, falling back to proximity.

### M11. Tick-damage mistakes have no default cooldown
`lava` (per-heart 14), `inWall`, `drown`, `freeze`, `starve`, `fall` have no `cooldown_seconds` in default.json. Lava damages every 10 ticks → roughly -17 score twice per second while in lava, on top of Fractured Composure multipliers. That reads as punishing lag/one mistake many times, not "one mistake." Give the periodic damage types 1–3 s cooldowns like `inFire`/`onFire` already have.

### M12. Threshold effect stacking is undocumented behavior
At 9000+ score a player holds luck (×2 tiers), Hero of the Village, speed I and II simultaneously (`applyThresholdEffects` applies every qualifying tier; higher amplifier wins per effect). If "all tiers apply cumulatively" is intended, document it; otherwise apply only the highest qualifying tier per effect id. Reapplication every second also makes the effects un-milk-able — intended, but worth a README note.

### M13. Creative mode freezes the survival timers
`onPlayerTick` early-returns for creative (`JudgeEventHandler.java:164`), so `timeSinceLastDeath` stops: FC stacks stop decaying and `survive_time` progress pauses while in creative. Probably fine for FC, but an admin flipping to creative briefly shouldn't stall a player-facing recovery timer. Consider still ticking the clock and only skipping the *rewards*.

### M14. FakePlayers (mod machinery) get full Judge treatment
Capabilities attach to any `Player`, and `LivingDeathEvent` credits `event.getSource().getEntity() instanceof ServerPlayer` — mod turrets/deployers using FakePlayer earn/lose score and get gated. Add `if (player instanceof FakePlayer) return;` guards.

### M15. Gate-denial message spam
Standing in a Nether portal below the required level re-fires `EntityTravelToDimensionEvent` (and the action-bar message) every portal attempt tick. Same for holding right-click on a gated block. Throttle the message (e.g., once per second per player) — the cancel itself is fine.

### M16. `Config.java` is dead weight
Empty spec, empty `onLoad`, and the SPEC is never registered (`Config.java`). Either delete the file or move the balance knobs there. Note `@Mod.EventBusSubscriber` still scans it at runtime for nothing.

### M17. Recipes have no unlock advancements
Both recipes lack `advancement` criteria files, so they never appear in the recipe book and players can't discover them in-game. Add recipe advancements (or at least document the recipes in-game via a patchouli-style book later).

### M18. The entire mod is unlocalized
`en_us.json` defines 8 `message.judgemod.*` keys that are **never used** — every user-facing string is `Component.literal` with hardcoded English (`JudgeEventHandler`, `PacketTribunalAction`, `TribunalScreen`, HUD "Score:"/"Level:" labels). Switch to `Component.translatable(...)` and route everything through the lang file; delete unused keys or wire them up.

### M19. Tribunal screen UX
`TribunalScreen.java`:
- Buttons stay enabled when the player can't afford the action / has no stacks to cleanse — disable (`Button.active`) based on synced data.
- Cleansing with 0 stacks silently does nothing server-side (`PacketTribunalAction.java:55-75` has no else) — either disable the button or send feedback.
- The default `AbstractContainerScreen` title + "Inventory" labels render on top of the custom text layout — override `renderLabels` to position or suppress them.
- `RenderSystem.setShaderTexture` before `guiGraphics.blit(TEXTURE, ...)` is redundant (blit binds it).

### M20. Right-click handlers fire per hand
`PlayerInteractEvent.RightClickBlock` fires for main and off hand — the gate message can appear twice, and `onChestOpen` can be evaluated twice per click. Check `event.getHand() == InteractionHand.MAIN_HAND` where appropriate.

---

## 4. LOW

- **L1. Unused imports**: `RenderSystem`, `java.awt.Color` (`ClientEventHandler`); `Warden`, `Advancement` (`JudgeEventHandler`); `Minecraft`, `JudgeDataProvider` (`PacketSyncJudgeData`); `CommandSyntaxException` (`JudgeCommands`); `Direction` unused param convention fine.
- **L2. `actionHistory` keys are never removed** once their timestamp lists prune to empty — slow map growth per rule id / damage type. Trivial, but easy to clean during prune.
- **L3. `chronologicalHistory.remove(0)`** on an `ArrayList` is O(n) per action (`JudgeData.java:116`) — use `ArrayDeque` or a ring buffer.
- **L4. `TribunalStoneBlockEntity.tick` is empty** — return `null` from `getTicker` and delete the ticker plumbing to save per-block-entity tick overhead.
- **L5. `#minecraft:animals` / `#minecraft:monsters` entity-type tags don't exist in vanilla 1.20.1** — the tag lookup always misses and only the hardcoded `instanceof Animal` / `instanceof Enemy` fallbacks (`JudgeEventHandler.java:356-357`) do the work. Either ship real tags in the datapack (`data/judgemod/tags/entity_types/...` and reference `#judgemod:animals`) or drop the misleading tag syntax from default.json.
- **L6. `run` configs**: `gameTestServer` and `data` runs are missing the `-mixin.config` arg the client/server runs have.
- **L7. `org.gradle.daemon=false`** in gradle.properties makes every build cold-start; that's an MDK default worth removing on a dev machine.
- **L8. MDK leftovers**: `changelog.txt`, `CREDITS.txt` are stock Forge files; update or remove before release.
- **L9. Popup id collision**: `PacketJudgePopup` with `"level_up"` reuses `value` as the level number, while every other popup treats `value` as a score delta (`ClientEventHandler.java:102`). Works, but a dedicated packet field/flag would be cleaner than the string sentinel.

---

## 5. DESIGN / BALANCE OBSERVATIONS (not bugs)

- **Negative combo scaling is negligible at current numbers.** README promises quadratic punishment, but `-5000 - (combo-1)²` on kill_pet means the second pet costs -5001. If escalation is the intent, use a multiplier (`delta * combo`) for negative combos.
- **Combo state is a single slot** (`comboActionKey`): alternating two combo-enabled actions resets both chains. Fine if intended ("pure streaks only"), worth documenting.
- **`survive_time` interval is hardcoded** to 10 minutes (`JudgeEventHandler.java:172`) while everything else is data-driven. Expose it in JSON (the rule id `survive_10_min` already implies the coupling).
- **Level costs vs. gates sanity check**: reaching the Nether gate (level 15) costs Σ(B + A·L + Q·L²) for L=0..14 ≈ 12,000+ score under defaults, while the best repeatable income is capped (~100/10 min hostiles, 40/day sleep). Verify this curve against playtesting — it may be dramatically slower than intended.
- **`kill_pet` hits the owner's own pets and other players' equally** (-5000). If griefing others' pets should be worse than putting down your own, condition on ownership.
- **Vanilla score takeover**: `player.setScore` every tick works, but vanilla also increments score on XP gain, so there's a 1-tick flicker window. Consider only setting it on change events (you already do) and dropping the per-tick reconciliation once trust is established — or keep it, it's cheap.

---

## 6. PROJECT HYGIENE

### P1. Not a git repository
The project has `.gitignore`/`.gitattributes` but **no version control is active**. This is the single highest-leverage fix in this section: `git init`, commit, and push to GitHub (the folder layout suggests that was the plan). `.idea/workspace.xml` should be ignored once initialized.

### P2. `mods.toml` is out of sync with `gradle.properties` and not templated
`build.gradle` runs `processResources` with `expand` over `mods.toml` (build.gradle:76-89), but the file contains **no placeholders**, so:
- `version="1.0.0"` ships while `gradle.properties` says `mod_version=0.0.2`
- `license="All rights reserved"` vs `mod_license=MIT` vs `LICENSE.txt` containing the **Forge LGPL text** — three different licenses in one repo
- Restore the MDK placeholders (`version="${mod_version}"`, `license="${mod_license}"`, etc.) and replace LICENSE.txt with your actual chosen license.

### P3. `logoFile="logo.png"` but no logo.png exists in resources — mod-list logo silently missing.

### P4. Verify `issueTrackerURL`/`displayURL` (`github.com/camacraft/judgemod`, `camacraft.net`) point at real destinations before publishing.

---

## Suggested fix order

1. **C1, C2, C3** — trigger mismatches + spawner check (small diffs, restores advertised gameplay)
2. **C4** — raid detection rework (exploit)
3. **C7** — config clamping (crash)
4. **C5** — gate coverage in `RightClickBlock`/`EntityInteract`
5. **C6, H5** — move all timing to game ticks and size pruning to cap windows (one refactor solves both)
6. **H1, H10** — packet validation + strict channel version
7. **H3, H4** — chest loot-table detection, post-travel dimension rewards
8. **H2, H9** — tribunal menu sync/robustness
9. Everything in MEDIUM as a cleanup pass, then hygiene (P1 first — get it in git before touching anything).
