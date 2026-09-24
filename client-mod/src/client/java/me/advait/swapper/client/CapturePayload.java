package me.advait.swapper.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CapturePayload(int protocol, int request) implements CustomPacketPayload {
  public static final Type<CapturePayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("swapper", "capture"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CapturePayload> CODEC =
      StreamCodec.of(
          (buffer, value) -> {
            buffer.writeInt(value.protocol());
            buffer.writeInt(value.request());
          },
          buffer -> new CapturePayload(buffer.readInt(), buffer.readInt()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
