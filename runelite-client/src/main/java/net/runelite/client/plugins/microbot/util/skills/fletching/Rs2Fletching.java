package net.runelite.client.plugins.microbot.util.skills.fletching;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.*;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * General utility for fletching operations
 * Compatible with existing fletching plugin and reusable across plugins
 */
@Slf4j
public class Rs2Fletching {

    private static ScheduledFuture<?> fletchingWhileMovingTask;
    private static boolean isFletchingActive = false;
            
    /**
     * check if player has knife in inventory
     */
    public static boolean hasKnife() {
        return Rs2Inventory.hasItem(ItemID.FLETCHING_KNIFE) || Rs2Inventory.hasItem(ItemID.KNIFE);
    }
    
    /**
     * start fletching interface with knife and material
     * @param materialName the material item name (e.g., "yew logs")
     */
    public static boolean startFletching(String materialName) {
        if (!hasKnife()) {
            log.error("no knife found for fletching");
            return false;
        }
        
        if (!Rs2Inventory.hasItem(materialName)) {
            log.error("material not found for fletching: {}", materialName);
            return false;
        }
        
        // use combineClosest like the existing plugin for consistency
        return Rs2Inventory.combineClosest(Rs2Inventory.hasItem(ItemID.KNIFE) ? "knife" : "fletching knife", materialName);
    }
    
    /**
     * start fletching interface with knife and material ID
     * @param materialId the material item id
     */
    public static boolean startFletching(int materialId) {
        if (!hasKnife()) {
            log.error("no knife found for fletching");
            return false;
        }
              
        if (!Rs2Inventory.hasItem(materialId)) {
            log.error("material not found for fletching: {}", materialId);
            return false;
        }
        
        // get knife item ID for proper ID-based combining
        int knifeId =  Rs2Inventory.hasItem(ItemID.KNIFE) ? 
            ItemID.KNIFE : 
            ItemID.FLETCHING_KNIFE;
            
        return Rs2Inventory.combine(knifeId, materialId);
    }
    
    /**
     * combine two items for fletching (general item combining)
     * @param itemA first item name or ID
     * @param itemB second item name or ID
     */
    public static boolean combineItems(Object itemA, Object itemB) {
        Rs2ItemModel modelA = null;
        Rs2ItemModel modelB = null;
        
        // get item models from inventory
        if (itemA instanceof String) {
            modelA = Rs2Inventory.get((String) itemA);
        } else if (itemA instanceof Integer) {
            modelA = Rs2Inventory.get((int) itemA);
        }
        
        if (itemB instanceof String) {
            modelB = Rs2Inventory.get((String) itemB);
        } else if (itemB instanceof Integer) {
            modelB = Rs2Inventory.get((int) itemB);
        }
        
        if (modelA == null) {
            log.error("first item not found for combining: {}", itemA);
            return false;
        }
        if (modelB == null) {
            log.error("second item not found for combining: {}", itemB);
            return false;
        }
        
        // use Rs2ItemModel combine for proper item handling
        return Rs2Inventory.combine(modelA, modelB);
    }
    
    /**
     * combine two items using names (convenience method)
     */
    public static boolean combineItems(String itemNameA, String itemNameB) {
        return Rs2Inventory.combine(itemNameA, itemNameB);
    }
    
    /**
     * combine two items using IDs (convenience method)
     */
    public static boolean combineItems(int itemIdA, int itemIdB) {
        return Rs2Inventory.combine(itemIdA, itemIdB);
    }
    
    /**
     * wait for fletching interface to open
     */
    public static boolean waitForFletchingInterface() {
        return Rs2Widget.waitForWidget("Make", 5000) || 
               sleepUntil(() -> Rs2Widget.isProductionWidgetOpen(), 5000);
    }
    
    /**
     * select fletching option by name (improved dynamic detection)
     * @param optionName the option name to select
     * @return true if selection was successful
     */
    private static boolean selectFletchingOption(String optionName) {
        return Rs2Widget.handleProcessingInterface( optionName);
    }
    
