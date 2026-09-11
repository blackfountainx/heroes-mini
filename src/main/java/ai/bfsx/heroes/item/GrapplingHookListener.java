package ai.bfsx.heroes.item;

import ai.bfsx.heroes.HeroesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Craftable Grappling Hooks (iron + diamond) on a carrot-on-a-stick base, no resource pack needed.
 * First right-click raytraces an anchor (block or entity) within hook-max-range and draws a
 * particle line to it; second right-click flings the player toward the anchor, consumes one use
 * (lore "Uses left" + the vanilla durability bar mirror remaining uses) and grants ~6 seconds of
 * fall-damage immunity so the landing never kills. A normal tool: usable regardless of event
 * state, no cooldown, breaks at 0 uses.
 */
public class GrapplingHookListener implements Listener {

    public static final String VARIANT_IRON = "iron";
    public static final String VARIANT_DIAMOND = "diamond";

    /** Fall damage is forgiven for this long after a pull (ms). */
    private static final long FALL_GRACE_MILLIS = 6000L;
    private static final int IRON_MODEL_DATA = 1001;
    private static final int DIAMOND_MODEL_DATA = 1002;

    private final HeroesPlugin plugin;
    private final NamespacedKey variantKey;
    private final NamespacedKey usesKey;
    private final Map<UUID, Location> anchors = new HashMap<>();
    private final Map<UUID, Long> lastPullMillis = new HashMap<>();

    public GrapplingHookListener(HeroesPlugin plugin) {
        this.plugin = plugin;
        this.variantKey = new NamespacedKey(plugin, "hook_variant");
        this.usesKey = new NamespacedKey(plugin, "hook_uses");
    }

    /** Registers both crafting recipes; called from onEnable. */
    public void registerRecipes() {
        registerRecipe(VARIANT_IRON, Material.IRON_INGOT, "grappling_hook_iron");
        registerRecipe(VARIANT_DIAMOND, Material.DIAMOND, "grappling_hook_diamond");
    }

    /** Recipes are re-added on every enable, so drop them on disable to survive /reload cleanly. */
    public void unregisterRecipes() {
        Bukkit.removeRecipe(new NamespacedKey(plugin, "grappling_hook_iron"));
        Bukkit.removeRecipe(new NamespacedKey(plugin, "grappling_hook_diamond"));
    }

