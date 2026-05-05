package id.seria.wardrobe.listener;

import id.seria.wardrobe.SeriaWardrobePlugin;
import id.seria.wardrobe.gui.WardrobeGUI;
import id.seria.wardrobe.model.ArmorSet;
import id.seria.wardrobe.model.WardrobeData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Handles all inventory interactions inside the WardrobeGUI.
 * Responsibilities:
 *  1. Block invalid placements (wrong armor type in wrong row, separator, buttons).
 *  2. Update model + GUI when a piece is placed or removed.
 *  3. Handle the "Use" button: swap wardrobe set ↔ player's equipped armor.
 *  4. Persist data on close.
 */
public class WardrobeListener implements Listener {

    private final SeriaWardrobePlugin plugin;

    public WardrobeListener(SeriaWardrobePlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Click Handler ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof WardrobeGUI gui)) return;

        Inventory clickedInv = event.getClickedInventory();
        int rawSlot = event.getRawSlot();
        ClickType click = event.getClick();

        // ── Clicked inside the wardrobe GUI ──────────────────────────────────
        if (clickedInv != null && clickedInv.getHolder() instanceof WardrobeGUI) {

            // Separator row — always block
            if (WardrobeGUI.isSeparator(rawSlot)) {
                event.setCancelled(true);
                return;
            }

            // Use button
            if (WardrobeGUI.isUseButton(rawSlot)) {
                event.setCancelled(true);
                int col = WardrobeGUI.getColumnForSlot(rawSlot);
                handleUseButton(player, gui, col);
                return;
            }

            int armorRow = WardrobeGUI.getArmorRowForSlot(rawSlot);
            if (armorRow < 0) {
                event.setCancelled(true);
                return;
            }

            // ── Armor slot interaction ─────────────────────────────────────────
            int col = WardrobeGUI.getColumnForSlot(rawSlot);

            // Shift-click from player inventory → wardrobe not allowed here;
            // handled via shift-click into the wardrobe's own slots below.
            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                // Shifting FROM wardrobe TO player inventory — always allow removal
                ItemStack current = clickedInv.getItem(rawSlot);
                if (current != null && current.getType() != Material.AIR
                        && !isFillerItem(current)) {
                    // Allow, and update model afterward via scheduleUpdate
                    scheduleUpdate(player, gui, col);
                    return; // let Bukkit handle the shift
                } else {
                    event.setCancelled(true);
                    return;
                }
            }

            // Placing a cursor item into an armor slot
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (!WardrobeGUI.isValidArmorForRow(cursor, armorRow)) {
                    event.setCancelled(true);
                    sendMsg(player, plugin.getConfig().getString("messages.invalid-slot",
                            "§cYou can only place armor in its correct slot."));
                    return;
                }
            }

            // Picking up from a slot — always allowed; update model after
            scheduleUpdate(player, gui, col);

        } else {
            // ── Clicked in player inventory while wardrobe is open ────────────

            // Shift-click from player inventory into wardrobe
            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                ItemStack item = event.getCurrentItem();
                if (item == null || item.getType() == Material.AIR) {
                    event.setCancelled(true);
                    return;
                }
                // Try to auto-place into the correct row
                event.setCancelled(true);
                autoPlaceArmorIntoWardrobe(player, gui, item, event);
            }
        }
    }

    // ─── Drag Handler ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof WardrobeGUI gui)) return;

        // Check if any dragged slot falls inside the wardrobe
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= WardrobeGUI.SIZE) continue; // player inventory side

            // Block drags into separator or button rows
            if (WardrobeGUI.isSeparator(rawSlot) || WardrobeGUI.isUseButton(rawSlot)) {
                event.setCancelled(true);
                return;
            }

            int armorRow = WardrobeGUI.getArmorRowForSlot(rawSlot);
            if (armorRow < 0) {
                event.setCancelled(true);
                return;
            }

            // Validate armor type
            ItemStack dragged = event.getOldCursor();
            if (!WardrobeGUI.isValidArmorForRow(dragged, armorRow)) {
                event.setCancelled(true);
                sendMsg(player, plugin.getConfig().getString("messages.invalid-slot",
                        "§cYou can only place armor in its correct slot."));
                return;
            }
        }

        // If we get here the drag is valid — update model after
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < WardrobeGUI.SIZE && WardrobeGUI.getArmorRowForSlot(rawSlot) >= 0) {
                    int col = WardrobeGUI.getColumnForSlot(rawSlot);
                    syncSlotToModel(gui, rawSlot, col);
                    gui.refreshColumn(col);
                }
            }
            plugin.getWardrobeManager().save(player.getUniqueId());
        });
    }

    // ─── Close Handler ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof WardrobeGUI gui)) return;

        // Sync all slots to model and save
        syncAllSlotsToModel(gui);
        plugin.getWardrobeManager().save(player.getUniqueId());
    }

    // ─── Use Button Logic ─────────────────────────────────────────────────────

    /**
     * Swap the player's currently equipped armor with the selected wardrobe set.
     *
     *  Steps:
     *   1. Read wardrobe set.
     *   2. If set is empty → send error, abort.
     *   3. Read player's current armor (helmet, chest, legs, boots).
     *   4. Set player's armor to wardrobe pieces.
     *   5. Store old player armor back into wardrobe set.
     *   6. Refresh GUI column.
     *   7. Save.
     */
    private void handleUseButton(Player player, WardrobeGUI gui, int col) {
        WardrobeData data = gui.getData();
        ArmorSet set = data.getSet(col);

        if (set.isEmpty()) {
            sendMsg(player, plugin.getConfig().getString("messages.set-empty",
                    "§cThat armor set is empty!"));
            return;
        }

        PlayerInventory pInv = player.getInventory();

        // Snapshot current player armor
        ItemStack oldHelmet      = pInv.getHelmet();
        ItemStack oldChestplate  = pInv.getChestplate();
        ItemStack oldLeggings    = pInv.getLeggings();
        ItemStack oldBoots       = pInv.getBoots();

        // Equip wardrobe set onto player
        pInv.setHelmet(     cloneOrNull(set.getHelmet())     );
        pInv.setChestplate( cloneOrNull(set.getChestplate()) );
        pInv.setLeggings(   cloneOrNull(set.getLeggings())   );
        pInv.setBoots(      cloneOrNull(set.getBoots())      );

        // Store old armor back into wardrobe set
        set.setPiece(0, cloneOrNull(oldHelmet));
        set.setPiece(1, cloneOrNull(oldChestplate));
        set.setPiece(2, cloneOrNull(oldLeggings));
        set.setPiece(3, cloneOrNull(oldBoots));

        // Refresh GUI to reflect new wardrobe contents
        gui.refreshColumn(col);

        sendMsg(player, plugin.getConfig().getString("messages.set-equipped",
                "§aArmor set §e{page} §aequipped!").replace("{page}", String.valueOf(col + 1)));

        plugin.getWardrobeManager().save(player.getUniqueId());
    }

    // ─── Auto-Place (Shift-click from player inventory) ───────────────────────

    /**
     * Tries to auto-place the shift-clicked item into the correct armor row
     * in the first column that has an empty slot for that piece type.
     */
    private void autoPlaceArmorIntoWardrobe(Player player, WardrobeGUI gui,
                                             ItemStack item, InventoryClickEvent event) {
        // Determine which armor row this piece belongs to
        int targetRow = -1;
        String typeName = item.getType().name();
        if (typeName.endsWith("_HELMET") || typeName.endsWith("_SKULL") || typeName.equals("CARVED_PUMPKIN"))
            targetRow = WardrobeGUI.ROW_HELMET;
        else if (typeName.endsWith("_CHESTPLATE") || typeName.equals("ELYTRA"))
            targetRow = WardrobeGUI.ROW_CHESTPLATE;
        else if (typeName.endsWith("_LEGGINGS"))
            targetRow = WardrobeGUI.ROW_LEGGINGS;
        else if (typeName.endsWith("_BOOTS"))
            targetRow = WardrobeGUI.ROW_BOOTS;

        if (targetRow < 0) return; // not an armor piece

        WardrobeData data = gui.getData();
        int maxSets = data.getMaxSets();

        // Find the first column with an empty armor piece for that row
        for (int col = 0; col < maxSets; col++) {
            ArmorSet set = data.getSet(col);
            ItemStack existing = set.getPiece(targetRow);
            if (existing == null || existing.getType() == Material.AIR) {
                // Place it
                set.setPiece(targetRow, item.clone());
                event.setCurrentItem(null); // remove from player inventory
                gui.refreshColumn(col);
                plugin.getWardrobeManager().save(player.getUniqueId());
                return;
            }
        }
        // All columns full for this piece type — do nothing
    }

    // ─── Model Sync Helpers ───────────────────────────────────────────────────

    /**
     * Reads a single inventory slot back to the model, then refreshes the GUI column.
     * Scheduled one tick later so Bukkit has updated the inventory first.
     */
    private void scheduleUpdate(Player player, WardrobeGUI gui, int col) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Sync all 4 rows for this column
            for (int row = 0; row < 4; row++) {
                int slot = row * 9 + col;
                syncSlotToModel(gui, slot, col);
            }
            gui.refreshColumn(col);
            plugin.getWardrobeManager().save(player.getUniqueId());
        });
    }

    private void syncSlotToModel(WardrobeGUI gui, int slot, int col) {
        int armorRow = WardrobeGUI.getArmorRowForSlot(slot);
        if (armorRow < 0) return;

        ItemStack current = gui.getInventory().getItem(slot);
        // If it's a filler item or null, treat as empty
        if (current == null || current.getType() == Material.AIR || isFillerItem(current)) {
            gui.getData().getSet(col).setPiece(armorRow, null);
        } else {
            gui.getData().getSet(col).setPiece(armorRow, current.clone());
        }
    }

    private void syncAllSlotsToModel(WardrobeGUI gui) {
        int maxSets = gui.getData().getMaxSets();
        for (int col = 0; col < maxSets; col++) {
            for (int row = 0; row < 4; row++) {
                syncSlotToModel(gui, row * 9 + col, col);
            }
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /** Returns true if the item is a decorative filler/placeholder (glass pane). */
    private boolean isFillerItem(ItemStack item) {
        if (item == null) return true;
        String name = item.getType().name();
        return name.endsWith("GLASS_PANE") || name.endsWith("DYE");
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return (item == null || item.getType() == Material.AIR) ? null : item.clone();
    }

    private void sendMsg(Player player, String msg) {
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(msg);
        }
    }
}
