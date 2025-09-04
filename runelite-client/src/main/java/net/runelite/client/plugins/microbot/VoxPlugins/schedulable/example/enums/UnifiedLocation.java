package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example.enums;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.walker.enums.*;
import lombok.Getter;

/**
 * Unified location enum that encompasses major location types used in the walker system.
 * This provides a single interface for selecting locations from various categories
 * including banks, deposit boxes, farming locations, slayer masters, and hunting areas.
 * 
 * This is a simplified version containing only the most commonly used locations
 * to avoid compatibility issues with varying enum implementations.
 */
@Getter
public enum UnifiedLocation {
    
    // None option
    NONE("None", LocationType.NONE, null),
    
    // Major Bank Locations
    BANK_GRAND_EXCHANGE("Grand Exchange Bank", LocationType.BANK, BankLocation.GRAND_EXCHANGE),
    BANK_VARROCK_WEST("Varrock West Bank", LocationType.BANK, BankLocation.VARROCK_WEST),
    BANK_VARROCK_EAST("Varrock East Bank", LocationType.BANK, BankLocation.VARROCK_EAST),
    BANK_LUMBRIDGE_FRONT("Lumbridge Bank", LocationType.BANK, BankLocation.LUMBRIDGE_FRONT),
    BANK_FALADOR_WEST("Falador West Bank", LocationType.BANK, BankLocation.FALADOR_WEST),
    BANK_FALADOR_EAST("Falador East Bank", LocationType.BANK, BankLocation.FALADOR_EAST),
    BANK_EDGEVILLE("Edgeville Bank", LocationType.BANK, BankLocation.EDGEVILLE),
    BANK_DRAYNOR_VILLAGE("Draynor Village Bank", LocationType.BANK, BankLocation.DRAYNOR_VILLAGE),
    BANK_AL_KHARID("Al Kharid Bank", LocationType.BANK, BankLocation.AL_KHARID),
    BANK_CATHERBY("Catherby Bank", LocationType.BANK, BankLocation.CATHERBY),
    BANK_CAMELOT("Camelot Bank", LocationType.BANK, BankLocation.CAMELOT),
    BANK_ARDOUGNE_NORTH("Ardougne North Bank", LocationType.BANK, BankLocation.ARDOUGNE_NORTH),
    BANK_ARDOUGNE_SOUTH("Ardougne South Bank", LocationType.BANK, BankLocation.ARDOUGNE_SOUTH),
    BANK_CANIFIS("Canifis Bank", LocationType.BANK, BankLocation.CANIFIS),
    BANK_FISHING_GUILD("Fishing Guild Bank", LocationType.BANK, BankLocation.FISHING_GUILD),
    BANK_FOSSIL_ISLAND("Fossil Island Bank", LocationType.BANK, BankLocation.FOSSIL_ISLAND),
    BANK_ARCEUUS("Arceuus Bank", LocationType.BANK, BankLocation.ARCEUUS),
    BANK_HOSIDIUS("Hosidius Bank", LocationType.BANK, BankLocation.HOSIDIUS),
    BANK_LOVAKENGJ("Lovakengj Bank", LocationType.BANK, BankLocation.LOVAKENGJ),
    BANK_PISCARILIUS("Piscarilius Bank", LocationType.BANK, BankLocation.PISCARILIUS),
    BANK_SHAYZIEN_BANK("Shayzien Bank", LocationType.BANK, BankLocation.SHAYZIEN_BANK),
    BANK_FARMING_GUILD("Farming Guild Bank", LocationType.BANK, BankLocation.FARMING_GUILD),
    
