package net.camacraft.judgemod.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.judgemod.client.ClientSetup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateTribunalMenu {
    private final int lifeScore;
    private final int judgeLevel;
    private final int fracturedComposureStacks;
    private final int costToNextLevel;

    public PacketUpdateTribunalMenu(int lifeScore, int judgeLevel, int fracturedComposureStacks, int costToNextLevel) {
        this.lifeScore = lifeScore;
        this.judgeLevel = judgeLevel;
        this.fracturedComposureStacks = fracturedComposureStacks;
        this.costToNextLevel = costToNextLevel;
    }

    public PacketUpdateTribunalMenu(FriendlyByteBuf buf) {
        this.lifeScore = buf.readInt();
        this.judgeLevel = buf.readInt();
        this.fracturedComposureStacks = buf.readInt();
        this.costToNextLevel = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(lifeScore);
        buf.writeInt(judgeLevel);
        buf.writeInt(fracturedComposureStacks);
        buf.writeInt(costToNextLevel);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        // The cost travels with the packet: the level-cost config lives in server
        // datapacks, so the client must never recompute it locally.
        Packets.handleClient(supplier, () -> () ->
                ClientSetup.handleTribunalUpdate(lifeScore, judgeLevel, fracturedComposureStacks, costToNextLevel));
    }
}
