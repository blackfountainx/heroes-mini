package ai.bfsx.heroes.ui;

import ai.bfsx.heroes.HeroesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Once per second: "❤❤❤   ⚔ 17s" in the action bar (the text line above the XP bar).
 * Lost hearts are shown dark, the Combat countdown only while in Combat.
 * During the opening protection phase the countdown shows "Protection <s>s" instead.
 */
public class ActionBarTask extends BukkitRunnable {

    private final HeroesPlugin plugin;
    private boolean wasProtected = false;

    public ActionBarTask(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.hearts().isRunning()) return;
        boolean prot = plugin.hearts().isProtected();
        if (wasProtected && !prot) {
            Bukkit.broadcast(Component.text("Protection phase over — PvP is now live!", NamedTextColor.RED));
        }
        wasProtected = prot;
        int max = plugin.hearts().startHearts();
        for (Player p : Bukkit.getOnlinePlayers()) {
            int have = plugin.hearts().getHearts(p.getUniqueId());
            Component line = Component.empty();
            for (int i = 0; i < max; i++) {
                // BLUE per stakeholder request; switch to AQUA if BLUE reads too dark in-game.
                line = line.append(Component.text("❤", i < have ? NamedTextColor.BLUE : NamedTextColor.GRAY));
            }
            if (have <= 0) {
                line = line.append(Component.text("  ELIMINATED", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            } else if (prot) {
                // No shield icon: vanilla clients cannot render U+1F6E1, so plain "Protection <s>s".
                line = line.append(Component.text("   Protection " + plugin.hearts().protectionRemainingSeconds() + "s",
                        NamedTextColor.AQUA));
            } else {
                int secs = plugin.combat().remainingSeconds(p.getUniqueId());
                if (secs > 0) {
                    line = line.append(Component.text("   ⚔ Combat " + secs + "s",
                            secs <= 5 ? NamedTextColor.YELLOW : NamedTextColor.GOLD));
                }
            }
            p.sendActionBar(line);
        }
    }
}
