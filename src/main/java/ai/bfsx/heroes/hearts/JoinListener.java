package ai.bfsx.heroes.hearts;

import ai.bfsx.heroes.HeroesPlugin;
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
        // Eliminated players cannot join at all (banned), so no spectator handling here
        Bukkit.getScheduler().runTask(plugin, () -> plugin.hearts().applyMode(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Logging out does not clear Combat: the timer keeps running in real time,
        // so a player who logs out mid-fight and comes back within 20 s is still in Combat.
    }
}
