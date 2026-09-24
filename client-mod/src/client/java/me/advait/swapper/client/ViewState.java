package me.advait.swapper.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.*;
import me.advait.swapper.client.mixin.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.lwjgl.glfw.GLFW;

public final class ViewState {
  private ViewState() {}

  public static String capture(Minecraft client) {
    JsonObject state = new JsonObject();
    state.addProperty("camera", client.options.getCameraType().ordinal());
    state.addProperty("hudHidden", client.gui.hud.isHidden());
    var screen = client.gui.screen();
    boolean inventory =
        screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    state.addProperty(
        "screen", inventory ? "inventory" : screen instanceof ChatScreen ? "chat" : "none");
    double x = client.mouseHandler.getScaledXPos(client.getWindow());
    double y = client.mouseHandler.getScaledYPos(client.getWindow());
    if (inventory) {
      var container = (ContainerAccessor) screen;
      x -= container.swapper$leftPos();
      y -= container.swapper$topPos();
    } else {
      x /= client.getWindow().getGuiScaledWidth();
      y /= client.getWindow().getGuiScaledHeight();
    }
    state.addProperty("x", x);
    state.addProperty("y", y);
    if (screen instanceof ChatScreen) {
      var input = ((ChatScreenAccessor) screen).swapper$input();
      state.addProperty("draft", input.getValue());
      state.addProperty("caret", input.getCursorPosition());
    }
    var chat = (ChatAccessor) client.gui.hud.getChat();
    state.addProperty("scroll", chat.swapper$chatScrollbarPos());
    JsonArray messages = new JsonArray();
    state.add("messages", messages);
    int bytes = state.toString().getBytes(StandardCharsets.UTF_8).length;
    for (GuiMessage message : chat.swapper$allMessages()) {
      JsonObject entry = new JsonObject();
      entry.addProperty("age", Math.max(0, client.gui.hud.getGuiTicks() - message.addedTime()));
      entry.add("content", encode(client, message.content()));
      entry.addProperty("source", message.source().name());
      if (message.tag() != null) {
        var tag = message.tag();
        JsonObject tagData = new JsonObject();
        tagData.addProperty("color", tag.indicatorColor());
        if (tag.icon() != null) tagData.addProperty("icon", tag.icon().name());
        if (tag.text() != null) tagData.add("text", encode(client, tag.text()));
        if (tag.logTag() != null) tagData.addProperty("log", tag.logTag());
        entry.add("tag", tagData);
      }
      int size = entry.toString().getBytes(StandardCharsets.UTF_8).length + 1;
      if (bytes + size > 29000 || messages.size() >= 100) break;
      bytes += size;
      messages.add(entry);
    }
    return state.toString();
  }

  public static void restore(Minecraft client, String json, int elapsed) {
    JsonObject state = JsonParser.parseString(json).getAsJsonObject();
    int camera = state.get("camera").getAsInt();
    if (camera < 0 || camera >= CameraType.values().length)
      throw new IllegalArgumentException("Invalid camera");
    List<GuiMessage> messages = new ArrayList<>();
    int now = client.gui.hud.getGuiTicks();
    for (JsonElement element : state.getAsJsonArray("messages")) {
      if (messages.size() >= 100) break;
      var entry = element.getAsJsonObject();
      int age =
          (int)
              Math.min(
                  1_000_000L, Math.max(0L, entry.get("age").getAsLong()) + Math.max(0L, elapsed));
      GuiMessageTag tag = null;
      if (entry.has("tag")) {
        var data = entry.getAsJsonObject("tag");
        tag =
            new GuiMessageTag(
                data.get("color").getAsInt(),
                data.has("icon")
                    ? GuiMessageTag.Icon.valueOf(data.get("icon").getAsString())
                    : null,
                data.has("text") ? decode(client, data.get("text")) : null,
                data.has("log") ? data.get("log").getAsString() : null);
      }
      messages.add(
          new GuiMessage(
              now - age,
              decode(client, entry.get("content")),
              null,
              GuiMessageSource.valueOf(entry.get("source").getAsString()),
              tag));
    }
    client.options.setCameraType(CameraType.values()[camera]);
    if (state.has("hudHidden")
        && state.get("hudHidden").getAsBoolean() != client.gui.hud.isHidden())
      client.gui.hud.toggle();
    String kind = state.has("screen") ? state.get("screen").getAsString() : "none";
    client.gui.setScreen(
        switch (kind) {
          case "inventory" -> new InventoryScreen(client.player);
          case "chat" ->
              new ChatScreen(state.has("draft") ? state.get("draft").getAsString() : "", false);
          default -> null;
        });
    var chat = client.gui.hud.getChat();
    chat.restoreState(
        new ChatComponent.State(messages, List.copyOf(chat.getRecentChat()), List.of()));
    chat.resetChatScroll();
    if (state.has("scroll")) chat.scrollChat(Math.max(0, state.get("scroll").getAsInt()));
    if (client.gui.screen() instanceof ChatScreen && state.has("caret")) {
      ((ChatScreenAccessor) client.gui.screen())
          .swapper$input()
          .setCursorPosition(state.get("caret").getAsInt());
    }
    if (client.gui.screen() != null && state.has("x") && state.has("y")) {
      double x = state.get("x").getAsDouble();
      double y = state.get("y").getAsDouble();
      if (client.gui.screen() instanceof AbstractContainerScreen<?> container) {
        x += ((ContainerAccessor) container).swapper$leftPos();
        y += ((ContainerAccessor) container).swapper$topPos();
      } else {
        x *= client.getWindow().getGuiScaledWidth();
        y *= client.getWindow().getGuiScaledHeight();
      }
      if (Double.isFinite(x) && Double.isFinite(y)) {
        var window = client.getWindow();
        x =
            Math.clamp(
                x / window.getGuiScaledWidth() * window.getScreenWidth(),
                0,
                window.getScreenWidth());
        y =
            Math.clamp(
                y / window.getGuiScaledHeight() * window.getScreenHeight(),
                0,
                window.getScreenHeight());
        GLFW.glfwSetCursorPos(window.handle(), x, y);
        ((MouseAccessor) client.mouseHandler).swapper$setxpos(x);
        ((MouseAccessor) client.mouseHandler).swapper$setypos(y);
      }
    }
  }

  private static JsonElement encode(Minecraft client, Component text) {
    return ComponentSerialization.CODEC
        .encodeStart(
            client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), text)
        .result()
        .orElseGet(() -> new JsonPrimitive(text.getString()));
  }

  private static Component decode(Minecraft client, JsonElement json) {
    return ComponentSerialization.CODEC
        .parse(client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
        .result()
        .orElseGet(() -> Component.literal(""));
  }
}
