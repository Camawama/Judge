package net.camacraft.judgemod.mixin;

import net.camacraft.judgemod.client.ClientEventHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {

    @Shadow private Component causeOfDeath;

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 1)
    private Component judgemod$hideVanillaScore(Component text) {
        // Match the vanilla score line by its translation key, not its rendered
        // English text, so this works in every locale.
        if (text != null && text.getContents() instanceof TranslatableContents translatable
                && translatable.getKey().startsWith("deathScreen.score")) {
            return Component.empty();
        }
        return text;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void judgemod$renderCustomScore(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick, CallbackInfo ci) {
        int y = (this.causeOfDeath != null) ? 115 : 100;
        ClientEventHandler.renderDeathScreenScore(pGuiGraphics, ((DeathScreen)(Object)this).width, y);
    }
}
