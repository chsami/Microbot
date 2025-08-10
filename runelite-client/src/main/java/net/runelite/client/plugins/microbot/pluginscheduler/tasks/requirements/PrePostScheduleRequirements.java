package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry.RequirementRegistry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.OrderedRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.LogicalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util.RequirementSolver;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util.RequirementSelector;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.FulfillmentStep;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;

import java.util.stream.Collectors;



/**
 * Enhanced collection that manages ItemRequirement objects with support for inventory and equipment requirements.
 * 
 * This class now uses a centralized RequirementRegistry for improved consistency, uniqueness enforcement,
 * and simplified requirement management while maintaining backward compatibility.
 */
@Slf4j
public abstract class PrePostScheduleRequirements  {    
  
    
    @Getter
    private final String collectionName;
    @Getter
    private final String activityType;
    
    // Centralized requirement management
    private final RequirementRegistry registry = new RequirementRegistry();
    
    /**
     * Tracks the original spellbook before switching for pre-schedule requirements.
     * This is used to restore the original spellbook during post-schedule fulfillment.
     */
    private volatile Rs2Spellbook originalSpellbook;
    
    // Centralized state tracking for overlay display
    @Getter
    private final TaskExecutionState executionState = new TaskExecutionState();
    
    @Getter
    private final boolean isWildernessCollection;
    
    public PrePostScheduleRequirements() {
        this("", "", false);
    }
    
    /**
     * Creates a new collection with name and activity type for better organization.
     * 
     * @param collectionName A descriptive name for this collection
     * @param activityType The type of activity these requirements are for (e.g., "GOTR", "Mining", "Combat")
     */
    public PrePostScheduleRequirements(String collectionName, String activityType, boolean isWildernessCollection) {
        this.collectionName = collectionName;
        this.activityType = activityType;
        this.isWildernessCollection = isWildernessCollection; // Set wilderness flag
        initializeRequirements();
    }
    protected abstract void initializeRequirements();


    /**
     * Gets access to the internal requirement registry.
     * This is useful for overlay components that need to access requirements by type and context.
     * 
     * @return The requirement registry
     */
    public RequirementRegistry getRegistry() {
        return registry;
    }
    
    /**
     * Gets all mandatory equipment items organized by slot.
     * 
     * @return A map of equipment slot to list of mandatory equipment requirements
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> getEquipmentItemsBySlot(Priority priority) {
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> mandatoryEquipment = new HashMap<>();
        
        // Get equipment requirements from slot-based cache
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentSlotRequirements = registry.getEquipmentSlotItems();
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> entry : equipmentSlotRequirements.entrySet()) {
            LinkedHashSet<ItemRequirement> mandatorySlotItems = entry.getValue().stream()
                    .filter(item -> item.getPriority() == priority)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            
            if (!mandatorySlotItems.isEmpty()) {
                mandatoryEquipment.put(entry.getKey(), mandatorySlotItems);
            }
        }
        
        return mandatoryEquipment;
    }
    
    /**
     * Gets all mandatory inventory items (excluding equipment items).
     */
    public LinkedHashSet<ItemRequirement> getInventoryItems(Priority priority) {
        LinkedHashSet<ItemRequirement> mandatoryInventory = new LinkedHashSet<>();
        
        // Get inventory items from slot-based cache (slot -1 represents "any slot")
        LinkedHashSet<ItemRequirement> inventoryItems = registry.getInventorySlotItems();
        mandatoryInventory.addAll(inventoryItems.stream()
                .filter(item -> item.getPriority() == priority)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        
        return mandatoryInventory;
    }
   
    /**
     * Validates that all mandatory items are available for this collection.
     * Checks both equipment (at least one per required slot) and inventory items.
     * 
     * @return true if all mandatory requirements are met, false otherwise
     */
    public boolean validateItems(Priority priority) {
        PrePostScheduleRequirements collection = this; // Use the current collection           
        
        // Check mandatory equipment items by slot
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> mandatoryEquipmentBySlot = collection.getEquipmentItemsBySlot(priority);
        
        // For each slot with mandatory items, check if we have at least one item for that slot
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> entry : mandatoryEquipmentBySlot.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            LinkedHashSet<ItemRequirement> slotItems = entry.getValue();
            
            boolean hasSlotItem = false;
            
            // Check if we have any variant of any mandatory item for this slot
            for (ItemRequirement item : slotItems) {
                Integer itemId = item.getId();
                if (Rs2Inventory.hasItem(itemId) || Rs2Equipment.isWearing(itemId) || Rs2Bank.hasItem(itemId)) {
                    hasSlotItem = true;
                    break;
                }
            }
            
            if (!hasSlotItem) {
                Microbot.log("Missing mandatory equipment for slot: " + slot);
                return false;
            }
        }
        
        // Check mandatory inventory items
        LinkedHashSet<ItemRequirement> mandatoryInventoryItems = collection.getInventoryItems(priority);
        
        for (ItemRequirement item : mandatoryInventoryItems) {
            // Check if we have this mandatory inventory item
            Integer itemId = item.getId();
            if (!(Rs2Inventory.hasItem(itemId) || Rs2Bank.hasItem(itemId))) {
                Microbot.log("Missing mandatory inventory item: " + itemId);
                return false;
            }
        }
        
        return true;
    }

    /**
     * Sorts a LinkedHashSet of items by rating (highest first), then by priority level.
     * Since LinkedHashSet doesn't have a sort method, we convert to list, sort, and recreate.
     */
    private LinkedHashSet<ItemRequirement> sortItemList(LinkedHashSet<ItemRequirement> items) {
        List<ItemRequirement> sortedList = new ArrayList<>(items);
        sortedList.sort((a, b) -> {
            // First sort by priority (MANDATORY > RECOMMENDED > OPTIONAL)
            int priorityCompare = a.getPriority().compareTo(b.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // Then by rating (highest first)
            return Integer.compare(b.getRating(), a.getRating());
        });
        
        LinkedHashSet<ItemRequirement> sortedSet = new LinkedHashSet<>(sortedList);
        items.clear();
        items.addAll(sortedSet);
        return items;
    }
    
    /**
     * Gets all items for a specific equipment slot, sorted by effectiveness.
     */
    public LinkedHashSet<ItemRequirement> getItemsForSlot(EquipmentInventorySlot slot) {
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = registry.getEquipmentSlotItems();
        return equipmentItems.getOrDefault(slot, new LinkedHashSet<>());
    }
    
    /**
     * Gets all equipment items (both dedicated equipment and "either" items) by slot.
     * 
     * @return A map of equipment slot to list of all equipment requirements
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> getAllEquipmentItemsBySlot() {
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> allEquipmentItems = new HashMap<>();
        
        // Get all equipment items from slot-based cache (already includes EITHER items)
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = registry.getEquipmentSlotItems();
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> entry : equipmentItems.entrySet()) {
            allEquipmentItems.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        
        // Sort each list by priority and rating
        for (LinkedHashSet<ItemRequirement> items : allEquipmentItems.values()) {
            sortItemList(items);
        }
        
        return allEquipmentItems;
    }
    
    /**
     * Gets the most effective item for a specific equipment slot.
     */
    public Optional<ItemRequirement> getBestItemForSlot(EquipmentInventorySlot slot) {
        LinkedHashSet<ItemRequirement> items = getItemsForSlot(slot);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.iterator().next());
    }
    
    /**
     * Gets all inventory-only items.
     */
    public LinkedHashSet<ItemRequirement> getInventoryItems() {
        // Get all inventory items from slot-based cache (slot -1 represents "any slot")
        return new LinkedHashSet<>(registry.getInventorySlotItems());
    }
    
   
    
    /**
     * Merges another collection into this one.
     */
    public void merge(PrePostScheduleRequirements other) {
        // Merge all requirements through the registry
        for (Requirement requirement : other.registry.getAllRequirements()) {
            registry.register(requirement);
        }
    }
     /**
     * Gets all items that can be either equipped or in inventory.
     */
    public LinkedHashSet<ItemRequirement> getEitherItems() {
        return new LinkedHashSet<>(registry.getAllEitherItems());
    }
 
    
   
       
    /**
     * Gets all equipment slots that have recommended items.
     */
    public Set<EquipmentInventorySlot> getEquipmentSlots() {
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = registry.getEquipmentSlotItems();
        return equipmentItems.keySet();
    }
     /**
     * Gets all pritems across all categories.
     */
    public LinkedHashSet<ItemRequirement> getItems(Priority priority) {
        LinkedHashSet<ItemRequirement> recommended = new LinkedHashSet<>();
        
        // Add recommended inventory items
        recommended.addAll(getInventoryItems(priority));
        
        // Add one item from each recommended equipment slot
        for (LinkedHashSet<ItemRequirement> slotItems : getEquipmentItemsBySlot(priority).values()) {
            if (!slotItems.isEmpty()) {
                recommended.add(slotItems.iterator().next());
            }
        }
        
        return recommended;
    }
    
    /**
     * Gets total count of all items in the collection.
     */
    public int getTotalItemCount() {
        LinkedHashSet<ItemRequirement> inventoryItems = registry.getInventorySlotItems();
        int count = inventoryItems.size();
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = registry.getEquipmentSlotItems();
        for (LinkedHashSet<ItemRequirement> slotItems : equipmentItems.values()) {
            count += slotItems.size();
        }
        return count;
    }
    
    /**
     * Gets all equipment recommendations organized by slot.
     * Provides compatibility with the old RecommendEquipmentCollection API.
     * 
     * @return A map of equipment slot to list of requirements
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> getEquipmentRequirements() {
        // Get all equipment items from slot-based cache (already includes EITHER items)
        return new HashMap<>(registry.getEquipmentSlotItems());
    }
    
    
    
  
    
    /**
     /**
     * *****************************************************
     * Location req. related section
     * **************************************************************
     * 
     */

  
    
    /**
     * Attempts to fulfill the pre-schedule location requirement.
     * This method should be called before starting the script.
     * 
     * @return true if positioning was successful or no requirement exists, false otherwise
     */
    public boolean fulfillPreScheduleLocationRequirement() {
        LocationRequirement preScheduleLocationRequirement = registry.getPreScheduleLocationRequirement();
        if (preScheduleLocationRequirement == null) {
            log.debug("No pre-schedule location requirement to fulfill");
            return true; // No pre-schedule location requirement
        }
        
        // Update state tracking for this specific requirement
        updateFulfillmentStep(FulfillmentStep.LOCATION, "Traveling to " + preScheduleLocationRequirement.getName(), 1);
        updateCurrentRequirement(preScheduleLocationRequirement, 1);
        log.debug("Processing pre-schedule location requirement: {}", preScheduleLocationRequirement.getName());
        
        return preScheduleLocationRequirement.fulfillRequirement();
    }
    
    /**
     * Attempts to fulfill the post-schedule location requirement.
     * This method should be called after the script completes.
     * 
     * @return true if positioning was successful or no requirement exists, false otherwise
     */
    public boolean fulfillPostScheduleLocationRequirement() {
        LocationRequirement postScheduleLocationRequirement = registry.getPostScheduleLocationRequirement();
        if (postScheduleLocationRequirement == null) {
            log.debug("No post-schedule location requirement to fulfill");
            return true; // No post-schedule location requirement
        }
        
        // Update state tracking for this specific requirement
        updateFulfillmentStep(FulfillmentStep.LOCATION, "Traveling to " + postScheduleLocationRequirement.getName(), 1);
        updateCurrentRequirement(postScheduleLocationRequirement, 1);
        log.debug("Processing post-schedule location requirement: {}", postScheduleLocationRequirement.getName());
        
        return postScheduleLocationRequirement.fulfillRequirement();
    }

