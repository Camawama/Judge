# JudgeMod - A Progression & Judgment Overhaul

Welcome to **JudgeMod**, a mod that fundamentally changes Minecraft progression by replacing the vanilla death score with a deep, data-driven system of **Performance, Judgment, and Composure**.

## ⚖️ The Core Systems

*   **Life Score (Volatile)**: Your current "performance rating."
    *   **Gain** it by killing monsters, exploring, and completing advancements.
    *   **Lose** it by dying, taking avoidable damage (falling, drowning), or acting dishonorably (killing villagers/pets).
    *   *Note: Score resets to 0 on death, but the death screen shows your final score.*
*   **Judge Level (Persistent)**: Your permanent rank.
    *   Spend **Life Score** at a **Tribunal Stone** to level up.
    *   Unlocks dimensions (Nether/End) and key blocks (Enchanting Table, Anvil, etc.).
*   **Fractured Composure**: The price of carelessness.
    *   Dying too frequently (3+ times in 5 mins) applies a stacking penalty multiplier.
    *   Cleanse it at a Tribunal Stone using Score or a **Purification Offering**.

## 🚀 Getting Started

1.  **Craft a Tribunal Stone**: (Stone Bricks, Amethyst, Book, Gold Ingot).
2.  **Check Status**: Right-click the block to see your Level, Score, and Next Level Cost.
3.  **Earn Score**: Play skillfully! Avoid damage, kill hostiles, and explore.
4.  **Level Up**: Use the Tribunal to buy levels and unlock game features.
5.  **Visuals**: Watch the HUD! Green numbers mean good actions, Red numbers mean mistakes. Consecutive actions trigger a **Combo Multiplier**.

---

## 🛠️ Configuration & Customization
<details>
<summary><strong>Click to view Admin Commands & Datapack Guide</strong></summary>

### Client Config
Located in `config/judgemod-client.toml`.
*   **Persistent Display**: Toggle the permanent Score/Level HUD.
*   **Position & Scale**: Move and resize HUD elements.

### Admin Commands
*   `/judge get <player> score|level|composure`: View stats.
*   `/judge get <player> history [page]`: Paginated action log (newest first).
*   `/judge set <player> score|level|composure <amount>`: Manually set stats.
*   `/judge add <player> score <amount>`: Adjust score relatively.
*   `/judge reset <player>`: Reset score, composure, and combos (Judge Level and history are kept).

### Datapack Configuration
JudgeMod is fully data-driven. You can override the default rules by creating a datapack and placing a JSON file at `data/judgemod/judge_rules/default.json`.

#### 1. Level Cost Formula
`Cost = B + (A * Level) + (Q * Level^2)`
*   **B (Base)**: Starting cost.
*   **A (Linear)**: Steady increase per level.
*   **Q (Quadratic)**: Exponential increase for high levels.

#### 2. Fractured Composure Settings
*   `window_minutes`: Time window to track recent deaths (default: 5, minimum 1).
*   `trigger_deaths`: Number of deaths in the window to trigger the penalty (default: 3, minimum 1).
*   `max_stacks`: Maximum penalty stacks a player can accumulate.
*   `dishonor_multiplier_per_stack`: Multiplier for "Dishonor" penalties (e.g., killing villagers) per stack.
*   `mistake_multiplier_per_stack`: Multiplier for "Mistake" penalties (e.g., fall damage) per stack.
*   `decay_minutes_per_stack`: Time alive required to remove one stack naturally (minimum 1).

All values are clamped to sane minimums at load, so a bad datapack cannot crash the server.

#### 2b. Survive Reward Interval
*   `survive_interval_minutes` (top level, default: 10): How often the `survive_time` trigger fires while a player stays alive.

#### 3. Progression Gates
Define the Judge Level required to access features.
*   `dimension_gates`: Map of `dimension_id` -> `level_required`.
*   `block_interaction_gates`: Map of `block_id` -> `level_required` (for right-clicking).
*   `block_break_gates`: Map of `block_id` -> `level_required` (for breaking).

#### 4. Action Rules (Gains/Losses)
Define events that change Life Score.
*   `trigger`: Event type. One of: `kill_mob`, `hit_entity`, `break_block`, `sleep`, `enter_dimension`, `advancement_task`, `advancement_goal`, `advancement_challenge`, `trade_villager`, `loot_chest`, `loot_village_chest`, `survive_time`, `cure_villager`, `win_raid`.
*   `target`: Entity ID or entity tag for `kill_mob`/`hit_entity` (e.g., `#judgemod:animals`, `#judgemod:monsters`); Block ID or block tag for `break_block` (e.g., `#minecraft:job_site_blocks`).
*   `delta`: Score change (positive or negative).
*   `cap`: Max times this rule can trigger in a window.
*   `cap_duration_minutes`: The window for the cap (any length is honored, including multi-day windows).
*   `cooldown_seconds`: Minimum time between triggers of this rule.
*   `combo_window_seconds`: Time to chain this action for a multiplier.
*   `ignore_spawners`: If true, mobs spawned by monster spawners never award this rule.
*   `exclusive`: If true and this rule matches, no later rule for the same trigger is evaluated (rules run in file order). Used so `kill_pet` doesn't also count as `kill_passive`.
*   `condition`: Optional checks (`player_food_full`, `is_tamed`, `in_village`).

