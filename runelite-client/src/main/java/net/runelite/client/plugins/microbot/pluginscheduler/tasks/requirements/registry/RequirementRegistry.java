package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection.LootRequirement;
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
    
    // Central storage - this is the single source of truth
    private final Map<RequirementKey, Requirement> requirements = new ConcurrentHashMap<>();
    
    // Slot-based inventory requirements (index 0-27, or -1 for any slot, -2 only for equipment)
    @Getter
    private volatile Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotRequirementsCache = new HashMap<>();
    
    // Cached views for efficient access (rebuilt when requirements change)
    @Getter
    private volatile Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentItemsCache = new HashMap<>();
    @Getter
    private volatile LinkedHashSet<LogicalRequirement> shopRequirementsCache = new LinkedHashSet<>();
    @Getter
    private volatile LinkedHashSet<LogicalRequirement> lootRequirementsCache = new LinkedHashSet<>();
    @Getter
    private volatile LinkedHashSet<LogicalRequirement> conditionalRequirementsCache = new LinkedHashSet<>();
    
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
    public <T extends Requirement> List<T> getRequirements(Class<T> clazz) {
        return requirements.values().stream()
                .filter(clazz::isInstance)
                .map(req -> (T) req)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all requirements for a specific schedule context.
     */
    public List<Requirement> getRequirements(ScheduleContext context) {
        return requirements.values().stream()
                .filter(req -> req.hasScheduleContext() && 
                        (req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all requirements of a specific type for a specific schedule context.
     */
    @SuppressWarnings("unchecked")
    public <T extends Requirement> List<T> getRequirements(Class<T> clazz, ScheduleContext context) {
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
     * Invalidates cached views, forcing them to be rebuilt on next access.
     */
    private void invalidateCache() {
        cacheValid = false;
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
        
        // New caches with logical requirements
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> newEquipmentCache = new HashMap<>();
        LinkedHashSet<LogicalRequirement> newShopCache = new LinkedHashSet<>();
        LinkedHashSet<LogicalRequirement> newLootCache = new LinkedHashSet<>();
        Map<Integer, LinkedHashSet<LogicalRequirement>> newInventorySlotCache = new HashMap<>();
        
        // Group requirements by type for logical grouping
        Map<EquipmentInventorySlot, List<ItemRequirement>> equipmentBySlot = new HashMap<>();
        List<ItemRequirement> inventoryItems = new ArrayList<>();
        List<ItemRequirement> eitherItems = new ArrayList<>();
        List<ShopRequirement> shopReqs = new ArrayList<>();
        List<LootRequirement> lootReqs = new ArrayList<>();
        
        // First pass: collect and group requirements
        for (Requirement requirement : requirements.values()) {
            if (requirement instanceof LogicalRequirement) {
                // Already logical - add to appropriate cache
                LogicalRequirement logical = (LogicalRequirement) requirement;
                addLogicalRequirementToCache(logical, newEquipmentCache, 
                        newShopCache, newLootCache, newInventorySlotCache);
            } else if (requirement instanceof ItemRequirement) {
                ItemRequirement itemReq = (ItemRequirement) requirement;
                switch (itemReq.getRequirementType()) {
                    case EQUIPMENT:
                        if (itemReq.getEquipmentSlot() != null) {
                            equipmentBySlot.computeIfAbsent(itemReq.getEquipmentSlot(), 
                                    k -> new ArrayList<>()).add(itemReq);
                        }
                        break;
                    case INVENTORY:
                        inventoryItems.add(itemReq);
                        break;
                    case EITHER:
                        eitherItems.add(itemReq);
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
                        // These types are not expected for ItemRequirement
                        log.warn("Unexpected requirement type for ItemRequirement: {}", itemReq.getRequirementType());
                        break;
                }
            } else if (requirement instanceof ShopRequirement) {
                shopReqs.add((ShopRequirement) requirement);
            } else if (requirement instanceof LootRequirement) {
                lootReqs.add((LootRequirement) requirement);
            }
        }
        
        // Second pass: create logical requirements from grouped items
        createLogicalRequirementsFromGroups(equipmentBySlot, inventoryItems, eitherItems, 
                shopReqs, lootReqs, newEquipmentCache, 
                newShopCache, newLootCache, newInventorySlotCache);
        
        // Sort all caches
        sortAllCaches(newEquipmentCache, newShopCache, newLootCache, newInventorySlotCache);
        
        // Atomically update caches
        equipmentItemsCache = newEquipmentCache;
        shopRequirementsCache = newShopCache;
        lootRequirementsCache = newLootCache;
        inventorySlotRequirementsCache = newInventorySlotCache;
        
        cacheValid = true;
        log.debug("Rebuilt requirement caches with {} total requirements", requirements.size());
    }
    
    /**
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
     * Creates logical requirements from grouped individual requirements.
     */
    private void createLogicalRequirementsFromGroups(
            Map<EquipmentInventorySlot, List<ItemRequirement>> equipmentBySlot,
            List<ItemRequirement> inventoryItems,
            List<ItemRequirement> eitherItems,
            List<ShopRequirement> shopReqs,
            List<LootRequirement> lootReqs,
            Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache,
            LinkedHashSet<LogicalRequirement> shopCache,
            LinkedHashSet<LogicalRequirement> lootCache,
            Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache) {
        
        // Equipment items: create OR requirements for each slot
        for (Map.Entry<EquipmentInventorySlot, List<ItemRequirement>> entry : equipmentBySlot.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            List<ItemRequirement> slotItems = entry.getValue();
            
            if (slotItems.size() == 1) {
                // Single item - wrap in OR requirement for consistency
                ItemRequirement item = slotItems.get(0);
                OrRequirement orReq = new OrRequirement(item.getPriority(), item.getRating(),
                        "Equipment for " + slot.name(), item.getScheduleContext(), item);
                equipmentCache.computeIfAbsent(slot, k -> new LinkedHashSet<>()).add(orReq);
            } else {
                // Multiple items - create OR requirement with all alternatives
                Priority highestPriority = slotItems.stream()
                        .map(Requirement::getPriority)
                        .min(Priority::compareTo)
                        .orElse(Priority.OPTIONAL);
                int highestRating = slotItems.stream()
                        .mapToInt(Requirement::getRating)
                        .max()
                        .orElse(5);
                ScheduleContext commonContext = determineCommonScheduleContext(slotItems);
                
                OrRequirement orReq = new OrRequirement(highestPriority, highestRating,
                        slot.name() + " equipment options (" + slotItems.size() + " alternatives)",
                        commonContext, slotItems.toArray(new Requirement[0]));
                equipmentCache.computeIfAbsent(slot, k -> new LinkedHashSet<>()).add(orReq);
            }
        }
        
        // For now, wrap individual requirements in OR requirements
        // TODO: In the future, implement intelligent grouping (e.g., food types)
        for (ItemRequirement item : inventoryItems) {
            OrRequirement orReq = new OrRequirement(item.getPriority(), item.getRating(),
                    item.getDescription(), item.getScheduleContext(), item);
            
            // Add to slot-specific cache if specific slot is required, otherwise use -1 for any slot
            if (item.hasSpecificInventorySlot()) {
                inventorySlotCache.computeIfAbsent(item.getInventorySlot(), 
                        k -> new LinkedHashSet<>()).add(orReq);
            } else {
                inventorySlotCache.computeIfAbsent(-1, k -> new LinkedHashSet<>()).add(orReq);
            }
        }
        
        // EITHER items: distribute to both inventory and equipment caches based on their capabilities
        for (ItemRequirement item : eitherItems) {
            OrRequirement orReq = new OrRequirement(item.getPriority(), item.getRating(),
                    item.getDescription(), item.getScheduleContext(), item);
            
            // Add to inventory slot cache (specific slot or -1 for any slot)
            if (item.hasSpecificInventorySlot()) {
                inventorySlotCache.computeIfAbsent(item.getInventorySlot(), 
                        k -> new LinkedHashSet<>()).add(orReq);
            } else {
                inventorySlotCache.computeIfAbsent(-1, k -> new LinkedHashSet<>()).add(orReq);
            }
            
            // Also add to equipment cache if it has an equipment slot
            if (item.getEquipmentSlot() != null) {
                equipmentCache.computeIfAbsent(item.getEquipmentSlot(), 
                        k -> new LinkedHashSet<>()).add(orReq);
            }
        }
        
        for (ShopRequirement shop : shopReqs) {
            OrRequirement orReq = new OrRequirement(shop.getPriority(), shop.getRating(),
                    shop.getDescription(), shop.getScheduleContext(), shop);
            shopCache.add(orReq);
        }
        
        for (LootRequirement loot : lootReqs) {
            OrRequirement orReq = new OrRequirement(loot.getPriority(), loot.getRating(),
                    loot.getDescription(), loot.getScheduleContext(), loot);
            lootCache.add(orReq);
        }
    }
    
    /**
     * Determines a common schedule context from a list of requirements.
     */
    private ScheduleContext determineCommonScheduleContext(List<? extends Requirement> requirements) {
        if (requirements.isEmpty()) {
            return ScheduleContext.BOTH;
        }
        
        ScheduleContext first = requirements.get(0).getScheduleContext();
        boolean allSame = requirements.stream().allMatch(req -> req.getScheduleContext() == first);
        
        return allSame ? first : ScheduleContext.BOTH;
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
     * Gets equipment logical requirements cache, rebuilding if necessary.
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> getEquipmentLogicalRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        return equipmentItemsCache;
    }
    
    /**
     * Gets equipment items cache, rebuilding if necessary.
     * @deprecated Use getEquipmentLogicalRequirements() instead
     */
    @Deprecated
    public Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> getEquipmentItems() {
        // For backward compatibility, extract ItemRequirements from logical requirements
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> logicalCache = getEquipmentLogicalRequirements();
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> itemCache = new HashMap<>();
        
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : logicalCache.entrySet()) {
            LinkedHashSet<ItemRequirement> items = new LinkedHashSet<>();
            for (LogicalRequirement logical : entry.getValue()) {
                extractItemRequirements(logical, items);
            }
            itemCache.put(entry.getKey(), items);
        }
        
        return itemCache;
    }
    
    /**
     * Gets inventory logical requirements cache, rebuilding if necessary.
     * Returns requirements that can be placed in any inventory slot.
     */
    public LinkedHashSet<LogicalRequirement> getInventoryLogicalRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        return inventorySlotRequirementsCache.getOrDefault(-1, new LinkedHashSet<>());
    }
    
    /**
     * Gets inventory items cache, rebuilding if necessary.
     * @deprecated Use getInventoryLogicalRequirements() instead
     */
    @Deprecated
    public LinkedHashSet<ItemRequirement> getInventoryItems() {
        LinkedHashSet<ItemRequirement> items = new LinkedHashSet<>();
        for (LogicalRequirement logical : getInventoryLogicalRequirements()) {
            extractItemRequirements(logical, items);
        }
        return items;
    }
    
    /**
     * Gets either logical requirements cache, rebuilding if necessary.
     * @deprecated EITHER requirements are now distributed to equipment and inventory caches.
     *             Use getEquipmentLogicalRequirements() and getInventoryLogicalRequirements() instead.
     */
    @Deprecated
    public LinkedHashSet<LogicalRequirement> getEitherLogicalRequirements() {
        log.warn("getEitherLogicalRequirements() is deprecated. EITHER requirements are now distributed to equipment and inventory caches.");
        return new LinkedHashSet<>();
    }
    
    /**
     * Gets either items cache, rebuilding if necessary.
     * @deprecated Use getEitherLogicalRequirements() instead
     */
    @Deprecated
    public LinkedHashSet<ItemRequirement> getEitherItems() {
        LinkedHashSet<ItemRequirement> items = new LinkedHashSet<>();
        for (LogicalRequirement logical : getEitherLogicalRequirements()) {
            extractItemRequirements(logical, items);
        }
        return items;
    }
    
    /**
     * Gets shop logical requirements cache, rebuilding if necessary.
     */
    public LinkedHashSet<LogicalRequirement> getShopLogicalRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        return shopRequirementsCache;
    }
    
    /**
     * Gets shop requirements cache, rebuilding if necessary.
     * @deprecated Use getShopLogicalRequirements() instead
     */
    @Deprecated
    public LinkedHashSet<ShopRequirement> getShopRequirements() {
        LinkedHashSet<ShopRequirement> shops = new LinkedHashSet<>();
        for (LogicalRequirement logical : getShopLogicalRequirements()) {
            extractShopRequirements(logical, shops);
        }
        return shops;
    }
    
    /**
     * Gets loot logical requirements cache, rebuilding if necessary.
     */
    public LinkedHashSet<LogicalRequirement> getLootLogicalRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        return lootRequirementsCache;
    }
    
    /**
     * Gets loot requirements cache, rebuilding if necessary.
     * @deprecated Use getLootLogicalRequirements() instead
     */
    @Deprecated
    public LinkedHashSet<LootRequirement> getLootRequirements() {
        LinkedHashSet<LootRequirement> loots = new LinkedHashSet<>();
        for (LogicalRequirement logical : getLootLogicalRequirements()) {
            extractLootRequirements(logical, loots);
        }
        return loots;
    }
    
    /**
     * Validates the consistency of the registry.
     * 
     * @return true if the registry is consistent, false otherwise
     */
    public boolean validateConsistency() {
        try {
            // Ensure single-instance requirements are properly referenced
            long preSpellbookCount = getRequirements(SpellbookRequirement.class, ScheduleContext.PRE_SCHEDULE).size();
            long postSpellbookCount = getRequirements(SpellbookRequirement.class, ScheduleContext.POST_SCHEDULE).size();
            long preLocationCount = getRequirements(LocationRequirement.class, ScheduleContext.PRE_SCHEDULE).size();
            long postLocationCount = getRequirements(LocationRequirement.class, ScheduleContext.POST_SCHEDULE).size();
            
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
     * Recursively extracts ShopRequirements from a LogicalRequirement.
     */
    private void extractShopRequirements(LogicalRequirement logical, LinkedHashSet<ShopRequirement> shops) {
        for (Requirement child : logical.getChildRequirements()) {
            if (child instanceof ShopRequirement) {
                shops.add((ShopRequirement) child);
            } else if (child instanceof LogicalRequirement) {
                extractShopRequirements((LogicalRequirement) child, shops);
            }
        }
    }
    
    /**
     * Recursively extracts LootRequirements from a LogicalRequirement.
     */
    private void extractLootRequirements(LogicalRequirement logical, LinkedHashSet<LootRequirement> loots) {
        for (Requirement child : logical.getChildRequirements()) {
            if (child instanceof LootRequirement) {
                loots.add((LootRequirement) child);
            } else if (child instanceof LogicalRequirement) {
                extractLootRequirements((LogicalRequirement) child, loots);
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
     * Gets all inventory slot requirements.
     * 
     * @return Map of slot to logical requirements
     */
    public Map<Integer, LinkedHashSet<LogicalRequirement>> getAllInventorySlotRequirements() {
        if (!cacheValid) {
            rebuildCache();
        }
        return new HashMap<>(inventorySlotRequirementsCache);
    }
    
    /**
     * Gets equipment items from slot-based cache, extracting ItemRequirements from LogicalRequirements.
     * 
     * @return Map of equipment slot to ItemRequirements
     */
    public Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> getEquipmentSlotItems() {
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentItems = new HashMap<>();
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> logicalReqs = getEquipmentLogicalRequirements();
        
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
        
        // Display Equipment Requirements by Slot
        sb.append("=== EQUIPMENT REQUIREMENTS BY SLOT ===\n");
        Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentCache = getEquipmentLogicalRequirements();
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
        Map<Integer, LinkedHashSet<LogicalRequirement>> inventorySlotCache = getInventorySlotRequirementsCache();
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
        LinkedHashSet<LogicalRequirement> shopCache = getShopRequirementsCache();
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
        LinkedHashSet<LogicalRequirement> lootCache = getLootRequirementsCache();
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
        LinkedHashSet<LogicalRequirement> conditionalCache = getConditionalRequirementsCache();
        if (conditionalCache.isEmpty()) {
            sb.append("\tNo conditional requirements registered.\n");
        } else {
            for (LogicalRequirement logicalReq : conditionalCache) {
                sb.append("\t").append(formatLogicalRequirement(logicalReq, "\t")).append("\n");
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
        return String.format("%s [%s, Rating: %d] - %s", 
                req.getName(), 
                req.getPriority().name(), 
                req.getRating(),
                req.getScheduleContext().name());
    }
    
    /**
     * Ensures the cache is valid, rebuilding if necessary.
     */
    private void ensureCacheValid() {
        if (!cacheValid) {
            rebuildCache();
        }
    }
}