    /**
     * select fletching option with quantity
     * @param optionName the option name to select
     * @param quantity the quantity (1, 5, 10, 27, or "All")
     * @return true if selection was successful
     */
    private static boolean selectFletchingOption(String optionName, String quantity) {
        if (!waitForFletchingInterface()) {
            log.error("fletching interface did not open");
            return false;
        }
        
        // enable quantity option first
        Rs2Widget.enableQuantityOption( quantity);
        
        // select the specific option
        boolean success = selectFletchingOption(optionName);
        if (success) {
            log.info("selected {} x\"{}\" for fletching", optionName, quantity);
        }
        return success;
    }
    
    /**
     * wait for fletching animation to complete
     * compatible with existing plugin timeout logic
     */
    public static boolean waitForFletchingCompletion(String materialName) {
        return sleepUntil(() -> !Rs2Inventory.hasItem(materialName), 60000);
    }
    
    /**
     * wait for fletching animation to complete with timeout
     * @param timeoutSeconds timeout in seconds
     */
    public static boolean waitForFletchingCompletion(int timeoutSeconds) {
        // wait for fletching to start
        sleepUntil(() -> Rs2Player.isAnimating(), 2000);
        
        // wait for fletching to finish  
        boolean completed = sleepUntil(() -> !Rs2Player.isAnimating(), timeoutSeconds * 1000);
        
        if (completed) {
            log.info("fletching completed");
        } else {
            log.warn("fletching timed out after {} seconds", timeoutSeconds);
        }
        return completed;
    }
    
    /**
     * fletch items using improved interface interaction
     * @param materialName the material name
     * @param itemName the item name to fletch
     * @param quantity the quantity
     */
    public static boolean fletchItems(String materialName, String itemName, String quantity) {
        if (!startFletching(materialName)) return false;
        if (!selectFletchingOption(itemName, quantity)) return false;
        return waitForFletchingCompletion(materialName);
    }
    
    /**
     * fletch items using material ID
     * @param materialId the material item id
     * @param itemName the item name to fletch
     * @param quantity the quantity
     */
    public static boolean fletchItems(int materialId, String itemName, String quantity) {
        if (!startFletching(materialId)) return false;
        if (!selectFletchingOption(itemName, quantity)) return false;
        return waitForFletchingCompletion("All".equals(quantity) ? 60 : 10);
    }
    
    /**
     * string bows with bowstring (compatible with existing plugin)
     * @param bowName the bow name without (u) suffix
     */
    public static boolean stringBows(String bowName) {
        String unstrungName = bowName + " (u)";
        
        if (!Rs2Inventory.hasItem(unstrungName) || !Rs2Inventory.hasItem("bow string")) {
            log.warn("missing materials for stringing: {} or bow string", unstrungName);
            return false;
        }
        
        // use combineClosest like existing plugin
        if (Rs2Inventory.combineClosest("bow string", unstrungName)) {
            // wait for interface and select option
            if (waitForFletchingInterface()) {
                selectFletchingOption("String", "All");
                return waitForFletchingCompletion(unstrungName);
            }
        }
        
        return false;
    }
    
    // === ARROW MAKING METHODS ===
    
    /**
     * make headless arrows from arrow shafts and feathers
     * @param quantity the quantity to make
     */
    public static boolean makeHeadlessArrows(String quantity) {
        if (!combineItems("arrow shaft", "feather")) {
            return false;
        }
        
        return selectFletchingOption("Headless arrow", quantity) && 
               waitForFletchingCompletion("arrow shaft");
    }
    
    /**
     * make headless arrows directly without interface (faster spamming method)
     * @return true if combination was successful
     */
    public static boolean makeHeadlessArrowsFast() {
        if (!Rs2Inventory.hasItem("arrow shaft") || !Rs2Inventory.hasItem("feather")) {
            log.error("missing materials for headless arrow making: arrow shaft or feather");
            return false;
        }
        
        // direct combine without interface - faster for repeated actions
        boolean success = Rs2Inventory.combine("arrow shaft", "feather");
        if (success) {
            log.info("fast headless arrow making");
        }
        return success;
    }
    
