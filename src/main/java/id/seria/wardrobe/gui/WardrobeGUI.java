package id.seria.wardrobe.gui;

import id.seria.wardrobe.SeriaWardrobePlugin;
import id.seria.wardrobe.model.ArmorSet;
import id.seria.wardrobe.model.WardrobeData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════
 *  SeriaWardrobe GUI Layout (54 slots, 6 rows × 9 cols)
 * ══════════════════════════════════════════════════════════════
 *
 *  Slot layout per armor-set column (0-indexed):
 *
 *  Row 0  │ [H0][H1][H2][H3][H4][H5][H6][H7][H8]   ← Helmets     (cols 0-8)
 *  Row 1  │ [C0][C1][C2][C3][C4][C5][C6][C7][C8]   ← Chestplates
 *  Row 2  │ [L0][L1][L2][L3][L4][L5][L6][L7][L8]   ← Leggings
 *  Row 3  │ [B0][B1][B2][B3][B4][B5][B6][B7][B8]   ← Boots
 *  Row 4  │ [G ][G ][G ][G ][G ][G ][G ][G ][G ]   ← Glass pane separator
 *  Row 5  │ [U0][U1][U2][U3][U4][U5][U6][U7][U8]   ← "Use / Unuse" buttons
 *
 *  Column index = armor-set index (0-8).
 *  Piece index  = row index (0-3).
 *
 *  Slot number = row * 9 + col
 *
 *  This class is the InventoryHolder so we can identify the GUI in listeners.
 * ══════════════════════════════════════════════════════════════
 */
public class WardrobeGUI implements InventoryHolder {

    // ── Constants ────────────────────────────────────────────────────────────
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9; // 54

    /** Row indices for each armor piece type */
    public static final int ROW_HELMET      = 0;
    public static final int ROW_CHESTPLATE  = 1;
    public static final int ROW_LEGGINGS    = 2;
    public static final int ROW_BOOTS       = 3;
    public static final int ROW_SEPARATOR   = 4;
    public static final int ROW_BUTTONS     = 5;

    // ── Fields ───────────────────────────────────────────────────────────────
    private final SeriaWardrobePlugin plugin;
    private final Player player;
    private final WardrobeData data;
    private final Inventory inventory;

    // ── Constructor ──────────────────────────────────────────────────────────

