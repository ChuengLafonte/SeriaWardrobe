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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Handles all inventory interactions inside the WardrobeGUI.
 *
 * Fix #1 — Filler items (glass panes, dyes used as buttons) are blocked
 *           from being picked up or moved by the player.
 *
 * Fix #2 — "Use" button now implements bind-slot logic:
 *   • Click Use on an empty slot    → nothing (message).
 *   • Click Use on a slot (no active)  → equip that set, mark it orange_dye,
 *                                        armor row shows orange glass placeholders.
 *   • Click Use on the SAME slot (already active) → unequip: return armor from
 *                                        player back into that slot, clear active.
 *   • Click Use on a DIFFERENT slot (another is active) → first return armor
 *                                        from player to the previously active slot,
 *                                        then equip the new slot and mark it orange.
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

        // ── Click is inside the wardrobe GUI ─────────────────────────────────
        if (clickedInv != null && clickedInv.getHolder() instanceof WardrobeGUI) {

            // ① Separator row — always block
            if (WardrobeGUI.isSeparator(rawSlot)) {
                event.setCancelled(true);
                return;
            }

            // ② Use / Active button row
            if (WardrobeGUI.isUseButton(rawSlot)) {
                event.setCancelled(true);
                int col = WardrobeGUI.getColumnForSlot(rawSlot);
                handleUseButton(player, gui, col);
                return;
            }

            // ③ Armor rows — validate piece type and block filler pickup
            int armorRow = WardrobeGUI.getArmorRowForSlot(rawSlot);
            if (armorRow < 0) {
                // Slot outside expected rows (shouldn't happen, but be safe)
                event.setCancelled(true);
                return;
            }

            int col = WardrobeGUI.getColumnForSlot(rawSlot);

            // Block any interaction with the active set's armor rows (orange glass)
            if (gui.getData().getActiveSetIndex() == col) {
                event.setCancelled(true);
                return;
            }

            ItemStack currentItem = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;

            // ── Placing armor over a filler slot ─────────────────────────────
            // Bukkit's default behavior when the cursor has an item and the slot
            // has a different item is to SWAP them, which would put the glass pane
            // onto the player's cursor (and eventually into their inventory).
            // We intercept and handle placement manually to discard the filler.
            if (!cursorEmpty && isFillerItem(currentItem)) {
                event.setCancelled(true);
                // Validate armor type before accepting
                if (!WardrobeGUI.isValidArmorForRow(cursor, armorRow)) {
                    sendMsg(player, plugin.getConfig().getString("messages.invalid-slot",
                            "§cYou can only place armor in its correct slot."));
                    return;
                }
                // Place armor; filler is simply discarded (it's a GUI decoration)
                gui.getInventory().setItem(rawSlot, cursor.clone());
                player.setItemOnCursor(new ItemStack(Material.AIR));
                scheduleUpdate(player, gui, col);
                return;
            }

            // ── Block pickup of filler items when cursor is empty ─────────────
            if (isFillerItem(currentItem) && cursorEmpty) {
                event.setCancelled(true);
                return;
            }

            // ── Shift-click WITHIN wardrobe → send piece back to player inv ──
            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                if (!isFillerItem(currentItem)
                        && currentItem != null
                        && currentItem.getType() != Material.AIR) {
                    scheduleUpdate(player, gui, col);
                    return;
                }
                event.setCancelled(true);
                return;
            }

            // ── Placing cursor into a slot that already has real armor (swap) ─
            if (!cursorEmpty) {
                if (!WardrobeGUI.isValidArmorForRow(cursor, armorRow)) {
                    event.setCancelled(true);
                    sendMsg(player, plugin.getConfig().getString("messages.invalid-slot",
                            "§cYou can only place armor in its correct slot."));
                    return;
                }
            }

            // Any other valid interaction in armor rows — sync model after
            scheduleUpdate(player, gui, col);

        } else {
            // ── Click is in the player inventory while wardrobe is open ───────

            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                ItemStack item = event.getCurrentItem();
                if (item == null || item.getType() == Material.AIR) {
                    event.setCancelled(true);
                    return;
                }
                // Try to auto-place into the correct armor row
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

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= WardrobeGUI.SIZE) continue; // player side — fine

            // Block drags into separator or button row
            if (WardrobeGUI.isSeparator(rawSlot) || WardrobeGUI.isUseButton(rawSlot)) {
                event.setCancelled(true);
                return;
            }

            int armorRow = WardrobeGUI.getArmorRowForSlot(rawSlot);
            if (armorRow < 0) {
                event.setCancelled(true);
                return;
            }

            int col = WardrobeGUI.getColumnForSlot(rawSlot);

            // Block drags onto active-set column
            if (gui.getData().getActiveSetIndex() == col) {
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

        // Valid drag — sync model after
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

        syncAllSlotsToModel(gui);
        plugin.getWardrobeManager().save(player.getUniqueId());
    }

    // ─── Use Button — Bind-Slot Logic ─────────────────────────────────────────

    /**
     * Implements the bind-slot "Use" mechanic:
     *
     *   activeSlot = data.getActiveSetIndex()   (-1 = none worn)
     *
     *   Case A  col == activeSlot  → UNEQUIP
     *            • Pull armor from player body → store in col's set
     *            • activeSetIndex = -1
     *            • Refresh col
     *
     *   Case B  activeSlot == -1 and col is not empty  → EQUIP
     *            • Pull armor from col's set → put on player body
     *            • col's set pieces set to null (they're on body)
     *            • activeSetIndex = col
     *            • Refresh col
     *
     *   Case C  activeSlot >= 0 and col != activeSlot  → SWITCH
     *            • Return armor from player → store in activeSlot's set
     *            • Refresh activeSlot column
     *            • Then equip col (same as Case B)
     */
    private void handleUseButton(Player player, WardrobeGUI gui, int col) {
        WardrobeData data = gui.getData();
        int activeSlot = data.getActiveSetIndex();

        // ── Case A: toggle off the currently active set ───────────────────
        if (col == activeSlot) {
            unequipActiveSet(player, gui);
            sendMsg(player, plugin.getConfig()
                    .getString("messages.set-stored", "§7Armor set §e{page} §7stored back into wardrobe.")
                    .replace("{page}", String.valueOf(col + 1)));
            plugin.getWardrobeManager().save(player.getUniqueId());
            return;
        }

        // ── Check target set is not empty ─────────────────────────────────
        ArmorSet targetSet = data.getSet(col);
        if (targetSet.isEmpty()) {
            sendMsg(player, plugin.getConfig()
                    .getString("messages.set-empty", "§cThat armor set is empty!"));
            return;
        }

        // ── Case C: another set is active — return it first ───────────────
        if (activeSlot >= 0 && activeSlot < data.getMaxSets()) {
            unequipActiveSet(player, gui);
        }

        // ── Case B (or C continued): equip the target set ─────────────────
        equipSet(player, gui, col);

        sendMsg(player, plugin.getConfig()
                .getString("messages.set-equipped", "§aArmor set §e{page} §aequipped!")
                .replace("{page}", String.valueOf(col + 1)));
        plugin.getWardrobeManager().save(player.getUniqueId());
    }

    /**
     * Equips the armor set at {@code col} onto the player's body.
     *
     * Before equipping, any armor the player is currently wearing that is NOT
     * tracked by a wardrobe slot is returned to their inventory.
     * If the inventory is full the pieces are dropped at the player's feet.
     *
     * Clears the set's piece slots (pieces are now on the body) and marks it active.
     */
    private void equipSet(Player player, WardrobeGUI gui, int col) {
        WardrobeData data = gui.getData();
        ArmorSet set = data.getSet(col);
        PlayerInventory pInv = player.getInventory();

        // ── Salvage any non-wardrobe armor currently on the player ───────────
        // unequipActiveSet() already cleared armor for the previously active set.
        // If activeSetIndex is -1 here, the player was wearing their own armor
        // (not from wardrobe) — we must return those pieces before overwriting.
        if (data.getActiveSetIndex() < 0) {
            salvagePlayerArmor(player);
        }

        // ── Equip wardrobe set ────────────────────────────────────────────────
        pInv.setHelmet(     cloneOrNull(set.getHelmet())     );
        pInv.setChestplate( cloneOrNull(set.getChestplate()) );
        pInv.setLeggings(   cloneOrNull(set.getLeggings())   );
        pInv.setBoots(      cloneOrNull(set.getBoots())      );

        // Clear wardrobe slots — pieces are now on the player's body
        set.setPiece(0, null);
        set.setPiece(1, null);
        set.setPiece(2, null);
        set.setPiece(3, null);

        data.setActiveSetIndex(col);
        gui.refreshColumn(col);
    }

    /**
     * Returns whatever armor is currently on the player's body to their inventory.
     * If any inventory slot is full, the excess item is dropped naturally at their
     * feet so no armor is ever silently deleted.
     */
    private void salvagePlayerArmor(Player player) {
        PlayerInventory pInv = player.getInventory();
        ItemStack[] worn = {
                pInv.getHelmet(),
                pInv.getChestplate(),
                pInv.getLeggings(),
                pInv.getBoots()
        };
        // Clear armor slots first so addItem doesn't fill them again
        pInv.setHelmet(null);
        pInv.setChestplate(null);
        pInv.setLeggings(null);
        pInv.setBoots(null);

        for (ItemStack piece : worn) {
            if (piece == null || piece.getType() == Material.AIR) continue;
            var leftover = pInv.addItem(piece.clone());
            // Drop any items that didn't fit
            leftover.values().forEach(drop ->
                    player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
    }

    /**
     * Returns the player's currently worn armor back into the active wardrobe slot,
     * then clears their armor slots and resets activeSetIndex to -1.
     */
    private void unequipActiveSet(Player player, WardrobeGUI gui) {
        WardrobeData data = gui.getData();
        int activeSlot = data.getActiveSetIndex();
        if (activeSlot < 0 || activeSlot >= data.getMaxSets()) return;

        ArmorSet set = data.getSet(activeSlot);
        PlayerInventory pInv = player.getInventory();

        // Store what the player is wearing back into the wardrobe set
        set.setPiece(0, cloneOrNull(pInv.getHelmet()));
        set.setPiece(1, cloneOrNull(pInv.getChestplate()));
        set.setPiece(2, cloneOrNull(pInv.getLeggings()));
        set.setPiece(3, cloneOrNull(pInv.getBoots()));

        // Unequip player armor
        pInv.setHelmet(null);
        pInv.setChestplate(null);
        pInv.setLeggings(null);
        pInv.setBoots(null);

        data.setActiveSetIndex(-1);
        gui.refreshColumn(activeSlot);
    }

    // ─── Auto-Place (Shift-click from player inventory) ───────────────────────

    private void autoPlaceArmorIntoWardrobe(Player player, WardrobeGUI gui,
                                             ItemStack item, InventoryClickEvent event) {
        int targetRow = getArmorRowForItem(item);
        if (targetRow < 0) return;

        WardrobeData data = gui.getData();
        int maxSets = data.getMaxSets();

        for (int col = 0; col < maxSets; col++) {
            // Skip the active column — can't place into worn set
            if (data.getActiveSetIndex() == col) continue;

            ArmorSet set = data.getSet(col);
            ItemStack existing = set.getPiece(targetRow);
            if (existing == null || existing.getType() == Material.AIR) {
                set.setPiece(targetRow, item.clone());
                event.setCurrentItem(null);
                gui.refreshColumn(col);
                plugin.getWardrobeManager().save(player.getUniqueId());
                return;
            }
        }
    }

    // ─── Model Sync Helpers ───────────────────────────────────────────────────

    private void scheduleUpdate(Player player, WardrobeGUI gui, int col) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (int row = 0; row < 4; row++) {
                syncSlotToModel(gui, row * 9 + col, col);
            }
            gui.refreshColumn(col);
            plugin.getWardrobeManager().save(player.getUniqueId());
        });
    }

    private void syncSlotToModel(WardrobeGUI gui, int slot, int col) {
        int armorRow = WardrobeGUI.getArmorRowForSlot(slot);
        if (armorRow < 0) return;

        // Never sync an active slot from GUI — those are orange glass placeholders
        if (gui.getData().getActiveSetIndex() == col) return;

        ItemStack current = gui.getInventory().getItem(slot);
        if (current == null || current.getType() == Material.AIR || isFillerItem(current)) {
            gui.getData().getSet(col).setPiece(armorRow, null);
        } else {
            gui.getData().getSet(col).setPiece(armorRow, current.clone());
        }
    }

    private void syncAllSlotsToModel(WardrobeGUI gui) {
        int maxSets = gui.getData().getMaxSets();
        for (int col = 0; col < maxSets; col++) {
            // Skip active column — pieces are on the player's body, not in GUI
            if (gui.getData().getActiveSetIndex() == col) continue;
            for (int row = 0; row < 4; row++) {
                syncSlotToModel(gui, row * 9 + col, col);
            }
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /**
     * Determines which armor row (0-3) an item belongs to based on its material.
     * Returns -1 if it is not an armor piece.
     */
    private int getArmorRowForItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return -1;
        String name = item.getType().name();
        if (name.endsWith("_HELMET") || name.endsWith("_SKULL") || name.equals("CARVED_PUMPKIN"))
            return WardrobeGUI.ROW_HELMET;
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA"))
            return WardrobeGUI.ROW_CHESTPLATE;
        if (name.endsWith("_LEGGINGS"))
            return WardrobeGUI.ROW_LEGGINGS;
        if (name.endsWith("_BOOTS"))
            return WardrobeGUI.ROW_BOOTS;
        return -1;
    }

    /**
     * Returns true if the item is a decorative filler that the player
     * should never be able to pick up (glass panes, dyes used as buttons).
     */
    private boolean isFillerItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_GLASS_PANE")
                || name.endsWith("_DYE")
                || name.equals("GRAY_DYE")
                || name.equals("LIME_DYE")
                || name.equals("ORANGE_DYE");
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return (item == null || item.getType() == Material.AIR) ? null : item.clone();
    }

    private void sendMsg(Player player, String msg) {
        if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
    }
}
