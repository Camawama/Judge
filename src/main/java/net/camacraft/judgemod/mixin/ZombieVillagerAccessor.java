package net.camacraft.judgemod.mixin;

import net.minecraft.world.entity.monster.ZombieVillager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/** Exposes the player who fed the golden apple, so cure_villager credits the right person. */
@Mixin(ZombieVillager.class)
public interface ZombieVillagerAccessor {
    @Accessor("conversionStarter")
    @Nullable
    UUID judgemod$getConversionStarter();
}
