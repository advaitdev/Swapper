package me.advait.swapper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import java.util.UUID;
import me.advait.swapper.SwapperPlugin;
import me.advait.swapper.discord.DiscordClient;
import me.advait.swapper.manager.SwapperManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("swapper")
@CommandPermission("swapper.admin")
public final class SwapperCommand extends BaseCommand {
  private final SwapperPlugin plugin;

  public SwapperCommand(SwapperPlugin plugin) {
    this.plugin = plugin;
  }

  @Default
  public void usage(CommandSender sender) {
    sender.sendMessage(Component.text("Swapper commands:", NamedTextColor.AQUA));
    sender.sendMessage(Component.text("  /swapper add <player>", NamedTextColor.GRAY));
    sender.sendMessage(Component.text("  /swapper remove <player>", NamedTextColor.GRAY));
    sender.sendMessage(Component.text("  /swapper timer <seconds>", NamedTextColor.GRAY));
    sender.sendMessage(Component.text("  /swapper stoptimer", NamedTextColor.GRAY));
    sender.sendMessage(
        Component.text("  /swapper linkdiscord <player> <discord>", NamedTextColor.GRAY));
  }

  @Subcommand("add")
  @CommandCompletion("@players")
  @Syntax("<player>")
  public void add(CommandSender sender, OnlinePlayer player) {
    Player target = player.getPlayer();
    try {
      plugin.getManager().add(target);
      sendOk(sender, "Added " + target.getName() + " to the swap pool.");
    } catch (SwapperManager.SwapperException e) {
      sendError(sender, e.getMessage());
    }
  }

  @Subcommand("remove")
  @CommandCompletion("@players")
  @Syntax("<player>")
  public void remove(CommandSender sender, OnlinePlayer player) {
    Player target = player.getPlayer();
    try {
      plugin.getManager().remove(target);
      sendOk(sender, "Removed " + target.getName() + " from the swap pool.");
    } catch (SwapperManager.SwapperException e) {
      sendError(sender, e.getMessage());
    }
  }

  @Subcommand("timer")
  @Syntax("<seconds>")
  public void timer(CommandSender sender, int seconds) {
    if (seconds < 1) {
      sendError(sender, "The timer must be at least 1 second.");
      return;
    }
    plugin.getManager().setTimerSeconds(seconds);
    try {
      boolean wasRunning = plugin.getManager().isRunning();
      plugin.getManager().startTimer();
      sendOk(
          sender,
          "Timer set to "
              + seconds
              + " second"
              + (seconds == 1 ? "" : "s")
              + (wasRunning ? " and reset." : " and started."));
    } catch (SwapperManager.SwapperException e) {
      sendOk(sender, "Timer set to " + seconds + " second" + (seconds == 1 ? "" : "s") + ".");
      sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.YELLOW));
    }
  }

  @Subcommand("stoptimer")
  public void stopTimer(CommandSender sender) {
    if (!plugin.getManager().isRunning() && !plugin.getManager().isPausedForReconnect()) {
      sendError(sender, "The timer is not running.");
      return;
    }
    plugin.getManager().stopTimer();
    sendOk(sender, "Timer stopped.");
  }

  @Subcommand("linkdiscord")
  @CommandCompletion("@players @nothing")
  @Syntax("<player> <discord-name-or-id>")
  public void linkDiscord(CommandSender sender, OnlinePlayer player, String discordInput) {
    Player target = player.getPlayer();
    UUID uuid = target.getUniqueId();
    if (!plugin.getManager().isInPool(uuid)) {
      sendError(
          sender, target.getName() + " must be added to the swap pool before linking Discord.");
      return;
    }
    if (!plugin.getDiscord().isEnabled()) {
      sendError(
          sender,
          "Discord integration is not active. Configure discord.* in config.yml and restart.");
      return;
    }
    sender.sendMessage(
        Component.text("Looking up Discord user '" + discordInput + "'...", NamedTextColor.GRAY));
    plugin
        .getDiscord()
        .findMember(discordInput)
        .thenAccept(
            member ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (member.isEmpty()) {
                            sendError(
                                sender,
                                "Discord user not found: '"
                                    + discordInput
                                    + "'. Check the server console for the Discord HTTP error.");
                            return;
                          }
                          DiscordClient.DiscordMember match = member.get();
                          plugin.getDiscord().setLink(uuid, match.id());
                          sendOk(
                              sender,
                              "Linked "
                                  + target.getName()
                                  + " <-> @"
                                  + match.username()
                                  + " ("
                                  + match.id()
                                  + ").");
                          if (plugin.getManager().isWaitingPlayer(uuid)) {
                            plugin.getDiscord().deafen(uuid);
                          }
                        }))
        .exceptionally(
            error -> {
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> sendError(sender, "Discord lookup failed: " + error.getMessage()));
              return null;
            });
  }

  private static void sendOk(CommandSender sender, String message) {
    sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
  }

  private static void sendError(CommandSender sender, String message) {
    sender.sendMessage(Component.text(message, NamedTextColor.RED));
  }
}
