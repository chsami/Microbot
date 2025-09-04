package net.runelite.client.plugins.microbot.util.skills.fishing.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.FishingSpot;

import java.util.List;

/**
 * Enhanced fish enumeration that provides comprehensive fish information for the AutoFish plugin.
 * Includes fishing requirements, equipment needed, and expected fish catches.
 * Based on OSRS Wiki fishing data: https://oldschool.runescape.wiki/w/Fishing
 */
@Getter
public enum FishType {
    // Net fishing (Small fishing net)
    SHRIMP(
            "Shrimp/Anchovies",
            FishingSpot.SHRIMP.getIds(),
            List.of("net", "small net"),
            List.of("raw shrimps", "raw anchovies"),
            List.of(ItemID.NET), // Small fishing net
            -1, // No secondary item needed
            1, // Fishing level requirement
            false, // Not members only
            "One of the most popular beginner fishing spots"
    ),
    
    // Bait fishing (Fishing rod + bait)
    SARDINE(
            "Sardine/Herring", 
            FishingSpot.SHRIMP.getIds(),
            List.of("bait"),
            List.of("raw sardine", "raw herring"),
            List.of(ItemID.FISHING_ROD),
            ItemID.FISHING_BAIT,
            5, // Fishing level requirement 
            false, // Not members only
            "Bait fishing for sardines and herring"
    ),
    
    // Big net fishing 
    MACKEREL(
            "Mackerel/Cod/Bass",
            FishingSpot.SHARK.getIds(),
            List.of("big net"),
            List.of("raw mackerel", "raw cod", "raw bass"),
            List.of(305), // BIG_FISHING_NET
            -1, // No secondary item needed
            16, // Fishing level requirement for mackerel
            false, // Not members only
            "Big net fishing for various sea fish"
    ),
    
    // Fly fishing (Fly fishing rod + feather)
    TROUT(
            "Trout/Salmon",
            FishingSpot.SALMON.getIds(),
            List.of("lure"),
            List.of("raw trout", "raw salmon"),
            List.of(ItemID.FLY_FISHING_ROD),
            ItemID.FEATHER,
            20, // Fishing level requirement for trout
            false, // Not members only
            "Popular training spot with good XP rates"
    ),
    
    // Bait fishing for pike
    PIKE(
            "Pike",
            FishingSpot.SALMON.getIds(),
            List.of("bait"),
            List.of("raw pike"),
            List.of(ItemID.FISHING_ROD),
            ItemID.FISHING_BAIT,
            25, // Fishing level requirement
            false, // Not members only
            "Single-species fishing spot for pike"
    ),
    
    // Harpoon fishing
    TUNA(
            "Tuna/Swordfish",
            FishingSpot.LOBSTER.getIds(),
            List.of("harpoon"),
            List.of("raw tuna", "raw swordfish"),
            List.of(ItemID.HARPOON),
            -1, // No secondary item needed
            35, // Fishing level requirement for tuna
            false, // Not members only
            "Mid-level harpoon fishing"
    ),
    
    // Lobster pot fishing
    LOBSTER(
            "Lobster",
            FishingSpot.LOBSTER.getIds(),
            List.of("cage"),
            List.of("raw lobster"),
            List.of(ItemID.LOBSTER_POT),
            -1, // No secondary item needed
            40, // Fishing level requirement
            false, // Not members only
            "Popular money-making fish"
    ),
    
    // Monkfish (Members only)
    MONKFISH(
            "Monkfish",
            FishingSpot.MONKFISH.getIds(),
            List.of("net"),
            List.of("raw monkfish"),
            List.of(303), // SMALL_FISHING_NET
            -1, // No secondary item needed
            62, // Fishing level requirement
            true, // Members only
            "Excellent XP and profit, requires Swan Song quest"
    ),
    
    // Karambwanji (Members only)
    KARAMBWANJI(
            "Karambwanji",
            FishingSpot.KARAMBWANJI.getIds(),
            List.of("net"),
            List.of("raw karambwanji"),
            List.of(303), // SMALL_FISHING_NET
            -1, // No secondary item needed
            5, // Fishing level requirement
            true, // Members only
            "Used as bait for karambwan fishing"
    ),
    
