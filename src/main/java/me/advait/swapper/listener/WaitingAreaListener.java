package me.advait.swapper.listener;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import me.advait.swapper.SwapperPlugin;
import me.advait.swapper.manager.SwapperManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;

public class WaitingAreaListener implements Listener {
  private final SwapperPlugin plugin;
  private final SwapperManager manager;

  public WaitingAreaListener(SwapperPlugin plugin, SwapperManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  private boolean isWaiting(Player p) {
    return this.manager.isWaitingPlayer(p.getUniqueId());
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onDamage(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player p && this.isWaiting(p)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onAttack(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p && this.isWaiting(p)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onTarget(EntityTargetEvent e) {
    if (e.getTarget() instanceof Player p && this.isWaiting(p)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onFood(FoodLevelChangeEvent e) {
    if (e.getEntity() instanceof Player p && this.isWaiting(p)) {
      e.setCancelled(true);
      p.setFoodLevel(20);
      p.setSaturation(20.0F);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onDrop(PlayerDropItemEvent e) {
    if (this.isWaiting(e.getPlayer())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent e) {
    if (this.isWaiting(e.getPlayer())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent e) {
    if (this.isWaiting(e.getPlayer())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent e) {
    if (this.isWaiting(e.getPlayer())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onInteractEntity(PlayerInteractEntityEvent e) {
    if (this.isWaiting(e.getPlayer())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onWaitingMove(PlayerMoveEvent event) {
    if (!this.isWaiting(event.getPlayer())) return;
    Location target = this.manager.getWaitingLocation(event.getPlayer().getUniqueId());
    if (target != null) event.setTo(target);
  }

  @EventHandler
  public void onDisable(PluginDisableEvent event) {
    if (event.getPlugin() == this.plugin) {
      // Paper emits this before marking the plugin disabled; custom payloads can still be sent.
      this.manager.shutdown();
      this.manager.restoreAll();
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onSpectate(PlayerStartSpectatingEntityEvent event) {
    if (this.isWaiting(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void onSpectatorTeleport(PlayerTeleportEvent event) {
    if (this.isWaiting(event.getPlayer())
        && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    if (this.manager.isInPool(player.getUniqueId())) {
      try {
        // Restore before Paper saves the disconnecting player's data.
        this.manager.remove(player);
      } catch (SwapperManager.SwapperException exception) {
        this.plugin.getLogger().warning(exception.getMessage());
      }
    }
    this.plugin.getBlackout().forget(player);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    if (this.manager.isWaitingPlayer(p.getUniqueId())) {
      Bukkit.getScheduler()
          .runTaskLater(
              this.plugin,
              () -> {
                if (p.isOnline() && this.manager.isWaitingPlayer(p.getUniqueId())) {
                  this.manager.sendToWaiting(p);
                }
              },
              5L);
    }
  }
}
