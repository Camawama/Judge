package net.camacraft.judgemod.capability;

import net.camacraft.judgemod.data.JudgeDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JudgeData implements IJudgeData {
    /**
     * Mechanic timestamps (action history, death window, combos) are stored in game
     * ticks. Saves from versions before 0.0.3 stored wall-clock milliseconds; any
     * stored value at or above this threshold is treated as legacy data and dropped
     * on load (game time would need ~158 years of uptime to reach it).
     */
    private static final long LEGACY_TIMESTAMP_CUTOFF = 100_000_000_000L;
    private static final int MAX_CHRONOLOGICAL_ENTRIES = 100;

    private int lifeScore;
    private int judgeLevel;
    private int lastDeathLifeScore;
    private int fracturedComposureStacks;
    private int timeSinceLastDeath; // In ticks
    private List<Long> deathTimestamps = new ArrayList<>(); // Game ticks

    // --- Action Tracking ---
    private final Map<String, List<Long>> actionHistory = new HashMap<>(); // Game ticks
    private final Deque<ActionRecord> chronologicalHistory = new ArrayDeque<>(); // Wall-clock, display only
    private final Set<String> enteredDimensionsThisLife = new HashSet<>();
    private long lastSleptDay = -1;

    // --- Combo Tracking (game ticks) ---
    private String comboActionKey = "";
    private int comboCount = 0;
    private long comboExpireTime = 0;

    public static class ActionRecord {
        public final String actionId;
        public final long timestamp; // Wall-clock millis, for /judge history display only
        public final int delta;

        public ActionRecord(String actionId, long timestamp, int delta) {
            this.actionId = actionId;
            this.timestamp = timestamp;
            this.delta = delta;
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", actionId);
            tag.putLong("time", timestamp);
            tag.putInt("delta", delta);
            return tag;
        }

        public static ActionRecord deserialize(CompoundTag tag) {
            return new ActionRecord(tag.getString("id"), tag.getLong("time"), tag.getInt("delta"));
        }
    }

    @Override
    public int getLifeScore() { return lifeScore; }
    @Override
    public void setLifeScore(int score) { this.lifeScore = score; }
    @Override
    public void addLifeScore(int amount) { this.lifeScore += amount; }

    @Override
    public int getJudgeLevel() { return judgeLevel; }
    @Override
    public void setJudgeLevel(int level) { this.judgeLevel = Math.max(0, level); }

    @Override
    public int getLastDeathLifeScore() { return lastDeathLifeScore; }
    @Override
    public void setLastDeathLifeScore(int score) { this.lastDeathLifeScore = score; }

    @Override
    public int getFracturedComposureStacks() { return fracturedComposureStacks; }
    @Override
    public void setFracturedComposureStacks(int stacks) { this.fracturedComposureStacks = Math.max(0, stacks); }

    @Override
    public int getTimeSinceLastDeath() { return timeSinceLastDeath; }
    @Override
    public void setTimeSinceLastDeath(int ticks) { this.timeSinceLastDeath = ticks; }
    @Override
    public void tick() { this.timeSinceLastDeath++; }

    @Override
    public void recordDeath(long gameTime) {
        this.deathTimestamps.add(gameTime);
        long cutoff = gameTime - JudgeDataManager.getHistoryRetentionTicks();
        this.deathTimestamps.removeIf(t -> t < cutoff);
    }

    @Override
    public int getDeathsInWindow(long windowTicks, long gameTime) {
        long cutoff = gameTime - windowTicks;
        return (int) deathTimestamps.stream().filter(t -> t >= cutoff).count();
    }

    @Override
    public void recordAction(String actionKey, long gameTime, int delta) {
        // Update map for cooldown/cap/combo checks
        actionHistory.computeIfAbsent(actionKey, k -> new ArrayList<>()).add(gameTime);
        long cutoff = gameTime - JudgeDataManager.getHistoryRetentionTicks();
        actionHistory.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(t -> t < cutoff);
            return entry.getValue().isEmpty();
        });

        // Update chronological list for the history command (wall clock for display)
        chronologicalHistory.addLast(new ActionRecord(actionKey, System.currentTimeMillis(), delta));
        while (chronologicalHistory.size() > MAX_CHRONOLOGICAL_ENTRIES) {
            chronologicalHistory.removeFirst();
        }
    }

    @Override
    public List<Long> getActionTimestamps(String actionKey) {
        List<Long> timestamps = actionHistory.get(actionKey);
        return timestamps == null ? List.of() : List.copyOf(timestamps);
    }

    @Override
    public List<ActionRecord> getChronologicalHistory() {
        return new ArrayList<>(chronologicalHistory);
    }

    @Override
    public String getComboActionKey() { return comboActionKey; }
    @Override
    public void setComboActionKey(String key) { this.comboActionKey = key; }
    @Override
    public int getComboCount() { return comboCount; }
    @Override
    public void setComboCount(int count) { this.comboCount = count; }
    @Override
    public long getComboExpireTime() { return comboExpireTime; }
    @Override
    public void setComboExpireTime(long gameTime) { this.comboExpireTime = gameTime; }

    @Override
    public boolean hasEnteredDimensionThisLife(String dimensionId) {
        return enteredDimensionsThisLife.contains(dimensionId);
    }

    @Override
    public void setEnteredDimensionThisLife(String dimensionId) {
        enteredDimensionsThisLife.add(dimensionId);
    }

    @Override
    public boolean hasSleptOnDay(long day) {
        return lastSleptDay == day;
    }

    @Override
    public void setLastSleptDay(long day) {
        this.lastSleptDay = day;
    }

    @Override
    public void copyFrom(IJudgeData other) {
        if (other instanceof JudgeData data) {
            this.judgeLevel = data.judgeLevel;
            this.fracturedComposureStacks = data.fracturedComposureStacks;
            this.deathTimestamps = new ArrayList<>(data.deathTimestamps);
            this.lastSleptDay = data.lastSleptDay;
            data.actionHistory.forEach((key, timestamps) -> this.actionHistory.put(key, new ArrayList<>(timestamps)));
            this.chronologicalHistory.addAll(data.chronologicalHistory);
        }
    }

    @Override
    public void resetForNewLife() {
        setLifeScore(0);
        setTimeSinceLastDeath(0);
        enteredDimensionsThisLife.clear();
        // Do NOT reset judgeLevel here. It persists.
        setComboActionKey("");
        setComboCount(0);
        setComboExpireTime(0);
        // We keep history across lives for the command log
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("LifeScore", lifeScore);
        tag.putInt("JudgeLevel", judgeLevel);
        tag.putInt("LastDeathLifeScore", lastDeathLifeScore);
        tag.putInt("FracturedComposure", fracturedComposureStacks);
        tag.putInt("TimeSinceLastDeath", timeSinceLastDeath);
        tag.putLong("LastSleptDay", lastSleptDay);
        tag.putString("ComboActionKey", comboActionKey);
        tag.putInt("ComboCount", comboCount);
        tag.putLong("ComboExpireTime", comboExpireTime);

        ListTag deaths = new ListTag();
        deathTimestamps.forEach(t -> deaths.add(LongTag.valueOf(t)));
        tag.put("DeathTimestamps", deaths);

        ListTag enteredDims = new ListTag();
        enteredDimensionsThisLife.forEach(dim -> enteredDims.add(StringTag.valueOf(dim)));
        tag.put("EnteredDimensions", enteredDims);

        CompoundTag actions = new CompoundTag();
        actionHistory.forEach((key, timestamps) -> {
            ListTag times = new ListTag();
            timestamps.forEach(t -> times.add(LongTag.valueOf(t)));
            actions.put(key, times);
        });
        tag.put("ActionHistory", actions);

        ListTag chrono = new ListTag();
        chronologicalHistory.forEach(record -> chrono.add(record.serialize()));
        tag.put("ChronologicalHistory", chrono);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        lifeScore = tag.getInt("LifeScore");
        judgeLevel = tag.getInt("JudgeLevel");
        lastDeathLifeScore = tag.getInt("LastDeathLifeScore");
        fracturedComposureStacks = tag.getInt("FracturedComposure");
        timeSinceLastDeath = tag.getInt("TimeSinceLastDeath");
        lastSleptDay = tag.getLong("LastSleptDay");
        comboActionKey = tag.getString("ComboActionKey");
        comboCount = tag.getInt("ComboCount");
        comboExpireTime = tag.getLong("ComboExpireTime");
        if (comboExpireTime >= LEGACY_TIMESTAMP_CUTOFF) comboExpireTime = 0;

        deathTimestamps = tag.getList("DeathTimestamps", Tag.TAG_LONG).stream()
                .map(t -> ((LongTag) t).getAsLong())
                .filter(t -> t < LEGACY_TIMESTAMP_CUTOFF)
                .collect(Collectors.toList());

        enteredDimensionsThisLife.clear();
        tag.getList("EnteredDimensions", Tag.TAG_STRING).forEach(t -> enteredDimensionsThisLife.add(t.getAsString()));

        actionHistory.clear();
        CompoundTag actions = tag.getCompound("ActionHistory");
        actions.getAllKeys().forEach(key -> {
            List<Long> timestamps = actions.getList(key, Tag.TAG_LONG).stream()
                    .map(t -> ((LongTag) t).getAsLong())
                    .filter(t -> t < LEGACY_TIMESTAMP_CUTOFF)
                    .collect(Collectors.toList());
            if (!timestamps.isEmpty()) actionHistory.put(key, timestamps);
        });

        chronologicalHistory.clear();
        tag.getList("ChronologicalHistory", Tag.TAG_COMPOUND).forEach(t -> chronologicalHistory.addLast(ActionRecord.deserialize((CompoundTag) t)));
    }
}
