package net.camacraft.judgemod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.camacraft.judgemod.capability.IJudgeData;
import net.camacraft.judgemod.capability.JudgeData;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class JudgeCommands {
    private static final int HISTORY_PAGE_SIZE = 10;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("judge")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("get")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("score").executes(context -> getScore(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                    .then(Commands.literal("level").executes(context -> getLevel(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                    .then(Commands.literal("composure").executes(context -> getComposure(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                    .then(Commands.literal("history")
                        .executes(context -> getHistory(context.getSource(), EntityArgument.getPlayer(context, "player"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(context -> getHistory(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "page")))))
                )
            )
            .then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("score")
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(context -> setScore(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))
                    )
                    .then(Commands.literal("level")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(context -> setLevel(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))
                    )
                    .then(Commands.literal("composure")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(context -> setComposure(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))
                    )
                )
            )
            .then(Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("score")
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(context -> addScore(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))
                    )
                )
            )
            .then(Commands.literal("reset")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> reset(context.getSource(), EntityArgument.getPlayer(context, "player")))
                )
            )
        );
    }

    @Nullable
    private static IJudgeData data(CommandSourceStack source, ServerPlayer player) {
        IJudgeData data = player.getCapability(JudgeDataProvider.JUDGE_DATA).orElse(null);
        if (data == null) {
            source.sendFailure(Component.literal("No judge data found for " + player.getDisplayName().getString()));
        }
        return data;
    }

    private static void syncAfterChange(ServerPlayer player, IJudgeData data) {
        player.setScore(data.getLifeScore()); // Keep the vanilla score in step immediately
        PacketHandler.syncJudgeData(player);
    }

    private static int getScore(CommandSourceStack source, ServerPlayer player) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        source.sendSuccess(() -> Component.literal(player.getDisplayName().getString() + "'s Life Score: " + data.getLifeScore()), false);
        return 1;
    }

    private static int getLevel(CommandSourceStack source, ServerPlayer player) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        source.sendSuccess(() -> Component.literal(player.getDisplayName().getString() + "'s Judge Level: " + data.getJudgeLevel()), false);
        return 1;
    }

    private static int getComposure(CommandSourceStack source, ServerPlayer player) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        source.sendSuccess(() -> Component.literal(player.getDisplayName().getString() + "'s Fractured Composure stacks: " + data.getFracturedComposureStacks()), false);
        return 1;
    }

    private static int getHistory(CommandSourceStack source, ServerPlayer player, int page) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;

        List<JudgeData.ActionRecord> history = data.getChronologicalHistory();
        if (history.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No history recorded.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        int totalPages = (history.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE;
        int clampedPage = Math.min(page, totalPages);
        source.sendSuccess(() -> Component.literal("--- Action History for " + player.getDisplayName().getString()
                + " (page " + clampedPage + "/" + totalPages + ", newest first) ---").withStyle(ChatFormatting.GOLD), false);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        // Newest first: page 1 starts from the end of the list
        int start = history.size() - 1 - (clampedPage - 1) * HISTORY_PAGE_SIZE;
        int end = Math.max(start - HISTORY_PAGE_SIZE + 1, 0);
        for (int i = start; i >= end; i--) {
            JudgeData.ActionRecord record = history.get(i);
            String time = sdf.format(new Date(record.timestamp));
            String sign = record.delta > 0 ? "+" : "";
            ChatFormatting color = record.delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
            source.sendSuccess(() -> Component.literal("[" + time + "] " + record.actionId + ": ")
                    .append(Component.literal(sign + record.delta).withStyle(color)), false);
        }
        return 1;
    }

    private static int setScore(CommandSourceStack source, ServerPlayer player, int amount) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        data.setLifeScore(amount);
        syncAfterChange(player, data);
        source.sendSuccess(() -> Component.literal("Set " + player.getDisplayName().getString() + "'s Life Score to " + amount), true);
        return 1;
    }

    private static int addScore(CommandSourceStack source, ServerPlayer player, int amount) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        data.addLifeScore(amount);
        syncAfterChange(player, data);
        source.sendSuccess(() -> Component.literal("Added " + amount + " to " + player.getDisplayName().getString() + "'s Life Score (now " + data.getLifeScore() + ")"), true);
        return 1;
    }

    private static int setLevel(CommandSourceStack source, ServerPlayer player, int amount) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        data.setJudgeLevel(amount);
        syncAfterChange(player, data);
        source.sendSuccess(() -> Component.literal("Set " + player.getDisplayName().getString() + "'s Judge Level to " + amount), true);
        return 1;
    }

    private static int setComposure(CommandSourceStack source, ServerPlayer player, int amount) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        data.setFracturedComposureStacks(amount);
        syncAfterChange(player, data);
        source.sendSuccess(() -> Component.literal("Set " + player.getDisplayName().getString() + "'s Fractured Composure stacks to " + data.getFracturedComposureStacks()), true);
        return 1;
    }

    /** Resets Life Score, composure, combos, and the survive timer. Judge Level and history are kept. */
    private static int reset(CommandSourceStack source, ServerPlayer player) {
        IJudgeData data = data(source, player);
        if (data == null) return 0;
        data.resetForNewLife();
        data.setFracturedComposureStacks(0);
        syncAfterChange(player, data);
        source.sendSuccess(() -> Component.literal("Reset " + player.getDisplayName().getString() + "'s Life Score and composure (Judge Level kept)"), true);
        return 1;
    }
}
