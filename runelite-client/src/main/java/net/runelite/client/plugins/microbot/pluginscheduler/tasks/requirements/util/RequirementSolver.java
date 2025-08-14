package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementMode;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry.RequirementRegistry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.OrderedRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.LogicalRequirement;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for solving different types of requirements with common patterns.
 * Provides reusable fulfillment logic and error handling patterns.
 * 
 * This is the unified fulfillment system that handles both standard and external requirements
 * using the same logic patterns and error handling.
 */
@Slf4j
public class RequirementSolver {
    
    /**
     * Fulfills shop requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param shopRequirements The shop requirements to fulfill
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all shop requirements were fulfilled successfully, false otherwise
     */
    public static boolean fulfillShopRequirements(CompletableFuture<Boolean> scheduledFuture,List<ShopRequirement> shopRequirements, TaskContext context) {
        List<ShopRequirement> contextReqs = shopRequirements.stream()
            .filter(req -> req.getTaskContext() == context || req.getTaskContext() == TaskContext.BOTH)
            .collect(java.util.stream.Collectors.toList());
            
        if (contextReqs.isEmpty()) {
            log.debug("No shop requirements for context: {}", context);
            return true;
        }
        
        boolean success = true;
        int fulfilled = 0;
        
        for (int i = 0; i < contextReqs.size(); i++) {
            ShopRequirement requirement = contextReqs.get(i);
            
            try {
                log.info("Processing shop requirement {}/{}: {}", i + 1, contextReqs.size(), requirement.getName());
                boolean requirementFulfilled = requirement.fulfillRequirement(scheduledFuture);
                
                if (requirementFulfilled) {
                    fulfilled++;
                } else {
                    if (requirement.isMandatory()) {
                        log.error("Failed to fulfill mandatory shop requirement: {}", requirement.getName());
                        success = false;
                        break; // Stop on mandatory failure
                    } else {
                        log.debug("Failed to fulfill optional shop requirement: {}", requirement.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error fulfilling shop requirement {}: {}", requirement.getName(), e.getMessage());
                if (requirement.isMandatory()) {
                    success = false;
                }
            }
        }
        
        log.info("Shop requirements fulfillment completed. Success: {}, Fulfilled: {}/{}", success, fulfilled, contextReqs.size());
        return success;
    }
    
    /**
     * Fulfills loot requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param lootLogical The logical loot requirements to fulfill
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all loot requirements were fulfilled successfully, false otherwise
     */
    public static boolean fulfillLootRequirements(CompletableFuture<Boolean> scheduledFuture, List<LogicalRequirement> lootLogical, TaskContext context) {
        List<LogicalRequirement> contextReqs = LogicalRequirement.filterByContext(lootLogical, context);
            
        if (contextReqs.isEmpty()) {
            log.debug("No loot requirements for context: {}", context);
            return true;
        }
        
        return LogicalRequirement.fulfillLogicalRequirements(scheduledFuture,contextReqs, "loot");
    }
    
    /**
     * Fulfills location requirements for the specified schedule context.
     * Uses the unified filtering system to automatically handle pre/post schedule requirements.
     * 
     * @param locationReqs The location requirements to fulfill
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all location requirements were fulfilled successfully, false otherwise
     */
    public static boolean fulfillLocationRequirements(CompletableFuture<Boolean> scheduledFuture, List<LocationRequirement> locationReqs, TaskContext context) {
        List<LocationRequirement> contextReqs = locationReqs.stream()
            .filter(req -> req.getTaskContext() == context || req.getTaskContext() == TaskContext.BOTH)
            .collect(java.util.stream.Collectors.toList());
            
        if (contextReqs.isEmpty()) {
            log.debug("No location requirements for context: {}", context);
            return true;
        }
        
        boolean success = true;
        int fulfilled = 0;
        
        for (int i = 0; i < contextReqs.size(); i++) {
            LocationRequirement requirement = contextReqs.get(i);
            if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                log.warn("Scheduled future is cancelled, skipping location requirement fulfillment: {}", requirement.getName());
                return false; // Skip if scheduled future is cancelled
            }
            try {
                log.debug("Processing location requirement {}/{}: {}", i + 1, contextReqs.size(), requirement.getName());
                boolean requirementFulfilled = requirement.fulfillRequirement(scheduledFuture);
                
                if (requirementFulfilled) {
                    fulfilled++;
                } else {
                    if (requirement.isMandatory()) {
                        log.error("Failed to fulfill mandatory location requirement: {}", requirement.getName());
                        success = false;
                        break; // Stop on mandatory failure
                    } else {
                        log.debug("Failed to fulfill optional location requirement: {}", requirement.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error fulfilling location requirement {}: {}", requirement.getName(), e.getMessage());
                if (requirement.isMandatory()) {
                    success = false;
                }
            }
        }
        
        log.debug("Location requirements fulfillment completed. Success: {}, Fulfilled: {}/{}", success, fulfilled, contextReqs.size());
        return success;
    }
    
    /**
     * Fulfills spellbook requirements for the specified schedule context.
     * 
     * @param spellbookReqs The spellbook requirements to fulfill
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param saveCurrentSpellbook Whether to save the current spellbook before switching
     * @return true if all spellbook requirements were fulfilled successfully, false otherwise
     */
    public static boolean fulfillSpellbookRequirements(CompletableFuture<Boolean> scheduledFuture, List<SpellbookRequirement> spellbookReqs, 
                                                     TaskContext context, 
                                                     boolean saveCurrentSpellbook) {
        List<SpellbookRequirement> contextReqs = spellbookReqs.stream()
            .filter(req -> req.getTaskContext() == context || req.getTaskContext() == TaskContext.BOTH)
            .collect(java.util.stream.Collectors.toList());
            
        if (contextReqs.isEmpty()) {
            log.debug("No spellbook requirements for context: {}", context);
            return true;
        }
        
        boolean success = true;
        int fulfilled = 0;
        
        for (int i = 0; i < contextReqs.size(); i++) {
            SpellbookRequirement requirement = contextReqs.get(i);
            
            try {
                log.debug("Processing spellbook requirement {}/{}: {}", i + 1, contextReqs.size(), requirement.getName());
                
                // Save current spellbook if requested (typically for PRE_SCHEDULE context)
                if (saveCurrentSpellbook) {
                    // This would need to be handled by the calling class as it maintains state
                    log.debug("Spellbook saving requested (handled by caller)");
                }
                
                boolean requirementFulfilled = requirement.fulfillRequirement(scheduledFuture);
                
                if (requirementFulfilled) {
                    fulfilled++;
                } else {
                    if (requirement.isMandatory()) {
                        log.error("Failed to fulfill mandatory spellbook requirement: {}", requirement.getName());
                        success = false;
                        break; // Stop on mandatory failure
                    } else {
                        log.debug("Failed to fulfill optional spellbook requirement: {}", requirement.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error fulfilling spellbook requirement {}: {}", requirement.getName(), e.getMessage());
                if (requirement.isMandatory()) {
                    success = false;
                }
            }
        }
        
        log.debug("Spellbook requirements fulfillment completed. Success: {}, Fulfilled: {}/{}", success, fulfilled, contextReqs.size());
        return success;
    }
    
    /**
     * Fulfills conditional requirements for the specified schedule context.
     * 
     * @param conditionalReqs The conditional requirements to fulfill
     * @param orderedReqs The ordered requirements to fulfill
     * @param context The schedule context (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if all conditional requirements were fulfilled successfully, false otherwise
     */
    public static boolean fulfillConditionalRequirements(CompletableFuture<Boolean> scheduledFuture,
                                                        TaskExecutionState executionState,
                                                        List<ConditionalRequirement> conditionalReqs,
                                                        List<OrderedRequirement> orderedReqs,
                                                        TaskContext context) {
        List<ConditionalRequirement> contextConditionalReqs = conditionalReqs.stream()
            .filter(req -> req.getTaskContext() == context || req.getTaskContext() == TaskContext.BOTH)
            .collect(java.util.stream.Collectors.toList());
            
        List<OrderedRequirement> contextOrderedReqs = orderedReqs.stream()
            .filter(req -> req.getTaskContext() == context || req.getTaskContext() == TaskContext.BOTH)
            .collect(java.util.stream.Collectors.toList());
        
        if (contextConditionalReqs.isEmpty() && contextOrderedReqs.isEmpty()) {
            log.debug("No conditional or ordered requirements to fulfill for context: {}", context);
            return true; // No requirements to fulfill
        }
        
        boolean success = true;
        int currentIndex = 0;
        int totalReqs = contextConditionalReqs.size() + contextOrderedReqs.size();
        
        // Process ConditionalRequirements first
        for (ConditionalRequirement requirement : contextConditionalReqs) {
            try {
                log.debug("Processing conditional requirement {}/{}: {}", ++currentIndex, totalReqs, requirement.getName());
                boolean fulfilled = requirement.fulfillRequirement(scheduledFuture);
                if (!fulfilled && requirement.getPriority() == RequirementPriority.MANDATORY) {
                    Microbot.log("Failed to fulfill mandatory conditional requirement: " + requirement.getName(), Level.ERROR);
                    success = false;
                } else if (!fulfilled) {
                    Microbot.log("Failed to fulfill optional conditional requirement: " + requirement.getName(), Level.WARN);
                }
            } catch (Exception e) {
                log.error("Error fulfilling conditional requirement '{}': {}", requirement.getName(), e.getMessage(), e);
                Microbot.log("Error fulfilling conditional requirement " + requirement.getName() + ": " + e.getMessage(), Level.ERROR);
                if (requirement.getPriority() == RequirementPriority.MANDATORY) {
                    success = false;
                }
            }
        }
        
        // Process OrderedRequirements second
        for (OrderedRequirement requirement : contextOrderedReqs) {
            try {
                log.debug("Processing ordered requirement {}/{}: {}", ++currentIndex, totalReqs, requirement.getName());
                boolean fulfilled = requirement.fulfillRequirement(scheduledFuture);
                if (!fulfilled && requirement.getPriority() == RequirementPriority.MANDATORY) {
                    Microbot.log("Failed to fulfill mandatory ordered requirement: " + requirement.getName(), Level.ERROR);
                    success = false;
                } else if (!fulfilled) {
                    Microbot.log("Failed to fulfill optional ordered requirement: " + requirement.getName(), Level.WARN);
                }
            } catch (Exception e) {
                log.error("Error fulfilling ordered requirement '{}': {}", requirement.getName(), e.getMessage(), e);
                Microbot.log("Error fulfilling ordered requirement " + requirement.getName() + ": " + e.getMessage(), Level.ERROR);
                if (requirement.getPriority() == RequirementPriority.MANDATORY) {
                    success = false;
                }
            }
        }
        
        log.debug("Conditional requirements fulfillment completed. Success: {}, Total processed: {}", success, totalReqs);
        return success;
    }
    
    /**
     * Generic requirement fulfillment method with common error handling patterns.
     * 
     * @param requirements List of requirements to fulfill
     * @param requirementTypeName Name of the requirement type for logging
     * @param context The schedule context
     * @param <T> The requirement type
     * @return true if all mandatory requirements were fulfilled successfully
     */
    public static <T extends Requirement> boolean fulfillRequirements( CompletableFuture<Boolean> scheduledFuture,
                                                                        List<T> requirements, 
                                                                     String requirementTypeName,
                                                                     TaskContext context) {
        List<T> contextReqs = requirements.stream()
            .filter(req -> req.getTaskContext() == context || req.getTaskContext() == TaskContext.BOTH)
            .collect(java.util.stream.Collectors.toList());
            
        if (contextReqs.isEmpty()) {
            log.debug("No {} requirements for context: {}", requirementTypeName, context);
            return true;
        }
        
        boolean success = true;
        int fulfilled = 0;
        
        for (int i = 0; i < contextReqs.size(); i++) {
            T requirement = contextReqs.get(i);
            
            try {
                log.debug("Processing {} requirement {}/{}: {}", requirementTypeName, i + 1, contextReqs.size(), requirement.getName());
                boolean requirementFulfilled = requirement.fulfillRequirement(scheduledFuture);
                
                if (requirementFulfilled) {
                    fulfilled++;
                } else {
                    if (requirement.isMandatory()) {
                        log.error("Failed to fulfill mandatory {} requirement: {}", requirementTypeName, requirement.getName());
                        success = false;
                        break; // Stop on mandatory failure
                    } else {
                        log.debug("Failed to fulfill optional {} requirement: {}", requirementTypeName, requirement.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error fulfilling {} requirement {}: {}", requirementTypeName, requirement.getName(), e.getMessage());
                if (requirement.isMandatory()) {
                    success = false;
                }
            }
        }
        
        log.debug("{} requirements fulfillment completed. Success: {}, Fulfilled: {}/{}", 
                requirementTypeName, success, fulfilled, contextReqs.size());
        return success;
    }
}
