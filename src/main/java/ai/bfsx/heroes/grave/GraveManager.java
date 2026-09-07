package ai.bfsx.heroes.grave;

import ai.bfsx.heroes.HeroesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates, shows, opens and persists graves.
 * Visual: a small invisible armor stand wearing the dead player's head, with a floating name tag.
 */
public class GraveManager {

    private final HeroesPlugin plugin;
    private final File file;
    private final NamespacedKey markerKey;
    private final Map<UUID, Grave> graves = new LinkedHashMap<>();

    public GraveManager(HeroesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
        this.markerKey = new NamespacedKey(plugin, "grave");
    }

    public NamespacedKey markerKey() { return markerKey; }

    public Collection<Grave> all() { return graves.values(); }

    public Grave get(UUID id) { return graves.get(id); }

    /** Finds a grave by the first characters of its id (for commands). */
    public Grave findByPrefix(String prefix) {
        for (Grave g : graves.values()) {
            if (g.id().toString().startsWith(prefix.toLowerCase())) return g;
        }
        return null;
    }

    /** Grave the given entity represents, or null. */
    public Grave fromEntity(Entity entity) {
        if (!(entity instanceof ArmorStand)) return null;
        String id = entity.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        if (id == null) return null;
        try {
            return graves.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ create

    public Grave create(Player dead, Location deathLocation, List<ItemStack> items) {
        Location loc = SafeLocation.find(deathLocation, plugin.getConfig().getInt("grave-safe-radius", 6));
        ItemStack[] contents = new ItemStack[Grave.SLOTS];
        int i = 0;
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack it : items) {
            if (i < Grave.SLOTS) contents[i++] = it;
            else overflow.add(it);
        }
        Grave grave = new Grave(UUID.randomUUID(), dead.getUniqueId(), dead.getName(), loc,
                System.currentTimeMillis(), contents);
        graves.put(grave.id(), grave);
        spawnMarker(grave);
        // More items than the grave can hold (should not happen: player inventory is 41 slots) -> drop the rest
        for (ItemStack it : overflow) loc.getWorld().dropItemNaturally(loc, it);
        save();
        return grave;
    }

    // ------------------------------------------------------------------ marker

    private void spawnMarker(Grave grave) {
        Location loc = grave.location();
        World world = loc.getWorld();
        if (world == null) return;
        loc.getChunk().load();
        removeMarkersAt(grave, loc);

        Location standLoc = loc.clone();
        // A small armor stand is ~1 block tall; sink it so the head sits on the ground
        standLoc.setY(loc.getBlockY() - 0.7);
        standLoc.setX(loc.getBlockX() + 0.5);
        standLoc.setZ(loc.getBlockZ() + 0.5);

        ArmorStand stand = (ArmorStand) world.spawnEntity(standLoc, EntityType.ARMOR_STAND);
        stand.setSmall(true);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setInvulnerable(true);
        stand.setPersistent(true);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.customName(Component.text(grave.ownerName() + "'s grave", NamedTextColor.GRAY));
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, grave.id().toString());

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(grave.owner()));
            head.setItemMeta(meta);
        }
        stand.getEquipment().setHelmet(head);

        grave.setMarkerId(stand.getUniqueId());
    }

    private void removeMarkersAt(Grave grave, Location loc) {
        for (Entity e : loc.getChunk().getEntities()) {
            if (!(e instanceof ArmorStand)) continue;
            String id = e.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
            if (grave.id().toString().equals(id)) e.remove();
        }
    }

    private void removeMarker(Grave grave) {
        Location loc = grave.location();
        if (loc.getWorld() == null) return;
        loc.getChunk().load();
        if (grave.markerId() != null) {
            Entity e = Bukkit.getEntity(grave.markerId());
            if (e != null) e.remove();
        }
        removeMarkersAt(grave, loc);
    }

    /** True when the grave's marker stand still exists and carries this grave's id. */
    private boolean isMarkerAlive(Grave grave) {
        if (grave.markerId() == null) return false;
        Entity e = Bukkit.getEntity(grave.markerId());
        if (!(e instanceof ArmorStand stand) || !e.isValid()) return false;
        String id = stand.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        return grave.id().toString().equals(id);
    }

    /** Re-spawns the marker if it went missing at runtime (entity clear, /kill, chunk edge case). */
    public void reviveMarkerIfMissing(Grave grave) {
        if (isMarkerAlive(grave)) return;
        Location loc = grave.location();
        // Never force-load chunks: unloaded graves wait until their chunk is loaded again
        if (loc.getWorld() == null || !loc.getChunk().isLoaded()) return;
        spawnMarker(grave);
        save();
    }

    /** Checks every grave in a loaded chunk and re-spawns missing markers. */
    public void reviveAllInLoadedChunks() {
        for (Grave g : graves.values()) {
            Location loc = g.location();
            if (loc.getWorld() == null || !loc.getChunk().isLoaded()) continue;
            reviveMarkerIfMissing(g);
        }
    }

    // ------------------------------------------------------------------ open / loot

    public void open(Player player, Grave grave) {
        if (grave.openedBy() != null) {
            Player other = Bukkit.getPlayer(grave.openedBy());
            if (other != null && other.isOnline()) {
                if (!other.equals(player)) {
                    player.sendMessage(Component.text(other.getName() + " is already looting this grave.", NamedTextColor.RED));
                }
                return; // already open by an online player (incl. this one) -> do not open a second view
            }
        }
        GraveHolder holder = new GraveHolder(grave.id());
        Inventory inv = Bukkit.createInventory(holder, Grave.SLOTS,
                Component.text("Grave of " + grave.ownerName(), NamedTextColor.DARK_GRAY));
        holder.setInventory(inv);
        inv.setContents(grave.contents());
        grave.setOpenedBy(player.getUniqueId());
        player.openInventory(inv);
    }

    /** Called when the looting inventory closes: write back what is left, remove grave if empty. */
    public void closed(Grave grave, Inventory inv) {
        grave.setContents(inv.getContents());
        grave.setOpenedBy(null);
        if (grave.isEmpty()) {
            remove(grave, true);
        } else {
            save();
        }
    }

    public void remove(Grave grave, boolean announce) {
        removeMarker(grave);
        graves.remove(grave.id());
        save();
        if (announce) {
            Player owner = Bukkit.getPlayer(grave.owner());
            if (owner != null) {
                owner.sendMessage(Component.text("Your grave has been emptied and is gone.", NamedTextColor.GRAY));
            }
        }
    }

    public void removeAll() {
        for (Grave g : new ArrayList<>(graves.values())) remove(g, false);
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        graves.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("graves");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection g = sec.getConfigurationSection(key);
            if (g == null) continue;
            try {
                World world = Bukkit.getWorld(g.getString("world", "world"));
                if (world == null) {
                    plugin.getLogger().warning("Grave " + key + " is in unknown world " + g.getString("world") + ", skipped.");
                    continue;
                }
                Location loc = new Location(world, g.getDouble("x"), g.getDouble("y"), g.getDouble("z"));
                ItemStack[] contents = new ItemStack[Grave.SLOTS];
                ConfigurationSection items = g.getConfigurationSection("items");
                if (items != null) {
                    for (String slot : items.getKeys(false)) {
                        int i = Integer.parseInt(slot);
                        if (i >= 0 && i < Grave.SLOTS) contents[i] = items.getItemStack(slot);
                    }
                }
                Grave grave = new Grave(UUID.fromString(key), UUID.fromString(g.getString("owner")),
                        g.getString("owner-name", "?"), loc, g.getLong("created", System.currentTimeMillis()), contents);
                graves.put(grave.id(), grave);
                spawnMarker(grave);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not load grave " + key + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + graves.size() + " grave(s).");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Grave grave : graves.values()) {
            String k = "graves." + grave.id();
            Location loc = grave.location();
            y.set(k + ".owner", grave.owner().toString());
            y.set(k + ".owner-name", grave.ownerName());
            y.set(k + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
            y.set(k + ".x", loc.getX());
            y.set(k + ".y", loc.getY());
            y.set(k + ".z", loc.getZ());
            y.set(k + ".created", grave.createdAt());
            ItemStack[] c = grave.contents();
            for (int i = 0; c != null && i < c.length; i++) {
                if (c[i] != null && !c[i].getType().isAir()) {
                    y.set(k + ".items." + i, c[i]);
                }
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save graves.yml: " + ex.getMessage());
        }
    }

    /** Block-safety helper shared with SafeLocation. */
    static boolean isSolidGround(Block b) {
        return b.getType().isSolid() && !b.isLiquid();
    }
}
