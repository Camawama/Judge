package net.camacraft.judgemod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.camacraft.judgemod.capability.JudgeData;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.network.PacketHandler;
import net.camacraft.judgemod.network.PacketSyncJudgeData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class JudgeCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("judge")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("get")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("score").executes(context -> getScore(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                    .then(Commands.literal("level").executes(context -> getLevel(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                    .then(Commands.literal("history").executes(context -> getHistory(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                )
            )
            .then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("score")
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(context -> setScore(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))
                    )
                    .then(Commands.literal("level")
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(context -> setLevel(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))
                    )
                )
            )
        );
    }

    private static int getScore(CommandSourceStack source, ServerPlayer player) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            source.sendSuccess(() -> Component.literal(player.getDisplayName().getString() + "'s Life Score: " + data.getLifeScore()), false);
        });
        return 1;
    }

    private static int getLevel(CommandSourceStack source, ServerPlayer player) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            source.sendSuccess(() -> Component.literal(player.getDisplayName().getString() + "'s Judge Level: " + data.getJudgeLevel()), false);
        });
        return 1;
    }

    private static int getHistory(CommandSourceStack source, ServerPlayer player) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            source.sendSuccess(() -> Component.literal("--- Action History for " + player.getDisplayName().getString() + " ---").withStyle(ChatFormatting.GOLD), false);
            List<JudgeData.ActionRecord> history = data.getChronologicalHistory();
            if (history.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No history recorded.").withStyle(ChatFormatting.GRAY), false);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                for (JudgeData.ActionRecord record : history) {
                    String time = sdf.format(new Date(record.timestamp));
                    String sign = record.delta > 0 ? "+" : "";
                    ChatFormatting color = record.delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
                    source.sendSuccess(() -> Component.literal("[" + time + "] " + record.actionId + ": ")
                            .append(Component.literal(sign + record.delta).withStyle(color)), false);
                }
            }
        });
        return 1;
    }

    private static int setScore(CommandSourceStack source, ServerPlayer player, int amount) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            data.setLifeScore(amount);
            PacketHandler.sendToPlayer(new PacketSyncJudgeData(data.getLifeScore(), data.getJudgeLevel(), data.getLastDeathLifeScore(), data.getFracturedComposureStacks()), player);
            source.sendSuccess(() -> Component.literal("Set " + player.getDisplayName().getString() + "'s Life Score to " + amount), true);
        });
        return 1;
    }

    private static int setLevel(CommandSourceStack source, ServerPlayer player, int amount) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            data.setJudgeLevel(amount);
            PacketHandler.sendToPlayer(new PacketSyncJudgeData(data.getLifeScore(), data.getJudgeLevel(), data.getLastDeathLifeScore(), data.getFracturedComposureStacks()), player);
            source.sendSuccess(() -> Component.literal("Set " + player.getDisplayName().getString() + "'s Judge Level to " + amount), true);
        });
        return 1;
    }
}
