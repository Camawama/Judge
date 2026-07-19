package net.camacraft.judgemod.block;

import net.camacraft.judgemod.menu.TribunalMenu;
import net.camacraft.judgemod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TribunalStoneBlockEntity extends BlockEntity implements MenuProvider {
    public TribunalStoneBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.TRIBUNAL_STONE_BE.get(), pPos, pBlockState);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Tribunal Stone");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new TribunalMenu(pContainerId, pPlayerInventory, this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TribunalStoneBlockEntity pEntity) {
        // Tick logic if needed
    }
}