    private void registerRecipe(String variant, Material top, String keyName) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, keyName), createHook(variant));
        recipe.shape(" I ", " L ", " LS");
        recipe.setIngredient('I', top);
        recipe.setIngredient('L', Material.LEAD);
        recipe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(recipe);
    }

    /** Hook identity lives in the PDC (variant + uses) so it survives as a plain ItemStack. */
    public ItemStack createHook(String variant) {
        int maxUses = maxUses(variant);
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = item.getItemMeta();
        boolean diamond = VARIANT_DIAMOND.equals(variant);
        meta.displayName(Component.text(diamond ? "Diamond Grappling Hook" : "Iron Grappling Hook",
                diamond ? NamedTextColor.AQUA : NamedTextColor.WHITE));
        meta.setCustomModelData(diamond ? DIAMOND_MODEL_DATA : IRON_MODEL_DATA);
        meta.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, variant);
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, maxUses);
        meta.lore(List.of(Component.text("Uses left: " + maxUses, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private int maxUses(String variant) {
        return VARIANT_DIAMOND.equals(variant)
                ? plugin.getConfig().getInt("hook-diamond-uses", 165)
                : plugin.getConfig().getInt("hook-iron-uses", 100);
    }

    /** The hook variant carried by this stack, or null if it is not a grappling hook. */
    private String hookVariant(ItemStack item) {
        if (item == null || item.getType() != Material.CARROT_ON_A_STICK || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(variantKey, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        String variant = hookVariant(item);
        if (variant == null) return;
        // A hook in each hand would fire both events on one click: only the main hand acts.
        if (e.getHand() == EquipmentSlot.OFF_HAND && hookVariant(e.getPlayer().getInventory().getItemInMainHand()) != null) return;
        e.setCancelled(true); // no door opening, pig steering, etc. through the hook

        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR) return;

        Location anchor = anchors.remove(p.getUniqueId());
        if (anchor == null) {
            shootAnchor(p);
        } else {
            pull(p, anchor, item, e.getHand(), variant);
        }
    }

    /** First click: store the anchor point and show the player where it landed. */
    private void shootAnchor(Player p) {
        // hook-max-range 250 needs allow-flight=true in server.properties, or long pulls
        // can trip a "Flying is not enabled" kick (vanilla anti-fly, not a plugin bug).
        int range = plugin.getConfig().getInt("hook-max-range", 30);
        Location eye = p.getEyeLocation();
        World world = p.getWorld();
        RayTraceResult hit = world.rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.0, en -> !en.equals(p) && !(en instanceof Player q && q.getGameMode() == GameMode.SPECTATOR));
        if (hit == null) {
            p.sendActionBar(Component.text("Hook hit nothing", NamedTextColor.RED));
            return;
        }
        // Blocks, players and mobs all store the same thing: the contact point. The user is
        // always flung TO this anchor; the hit target is never pulled toward the user.
        Location anchor = hit.getHitPosition().toLocation(world);
        anchors.put(p.getUniqueId(), anchor);
        drawLine(eye.toVector(), hit.getHitPosition(), world);
        p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1.4f);
    }

    private void drawLine(Vector from, Vector to, World world) {
        double dist = from.distance(to);
        if (dist < 0.5) return;
        int points = Math.max(2, (int) (dist / 0.75));
        for (int i = 1; i <= points; i++) {
            Vector point = to.clone().subtract(from).multiply((double) i / points).add(from);
            world.spawnParticle(Particle.END_ROD, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0);
        }
    }

    /** Second click: fling the player to the anchor, forgive the landing, spend one use. */
    private void pull(Player p, Location anchor, ItemStack item, EquipmentSlot hand, String variant) {
        Vector dir = anchor.toVector().subtract(p.getEyeLocation().toVector());
        double dist = dir.length();
        // Zero-length normalize throws; a point-blank anchor just gets the upward boost.
        // Power stays capped at 2.5 even for 250-block anchors: a controlled, repeatable
        // arc instead of one violent yank (re-click to re-anchor and continue).
        Vector v = dist < 0.01 ? new Vector(0, 0.4, 0)
                : dir.normalize().multiply(Math.min(dist * 0.25, 2.5)).add(new Vector(0, 0.4, 0));
        p.setVelocity(v);
        lastPullMillis.put(p.getUniqueId(), System.currentTimeMillis());
        p.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.7f);
        consumeUse(p, item, hand, variant);
    }

    private void consumeUse(Player p, ItemStack item, EquipmentSlot hand, String variant) {
        ItemMeta meta = item.getItemMeta();
        int uses = meta.getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, 1);
        int left = uses - 1;
        if (left <= 0) {
            p.getInventory().setItem(hand, null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, left);
        meta.lore(List.of(Component.text("Uses left: " + left, NamedTextColor.GRAY)));
        if (meta instanceof Damageable dmg) {
            // Bar mirrors remaining uses: full at max uses, nearly empty at 1.
            int maxDur = item.getType().getMaxDurability();
            dmg.setDamage((int) Math.round(maxDur * (1.0 - (double) left / maxUses(variant))));
        }
        item.setItemMeta(meta);
        p.getInventory().setItem(hand, item);
    }

    /** Grappling shouldn't kill you on landing: fall damage is cancelled shortly after a pull. */
    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        Long pulled = lastPullMillis.get(p.getUniqueId());
        if (pulled == null) return;
        if (System.currentTimeMillis() - pulled <= FALL_GRACE_MILLIS) {
            e.setCancelled(true);
        } else {
            lastPullMillis.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        anchors.remove(e.getPlayer().getUniqueId());
        lastPullMillis.remove(e.getPlayer().getUniqueId());
    }
}
