# Changelog

All notable changes to JUDGE are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/); versions follow [SemVer](https://semver.org/).
Item codes (C1, H3, M12, ...) reference the findings in [AUDIT.md](AUDIT.md).

## [0.0.4] — 2026-10-23

### Fixed
- **Worlds made by Multiworld are judged by what they are.** Dimension gates take Camafy's dimension entries (an id, `multiworld:*`, `type:<dimension type>`, `kind:<overworld|nether|end>`) and the defaults are `kind:nether` 15 and `kind:end` 35: a Multiworld copy of the Nether or the End was ungated. `enter_dimension` counts the vanilla three by kind (a copy of the Nether is the Nether), so hopping between Multiworld worlds no longer pays +250 each. The sleep reward follows the overworld's day: each Multiworld world keeps its own clock, and sleeping in several paid several times a night. Needs Camafy 0.1.45.

## [0.0.3] — 2026-07-18

Full remediation pass over the 2026-07-18 codebase audit. Every finding in AUDIT.md was addressed.

### Fixed — Critical
- **Dead rules from trigger mismatches (C1):** the event handler now fires `hit_entity` (with the target entity) for any entity attack and `break_block` (with block/tag matching) for block breaks, matching the rule JSON. `hit_villager`, `attack_golem`, and `break_workstation` penalties work again. Block-break rules get their own block-ID/block-tag matcher; `break_workstation` uses the new `in_village` condition instead of a hardcoded check.
- **`kill_player` was unreachable (C2):** victim sync and killer credit in `onLivingDeath` are now independent, so player kills score the killer (self-kills excluded).
- **`ignore_spawners` was never enforced (C3):** rules with `ignore_spawners: true` now skip mobs tagged as spawner-spawned. AFK spawner farms no longer print score.
- **Infinite score exploit via threshold Hero of the Village (C4):** effects applied by the threshold system are flagged and can no longer be mistaken for a raid victory (`win_raid` +1200 loop). See also the 2500-tier balance change below.
- **Item gates were bypassable (C5):** item gates now also cover the use-on-block path (minecarts, end crystals, fireworks — via `setUseItem(DENY)` so ungated block interaction still works) and entity interaction (`EntityInteract`/`EntityInteractSpecific` — leads, name tags).
- **Long caps silently reset (C6):** action-history retention is now computed from the longest configured cap window instead of a fixed 1 hour. The 24-hour `trade_villager` cap is actually enforced daily now.
- **Datapack-triggered crash (C7):** all `fractured_composure` and `level_cost` values are clamped at parse time. `decay_minutes_per_stack: 0` no longer causes a modulo-by-zero crash loop; level costs can't go negative, zero, or overflow (`getLevelCost` computes in long space, clamped to [1, MAX_INT]).

### Fixed — High
- **Tribunal packets validated server-side (H1):** `BUY_LEVEL`/`CLEANSE` are rejected unless the sender has a Tribunal menu open on a real, in-range Tribunal Stone.
- **Wrong cost display on dedicated servers (H2):** the next-level cost is now computed server-side and sent in `PacketUpdateTribunalMenu`; the client never recomputes it from (unsynced) config.
- **Village chest penalty overhaul (H3):** only naturally generated, still-unopened loot containers (pending `LootTable` tag) count as looting — chests, trapped chests, and barrels included. Player-placed storage in villages is never penalized. The per-player looted-chest position set is gone entirely (it was dimension-blind and grew in NBT forever).
- **Dimension reward double-pay (H4):** the current dimension is marked "entered" on login/respawn, and the reward is granted after travel completes (`PlayerChangedDimensionEvent`), not before. Returning to the Overworld no longer pays +250.
- **Wall-clock timing replaced with game time (H5):** cooldowns, caps, combos, and the death window now use `level.getGameTime()` ticks. Pausing single-player, server restarts, and host clock changes no longer distort mechanics. Legacy millisecond timestamps in old saves are detected and discarded on load. (`/judge history` display timestamps remain wall-clock.)
- **Overlapping kill rules (H6):** new `exclusive` rule flag; `kill_pet` is exclusive so killing a tamed animal no longer also charges `kill_passive` (-5050 → -5000).
- **Client mixin in common config (H7):** `DeathScreenMixin` moved to the `client` section of `judgemod.mixins.json`.
- **Spectators and fake players (H8, M14):** a single exemption check (creative, spectator, FakePlayer) now guards every rule and gate path. Mod machinery can no longer earn/lose score; spectators are no longer gate-blocked.
- **Tribunal menu client crashes (H9):** the server always writes the full menu payload (no buffer underflow if the capability is missing), and the client constructor tolerates a missing/wrong block entity instead of throwing.
- **Network channel hardened (H10):** strict protocol version (`2`) on both sides — vanilla or version-mismatched clients can no longer join a JUDGE server.

### Fixed — Medium
- Removed the duplicate Forge-bus registration of `ClientEventHandler`; the `@Mod.EventBusSubscriber` annotation (now with explicit modid) is the single registration path (M1).
- Death-screen score hiding now matches the vanilla component's translation key instead of the literal English string `"Score:"` — works in all locales (M2).
- Client HUD state (score/level/popups) resets on logout, so a previous world's numbers never flash after joining a new one (M3).
- `copyFrom` deep-copies action-history lists instead of sharing mutable references (M4); `getActionTimestamps` returns an immutable copy instead of leaking internal state (M5).
- Commands (M6): `/judge set score` updates the vanilla score immediately; `/judge get history [page]` is paginated (10/page, newest first); added `/judge add score`, `/judge get|set composure`, `/judge reset`; all commands return failure (0) when no capability is present.
- README corrected: `per_heart_penalty` is charged per full heart (M7); documented the real trigger names, conditions, `exclusive`, retention behavior, and loot-chest semantics (M8).
- Renamed the misleading `player_not_hungry` condition to `player_food_full` (same semantics: rule applies only at full hunger) (M9).
- `cure_villager` credits the player who actually fed the golden apple (via a `ZombieVillager` conversion-starter accessor mixin), falling back to proximity only if they're unavailable (M10).
- Default cooldowns added to periodic damage types (lava, in-wall, drowning, freezing, starvation) so tick damage reads as one mistake, not twenty (M11).
- Threshold effects: only the strongest qualifying tier per effect applies; `is_negative` is now a real, overridable field (M12).
- The survival clock keeps ticking during creative/spectator stints, so composure decay is never frozen; only rewards/effects are suspended (M13).
- Gate-denial messages are throttled (1/sec per player) — standing in a portal no longer floods the action bar (M15).
- Deleted the empty, never-registered `Config.java` (M16).
- Added recipe-unlock advancements so both recipes appear in the recipe book (M17).
- All player-facing text now goes through translatable components and `en_us.json` — gates, tribunal messages, screen labels, HUD labels, popups. Admin command output intentionally remains plain English (M18).
- Tribunal screen: buttons disable when the action isn't available, cleansing with zero stacks gives feedback, default container labels no longer overlap the custom text, removed redundant texture bind (M19).
- Loot-container detection runs for the main hand only; gate checks stay per-hand (offhand items must still be gated) with message throttling absorbing duplicates (M20).

### Fixed — Low
- Removed unused imports across the codebase (L1); empty action-history keys are pruned (L2); chronological history uses an `ArrayDeque` instead of `ArrayList.remove(0)` (L3); removed the empty block-entity ticker (L4).
- Shipped real entity-type tags `#judgemod:animals` and `#judgemod:monsters` (used by the default rules); the class-based fallback remains for modded entities and now counts hoglins as monsters, not passives (L5).
- `gameTestServer` run config gets the mixin arg (L6); removed `org.gradle.daemon=false` (L7); removed the stock MDK `changelog.txt` in favor of this file (L8).
- Level-up popups use a dedicated packet flag instead of the `"level_up"` string sentinel (L9).

### Changed — Balance / Design
- **2500 score threshold now grants Resistance instead of Hero of the Village.** Hero of the Village skewed villager prices and compounded with `trade_villager` combo scoring (see C4). If you want the old behavior, override the tier in a datapack.
- New datapack knobs: top-level `survive_interval_minutes` (was hardcoded to 10), per-rule `exclusive`, `in_village` / `player_food_full` conditions, `is_negative` on threshold tiers.
- Deliberately **unchanged** (documented design decisions from audit §5): linear/quadratic combo scaling formulas, the single-slot combo chain, `kill_pet` applying equally to your own and others' pets, and the default level-cost curve. Revisit after playtesting.

### Project
- Initialized git version control; the pre-fix state is preserved as the baseline commit (P1).
- `mods.toml` is templated from `gradle.properties` again — version and license can no longer drift (was: 1.0.0 vs 0.0.2, "All rights reserved" vs MIT) (P2).
- `LICENSE.txt` now contains the actual MIT license instead of the Forge MDK's LGPL text (P2).
- Removed the reference to the nonexistent `logo.png` (P3). Verify `displayURL`/`issueTrackerURL` before publishing (P4).

### Migration notes
- **Saves:** old capability data loads cleanly. Legacy wall-clock timestamps (action history, death window, combo expiry) are discarded on first load — cooldowns/caps/composure windows restart fresh. Scores, levels, and composure stacks are preserved. The obsolete looted-chest list is dropped.
- **Datapacks:** `player_not_hungry` → `player_food_full`. `#minecraft:animals`/`#minecraft:monsters` targets still work via the fallback, but `#judgemod:animals`/`#judgemod:monsters` are the supported spellings.
- **Network:** protocol version bumped to `2`; both sides must run 0.0.3+.

## [0.0.2] — baseline

Pre-audit state. See [AUDIT.md](AUDIT.md) for the full list of issues present in this version.