    /***************
     * *************************+
     * Spellbook req. related section
     * ****************
     * 
     */
         
    /**
     * Attempts to fulfill the pre-schedule spellbook requirement.
     * This method should be called before starting the script.
     * 
     * @return true if spellbook switching was successful or no requirement exists, false otherwise
     */
    public boolean fulfillPreScheduleSpellbookRequirement() {
        SpellbookRequirement preScheduleSpellbookRequirement = registry.getPreScheduleSpellbookRequirement();
        if (preScheduleSpellbookRequirement == null) {
            log.debug("No pre-schedule spellbook requirement to fulfill");
            return true; // No pre-schedule spellbook requirement
        }
         if (Microbot.getClient().isClientThread()) {
            Microbot.log("Please run fulfillPreScheduleSpellbookRequirement() on a non-client thread.", Level.ERROR);
            return false;
        }
        
        // Update state tracking for this specific requirement
        updateFulfillmentStep(FulfillmentStep.SPELLBOOK, "Switching to " + preScheduleSpellbookRequirement.getRequiredSpellbook(), 1);
        updateCurrentRequirement(preScheduleSpellbookRequirement, 1);
        log.debug("Processing pre-schedule spellbook requirement: {}", preScheduleSpellbookRequirement.getName());
        
        return preScheduleSpellbookRequirement.fulfillRequirement();
    }
    
    /**
     * Attempts to fulfill the post-schedule spellbook requirement.
     * This method should be called after the script completes.
     * 
     * @return true if spellbook switching was successful or no requirement exists, false otherwise
     */
    public boolean fulfillPostScheduleSpellbookRequirement() {
        SpellbookRequirement postScheduleSpellbookRequirement = registry.getPostScheduleSpellbookRequirement();
        if (postScheduleSpellbookRequirement == null) {
            log.debug("No post-schedule spellbook requirement to fulfill");
            return true; // No post-schedule spellbook requirement
        }
        if (Microbot.getClient().isClientThread()) {
            Microbot.log("Please run fulfillPostScheduleSpellbookRequirement() on a non-client thread.", Level.ERROR);
            return false;
        }
        
        // Update state tracking for this specific requirement
        updateFulfillmentStep(FulfillmentStep.SPELLBOOK, "Switching to " + postScheduleSpellbookRequirement.getRequiredSpellbook(), 1);
        updateCurrentRequirement(postScheduleSpellbookRequirement, 1);
        log.debug("Processing post-schedule spellbook requirement: {}", postScheduleSpellbookRequirement.getName());
        
        return postScheduleSpellbookRequirement.fulfillRequirement();
    }   
    /**
     * Switches back to the original spellbook that was active before pre-schedule requirements.
     * This method should be called after completing activities that required a specific spellbook.
     * 
     * @return true if switch was successful or no switch was needed, false if switch failed
     */
    public boolean switchBackToOriginalSpellbook() {
        if (originalSpellbook == null) {
            Microbot.log("No original spellbook saved - no switch needed");
            return true; // No original spellbook saved, so no switch needed
        }
        
        Rs2Spellbook currentSpellbook = Rs2Spellbook.getCurrentSpellbook();
        if (currentSpellbook == originalSpellbook) {
            Microbot.log("Already on original spellbook: " + originalSpellbook);
            return true; // Already on the original spellbook
        }
        
        Microbot.log("Switching back to original spellbook: " + originalSpellbook + " from current: " + currentSpellbook);
        boolean success = SpellbookRequirement.switchBackToSpellbook(originalSpellbook);
        
        if (success) {
            // Clear the saved spellbook after successful restoration
            originalSpellbook = null;
            Microbot.log("Successfully restored original spellbook");
        } else {
            Microbot.log("Failed to restore original spellbook: " + originalSpellbook, Level.ERROR);
        }
        
        return success;
    }
    
    /**
     * Gets the original spellbook that was saved before pre-schedule requirements.
     * 
     * @return The original spellbook, or null if none was saved
     */
    public Rs2Spellbook getOriginalSpellbook() {
        return originalSpellbook;
    }
    
    /**
     * Checks if an original spellbook is currently saved.
     * 
     * @return true if an original spellbook is saved, false otherwise
     */
    public boolean hasOriginalSpellbook() {
        return originalSpellbook != null;
    }
    
    /**
     * Clears the saved original spellbook. This can be useful for cleanup
     * or when you want to start fresh without restoring the previous spellbook.
     */
    public void clearOriginalSpellbook() {
        if (originalSpellbook != null) {
            Microbot.log("Clearing saved original spellbook: " + originalSpellbook);
            originalSpellbook = null;
        }
    }

           
    /**
     * Registers a requirement in the central registry.
     * The registry automatically handles categorization, uniqueness, and consistency.
     * 
     * @param requirement The requirement to register
     */
    public void register(Requirement requirement) {
        if (requirement == null) {
            return;
        }
        if(registry.contains(requirement)) {
            Microbot.log("Requirement already registered: " + requirement.getName(), Level.WARN);
            return; // Avoid duplicate registration
        }
        registry.register(requirement);
    }
    
    /**
     * Gets all requirements that match the specified schedule context.
     * 
     * @param context The schedule context to filter by
     * @return List of requirements matching the context
     */
    public List<Requirement> getRequirementsByScheduleContext(ScheduleContext context) {
        return registry.getRequirements(context);
    }
    
    /**
     * Gets all requirements that should be fulfilled before script execution.
     * 
     * @return List of pre-schedule requirements
     */
    public List<Requirement> getPreScheduleRequirements() {
        return registry.getRequirements(ScheduleContext.PRE_SCHEDULE);
    }
    
    /**
     * Gets all requirements that should be fulfilled after script completion.
     * 
     * @return List of post-schedule requirements
     */
    public List<Requirement> getPostScheduleRequirements() {
        return registry.getRequirements(ScheduleContext.POST_SCHEDULE);
    }
    
    /**
     * Gets all requirements of a specific type for a specific schedule context.
     * 
     * @param <T> The requirement type
     * @param clazz The class type to filter by
     * @param context The schedule context to filter by
     * @return List of requirements matching both type and context
     */
    public <T extends Requirement> List<T> getRequirements(Class<T> clazz, ScheduleContext context) {
        return registry.getRequirements(clazz, context);
    }
    
    /**
     * Gets all requirements of a specific type.
     * 
     * @param <T> The requirement type
     * @param clazz The class type to filter by
     * @return List of requirements matching the type
     */
    public <T extends Requirement> List<T> getRequirements(Class<T> clazz) {
        return registry.getRequirements(clazz);
    }
    
