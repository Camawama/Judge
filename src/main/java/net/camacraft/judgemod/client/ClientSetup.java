package net.camacraft.judgemod.client;

import net.camacraft.camafy.client.gui.hud.HudOverlays;
import net.camacraft.camafy.client.gui.hud.HudOverlays.Gate;
import net.camacraft.judgemod.JudgeMod;
import net.camacraft.judgemod.menu.ModMenus;
import net.camacraft.judgemod.menu.TribunalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = JudgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.TRIBUNAL_MENU.get(), TribunalScreen::new));
        // ClientEventHandler self-registers via @Mod.EventBusSubscriber; do not register it again here.
    }

    /**
     * Real overlays instead of Post listeners on vanilla overlays: other mods cancel the XP bar's
     * Pre event while their own bars show, which also skips its Post and hid Judge's popups.
     * Camafy skips them while the GUI is hidden (F1) or there is no player, and on the death
     * screen (which draws the score itself, see DeathScreenMixin).
     */
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        HudOverlays.above(event, VanillaGuiOverlay.EXPERIENCE_BAR, "action_popups", ClientEventHandler::renderActionPopups, Gate.DEATH_SCREEN);
        HudOverlays.above(event, VanillaGuiOverlay.PLAYER_HEALTH, "score_hud", ClientEventHandler::renderScoreHud, Gate.DEATH_SCREEN);
    }

    public static void handleSyncPacket(int lifeScore, int judgeLevel, int lastDeathLifeScore, int fracturedComposureStacks) {
        ClientEventHandler.updateClientData(lifeScore, judgeLevel, lastDeathLifeScore, fracturedComposureStacks);
    }

    public static void handleTribunalUpdate(int lifeScore, int judgeLevel, int fracturedComposureStacks, int costToNextLevel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof TribunalMenu menu) {
            menu.updateData(lifeScore, judgeLevel, fracturedComposureStacks, costToNextLevel);
        }
    }

    public static void showPopup(String id, int value, int combo, boolean levelUp) {
        ClientEventHandler.showPopup(id, value, combo, levelUp);
    }
}