    /**
     * make arrows from headless arrows and arrow tips
     * @param arrowTipName the arrow tip name
     * @param quantity the quantity to make
     */
    public static boolean makeArrows(String arrowTipName, String quantity) {
        if (!combineItems("headless arrow", arrowTipName)) {
            return false;
        }
        
        return selectFletchingOption("Arrow", quantity) && 
               waitForFletchingCompletion(arrowTipName);
    }
    
    // === BOLT MAKING METHODS ===
    
    /**
     * make bolts from unfinished bolts and feathers
     * @param unfinishedBoltName the unfinished bolt name
     * @param quantity the quantity to make
     */
    public static boolean makeBolts(String unfinishedBoltName, String quantity) {
        if (!combineItems(unfinishedBoltName, "feather")) {
            return false;
        }
        
        return selectFletchingOption("Bolt", quantity) && 
               waitForFletchingCompletion(unfinishedBoltName);
    }
    
    /**
     * add tips to bolts for enhanced effects
     * @param boltName the bolt name
     * @param boltTipName the bolt tip name
     * @param quantity the quantity to make
     */
    public static boolean addBoltTips(String boltName, String boltTipName, String quantity) {
        if (!combineItems(boltName, boltTipName)) {
            return false;
        }
        
        return selectFletchingOption("Bolt", quantity) && 
               waitForFletchingCompletion(boltTipName);
    }
    
    /**
     * make bolts directly without interface (faster spamming method)
     * @param unfinishedBoltName the unfinished bolt name
     * @return true if combination was successful
     */
    public static boolean makeBoltsFast(String unfinishedBoltName) {
        if (!Rs2Inventory.hasItem(unfinishedBoltName) || !Rs2Inventory.hasItem("feather")) {
            log.error("missing materials for bolt making: {} or feather", unfinishedBoltName);
            return false;
        }
        
        // direct combine without interface - faster for repeated actions
        boolean success = Rs2Inventory.combine(unfinishedBoltName, "feather");
        if (success) {
            log.info("fast bolt making: {}", unfinishedBoltName);
        }
        return success;
    }
    
    /**
     * add bolt tips directly without interface (faster method)
     * @param boltName the bolt name
     * @param boltTipName the bolt tip name
     * @return true if combination was successful
     */
    public static boolean addBoltTipsFast(String boltName, String boltTipName) {
        if (!Rs2Inventory.hasItem(boltName) || !Rs2Inventory.hasItem(boltTipName)) {
            log.error("missing materials for bolt tip adding: {} or {}", boltName, boltTipName);
            return false;
        }
        
        // direct combine without interface - faster for repeated actions
        boolean success = Rs2Inventory.combine(boltName, boltTipName);
        if (success) {
            log.info("fast bolt tip adding: {} + {}", boltName, boltTipName);
        }
        return success;
    }
    
    // === DART MAKING METHODS ===
    
    /**
     * make darts from dart tips and feathers
     * @param dartTipName the dart tip name
     * @param quantity the quantity to make
     */
    public static boolean makeDarts(String dartTipName, String quantity) {
        if (!combineItems(dartTipName, "feather")) {
            return false;
        }
        
        return selectFletchingOption("Dart", quantity) && 
               waitForFletchingCompletion(dartTipName);
    }
    
    /**
     * make darts directly without interface (faster spamming method)
     * @param dartTipName the dart tip name
     * @return true if combination was successful
     */
    public static boolean makeDartsFast(String dartTipName) {
        if (!Rs2Inventory.hasItem(dartTipName) || !Rs2Inventory.hasItem("feather")) {
            log.error("missing materials for dart making: {} or feather", dartTipName);
            return false;
        }
        
        // direct combine without interface - faster for repeated actions
        boolean success = Rs2Inventory.combine(dartTipName, "feather");
        if (success) {
            log.info("fast dart making: {}", dartTipName);
        }
        return success;
    }
    
