package net.camacraft.judgemod.network;

import net.camacraft.judgemod.JudgeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(JudgeMod.MODID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
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
}
