package me.advait.swapper.discord;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DiscordLinks {
  private final File file;
  private final Logger logger;
  private final ConcurrentMap<UUID, String> links = new ConcurrentHashMap<>();

  public DiscordLinks(File file, Logger logger) {
    this.file = file;
    this.logger = logger;
    this.load();
  }

  public Optional<String> get(UUID uuid) {
    return Optional.ofNullable(this.links.get(uuid));
  }

  public void put(UUID uuid, String discordId) {
    this.links.put(uuid, discordId);
    this.save();
  }

  public void remove(UUID uuid) {
    if (this.links.remove(uuid) != null) {
      this.save();
    }
  }

  private void load() {
    if (this.file.exists()) {
      FileConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);

      for (String key : cfg.getKeys(false)) {
        try {
          UUID id = UUID.fromString(key);
          String discord = cfg.getString(key);
          if (discord != null && !discord.isBlank()) {
            this.links.put(id, discord);
          }
        } catch (IllegalArgumentException var6) {
        }
      }
    }
  }

  private void save() {
    FileConfiguration cfg = new YamlConfiguration();
    this.links.forEach((uuid, id) -> cfg.set(uuid.toString(), id));

    try {
      File parent = this.file.getParentFile();
      if (parent != null) {
        parent.mkdirs();
      }

      cfg.save(this.file);
    } catch (IOException e) {
      this.logger.warning("Failed to save discord-links.yml: " + e.getMessage());
    }
  }
}
