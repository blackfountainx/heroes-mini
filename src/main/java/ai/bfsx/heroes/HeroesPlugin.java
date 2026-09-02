package ai.bfsx.heroes;

import ai.bfsx.heroes.combat.CombatListener;
import ai.bfsx.heroes.combat.CombatManager;
import ai.bfsx.heroes.command.HeroesCommand;
import ai.bfsx.heroes.grave.GraveListener;
import ai.bfsx.heroes.grave.GraveManager;
import ai.bfsx.heroes.hearts.DeathListener;
import ai.bfsx.heroes.hearts.HeartsManager;
import ai.bfsx.heroes.hearts.JoinListener;
import ai.bfsx.heroes.ui.ActionBarTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HeroesPlugin extends JavaPlugin {

    public static final int START_HEARTS = 3;
    public static final int COMBAT_SECONDS = 20;

    private HeartsManager hearts;
    private CombatManager combat;
    private GraveManager graves;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        hearts = new HeartsManager(this);
        combat = new CombatManager(this);
        graves = new GraveManager(this);

        hearts.load();
        graves.load();

        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GraveListener(this), this);

        HeroesCommand cmd = new HeroesCommand(this);
        getCommand("heroes").setExecutor(cmd);
        getCommand("heroes").setTabCompleter(cmd);

        new ActionBarTask(this).runTaskTimer(this, 20L, 20L);

        getLogger().info("HeroesMini enabled. Event state: " + hearts.getState());
    }

    @Override
    public void onDisable() {
        if (hearts != null) hearts.save();
        if (graves != null) graves.save();
        getLogger().info("HeroesMini disabled, state saved.");
    }

    public HeartsManager hearts() { return hearts; }
    public CombatManager combat() { return combat; }
    public GraveManager graves() { return graves; }
}
