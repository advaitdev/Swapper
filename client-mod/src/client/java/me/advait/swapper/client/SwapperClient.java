package me.advait.swapper.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SwapperClient implements ClientModInitializer {
  private static final int PROTOCOL = 2;
  private static ScreenPayload screen;
  private static int acknowledgedSession;
  private static int helloTicks;
  private static RestorePayload pendingView;
  private static int viewReceivedTick;
  private static boolean releaseScreen;

  @Override
  public void onInitializeClient() {
    PayloadTypeRegistry.clientboundPlay().register(ScreenPayload.TYPE, ScreenPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(ReadyPayload.TYPE, ReadyPayload.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(CapturePayload.TYPE, CapturePayload.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(RestorePayload.TYPE, RestorePayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(ViewPayload.TYPE, ViewPayload.CODEC);
    ClientPlayNetworking.registerGlobalReceiver(
        ScreenPayload.TYPE,
        (payload, context) -> {
          if (payload.protocol() != PROTOCOL) return;
          if (payload.black()) {
            screen = payload;
            releaseScreen = false;
          } else {
            releaseScreen = true;
          }
        });
    ClientPlayNetworking.registerGlobalReceiver(
        CapturePayload.TYPE,
        (payload, context) -> {
          if (payload.protocol() == PROTOCOL && context.client().level != null && screen == null) {
            ClientPlayNetworking.send(
                new ViewPayload(PROTOCOL, payload.request(), ViewState.capture(context.client())));
          }
        });
    ClientPlayNetworking.registerGlobalReceiver(
        RestorePayload.TYPE,
        (payload, context) -> {
          if (payload.protocol() != PROTOCOL) return;
          pendingView = payload;
          viewReceivedTick = helloTicks;
        });
    ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          helloTicks++;
          if (client.getConnection() == null || client.player == null || client.level == null)
            return;
          if (pendingView != null && (screen == null || releaseScreen)) {
            try {
              ViewState.restore(
                  client,
                  pendingView.json(),
                  (int)
                      Math.min(
                          Integer.MAX_VALUE,
                          (long) pendingView.elapsed() + helloTicks - viewReceivedTick));
              pendingView = null;
            } catch (RuntimeException exception) {
              org.slf4j.LoggerFactory.getLogger("Swapper")
                  .error("Could not restore swap screen", exception);
              pendingView = null;
            }
          }
          if (releaseScreen && pendingView == null) {
            screen = null;
            releaseScreen = false;
          }
          if (helloTicks % 20 == 0 && ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
            ClientPlayNetworking.send(new HelloPayload(PROTOCOL));
            if (screen == null && ClientPlayNetworking.canSend(ViewPayload.TYPE)) {
              ClientPlayNetworking.send(new ViewPayload(PROTOCOL, 0, ViewState.capture(client)));
            }
          }
        });
  }

  private static void reset() {
    screen = null;
    acknowledgedSession = 0;
    helloTicks = 0;
    pendingView = null;
    releaseScreen = false;
  }

  public static boolean isBlackedOut() {
    return screen != null;
  }

  public static void extractBlackout(Minecraft client, GuiGraphicsExtractor graphics) {
    ScreenPayload state = screen;
    if (state == null || client.getConnection() == null) return;
    int width = graphics.guiWidth();
    int height = graphics.guiHeight();
    graphics.nextStratum();
    graphics.fill(0, 0, width, height, 0xFF000000);
    graphics.nextStratum();
    String title =
        state.paused() ? "Swapping paused" : "Swapping in " + Math.max(0, state.seconds());
    String[] status = {
      "Sneaking: ",
      state.sneaking() ? "Yes" : "No",
      ", Sprinting: ",
      state.sprinting() ? "Yes" : "No"
    };
    int[] colors = {
      0xFFFFAA00,
      state.sneaking() ? 0xFF55FF55 : 0xFFFF5555,
      0xFFFFAA00,
      state.sprinting() ? 0xFF55FF55 : 0xFFFF5555
    };
    int subtitleWidth = client.font.width(String.join("", status));
    float titleScale = Math.min(4f, (width * 0.65f) / Math.max(1, client.font.width(title)));
    float subtitleScale = Math.min(2f, (width * 0.62f) / Math.max(1, subtitleWidth));
    graphics.pose().pushMatrix();
    graphics.pose().translate(width / 2f, height / 2f);
    graphics.pose().scale(titleScale, titleScale);
    graphics.centeredText(client.font, title, 0, -10, 0xFFFFFF55);
    graphics.pose().popMatrix();
    graphics.pose().pushMatrix();
    graphics.pose().translate(width / 2f, height / 2f + 12f);
    graphics.pose().scale(subtitleScale, subtitleScale);
    int x = -subtitleWidth / 2;
    for (int i = 0; i < status.length; i++) {
      graphics.nextStratum();
      graphics.text(client.font, status[i], x, 5, colors[i]);
      x += client.font.width(status[i]);
    }
    graphics.pose().popMatrix();
    if (acknowledgedSession != state.session() && ClientPlayNetworking.canSend(ReadyPayload.TYPE)) {
      acknowledgedSession = state.session();
      ClientPlayNetworking.send(new ReadyPayload(PROTOCOL, state.session()));
    }
  }
}
