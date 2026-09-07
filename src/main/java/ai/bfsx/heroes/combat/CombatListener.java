package ai.bfsx.heroes.combat;

import ai.bfsx.heroes.HeroesPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class CombatListener implements Listener {

    private final HeroesPlugin plugin;

    public CombatListener(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    // LOW + ignoreCancelled: cancelling here also keeps the MONITOR handler below from tagging combat.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProtectedPvp(EntityDamageByEntityEvent e) {
        if (!plugin.hearts().isProtected()) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (victim.getGameMode() == GameMode.SPECTATOR || attacker.getGameMode() == GameMode.SPECTATOR) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!plugin.hearts().isRunning()) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (victim.getGameMode() == GameMode.SPECTATOR || attacker.getGameMode() == GameMode.SPECTATOR) return;
        if (e.getFinalDamage() <= 0) return;

        plugin.hearts().ensureRegistered(victim);
        plugin.hearts().ensureRegistered(attacker);
        plugin.combat().tag(victim);
        plugin.combat().tag(attacker);
    }

    /** Direct hits, arrows/tridents/snowballs, and TNT lit by a player all count as PvP. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }
}
