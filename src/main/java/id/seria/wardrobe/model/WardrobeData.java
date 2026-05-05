package id.seria.wardrobe.model;

import java.util.UUID;

/**
 * Holds all wardrobe data for a single player.
 * Supports up to {@code maxSets} armor sets.
 */
public class WardrobeData {

    private final UUID playerUUID;
    private final ArmorSet[] sets;

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
}
