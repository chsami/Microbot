package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.RunePouchRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.LogicalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enhanced requirement registry that manages all types of requirements with automatic
 * uniqueness enforcement, consistency guarantees, and efficient lookup.
 * 
 * This class solves the issues with the previous approach:
 * - Eliminates duplication between specific collections and central registry
 * - Enforces uniqueness automatically 
 * - Provides type-safe access while maintaining consistency
 * - Simplifies requirement management
 */
@Slf4j
public class RequirementRegistry {
    
    /**
     * Key class for ensuring requirement uniqueness.
     * Two requirements are considered the same if they have the same type, 
     * schedule context, and core identity (defined by the requirement itself).
     */
    public static class RequirementKey {
        private final Class<? extends Requirement> type;
        private final ScheduleContext scheduleContext;
        private final String identity; // Unique identifier from the requirement
        
        public RequirementKey(Requirement requirement) {
            this.type = requirement.getClass();
            this.scheduleContext = requirement.hasScheduleContext() ? requirement.getScheduleContext() : null;
            this.identity = requirement.getUniqueIdentifier();
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RequirementKey that = (RequirementKey) o;
            return Objects.equals(type, that.type) &&
                   Objects.equals(scheduleContext, that.scheduleContext) &&
                   Objects.equals(identity, that.identity);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(type, scheduleContext, identity);
        }
        
        @Override
        public String toString() {
            return String.format("%s[%s:%s]", type.getSimpleName(), scheduleContext, identity);
        }
    }
    
    // Central storage - this is the single source of truth for standard requirements
    private final Map<RequirementKey, Requirement> requirements = new ConcurrentHashMap<>();
    
    // Separate storage for externally added requirements to prevent mixing
    private final Map<RequirementKey, Requirement> externalRequirements = new ConcurrentHashMap<>();
    
    // Standard requirements cached views for efficient access (rebuilt when requirements change)
    private volatile Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotRequirementsCache = new HashMap<>();
    private volatile Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentItemsCache = new HashMap<>();
    private volatile LinkedHashSet<LogicalRequirement> shopRequirementsCache = new LinkedHashSet<>();
    private volatile LinkedHashSet<LogicalRequirement> lootRequirementsCache = new LinkedHashSet<>();
    private volatile LinkedHashSet<ConditionalRequirement> conditionalItemRequirementsCache = new LinkedHashSet<>();
    
    // External requirements cached views for efficient access (rebuilt when external requirements change)
    private volatile Map<Integer, LinkedHashSet<LogicalRequirement>> externalInventorySlotRequirementsCache = new HashMap<>();
    private volatile Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> externalEquipmentItemsCache = new HashMap<>();
    private volatile LinkedHashSet<LogicalRequirement> externalShopRequirementsCache = new LinkedHashSet<>();
    private volatile LinkedHashSet<LogicalRequirement> externalLootRequirementsCache = new LinkedHashSet<>();
    private volatile LinkedHashSet<ConditionalRequirement> externalIConditionalItemRequirementsCache = new LinkedHashSet<>();
    
    // Single-instance requirements (enforced by registry)
    @Getter
    private volatile SpellbookRequirement preScheduleSpellbookRequirement = null;
    @Getter
    private volatile SpellbookRequirement postScheduleSpellbookRequirement = null;
    @Getter
    private volatile LocationRequirement preScheduleLocationRequirement = null;
    @Getter
    private volatile LocationRequirement postScheduleLocationRequirement = null;
    
    private volatile boolean cacheValid = false;
    private volatile boolean externalCacheValid = false;
    
    /**
     * Registers a requirement in the registry.
     * Automatically handles uniqueness, categorization, and cache invalidation.
     * Includes special validation for dummy items to ensure proper slot assignment.
     * 
     * @param requirement The requirement to register
     * @return true if the requirement was added (new), false if it replaced an existing one
     */
    public boolean register(Requirement requirement) {
        if (requirement == null) {
            log.warn("Attempted to register null requirement");
            return false;
        }
        
        // Special validation for dummy items
        if (requirement instanceof ItemRequirement) {
            ItemRequirement itemReq = (ItemRequirement) requirement;
            if (itemReq.isDummyItemRequirement()) {
                // Validate dummy item configuration
                if (itemReq.getEquipmentSlot() == null && itemReq.getInventorySlot() == null) {
                    log.error("Dummy item requirement must specify either equipment slot or inventory slot");
                    return false;
                }
                if (itemReq.getEquipmentSlot() != null && itemReq.getInventorySlot() != null && 
                    itemReq.getInventorySlot() >= 0) {
                    log.error("Dummy item requirement cannot specify both equipment slot and specific inventory slot");
                    return false;
                }
                log.debug("Registering dummy item requirement for slot: {} (equipment: {}, inventory: {})", 
                    itemReq.getDescription(), itemReq.getEquipmentSlot(), itemReq.getInventorySlot());
            }
        }
        
        RequirementKey key = new RequirementKey(requirement);
        
        // Special handling for single-instance requirements
        if (requirement instanceof SpellbookRequirement) {
            return registerSpellbookRequirement((SpellbookRequirement) requirement, key);
        } else if (requirement instanceof LocationRequirement) {
            return registerLocationRequirement((LocationRequirement) requirement, key);
        } else if (requirement instanceof RunePouchRequirement) {
            return registerRunePouchRequirement((RunePouchRequirement) requirement, key);
        }
        
        // For multi-instance requirements, just add to central storage
        Requirement previous = requirements.put(key, requirement);
        invalidateCache();
        
        if (previous != null) {
            log.debug("Replaced existing requirement: {} -> {}", previous, requirement);
            return false;
        } else {
            log.debug("Added new requirement: {}", requirement);
            return true;
        }
    }
    
    /**
     * Registers an externally added requirement in the registry.
     * These requirements are tracked separately and fulfilled after all standard requirements.
     * 
     * @param requirement The externally added requirement to register
     * @return true if the requirement was added (new), false if it replaced an existing one
     */
    public boolean registerExternal(Requirement requirement) {
        if (requirement == null) {
            log.warn("Attempted to register null external requirement");
            return false;
        }
        
        RequirementKey key = new RequirementKey(requirement);
        
        // Store directly in external requirements map
        Requirement previous = externalRequirements.put(key, requirement);
        invalidateExternalCache();
        
        if (previous != null) {
            log.debug("Replaced external requirement: {} -> {}", previous.getDescription(), requirement.getDescription());
            return false;
        } else {
            log.debug("Registered new external requirement: {}", requirement.getDescription());
            return true;
        }
    }
    
    /**
     * Gets all externally added requirements for a specific schedule context.
     * These requirements should be fulfilled after all standard requirements.
     * 
     * @param context The schedule context to filter by
     * @return List of externally added requirements for the given context
     */
    public List<Requirement> getExternalRequirements(ScheduleContext context) {
        return externalRequirements.values().stream()
            .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
            .collect(Collectors.toList());
    }
    
    private boolean registerSpellbookRequirement(SpellbookRequirement requirement, RequirementKey key) {
        boolean isPreSchedule = requirement.isPreSchedule();
        boolean isPostSchedule = requirement.isPostSchedule();
        
        if (isPreSchedule && preScheduleSpellbookRequirement != null) {
            log.warn("Replacing existing pre-schedule spellbook requirement: {} -> {}", 
                    preScheduleSpellbookRequirement, requirement);
            RequirementKey preScheduleKey = new RequirementKey(preScheduleSpellbookRequirement);
            requirements.remove(preScheduleKey); // Remove old requirement to avoid duplicates
        }
        if (isPostSchedule && postScheduleSpellbookRequirement != null) {
            log.warn("Replacing existing post-schedule spellbook requirement: {} -> {}", 
                    postScheduleSpellbookRequirement, requirement);
            RequirementKey postScheduleKey = new RequirementKey(postScheduleSpellbookRequirement);
            requirements.remove(postScheduleKey); // Remove old requirement to avoid duplicates
        }
        
        Requirement previous = requirements.put(key, requirement);
        
        if (isPreSchedule) {
            preScheduleSpellbookRequirement = requirement;
        }
        if (isPostSchedule) {
            postScheduleSpellbookRequirement = requirement;
        }
        
        invalidateCache();
        return previous == null;
    }
    
    private boolean registerLocationRequirement(LocationRequirement requirement, RequirementKey key) {
        boolean isPreSchedule = requirement.isPreSchedule();
        boolean isPostSchedule = requirement.isPostSchedule();
        
        if (isPreSchedule && preScheduleLocationRequirement != null) {
            log.warn("Replacing existing pre-schedule location requirement: {} -> {}", 
                    preScheduleLocationRequirement, requirement);
            RequirementKey preRequirementKey = new RequirementKey(preScheduleLocationRequirement);
            requirements.remove(preRequirementKey); // Remove old requirement to avoid duplicates
        }
        if (isPostSchedule && postScheduleLocationRequirement != null) {
            log.warn("Replacing existing post-schedule location requirement: {} -> {}", 
                    postScheduleLocationRequirement, requirement);
            RequirementKey postRequirementKey = new RequirementKey(postScheduleLocationRequirement);
            requirements.remove(postRequirementKey); // Remove old requirement to avoid duplicates
        }
        
        Requirement previous = requirements.put(key, requirement);
        
        if (isPreSchedule) {
            preScheduleLocationRequirement = requirement;
        }
        if (isPostSchedule) {
            postScheduleLocationRequirement = requirement;
        }
        
        invalidateCache();
        return previous == null;
    }
    
    private boolean registerRunePouchRequirement(RunePouchRequirement requirement, RequirementKey key) {
        // Check if any RunePouchRequirement already exists
        RunePouchRequirement existingRunePouchRequirement = requirements.values().stream()
                .filter(r -> r instanceof RunePouchRequirement)
                .map(r -> (RunePouchRequirement) r)
                .findFirst()
                .orElse(null);
                
        if (existingRunePouchRequirement != null) {
            log.warn("Replacing existing rune pouch requirement: {} -> {}", 
                    existingRunePouchRequirement, requirement);
            RequirementKey existingKey = new RequirementKey(existingRunePouchRequirement);
            requirements.remove(existingKey); // Remove old requirement to avoid duplicates
        }
        
        Requirement previous = requirements.put(key, requirement);
        invalidateCache();
        
        log.debug("Registered rune pouch requirement: {}", requirement);
        return previous == null;
    }
    
