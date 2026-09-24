package me.advait.swapper.manager;

import java.util.*;
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
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class SwapperManager {
  private final SwapperPlugin plugin;
  private final SwapperConfig config;
  private final DiscordManager discord;
  private final List<UUID> order = new ArrayList<>();
  private final Map<UUID, PlayerSnapshot> originalStates = new HashMap<>();
  private final Set<UUID> advancementSyncing = new HashSet<>();
  private final Map<UUID, BukkitTask> invincibilityTasks = new HashMap<>();
  private final Map<UUID, Boolean> previousInvulnerability = new HashMap<>();
  private volatile Set<UUID> waitingPlayers = Set.of();
  private int activeIndex;
  private boolean running;
  private boolean swapPending;
  private long swapGeneration;
  private long ticksUntilSwap;
  private long waitingTicks;
  private BukkitTask timerTask;
  private BukkitTask anchorTask;

  public SwapperManager(SwapperPlugin plugin, SwapperConfig config, DiscordManager discord) {
    this.plugin = plugin;
    this.config = config;
    this.discord = discord;
  }

  public void start() {
    anchorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::anchorWaiting, 1L, 1L);
  }

  public void shutdown() {
    stopTimer();
    if (anchorTask != null) {
      anchorTask.cancel();
      anchorTask = null;
    }
  }

  public List<UUID> getOrder() {
    return List.copyOf(order);
  }

  public boolean isRunning() {
    return running;
  }

  public boolean isInPool(UUID id) {
    return order.contains(id);
  }

  public boolean isActivePlayer(UUID id) {
    return !order.isEmpty() && order.get(activeIndex).equals(id);
  }

  public boolean isWaitingPlayer(UUID id) {
    return waitingPlayers.contains(id);
  }

  public boolean isAdvancementSyncing(UUID id) {
    return advancementSyncing.contains(id);
  }

  public void markAdvancementSyncing(UUID id) {
    advancementSyncing.add(id);
  }

  public void unmarkAdvancementSyncing(UUID id) {
    advancementSyncing.remove(id);
  }

  private Player active() {
    return order.isEmpty() ? null : Bukkit.getPlayer(order.get(activeIndex));
  }

  private void refreshWaitingPlayers() {
    Set<UUID> waiting = new HashSet<>(order);
    if (!order.isEmpty()) waiting.remove(order.get(activeIndex));
    waitingPlayers = Set.copyOf(waiting);
  }

  public void add(Player player) throws SwapperException {
    UUID id = player.getUniqueId();
    if (order.contains(id))
      throw new SwapperException(player.getName() + " is already in the swap pool.");
    if (!plugin.getBlackout().hasClient(player)) {
      throw new SwapperException(
          player.getName()
              + " needs Swapper Client for Fabric 26.2. Install the mod and Fabric API, then"
              + " reconnect.");
    }
    originalStates.put(id, PlayerSnapshot.capture(player));
    plugin.getBlackout().rememberOriginal(player);
    order.add(id);
    refreshWaitingPlayers();
    if (order.size() == 1) {
      activeIndex = 0;
      discord.undeafen(id);
    } else {
      sendToWaiting(player);
      discord.deafen(id);
      anchorWaiting();
    }
  }

  public void remove(Player player) throws SwapperException {
    UUID id = player.getUniqueId();
    int index = order.indexOf(id);
    if (index < 0) throw new SwapperException(player.getName() + " is not in the swap pool.");
    cancelPendingSwap();
    clearSpawnInvincibility(player);
    Player promoted = null;
    if (index == activeIndex && order.size() > 1) {
      promoted = Bukkit.getPlayer(order.get((index + 1) % order.size()));
      if (promoted != null) {
        PlayerSnapshot shared = PlayerSnapshot.capture(player);
        releaseInventory(player);
        player.leaveVehicle();
        shared.applyTo(promoted);
        plugin.getBlackout().restoreView(promoted, plugin.getBlackout().latestView(player));
      }
    }
    order.remove(index);
    if (order.isEmpty()) activeIndex = 0;
    else if (index < activeIndex) activeIndex--;
    else if (index == activeIndex) activeIndex %= order.size();
    refreshWaitingPlayers();
    if (promoted != null) {
      plugin.getBlackout().hide(promoted);
      discord.undeafen(promoted.getUniqueId());
      reassignActivePearls(promoted);
    }
    PlayerSnapshot original = originalStates.remove(id);
    if (original != null && player.isOnline()) {
      releaseInventory(player);
      original.applyTo(player);
      plugin.getBlackout().restoreOriginal(player);
    }
    plugin.getBlackout().hide(player);
    clearActionBar(player);
    discord.undeafen(id);
    if (order.size() < 2) stopTimer();
    anchorWaiting();
  }

  public void setTimerSeconds(int seconds) {
    cancelPendingSwap();
    config.setTimerSeconds(seconds);
    config.save();
    ticksUntilSwap = config.getTimerSeconds() * 20L;
  }

  public void startTimer() throws SwapperException {
    if (order.size() < 2)
      throw new SwapperException("Need at least 2 players in the pool to start the timer.");
    for (UUID id : order) {
      Player player = Bukkit.getPlayer(id);
      if (player == null || !plugin.getBlackout().hasClient(player)) {
        throw new SwapperException("Every player needs Swapper Client for Fabric 26.2.");
      }
    }
    cancelPendingSwap();
    ticksUntilSwap = config.getTimerSeconds() * 20L;
    if (!running) {
      running = true;
      timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }
  }

  private void cancelPendingSwap() {
    swapGeneration++;
    swapPending = false;
    plugin.getBlackout().cancelCaptures();
  }

  public void stopTimer() {
    running = false;
    cancelPendingSwap();
    if (timerTask != null) {
      timerTask.cancel();
      timerTask = null;
    }
    for (UUID id : order) {
      Player player = Bukkit.getPlayer(id);
      if (player != null) clearActionBar(player);
    }
  }

  public void restoreAll() {
    stopTimer();
    List<UUID> restoring = List.copyOf(order);
    order.clear();
    waitingPlayers = Set.of();
    activeIndex = 0;
    for (UUID id : restoring) {
      Player player = Bukkit.getPlayer(id);
      PlayerSnapshot original = originalStates.remove(id);
      if (player != null) {
        clearSpawnInvincibility(player);
        if (original != null) {
          releaseInventory(player);
          original.applyTo(player);
          plugin.getBlackout().restoreOriginal(player);
        }
        plugin.getBlackout().hide(player);
        clearActionBar(player);
      }
      discord.undeafen(id);
    }
  }

  private void tick() {
    if (order.size() < 2) {
      stopTimer();
      return;
    }
    if (swapPending) return;
    if (ticksUntilSwap > 0) ticksUntilSwap--;
    if (config.getTimerSeconds() >= 10 && ticksUntilSwap == config.getTimerSeconds() * 10L) {
      Player player = active();
      if (player != null)
        player.sendMessage(
            Component.text(
                "Swapping in " + (ticksUntilSwap + 19) / 20 + " seconds!", NamedTextColor.YELLOW));
    }
    if (ticksUntilSwap % 5 == 0) updateActionBar();
    if (ticksUntilSwap <= 0) requestSwap();
  }

  private void requestSwap() {
    Player outgoing = active();
    if (outgoing == null || outgoing.isDead()) return;
    Player incoming = Bukkit.getPlayer(order.get((activeIndex + 1) % order.size()));
    if (incoming == null || !plugin.getBlackout().isReady(incoming)) {
      stopTimer();
      outgoing.sendMessage(
          Component.text(
              "Swap paused: the next player's blackout is not ready.", NamedTextColor.YELLOW));
      return;
    }
    swapPending = true;
    long generation = swapGeneration;
    plugin
        .getBlackout()
        .captureView(
            outgoing,
            view -> {
              if (generation != swapGeneration || !running || active() != outgoing) return;
              swapPending = false;
              if (view == null) {
                stopTimer();
                outgoing.sendMessage(
                    Component.text(
                        "Swap paused: your client did not return its screen state.",
                        NamedTextColor.YELLOW));
                return;
              }
              if (outgoing.isDead()) return;
              doSwap(outgoing, incoming, view);
            });
  }

  private void doSwap(Player outgoing, Player incoming, byte[] view) {
    clearSpawnInvincibility(outgoing);
    PlayerSnapshot shared = PlayerSnapshot.capture(outgoing);
    releaseInventory(outgoing);
    outgoing.leaveVehicle();
    if (incoming.isDead()) incoming.spigot().respawn();
    shared.applyTo(incoming);
    activeIndex = (activeIndex + 1) % order.size();
    refreshWaitingPlayers();
    ticksUntilSwap = config.getTimerSeconds() * 20L;
    plugin.getBlackout().restoreView(incoming, view);
    plugin.getBlackout().hide(incoming);
    sendToWaiting(outgoing);
    discord.deafen(outgoing.getUniqueId());
    discord.undeafen(incoming.getUniqueId());
    reassignActivePearls(incoming);
    grantSpawnInvincibility(incoming);
  }

  private void updateActionBar() {
    Player player = active();
    if (player == null) return;
    int seconds = (int) Math.max(1, (ticksUntilSwap + 19) / 20);
    if (seconds <= 10) {
      player.sendActionBar(
          Component.text(
              TimeFormat.formatShort(seconds),
              seconds <= 3 ? NamedTextColor.RED : NamedTextColor.YELLOW));
    } else clearActionBar(player);
  }

  public Location getWaitingLocation(UUID id) {
    Player player = Bukkit.getPlayer(id);
    Player active = active();
    if (player == null || active == null || !isWaitingPlayer(id)) return null;
    return plugin.getBlackout().isReady(player) ? active.getLocation() : player.getLocation();
  }

  public void anchorWaiting() {
    waitingTicks++;
    Player active = active();
    if (active == null) return;
    for (UUID id : waitingPlayers) {
      Player player = Bukkit.getPlayer(id);
      if (player == null) continue;
      if (waitingTicks % 5 == 0 || !plugin.getBlackout().isReady(player))
        updateWaitingScreen(player, active);
      if (!plugin.getBlackout().isReady(player)) continue;
      if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
      if (!player.hasPotionEffect(PotionEffectType.BLINDNESS))
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.BLINDNESS, -1, 0, false, false, false));
      Location target = active.getLocation();
      if (!player.getWorld().equals(target.getWorld())
          || player.getLocation().distanceSquared(target) > 0.01) player.teleport(target);
    }
  }

  private void updateWaitingScreen(Player player, Player active) {
    int turns = (order.indexOf(player.getUniqueId()) - activeIndex + order.size()) % order.size();
    long seconds =
        Math.max(0L, (ticksUntilSwap + 19) / 20)
            + (long) Math.max(0, turns - 1) * config.getTimerSeconds();
    plugin.getBlackout().show(player, seconds, !running, active);
  }

  private void releaseInventory(Player player) {
    player.setItemOnCursor(null);
    if (player.getOpenInventory().getTopInventory()
            instanceof org.bukkit.inventory.CraftingInventory grid
        && grid.getMatrix().length == 4) grid.setMatrix(new ItemStack[4]);
    player.closeInventory();
  }

  public void sendToWaiting(Player player) {
    updateWaitingScreen(player, active());
    clearSpawnInvincibility(player);
    releaseInventory(player);
    player.leaveVehicle();
    player.getInventory().clear();
    player.getInventory().setArmorContents(new ItemStack[4]);
    player.getInventory().setItemInOffHand(null);
    player.getEnderChest().clear();
    for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects()))
      player.removePotionEffect(effect.getType());
    player.setFireTicks(0);
    player.setFallDistance(0);
    player.setFoodLevel(20);
    player.setSaturation(20);
    player.setExhaustion(0);
    player.setRemainingAir(player.getMaximumAir());
    player.setLevel(0);
    player.setExp(0);
    player.setTotalExperience(0);
    player.setGameMode(GameMode.SPECTATOR);
    player.setAllowFlight(true);
    player.setFlying(true);
    player.addPotionEffect(
        new PotionEffect(PotionEffectType.BLINDNESS, -1, 0, false, false, false));
    if (plugin.getBlackout().isReady(player)) player.teleport(active().getLocation());
  }

  private void grantSpawnInvincibility(Player player) {
    clearSpawnInvincibility(player);
    UUID id = player.getUniqueId();
    previousInvulnerability.put(id, player.isInvulnerable());
    player.setInvulnerable(true);
    invincibilityTasks.put(
        id, Bukkit.getScheduler().runTaskLater(plugin, () -> clearSpawnInvincibility(player), 20L));
  }

  private void clearSpawnInvincibility(Player player) {
    BukkitTask task = invincibilityTasks.remove(player.getUniqueId());
    if (task != null) task.cancel();
    Boolean previous = previousInvulnerability.remove(player.getUniqueId());
    if (previous != null) player.setInvulnerable(previous);
  }

  private void reassignActivePearls(Player next) {
    for (World world : Bukkit.getWorlds()) {
      for (EnderPearl pearl : world.getEntitiesByClass(EnderPearl.class)) {
        if (pearl.getShooter() instanceof Player owner && order.contains(owner.getUniqueId()))
          pearl.setShooter(next);
      }
    }
  }

  private void clearActionBar(Player player) {
    player.sendActionBar(Component.empty());
  }

  public static class SwapperException extends Exception {
    public SwapperException(String message) {
      super(message);
    }
  }
}
