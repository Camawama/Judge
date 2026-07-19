package net.camacraft.judgemod.network;

import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.client.ClientSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncJudgeData {
    private final int lifeScore;
    private final int judgeLevel;
    private final int lastDeathLifeScore;
    private final int fracturedComposureStacks;

    public PacketSyncJudgeData(int lifeScore, int judgeLevel, int lastDeathLifeScore, int fracturedComposureStacks) {
        this.lifeScore = lifeScore;
        this.judgeLevel = judgeLevel;
        this.lastDeathLifeScore = lastDeathLifeScore;
        this.fracturedComposureStacks = fracturedComposureStacks;
    }

    public PacketSyncJudgeData(FriendlyByteBuf buf) {
        this.lifeScore = buf.readInt();
        this.judgeLevel = buf.readInt();
        this.lastDeathLifeScore = buf.readInt();
        this.fracturedComposureStacks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(lifeScore);
        buf.writeInt(judgeLevel);
        buf.writeInt(lastDeathLifeScore);
        buf.writeInt(fracturedComposureStacks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // Client side handling
            // We need to access the client player safely
            ClientSetup.handleSyncPacket(lifeScore, judgeLevel, lastDeathLifeScore, fracturedComposureStacks);
        });
        return true;
    }
}
