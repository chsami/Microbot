package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple conditions for script execution.
 * Supports AND/OR combinations of conditions and listens to relevant events
 * to update condition statuses.
 */
@Slf4j
public class ConditionManager {
    
    //private final List<Condition> userConditions = new ArrayList<>();    
    
    private LogicalCondition pluginCondition = null;
    @Getter
    private LogicalCondition userLogicalCondition;
    private final EventBus eventBus;
    private boolean eventsRegistered = false;
    public ConditionManager() {
        this.eventBus = Microbot.getEventBus();
        userLogicalCondition = new AndCondition();
        registerEvents();
    }
    public void setPluginCondition(LogicalCondition condition) {
        pluginCondition = condition;
    }
    public LogicalCondition getPluginCondition() {
        return pluginCondition;
    }    
    public List<Condition> getConditions() {
        List<Condition> conditions = new ArrayList<>();
        if (pluginCondition != null) {
            conditions.addAll(pluginCondition.getConditions());
        }
        conditions.addAll(userLogicalCondition.getConditions());
        return conditions;
    }
    public LogicalCondition getUserCondition() {
        return userLogicalCondition;
    }
    public  void clearUserConditions() {
        userLogicalCondition.getConditions().clear();
    }
    public boolean areConditionsMet() {
        boolean userConditionsMet = false;
    
        if (userLogicalCondition == null){
            requiresAll();            
        }
        userConditionsMet = userLogicalCondition.isSatisfied();                            
        if (pluginCondition != null) {          

            return userConditionsMet && pluginCondition.isSatisfied();
        }
        return userConditionsMet;                                                
    }
   /**
     * Returns a list of conditions that were defined by the user (not plugin-defined)
     */
    public List<Condition> getUserConditions() {
        if (userLogicalCondition == null) {
            return new ArrayList<>();
        }
        
        List<Condition> result = new ArrayList<>();
        for (Condition condition : userLogicalCondition.getConditions()) {
            if (!isPluginDefinedCondition(condition)) {
                result.add(condition);
            }
        }
        return result;
    }
    public void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        log.info(getDescription() + " Registering events");
        eventBus.register(this);
        eventsRegistered = true;
    }
    
    public void unregisterEvents() {
        if (!eventsRegistered) {
            return;
        }
        log.info(getDescription() + " Unregistering events");
        eventBus.unregister(this);
        eventsRegistered = false;
    }
    
   
    public void setRequireAll() {
        userLogicalCondition = new AndCondition();
        setUserLogicalCondition(userLogicalCondition);
        
    }
    
    public void setRequireAny() {
        userLogicalCondition = new OrCondition();        
        setUserLogicalCondition(userLogicalCondition);
    }
    
   
    
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
     * Checks if the manager requires all conditions to be met (AND logic)
     */
    public boolean userConditionRequiresAll() {

        return userLogicalCondition instanceof AndCondition;
    }
    public boolean userConditionRequiresAny() {
        return userLogicalCondition instanceof OrCondition;
    }
    public boolean requiresAll() {
        return this.getFullLogicalCondition() instanceof AndCondition;
    }
    public boolean requiresAny() {
        return this.getFullLogicalCondition() instanceof OrCondition;
    }
    public void reset() {
        userLogicalCondition.reset();
        if (pluginCondition != null) {
            pluginCondition.reset();
        }
        
    }
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
            if (pluginCondition == condition) {
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
                    if (logicalChild.getConditions().isEmpty()) {
                        parent.getConditions().remove(i);
                    }
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
                        if (wrappedLogical.getConditions().isEmpty()) {
                            parent.getConditions().remove(i);
                        }
                        return true;
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
     * Finds which logical condition contains the given condition
     */
    public LogicalCondition findContainingLogical(Condition condition) {
        ensureUserLogicalExists();
        
        // Check user logical first
        LogicalCondition result = userLogicalCondition.findContainingLogical(condition);
        if (result != null) {
            return result;
        }
        
        // Check plugin logical if exists
        if (pluginCondition != null) {
            return pluginCondition.findContainingLogical(condition);
        }
        
        return null;
    }

    /**
     * Adds a condition to the specified logical condition, or to the user root if none specified
     */
    public void addCondition(Condition condition, LogicalCondition targetLogical) {
        ensureUserLogicalExists();
        
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
    public void addCondition(Condition condition) {
        addCondition(condition, userLogicalCondition);
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

    @Subscribe(priority = -1)
    public void onStatChanged(StatChanged event) {
        // Propagate event to all conditions
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onStatChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during StatChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onStatChanged(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during StatChanged event: {}", 
                    e.getMessage(), e);
            }
        }
    }
    
    @Subscribe(priority = -1)
    public void onItemContainerChanged(ItemContainerChanged event) {
         // Propagate event to all conditions
         log.info("ItemContainerChanged event" + event);
         for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onItemContainerChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during ItemContainerChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onItemContainerChanged(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during ItemContainerChanged event: {}", 
                    e.getMessage(), e);
            }
        }
    }
    @Subscribe(priority = -1)
    public void onGameTick(GameTick gameTick) {
        // Propagate event to all conditions
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onGameTick(gameTick);
            } catch (Exception e) {
                log.error("Error in condition {} during GameTick event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onGameTick(gameTick);
            } catch (Exception e) {
                log.error("Error in plugin condition during GameTick event: {}", 
                    e.getMessage(), e);
            }
        }
    }

    @Subscribe(priority = -1)
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        // Propagate event to all conditions
        for (Condition condition : userLogicalCondition.getConditions()) {
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
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onGroundObjectDespawned(event);
            } catch (Exception e) {
                log.error("Error in condition {} during GroundItemDespawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onGroundObjectDespawned(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during GroundItemDespawned event: {}", 
                    e.getMessage(), e);
            }
        }
    }

    @Subscribe(priority = -1)
    public void onMenuOptionClicked(MenuOptionClicked event) {
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onMenuOptionClicked(event);
            } catch (Exception e) {
                log.error("Error in condition {} during MenuOptionClicked event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onMenuOptionClicked(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during MenuOptionClicked event: {}", 
                    e.getMessage(), e);
            }
        }
    }

    @Subscribe(priority = -1)
    public void onChatMessage(ChatMessage event) {
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onChatMessage(event);
            } catch (Exception e) {
                log.error("Error in condition {} during ChatMessage event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onChatMessage(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during ChatMessage event: {}", 
                    e.getMessage(), e);
            }
        }
    }

    @Subscribe(priority = -1)
    public void onHitsplatApplied(HitsplatApplied event) {
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onHitsplatApplied(event);
            } catch (Exception e) {
                log.error("Error in condition {} during HitsplatApplied event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onHitsplatApplied(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during HitsplatApplied event: {}", 
                    e.getMessage(), e);
            }
        }
    }
    @Subscribe(priority = -1)
	public void onVarbitChanged(VarbitChanged event)
	{
		for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onVarbitChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during VarbitChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onVarbitChanged(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during VarbitChanged event: {}", 
                    e.getMessage(), e);
            }
        }      
	}
    @Subscribe(priority = -1)
    void onNpcChanged(NpcChanged event){
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onNpcChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during NpcChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onNpcChanged(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during NpcChanged event: {}", 
                    e.getMessage(), e);
            }
        }
    }
    @Subscribe(priority = -1)
    void onNpcSpawned(NpcSpawned npcSpawned){
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onNpcSpawned(npcSpawned);
            } catch (Exception e) {
                log.error("Error in condition {} during NpcSpawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onNpcSpawned(npcSpawned);
            } catch (Exception e) {
                log.error("Error in plugin condition during NpcSpawned event: {}", 
                    e.getMessage(), e);
            }
        }
    }
    @Subscribe(priority = -1)
    void onNpcDespawned(NpcDespawned npcDespawned){
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onNpcDespawned(npcDespawned);
            } catch (Exception e) {
                log.error("Error in condition {} during NpcDespawned event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onNpcDespawned(npcDespawned);
            } catch (Exception e) {
                log.error("Error in plugin condition during NpcDespawned event: {}", 
                    e.getMessage(), e);
            }
        }
    }
    @Subscribe(priority = -1)
    void onInteractingChanged(InteractingChanged event){
        for (Condition condition : userLogicalCondition.getConditions()) {
            try {
                condition.onInteractingChanged(event);
            } catch (Exception e) {
                log.error("Error in condition {} during InteractingChanged event: {}", 
                    condition.getDescription(), e.getMessage(), e);
            }
        }
        if (pluginCondition != null) {
            try {
                pluginCondition.onInteractingChanged(event);
            } catch (Exception e) {
                log.error("Error in plugin condition during InteractingChanged event: {}", 
                    e.getMessage(), e);
            }
        }
    }
}