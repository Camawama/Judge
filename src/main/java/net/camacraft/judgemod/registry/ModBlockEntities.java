package net.camacraft.judgemod.registry;

import net.camacraft.judgemod.JudgeMod;
import net.camacraft.judgemod.block.TribunalStoneBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, JudgeMod.MODID);

    public static final RegistryObject<BlockEntityType<TribunalStoneBlockEntity>> TRIBUNAL_STONE_BE =
            BLOCK_ENTITIES.register("tribunal_stone_be", () ->
                    BlockEntityType.Builder.of(TribunalStoneBlockEntity::new, ModBlocks.TRIBUNAL_STONE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
