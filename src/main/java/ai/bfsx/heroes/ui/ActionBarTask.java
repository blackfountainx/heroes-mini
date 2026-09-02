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
 */
public class ActionBarTask extends BukkitRunnable {

    private final HeroesPlugin plugin;

    public ActionBarTask(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.hearts().isRunning()) return;
        int max = plugin.hearts().startHearts();
        for (Player p : Bukkit.getOnlinePlayers()) {
            int have = plugin.hearts().getHearts(p.getUniqueId());
            Component line = Component.empty();
            for (int i = 0; i < max; i++) {
                line = line.append(Component.text("❤", i < have ? NamedTextColor.RED : NamedTextColor.DARK_GRAY));
            }
            if (have <= 0) {
                line = line.append(Component.text("  ELIMINATED", NamedTextColor.DARK_RED, TextDecoration.BOLD));
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
