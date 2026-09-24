package me.advait.swapper.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HelloPayload(int protocol) implements CustomPacketPayload {
  public static final Type<HelloPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("swapper", "hello"));
  public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC =
      StreamCodec.of(
          (buffer, value) -> buffer.writeInt(value.protocol()),
          buffer -> new HelloPayload(buffer.readInt()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
