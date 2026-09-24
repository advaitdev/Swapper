package me.advait.swapper.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.UUID;
import me.advait.swapper.SwapperPlugin;
import me.advait.swapper.discord.DiscordClient;
import me.advait.swapper.manager.SwapperManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SwapperCommand {
  private SwapperCommand() {}

  public static LiteralCommandNode<CommandSourceStack> build(SwapperPlugin plugin) {
    return Commands.literal("swapper")
        .requires(source -> source.getSender().hasPermission("swapper.admin"))
        .executes(
            context -> {
              sendUsage(context.getSource().getSender());
              return 1;
            })
        .then(
            Commands.literal("add")
                .executes(context -> usage(context.getSource(), "/swapper add <player>"))
                .then(
                    Commands.argument("player", ArgumentTypes.player())
                        .executes(
                            context ->
                                handleAdd(
                                    plugin,
                                    context.getSource(),
                                    context.getArgument(
                                        "player", PlayerSelectorArgumentResolver.class)))))
        .then(
            Commands.literal("remove")
                .executes(context -> usage(context.getSource(), "/swapper remove <player>"))
                .then(
                    Commands.argument("player", ArgumentTypes.player())
                        .executes(
                            context ->
                                handleRemove(
                                    plugin,
                                    context.getSource(),
                                    context.getArgument(
                                        "player", PlayerSelectorArgumentResolver.class)))))
        .then(
            Commands.literal("timer")
                .executes(context -> usage(context.getSource(), "/swapper timer <seconds>"))
                .then(
                    Commands.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(
                            context ->
                                handleTimer(
                                    plugin,
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "seconds")))))
        .then(
            Commands.literal("stoptimer")
                .executes(context -> handleStopTimer(plugin, context.getSource())))
        .then(
            Commands.literal("linkdiscord")
                .executes(
                    context ->
                        usage(
                            context.getSource(),
                            "/swapper linkdiscord <player> <discord-name-or-id>"))
                .then(
                    Commands.argument("player", ArgumentTypes.player())
                        .executes(
                            context ->
                                usage(
                                    context.getSource(),
                                    "/swapper linkdiscord <player> <discord-name-or-id>"))
                        .then(
                            Commands.argument("discord", StringArgumentType.word())
                                .executes(
                                    context ->
                                        handleLinkDiscord(
                                            plugin,
                                            context.getSource(),
                                            context.getArgument(
                                                "player", PlayerSelectorArgumentResolver.class),
                                            StringArgumentType.getString(context, "discord"))))))
        .build();
  }

  private static int usage(CommandSourceStack source, String command) {
    sendError(source.getSender(), "Usage: " + command);
    return 0;
  }

  private static int handleAdd(
      SwapperPlugin plugin, CommandSourceStack src, PlayerSelectorArgumentResolver sel) {
    CommandSender sender = src.getSender();
    Player target = resolveSingle(sender, src, sel);
    if (target == null) {
      return 0;
    }

    try {
      plugin.getManager().add(target);
      sendOk(sender, "Added " + target.getName() + " to the swap pool.");
      return 1;
    } catch (SwapperManager.SwapperException e) {
      sendError(sender, e.getMessage());
      return 0;
    }
  }

  private static int handleRemove(
      SwapperPlugin plugin, CommandSourceStack src, PlayerSelectorArgumentResolver sel) {
    CommandSender sender = src.getSender();
    Player target = resolveSingle(sender, src, sel);
    if (target == null) {
      return 0;
    }

    try {
      plugin.getManager().remove(target);
      sendOk(sender, "Removed " + target.getName() + " from the swap pool.");
      return 1;
    } catch (SwapperManager.SwapperException e) {
      sendError(sender, e.getMessage());
      return 0;
    }
  }

  private static int handleTimer(SwapperPlugin plugin, CommandSourceStack src, int seconds) {
    CommandSender sender = src.getSender();
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
      return 1;
    } catch (SwapperManager.SwapperException e) {
      sendOk(sender, "Timer set to " + seconds + " second" + (seconds == 1 ? "" : "s") + ".");
      sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.YELLOW));
      return 1;
    }
  }

  private static int handleLinkDiscord(
      SwapperPlugin plugin,
      CommandSourceStack src,
      PlayerSelectorArgumentResolver sel,
      String discordInput) {
    CommandSender sender = src.getSender();
    Player target = resolveSingle(sender, src, sel);
    if (target == null) {
      return 0;
    } else {
      UUID uuid = target.getUniqueId();
      if (!plugin.getManager().isInPool(uuid)) {
        sendError(
            sender, target.getName() + " must be added to the swap pool before linking Discord.");
        return 0;
      } else if (!plugin.getDiscord().isEnabled()) {
        sendError(
            sender,
            "Discord integration is not active. Configure discord.* in config.yml and restart.");
        return 0;
      } else {
        sender.sendMessage(
            Component.text(
                "Looking up Discord user '" + discordInput + "'...", NamedTextColor.GRAY));
        plugin
            .getDiscord()
            .findMember(discordInput)
            .thenAccept(
                opt ->
                    Bukkit.getScheduler()
                        .runTask(
                            plugin,
                            () -> {
                              if (opt.isEmpty()) {
                                sendError(
                                    sender,
                                    "Discord user not found: '"
                                        + discordInput
                                        + "'. Check the server console for the exact HTTP error"
                                        + " from Discord — it usually says whether the bot is in"
                                        + " the wrong guild, missing an intent, or the ID is"
                                        + " wrong.");
                              } else {
                                DiscordClient.DiscordMember m = opt.get();
                                plugin.getDiscord().setLink(uuid, m.id());
                                sendOk(
                                    sender,
                                    "Linked "
                                        + target.getName()
                                        + " <-> @"
                                        + m.username()
                                        + " ("
                                        + m.id()
                                        + ").");
                                if (plugin.getManager().isWaitingPlayer(uuid)) {
                                  plugin.getDiscord().deafen(uuid);
                                }
                              }
                            }))
            .exceptionally(
                t -> {
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () -> sendError(sender, "Discord lookup failed: " + t.getMessage()));
                  return null;
                });
        return 1;
      }
    }
  }

  private static int handleStopTimer(SwapperPlugin plugin, CommandSourceStack src) {
    CommandSender sender = src.getSender();
    if (!plugin.getManager().isRunning()) {
      sendError(sender, "The timer is not running.");
      return 0;
    } else {
      plugin.getManager().stopTimer();
      sendOk(sender, "Timer stopped.");
      return 1;
    }
  }

  private static Player resolveSingle(
      CommandSender sender, CommandSourceStack src, PlayerSelectorArgumentResolver sel) {
    try {
      List<Player> players = sel.resolve(src);
      if (players.isEmpty()) {
        sendError(sender, "No player matched.");
        return null;
      } else {
        return players.get(0);
      }
    } catch (Exception e) {
      sendError(sender, "Could not resolve player: " + e.getMessage());
      return null;
    }
  }

  private static void sendUsage(CommandSender sender) {
    sender.sendMessage(Component.text("Swapper commands:", NamedTextColor.AQUA));
    sender.sendMessage(Component.text("  /swapper add <player>", NamedTextColor.GRAY));
    sender.sendMessage(Component.text("  /swapper remove <player>", NamedTextColor.GRAY));
    sender.sendMessage(Component.text("  /swapper timer <seconds>", NamedTextColor.GRAY));
    sender.sendMessage(Component.text("  /swapper stoptimer", NamedTextColor.GRAY));
    sender.sendMessage(
        Component.text("  /swapper linkdiscord <player> <discord>", NamedTextColor.GRAY));
  }

  private static void sendOk(CommandSender sender, String msg) {
    sender.sendMessage(Component.text(msg, NamedTextColor.GREEN));
  }

  private static void sendError(CommandSender sender, String msg) {
    sender.sendMessage(Component.text(msg, NamedTextColor.RED));
  }
}
