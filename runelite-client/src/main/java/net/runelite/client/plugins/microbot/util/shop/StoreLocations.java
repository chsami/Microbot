package net.runelite.client.plugins.microbot.util.shop;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopSource;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopType;

/**
 * Enum representing all general stores in OSRS with their locations, buy rates, stock items, and NPCs.
 * Each store has different buy rates (25%, 40%, 50%, 55%, 60%) and different stock items.
 * Includes Trader Stan's Trading Post locations which have lower buy rates.
 */
public enum StoreLocations {
    
        // 40% buy rate stores
    LUMBRIDGE_GENERAL_STORE(
        "Lumbridge General Store",
        "Shop keeper",
        new WorldPoint(3212, 3247, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,
        0.03,
        false,
        Map.of(),
        Map.of(),
        Map.of(),
        new int[]{
            ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
        
    ),
    
    VARROCK_GENERAL_STORE(
        "Varrock General Store",
        "Shop keeper",
        new WorldPoint(3218, 3413, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,
        0.03,
        false,
        Map.of(),
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
    ),
    
    EDGEVILLE_GENERAL_STORE(
        "Edgeville General Store",
        "Shop keeper",
        new WorldPoint(3079, 3510, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,
        0.03,
        false,
        Map.of(),
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
    ),
    
    RIMMINGTON_GENERAL_STORE(
        "Rimmington General Store",
        "Shop keeper",
        new WorldPoint(2946, 3208, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,
        0.03,
        false,
        Map.of(),
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
    ),
    
    FALADOR_GENERAL_STORE(
        "Falador General Store",
        "Shop keeper",
        new WorldPoint(2955, 3390, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,
        0.03,
        false,
        Map.of(),
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
    ),
    

    KARAMJA_GENERAL_STORE(
        "Karamja General Store",
        "Shop keeper",
        new WorldPoint(2955, 3081, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,
        0.03,
        false,
        Map.of(),
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
    ),
    KARAMJA_GENERAL_STORE_DIARY(
        "Karamja General Store (Diary)",        
        "Shop keeper",
        new WorldPoint(2955, 3081, 0),
        Rs2ShopType.GENERAL_STORE,
        1.10, // percentageSoldAt
        0.60,
        0.03,
        true,
        Map.of(),
        Map.of(),// Varbit requirements for the shop, with progress state.. have to check cant find it in varbit..
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
    ),
    
    ARHEIN_STORE(
        "Arhein Store Seers' Village General Store",
        "Arhein",
        new WorldPoint(2803, 3432, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,        
        0.03,
        true,
        Map.of(),
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.NEWCOMER_MAP, ItemID.SOS_SECURITY_BOOK
        }
    ),
    

    CANIFIS_GENERAL_STORE(
        "General Store (Canifis)",
        "Fidelio",
        new WorldPoint(3430, 3484, 0),
        Rs2ShopType.GENERAL_STORE,
        2.0,
        0.10,
        0.03,
        true,
        Map.of(Quest.PRIEST_IN_PERIL, QuestState.FINISHED),        
        Map.of(),
        Map.of(),
        new int[]{
            ItemID.NEEDLE,ItemID.THREAD ,ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,ItemID.EMPTY_DYE_BOTTLE,
            ItemID.CHISEL, ItemID.HAMMER
        }
    ),

    LLETYA_GENERAL_STORE(
        "Lletya General Store",
        "Eudav",
        new WorldPoint(2350, 3166, 0),
        Rs2ShopType.GENERAL_STORE,
        1.30, // percentageSoldAt
        0.40,
        0.03,
        true,
        Map.of(Quest.MOURNINGS_END_PART_I, QuestState.FINISHED),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER
        }
    ),
    
  
    
    // 60% buy rate stores
    BANDIT_DUTY_FREE(
        "Bandit Duty Free",
        "Noterazzo",
        new WorldPoint(3026, 3702, 0),
        Rs2ShopType.GENERAL_STORE,
        0.9,
        0.60,
        0.03,
        false,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.PACK_JUG_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.BRONZE_PICKAXE, ItemID.BRONZE_AXE
        }
    ),
    
    
    // 55% buy rate stores (West Ardougne and Pollnivneach when empty stock)
    WEST_ARDOUGNE_GENERAL_STORE(
        "West Ardougne General Store",
        "Chadwell",
        new WorldPoint(2548, 3328, 0),
        Rs2ShopType.GENERAL_STORE,
        1.2, // percentageSoldAt
        0.55,
        0.02,
        false,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY,  ItemID.ROPE, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX, ItemID.HAMMER,ItemID.LEATHER_BOOTS,ItemID.LONGBOW,ItemID.MEAT_PIE, ItemID.SALMON,
             ItemID.COOKED_MEAT, ItemID.BREAD,ItemID.BRONZE_ARROW

                    }
    ),
    
       
    // 15% buy rate stores (Trader Stan's Trading Post locations)
    TRADER_STAN_PORT_SARIM(
        "Trader Stan's Trading Post",
        "Trader Crewmembers",
        new WorldPoint(3041, 3193, 0),
        Rs2ShopType.GENERAL_STORE,
        2.5,
        0.15,
        0.03,
        true,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.KNIFE, ItemID.ROPE,ItemID.PINEAPPLE, ItemID.BANANA,ItemID.ORANGE,ItemID.BUCKET_ECTOPLASM,
            ItemID.GLASSBLOWINGPIPE, ItemID.BUCKET_SAND, ItemID.SEAWEED, ItemID.SODA_ASH,ItemID.LOBSTER_POT,ItemID.FISHING_ROD,
            ItemID.SWAMP_TAR,ItemID.SAIL_TYRAS_HELM,ItemID.RAW_RABBIT,ItemID.EYE_PATCH
        }
    ),
    
    TRADER_STAN_KARAMJA(
        "Trader Stan's Trading Post",
        "Trader Crewmembers",
        new WorldPoint(2954, 3146, 0),
        Rs2ShopType.GENERAL_STORE,
        2.5,
        0.15,
        0.03,
        true,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.KNIFE, ItemID.ROPE,ItemID.PINEAPPLE, ItemID.BANANA,ItemID.ORANGE,ItemID.BUCKET_ECTOPLASM,
            ItemID.GLASSBLOWINGPIPE, ItemID.BUCKET_SAND, ItemID.SEAWEED, ItemID.SODA_ASH,ItemID.LOBSTER_POT,ItemID.FISHING_ROD,
            ItemID.SWAMP_TAR,ItemID.SAIL_TYRAS_HELM,ItemID.RAW_RABBIT,ItemID.EYE_PATCH
        }
    ),
    
    TRADER_STAN_CATHERBY(
        "Trader Stan's Trading Post", 
        "Trader Crewmembers",
        new WorldPoint(2792, 3414, 0),
          Rs2ShopType.GENERAL_STORE,
        2.5,
        0.15,
        0.03,
        true,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.KNIFE, ItemID.ROPE,ItemID.PINEAPPLE, ItemID.BANANA,ItemID.ORANGE,ItemID.BUCKET_ECTOPLASM,
            ItemID.GLASSBLOWINGPIPE, ItemID.BUCKET_SAND, ItemID.SEAWEED, ItemID.SODA_ASH,ItemID.LOBSTER_POT,ItemID.FISHING_ROD,
            ItemID.SWAMP_TAR,ItemID.SAIL_TYRAS_HELM,ItemID.RAW_RABBIT,ItemID.EYE_PATCH
        }
    ),
    
    TRADER_STAN_RELLEKKA(
        "Trader Stan's Trading Post",
        "Trader Crewmembers",
        new WorldPoint(2629, 3693, 0),
          Rs2ShopType.GENERAL_STORE,
        2.5,
        0.15,
        0.03,
        true,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.KNIFE, ItemID.ROPE,ItemID.PINEAPPLE, ItemID.BANANA,ItemID.ORANGE,ItemID.BUCKET_ECTOPLASM,
            ItemID.GLASSBLOWINGPIPE, ItemID.BUCKET_SAND, ItemID.SEAWEED, ItemID.SODA_ASH,ItemID.LOBSTER_POT,ItemID.FISHING_ROD,
            ItemID.SWAMP_TAR,ItemID.SAIL_TYRAS_HELM,ItemID.RAW_RABBIT,ItemID.EYE_PATCH
        }
    ),
    