    public WardrobeGUI(SeriaWardrobePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.data   = plugin.getWardrobeManager().getOrCreate(player.getUniqueId());

        String title = plugin.getConfig().getString("gui-title", "§8⚔ §bWardrobe §8⚔");
        this.inventory = Bukkit.createInventory(this, SIZE, title);

        build();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /** Populate all slots from scratch. */
    public void build() {
        inventory.clear();
        fillSeparatorRow();

        int maxSets = data.getMaxSets();
        for (int col = 0; col < 9; col++) {
            if (col < maxSets) {
                buildSetColumn(col);
            } else {
                buildLockedColumn(col);
            }
        }
    }

    /** Rebuild only a single set column (after a change). */
    public void refreshColumn(int col) {
        buildSetColumn(col);
        buildUseButton(col);
    }

    // ── Private Builders ──────────────────────────────────────────────────────

    private void buildSetColumn(int col) {
        ArmorSet set = data.getSet(col);
        int setNumber = col + 1;
        boolean isActive = data.getActiveSetIndex() == col;

        // Armor piece slots (rows 0-3)
        for (int row = 0; row < 4; row++) {
            int slot = row * 9 + col;
            ItemStack piece = set.getPiece(row);
            if (isActive) {
                // Pieces are on the player's body — show "In Use" placeholder
                inventory.setItem(slot, makeInUseSlot(row, setNumber));
            } else if (piece != null && piece.getType() != Material.AIR) {
                inventory.setItem(slot, piece.clone());
            } else {
                inventory.setItem(slot, makeEmptyArmorSlot(row, setNumber));
            }
        }

        // Use button (row 5)
        buildUseButton(col);
    }

    private void buildUseButton(int col) {
        int setNumber = col + 1;
        int slot = ROW_BUTTONS * 9 + col;

        if (data.getActiveSetIndex() == col) {
            // This set is currently worn — show orange "in use" indicator
            inventory.setItem(slot, makeActiveButton(setNumber));
        } else {
            ArmorSet set = data.getSet(col);
            inventory.setItem(slot, makeUseButton(setNumber, set));
        }
    }

    private void buildLockedColumn(int col) {
        for (int row = 0; row < 4; row++) {
            inventory.setItem(row * 9 + col, makeLockedSlot());
        }
        inventory.setItem(ROW_BUTTONS * 9 + col, makeLockedSlot());
    }

    private void fillSeparatorRow() {
        ItemStack pane = makeFiller(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int col = 0; col < 9; col++) {
            inventory.setItem(ROW_SEPARATOR * 9 + col, pane);
        }
    }

    // ── Item Factories ────────────────────────────────────────────────────────

    private static final String[] PIECE_LABEL = {"Helmet", "Chestplate", "Leggings", "Boots"};
    private static final Material[] PIECE_EMPTY_MATERIAL = {
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE
    };

    private ItemStack makeEmptyArmorSlot(int pieceRow, int setNumber) {
        ItemStack item = new ItemStack(PIECE_EMPTY_MATERIAL[pieceRow]);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Set " + setNumber + " — §f" + PIECE_LABEL[pieceRow]);
        meta.setLore(List.of("§8Drag an armor piece here."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeUseButton(int setNumber, ArmorSet set) {
        boolean empty = set.isEmpty();
        Material mat = empty ? Material.GRAY_DYE : Material.LIME_DYE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(empty
                ? "§7Set " + setNumber + " §8(empty)"
                : "§aUse Set " + setNumber);
        List<String> lore = new ArrayList<>();
        if (!empty) {
            lore.add("§7Click to equip this armor set.");
            lore.add("§7Previously worn armor will be");
            lore.add("§7returned to its wardrobe slot.");
        } else {
            lore.add("§8Place armor pieces above to create a set.");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Orange dye button shown on the currently active (worn) armor set column. */
    private ItemStack makeActiveButton(int setNumber) {
        ItemStack item = new ItemStack(Material.ORANGE_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§l✦ Set " + setNumber + " §e§l(In Use)");
        meta.setLore(List.of(
                "§7This set is currently equipped.",
                "§7Click again to unequip and",
                "§7store the armor back here."
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeLockedSlot() {
        return makeFiller(Material.RED_STAINED_GLASS_PANE, "§c§lLocked");
    }

    /** Placeholder shown in armor rows when that set's pieces are currently worn. */
    private ItemStack makeInUseSlot(int pieceRow, int setNumber) {
        ItemStack item = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Set " + setNumber + " — §e" + PIECE_LABEL[pieceRow]);
        meta.setLore(List.of("§8Currently equipped on your body."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFiller(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    // ── Slot Utilities (static helpers used by listener) ──────────────────────

    /**
     * Returns the slot's armor-piece row (0-3) if it is an armor slot,
     * or -1 if it's not.
     */
    public static int getArmorRowForSlot(int slot) {
        if (slot < 0 || slot >= SIZE) return -1;
        int row = slot / 9;
        return (row >= ROW_HELMET && row <= ROW_BOOTS) ? row : -1;
    }

    /** Returns the column (set index 0-8) for a given inventory slot. */
    public static int getColumnForSlot(int slot) {
        return slot % 9;
    }

    /** Returns true if the slot is a Use button. */
    public static boolean isUseButton(int slot) {
        return slot / 9 == ROW_BUTTONS;
    }

    /** Returns true if the slot is the separator row. */
    public static boolean isSeparator(int slot) {
        return slot / 9 == ROW_SEPARATOR;
    }

    /**
     * Validates whether an item can be placed in a given armor row.
     * Returns true if the item's type matches the expected armor type.
     */
    public static boolean isValidArmorForRow(ItemStack item, int row) {
        if (item == null || item.getType() == Material.AIR) return false;
        String typeName = item.getType().name();
        return switch (row) {
            case ROW_HELMET     -> typeName.endsWith("_HELMET")     || typeName.endsWith("_SKULL") || typeName.equals("CARVED_PUMPKIN");
            case ROW_CHESTPLATE -> typeName.endsWith("_CHESTPLATE") || typeName.equals("ELYTRA");
            case ROW_LEGGINGS   -> typeName.endsWith("_LEGGINGS");
            case ROW_BOOTS      -> typeName.endsWith("_BOOTS");
            default -> false;
        };
    }

    // ── InventoryHolder ───────────────────────────────────────────────────────

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Player getPlayer() { return player; }
    public WardrobeData getData() { return data; }
}
