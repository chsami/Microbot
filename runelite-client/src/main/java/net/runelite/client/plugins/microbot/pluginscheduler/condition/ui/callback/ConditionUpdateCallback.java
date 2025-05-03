package net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.callback;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;

import java.io.File;

/**
 * Callback interface for condition updates in the scheduler system.
 * <p>
 * This interface defines methods that are called when conditions are updated or reset
 * in the UI. Implementing classes can respond to these events by saving the updated
 * conditions to the appropriate location (config or file).
 * <p>
 * This approach provides a flexible way for different components of the system to be
 * notified about condition changes without needing to know the details of how and
 * where conditions are saved.
 */
public interface ConditionUpdateCallback {
    
    /**
     * Called when conditions are updated in the UI.
     * This version saves to the default configuration.
     * 
     * @param logicalCondition The updated logical condition structure
     * @param plugin The plugin entry whose conditions are being updated
     * @param isStopCondition True if these are stop conditions, false for start conditions
     */
    void onConditionsUpdated(LogicalCondition logicalCondition, 
                            PluginScheduleEntry plugin, 
                            boolean isStopCondition);
    
    /**
     * Called when conditions are updated in the UI with a specific file destination.
     * 
     * @param logicalCondition The updated logical condition structure
     * @param plugin The plugin entry whose conditions are being updated
     * @param isStopCondition True if these are stop conditions, false for start conditions
     * @param saveFile The file to save the conditions to, or null to use default config
     */
    void onConditionsUpdated(LogicalCondition logicalCondition, 
                            PluginScheduleEntry plugin, 
                            boolean isStopCondition,
                            File saveFile);
    
    /**
     * Called when conditions are reset in the UI.
     * 
     * @param plugin The plugin entry whose conditions are being reset
     * @param isStopCondition True if these are stop conditions, false for start conditions
     */
    void onConditionsReset(PluginScheduleEntry plugin, boolean isStopCondition);
}