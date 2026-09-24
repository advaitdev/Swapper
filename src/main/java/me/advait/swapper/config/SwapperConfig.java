package me.advait.swapper.config;

import me.advait.swapper.SwapperPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class SwapperConfig {
  private final SwapperPlugin plugin;

  public SwapperConfig(SwapperPlugin plugin) {
    this.plugin = plugin;
  }

  private FileConfiguration cfg() {
    return this.plugin.getConfig();
  }

  public int getTimerSeconds() {
    return Math.max(1, this.cfg().getInt("timer-seconds", 60));
  }

  public void setTimerSeconds(int seconds) {
    this.cfg().set("timer-seconds", Math.max(1, seconds));
  }

  public double getVoidCenterX() {
    return this.cfg().getDouble("void-x", 0.5);
  }

  public double getVoidCenterZ() {
    return this.cfg().getDouble("void-z", 0.5);
  }

  public int getVoidDepth() {
    return Math.max(1, this.cfg().getInt("void-depth", 50));
  }

  public double getVoidSpacing() {
    return Math.max(0.5, this.cfg().getDouble("void-spacing", 2.0));
  }

  public void save() {
    this.plugin.saveConfig();
  }
}
