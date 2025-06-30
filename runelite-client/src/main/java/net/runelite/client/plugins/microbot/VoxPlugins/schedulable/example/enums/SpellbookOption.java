package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example.enums;

import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import lombok.Getter;

/**
 * Unified spellbook enum that includes a "NONE" option for configurations
 * where no spellbook switching is desired.
 */
@Getter
public enum SpellbookOption {
    
    NONE("None (No Switching)", null),
    MODERN("Standard/Modern Spellbook", Rs2Spellbook.MODERN),
    ANCIENT("Ancient Magicks", Rs2Spellbook.ANCIENT),
    LUNAR("Lunar Spellbook", Rs2Spellbook.LUNAR),
    ARCEUUS("Arceuus Spellbook", Rs2Spellbook.ARCEUUS);
    
    private final String displayName;
    private final Rs2Spellbook spellbook;
    
    SpellbookOption(String displayName, Rs2Spellbook spellbook) {
        this.displayName = displayName;
        this.spellbook = spellbook;
    }
    
    /**
     * Gets the Rs2Spellbook enum value, or null if this is the NONE option
     * 
     * @return Rs2Spellbook enum value or null
     */
    public Rs2Spellbook getSpellbook() {
        return spellbook;
    }
    
    /**
     * Checks if this option represents no spellbook switching
     * 
     * @return true if this is the NONE option
     */
    public boolean isNone() {
        return this == NONE;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
