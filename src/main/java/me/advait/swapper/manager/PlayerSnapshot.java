package me.advait.swapper.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public final class PlayerSnapshot {
  private final Location location;
  private final ItemStack[] storage;
  private final ItemStack[] armor;
  private final ItemStack offhand;
  private final ItemStack[] enderChest;
  private final double health;
  private final int foodLevel;
  private final float saturation;
  private final float exhaustion;
  private final int xpLevel;
  private final float xpProgress;
  private final int totalXp;
  private final GameMode gameMode;
  private final List<PotionEffect> effects;
  private final int fireTicks;
  private final int remainingAir;
  private final float fallDistance;
  private final int heldSlot;
  private final Vector velocity;
  private final boolean allowFlight;
  private final boolean flying;
  private final UUID vehicleUuid;

  private PlayerSnapshot(
      Location location,
      ItemStack[] storage,
      ItemStack[] armor,
      ItemStack offhand,
      ItemStack[] enderChest,
      double health,
      int foodLevel,
      float saturation,
      float exhaustion,
      int xpLevel,
      float xpProgress,
      int totalXp,
      GameMode gameMode,
      List<PotionEffect> effects,
      int fireTicks,
      int remainingAir,
      float fallDistance,
      int heldSlot,
      Vector velocity,
      boolean allowFlight,
      boolean flying,
      UUID vehicleUuid) {
    this.location = location;
    this.storage = storage;
    this.armor = armor;
    this.offhand = offhand;
    this.enderChest = enderChest;
    this.health = health;
    this.foodLevel = foodLevel;
    this.saturation = saturation;
    this.exhaustion = exhaustion;
    this.xpLevel = xpLevel;
    this.xpProgress = xpProgress;
    this.totalXp = totalXp;
    this.gameMode = gameMode;
    this.effects = effects;
    this.fireTicks = fireTicks;
    this.remainingAir = remainingAir;
    this.fallDistance = fallDistance;
    this.heldSlot = heldSlot;
    this.velocity = velocity;
    this.allowFlight = allowFlight;
    this.flying = flying;
    this.vehicleUuid = vehicleUuid;
  }

  public static PlayerSnapshot capture(Player p) {
    Entity vehicle = p.getVehicle();
    return new PlayerSnapshot(
        p.getLocation().clone(),
        cloneItems(p.getInventory().getStorageContents()),
        cloneItems(p.getInventory().getArmorContents()),
        p.getInventory().getItemInOffHand().clone(),
        cloneItems(p.getEnderChest().getContents()),
        p.getHealth(),
        p.getFoodLevel(),
        p.getSaturation(),
        p.getExhaustion(),
        p.getLevel(),
        p.getExp(),
        p.getTotalExperience(),
        p.getGameMode(),
        new ArrayList<>(p.getActivePotionEffects()),
        p.getFireTicks(),
        p.getRemainingAir(),
        p.getFallDistance(),
        p.getInventory().getHeldItemSlot(),
        p.getVelocity().clone(),
        p.getAllowFlight(),
        p.isFlying(),
        vehicle != null ? vehicle.getUniqueId() : null);
  }

  public void applyTo(Player p) {
    p.leaveVehicle();
    p.teleport(this.location);
    p.setGameMode(this.gameMode);
    p.getInventory().setStorageContents(cloneItems(this.storage));
    p.getInventory().setArmorContents(cloneItems(this.armor));
    p.getInventory().setItemInOffHand(this.offhand == null ? null : this.offhand.clone());
    p.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, this.heldSlot)));
    p.getEnderChest().setContents(cloneItems(this.enderChest));
    p.setFoodLevel(this.foodLevel);
    p.setSaturation(this.saturation);
    p.setExhaustion(this.exhaustion);
    p.setLevel(this.xpLevel);
    p.setExp(this.xpProgress);
    p.setTotalExperience(this.totalXp);

    for (PotionEffect active : new ArrayList<>(p.getActivePotionEffects())) {
      p.removePotionEffect(active.getType());
    }

    for (PotionEffect effect : this.effects) {
      p.addPotionEffect(effect);
    }

    AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
    double cap = maxHealth == null ? 20.0 : maxHealth.getValue();
    p.setHealth(Math.max(0.5, Math.min(this.health, cap)));

    p.setFireTicks(this.fireTicks);
    p.setRemainingAir(this.remainingAir);
    p.setFallDistance(this.fallDistance);
    p.setVelocity(this.velocity);
    p.setAllowFlight(this.allowFlight);
    if (this.allowFlight) {
      p.setFlying(this.flying);
    }

    if (this.vehicleUuid != null) {
      Entity vehicle = Bukkit.getEntity(this.vehicleUuid);
      if (vehicle != null && !vehicle.isDead()) {
        if (p.getVehicle() != null) {
          p.leaveVehicle();
        }

        vehicle.addPassenger(p);
      }
    }
  }

  private static ItemStack[] cloneItems(ItemStack[] source) {
    ItemStack[] out = new ItemStack[source.length];

    for (int i = 0; i < source.length; i++) {
      out[i] = source[i] == null ? null : source[i].clone();
    }

    return out;
  }
}
