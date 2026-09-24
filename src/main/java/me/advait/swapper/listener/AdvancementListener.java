package me.advait.swapper.listener;

import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.advait.swapper.SwapperPlugin;
import me.advait.swapper.manager.SwapperManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public class AdvancementListener implements Listener {
  private final SwapperPlugin plugin;
  private final SwapperManager manager;

  public AdvancementListener(SwapperPlugin plugin, SwapperManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  private boolean isWaiting(Player p) {
    return this.manager.isWaitingPlayer(p.getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onAdvancement(PlayerAdvancementDoneEvent event) {
    Player player = event.getPlayer();
    UUID id = player.getUniqueId();
    if (this.manager.isAdvancementSyncing(id)) {
      event.message(null);
    } else if (this.manager.isInPool(id)) {
      if (event.message() != null) {
        Advancement adv = event.getAdvancement();
        AdvancementDisplay display = adv.getDisplay();
        if (display != null && display.doesAnnounceToChat()) {
          event.message(null);
          List<UUID> pool = this.manager.getOrder();
          Component playerNames = this.formatNames(pool);
          Component bracketedAdv = this.bracketed(display);
          boolean plural = pool.size() > 1;
          Component msg =
              ((Component.empty().append(playerNames))
                      .append(
                          Component.text(
                              plural
                                  ? " have made the advancement "
                                  : " has made the advancement ")))
                  .append(bracketedAdv);

          for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!this.isWaiting(viewer)) {
              viewer.sendMessage(msg);
            }
          }

          Bukkit.getConsoleSender().sendMessage(msg);

          for (UUID otherId : pool) {
            if (!otherId.equals(id)) {
              Player other = Bukkit.getPlayer(otherId);
              if (other != null) {
                AdvancementProgress prog = other.getAdvancementProgress(adv);
                if (!prog.isDone()) {
                  this.manager.markAdvancementSyncing(otherId);

                  try {
                    for (String criterion : adv.getCriteria()) {
                      if (!prog.getAwardedCriteria().contains(criterion)) {
                        prog.awardCriteria(criterion);
                      }
                    }
                  } finally {
                    this.manager.unmarkAdvancementSyncing(otherId);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private Component formatNames(List<UUID> pool) {
    List<String> names = new ArrayList<>();

    for (UUID id : pool) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) {
        names.add(p.getName());
      } else {
        String off = Bukkit.getOfflinePlayer(id).getName();
        names.add(off == null ? id.toString().substring(0, 8) : off);
      }
    }

    if (names.size() == 1) {
      return Component.text(names.get(0));
    }

    if (names.size() == 2) {
      return Component.text(names.get(0) + " & " + names.get(1));
    }

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < names.size() - 1; i++) {
      sb.append(names.get(i)).append(", ");
    }

    sb.append("& ").append(names.get(names.size() - 1));
    return Component.text(sb.toString());
  }

  private Component bracketed(AdvancementDisplay display) {
    NamedTextColor color =
        switch (display.frame()) {
          case CHALLENGE -> NamedTextColor.DARK_PURPLE;
          default -> NamedTextColor.GREEN;
        };
    Component title = display.title().colorIfAbsent(color);
    Component hover =
        ((Component.empty().append(title)).append(Component.newline()))
            .append(display.description());
    return ((((Component.text().append(Component.text("[", color))).append(title))
                .append(Component.text("]", color)))
            .hoverEvent(HoverEvent.showText(hover)))
        .build();
  }
}
