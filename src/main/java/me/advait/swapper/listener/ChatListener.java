package me.advait.swapper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.advait.swapper.SwapperPlugin;
import me.advait.swapper.manager.SwapperManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {
  private final SwapperPlugin plugin;
  private final SwapperManager manager;

  public ChatListener(SwapperPlugin plugin, SwapperManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  private boolean isWaiting(Player p) {
    return this.manager.isWaitingPlayer(p.getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onChat(AsyncChatEvent e) {
    e.viewers().removeIf(v -> v instanceof Player p && this.isWaiting(p));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onDeath(PlayerDeathEvent e) {
    Component msg = e.deathMessage();
    if (msg != null) {
      e.deathMessage(null);
      this.broadcastToNonWaiting(msg, null);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onJoin(PlayerJoinEvent e) {
    Component msg = e.joinMessage();
    if (msg != null) {
      e.joinMessage(null);
      this.broadcastToNonWaiting(msg, e.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onQuit(PlayerQuitEvent e) {
    Component msg = e.quitMessage();
    if (msg != null) {
      e.quitMessage(null);
      this.broadcastToNonWaiting(msg, e.getPlayer());
    }
  }

  private void broadcastToNonWaiting(Component msg, Player exclude) {
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (!viewer.equals(exclude) && !this.isWaiting(viewer)) {
        viewer.sendMessage(msg);
      }
    }

    Bukkit.getConsoleSender().sendMessage(msg);
  }
}