    TRADER_STAN_BRIMHAVEN(
        "Trader Stan's Trading Post",
        "Trader Crewmembers",
        new WorldPoint(2760, 3238, 0),
         Rs2ShopType.GENERAL_STORE,
        2.5,
        0.15,
        0.03,
        true,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.KNIFE, ItemID.ROPE,ItemID.PINEAPPLE, ItemID.BANANA,ItemID.ORANGE,ItemID.BUCKET_ECTOPLASM,
            ItemID.GLASSBLOWINGPIPE, ItemID.BUCKET_SAND, ItemID.SEAWEED, ItemID.SODA_ASH,ItemID.LOBSTER_POT,ItemID.FISHING_ROD,
            ItemID.SWAMP_TAR,ItemID.SAIL_TYRAS_HELM,ItemID.RAW_RABBIT,ItemID.EYE_PATCH
        }
    ),
    
    TRADER_STAN_SHIPYARD(
        "Trader Stan's Trading Post",
        "Trader Crewmembers",
        new WorldPoint(3044, 3242, 0),
        Rs2ShopType.GENERAL_STORE,
        2.5,
        0.15,
        0.03,
        true,
        Map.of(),        
        Map.of(),
        Map.of(),
        new int[]{
             ItemID.POT_EMPTY, ItemID.JUG_EMPTY, ItemID.SHEARS, ItemID.BUCKET_EMPTY, ItemID.TINDERBOX,
            ItemID.CHISEL, ItemID.HAMMER, ItemID.KNIFE, ItemID.ROPE,ItemID.PINEAPPLE, ItemID.BANANA,ItemID.ORANGE,ItemID.BUCKET_ECTOPLASM,
            ItemID.GLASSBLOWINGPIPE, ItemID.BUCKET_SAND, ItemID.SEAWEED, ItemID.SODA_ASH,ItemID.LOBSTER_POT,ItemID.FISHING_ROD,
            ItemID.SWAMP_TAR,ItemID.SAIL_TYRAS_HELM,ItemID.RAW_RABBIT,ItemID.EYE_PATCH
        }
    );

    private final String name;
    private final Rs2ShopSource shopSource;
    private final int[] stockItems;

    StoreLocations(String name, String npcName, WorldPoint location,Rs2ShopType shopType, double sellRate, double buyRate, double changePercent, 
                boolean members, Map<Quest, QuestState> questRequirements, 
                Map<Integer,Integer> varbitRequirements, 
                Map<Integer,Integer> varPlayerRequirements,                
                int[] stockItems) {
        this.name = name;
        shopSource = new Rs2ShopSource(
            npcName,
            new WorldArea(location.getX() - 3, location.getY() - 3, 6, 6, location.getPlane()),
            Rs2ShopType.GENERAL_STORE,
            sellRate, // percentageSoldAt 
            buyRate,
            changePercent,
            questRequirements,
            varbitRequirements,
            varPlayerRequirements,
            members,
            name
        );
        
        this.stockItems = stockItems;
    }

    public String getName() {
        return name;
    }

    public String getNpcName() {
        return shopSource.getShopNpcName();
    }

    public WorldPoint getLocation() {
        return shopSource.getLocation();
    }

    public WorldArea getLocationArea() {
        return shopSource.getLocationArea();
    }

    public Rs2ShopType getShopType() {
        return shopSource.getShopType();
    }
    
    public double getSellRate() {
        return shopSource.getPercentageSoldAt();
    }
    public double getBuyRate() {
        return shopSource.getPercentageBoughtAt();
    }

    public double getChangePercent() {
        return shopSource.getChangePercent();
    }

    public boolean isMembers() {
        return shopSource.isMembers();
    }

    public Map<Quest, QuestState> getQuestRequirements() {
        return shopSource.getQuests();
    }

    public String getNotes() {
        return shopSource.getNotes();
    }

    public int[] getStockItems() {
        return stockItems;
    }

    /**
     * Checks if this store stocks the specified item
     * @param itemId The item ID to check for
     * @return true if the store stocks this item, false otherwise
     */
    public boolean hasItem(int itemId) {
        return Arrays.stream(stockItems).anyMatch(id -> id == itemId);
    }

    /**
     * Checks if this store stocks all of the specified items
     * @param itemIds Array of item IDs to check for
     * @return true if the store stocks all items, false otherwise
     */
    public boolean hasAllItems(int... itemIds) {
        return Arrays.stream(itemIds).allMatch(this::hasItem);
    }

    /**
     * Checks if this store stocks any of the specified items
     * @param itemIds Array of item IDs to check for
     * @return true if the store stocks at least one item, false otherwise
     */
    public boolean hasAnyItem(int... itemIds) {
        return Arrays.stream(itemIds).anyMatch(this::hasItem);
    }

    /**
     * Gets the nearest general store to the current player position
     * @return The nearest GeneralStore, or null if player position cannot be determined
     */
    public static StoreLocations getNearestStore() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }

        return Arrays.stream(values())
                .min(Comparator.comparingInt(store -> store.getLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    /**
     * Gets the nearest general store that stocks the specified item
     * @param itemId The item ID to search for
     * @return The nearest GeneralStore that stocks the item, or null if none found or player position cannot be determined
     */
    public static StoreLocations getNearestStoreWithItem(int itemId) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }

        return Arrays.stream(values())
                .filter(store -> store.hasItem(itemId))
                .min(Comparator.comparingInt(store -> store.getLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    /**
     * Gets the nearest general store that stocks all of the specified items
     * @param itemIds Array of item IDs to search for
     * @return The nearest GeneralStore that stocks all items, or null if none found or player position cannot be determined
     */
    public static StoreLocations getNearestStoreWithAllItems(int... itemIds) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }

        return Arrays.stream(values())
                .filter(store -> store.hasAllItems(itemIds))
                .min(Comparator.comparingInt(store -> store.getLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    /**
     * Gets the nearest general store that stocks any of the specified items
     * @param itemIds Array of item IDs to search for
     * @return The nearest GeneralStore that stocks at least one item, or null if none found or player position cannot be determined
     */
    public static StoreLocations getNearestStoreWithAnyItem(int... itemIds) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }

        return Arrays.stream(values())
                .filter(store -> store.hasAnyItem(itemIds))
                .min(Comparator.comparingInt(store -> store.getLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    /**
     * Gets the general store with the highest buy rate that stocks the specified item
     * @param itemId The item ID to search for
     * @return The GeneralStore with the highest buy rate that stocks the item, or null if none found
     */
    public static StoreLocations getHighestBuyRateStoreWithItem(int itemId) {
        return Arrays.stream(values())
                .filter(store -> store.hasItem(itemId))
                .max(Comparator.comparingDouble(StoreLocations::getBuyRate))
                .orElse(null);
    }

    /**
     * Gets the general store with the highest buy rate that stocks all of the specified items
     * @param itemIds Array of item IDs to search for
     * @return The GeneralStore with the highest buy rate that stocks all items, or null if none found
     */
    public static StoreLocations getHighestBuyRateStoreWithAllItems(int... itemIds) {
        return Arrays.stream(values())
                .filter(store -> store.hasAllItems(itemIds))
                .max(Comparator.comparingDouble(StoreLocations::getBuyRate))
                .orElse(null);
    }


    @Override
    public String toString() {
        return name;
    }
}
    