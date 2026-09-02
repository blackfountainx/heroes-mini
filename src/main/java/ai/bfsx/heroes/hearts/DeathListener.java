package ai.bfsx.heroes.hearts;

import ai.bfsx.heroes.HeroesPlugin;
import ai.bfsx.heroes.grave.Grave;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DeathListener implements Listener {

    private final HeroesPlugin plugin;

    public DeathListener(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();

        // ---- Grave: no scattered items, everything goes into a grave at the death spot
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) items.add(it.clone());
        }
        if (!items.isEmpty()) {
            e.getDrops().clear();
            e.setKeepInventory(false);
            Location deathLoc = p.getLocation();
            Grave grave = plugin.graves().create(p, deathLoc, items);
            Location g = grave.location();
            p.sendMessage(Component.text("Your grave is at ", NamedTextColor.GRAY)
                    .append(Component.text(g.getBlockX() + " " + g.getBlockY() + " " + g.getBlockZ(), NamedTextColor.AQUA))
                    .append(Component.text(" (" + g.getWorld().getName() + "). Anyone can loot it.", NamedTextColor.GRAY)));
        }

        // ---- Special Hearts: only lost when the player died while in Combat
        if (!plugin.hearts().isRunning()) return;
        plugin.hearts().ensureRegistered(p);
        boolean wasInCombat = plugin.combat().inCombat(p.getUniqueId());
        plugin.combat().clear(p.getUniqueId());

        if (!wasInCombat) {
            p.sendMessage(Component.text("You died outside of Combat - no Special Heart lost.", NamedTextColor.GRAY));
            return;
        }

        int remaining = plugin.hearts().loseHeart(p);
        if (remaining > 0) {
            Bukkit.broadcast(Component.text(p.getName() + " lost a Special Heart! " + remaining + " left.", NamedTextColor.RED));
        } else {
            Bukkit.broadcast(Component.text("☠ " + p.getName() + " has lost all Special Hearts and is eliminated!", NamedTextColor.DARK_RED));
            plugin.hearts().checkWinner();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        // Apply spectator one tick after respawn so the game mode change sticks
        Bukkit.getScheduler().runTask(plugin, () -> plugin.hearts().applyMode(p));
    }
}
