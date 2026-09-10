package ai.bfsx.heroes.hearts;

import ai.bfsx.heroes.HeroesPlugin;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Forbidden items during a live event: Mace, Enchanted Golden Apple, and the Mending enchantment.
 * Blocks crafting, anvil combining, ground pickups, inventory moves and eating.
 * Ops/admins (heroes.admin) are exempt; nothing is enforced unless the event runs
 * and ban-forbidden-items is true.
 */
public class BannedItemListener implements Listener {

    private static final Component BANNED_MSG =
            Component.text("That item is banned during the Heroes event.", NamedTextColor.RED);

    private final HeroesPlugin plugin;
    private final Enchantment mending;

    public BannedItemListener(HeroesPlugin plugin) {
        this.plugin = plugin;
        this.mending = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft("mending"));
    }

    /** True when forbidden-item rules apply to this viewer right now. */
    private boolean enforcing(HumanEntity viewer) {
        return plugin.hearts().isRunning()
                && plugin.getConfig().getBoolean("ban-forbidden-items", true)
                && viewer instanceof Player p
                && !p.hasPermission("heroes.admin");
    }

    /** Mace, Enchanted Golden Apple, or any item carrying Mending. */
    private boolean isBanned(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (it.getType() == Material.MACE || it.getType() == Material.ENCHANTED_GOLDEN_APPLE) return true;
        return mending != null && it.getItemMeta().hasEnchant(mending);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        // Fires on every grid change, so no chat message here - the output slot just stays empty
        if (!enforcing(e.getView().getPlayer())) return;
        if (isBanned(e.getInventory().getResult())) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!enforcing(e.getWhoClicked())) return;
        if (isBanned(e.getInventory().getResult())) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) p.sendMessage(BANNED_MSG);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        if (!enforcing(e.getView().getPlayer())) return;
        ItemStack result = e.getResult();
        if (!isBanned(result)) return;
        if (result.getType() == Material.MACE || result.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            e.setResult(null);
            return;
        }
        // Mending came out of the combine - strip it, keep the rest of the result
        ItemStack cleaned = result.clone();
        ItemMeta meta = cleaned.getItemMeta();
        meta.removeEnchant(mending);
        cleaned.setItemMeta(meta);
        e.setResult(cleaned);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!enforcing(p)) return;
        if (isBanned(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
            p.sendMessage(BANNED_MSG);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!enforcing(e.getWhoClicked())) return;
        if (isBanned(e.getCurrentItem())) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) p.sendMessage(BANNED_MSG);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!enforcing(e.getPlayer())) return;
        if (isBanned(e.getItem())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(BANNED_MSG);
        }
    }
}
