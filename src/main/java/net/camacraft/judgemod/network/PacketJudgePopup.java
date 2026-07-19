package net.camacraft.judgemod.network;

import net.camacraft.judgemod.client.ClientSetup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketJudgePopup {
    private final String id;
    private final int value;
    private final int combo;

    public PacketJudgePopup(String id, int value, int combo) {
        this.id = id;
        this.value = value;
        this.combo = combo;
    }

    public PacketJudgePopup(FriendlyByteBuf buf) {
        this.id = buf.readUtf();
        this.value = buf.readInt();
        this.combo = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeInt(value);
        buf.writeInt(combo);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientSetup.showPopup(id, value, combo);
        });
        return true;
    }
}