    /**
     * Gets all requirements for a specific schedule context.
     * 
     * @param context The schedule context to filter by
     * @return List of requirements matching the context
     */
    @SuppressWarnings("unchecked")
    public <T extends Requirement> List<T> getRequirements(ScheduleContext context) {
        return (List<T>) registry.getRequirements(context);
    }
    
    
    /**
     * Fulfills all requirements for the specified schedule context.
     * This is a convenience method that calls all the specific fulfillment methods.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @param saveCurrentSpellbook Whether to save the current spellbook for restoration
     * @return true if all requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillAllRequirements(ScheduleContext context, ScheduledExecutorService executorService, boolean saveCurrentSpellbook) {
        boolean success = true;
        
        // Fulfill requirements in logical order -> we should always fulfill loot requirements first, then shop, then item, then spellbook, and finally location requirements
        // when adding new requirements, make sure to follow this order or think about the order in which they should be fulfilled
        // we cann also think about chaning the order for pre and post schedule requirements, but for now we will keep it the same
        if (context == null) {
            Microbot.log("Context cannot be null", Level.ERROR);
            executionState.markError("Context cannot be null");
            return false;
        }
        if (Microbot.getClient().isClientThread()) {
            Microbot.log("Please run fulfillAllRequirements() on a non-client thread.", Level.ERROR);
            executionState.markError("Cannot run on client thread");
            return false;
        }

        // Initialize state tracking
        TaskExecutionState.ExecutionPhase phase = context == ScheduleContext.PRE_SCHEDULE ? 
            TaskExecutionState.ExecutionPhase.PRE_SCHEDULE : TaskExecutionState.ExecutionPhase.POST_SCHEDULE;
        executionState.updatePhase(phase);

        log.info("\n" + "=".repeat(80));
        log.info("FULFILLING REQUIREMENTS FOR CONTEXT: {}", context);
        log.info("Collection: {} | Activity: {} | Wilderness: {}", collectionName, activityType, isWildernessCollection);
        log.info("=".repeat(80));
        
        // Display complete registry information
        log.info("\n=== COMPLETE REQUIREMENT REGISTRY ===");
        log.info(registry.getDetailedRegistryString());
        
        // Step 0: Conditional and Ordered Requirements (execute first as they may contain prerequisites)
        List<ConditionalRequirement> conditionalReqs = getRequirements(ConditionalRequirement.class, context);
        List<OrderedRequirement> orderedReqs = getRequirements(OrderedRequirement.class, context);
        
        StringBuilder conditionalReqInfo = new StringBuilder();
        conditionalReqInfo.append("\n=== STEP 0: CONDITIONAL REQUIREMENTS ===\n");
        if (conditionalReqs.isEmpty()) {
            conditionalReqInfo.append(String.format("No conditional requirements for context: %s\n", context));
        } else {
            conditionalReqInfo.append(String.format("Found %d conditional requirement(s):\n", conditionalReqs.size()));
            for (int i = 0; i < conditionalReqs.size(); i++) {
            conditionalReqInfo.append(String.format("\n--- Conditional Requirement %d ---\n", i + 1));
            conditionalReqInfo.append(conditionalReqs.get(i).displayString()).append("\n");
            }
        }
        
        conditionalReqInfo.append("\n=== STEP 1: ORDERED REQUIREMENTS ===\n");
        if (orderedReqs.isEmpty()) {
            conditionalReqInfo.append(String.format("No ordered requirements for context: %s\n", context));
        } else {
            conditionalReqInfo.append(String.format("Found %d ordered requirement(s):\n", orderedReqs.size()));
            for (int i = 0; i < orderedReqs.size(); i++) {
            conditionalReqInfo.append(String.format("\n--- Ordered Requirement %d ---\n", i + 1));
            conditionalReqInfo.append(orderedReqs.get(i).displayString()).append("\n");
            }
        }
        
        log.info(conditionalReqInfo.toString());
        executionState.updateFulfillmentStep(FulfillmentStep.CONDITIONAL, "Executing conditional and ordered requirements");
        success &= fulfillConditionalRequirements(context, executorService);
        
        if (!success) {
            executionState.markFailed("Failed to fulfill conditional requirements");
            return false;
        }
        
        // Step 2: Loot Requirements
        List<LootRequirement> lootReqs = getRequirements(LootRequirement.class, context);
        StringBuilder lootReqInfo = new StringBuilder();
        lootReqInfo.append("\n=== STEP 2: LOOT REQUIREMENTS ===\n");
        if (lootReqs.isEmpty()) {
            lootReqInfo.append(String.format("No loot requirements for context: %s\n", context));
        } else {
            lootReqInfo.append(String.format("Found %d loot requirement(s):\n", lootReqs.size()));
            for (int i = 0; i < lootReqs.size(); i++) {
            lootReqInfo.append(String.format("\n--- Loot Requirement %d ---\n", i + 1));
            lootReqInfo.append(lootReqs.get(i).displayString()).append("\n");
            }
        }
        
        log.info(lootReqInfo.toString());
        executionState.updateFulfillmentStep(FulfillmentStep.LOOT, "Collecting loot items ");
        success &= fulfillPrePostLootRequirements(context, executorService);
        
        if (!success) {
            executionState.markFailed("Failed to fulfill loot requirements");
            return false;
        }
        
        // Step 3: Shop Requirements
        List<ShopRequirement> shopReqs = getRequirements(ShopRequirement.class, context);
        StringBuilder shopReqInfo = new StringBuilder();
        shopReqInfo.append("\n=== STEP 3: SHOP REQUIREMENTS ===\n");
        if (shopReqs.isEmpty()) {
            shopReqInfo.append(String.format("No shop requirements for context: %s\n", context));
        } else {
            shopReqInfo.append(String.format("Found %d shop requirement(s):\n", shopReqs.size()));
            for (int i = 0; i < shopReqs.size(); i++) {
            shopReqInfo.append(String.format("\n--- Shop Requirement %d ---\n", i + 1));
            shopReqInfo.append(shopReqs.get(i).displayString()).append("\n");
            }
        }
        
        log.info(shopReqInfo.toString());
        executionState.updateFulfillmentStep(FulfillmentStep.SHOP, "Purchasing shop items");
        success &= fulfillPrePostShopRequirements(context, executorService);
        
        if (!success) {
            executionState.markFailed("Failed to fulfill shop requirements");
            return false;
        }
        
        // Step 4: Item Requirements
        StringBuilder itemReqInfo = new StringBuilder();
        List<ItemRequirement> itemReqs = getRequirements(ItemRequirement.class, context);
        itemReqInfo.append("\n=== STEP 4: ITEM REQUIREMENTS ===\n");
        if (itemReqs.isEmpty()) {
             itemReqInfo.append(String.format("No item requirements for context: %s\n", context));
        } else {            
            itemReqInfo.append(String.format("Found %d item requirement(s):\n", itemReqs.size()));
            
            for (int i = 0; i < itemReqs.size(); i++) {
            itemReqInfo.append(String.format("--- Item Requirement %d ---\n", i + 1));
            itemReqInfo.append(itemReqs.get(i).displayString()).append("\n");
            }
            
            // Display detailed equipment and inventory cache information
            itemReqInfo.append("--- Equipment Items Cache by Slot ---\n");
            Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache = registry.getEquipmentLogicalRequirements();
            if (equipmentCache.isEmpty()) {
            itemReqInfo.append("No equipment items in cache\n");
            } else {
            for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentCache.entrySet()) {
            itemReqInfo.append(String.format("%s: %d logical requirement(s)\n", 
                entry.getKey().name(), entry.getValue().size()));
            for (LogicalRequirement logicalReq : entry.getValue()) {
            itemReqInfo.append(String.format("\t- %s\n", logicalReq.getStatusInfo()));
            }
            }
            }
            
            itemReqInfo.append("--- Inventory Slot Requirements Cache ---\n");
            Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache = registry.getInventorySlotRequirementsCache();
            if (inventorySlotCache.isEmpty()) {
            itemReqInfo.append("No specific inventory slot requirements in cache\n");
            } else {
            for (Map.Entry<Integer, LinkedHashSet<LogicalRequirement>> entry : inventorySlotCache.entrySet()) {
            String slotName = entry.getKey() == -1 ? "ANY_SLOT" : "SLOT_" + entry.getKey();
            itemReqInfo.append(String.format("%s: %d logical requirement(s)\n", 
                slotName, entry.getValue().size()));
            for (LogicalRequirement logicalReq : entry.getValue()) {
            itemReqInfo.append(String.format("\t- %s\n", logicalReq.getStatusInfo()));
            }
            }
            }
            
            
        }
        
        log.info(itemReqInfo.toString());
        executionState.updateFulfillmentStep(FulfillmentStep.ITEMS, "Preparing inventory and equipment");
        success &= fulfillPrePostItemRequirements(context, executorService);
        
        if (!success) {
            executionState.markFailed("Failed to fulfill item requirements");
            return false;
        }
        
        // Step 5: Spellbook Requirements
        List<SpellbookRequirement> spellbookReqs = getRequirements(SpellbookRequirement.class, context);
        StringBuilder spellbookReqInfo = new StringBuilder();
        spellbookReqInfo.append("\n=== STEP 5: SPELLBOOK REQUIREMENTS ===\n");
        if (spellbookReqs.isEmpty()) {
            spellbookReqInfo.append(String.format("No spellbook requirements for context: %s\n", context));
        } else {
            spellbookReqInfo.append(String.format("Found %d spellbook requirement(s):\n", spellbookReqs.size()));
            for (int i = 0; i < spellbookReqs.size(); i++) {
            spellbookReqInfo.append(String.format("\n--- Spellbook Requirement %d ---\n", i + 1));
            spellbookReqInfo.append(spellbookReqs.get(i).displayString()).append("\n");
            }
        }
        
        log.info(spellbookReqInfo.toString());
        executionState.updateFulfillmentStep(FulfillmentStep.SPELLBOOK, "Switching spellbook");
        success &= fulfillPrePostSpellbookRequirements(context, saveCurrentSpellbook);
        
        if (!success) {
            executionState.markFailed("Failed to fulfill spellbook requirements");
            return false;
        }
        
        // Step 6: Location Requirements (always fulfill location requirements last)
        List<LocationRequirement> locationReqs = getRequirements(LocationRequirement.class, context);
        StringBuilder locationReqInfo = new StringBuilder();
        locationReqInfo.append("\n=== STEP 6: LOCATION REQUIREMENTS ===\n");
        if (locationReqs.isEmpty()) {
            locationReqInfo.append(String.format("No location requirements for context: %s\n", context));
        } else {
            locationReqInfo.append(String.format("Found %d location requirement(s):\n", locationReqs.size()));
            for (int i = 0; i < locationReqs.size(); i++) {
            locationReqInfo.append(String.format("\n--- Location Requirement %d ---\n", i + 1));
            locationReqInfo.append(locationReqs.get(i).displayString()).append("\n");
            }
        }
        
        log.info(locationReqInfo.toString());
        executionState.updateFulfillmentStep(FulfillmentStep.LOCATION, "Moving to required location");
        success &= fulfillPrePostLocationRequirements(context, executorService);
        
        if (success) {
            executionState.updateState(TaskExecutionState.ExecutionState.COMPLETED, "All requirements fulfilled successfully");            
            log.info("\n" + "=".repeat(80)+"\nALL REQUIREMENTS FULFILLED SUCCESSFULLY FOR CONTEXT: {}\n"+"=".repeat(80), context);            
        } else {
            executionState.markFailed("Failed to fulfill location requirements");
            log.error("\n" + "=".repeat(80)+"\nFAILED TO FULFILL REQUIREMENTS FOR CONTEXT: {}\n"+ "=".repeat(80), context);
        }
        
        return success;
    }
    
    /**
     * Convenience method to fulfill all pre-schedule requirements.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @param saveCurrentSpellbook Whether to save current spellbook for restoration
     * @return true if all pre-schedule requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPreScheduleRequirements(ScheduledExecutorService executorService, boolean saveCurrentSpellbook) {
        return fulfillAllRequirements(ScheduleContext.PRE_SCHEDULE, executorService, saveCurrentSpellbook);
    }
    
    /**
     * Backward compatibility method to fulfill all pre-schedule requirements without executor service.
     * 
     * @param saveCurrentSpellbook Whether to save current spellbook for restoration
     * @return true if all pre-schedule requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPreScheduleRequirements(boolean saveCurrentSpellbook) {
        return fulfillAllRequirements(ScheduleContext.PRE_SCHEDULE, null, saveCurrentSpellbook);
    }
    
    /**
     * Convenience method to fulfill all post-schedule requirements.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @param saveCurrentSpellbook Whether to save current spellbook for restoration
     * @return true if all post-schedule requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPostScheduleRequirements(ScheduledExecutorService executorService, boolean saveCurrentSpellbook) {
        return fulfillAllRequirements(ScheduleContext.POST_SCHEDULE, executorService, saveCurrentSpellbook);
    }
    
    /**
     * Backward compatibility method to fulfill all post-schedule requirements without executor service.
     * 
     * @param saveCurrentSpellbook Whether to save current spellbook for restoration
     * @return true if all post-schedule requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPostScheduleRequirements(boolean saveCurrentSpellbook) {
        return fulfillAllRequirements(ScheduleContext.POST_SCHEDULE, null, saveCurrentSpellbook);
    }
    


    // === UNIFIED REQUIREMENT FULFILLMENT FUNCTIONS ===
    
    /**
     * Fulfills all item requirements (equipment, inventory, either) for the specified schedule context.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all item requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostItemRequirements(ScheduleContext context, ScheduledExecutorService executorService) {
        // Get all logical requirements for this context
        List<LogicalRequirement> logicalReqs = getLogicalRequirements(context);
        
        if (logicalReqs.isEmpty()) {
            log.debug("No item requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        
        // Initialize step tracking
        updateFulfillmentStep(FulfillmentStep.ITEMS, "Processing item requirements", logicalReqs.size());
        
        boolean success = true;
        success = fulfillOptimalInventoryAndEquipmentLayout(context, executorService);
        
        
        log.debug("Item requirements fulfillment completed. Success: {}", success);
        return success;
    }
    
    /**
     * Backward compatibility method to fulfill item requirements without executor service.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all item requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostItemRequirements(ScheduleContext context) {
        return fulfillPrePostItemRequirements(context, null);
    }
    
    /**
     * Fulfills shop requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all shop requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostShopRequirements(ScheduleContext context, ScheduledExecutorService executorService) {
        LinkedHashSet<LogicalRequirement> shopLogical = registry.getShopLogicalRequirements();
        
        if (shopLogical.isEmpty()) {
            log.debug("No shop requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        
        // Initialize step tracking
        List<LogicalRequirement> contextReqs = LogicalRequirement.filterByContext(new ArrayList<>(shopLogical), context);
        updateFulfillmentStep(FulfillmentStep.SHOP, "Processing shop requirements", contextReqs.size());
        log.info("Processing shop requirements for context: {} number of  req.", context, contextReqs.size());
        // Use the utility class for fulfillment
        return LogicalRequirement.fulfillLogicalRequirements(contextReqs, "shop");
    }
    
    /**
     * Backward compatibility method to fulfill shop requirements without executor service.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all shop requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostShopRequirements(ScheduleContext context) {
        return fulfillPrePostShopRequirements(context, null);
    }
    
    /**
     * Fulfills loot requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all loot requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostLootRequirements(ScheduleContext context, ScheduledExecutorService executorService) {
        LinkedHashSet<LogicalRequirement> lootLogical = registry.getLootLogicalRequirements();
        
        if (lootLogical.isEmpty()) {
            log.debug("No loot requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        
        // Initialize step tracking
        List<LogicalRequirement> contextReqs = LogicalRequirement.filterByContext(new ArrayList<>(lootLogical), context);
        updateFulfillmentStep(FulfillmentStep.LOOT, "Collecting loot items", contextReqs.size());
        
        // Use the utility class for fulfillment
        return LogicalRequirement.fulfillLogicalRequirements(contextReqs, "loot");
               
       
    }
    
    /**
     * Backward compatibility method to fulfill loot requirements without executor service.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all loot requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostLootRequirements(ScheduleContext context) {
        return fulfillPrePostLootRequirements(context, null);
    }
    
    /**
     * Fulfills location requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all location requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostLocationRequirements(ScheduleContext context, ScheduledExecutorService executorService) {
        List<LocationRequirement> locationReqs = getRequirements(LocationRequirement.class, context);
        
        if (locationReqs.isEmpty()) {
            log.debug("No location requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        if (locationReqs.size() > 1) {
            Microbot.log("Multiple location requirements found for context " + context + ". Only one should be set at a time.", Level.ERROR);
            return false; // Only one location requirement should be set per context
        }
        
        // Initialize step tracking
        LocationRequirement locationReq = locationReqs.get(0);
        updateFulfillmentStep(FulfillmentStep.LOCATION, "Moving to " + locationReq.getName(), locationReqs.size());
        
        boolean success = true;
        
        for (int i = 0; i < locationReqs.size(); i++) {
            LocationRequirement requirement = locationReqs.get(i);
            
            // Update current requirement tracking
            updateCurrentRequirement(requirement, i + 1);
            
            try {
                log.debug("Processing location requirement {}/{}: {}", i + 1, locationReqs.size(), requirement.getName());
                boolean fulfilled = requirement.fulfillRequirement(executorService);
                if (!fulfilled && requirement.isMandatory()) {
                    Microbot.log("Failed to fulfill mandatory location requirement: " + requirement.getName());
                    success = false;
                } else if (!fulfilled) {
                    Microbot.log("Failed to fulfill optional location requirement: " + requirement.getName());
                }
            } catch (Exception e) {
                Microbot.log("Error fulfilling location requirement " + requirement.getName() + ": " + e.getMessage());
                if (requirement.isMandatory()) {
                    success = false;
                }
            }
        }
        
        log.debug("Location requirements fulfillment completed. Success: {}", success);
        return success;
    }
    
    /**
     * Backward compatibility method to fulfill location requirements without executor service.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all location requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostLocationRequirements(ScheduleContext context) {
        return fulfillPrePostLocationRequirements(context, null);
    }
    
    /**
     * Fulfills spellbook requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param saveCurrentSpellbook Whether to save the current spellbook before switching (for pre-schedule)
     * @return true if all spellbook requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostSpellbookRequirements(ScheduleContext context, boolean saveCurrentSpellbook) {
        List<SpellbookRequirement> spellbookReqs = getRequirements(SpellbookRequirement.class, context);
        
        if (spellbookReqs.isEmpty()) {
            log.debug("No spellbook requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        if(spellbookReqs.size() > 1) {            
            Microbot.log("Multiple spellbook requirements found for context " + context + ". Only one should be set at a time.", Level.ERROR);
            return false; // Only one spellbook requirement should be set per context
        }
        
        SpellbookRequirement spellbookReq = spellbookReqs.get(0);
        
        // Initialize step tracking
        updateFulfillmentStep(FulfillmentStep.SPELLBOOK, "Switching to " + spellbookReq.getRequiredSpellbook().name(), spellbookReqs.size());
        
        boolean success = true;
        
        // Save original spellbook if this is for pre-schedule and we should save it
        if (context == ScheduleContext.PRE_SCHEDULE && saveCurrentSpellbook) {
            originalSpellbook = Rs2Spellbook.getCurrentSpellbook();
            Microbot.log("Saved original spellbook: " + originalSpellbook + " before switching for pre-schedule requirements");
        }
      
        for (int i = 0; i < spellbookReqs.size(); i++) {
            SpellbookRequirement requirement = spellbookReqs.get(i);
            
            // Update current requirement tracking
            updateCurrentRequirement(requirement, i + 1);
            
            try {
                log.debug("Processing spellbook requirement {}/{}: {}", i + 1, spellbookReqs.size(), requirement.getName());
                boolean fulfilled = requirement.fulfillRequirement();
                if (!fulfilled && requirement.isMandatory()) {
                    Microbot.log("Failed to fulfill mandatory spellbook requirement: " + requirement.getName());
                    success = false;
                } else if (!fulfilled) {
                    Microbot.log("Failed to fulfill optional spellbook requirement: " + requirement.getName());
                }
            } catch (Exception e) {
                Microbot.log("Error fulfilling spellbook requirement " + requirement.getName() + ": " + e.getMessage());
                if (requirement.isMandatory()) {
                    success = false;
                }
            }
        }
        
        // Special handling for post-schedule: if no post-schedule spellbook requirement is defined
        // but we have a saved original spellbook, automatically restore it
        if (context == ScheduleContext.POST_SCHEDULE && spellbookReqs.isEmpty() && originalSpellbook != null) {
            log.debug("No post-schedule spellbook requirement defined, automatically restoring original spellbook");
            boolean restored = switchBackToOriginalSpellbook();
            if (!restored) {
                Microbot.log("Failed to automatically restore original spellbook during post-schedule fulfillment", Level.WARN);
                // Don't mark as failure since this is automatic restoration, not an explicit requirement
            }
        }
        
        log.debug("Spellbook requirements fulfillment completed. Success: {}", success);
        return success;
    }
    
    /**
     * Fulfills conditional and ordered requirements for the specified schedule context.
     * These requirements are processed first as they may contain prerequisites for other requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all conditional requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillConditionalRequirements(ScheduleContext context, ScheduledExecutorService executorService) {
        List<ConditionalRequirement> conditionalReqs = getRequirements(ConditionalRequirement.class, context);
        List<OrderedRequirement> orderedReqs = getRequirements(OrderedRequirement.class, context);
        
        // Initialize step tracking
        int totalReqs = conditionalReqs.size() + orderedReqs.size();
        updateFulfillmentStep(FulfillmentStep.CONDITIONAL, 
                "Processing " + totalReqs + " conditional/ordered requirement(s)", totalReqs);
        
        // Use the utility class for fulfillment
        return RequirementSolver.fulfillConditionalRequirements(conditionalReqs, orderedReqs, context);
              
    }
    
    /**
     * Backward compatibility method to fulfill conditional requirements without executor service.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all conditional requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillConditionalRequirements(ScheduleContext context) {
        return fulfillConditionalRequirements(context, null);
    }
    
    
    /**
     * Updates the fulfillment state for a specific step with requirement counting.
     * This is the preferred method for tracking step-level progress.
     * 
     * @param step The fulfillment step being processed
     * @param details Descriptive text about what's happening in this step
     * @param totalRequirements Total number of requirements in this step
     */
    protected void updateFulfillmentStep(FulfillmentStep step, String details, int totalRequirements) {
        executionState.updateFulfillmentStep(step, details, totalRequirements);
    }
    