    /**
     * make darts directly using IDs (fastest method)
     * @param dartTipId the dart tip item id
     * @return true if combination was successful
     */
    public static boolean makeDartsFast(int dartTipId) {
        if (!Rs2Inventory.hasItem(dartTipId) || !Rs2Inventory.hasItem("feather")) {
            log.error("missing materials for dart making: {} or feather", dartTipId);
            return false;
        }
        
        // direct combine using IDs - fastest method
        int featherId = Rs2Inventory.get("feather").getId();
        boolean success = Rs2Inventory.combine(dartTipId, featherId);
        if (success) {
            log.info("fast dart making with id: {}", dartTipId);
        }
        return success;
    }
    
    /**
     * drop fletched items by name
     */
    public static void dropFletchedItems(String itemName) {
        Rs2Inventory.dropAll(rsitem -> rsitem.getName().contains(itemName), InteractOrder.ZIGZAG);
        log.info("dropped fletched items: {}", itemName);
    }
    
    // === FLETCHING WHILE MOVING METHODS ===
    
    /**
     * fletch logs while moving (optimization for woodcutting travel time)
     * @param materialName the material name
     * @param itemName the item name to fletch
     * @param maxItems maximum items to fletch
     * @param executor the scheduler to use
     */
    public static void startFletchingWhileMoving(String materialName, String itemName, int maxItems, ScheduledExecutorService executor) {
        if (fletchingWhileMovingTask != null || isFletchingActive) {
            log.warn("fletching while moving already active");
            return;
        }
        
        if (!hasKnife() || !Rs2Inventory.hasItem(materialName)) {
            log.warn("insufficient materials for fletching while moving");
            return;
        }
        
        isFletchingActive = true;
        
        fletchingWhileMovingTask = executor.scheduleWithFixedDelay(() -> {
            try {
                // only fletch while moving and not animating
                if (Rs2Player.isMoving() && !Rs2Player.isAnimating() && hasKnife() && 
                    Rs2Inventory.hasItem(materialName)) {
                    
                    if (startFletching(materialName)) {
                        selectFletchingOption(itemName, "5");
                        log.info("started fletching {} while moving", itemName);
                    }
                }
                
                // stop if no more materials or not moving
                if (!Rs2Inventory.hasItem(materialName) || !Rs2Player.isMoving()) {
                    stopFletchingWhileMoving();
                }
                
            } catch (Exception ex) {
                log.error("error during fletching while moving: {}", ex.getMessage());
                stopFletchingWhileMoving();
            }
        }, 0, 1800, TimeUnit.MILLISECONDS);
        
        log.info("started fletching while moving task");
    }
    
    /**
     * make headless arrows while moving (efficient for ironmen)
     * @param executor the scheduler to use
     */
    public static void startArrowFletchingWhileMoving(ScheduledExecutorService executor) {
        if (fletchingWhileMovingTask != null || isFletchingActive) {
            log.warn("fletching while moving already active");
            return;
        }
        
        if (!Rs2Inventory.hasItem("arrow shaft") || !Rs2Inventory.hasItem("feather")) {
            log.warn("insufficient materials for arrow fletching while moving");
            return;
        }
        
        isFletchingActive = true;
        
        fletchingWhileMovingTask = executor.scheduleWithFixedDelay(() -> {
            try {
                // only fletch while moving and not animating
                if (Rs2Player.isMoving() && !Rs2Player.isAnimating() && 
                    Rs2Inventory.hasItem("arrow shaft") && Rs2Inventory.hasItem("feather")) {
                    
                    if (combineItems("arrow shaft", "feather")) {
                        selectFletchingOption("Headless arrow", "5");
                        log.info("started arrow fletching while moving");
                    }
                }
                
                // stop if no more materials or not moving
                if (!Rs2Inventory.hasItem("arrow shaft") || !Rs2Inventory.hasItem("feather") || !Rs2Player.isMoving()) {
                    stopFletchingWhileMoving();
                }
                
            } catch (Exception ex) {
                log.error("error during arrow fletching while moving: {}", ex.getMessage());
                stopFletchingWhileMoving();
            }
        }, 0, 1800, TimeUnit.MILLISECONDS);
        
        log.info("started arrow fletching while moving task");
    }
    