    // Major Deposit Box Locations
    DEPOSIT_BOX_GRAND_EXCHANGE("Grand Exchange Deposit Box", LocationType.DEPOSIT_BOX, DepositBoxLocation.GRAND_EXCHANGE),
    DEPOSIT_BOX_EDGEVILLE("Edgeville Deposit Box", LocationType.DEPOSIT_BOX, DepositBoxLocation.EDGEVILLE),
    DEPOSIT_BOX_BARBARIAN_ASSAULT("Barbarian Assault Deposit Box", LocationType.DEPOSIT_BOX, DepositBoxLocation.BARBARIAN_ASSAULT),
    DEPOSIT_BOX_FALADOR("Falador Deposit Box", LocationType.DEPOSIT_BOX, DepositBoxLocation.FALADOR),
    DEPOSIT_BOX_VARROCK("Varrock Deposit Box", LocationType.DEPOSIT_BOX, DepositBoxLocation.VARROCK),
    DEPOSIT_BOX_LUMBRIDGE("Lumbridge Deposit Box", LocationType.DEPOSIT_BOX, DepositBoxLocation.LUMBRIDGE),
    DEPOSIT_BOX_FARMING_GUILD("Farming Guild Deposit Box", LocationType.DEPOSIT_BOX, DepositBoxLocation.FARMING_GUILD),
    
    // Major Slayer Masters
    SLAYER_MASTER_TURAEL("Turael (Burthorpe)", LocationType.SLAYER_MASTER, SlayerMasters.TURAEL),
    SLAYER_MASTER_SPRIA("Spria (Draynor Village)", LocationType.SLAYER_MASTER, SlayerMasters.SPRIA),
    SLAYER_MASTER_MAZCHNA("Mazchna (Canifis)", LocationType.SLAYER_MASTER, SlayerMasters.MAZCHNA),
    SLAYER_MASTER_VANNAKA("Vannaka (Edgeville Dungeon)", LocationType.SLAYER_MASTER, SlayerMasters.VANNAKA),
    SLAYER_MASTER_CHAELDAR("Chaeldar (Zanaris)", LocationType.SLAYER_MASTER, SlayerMasters.CHAELDAR),
    SLAYER_MASTER_KONAR("Konar quo Maten (Mount Karuulm)", LocationType.SLAYER_MASTER, SlayerMasters.KONAR),
    SLAYER_MASTER_NIEVE("Nieve (Gnome Stronghold)", LocationType.SLAYER_MASTER, SlayerMasters.NIEVE),
    SLAYER_MASTER_STEVE("Steve (Gnome Stronghold)", LocationType.SLAYER_MASTER, SlayerMasters.STEVE),
    SLAYER_MASTER_DURADEL("Duradel (Shilo Village)", LocationType.SLAYER_MASTER, SlayerMasters.DURADEL),
    SLAYER_MASTER_KRYSTILIA("Krystilia (Edgeville)", LocationType.SLAYER_MASTER, SlayerMasters.KRYSTILIA),
    
    // Major Farming Locations - Allotments
    FARMING_FALADOR_ALLOTMENT("Falador Allotment", LocationType.FARMING, Allotments.FALADOR),
    FARMING_CATHERBY_ALLOTMENT("Catherby Allotment", LocationType.FARMING, Allotments.CATHERBY),
    FARMING_ARDOUGNE_ALLOTMENT("Ardougne Allotment", LocationType.FARMING, Allotments.ARDOUGNE),
    FARMING_MORYTANIA_ALLOTMENT("Morytania Allotment", LocationType.FARMING, Allotments.MORYTANIA),
    FARMING_KOUREND_ALLOTMENT("Kourend Allotment", LocationType.FARMING, Allotments.KOUREND),
    FARMING_GUILD_ALLOTMENT("Farming Guild Allotment", LocationType.FARMING, Allotments.FARMING_GUILD),
    
    // Major Farming Locations - Trees
    FARMING_TREE_FALADOR("Falador Tree", LocationType.FARMING, Trees.FALADOR),
    FARMING_TREE_FARMING_GUILD("Farming Guild Tree", LocationType.FARMING, Trees.FARMING_GUILD),
    FARMING_TREE_GNOME_STRONGHOLD("Gnome Stronghold Tree", LocationType.FARMING, Trees.GNOME_STRONGHOLD),
    FARMING_TREE_LUMBRIDGE("Lumbridge Tree", LocationType.FARMING, Trees.LUMBRIDGE),
    FARMING_TREE_TAVERLEY("Taverley Tree", LocationType.FARMING, Trees.TAVERLEY),
    FARMING_TREE_VARROCK("Varrock Tree", LocationType.FARMING, Trees.VARROCK),
    
