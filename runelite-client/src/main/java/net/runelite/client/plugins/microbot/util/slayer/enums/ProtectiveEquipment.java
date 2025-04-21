package net.runelite.client.plugins.microbot.util.slayer.enums;

public enum ProtectiveEquipment {
    SPINY_HELMET("Spiny helmet", new String[] { "Wall beasts" }),
    REINFORCED_GOGGLES("Reinforced goggles", new String[] { "Sourhogs" }),
    SHAYZIEN_ARMOUR("Shayzien armour", new String[] { "Lizardmen" }),
    FACEMASK("Facemask", new String[] { "Dust devils" }),
    EARMUFFS("Earmuffs", new String[] { "Banshees" }),
    MIRROR_SHIELD("Mirror shield", new String[] { "Basilisks", "Cockatrice" }),
    WITCHWOOD_ICON("Witchwood icon", new String[] { "Cave horrors" }),
    INSULATED_BOOTS("Insulated boots", new String[] { "Killerwatts", "Rune Dragons" }),
    SLAYER_GLOVES("Slayer gloves", new String[] { "Fever spiders" }),
    NOSE_PEG("Nose peg", new String[] { "Aberrant spectres" }),
    // The Slayer helmet is a composite item, providing the protection of its components:
    // Spiny helmet (Wall beast), Facemask (Dust devil), Earmuffs (Banshee),
    // Nose peg (Aberrant spectre), and Reinforced goggles (Sourhogs).
    SLAYER_HELMET("Slayer helmet", new String[] { "Wall beasts", "Dust devils", "Banshees", "Aberrant spectres", "Sourhogs" });

    private final String itemName;
    private final String[] protectsAgainst;

    ProtectiveEquipment(String itemName, String[] protectsAgainst) {
        this.itemName = itemName;
        this.protectsAgainst = protectsAgainst;
    }

    /**
     * Returns the display name of the equipment.
     *
     * @return the equipment name.
     */
    public String getItemName() {
        return itemName;
    }

    /**
     * Returns an array of creature names that this equipment protects against.
     *
     * @return an array of creature names.
     */
    public String[] getProtectsAgainst() {
        return protectsAgainst;
    }

    /**
     * Returns the display name of the equipment that protects against the specified creature.
     *
     * @param creatureName the name of the creature.
     * @return the equipment name.
     */
    public static String getItemNameByCreature(String creatureName) {
        for (ProtectiveEquipment equipment : values()) {
            for (String creature : equipment.protectsAgainst) {
                if (creature.equalsIgnoreCase(creatureName)) {
                    return equipment.itemName;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return itemName + " (protects against: " + String.join(", ", protectsAgainst) + ")";
    }
}
