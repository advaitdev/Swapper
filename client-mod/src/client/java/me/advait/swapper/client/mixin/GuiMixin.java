package me.advait.swapper.client.mixin;

import me.advait.swapper.client.SwapperClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
  @Shadow @Final private Minecraft minecraft;
  @Shadow @Final private GuiRenderState guiRenderState;

  @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
  private void swapper$blackout(
      DeltaTracker deltaTracker, boolean advanceGameTime, boolean loaded, CallbackInfo ci) {
    if (!SwapperClient.isBlackedOut() || minecraft.getConnection() == null) return;
    guiRenderState.reset();
    guiRenderState.isHudHidden = true;
    SwapperClient.extractBlackout(
        minecraft, new GuiGraphicsExtractor(minecraft, guiRenderState, 0, 0));
    ci.cancel();
  }
}
