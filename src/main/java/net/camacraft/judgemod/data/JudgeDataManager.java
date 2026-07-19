package net.camacraft.judgemod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.camacraft.judgemod.JudgeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JudgeDataManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // --- Configurable Settings ---
    private static LevelCostConfig levelCostConfig = new LevelCostConfig();
    private static FracturedComposureConfig fcConfig = new FracturedComposureConfig();
    private static final Map<String, Integer> DIMENSION_GATES = new HashMap<>();
    private static final Map<String, Integer> BLOCK_INTERACTION_GATES = new HashMap<>();
    private static final Map<String, Integer> BLOCK_BREAK_GATES = new HashMap<>();
    private static final Map<String, Integer> ITEM_USE_GATES = new HashMap<>();
    private static final List<ActionRule> ACTION_RULES = new ArrayList<>();
    private static final List<MistakeRule> MISTAKE_RULES = new ArrayList<>();
    private static final List<ThresholdTier> THRESHOLD_TIERS = new ArrayList<>();

    // --- Data Structures ---
    public static class LevelCostConfig {
        public int B = 100, A = 25, Q = 6;
    }

    public static class FracturedComposureConfig {
        public int windowMinutes = 5;
        public int triggerDeaths = 3;
        public int maxStacks = 3;
        public double dishonorMultiplier = 0.75;
        public double mistakeMultiplier = 0.35;
        public int decayMinutes = 2;
    }

    public static class ActionRule {
        public String id;
        public String trigger;
        public String target;
        public int delta;
        public int cap;
        public int capDurationMinutes;
        public int cooldownSeconds;
        public int combo_window_seconds;
        public boolean ignoreSpawners;
        public String condition;
    }

    public static class MistakeRule {
        public String damageType;
        public int basePenalty;
        public int perHeartPenalty;
        public int cooldownSeconds;
    }

    public static class ThresholdTier {
        public int threshold;
        public String effectId;
        public int amplifier;
        public boolean isNegative;
    }

    public JudgeDataManager() {
        super(GSON, "judge_rules");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        // Clear old data
        DIMENSION_GATES.clear();
        BLOCK_INTERACTION_GATES.clear();
        BLOCK_BREAK_GATES.clear();
        ITEM_USE_GATES.clear();
        ACTION_RULES.clear();
        MISTAKE_RULES.clear();
        THRESHOLD_TIERS.clear();
        levelCostConfig = new LevelCostConfig();
        fcConfig = new FracturedComposureConfig();

        pObject.forEach((location, json) -> {
            try {
                JsonObject obj = json.getAsJsonObject();
                if (obj.has("level_cost")) parseLevelCost(obj.getAsJsonObject("level_cost"));
                if (obj.has("fractured_composure")) parseFcConfig(obj.getAsJsonObject("fractured_composure"));
                if (obj.has("dimension_gates")) parseGates(obj.getAsJsonObject("dimension_gates"), DIMENSION_GATES);
                if (obj.has("block_interaction_gates")) parseGates(obj.getAsJsonObject("block_interaction_gates"), BLOCK_INTERACTION_GATES);
                if (obj.has("block_break_gates")) parseGates(obj.getAsJsonObject("block_break_gates"), BLOCK_BREAK_GATES);
                if (obj.has("item_use_gates")) parseGates(obj.getAsJsonObject("item_use_gates"), ITEM_USE_GATES);
                if (obj.has("action_rules")) parseActionRules(obj.getAsJsonArray("action_rules"));
                if (obj.has("mistake_rules")) parseMistakeRules(obj.getAsJsonArray("mistake_rules"));
                if (obj.has("threshold_tiers")) parseThresholdTiers(obj.getAsJsonArray("threshold_tiers"));
            } catch (Exception e) {
                JudgeMod.LOGGER.error("Failed to load judge rule file: {}", location, e);
            }
        });
    }

    private void parseLevelCost(JsonObject cost) {
        levelCostConfig.B = GsonHelper.getAsInt(cost, "B", 100);
        levelCostConfig.A = GsonHelper.getAsInt(cost, "A", 25);
        levelCostConfig.Q = GsonHelper.getAsInt(cost, "Q", 6);
    }

    private void parseFcConfig(JsonObject fc) {
        fcConfig.windowMinutes = GsonHelper.getAsInt(fc, "window_minutes", 5);
        fcConfig.triggerDeaths = GsonHelper.getAsInt(fc, "trigger_deaths", 3);
        fcConfig.maxStacks = GsonHelper.getAsInt(fc, "max_stacks", 3);
        fcConfig.dishonorMultiplier = GsonHelper.getAsDouble(fc, "dishonor_multiplier_per_stack", 0.75);
        fcConfig.mistakeMultiplier = GsonHelper.getAsDouble(fc, "mistake_multiplier_per_stack", 0.35);
        fcConfig.decayMinutes = GsonHelper.getAsInt(fc, "decay_minutes_per_stack", 2);
    }

    private void parseGates(JsonObject gates, Map<String, Integer> map) {
        gates.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsInt()));
    }

    private void parseActionRules(JsonArray rules) {
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            ActionRule rule = new ActionRule();
            rule.id = GsonHelper.getAsString(r, "id");
            rule.trigger = GsonHelper.getAsString(r, "trigger");
            rule.target = GsonHelper.getAsString(r, "target", "any");
            rule.delta = GsonHelper.getAsInt(r, "delta");
            rule.cap = GsonHelper.getAsInt(r, "cap", -1);
            rule.capDurationMinutes = GsonHelper.getAsInt(r, "cap_duration_minutes", -1);
            rule.cooldownSeconds = GsonHelper.getAsInt(r, "cooldown_seconds", 0);
            rule.combo_window_seconds = GsonHelper.getAsInt(r, "combo_window_seconds", 0);
            rule.ignoreSpawners = GsonHelper.getAsBoolean(r, "ignore_spawners", false);
            rule.condition = GsonHelper.getAsString(r, "condition", null);
            ACTION_RULES.add(rule);
        }
    }

    private void parseMistakeRules(JsonArray rules) {
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            MistakeRule rule = new MistakeRule();
            rule.damageType = GsonHelper.getAsString(r, "damage_type");
            rule.basePenalty = GsonHelper.getAsInt(r, "base_penalty", 0);
            rule.perHeartPenalty = GsonHelper.getAsInt(r, "per_heart_penalty", 0);
            rule.cooldownSeconds = GsonHelper.getAsInt(r, "cooldown_seconds", 0);
            MISTAKE_RULES.add(rule);
        }
    }

    private void parseThresholdTiers(JsonArray tiers) {
        for (JsonElement e : tiers) {
            JsonObject t = e.getAsJsonObject();
            ThresholdTier tier = new ThresholdTier();
            tier.threshold = GsonHelper.getAsInt(t, "threshold");
            tier.effectId = GsonHelper.getAsString(t, "effect");
            tier.amplifier = GsonHelper.getAsInt(t, "amplifier", 0);
            tier.isNegative = tier.threshold < 0;
            THRESHOLD_TIERS.add(tier);
        }
    }

    // --- Public Getters ---
    public static LevelCostConfig getLevelCostConfig() { return levelCostConfig; }
    public static FracturedComposureConfig getFcConfig() { return fcConfig; }
    public static int getDimensionReq(String dimId) { return DIMENSION_GATES.getOrDefault(dimId, 0); }
    public static int getBlockInteractReq(String blockId) { return BLOCK_INTERACTION_GATES.getOrDefault(blockId, 0); }
    public static int getBlockBreakReq(String blockId) { return BLOCK_BREAK_GATES.getOrDefault(blockId, 0); }
    public static int getItemUseReq(String itemId) { return ITEM_USE_GATES.getOrDefault(itemId, 0); }
    public static List<ActionRule> getActionRules() { return ACTION_RULES; }
    public static List<MistakeRule> getMistakeRules() { return MISTAKE_RULES; }
    public static List<ThresholdTier> getThresholdTiers() { return THRESHOLD_TIERS; }
}
