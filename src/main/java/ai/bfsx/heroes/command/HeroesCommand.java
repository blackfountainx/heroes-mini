package ai.bfsx.heroes.command;

import ai.bfsx.heroes.HeroesPlugin;
import ai.bfsx.heroes.grave.Grave;
import ai.bfsx.heroes.hearts.HeartsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /heroes start|stop|reset|status
 * /heroes protection [end]
 * /heroes hearts <player> <n>
 * /heroes revive <player>
 * /heroes combat <player> clear
 * /heroes graves [tp <id>|remove <id>|clear]
 */
public class HeroesCommand implements TabExecutor {

    private final HeroesPlugin plugin;

    public HeroesCommand(HeroesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String[] a) {
        if (a.length == 0) return usage(s);
        HeartsManager hearts = plugin.hearts();
        switch (a[0].toLowerCase()) {
            case "start" -> {
                if (hearts.isRunning()) return msg(s, "The event is already running.", NamedTextColor.RED);
                hearts.start();
                return true;
            }
            case "stop" -> {
                hearts.stop();
                return true;
            }
            case "reset" -> {
                hearts.reset();
                plugin.graves().removeAll();
                return msg(s, "Event reset: hearts cleared, all graves removed, state IDLE.", NamedTextColor.GREEN);
            }
            case "status" -> {
                msg(s, "State: " + hearts.getState() + " | Graves: " + plugin.graves().all().size(), NamedTextColor.GOLD);
                for (Map.Entry<UUID, Integer> e : hearts.all().entrySet()) {
                    Player online = Bukkit.getPlayer(e.getKey());
                    String combat = online != null && plugin.combat().inCombat(e.getKey())
                            ? " ⚔" + plugin.combat().remainingSeconds(e.getKey()) + "s" : "";
                    msg(s, "  " + hearts.nameOf(e.getKey()) + ": " + "❤".repeat(Math.max(0, e.getValue()))
                            + (e.getValue() <= 0 ? " eliminated" : "") + combat,
                            e.getValue() > 0 ? NamedTextColor.RED : NamedTextColor.DARK_GRAY);
                }
                return true;
            }
            case "protection" -> {
                if (a.length >= 2 && a[1].equalsIgnoreCase("end")) {
                    hearts.endProtection();
                    return msg(s, "Protection phase ended. PvP is live.", NamedTextColor.GREEN);
                }
                if (hearts.isProtected()) {
                    return msg(s, "Protection active: " + hearts.protectionRemainingSeconds() + "s remaining.", NamedTextColor.AQUA);
                }
                return msg(s, "No protection phase active.", NamedTextColor.GRAY);
            }
            case "hearts" -> {
                if (a.length < 3) return usage(s);
                OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
                int n;
                try {
                    n = Integer.parseInt(a[2]);
                } catch (NumberFormatException ex) {
                    return msg(s, "Amount must be a number.", NamedTextColor.RED);
                }
                hearts.setHearts(target.getUniqueId(), target.getName(), n);
                hearts.checkWinner();
                return msg(s, hearts.nameOf(target.getUniqueId()) + " now has " + Math.max(0, n) + " Special Hearts.", NamedTextColor.GREEN);
            }
            case "revive" -> {
                if (a.length < 2) return usage(s);
                OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
                hearts.setHearts(target.getUniqueId(), target.getName(), 1);
                return msg(s, hearts.nameOf(target.getUniqueId()) + " revived with 1 Special Heart.", NamedTextColor.GREEN);
            }
            case "combat" -> {
                if (a.length < 2) return usage(s);
                OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
                plugin.combat().clear(target.getUniqueId());
                return msg(s, "Combat cleared for " + hearts.nameOf(target.getUniqueId()) + ".", NamedTextColor.GREEN);
            }
            case "graves" -> {
                if (a.length == 1) {
                    if (plugin.graves().all().isEmpty()) return msg(s, "No graves.", NamedTextColor.GRAY);
                    for (Grave g : plugin.graves().all()) {
                        Location l = g.location();
                        msg(s, g.shortId() + "  " + g.ownerName() + "  " + l.getWorld().getName() + " "
                                + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                                + "  (" + g.nonEmptyItems().size() + " items)", NamedTextColor.GRAY);
                    }
                    return true;
                }
                if (a[1].equalsIgnoreCase("clear")) {
                    plugin.graves().removeAll();
                    return msg(s, "All graves removed.", NamedTextColor.GREEN);
                }
                if (a.length < 3) return usage(s);
                Grave g = plugin.graves().findByPrefix(a[2]);
                if (g == null) return msg(s, "No grave starting with " + a[2] + ".", NamedTextColor.RED);
                if (a[1].equalsIgnoreCase("tp")) {
                    if (!(s instanceof Player p)) return msg(s, "Only players can teleport.", NamedTextColor.RED);
                    p.teleport(g.location().add(0, 1, 0));
                    return msg(s, "Teleported to " + g.ownerName() + "'s grave.", NamedTextColor.GREEN);
                }
                if (a[1].equalsIgnoreCase("remove")) {
                    plugin.graves().remove(g, false);
                    return msg(s, "Grave " + g.shortId() + " removed (items deleted).", NamedTextColor.GREEN);
                }
                return usage(s);
            }
            default -> {
                return usage(s);
            }
        }
    }

    private boolean usage(CommandSender s) {
        msg(s, "/heroes start | stop | reset | status | protection [end]", NamedTextColor.YELLOW);
        msg(s, "/heroes hearts <player> <n>   /heroes revive <player>   /heroes combat <player>", NamedTextColor.YELLOW);
        msg(s, "/heroes graves [tp <id> | remove <id> | clear]", NamedTextColor.YELLOW);
        return true;
    }

    private boolean msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : List.of("start", "stop", "reset", "status", "protection", "hearts", "revive", "combat", "graves")) {
                if (o.startsWith(a[0].toLowerCase())) out.add(o);
            }
        } else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "hearts", "revive", "combat" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
                    }
                }
                case "graves" -> {
                    for (String o : List.of("tp", "remove", "clear")) {
                        if (o.startsWith(a[1].toLowerCase())) out.add(o);
                    }
                }
                case "protection" -> {
                    for (String o : List.of("end")) {
                        if (o.startsWith(a[1].toLowerCase())) out.add(o);
                    }
                }
                default -> {
                }
            }
        } else if (a.length == 3) {
            if (a[0].equalsIgnoreCase("hearts")) {
                out.addAll(List.of("0", "1", "2", "3"));
            } else if (a[0].equalsIgnoreCase("graves")) {
                for (Grave g : plugin.graves().all()) {
                    if (g.id().toString().startsWith(a[2].toLowerCase())) out.add(g.shortId());
                }
            }
        }
        return out;
    }
}
