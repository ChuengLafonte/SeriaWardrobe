package id.seria.wardrobe.data;

import id.seria.wardrobe.SeriaWardrobePlugin;
import id.seria.wardrobe.model.ArmorSet;
import id.seria.wardrobe.model.WardrobeData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages loading and saving of all players' wardrobe data.
 * Each player's data is stored in:
 *   plugins/SeriaWardrobe/data/<UUID>.yml
 */
public class WardrobeManager {

    private final SeriaWardrobePlugin plugin;
    private final File dataFolder;
    private final Map<UUID, WardrobeData> cache = new HashMap<>();

    private static final String[] PIECE_KEYS = {"helmet", "chestplate", "leggings", "boots"};

    public WardrobeManager(SeriaWardrobePlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Returns (and caches) wardrobe data for a player, loading from disk if necessary. */
    public WardrobeData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    /** Saves a single player's data to disk immediately. */
    public void save(UUID uuid) {
        WardrobeData data = cache.get(uuid);
        if (data != null) saveToDisk(data);
    }

    /** Saves all cached data. */
    public void saveAll() {
        cache.values().forEach(this::saveToDisk);
    }

    /** Loads all existing player files into the cache. */
    public void loadAll() {
        File[] files = dataFolder.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            String name = f.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(name);
                cache.put(uuid, loadFromDisk(uuid));
            } catch (IllegalArgumentException ignored) { /* bad filename */ }
        }
        plugin.getLogger().info("Loaded wardrobe data for " + cache.size() + " player(s).");
    }

    // ─── Disk I/O ─────────────────────────────────────────────────────────────

    private WardrobeData loadFromDisk(UUID uuid) {
        int maxSets = plugin.getConfig().getInt("max-sets", 9);
        WardrobeData data = new WardrobeData(uuid, maxSets);

        File file = getFile(uuid);
        if (!file.exists()) return data;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Load active set index (-1 = none)
        data.setActiveSetIndex(cfg.getInt("active-set", -1));

        for (int setIdx = 0; setIdx < maxSets; setIdx++) {
            String setPath = "sets.set" + (setIdx + 1);
            if (!cfg.contains(setPath)) continue;
            ArmorSet set = new ArmorSet();
            set.setDisplayName(cfg.getString(setPath + ".name", ""));
            for (int p = 0; p < 4; p++) {
                ItemStack item = cfg.getItemStack(setPath + "." + PIECE_KEYS[p]);
                set.setPiece(p, item);
            }
            data.setSet(setIdx, set);
        }
        return data;
    }

    private void saveToDisk(WardrobeData data) {
        File file = getFile(data.getPlayerUUID());
        YamlConfiguration cfg = new YamlConfiguration();

        // Persist active set index
        cfg.set("active-set", data.getActiveSetIndex());

        for (int setIdx = 0; setIdx < data.getMaxSets(); setIdx++) {
            ArmorSet set = data.getSet(setIdx);
            // Always save the set path even if "empty" so we don't lose
            // the record that pieces are on the player's body (active set)
            String setPath = "sets.set" + (setIdx + 1);
            if (set == null) continue;
            cfg.set(setPath + ".name", set.getDisplayName());
            for (int p = 0; p < 4; p++) {
                cfg.set(setPath + "." + PIECE_KEYS[p], set.getPiece(p));
            }
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save wardrobe data for " + data.getPlayerUUID() + ": " + e.getMessage());
        }
    }

    private File getFile(UUID uuid) {
        return new File(dataFolder, uuid + ".yml");
    }
}
