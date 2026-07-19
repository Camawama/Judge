package net.camacraft.judgemod.client;

import net.camacraft.judgemod.menu.ModMenus;
import net.camacraft.judgemod.menu.TribunalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.TRIBUNAL_MENU.get(), TribunalScreen::new);
        });
        
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
    }

    public static void handleSyncPacket(int lifeScore, int judgeLevel, int lastDeathLifeScore, int fracturedComposureStacks) {
        ClientEventHandler.updateClientData(lifeScore, judgeLevel, lastDeathLifeScore, fracturedComposureStacks);
    }

    public static void handleTribunalUpdate(int lifeScore, int judgeLevel, int fracturedComposureStacks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof TribunalMenu menu) {
            menu.updateData(lifeScore, judgeLevel, fracturedComposureStacks);
        }
    }

    public static void showPopup(String id, int value, int combo) {
        ClientEventHandler.showPopup(id, value, combo);
    }
}
