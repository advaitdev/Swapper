package me.advait.swapper.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.advait.swapper.SwapperPlugin;
import me.advait.swapper.config.SwapperConfig;
import me.advait.swapper.discord.DiscordManager;
import me.advait.swapper.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class SwapperManager {
  private static final int PRELOAD_LEAD_TICKS = 100;
  private static final long ANCHOR_PERIOD_TICKS = 20L;
  private static final double ANCHOR_DRIFT_SQ = 4.0;
  private final SwapperPlugin plugin;
  private final SwapperConfig config;
  private final DiscordManager discord;
  private final List<UUID> order = new ArrayList<>();
  private final Map<UUID, PlayerSnapshot> originalStates = new HashMap<>();
  private final Set<UUID> advancementSyncing = new HashSet<>();
  private final Set<UUID> preloading = new HashSet<>();
  private final Map<UUID, BukkitTask> invincibilityTasks = new HashMap<>();
  private final Map<UUID, Boolean> previousInvulnerability = new HashMap<>();
  // Async chat reads this immutable snapshot; all gameplay mutations run on the server thread.
  private volatile Set<UUID> waitingPlayers = Set.of();
  private int activeIndex = 0;
  private boolean running = false;
  private long ticksUntilSwap = 0;
  private BukkitTask timerTask;
  private BukkitTask anchorTask;
  private UUID preloadingPlayer;
  private Location preloadAnchor;

  public SwapperManager(SwapperPlugin plugin, SwapperConfig config, DiscordManager discord) {
    this.plugin = plugin;
    this.config = config;
    this.discord = discord;
  }

  public void start() {
    this.anchorTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                this.plugin, this::anchorWaiting, ANCHOR_PERIOD_TICKS, ANCHOR_PERIOD_TICKS);
  }

  public void shutdown() {
    this.stopTimer();
    if (this.anchorTask != null) {
      this.anchorTask.cancel();
      this.anchorTask = null;
    }
  }

  public List<UUID> getOrder() {
    return List.copyOf(this.order);
  }

  public boolean isRunning() {
    return this.running;
  }

  public boolean isInPool(UUID id) {
    return this.order.contains(id);
  }

  public boolean isActivePlayer(UUID id) {
    return !this.order.isEmpty() && this.order.get(this.activeIndex).equals(id);
  }

  public boolean isWaitingPlayer(UUID id) {
    return this.waitingPlayers.contains(id);
  }

  private void refreshWaitingPlayers() {
    Set<UUID> waiting = new HashSet<>(this.order);
    if (!this.order.isEmpty()) {
      waiting.remove(this.order.get(this.activeIndex));
    }
    this.waitingPlayers = Set.copyOf(waiting);
  }

  public boolean isAdvancementSyncing(UUID id) {
    return this.advancementSyncing.contains(id);
  }

  public void markAdvancementSyncing(UUID id) {
    this.advancementSyncing.add(id);
  }

  public void unmarkAdvancementSyncing(UUID id) {
    this.advancementSyncing.remove(id);
  }

  public boolean isPreloading(UUID id) {
    return this.preloading.contains(id);
  }

  public Location getPreloadAnchor() {
    return this.preloadAnchor;
  }

  public void add(Player p) throws SwapperManager.SwapperException {
    UUID id = p.getUniqueId();
    if (this.order.contains(id)) {
      throw new SwapperManager.SwapperException(p.getName() + " is already in the swap pool.");
    }

    this.originalStates.put(id, PlayerSnapshot.capture(p));
    this.order.add(id);
    this.refreshWaitingPlayers();
    if (this.order.size() == 1) {
      this.activeIndex = 0;
      this.discord.undeafen(id);
    } else {
      this.sendToWaiting(p);
      this.discord.deafen(id);
      this.anchorWaiting();
    }
  }

  public void remove(Player p) throws SwapperManager.SwapperException {
    UUID id = p.getUniqueId();
    int idx = this.order.indexOf(id);
    if (idx < 0) {
      throw new SwapperManager.SwapperException(p.getName() + " is not in the swap pool.");
    }

    this.cancelPreload();
    this.clearSpawnInvincibility(p);
    boolean wasActive = idx == this.activeIndex;
    UUID promotedId = null;
    if (wasActive && this.order.size() > 1) {
      int nextIdx = (idx + 1) % this.order.size();
      promotedId = this.order.get(nextIdx);
      Player next = Bukkit.getPlayer(promotedId);
      if (next != null) {
        PlayerSnapshot state = PlayerSnapshot.capture(p);
        state.applyTo(next);
      }
    }

    this.order.remove(idx);
    PlayerSnapshot orig = this.originalStates.remove(id);
    this.discord.undeafen(id);
    if (promotedId != null) {
      this.discord.undeafen(promotedId);
    }

    if (this.order.isEmpty()) {
      this.activeIndex = 0;
      this.stopTimer();
    } else if (idx < this.activeIndex) {
      this.activeIndex--;
    } else if (idx == this.activeIndex) {
      this.activeIndex = this.activeIndex % this.order.size();
    }

    this.refreshWaitingPlayers();
    if (promotedId != null) {
      Player promoted = Bukkit.getPlayer(promotedId);
      if (promoted != null) this.reassignActivePearls(promoted);
    }
    if (this.order.size() < 2) this.stopTimer();
    if (orig != null && p.isOnline()) {
      orig.applyTo(p);
    }

    this.clearActionBar(p);
    this.anchorWaiting();
  }

  public void setTimerSeconds(int seconds) {
    this.cancelPreload();
    this.config.setTimerSeconds(seconds);
    this.config.save();
    this.ticksUntilSwap = this.config.getTimerSeconds() * 20L;
  }

  public void startTimer() throws SwapperManager.SwapperException {
    if (this.order.size() < 2) {
      throw new SwapperManager.SwapperException(
          "Need at least 2 players in the pool to start the timer.");
    }

    this.cancelPreload();
    this.ticksUntilSwap = this.config.getTimerSeconds() * 20L;
    if (!this.running) {
      this.running = true;
      this.timerTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
    }
  }

  public void stopTimer() {
    this.running = false;
    if (this.timerTask != null) {
      this.timerTask.cancel();
      this.timerTask = null;
    }

    this.cancelPreload();

    for (UUID id : this.order) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) {
        this.clearActionBar(p);
      }
    }
  }

  public void restoreAll() {
    this.stopTimer();
    this.waitingPlayers = Set.of();
    for (UUID id : new ArrayList<>(this.order)) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) this.clearSpawnInvincibility(p);
      PlayerSnapshot orig = this.originalStates.get(id);
      if (p != null && orig != null) {
        orig.applyTo(p);
      }

      if (p != null) {
        this.clearActionBar(p);
      }

      this.discord.undeafen(id);
    }

    this.order.clear();
    this.originalStates.clear();
    this.activeIndex = 0;
  }

  private void tick() {
    if (this.order.size() < 2) {
      this.stopTimer();
    } else {
      this.ticksUntilSwap--;
      if (this.ticksUntilSwap == PRELOAD_LEAD_TICKS) {
        this.startPreload();
      }

      if (!this.preloading.isEmpty()) {
        this.anchorPreloading();
      }

      if (this.ticksUntilSwap % 5 == 0) {
        this.updateActionBars();
      }

      if (this.ticksUntilSwap <= 0) {
        Player active = Bukkit.getPlayer(this.order.get(this.activeIndex));
        // Preserve the death/respawn flow before handing off the shared state.
        if (active != null && active.isDead()) return;
        this.doSwap();
        this.ticksUntilSwap = this.config.getTimerSeconds() * 20L;
      }
    }
  }

  private void startPreload() {
    if (this.config.getTimerSeconds() > 5) {
      if (this.order.size() >= 2) {
        int nextIdx = (this.activeIndex + 1) % this.order.size();
        UUID nextId = this.order.get(nextIdx);
        Player next = Bukkit.getPlayer(nextId);
        Player active = Bukkit.getPlayer(this.order.get(this.activeIndex));
        if (next != null && active != null) {
          if (next.isDead()) {
            next.spigot().respawn();
          }

          Location activeLoc = active.getLocation();
          Location preload =
              new Location(
                  active.getWorld(),
                  activeLoc.getX(),
                  activeLoc.getY() + 50.0,
                  activeLoc.getZ(),
                  activeLoc.getYaw(),
                  90.0F);
          next.addPotionEffect(
              new PotionEffect(PotionEffectType.BLINDNESS, 140, 0, false, false, false));
          next.setGameMode(GameMode.SPECTATOR);
          next.teleport(preload);
          this.preloadingPlayer = nextId;
          this.preloadAnchor = preload;
          this.preloading.add(nextId);
        }
      }
    }
  }

  private void anchorPreloading() {
    if (this.preloadingPlayer != null && this.preloadAnchor != null) {
      Player p = Bukkit.getPlayer(this.preloadingPlayer);
      if (p == null) {
        this.clearPreload();
      } else {
        if (!p.getWorld().equals(this.preloadAnchor.getWorld())
            || p.getLocation().distanceSquared(this.preloadAnchor) > ANCHOR_DRIFT_SQ) {
          p.teleport(this.preloadAnchor);
        }
      }
    }
  }

  private void cancelPreload() {
    Player player = this.preloadingPlayer == null ? null : Bukkit.getPlayer(this.preloadingPlayer);
    this.clearPreload();
    if (player != null && this.isWaitingPlayer(player.getUniqueId())) this.sendToWaiting(player);
  }

  private void clearPreload() {
    if (this.preloadingPlayer != null) {
      Player p = Bukkit.getPlayer(this.preloadingPlayer);
      if (p != null) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
      }

      this.preloading.remove(this.preloadingPlayer);
    }

    this.preloadingPlayer = null;
    this.preloadAnchor = null;
  }

  private void doSwap() {
    if (this.order.size() >= 2) {
      UUID currentId = this.order.get(this.activeIndex);
      int nextIdx = (this.activeIndex + 1) % this.order.size();
      UUID nextId = this.order.get(nextIdx);
      Player current = Bukkit.getPlayer(currentId);
      Player next = Bukkit.getPlayer(nextId);
      if (current != null && next != null) {
        PlayerSnapshot state = PlayerSnapshot.capture(current);
        this.sendToWaiting(current);
        this.discord.deafen(currentId);
        if (next.isDead()) {
          next.spigot().respawn();
        }

        this.clearPreload();
        state.applyTo(next);
        this.discord.undeafen(nextId);
        this.activeIndex = nextIdx;
        this.refreshWaitingPlayers();
        this.reassignActivePearls(next);
        this.grantSpawnInvincibility(next);
        Component msg = Component.text("Swapped to " + next.getName() + "!", NamedTextColor.AQUA);

        for (UUID id : this.order) {
          Player p = Bukkit.getPlayer(id);
          if (p != null) {
            p.sendMessage(msg);
          }
        }
      } else {
        this.plugin.getLogger().warning("Swap skipped: one of the swap targets is offline.");
      }
    }
  }

  private void updateActionBars() {
    int secondsRemaining = (int) Math.max(1L, (this.ticksUntilSwap + 19) / 20);
    int n = this.order.size();
    int timer = this.config.getTimerSeconds();

    for (int i = 0; i < n; i++) {
      Player p = Bukkit.getPlayer(this.order.get(i));
      if (p != null) {
        if (i == this.activeIndex) {
          if (secondsRemaining <= 10) {
            NamedTextColor color =
                secondsRemaining <= 3 ? NamedTextColor.RED : NamedTextColor.YELLOW;
            p.sendActionBar(Component.text(TimeFormat.formatShort(secondsRemaining), color));
          } else {
            this.clearActionBar(p);
          }
        } else {
          int k = (i - this.activeIndex + n) % n;
          long totalSec = (long) (k - 1) * timer + secondsRemaining;
          p.sendActionBar(Component.text(TimeFormat.formatLong(totalSec), NamedTextColor.AQUA));
        }
      }
    }
  }

  public Location getWaitingLocation(UUID playerId) {
    int slot = this.order.indexOf(playerId);
    return slot < 0 ? null : this.computeSlot(slot, this.order.size(), this.getActiveWorld());
  }

  private World getActiveWorld() {
    if (!this.order.isEmpty()) {
      Player active = Bukkit.getPlayer(this.order.get(this.activeIndex));
      if (active != null) {
        return active.getWorld();
      }
    }

    return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
  }

  private Location computeSlot(int slot, int totalSlots, World world) {
    if (world == null) {
      return null;
    }

    double cx = this.config.getVoidCenterX();
    double cz = this.config.getVoidCenterZ();
    double y = world.getMinHeight() - this.config.getVoidDepth();
    if (totalSlots <= 1) {
      return new Location(world, cx, y, cz, 0.0F, 0.0F);
    }

    double spacing = this.config.getVoidSpacing();
    double radius = spacing / (2.0 * Math.sin(Math.PI / totalSlots));
    double angle = (Math.PI * 2) * slot / totalSlots;
    double x = cx + radius * Math.cos(angle);
    double z = cz + radius * Math.sin(angle);
    double dx = cx - x;
    double dz = cz - z;
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    return new Location(world, x, y, z, yaw, 0.0F);
  }

  private void grantSpawnInvincibility(Player player) {
    this.clearSpawnInvincibility(player);
    UUID id = player.getUniqueId();
    this.previousInvulnerability.put(id, player.isInvulnerable());
    player.setInvulnerable(true);
    this.invincibilityTasks.put(
        id,
        Bukkit.getScheduler()
            .runTaskLater(this.plugin, () -> this.clearSpawnInvincibility(player), 20L));
  }

  private void clearSpawnInvincibility(Player player) {
    UUID id = player.getUniqueId();
    BukkitTask task = this.invincibilityTasks.remove(id);
    if (task != null) task.cancel();
    Boolean previous = this.previousInvulnerability.remove(id);
    if (previous != null) player.setInvulnerable(previous);
  }

  private void reassignActivePearls(Player newActive) {
    for (World world : Bukkit.getWorlds()) {
      for (EnderPearl pearl : world.getEntitiesByClass(EnderPearl.class)) {
        if (pearl.getShooter() instanceof Player p && this.order.contains(p.getUniqueId())) {
          pearl.setShooter(newActive);
        }
      }
    }
  }

  public void anchorWaiting() {
    if (this.order.size() >= 2) {
      World world = this.getActiveWorld();
      if (world != null) {
        int n = this.order.size();

        for (int i = 0; i < n; i++) {
          if (i != this.activeIndex) {
            UUID id = this.order.get(i);
            if (!this.preloading.contains(id)) {
              Player p = Bukkit.getPlayer(id);
              if (p != null) {
                Location target = this.computeSlot(i, n, world);
                if (target != null
                    && (!p.getWorld().equals(target.getWorld())
                        || p.getLocation().distanceSquared(target) > ANCHOR_DRIFT_SQ)) {
                  p.teleport(target);
                }
              }
            }
          }
        }
      }
    }
  }

  public void sendToWaiting(Player p) {
    Location waiting = this.getWaitingLocation(p.getUniqueId());
    if (waiting == null) {
      this.plugin.getLogger().warning("Cannot compute waiting location for " + p.getName());
    } else {
      this.clearSpawnInvincibility(p);
      p.leaveVehicle();
      AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
      p.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
      p.setFoodLevel(20);
      p.setSaturation(20.0F);
      p.setExhaustion(0.0F);
      p.setFireTicks(0);
      p.setFallDistance(0.0F);
      p.setRemainingAir(p.getMaximumAir());
      p.getInventory().clear();
      p.getInventory().setArmorContents(new ItemStack[4]);
      p.getInventory().setItemInOffHand(null);
      p.getEnderChest().clear();

      for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
        p.removePotionEffect(effect.getType());
      }

      p.setLevel(0);
      p.setExp(0.0F);
      p.setTotalExperience(0);
      p.setGameMode(GameMode.ADVENTURE);
      p.setAllowFlight(false);
      p.setFlying(false);
      p.teleport(waiting);
      p.addPotionEffect(
          new PotionEffect(
              PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
    }
  }

  private void clearActionBar(Player p) {
    p.sendActionBar(Component.empty());
  }

  public static class SwapperException extends Exception {
    public SwapperException(String msg) {
      super(msg);
    }
  }
}
