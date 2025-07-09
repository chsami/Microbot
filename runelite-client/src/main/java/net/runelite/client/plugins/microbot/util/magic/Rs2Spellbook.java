package net.runelite.client.plugins.microbot.util.magic;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

// TODO: Uncomment when implementing actual spellbook switching functionality
// import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
// import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

/**
 * Enum representing the different spellbooks in RuneScape with switching mechanics.
 * The integer values correspond to the varbit {@link net.runelite.api.gameval.VarbitID#SPELLBOOK}.
 * <p>
 * Enhanced with spellbook switching information including locations, methods,
 * object IDs, NPC IDs, and dialogue requirements.
 */
@Getter
public enum Rs2Spellbook {
    /**
     * Standard/Modern Spellbook - Default spellbook available to all players
     * Can be accessed from any other spellbook altar or via Magic cape
     */
    MODERN(0, 
           null, // No specific location - can be accessed from any altar                      
           null,
           null,
           "Standard spellbook - accessible from any other spellbook altar"),
    
    /**
     * Ancient Magicks Spellbook - Unlocked after Desert Treasure I quest
     * Located in Ancient Pyramid (Level 4 altar room)
     */
    ANCIENT(1,
            new WorldPoint(3233, 9315, 0), // Ancient Pyramid Level 4 altar room
            ObjectID.DT_ZAROS_ALTAR, // Ancient Pyramid altar object ID (ObjectID.ALTAR_6552)            
            Quest.DESERT_TREASURE_I,
            "Ancient Pyramid altar - pray to switch between Ancient Magicks and Standard spellbook"),
    
    /**
     * Lunar Spellbook - Unlocked after Lunar Diplomacy quest
     * Located at Astral Altar on Lunar Isle
     */
    LUNAR(2,
          new WorldPoint(2158, 3866, 0), // Astral Altar on Lunar Isle (south-east)
          ObjectID.ASTRAL_ALTAR, // Astral Altar object ID          
          Quest.LUNAR_DIPLOMACY,
          "Astral Altar on Lunar Isle - pray to switch to Lunar spellbook or back to Standard"),
    
    /**
     * Arceuus Spellbook - No quest requirement, accessible to all players
     * Located at Dark Altar north of Arceuus - requires speaking to Tyss NPC
     */
    ARCEUUS(3,
            new WorldPoint(1694, 3878, 0), // Dark Altar location north of Arceuus            
            NpcID.ARCEUUS_DARKGUARDIAN, // Tyss NPC ID
            null,
            "Dark Altar north of Arceuus - speak to Tyss to access Arceuus spellbook");

    private final int value;
    private final WorldPoint switchLocation;
    private final Integer gameID; // Object ID for altars, null if not applicable, for aceueus spellbook this is the NPC ID        
    private final Quest requiredQuest;
    private final String description;

    /**
     * Constructor for Rs2Spellbook enum
     */
    Rs2Spellbook(int value, WorldPoint switchLocation, Integer gameID, Quest requiredQuest, String description) {
        this.value = value;
        this.switchLocation = switchLocation;
        this.gameID = gameID;        
        this.requiredQuest = requiredQuest;
        this.description = description;
    }


    /**
     * Get the spellbook from its value
     * 
     * @param value The numeric value of the spellbook
     * @return The corresponding Rs2Spellbook enum
     */
    public static Rs2Spellbook fromValue(int value) {
        for (Rs2Spellbook spellbook : Rs2Spellbook.values()) {
            if (spellbook.getValue() == value) {
                return spellbook;
            }
        }
        return MODERN;  // Default to modern spellbook
    }
    
    /**
     * Get the current active spellbook
     * 
     * @return The currently active Rs2Spellbook
     */
    public static Rs2Spellbook getCurrentSpellbook() {
        return fromValue(Microbot.getVarbitValue(VarbitID.SPELLBOOK));
    }
    