All timing (cooldowns, caps, combos, the death window) runs on game time, so it pauses when the game is paused and survives server restarts.

Note on loot chests: only naturally generated, still-unopened loot containers (chests, trapped chests, barrels with a pending loot table) count for `loot_chest`/`loot_village_chest`. Player-placed storage is never penalized.

#### 5. Mistake Rules (Damage)
Define penalties for taking specific types of damage. The first matching rule applies. Damage someone else is responsible for is never a mistake: no penalty when the damage has an attacker, or when the player was hurt by a player or mob in the last 5 seconds (knocked off a ledge, into lava, into a wall).
*   `damage_type`: Which damage counts. A damage-type tag (`#minecraft:is_fall`), one damage type (`minecraft:lava`), or a legacy message id (`fall`, `inWall`, `drown`). Prefer tags: mods replace vanilla damage with their own types (Full Stop bills landings as `fullstop:kinetic_fall`, which only `#minecraft:is_fall` catches).
*   `id` (optional): Name used in history and cooldowns (`mistake_<id>`). Defaults to `damage_type` without the `#`.
*   `base_penalty`: Flat score reduction.
*   `per_heart_penalty`: Additional reduction per full heart of damage taken.
*   `cooldown_seconds`: Minimum time between penalties for this damage type (recommended for periodic damage like lava).

#### 6. Threshold Tiers (Buffs)
Define effects granted based on current Life Score. When several tiers qualify for the same effect, only the strongest (highest amplifier) applies.
*   `threshold`: The score value required.
*   `effect`: The potion effect ID (e.g., `minecraft:luck`).
*   `amplifier`: The effect level (0 = Level I).
*   `is_negative`: If true, applies when score is *at or below* the threshold (for debuffs). Defaults to true for negative thresholds, false otherwise.

**Comprehensive JSON Example:**
```json
{
  "level_cost": { "B": 100, "A": 25, "Q": 6 },
  "fractured_composure": {
    "window_minutes": 5,
    "trigger_deaths": 3,
    "max_stacks": 3,
    "dishonor_multiplier_per_stack": 0.75,
    "mistake_multiplier_per_stack": 0.35,
    "decay_minutes_per_stack": 2
  },
  "dimension_gates": { "minecraft:the_nether": 15 },
  "block_interaction_gates": { "minecraft:enchanting_table": 12 },
  "block_break_gates": { "minecraft:obsidian": 15 },
  "action_rules": [
    {
      "id": "kill_zombie",
      "trigger": "kill_mob",
      "target": "minecraft:zombie",
      "delta": 5,
      "cap": 50,
      "combo_window_seconds": 5,
      "ignore_spawners": true
    }
  ],
  "mistake_rules": [
    {
      "id": "fall",
      "damage_type": "#minecraft:is_fall",
      "base_penalty": 5,
      "per_heart_penalty": 12
    }
  ],
  "threshold_tiers": [
    {
      "threshold": 1000,
      "effect": "minecraft:luck",
      "amplifier": 1,
      "is_negative": false
    }
  ]
}
```
</details>

---

## ⚙️ Technical Specification
<details>
<summary><strong>Click to view Implementation Details (For Developers/LLMs)</strong></summary>

### Data Persistence
*   **Capability**: `IJudgeData` attached to `Player`.
*   **Persistence**: `JudgeLevel` and `ActionHistory` persist across death. `LifeScore` resets to 0.
*   **Sync**: Server-authoritative. Data synced to client via `PacketSyncJudgeData` on login, respawn, and score changes.

### Event Triggers
*   **Mob Kills**: `LivingDeathEvent`. Checks `FromSpawner` tag for anti-cheese.
*   **Trading**: `TradeWithVillagerEvent` (fires on successful trade).
*   **Raid Victory**: `MobEffectEvent.Added` (checks for `HERO_OF_THE_VILLAGE`).
*   **Advancements**: `AdvancementEvent.AdvancementEarnEvent` (checks `FrameType`).
*   **Dimension**: `EntityTravelToDimensionEvent` (cancellable for gating).
*   **Block Interaction**: `PlayerInteractEvent.RightClickBlock` (cancellable).
*   **Block Breaking**: `BlockEvent.BreakEvent` (cancellable).

### Combo Math
*   **Positive Actions**: `Delta = BaseDelta + (ComboCount - 1)` (Linear scaling).
*   **Negative Actions**: `Delta = BaseDelta - (ComboCount - 1)^2` (Quadratic scaling).
*   **Window**: Defined per-rule in `combo_window_seconds`.

### Visuals
*   **Death Screen**: `DeathScreenMixin` cancels vanilla score render and injects custom `renderDeathScreenScore` at correct Z-level.
*   **HUD**: `ClientEventHandler` manages a list of `ScorePopup` objects for stacking notifications.
</details>
