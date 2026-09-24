package me.advait.swapper.client.mixin;

import me.advait.swapper.client.SwapperClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
  @Inject(method = "play", at = @At("HEAD"), cancellable = true)
  private void swapper$mute(
      SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
    if (SwapperClient.isBlackedOut()) callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
  }

  @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
  private void swapper$muteDelayed(SoundInstance sound, int delay, CallbackInfo callback) {
    if (SwapperClient.isBlackedOut()) callback.cancel();
  }

  @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
  private void swapper$muteTicking(TickableSoundInstance sound, CallbackInfo callback) {
    if (SwapperClient.isBlackedOut()) callback.cancel();
  }
}
