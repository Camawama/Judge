package net.camacraft.judgemod.network;

import net.camacraft.camafy.network.CamafyChannel;
import net.camacraft.judgemod.JudgeMod;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class PacketHandler {
    /**
     * Bumped whenever a packet format changes. Both sides must match exactly:
     * this mod is server-authoritative gameplay, so vanilla or mismatched
     * clients must not connect.
     */
    private static final String PROTOCOL_VERSION = "2";

    private static CamafyChannel INSTANCE;

    public static void register() {
        // Registration order sets the message ids (0-3); keep it when adding packets.
        // Each message is bound to its direction, so one arriving the wrong way is dropped.
        INSTANCE = CamafyChannel.create(new ResourceLocation(JudgeMod.MODID, "messages"), PROTOCOL_VERSION)
                .clientbound(PacketSyncJudgeData.class, PacketSyncJudgeData::toBytes, PacketSyncJudgeData::new, PacketSyncJudgeData::handle)
                .serverbound(PacketTribunalAction.class, PacketTribunalAction::toBytes, PacketTribunalAction::new, PacketTribunalAction::handle)
                .clientbound(PacketJudgePopup.class, PacketJudgePopup::toBytes, PacketJudgePopup::new, PacketJudgePopup::handle)
                .clientbound(PacketUpdateTribunalMenu.class, PacketUpdateTribunalMenu::toBytes, PacketUpdateTribunalMenu::new, PacketUpdateTribunalMenu::handle);
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.toServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.toPlayer(player, message);
    }

    /** Sends the player their current judge data snapshot. Single sync path for all server code. */
    public static void syncJudgeData(ServerPlayer player) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data ->
                sendToPlayer(new PacketSyncJudgeData(data.getLifeScore(), data.getJudgeLevel(),
                        data.getLastDeathLifeScore(), data.getFracturedComposureStacks()), player));
    }
}
