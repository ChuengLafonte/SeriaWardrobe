package id.seria.wardrobe;

import id.seria.wardrobe.command.WardrobeCommand;
import id.seria.wardrobe.data.WardrobeManager;
import id.seria.wardrobe.hook.MMOItemsHook;
import id.seria.wardrobe.listener.WardrobeListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SeriaWardrobe — Beta
 * Armor wardrobe plugin: save and switch full armor sets via a GUI.
 */
public final class SeriaWardrobePlugin extends JavaPlugin {

    private static SeriaWardrobePlugin instance;
    private WardrobeManager wardrobeManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Hook into optional plugins
        MMOItemsHook.setup();

        // Initialize data manager
        wardrobeManager = new WardrobeManager(this);
        wardrobeManager.loadAll();

        // Register listener
        getServer().getPluginManager().registerEvents(new WardrobeListener(this), this);

        // Register command
        WardrobeCommand cmd = new WardrobeCommand(this);
        getCommand("wardrobe").setExecutor(cmd);
        getCommand("wardrobe").setTabCompleter(cmd);

        getLogger().info("SeriaWardrobe v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (wardrobeManager != null) {
            wardrobeManager.saveAll();
        }
        getLogger().info("SeriaWardrobe disabled. All data saved.");
    }

    public static SeriaWardrobePlugin getInstance() {
        return instance;
    }

    public WardrobeManager getWardrobeManager() {
        return wardrobeManager;
    }
}
