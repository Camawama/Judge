package net.camacraft.judgemod.client;

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

    private Button buyLevelButton;
    private Button cleanseButton;

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

        this.buyLevelButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.judgemod.buy_level"), button ->
                PacketHandler.sendToServer(new PacketTribunalAction(PacketTribunalAction.Action.BUY_LEVEL))
        ).bounds(x + 10, y + 20, 80, 20).build());

        this.cleanseButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.judgemod.cleanse"), button ->
                PacketHandler.sendToServer(new PacketTribunalAction(PacketTribunalAction.Action.CLEANSE))
        ).bounds(x + 10, y + 50, 80, 20).build());

        updateButtonStates();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (buyLevelButton != null) {
            buyLevelButton.active = this.menu.clientLifeScore >= this.menu.clientCostToNextLevel;
        }
        if (cleanseButton != null) {
            // Cleansing can also be paid with an offering, so stacks are the only hard requirement.
            cleanseButton.active = this.menu.clientFracturedComposureStacks > 0;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        // Suppress the default title/inventory labels; this screen draws its own text.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Draw info from the menu, which holds the server-synced data
        guiGraphics.drawString(this.font, Component.translatable("screen.judgemod.level", this.menu.clientJudgeLevel), x + 100, y + 20, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("screen.judgemod.score", this.menu.clientLifeScore), x + 100, y + 30, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("screen.judgemod.cost", this.menu.clientCostToNextLevel), x + 100, y + 40, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("screen.judgemod.stains", this.menu.clientFracturedComposureStacks), x + 100, y + 60, 0xFF5555);
    }
}
