package net.camacraft.judgemod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.camacraft.judgemod.JudgeMod;
import net.camacraft.judgemod.menu.TribunalMenu;
import net.camacraft.judgemod.network.PacketHandler;
import net.camacraft.judgemod.network.PacketTribunalAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class TribunalScreen extends AbstractContainerScreen<TribunalMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(JudgeMod.MODID, "textures/gui/tribunal.png");

    public TribunalScreen(TribunalMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Buy Level"), button -> {
            PacketHandler.sendToServer(new PacketTribunalAction(PacketTribunalAction.Action.BUY_LEVEL));
        }).bounds(x + 10, y + 20, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cleanse"), button -> {
            PacketHandler.sendToServer(new PacketTribunalAction(PacketTribunalAction.Action.CLEANSE));
        }).bounds(x + 10, y + 50, 80, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
        
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // Draw info from the menu, which now holds the server-synced data
        int level = this.menu.clientJudgeLevel;
        int score = this.menu.clientLifeScore;
        int cost = this.menu.clientCostToNextLevel;
        int stacks = this.menu.clientFracturedComposureStacks;
        
        guiGraphics.drawString(this.font, "Level: " + level, x + 100, y + 20, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Score: " + score, x + 100, y + 30, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Cost: " + cost, x + 100, y + 40, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Stains: " + stacks, x + 100, y + 60, 0xFF5555);
    }
}
