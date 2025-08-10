package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopRequirement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * Abstract base class for logical combinations of requirements.
 * Provides common functionality for AND and OR requirement combinations.
 * 
 * Similar to LogicalCondition but adapted for the requirement system.
 * Logical requirements can contain other requirements (including other logical requirements)
 * and evaluate them according to logical rules.
 * 
 * @see net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public abstract class LogicalRequirement extends Requirement {
    
    @Getter
    protected final List<Requirement> childRequirements = new ArrayList<>();
    
    /**
     * Protected constructor for logical requirements.
     * Child classes must call this constructor and provide their own requirement type.
     * 
     * @param requirementType The type of logical requirement (AND_LOGICAL or OR_LOGICAL)
     * @param priority Priority level of this logical requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     * @param scheduleContext When this requirement should be fulfilled
     * @param requirements Child requirements to combine logically
     */
    protected LogicalRequirement(RequirementType requirementType, Priority priority, int rating, 
                               String description, ScheduleContext scheduleContext, 
                               Requirement... requirements) {
        super(requirementType, priority, rating, description, List.of(), scheduleContext);
        this.childRequirements.addAll(Arrays.asList(requirements));
    }
    
    /**
     * Adds a child requirement to this logical requirement.
     * 
     * @param requirement The requirement to add
     * @return This logical requirement for method chaining
     */
    public LogicalRequirement addRequirement(Requirement requirement) {
        if (requirement != null && !childRequirements.contains(requirement)) {
            childRequirements.add(requirement);
        }
        return this;
    }
    
    /**
     * Removes a child requirement from this logical requirement.
     * 
     * @param requirement The requirement to remove
     * @return true if the requirement was removed, false if it wasn't found
     */
    public boolean removeRequirement(Requirement requirement) {
        return childRequirements.remove(requirement);
    }
    
    /**
     * Checks if this logical requirement contains the specified requirement,
     * either directly or within any nested logical requirements.
     * 
     * @param targetRequirement The requirement to search for
     * @return true if the requirement exists within this logical structure, false otherwise
     */
    public boolean contains(Requirement targetRequirement) {
        if (childRequirements.contains(targetRequirement)) {
            return true;
        }
        
        // Check nested logical requirements
        for (Requirement child : childRequirements) {
            if (child instanceof LogicalRequirement) {
                if (((LogicalRequirement) child).contains(targetRequirement)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Gets the total number of requirements in this logical structure (including nested).
     * 
     * @return Total count of all requirements
     */
    public int getTotalRequirementCount() {
        int count = 0;
        for (Requirement child : childRequirements) {
            if (child instanceof LogicalRequirement) {
                count += ((LogicalRequirement) child).getTotalRequirementCount();
            } else {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets the count of fulfilled requirements in this logical structure.
     * 
     * @return Count of fulfilled requirements
     */
    public int getFulfilledRequirementCount() {
        int count = 0;
        for (Requirement child : childRequirements) {
            if (child instanceof LogicalRequirement) {
                // For logical requirements, check if they are fulfilled as a whole
                if (((LogicalRequirement) child).isLogicallyFulfilled()) {
                    count += ((LogicalRequirement) child).getTotalRequirementCount();
                }
            } else {
                if (child.isFulfilled()) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Abstract method to check if this logical requirement is fulfilled.
     * AND requirements need all children fulfilled, OR requirements need at least one.
     * 
     * @return true if the logical requirement is fulfilled, false otherwise
     */
    public abstract boolean isLogicallyFulfilled();
    
    /**
     * Abstract method to get requirements that are blocking fulfillment.
     * For AND: all unfulfilled requirements block
     * For OR: all requirements block if none are fulfilled
     * 
     * @return List of requirements that are preventing fulfillment
     */
    public abstract List<Requirement> getBlockingRequirements();
    
    /**
     * Gets the progress percentage for this logical requirement.
     * 
     * @return Progress percentage (0.0 to 100.0)
     */
    public double getProgressPercentage() {
        if (childRequirements.isEmpty()) {
            return 100.0;
        }
        
        int total = getTotalRequirementCount();
        int fulfilled = getFulfilledRequirementCount();
        
        return total > 0 ? (fulfilled * 100.0) / total : 0.0;
    }
    
    /**
     * Checks if this logical requirement is fulfilled.
     * This method is required by the Requirement base class.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if the logical requirement is fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement(ScheduledExecutorService executorService) {
        log.debug("Attempting to fulfill logical requirement: {}", getName());
        
        // For logical requirements, we don't directly fulfill them
        // Instead, we check if they are already fulfilled by their children

        boolean fulfilled = fulfillLogicalRequirement();
        
        if (fulfilled) {
            log.debug("Logical requirement {} is already fulfilled", getName());
        } else {
            log.debug("Logical requirement {} is not fulfilled. Blocking requirements: {}", 
                    getName(), getBlockingRequirements().size());
        }
        
        return fulfilled;
    }
    
    /**
     * Gets a unique identifier for this logical requirement.
     * Includes the logical operator and child requirement identifiers.
     * 
     * @return A unique identifier string
     */
    @Override
    public String getUniqueIdentifier() {
        String childIds = childRequirements.stream()
                .map(Requirement::getUniqueIdentifier)
                .collect(Collectors.joining(","));
        
        return String.format("%s:LOGICAL:%s:[%s]", 
                requirementType.name(),
                getClass().getSimpleName(),
                childIds);
    }
    
    /**
     * Gets the best available requirement from this logical structure.
     * For OR requirements, this returns the highest-rated fulfilled requirement.
     * For AND requirements, this returns null if not all are fulfilled.
     * 
     * @return The best available requirement, or null if none available
     */
    public Optional<Requirement> getBestAvailableRequirement() {
        if (!isLogicallyFulfilled()) {
            return Optional.empty();
        }
        
        // Find the highest-rated fulfilled requirement
        return childRequirements.stream()
                .filter(req -> {
                    if (req instanceof LogicalRequirement) {
                        return ((LogicalRequirement) req).isLogicallyFulfilled();
                    } else {
                        return req.isFulfilled();
                    }
                })
                .max(Requirement::compareTo);
    }
    
    /**
     * Gets all requirements that can fulfill this logical requirement.
     * For OR requirements, returns all fulfilled requirements.
     * For AND requirements, returns all requirements if all are fulfilled.
     * 
     * @return List of requirements that can fulfill this logical requirement
     */
    public List<Requirement> getAvailableRequirements() {
        if (!isLogicallyFulfilled()) {
            return new ArrayList<>();
        }
        
        List<Requirement> available = new ArrayList<>();
        
        for (Requirement child : childRequirements) {
            if (child instanceof LogicalRequirement) {
                LogicalRequirement logical = (LogicalRequirement) child;
                if (logical.isLogicallyFulfilled()) {
                    available.addAll(logical.getAvailableRequirements());
                }
            } else {
                if (child.isFulfilled()) {
                    available.add(child);
                }
            }
        }
        
        return available;
    }
    
    /**
     * Gets a detailed status string for this logical requirement.
     * 
     * @return Formatted status information
     */
    public String getStatusInfo() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(getName()).append(" (").append(getClass().getSimpleName()).append(")\n");
        sb.append("  Status: ").append(isLogicallyFulfilled() ? "Fulfilled" : "Not Fulfilled").append("\n");
        sb.append("  Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        sb.append("  Child Requirements: ").append(childRequirements.size()).append("\n");
        
        for (int i = 0; i < childRequirements.size(); i++) {
            Requirement child = childRequirements.get(i);
            String prefix = (i == childRequirements.size() - 1) ? "    └─ " : "    ├─ ";
            
            if (child instanceof LogicalRequirement) {
                sb.append(prefix).append(((LogicalRequirement) child).getStatusInfo().replace("\n", "\n    "));
            } else {
                sb.append(prefix).append(child.getName())
                  .append(" [").append(child.isFulfilled() ? "FULFILLED" : "NOT FULFILLED").append("]\n");
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("%s[%s children, %s]", 
                getClass().getSimpleName(), 
                childRequirements.size(),
                isLogicallyFulfilled() ? "FULFILLED" : "NOT FULFILLED");
    }



      /**
     * Attempts to fulfill a single logical requirement.
     * 
     * @param logicalReq The logical requirement to fulfill
     * @param preferEquipment If true, prefer equipping items; if false, prefer inventory
     * @return true if the logical requirement was fulfilled
     */
    private boolean fulfillLogicalRequirement() {
        // If already fulfilled, nothing to do

        if (this instanceof OrRequirement) {
            return fulfillOrRequirement((OrRequirement) this);
        } else {

            log.warn("Unknown logical requirement type: {}", this.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Fulfills an OR logical requirement (at least one child must be fulfilled).
     * Sorts child requirements by rating (highest first) and attempts to fulfill
     * only the highest-rated available requirement.
     * 
     * @param orReq The OR requirement
     * @param preferEquipment If true, prefer equipping items; if false, prefer inventory
     * @return true if at least one child requirement was fulfilled
     */
    private boolean fulfillOrRequirement(OrRequirement orReq) {
        // Sort child requirements by rating (highest first), then by priority
        List<Requirement> sortedRequirements = orReq.getChildRequirements().stream()
                .sorted((r1, r2) -> {
                    // First sort by rating (highest first)
                    int ratingCompare = Integer.compare(r2.getRating(), r1.getRating());
                    if (ratingCompare != 0) {
                        return ratingCompare;
                    }
                    // Then by priority (mandatory first)
                    return r1.getPriority().compareTo(r2.getPriority());
                })
                .collect(Collectors.toList());
        boolean foundFulfilled = false;
        // Try to fulfill all requirements in order of rating (highest first), but only need one to succeed
        for (Requirement childReq : sortedRequirements) {
            if (childReq instanceof LogicalRequirement) {
                if (childReq.fulfillRequirement()) {
                    log.info("Fulfilled OR requirement using logical child: {} (rating: {})", 
                            childReq.getDescription(), childReq.getRating());
                    foundFulfilled = true; 
                }
            } else if (childReq instanceof ItemRequirement) {
                ItemRequirement itemReq = (ItemRequirement) childReq;                
                itemReq.fulfillRequirement();
                
                if (orReq.isFulfilled() || itemReq.isFulfilled()) {
                    log.info("Fulfilled OR requirement using item: {} (rating: {})", 
                            itemReq.getName(), itemReq.getRating());
                    foundFulfilled = true; 
                }
            } else if (childReq instanceof ShopRequirement) {
                ShopRequirement shopReq = (ShopRequirement) childReq;
                try {
                    if (shopReq.fulfillRequirement()) {
                        log.info("Fulfilled shop OR requirement: {} (rating: {})", 
                                shopReq.getName(), shopReq.getRating());
                        foundFulfilled = true; 
                    }
                } catch (Exception e) {
                    log.info("Failed to fulfill shop requirement {}: {}", shopReq.getName(), e.getMessage());
                }
            } else if (childReq instanceof LootRequirement) {
                LootRequirement lootReq = (LootRequirement) childReq;
                try {
                    if (lootReq.fulfillRequirement()) {
                        log.debug("Fulfilled loot OR requirement: {} (rating: {})", 
                                lootReq.getName(), lootReq.getRating());
                        foundFulfilled = true; 
                    }
                } catch (Exception e) {
                    log.debug("Failed to fulfill loot requirement {}: {}", lootReq.getName(), e.getMessage());
                }
            }
        }
        
        log.info("OR requirement {} fulfilled: {}", 
                orReq.getDescription(), foundFulfilled);
        return foundFulfilled; // No child requirements were fulfilled
    }
    
    // ========== STATIC UTILITY METHODS FOR LOGICAL REQUIREMENT PROCESSING ==========
    
    /**
     * Filters logical requirements by schedule context.
     * 
     * @param requirements The logical requirements to filter
     * @param context The schedule context to match
     * @return List of requirements matching the context
     */
    public static List<LogicalRequirement> filterByContext(List<LogicalRequirement> requirements, ScheduleContext context) {
        return requirements.stream()
            .filter(req -> req.getScheduleContext() == context || req.getScheduleContext() == ScheduleContext.BOTH)
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if any requirement within a logical requirement collection has mandatory items.
     * 
     * @param logicalReqs The logical requirements to check
     * @return true if any requirement contains mandatory items
     */
    public static boolean hasMandatoryItems(List<LogicalRequirement> logicalReqs) {
        return logicalReqs.stream()
            .anyMatch(req -> extractItemRequirementsFromLogical(req).stream()
                .anyMatch(ItemRequirement::isMandatory));
    }
    
    /**
     * Checks if any requirement within a logical requirement collection has mandatory shop items.
     * 
     * @param logicalReqs The logical requirements to check
     * @return true if any requirement contains mandatory shop items
     */
    public static boolean hasMandatoryShopItems(List<LogicalRequirement> logicalReqs) {
        return logicalReqs.stream()
            .anyMatch(req -> extractShopRequirementsFromLogical(req).stream()
                .anyMatch(ShopRequirement::isMandatory));
    }
    
    /**
     * Checks if any requirement within a logical requirement collection has mandatory loot items.
     * 
     * @param logicalReqs The logical requirements to check
     * @return true if any requirement contains mandatory loot items
     */
    public static boolean hasMandatoryLootItems(List<LogicalRequirement> logicalReqs) {
        return logicalReqs.stream()
            .anyMatch(req -> extractLootRequirementsFromLogical(req).stream()
                .anyMatch(LootRequirement::isMandatory));
    }
    
    /**
     * Extracts all item requirements from a collection of logical requirements.
     * 
     * @param logicalReqs The logical requirements to process
     * @return List of all item requirements found within the logical structure
     */
    public static List<ItemRequirement> extractAllItemRequirements(List<LogicalRequirement> logicalReqs) {
        return logicalReqs.stream()
            .flatMap(req -> extractItemRequirementsFromLogical(req).stream())
            .collect(Collectors.toList());
    }
    
    /**
     * Extracts all shop requirements from a collection of logical requirements.
     * 
     * @param logicalReqs The logical requirements to process
     * @return List of all shop requirements found within the logical structure
     */
    public static List<ShopRequirement> extractAllShopRequirements(List<LogicalRequirement> logicalReqs) {
        return logicalReqs.stream()
            .flatMap(req -> extractShopRequirementsFromLogical(req).stream())
            .collect(Collectors.toList());
    }
    
    /**
     * Extracts all loot requirements from a collection of logical requirements.
     * 
     * @param logicalReqs The logical requirements to process
     * @return List of all loot requirements found within the logical structure
     */
    public static List<LootRequirement> extractAllLootRequirements(List<LogicalRequirement> logicalReqs) {
        return logicalReqs.stream()
            .flatMap(req -> extractLootRequirementsFromLogical(req).stream())
            .collect(Collectors.toList());
    }
    
    /**
     * Extracts only mandatory item requirements from a collection of logical requirements.
     * 
     * @param logicalReqs The logical requirements to process
     * @return List of mandatory item requirements found within the logical structure
     */
    public static List<ItemRequirement> extractMandatoryItemRequirements(List<LogicalRequirement> logicalReqs) {
        return extractAllItemRequirements(logicalReqs).stream()
            .filter(ItemRequirement::isMandatory)
            .collect(Collectors.toList());
    }
    
    /**
     * Processes a collection of logical requirements and fulfills them according to their logic.
     * Provides common error handling and logging patterns.
     * 
     * @param logicalReqs The logical requirements to fulfill
     * @param requirementType Description of the requirement type for logging
     * @return true if all mandatory requirements were fulfilled, false otherwise
     */
    public static boolean fulfillLogicalRequirements(List<LogicalRequirement> logicalReqs, String requirementType) {
        if (logicalReqs.isEmpty()) {
            log.debug("No {} requirements to fulfill", requirementType);
            return true;
        }
        
        boolean success = true;
        int fulfilled = 0;
        
        for (int i = 0; i < logicalReqs.size(); i++) {
            LogicalRequirement logicalReq = logicalReqs.get(i);
            
            try {
                log.debug("Processing {} logical requirement {}/{}: {}", 
                    requirementType, i + 1, logicalReqs.size(), logicalReq.getDescription());
                
                if (logicalReq.isLogicallyFulfilled()) {
                    fulfilled++;
                    continue;
                }
                
                boolean requirementFulfilled = logicalReq.fulfillRequirement();
                
                if (requirementFulfilled) {
                    fulfilled++;
                } else {
                    // Check if any child requirement was mandatory
                    boolean hasMandatory = hasMandatoryItems(Arrays.asList(logicalReq));
                    
                    if (hasMandatory) {
                        log.error("Failed to fulfill mandatory {} requirement: {}", 
                            requirementType, logicalReq.getDescription());
                        success = false;
                        break; // Stop on mandatory failure
                    } else {
                        log.debug("Failed to fulfill optional {} requirement: {}", 
                            requirementType, logicalReq.getDescription());
                    }
                }
            } catch (Exception e) {
                log.error("Error fulfilling {} requirement {}: {}", 
                    requirementType, logicalReq.getDescription(), e.getMessage());
                
                boolean hasMandatory = hasMandatoryItems(Arrays.asList(logicalReq));
                
                if (hasMandatory) {
                    success = false;
                }
            }
        }
        
        log.debug("{} requirements fulfillment completed. Success: {}, Fulfilled: {}/{}", 
            requirementType, success, fulfilled, logicalReqs.size());
        return success;
    }
    
    /**
     * Recursively extracts all ItemRequirement instances from a logical requirement structure.
     * This method handles nested logical requirements and returns a flat list of all item requirements.
     * 
     * @param logicalReq The logical requirement to extract from
     * @return List of all ItemRequirement instances found within the logical structure
     */
    public static List<ItemRequirement> extractItemRequirementsFromLogical(LogicalRequirement logicalReq) {
        List<ItemRequirement> items = new ArrayList<>();
        
        for (Requirement child : logicalReq.getChildRequirements()) {
            if (child instanceof ItemRequirement) {
                items.add((ItemRequirement) child);
            } else if (child instanceof LogicalRequirement) {
                items.addAll(extractItemRequirementsFromLogical((LogicalRequirement) child));
            }
        }
        
        return items;
    }
    
    /**
     * Recursively extracts all ShopRequirement instances from a logical requirement structure.
     * This method handles nested logical requirements and returns a flat list of all shop requirements.
     * 
     * @param logicalReq The logical requirement to extract from
     * @return List of all ShopRequirement instances found within the logical structure
     */
    public static List<ShopRequirement> extractShopRequirementsFromLogical(LogicalRequirement logicalReq) {
        List<ShopRequirement> shops = new ArrayList<>();
        
        for (Requirement child : logicalReq.getChildRequirements()) {
            if (child instanceof ShopRequirement) {
                shops.add((ShopRequirement) child);
            } else if (child instanceof LogicalRequirement) {
                shops.addAll(extractShopRequirementsFromLogical((LogicalRequirement) child));
            }
        }
        
        return shops;
    }
    
    /**
     * Recursively extracts all LootRequirement instances from a logical requirement structure.
     * This method handles nested logical requirements and returns a flat list of all loot requirements.
     * 
     * @param logicalReq The logical requirement to extract from
     * @return List of all LootRequirement instances found within the logical structure
     */
    public static List<LootRequirement> extractLootRequirementsFromLogical(LogicalRequirement logicalReq) {
        List<LootRequirement> loots = new ArrayList<>();
        
        for (Requirement child : logicalReq.getChildRequirements()) {
            if (child instanceof LootRequirement) {
                loots.add((LootRequirement) child);
            } else if (child instanceof LogicalRequirement) {
                loots.addAll(extractLootRequirementsFromLogical((LogicalRequirement) child));
            }
        }
        
        return loots;
    }
    //     private String formatLogicalRequirement(LogicalRequirement logicalReq) {
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
}
