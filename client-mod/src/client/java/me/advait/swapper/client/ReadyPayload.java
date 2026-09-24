package me.advait.swapper.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ReadyPayload(int protocol, int session) implements CustomPacketPayload {
  public static final Type<ReadyPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("swapper", "ready"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ReadyPayload> CODEC =
      StreamCodec.of(
          (buffer, value) -> {
            buffer.writeInt(value.protocol());
            buffer.writeInt(value.session());
          },
          buffer -> new ReadyPayload(buffer.readInt(), buffer.readInt()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