    /**
     * Check if the Lunar spellbook is unlocked (requires Lunar Diplomacy quest completion)
     * Note: His Faithful Servants quest was merged into Lunar Diplomacy
     * 
     * @return true if Lunar spellbook is available
     */
    public static boolean isLunarSpellBookUnlocked() {
        return Rs2Player.getQuestState(Quest.LUNAR_DIPLOMACY) == QuestState.FINISHED;               
    }
    
    /**
     * Check if the Ancient Magicks spellbook is unlocked (requires Desert Treasure I quest completion)
     * 
     * @return true if Ancient Magicks spellbook is available
     */
    public static boolean isAncientSpellBookUnlocked() {
        return Rs2Player.getQuestState(Quest.DESERT_TREASURE_I) == QuestState.FINISHED;                
    }
    public static boolean isArceuusSpellBookFullyUnlocked() { //some Arceuus spells require quests
        // Currently, no quests are required for Arceuus spellbook, but this can be expanded in the future
        return Rs2Player.getQuestState(Quest.A_KINGDOM_DIVIDED) == QuestState.FINISHED;                
    }
    
    /**
     * Check if the Arceuus spellbook is unlocked
     * Note: Arceuus spellbook is available to all players by default, no quest requirement -> full unlock required a kingdom divided quest completion
     * 
     * @return true (always available)
     */
    public static boolean isArceuusSpellBookUnlocked() {
        return true; // No quest requirement for Arceuus spellbook               
    }
    
    /**
     * Check if spellbook was changed by Lunar magic (for spellbook swap spell)
     * 
     * @return true if spellbook was changed via lunar magic
     */
    public static boolean isSpellbookChangedByLunar() {
        return Microbot.getVarbitValue(VarbitID.LUNAR_SPELLBOOK_CHANGE) == 1;                
    }
    
    /**
     * Check if this spellbook is currently unlocked/available to the player
     * 
     * @return true if the spellbook is unlocked and can be used
     */
    public boolean isUnlocked() {
        switch (this) {
            case MODERN:
                return true; // Always available
            case ANCIENT:
                return isAncientSpellBookUnlocked();
            case LUNAR:
                return isLunarSpellBookUnlocked();
            case ARCEUUS:
                return isArceuusSpellBookUnlocked();
            default:
                return false;
        }
    }
    
    /**
     * Attempt to switch to this spellbook using the appropriate method
     * Tries multiple methods in order of preference and availability
     * 
     * @return true if spellbook switching was initiated successfully
     */
    public boolean switchTo() {
        if (!isUnlocked()) {
            Microbot.log("Cannot switch to " + this.name() + " spellbook - not unlocked");
            return false;
        }
        
        if (getCurrentSpellbook() == this) {
            Microbot.log("Already on " + this.name() + " spellbook");
            return true;
        }
        
        switch (this) {
            case MODERN:
                // For switching to MODERN, try altar method first (most reliable)
                // We determine which altar to use based on current spellbook
                return switchViaAltar();
                
            case ANCIENT:
            case LUNAR:
                // For ANCIENT and LUNAR, use their specific altars
                if (switchLocation == null) {
                    Microbot.log("No switch location defined for " + this.name() + " spellbook");
                    return false;
                }
                return switchViaAltar();
                
            case ARCEUUS:
                // For ARCEUUS, prefer NPC method as it's the primary way
                if (switchLocation == null) {
                    Microbot.log("No switch location defined for " + this.name() + " spellbook");
                    return false;
                }
                return switchViaNpc();
                
            default:
                Microbot.log("Unknown switch method for " + this.name() + " spellbook");
                return false;
        }
    }
    