    /**
     * make darts while moving (efficient for training)
     * @param dartTipName the dart tip name
     * @param executor the scheduler to use
     */
    public static void startDartFletchingWhileMoving(String dartTipName, ScheduledExecutorService executor) {
        if (fletchingWhileMovingTask != null || isFletchingActive) {
            log.warn("fletching while moving already active");
            return;
        }
        
        if (!Rs2Inventory.hasItem(dartTipName) || !Rs2Inventory.hasItem("feather")) {
            log.warn("insufficient materials for dart fletching while moving");
            return;
        }
        
        isFletchingActive = true;
        
        fletchingWhileMovingTask = executor.scheduleWithFixedDelay(() -> {
            try {
                // only fletch while moving and not animating
                if (Rs2Player.isMoving() && !Rs2Player.isAnimating() && 
                    Rs2Inventory.hasItem(dartTipName) && Rs2Inventory.hasItem("feather")) {
                    
                    if (combineItems(dartTipName, "feather")) {
                        selectFletchingOption("Dart", "5");
                        log.info("started dart fletching while moving: {}", dartTipName);
                    }
                }
                
                // stop if no more materials or not moving
                if (!Rs2Inventory.hasItem(dartTipName) || !Rs2Inventory.hasItem("feather") || !Rs2Player.isMoving()) {
                    stopFletchingWhileMoving();
                }
                
            } catch (Exception ex) {
                log.error("error during dart fletching while moving: {}", ex.getMessage());
                stopFletchingWhileMoving();
            }
        }, 0, 1800, TimeUnit.MILLISECONDS);
        
        log.info("started dart fletching while moving task");
    }
    
    /**
     * stop fletching while moving
     */
    public static void stopFletchingWhileMoving() {
        isFletchingActive = false;
        if (fletchingWhileMovingTask != null) {
            fletchingWhileMovingTask.cancel(false);
            fletchingWhileMovingTask = null;
            log.info("stopped fletching while moving");
        }
    }
    
    /**
     * check if fletching interface is open
     */
    public static boolean isFletchingInterfaceOpen() {
        return Rs2Widget.isProductionWidgetOpen()|| 
               Rs2Widget.hasVisibleWidgetText("Make");
    }
    
    /**
     * get fletching status for overlay/debugging
     */
    public static String getFletchingStatus() {
        if (isFletchingActive) {
            return "Fletching: Active (while moving)";
        }
        
        if (Rs2Player.isAnimating() && isFletchingInterfaceOpen()) {
            return "Fletching: Animating";
        }
        
        return "Fletching: Idle";
    }
    
    // === DYNAMIC FLETCHING METHODS ===
    
