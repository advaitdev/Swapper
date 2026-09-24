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

  public void save() {
    this.plugin.saveConfig();
  }
}
