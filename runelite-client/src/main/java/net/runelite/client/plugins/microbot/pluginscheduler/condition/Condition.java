package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

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

/**
 * Base interface for script execution conditions.
 * Provides common functionality for condition checking and configuration.
 */
public interface Condition {
    
    public static String getVersion(){
        return getVersion();
    }
    /**
     * Checks if the condition is currently met
     * @return true if condition is satisfied, false otherwise
     */
    boolean isSatisfied();
    
    /**
     * Returns a human-readable description of this condition
     * @return description string
     */
    String getDescription();

    /**
     * Returns a detailed description of the level condition with additional status information
     */
    String getDetailedDescription();
    /**
     * Gets the next time this condition will be satisfied.
     * For time-based conditions, this returns the actual next trigger time.
     * For satisfied conditions, this returns a time slightly in the past.
     * For non-time conditions that aren't satisfied, this returns Optional.empty().
     * 
     * @return Optional containing the next trigger time, or empty if not applicable
     */
    default Optional<ZonedDateTime> getCurrentTriggerTime() {
        // If the condition is already satisfied, return a time 1 second in the past
        if (isSatisfied()) {
            return Optional.of(ZonedDateTime.now(ZoneId.systemDefault()).minusSeconds(1));
        }
        // Default implementation for non-time conditions that aren't satisfied
        return Optional.empty();
    }
        /**
     * Returns the type of this condition
     * @return ConditionType enum value
     */
    ConditionType getType();
    /**
     * Resets the condition to its initial state.
     * <p>
     * For example, in a time-based condition, calling this method
     * will update the reference timestamp used for calculating
     * time intervals.
     */
    default void reset(){
        reset(false);
    }

    void reset (boolean randomize);

    default void onGameStateChanged(GameStateChanged gameStateChanged) {
        // This event handler is called whenever the game state changes
        // Useful for conditions that depend on the game state (e.g., logged in, logged out)
    }
    default void onStatChanged(StatChanged event) {
        // This event handler is called whenever a skill stat changes
        // Useful for skill-based conditions
    }
    default void onItemContainerChanged(ItemContainerChanged event) {
        // This event handler is called whenever inventory or bank contents change
        // Useful for item-based conditions
    }
    default void onGameTick(GameTick gameTick) {
        // This event handler is called every game tick (approximately once per 0.6 seconds)
        // Useful for time-based conditions
    }
    default void onNpcChanged(NpcChanged event){
        // This event handler is called whenever an NPC changes
        // Useful for NPC-based conditions
    }
    default void onNpcSpawned(NpcSpawned npcSpawned){
        // This event handler is called whenever an NPC spawns
        // Useful for NPC-based conditions
    }
    default void onNpcDespawned(NpcDespawned npcDespawned){
        // This event handler is called whenever an NPC despawns
        // Useful for NPC-based conditions
    }
     /**
     * Called when a ground item is spawned in the game world
     */
    default void onGroundObjectSpawned(GroundObjectSpawned event) {
        // Optional implementation
    }
    
    /**
     * Called when a ground item is despawned from the game world
     */
    default void onGroundObjectDespawned(GroundObjectDespawned event) {
        // Optional implementation
    }
    /**
     * Called when an item is spawned in the game world
     */
    default void onItemSpawned(ItemSpawned event) {
        // Optional implementation
    }
    /**
     * Called when an item is despawned from the game world
     */
    default void onItemDespawned(ItemDespawned event){
        // This event handler is called whenever an item despawns
        // Useful for item-based conditions
    }
    
    /**
     * Called when a menu option is clicked
     */
    default void onMenuOptionClicked(MenuOptionClicked event) {
        // Optional implementation  
    }
    
    /**
     * Called when a chat message is received
     */
    default void onChatMessage(ChatMessage event) {
        // Optional implementation
    }
    
    /**
     * Called when a hitsplat is applied to a character
     */
    default void onHitsplatApplied(HitsplatApplied event) {
        // Optional implementation
    }
    default void onVarbitChanged(VarbitChanged event){
        // Optional implementation
    }
    default void onInteractingChanged(InteractingChanged event){
        // Optional implementation
    }      
    default void onAnimationChanged(AnimationChanged event) {
        // Optional implementation
    }
    /**
     * Returns the progress percentage for this condition (0-100).
     * For simple conditions that are either met or not met, this will return 0 or 100.
     * For conditions that track progress (like XP conditions), this will return a value between 0 and 100.
     * 
     * @return Percentage of condition completion (0-100)
     */
    default double getProgressPercentage() {
        // Default implementation returns 0 for not met, 100 for met
        return isSatisfied() ? 100.0 : 0.0;
    }
    
    /**
     * Gets the total number of leaf conditions in this condition tree.
     * For simple conditions, this is 1.
     * For logical conditions, this is the sum of all contained conditions' counts.
     *
     * @return Total number of leaf conditions in this tree
     */
    default int getTotalConditionCount() {
        return 1; // Simple conditions count as 1
    }
    
    /**
     * Gets the number of leaf conditions that are currently met.
     * For simple conditions, this is 0 or 1.
     * For logical conditions, this is the sum of all contained conditions' met counts.
     *
     * @return Number of met leaf conditions in this tree
     */
    default int getMetConditionCount() {
        return isSatisfied() ? 1 : 0; // Simple conditions return 1 if met, 0 otherwise
    }
    
    /**
     * Generates detailed status information for this condition and any nested conditions.
     * 
     * @param indent Current indentation level for nested formatting
     * @param showProgress Whether to include progress percentage in the output
     * @return A string with detailed status information
     */
    default String getStatusInfo(int indent, boolean showProgress) {
        StringBuilder sb = new StringBuilder();
        
        String indentation = " ".repeat(indent);
        boolean isSatisfied = isSatisfied();
        
        sb.append(indentation)
          .append(getDescription())
          .append(" [")
          .append(isSatisfied ? "SATISFIED" : "NOT SATISFIED")
          .append("]");
        
        if (showProgress) {
            double progress = getProgressPercentage();
            if (progress > 0 && progress < 100) {
                sb.append(" (").append(String.format("%.1f%%", progress)).append(")");
            }
        }
        
        return sb.toString();
    }
    
}