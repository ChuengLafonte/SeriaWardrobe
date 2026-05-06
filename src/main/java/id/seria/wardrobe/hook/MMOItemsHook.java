package id.seria.wardrobe.hook;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Soft hook into MMOItems via MythicLib's NBT API.
 *
 * MMOItems stores the item's TYPE as an NBT tag ("MMOITEMS_ITEM_TYPE").
 * We read that tag to determine whether the item qualifies as a specific
 * armor slot type.  The check is done entirely via MythicLib so we
 * don't need a hard dependency — if MMOItems is absent the hook is a
 * no-op and vanilla material checks take over.
 *
 * Supported MMOItems type IDs (case-insensitive):
 *   HELMET, CHESTPLATE, LEGGINGS, BOOTS
 */
public class MMOItemsHook {

    private static boolean available = false;

    /** Call once during onEnable() after plugins are loaded. */
    public static void setup() {
        available = Bukkit.getPluginManager().getPlugin("MMOItems") != null;
        if (available) {
            Bukkit.getLogger().info("[SeriaWardrobe] MMOItems detected — custom armor type detection enabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Returns the MMOItems type ID string (e.g. "HELMET") for the given item,
     * or {@code null} if the item is not an MMOItem or MMOItems is not loaded.
     */
    public static String getMMOItemType(ItemStack item) {
        if (!available || item == null) return null;
        try {
            NBTItem nbt = NBTItem.get(item);
            if (!nbt.hasType()) return null;
            return nbt.getType(); // e.g. "HELMET", "CHESTPLATE" …
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the item is an MMOItems armor piece that belongs to
     * the given wardrobe row index (0=Helmet, 1=Chestplate, 2=Leggings, 3=Boots).
     */
    public static boolean isValidForRow(ItemStack item, int row) {
        String type = getMMOItemType(item);
        if (type == null) return false;
        return switch (row) {
            case 0 -> type.equalsIgnoreCase("HELMET");
            case 1 -> type.equalsIgnoreCase("CHESTPLATE");
            case 2 -> type.equalsIgnoreCase("LEGGINGS");
            case 3 -> type.equalsIgnoreCase("BOOTS");
            default -> false;
        };
    }
}