    /**
     * automatically detects available materials and crafts accordingly using enums
     * @param quantity the quantity to make
     */
    public static boolean autoFletchAvailable(String quantity) {
        // priority: darts > arrows > bolts > logs (efficiency/profit order)
        
        // check for dart materials (highest priority - fast xp and profit)
        for (FletchingDart dart : FletchingDart.values()) {
            if (dart.meetsLevelRequirement() && 
                Rs2Inventory.hasItem(dart.getDartTipName()) && 
                Rs2Inventory.hasItem("feather")) {
                log.info("auto-fletching darts: {}", dart.getDartTipName());
                return makeDarts(dart.getDartTipName(), quantity);
            }
        }
        
        // check for arrow materials (headless arrows + tips)
        for (FletchingArrow arrow : FletchingArrow.values()) {
            if (arrow.meetsLevelRequirement() && 
                Rs2Inventory.hasItem(arrow.getHeadlessArrowName()) && 
                Rs2Inventory.hasItem(arrow.getArrowTipName())) {
                log.info("auto-fletching arrows: {}", arrow.getArrowTipName());
                return makeArrows(arrow.getArrowTipName(), quantity);
            }
        }
        
        // check for headless arrow materials (arrow shafts + feathers)
        if (Rs2Inventory.hasItem("arrow shaft") && Rs2Inventory.hasItem("feather")) {
            log.info("auto-fletching headless arrows");
            return makeHeadlessArrows(quantity);
        }
        
        // check for bolt materials (unfinished bolts + feathers)
        for (FletchingBolt bolt : FletchingBolt.values()) {
            if (bolt.meetsLevelRequirement() && 
                Rs2Inventory.hasItem(bolt.getUnfinishedBoltName()) && 
                Rs2Inventory.hasItem("feather")) {
                log.info("auto-fletching bolts: {}", bolt.getUnfinishedBoltName());
                return makeBolts(bolt.getUnfinishedBoltName(), quantity);
            }
        }
        
        // check for bolt tip attachment (finished bolts + bolt tips)
        for (FletchingBolt bolt : FletchingBolt.values()) {
            if (bolt.supportsTips() && bolt.meetsLevelRequirement() && 
                Rs2Inventory.hasItem(bolt.getFinishedBoltName()) && 
                Rs2Inventory.hasItem(bolt.getBoltTipName())) {
                log.info("auto-fletching bolt tips: {} + {}", bolt.getFinishedBoltName(), bolt.getBoltTipName());
                return addBoltTips(bolt.getFinishedBoltName(), bolt.getBoltTipName(), quantity);
            }
        }
        
        // check for log fletching materials (knife + logs)
        if (hasKnife()) {
            // check in reverse order for better materials first
            FletchingMaterial[] materials = {FletchingMaterial.MAGIC, FletchingMaterial.YEW, 
                                           FletchingMaterial.MAPLE, FletchingMaterial.WILLOW, 
                                           FletchingMaterial.OAK, FletchingMaterial.WOOD};
            
            for (FletchingMaterial material : materials) {
                String logName = material.getName().toLowerCase() + " logs";
                if (material == FletchingMaterial.WOOD) {
                    logName = "logs"; // regular logs don't have "wood" prefix
                }
                
                if (Rs2Inventory.hasItem(logName)) {
                    // determine best item to make based on GE value
                    FletchingItem bestItem = getBestFletchingItemByValue(material, logName);
                    String itemName = getFletchingItemDisplayName(bestItem, material);
                    log.info("auto-fletching logs: {} -> {} (selected by GE value)", logName, itemName);
                    return fletchItems(logName, itemName, quantity);
                }
            }
        }
        
        // check for bow stringing (unstrung bows + bow string)
        if (Rs2Inventory.hasItem("bow string")) {
            String[] bowTypes = {"magic longbow (u)", "yew longbow (u)", "maple longbow (u)", 
                               "willow longbow (u)", "oak longbow (u)", "longbow (u)",
                               "magic shortbow (u)", "yew shortbow (u)", "maple shortbow (u)", 
                               "willow shortbow (u)", "oak shortbow (u)", "shortbow (u)"};
            
            for (String bowType : bowTypes) {
                if (Rs2Inventory.hasItem(bowType)) {
                    String finishedBow = bowType.replace(" (u)", "");
                    log.info("auto-stringing bow: {} -> {}", bowType, finishedBow);
                    return stringBows(finishedBow);
                }
            }
        }
        
        log.warn("no fletching materials found in inventory");
        return false;
    }
    
    /**
     * determines the best fletching item based on GE value using Rs2ItemModel
     */
    private static FletchingItem getBestFletchingItemByValue(FletchingMaterial material, String logName) {
        Rs2ItemModel logItem = Rs2Inventory.get(logName);
        if (logItem == null) {
            return FletchingItem.SHORT; // fallback
        }
        
        // get potential fletching options for this material
        FletchingItem[] options = {FletchingItem.SHORT, FletchingItem.LONG, FletchingItem.ARROW_SHAFT};
        FletchingItem bestItem = FletchingItem.SHORT;
        int bestValue = 0;
        
        for (FletchingItem item : options) {
            String resultItemName = getPotentialFletchingResult(material, item);
            if (resultItemName != null) {
                // try to get GE value for the resulting item
                int geValue = getEstimatedGEValue(resultItemName);
                if (geValue > bestValue) {
                    bestValue = geValue;
                    bestItem = item;
                }
            }
        }
        
        log.debug("selected {} for {} (estimated value: {})", bestItem, logName, bestValue);
        return bestItem;
    }
    