    /**
     * Switch spellbook by praying at the appropriate altar
     * Works for LUNAR, ANCIENT, and back to MODERN spellbook
     * 
     * @return true if switching was attempted
     */
    private boolean switchViaAltar() {
        WorldPoint currentPlayerLocation = Rs2Player.getWorldLocation();
        final int maxSearchRadius = 10; // Adjust as needed for altar search radius
        
        // For MODERN spellbook, we need to determine which altar to use based on current spellbook
        Rs2Spellbook targetAltarSpellbook = this;
        if (this == MODERN) {
            Rs2Spellbook currentSpellbook = getCurrentSpellbook();
            switch (currentSpellbook) {
                case ANCIENT:
                    targetAltarSpellbook = ANCIENT;
                    break;
                case LUNAR:
                    targetAltarSpellbook = LUNAR;
                    break;
                case ARCEUUS:
                    // For ARCEUUS to MODERN, we still need to use an altar, but Arceuus uses NPC
                    // Try to find any available altar (Ancient or Lunar)
                    if (!Rs2GameObject.getGameObjects(o -> o.getId() == ANCIENT.getGameID(), maxSearchRadius).isEmpty()) {
                        targetAltarSpellbook = ANCIENT;
                    } else if (!Rs2GameObject.getGameObjects(o -> o.getId() == LUNAR.getGameID(), maxSearchRadius).isEmpty()) {
                        targetAltarSpellbook = LUNAR;
                    } else {
                        Microbot.log("No suitable altar found to switch from Arceuus to Modern spellbook");
                        return false;
                    }
                    break;
                default:
                    Microbot.log("Already on Modern spellbook");
                    return true;
            }
        }
      
        if (targetAltarSpellbook.getGameID() == null || targetAltarSpellbook.getGameID() == -1) {
            Microbot.log("TODO: Find altar object ID for " + targetAltarSpellbook.name() + " spellbook");
            return false;
        }
        
        if (targetAltarSpellbook.getSwitchLocation() == null || currentPlayerLocation == null) {
            Microbot.log("No switch location defined for " + targetAltarSpellbook.name() + " spellbook");
            return false;
        }
        
        // Check if player is at the correct location for the altar
        if (currentPlayerLocation.distanceTo(targetAltarSpellbook.getSwitchLocation()) >= maxSearchRadius) {
            Microbot.log("Player is not at the correct location for " + targetAltarSpellbook.name() + 
                        " spellbook switch. Current location: " + currentPlayerLocation + 
                        ", required: " + targetAltarSpellbook.getSwitchLocation());
            return false; // Player not at the correct location
        }
        
        // Create a final copy of targetAltarSpellbook for use in the lambda
        final Rs2Spellbook finalTargetAltarSpellbook = targetAltarSpellbook;   
        GameObject altarObject = Rs2GameObject.getGameObjects(o -> finalTargetAltarSpellbook.getGameID().equals(o.getId()), maxSearchRadius).stream()
                .findFirst()
                .orElse(null);
                
        if (altarObject == null) {
            Microbot.log("No altar object found for " + targetAltarSpellbook.name() + 
                        " spellbook switch location: " + targetAltarSpellbook.getSwitchLocation() +
                        " current player location: " + currentPlayerLocation);    
            return false;
        }
        
        // Interact with the altar to pray and switch spellbook
        if (Rs2GameObject.interact(altarObject, "Pray")) {
            Rs2Player.waitForAnimation();
            // Wait for spellbook change confirmation
            return sleepUntil(() -> getCurrentSpellbook() == this, 5000);
        }
        
        return false; // Interaction failed
    }
    