    // Shark fishing
    SHARK(
            "Shark",
            FishingSpot.SHARK.getIds(),
            List.of("harpoon"),
            List.of("raw shark"),
            List.of(ItemID.HARPOON),
            -1, // No secondary item needed
            76, // Fishing level requirement
            false, // Not members only
            "High-level fishing with excellent profit"
    ),
    
    // Anglerfish (Members only)
    ANGLERFISH(
            "Anglerfish",
            FishingSpot.ANGLERFISH.getIds(),
            List.of("sandworms", "bait"),
            List.of("raw anglerfish"),
            List.of(ItemID.FISHING_ROD),
            ItemID.PISCARILIUS_SANDWORMS,
            82, // Fishing level requirement
            true, // Members only
            "Requires 100% Piscarilius favour and sandworms"
    ),
    
    // Karambwan fishing (Members only)
    KARAMBWAN(
            "Karambwan", 
            FishingSpot.KARAMBWAN.getIds(),
            List.of("fish"),
            List.of("raw karambwan"),
            List.of(ItemID.TBWT_KARAMBWAN_VESSEL,ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI),
            ItemID.TBWT_RAW_KARAMBWANJI,
            65, // Fishing level requirement
            true, // Members only
            "Requires Tai Bwo Wannai Trio quest"
    ),
    
    // Barbarian fishing (Members only)
    BARBARIAN(
            "Barbarian Fishing",
            FishingSpot.BARB_FISH.getIds(),
            List.of("use-rod"),
            List.of("leaping trout", "leaping salmon", "leaping sturgeon"),
            List.of(ItemID.BRUT_FISHING_ROD),
            ItemID.FEATHER,
            48, // Fishing level requirement (leaping trout)
            true, // Members only
            "Trains Fishing, Agility, and Strength simultaneously"
    ),
    
    // Cave eel (Members only)
    CAVE_EEL(
            "Cave Eel",
            FishingSpot.CAVE_EEL.getIds(),
            List.of("bait"),
            List.of("raw cave eel"),
            List.of(ItemID.FISHING_ROD),
            ItemID.FISHING_BAIT,
            38, // Fishing level requirement
            true, // Members only
            "Found in underground locations"
    ),
    
    // Lava eel (Members only)
    LAVA_EEL(
            "Lava Eel",
            FishingSpot.LAVA_EEL.getIds(),
            List.of("lure"),
            List.of("raw lava eel"),
            List.of(ItemID.OILY_FISHING_ROD),
            ItemID.FISHING_BAIT,
            53, // Fishing level requirement
            true, // Members only
            "Requires oily fishing rod, found in Taverley Dungeon"
    ),
    
    // Infernal eel (Members only)
    INFERNAL_EEL(
            "Infernal Eel",
            new int[]{7756}, // NPC ID for infernal eel fishing spot
            List.of("bait"),
            List.of("infernal eel"),
            List.of(ItemID.OILY_FISHING_ROD),
            ItemID.FISHING_BAIT,
            80, // Fishing level requirement
            true, // Members only
            "Found in Mor Ul Rek, requires access to inner city"
    ),
    
    // Sacred eel (Members only)
    SACRED_EEL(
            "Sacred Eel",
            FishingSpot.SACRED_EEL.getIds(),
            List.of("bait"),
            List.of("sacred eel"),
            List.of(ItemID.FISHING_ROD),
            ItemID.FISHING_BAIT,
            87, // Fishing level requirement
            true, // Members only
            "Found at Zul-Andra, requires Regicide quest progress"
    ),
    
    // Dark crab (Members only)
    DARK_CRAB(
            "Dark Crab",
            FishingSpot.DARK_CRAB.getIds(),
            List.of("cage"),
            List.of("raw dark crab"),
            List.of(ItemID.LOBSTER_POT),
            ItemID.WILDERNESS_FISHING_BAIT,
            85, // Fishing level requirement
            true, // Members only
            "Wilderness fishing with excellent profit but high risk"
    ),
    
