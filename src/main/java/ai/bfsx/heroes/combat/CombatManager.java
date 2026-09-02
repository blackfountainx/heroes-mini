package ai.bfsx.heroes.combat;

import ai.bfsx.heroes.HeroesPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat timer per player. A PvP hit puts both attacker and victim into Combat
 * for the configured number of seconds; every further hit refreshes the timer.
 */
public class CombatManager {

    private final HeroesPlugin plugin;
    private final Map<UUID, Long> combatUntil = new HashMap<>();

    public CombatManager(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    public int combatSeconds() {
        return plugin.getConfig().getInt("combat-seconds", HeroesPlugin.COMBAT_SECONDS);
    }

    public void tag(Player p) {
        combatUntil.put(p.getUniqueId(), System.currentTimeMillis() + combatSeconds() * 1000L);
    }

    public void clear(UUID id) {
        combatUntil.remove(id);
    }

    public boolean inCombat(UUID id) {
        return remainingSeconds(id) > 0;
    }

    /** Whole seconds left, rounded up; 0 when not in combat. */
    public int remainingSeconds(UUID id) {
        Long until = combatUntil.get(id);
        if (until == null) return 0;
        long ms = until - System.currentTimeMillis();
        if (ms <= 0) {
            combatUntil.remove(id);
            return 0;
        }
        return (int) Math.ceil(ms / 1000.0);
    }
}
