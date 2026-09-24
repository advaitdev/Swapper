package me.advait.swapper.client;

import java.nio.charset.StandardCharsets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ViewPayload(int protocol, int request, String json) implements CustomPacketPayload {
  public static final Type<ViewPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("swapper", "view"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> CODEC =
      StreamCodec.of(
          (buffer, value) -> {
            buffer.writeInt(value.protocol());
            buffer.writeInt(value.request());
            buffer.writeBytes(value.json().getBytes(StandardCharsets.UTF_8));
          },
          buffer -> {
            int protocol = buffer.readInt();
            int request = buffer.readInt();
            if (buffer.readableBytes() > 30000)
              throw new IllegalArgumentException("Screen state too large");
            String json =
                buffer.toString(
                    buffer.readerIndex(), buffer.readableBytes(), StandardCharsets.UTF_8);
            buffer.skipBytes(buffer.readableBytes());
            return new ViewPayload(protocol, request, json);
          });

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
