package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

/**
 * Preset filters for NPCs in the Game Information overlay system.
 * Provides common filtering options that can be selected from config.
 */
@Getter
@RequiredArgsConstructor
public enum NpcFilterPreset {
    ALL("All NPCs", "Show all NPCs"),
    ATTACKABLE("Attackable", "Show only attackable NPCs"),
    NON_ATTACKABLE("Non-Attackable", "Show only non-attackable NPCs"),
    COMBAT_LEVEL_HIGH("High Combat (100+)", "Show NPCs with combat level 100 or higher"),
    COMBAT_LEVEL_MID("Mid Combat (50-99)", "Show NPCs with combat level 50-99"),
    COMBAT_LEVEL_LOW("Low Combat (1-49)", "Show NPCs with combat level 1-49"),
    INTERACTABLE("Interactable", "Show only interactable NPCs (shops, banks, etc.)"),
    AGRESSIVE("Aggressive", "Show only aggressive NPCs"),
    BOSSES("Bosses", "Show only boss NPCs"),
    SLAYER_MONSTERS("Slayer Monsters", "Show only slayer task monsters"),
    RECENTLY_SPAWNED("Recently Spawned", "Show NPCs spawned in last 10 ticks"),
    NAMED_ONLY("Named Only", "Show only NPCs with custom names"),
    WITHIN_5_TILES("Within 5 Tiles", "Show NPCs within 5 tiles"),
    WITHIN_10_TILES("Within 10 Tiles", "Show NPCs within 10 tiles"),
    CUSTOM("Custom", "Use custom filter criteria");

    private final String displayName;
    private final String description;

    @Override
    public String toString() {
        return displayName;
    }
    
    /**
     * Test if an NPC matches this filter preset.
     * 
     * @param npc The NPC to test
     * @return true if the NPC matches the filter criteria
     */
    public boolean test(Rs2NpcModel npc) {
        if (npc == null) {
            return false;
        }
        
        switch (this) {
            case ALL:
                return true;
                
            case ATTACKABLE:
                return npc.getCombatLevel() > 0;
                
            case NON_ATTACKABLE:
                return npc.getCombatLevel() <= 0;
                
            case COMBAT_LEVEL_HIGH:
                return npc.getCombatLevel() >= 100;
                
            case COMBAT_LEVEL_MID:
                return npc.getCombatLevel() >= 50 && npc.getCombatLevel() <= 99;
                
            case COMBAT_LEVEL_LOW:
                return npc.getCombatLevel() >= 1 && npc.getCombatLevel() <= 49;
                
            case INTERACTABLE:
                // Basic check for common interactable NPCs
                String name = npc.getName();
                if (name == null) return false;
                return name.toLowerCase().contains("banker") || 
                       name.toLowerCase().contains("shop") ||
                       name.toLowerCase().contains("clerk") ||
                       name.toLowerCase().contains("trader");
                
            case AGRESSIVE:
                // This would require more complex logic to determine aggression
                return npc.getCombatLevel() > 0;
                
            case BOSSES:
                // Basic boss detection - could be enhanced with specific boss IDs
                return npc.getCombatLevel() >= 200;
                
            case SLAYER_MONSTERS:
                // This would require slayer task checking - placeholder for now
                return npc.getCombatLevel() > 0;
                
            case RECENTLY_SPAWNED:
                // For now, just return true - proper implementation would need cache timing
                return true;
                
            case NAMED_ONLY:
                String npcName = npc.getName();
                return npcName != null && !npcName.trim().isEmpty();
                
            case WITHIN_5_TILES:
                return npc.getDistanceFromPlayer() <= 5;
                
            case WITHIN_10_TILES:
                return npc.getDistanceFromPlayer() <= 10;
                
            case CUSTOM:
                // Custom filtering should be handled by the plugin logic
                return true;
                
            default:
                return true;
        }
    }
}
