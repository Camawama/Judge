package net.camacraft.judgemod.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.List;

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

    // --- Timers and Cooldowns (all timestamps are in game ticks) ---
    int getTimeSinceLastDeath();
    void setTimeSinceLastDeath(int ticks);
    void tick();

    void recordDeath(long gameTime);
    int getDeathsInWindow(long windowTicks, long gameTime);

    // --- Action Tracking for Anti-Cheese & Combos ---
    void recordAction(String actionKey, long gameTime, int delta);
    List<Long> getActionTimestamps(String actionKey);
    List<JudgeData.ActionRecord> getChronologicalHistory();

    String getComboActionKey();
    void setComboActionKey(String key);
    int getComboCount();
    void setComboCount(int count);
    long getComboExpireTime();
    void setComboExpireTime(long gameTime);

    boolean hasEnteredDimensionThisLife(String dimensionId);
    void setEnteredDimensionThisLife(String dimensionId);

    boolean hasSleptOnDay(long day);
    void setLastSleptDay(long day);

    // --- Persistence ---
    void copyFrom(IJudgeData other);
    void resetForNewLife();
}