    /**
     * Removes a requirement from the registry.
     * 
     * @param requirement The requirement to remove
     * @return true if the requirement was removed, false if it wasn't found
     */
    public boolean unregister(Requirement requirement) {
        if (requirement == null) {
            return false;
        }
        
        RequirementKey key = new RequirementKey(requirement);
        Requirement removed = requirements.remove(key);
        
        if (removed != null) {
            // Clear single-instance references if applicable
            if (removed == preScheduleSpellbookRequirement) {
                preScheduleSpellbookRequirement = null;
            }
            if (removed == postScheduleSpellbookRequirement) {
                postScheduleSpellbookRequirement = null;
            }
            if (removed == preScheduleLocationRequirement) {
                preScheduleLocationRequirement = null;
            }
            if (removed == postScheduleLocationRequirement) {
                postScheduleLocationRequirement = null;
            }
            
            invalidateCache();
            log.debug("Removed requirement: {}", removed);
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets all requirements of a specific type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Requirement> List<T> getStandardRequirements(Class<T> clazz) {
        return requirements.values().stream()
                .filter(clazz::isInstance)
                .map(req -> (T) req)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all requirements for a specific schedule context.
     */
    public List<Requirement> getStandardRequirements(ScheduleContext context) {
        return requirements.values().stream()
                .filter(req -> req.hasScheduleContext() && 
                        (req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH))
                .collect(Collectors.toList());
    }
    
   
    /**
     * Gets all standard (non-external) requirements of a specific type for a specific schedule context.
     * This excludes externally added requirements to prevent double processing.
     */
    @SuppressWarnings("unchecked")
    public <T extends Requirement> List<T> getStandardRequirements(Class<T> clazz, ScheduleContext context) {
        return requirements.values().stream()
                .filter(clazz::isInstance)
                .filter(req -> req.hasScheduleContext() && 
                        (req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH))
                .map(req -> (T) req)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all requirements in the registry.
     */
    public LinkedHashSet<Requirement> getAllRequirements() {
        return new LinkedHashSet<>(requirements.values());
    }
    
    /**
     * Checks if a requirement exists in the registry.
     */
    public boolean contains(Requirement requirement) {
        if (requirement == null) {
            return false;
        }
        return requirements.containsKey(new RequirementKey(requirement));
    }
    
    /**
     * Gets the total number of requirements.
     */
    public int size() {
        return requirements.size();
    }
    
    /**
     * Clears all requirements from the registry.
     */
    public void clear() {
        requirements.clear();
        preScheduleSpellbookRequirement = null;
        postScheduleSpellbookRequirement = null;
        preScheduleLocationRequirement = null;
        postScheduleLocationRequirement = null;
        invalidateCache();
    }
    
    /**
     * Helper class to hold a complete set of caches returned by the unified rebuild method.
     */
    private static class CacheSet {
        final Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache;
        final LinkedHashSet<LogicalRequirement> shopCache;
        final LinkedHashSet<LogicalRequirement> lootCache;
        final Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache;
        final LinkedHashSet<ConditionalRequirement> conditionalCache;
        
        CacheSet(Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache,
                LinkedHashSet<LogicalRequirement> shopCache,
                LinkedHashSet<LogicalRequirement> lootCache,
                Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache,
                LinkedHashSet<ConditionalRequirement> conditionalCache) {
            this.equipmentCache = equipmentCache;
            this.shopCache = shopCache;
            this.lootCache = lootCache;
            this.inventorySlotCache = inventorySlotCache;
            this.conditionalCache = conditionalCache;
        }
    }
    
    /**
     * Unified cache rebuilding logic used by both standard and external cache rebuild methods.
     * This method groups compatible requirements into logical requirements for better organization.
     * 
     * @param requirementCollection The collection of requirements to process
     * @param cacheType The type of cache being rebuilt ("standard" or "external") for logging
     * @return A CacheSet containing all the rebuilt caches
     */
    private CacheSet rebuildCacheUnified(Collection<Requirement> requirementCollection, String cacheType) {
        // New caches with logical requirements
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> newEquipmentCache = new HashMap<>();
        LinkedHashSet<LogicalRequirement> newShopCache = new LinkedHashSet<>();
        LinkedHashSet<LogicalRequirement> newLootCache = new LinkedHashSet<>();
        Map<Integer, LinkedHashSet<LogicalRequirement>> newInventorySlotCache = new HashMap<>();
        LinkedHashSet<ConditionalRequirement> newConditionalCache = new LinkedHashSet<>();
        
        // Group requirements by schedule context FIRST, then by type and slot
        Map<ScheduleContext, Map<EquipmentInventorySlot, List<ItemRequirement>>> equipmentByContextAndSlot = new HashMap<>();
        Map<ScheduleContext, Map<Integer, List<ItemRequirement>>> inventoryByContextAndSlot = new HashMap<>();
        Map<ScheduleContext, List<ItemRequirement>> eitherByContext = new HashMap<>();
        Map<ScheduleContext, List<ShopRequirement>> shopByContext = new HashMap<>();
        Map<ScheduleContext, List<LootRequirement>> lootByContext = new HashMap<>();
        
        // First pass: collect and group requirements by schedule context, then by slot
        for (Requirement requirement : requirementCollection) {
            if (requirement instanceof LogicalRequirement) {
                // Decompose logical requirement into child requirements for priority-based grouping
                LogicalRequirement logical = (LogicalRequirement) requirement;
                decomposeLogicalRequirement(logical, equipmentByContextAndSlot, inventoryByContextAndSlot, 
                        eitherByContext, shopByContext, lootByContext);
            } else if (requirement instanceof ConditionalRequirement) {
                // Handle ConditionalRequirement - only cache if it contains only ItemRequirements
                ConditionalRequirement conditionalReq = (ConditionalRequirement) requirement;
                if (conditionalReq.containsOnlyItemRequirements()) {
                    newConditionalCache.add(conditionalReq);
                    log.debug("Cached ConditionalRequirement with only ItemRequirements: {}", conditionalReq.getName());
                } else {
                    log.debug("Skipped ConditionalRequirement with mixed requirement types for now: {}", conditionalReq.getName());
                }
            } else if (requirement instanceof ItemRequirement) {
                ItemRequirement itemReq = (ItemRequirement) requirement;
                ScheduleContext context = itemReq.getScheduleContext();
                
                switch (itemReq.getRequirementType()) {
                    case EQUIPMENT:
                        if (itemReq.getEquipmentSlot() != null) {
                            equipmentByContextAndSlot
                                .computeIfAbsent(context, k -> new HashMap<>())
                                .computeIfAbsent(itemReq.getEquipmentSlot(), k -> new ArrayList<>())
                                .add(itemReq);
                        }
                        break;
                    case INVENTORY:
                        int slot = itemReq.hasSpecificInventorySlot() ? itemReq.getInventorySlot() : -1;
                        inventoryByContextAndSlot
                            .computeIfAbsent(context, k -> new HashMap<>())
                            .computeIfAbsent(slot, k -> new ArrayList<>())
                            .add(itemReq);
                        break;
                    case EITHER:
                        eitherByContext.computeIfAbsent(context, k -> new ArrayList<>()).add(itemReq);
                        break;
                    case PLAYER_STATE:
                    case LOCATION:
                    case GAME_CONFIG:
                        // These are handled elsewhere
                        break;
                    case OR_LOGICAL:
                        log.warn("ItemRequirement with logical type: {}", itemReq);
                        break;
                    case SHOP:
                    case CONDITIONAL:
                    case LOOT:
                    case CUSTOM:
                        // These types are not expected for ItemRequirement
                        log.warn("Unexpected requirement type for ItemRequirement: {}", itemReq.getRequirementType());
                        break;
                }
            } else if (requirement instanceof ShopRequirement) {
                ShopRequirement shopReq = (ShopRequirement) requirement;
                shopByContext.computeIfAbsent(shopReq.getScheduleContext(), k -> new ArrayList<>()).add(shopReq);
            } else if (requirement instanceof LootRequirement) {
                LootRequirement lootReq = (LootRequirement) requirement;
                lootByContext.computeIfAbsent(lootReq.getScheduleContext(), k -> new ArrayList<>()).add(lootReq);
            }
        }
        
        // Debug equipment grouping before creating logical requirements (only for standard cache)
        if ("standard".equals(cacheType)) {
            log.info("=== EQUIPMENT GROUPING DEBUG ===");
            for (Map.Entry<ScheduleContext, Map<EquipmentInventorySlot, List<ItemRequirement>>> contextEntry : equipmentByContextAndSlot.entrySet()) {
                ScheduleContext context = contextEntry.getKey();
                log.info("Schedule Context: {}", context);
                for (Map.Entry<EquipmentInventorySlot, List<ItemRequirement>> slotEntry : contextEntry.getValue().entrySet()) {
                    EquipmentInventorySlot slot = slotEntry.getKey();
                    List<ItemRequirement> slotItems = slotEntry.getValue();
                    log.info("  Slot {}: {} items", slot, slotItems.size());
                    for (ItemRequirement item : slotItems) {
                        log.info("    - {} (ID: {}, Priority: {}, Rating: {})", 
                            item.getName(), item.getId(), item.getPriority(), item.getRating());
                    }
                }
            }
            log.info("=== END EQUIPMENT GROUPING DEBUG ===");
        }

        // Second pass: create logical requirements from grouped items
        createLogicalRequirementsFromGroups(equipmentByContextAndSlot, inventoryByContextAndSlot, 
                eitherByContext, shopByContext, lootByContext, newEquipmentCache, 
                newShopCache, newLootCache, newInventorySlotCache);
        
        // Sort all caches
        sortAllCaches(newEquipmentCache, newShopCache, newLootCache, newInventorySlotCache);
        
        return new CacheSet(newEquipmentCache, newShopCache, newLootCache, newInventorySlotCache, newConditionalCache);
    }

    /**
     * Invalidates cached views, forcing them to be rebuilt on next access.
     */
    private void invalidateCache() {
        cacheValid = false;
    }
    
    /**
     * Invalidates external cached views, forcing them to be rebuilt on next access.
     */
    private void invalidateExternalCache() {
        externalCacheValid = false;
    }
    
    /**
     * Rebuilds all cached views from the central requirements storage.
     * This method groups compatible requirements into logical requirements for better organization.
     * 
     * The new approach:
     * - Equipment items for the same slot become OR requirements
     * - Individual requirements are wrapped in logical requirements for consistency
     * - Already logical requirements are categorized appropriately
     */
    private synchronized void rebuildCache() {
        if (cacheValid) {
            return; // Another thread already rebuilt the cache
        }
        
        log.debug("Rebuilding requirement caches...");
        
        // Use unified rebuild logic for standard requirements
        CacheSet newCaches = rebuildCacheUnified(requirements.values(), "standard");
        
        // Atomically update caches
        equipmentItemsCache = newCaches.equipmentCache;
        shopRequirementsCache = newCaches.shopCache;
        lootRequirementsCache = newCaches.lootCache;
        inventorySlotRequirementsCache = newCaches.inventorySlotCache;
        conditionalItemRequirementsCache = newCaches.conditionalCache;
        
        cacheValid = true;
        log.debug("Rebuilt requirement caches with {} total requirements", requirements.size());
    }
    
    /**
     * Decomposes a logical requirement into its child requirements and adds them to the appropriate maps
     * for priority-based grouping.
     */
    private void decomposeLogicalRequirement(LogicalRequirement logical,
            Map<ScheduleContext, Map<EquipmentInventorySlot, List<ItemRequirement>>> equipmentByContextAndSlot,
            Map<ScheduleContext, Map<Integer, List<ItemRequirement>>> inventoryByContextAndSlot,
            Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
            Map<ScheduleContext, List<ShopRequirement>> shopByContext,
            Map<ScheduleContext, List<LootRequirement>> lootByContext) {
        
        for (Requirement child : logical.getChildRequirements()) {
            if (child instanceof ItemRequirement) {
                ItemRequirement itemReq = (ItemRequirement) child;
                ScheduleContext context = itemReq.getScheduleContext();
                
                switch (itemReq.getRequirementType()) {
                    case EQUIPMENT:
                        if (itemReq.getEquipmentSlot() != null) {
                            equipmentByContextAndSlot
                                .computeIfAbsent(context, k -> new HashMap<>())
                                .computeIfAbsent(itemReq.getEquipmentSlot(), k -> new ArrayList<>())
                                .add(itemReq);
                        }
                        break;
                    case INVENTORY:
                        int slot = itemReq.hasSpecificInventorySlot() ? itemReq.getInventorySlot() : -1;
                        inventoryByContextAndSlot
                            .computeIfAbsent(context, k -> new HashMap<>())
                            .computeIfAbsent(slot, k -> new ArrayList<>())
                            .add(itemReq);
                        break;
                    case EITHER:
                        eitherByContext.computeIfAbsent(context, k -> new ArrayList<>()).add(itemReq);
                        break;
                    default:
                        // Other types don't go in these maps
                        break;
                }
            } else if (child instanceof ShopRequirement) {
                ShopRequirement shopReq = (ShopRequirement) child;
                shopByContext.computeIfAbsent(shopReq.getScheduleContext(), k -> new ArrayList<>()).add(shopReq);
            } else if (child instanceof LootRequirement) {
                LootRequirement lootReq = (LootRequirement) child;
                lootByContext.computeIfAbsent(lootReq.getScheduleContext(), k -> new ArrayList<>()).add(lootReq);
            } else if (child instanceof LogicalRequirement) {
                // Recursively decompose nested logical requirements
                decomposeLogicalRequirement((LogicalRequirement) child, equipmentByContextAndSlot, 
                        inventoryByContextAndSlot, eitherByContext, shopByContext, lootByContext);
            }
        }
    }

    /**
     * Legacy method - kept for potential future use but no longer used in main flow.
     * Adds a logical requirement to the appropriate cache based on its content.
     * This method analyzes the child requirements to determine the best placement.
     */
    private void addLogicalRequirementToCache(LogicalRequirement logical, 
            Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache,
            LinkedHashSet<LogicalRequirement> shopCache,
            LinkedHashSet<LogicalRequirement> lootCache,
            Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache) {
        
        // Analyze child requirements to determine placement
        boolean hasEquipment = false;
        boolean hasInventory = false;
        boolean hasEither = false;
        boolean hasShop = false;
        boolean hasLoot = false;
        EquipmentInventorySlot equipmentSlot = null;
        Integer specificInventorySlot = null;
        
        for (Requirement child : logical.getChildRequirements()) {
            if (child instanceof ItemRequirement) {
                ItemRequirement item = (ItemRequirement) child;
                switch (item.getRequirementType()) {
                    case EQUIPMENT:
                        hasEquipment = true;
                        if (equipmentSlot == null) {
                            equipmentSlot = item.getEquipmentSlot();
                        }
                        break;
                    case INVENTORY:
                        hasInventory = true;
                        if (item.hasSpecificInventorySlot() && specificInventorySlot == null) {
                            specificInventorySlot = item.getInventorySlot();
                        }
                        break;
                    case EITHER:
                        hasEither = true;
                        if (item.getEquipmentSlot() != null && equipmentSlot == null) {
                            equipmentSlot = item.getEquipmentSlot();
                        }
                        if (item.hasSpecificInventorySlot() && specificInventorySlot == null) {
                            specificInventorySlot = item.getInventorySlot();
                        }
                        break;
                    case PLAYER_STATE:
                    case LOCATION:
                    case GAME_CONFIG:                    
                    case OR_LOGICAL:
                        // These don't affect cache placement for items
                        break;
                    case SHOP:
                    case CONDITIONAL:
                    case LOOT:
                    case CUSTOM:
                        // These types are not expected for ItemRequirement
                        log.warn("Unexpected requirement type for ItemRequirement in logical: {}", item.getRequirementType());
                        break;
                }
            } else if (child instanceof ShopRequirement) {
                hasShop = true;
            } else if (child instanceof LootRequirement) {
                hasLoot = true;
            } else if (child instanceof LogicalRequirement) {
                // For nested logical requirements, recursively add them
                addLogicalRequirementToCache((LogicalRequirement) child, equipmentCache, 
                        shopCache, lootCache, inventorySlotCache);
                return; // Don't add the parent logical requirement
            }
        }
        
        // Place in the most appropriate cache(s)
        // EITHER items can be placed in multiple caches based on their capabilities
        if (hasEquipment && equipmentSlot != null) {
            equipmentCache.computeIfAbsent(equipmentSlot, k -> new LinkedHashSet<>()).add(logical);
        }
        
        if (hasShop) {
            shopCache.add(logical);
        } else if (hasLoot) {
            lootCache.add(logical);
        } else if (hasInventory || hasEither) {
            // Add to inventory slot cache (specific slot or -1 for any slot)
            if (specificInventorySlot != null) {
                // Add to specific slot cache
                inventorySlotCache.computeIfAbsent(specificInventorySlot, k -> new LinkedHashSet<>()).add(logical);
            } else {
                // Add to any-slot cache (key = -1)
                inventorySlotCache.computeIfAbsent(-1, k -> new LinkedHashSet<>()).add(logical);
            }
        }
    }
    
    /**
     * Creates logical requirements from grouped individual requirements organized by priority.
     * This creates up to 2 OR requirements per slot: one for MANDATORY priority, one for RECOMMENDED priority.
     */
    private void createLogicalRequirementsFromGroups(
            Map<ScheduleContext, Map<EquipmentInventorySlot, List<ItemRequirement>>> equipmentByContextAndSlot,
            Map<ScheduleContext, Map<Integer, List<ItemRequirement>>> inventoryByContextAndSlot,
            Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
            Map<ScheduleContext, List<ShopRequirement>> shopByContext,
            Map<ScheduleContext, List<LootRequirement>> lootByContext,
            Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache,
            LinkedHashSet<LogicalRequirement> shopCache,
            LinkedHashSet<LogicalRequirement> lootCache,
            Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache) {
        
        // Process equipment items: create consolidated PRE and POST OR requirements per slot
        processEquipmentSlots(equipmentByContextAndSlot, eitherByContext, equipmentCache);
        
        // Process inventory items: create consolidated PRE and POST OR requirements per slot
        processInventorySlots(inventoryByContextAndSlot, eitherByContext, inventorySlotCache);
        
        // Process shop and loot requirements (these don't need slot consolidation)
        processShopAndLootRequirements(shopByContext, lootByContext, shopCache, lootCache);
    }
    
    /**
     * Processes equipment slots to create up to 2 OR requirements per slot (one for each priority level).
     */
    private void processEquipmentSlots(
            Map<ScheduleContext, Map<EquipmentInventorySlot, List<ItemRequirement>>> equipmentByContextAndSlot,
            Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
            Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache) {
        
        // Collect all equipment slots that have requirements
        Set<EquipmentInventorySlot> allEquipmentSlots = new HashSet<>();
        for (Map<EquipmentInventorySlot, List<ItemRequirement>> slotMap : equipmentByContextAndSlot.values()) {
            allEquipmentSlots.addAll(slotMap.keySet());
        }
        
        // Add slots from EITHER items
        for (List<ItemRequirement> eitherItems : eitherByContext.values()) {
            for (ItemRequirement item : eitherItems) {
                if (item.getEquipmentSlot() != null) {
                    allEquipmentSlots.add(item.getEquipmentSlot());
                }
            }
        }
        
        // For each equipment slot, create MANDATORY and RECOMMENDED OR requirements
        for (EquipmentInventorySlot slot : allEquipmentSlots) {
            // Collect items for MANDATORY priority (all schedule contexts)
            List<ItemRequirement> mandatoryItems = new ArrayList<>();
            addItemsForPriority(equipmentByContextAndSlot, slot, RequirementPriority.MANDATORY, mandatoryItems);
            addEitherItemsForEquipmentSlotPriority(eitherByContext, slot, RequirementPriority.MANDATORY, mandatoryItems);
            
            // Collect items for RECOMMENDED priority (all schedule contexts)
            List<ItemRequirement> recommendedItems = new ArrayList<>();
            addItemsForPriority(equipmentByContextAndSlot, slot, RequirementPriority.RECOMMENDED, recommendedItems);
            addEitherItemsForEquipmentSlotPriority(eitherByContext, slot, RequirementPriority.RECOMMENDED, recommendedItems);
            
            // Create OR requirements if items exist
            LinkedHashSet<LogicalRequirement> slotRequirements = new LinkedHashSet<>();
            
            if (!mandatoryItems.isEmpty()) {
                OrRequirement mandatoryReq = createMergedOrRequirement(mandatoryItems, 
                    slot.name() + " equipment (MANDATORY)", determineScheduleContext(mandatoryItems));
                slotRequirements.add(mandatoryReq);
                log.debug("Created MANDATORY equipment OR requirement for slot {}: {} with {} alternatives", 
                    slot, mandatoryReq.getName(), mandatoryItems.size());
            }
            
            if (!recommendedItems.isEmpty()) {
                OrRequirement recommendedReq = createMergedOrRequirement(recommendedItems, 
                    slot.name() + " equipment (RECOMMENDED)", determineScheduleContext(recommendedItems));
                slotRequirements.add(recommendedReq);
                log.debug("Created RECOMMENDED equipment OR requirement for slot {}: {} with {} alternatives", 
                    slot, recommendedReq.getName(), recommendedItems.size());
            }
            
            if (!slotRequirements.isEmpty()) {
                equipmentCache.put(slot, slotRequirements);
            }
        }
    }
    
    /**
     * Processes inventory slots to create up to 2 OR requirements per slot (one for each priority level).
     */
    private void processInventorySlots(
            Map<ScheduleContext, Map<Integer, List<ItemRequirement>>> inventoryByContextAndSlot,
            Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
            Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache) {
        
        // Collect all inventory slots that have requirements
        Set<Integer> allInventorySlots = new HashSet<>();
        for (Map<Integer, List<ItemRequirement>> slotMap : inventoryByContextAndSlot.values()) {
            allInventorySlots.addAll(slotMap.keySet());
        }
        
        // Add slots from EITHER items
        for (List<ItemRequirement> eitherItems : eitherByContext.values()) {
            for (ItemRequirement item : eitherItems) {
                int invSlot = item.hasSpecificInventorySlot() ? item.getInventorySlot() : -1;
                allInventorySlots.add(invSlot);
            }
        }
        
        // For each inventory slot, create MANDATORY and RECOMMENDED OR requirements
        for (Integer slot : allInventorySlots) {
            // Collect items for MANDATORY priority (all schedule contexts)
            List<ItemRequirement> mandatoryItems = new ArrayList<>();
            addInventoryItemsForPriority(inventoryByContextAndSlot, slot, RequirementPriority.MANDATORY, mandatoryItems);
            addEitherItemsForInventorySlotPriority(eitherByContext, slot, RequirementPriority.MANDATORY, mandatoryItems);
            
            // Collect items for RECOMMENDED priority (all schedule contexts)
            List<ItemRequirement> recommendedItems = new ArrayList<>();
            addInventoryItemsForPriority(inventoryByContextAndSlot, slot, RequirementPriority.RECOMMENDED, recommendedItems);
            addEitherItemsForInventorySlotPriority(eitherByContext, slot, RequirementPriority.RECOMMENDED, recommendedItems);
            
            // Create OR requirements if items exist
            LinkedHashSet<LogicalRequirement> slotRequirements = new LinkedHashSet<>();
            String slotDescription = slot == -1 ? "any inventory slot" : "inventory slot " + slot;
            
            if (!mandatoryItems.isEmpty()) {
                OrRequirement mandatoryReq = createMergedOrRequirement(mandatoryItems, 
                    slotDescription + " (MANDATORY)", determineScheduleContext(mandatoryItems));
                slotRequirements.add(mandatoryReq);
                log.debug("Created MANDATORY inventory OR requirement for {}: {} with {} alternatives", 
                    slotDescription, mandatoryReq.getName(), mandatoryItems.size());
            }
            
            if (!recommendedItems.isEmpty()) {
                OrRequirement recommendedReq = createMergedOrRequirement(recommendedItems, 
                    slotDescription + " (RECOMMENDED)", determineScheduleContext(recommendedItems));
                slotRequirements.add(recommendedReq);
                log.debug("Created RECOMMENDED inventory OR requirement for {}: {} with {} alternatives", 
                    slotDescription, recommendedReq.getName(), recommendedItems.size());
            }
            
            if (!slotRequirements.isEmpty()) {
                inventorySlotCache.put(slot, slotRequirements);
            }
        }
    }
    
    // Helper methods for collecting items
    private void addItemsForContext(Map<ScheduleContext, Map<EquipmentInventorySlot, List<ItemRequirement>>> equipmentByContextAndSlot,
                                   EquipmentInventorySlot slot, ScheduleContext context, List<ItemRequirement> targetList) {
        Map<EquipmentInventorySlot, List<ItemRequirement>> contextMap = equipmentByContextAndSlot.get(context);
        if (contextMap != null) {
            List<ItemRequirement> items = contextMap.get(slot);
            if (items != null) {
                targetList.addAll(items);
            }
        }
    }
    
    private void addInventoryItemsForContext(Map<ScheduleContext, Map<Integer, List<ItemRequirement>>> inventoryByContextAndSlot,
                                           Integer slot, ScheduleContext context, List<ItemRequirement> targetList) {
        Map<Integer, List<ItemRequirement>> contextMap = inventoryByContextAndSlot.get(context);
        if (contextMap != null) {
            List<ItemRequirement> items = contextMap.get(slot);
            if (items != null) {
                targetList.addAll(items);
            }
        }
    }
    
    private void addEitherItemsForEquipmentSlot(Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
                                              EquipmentInventorySlot equipSlot, ScheduleContext context, List<ItemRequirement> targetList) {
        List<ItemRequirement> eitherItems = eitherByContext.get(context);
        if (eitherItems != null) {
            for (ItemRequirement item : eitherItems) {
                if (equipSlot.equals(item.getEquipmentSlot())) {
                    targetList.add(item);
                }
            }
        }
    }
    
    private void addEitherItemsForInventorySlot(Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
                                              Integer invSlot, ScheduleContext context, List<ItemRequirement> targetList) {
        List<ItemRequirement> eitherItems = eitherByContext.get(context);
        if (eitherItems != null) {
            for (ItemRequirement item : eitherItems) {
                int itemInvSlot = item.hasSpecificInventorySlot() ? item.getInventorySlot() : -1;
                if (invSlot.equals(itemInvSlot)) {
                    targetList.add(item);
                }
            }
        }
    }
    
    // Priority-based helper methods for collecting items
    private void addItemsForPriority(Map<ScheduleContext, Map<EquipmentInventorySlot, List<ItemRequirement>>> equipmentByContextAndSlot,
                                   EquipmentInventorySlot slot, RequirementPriority priority, List<ItemRequirement> targetList) {
        // Check all schedule contexts for items with the specified priority
        for (Map<EquipmentInventorySlot, List<ItemRequirement>> contextMap : equipmentByContextAndSlot.values()) {
            List<ItemRequirement> items = contextMap.get(slot);
            if (items != null) {
                for (ItemRequirement item : items) {
                    if (item.getPriority() == priority) {
                        targetList.add(item);
                    }
                }
            }
        }
    }
    
    private void addEitherItemsForEquipmentSlotPriority(Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
                                                      EquipmentInventorySlot equipSlot, RequirementPriority priority, List<ItemRequirement> targetList) {
        // Check all schedule contexts for either items with the specified priority
        for (List<ItemRequirement> eitherItems : eitherByContext.values()) {
            if (eitherItems != null) {
                for (ItemRequirement item : eitherItems) {
                    if (equipSlot.equals(item.getEquipmentSlot()) && item.getPriority() == priority) {
                        targetList.add(item);
                    }
                }
            }
        }
    }
    
    private ScheduleContext determineScheduleContext(List<ItemRequirement> items) {
        // Determine the most appropriate ScheduleContext for a group of items
        // Priority: PRE_SCHEDULE -> POST_SCHEDULE -> BOTH
        boolean hasPre = false, hasPost = false, hasBoth = false;
        
        for (ItemRequirement item : items) {
            switch (item.getScheduleContext()) {
                case PRE_SCHEDULE:
                    hasPre = true;
                    break;
                case POST_SCHEDULE:
                    hasPost = true;
                    break;
                case BOTH:
                    hasBoth = true;
                    break;
            }
        }
        
        // If all items have the same context, use that
        if (hasPre && !hasPost && !hasBoth) return ScheduleContext.PRE_SCHEDULE;
        if (hasPost && !hasPre && !hasBoth) return ScheduleContext.POST_SCHEDULE;
        if (hasBoth && !hasPre && !hasPost) return ScheduleContext.BOTH;
        
        // Mixed contexts - default to BOTH (covers all cases)
        return ScheduleContext.BOTH;
    }
    
    // Priority-based helper methods for inventory items
    private void addInventoryItemsForPriority(Map<ScheduleContext, Map<Integer, List<ItemRequirement>>> inventoryByContextAndSlot,
                                            Integer slot, RequirementPriority priority, List<ItemRequirement> targetList) {
        // Check all schedule contexts for items with the specified priority
        for (Map<Integer, List<ItemRequirement>> contextMap : inventoryByContextAndSlot.values()) {
            List<ItemRequirement> items = contextMap.get(slot);
            if (items != null) {
                for (ItemRequirement item : items) {
                    if (item.getPriority() == priority) {
                        targetList.add(item);
                    }
                }
            }
        }
    }
    
    private void addEitherItemsForInventorySlotPriority(Map<ScheduleContext, List<ItemRequirement>> eitherByContext,
                                                      Integer invSlot, RequirementPriority priority, List<ItemRequirement> targetList) {
        // Check all schedule contexts for either items with the specified priority
        for (List<ItemRequirement> eitherItems : eitherByContext.values()) {
            if (eitherItems != null) {
                for (ItemRequirement item : eitherItems) {
                    int itemInvSlot = item.hasSpecificInventorySlot() ? item.getInventorySlot() : -1;
                    if (invSlot.equals(itemInvSlot) && item.getPriority() == priority) {
                        targetList.add(item);
                    }
                }
            }
        }
    }
    
    /**
     * Processes shop and loot requirements (these don't need slot-based consolidation).
     */
    private void processShopAndLootRequirements(
            Map<ScheduleContext, List<ShopRequirement>> shopByContext,
            Map<ScheduleContext, List<LootRequirement>> lootByContext,
            LinkedHashSet<LogicalRequirement> shopCache,
            LinkedHashSet<LogicalRequirement> lootCache) {
        
        // Process shop requirements grouped by schedule context
        for (Map.Entry<ScheduleContext, List<ShopRequirement>> contextEntry : shopByContext.entrySet()) {
            ScheduleContext scheduleContext = contextEntry.getKey();
            List<ShopRequirement> shopReqs = contextEntry.getValue();
            
            for (ShopRequirement shop : shopReqs) {
                OrRequirement orReq = new OrRequirement(shop.getPriority(), shop.getRating(),
                        shop.getDescription(), scheduleContext, shop);
                shopCache.add(orReq);
            }
        }
        
        // Process loot requirements grouped by schedule context
        for (Map.Entry<ScheduleContext, List<LootRequirement>> contextEntry : lootByContext.entrySet()) {
            ScheduleContext scheduleContext = contextEntry.getKey();
            List<LootRequirement> lootReqs = contextEntry.getValue();
            
            for (LootRequirement loot : lootReqs) {
                OrRequirement orReq = new OrRequirement(loot.getPriority(), loot.getRating(),
                        loot.getDescription(), scheduleContext, loot);
                lootCache.add(orReq);
            }
        }
    }
    
    /**
     * Creates a merged OR requirement from a list of competing items for the same slot.
     * Implements proper rating calculation (sum) and priority selection (highest).
     * 
     * @param items List of items competing for the same slot
     * @param slotDescription Description of the slot for the OR requirement name
     * @param scheduleContext The schedule context (must be the same for all items)
     * @return A merged OrRequirement with correct rating and priority
     */
    private OrRequirement createMergedOrRequirement(List<ItemRequirement> items, String slotDescription, ScheduleContext scheduleContext) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot create OR requirement from empty item list");
        }
        
        // Calculate merged rating as sum of all item ratings
        int mergedRating = items.stream().mapToInt(Requirement::getRating).sum();
        
        // Calculate merged priority as the highest priority (lowest ordinal value)
        RequirementPriority mergedPriority = items.stream()
                .map(Requirement::getPriority)
                .min(RequirementPriority::compareTo)
                .orElse(RequirementPriority.RECOMMENDED);
        
        // Verify all items have the same schedule context
        boolean allSameContext = items.stream()
                .map(Requirement::getScheduleContext)
                .allMatch(context -> context == scheduleContext);
        
        if (!allSameContext) {
            log.warn("Items for {} have different schedule contexts, using {}", slotDescription, scheduleContext);
        }
        
        // Create descriptive name showing alternatives count
        String name = slotDescription + " options (" + items.size() + " alternatives, rating: " + mergedRating + ")";
        
        return new OrRequirement(mergedPriority, mergedRating, name, scheduleContext, 
                items.toArray(new Requirement[0]));
    }
    
    /**
     * Sorts all cache collections by priority and rating.
     */
    private void sortAllCaches(
            Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache,
            LinkedHashSet<LogicalRequirement> shopCache,
            LinkedHashSet<LogicalRequirement> lootCache,
            Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache) {
        
        // Sort equipment items within each slot
        for (LinkedHashSet<LogicalRequirement> slotRequirements : equipmentCache.values()) {
            sortLogicalRequirements(slotRequirements);
        }
        
        // Sort inventory slot requirements
        for (LinkedHashSet<LogicalRequirement> slotRequirements : inventorySlotCache.values()) {
            sortLogicalRequirements(slotRequirements);
        }
        
        // Sort other collections
        sortLogicalRequirements(shopCache);
        sortLogicalRequirements(lootCache);
    }
    
    /**
     * Sorts logical requirements by priority and rating.
     */
    private void sortLogicalRequirements(LinkedHashSet<LogicalRequirement> requirements) {
        List<LogicalRequirement> sorted = new ArrayList<>(requirements);
        sorted.sort((a, b) -> {
            // First sort by priority (MANDATORY > RECOMMENDED > OPTIONAL)
            int priorityCompare = a.getPriority().compareTo(b.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // Then sort by rating (higher rating is better)
            return Integer.compare(b.getRating(), a.getRating());
        });
        
        requirements.clear();
        requirements.addAll(sorted);
    }
    
    /**
     * Gets equipment logical requirements cache for a specific schedule context, rebuilding if necessary.
     * Returns only the LogicalRequirements that match the given context.
     * 
     * @param context The schedule context to filter by (PRE_SCHEDULE or POST_SCHEDULE)
     * @return Map of equipment slot to context-specific logical requirements
     */
    public Map<EquipmentInventorySlot, LogicalRequirement> getEquipmentLogicalRequirements(ScheduleContext context) {
        if (!cacheValid) {
            rebuildCache();
        }
        
        Map<EquipmentInventorySlot, LogicalRequirement> filteredCache = new HashMap<>();
        
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentItemsCache.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            LinkedHashSet<LogicalRequirement> requirements = entry.getValue();
            
            // Find the LogicalRequirement that matches the requested context
            for (LogicalRequirement requirement : requirements) {
                if (requirement.getScheduleContext() == context) {
                    filteredCache.put(slot, requirement);
                    break; // Each slot should have exactly one requirement per context
                }
            }
        }
        
        return filteredCache;
    }
   
    
    /**
     * Gets standard (non-external) equipment logical requirements cache, rebuilding if necessary.
     * This excludes externally added requirements to prevent double processing.
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> getStandardEquipmentLogicalRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> standardEquipment = new HashMap<>();
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentItemsCache.entrySet()) {
            LinkedHashSet<LogicalRequirement> standardSlotReqs = new LinkedHashSet<>();
            for (LogicalRequirement logical : entry.getValue()) {
                if (isStandardLogicalRequirement(logical)) {
                    standardSlotReqs.add(logical);
                }
            }
            if (!standardSlotReqs.isEmpty()) {
                standardEquipment.put(entry.getKey(), standardSlotReqs);
            }
        }
        return standardEquipment;
    }
    
    
    
    /**
     * Gets inventory logical requirements cache, rebuilding if necessary.
     * Returns requirements that can be placed in any inventory slot.
     */
    /**
     * Gets standard (non-external) inventory logical requirements for the -1 slot (any slot) for a specific context.
     * This excludes externally added requirements to prevent double processing.
     * Returns requirements that can be placed in any inventory slot.
     */
    public LinkedHashSet<LogicalRequirement> getStandardInventoryLogicalRequirements(ScheduleContext context) {
        if (!cacheValid) {
            rebuildCache();
        }
        
        LinkedHashSet<LogicalRequirement> standardInventory = new LinkedHashSet<>();
        LogicalRequirement anySlotReq = getAllInventorySlotRequirements(context).get(-1);
        
        if (anySlotReq != null && isStandardLogicalRequirement(anySlotReq)) {
            standardInventory.add(anySlotReq);
        }
        
        return standardInventory;
    }
    
    /**
     * Gets standard (non-external) shop logical requirements cache, rebuilding if necessary.
     * This excludes externally added requirements to prevent double processing.
     */
    public LinkedHashSet<LogicalRequirement> getStandardShopLogicalRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        
        LinkedHashSet<LogicalRequirement> standardShops = new LinkedHashSet<>();
        for (LogicalRequirement logical : shopRequirementsCache) {
            if (isStandardLogicalRequirement(logical)) {
                standardShops.add(logical);
            }
        }
        return standardShops;
    }
    
    /**
     * Checks if a logical requirement contains only standard (non-external) child requirements.
     * 
     * @param logical The logical requirement to check
     * @return true if all child requirements are standard, false if any are external
     */
    private boolean isStandardLogicalRequirement(LogicalRequirement logical) {
        for (Requirement child : logical.getChildRequirements()) {
            RequirementKey childKey = new RequirementKey(child);
            if (externalRequirements.containsKey(childKey)) {
                return false; // Contains external requirement
            }
            // For nested logical requirements, check recursively
            if (child instanceof LogicalRequirement) {
                if (!isStandardLogicalRequirement((LogicalRequirement) child)) {
                    return false;
                }
            }
        }
        return true; // All child requirements are standard
    }
       
    
    /**
     * Gets standard (non-external) loot logical requirements cache, rebuilding if necessary.
     * This excludes externally added requirements to prevent double processing.
     */
    public LinkedHashSet<LogicalRequirement> getStandardLootLogicalRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        
        LinkedHashSet<LogicalRequirement> standardLoots = new LinkedHashSet<>();
        for (LogicalRequirement logical : lootRequirementsCache) {
            if (isStandardLogicalRequirement(logical)) {
                standardLoots.add(logical);
            }
        }
        return standardLoots;
    }
    
    
    /**
     * Validates the consistency of the registry.
     * 
     * @return true if the registry is consistent, false otherwise
     */
    public boolean validateConsistency() {
        try {
            // Ensure single-instance requirements are properly referenced
            long preSpellbookCount = getStandardRequirements(SpellbookRequirement.class, ScheduleContext.PRE_SCHEDULE).size();
            long postSpellbookCount = getStandardRequirements(SpellbookRequirement.class, ScheduleContext.POST_SCHEDULE).size();
            long preLocationCount = getStandardRequirements(LocationRequirement.class, ScheduleContext.PRE_SCHEDULE).size();
            long postLocationCount = getStandardRequirements(LocationRequirement.class, ScheduleContext.POST_SCHEDULE).size();
            
            if (preSpellbookCount > 1 || postSpellbookCount > 1) {
                log.error("Multiple spellbook requirements detected: pre={}, post={}", preSpellbookCount, postSpellbookCount);
                return false;
            }
            
            if (preLocationCount > 1 || postLocationCount > 1) {
                log.error("Multiple location requirements detected: pre={}, post={}", preLocationCount, postLocationCount);
                return false;
            }
            
            // Ensure cache consistency (if cache is valid)
            if (cacheValid) {
                int cacheSize = equipmentItemsCache.values().stream().mapToInt(Set::size).sum() +
                               inventorySlotRequirementsCache.values().stream().mapToInt(Set::size).sum() + 
                               shopRequirementsCache.size() + lootRequirementsCache.size();
                
                // Note: EITHER items are now distributed to equipment and inventory caches,
                // so we need to account for potential duplicates in the count
                long actualItemCount = requirements.values().stream()
                        .filter(req -> req instanceof ItemRequirement || 
                                      req instanceof ShopRequirement || 
                                      req instanceof LootRequirement)
                        .count();
                
                // For now, we'll log cache statistics but not fail validation
                // since EITHER items are counted in multiple caches
                log.debug("Cache statistics: cache={}, actual={}", cacheSize, actualItemCount);
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error during consistency validation", e);
            return false;
        }
    }
    
    /**
     * Gets debug information about the registry state.
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("RequirementRegistry Debug Info:\n");
        sb.append("  Total requirements: ").append(requirements.size()).append("\n");
        sb.append("  Cache valid: ").append(cacheValid).append("\n");
        sb.append("  Pre-schedule spellbook: ").append(preScheduleSpellbookRequirement != null).append("\n");
        sb.append("  Post-schedule spellbook: ").append(postScheduleSpellbookRequirement != null).append("\n");
        sb.append("  Pre-schedule location: ").append(preScheduleLocationRequirement != null).append("\n");
        sb.append("  Post-schedule location: ").append(postScheduleLocationRequirement != null).append("\n");
        
        Map<Class<?>, Long> typeCounts = requirements.values().stream()
                .collect(Collectors.groupingBy(Object::getClass, Collectors.counting()));
        
        sb.append("  Requirements by type:\n");
        typeCounts.forEach((type, count) -> 
                sb.append("    ").append(type.getSimpleName()).append(": ").append(count).append("\n"));
        
        return sb.toString();
    }
    
    /**
     * Gets a comprehensive validation summary of all requirements in the registry.
     * This provides an overall evaluation of requirement fulfillment status organized by priority and context.
     * 
     * @param context The schedule context to filter requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return A formatted string containing the validation summary
     */
    public String getValidationSummary(ScheduleContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Requirements Validation Summary ===\n");
        
        // Get all requirements for the specified context
        List<Requirement> contextRequirements = getAllRequirements().stream()
                .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
                .collect(Collectors.toList());
        
        if (contextRequirements.isEmpty()) {
            sb.append("No requirements found for context: ").append(context.name()).append("\n");
            return sb.toString();
        }
        
        // Group requirements by priority for better organization
        Map<RequirementPriority, List<Requirement>> byPriority = contextRequirements.stream()
                .collect(Collectors.groupingBy(Requirement::getPriority));
        
        // Overall statistics
        long totalRequirements = contextRequirements.size();
        long fulfilledCount = contextRequirements.stream().mapToLong(req -> req.isFulfilled() ? 1 : 0).sum();
        long notFulfilledCount = totalRequirements - fulfilledCount;
        
        sb.append("Context: ").append(context.name()).append("\n");
        sb.append("Total Requirements: ").append(totalRequirements).append("\n");
        sb.append("Fulfilled: ").append(fulfilledCount).append(" (")
          .append(totalRequirements > 0 ? (fulfilledCount * 100 / totalRequirements) : 0).append("%)\n");
        sb.append("Not Fulfilled: ").append(notFulfilledCount).append(" (")
          .append(totalRequirements > 0 ? (notFulfilledCount * 100 / totalRequirements) : 0).append("%)\n\n");
        
        // Detailed breakdown by priority
        for (RequirementPriority priority : RequirementPriority.values()) {
            List<Requirement> priorityRequirements = byPriority.getOrDefault(priority, Collections.emptyList());
            if (priorityRequirements.isEmpty()) {
                continue;
            }
            
            long priorityFulfilled = priorityRequirements.stream().mapToLong(req -> req.isFulfilled() ? 1 : 0).sum();
            long priorityNotFulfilled = priorityRequirements.size() - priorityFulfilled;
            
            sb.append("--- ").append(priority.name()).append(" Requirements ---\n");
            sb.append("Total: ").append(priorityRequirements.size()).append(" | ");
            sb.append("Fulfilled: ").append(priorityFulfilled).append(" | ");
            sb.append("Not Fulfilled: ").append(priorityNotFulfilled).append("\n");
            
            // Group by requirement type for better organization
            Map<RequirementType, List<Requirement>> byType = priorityRequirements.stream()
                    .collect(Collectors.groupingBy(Requirement::getRequirementType));
            
            for (Map.Entry<RequirementType, List<Requirement>> typeEntry : byType.entrySet()) {
                RequirementType type = typeEntry.getKey();
                List<Requirement> typeRequirements = typeEntry.getValue();
                
                long typeFulfilled = typeRequirements.stream().mapToLong(req -> req.isFulfilled() ? 1 : 0).sum();
                
                sb.append("  ").append(type.name()).append(": ")
                  .append(typeFulfilled).append("/").append(typeRequirements.size())
                  .append(" (").append(typeRequirements.size() > 0 ? (typeFulfilled * 100 / typeRequirements.size()) : 0).append("% fulfilled)\n");
                
                // Show validation status for each requirement in this type
                typeRequirements.forEach(req -> {
                    String status = req.isFulfilled() ? "✓" : "✗";
                    sb.append("    ").append(status).append(" ")
                      .append("Rating: ").append(req.getRating()).append("/10")
                      .append(" | Type: ").append(req.getRequirementType().name()).append("\n");
                });
            }
            sb.append("\n");
        }
        
        // Critical validation status
        List<Requirement> mandatoryNotFulfilled = contextRequirements.stream()
                .filter(req -> req.getPriority() == RequirementPriority.MANDATORY && !req.isFulfilled())
                .collect(Collectors.toList());
        
        if (!mandatoryNotFulfilled.isEmpty()) {
            sb.append("⚠️  CRITICAL: ").append(mandatoryNotFulfilled.size())
              .append(" mandatory requirements are not fulfilled!\n");
        } else if (byPriority.containsKey(RequirementPriority.MANDATORY)) {
            sb.append("✓ All mandatory requirements are fulfilled\n");
        }
        
        // External requirements summary
        List<Requirement> externalContextRequirements = getExternalRequirements(context);
        if (!externalContextRequirements.isEmpty()) {
            long externalFulfilled = externalContextRequirements.stream().mapToLong(req -> req.isFulfilled() ? 1 : 0).sum();
            sb.append("\n--- External Requirements ---\n");
            sb.append("Total: ").append(externalContextRequirements.size()).append(" | ");
            sb.append("Fulfilled: ").append(externalFulfilled).append(" | ");
            sb.append("Not Fulfilled: ").append(externalContextRequirements.size() - externalFulfilled).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets a concise validation status summary for quick overview.
     * 
     * @param context The schedule context to evaluate
     * @return A brief status summary string
     */
    public String getValidationStatusSummary(ScheduleContext context) {
        List<Requirement> contextRequirements = getAllRequirements().stream()
                .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
                .collect(Collectors.toList());
        
        if (contextRequirements.isEmpty()) {
            return "No requirements for " + context.name();
        }
        
        long totalRequirements = contextRequirements.size();
        long fulfilledCount = contextRequirements.stream().mapToLong(req -> req.isFulfilled() ? 1 : 0).sum();
        long mandatoryCount = contextRequirements.stream().mapToLong(req -> req.getPriority() == RequirementPriority.MANDATORY ? 1 : 0).sum();
        long mandatoryFulfilled = contextRequirements.stream()
                .filter(req -> req.getPriority() == RequirementPriority.MANDATORY)
                .mapToLong(req -> req.isFulfilled() ? 1 : 0).sum();
        
        String mandatoryStatus = mandatoryCount > 0 ? 
                String.format(" | Mandatory: %d/%d", mandatoryFulfilled, mandatoryCount) : "";
        
        return String.format("Requirements [%s]: %d/%d fulfilled (%.0f%%)%s", 
                context.name(), fulfilledCount, totalRequirements, 
                totalRequirements > 0 ? (fulfilledCount * 100.0 / totalRequirements) : 0.0,
                mandatoryStatus);
    }
    
    /**
     * Recursively extracts ItemRequirements from a LogicalRequirement.
     */
    private void extractItemRequirements(LogicalRequirement logical, LinkedHashSet<ItemRequirement> items) {
        for (Requirement child : logical.getChildRequirements()) {
            if (child instanceof ItemRequirement) {
                items.add((ItemRequirement) child);
            } else if (child instanceof LogicalRequirement) {
                extractItemRequirements((LogicalRequirement) child, items);
            }
        }
    }
    
    /**
     * Gets requirements for a specific inventory slot.
     * 
     * @param slot The inventory slot (0-27)
     * @return Logical requirements for the specified slot
     */
    public LinkedHashSet<LogicalRequirement> getInventorySlotRequirements(int slot) {
        if (!cacheValid) {
            rebuildCache();
        }
        return inventorySlotRequirementsCache.getOrDefault(slot, new LinkedHashSet<>());
    }
    
    /**
     * Gets all inventory slot requirements for a specific schedule context.
     * Returns only the LogicalRequirements that match the given context.
     * 
     * @param context The schedule context to filter by (PRE_SCHEDULE or POST_SCHEDULE)
     * @return Map of slot to context-specific logical requirements
     */
    public Map<Integer, LogicalRequirement> getAllInventorySlotRequirements(ScheduleContext context) {
        if (!cacheValid) {
            rebuildCache();
        }
        
        Map<Integer, LogicalRequirement> filteredCache = new HashMap<>();
        
        for (Map.Entry<Integer, LinkedHashSet<LogicalRequirement>> entry : inventorySlotRequirementsCache.entrySet()) {
            Integer slot = entry.getKey();
            LinkedHashSet<LogicalRequirement> requirements = entry.getValue();
            
            // Find the LogicalRequirement that matches the requested context
            for (LogicalRequirement requirement : requirements) {
                if (requirement.getScheduleContext() == context) {
                    filteredCache.put(slot, requirement);
                    break; // Each slot should have exactly one requirement per context
                }
            }
        }
        
        return filteredCache;
    }
    
  
    
    /**
     * Gets equipment items from slot-based cache, extracting ItemRequirements from LogicalRequirements.
     * 
     * @return Map of equipment slot to ItemRequirements
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> getEquipmentSlotItems() {
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = new HashMap<>();
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> logicalReqs = this.equipmentItemsCache;
        
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : logicalReqs.entrySet()) {
            LinkedHashSet<ItemRequirement> items = new LinkedHashSet<>();
            for (LogicalRequirement logical : entry.getValue()) {
                extractItemRequirements(logical, items);
            }
            if (!items.isEmpty()) {
                equipmentItems.put(entry.getKey(), items);
            }
        }
        
        return equipmentItems;
    }
    
    /**
     * Gets inventory items from slot-based cache, extracting ItemRequirements from LogicalRequirements.
     * 
     * @return Set of inventory ItemRequirements (from slot -1 which represents "any slot")
     */
    public LinkedHashSet<ItemRequirement> getInventorySlotItems() {
        LinkedHashSet<ItemRequirement> inventoryItems = new LinkedHashSet<>();
        LinkedHashSet<LogicalRequirement> logicalReqs = getInventorySlotRequirements(-1);
        
        for (LogicalRequirement logical : logicalReqs) {
            extractItemRequirements(logical, inventoryItems);
        }
        
        return inventoryItems;
    }
    
    /**
     * Gets items for a specific inventory slot.
     * 
     * @param slot The inventory slot (0-27, or -1 for "any slot")
     * @return ItemRequirements for the specified slot
     */
    public LinkedHashSet<ItemRequirement> getInventorySlotItems(int slot) {
        LinkedHashSet<ItemRequirement> slotItems = new LinkedHashSet<>();
        LinkedHashSet<LogicalRequirement> logicalReqs = getInventorySlotRequirements(slot);
        
        for (LogicalRequirement logical : logicalReqs) {
            extractItemRequirements(logical, slotItems);
        }
        
        return slotItems;
    }
    
    /**
     * Gets all EITHER items by aggregating from both equipment and inventory slot caches.
     * Since EITHER requirements are now distributed across caches, we need to collect them.
     * 
     * @return Set of all EITHER ItemRequirements
     */
    public LinkedHashSet<ItemRequirement> getAllEitherItems() {
        LinkedHashSet<ItemRequirement> eitherItems = new LinkedHashSet<>();
        
        // Check equipment slots for EITHER items
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = getEquipmentSlotItems();
        for (LinkedHashSet<ItemRequirement> slotItems : equipmentItems.values()) {
            for (ItemRequirement item : slotItems) {
                if (RequirementType.EITHER.equals(item.getRequirementType())) {
                    eitherItems.add(item);
                }
            }
        }
        
        // Check inventory slot cache for EITHER items
        LinkedHashSet<ItemRequirement> inventoryItems = getInventorySlotItems();
        for (ItemRequirement item : inventoryItems) {
            if (RequirementType.EITHER.equals(item.getRequirementType())) {
                eitherItems.add(item);
            }
        }
        
        return eitherItems;
    }
    
    // === UNIFIED ACCESS API ===
    
    /**
     * Gets all logical requirements (equipment + inventory) for a specific schedule context.
     * This provides a unified view of all item-related requirements.
     * 
     * @param context The schedule context to filter by (PRE_SCHEDULE or POST_SCHEDULE)
     * @return List of all logical requirements for the given context
     */
    public List<LogicalRequirement> getAllItemRequirements(ScheduleContext context) {
        List<LogicalRequirement> allLogicalReqs = new ArrayList<>();
        
        // Add equipment logical requirements (already filtered by context)
        allLogicalReqs.addAll(getEquipmentLogicalRequirements(context).values());
        
        // Add inventory logical requirements (already filtered by context)
        allLogicalReqs.addAll(getAllInventorySlotRequirements(context).values());
        
        return allLogicalReqs;
    }
    
    /**
     * Gets the total count of all logical requirements (equipment + inventory) for a specific schedule context.
     * This is a convenience method for UI components that need to display counts.
     * 
     * @param context The schedule context to filter by (PRE_SCHEDULE or POST_SCHEDULE)
     * @return Total count of logical requirements for the given context
     */
    public int getItemCount(ScheduleContext context) {
        return getEquipmentLogicalRequirements(context).size() + getAllInventorySlotRequirements(context).size();
    }
    
   
    
   
    
    /**
     * Ensures the cache is valid, rebuilding if necessary.
     */
    private void ensureCacheValid() {
        if (!cacheValid) {
            rebuildCache();
        }
    }
    
    /**
     * Ensures the external cache is valid, rebuilding if necessary.
     */
    private void ensureExternalCacheValid() {
        if (!externalCacheValid) {
            rebuildExternalCache();
        }
    }
    
    // ===============================
    // EXTERNAL REQUIREMENTS METHODS
    // ===============================
    
    /**
     * Gets all external requirements of a specific type for a specific schedule context.
     */
    @SuppressWarnings("unchecked")
    public <T extends Requirement> List<T> getExternalRequirements(Class<T> clazz, ScheduleContext context) {
        return externalRequirements.values().stream()
                .filter(clazz::isInstance)
                .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
                .map(req -> (T) req)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets external equipment logical requirements cache, rebuilding if necessary.
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> getExternalEquipmentLogicalRequirements() {
        ensureExternalCacheValid();
        return externalEquipmentItemsCache;
    }
    
    /**
     * Gets external inventory logical requirements cache, rebuilding if necessary.
     */
    public LinkedHashSet<LogicalRequirement> getExternalInventoryLogicalRequirements() {
        ensureExternalCacheValid();
        return externalInventorySlotRequirementsCache.getOrDefault(-1, new LinkedHashSet<>());
    }
    
    /**
     * Gets external shop logical requirements cache, rebuilding if necessary.
     */
    public LinkedHashSet<LogicalRequirement> getExternalShopLogicalRequirements() {
        ensureExternalCacheValid();
        return externalShopRequirementsCache;
    }
    
    /**
     * Gets external loot logical requirements cache, rebuilding if necessary.
     */
    public LinkedHashSet<LogicalRequirement> getExternalLootLogicalRequirements() {
        ensureExternalCacheValid();
        return externalLootRequirementsCache;
    }
    
    /**
     * Gets conditional item requirements cache, rebuilding if necessary.
     */
    public LinkedHashSet<ConditionalRequirement> getConditionalItemRequirements() {
        ensureCacheValid();
        return conditionalItemRequirementsCache;
    }
    
    /**
     * Gets external conditional item requirements cache, rebuilding if necessary.
     */
    public LinkedHashSet<ConditionalRequirement> getExternalConditionalItemRequirements() {
        ensureExternalCacheValid();
        return externalIConditionalItemRequirementsCache;
    }
    
    /**
     * Gets conditional item requirements for a specific schedule context.
     * 
     * @param context The schedule context to filter by
     * @return List of conditional requirements for the given context
     */
    public List<ConditionalRequirement> getConditionalItemRequirements(ScheduleContext context) {
        ensureCacheValid();
        return conditionalItemRequirementsCache.stream()
                .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets external conditional item requirements for a specific schedule context.
     * 
     * @param context The schedule context to filter by
     * @return List of external conditional requirements for the given context
     */
    public List<ConditionalRequirement> getExternalConditionalItemRequirements(ScheduleContext context) {
        ensureExternalCacheValid();
        return externalIConditionalItemRequirementsCache.stream()
                .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
                .collect(Collectors.toList());
    }
    
    /**
     * Rebuilds external requirement caches from the external requirements storage.
     */
    private synchronized void rebuildExternalCache() {
        if (externalCacheValid) {
            return; // Another thread already rebuilt the cache
        }
        
        log.debug("Rebuilding external requirement caches...");
        
        // Use unified rebuild logic for external requirements
        CacheSet newCaches = rebuildCacheUnified(externalRequirements.values(), "external");
        
        // Atomically update external caches
        this.externalEquipmentItemsCache = newCaches.equipmentCache;
        this.externalShopRequirementsCache = newCaches.shopCache;
        this.externalLootRequirementsCache = newCaches.lootCache;
        this.externalInventorySlotRequirementsCache = newCaches.inventorySlotCache;
        this.externalIConditionalItemRequirementsCache = newCaches.conditionalCache;
        
        externalCacheValid = true;
        log.debug("Rebuilt external requirement caches with {} external requirements", externalRequirements.size());
    }
    
    /**
     * Gets item requirements separated into equipment and inventory categories with detailed slot breakdowns.
     * This method uses the processed logical requirements from the caches to provide accurate counts
     * that reflect OR requirement grouping (e.g., pickaxes grouped as one requirement).
     * 
     * @param context The schedule context to filter by
     * @return A breakdown containing detailed slot-by-slot requirement analysis
     */
    public RequirementBreakdown getItemRequirementBreakdown(ScheduleContext context) {
        // Ensure caches are valid
        ensureCacheValid();
        
        Map<EquipmentInventorySlot, Map<RequirementPriority, Integer>> equipmentSlotBreakdown = new LinkedHashMap<>();
        Map<Integer, Map<RequirementPriority, Integer>> inventorySlotBreakdown = new LinkedHashMap<>();
        
        // Process equipment logical requirements by slot
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentItemsCache.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            LinkedHashSet<LogicalRequirement> logicalReqs = entry.getValue();
            
            Map<RequirementPriority, Integer> slotCounts = new EnumMap<>(RequirementPriority.class);
            slotCounts.put(RequirementPriority.MANDATORY, 0);
            slotCounts.put(RequirementPriority.RECOMMENDED, 0);
            slotCounts.put(RequirementPriority.RECOMMENDED, 0);
            
            // Count logical requirements (not individual items) by priority
            for (LogicalRequirement logicalReq : logicalReqs) {
                if (logicalReq.getScheduleContext() == context || logicalReq.getScheduleContext() == ScheduleContext.BOTH) {
                    RequirementPriority priority = logicalReq.getPriority();
                    slotCounts.put(priority, slotCounts.get(priority) + 1);
                }
            }
            
            // Only add slots that have requirements
            if (slotCounts.values().stream().anyMatch(count -> count > 0)) {
                equipmentSlotBreakdown.put(slot, slotCounts);
            }
        }
        
        // Process inventory logical requirements by slot (EXCLUDE -1 slot for "any slot" items)
        for (Map.Entry<Integer, LinkedHashSet<LogicalRequirement>> entry : inventorySlotRequirementsCache.entrySet()) {
            int slot = entry.getKey();
            LinkedHashSet<LogicalRequirement> logicalReqs = entry.getValue();
            
            // Skip -1 slot (any slot items) as requested
            if (slot == -1) {
                continue;
            }
            
            Map<RequirementPriority, Integer> slotCounts = new EnumMap<>(RequirementPriority.class);
            slotCounts.put(RequirementPriority.MANDATORY, 0);
            slotCounts.put(RequirementPriority.RECOMMENDED, 0);            
            
            // Count logical requirements (not individual items) by priority
            for (LogicalRequirement logicalReq : logicalReqs) {
                if (logicalReq.getScheduleContext() == context || logicalReq.getScheduleContext() == ScheduleContext.BOTH) {
                    RequirementPriority priority = logicalReq.getPriority();
                    slotCounts.put(priority, slotCounts.get(priority) + 1);
                }
            }
            
            // Only add slots that have requirements
            if (slotCounts.values().stream().anyMatch(count -> count > 0)) {
                inventorySlotBreakdown.put(slot, slotCounts);
            }
        }
        
        return new RequirementBreakdown(equipmentSlotBreakdown, inventorySlotBreakdown, getConditionalItemRequirements(context));
    }
    
    /**
     * Counts requirements by priority for a specific type and context.
     * 
     * @param clazz The requirement class to count
     * @param context The schedule context to filter by
     * @return A map of Priority to count
     */
    public <T extends Requirement> Map<RequirementPriority, Long> countRequirementsByPriority(Class<T> clazz, ScheduleContext context) {
        return getStandardRequirements(clazz, context).stream()
            .collect(Collectors.groupingBy(
                Requirement::getPriority,
                Collectors.counting()
            ));
    }
    
    /**
     * Helper class to hold detailed slot-by-slot requirement breakdowns with priority counts.
     */
    public static class RequirementBreakdown {
        private final Map<EquipmentInventorySlot, Map<RequirementPriority, Integer>> equipmentSlotBreakdown;
        private final Map<Integer, Map<RequirementPriority, Integer>> inventorySlotBreakdown;
        private final List<ConditionalRequirement> conditionalRequirements;

        public RequirementBreakdown(
                Map<EquipmentInventorySlot, Map<RequirementPriority, Integer>> equipmentSlotBreakdown,
                Map<Integer, Map<RequirementPriority, Integer>> inventorySlotBreakdown,
                List<ConditionalRequirement> conditionalRequirements) {
            this.equipmentSlotBreakdown = new LinkedHashMap<>(equipmentSlotBreakdown);
            this.inventorySlotBreakdown = new LinkedHashMap<>(inventorySlotBreakdown);
            this.conditionalRequirements = conditionalRequirements != null ? new ArrayList<>(conditionalRequirements) : List.of();
        }
        
        /**
         * Gets the detailed breakdown of equipment slots.
         * @return Map of equipment slot to priority counts
         */
        public Map<EquipmentInventorySlot, Map<RequirementPriority, Integer>> getEquipmentSlotBreakdown() {
            return new LinkedHashMap<>(equipmentSlotBreakdown);
        }
        
        /**
         * Gets the detailed breakdown of inventory slots.
         * @return Map of inventory slot to priority counts
         */
        public Map<Integer, Map<RequirementPriority, Integer>> getInventorySlotBreakdown() {
            return new LinkedHashMap<>(inventorySlotBreakdown);
        }
        
        /**
         * Gets total count of equipment requirements by priority across all slots.
         */
        public long getEquipmentCount(RequirementPriority priority) {
            return equipmentSlotBreakdown.values().stream()
                .mapToLong(slotCounts -> slotCounts.getOrDefault(priority, 0))
                .sum();
        }
        
        /**
         * Gets total count of inventory requirements by priority across all slots.
         */
        public long getInventoryCount(RequirementPriority priority) {
            return inventorySlotBreakdown.values().stream()
                .mapToLong(slotCounts -> slotCounts.getOrDefault(priority, 0))
                .sum();
        }
        
        /**
         * Gets total count of all equipment requirements across all slots.
         */
        public long getTotalEquipmentCount() {
            return equipmentSlotBreakdown.values().stream()
                .mapToLong(slotCounts -> slotCounts.values().stream().mapToInt(Integer::intValue).sum())
                .sum();
        }
        
        /**
         * Gets total count of all inventory requirements across all slots.
         */
        public long getTotalInventoryCount() {
            return inventorySlotBreakdown.values().stream()
                .mapToLong(slotCounts -> slotCounts.values().stream().mapToInt(Integer::intValue).sum())
                .sum();
        }
        
        /**
         * Gets a detailed string representation of the breakdown showing slot-by-slot details, including conditional requirements.
         */
        public String getDetailedBreakdownString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== DETAILED REQUIREMENT BREAKDOWN ===\n");

            if (!equipmentSlotBreakdown.isEmpty()) {
                sb.append("Equipment Slots:\n");
                for (Map.Entry<EquipmentInventorySlot, Map<RequirementPriority, Integer>> entry : equipmentSlotBreakdown.entrySet()) {
                    EquipmentInventorySlot slot = entry.getKey();
                    Map<RequirementPriority, Integer> counts = entry.getValue();
                    sb.append(String.format("  %s: M=%d, R=%d\n",
                        slot.name(),
                        counts.getOrDefault(RequirementPriority.MANDATORY, 0),
                        counts.getOrDefault(RequirementPriority.RECOMMENDED, 0)));
                }
            }

            if (!inventorySlotBreakdown.isEmpty()) {
                sb.append("Inventory Slots:\n");
                for (Map.Entry<Integer, Map<RequirementPriority, Integer>> entry : inventorySlotBreakdown.entrySet()) {
                    Integer slot = entry.getKey();
                    Map<RequirementPriority, Integer> counts = entry.getValue();
                    sb.append(String.format("  Slot %d: M=%d, R=%d\n",
                        slot,
                        counts.getOrDefault(RequirementPriority.MANDATORY, 0),
                        counts.getOrDefault(RequirementPriority.RECOMMENDED, 0)));
                }
            }

            // Conditional requirements statistics
            if (!conditionalRequirements.isEmpty()) {
                sb.append("\nConditional Requirements Statistics:\n");
                int totalSteps = 0;
                int totalItemReqs = 0;
                int totalLogicalReqs = 0;
                for (ConditionalRequirement conditionalReq : conditionalRequirements) {
                    sb.append("  ").append(conditionalReq.getName()).append(":\n");
                    int stepIdx = 0;
                    for (var step : conditionalReq.getSteps()) {
                        totalSteps++;
                        sb.append(String.format("    Step %d: %s\n", stepIdx++, step.getDescription()));
                        var req = step.getRequirement();
                        if (req instanceof LogicalRequirement) {
                            totalLogicalReqs++;
                            sb.append("      LogicalRequirement\n");
                        } else if (req instanceof ItemRequirement) {
                            totalItemReqs++;
                            sb.append("      ItemRequirement\n");
                        } else {
                            sb.append("      OtherRequirement\n");
                        }
                    }
                }
                sb.append(String.format("  Total Conditional Steps: %d\n", totalSteps));
                sb.append(String.format("  Total LogicalRequirements: %d\n", totalLogicalReqs));
                sb.append(String.format("  Total ItemRequirements: %d\n", totalItemReqs));
            }

            return sb.toString();
        }
        
        /**
         * Gets count of requirements for a specific equipment slot and priority.
         */
        public int getEquipmentSlotCount(EquipmentInventorySlot slot, RequirementPriority priority) {
            return equipmentSlotBreakdown.getOrDefault(slot, Map.of()).getOrDefault(priority, 0);
        }
        
        /**
         * Gets count of requirements for a specific inventory slot and priority.
         */
        public int getInventorySlotCount(int slot, RequirementPriority priority) {
            return inventorySlotBreakdown.getOrDefault(slot, Map.of()).getOrDefault(priority, 0);
        }
        public boolean isEmpty() {
            return equipmentSlotBreakdown.isEmpty() && inventorySlotBreakdown.isEmpty();
        }
    }



     /**
     * Provides a comprehensive string representation of the entire registry.
     * Shows all requirements organized by type, slot, and schedule context with proper formatting.
     * 
     * @return A detailed string representation of the registry
     */
    @Override
    public String toString() {
        return getDetailedRegistryString();
    }
    
    /**
     * Gets a detailed string representation of the entire registry.
     * Organizes requirements by type and provides clear structure with proper indentation.
     * 
     * @return A comprehensive string showing all registered requirements
     */
    public String getDetailedRegistryString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== Requirement Registry Summary ===\n");
        sb.append("Total Requirements: ").append(requirements.size()).append("\n");
        sb.append("Cache Valid: ").append(cacheValid).append("\n\n");
        
        if (requirements.isEmpty()) {
            sb.append("No requirements registered.\n");
            return sb.toString();
        }
        
        // Ensure cache is rebuilt for accurate display
        ensureCacheValid();
        // Add detailed breakdown for conditional requirements just before returning
        if (!conditionalItemRequirementsCache.isEmpty()) {
            sb.append("\n=== CONDITIONAL REQUIREMENTS BREAKDOWN ===\n");
            for (ConditionalRequirement conditionalReq : conditionalItemRequirementsCache) {
                sb.append(formatConditionalRequirement(conditionalReq, "  ")).append("\n");
            }
        }
        
        // Display Equipment Requirements by Slot
        sb.append("=== EQUIPMENT REQUIREMENTS BY SLOT ===\n");
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache = this.equipmentItemsCache;
        if (equipmentCache.isEmpty()) {
            sb.append("\tNo equipment requirements registered.\n");
        } else {
            for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentCache.entrySet()) {
                sb.append("\t").append(entry.getKey().name()).append(":\n");
                for (LogicalRequirement logicalReq : entry.getValue()) {
                    sb.append("\t\t").append(formatLogicalRequirement(logicalReq, "\t\t")).append("\n");
                }
            }
        }
        sb.append("\n");
        
        // Display Inventory Slot Requirements
        sb.append("=== INVENTORY SLOT REQUIREMENTS ===\n");
        Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache = inventorySlotRequirementsCache;
        if (inventorySlotCache.isEmpty()) {
            sb.append("\tNo specific inventory slot requirements registered.\n");
        } else {
            for (Map.Entry<Integer, LinkedHashSet<LogicalRequirement>> entry : inventorySlotCache.entrySet()) {
                String slotName = entry.getKey() == -1 ? "ANY_SLOT" : "SLOT_" + entry.getKey();
                sb.append("\t").append(slotName).append(":\n");
                for (LogicalRequirement logicalReq : entry.getValue()) {
                    sb.append("\t\t").append(formatLogicalRequirement(logicalReq, "\t\t")).append("\n");
                }
            }
        }
        sb.append("\n");
        
        // Display Shop Requirements
        sb.append("=== SHOP REQUIREMENTS ===\n");
        LinkedHashSet<LogicalRequirement> shopCache = shopRequirementsCache;
        if (shopCache.isEmpty()) {
            sb.append("\tNo shop requirements registered.\n");
        } else {
            for (LogicalRequirement logicalReq : shopCache) {
                sb.append("\t").append(formatLogicalRequirement(logicalReq, "\t")).append("\n");
            }
        }
        sb.append("\n");
        
        // Display Loot Requirements
        sb.append("=== LOOT REQUIREMENTS ===\n");
        LinkedHashSet<LogicalRequirement> lootCache = lootRequirementsCache;
        if (lootCache.isEmpty()) {
            sb.append("\tNo loot requirements registered.\n");
        } else {
            for (LogicalRequirement logicalReq : lootCache) {
                sb.append("\t").append(formatLogicalRequirement(logicalReq, "\t")).append("\n");
            }
        }
        sb.append("\n");
        
        // Display Conditional Requirements
        sb.append("=== CONDITIONAL REQUIREMENTS ===\n");
        LinkedHashSet<ConditionalRequirement> conditionalCache = conditionalItemRequirementsCache;
        if (conditionalCache.isEmpty()) {
            sb.append("\tNo conditional requirements registered.\n");
        } else {
            for (ConditionalRequirement conditionalReq : conditionalCache) {
                sb.append(formatConditionalRequirement(conditionalReq, "\t")).append("\n");
            }
        }
        sb.append("\n");
        
        // Display Single-Instance Requirements
        sb.append("=== SINGLE-INSTANCE REQUIREMENTS ===\n");
        sb.append("\tPre-Schedule Spellbook: ").append(preScheduleSpellbookRequirement != null ? 
                preScheduleSpellbookRequirement.getName() : "None").append("\n");
        sb.append("\tPost-Schedule Spellbook: ").append(postScheduleSpellbookRequirement != null ? 
                postScheduleSpellbookRequirement.getName() : "None").append("\n");
        sb.append("\tPre-Schedule Location: ").append(preScheduleLocationRequirement != null ? 
                preScheduleLocationRequirement.getName() : "None").append("\n");
        sb.append("\tPost-Schedule Location: ").append(postScheduleLocationRequirement != null ? 
                postScheduleLocationRequirement.getName() : "None").append("\n");
        
        return sb.toString();
    }

    
    



/**
     * Gets a detailed string representation of cached requirements for a specific schedule context.
     * This shows only the processed logical requirements from the cache for the given context,
     * providing a focused view of what will actually be fulfilled.
     * 
     * @param context The schedule context to display (PRE_SCHEDULE or POST_SCHEDULE)
     * @return A formatted string showing cached requirements for the context
     */
    public String getDetailedCacheStringForContext(ScheduleContext context) {
        StringBuilder sb = new StringBuilder();
        if (context == null) {
            sb.append("Invalid context provided.\n");
            return sb.toString();
        }
        sb.append("=== Cached Requirements for Context: ").append(context.name()).append(" ===\n");
        
     
        
        // Ensure cache is rebuilt for accurate display
        ensureCacheValid();
        
        boolean hasAnyRequirements = false;
        
        // Display Equipment Requirements by Slot for this context
        sb.append("=== EQUIPMENT REQUIREMENTS BY SLOT ===\n");
        Map<EquipmentInventorySlot, LogicalRequirement> equipmentCache = getEquipmentLogicalRequirements(context);
        if (equipmentCache.isEmpty()) {
            sb.append("\tNo equipment requirements for context: ").append(context.name()).append("\n");
        } else {
            hasAnyRequirements = true;
            for (Map.Entry<EquipmentInventorySlot, LogicalRequirement> entry : equipmentCache.entrySet()) {
                sb.append("\t").append(entry.getKey().name()).append(":\n");
                sb.append("\t\t").append(formatLogicalRequirement(entry.getValue(), "\t\t")).append("\n");
            }
        }
        sb.append("\n");
        
        // Display Inventory Slot Requirements for this context
        sb.append("=== INVENTORY SLOT REQUIREMENTS ===\n");
        Map<Integer, LogicalRequirement> inventorySlotCache = getAllInventorySlotRequirements(context);
        if (inventorySlotCache.isEmpty()) {
            sb.append("\tNo inventory slot requirements for context: ").append(context.name()).append("\n");
        } else {
            hasAnyRequirements = true;
            for (Map.Entry<Integer, LogicalRequirement> entry : inventorySlotCache.entrySet()) {
                String slotName = entry.getKey() == -1 ? "ANY_SLOT" : "SLOT_" + entry.getKey();
                sb.append("\t").append(slotName).append(":\n");
                sb.append("\t\t").append(formatLogicalRequirement(entry.getValue(), "\t\t")).append("\n");
            }
        }
        sb.append("\n");
        
        // Display Shop Requirements for this context
        sb.append("=== SHOP REQUIREMENTS ===\n");
        LinkedHashSet<LogicalRequirement> shopCache = getStandardShopLogicalRequirements();
        List<LogicalRequirement> contextShopReqs = LogicalRequirement.filterByContext(new ArrayList<>(shopCache), context);
        if (contextShopReqs.isEmpty()) {
            sb.append("\tNo shop requirements for context: ").append(context.name()).append("\n");
        } else {
            hasAnyRequirements = true;
            for (LogicalRequirement logicalReq : contextShopReqs) {
                sb.append("\t").append(formatLogicalRequirement(logicalReq, "\t")).append("\n");
            }
        }
        sb.append("\n");
        
        // Display Loot Requirements for this context
        sb.append("=== LOOT REQUIREMENTS ===\n");
        LinkedHashSet<LogicalRequirement> lootCache = getStandardLootLogicalRequirements();
        List<LogicalRequirement> contextLootReqs = LogicalRequirement.filterByContext(new ArrayList<>(lootCache), context);
        if (contextLootReqs.isEmpty()) {
            sb.append("\tNo loot requirements for context: ").append(context.name()).append("\n");
        } else {
            hasAnyRequirements = true;
            for (LogicalRequirement logicalReq : contextLootReqs) {
                sb.append("\t").append(formatLogicalRequirement(logicalReq, "\t")).append("\n");
            }
        }
        sb.append("\n");
        
        // Display Conditional Requirements for this context
        sb.append("=== CONDITIONAL REQUIREMENTS ===\n");
        List<ConditionalRequirement> conditionalReqs = getConditionalItemRequirements(context);
        if (conditionalReqs.isEmpty()) {
            sb.append("\tNo conditional requirements for context: ").append(context.name()).append("\n");
        } else {
            hasAnyRequirements = true;
            for (ConditionalRequirement conditionalReq : conditionalReqs) {
                sb.append(formatConditionalRequirement(conditionalReq, "\t")).append("\n");
            }
        }
        sb.append("\n");

  
        
        // Display Single-Instance Requirements for this context
        sb.append("=== SINGLE-INSTANCE REQUIREMENTS ===\n");
        boolean hasSpellbook = false;
        boolean hasLocation = false;
        
        if (context == ScheduleContext.PRE_SCHEDULE || context == ScheduleContext.BOTH) {
            if (preScheduleSpellbookRequirement != null) {
                sb.append("\tPre-Schedule Spellbook: ").append(preScheduleSpellbookRequirement.getName()).append("\n");
                hasSpellbook = true;
                hasAnyRequirements = true;
            }
            if (preScheduleLocationRequirement != null) {
                sb.append("\tPre-Schedule Location: ").append(preScheduleLocationRequirement.getName()).append("\n");
                hasLocation = true;
                hasAnyRequirements = true;
            }
        }
        
        if (context == ScheduleContext.POST_SCHEDULE || context == ScheduleContext.BOTH) {
            if (postScheduleSpellbookRequirement != null) {
                sb.append("\tPost-Schedule Spellbook: ").append(postScheduleSpellbookRequirement.getName()).append("\n");
                hasSpellbook = true;
                hasAnyRequirements = true;
            }
            if (postScheduleLocationRequirement != null) {
                sb.append("\tPost-Schedule Location: ").append(postScheduleLocationRequirement.getName()).append("\n");
                hasLocation = true;
                hasAnyRequirements = true;
            }
        }
        
        if (!hasSpellbook && !hasLocation) {
            sb.append("\tNo single-instance requirements for context: ").append(context.name()).append("\n");
        }
        sb.append("\n");
        
        // Summary
        if (!hasAnyRequirements) {
            sb.append("=== SUMMARY ===\n");
            sb.append("No cached requirements found for context: ").append(context.name()).append("\n");
        }
        
        return sb.toString();
    }
      
    /**
     * Formats a ConditionalRequirement for display, including all steps, their conditions, and requirements.
     * @param conditionalReq The ConditionalRequirement to format
     * @param indent The indentation prefix
     * @return A formatted string representation
     */
    private String formatConditionalRequirement(ConditionalRequirement conditionalReq, String indent) {
        StringBuilder sb2 = new StringBuilder();
        sb2.append(conditionalReq.getName())
          .append(" [StopOnFirstFailure: ").append(conditionalReq.isStopOnFirstFailure())
          .append(", Parallel: ").append(conditionalReq.isAllowParallelExecution())
          .append("]\n");
        int stepIdx = 0;
        for (var step : conditionalReq.getSteps()) {
            boolean conditionMet = false;
            try { conditionMet = step.needsExecution(); } catch (Throwable t) { conditionMet = false; }
            sb2.append(indent).append("Step ").append(stepIdx++).append(": ")
              .append(step.getDescription())
              .append(" [ConditionMet: ").append(conditionMet)
              .append(", Optional: ").append(step.isOptional()).append("]\n");
            // Show the requirement for this step
            Requirement req = step.getRequirement();
            if (req instanceof LogicalRequirement) {
                sb2.append(indent).append("  ").append(formatLogicalRequirement((LogicalRequirement)req, indent + "  ")).append("\n");
            } else {
                sb2.append(indent).append("  ").append(formatSingleRequirement(req)).append("\n");
            }
        }
        return sb2.toString();
    }
    /**
     * Formats a logical requirement for display with proper indentation.
     * 
     * @param logicalReq The logical requirement to format
     * @param indent The indentation prefix
     * @return A formatted string representation
     */
    private String formatLogicalRequirement(LogicalRequirement logicalReq, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(logicalReq.getClass().getSimpleName()).append(": ");
        
        if (logicalReq instanceof OrRequirement) {
            OrRequirement orReq = (OrRequirement) logicalReq;
            sb.append("(").append(orReq.getChildRequirements().size()).append(" options)");
            for (Requirement childReq : orReq.getChildRequirements()) {
                sb.append("\n").append(indent).append("\t- ").append(formatSingleRequirement(childReq));
            }
        } else {
            sb.append(formatSingleRequirement(logicalReq));
        }
        
        return sb.toString();
    }
    
    /**
     * Formats a single requirement for display.
     * 
     * @param req The requirement to format
     * @return A formatted string representation
     */
    private String formatSingleRequirement(Requirement req) {
        if (req instanceof ItemRequirement) {
            ItemRequirement itemReq = (ItemRequirement) req;
            return  String.format("%s (id:%d amount:%d)[%s, Rating: %d] - %s", 
                itemReq.getName(), 
                itemReq.getId(), 
                itemReq.getAmount(),
                itemReq.getPriority().name(), 
                itemReq.getRating(),
                itemReq.getScheduleContext().name());
        }
        return String.format("%s [%s, Rating: %d] - %s", 
                req.getName(), 
                req.getPriority().name(), 
                req.getRating(),
                req.getScheduleContext().name());
    }
}
