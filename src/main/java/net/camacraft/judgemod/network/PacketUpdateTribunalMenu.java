package net.camacraft.judgemod.network;

import net.camacraft.judgemod.client.ClientSetup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateTribunalMenu {
    private final int lifeScore;
    private final int judgeLevel;
    private final int fracturedComposureStacks;

    public PacketUpdateTribunalMenu(int lifeScore, int judgeLevel, int fracturedComposureStacks) {
        this.lifeScore = lifeScore;
        this.judgeLevel = judgeLevel;
        this.fracturedComposureStacks = fracturedComposureStacks;
    }

    public PacketUpdateTribunalMenu(FriendlyByteBuf buf) {
        this.lifeScore = buf.readInt();
        this.judgeLevel = buf.readInt();
        this.fracturedComposureStacks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(lifeScore);
        buf.writeInt(judgeLevel);
        buf.writeInt(fracturedComposureStacks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientSetup.handleTribunalUpdate(lifeScore, judgeLevel, fracturedComposureStacks);
        });
        return true;
    }
}
