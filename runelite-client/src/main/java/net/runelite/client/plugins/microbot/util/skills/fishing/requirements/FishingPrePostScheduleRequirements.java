package net.runelite.client.plugins.microbot.util.skills.fishing.requirements;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.fishing.aerial.AerialFishingConfig;
import net.runelite.client.plugins.microbot.fishing.barbarian.BarbarianFishingConfig;
import net.runelite.client.plugins.microbot.fishing.eel.EelFishingConfig;
import net.runelite.client.plugins.microbot.fishing.minnows.MinnowsConfig;
import net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing.AutoFishConfig;
import net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing.enums.Fish;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationOption;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.skills.fishing.data.FishingSpotLocations;
import net.runelite.client.plugins.microbot.util.skills.fishing.enums.FishType;

import java.util.List;

/**
 * Enhanced fishing requirements implementation that supports multiple fishing configurations.
 * Provides proper equipment, location, and item requirements for various fishing activities.
 */
public class FishingPrePostScheduleRequirements extends PrePostScheduleRequirements {
    private final MinnowsConfig minnowsConfig;
    private final EelFishingConfig eelFishingConfig;
    private final BarbarianFishingConfig barbarianFishingConfig;
    private final AerialFishingConfig aerialFishingConfig;
    private final AutoFishConfig autoFishConfig;
    
    // Constructor for AutoFishConfig (primary fishing plugin)
    public FishingPrePostScheduleRequirements(AutoFishConfig config) {
        super("Auto Fishing", "Fishing", false);
        this.autoFishConfig = config;
        this.minnowsConfig = null;
        this.eelFishingConfig = null;
        this.barbarianFishingConfig = null;
        this.aerialFishingConfig = null;
        initializeRequirements();
    }
    
    // Constructor for MinnowsConfig
    public FishingPrePostScheduleRequirements(MinnowsConfig config) {
        super("Minnows Fishing", "Fishing", false);
        this.minnowsConfig = config;
        this.autoFishConfig = null;
        this.eelFishingConfig = null;
        this.barbarianFishingConfig = null;
        this.aerialFishingConfig = null;
        initializeRequirements();
    }
    
    // Constructor for EelFishingConfig
    public FishingPrePostScheduleRequirements(EelFishingConfig config) {
        super("Eel Fishing", "Fishing", false);
        this.eelFishingConfig = config;
        this.autoFishConfig = null;
        this.minnowsConfig = null;
        this.barbarianFishingConfig = null;
        this.aerialFishingConfig = null;
        initializeRequirements();
    }
    
    // Constructor for BarbarianFishingConfig
    public FishingPrePostScheduleRequirements(BarbarianFishingConfig config) {
        super("Barbarian Fishing", "Fishing", false);
        this.barbarianFishingConfig = config;
        this.autoFishConfig = null;
        this.minnowsConfig = null;
        this.eelFishingConfig = null;
        this.aerialFishingConfig = null;
        initializeRequirements();
    }
    
    // Constructor for AerialFishingConfig
    public FishingPrePostScheduleRequirements(AerialFishingConfig config) {
        super("Aerial Fishing", "Fishing", false);
        this.aerialFishingConfig = config;
        this.autoFishConfig = null;
        this.minnowsConfig = null;
        this.eelFishingConfig = null;
        this.barbarianFishingConfig = null;
        initializeRequirements();
    }

    @Override
    protected boolean initializeRequirements() {
        // Ensure at least one config is provided
        if (autoFishConfig == null && minnowsConfig == null && eelFishingConfig == null && 
            barbarianFishingConfig == null && aerialFishingConfig == null) {
            return false;
        }
        
        this.getRegistry().clear();
        
        // Register location requirements based on the specific config
        registerLocationRequirements();
        
        // Register equipment and item requirements
        registerEquipmentRequirements();
        
        // Register common fishing items
        registerCommonFishingItems();
        
        // Register post-schedule banking location
        LocationRequirement bankingReq = new LocationRequirement(
            BankLocation.GRAND_EXCHANGE, 
            true, //use transportation ->banked items
            -1,
            ScheduleContext.POST_SCHEDULE,
            RequirementPriority.RECOMMENDED 
            
        );
        this.register(bankingReq);
        
        return true;
    }
    
    private void registerLocationRequirements() {
        if (autoFishConfig != null) {
            registerAutoFishLocationRequirements();
        } else if (minnowsConfig != null) {
            registerMinnowsLocationRequirements();
        } else if (eelFishingConfig != null) {
            registerEelFishingLocationRequirements();
        } else if (barbarianFishingConfig != null) {
            registerBarbarianFishingLocationRequirements();
        } else if (aerialFishingConfig != null) {
            registerAerialFishingLocationRequirements();
        }
    }
    
