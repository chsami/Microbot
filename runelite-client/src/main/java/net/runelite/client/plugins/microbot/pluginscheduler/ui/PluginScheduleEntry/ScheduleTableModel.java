package net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry;

import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;

/**
 * Interface for table models that store PluginScheduleEntry instances
 * and can retrieve them by row index.
 */
public interface ScheduleTableModel {
    /**
     * Gets the PluginScheduleEntry at the specified row index
     * 
     * @param row The row index
     * @return The PluginScheduleEntry at that row, or null if not found
     */
    PluginScheduleEntry getPluginAtRow(int row);
}