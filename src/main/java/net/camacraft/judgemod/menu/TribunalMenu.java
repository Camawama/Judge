package net.camacraft.judgemod.menu;

import net.camacraft.judgemod.data.JudgeDataManager;
import net.camacraft.judgemod.registry.ModBlocks;
import net.camacraft.judgemod.block.TribunalStoneBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TribunalMenu extends AbstractContainerMenu {
    public final TribunalStoneBlockEntity blockEntity;
    private final ContainerLevelAccess levelAccess;

    public int clientLifeScore;
    public int clientJudgeLevel;
    public int clientFracturedComposureStacks;
    public int clientCostToNextLevel;

    public TribunalMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
        this.clientLifeScore = extraData.readInt();
        this.clientJudgeLevel = extraData.readInt();
        this.clientFracturedComposureStacks = extraData.readInt();
        this.clientCostToNextLevel = extraData.readInt();
    }

    public TribunalMenu(int pContainerId, Inventory inv, BlockEntity entity) {
        super(ModMenus.TRIBUNAL_MENU.get(), pContainerId);
        checkContainerSize(inv, 0);
        this.blockEntity = (TribunalStoneBlockEntity) entity;
        this.levelAccess = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());

        addPlayerInventory(inv);
        addPlayerHotbar(inv);
    }

    public void updateData(int lifeScore, int judgeLevel, int fracturedComposureStacks) {
        this.clientLifeScore = lifeScore;
        this.clientJudgeLevel = judgeLevel;
        this.clientFracturedComposureStacks = fracturedComposureStacks;
        
        JudgeDataManager.LevelCostConfig costConfig = JudgeDataManager.getLevelCostConfig();
        this.clientCostToNextLevel = costConfig.B + costConfig.A * judgeLevel + costConfig.Q * judgeLevel * judgeLevel;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(levelAccess, pPlayer, ModBlocks.TRIBUNAL_STONE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return ItemStack.EMPTY;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
}
