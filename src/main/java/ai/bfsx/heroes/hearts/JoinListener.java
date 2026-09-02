package ai.bfsx.heroes.hearts;

import ai.bfsx.heroes.HeroesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinListener implements Listener {

    private final HeroesPlugin plugin;

    public JoinListener(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.hearts().isRunning()) return;
        plugin.hearts().ensureRegistered(p);
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.hearts().applyMode(p);
            if (plugin.hearts().isEliminated(p.getUniqueId())) {
                p.sendMessage(Component.text("You are eliminated - you can watch the rest of the event as a spectator.",
                        NamedTextColor.GRAY));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Logging out does not clear Combat: the timer keeps running in real time,
        // so a player who logs out mid-fight and comes back within 20 s is still in Combat.
    }
}
