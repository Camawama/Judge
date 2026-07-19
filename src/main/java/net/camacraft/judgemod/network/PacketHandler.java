package net.camacraft.judgemod.network;

import net.camacraft.judgemod.JudgeMod;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    /**
     * Bumped whenever a packet format changes. Both sides must match exactly:
     * this mod is server-authoritative gameplay, so vanilla or mismatched
     * clients must not connect.
     */
    private static final String PROTOCOL_VERSION = "2";

    private static SimpleChannel INSTANCE;
    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(JudgeMod.MODID, "messages"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(PacketSyncJudgeData.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketSyncJudgeData::new)
                .encoder(PacketSyncJudgeData::toBytes)
                .consumerMainThread(PacketSyncJudgeData::handle)
                .add();

        net.messageBuilder(PacketTribunalAction.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketTribunalAction::new)
                .encoder(PacketTribunalAction::toBytes)
                .consumerMainThread(PacketTribunalAction::handle)
                .add();

        net.messageBuilder(PacketJudgePopup.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketJudgePopup::new)
                .encoder(PacketJudgePopup::toBytes)
                .consumerMainThread(PacketJudgePopup::handle)
                .add();

        net.messageBuilder(PacketUpdateTribunalMenu.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketUpdateTribunalMenu::new)
                .encoder(PacketUpdateTribunalMenu::toBytes)
                .consumerMainThread(PacketUpdateTribunalMenu::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Sends the player their current judge data snapshot. Single sync path for all server code. */
    public static void syncJudgeData(ServerPlayer player) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data ->
                sendToPlayer(new PacketSyncJudgeData(data.getLifeScore(), data.getJudgeLevel(),
                        data.getLastDeathLifeScore(), data.getFracturedComposureStacks()), player));
    }
}
