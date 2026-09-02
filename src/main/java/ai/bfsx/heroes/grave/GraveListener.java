package ai.bfsx.heroes.grave;

import ai.bfsx.heroes.HeroesPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

public class GraveListener implements Listener {

    private final HeroesPlugin plugin;

    public GraveListener(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Right-click on the grave marker opens the loot GUI. Any player may loot any grave. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractAtEntityEvent e) {
        Grave grave = plugin.graves().fromEntity(e.getRightClicked());
        if (grave == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        plugin.graves().open(p, grave);
    }

    /** Nobody may take the head off the marker or move its pose. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (plugin.graves().fromEntity(e.getRightClicked()) != null) e.setCancelled(true);
    }

    /** Markers cannot be destroyed. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (plugin.graves().fromEntity(e.getEntity()) != null) e.setCancelled(true);
    }

    /** Closing the GUI writes the remaining items back; an empty grave disappears. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof GraveHolder holder)) return;
        Grave grave = plugin.graves().get(holder.graveId());
        if (grave == null) return;
        plugin.graves().closed(grave, e.getInventory());
    }
}