    /**
     * Switch spellbook by speaking to an NPC (Arceuus method)
     * 
     * @return true if switching was attempted
     */
    public static boolean switchViaNpc() {
        switch (getCurrentSpellbook()) {
            case ARCEUUS:
                // Switch FROM Arceuus to Modern via Tyss
                if (Rs2Npc.interact("Tyss", "Spellbook")) {
                    if (Rs2Dialogue.sleepUntilHasContinue()) {
                        Rs2Dialogue.clickContinue();
                        // Wait for dialogue options to appear
                        if (Rs2Dialogue.sleepUntilSelectAnOption()) {
                            // Select the option to stop using Arceuus spellbook
                            Rs2Dialogue.clickOption("I'd like to stop using your spellbook now.");
                            return Rs2Dialogue.sleepUntilHasContinue();
                        }
                    }
                }
                break;
                
            case MODERN:
                // Switch FROM Modern to Arceuus via Tyss
                if (Rs2Npc.interact("Tyss", "Spellbook")) {
                    if (Rs2Dialogue.sleepUntilHasContinue()) {
                        Rs2Dialogue.clickContinue();
                        // Wait for dialogue options to appear
                        if (Rs2Dialogue.sleepUntilSelectAnOption()) {
                            // Select the option to try Arceuus magicks
                            Rs2Dialogue.clickOption("Can I try the magicks myself?");
                            return Rs2Dialogue.sleepUntilHasContinue();
                        }
                    }
                }
                break;
                
            default:
                Microbot.log("Tyss NPC switching not supported for current spellbook: " + getCurrentSpellbook());
                return false;
        }
        
        return false;
    }
    
    /**
     * Switch spellbook using Magic cape (99 Magic requirement, 5 times per day)
     * 
     * @return true if switching was attempted
     */
    @SuppressWarnings("unused")
    private boolean switchViaMagicCape() {
        // TODO: Implement Magic cape switching
        // Example implementation:
        // 1. Check if player has 99 Magic skill level
        // 2. Check if Magic cape is equipped or in inventory
        // 3. Check daily usage count (5 times per day limit)
        // 4. Right-click Magic cape and select "Spellbook" option
        // 5. Choose desired spellbook from the interface
        // 6. Wait for spellbook change confirmation
        //
        // Sample code structure:
        // if (Rs2Player.getSkillLevel(Skill.MAGIC) < 99) {
        //     System.out.println("99 Magic required for Magic cape spellbook switching");
        //     return false;
        // }
        // if (!Rs2Equipment.isWearing(ItemID.MAGIC_CAPE) && !Rs2Inventory.hasItem(ItemID.MAGIC_CAPE)) {
        //     System.out.println("Magic cape not found in equipment or inventory");
        //     return false;
        // }
        // // Check daily usage limit via varbit or other tracking method
        // if (Rs2Equipment.interact(ItemID.MAGIC_CAPE, "Spellbook")) {
        //     // Handle spellbook selection interface
        //     return sleepUntil(() -> getCurrentSpellbook() == this, 5000);
        // }
        
        System.out.println("TODO: Implement Magic cape spellbook switching");
        return false;
    }
    
    /**
     * Switch spellbook using Player Owned House (POH) portal/nexus
     * Requires house with portal room or portal nexus
     * 
     * @return true if switching was attempted
     */
    @SuppressWarnings("unused")
    private boolean switchViaPOH() {
        // TODO: Implement Player Owned House spellbook switching,  first draft concept implentation..
        // Example implementation:
        // 1. Check if player is in their POH or has house tablet/teleport
        // 2. Navigate to house portal room or portal nexus
        // 3. Check if desired spellbook portal is built (requires specific construction levels)
        // 4. Click on the appropriate portal:
        //    - Ancient Magicks portal (requires 50 Construction + completed Desert Treasure I)
        //    - Lunar portal (requires 50 Construction + completed Lunar Diplomacy)
        //    - Arceuus portal (requires 50 Construction, no quest requirement)
        //    - Standard portal (default, always available)
        // 5. Wait for spellbook change confirmation
        //
        // Sample code structure:
        // if (!Rs2Player.isInHouse()) { // NoTE: walker should be capable of teleporting to POH....
        //     // Use house teleport tablet or teleport spell
        //     if (Rs2Inventory.hasItem(ItemID.TELEPORT_TO_HOUSE)) {
        //         Rs2Inventory.interact(ItemID.TELEPORT_TO_HOUSE, "Break");
        //         sleepUntil(() -> Rs2Player.isInHouse(), 5000);
        //     } else if (Rs2Magic.canCast(Spells.TELEPORT_TO_HOUSE)) {
        //         Rs2Magic.cast(Spells.TELEPORT_TO_HOUSE);
        //         sleepUntil(() -> Rs2Player.isInHouse(), 5000);
        //     } else {
        //         System.out.println("No way to access POH for spellbook switching");
        //         return false;
        //     }
        // }
        // //NOTE should be support by the new utility class for the PoH Rooms, Rs2AchvienmtGallary
        // GameObject portal = Rs2GameObject.getGameObjects(o -> 
        //     o.getId() == getGameID() && o.getName().contains("portal")).stream()
        //     .findFirst()
        //     .orElse(null);
        // 
        // if (portal != null && Rs2GameObject.interact(portal, "Enter")) {
        //     return sleepUntil(() -> getCurrentSpellbook() == this, 5000);
        // }
        
        System.out.println("TODO: Implement POH spellbook switching for " + this.name() + " spellbook");
        return false;
    }
    
