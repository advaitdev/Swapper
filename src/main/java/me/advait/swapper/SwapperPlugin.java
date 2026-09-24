package me.advait.swapper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.advait.swapper.command.SwapperCommand;
import me.advait.swapper.config.SwapperConfig;
import me.advait.swapper.discord.DiscordManager;
import me.advait.swapper.listener.AdvancementListener;
import me.advait.swapper.listener.ChatListener;
import me.advait.swapper.listener.WaitingAreaListener;
import me.advait.swapper.manager.SwapperManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SwapperPlugin extends JavaPlugin {
  private SwapperConfig swapperConfig;
  private DiscordManager discord;
  private SwapperManager manager;

  @Override
  public void onEnable() {
    this.saveDefaultConfig();
    this.swapperConfig = new SwapperConfig(this);
    this.discord = new DiscordManager(this);
    this.manager = new SwapperManager(this, this.swapperConfig, this.discord);
    this.discord.start();
    this.manager.start();
    Bukkit.getPluginManager().registerEvents(new WaitingAreaListener(this, this.manager), this);
    Bukkit.getPluginManager().registerEvents(new ChatListener(this, this.manager), this);
    Bukkit.getPluginManager().registerEvents(new AdvancementListener(this, this.manager), this);
    this.getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event ->
                event
                    .registrar()
                    .register(SwapperCommand.build(this), "Manage the Swapper challenge"));
    this.getLogger().info("Swapper enabled.");
  }

  @Override
  public void onDisable() {
    if (this.manager != null) {
      this.manager.shutdown();
      this.manager.restoreAll();
    }

    this.getLogger().info("Swapper disabled.");
  }

  public SwapperConfig getSwapperConfig() {
    return this.swapperConfig;
  }

  public SwapperManager getManager() {
    return this.manager;
  }

  public DiscordManager getDiscord() {
    return this.discord;
  }
}
