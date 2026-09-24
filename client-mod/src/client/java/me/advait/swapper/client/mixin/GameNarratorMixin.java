package me.advait.swapper.client.mixin;

import me.advait.swapper.client.SwapperClient;
import net.minecraft.client.GameNarrator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameNarrator.class)
public abstract class GameNarratorMixin {
  @Inject(method = "narrateMessage", at = @At("HEAD"), cancellable = true)
  private void swapper$muteNarrator(String message, boolean interrupt, CallbackInfo callback) {
    if (SwapperClient.isBlackedOut()) callback.cancel();
  }
}
