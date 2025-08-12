package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.OrRequirementMode;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry.RequirementRegistry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.InventorySetupPlanner;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.OrderedRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.LogicalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util.RequirementSolver;
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
    @Setter
    @Getter
    private boolean initialized = false;
    // Centralized requirement management

    private final RequirementRegistry registry = new RequirementRegistry();
    
    /**
     * Mode for handling OR requirements during planning.
     * Default is ANY_COMBINATION for backward compatibility.
     */
    @Getter
    @Setter
    private OrRequirementMode orRequirementMode = OrRequirementMode.ANY_COMBINATION;
    
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
    protected abstract boolean initializeRequirements();
    public abstract void reset();

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
     * Adds a custom requirement to this requirements collection.
     * Custom requirements are marked with CUSTOM type and are fulfilled after all standard requirements.
     * 
     * @param requirement The requirement to add
     * @param scheduleContext The context in which this requirement should be fulfilled
     * @return true if the requirement was successfully added, false otherwise
     */
    public boolean addCustomRequirement(Requirement requirement, ScheduleContext scheduleContext) {
        if (requirement == null) {
            log.warn("Cannot add null custom requirement");
            return false;
        }
        
        if (scheduleContext == null) {
            log.warn("Cannot add custom requirement without schedule context");
            return false;
        }
        
        try {
            // Update the requirement's schedule context if needed
            if (requirement.getScheduleContext() != scheduleContext && 
                requirement.getScheduleContext() != ScheduleContext.BOTH) {
                requirement.setScheduleContext(scheduleContext);
            }
            
            // Register in the registry as an external requirement
            boolean registered = registry.registerExternal(requirement);
            
            if (registered) {
                log.info("Successfully registered custom requirement: {} for context: {}", 
                    requirement.getDescription(), scheduleContext);
                return true;
            } else {
                log.warn("Failed to register custom requirement in registry: {}", 
                    requirement.getDescription());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error adding custom requirement: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets all mandatory equipment items organized by slot.
     * 
     * @return A map of equipment slot to list of mandatory equipment requirements
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> getEquipmentItemsBySlot(RequirementPriority priority) {
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
    public LinkedHashSet<ItemRequirement> getInventoryItems(RequirementPriority priority) {
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
    public boolean validateItems(RequirementPriority priority) {
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
     * Gets the most effective item for a specific equipment slot.
     */
    public Optional<ItemRequirement> getBestItemForSlot(EquipmentInventorySlot slot) {
        LinkedHashSet<ItemRequirement> items = registry.getEquipmentSlotItems().getOrDefault(slot, new LinkedHashSet<>());
        return items.isEmpty() ? Optional.empty() : Optional.of(items.iterator().next());
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
     * Gets all equipment slots that have recommended items.
     */
    public Set<EquipmentInventorySlot> getEquipmentSlots() {
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = registry.getEquipmentSlotItems();
        return equipmentItems.keySet();
    }
     /**
     * Gets all pritems across all categories.
     */
    public LinkedHashSet<ItemRequirement> getItems(RequirementPriority priority) {
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
    public boolean fulfillPreScheduleLocationRequirement(CompletableFuture<Boolean> scheduledFuture) {
        LocationRequirement preScheduleLocationRequirement = registry.getPreScheduleLocationRequirement();
        if (preScheduleLocationRequirement == null) {
            log.debug("No pre-schedule location requirement to fulfill");
            return true; // No pre-schedule location requirement
        }
        
        // Update state tracking for this specific requirement
        updateFulfillmentStep(FulfillmentStep.LOCATION, "Traveling to " + preScheduleLocationRequirement.getName(), 1);
        updateCurrentRequirement(preScheduleLocationRequirement, 1);
        log.debug("Processing pre-schedule location requirement: {}", preScheduleLocationRequirement.getName());
        
        return preScheduleLocationRequirement.fulfillRequirement(scheduledFuture);
    }
    
    /**
     * Attempts to fulfill the post-schedule location requirement.
     * This method should be called after the script completes.
     * 
     * @return true if positioning was successful or no requirement exists, false otherwise
     */
    public boolean fulfillPostScheduleLocationRequirement(CompletableFuture<Boolean> scheduledFuture) {
        LocationRequirement postScheduleLocationRequirement = registry.getPostScheduleLocationRequirement();
        if (postScheduleLocationRequirement == null) {
            log.debug("No post-schedule location requirement to fulfill");
            return true; // No post-schedule location requirement
        }
        
        // Update state tracking for this specific requirement
        updateFulfillmentStep(FulfillmentStep.LOCATION, "Traveling to " + postScheduleLocationRequirement.getName(), 1);
        updateCurrentRequirement(postScheduleLocationRequirement, 1);
        log.debug("Processing post-schedule location requirement: {}", postScheduleLocationRequirement.getName());
        
        return postScheduleLocationRequirement.fulfillRequirement(scheduledFuture);
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
    public boolean fulfillPreScheduleSpellbookRequirement(CompletableFuture<Boolean> scheduledFuture) {
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
        
        return preScheduleSpellbookRequirement.fulfillRequirement(scheduledFuture);
    }
    
    /**
     * Attempts to fulfill the post-schedule spellbook requirement.
     * This method should be called after the script completes.
     * 
     * @return true if spellbook switching was successful or no requirement exists, false otherwise
     */
    public boolean fulfillPostScheduleSpellbookRequirement( CompletableFuture<Boolean> scheduledFuture) {
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
        
        return postScheduleSpellbookRequirement.fulfillRequirement(scheduledFuture);
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
            log.debug("Clearing saved original spellbook: " + originalSpellbook);
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
            log.debug("Requirement already registered: " + requirement.getName(), Level.WARN);
            return; // Avoid duplicate registration
        }
        registry.register(requirement);
    }
    
    /**
     * Fulfills all requirements for the specified schedule context.
     * This is a convenience method that calls all the specific fulfillment methods.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @param saveCurrentSpellbook Whether to save the current spellbook for restoration
     * @return true if all requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillAllRequirements(CompletableFuture<Boolean> scheduledFuture, ScheduleContext context, boolean saveCurrentSpellbook) {
        boolean success = true;
        ScheduledExecutorService cancellationWatchdogService = null;
        ScheduledFuture<?> cancellationWatchdog = null;

        try {
            // Fulfill requirements in logical order -> we should always fulfill loot requirements first, then shop, then item, then spellbook, and finally location requirements
            // when adding new requirements, make sure to follow this order or think about the order in which they should be fulfilled
            // we can also think about changing the order for pre and post schedule requirements, but for now we will keep it the same
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

        // Start cancellation watchdog if we have a scheduledFuture to monitor
        if (scheduledFuture != null) {
            cancellationWatchdogService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "RequirementFulfillment-CancellationWatchdog-" + context);
                thread.setDaemon(true);
                return thread;
            });
            
            cancellationWatchdog = startCancellationWatchdog(cancellationWatchdogService, scheduledFuture, context);
            log.debug("Started cancellation watchdog for requirement fulfillment: {}", context);
        }

        log.info("\n" + "=".repeat(80));
        log.info("FULFILLING REQUIREMENTS FOR CONTEXT: {}", context);
        log.info("Collection: {} | Activity: {} | Wilderness: {}", collectionName, activityType, isWildernessCollection);
        log.info("=".repeat(80));
        
        // Display complete registry information
        log.info("\n=== COMPLETE REQUIREMENT REGISTRY ===");
        log.info(registry.getDetailedRegistryString());
        
        // Step 0: Conditional and Ordered Requirements (execute first as they may contain prerequisites)
        List<ConditionalRequirement> conditionalReqs = this.registry.getStandardRequirements(ConditionalRequirement.class, context);
        List<OrderedRequirement> orderedReqs = this.registry.getStandardRequirements(OrderedRequirement.class, context);
        
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
        success &= fulfillConditionalRequirements(scheduledFuture, context);
        
        // Check for cancellation after conditional requirements
        if (scheduledFuture != null && scheduledFuture.isCancelled()) {
            log.warn("Requirements fulfillment cancelled after conditional requirements");
            return false;
        }
        
        if (!success) {
            executionState.markFailed("Failed to fulfill conditional requirements");
            return false;
        }
        
        // Step 2: Loot Requirements
        List<LootRequirement> lootReqs = this.registry.getStandardRequirements(LootRequirement.class, context);
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
        success &= fulfillPrePostLootRequirements(scheduledFuture, context);
        
        // Check for cancellation after loot requirements
        if (scheduledFuture != null && scheduledFuture.isCancelled()) {
            log.warn("Requirements fulfillment cancelled after loot requirements");
            return false;
        }
        
        if (!success) {
            executionState.markFailed("Failed to fulfill loot requirements");
            return false;
        }
        
        // Step 3: Shop Requirements
        List<ShopRequirement> shopReqs = this.registry.getStandardRequirements(ShopRequirement.class, context);
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
        success &= fulfillPrePostShopRequirements(scheduledFuture, context);
        
        // Check for cancellation after shop requirements
        if (scheduledFuture != null && scheduledFuture.isCancelled()) {
            log.warn("Requirements fulfillment cancelled after shop requirements");
            return false;
        }
        
        if (!success) {
            executionState.markFailed("Failed to fulfill shop requirements");
            return false;
        }
        
        // Step 4: Item Requirements
        StringBuilder itemReqInfo = new StringBuilder();
        RequirementRegistry.RequirementBreakdown itemReqBreakdown = this.registry.getItemRequirementBreakdown( context);
        itemReqInfo.append("\n=== STEP 4: ITEM REQUIREMENTS ===\n");
        if (itemReqBreakdown.isEmpty()) {
            itemReqInfo.append(String.format("No item requirements for context: %s\n", context));
        } else {           
            
            itemReqInfo.append(itemReqBreakdown.getDetailedBreakdownString());
            
            /**for (int i = 0; i < itemReqs.size(); i++) {
               itemReqInfo.append(String.format("--- Item Requirement %d ---\n", i + 1));
                itemReqInfo.append(itemReqs.get(i).displayString()).append("\n");
            }**/
           itemReqInfo.append(this.registry.getDetailedCacheStringForContext(context));
           
            
            
        }
        
        log.info(itemReqInfo.toString());
        executionState.updateFulfillmentStep(FulfillmentStep.ITEMS, "Preparing inventory and equipment");
        success &= fulfillPrePostItemRequirements(scheduledFuture, context);
        
        // Check for cancellation after item requirements
        if (scheduledFuture != null && scheduledFuture.isCancelled()) {
            log.warn("Requirements fulfillment cancelled after item requirements");
            return false;
        }
        
        if (!success) {
            executionState.markFailed("Failed to fulfill item requirements");
            return false;
        }
        
        // Step 5: Spellbook Requirements
        List<SpellbookRequirement> spellbookReqs = this.registry.getStandardRequirements(SpellbookRequirement.class, context);
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
        success &= fulfillPrePostSpellbookRequirements(scheduledFuture, context, saveCurrentSpellbook);
        
        // Check for cancellation after spellbook requirements
        if (scheduledFuture != null && scheduledFuture.isCancelled()) {
            log.warn("Requirements fulfillment cancelled after spellbook requirements");
            return false;
        }
        
        if (!success) {
            executionState.markFailed("Failed to fulfill spellbook requirements");
            return false;
        }
        
        // Step 6: Location Requirements (always fulfill location requirements last)
        List<LocationRequirement> locationReqs = this.registry.getStandardRequirements(LocationRequirement.class, context);
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
        success &= fulfillPrePostLocationRequirements(scheduledFuture, context);
        
        // Check for cancellation after location requirements
        if (scheduledFuture != null && scheduledFuture.isCancelled()) {
            log.warn("Requirements fulfillment cancelled after location requirements");
            return false;
        }
        
        // Step 7: Handle External Requirements (added by plugins or external systems)
        if (success) {
            List<Requirement> externalRequirements = registry.getExternalRequirements(context);
            
            StringBuilder externalReqInfo = new StringBuilder();
            externalReqInfo.append("\n=== STEP 7: EXTERNAL REQUIREMENTS ===\n");
            if (externalRequirements.isEmpty()) {
                externalReqInfo.append(String.format("No external requirements for context: %s\n", context));
            } else {
                externalReqInfo.append(String.format("Found %d external requirement(s):\n", externalRequirements.size()));
                for (int i = 0; i < externalRequirements.size(); i++) {
                    externalReqInfo.append(String.format("\n--- External Requirement %d ---\n", i + 1));
                    externalReqInfo.append(externalRequirements.get(i).displayString()).append("\n");
                }
            }
            log.info(externalReqInfo.toString());
            
            if (!externalRequirements.isEmpty()) {
                executionState.updateFulfillmentStep(FulfillmentStep.EXTERNAL_REQUIREMENTS, "Fulfilling external requirements");
                
                for (Requirement externalReq : externalRequirements) {
                    try {
                        log.info("\nFulfilling external requirement: \n\t{}", externalReq.getDescription());
                        boolean externalSuccess = externalReq.fulfillRequirement(scheduledFuture);
                        if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                            log.warn("Executor service is shutdown, skipping external requirement fulfillment: {}", externalReq.getDescription());
                            return false; // Skip if executor service is shutdown
                        }
                        if (!externalSuccess) {
                            log.error("Failed to fulfill external requirement: {}", externalReq.getDescription());
                            success = false;
                            break;
                        } else {
                            log.info("Successfully fulfilled external requirement: {}", externalReq.getDescription());
                        }
                    } catch (Exception e) {
                        log.error("Error fulfilling external requirement: {}", externalReq.getDescription(), e);
                        success = false;
                        break;
                    }
                }
                
                if (success) {
                    log.info("All external requirements fulfilled successfully");
                } else {
                    log.error("Failed to fulfill external requirements");
                }
            } else {
                log.info("External requirements step completed - no external requirements to fulfill");
            }
        }
        
        if (success) {
            executionState.updateState(TaskExecutionState.ExecutionState.COMPLETED, "All requirements fulfilled successfully");
            log.info("\n" + "=".repeat(80) + "\nALL REQUIREMENTS FULFILLED SUCCESSFULLY FOR CONTEXT: {}\n" + "=".repeat(80), context);
        } else {
            executionState.markFailed("Failed to fulfill location requirements");
            log.error("\n" + "=".repeat(80) + "\nFAILED TO FULFILL REQUIREMENTS FOR CONTEXT: {}\n" + "=".repeat(80), context);
        }
        
        return success;
        } finally {
            // Always clean up the watchdog
            if (cancellationWatchdog != null && !cancellationWatchdog.isDone()) {
                cancellationWatchdog.cancel(true);
            }
            
            // Shutdown the executor service
            if (cancellationWatchdogService != null) {
                cancellationWatchdogService.shutdown();
                try {
                    if (!cancellationWatchdogService.awaitTermination(2, TimeUnit.SECONDS)) {
                        cancellationWatchdogService.shutdownNow();
                        if (!cancellationWatchdogService.awaitTermination(1, TimeUnit.SECONDS)) {
                            log.warn("Cancellation watchdog executor service did not terminate cleanly for context: {}", context);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancellationWatchdogService.shutdownNow();
                }
            }
        }
    }
    
    /**
     * Starts a cancellation watchdog that monitors for task cancellation and stops any ongoing walking operations.
     * This prevents walking operations from continuing when the overall requirement fulfillment has been cancelled.
     * 
     * @param executorService The executor service to run the watchdog on
     * @param scheduledFuture The future to monitor for cancellation
     * @param context The schedule context for logging purposes
     * @return The scheduled future for the watchdog task
     */
    private ScheduledFuture<?> startCancellationWatchdog(   ScheduledExecutorService executorService, 
                                                            CompletableFuture<Boolean> scheduledFuture, 
                                                            ScheduleContext context) {
        return executorService.scheduleAtFixedRate(() -> {
            try {
                // Check for cancellation
                if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                    log.info("Requirement fulfillment cancellation watchdog triggered for context: {}", context);
                    
                    // Stop any ongoing walking by clearing the walker target
                    Rs2Walker.setTarget(null);
                    
                    // Cancel this watchdog by throwing an exception
                    throw new RuntimeException("Requirement fulfillment cancelled - stopping walking operations");
                }
            } catch (Exception e) {
                log.debug("Cancellation watchdog stopping for context {}: {}", context, e.getMessage());
                throw e; // Re-throw to stop the scheduled task
            }
        }, 2, 2, TimeUnit.SECONDS); // Check every 2 seconds (more frequent than location watchdog)
    }
    
    /**
     * Convenience method to fulfill all pre-schedule requirements.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @param saveCurrentSpellbook Whether to save current spellbook for restoration
     * @return true if all pre-schedule requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPreScheduleRequirements(CompletableFuture<Boolean> scheduledFuture, boolean saveCurrentSpellbook) {
        return fulfillAllRequirements(scheduledFuture,ScheduleContext.PRE_SCHEDULE, saveCurrentSpellbook);
    }
    
  
    
    /**
     * Convenience method to fulfill all post-schedule requirements.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @param saveCurrentSpellbook Whether to save current spellbook for restoration
     * @return true if all post-schedule requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPostScheduleRequirements(CompletableFuture<Boolean> scheduledFuture, boolean saveCurrentSpellbook) {
        return fulfillAllRequirements(scheduledFuture, ScheduleContext.POST_SCHEDULE, saveCurrentSpellbook);
    }
    

    // === UNIFIED REQUIREMENT FULFILLMENT FUNCTIONS ===
    
    /**
     * Fulfills all item requirements (equipment, inventory, either) for the specified schedule context.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all item requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostItemRequirements(CompletableFuture<Boolean> scheduledFuture, ScheduleContext context) {
        // Get count of logical requirements for this context using the unified API
        int logicalReqsCount = registry.getItemCount(context);
        
        if (logicalReqsCount == 0) {
            log.debug("No item requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        
        // Initialize step tracking
        updateFulfillmentStep(FulfillmentStep.ITEMS, "Processing item requirements", logicalReqsCount);
        
        boolean success = true;
        success = fulfillOptimalInventoryAndEquipmentLayout(scheduledFuture, context);
        
        
        log.debug("Item requirements fulfillment completed. Success: {}", success);
        return success;
    }
            
    /**
     * Fulfills shop requirements for the specified schedule context.
     * Uses the unified RequirementSolver to handle both standard and external requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @return true if all shop requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostShopRequirements(CompletableFuture<Boolean> scheduledFuture,ScheduleContext context ) {
        LinkedHashSet<LogicalRequirement> shopLogical = registry.getStandardShopLogicalRequirements();
        
        if (shopLogical.isEmpty()) {
            log.debug("No shop requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        
        // Initialize step tracking
        List<LogicalRequirement> contextReqs = LogicalRequirement.filterByContext(new ArrayList<>(shopLogical), context);
        updateFulfillmentStep(FulfillmentStep.SHOP, "Processing shop requirements", contextReqs.size());
        log.info("Processing shop requirements for context: {} number of  req.", context, contextReqs.size());
        // Use the utility class for fulfillment
        return LogicalRequirement.fulfillLogicalRequirements(scheduledFuture,contextReqs, "shop");
    }
    
  
    /**
     * Fulfills loot requirements for the specified schedule context.
     * Uses the unified RequirementSolver to handle both standard and external requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @return true if all loot requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostLootRequirements(CompletableFuture<Boolean> scheduledFuture, ScheduleContext context ) {
        // Get requirements count for step tracking
        LinkedHashSet<LogicalRequirement> lootLogical = registry.getStandardLootLogicalRequirements();
        
        if (lootLogical.isEmpty()) {
            log.debug("No loot requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        
        // Initialize step tracking
        List<LogicalRequirement> contextReqs = LogicalRequirement.filterByContext(new ArrayList<>(lootLogical), context);
        updateFulfillmentStep(FulfillmentStep.LOOT, "Collecting loot items", contextReqs.size());
        
        // Use the utility class for fulfillment
        return LogicalRequirement.fulfillLogicalRequirements(scheduledFuture,contextReqs, "loot");
               
       
    }
    

    
    /**
     * Fulfills location requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all location requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostLocationRequirements(CompletableFuture<Boolean> scheduledFuture, ScheduleContext context) {
        // Get requirements count for step tracking
        List<LocationRequirement> locationReqs = this.registry.getStandardRequirements(LocationRequirement.class, context);
        
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
                boolean fulfilled = requirement.fulfillRequirement(scheduledFuture);
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
     * Fulfills spellbook requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param saveCurrentSpellbook Whether to save the current spellbook before switching (for pre-schedule)
     * @return true if all spellbook requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillPrePostSpellbookRequirements(CompletableFuture<Boolean> scheduledFuture, ScheduleContext context, boolean saveCurrentSpellbook) {
        List<SpellbookRequirement> spellbookReqs = this.registry.getStandardRequirements(SpellbookRequirement.class, context);
        
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
            log.debug("Saved original spellbook: " + originalSpellbook + " before switching for pre-schedule requirements");
        }
      
        for (int i = 0; i < spellbookReqs.size(); i++) {
            SpellbookRequirement requirement = spellbookReqs.get(i);
            
            // Update current requirement tracking
            updateCurrentRequirement(requirement, i + 1);
            
            try {
                log.debug("Processing spellbook requirement {}/{}: {}", i + 1, spellbookReqs.size(), requirement.getName());
                boolean fulfilled = requirement.fulfillRequirement(scheduledFuture);
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
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @return true if all conditional requirements were fulfilled successfully, false otherwise
     */
    public boolean fulfillConditionalRequirements(CompletableFuture<Boolean> scheduledFuture, ScheduleContext context ) {
        // Get requirements count for step tracking
        List<ConditionalRequirement> conditionalReqs = this.registry.getStandardRequirements(ConditionalRequirement.class, context);
        List<OrderedRequirement> orderedReqs = this.registry.getStandardRequirements(OrderedRequirement.class, context);
        
        // Initialize step tracking
        int totalReqs = conditionalReqs.size() + orderedReqs.size();
        updateFulfillmentStep(FulfillmentStep.CONDITIONAL, 
                "Processing " + totalReqs + " conditional/ordered requirement(s)", totalReqs);
        
        // Use the utility class for fulfillment
        return RequirementSolver.fulfillConditionalRequirements(scheduledFuture,conditionalReqs, orderedReqs, context);
              
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
     * Comprehensive inventory and equipment layout planning and fulfillment system.
     * This method analyzes all requirements and creates optimal item placement maps,
     * considering slot constraints, priority levels, and availability.
     * 
     * @param context The schedule context
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if all mandatory requirements can be fulfilled
     */
    private boolean fulfillOptimalInventoryAndEquipmentLayout(CompletableFuture<Boolean> scheduledFuture, ScheduleContext context) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n" + "=".repeat(60));
            sb.append("\n\tOPTIMAL INVENTORY AND EQUIPMENT LAYOUT PLANNING");
            sb.append("\n\tContext: "+ context +"| Collection: "+collectionName).append("\n");
            sb.append("=".repeat(60));
            log.info(sb.toString());
            // Ensure bank is open for all operations
            if (!Rs2Bank.isOpen()) {
                log.info("\n\tBank is not open, attempting to open bank for comprehensive item management");
                if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Player.isInteracting() && !Rs2Player.isMoving()) {
                   log.error("\n\tFailed to open bank for comprehensive item management");                    
                }
                boolean openBank= sleepUntil(() -> Rs2Bank.isOpen(), 5000);
                if (!openBank) {
                    log.error("\n\tFailed to open bank within timeout for context: {}", context);
                    return false;
                }
            }

            // Step 1: Analyze all requirements and create constraint maps
            log.info("\n--- Step 1: Analyzing Requirements and Creating Layout Plan ---");
            InventorySetupPlanner layoutPlan = analyzeRequirementsAndCreateLayoutPlan(scheduledFuture,context);
            
            if (layoutPlan == null) {
                log.error("Failed to create inventory layout plan");
                return false;
            }
            
            // Display detailed plan information
            log.info("\n--- Generated Layout Plan ---");
            log.info("\n"+layoutPlan.getDetailedPlanString());
            
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
            log.info("\n"+layoutPlan.getOccupiedSlotsSummary());
            
            // Step 2.5: Convert plan to InventorySetup and add to plugin BEFORE execution
            log.info("\n--- Step 2.5: Creating InventorySetup from Plan ---");
            String inventorySetupName = "Optimal_Setup_" + collectionName + "_" + context.name();
            InventorySetup createdSetup = layoutPlan.addToInventorySetupsPlugin(inventorySetupName);
            
            if (createdSetup == null) {
                log.error("Failed to create InventorySetup from plan");
                return false;
            }
            
            log.info("Successfully created InventorySetup: {}", createdSetup.getName());
            
            // Step 3: Execute using Rs2InventorySetup approach
            log.info("\n--- Step 3: Executing Plan Using Rs2InventorySetup ---");
            boolean success = layoutPlan.executeUsingRs2InventorySetup(scheduledFuture, createdSetup.getName());
            
            if (success) {
                log.info("\n" + "=".repeat(60));
                log.info("SUCCESSFULLY EXECUTED OPTIMAL INVENTORY AND EQUIPMENT LAYOUT");
                log.info("Used Rs2InventorySetup approach with setup: {}", createdSetup.getName());
                log.info("=".repeat(60));
            } else {
                log.error("\n" + "=".repeat(60));
                log.error("FAILED TO EXECUTE INVENTORY AND EQUIPMENT LAYOUT PLAN");
                log.error("Rs2InventorySetup approach failed for setup: {}", createdSetup.getName());
                log.error("=".repeat(60));
            }
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                log.info("Closed bank after inventory and equipment fulfillment");
            }
            return success;

        } catch (Exception e) {
            log.error("\nError in comprehensive inventory and equipment fulfillment: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Analyzes all requirements and creates an optimal inventory and equipment layout plan.
     * Uses the enhanced InventorySetupPlanner with requirement registry support.
     * 
     * @param scheduledFuture The scheduled future for cancellation checking
     * @param context The schedule context (PRE_SCHEDULE or POST_SCHEDULE)
     * @return The created inventory setup plan, or null if planning failed
     */
    private InventorySetupPlanner analyzeRequirementsAndCreateLayoutPlan(CompletableFuture<?> scheduledFuture, ScheduleContext context) {
        // Create enhanced planner with registry and OR requirement mode
        InventorySetupPlanner plan = new InventorySetupPlanner(registry, context, orRequirementMode);
        
        // Create the plan from requirements
        boolean planningSuccessful = plan.createPlanFromRequirements();
        
        if (!planningSuccessful) {
            log.error("Failed to create inventory setup plan for context: {}", context);
            return null;
        }
        
        log.info("Successfully created inventory setup plan for context: {} with OR mode: {}", context, orRequirementMode);
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
    public String getDetailedDisplay(){
        StringBuilder sb = new StringBuilder();
        sb.append("=== Pre/Post Schedule Requirements Summary ===\n");
        sb.append("Collection: ").append(collectionName).append("\n");
        sb.append("Current Spellbook: ").append(Rs2Spellbook.getCurrentSpellbook()).append("\n");
        if (originalSpellbook != null) {
            sb.append("Original Spellbook: ").append(originalSpellbook).append("\n");
        }
        sb.append("Total Pre\\Post Requirements Registered: ").append(getRegistry().getAllRequirements().size()).append("\n");
        
        // Pre-Schedule Requirements
        sb.append(" Pre Requirements Registered: ").append(this.registry.getStandardRequirements(ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Spellbook Requirements: ").append(this.registry.getStandardRequirements(SpellbookRequirement.class,ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Location Requirements: ").append(this.registry.getStandardRequirements(LocationRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Loot Requirements: ").append(this.registry.getStandardRequirements(LootRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        
        // Equipment Requirements breakdown
        RequirementRegistry.RequirementBreakdown preEquipBreakdown = registry.getItemRequirementBreakdown(ScheduleContext.PRE_SCHEDULE);
        sb.append("    - Equipment Requirements: ").append(preEquipBreakdown.getTotalEquipmentCount()).append("\n");
        sb.append("      └─ Mandatory: ").append(preEquipBreakdown.getEquipmentCount(RequirementPriority.MANDATORY)).append(", ");
        sb.append("Recommended: ").append(preEquipBreakdown.getEquipmentCount(RequirementPriority.RECOMMENDED)).append(", ");
        sb.append("Optional: ").append(preEquipBreakdown.getEquipmentCount(RequirementPriority.RECOMMENDED)).append("\n");
        
        // Equipment slot details for Pre
        Map<EquipmentInventorySlot, Map<RequirementPriority, Integer>> preEquipSlots = preEquipBreakdown.getEquipmentSlotBreakdown();
        if (!preEquipSlots.isEmpty()) {
            sb.append("      └─ Equipment Slots Detail:\n");
            for (Map.Entry<EquipmentInventorySlot, Map<RequirementPriority, Integer>> entry : preEquipSlots.entrySet()) {
                EquipmentInventorySlot slot = entry.getKey();
                Map<RequirementPriority, Integer> counts = entry.getValue();
                sb.append("         ").append(slot.name()).append(": M=").append(counts.getOrDefault(RequirementPriority.MANDATORY, 0))
                  .append(", R=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0))
                  .append(", O=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0)).append("\n");
            }
        }
        
        // Inventory Requirements breakdown
        sb.append("    - Inventory Requirements: ").append(preEquipBreakdown.getTotalInventoryCount()).append("\n");
        sb.append("      └─ Mandatory: ").append(preEquipBreakdown.getInventoryCount(RequirementPriority.MANDATORY)).append(", ");
        sb.append("Recommended: ").append(preEquipBreakdown.getInventoryCount(RequirementPriority.RECOMMENDED)).append(", ");
        sb.append("Optional: ").append(preEquipBreakdown.getInventoryCount(RequirementPriority.RECOMMENDED)).append("\n");
        
        // Inventory slot details for Pre
        Map<Integer, Map<RequirementPriority, Integer>> preInventorySlots = preEquipBreakdown.getInventorySlotBreakdown();
        if (!preInventorySlots.isEmpty()) {
            sb.append("      └─ Inventory Slots Detail:\n");
            for (Map.Entry<Integer, Map<RequirementPriority, Integer>> entry : preInventorySlots.entrySet()) {
                Integer slot = entry.getKey();
                Map<RequirementPriority, Integer> counts = entry.getValue();
                sb.append("         Slot ").append(slot).append(": M=").append(counts.getOrDefault(RequirementPriority.MANDATORY, 0))
                  .append(", R=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0))
                  .append(", O=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0)).append("\n");
            }
        }
        
        sb.append("    - Shop Requirements: ").append(this.registry.getStandardRequirements(ShopRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - all external requirements: ").append(registry.getExternalRequirements(ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        
        // Post-Schedule Requirements
        sb.append(" Post Requirements Registered: ").append(this.registry.getStandardRequirements(ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Spellbook Requirements: ").append(this.registry.getStandardRequirements(SpellbookRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Location Requirements: ").append(this.registry.getStandardRequirements(LocationRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Loot Requirements: ").append(this.registry.getStandardRequirements(LootRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        
        // Equipment Requirements breakdown for Post
        RequirementRegistry.RequirementBreakdown postEquipBreakdown = registry.getItemRequirementBreakdown(ScheduleContext.POST_SCHEDULE);
        sb.append("    - Equipment Requirements: ").append(postEquipBreakdown.getTotalEquipmentCount()).append("\n");
        sb.append("      └─ Mandatory: ").append(postEquipBreakdown.getEquipmentCount(RequirementPriority.MANDATORY)).append(", ");
        sb.append("Recommended: ").append(postEquipBreakdown.getEquipmentCount(RequirementPriority.RECOMMENDED)).append(", ");
        sb.append("Optional: ").append(postEquipBreakdown.getEquipmentCount(RequirementPriority.RECOMMENDED)).append("\n");
        
        // Equipment slot details for Post
        Map<EquipmentInventorySlot, Map<RequirementPriority, Integer>> postEquipSlots = postEquipBreakdown.getEquipmentSlotBreakdown();
        if (!postEquipSlots.isEmpty()) {
            sb.append("      └─ Equipment Slots Detail:\n");
            for (Map.Entry<EquipmentInventorySlot, Map<RequirementPriority, Integer>> entry : postEquipSlots.entrySet()) {
                EquipmentInventorySlot slot = entry.getKey();
                Map<RequirementPriority, Integer> counts = entry.getValue();
                sb.append("         ").append(slot.name()).append(": M=").append(counts.getOrDefault(RequirementPriority.MANDATORY, 0))
                  .append(", R=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0))
                  .append(", O=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0)).append("\n");
            }
        }
        
        // Extra statistics before Inventory Requirements breakdown for Post
        Map<Integer, Map<RequirementPriority, Integer>> postInventorySlots = postEquipBreakdown.getInventorySlotBreakdown();
        sb.append("    - Inventory Slot Statistics (specific slots only):\n");
        sb.append("      └─ Total Specific Slots Used: ").append(postInventorySlots.size()).append("\n");
        if (!postInventorySlots.isEmpty()) {
            sb.append("      └─ Slot Range: ").append(postInventorySlots.keySet().stream().min(Integer::compareTo).orElse(0))
              .append(" to ").append(postInventorySlots.keySet().stream().max(Integer::compareTo).orElse(0)).append("\n");
        }
        
        // Inventory Requirements breakdown for Post
        sb.append("    - Inventory Requirements: ").append(postEquipBreakdown.getTotalInventoryCount()).append("\n");
        sb.append("      └─ Mandatory: ").append(postEquipBreakdown.getInventoryCount(RequirementPriority.MANDATORY)).append(", ");
        sb.append("Recommended: ").append(postEquipBreakdown.getInventoryCount(RequirementPriority.RECOMMENDED)).append(", ");
        sb.append("Optional: ").append(postEquipBreakdown.getInventoryCount(RequirementPriority.RECOMMENDED)).append("\n");
        
        // Inventory slot details for Post
        if (!postInventorySlots.isEmpty()) {
            sb.append("      └─ Inventory Slots Detail:\n");
            for (Map.Entry<Integer, Map<RequirementPriority, Integer>> entry : postInventorySlots.entrySet()) {
                Integer slot = entry.getKey();
                Map<RequirementPriority, Integer> counts = entry.getValue();
                sb.append("         Slot ").append(slot).append(": M=").append(counts.getOrDefault(RequirementPriority.MANDATORY, 0))
                  .append(", R=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0))
                  .append(", O=").append(counts.getOrDefault(RequirementPriority.RECOMMENDED, 0)).append("\n");
            }
        }
        
        sb.append("    - Shop Requirements: ").append(this.registry.getStandardRequirements(ShopRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - all external requirements: ").append(registry.getExternalRequirements(ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("=============================================\n");
        return sb.toString();   
    }
        
}
