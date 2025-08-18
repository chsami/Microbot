package net.runelite.client.plugins.microbot.bga.autoherblore.enums;

public enum UnfinishedPotionMode {
    ANY_AND_ALL("Any available", "Any available", "Any available"),
    GUAM_POTION_UNF("Guam leaf", "Vial of water", "Guam potion (unf)"),
    MARRENTILL_POTION_UNF("Marrentill", "Vial of water", "Marrentill potion (unf)"),
    TARROMIN_POTION_UNF("Tarromin", "Vial of water", "Tarromin potion (unf)"),
    HARRALANDER_POTION_UNF("Harralander", "Vial of water", "Harralander potion (unf)"),
    RANARR_POTION_UNF("Ranarr weed", "Vial of water", "Ranarr potion (unf)"),
    TOADFLAX_POTION_UNF("Toadflax", "Vial of water", "Toadflax potion (unf)"),
    IRIT_POTION_UNF("Irit leaf", "Vial of water", "Irit potion (unf)"),
    AVANTOE_POTION_UNF("Avantoe", "Vial of water", "Avantoe potion (unf)"),
    KWUARM_POTION_UNF("Kwuarm", "Vial of water", "Kwuarm potion (unf)"),
    SNAPDRAGON_POTION_UNF("Snapdragon", "Vial of water", "Snapdragon potion (unf)"),
    CADANTINE_POTION_UNF("Cadantine", "Vial of water", "Cadantine potion (unf)"),
    LANTADYME_POTION_UNF("Lantadyme", "Vial of water", "Lantadyme potion (unf)"),
    DWARF_WEED_POTION_UNF("Dwarf weed", "Vial of water", "Dwarf weed potion (unf)"),
    TORSTOL_POTION_UNF("Torstol", "Vial of water", "Torstol potion (unf)");

    private final String herbName;
    private final String vialName;
    private final String unfinishedPotionName;

    UnfinishedPotionMode(String herbName, String vialName, String unfinishedPotionName) {
        this.herbName = herbName;
        this.vialName = vialName;
        this.unfinishedPotionName = unfinishedPotionName;
    }

    public String getHerbName() {
        return herbName;
    }

    public String getVialName() {
        return vialName;
    }

    public String getUnfinishedPotionName() {
        return unfinishedPotionName;
    }

    @Override
    public String toString() {
        return unfinishedPotionName;
    }
}