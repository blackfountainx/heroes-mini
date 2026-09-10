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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
        if (!plugin.hearts().isRunning()) return;
        Player p = e.getPlayer();
        if (!plugin.combat().inCombat(p.getUniqueId())) return;

        // Logging out mid-fight counts as a Combat death: items go into a grave, exactly one heart is lost
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) items.add(it.clone());
        }
        if (!items.isEmpty()) {
            plugin.graves().create(p, p.getLocation(), items);
            p.getInventory().clear();
        }

        plugin.combat().clear(p.getUniqueId());
        int remaining = plugin.hearts().loseHeart(p);
        if (remaining > 0) {
            Bukkit.broadcast(Component.text(p.getName() + " logged out during Combat and lost a Special Heart! "
                    + remaining + " left.", NamedTextColor.RED));
        } else {
            Bukkit.broadcast(Component.text("☠ " + p.getName() + " logged out during Combat and is eliminated!",
                    NamedTextColor.DARK_RED));
            plugin.hearts().checkWinner();
        }
    }
}