    // Major Farming Locations - Fruit Trees
    FARMING_FRUIT_TREE_BRIMHAVEN("Brimhaven Fruit Tree", LocationType.FARMING, FruitTrees.BRIMHAVEN),
    FARMING_FRUIT_TREE_CATHERBY("Catherby Fruit Tree", LocationType.FARMING, FruitTrees.CATHERBY),
    FARMING_FRUIT_TREE_FARMING_GUILD("Farming Guild Fruit Tree", LocationType.FARMING, FruitTrees.FARMING_GUILD),
    FARMING_FRUIT_TREE_GNOME_STRONGHOLD("Gnome Stronghold Fruit Tree", LocationType.FARMING, FruitTrees.GNOME_STRONGHOLD),
    FARMING_FRUIT_TREE_TREE_GNOME_VILLAGE("Tree Gnome Village Fruit Tree", LocationType.FARMING, FruitTrees.TREE_GNOME_VILLAGE),
    FARMING_FRUIT_TREE_TAI_BWO_WANNAI("Tai Bwo Wannai Fruit Tree", LocationType.FARMING, FruitTrees.TAI_BWO_WANNAI),
    FARMING_FRUIT_TREE_PRIFDDINAS("Prifddinas Fruit Tree", LocationType.FARMING, FruitTrees.PRIFDDINAS);
    
    private final String displayName;
    private final LocationType type;
    private final Object locationData;
    
    UnifiedLocation(String displayName, LocationType type, Object locationData) {
        this.displayName = displayName;
        this.type = type;
        this.locationData = locationData;
    }
    
    /**
     * Gets the WorldPoint for this location.
     * 
     * @return WorldPoint if available, null otherwise
     */
    public WorldPoint getWorldPoint() {
        if (locationData == null) {
            return null;
        }
        
        switch (type) {
            case BANK:
                return ((BankLocation) locationData).getWorldPoint();
                
            case DEPOSIT_BOX:
                return ((DepositBoxLocation) locationData).getWorldPoint();
                
            case SLAYER_MASTER:
                return ((SlayerMasters) locationData).getWorldPoint();
                
            case FARMING:
                if (locationData instanceof Allotments) {
                    return ((Allotments) locationData).getWorldPoint();
                } else if (locationData instanceof Herbs) {
                    return ((Herbs) locationData).getWorldPoint();
                } else if (locationData instanceof Trees) {
                    return ((Trees) locationData).getWorldPoint();
                } else if (locationData instanceof FruitTrees) {
                    return ((FruitTrees) locationData).getWorldPoint();
                } else if (locationData instanceof Bushes) {
                    return ((Bushes) locationData).getWorldPoint();
                } else if (locationData instanceof Hops) {
                    return ((Hops) locationData).getWorldPoint();
                } else if (locationData instanceof CompostBins) {
                    return ((CompostBins) locationData).getWorldPoint();
                }
                break;
                
            case HUNTING:
                if (locationData instanceof Birds) {
                    return ((Birds) locationData).getWorldPoint();
                } else if (locationData instanceof Chinchompas) {
                    return ((Chinchompas) locationData).getWorldPoint();
                } else if (locationData instanceof Insects) {
                    return ((Insects) locationData).getWorldPoint();
                } else if (locationData instanceof Kebbits) {
                    return ((Kebbits) locationData).getWorldPoint();
                } else if (locationData instanceof Salamanders) {
                    return ((Salamanders) locationData).getWorldPoint();
                } else if (locationData instanceof SpecialHuntingAreas) {
                    return ((SpecialHuntingAreas) locationData).getWorldPoint();
                }
                break;
                
            case NONE:
            default:
                return null;
        }
        
        return null;
    }
    
    /**
     * Gets the original location data object (BankLocation, DepositBoxLocation, etc.)
     * 
     * @return The original location data object
     */
    public Object getOriginalLocationData() {
        return locationData;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
    
    /**
     * Enum representing the different types of locations
     */
    public enum LocationType {
        NONE,
        BANK,
        DEPOSIT_BOX,
        SLAYER_MASTER,
        FARMING,
        HUNTING
    }
}
