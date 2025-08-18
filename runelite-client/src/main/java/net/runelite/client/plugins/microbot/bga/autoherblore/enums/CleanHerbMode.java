package net.runelite.client.plugins.microbot.bga.autoherblore.enums;

public enum CleanHerbMode {
    ANY_AND_ALL("Any available", "Any available"),
    GUAM("Grimy guam leaf", "Guam leaf"),
    MARRENTILL("Grimy marrentill", "Marrentill"),
    TARROMIN("Grimy tarromin", "Tarromin"),
    HARRALANDER("Grimy harralander", "Harralander"),
    RANARR("Grimy ranarr weed", "Ranarr weed"),
    TOADFLAX("Grimy toadflax", "Toadflax"),
    IRIT("Grimy irit leaf", "Irit leaf"),
    AVANTOE("Grimy avantoe", "Avantoe"),
    KWUARM("Grimy kwuarm", "Kwuarm"),
    SNAPDRAGON("Grimy snapdragon", "Snapdragon"),
    CADANTINE("Grimy cadantine", "Cadantine"),
    LANTADYME("Grimy lantadyme", "Lantadyme"),
    DWARF("Grimy dwarf weed", "Dwarf weed"),
    TORSTOL("Grimy torstol", "Torstol");

    private final String grimyName;
    private final String cleanName;

    CleanHerbMode(String grimyName, String cleanName) {
        this.grimyName = grimyName;
        this.cleanName = cleanName;
    }

    public String getGrimyName() {
        return grimyName;
    }

    public String getCleanName() {
        return cleanName;
    }

    @Override
    public String toString() {
        return cleanName;
    }
}