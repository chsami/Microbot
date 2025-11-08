package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums;

/**
 * combat style enum for PvM combat system
 * represents the three main combat styles in OSRS
 */
public enum CombatStyle {
    /**
     * melee combat (including all melee weapon types)
     */
    MELEE,
    
    /**
     * ranged combat (including thrown weapons and bows)
     */
    RANGED,
    
    /**
     * magic combat (including all spell types)
     */
    MAGIC;
    
    /**
     * get combat style name
     */
    public String getDisplayName() {
        switch (this) {
            case MELEE: return "Melee";
            case RANGED: return "Ranged";
            case MAGIC: return "Magic";
            default: return name();
        }
    }
    
    /**
     * get combat style from string
     */
    public static CombatStyle fromString(String style) {
        if (style == null) {
            return null;
        }
        
        String normalized = style.toUpperCase().trim();
        
        // handle common variations
        if (normalized.contains("MELEE") || normalized.contains("ATTACK") 
                || normalized.contains("STRENGTH") || normalized.contains("DEFENCE")) {
            return MELEE;
        }
        
        if (normalized.contains("RANGE") || normalized.contains("RANGED") 
                || normalized.contains("BOW") || normalized.contains("THROW")) {
            return RANGED;
        }
        
        if (normalized.contains("MAGIC") || normalized.contains("MAGE") 
                || normalized.contains("SPELL") || normalized.contains("CAST")) {
            return MAGIC;
        }
        
        // try exact match
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