    private void registerAutoFishLocationRequirements() {
        Fish selectedFish = autoFishConfig.fish();
        FishType fishType = mapFishToFishType(selectedFish);
        
        if (fishType != null) {
            List<LocationOption> locations = FishingSpotLocations.getLocationsForFish(fishType);
            if (!locations.isEmpty()) {
                LocationRequirement locationReq = new LocationRequirement(
                     locations, 
                    10, 
                    true,
                    -1,
                    ScheduleContext.PRE_SCHEDULE,
                    RequirementPriority.MANDATORY,  
                    9,  
                    "Fishing location for " + fishType.name()
                );
                this.register(locationReq);
            }
        }
    }
    
    private void registerMinnowsLocationRequirements() {
        List<LocationOption> locations = FishingSpotLocations.getLocationsForFish(FishType.MINNOWS);
        if (!locations.isEmpty()) {
            LocationRequirement locationReq = new LocationRequirement(
                locations, 
                10, 
                true,
                -1,
                ScheduleContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,  
                9,               
                "Minnows fishing location"
              
            );
            this.register(locationReq);
        }
    }
    
    private void registerEelFishingLocationRequirements() {
        // Default to cave eels, but this could be enhanced based on specific eel config
        List<LocationOption> locations = FishingSpotLocations.getLocationsForFish(FishType.CAVE_EEL);
        if (!locations.isEmpty()) {
            LocationRequirement locationReq = new LocationRequirement(
                locations, 
                10, 
                true,
                -1,
                ScheduleContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,  
                9,        
                "Cave eel fishing location"
                
            );
            this.register(locationReq);
        }
    }
    
    private void registerBarbarianFishingLocationRequirements() {
        List<LocationOption> locations = FishingSpotLocations.getLocationsForFish(FishType.BARBARIAN);
        if (!locations.isEmpty()) {
            LocationRequirement locationReq = new LocationRequirement(
                locations,                 
                     10, 
                true,
                -1,
                ScheduleContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,  
                9,       
                "Barbarian fishing location"
            );
            this.register(locationReq);
        }
    }
    
    private void registerAerialFishingLocationRequirements() {
        // Aerial fishing uses bluegill and common tench
        List<LocationOption> locations = FishingSpotLocations.getLocationsForFish(FishType.BLUEGILL);
        if (!locations.isEmpty()) {
            LocationRequirement locationReq = new LocationRequirement(
                locations,
                    10, 
                true,
                -1,
                ScheduleContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,  
                9,       
                "Aerial fishing location"
            );
            this.register(locationReq);
        }
    }
    
    private void registerEquipmentRequirements() {
        if (autoFishConfig != null) {
            registerAutoFishEquipment();
        } else if (minnowsConfig != null) {
            registerMinnowsEquipment();
        } else if (eelFishingConfig != null) {
            registerEelFishingEquipment();
        } else if (barbarianFishingConfig != null) {
            registerBarbarianFishingEquipment();
        } else if (aerialFishingConfig != null) {
            registerAerialFishingEquipment();
        }
    }
    
    private void registerAutoFishEquipment() {
        Fish selectedFish = autoFishConfig.fish();
        FishType fishType = mapFishToFishType(selectedFish);
        
        if (fishType != null) {
            List<Integer> equipmentIds = fishType.getPrimaryEquipmentIds();
            if (!equipmentIds.isEmpty()) {
                // Register the primary equipment as mandatory (in inventory, not weapon slot)
                OrRequirement equipmentReq = ItemRequirement.createOrRequirement(
                    equipmentIds, // Use first equipment ID
                    1, 
                    null, // No equipment slot - goes in inventory
                    -1, // No specific inventory slot
                    RequirementPriority.MANDATORY, 
                    9, // High rating for primary equipment
                    "Primary fishing equipment for " + fishType.name(),
                    ScheduleContext.PRE_SCHEDULE,
                    null, // No skill to use
                    null, // No minimum level to use
                    null, // No skill to equip
                    null, // No minimum level to equip
                    false // Not fuzzy
                );
                this.register(equipmentReq);
                
                // Register secondary equipment if needed (like bait)
                if (fishType.getSecondaryEquipmentId() != -1) {
                    ItemRequirement baitReq = new ItemRequirement(
                        fishType.getSecondaryEquipmentId(), 
                        50, // Reasonable amount of bait
                        null, // No equipment slot - goes in inventory
                        -1, // No specific inventory slot
                        RequirementPriority.MANDATORY, 
                        8,
                        "Bait for " + fishType.name(),
                        ScheduleContext.PRE_SCHEDULE,
                        null, // No skill to use
                        null, // No minimum level to use
                        null, // No skill to equip
                        null, // No minimum level to equip
                        false // Not fuzzy
                    );
                    this.register(baitReq);
                }
            }
        }
    }
    
