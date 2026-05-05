package id.seria.wardrobe.model;

import org.bukkit.inventory.ItemStack;

/**
 * Represents a single armor set (4 pieces + metadata).
 */
public class ArmorSet {

    /** 0 = helmet, 1 = chestplate, 2 = leggings, 3 = boots */
    private final ItemStack[] pieces;
    private String displayName;

    public ArmorSet() {
        this.pieces = new ItemStack[4];
        this.displayName = "";
    }

    public ArmorSet(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        this.pieces = new ItemStack[]{ helmet, chestplate, leggings, boots };
        this.displayName = "";
    }

    public ItemStack getHelmet()     { return pieces[0]; }
    public ItemStack getChestplate() { return pieces[1]; }
    public ItemStack getLeggings()   { return pieces[2]; }
    public ItemStack getBoots()      { return pieces[3]; }

    public ItemStack getPiece(int slot) { return pieces[slot]; }
    public void setPiece(int slot, ItemStack item) { pieces[slot] = item; }

    public ItemStack[] getPieces() { return pieces; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }

    /** Returns true if every slot is null/air. */
    public boolean isEmpty() {
        for (ItemStack piece : pieces) {
            if (piece != null && piece.getType().isItem()) return false;
        }
        return true;
    }
}
