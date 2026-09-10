package ai.bfsx.heroes.hearts;

import ai.bfsx.heroes.HeroesPlugin;
import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Special Hearts and event state. Persisted to data.yml on every change.
 */
public class HeartsManager {

    public enum State { IDLE, RUNNING, ENDED }

    private final HeroesPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> hearts = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    /** Players banned by this plugin for elimination — never touch bans from other sources. */
    private final Set<UUID> eventBanned = new HashSet<>();
    private State state = State.IDLE;
    private long protectionUntil = 0L;

    public HeartsManager(HeroesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public int startHearts() {
        return plugin.getConfig().getInt("start-hearts", HeroesPlugin.START_HEARTS);
    }

    public int protectionSeconds() {
        return plugin.getConfig().getInt("protection-seconds", HeroesPlugin.PROTECTION_SECONDS);
    }

    /** True while the opening PvP protection phase is active. */
    public boolean isProtected() {
        return isRunning() && protectionUntil > System.currentTimeMillis();
    }

    /** Whole seconds of protection left, rounded up; 0 when not protected. */
    public int protectionRemainingSeconds() {
        if (!isProtected()) return 0;
        return (int) Math.ceil((protectionUntil - System.currentTimeMillis()) / 1000.0);
    }

    public void endProtection() {
        protectionUntil = 0L;
        save();
    }

    public State getState() { return state; }

    public boolean isRunning() { return state == State.RUNNING; }

    /** Hearts of a player. Players unknown to the event start with the full amount once the event runs. */
    public int getHearts(UUID id) {
        return hearts.getOrDefault(id, startHearts());
    }

    public boolean isEliminated(UUID id) {
        return hearts.containsKey(id) && hearts.get(id) <= 0;
    }

    public boolean isParticipant(UUID id) {
        return hearts.containsKey(id);
    }

    public Map<UUID, Integer> all() { return new LinkedHashMap<>(hearts); }

    public String nameOf(UUID id) {
        String n = names.get(id);
        if (n != null) return n;
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : id.toString().substring(0, 8);
    }

    /** Registers an online player into the running event if not yet known. */
    public void ensureRegistered(Player p) {
        if (!isRunning()) return;
        if (!hearts.containsKey(p.getUniqueId())) {
            hearts.put(p.getUniqueId(), startHearts());
            names.put(p.getUniqueId(), p.getName());
            save();
        }
    }

    public void setHearts(UUID id, String name, int amount) {
        hearts.put(id, Math.max(0, amount));
        if (name != null) names.put(id, name);
        save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) applyMode(p);
    }

    /** Removes one Special Heart. Returns the remaining amount. */
    public int loseHeart(Player p) {
        int remaining = Math.max(0, getHearts(p.getUniqueId()) - 1);
        hearts.put(p.getUniqueId(), remaining);
        names.put(p.getUniqueId(), p.getName());
        if (remaining <= 0) {
            banForElimination(p);
        } else {
            save();
        }
        return remaining;
    }

    /** Elimination means a real server ban until the next event, not spectator mode. */
    private void banForElimination(Player p) {
        eventBanned.add(p.getUniqueId());
        Bukkit.getBanList(BanListType.PROFILE).addBan(p.getPlayerProfile(),
                "Eliminated - you lost all your Special Hearts!", (Date) null, "HeroesMini");
        p.kick(Component.text("You lost all 3 Special Hearts - you're out! Good game.", NamedTextColor.RED));
        save();
    }

    /** Lifts only the bans this plugin created; bans set by other means are untouched. */
    public void clearEventBans() {
        ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
        for (UUID id : eventBanned) {
            bans.pardon(Bukkit.createProfile(id));
        }
        eventBanned.clear();
        save();
    }

    /** Revive: lifts an event ban if present and restores 1 heart. Returns true if a ban was lifted. */
    public boolean revive(UUID id, String name) {
        boolean wasBanned = eventBanned.remove(id);
        if (wasBanned) {
            Bukkit.getBanList(BanListType.PROFILE).pardon(Bukkit.createProfile(id));
        }
        setHearts(id, name, 1);
        return wasBanned;
    }

    /** Eliminated players are banned and kicked; participants stuck in spectator go back to survival. */
    public void applyMode(Player p) {
        if (isRunning() && isEliminated(p.getUniqueId())) {
            banForElimination(p);
        } else if (p.getGameMode() == GameMode.SPECTATOR && isParticipant(p.getUniqueId())) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void start() {
        hearts.clear();
        names.clear();
        state = State.RUNNING;
        for (Player p : Bukkit.getOnlinePlayers()) {
            hearts.put(p.getUniqueId(), startHearts());
            names.put(p.getUniqueId(), p.getName());
            if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        }
        protectionUntil = System.currentTimeMillis() + protectionSeconds() * 1000L;
        plugin.combat().clearAll();
        clearEventBans();
        int mins = (protectionSeconds() + 59) / 60;
        save();
        Bukkit.broadcast(Component.text("The Heroes event has started! Everyone has " + startHearts()
                + " Special Hearts. Last one standing wins. A " + mins
                + "-minute PvP protection phase is active — no PvP damage until it ends!", NamedTextColor.GOLD));
    }

    public void stop() {
        state = State.IDLE;
        protectionUntil = 0L;
        plugin.combat().clearAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        }
        save();
        Bukkit.broadcast(Component.text("The Heroes event has been stopped.", NamedTextColor.YELLOW));
    }

    public void reset() {
        hearts.clear();
        names.clear();
        state = State.IDLE;
        protectionUntil = 0L;
        plugin.combat().clearAll();
        clearEventBans();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        }
        save();
    }

    /** Ends the event if only one participant still has hearts. */
    public void checkWinner() {
        if (!isRunning()) return;
        UUID last = null;
        int alive = 0;
        for (Map.Entry<UUID, Integer> e : hearts.entrySet()) {
            if (e.getValue() > 0) {
                alive++;
                last = e.getKey();
            }
        }
        if (alive == 1 && hearts.size() > 1) {
            state = State.ENDED;
            protectionUntil = 0L;
            plugin.combat().clearAll();
            save();
            Bukkit.broadcast(Component.text("★ " + nameOf(last) + " is the last one standing and wins the Heroes event! ★",
                    NamedTextColor.GOLD));
        }
    }

    public void load() {
        hearts.clear();
        names.clear();
        eventBanned.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try {
            state = State.valueOf(y.getString("state", "IDLE"));
        } catch (IllegalArgumentException ex) {
            state = State.IDLE;
        }
        protectionUntil = y.getLong("protection-until", 0L);
        for (String s : y.getStringList("event-banned")) {
            try {
                eventBanned.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (y.isConfigurationSection("players")) {
            for (String key : y.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    hearts.put(id, y.getInt("players." + key + ".hearts", startHearts()));
                    String n = y.getString("players." + key + ".name");
                    if (n != null) names.put(id, n);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("state", state.name());
        y.set("protection-until", protectionUntil);
        List<String> banned = new ArrayList<>();
        for (UUID id : eventBanned) banned.add(id.toString());
        y.set("event-banned", banned);
        for (Map.Entry<UUID, Integer> e : hearts.entrySet()) {
            String k = "players." + e.getKey();
            y.set(k + ".hearts", e.getValue());
            y.set(k + ".name", names.get(e.getKey()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }
}