    private void registerMinnowsEquipment() {
        // Minnows require small fishing net (goes in inventory, not weapon slot)
        ItemRequirement netReq = new ItemRequirement(
            ItemID.NET, 
            1, 
            null, // No equipment slot - goes in inventory
            -1, // No specific inventory slot
            RequirementPriority.MANDATORY, 
            9,
            "Small fishing net for minnows",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(netReq);
    }
    
    private void registerEelFishingEquipment() {
        // Cave eels require fishing rod and bait (both go in inventory)
        ItemRequirement rodReq = new ItemRequirement(
            ItemID.FISHING_ROD, 
            1, 
            null, // No equipment slot - goes in inventory
            -1, // No specific inventory slot
            RequirementPriority.MANDATORY, 
            9,
            "Fishing rod for eel fishing",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(rodReq);
        
        ItemRequirement baitReq = new ItemRequirement(
            ItemID.FISHING_BAIT, 
            100, 
            null, // No equipment slot - goes in inventory
            -1, // No specific inventory slot
            RequirementPriority.MANDATORY, 
            8,
            "Fishing bait for eel fishing",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(baitReq);
    }
    
    private void registerBarbarianFishingEquipment() {
        // Barbarian fishing requires barbarian rod (goes in inventory)
        ItemRequirement rodReq = new ItemRequirement(
            ItemID.BRUT_FISHING_ROD, 
            1, 
            null, // No equipment slot - goes in inventory
            -1, // No specific inventory slot
            RequirementPriority.MANDATORY, 
            9,
            "Barbarian fishing rod",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(rodReq);
        
        // Feathers for bait
        ItemRequirement featherReq = new ItemRequirement(
            ItemID.FEATHER, 
            200, 
            null, // No equipment slot - goes in inventory
            null, // No specific inventory slot
            RequirementPriority.MANDATORY, 
            8,
            "Feathers for barbarian fishing bait",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(featherReq);
    }
    
    private void registerAerialFishingEquipment() {
        // Aerial fishing requires cormorant's glove and fishing rod
        OrRequirement gloveReq = ItemRequirement.createOrRequirement(
            List.of(ItemID.AERIAL_FISHING_GLOVES_NO_BIRD,ItemID.AERIAL_FISHING_GLOVES_BIRD), 
            1, 
            EquipmentInventorySlot.GLOVES, 
            -2, //must be quipped 
            RequirementPriority.MANDATORY, 
            9,
            "Cormorant's glove for aerial fishing",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(gloveReq);
        
        ItemRequirement rodReq = new ItemRequirement(
            ItemID.FISHING_ROD, 
            1, 
            null, // No equipment slot - goes in inventory
            null, // No specific inventory slot
            RequirementPriority.MANDATORY, 
            8,
            "Fishing rod for aerial fishing",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(rodReq);
        
        // King worms for bait
        ItemRequirement baitReq = new ItemRequirement(
            ItemID.KING_WORM, 
            50, 
            null, // No equipment slot - goes in inventory
            null, // No specific inventory slot
            RequirementPriority.RECOMMENDED, 
            7,
            "King worms for aerial fishing bait",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(baitReq);
    }
    
    private void registerCommonFishingItems() {
        // Angler outfit - provides XP bonus for fishing
        ItemRequirementCollection.registerAnglerOutfit(
            this, 
            RequirementPriority.RECOMMENDED, 
            10, 
            ScheduleContext.PRE_SCHEDULE, 
            false, // Don't skip hat
            false, // Don't skip top
            false, // Don't skip legs
            true   // Skip boots (allow graceful boots)
        );
        
        // Food for longer fishing sessions
        ItemRequirement foodReq = new ItemRequirement(
            ItemID.LOBSTER, 
            5, 
            null, // No equipment slot - goes in inventory
            null, // No specific inventory slot
            RequirementPriority.RECOMMENDED, 
            6,
            "Food for healing during fishing",
            ScheduleContext.PRE_SCHEDULE,
            null, // No skill to use
            null, // No minimum level to use
            null, // No skill to equip
            null, // No minimum level to equip
            false // Not fuzzy
        );
        this.register(foodReq);
    }
    
    /**
     * Maps the AutoFish Fish enum to our enhanced FishType enum
     */
    private FishType mapFishToFishType(Fish fish) {
        if (fish == null) return null;
        
        switch (fish) {
            case SHRIMP:
                return FishType.SHRIMP;
            case SARDINE:
                return FishType.SARDINE;
            case MACKEREL:
                return FishType.MACKEREL;
            case TROUT:
                return FishType.TROUT;
            case PIKE:
                return FishType.PIKE;
            case TUNA:
                return FishType.TUNA;
            case LOBSTER:
                return FishType.LOBSTER;
            case MONKFISH:
                return FishType.MONKFISH;
            case SHARK:
                return FishType.SHARK;
            case CAVE_EEL:
                return FishType.CAVE_EEL;
            case LAVA_EEL:
                return FishType.LAVA_EEL;
            case ANGLERFISH:
                return FishType.ANGLERFISH;
            case KARAMBWAN:
                return FishType.KARAMBWAN;
            case KARAMBWANJI:
                return FishType.KARAMBWANJI;
            default:
                return FishType.SHRIMP; // Default fallback
        }
    }

    @Override
    public void reset() {
        this.getRegistry().clear(); // Clear the registry to remove all requirements
        this.setInitialized(false); // Mark as uninitialized
        initializeRequirements(); // Reinitialize requirements  
    }
}
