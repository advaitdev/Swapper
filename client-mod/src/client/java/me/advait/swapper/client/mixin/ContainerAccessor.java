package me.advait.swapper.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
public interface ContainerAccessor {
  @Accessor("leftPos")
  int swapper$leftPos();

  @Accessor("topPos")
  int swapper$topPos();
}
