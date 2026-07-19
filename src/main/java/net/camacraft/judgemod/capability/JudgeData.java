package net.camacraft.judgemod.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JudgeData implements IJudgeData {
    private int lifeScore;
    private int judgeLevel;
    private int lastDeathLifeScore;
    private int fracturedComposureStacks;
    private int timeSinceLastDeath; // In ticks
    private List<Long> deathTimestamps = new ArrayList<>();

    // --- Action Tracking ---
    private final Map<String, List<Long>> actionHistory = new HashMap<>();
    private final List<ActionRecord> chronologicalHistory = new ArrayList<>();
    private final Set<String> enteredDimensionsThisLife = new HashSet<>();
    private final Set<BlockPos> lootedChests = new HashSet<>();
    private long lastSleptDay = -1;
    
    // --- Combo Tracking ---
    private String comboActionKey = "";
    private int comboCount = 0;
    private long comboExpireTime = 0;

    public static class ActionRecord {
        public final String actionId;
        public final long timestamp;
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
    public void recordDeath() {
        this.deathTimestamps.add(System.currentTimeMillis());
        long cutoff = System.currentTimeMillis() - 3600000; // 1 hour
        this.deathTimestamps.removeIf(t -> t < cutoff);
    }

    @Override
    public int getDeathsInWindow(long windowMillis) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        return (int) deathTimestamps.stream().filter(t -> t >= cutoff).count();
    }

    @Override
    public void recordAction(String actionKey, long timestamp, int delta) {
        // Update map for logic checks
        actionHistory.computeIfAbsent(actionKey, k -> new ArrayList<>()).add(timestamp);
        long cutoff = System.currentTimeMillis() - 3600000; // 1 hour
        actionHistory.get(actionKey).removeIf(t -> t < cutoff);

        // Update chronological list for history command
        chronologicalHistory.add(new ActionRecord(actionKey, timestamp, delta));
        // Keep only last 100 entries to prevent bloat
        if (chronologicalHistory.size() > 100) {
            chronologicalHistory.remove(0);
        }
    }

    @Override
    public List<Long> getActionTimestamps(String actionKey) {
        return actionHistory.getOrDefault(actionKey, new ArrayList<>());
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
    public void setComboExpireTime(long time) { this.comboExpireTime = time; }

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
    public boolean hasLootedChest(BlockPos pos) {
        return lootedChests.contains(pos);
    }

    @Override
    public void addLootedChest(BlockPos pos) {
        lootedChests.add(pos);
    }

    @Override
    public void copyFrom(IJudgeData other) {
        if (other instanceof JudgeData data) {
            this.judgeLevel = data.judgeLevel;
            this.fracturedComposureStacks = data.fracturedComposureStacks;
            this.deathTimestamps = new ArrayList<>(data.deathTimestamps);
            this.lootedChests.addAll(data.lootedChests);
            this.lastSleptDay = data.lastSleptDay;
            this.actionHistory.putAll(data.actionHistory);
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
    public void sync() {
        // Handled by event handler
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

        ListTag looted = new ListTag();
        lootedChests.forEach(pos -> looted.add(NbtUtils.writeBlockPos(pos)));
        tag.put("LootedChests", looted);

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

        deathTimestamps = tag.getList("DeathTimestamps", Tag.TAG_LONG).stream()
                .map(t -> ((LongTag) t).getAsLong()).collect(Collectors.toList());

        lootedChests.clear();
        tag.getList("LootedChests", Tag.TAG_COMPOUND).forEach(t -> lootedChests.add(NbtUtils.readBlockPos((CompoundTag) t)));

        enteredDimensionsThisLife.clear();
        tag.getList("EnteredDimensions", Tag.TAG_STRING).forEach(t -> enteredDimensionsThisLife.add(t.getAsString()));

        actionHistory.clear();
        CompoundTag actions = tag.getCompound("ActionHistory");
        actions.getAllKeys().forEach(key -> {
            List<Long> timestamps = actions.getList(key, Tag.TAG_LONG).stream()
                    .map(t -> ((LongTag) t).getAsLong()).collect(Collectors.toList());
            actionHistory.put(key, timestamps);
        });

        chronologicalHistory.clear();
        tag.getList("ChronologicalHistory", Tag.TAG_COMPOUND).forEach(t -> chronologicalHistory.add(ActionRecord.deserialize((CompoundTag) t)));
    }
}
