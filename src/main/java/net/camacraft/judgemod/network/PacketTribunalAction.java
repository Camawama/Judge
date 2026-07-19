package net.camacraft.judgemod.network;

import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.data.JudgeDataManager;
import net.camacraft.judgemod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketTribunalAction {
    public enum Action {
        BUY_LEVEL,
        CLEANSE
    }

    private final Action action;

    public PacketTribunalAction(Action action) {
        this.action = action;
    }

    public PacketTribunalAction(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(action);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                if (action == Action.BUY_LEVEL) {
                    JudgeDataManager.LevelCostConfig costConfig = JudgeDataManager.getLevelCostConfig();
                    int currentLevel = data.getJudgeLevel();
                    int cost = costConfig.B + costConfig.A * currentLevel + costConfig.Q * currentLevel * currentLevel;
                    
                    if (data.getLifeScore() >= cost) {
                        data.addLifeScore(-cost);
                        data.setJudgeLevel(currentLevel + 1);
                        PacketHandler.sendToPlayer(new PacketJudgePopup("level_up", currentLevel + 1, 1), player);
                        player.sendSystemMessage(Component.literal("Judge Level Increased!").withStyle(ChatFormatting.GOLD));
                    } else {
                        player.displayClientMessage(Component.literal("Insufficient Life Score!").withStyle(ChatFormatting.RED), true);
                    }
                } else if (action == Action.CLEANSE) {
                    if (data.getFracturedComposureStacks() > 0) {
                        JudgeDataManager.LevelCostConfig costConfig = JudgeDataManager.getLevelCostConfig();
                        int nextLevelCost = costConfig.B + costConfig.A * data.getJudgeLevel() + costConfig.Q * data.getJudgeLevel() * data.getJudgeLevel();
                        int cleanseCost = Math.max(150, (int)(0.20 * nextLevelCost));

                        if (data.getLifeScore() >= cleanseCost) {
                            data.addLifeScore(-cleanseCost);
                            data.setFracturedComposureStacks(data.getFracturedComposureStacks() - 1);
                            player.sendSystemMessage(Component.literal("Fractured Composure Cleansed.").withStyle(ChatFormatting.AQUA));
                        } else {
                            ItemStack offering = new ItemStack(ModItems.PURIFICATION_OFFERING.get());
                            if (player.getInventory().contains(offering)) {
                                player.getInventory().clearOrCountMatchingItems(p -> p.is(offering.getItem()), 1, player.inventoryMenu.getCraftSlots());
                                data.setFracturedComposureStacks(data.getFracturedComposureStacks() - 1);
                                player.sendSystemMessage(Component.literal("Fractured Composure Cleansed via Offering.").withStyle(ChatFormatting.AQUA));
                            } else {
                                player.displayClientMessage(Component.literal("Insufficient Life Score or Purification Offering!").withStyle(ChatFormatting.RED), true);
                            }
                        }
                    }
                }
                
                // Sync vanilla score
                player.setScore(data.getLifeScore());

                // Sync data after any action
                PacketHandler.sendToPlayer(new PacketSyncJudgeData(data.getLifeScore(), data.getJudgeLevel(), data.getLastDeathLifeScore(), data.getFracturedComposureStacks()), player);
                
                // Also send a packet to update the open container if it's a TribunalMenu
                if (player.containerMenu instanceof net.camacraft.judgemod.menu.TribunalMenu menu) {
                    menu.updateData(data.getLifeScore(), data.getJudgeLevel(), data.getFracturedComposureStacks());
                    PacketHandler.sendToPlayer(new PacketUpdateTribunalMenu(data.getLifeScore(), data.getJudgeLevel(), data.getFracturedComposureStacks()), player);
                }
            });
        });
        return true;
    }
}
