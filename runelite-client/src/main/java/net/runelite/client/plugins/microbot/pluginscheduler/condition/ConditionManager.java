package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.Skill;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.enums.UpdateOption;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ResourceCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;

/**
 * Manages a hierarchical structure of logical conditions for plugin scheduling.
 * <p>
 * The ConditionManager maintains two primary logical condition structures:
 * <ul>
 *   <li>Plugin conditions: Defined and managed by the plugin itself</li>
 *   <li>User conditions: Defined and managed by the user of the plugin</li>
 * </ul>
 * <p>
 * These structures can contain nested AND/OR logical conditions with various condition types
 * including time-based conditions, resource conditions, game event conditions, etc.
 * <p>
 * The manager processes RuneLite events and updates condition states accordingly,
 * allowing plugins to execute based on complex condition combinations.
 */
@Slf4j
public class ConditionManager implements AutoCloseable {
    
    /**
     * Shared thread pool for condition watchdog tasks across all ConditionManager instances.
     * Uses daemon threads to prevent blocking application shutdown.
     */
    private transient static final ScheduledExecutorService SHARED_WATCHDOG_EXECUTOR = 
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ConditionWatchdog");
            t.setDaemon(true);
            return t;
        });
    
    /**
     * Keeps track of all scheduled futures created by this manager's watchdog system.
     * Used to ensure all scheduled tasks are properly canceled when the manager is closed.
     */
    private transient final List<ScheduledFuture<?>> watchdogFutures = 
        new ArrayList<>();
    
    /**
     * Plugin-defined logical condition structure. Contains conditions defined by the plugin itself.
     * This is combined with user conditions using AND logic when evaluating the full condition set.
     */
    private LogicalCondition pluginCondition = new OrCondition();
    
    /**
     * User-defined logical condition structure. Contains conditions defined by the user.
     * This is combined with plugin conditions using AND logic when evaluating the full condition set.
     */
    @Getter
    private LogicalCondition userLogicalCondition;
    
    /**
     * Reference to the event bus for registering condition event listeners.
     */
    private final EventBus eventBus;
    
    /**
     * Tracks whether this manager has registered its event listeners with the event bus.
     */
    private boolean eventsRegistered = false;
    
    /**
     * Indicates whether condition watchdog tasks are currently active.
     */
    private boolean watchdogsRunning = false;
    
    /**
     * Stores the current update strategy for watchdog condition updates.
     * Controls how new conditions are merged with existing ones during watchdog checks.
     */
    private UpdateOption currentWatchdogUpdateOption = UpdateOption.SYNC;
    
    /**
     * The current interval in milliseconds between watchdog condition checks.
     */
    private long currentWatchdogInterval = 10000; // Default interval
    
    /**
     * The current supplier function that provides updated conditions for watchdog checks.
     */
    private Supplier<LogicalCondition> currentWatchdogSupplier = null;

    /**
     * Creates a new condition manager with default settings.
     * Initializes the user logical condition as an AND condition (all conditions must be met).
     */
    public ConditionManager() {
        this.eventBus = Microbot.getEventBus();
        userLogicalCondition = new AndCondition();
    }

    /**
     * Sets the plugin-defined logical condition structure.
     * 
     * @param condition The logical condition to set as the plugin structure
     */
    public void setPluginCondition(LogicalCondition condition) {
        pluginCondition = condition;
    }
    
    /**
     * Gets the plugin-defined logical condition structure.
     * 
     * @return The current plugin logical condition, or a default OrCondition if none was set
     */
    public LogicalCondition getPluginCondition() {
        return pluginCondition;
    }
    
    /**
     * Returns a combined list of all conditions from both plugin and user condition structures.
     * Plugin conditions are listed first, followed by user conditions.
     * 
     * @return A list containing all conditions managed by this ConditionManager
     */
    public List<Condition> getConditions() {
        List<Condition> conditions = new ArrayList<>();
        if (pluginCondition != null) {
            conditions.addAll(pluginCondition.getConditions());
        }
        conditions.addAll(userLogicalCondition.getConditions());
        return conditions;
    }
    /**
     * Checks if any conditions exist in the manager.
     * 
     * @return true if at least one condition exists in either plugin or user condition structures
     */
    public boolean hasConditions() {
        return !getConditions().isEmpty();
    }
    /**
     * Retrieves all time-based conditions from both plugin and user condition structures.
     * Uses the LogicalCondition.findTimeConditions method to recursively find all TimeCondition
     * instances throughout the nested logical structure.
     * 
     * @return A list of all TimeCondition instances managed by this ConditionManager
     */
    public List<TimeCondition> getTimeConditions() {
        List<TimeCondition> timeConditions = new ArrayList<>();
        
        //log.info("Searching for TimeConditions in logical structures");
        
        // Get time conditions from user logical structure
        if (userLogicalCondition != null) {
            List<Condition> userTimeConditions = userLogicalCondition.findTimeConditions();
            //log.info("Found {} time conditions in user logical structure", userTimeConditions.size());
            
            for (Condition condition : userTimeConditions) {
                if (condition instanceof TimeCondition) {
                    TimeCondition timeCondition = (TimeCondition) condition;
          //          log.info("Found TimeCondition in user structure: {} (implementation: {})", 
                     //       timeCondition, timeCondition.getClass().getSimpleName());
                    timeConditions.add(timeCondition);
                }
            }
        }
        
        // Get time conditions from plugin logical structure
        if (pluginCondition != null) {
            List<Condition> pluginTimeConditions = pluginCondition.findTimeConditions();
            //log.info("Found {} time conditions in plugin logical structure", pluginTimeConditions.size());
            
            for (Condition condition : pluginTimeConditions) {
                if (condition instanceof TimeCondition) {
                    TimeCondition timeCondition = (TimeCondition) condition;
                    //log.info("Found TimeCondition in plugin structure: {} (implementation: {})", 
                            //timeCondition, timeCondition.getClass().getSimpleName());
                    timeConditions.add(timeCondition);
                }
            }
        }
        
        //log.info("Total TimeConditions found in all logical structures: {}", timeConditions.size());
        return timeConditions;
    }

    /**
     * Retrieves all non-time-based conditions from both plugin and user condition structures.
     * Uses the LogicalCondition.findNonTimeConditions method to recursively find all non-TimeCondition
     * instances throughout the nested logical structure.
     * 
     * @return A list of all non-TimeCondition instances managed by this ConditionManager
     */
    public List<Condition> getNonTimeConditions() {
        List<Condition> nonTimeConditions = new ArrayList<>();
        
        // Get non-time conditions from user logical structure
        if (userLogicalCondition != null) {
            List<Condition> userNonTimeConditions = userLogicalCondition.findNonTimeConditions();
            nonTimeConditions.addAll(userNonTimeConditions);
        }
        
        // Get non-time conditions from plugin logical structure
        if (pluginCondition != null) {
            List<Condition> pluginNonTimeConditions = pluginCondition.findNonTimeConditions();
            nonTimeConditions.addAll(pluginNonTimeConditions);
        }
        
        return nonTimeConditions;
    }
    
    /**
     * Checks if the condition manager contains only time-based conditions.
     * 
     * @return true if all conditions in the manager are TimeConditions, false otherwise
     */
    public boolean hasOnlyTimeConditions() {
        return getNonTimeConditions().isEmpty();
    }
    /**
     * Returns the user logical condition structure.
     * 
     * @return The logical condition structure containing user-defined conditions
     */
    public LogicalCondition getUserCondition() {
        return userLogicalCondition;
    }
    /**
     * Removes all user-defined conditions while preserving the logical structure.
     * This clears the user condition list without changing the logical operator (AND/OR).
     */
    public void clearUserConditions() {
        userLogicalCondition.getConditions().clear();
    }
    /**
     * Evaluates if all conditions are currently satisfied, respecting the logical structure.
     * <p>
     * This method first checks if user conditions are met according to their logical operator (AND/OR).
     * If plugin conditions exist, they must also be satisfied (always using AND logic between
     * user and plugin conditions).
     * 
     * @return true if all required conditions are satisfied based on the logical structure
     */
    public boolean areConditionsMet() {
        boolean userConditionsMet = false;
        
        userConditionsMet = userLogicalCondition.isSatisfied();                            
        log.debug("User conditions met: {} (using {} logic)", 
            userConditionsMet, 
            (userLogicalCondition instanceof AndCondition) ? "AND" : "OR");
        if (pluginCondition != null && !pluginCondition.getConditions().isEmpty()) {          
            boolean pluginConditionsMet = pluginCondition.isSatisfied();
            log.debug("Plugin conditions met: {}", pluginConditionsMet);
            return userConditionsMet && pluginConditionsMet;            
        }
        if (userLogicalCondition.getConditions().isEmpty()  && pluginCondition.getConditions().isEmpty() ){
            return false;
        }
        return userConditionsMet;                                                
    }
   /**
     * Returns a list of conditions that were defined by the user (not plugin-defined).
     * This method only retrieves conditions from the user logical condition structure,
     * not from the plugin condition structure.
     * 
     * @return List of user-defined conditions, or an empty list if no user logical condition exists
     */
    public List<Condition> getUserConditions() {
        if (userLogicalCondition == null) {
            return new ArrayList<>();
        }
                
        return userLogicalCondition.getConditions();
    }
    /**
     * Registers this condition manager to receive RuneLite events.
     * Event listeners are registered with the event bus to allow conditions
     * to update their state based on game events. This method is idempotent
     * and will not register the same listeners twice.
     */
    public void registerEvents() {
        if (eventsRegistered) {
            return;
        }                
        eventBus.register(this);
        eventsRegistered = true;
    }
    
    /**
     * Unregisters this condition manager from receiving RuneLite events.
     * This removes all event listeners from the event bus that were previously registered.
     * This method is idempotent and will do nothing if events are not currently registered.
     */
    public void unregisterEvents() {
        if (!eventsRegistered) {
            return;
        }        
        eventBus.unregister(this);
        eventsRegistered = false;
    }
    
   
    /**
     * Sets the user logical condition to require ALL conditions to be met (AND logic).
     * This creates a new AndCondition to replace the existing user logical condition.
     */
    public void setRequireAll() {
        userLogicalCondition = new AndCondition();
        setUserLogicalCondition(userLogicalCondition);
        
    }
    
    /**
     * Sets the user logical condition to require ANY condition to be met (OR logic).
     * This creates a new OrCondition to replace the existing user logical condition.
     */
    public void setRequireAny() {
        userLogicalCondition = new OrCondition();        
        setUserLogicalCondition(userLogicalCondition);
    }
    
   
    
    /**
     * Generates a human-readable description of the current condition structure.
     * The description includes the logical operator type (ANY/ALL) and descriptions
     * of all user conditions. If plugin conditions exist, those are appended as well.
     * 
     * @return A string representation of the condition structure
     */
    public String getDescription() {
        
                  
        StringBuilder sb;
        if (requiresAny()){
            sb = new StringBuilder("ANY of: (");
        }else{
            sb = new StringBuilder("ALL of: (");
        }
        List<Condition> userConditions = userLogicalCondition.getConditions();
        if (userConditions.isEmpty()) {
            sb.append("No conditions");
        }else{
            for (int i = 0; i < userConditions.size(); i++) {
                if (i > 0) sb.append(" OR ");
                sb.append(userConditions.get(i).getDescription());
            }
        }
        sb.append(")");

        if ( this.pluginCondition!= null) {
            sb.append(" AND : ");
            sb.append(this.pluginCondition.getDescription());            
        }
        return sb.toString();      
        
    }
    
   /**
     * Checks if the user logical condition requires all conditions to be met (AND logic).
     * 
     * @return true if the user logical condition is an AndCondition, false otherwise
     */
    public boolean userConditionRequiresAll() {

        return userLogicalCondition instanceof AndCondition;
    }
    /**
     * Checks if the user logical condition requires any condition to be met (OR logic).
     * 
     * @return true if the user logical condition is an OrCondition, false otherwise
     */
    public boolean userConditionRequiresAny() {
        return userLogicalCondition instanceof OrCondition;
    }
    /**
     * Checks if the full logical structure (combining user and plugin conditions)
     * requires all conditions to be met (AND logic).
     * 
     * @return true if the full logical condition is an AndCondition, false otherwise
     */
    public boolean requiresAll() {
        return this.getFullLogicalCondition() instanceof AndCondition;
    }
    /**
     * Checks if the full logical structure (combining user and plugin conditions)
     * requires any condition to be met (OR logic).
     * 
     * @return true if the full logical condition is an OrCondition, false otherwise
     */
    public boolean requiresAny() {
        return this.getFullLogicalCondition() instanceof OrCondition;
    }
    /**
     * Resets all conditions in both user and plugin logical structures to their initial state.
     * This method calls the reset() method on all conditions.
     */
    public void reset() {
        userLogicalCondition.reset();
        if (pluginCondition != null) {
            pluginCondition.reset();
        }
        
    }
    /**
     * Resets all conditions in both user and plugin logical structures with an option to randomize.
     * 
     * @param randomize If true, conditions will be reset with randomized initial values where applicable
     */
    public void reset(boolean randomize) {
        userLogicalCondition.reset(randomize);
        if (pluginCondition != null) {
            pluginCondition.reset(randomize);
        }
    }

    /**
     * Checks if a condition is a plugin-defined condition that shouldn't be edited by users.
     */
    public boolean isPluginDefinedCondition(Condition condition) {
        // If there are no plugin-defined conditions, return false
        if (pluginCondition == null) {
            return false;
        }
        if (condition instanceof LogicalCondition) {
            // If the condition is a logical condition, check if it's part of the plugin condition
            if (pluginCondition.equals(condition)) {
                return true;
            }            
        }
        // Checfk if the condition is contained in the plugin condition hierarchy
        return pluginCondition.contains(condition);
    }

    

    /**
     * Recursively searches for and removes a condition from nested logical conditions.
     * 
     * @param parent The logical condition to search within
     * @param target The condition to remove
     * @return true if the condition was found and removed, false otherwise
     */
    private boolean removeFromNestedCondition(LogicalCondition parent, Condition target) {
        // Search each child of the parent logical condition
        for (int i = 0; i < parent.getConditions().size(); i++) {
            Condition child = parent.getConditions().get(i);
            
            // If this child is itself a logical condition, search within it
            if (child instanceof LogicalCondition) {
                LogicalCondition logicalChild = (LogicalCondition) child;
                
                // First check if the target is a direct child of this logical condition
                if (logicalChild.getConditions().remove(target)) {
                    // If removing the condition leaves the logical condition empty, remove it too
                    if (logicalChild.getConditions().isEmpty()) {
                        parent.getConditions().remove(i);
                    }
                    return true;
                }
                
                // If not a direct child, recurse into the logical child
                if (removeFromNestedCondition(logicalChild, target)) {
                    // If removing the condition leaves the logical condition empty, remove it too
                    parent.getConditions().remove(i);
                    return true;
                }
            }
            // Special case for NotCondition
            else if (child instanceof NotCondition) {
                NotCondition notChild = (NotCondition) child;
                
                // If the NOT condition wraps our target, remove the whole NOT condition
                if (notChild.getCondition() == target) {
                    parent.getConditions().remove(i);
                    return true;
                }
                
                // If the NOT condition wraps a logical condition, search within that
                if (notChild.getCondition() instanceof LogicalCondition) {
                    LogicalCondition wrappedLogical = (LogicalCondition) notChild.getCondition();
                    if (removeFromNestedCondition(wrappedLogical, target)) {
                        // If removing the condition leaves the logical condition empty, remove the NOT condition too
                        parent.getConditions().remove(i);
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Sets the user's logical condition structure
     * 
     * @param logicalCondition The logical condition to set as the user structure
     */
    public void setUserLogicalCondition(LogicalCondition logicalCondition) {
        this.userLogicalCondition = logicalCondition;
    }

    /**
     * Gets the user's logical condition structure
     * 
     * @return The current user logical condition, or null if none exists
     */
    public LogicalCondition getUserLogicalCondition() {
        return this.userLogicalCondition;
    }

    
    public boolean addToLogicalStructure(LogicalCondition parent, Condition toAdd) {
        // Try direct addition first
        if (parent.getConditions().add(toAdd)) {
            return true;
        }
        
        // Try to add to child logical conditions
        for (Condition child : parent.getConditions()) {
            if (child instanceof LogicalCondition) {
                if (addToLogicalStructure((LogicalCondition) child, toAdd)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Recursively removes a condition from a logical structure
     */
    public boolean removeFromLogicalStructure(LogicalCondition parent, Condition toRemove) {
        // Try direct removal first
        if (parent.getConditions().remove(toRemove)) {
            return true;
        }
        
        // Try to remove from child logical conditions
        for (Condition child : parent.getConditions()) {
            if (child instanceof LogicalCondition) {
                if (removeFromLogicalStructure((LogicalCondition) child, toRemove)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Makes sure there's a valid user logical condition to work with
     */
    private void ensureUserLogicalExists() {
        if (userLogicalCondition == null) {
            userLogicalCondition = new AndCondition();
        }
    }

    /**
     * Checks if the condition exists in either user or plugin logical structures
     */
    public boolean containsCondition(Condition condition) {
        ensureUserLogicalExists();
        
        // Check user conditions
        if (userLogicalCondition.contains(condition)) {
            return true;
        }
        
        // Check plugin conditions
        return pluginCondition != null && pluginCondition.contains(condition);
    }

   

    /**
     * Adds a condition to the specified logical condition, or to the user root if none specified
     */
    public void addConditionToLogical(Condition condition, LogicalCondition targetLogical) {
        ensureUserLogicalExists();
        // find if the user logical condition contains the target logical condition
        if (  targetLogical != userLogicalCondition && (targetLogical != null && !userLogicalCondition.contains(targetLogical))) {
            log.warn("Target logical condition not found in user logical structure");
            return;
        }
        // check if condition already exists in logical structure
        if (targetLogical != null && targetLogical.contains(condition)) {
            log.warn("Condition already exists in logical structure");
            return;
        }
        // If no target specified, add to user root
        if (targetLogical == null) {
            userLogicalCondition.addCondition(condition);
            return;
        }
        
        // Otherwise, add to the specified logical
        targetLogical.addCondition(condition);
    }

    /**
     * Adds a condition to the user logical root
     */
    public void addUserCondition(Condition condition) {
        addConditionToLogical(condition, userLogicalCondition);
    }

    /**
     * Removes a condition from any location in the logical structure
     */
    public boolean removeCondition(Condition condition) {
        ensureUserLogicalExists();
        
        // Don't allow removing plugin conditions
        if (isPluginDefinedCondition(condition)) {
            log.warn("Attempted to remove a plugin-defined condition");
            return false;
        }
        if (condition instanceof LogicalCondition) {
            // If the condition is a logical condition, check if it's part of the user logical structure
            if (userLogicalCondition.equals(condition)) {
                log.warn("Attempted to remove the user logical condition itself");
                userLogicalCondition =  new AndCondition(); 
            }
        }
        // Remove from user logical structure
        if (userLogicalCondition.removeCondition(condition)) {
            return true;
        }
        
        log.warn("Condition not found in any logical structure");
        return false;
    }

    

    /**
     * Gets the root logical condition that should be used for the current UI operation
     */
    public LogicalCondition getFullLogicalCondition() {
        // First check if there are plugin conditions
        if (pluginCondition != null && !pluginCondition.getConditions().isEmpty()) {
            // Need to combine user and plugin conditions with AND logic
            AndCondition combinedRoot = new AndCondition();
            
            // Add user logical if it has conditions
            if (userLogicalCondition != null && !userLogicalCondition.getConditions().isEmpty()) {
                combinedRoot.addCondition(userLogicalCondition);
                // Add plugin logical
                combinedRoot.addCondition(pluginCondition);
                return combinedRoot;
            }else {
                // If no user conditions, just return plugin condition
                return pluginCondition;
            }                                                
        }
        
        // If no plugin conditions, just return user logical
        return userLogicalCondition;
    }
    public LogicalCondition getFullLogicalUserCondition() {
        return userLogicalCondition;
    }
    public LogicalCondition getFullLogicalPluginCondition() {
        return pluginCondition;
    }

    /**
     * Checks if a condition is a SingleTriggerTimeCondition
     * 
     * @param condition The condition to check
     * @return true if the condition is a SingleTriggerTimeCondition
     */
    private boolean isSingleTriggerCondition(Condition condition) {
        return condition instanceof SingleTriggerTimeCondition;
    }
    public List<SingleTriggerTimeCondition> getTriggeredOneTimeConditions(){
        List<SingleTriggerTimeCondition> result = new ArrayList<>();
        for (Condition condition : userLogicalCondition.getConditions()) {
            if (isSingleTriggerCondition(condition)) {
                SingleTriggerTimeCondition singleTrigger = (SingleTriggerTimeCondition) condition;
                if (singleTrigger.canTriggerAgain()) {
                    result.add(singleTrigger);
                }
            }
        }
        if (pluginCondition != null) {
            for (Condition condition : pluginCondition.getConditions()) {
                if (isSingleTriggerCondition(condition)) {
                    SingleTriggerTimeCondition singleTrigger = (SingleTriggerTimeCondition) condition;
                    if (singleTrigger.canTriggerAgain()) {
                        result.add(singleTrigger);
                    }
                }
            }
        }
        return result;
    }
    /**
     * Checks if this condition manager contains any SingleTriggerTimeCondition that
     * can no longer trigger (has already triggered)
     * 
     * @return true if at least one single-trigger condition has already triggered
     */
    public boolean hasTriggeredOneTimeConditions() {
        // Check user conditions first
        for (Condition condition : getUserLogicalCondition().getConditions()) {
            if (isSingleTriggerCondition(condition)) {
                SingleTriggerTimeCondition singleTrigger = (SingleTriggerTimeCondition) condition;
                if (!singleTrigger.canTriggerAgain()) {
                    return true;
                }
            }
        }
        
        // Then check plugin conditions if present
        if (pluginCondition != null) {
            for (Condition condition : pluginCondition.getConditions()) {
                if (isSingleTriggerCondition(condition)) {
                    SingleTriggerTimeCondition singleTrigger = (SingleTriggerTimeCondition) condition;
                    if (!singleTrigger.canTriggerAgain()) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

     /**
     * Checks if the logical structure cannot trigger again due to triggered one-time conditions.
     * Considers the nested AND/OR condition structure to determine if future triggering is possible.
     * 
     * @return true if the structure cannot trigger again due to one-time conditions
     */
    public boolean cannotTriggerDueToOneTimeConditions() {
        // If there are no one-time conditions, the structure can always trigger again
        if (!hasAnyOneTimeConditions()) {
            return false;
        }

        // Start evaluation at the root of the condition tree
        return !canLogicalStructureTriggerAgain(getFullLogicalCondition());
    }

    /**
     * Recursively evaluates if a logical structure can trigger again based on one-time conditions.
     * 
     * @param logical The logical condition to evaluate
     * @return true if the logical structure can trigger again, false otherwise
     */
    private boolean canLogicalStructureTriggerAgain(LogicalCondition logical) {
        if (logical instanceof AndCondition) {
            // For AND logic, if any direct child one-time condition has triggered,
            // the entire AND branch cannot trigger again
            for (Condition condition : logical.getConditions()) {
                if (condition instanceof TimeCondition) {
                    TimeCondition timeCondition = (TimeCondition) condition;
                    if (timeCondition.canTriggerAgain()) {                        
                        return false;
                    }
                }             
                else if (condition instanceof ResourceCondition) {
                    ResourceCondition resourceCondition = (ResourceCondition) condition;
                    
                }
                else if (condition instanceof LogicalCondition) {
                    // Recursively check nested logic
                    if (!canLogicalStructureTriggerAgain((LogicalCondition) condition)) {
                        // If a nested branch can't trigger, this AND branch can't trigger
                        return false;
                    }
                }
            }
            // If we get here, all branches can still trigger
            return true;
        } else if (logical instanceof OrCondition) {
            // For OR logic, if any one-time condition hasn't triggered yet,
            // the OR branch can still trigger
            boolean anyCanTrigger = false;
            
            for (Condition child : logical.getConditions()) {
                if (child instanceof TimeCondition) {
                    TimeCondition singleTrigger = (TimeCondition) child;
                    if (!singleTrigger.hasTriggered()) {
                        // Found an untriggered one-time condition, so this branch can trigger
                        return true;
                    }
                } else if (child instanceof LogicalCondition) {
                    // Recursively check nested logic
                    if (canLogicalStructureTriggerAgain((LogicalCondition) child)) {
                        // If a nested branch can trigger, this OR branch can trigger
                        return true;
                    }
                } else {
                    // Regular non-one-time conditions can always trigger
                    anyCanTrigger = true;
                }
            }
            
            // If there are no one-time conditions in this OR, it can trigger if it has any conditions
            return anyCanTrigger;
        } else {
            // For any other logical condition type (e.g., NOT), assume it can trigger
            return true;
        }
    }
    /**
     * Validates if the current condition structure can be triggered again
     * based on the status of one-time conditions in the logical structure.
     * 
     * @return true if the condition structure can be triggered again
     */
    public boolean canTriggerAgain() {
        return !cannotTriggerDueToOneTimeConditions();
    }

    /**
     * Checks if this condition manager contains any SingleTriggerTimeConditions
     * 
     * @return true if at least one single-trigger condition exists
     */
    public boolean hasAnyOneTimeConditions() {
        // Check user conditions
        for (Condition condition : getUserLogicalCondition().getConditions()) {
            if (isSingleTriggerCondition(condition)) {
                return true;
            }
        }
        
        // Check plugin conditions if present
        if (pluginCondition != null) {
            for (Condition condition : pluginCondition.getConditions()) {
                if (isSingleTriggerCondition(condition)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    /**
     * Calculates overall progress percentage across all conditions.
     * This respects the logical structure of conditions.
     * Returns 0 if progress cannot be determined.
     */
    private double getFullRootConditionProgress() {
        // If there are no conditions, no progress to report -> nothing can be satisfied
        if ( getConditions().isEmpty()) {
            return 0.0;
        }
        
        // If using logical root condition, respect its logical structure
        LogicalCondition rootLogical = getFullLogicalCondition();
        if (rootLogical != null) {
            return rootLogical.getProgressPercentage();
        }
        
        // Fallback for direct condition list: calculate based on AND/OR logic
        boolean requireAll = requiresAll();
        List<Condition> conditions = getConditions();
        
        if (requireAll) {
            // For AND logic, use the minimum progress (weakest link)
            return conditions.stream()
                .mapToDouble(Condition::getProgressPercentage)
                .min()
                .orElse(0.0);
        } else {
            // For OR logic, use the maximum progress (strongest link)
            return conditions.stream()
                .mapToDouble(Condition::getProgressPercentage)
                .max()
                .orElse(0.0);
        }
    }
    /**
     * Gets the overall condition progress
     * Integrates both standard progress and single-trigger time conditions
     */
    public double getFullConditionProgress() {
        // First check for regular condition progress
        double stopProgress = getFullRootConditionProgress();
        
        // Then check if we have any single-trigger conditions
        boolean hasOneTime = hasAnyOneTimeConditions();
        if (hasOneTime) {
            // If all one-time conditions have triggered, return 100%
            if (canTriggerAgain()) {
                return 100.0;
            }
            
            // If no standard progress but we have one-time conditions that haven't
            // all triggered, return progress based on closest one-time condition
            if (stopProgress == 0.0) {
                return calculateClosestOneTimeProgress();
            }
        }
        
        // Return the standard progress
        return stopProgress;
    }

    /**
     * Calculates progress based on the closest one-time condition to triggering
     */
    private double calculateClosestOneTimeProgress() {
        // Find the single-trigger condition that's closest to triggering
        double maxProgress = 0.0;
        
        for (Condition condition : getConditions()) {
            if (condition instanceof SingleTriggerTimeCondition) {
                SingleTriggerTimeCondition singleTrigger = (SingleTriggerTimeCondition) condition;
                if (!singleTrigger.hasTriggered()) {
                    double progress = singleTrigger.getProgressPercentage();
                    maxProgress = Math.max(maxProgress, progress);
                }
            }
        }
        
        return maxProgress;
    }
    /**
     * Gets the next time any condition in the structure will trigger.
     * This recursively examines the logical condition tree and finds the earliest trigger time.
     * If conditions are already satisfied, returns the most recent time in the past.
     * 
     * @return Optional containing the earliest next trigger time, or empty if none available
     */
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        // Check if conditions are already met
        boolean conditionsMet = areConditionsMet();
        if (conditionsMet) {
            log.debug("Conditions already met, searching for most recent trigger time in the past");
            
            // Find the most recent trigger time in the past from all satisfied conditions
            ZonedDateTime mostRecentTriggerTime = null;
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            
            // Recursively scan all conditions that are satisfied
            for (Condition condition : getConditions()) {
                if (condition.isSatisfied()) {
                    Optional<ZonedDateTime> conditionTrigger = condition.getCurrentTriggerTime();
                    if (conditionTrigger.isPresent()) {
                        ZonedDateTime triggerTime = conditionTrigger.get();
                        
                        // Only consider times in the past
                        if (triggerTime.isBefore(now) || triggerTime.isEqual(now)) {
                            // Keep the most recent time in the past
                            if (mostRecentTriggerTime == null || triggerTime.isAfter(mostRecentTriggerTime)) {
                                mostRecentTriggerTime = triggerTime;
                                log.debug("Found more recent past trigger time: {}", mostRecentTriggerTime);
                            }
                        }
                    }
                }
            }
            
            // If we found a trigger time from satisfied conditions, return it
            if (mostRecentTriggerTime != null) {
                log.debug("Selected most recent past trigger time: {}", mostRecentTriggerTime);
                return Optional.of(mostRecentTriggerTime);
            }
            
            // If no trigger times found from satisfied conditions, default to immediate past
            ZonedDateTime immediateTime = now.minusSeconds(1);
            log.debug("No past trigger times found from satisfied conditions, returning immediate past time: {}", immediateTime);
            return Optional.of(immediateTime);
        }
        
        // Otherwise proceed with normal logic for finding next trigger time
        log.debug("Conditions not yet met, searching for next trigger time in logical structure");
        Optional<ZonedDateTime> nextTime = getCurrentTriggerTimeForLogical(getFullLogicalCondition());
        
        if (nextTime.isPresent()) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            ZonedDateTime triggerTime = nextTime.get();
            
            if (triggerTime.isBefore(now)) {
                log.debug("Found trigger time {} is in the past compared to now {}", 
                    triggerTime, now);
                
                // If trigger time is in the past but conditions aren't met,
                // this might indicate a condition that needs resetting
                if (!conditionsMet) {
                    log.debug("Trigger time in past but conditions not met - may need reset");
                }
            } else {
                log.debug("Found future trigger time: {}", triggerTime);
            }
        } else {
            log.debug("No trigger time found in condition structure");
        }
        
        return nextTime;
    }

    /**
     * Recursively finds the appropriate trigger time within a logical condition.
     * - For conditions not yet met: finds the earliest future trigger time
     * - For conditions already met: finds the most recent past trigger time
     * 
     * @param logical The logical condition to examine
     * @return Optional containing the appropriate trigger time, or empty if none available
     */
    private Optional<ZonedDateTime> getCurrentTriggerTimeForLogical(LogicalCondition logical) {
        if (logical == null || logical.getConditions().isEmpty()) {
            log.debug("Logical condition is null or empty, no trigger time available");
            return Optional.empty();
        }
        
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        
        // If the logical condition is already satisfied, find most recent past trigger time
        if (logical.isSatisfied()) {
            ZonedDateTime mostRecentTriggerTime = null;
            
            for (Condition condition : logical.getConditions()) {
                if (condition.isSatisfied()) {
                    log.debug("Checking past trigger time for satisfied condition: {}", condition.getDescription());
                    Optional<ZonedDateTime> triggerTime;
                    
                    if (condition instanceof LogicalCondition) {
                        // Recursively check nested logical conditions
                        triggerTime = getCurrentTriggerTimeForLogical((LogicalCondition) condition);
                    } else {
                        // Get trigger time from individual condition
                        triggerTime = condition.getCurrentTriggerTime();
                    }
                    
                    if (triggerTime.isPresent()) {
                        ZonedDateTime time = triggerTime.get();
                        // Only consider times in the past
                        if (time.isBefore(now) || time.isEqual(now)) {
                            // Keep the most recent time in the past
                            if (mostRecentTriggerTime == null || time.isAfter(mostRecentTriggerTime)) {
                                mostRecentTriggerTime = time;
                                log.debug("Found more recent past trigger time: {}", mostRecentTriggerTime);
                            }
                        }
                    }
                }
            }
            
            if (mostRecentTriggerTime != null) {
                return Optional.of(mostRecentTriggerTime);
            }
        }
        
        // If not satisfied, find earliest future trigger time (original behavior)
        ZonedDateTime earliestTrigger = null;
        
        for (Condition condition : logical.getConditions()) {
            log.debug("Checking next trigger time for condition: {}", condition.getDescription());
            Optional<ZonedDateTime> nextTrigger;
            
            if (condition instanceof LogicalCondition) {
                // Recursively check nested logical conditions
                log.debug("Recursing into nested logical condition");
                nextTrigger = getCurrentTriggerTimeForLogical((LogicalCondition) condition);
            } else {
                // Get trigger time from individual condition
                nextTrigger = condition.getCurrentTriggerTime();
                log.debug("Condition {} trigger time: {}", 
                    condition.getDescription(), 
                    nextTrigger.isPresent() ? nextTrigger.get() : "none");
            }
            
            // Update earliest trigger if this one is earlier
            if (nextTrigger.isPresent()) {
                ZonedDateTime triggerTime = nextTrigger.get();
                if (earliestTrigger == null || triggerTime.isBefore(earliestTrigger)) {
                    log.debug("Found earlier trigger time: {}", triggerTime);
                    earliestTrigger = triggerTime;
                }
            }
        }
        
        if (earliestTrigger != null) {
            log.debug("Earliest trigger time for logical condition: {}", earliestTrigger);
            return Optional.of(earliestTrigger);
        } else {
            log.debug("No trigger times found in logical condition");
            return Optional.empty();
        }
    }

    /**
     * Gets the next time any condition in the structure will trigger.
     * This recursively examines the logical condition tree and finds the earliest trigger time.
     * 
     * @return Optional containing the earliest next trigger time, or empty if none available
     */
    public Optional<ZonedDateTime> getCurrentTriggerTimeBasedOnUserConditions() {
        // Start at the root of the condition tree
        return getCurrentTriggerTimeForLogical(getFullLogicalUserCondition());
    }
    /**
     * Determines the next time a plugin should be triggered based on the plugin's set conditions.
     * This method evaluates the full logical condition tree for the plugin to calculate
     * when the next condition-based execution should occur.
     *
     * @return An Optional containing the ZonedDateTime of the next trigger time if one exists,
     *         or an empty Optional if no future trigger time can be determined
     */
    public Optional<ZonedDateTime> getCurrentTriggerTimeBasedOnPluginConditions() {
        // Start at the root of the condition tree
        return getCurrentTriggerTimeForLogical(getFullLogicalPluginCondition());
    }

    /**
     * Gets the duration until the next condition trigger.
     * For conditions already satisfied, returns Duration.ZERO.
     * 
     * @return Optional containing the duration until next trigger, or empty if none available
     */
    public Optional<Duration> getDurationUntilNextTrigger() {
        // If conditions are already met, return zero duration
        if (areConditionsMet()) {
            return Optional.of(Duration.ZERO);
        }
        
        Optional<ZonedDateTime> nextTrigger = getCurrentTriggerTime();
        if (nextTrigger.isPresent()) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            ZonedDateTime triggerTime = nextTrigger.get();
            
            // If trigger time is in the future, return the duration
            if (triggerTime.isAfter(now)) {
                return Optional.of(Duration.between(now, triggerTime));
            }
            
            // If trigger time is in the past but conditions aren't met,
            // this indicates a condition that needs resetting
            log.debug("Trigger time in past but conditions not met - returning zero duration");
            return Optional.of(Duration.ZERO);
        }
        return Optional.empty();
    }

    /**
     * Formats the next trigger time as a human-readable string.
     * 
     * @return A string representing when the next condition will trigger, or "No upcoming triggers" if none
     */
    public String getCurrentTriggerTimeString() {
        Optional<ZonedDateTime> nextTrigger = getCurrentTriggerTime();
        if (nextTrigger.isPresent()) {
            ZonedDateTime triggerTime = nextTrigger.get();
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            
            // Format nicely depending on how far in the future
            Duration timeUntil = Duration.between(now, triggerTime);
            long seconds = timeUntil.getSeconds();
            
            if (seconds < 0) {
                return "Already triggered";
            } else if (seconds < 60) {
                return String.format("Triggers in %d seconds", seconds);
            } else if (seconds < 3600) {
                return String.format("Triggers in %d minutes %d seconds", 
                        seconds / 60, seconds % 60);
            } else if (seconds < 86400) { // Less than a day
                return String.format("Triggers in %d hours %d minutes", 
                        seconds / 3600, (seconds % 3600) / 60);
            } else {
                // More than a day away, use date format
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d 'at' HH:mm");
                return "Triggers on " + triggerTime.format(formatter);
            }
        }
        
        return "No upcoming triggers";
    }

    /**
     * Gets the overall progress percentage toward the next trigger time.
     * For logical conditions, this respects the logical structure.
     * 
     * @return Progress percentage (0-100)
     */
    public double getProgressTowardNextTrigger() {
        LogicalCondition rootLogical = getFullLogicalCondition();
        
        if (rootLogical instanceof AndCondition) {
            // For AND logic, use the minimum progress (we're only as close as our furthest condition)
            return rootLogical.getConditions().stream()
                    .mapToDouble(Condition::getProgressPercentage)
                    .min()
                    .orElse(0.0);
        } else {
            // For OR logic, use the maximum progress (we're as close as our closest condition)
            return rootLogical.getConditions().stream()
                    .mapToDouble(Condition::getProgressPercentage)
                    .max()
                    .orElse(0.0);
        }
    }
    @Subscribe(priority = -1)
    public void onGameStateChanged(GameStateChanged gameStateChanged){
        for (Condition condition : getConditions( )) {
            try {
                condition.onGameStateChanged(gameStateChanged);
            } catch (Exception e) {
                log.error("Error in condition {} during GameStateChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
    }

@Subscribe(priority = -1)
public void onStatChanged(StatChanged event) {
             
        for (Condition condition : getConditions( )) {
            try {
                condition.onStatChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during StatChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
    }
   
    
    @Subscribe(priority = -1)
    public void onItemContainerChanged(ItemContainerChanged event) {
         // Propagate event to all conditions
         
         for (Condition condition : getConditions( )) {
            try {
                condition.onItemContainerChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during ItemContainerChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }       
    }
    @Subscribe(priority = -1)
    public void onGameTick(GameTick gameTick) {
        // Propagate event to all conditions
        
        for (Condition condition : getConditions( )) {
            try {
                condition.onGameTick(gameTick);
            } catch (Exception e) {
                log.error("Error in condition {} during GameTick event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }      
    }

    @Subscribe(priority = -1)
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        // Propagate event to all conditions
        for (Condition condition : getConditions( )) {
            try {
                condition.onGroundObjectSpawned(event);
            } catch (Exception e) {
                log.error("Error in condition {} during GroundItemSpawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onGroundObjectSpawned(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during GroundItemSpawned event: {}", 
                    e.getMessage(), e);
            }
        }
    }

    @Subscribe(priority = -1)
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        for (Condition condition : getConditions( )) {
            try {
                condition.onGroundObjectDespawned(event);
            } catch (Exception e) {
                log.error("Error in condition {} during GroundItemDespawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }       
    }

    @Subscribe(priority = -1)
    public void onMenuOptionClicked(MenuOptionClicked event) {
        for (Condition condition : getConditions( )) {
            try {
                condition.onMenuOptionClicked(event);
            } catch (Exception e) {
                log.error("Error in condition {} during MenuOptionClicked event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
    }

    @Subscribe(priority = -1)
    public void onChatMessage(ChatMessage event) {
        for (Condition condition : getConditions( )) {
            try {
                condition.onChatMessage(event);
            } catch (Exception e) {
                log.error("Error in condition {} during ChatMessage event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
    }

    @Subscribe(priority = -1)
    public void onHitsplatApplied(HitsplatApplied event) {
        for (Condition condition : getConditions( )) {
            try {
                condition.onHitsplatApplied(event);
            } catch (Exception e) {
                log.error("Error in condition {} during HitsplatApplied event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
       
    }
    @Subscribe(priority = -1)
	public void onVarbitChanged(VarbitChanged event)
	{
		for (Condition condition :getConditions( )) {
            try {
                condition.onVarbitChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during VarbitChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
	}
    @Subscribe(priority = -1)
    void onNpcChanged(NpcChanged event){
        for (Condition condition :getConditions( )) {
            try {
                condition.onNpcChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during NpcChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
    }
    @Subscribe(priority = -1)
    void onNpcSpawned(NpcSpawned npcSpawned){
        for (Condition condition : getConditions( )) {
            try {
                condition.onNpcSpawned(npcSpawned);
            } catch (Exception e) {
                log.error("Error in condition {} during NpcSpawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
        
    }
    @Subscribe(priority = -1)
    void onNpcDespawned(NpcDespawned npcDespawned){
        for (Condition condition : getConditions( )) {
            try {
                condition.onNpcDespawned(npcDespawned);
            } catch (Exception e) {
                log.error("Error in condition {} during NpcDespawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }        
    }
    @Subscribe(priority = -1)
    void onInteractingChanged(InteractingChanged event){
        for (Condition condition : getConditions( )) {
            try {
                condition.onInteractingChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during InteractingChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }       
    }
    @Subscribe(priority = -1)
    void onItemSpawned(ItemSpawned event){        
        List<Condition> allConditions = getConditions();            
        // Proceed with normal processing
        for (Condition condition : allConditions) {            
            try {                
                condition.onItemSpawned(event);
            } catch (Exception e) {
                log.error("Error in condition {} during ItemSpawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }       
    }
    @Subscribe(priority = -1)
    void onItemDespawned(ItemDespawned event){
        for (Condition condition :getConditions( )) {
            try {
                condition.onItemDespawned(event);
            } catch (Exception e) {
                log.error("Error in condition {} during ItemDespawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }       
    }
    @Subscribe(priority = -1)
    void onAnimationChanged(AnimationChanged event) {
        for (Condition condition :getConditions( )) {
            try {
                condition.onAnimationChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during AnimationChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }       
    }

    /**
     * Finds the logical condition that contains the given condition
     * 
     * @param targetCondition The condition to find
     * @return The logical condition containing it, or null if not found
     */
    public LogicalCondition findContainingLogical(Condition targetCondition) {
        // First check if it's in the plugin condition
        if (pluginCondition != null && findInLogical(pluginCondition, targetCondition) != null) {
            return findInLogical(pluginCondition, targetCondition);
        }
        
        // Then check user logical condition
        if (userLogicalCondition != null) {
            LogicalCondition result = findInLogical(userLogicalCondition, targetCondition);
            if (result != null) {
                return result;
            }
        }
        
        // Try root logical condition as a last resort
        return userLogicalCondition;
    }
    
    /**
     * Recursively searches for a condition within a logical condition
     */
    private LogicalCondition findInLogical(LogicalCondition logical, Condition targetCondition) {
        // Check if the condition is directly in this logical
        if (logical.getConditions().contains(targetCondition)) {
            return logical;
        }
        
        // Check nested logical conditions
        for (Condition condition : logical.getConditions()) {
            if (condition instanceof LogicalCondition) {
                LogicalCondition result = findInLogical((LogicalCondition) condition, targetCondition);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }

    /**
     * Creates a new ConditionManager that contains only the time conditions from this manager,
     * preserving the logical structure hierarchy (AND/OR relationships).
     * 
     * @return A new ConditionManager with only time conditions, or null if no time conditions exist
     */
    public ConditionManager createTimeOnlyConditionManager() {
        // Create a new condition manager
        ConditionManager timeOnlyManager = new ConditionManager();
        
        // Process user logical condition
        if (userLogicalCondition != null) {
            LogicalCondition timeOnlyUserLogical = userLogicalCondition.createTimeOnlyLogicalStructure();
            if (timeOnlyUserLogical != null) {
                timeOnlyManager.setUserLogicalCondition(timeOnlyUserLogical);
            }
        }
        
        // Process plugin logical condition
        if (pluginCondition != null) {
            LogicalCondition timeOnlyPluginLogical = pluginCondition.createTimeOnlyLogicalStructure();
            if (timeOnlyPluginLogical != null) {
                timeOnlyManager.setPluginCondition(timeOnlyPluginLogical);
            }
        }
        
        return timeOnlyManager;
    }
    
    /**
     * Evaluates whether this condition manager would be satisfied if only time conditions
     * were considered. This is useful to determine if a plugin schedule is blocked by 
     * non-time conditions or by the time conditions themselves.
     * 
     * @return true if time conditions alone would satisfy this condition manager, false otherwise
     */
    public boolean wouldBeTimeOnlySatisfied() {
        // If there are no time conditions at all, we can't satisfy with time only
        List<TimeCondition> timeConditions = getTimeConditions();
        if (timeConditions.isEmpty()) {
            return false;
        }
        
        boolean userConditionsSatisfied = false;
        boolean pluginConditionsSatisfied = false;
        
        // Check user logical condition
        if (userLogicalCondition != null) {
            userConditionsSatisfied = userLogicalCondition.wouldBeTimeOnlySatisfied();
        }
        
        // Check plugin logical condition
        if (pluginCondition != null && !pluginCondition.getConditions().isEmpty()) {
            pluginConditionsSatisfied = pluginCondition.wouldBeTimeOnlySatisfied();
            
            // Both user and plugin conditions must be satisfied (AND logic between them)
            return userConditionsSatisfied && pluginConditionsSatisfied;
        }
        
        // If no plugin conditions, just return user conditions result
        return userConditionsSatisfied;
    }
    
    /**
     * Evaluates if only the time conditions in both user and plugin logical structures would be met.
     * This method provides more detailed diagnostics than wouldBeTimeOnlySatisfied().
     * 
     * @return A string containing diagnostic information about time condition satisfaction
     */
    public String diagnoseTimeConditionsSatisfaction() {
        StringBuilder sb = new StringBuilder("Time conditions diagnosis:\n");
        
        // Get all time conditions
        List<TimeCondition> timeConditions = getTimeConditions();
        
        // Check if there are any time conditions
        if (timeConditions.isEmpty()) {
            sb.append("No time conditions defined - cannot be satisfied based on time only.\n");
            return sb.toString();
        }
        
        // Check user logical time conditions
        if (userLogicalCondition != null) {
            boolean userTimeOnlySatisfied = userLogicalCondition.wouldBeTimeOnlySatisfied();
            sb.append("User time conditions: ").append(userTimeOnlySatisfied ? "SATISFIED" : "NOT SATISFIED").append("\n");
            
            // List each time condition in user logical
            List<Condition> userTimeConditions = userLogicalCondition.findTimeConditions();
            if (!userTimeConditions.isEmpty()) {
                sb.append("  User time conditions (").append(userLogicalCondition instanceof AndCondition ? "ALL" : "ANY").append(" required):\n");
                for (Condition condition : userTimeConditions) {
                    boolean satisfied = condition.isSatisfied();
                    sb.append("    - ").append(condition.getDescription())
                      .append(": ").append(satisfied ? "SATISFIED" : "NOT SATISFIED")
                      .append("\n");
                }
            }
        }
        
        // Check plugin logical time conditions
        if (pluginCondition != null && !pluginCondition.getConditions().isEmpty()) {
            boolean pluginTimeOnlySatisfied = pluginCondition.wouldBeTimeOnlySatisfied();
            sb.append("Plugin time conditions: ").append(pluginTimeOnlySatisfied ? "SATISFIED" : "NOT SATISFIED").append("\n");
            
            // List each time condition in plugin logical
            List<Condition> pluginTimeConditions = pluginCondition.findTimeConditions();
            if (!pluginTimeConditions.isEmpty()) {
                sb.append("  Plugin time conditions (").append(pluginCondition instanceof AndCondition ? "ALL" : "ANY").append(" required):\n");
                for (Condition condition : pluginTimeConditions) {
                    boolean satisfied = condition.isSatisfied();
                    sb.append("    - ").append(condition.getDescription())
                      .append(": ").append(satisfied ? "SATISFIED" : "NOT SATISFIED")
                      .append("\n");
                }
            }
        }
        
        // Overall result
        boolean overallTimeOnlySatisfied = wouldBeTimeOnlySatisfied();
        sb.append("Overall result: ").append(overallTimeOnlySatisfied ? 
                "Would be SATISFIED based on time conditions only" : 
                "Would NOT be satisfied based on time conditions only");
        
        return sb.toString();
    }
    
  
    
   
 
    /**
     * Updates the plugin condition structure with new conditions from the given logical condition.
     * This method intelligently merges the new conditions into the existing structure
     * rather than replacing it completely, which preserves state and reduces unnecessary
     * condition reinitializations.
     * 
     * @param newPluginCondition The new logical condition to merge into the existing plugin condition
     * @return true if changes were made to the plugin condition, false otherwise
     */
    public boolean updatePluginCondition(LogicalCondition newPluginCondition) {
        // Use the default update option (ADD_ONLY)
        return updatePluginCondition(newPluginCondition, UpdateOption.SYNC);
    }
    
    /**
     * Updates the plugin condition structure with new conditions from the given logical condition.
     * This method provides fine-grained control over how conditions are merged.
     * 
     * @param newPluginCondition The new logical condition to merge into the existing plugin condition
     * @param updateOption Controls how conditions are merged
     * @return true if changes were made to the plugin condition, false otherwise
     */
    public boolean updatePluginCondition(LogicalCondition newPluginCondition, UpdateOption updateOption) {
        return updatePluginCondition(newPluginCondition, updateOption, true);
    }
    
    /**
     * Updates the plugin condition structure with new conditions from the given logical condition.
     * This method provides complete control over how conditions are merged.
     * 
     * @param newPluginCondition The new logical condition to merge into the existing plugin condition
     * @param updateOption Controls how conditions are merged
     * @param preserveState If true, existing condition state is preserved when possible
     * @return true if changes were made to the plugin condition, false otherwise
     */
    public boolean updatePluginCondition(LogicalCondition newPluginCondition, UpdateOption updateOption, boolean preserveState) {        
        if (newPluginCondition == null) {
            return false;
        }
         
        // If we don't have a plugin condition yet, just set it directly
        if (pluginCondition == null) {
            setPluginCondition(newPluginCondition);
            log.debug("Initialized plugin condition from null");
            return true;
        }
        
        // Create a copy of the new condition and optimize it before comparison
        // This ensures both structures are in optimized form when comparing
        LogicalCondition optimizedNewCondition;
        if (newPluginCondition instanceof AndCondition) {
            optimizedNewCondition = new AndCondition();
        } else {
            optimizedNewCondition = new OrCondition();
        }
        
        // Copy all conditions from the new structure to the optimized copy
        for (Condition condition : newPluginCondition.getConditions()) {
            optimizedNewCondition.addCondition(condition);
        }
        
        // Optimize the new structure to match how the existing one is optimized
        optimizedNewCondition.optimizeStructure();
        
        if (!optimizedNewCondition.equals(pluginCondition)) {
            StringBuilder sb = new StringBuilder();
            sb.append("\nNew Plugin Condition Detected:\n");
            sb.append("newPluginCondition: \n\n\t").append(optimizedNewCondition.getDescription()).append("\n\n");
            sb.append("pluginCondition: \n\n\t").append(pluginCondition.getDescription()).append("\n\n");
            sb.append("Differences: \n\t").append(pluginCondition.getStructureDifferences(optimizedNewCondition));
            log.info(sb.toString());
            
        }
        
        // If the logical types don't match (AND vs OR), and we're not in REPLACE mode,
        // we need special handling
        boolean typeMismatch = 
            (pluginCondition instanceof AndCondition && !(newPluginCondition instanceof AndCondition)) ||
            (pluginCondition instanceof OrCondition && !(newPluginCondition instanceof OrCondition));
        
        // Use the rest of the existing function logic
        if (typeMismatch) {
            if (updateOption == UpdateOption.REPLACE) {
                // For REPLACE, just replace the entire condition
                log.debug("Replacing plugin condition due to logical type mismatch: {} -> {}", 
                         pluginCondition.getClass().getSimpleName(),
                         newPluginCondition.getClass().getSimpleName());
                setPluginCondition(newPluginCondition);
                return true;
            } else if (updateOption == UpdateOption.SYNC) {
                // For SYNC with type mismatch, log a warning but try to merge anyway
                log.warn("\nAttempting to synchronize plugin conditions with different logical types: {} ({})-> {} ({})", 
                        pluginCondition.getClass().getSimpleName(),pluginCondition.getConditions().size(),
                        newPluginCondition.getClass().getSimpleName(),newPluginCondition.getConditions().size());
                // Continue with sync by creating a new condition of the correct type
                LogicalCondition newRootCondition;
                if (newPluginCondition instanceof AndCondition) {
                    newRootCondition = new AndCondition();
                } else {
                    newRootCondition = new OrCondition();
                }
                
                // Copy all conditions from the old structure that also appear in the new one
                for (Condition existingCond : pluginCondition.getConditions()) {
                    if (newPluginCondition.contains(existingCond)) {
                        newRootCondition.addCondition(existingCond);
                    }
                }
                
                // Add any new conditions from the new structure
                for (Condition newCond : newPluginCondition.getConditions()) {
                    if (!newRootCondition.contains(newCond)) {
                        newRootCondition.addCondition(newCond);
                    }
                }
                
                setPluginCondition(newRootCondition);
                return true;
            }
        }
        
        // Use the LogicalCondition's updateLogicalStructure method with the specified options
        boolean conditionsUpdated = pluginCondition.updateLogicalStructure(
            newPluginCondition, 
            updateOption, 
            preserveState);
        
        if (!optimizedNewCondition.equals(pluginCondition)) {            
            StringBuilder sb = new StringBuilder();
            sb.append("Plugin condition updated  with option ->  difference should not occur: \nObjection:\t").append(updateOption).append("\n");
            sb.append("New Plugin Condition Detected:\n");
            sb.append("newPluginCondition: \n\n").append(optimizedNewCondition.getDescription()).append("\n\n");
            sb.append("pluginCondition: \n\n").append(pluginCondition.getDescription()).append("\n\n");
            sb.append("Differences: should not exist: \n\t").append(pluginCondition.getStructureDifferences(optimizedNewCondition));
            log.warn(sb.toString());        
        }
        
        // Optimize the condition structure after update if needed
        if (conditionsUpdated && updateOption != UpdateOption.REMOVE_ONLY) {
            // Optimize only if we added or changed conditions
            boolean optimized = pluginCondition.optimizeStructure();
            if (optimized) {
                log.info("Optimized plugin condition structure after update!! \n new plugin condition: \n\n" + pluginCondition);
                
            }
            
            // Validate the structure
            List<String> issues = pluginCondition.validateStructure();
            if (!issues.isEmpty()) {
                log.warn("Validation issues found in plugin condition structure:");
                for (String issue : issues) {
                    log.warn("  - {}", issue);
                }
            }
        }
        
        if (conditionsUpdated) {
            log.debug("Updated plugin condition structure, changes were applied");
            
            if (log.isTraceEnabled()) {
                String differences = pluginCondition.getStructureDifferences(newPluginCondition);
                log.trace("Condition structure differences after update:\n{}", differences);
            }
        } else {
            log.debug("No changes needed to plugin condition structure");
        }
        
        return conditionsUpdated;
    }
    
   
    
    /**
     * Clean up resources when this condition manager is no longer needed.
     * Implements the AutoCloseable interface for proper resource management.
     */
    @Override
    public void close() {
        // Unregister from events to prevent memory leaks
        unregisterEvents();
        
        // Cancel all scheduled watchdog tasks
        cancelAllWatchdogs();
        
        log.debug("ConditionManager resources cleaned up");
    }
    
    /**
     * Cancels all watchdog tasks scheduled by this condition manager.
     */
    public void cancelAllWatchdogs() {
        synchronized (watchdogFutures) {
            for (ScheduledFuture<?> future : watchdogFutures) {
                if (future != null && !future.isDone()) {
                    future.cancel(false);
                }
            }
            watchdogFutures.clear();
        }
        watchdogsRunning = false;
    }
    
    /**
     * Shutdowns the shared watchdog executor service.
     * This should only be called when the application is shutting down.
     */
    public static void shutdownSharedExecutor() {
        if (!SHARED_WATCHDOG_EXECUTOR.isShutdown()) {
            SHARED_WATCHDOG_EXECUTOR.shutdown();
            try {
                if (!SHARED_WATCHDOG_EXECUTOR.awaitTermination(1, TimeUnit.SECONDS)) {
                    SHARED_WATCHDOG_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                SHARED_WATCHDOG_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Periodically checks the plugin condition structure against a new condition structure
     * and updates if necessary. This creates a scheduled task that runs at the specified interval.
     * Uses the default ADD_ONLY update option.
     * 
     * @param conditionSupplier A supplier that returns the current desired plugin condition
     * @param interval The interval in milliseconds between checks
     * @return A handle to the scheduled future task (can be used to cancel)
     */
    public ScheduledFuture<?> scheduleConditionWatchdog(
            java.util.function.Supplier<LogicalCondition> conditionSupplier,
            long interval) {
        
        return scheduleConditionWatchdog(conditionSupplier, interval, UpdateOption.SYNC);
    }
    
    /**
     * Periodically checks the plugin condition structure against a new condition structure
     * and updates if necessary. This creates a scheduled task that runs at the specified interval.
     * 
     * @param conditionSupplier A supplier that returns the current desired plugin condition
     * @param interval The interval in milliseconds between checks
     * @param updateOption The update option to use for condition changes
     * @return A handle to the scheduled future task (can be used to cancel)
     */
    public ScheduledFuture<?> scheduleConditionWatchdog(
            java.util.function.Supplier<LogicalCondition> conditionSupplier,
            long interval,
            UpdateOption updateOption) {
        if(areWatchdogsRunning() ) {
            
            log.debug("Watchdogs were already running, cancelling all before starting new ones");            
        }
        if (!watchdogFutures.isEmpty()) {
            watchdogFutures.clear();
        }
        
        // Store the configuration for possible later resume
        currentWatchdogSupplier = conditionSupplier;
        currentWatchdogInterval = interval;
        currentWatchdogUpdateOption = updateOption;
        
        ScheduledFuture<?> future = SHARED_WATCHDOG_EXECUTOR.scheduleWithFixedDelay(() -> { //scheduleWithFixedRate
            try {
                // First cleanup any non-triggerable conditions from existing structures
                boolean cleanupDone = cleanupNonTriggerableConditions();
                if (cleanupDone) {
                    log.debug("Watchdog removed non-triggerable conditions from logical structures");
                }
                
                // Then update with new conditions
                LogicalCondition currentDesiredCondition = conditionSupplier.get();
                if (currentDesiredCondition == null) {
                    currentDesiredCondition = new OrCondition();
                }
                
                // Clean non-triggerable conditions from the new condition structure too
                if (currentDesiredCondition != null) {
                    LogicalCondition.removeNonTriggerableConditions(currentDesiredCondition);
                    
                    boolean updated = updatePluginCondition(currentDesiredCondition, updateOption);
                    if (updated) {
                        log.debug("Watchdog updated plugin conditions using mode: {}", updateOption);
                    }
                }
            } catch (Exception e) {
                log.error("Error in plugin condition watchdog", e);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
        
        // Track this future for proper cleanup
        synchronized (watchdogFutures) {
            watchdogFutures.add(future);
        }
        
        watchdogsRunning = true;
        return future;
    }

    /**
     * Checks if watchdogs are currently running for this condition manager.
     * 
     * @return true if at least one watchdog is active
     */
    public boolean areWatchdogsRunning() {
        if (!watchdogsRunning) {
            return false;
        }
        
        // Double-check by examining futures
        synchronized (watchdogFutures) {
            if (watchdogFutures.isEmpty()) {
                watchdogsRunning = false;
                return false;
            }
            
            // Check if at least one watchdog is active
            for (ScheduledFuture<?> future : watchdogFutures) {
                if (future != null && !future.isDone() && !future.isCancelled()) {
                    return true;
                }
            }
            
            // No active watchdogs found
            watchdogsRunning = false;
            return false;
        }
    }
    
    /**
     * Pauses all watchdog tasks without removing them completely.
     * This allows them to be resumed later with the same settings.
     * 
     * @return true if watchdogs were successfully paused
     */
    public boolean pauseWatchdogs() {
        if (!watchdogsRunning) {
            return false; // Nothing to pause
        }
        
        cancelAllWatchdogs();
        watchdogsRunning = false;
        log.debug("Watchdogs paused");
        return true;
    }
    
    /**
     * Resumes watchdogs that were previously paused with the same configuration.
     * If no watchdog was previously configured, this does nothing.
     * 
     * @return true if watchdogs were successfully resumed
     */
    public boolean resumeWatchdogs() {
        if (watchdogsRunning || currentWatchdogSupplier == null) {
            return false; // Already running or never configured
        }
        
        scheduleConditionWatchdog(
            currentWatchdogSupplier,
            currentWatchdogInterval,
            currentWatchdogUpdateOption
        );
        
        watchdogsRunning = true;
        log.debug("Watchdogs resumed with interval {}ms and update option {}", 
                  currentWatchdogInterval, currentWatchdogUpdateOption);
        return true;
    }
    
    /**
     * Registers events and starts watchdogs in one call.
     * If watchdogs are already configured but paused, this will resume them.
     * 
     * @param conditionSupplier The supplier for conditions
     * @param intervalMillis The interval for watchdog checks
     * @param updateOption How to update conditions
     * @return true if successfully started
     */
    public boolean registerEventsAndStartWatchdogs(
            Supplier<LogicalCondition> conditionSupplier,
            long intervalMillis,
            UpdateOption updateOption) {
        
        // Register for events first
        registerEvents();
        
        // Then setup watchdogs
        if (watchdogsRunning) {
            // If already running with different settings, restart
            pauseWatchdogs();
        }
        
        // Store current configuration
        currentWatchdogSupplier = conditionSupplier;
        currentWatchdogInterval = intervalMillis;
        currentWatchdogUpdateOption = updateOption;
        
        // Start the watchdogs
        scheduleConditionWatchdog(conditionSupplier, intervalMillis, updateOption);
        watchdogsRunning = true;
        
        return true;
    }
    
    /**
     * Unregisters events and pauses watchdogs in one call.
     */
    public void unregisterEventsAndPauseWatchdogs() {
        unregisterEvents();
        pauseWatchdogs();
    }

    /**
     * Cleans up non-triggerable conditions from both plugin and user logical structures.
     * This is useful to call periodically to keep the condition structures streamlined.
     * 
     * @return true if any conditions were removed, false otherwise
     */
    public boolean cleanupNonTriggerableConditions() {
        boolean anyRemoved = false;
        
        // Clean up plugin conditions
        if (pluginCondition != null) {
            anyRemoved = LogicalCondition.removeNonTriggerableConditions(pluginCondition) || anyRemoved;
        }
        
        // Clean up user conditions
        if (userLogicalCondition != null) {
            anyRemoved = LogicalCondition.removeNonTriggerableConditions(userLogicalCondition) || anyRemoved;
        }
        
        return anyRemoved;
    }

}