package me.advait.swapper.discord;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.advait.swapper.SwapperPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public final class DiscordManager {
  private final SwapperPlugin plugin;
  private final DiscordLinks links;
  private volatile DiscordClient client;

  public DiscordManager(SwapperPlugin plugin) {
    this.plugin = plugin;
    this.links =
        new DiscordLinks(new File(plugin.getDataFolder(), "discord-links.yml"), plugin.getLogger());
  }

  public void start() {
    FileConfiguration cfg = this.plugin.getConfig();
    if (!cfg.getBoolean("discord.enabled", false)) {
      this.plugin
          .getLogger()
          .info("Discord integration disabled (set discord.enabled to true in config.yml).");
    } else {
      String token = cfg.getString("discord.token", "");
      String guildId = cfg.getString("discord.guild-id", "");
      if (token != null && !token.isBlank() && guildId != null && !guildId.isBlank()) {
        this.client = new DiscordClient(token, guildId, this.plugin.getLogger());
        this.client
            .verifyConnection()
            .thenAccept(
                opt -> {
                  if (opt.isEmpty()) {
                    this.plugin
                        .getLogger()
                        .warning(
                            "Discord verification failed. Token is invalid or Discord is"
                                + " unreachable.");
                    this.client = null;
                  } else {
                    this.plugin.getLogger().info("Discord bot connected as @" + opt.get());
                    DiscordClient c = this.client;
                    if (c != null) {
                      c.isInGuild()
                          .thenAccept(
                              inGuildOpt -> {
                                if (inGuildOpt.isEmpty()) {
                                  this.plugin
                                      .getLogger()
                                      .warning(
                                          "Could not verify the bot's guild membership. See"
                                              + " previous warning for HTTP details.");
                                } else if (!inGuildOpt.get()) {
                                  this.plugin
                                      .getLogger()
                                      .warning(
                                          "Discord bot is NOT a member of the configured guild '"
                                              + guildId
                                              + "'. Either the guild-id in config.yml is wrong, OR"
                                              + " the bot was never actually invited to that"
                                              + " server. Re-invite via OAuth2 -> URL Generator ->"
                                              + " scope=bot, permission=Deafen Members. (The newer"
                                              + " 'Add App' / user-install button does NOT add the"
                                              + " bot to a server.)");
                                } else {
                                  this.plugin
                                      .getLogger()
                                      .info("Discord bot confirmed in guild '" + guildId + "'.");
                                }
                              });
                    }
                  }
                })
            .exceptionally(
                t -> {
                  this.plugin.getLogger().warning("Discord connection error: " + t.getMessage());
                  this.client = null;
                  return null;
                });
      } else {
        this.plugin
            .getLogger()
            .warning("Discord enabled but token or guild-id is missing. Skipping.");
      }
    }
  }

  public boolean isEnabled() {
    return this.client != null;
  }

  public Optional<String> getLink(UUID uuid) {
    return this.links.get(uuid);
  }

  public void setLink(UUID uuid, String discordId) {
    this.links.put(uuid, discordId);
  }

  public void clearLink(UUID uuid) {
    this.links.remove(uuid);
  }

  public CompletableFuture<Optional<DiscordClient.DiscordMember>> findMember(String input) {
    DiscordClient c = this.client;
    return c == null ? CompletableFuture.completedFuture(Optional.empty()) : c.findMember(input);
  }

  public void deafen(UUID uuid) {
    this.setVoiceState(uuid, true);
  }

  public void undeafen(UUID uuid) {
    this.setVoiceState(uuid, false);
  }

  private void setVoiceState(UUID uuid, boolean silenced) {
    DiscordClient c = this.client;
    if (c != null) {
      Optional<String> discordId = this.links.get(uuid);
      if (!discordId.isEmpty()) {
        c.setVoiceState(discordId.get(), silenced, silenced)
            .thenAccept(
                status -> {
                  if (status < 200 || status >= 300) {
                    if (status != 400) {
                      this.plugin
                          .getLogger()
                          .warning(
                              "Discord setVoiceState("
                                  + discordId.get()
                                  + ", silenced="
                                  + silenced
                                  + ") returned HTTP "
                                  + status);
                    }
                  }
                })
            .exceptionally(
                t -> {
                  this.plugin.getLogger().warning("Discord setVoiceState error: " + t.getMessage());
                  return null;
                });
      }
    }
  }
}