    // Minnows (Members only) 
    MINNOWS(
            "Minnows",
            FishingSpot.MINNOW.getIds(),
            List.of("net"),
            List.of("minnow"),
            List.of(303), // SMALL_FISHING_NET
            -1, // No secondary item needed
            82, // Fishing level requirement
            true, // Members only
            "Requires full angler outfit shown to Kylie Minnow"
    ),
    
    // Aerial fishing fish (Members only)
    BLUEGILL(
            "Bluegill",
            new int[]{1542, 1544}, // Aerial fishing spot IDs
            List.of("fishing spot"),
            List.of("bluegill"),
            List.of(ItemID.AERIAL_FISHING_GLOVES_NO_BIRD,ItemID.AERIAL_FISHING_GLOVES_BIRD),
            ItemID.KING_WORM, // or Fish chunks
            43, // Fishing level requirement
            true, // Members only
            "Aerial fishing on Molch Island, trains Hunter too"
    ),
    
    COMMON_TENCH(
            "Common Tench",
            new int[]{1542, 1544}, // Aerial fishing spot IDs
            List.of("fishing spot"),
            List.of("common tench"),
            List.of(ItemID.AERIAL_FISHING_GLOVES_NO_BIRD,ItemID.AERIAL_FISHING_GLOVES_BIRD),
            ItemID.KING_WORM, // or Fish chunks
            56, // Fishing level requirement
            true, // Members only
            "Aerial fishing on Molch Island"
    ),
    
    MOTTLED_EEL(
            "Mottled Eel",
            new int[]{1542, 1544}, // Aerial fishing spot IDs
            List.of("fishing spot"),
            List.of("mottled eel"),
            List.of(ItemID.AERIAL_FISHING_GLOVES_NO_BIRD,ItemID.AERIAL_FISHING_GLOVES_BIRD),
            ItemID.KING_WORM, // or Fish chunks
            73, // Fishing level requirement
            true, // Members only
            "Aerial fishing on Molch Island"
    ),
    
    GREATER_SIREN(
            "Greater Siren",
            new int[]{1542, 1544}, // Aerial fishing spot IDs
            List.of("fishing spot"),
            List.of("greater siren"),
            List.of(ItemID.AERIAL_FISHING_GLOVES_NO_BIRD,ItemID.AERIAL_FISHING_GLOVES_BIRD),
            ItemID.KING_WORM, // or Fish chunks
            91, // Fishing level requirement
            true, // Members only
            "High-level aerial fishing on Molch Island"
    );

    private final String name;
    private final int[] fishingSpotIds;
    private final List<String> actions;
    private final List<String> rawNames;
    private final List<Integer> primaryEquipmentIds;
    private final int secondaryItemId; // -1 if no secondary item needed
    private final int levelRequirement;
    private final boolean membersOnly;
    private final String description;
    // Constructor for single equipment ID (converts to List)
    FishType(String name, int[] fishingSpotIds, List<String> actions, List<String> rawNames,
            int primaryEquipmentId, int secondaryItemId, int levelRequirement, boolean membersOnly, String description) {
        this.name = name;
        this.fishingSpotIds = fishingSpotIds;
        this.actions = actions;
        this.rawNames = rawNames;
        this.primaryEquipmentIds = List.of(primaryEquipmentId);
        this.secondaryItemId = secondaryItemId;
        this.levelRequirement = levelRequirement;
        this.membersOnly = membersOnly;
        this.description = description;
    }

    // Constructor for multiple equipment IDs (accepts List directly)
    FishType(String name, int[] fishingSpotIds, List<String> actions, List<String> rawNames,
            List<Integer> primaryEquipmentIds, int secondaryItemId, int levelRequirement, boolean membersOnly, String description) {
        this.name = name;
        this.fishingSpotIds = fishingSpotIds;
        this.actions = actions;
        this.rawNames = rawNames;
        this.primaryEquipmentIds = primaryEquipmentIds;
        this.secondaryItemId = secondaryItemId;
        this.levelRequirement = levelRequirement;
        this.membersOnly = membersOnly;
        this.description = description;
    }
    @Override
    public String toString() {
        return name;
    }
    
