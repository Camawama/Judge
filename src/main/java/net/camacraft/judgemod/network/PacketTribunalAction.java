package net.camacraft.judgemod.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.data.JudgeDataManager;
import net.camacraft.judgemod.menu.TribunalMenu;
import net.camacraft.judgemod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        // Runs on the server thread, only with a sending player.
        Packets.handleServer(supplier, player -> {
            // Server-side validation: tribunal actions are only legal while a
            // Tribunal menu opened from a real Tribunal Stone is in use.
            if (!(player.containerMenu instanceof TribunalMenu menu) || !menu.stillValid(player)) return;

            player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                if (action == Action.BUY_LEVEL) {
                    int currentLevel = data.getJudgeLevel();
                    int cost = JudgeDataManager.getLevelCost(currentLevel);

                    if (data.getLifeScore() >= cost) {
                        data.addLifeScore(-cost);
                        data.setJudgeLevel(currentLevel + 1);
                        PacketHandler.sendToPlayer(new PacketJudgePopup("level_up", currentLevel + 1, 1, true), player);
                        player.sendSystemMessage(Component.translatable("message.judgemod.level_up").withStyle(ChatFormatting.GOLD));
                    } else {
                        player.displayClientMessage(Component.translatable("message.judgemod.insufficient_score").withStyle(ChatFormatting.RED), true);
                    }
                } else if (action == Action.CLEANSE) {
                    if (data.getFracturedComposureStacks() <= 0) {
                        player.displayClientMessage(Component.translatable("message.judgemod.no_stacks").withStyle(ChatFormatting.RED), true);
                    } else {
                        int nextLevelCost = JudgeDataManager.getLevelCost(data.getJudgeLevel());
                        int cleanseCost = Math.max(150, (int) (0.20 * nextLevelCost));

                        if (data.getLifeScore() >= cleanseCost) {
                            data.addLifeScore(-cleanseCost);
                            data.setFracturedComposureStacks(data.getFracturedComposureStacks() - 1);
                            player.sendSystemMessage(Component.translatable("message.judgemod.cleanse").withStyle(ChatFormatting.AQUA));
                        } else {
                            ItemStack offering = new ItemStack(ModItems.PURIFICATION_OFFERING.get());
                            if (player.getInventory().contains(offering)) {
                                player.getInventory().clearOrCountMatchingItems(p -> p.is(offering.getItem()), 1, player.inventoryMenu.getCraftSlots());
                                data.setFracturedComposureStacks(data.getFracturedComposureStacks() - 1);
                                player.sendSystemMessage(Component.translatable("message.judgemod.cleanse_offering").withStyle(ChatFormatting.AQUA));
                            } else {
                                player.displayClientMessage(Component.translatable("message.judgemod.insufficient_cleanse").withStyle(ChatFormatting.RED), true);
                            }
                        }
                    }
                }

                // Sync vanilla score
                player.setScore(data.getLifeScore());

                // Sync data after any action
                PacketHandler.syncJudgeData(player);

                int cost = JudgeDataManager.getLevelCost(data.getJudgeLevel());
                menu.updateData(data.getLifeScore(), data.getJudgeLevel(), data.getFracturedComposureStacks(), cost);
                PacketHandler.sendToPlayer(new PacketUpdateTribunalMenu(data.getLifeScore(), data.getJudgeLevel(), data.getFracturedComposureStacks(), cost), player);
            });
        });
    }
}
