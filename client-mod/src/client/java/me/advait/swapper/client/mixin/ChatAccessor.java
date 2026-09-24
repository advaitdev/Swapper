package me.advait.swapper.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.gui.components.ChatComponent.class)
public interface ChatAccessor {
  @Accessor("allMessages")
  java.util.List<net.minecraft.client.multiplayer.chat.GuiMessage> swapper$allMessages();

  @Accessor("chatScrollbarPos")
  int swapper$chatScrollbarPos();
}
