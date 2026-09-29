package net.camacraft.judgemod.client;

import net.camacraft.camafy.util.color.Argb;
import net.camacraft.judgemod.JudgeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = JudgeMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    public static int clientLifeScore;
    public static int clientJudgeLevel;
    public static int clientLastDeathLifeScore;

    private static final List<ScorePopup> activePopups = new ArrayList<>();
    private static long totalScoreDisplayTime = 0;

    private static class ScorePopup {
        final String id;
        int value;
        int combo;
        boolean levelUp;
        long startTime;

        ScorePopup(String id, int value, int combo, boolean levelUp) {
            this.id = id;
            this.value = value;
            this.combo = combo;
            this.levelUp = levelUp;
            this.startTime = System.currentTimeMillis();
        }

        void updateValue(int newValue, int newCombo) {
            this.value = newValue;
            this.combo = newCombo;
            this.startTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > 2500;
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 2000) {
                return Math.max(0, 1.0f - (elapsed - 2000) / 500f);
            }
            return 1.0f;
        }
    }

    public static void updateClientData(int lifeScore, int judgeLevel, int lastDeathLifeScore, int fracturedComposureStacks) {
        clientLifeScore = lifeScore;
        clientJudgeLevel = judgeLevel;
        clientLastDeathLifeScore = lastDeathLifeScore;
    }

    public static void showPopup(String id, int value, int combo, boolean levelUp) {
        totalScoreDisplayTime = System.currentTimeMillis();
        for (ScorePopup popup : activePopups) {
            if (popup.id.equals(id)) {
                popup.updateValue(value, combo);
                return;
            }
        }
        activePopups.add(new ScorePopup(id, value, combo, levelUp));
        if (activePopups.size() > 5) {
            activePopups.remove(0);
        }
    }

    /** Stale data from a previous world must not flash on screen before the login sync arrives. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clientLifeScore = 0;
        clientJudgeLevel = 0;
        clientLastDeathLifeScore = 0;
        activePopups.clear();
        totalScoreDisplayTime = 0;
    }

    /**
     * Registered as its own overlay just above the XP bar (see {@link ClientSetup}), rather than
     * hooking the XP bar's Post event, so it still draws when another mod cancels the XP bar.
     * Not called while the GUI is hidden or on the death screen (the overlay's gates).
     */
    public static void renderActionPopups(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientConfig.DISPLAY_ACTION_POPUPS.get()) return;

        Minecraft mc = Minecraft.getInstance();

        activePopups.removeIf(ScorePopup::isExpired);
        if (activePopups.isEmpty()) return;

        int xOffset = ClientConfig.ACTION_POPUP_X.get();
        int yOffset = ClientConfig.ACTION_POPUP_Y.get();
        float scale = ClientConfig.ACTION_POPUP_SCALE.get().floatValue();

        for (int i = 0; i < activePopups.size(); i++) {
            ScorePopup popup = activePopups.get(i);
            int alpha = (int) (popup.getAlpha() * 255);
            // The font draws alpha below 4 fully opaque, so stop drawing there or the fade flashes.
            if (alpha < Argb.MIN_FONT_ALPHA) continue;

            String text = popup.levelUp
                    ? Component.translatable("popup.judgemod.level_up").getString()
                    : (popup.value > 0 ? "+" : "") + popup.value;
            int color = popup.levelUp ? 0xFFD700 : (popup.value > 0 ? 0x55FF55 : 0xFF5555);
            float popupScale = scale * (1.0f - i * 0.15f);
            int y = screenHeight / 2 - 30 - (i * 20) + yOffset;

            graphics.pose().pushPose();
            graphics.pose().translate(screenWidth / 2.0 + xOffset, y, 0);
            graphics.pose().scale(popupScale, popupScale, popupScale);

            color = (color & 0x00FFFFFF) | (alpha << 24);

            graphics.drawCenteredString(mc.font, text, 0, 0, color);
            if (popup.combo > 1) {
                graphics.drawString(mc.font, "x" + popup.combo, mc.font.width(text) / 2 + 5, -2, color);
            }
            graphics.pose().popPose();
        }
    }

    /**
     * Registered as its own overlay just above player health (see {@link ClientSetup}).
     * Not called while the GUI is hidden or on the death screen (the overlay's gates).
     */
    public static void renderScoreHud(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();

        // Score Display
        if (ClientConfig.DISPLAY_PERSISTENT_SCORE.get() || System.currentTimeMillis() - totalScoreDisplayTime < 2500) {
            float alpha = 1.0f;
            if (!ClientConfig.DISPLAY_PERSISTENT_SCORE.get() && System.currentTimeMillis() - totalScoreDisplayTime > 2000) {
                alpha = Math.max(0, 1.0f - (System.currentTimeMillis() - totalScoreDisplayTime - 2000) / 500f);
            }

            int xOffset = ClientConfig.SCORE_HUD_X.get();
            int yOffset = ClientConfig.SCORE_HUD_Y.get();
            float scale = ClientConfig.SCORE_HUD_SCALE.get().floatValue();

            String label = Component.translatable("hud.judgemod.score").getString() + " ";
            String scoreText = String.valueOf(clientLifeScore);
            int scoreColor = getScoreColor(clientLifeScore);
            int alphaInt = (int) (alpha * 255);
            int color = (scoreColor & 0x00FFFFFF) | (alphaInt << 24);
            int whiteColor = 0x00FFFFFF | (alphaInt << 24);

            int stringWidth = mc.font.width(label + scoreText);
            int x = screenWidth / 2 - stringWidth / 2 + xOffset;
            int y = yOffset;

            if (Argb.fontVisible(color)) {
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(scale, scale, scale);
                graphics.drawString(mc.font, label, 0, 0, whiteColor);
                graphics.drawString(mc.font, scoreText, mc.font.width(label), 0, color);
                graphics.pose().popPose();
            }
        }

        // Level Display
        if (ClientConfig.DISPLAY_PERSISTENT_LEVEL.get()) {
            int xOffset = ClientConfig.LEVEL_HUD_X.get();
            int yOffset = ClientConfig.LEVEL_HUD_Y.get();
            float scale = ClientConfig.LEVEL_HUD_SCALE.get().floatValue();

            String label = Component.translatable("hud.judgemod.level").getString() + " ";
            String levelText = String.valueOf(clientJudgeLevel);
            int levelColor = 0xFFFFFF00; // Always Yellow (Fully Opaque)

            int stringWidth = mc.font.width(label + levelText);
            int x = screenWidth / 2 - stringWidth / 2 + xOffset;
            int y = yOffset;

            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, scale);
            graphics.drawString(mc.font, label, 0, 0, 0xFFFFFF);
            graphics.drawString(mc.font, levelText, mc.font.width(label), 0, levelColor);
            graphics.pose().popPose();
        }
    }

    public static void renderDeathScreenScore(GuiGraphics graphics, int width, int y) {
        Minecraft mc = Minecraft.getInstance();

        // Score (Shifted up by 12 pixels)
        String scoreLabel = Component.translatable("hud.judgemod.score").getString() + " ";
        String scoreText = String.valueOf(clientLastDeathLifeScore);
        int scoreColor = getScoreColor(clientLastDeathLifeScore);
        int stringWidth = mc.font.width(scoreLabel + scoreText);
        int x = width / 2 - stringWidth / 2;

        graphics.drawString(mc.font, scoreLabel, x, y - 12, 0xFFFFFF);
        graphics.drawString(mc.font, scoreText, x + mc.font.width(scoreLabel), y - 12, scoreColor);

        // Level (At the original Y position)
        String levelLabel = Component.translatable("hud.judgemod.level").getString() + " ";
        String levelText = String.valueOf(clientJudgeLevel);
        int levelColor = 0xFFFFFF00; // Always Yellow (Fully Opaque)
        int levelStringWidth = mc.font.width(levelLabel + levelText);
        int levelX = width / 2 - levelStringWidth / 2;

        graphics.drawString(mc.font, levelLabel, levelX, y, 0xFFFFFF);
        graphics.drawString(mc.font, levelText, levelX + mc.font.width(levelLabel), y, levelColor);
    }

    private static int getScoreColor(int score) {
        float t;
        int r1, g1, b1, r2, g2, b2;

        if (score == 0) return 0xFFFFFF00; // Yellow (Fully Opaque)

        if (score > 0) {
            if (score <= 5000) { // Yellow to Bright Green
                t = score / 5000f;
                r1 = 255; g1 = 255; b1 = 0;   // Yellow
                r2 = 0;   g2 = 255; b2 = 0;   // Bright Green
            } else if (score <= 10000) { // Bright Green to Dark Green
                t = (score - 5000) / 5000f;
                r1 = 0;   g1 = 255; b1 = 0;   // Bright Green
                r2 = 0;   g2 = 100; b2 = 0;   // Dark Green
            } else { // Dark Green to Dark Purple
                t = Math.min(1.0f, (score - 10000) / 15000f);
                r1 = 0;   g1 = 100; b1 = 0;   // Dark Green
                r2 = 75;  g2 = 0;   b2 = 130; // Dark Purple
            }
        } else { // Negative
            if (score >= -5000) { // Yellow to Bright Red
                t = Math.abs(score) / 5000f;
                r1 = 255; g1 = 255; b1 = 0;   // Yellow
                r2 = 255; g2 = 0;   b2 = 0;   // Bright Red
            } else if (score >= -10000) { // Bright Red to Dark Red
                t = (Math.abs(score) - 5000) / 5000f;
                r1 = 255; g1 = 0;   b1 = 0;   // Bright Red
                r2 = 139; g2 = 0;   b2 = 0;   // Dark Red
            } else { // Dark Red to Black
                t = Math.min(1.0f, (Math.abs(score) - 10000) / 15000f);
                r1 = 139; g1 = 0;   b1 = 0;   // Dark Red
                r2 = 0;   g2 = 0;   b2 = 0;   // Black
            }
        }

        int r = (int) (r1 * (1 - t) + r2 * t);
        int g = (int) (g1 * (1 - t) + g2 * t);
        int b = (int) (b1 * (1 - t) + b2 * t);

        return (255 << 24) | (r << 16) | (g << 8) | b; // Ensure Alpha is 255
    }
}
