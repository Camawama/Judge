package net.camacraft.judgemod.menu;

import net.camacraft.judgemod.block.TribunalStoneBlockEntity;
import net.camacraft.judgemod.registry.ModBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class TribunalMenu extends AbstractContainerMenu {
    @Nullable
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

    public TribunalMenu(int pContainerId, Inventory inv, @Nullable BlockEntity entity) {
        super(ModMenus.TRIBUNAL_MENU.get(), pContainerId);
        // The client can race chunk sync and see no (or a different) block entity at
        // the position; degrade to a null BE instead of crashing the client.
        if (entity instanceof TribunalStoneBlockEntity tribunal) {
            this.blockEntity = tribunal;
            this.levelAccess = ContainerLevelAccess.create(tribunal.getLevel(), tribunal.getBlockPos());
        } else {
            this.blockEntity = null;
            this.levelAccess = ContainerLevelAccess.NULL;
        }

        addPlayerInventory(inv);
        addPlayerHotbar(inv);
    }

    /** Called with server-computed values only; the level-cost config is not synced to clients. */
    public void updateData(int lifeScore, int judgeLevel, int fracturedComposureStacks, int costToNextLevel) {
        this.clientLifeScore = lifeScore;
        this.clientJudgeLevel = judgeLevel;
        this.clientFracturedComposureStacks = fracturedComposureStacks;
        this.clientCostToNextLevel = costToNextLevel;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        // Server menus always have a real block entity; a null BE only occurs on a
        // desynced client, where vanilla treats NULL access as valid anyway.
        if (blockEntity == null) return pPlayer.level().isClientSide();
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
