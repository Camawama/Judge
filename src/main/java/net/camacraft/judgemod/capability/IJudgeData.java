package net.camacraft.judgemod.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.List;
import java.util.Map;

public interface IJudgeData extends INBTSerializable<CompoundTag> {
    int getLifeScore();
    void setLifeScore(int score);
    void addLifeScore(int amount);

    int getJudgeLevel();
    void setJudgeLevel(int level);

    int getLastDeathLifeScore();
    void setLastDeathLifeScore(int score);

    int getFracturedComposureStacks();
    void setFracturedComposureStacks(int stacks);

    // --- Timers and Cooldowns ---
    int getTimeSinceLastDeath();
    void setTimeSinceLastDeath(int ticks);
    void tick();

    void recordDeath();
    int getDeathsInWindow(long windowMillis);

    // --- Action Tracking for Anti-Cheese & Combos ---
    void recordAction(String actionKey, long timestamp, int delta);
    List<Long> getActionTimestamps(String actionKey);
    List<JudgeData.ActionRecord> getChronologicalHistory();
    
    String getComboActionKey();
    void setComboActionKey(String key);
    int getComboCount();
    void setComboCount(int count);
    long getComboExpireTime();
    void setComboExpireTime(long time);

    boolean hasEnteredDimensionThisLife(String dimensionId);
    void setEnteredDimensionThisLife(String dimensionId);

    boolean hasSleptOnDay(long day);
    void setLastSleptDay(long day);

    boolean hasLootedChest(BlockPos pos);
    void addLootedChest(BlockPos pos);

    // --- Persistence ---
    void copyFrom(IJudgeData other);
    void resetForNewLife();
    void sync();
}