    /**
     * Updates the current requirement being processed within a step.
     * This provides granular tracking of individual requirement progress.
     * 
     * @param requirement The specific requirement being processed
     * @param requirementIndex The 1-based index of this requirement in the current step
     */
    protected void updateCurrentRequirement(Requirement requirement, int requirementIndex) {
        if (requirement != null) {
            executionState.updateCurrentRequirement(requirement, requirement.getName(), requirementIndex);
        }
    }
   
    
    /**
     * Clears the current fulfillment state
     */
    public void clearFulfillmentState() {
        executionState.clear();
    }
    
    /**
     * Gets the current fulfillment status for overlay display
     * @return A formatted string describing the current state, or null if not fulfilling
     */
    public String getCurrentFulfillmentStatus() {
        return executionState.getDisplayStatus();
    }
    
    /**
     * Checks if any requirement fulfillment is currently in progress
     */
    public boolean isFulfilling() {
        return executionState.isExecuting();
    }
    
    /**
     * Gets all logical requirements for a specific schedule context.
     * 
     * @param context The schedule context to filter by
     * @return List of logical requirements for the context
     */
    private List<LogicalRequirement> getLogicalRequirements(ScheduleContext context) {
        List<LogicalRequirement> logicalReqs = new ArrayList<>();
        
        // Add equipment logical requirements
        for (LinkedHashSet<LogicalRequirement> slotReqs : registry.getEquipmentLogicalRequirements().values()) {
            for (LogicalRequirement req : slotReqs) {
                if (req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH) {
                    logicalReqs.add(req);
                }
            }
        }
        
        // Add inventory logical requirements
        for (LogicalRequirement req : registry.getInventoryLogicalRequirements()) {
            if (req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH) {
                logicalReqs.add(req);
            }
        }
        
        // Add slot-specific inventory requirements
        for (LinkedHashSet<LogicalRequirement> slotReqs : registry.getAllInventorySlotRequirements().values()) {
            for (LogicalRequirement req : slotReqs) {
                if (req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH) {
                    logicalReqs.add(req);
                }
            }
        }
        
        return logicalReqs;
    }
    
    