    /**
     * gets the potential fletching result item name
     */
    private static String getPotentialFletchingResult(FletchingMaterial material, FletchingItem item) {
        String materialPrefix = material.getName().toLowerCase();
        if (material == FletchingMaterial.WOOD) {
            materialPrefix = ""; // regular wood has no prefix
        } else if (!materialPrefix.isEmpty()) {
            materialPrefix += " ";
        }
        
        switch (item) {
            case SHORT:
                return materialPrefix + "shortbow (u)";
            case LONG:
                return materialPrefix + "longbow (u)";
            case ARROW_SHAFT:
                return "arrow shaft";
            case STOCK:
                return materialPrefix + "stock";
            case SHIELD:
                return materialPrefix + "shield";
            default:
                return null;
        }
    }
    
    /**
     * gets GE value using live data from Rs2GrandExchange, comparing with high alch value
     * uses whichever is higher for better profit calculation
     */
    private static int getEstimatedGEValue(String itemName) {
        try {
            // try to get item ID from inventory first
            int itemId = -1;
            Rs2ItemModel itemModel = Rs2Inventory.get(itemName);
            if (itemModel != null) {
                itemId = itemModel.getId();
            } else {
                // try to find by name lookup
                itemId = findItemIdByName(itemName);
            }
            
            if (itemId > 0) {
                // get live GE price
                int livePrice = Rs2GrandExchange.getPrice(itemId);
                
                // get high alch value from item mapping
                int highAlchValue = 0;
                ItemMappingData mappingData = Rs2GrandExchange.getItemMappingData(itemId);
                if (mappingData != null) {
                    highAlchValue = mappingData.highAlch;
                }
                
                // use whichever is higher
                int bestValue = Math.max(livePrice, highAlchValue);
                if (bestValue > 0) {
                    log.debug("item: {}, live GE: {}, high alch: {}, using: {}", 
                             itemName, livePrice, highAlchValue, bestValue);
                    return bestValue;
                }
            }
        } catch (Exception e) {
            log.debug("failed to get live GE data for {}: {}", itemName, e.getMessage());
        }
        
        return 0; // return 0 if no data available
    }
    
    /**
     * attempts to find item ID by name (common fletching items)
     */
    private static int findItemIdByName(String itemName) {
        itemName = itemName.toLowerCase();  
        if (itemName.contains("magic longbow")) return ItemID.MAGIC_LONGBOW;
        if (itemName.contains("yew longbow")) return ItemID.YEW_LONGBOW;
        if (itemName.contains("maple longbow")) return ItemID.MAPLE_LONGBOW;
        if (itemName.contains("willow longbow")) return ItemID.WILLOW_LONGBOW;
        if (itemName.contains("oak longbow")) return ItemID.OAK_LONGBOW;
        if (itemName.contains("longbow")) return ItemID.LONGBOW;
        
        if (itemName.contains("magic shortbow")) return ItemID.MAGIC_SHORTBOW;
        if (itemName.contains("yew shortbow")) return ItemID.YEW_SHORTBOW;
        if (itemName.contains("maple shortbow")) return ItemID.MAPLE_SHORTBOW;
        if (itemName.contains("willow shortbow")) return ItemID.WILLOW_SHORTBOW;
        if (itemName.contains("oak shortbow")) return ItemID.OAK_SHORTBOW;
        if (itemName.contains("shortbow")) return ItemID.SHORTBOW;
        
        if (itemName.contains("arrow shaft")) return ItemID.ARROW_SHAFT;
        
        return -1; // not found
    }
    
    /**
     * gets the display name for fletching interface
     */
    private static String getFletchingItemDisplayName(FletchingItem item, FletchingMaterial material) {
        switch (item) {
            case SHORT:
                return "shortbow";
            case LONG:
                return "longbow";
            case ARROW_SHAFT:
                return "arrow shaft";
            case STOCK:
                return "stock";
            case SHIELD:
                return "shield";
            default:
                return "shortbow";
        }
    }
    
}