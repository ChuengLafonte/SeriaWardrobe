package id.seria.wardrobe.model;

import java.util.UUID;

/**
 * Holds all wardrobe data for a single player.
 * Supports up to {@code maxSets} armor sets.
 */
public class WardrobeData {

    private final UUID playerUUID;
    private final ArmorSet[] sets;

    /**
     * Index of the currently active (worn) armor set, or -1 if none is active.
     * When a set is active, its pieces are on the player's body, not in the wardrobe.
     */
    private int activeSetIndex = -1;

    public WardrobeData(UUID playerUUID, int maxSets) {
        this.playerUUID = playerUUID;
        this.sets = new ArmorSet[maxSets];
        for (int i = 0; i < maxSets; i++) {
            this.sets[i] = new ArmorSet();
        }
    }

    public UUID getPlayerUUID() { return playerUUID; }

    public ArmorSet getSet(int index) { return sets[index]; }

    public void setSet(int index, ArmorSet set) { sets[index] = set; }

    public int getMaxSets() { return sets.length; }

    public int getActiveSetIndex() { return activeSetIndex; }

    public void setActiveSetIndex(int index) { this.activeSetIndex = index; }

    public boolean hasActiveSet() { return activeSetIndex >= 0 && activeSetIndex < sets.length; }
}