    /**
     * Gets the equipment name for the primary fishing tool
     */
    public String getPrimaryEquipmentName() {
        // Return the name of the first equipment ID (most common case)
        if (primaryEquipmentIds.isEmpty()) {
            return "Unknown equipment";
        }
        
        int primaryId = primaryEquipmentIds.get(0);
        switch (primaryId) {
            case ItemID.NET:
                return "Small fishing net";
            case 305: // BIG_FISHING_NET
                return "Big fishing net";
            case ItemID.FISHING_ROD:
                return "Fishing rod";
            case ItemID.FLY_FISHING_ROD:
                return "Fly fishing rod";
            case ItemID.OILY_FISHING_ROD:
                return "Oily fishing rod";
            case ItemID.BRUT_FISHING_ROD:
                return "Barbarian rod";
            case ItemID.HARPOON:
                return "Harpoon";
            case ItemID.LOBSTER_POT:
                return "Lobster pot";
            case ItemID.TBWT_KARAMBWAN_VESSEL:
            case ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI:
                return "Karambwan vessel";
            case ItemID.AERIAL_FISHING_GLOVES_NO_BIRD:
            case ItemID.AERIAL_FISHING_GLOVES_BIRD:
                return "Cormorant's glove";
            default:
                return "Unknown equipment";
        }
    }
    
    /**
     * Gets all primary equipment names (for fish that accept multiple tools)
     */
    public List<String> getAllPrimaryEquipmentNames() {
        return primaryEquipmentIds.stream()
                .map(this::getEquipmentNameById)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Helper method to get equipment name by ID
     */
    private String getEquipmentNameById(int equipmentId) {
        switch (equipmentId) {
            case ItemID.NET:
                return "Small fishing net";
            case 305: // BIG_FISHING_NET
                return "Big fishing net";
            case ItemID.FISHING_ROD:
                return "Fishing rod";
            case ItemID.FLY_FISHING_ROD:
                return "Fly fishing rod";
            case ItemID.OILY_FISHING_ROD:
                return "Oily fishing rod";
            case ItemID.BRUT_FISHING_ROD:
                return "Barbarian rod";
            case ItemID.HARPOON:
                return "Harpoon";
            case ItemID.LOBSTER_POT:
                return "Lobster pot";
            case ItemID.TBWT_KARAMBWAN_VESSEL:
            case ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI:
                return "Karambwan vessel";
            case ItemID.AERIAL_FISHING_GLOVES_NO_BIRD:
            case ItemID.AERIAL_FISHING_GLOVES_BIRD:
                return "Cormorant's glove";
            default:
                return "Unknown equipment";
        }
    }
    
    /**
     * Gets the secondary item name if required
     */
    public String getSecondaryItemName() {
        if (secondaryItemId == -1) {
            return null;
        }
        
        switch (secondaryItemId) {
            case ItemID.FISHING_BAIT:
                return "Fishing bait";
            case ItemID.FEATHER:
                return "Feather";
            case ItemID.PISCARILIUS_SANDWORMS:
                return "Sandworms";
            case ItemID.TBWT_RAW_KARAMBWANJI:
                return "Raw karambwanji";
            case ItemID.WILDERNESS_FISHING_BAIT:
                return "Dark fishing bait";
            case ItemID.KING_WORM:
                return "King worm";
            default:
                return "Unknown item";
        }
    }
    
    /**
     * Checks if this fish type requires a secondary item
     */
    public boolean requiresSecondaryItem() {
        return secondaryItemId != -1;
    }
    
    /**
     * Gets the secondary equipment ID (-1 if none required)
     */
    public int getSecondaryEquipmentId() {
        return secondaryItemId;
    }
}