    /**
     * Check if a spellbook altar switching option is available at current location
     * Useful for determining if player can switch spellbooks
     * 
     * @return true if any spellbook altar is nearby
     */
    public static boolean isAltarNearby() {
        final int maxSearchRadius = 10;
        
        // Check for any of the spellbook altars
        for (Rs2Spellbook spellbook : values()) {
            if (spellbook.getGameID() != null && spellbook != ARCEUUS) { // ARCEUUS uses NPC, not altar
                if (!Rs2GameObject.getGameObjects(o -> spellbook.getGameID().equals(o.getId()), maxSearchRadius).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Check if Tyss NPC (for Arceuus spellbook switching) is nearby
     * 
     * @return true if Tyss NPC is available for interaction
     */
    public static boolean isTyssNearby() {
        return Rs2Npc.getNpc(ARCEUUS.getGameID()) != null;
    }
    
    /**
     * Gets the nearest location where spellbooks can be switched to MODERN spellbook
     * @return WorldPoint of the nearest switching location, or null if none found
     */
    public static WorldPoint getNearestSwitchLocation(boolean includeBankeItems) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }
        
        
        
        
        // Check if POH teleport is available first (most convenient)
        if (canTeleportToPOH()) {
            // POH is accessible - this is typically the best option TODO Implentation comes later whne we support PoH "transports" in Walker also
            return null; // Return null to indicate POH should be used
        }
        List<WorldPoint> spellBookSwapSwitchLocations = Arrays.stream(Rs2Spellbook.values())
                .map(Rs2Spellbook::getSwitchLocation)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        int locationIndex = Rs2Walker.findNearestAccessibleTarget(playerLocation,spellBookSwapSwitchLocations, includeBankeItems, 10);
      
        if (locationIndex == -1) {
            Microbot.log("No spellbook switch locations found nearby");
            return null; // No locations found
        }
        return spellBookSwapSwitchLocations.get(locationIndex);
    }
    
    /**
     * Checks if the player can teleport to their POH via spell or tablet
     * @return true if POH teleport is available, false otherwise
     */
    private static boolean canTeleportToPOH() {
        // Check for house tablet
        if (Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
            return true;
        }
        
        // Check for teleport to house spell (requires runes)
        if (Rs2Magic.hasRequiredRunes(Rs2Spells.TELEPORT_TO_HOUSE)) {
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the player can switch spellbooks in their Player Owned House
     * @return true if player can switch spellbooks in POH, false otherwise
     */
    public static boolean canSwitchInPOH() {
        // Check if player is in their POH
        if (!PohTeleports.isInHouse()) {
            return false;
        }
        
        // TODO: Check for specific POH furniture that allows spellbook switching
        // This could include checking for:
        // - Occult Altar (requires 90 Construction)
        // - Portal room with spellbook portals
        // - Other spellbook switching furniture
        
        // For now, return true if in POH (assuming they have some form of spellbook switching)
        // This is a conservative approach that can be expanded with specific furniture checks
        return true;
    }
}

