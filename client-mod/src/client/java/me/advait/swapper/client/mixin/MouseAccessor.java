package me.advait.swapper.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.MouseHandler.class)
public interface MouseAccessor {
  @Accessor("xpos")
  void swapper$setxpos(double value);

  @Accessor("ypos")
  void swapper$setypos(double value);
}
