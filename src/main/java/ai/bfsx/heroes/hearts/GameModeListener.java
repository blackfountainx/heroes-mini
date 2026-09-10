package ai.bfsx.heroes.hearts;

import ai.bfsx.heroes.HeroesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

/**
 * During a running event, regular players cannot switch to Creative or Spectator;
 * ops/admins (heroes.admin) are exempt. The plugin's own SURVIVAL restores are never blocked.
 */
public class GameModeListener implements Listener {

    private final HeroesPlugin plugin;

    public GameModeListener(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        if (!plugin.hearts().isRunning()) return;
        Player p = e.getPlayer();
        if (p.hasPermission("heroes.admin")) return;
        GameMode target = e.getNewGameMode();
        if (target == GameMode.CREATIVE || target == GameMode.SPECTATOR) {
            e.setCancelled(true);
            p.sendMessage(Component.text("You can't change game mode during the Heroes event.",
                    NamedTextColor.RED));
        }
    }
}
