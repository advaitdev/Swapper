package me.advait.swapper.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.gui.screens.ChatScreen.class)
public interface ChatScreenAccessor {
  @Accessor("input")
  net.minecraft.client.gui.components.EditBox swapper$input();
}
