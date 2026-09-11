package ai.bfsx.heroes.item;

import ai.bfsx.heroes.HeroesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Craftable Backpack: 7 leather + 1 chest + 1 string, right-click to open a 54-slot inventory
 * whose contents persist per backpack. Identity is a UUID in the "backpack_id" PDC stamped at
 * craft time (fresh UUID on every PrepareItemCraft, so two crafted backpacks never share
 * contents) or lazily on first open; contents live in backpacks.yml keyed by that id, never in
 * the item. Dropped/died backpacks ride the normal grave flow untouched - whoever loots the item
 * opens the same contents. A normal utility item: usable regardless of event state.
 */
public class BackpackListener implements Listener {

    private static final int SIZE = 54;
    private static final int MODEL_DATA = 1003;

    private final HeroesPlugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey markerKey;
    private final NamespacedKey recipeKey;
    /** backpack id -> contents; loaded on enable, written back on close/disable. */
    private final Map<String, ItemStack[]> contents = new HashMap<>();

    public BackpackListener(HeroesPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "backpack_id");
        this.markerKey = new NamespacedKey(plugin, "backpack_item");
        this.recipeKey = new NamespacedKey(plugin, "backpack");
    }

    /** Identifies the open backpack GUI through the inventory's holder. */
    private static final class BackpackHolder implements InventoryHolder {
        private final String id;
        private Inventory inventory;

        private BackpackHolder(String id) {
            this.id = id;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Registers the crafting recipe; called from onEnable. */
    public void registerRecipes() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createBackpack());
        recipe.shape("LLL", "LCL", "LSL");
        recipe.setIngredient('L', Material.LEATHER);
        recipe.setIngredient('C', Material.CHEST);
        recipe.setIngredient('S', Material.STRING);
        Bukkit.addRecipe(recipe);
    }

    /** Recipes are re-added on every enable, so drop them on disable to survive /reload cleanly. */
    public void unregisterRecipes() {
        Bukkit.removeRecipe(recipeKey);
    }

    public void load() {
        File file = file();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            byte[] data = Base64.getDecoder().decode(yaml.getString(id, ""));
            try {
                contents.put(id, ItemStack.deserializeItemsFromBytes(data));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping corrupt backpack " + id + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + contents.size() + " backpack(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, ItemStack[]> e : contents.entrySet()) {
            yaml.set(e.getKey(), Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(e.getValue())));
        }
        try {
            yaml.save(file());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save backpacks.yml: " + ex.getMessage());
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), "backpacks.yml");
    }

    /** The base item: marker PDC identifies it, the id is stamped per craft/open. */
    private ItemStack createBackpack() {
        ItemStack item = new ItemStack(Material.LEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Backpack", NamedTextColor.GOLD));
        meta.lore(java.util.List.of(Component.text("Right-click to open (54 slots)", NamedTextColor.GRAY)));
        meta.setCustomModelData(MODEL_DATA);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True if this stack is a backpack of ours (crafted or copied - with or without an id yet). */
    private boolean isBackpack(ItemStack item) {
        return item != null && item.getType() == Material.LEATHER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * The recipe result is a single fixed stack, so stamping its id at registration would share
     * one backpack across every craft. Instead re-stamp a fresh UUID every time the grid is
     * prepared: each crafting pass yields a unique backpack.
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (!(e.getRecipe() instanceof CraftingRecipe r) || !r.getKey().equals(recipeKey)) return;
        ItemStack result = createBackpack();
        // getItemMeta returns a copy - stamp the id on it, then set it back to take effect
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        result.setItemMeta(meta);
        e.getInventory().setResult(result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return; // main hand only, avoids double-fire
        ItemStack item = e.getItem();
        if (!isBackpack(item)) return;
        e.setCancelled(true);

        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) {
            // Fallback for id-less copies (creative dupes of the bare recipe result): stamp now.
            id = UUID.randomUUID().toString();
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        open(e.getPlayer(), id);
    }

    private void open(Player p, String id) {
        BackpackHolder holder = new BackpackHolder(id);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Backpack", NamedTextColor.GOLD));
        holder.inventory = inv;
        ItemStack[] saved = contents.get(id);
        if (saved != null) inv.setContents(saved);
        p.openInventory(inv);
    }

    /** Closing the GUI is the save point: write the slots back under the backpack id. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof BackpackHolder holder)) return;
        contents.put(holder.id, e.getInventory().getContents());
        save();
    }

    /** No backpacks inside backpacks: cancel any click that would move one into the open GUI. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BackpackHolder)) return;
        boolean intoTop = e.getClickedInventory() == e.getView().getTopInventory();
        ItemStack moving = null;
        switch (e.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (intoTop) moving = e.getCursor();
            }
            case MOVE_TO_OTHER_INVENTORY -> moving = e.getCurrentItem(); // shift-click from the player side
            case HOTBAR_SWAP -> {
                // -1 means the offhand swap, which is not a hotbar slot
                if (intoTop && e.getHotbarButton() >= 0) {
                    moving = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
                }
            }
            default -> { }
        }
        if (isBackpack(moving)) e.setCancelled(true);
    }

    /** Drag is the other way to place an item into the open GUI. */
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof BackpackHolder)) return;
        if (!isBackpack(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) {
            if (raw < e.getInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
