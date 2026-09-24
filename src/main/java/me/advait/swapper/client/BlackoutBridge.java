package me.advait.swapper.client;

import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import me.advait.swapper.SwapperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

public final class BlackoutBridge implements PluginMessageListener {
  public static final int PROTOCOL = 2;
  public static final String HELLO = "swapper:hello";
  public static final String SCREEN = "swapper:screen";
  public static final String READY = "swapper:ready";
  public static final String CAPTURE = "swapper:capture";
  public static final String VIEW = "swapper:view";
  public static final String RESTORE = "swapper:restore";
  private final SwapperPlugin plugin;
  private final Set<UUID> clients = new HashSet<>();
  private final Map<UUID, Integer> sessions = new HashMap<>();
  private final Set<UUID> ready = new HashSet<>();
  private final Map<UUID, byte[]> latest = new HashMap<>();
  private final Map<UUID, byte[]> originals = new HashMap<>();
  private final Map<UUID, Capture> captures = new HashMap<>();
  private int sequence;

  private record Capture(int id, long started, Consumer<byte[]> callback, BukkitTask timeout) {}

  public BlackoutBridge(SwapperPlugin plugin) {
    this.plugin = plugin;
  }

  public void register() {
    var messenger = plugin.getServer().getMessenger();
    for (String channel : List.of(SCREEN, CAPTURE, RESTORE))
      messenger.registerOutgoingPluginChannel(plugin, channel);
    for (String channel : List.of(HELLO, READY, VIEW))
      messenger.registerIncomingPluginChannel(plugin, channel, this);
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
    if (bytes.length < 4 || ByteBuffer.wrap(bytes).getInt() != PROTOCOL) return;
    UUID id = player.getUniqueId();
    if (channel.equals(HELLO) && bytes.length == 4) {
      clients.add(id);
    } else if (channel.equals(READY) && bytes.length == 8) {
      if (Integer.valueOf(ByteBuffer.wrap(bytes).getInt(4)).equals(sessions.get(id))) ready.add(id);
    } else if (channel.equals(VIEW)
        && clients.contains(id)
        && bytes.length > 8
        && bytes.length <= 30008) {
      int request = ByteBuffer.wrap(bytes).getInt(4);
      Capture capture = captures.get(id);
      if (request != 0 && (capture == null || capture.id() != request)) return;
      if (request == 0 && plugin.getManager().isWaitingPlayer(id)) return;
      byte[] json = Arrays.copyOfRange(bytes, 8, bytes.length);
      try {
        var state =
            JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        int camera = state.get("camera").getAsInt();
        if (camera < 0 || camera > 2 || !state.get("messages").isJsonArray()) return;
      } catch (RuntimeException ignored) {
        return;
      }
      long now = System.nanoTime();
      long timestamp =
          capture != null && request != 0
              ? capture.started() + (now - capture.started()) / 2
              : now - Math.max(0, player.getPing()) * 500_000L;
      byte[] view = ByteBuffer.allocate(json.length + 8).putLong(timestamp).put(json).array();
      latest.put(id, view);
      if (request != 0) {
        captures.remove(id);
        capture.timeout().cancel();
        capture.callback().accept(view.clone());
      }
    }
  }

  public boolean hasClient(Player player) {
    return clients.contains(player.getUniqueId()) && latest.containsKey(player.getUniqueId());
  }

  public boolean isReady(Player player) {
    return ready.contains(player.getUniqueId());
  }

  public byte[] latestView(Player player) {
    byte[] view = latest.get(player.getUniqueId());
    return view == null ? null : view.clone();
  }

  public void rememberOriginal(Player player) {
    byte[] view = latestView(player);
    if (view != null) originals.put(player.getUniqueId(), view);
  }

  public void restoreOriginal(Player player) {
    restoreView(player, originals.remove(player.getUniqueId()));
  }

  public void captureView(Player player, Consumer<byte[]> callback) {
    int request = ++sequence;
    BukkitTask timeout =
        plugin
            .getServer()
            .getScheduler()
            .runTaskLater(
                plugin,
                () -> {
                  Capture capture = captures.remove(player.getUniqueId());
                  if (capture != null) capture.callback().accept(null);
                },
                40L);
    captures.put(player.getUniqueId(), new Capture(request, System.nanoTime(), callback, timeout));
    player.sendPluginMessage(
        plugin, CAPTURE, ByteBuffer.allocate(8).putInt(PROTOCOL).putInt(request).array());
  }

  public void cancelCaptures() {
    captures.values().forEach(capture -> capture.timeout().cancel());
    captures.clear();
  }

  public void restoreView(Player player, byte[] view) {
    if (view == null || !plugin.isEnabled()) return;
    long elapsed =
        Math.max(
            0,
            (System.nanoTime()
                    - ByteBuffer.wrap(view).getLong()
                    + Math.max(0, player.getPing()) * 500_000L)
                / 50_000_000L);
    byte[] data =
        ByteBuffer.allocate(view.length)
            .putInt(PROTOCOL)
            .putInt((int) Math.min(Integer.MAX_VALUE, elapsed))
            .put(view, 8, view.length - 8)
            .array();
    player.sendPluginMessage(plugin, RESTORE, data);
  }

  public void show(Player player, long seconds, boolean paused, Player active) {
    if (!hasClient(player)) return;
    int session = sessions.computeIfAbsent(player.getUniqueId(), id -> ++sequence);
    send(
        player,
        session,
        true,
        seconds,
        paused,
        active != null && active.isSneaking(),
        active != null && active.isSprinting());
  }

  public void hide(Player player) {
    ready.remove(player.getUniqueId());
    if (sessions.remove(player.getUniqueId()) != null) {
      send(player, ++sequence, false, 0, false, false, false);
      player.clearTitle();
    }
  }

  private void send(
      Player player,
      int session,
      boolean black,
      long seconds,
      boolean paused,
      boolean sneaking,
      boolean sprinting) {
    if (!plugin.isEnabled()) return;
    byte[] data =
        ByteBuffer.allocate(20)
            .putInt(PROTOCOL)
            .putInt(session)
            .put((byte) (black ? 1 : 0))
            .putLong(seconds)
            .put((byte) (paused ? 1 : 0))
            .put((byte) (sneaking ? 1 : 0))
            .put((byte) (sprinting ? 1 : 0))
            .array();
    player.sendPluginMessage(plugin, SCREEN, data);
  }

  public void forget(Player player) {
    hide(player);
    UUID id = player.getUniqueId();
    clients.remove(id);
    latest.remove(id);
    originals.remove(id);
    Capture capture = captures.remove(id);
    if (capture != null) {
      capture.timeout().cancel();
      capture.callback().accept(null);
    }
  }
}
