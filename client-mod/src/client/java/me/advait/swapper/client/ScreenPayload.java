package me.advait.swapper.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ScreenPayload(
    int protocol,
    int session,
    boolean black,
    long seconds,
    boolean paused,
    boolean sneaking,
    boolean sprinting)
    implements CustomPacketPayload {
  public static final Type<ScreenPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("swapper", "screen"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPayload> CODEC =
      StreamCodec.of(
          (buffer, value) -> {
            buffer.writeInt(value.protocol());
            buffer.writeInt(value.session());
            buffer.writeBoolean(value.black());
            buffer.writeLong(value.seconds());
            buffer.writeBoolean(value.paused());
            buffer.writeBoolean(value.sneaking());
            buffer.writeBoolean(value.sprinting());
          },
          buffer ->
              new ScreenPayload(
                  buffer.readInt(),
                  buffer.readInt(),
                  buffer.readBoolean(),
                  buffer.readLong(),
                  buffer.readBoolean(),
                  buffer.readBoolean(),
                  buffer.readBoolean()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