    /**
     * Comprehensive inventory and equipment layout planning and fulfillment system.
     * This method analyzes all requirements and creates optimal item placement maps,
     * considering slot constraints, priority levels, and availability.
     * 
     * @param context The schedule context
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all mandatory requirements can be fulfilled
     */
    private boolean fulfillOptimalInventoryAndEquipmentLayout(ScheduleContext context, ScheduledExecutorService executorService) {
        try {
            log.info("\n" + "=".repeat(60));
            log.info("OPTIMAL INVENTORY AND EQUIPMENT LAYOUT PLANNING");
            log.info("Context: {} | Collection: {}", context, collectionName);
            log.info("=".repeat(60));
            
            // Ensure bank is open for all operations
            if (!Rs2Bank.isOpen()) {
                if (!Rs2Bank.walkToBankAndUseBank()) {
                    Microbot.log("Failed to open bank for comprehensive item management");
                    return false;
                }
                sleepUntil(() -> Rs2Bank.isOpen(), 5000);
            }

            // Step 1: Analyze all requirements and create constraint maps
            log.info("\n--- Step 1: Analyzing Requirements and Creating Layout Plan ---");
            InventorySetupPlanner layoutPlan = analyzeRequirementsAndCreateLayoutPlan(context);
            
            if (layoutPlan == null) {
                log.error("Failed to create inventory layout plan");
                return false;
            }
            
            // Display detailed plan information
            log.info("\n--- Generated Layout Plan ---");
            log.info(layoutPlan.getDetailedPlanString());
            
            // Step 2: Check if the plan is feasible (all mandatory items can be fulfilled)
            log.info("\n--- Step 2: Feasibility Check ---");
            boolean feasible = layoutPlan.isFeasible();
            log.info("Plan Feasibility: {}", feasible ? "FEASIBLE" : "NOT FEASIBLE");
            
            if (!feasible) {
                log.error("Layout plan is not feasible - missing mandatory items or insufficient space");
                
                // Log detailed failure reasons
                if (!layoutPlan.getMissingMandatoryItems().isEmpty()) {
                    log.error("Missing mandatory items:");
                    for (ItemRequirement missing : layoutPlan.getMissingMandatoryItems()) {
                        log.error("\t- {}", missing.getName());
                    }
                }
                
                if (!layoutPlan.getMissingMandatoryEquipment().isEmpty()) {
                    log.error("Missing mandatory equipment slots:");
                    for (EquipmentInventorySlot slot : layoutPlan.getMissingMandatoryEquipment()) {
                        log.error("\t- {}", slot.name());
                    }
                }
                
                return false;
            }
            
            // Display slot utilization summary
            log.info("\n--- Slot Utilization Summary ---");
            log.info(layoutPlan.getOccupiedSlotsSummary());
            
            // Step 3: Bank items not in the planned loadout
            log.info("\n--- Step 3: Banking Items Not in Planned Loadout ---");
            log.info("This step removes any equipped or inventory items that are not part of the optimal layout plan.");
            log.info("This ensures a clean slate for precise loadout execution.");
            boolean bankingSuccess = layoutPlan.bankItemsNotInPlan();
            if (!bankingSuccess) {
                log.error("Failed to bank items not in planned loadout");
                return false;
            }
            
            // Step 4: Execute the plan - withdraw/equip items according to the optimal layout
            log.info("\n--- Step 4: Executing Layout Plan ---");
            boolean success = layoutPlan.executePlan();
            
            if (success) {
                log.info("\n" + "=".repeat(60));
                log.info("SUCCESSFULLY EXECUTED OPTIMAL INVENTORY AND EQUIPMENT LAYOUT");
                log.info("=".repeat(60));
                
                String inventorySetupName = "Optimal_Setup_"+collectionName+"_"+ context.name();
                MInventorySetupsPlugin inventorySetupsPlugin = (MInventorySetupsPlugin)Microbot.getPlugin(MInventorySetupsPlugin.class.getName());
                if (inventorySetupsPlugin != null) {
                    inventorySetupsPlugin.addInventorySetup(inventorySetupName);
                    log.info("Saved optimal inventory setup as: {}", inventorySetupName);
                } else {
                    log.warn("MInventorySetupsPlugin not found, cannot save inventory setup");
                }
            } else {
                log.error("\n" + "=".repeat(60));
                log.error("FAILED TO EXECUTE INVENTORY AND EQUIPMENT LAYOUT PLAN");
                log.error("=".repeat(60));
            }
            
            return success;

        } catch (Exception e) {
            log.error("\nError in comprehensive inventory and equipment fulfillment: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Analyzes all requirements and creates an optimal inventory and equipment layout plan.
     * Considers slot constraints, priority levels, availability, and space optimization.
     */
    private InventorySetupPlanner analyzeRequirementsAndCreateLayoutPlan(ScheduleContext context) {
        InventorySetupPlanner plan = new InventorySetupPlanner();
        
        // Track already planned items to avoid double-processing EITHER requirements
        Set<ItemRequirement> alreadyPlanned = new HashSet<>();
        
        // Get all requirements filtered by context
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentReqs = 
            registry.getEquipmentLogicalRequirements();
        Map<Integer, LinkedHashSet<LogicalRequirement>> slotSpecificReqs = 
            registry.getAllInventorySlotRequirements();
        
        // Step 0: Early feasibility check for mandatory items
        if (!InventorySetupPlanner.performEarlyFeasibilityCheck(context, equipmentReqs, slotSpecificReqs)) {
            log.warn("Early feasibility check failed - some mandatory items cannot be fulfilled");
            // Continue with planning but mark as potentially infeasible
        }
        
        // Step 1: Plan equipment slots (these have fixed positions)
        // This includes EITHER items that have equipment slots
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentReqs.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            LinkedHashSet<LogicalRequirement> logicalReqs = entry.getValue();
            
            // Filter by context
            List<LogicalRequirement> contextReqs = logicalReqs.stream()
                .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
                .collect(Collectors.toList());
            
            if (!contextReqs.isEmpty()) {
                // Use enhanced item selection for equipment slots
                ItemRequirement bestItem = RequirementSelector.findBestAvailableItemForSlot(contextReqs, slot.getSlotIdx(), plan);
                if (bestItem != null) {
                    plan.addEquipmentSlotAssignment(slot, bestItem);
                    alreadyPlanned.add(bestItem); // Mark as planned to avoid double-processing
                    
                    log.info("Assigned {} (type: {}) to equipment slot {}", 
                        bestItem.getName(), bestItem.getRequirementType(), slot);
                } else {
                    // Check if any requirement was mandatory
                    boolean hasMandatory = contextReqs.stream()
                        .anyMatch(req -> LogicalRequirement.extractItemRequirementsFromLogical(req).stream()
                            .anyMatch(ItemRequirement::isMandatory));
                    
                    if (hasMandatory) {
                        plan.addMissingMandatoryEquipment(slot);
                        log.warn("Cannot fulfill mandatory equipment requirement for slot {}", slot);
                        return null; // Early exit if mandatory equipment cannot be fulfilled
                    }
                }
            }
        }
        
        // Step 2: Plan specific inventory slots (0-27, excluding items already planned in equipment)
        for (Map.Entry<Integer, LinkedHashSet<LogicalRequirement>> entry : slotSpecificReqs.entrySet()) {
            int slot = entry.getKey();
            LinkedHashSet<LogicalRequirement> logicalReqs = entry.getValue();
            
            // Skip the "any slot" entry (-1) as we'll handle it in Step 3
            if (slot == -1) {
                continue;
            }
            
            // Filter by context
            List<LogicalRequirement> contextReqs = logicalReqs.stream()
                .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
                .collect(Collectors.toList());
            
            if (!contextReqs.isEmpty()) {
                ItemRequirement bestItem = RequirementSelector.findBestAvailableItemNotAlreadyPlanned(contextReqs, plan);
                if (bestItem != null) {
                    // Enhanced validation for slot assignment
                    if (!ItemRequirement.canAssignToSpecificSlot(bestItem, slot)) {
                        log.warn("Cannot assign item {} to slot {} due to constraints. Moving to flexible items.", 
                                bestItem.getName(), slot);
                        
                        // Handle the item as flexible instead
                        InventorySetupPlanner.handleItemAsFlexible(bestItem, plan, alreadyPlanned);
                    } else {
                        // Item can be assigned to specific slot
                        ItemRequirement slotSpecificItem = bestItem.copyWithSpecificSlot(slot);
                        plan.addInventorySlotAssignment(slot, slotSpecificItem);
                        alreadyPlanned.add(bestItem); // Mark as planned
                        
                        log.debug("Assigned {} to specific slot {} (amount: {}, stackable: {})", 
                                bestItem.getName(), slot, bestItem.getAmount(), bestItem.isStackable());
                    }
                } else {
                    // Handle missing mandatory items
                    InventorySetupPlanner.handleMissingMandatoryItem(contextReqs, plan, "inventory slot " + slot);
                }
            }
        }
        
        // Step 3: Plan flexible inventory items (any slot allowed, from the -1 key)
        LinkedHashSet<LogicalRequirement> anySlotReqs = slotSpecificReqs.getOrDefault(-1, new LinkedHashSet<>());
        List<LogicalRequirement> contextInventoryReqs = anySlotReqs.stream()
            .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
            .collect(Collectors.toList());
        
        // Enhanced flexible item processing with better optimization
        for (LogicalRequirement logicalReq : contextInventoryReqs) {
            ItemRequirement bestItem = RequirementSelector.findBestAvailableItemNotAlreadyPlanned(Arrays.asList(logicalReq), plan);
            if (bestItem != null) {
                InventorySetupPlanner.handleItemAsFlexible(bestItem, plan, alreadyPlanned);
            } else {
                // Handle missing mandatory items
                InventorySetupPlanner.handleMissingMandatoryItem(Arrays.asList(logicalReq), plan, "flexible inventory slot");
            }
        }
        
        // Step 4: Optimize and validate the entire plan
        InventorySetupPlanner.optimizeAndValidatePlan(plan);
        
        return plan;
    }

    /**
     * Adds a dummy equipment requirement to block a specific equipment slot.
     * Dummy items are used to reserve slots without specifying actual items.
     * 
     * @param equipmentSlot The equipment slot to block
     * @param scheduleContext When this requirement applies
     * @param description Description for the dummy requirement
     */
    protected void addDummyEquipmentRequirement(EquipmentInventorySlot equipmentSlot, 
                                               ScheduleContext scheduleContext, 
                                               String description) {
        ItemRequirement dummy = ItemRequirement.createDummyEquipmentRequirement(
            equipmentSlot, scheduleContext, description);
        registry.register(dummy);
        log.debug("Added dummy equipment requirement for slot {}: {}", equipmentSlot, description);
    }
    
    /**
     * Adds a dummy inventory requirement to block a specific inventory slot.
     * Dummy items are used to reserve slots without specifying actual items.
     * 
     * @param inventorySlot The inventory slot to block (0-27)
     * @param scheduleContext When this requirement applies
     * @param description Description for the dummy requirement
     */
    protected void addDummyInventoryRequirement(int inventorySlot, 
                                               ScheduleContext scheduleContext, 
                                               String description) {
        ItemRequirement dummy = ItemRequirement.createDummyInventoryRequirement(
            inventorySlot, scheduleContext, description);
        registry.register(dummy);
        log.debug("Added dummy inventory requirement for slot {}: {}", inventorySlot, description);
    }
    
//     /**
//      * Performs final optimization and validation on the complete plan.
//      * This includes space optimization, conflict resolution, feasibility checking, and comprehensive mandatory validation.
//      */
//     private boolean optimizeAndValidatePlan(InventorySetupPlanner plan) {
//         return InventorySetupPlanner.optimizeAndValidatePlan(plan);
//     }
    
//     /**
//      * Validates that all mandatory requirements are actually satisfied in the final plan.
//      * This is a comprehensive check that goes beyond just checking for "missing" items.
//      * 
//      * @param plan The inventory setup plan to validate
//      * @return true if all mandatory requirements are satisfied, false otherwise
//      */
//     private boolean validateAllMandatoryRequirementsSatisfied(InventorySetupPlanner plan) {
//         boolean allSatisfied = true;
//         List<String> unsatisfiedRequirements = new ArrayList<>();
        
//         // Get all mandatory requirements for the current context
//         List<ItemRequirement> allMandatoryItems = new ArrayList<>();
        
//         // Collect from equipment requirements
//         for (LinkedHashSet<LogicalRequirement> slotReqs : registry.getEquipmentLogicalRequirements().values()) {
//             for (LogicalRequirement req : slotReqs) {
//                 allMandatoryItems.addAll(LogicalRequirement.extractItemRequirementsFromLogical(req).stream()
//                     .filter(ItemRequirement::isMandatory)
//                     .collect(Collectors.toList()));
//             }
//         }
        
//         // Collect from inventory requirements
//         for (LinkedHashSet<LogicalRequirement> slotReqs : registry.getAllInventorySlotRequirements().values()) {
//             for (LogicalRequirement req : slotReqs) {
//                 allMandatoryItems.addAll(LogicalRequirement.extractItemRequirementsFromLogical(req).stream()
//                     .filter(ItemRequirement::isMandatory)
//                     .collect(Collectors.toList()));
//             }
//         }
        
//         // Check each mandatory requirement
//         for (ItemRequirement mandatoryItem : allMandatoryItems) {
//             boolean satisfied = false;
            
//             // Check if it's in equipment assignments
//             for (ItemRequirement equippedItem : plan.getEquipmentAssignments().values()) {
//                 if (itemRequirementMatches(mandatoryItem, equippedItem)) {
//                     satisfied = true;
//                     break;
//                 }
//             }
            
//             // Check if it's in inventory assignments
//             if (!satisfied) {
//                 for (ItemRequirement inventoryItem : plan.getInventorySlotAssignments().values()) {
//                     if (itemRequirementMatches(mandatoryItem, inventoryItem)) {
//                         satisfied = true;
//                         break;
//                     }
//                 }
//             }
            
//             // Check if it's in flexible inventory items
//             if (!satisfied) {
//                 for (ItemRequirement flexibleItem : plan.getFlexibleInventoryItems()) {
//                     if (itemRequirementMatches(mandatoryItem, flexibleItem)) {
//                         satisfied = true;
//                         break;
//                     }
//                 }
//             }
            
//             if (!satisfied) {
//                 unsatisfiedRequirements.add(mandatoryItem.getName() + " (" + mandatoryItem.getRequirementType() + ")");
//                 allSatisfied = false;
//             }
//         }
        
//         if (!allSatisfied) {
//             log.error("Final validation failed. {} mandatory requirements not satisfied:", unsatisfiedRequirements.size());
//             unsatisfiedRequirements.forEach(req -> log.error("  - {}", req));
//         } else {
//             log.debug("Final validation passed - all {} mandatory requirements are satisfied", allMandatoryItems.size());
//         }
        
//         return allSatisfied;
//     }
    
//     /**
//      * Checks if two ItemRequirements represent the same logical requirement.
//      * This accounts for the fact that requirements may be copied or modified during planning.
//      */
//     private boolean itemRequirementMatches(ItemRequirement original, ItemRequirement planned) {
//         // Check if they have overlapping item IDs
//         return original.getIds().stream().anyMatch(planned.getIds()::contains) &&
//                original.getAmount() <= planned.getAmount();
//     }
    
//     /**
//      * Performs an enhanced early feasibility check to determine if all mandatory requirements can be fulfilled.
//      * This simulates the actual planning logic to detect slot competition and conflicts early.
//      * 
//      * @param context The schedule context to check
//      * @param equipmentReqs Equipment requirements by slot
//      * @param slotSpecificReqs Inventory slot requirements
//      * @return true if all mandatory requirements appear to be fulfillable
//      */
//     private boolean performEarlyFeasibilityCheck(ScheduleContext context,
//             Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentReqs,
//             Map<Integer, LinkedHashSet<LogicalRequirement>> slotSpecificReqs) {
        
//         boolean allMandatoryFulfillable = true;
//         List<String> unfulfillableItems = new ArrayList<>();
//         List<String> conflictWarnings = new ArrayList<>();
        
//         // Track equipment slot assignments to detect conflicts
//         Map<EquipmentInventorySlot, ItemRequirement> simulatedEquipmentAssignments = new HashMap<>();
//         Set<ItemRequirement> alreadyAssigned = new HashSet<>();
        
//         // Enhanced check for mandatory equipment requirements with conflict detection
//         for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentReqs.entrySet()) {
//             EquipmentInventorySlot slot = entry.getKey();
//             LinkedHashSet<LogicalRequirement> logicalReqs = entry.getValue();
            
//             // Filter by context
//             List<LogicalRequirement> contextReqs = logicalReqs.stream()
//                 .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
//                 .collect(Collectors.toList());
            
//             // Get all mandatory requirements for this slot
//             List<ItemRequirement> mandatoryItems = contextReqs.stream()
//                 .flatMap(req -> LogicalRequirement.extractItemRequirementsFromLogical(req).stream())
//                 .filter(ItemRequirement::isMandatory)
//                 .collect(Collectors.toList());
            
//             if (!mandatoryItems.isEmpty()) {
//                 // Use enhanced selection logic that prioritizes EQUIPMENT over EITHER
//                 ItemRequirement bestItem = findBestAvailableItemForSlot(contextReqs, alreadyAssigned);
                
//                 if (bestItem != null) {
//                     simulatedEquipmentAssignments.put(slot, bestItem);
//                     alreadyAssigned.add(bestItem);
                    
//                     // Check for conflicts: multiple MANDATORY EQUIPMENT items for same slot
//                     List<ItemRequirement> mandatoryEquipmentItems = mandatoryItems.stream()
//                         .filter(item -> item.getRequirementType() == RequirementType.EQUIPMENT)
//                         .collect(Collectors.toList());
                    
//                     if (mandatoryEquipmentItems.size() > 1) {
//                         List<String> conflictingItems = mandatoryEquipmentItems.stream()
//                             .filter(item -> !item.equals(bestItem))
//                             .map(ItemRequirement::getName)
//                             .collect(Collectors.toList());
                        
//                         if (!conflictingItems.isEmpty()) {
//                             conflictWarnings.add("CONFLICT: Multiple MANDATORY EQUIPMENT items for " + slot + 
//                                 ": " + bestItem.getName() + " (selected) vs " + String.join(", ", conflictingItems));
                            
//                             // Mark conflicting EQUIPMENT items as unfulfillable
//                             unfulfillableItems.addAll(conflictingItems.stream()
//                                 .map(name -> name + " (equipment slot: " + slot + " - CONFLICT)")
//                                 .collect(Collectors.toList()));
//                             allMandatoryFulfillable = false;
//                         }
//                     }
                    
//                     log.debug("Early feasibility: Assigned {} to {} slot", bestItem.getName(), slot);
//                 } else {
//                     // No available item for mandatory requirements
//                     String mandatoryItemName = mandatoryItems.get(0).getName();
//                     unfulfillableItems.add(mandatoryItemName + " (equipment slot: " + slot + ")");
//                     allMandatoryFulfillable = false;
//                 }
//             }
//         }
        
        
//         // Check mandatory inventory requirements with simulation
//         Set<ItemRequirement> inventoryAssigned = new HashSet<>(alreadyAssigned);
        
//         for (Map.Entry<Integer, LinkedHashSet<LogicalRequirement>> entry : slotSpecificReqs.entrySet()) {
//             Integer slot = entry.getKey();
//             LinkedHashSet<LogicalRequirement> logicalReqs = entry.getValue();
            
//             // Filter by context
//             List<LogicalRequirement> contextReqs = logicalReqs.stream()
//                 .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
//                 .collect(Collectors.toList());
            
//             for (LogicalRequirement logicalReq : contextReqs) {
//                 List<ItemRequirement> mandatoryItems = LogicalRequirement.extractItemRequirementsFromLogical(logicalReq).stream()
//                     .filter(ItemRequirement::isMandatory)
//                     .collect(Collectors.toList());
                
//                 if (!mandatoryItems.isEmpty()) {
//                     // Check if any of these mandatory items are already assigned to equipment
//                     boolean alreadySatisfied = mandatoryItems.stream()
//                         .anyMatch(inventoryAssigned::contains);
                    
//                     if (!alreadySatisfied) {
//                         ItemRequirement bestItem = findBestAvailableItemNotAlreadyPlanned(Arrays.asList(logicalReq), inventoryAssigned);
//                         if (bestItem != null) {
//                             inventoryAssigned.add(bestItem);
//                             log.debug("Early feasibility: Assigned {} to inventory (slot: {})", 
//                                 bestItem.getName(), slot == -1 ? "any" : slot.toString());
//                         } else {
//                             // Find the mandatory item name for logging
//                             String mandatoryItemName = mandatoryItems.get(0).getName();
//                             String slotDescription = (slot == -1) ? "any inventory slot" : "inventory slot " + slot;
//                             unfulfillableItems.add(mandatoryItemName + " (" + slotDescription + ")");
//                             allMandatoryFulfillable = false;
//                         }
//                     }
//                 }
//             }
//         }
        
//         // Log conflict warnings
//         if (!conflictWarnings.isEmpty()) {
//             log.warn("Early feasibility check detected {} potential conflicts:", conflictWarnings.size());
//             conflictWarnings.forEach(warning -> log.warn("  - {}", warning));
//         }
        
//         // Log results
//         if (!allMandatoryFulfillable) {
//             log.error("Early feasibility check failed. Cannot fulfill {} mandatory items:", unfulfillableItems.size());
//             unfulfillableItems.forEach(item -> log.error("  - {}", item));
//         } else {
//             log.info("Early feasibility check passed - all {} mandatory items appear fulfillable", 
//                 alreadyAssigned.size() + inventoryAssigned.size() - alreadyAssigned.size());
//         }
        
//         return allMandatoryFulfillable;
//     }
    
//     /**
//      * Finds the best available item from a list of logical requirements.
//      * Considers priority, rating, availability, skill requirements, stackability, and current game state.
//      * Enhanced with better EITHER optimization and availability validation.
//      */
//     private ItemRequirement findBestAvailableItem(List<LogicalRequirement> logicalReqs) {
//         List<ItemRequirement> allItems = new ArrayList<>();
        
//         // Extract all item requirements from logical requirements
//         for (LogicalRequirement logical : logicalReqs) {
//             allItems.addAll(LogicalRequirement.extractItemRequirementsFromLogical(logical));
//         }
        
//         // Enhanced sorting: priority first, then availability score, then rating
//         allItems.sort((a, b) -> {
//             // Priority comparison (mandatory first)
//             int priorityCompare = a.getPriority().compareTo(b.getPriority());
//             if (priorityCompare != 0) {
//                 return priorityCompare;
//             }
            
//             // Availability comparison (fully available items first)
//             boolean aFullyAvailable = a.meetsAllRequirements();
//             boolean bFullyAvailable = b.meetsAllRequirements();
//             if (aFullyAvailable != bFullyAvailable) {
//                 return aFullyAvailable ? -1 : 1;
//             }
            
//             // For items that are equally available, prefer better ratings
//             int ratingCompare = Integer.compare(b.getRating(), a.getRating());
//             if (ratingCompare != 0) {
//                 return ratingCompare;
//             }
            
//             // Final tie-breaker: prefer currently equipped items to minimize equipment changes
//             boolean aEquipped = a.getEquipmentSlot() != null && Rs2Equipment.isEquipped(a.getIds().get(0), a.getEquipmentSlot());
//             boolean bEquipped = b.getEquipmentSlot() != null && Rs2Equipment.isEquipped(b.getIds().get(0), b.getEquipmentSlot());
//             if (aEquipped != bEquipped) {
//                 return aEquipped ? -1 : 1;
//             }
            
//             // Prefer items already in inventory to minimize banking
//             boolean aInInventory = a.getIds().stream().anyMatch(Rs2Inventory::hasItem);
//             boolean bInInventory = b.getIds().stream().anyMatch(Rs2Inventory::hasItem);
//             if (aInInventory != bInInventory) {
//                 return aInInventory ? -1 : 1;
//             }
            
//             return 0;
//         });
        
//         // Find the best item that meets all requirements
//         for (ItemRequirement item : allItems) {
//             if (item.meetsAllRequirements()) {
//                 // Additional validation for complex requirements
//                 if (ItemRequirement.validateItemSuitability(item)) {
//                     log.debug("Selected best available item: {} (priority: {}, rating: {})", 
//                             item.getName(), item.getPriority(), item.getRating());
//                     return item;
//                 }
//             }
//         }
        
//         // If no fully available item found, log detailed information
//         if (!allItems.isEmpty()) {
//             log.debug("No fully available item found from {} candidates. Best candidate: {}", 
//                     allItems.size(), allItems.get(0).getName());
            
//             // For debugging, show why the best candidate failed
//             ItemRequirement bestCandidate = allItems.get(0);
//             if (!bestCandidate.meetsAllRequirements()) {
//                 log.debug("Best candidate {} failed requirements check", bestCandidate.getName());
//             }
//         }
        
//         return null;
//     }
    
//     /**
//      * Validates that an item is suitable for the current game state and constraints.
//      * This includes checking for special conditions, charges, wilderness restrictions, etc.
//      */
//     private boolean validateItemSuitability(ItemRequirement item) {
//         // Basic validation - item must meet all requirements
//         if (!item.meetsAllRequirements()) {
//             return false;
//         }
        
//         // Check wilderness restrictions if applicable
//         if (isWildernessCollection && item.getName().toLowerCase().contains("untradeable")) {
//             log.debug("Skipping untradeable item {} for wilderness activity", item.getName());
//             return false;
//         }
        
//         // Check for sufficient charges if it's a charged item (using pattern from ItemRequirement)
//         if (item.getName().matches(".*\\((\\d+)\\)$")) {
//             // If it's a charged item, the meetsAllRequirements() should have already validated charges
//             // Additional validation could be added here if needed
//         }
        
//         // Validate stackability constraints for specific slots
//         if (item.getAmount() > 1 && !item.isStackable() && item.getInventorySlot() >= 0) {
//             log.debug("Non-stackable item {} with amount {} cannot fit in single specific slot {}", 
//                     item.getName(), item.getAmount(), item.getInventorySlot());
//             return false;
//         }
        
//         return true;
//     }
    
//     /**
//      * Finds the best available item from a list of logical requirements, excluding items already planned.
//      * This prevents double-processing of EITHER requirements that appear in multiple caches.
//      */
//     private ItemRequirement findBestAvailableItemNotAlreadyPlanned(List<LogicalRequirement> logicalReqs, Set<ItemRequirement> alreadyPlanned) {
//         List<ItemRequirement> allItems = new ArrayList<>();
        
//         // Extract all item requirements from logical requirements
//         for (LogicalRequirement logical : logicalReqs) {
//             allItems.addAll(LogicalRequirement.extractItemRequirementsFromLogical(logical));
//         }
        
//         // Filter out already planned items
//         allItems.removeIf(alreadyPlanned::contains);
        
//         // Sort by priority (mandatory first) then by rating (higher is better)
//         allItems.sort((a, b) -> {
//             int priorityCompare = a.getPriority().compareTo(b.getPriority());
//             if (priorityCompare != 0) {
//                 return priorityCompare;
//             }
//             return Integer.compare(b.getRating(), a.getRating());
//         });
        
//         // Find the best item that meets all requirements
//         for (ItemRequirement item : allItems) {
//             if (item.meetsAllRequirements()) {
//                 return item;
//             }
//         }
        
//         return null;
//     }
    
//     /**
//      * Finds the best available item for an equipment slot, with enhanced logic that properly
//      * prioritizes EQUIPMENT requirements over EITHER requirements when both have the same priority.
//      * This prevents EITHER requirements from taking equipment slots when EQUIPMENT requirements need them.
//      */
//     private ItemRequirement findBestAvailableItemForSlot(List<LogicalRequirement> logicalReqs, Set<ItemRequirement> alreadyPlanned) {
//         List<ItemRequirement> allItems = new ArrayList<>();
        
//         // Extract all item requirements from logical requirements
//         for (LogicalRequirement logical : logicalReqs) {
//             allItems.addAll(LogicalRequirement.extractItemRequirementsFromLogical(logical));
//         }
        
//         // Filter out already planned items
//         allItems.removeIf(alreadyPlanned::contains);
        
//         // Enhanced sorting with RequirementType-aware logic
//         allItems.sort((a, b) -> {
//             // Priority comparison (mandatory first)
//             int priorityCompare = a.getPriority().compareTo(b.getPriority());
//             if (priorityCompare != 0) {
//                 return priorityCompare;
//             }
            
//             // For same priority: EQUIPMENT takes precedence over EITHER for equipment slots
//             // This ensures that items that MUST be equipped get equipment slots first
//             if (a.getPriority() == Priority.MANDATORY && b.getPriority() == Priority.MANDATORY) {
//                 boolean aIsEquipment = a.getRequirementType() == RequirementType.EQUIPMENT;
//                 boolean bIsEquipment = b.getRequirementType() == RequirementType.EQUIPMENT;
                
//                 if (aIsEquipment != bIsEquipment) {
//                     return aIsEquipment ? -1 : 1; // EQUIPMENT before EITHER
//                 }
//             }
            
//             // Availability comparison (fully available items first)
//             boolean aFullyAvailable = a.meetsAllRequirements();
//             boolean bFullyAvailable = b.meetsAllRequirements();
//             if (aFullyAvailable != bFullyAvailable) {
//                 return aFullyAvailable ? -1 : 1;
//             }
            
//             // For items that are equally available, prefer better ratings
//             int ratingCompare = Integer.compare(b.getRating(), a.getRating());
//             if (ratingCompare != 0) {
//                 return ratingCompare;
//             }
            
//             // Final tie-breaker: prefer currently equipped items to minimize equipment changes
//             boolean aEquipped = a.getEquipmentSlot() != null && Rs2Equipment.isEquipped(a.getIds().get(0), a.getEquipmentSlot());
//             boolean bEquipped = b.getEquipmentSlot() != null && Rs2Equipment.isEquipped(b.getIds().get(0), b.getEquipmentSlot());
//             if (aEquipped != bEquipped) {
//                 return aEquipped ? -1 : 1;
//             }
            
//             return 0;
//         });
        
//         // Find the best item that meets all requirements
//         for (ItemRequirement item : allItems) {
//             if (item.meetsAllRequirements()) {
//                 if (ItemRequirement.validateItemSuitability(item)) {
//                     log.debug("Selected best item for equipment slot: {} (type: {}, priority: {}, rating: {})", 
//                             item.getName(), item.getRequirementType(), item.getPriority(), item.getRating());
//                     return item;
//                 }
//             }
//         }
        
//         return null;
//     }
    
//     /**
//      * Executes the inventory and equipment layout plan.
//      */
//     private boolean executeInventoryAndEquipmentPlan(InventorySetupPlanner plan) {
//         boolean allSuccess = true;
        
//         // Step 1: Execute equipment assignments
//         for (Map.Entry<EquipmentInventorySlot, ItemRequirement> entry : plan.getEquipmentAssignments().entrySet()) {
//             EquipmentInventorySlot slot = entry.getKey();
//             ItemRequirement item = entry.getValue();
            
//             if (!Rs2Equipment.isEquipped(item.getIds().get(0), slot)) {
//                 if (!item.fulfillRequirement()) {
//                     log.error("Failed to equip item for slot {}: {}", slot, item.getName());
//                     if (item.isMandatory()) {
//                         allSuccess = false;
//                     }
//                 }
//             }
//         }
        
//         // Step 2: Execute specific inventory slot assignments
//         for (Map.Entry<Integer, ItemRequirement> entry : plan.getInventorySlotAssignments().entrySet()) {
//             int slot = entry.getKey();
//             ItemRequirement item = entry.getValue();
            
//             // Check if the specific slot is available and withdraw item if needed
//             if (!ItemRequirement.hasItemInSpecificSlot(slot, item)) {
//                 if (!ItemRequirement.withdrawAndPlaceInSpecificSlot(slot, item)) {
//                     log.error("Failed to place item in inventory slot {}: {}", slot, item.getName());
//                     if (item.isMandatory()) {
//                         allSuccess = false;
//                     }
//                 }
//             }
//         }
        
//         // Step 3: Execute flexible inventory assignments
//         for (ItemRequirement item : plan.getFlexibleInventoryItems()) {
//             if (item.getInventoryCount() < item.getAmount()) {
//                 if (!item.fulfillRequirement()) {
//                     log.error("Failed to fulfill flexible inventory item: {}", item.getName());
//                     if (item.isMandatory()) {
//                         allSuccess = false;
//                     }
//                 }
//             }
//         }
        
//         return allSuccess;
//     }
    
//     /**
//      * Checks if a specific item is in a specific inventory slot.
//      * Uses Rs2Inventory.getItemInSlot() to verify the exact slot contents.
//      */
//     private boolean hasItemInSpecificSlot(int slot, ItemRequirement item) {
//         if (slot < 0 || slot > 27) {
//             return false;
//         }
        
//         // Get the item currently in the specific slot
//         Rs2ItemModel slotItem = Rs2Inventory.getItemInSlot(slot);
//         if (slotItem == null) {
//             return false; // Slot is empty
//         }
        
//         // Check if the slot contains one of the required item IDs
//         boolean hasCorrectItem = item.getIds().contains(slotItem.getId());
//         if (!hasCorrectItem) {
//             return false;
//         }
        
//         // Check if the quantity is sufficient
//         boolean hasSufficientQuantity = slotItem.getQuantity() >= item.getAmount();
//         if (!hasSufficientQuantity) {
//             log.debug("Slot {} has {} x{} but requires {} x{}", 
//                 slot, slotItem.getName(), slotItem.getQuantity(), item.getName(), item.getAmount());
//             return false;
//         }
        
//         log.debug("Slot {} correctly contains {} x{} (required: {} x{})", 
//             slot, slotItem.getName(), slotItem.getQuantity(), item.getName(), item.getAmount());
//         return true;
//     }
    
//     /**
//      * Withdraws an item and attempts to place it in a specific inventory slot.
//      * Creates a slot-specific copy of the requirement and fulfills it.
//      * Enhanced with additional validation and better slot management.
//      */
//     private boolean withdrawAndPlaceInSpecificSlot(int slot, ItemRequirement item) {
//         if (Microbot.getClient().isClientThread()) {
//             Microbot.log("Please run withdrawAndPlaceInSpecificSlot() on a non-client thread.", Level.ERROR);
//             return false;
//         }
        
//         try {
//             // Validate slot range
//             if (slot < 0 || slot > 27) {
//                 log.error("Invalid inventory slot: {}", slot);
//                 return false;
//             }
            
//             // Check if the item is already in the correct slot with sufficient quantity
//             if (ItemRequirement.hasItemInSpecificSlot(slot, item)) {
//                 log.debug("Item {} already in slot {} with sufficient quantity", item.getName(), slot);
//                 return true;
//             }
            
//             // Check if slot is occupied by a different item
//             Rs2ItemModel currentSlotItem = Rs2Inventory.getItemInSlot(slot);
//             if (currentSlotItem != null && !item.getIds().contains(currentSlotItem.getId())) {
//                 log.warn("Slot {} is occupied by {} but we need {}. May need to move items around.", 
//                         slot, currentSlotItem.getName(), item.getName());
//                 // For now, let the fulfillRequirement method handle this complexity
//             }
            
//             // Create a copy with the specific slot requirement
//             ItemRequirement slotSpecificItem = item.copyWithSpecificSlot(slot);
            
//             // Validate stackability constraints
//             if (slotSpecificItem.getAmount() > 1 && !slotSpecificItem.isStackable()) {
//                 log.error("Cannot place non-stackable item {} with amount {} in single slot {}", 
//                         slotSpecificItem.getName(), slotSpecificItem.getAmount(), slot);
//                 return false;
//             }
            
//             // Ensure bank is open for withdrawal operations
//             if (!Rs2Bank.isOpen()) {
//                 log.warn("Bank is not open for slot-specific withdrawal. Opening bank...");
//                 if (!Rs2Bank.walkToBankAndUseBank()) {
//                     log.error("Failed to open bank for slot-specific item placement");
//                     return false;
//                 }
//                 sleepUntil(() -> Rs2Bank.isOpen(), 5000);
//             }
            
//             // Fulfill the slot-specific requirement
//             boolean success = slotSpecificItem.fulfillRequirement();
            
//             if (success) {
//                 // Verify the item was placed correctly
//                 sleepUntil(() -> ItemRequirement.hasItemInSpecificSlot(slot, item), 3000);
//                 boolean verified = ItemRequirement.hasItemInSpecificSlot(slot, item);
//                 if (!verified) {
//                     log.warn("Item {} may not have been placed correctly in slot {}", item.getName(), slot);
//                 }
//                 return verified;
//             } else {
//                 log.error("Failed to fulfill slot-specific requirement for {} in slot {}", item.getName(), slot);
//                 return false;
//             }
            
//         } catch (Exception e) {
//             log.error("Error placing item in specific slot {}: {}", slot, e.getMessage(), e);
//             return false;
//         }
//     }
    
//     /**
//      * Determines if an item can be assigned to a specific inventory slot.
//      * Considers stackability, amount, and slot constraints.
//      */
//     private boolean canAssignToSpecificSlot(ItemRequirement item, int slot) {
//         // Basic slot range validation
//         if (slot < 0 || slot > 27) {
//             return false;
//         }
        
//         // Check stackability constraints
//         if (item.getAmount() > 1 && !item.isStackable()) {
//             log.debug("Non-stackable item {} with amount {} cannot fit in single slot {}", 
//                     item.getName(), item.getAmount(), slot);
//             return false;
//         }
        
//         // Check if the item itself allows slot assignment
//         return item.canFitInInventory();
//     }
    
//     /**
//      * Handles an item as a flexible inventory item instead of slot-specific.
//      * Breaks down non-stackable items with amount > 1 into individual items.
//      */
//     private void handleItemAsFlexible(ItemRequirement item, InventorySetupPlanner plan, Set<ItemRequirement> alreadyPlanned) {
//         if (item.getAmount() > 1 && !item.isStackable()) {
//             // Create multiple single-item requirements for non-stackable items
//             for (int i = 0; i < item.getAmount(); i++) {
//                 ItemRequirement singleItem = item.copyWithAmount(1);
//                 plan.addFlexibleInventoryItem(singleItem);
//                 log.debug("Created single flexible item {} ({}/{})", 
//                         singleItem.getName(), i + 1, item.getAmount());
//             }
//         } else {
//             // Stackable item or amount is 1
//             plan.addFlexibleInventoryItem(item);
//             log.debug("Added flexible item {} (amount: {})", item.getName(), item.getAmount());
//         }
//         alreadyPlanned.add(item); // Mark as planned
//     }
    
//     /**
//      * Handles missing mandatory items by adding them to the plan's missing items list.
//      */
//     private void handleMissingMandatoryItem(List<LogicalRequirement> contextReqs, InventorySetupPlanner plan, String slotDescription) {
//         boolean hasMandatory = contextReqs.stream()
//             .anyMatch(req -> LogicalRequirement.extractItemRequirementsFromLogical(req).stream()
//                 .anyMatch(ItemRequirement::isMandatory));
        
//         if (hasMandatory) {
//             // Find the first mandatory item for reporting
//             ItemRequirement mandatoryItem = contextReqs.stream()
//                 .flatMap(req -> LogicalRequirement.extractItemRequirementsFromLogical(req).stream())
//                 .filter(ItemRequirement::isMandatory)
//                 .findFirst()
//                 .orElse(null);
            
//             if (mandatoryItem != null) {
//                 plan.addMissingMandatoryInventoryItem(mandatoryItem);
//                 log.error("Missing mandatory item for {}: {}", slotDescription, mandatoryItem.getName());
//             }
//         }
//     }
    
//     /**
//      * Formats a logical requirement for logging display.
//      * 
//      * @param logicalReq The logical requirement to format
//      * @return A formatted string representation for logging
//      */
//     private String formatLogicalRequirementForLogging(LogicalRequirement logicalReq) {
//         if (logicalReq instanceof OrRequirement) {
//             OrRequirement orReq = (OrRequirement) logicalReq;
//             StringBuilder sb = new StringBuilder();
//             sb.append("OR(").append(orReq.getChildRequirements().size()).append(" options): ");
//             boolean first = true;
//             for (Requirement childReq : orReq.getChildRequirements()) {
//                 if (!first) sb.append(" | ");
//                 sb.append(childReq.getName()).append("[").append(childReq.getPriority().name()).append("]");
//                 first = false;
//             }
//             return sb.toString();
//         } else {
//             return String.format("%s [%s, Rating: %d, Context: %s]", 
//                     logicalReq.getName(), 
//                     logicalReq.getPriority().name(), 
//                     logicalReq.getRating(),
//                     logicalReq.getScheduleContext().name());
//         }
//     }
// }
//     /**
//      * Banks all equipped and inventory items that are not part of the planned loadout.
//      * This ensures a clean slate before executing the optimal layout plan.
//      * 
//      * @param plan The inventory setup plan containing the desired items
//      * @return true if banking was successful, false otherwise
//      */
//     private boolean bankItemsNotInPlan(InventorySetupPlanner plan) {
//         try {
//             log.info("Analyzing current equipment and inventory state...");
            
//             // Quick check: if plan is empty, bank everything
//             boolean hasEquipmentPlan = !plan.getEquipmentAssignments().isEmpty();
//             boolean hasInventoryPlan = !plan.getInventorySlotAssignments().isEmpty() || !plan.getFlexibleInventoryItems().isEmpty();
            
//             if (!hasEquipmentPlan && !hasInventoryPlan) {
//                 log.info("Plan is empty - banking all equipment and inventory items");
//             } else {
//                 log.info("Plan has {} equipment assignments, {} specific inventory slots, {} flexible items", 
//                         plan.getEquipmentAssignments().size(), 
//                         plan.getInventorySlotAssignments().size(), 
//                         plan.getFlexibleInventoryItems().size());
//             }
            
//             // Ensure bank is open
//             if (!Rs2Bank.isOpen()) {
//                 log.warn("Bank is not open for cleanup banking. Opening bank...");
//                 if (!Rs2Bank.walkToBankAndUseBank()) {
//                     log.error("Failed to open bank for cleanup banking");
//                     return false;
//                 }
//                 sleepUntil(() -> Rs2Bank.isOpen(), 5000);
//             }
            
//             boolean bankingSuccess = true;
//             int itemsBanked = 0;
            
//             // Step 1: Bank equipped items not in the plan
//             log.info("Banking equipped items not in planned loadout...");
//             for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
//                 // Check if this slot is planned to have an item
//                 ItemRequirement plannedItem = plan.getEquipmentAssignments().get(slot);
                
//                 // Get currently equipped item in this slot
//                 Rs2ItemModel equippedItem = Rs2Equipment.get(slot);
                
//                 if (equippedItem != null) { // Something is equipped in this slot
//                     boolean shouldKeepEquipped = false;
                    
//                     if (plannedItem != null) {
//                         // Check if the currently equipped item matches the planned item
//                         shouldKeepEquipped = plannedItem.getIds().contains(equippedItem.getId());
//                     }
                    
//                     if (!shouldKeepEquipped) {
//                         // Unequip the item (this will move it to inventory, then we can bank it)
//                         log.info("Unequipping item from slot {}: {}", slot.name(), equippedItem.getName());
                        
//                         if (Rs2Equipment.interact(item -> item.getSlot() == slot.getSlotIdx(), "remove")) {
//                             sleepUntil(() -> Rs2Equipment.get(slot) == null, 3000);
                            
//                             // Now bank the item from inventory
//                             if (Rs2Inventory.hasItem(equippedItem.getId())) {
//                                 Rs2Bank.depositOne(equippedItem.getId());
//                                 sleepUntil(() -> !Rs2Inventory.hasItem(equippedItem.getId()), 3000);
//                                 itemsBanked++;
//                             }
//                         } else {
//                             log.warn("Failed to unequip item from slot {}", slot.name());
//                             bankingSuccess = false;
//                         }
//                     } else {
//                         log.debug("Keeping equipped item in slot {}: matches planned loadout", slot.name());
//                     }
//                 }
//             }
            
//             // Step 2: Bank inventory items not in the plan
//             log.info("Banking inventory items not in planned loadout...");
            
//             // Collect all planned item IDs for quick lookup
//             Set<Integer> plannedItemIds = new HashSet<>();
            
//             // Add planned equipment item IDs
//             for (ItemRequirement equippedItem : plan.getEquipmentAssignments().values()) {
//                 plannedItemIds.addAll(equippedItem.getIds());
//             }
            
//             // Add planned specific slot item IDs
//             for (ItemRequirement slotItem : plan.getInventorySlotAssignments().values()) {
//                 plannedItemIds.addAll(slotItem.getIds());
//             }
            
//             // Add planned flexible item IDs
//             for (ItemRequirement flexibleItem : plan.getFlexibleInventoryItems()) {
//                 plannedItemIds.addAll(flexibleItem.getIds());
//             }
            
//             // Check each inventory slot
//             for (int slot = 0; slot < 28; slot++) {
//                 Rs2ItemModel currentItem = Rs2Inventory.getItemInSlot(slot);
//                 if (currentItem != null) {
//                     boolean shouldKeepInInventory = false;
                    
//                     // Check if this item is part of the planned loadout
//                     if (plannedItemIds.contains(currentItem.getId())) {
//                         // Further check: is this item in the right place according to the plan?
//                         shouldKeepInInventory = isItemInCorrectPlanPosition(currentItem, slot, plan);
//                     }
                    
//                     if (!shouldKeepInInventory) {
//                         // Bank the item
//                         log.info("Banking inventory item from slot {}: {} x{}", 
//                                 slot, currentItem.getName(), currentItem.getQuantity());
                        
//                         if (Rs2Bank.depositOne(currentItem.getId())) {
//                             sleepUntil(() -> Rs2Inventory.getItemInSlot(slot) == null || 
//                                      Rs2Inventory.getItemInSlot(slot).getId() != currentItem.getId(), 3000);
//                             itemsBanked++;
//                         } else {
//                             log.warn("Failed to bank item from inventory slot {}: {}", slot, currentItem.getName());
//                             bankingSuccess = false;
//                         }
//                     } else {
//                         log.debug("Keeping inventory item in slot {}: {} (matches planned position)", 
//                                 slot, currentItem.getName());
//                     }
//                 }
//             }
            
//             if (bankingSuccess) {
//                 log.info("Successfully banked {} items not in planned loadout", itemsBanked);
//             } else {
//                 log.warn("Banking completed with some failures. {} items banked.", itemsBanked);
//             }
            
//             return bankingSuccess;
            
//         } catch (Exception e) {
//             log.error("Error banking items not in plan: {}", e.getMessage(), e);
//             return false;
//         }
//     }
    
//     /**
//      * Checks if an item is in the correct position according to the plan.
//      * This considers both specific slot assignments and flexible item allowances.
//      * 
//      * @param currentItem The item currently in inventory
//      * @param currentSlot The slot where the item is currently located
//      * @param plan The inventory setup plan
//      * @return true if the item should stay in its current position, false if it should be banked
//      */
//     private boolean isItemInCorrectPlanPosition(Rs2ItemModel currentItem, int currentSlot, InventorySetupPlanner plan) {
//         int itemId = currentItem.getId();
//         int itemQuantity = currentItem.getQuantity();
        
//         // Check if this slot has a specific assignment that matches
//         ItemRequirement specificSlotItem = plan.getInventorySlotAssignments().get(currentSlot);
//         if (specificSlotItem != null) {
//             boolean idMatches = specificSlotItem.getIds().contains(itemId);
//             boolean quantityIsEnough = itemQuantity >= specificSlotItem.getAmount();
            
//             if (idMatches && quantityIsEnough) {
//                 log.debug("Item {} in slot {} matches specific slot assignment", currentItem.getName(), currentSlot);
//                 return true;
//             }
//         }
        
//         // Check if this item is part of flexible items and we have room for it
//         for (ItemRequirement flexibleItem : plan.getFlexibleInventoryItems()) {
//             if (flexibleItem.getIds().contains(itemId)) {
//                 // Item matches a flexible requirement
//                 // Check if this quantity is exactly what we need or if we have excess
//                 boolean quantityMatches = itemQuantity == flexibleItem.getAmount();
                
//                 if (quantityMatches) {
//                     log.debug("Item {} in slot {} matches flexible requirement with exact quantity, keeping in place", 
//                             currentItem.getName(), currentSlot);
//                     return true; // Keep it since it's exactly what we need
//                 } else {
//                     log.debug("Item {} in slot {} matches flexible requirement but quantity differs (has: {}, needs: {}), will be re-placed by plan", 
//                             currentItem.getName(), currentSlot, itemQuantity, flexibleItem.getAmount());
//                     return false; // Bank it and let the plan handle correct quantity
//                 }
//             }
//         }
        
//         // Check if this is part of an equipment item that might be in inventory temporarily
//         for (ItemRequirement equippedItem : plan.getEquipmentAssignments().values()) {
//             if (equippedItem.getIds().contains(itemId)) {
//                 // This should be equipped, not in inventory
//                 log.debug("Item {} in slot {} should be equipped, banking from inventory", 
//                         currentItem.getName(), currentSlot);
//                 return false; // Bank it so it can be properly equipped
//             }
//         }
        
//         // Item is not part of the plan - should be banked
//         return false;
//     }
}
