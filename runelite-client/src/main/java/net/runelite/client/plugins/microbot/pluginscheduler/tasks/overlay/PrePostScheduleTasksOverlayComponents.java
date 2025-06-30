package net.runelite.client.plugins.microbot.pluginscheduler.tasks.overlay;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory class for creating overlay components that display the current state of
 * PrePostScheduleRequirements and AbstractPrePostScheduleTasks.
 * 
 * This provides convenience methods to generate overlay components showing:
 * - Current task execution phase and status
 * - Active requirements being processed
 * - Progress indicators for different requirement types
 * 
 * The components are designed to be concise and non-cluttering for in-game display.
 */
@Slf4j
public class PrePostScheduleTasksOverlayComponents {
    
    // Color scheme for different states
    private static final Color TITLE_COLOR = Color.CYAN;
    private static final Color ACTIVE_COLOR = Color.YELLOW;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    private static final Color ERROR_COLOR = Color.RED;
    private static final Color INFO_COLOR = Color.WHITE;
    private static final Color DISABLED_COLOR = Color.GRAY;
    
    /**
     * Creates a title component for the requirement overlay.
     * 
     * @param pluginName The name of the plugin
     * @param tasks The task manager instance
     * @return A TitleComponent for the overlay
     */
    public static TitleComponent createTitleComponent(String pluginName, AbstractPrePostScheduleTasks tasks) {
        String title = pluginName + " Tasks";
        Color titleColor = TITLE_COLOR;
        
        if (tasks != null && tasks.isExecuting()) {
            title += " (ACTIVE)";
            titleColor = ACTIVE_COLOR;
        }
        
        return TitleComponent.builder()
            .text(title)
            .color(titleColor)
            .build();
    }
    
    /**
     * Creates line components showing the current execution status.
     * 
     * @param tasks The task manager instance
     * @param requirements The requirements instance
     * @return List of LineComponents showing current status
     */
    public static List<LineComponent> createExecutionStatusComponents(AbstractPrePostScheduleTasks tasks, PrePostScheduleRequirements requirements) {
        List<LineComponent> components = new ArrayList<>();
        
        if (tasks == null) {
            return components;
        }
        
        // Get state from either tasks or requirements (they should be synchronized)
        TaskExecutionState state = tasks.getExecutionState();
        if (requirements != null && requirements.getExecutionState().isExecuting()) {
            // If requirements are actively being fulfilled, use that state instead
            state = requirements.getExecutionState();
        }
        
        String displayStatus = state.getDisplayStatus();
        if (displayStatus != null) {
            Color statusColor = getStatusColor(state);
            components.add(LineComponent.builder()
                .left("Status:")
                .right(displayStatus)
                .leftColor(INFO_COLOR)
                .rightColor(statusColor)
                .build());
            
            // Show current requirement name if available
            String currentRequirementName = state.getCurrentRequirementName();
            if (currentRequirementName != null && !currentRequirementName.isEmpty() && state.isFulfillingRequirements()) {
                // Truncate long requirement names for overlay display
                String displayName = currentRequirementName.length() > 25 ? 
                    currentRequirementName.substring(0, 22) + "..." : currentRequirementName;
                    
                components.add(LineComponent.builder()
                    .left("  Processing:")
                    .right(displayName)
                    .leftColor(INFO_COLOR)
                    .rightColor(ACTIVE_COLOR)
                    .build());
            }
            
            // Show step progress if available
            if (state.isFulfillingRequirements() && state.getTotalRequirementsInStep() > 0) {
                String progress = String.format("Item %d/%d", 
                    state.getCurrentRequirementIndex(), state.getTotalRequirementsInStep());
                components.add(LineComponent.builder()
                    .left("  Progress:")
                    .right(progress)
                    .leftColor(INFO_COLOR)
                    .rightColor(state.getCurrentRequirementIndex() == state.getTotalRequirementsInStep() ? SUCCESS_COLOR : ACTIVE_COLOR)
                    .build());
            }
            
            // Show overall progress for requirement fulfillment
            if (state.isFulfillingRequirements() && state.getTotalSteps() > 0) {
                int overallProgress = state.getProgressPercentage();
                components.add(LineComponent.builder()
                    .left("  Overall:")
                    .right(overallProgress + "% (" + state.getCurrentStepNumber() + "/" + state.getTotalSteps() + ")")
                    .leftColor(INFO_COLOR)
                    .rightColor(overallProgress == 100 ? SUCCESS_COLOR : ACTIVE_COLOR)
                    .build());
            }
        }
        
        return components;
    }
    
    /**
     * Gets the appropriate color for the current execution state
     */
    private static Color getStatusColor(TaskExecutionState state) {
        if (state.isInErrorState()) {
            return ERROR_COLOR;
        }
        
        switch (state.getCurrentState()) {
            case COMPLETED:
                return SUCCESS_COLOR;
            case FAILED:
            case ERROR:
                return ERROR_COLOR;
            case FULFILLING_REQUIREMENTS:
            case CUSTOM_TASKS:
                return ACTIVE_COLOR;
            case STARTING:
            default:
                return INFO_COLOR;
        }
    }
    
    /**
     * Creates line components showing current location requirement status.
     * 
     * @param requirements The requirements instance
     * @param context The schedule context (PRE_SCHEDULE or POST_SCHEDULE)
     * @return List of LineComponents showing location status
     */
    public static List<LineComponent> createLocationStatusComponents(PrePostScheduleRequirements requirements, ScheduleContext context) {
        List<LineComponent> components = new ArrayList<>();
        
        if (requirements == null) {
            return components;
        }
        
        List<LocationRequirement> locationReqs = requirements.getRequirements(LocationRequirement.class, context);
        if (locationReqs.isEmpty()) {
            return components;
        }
        
        LocationRequirement locationReq = locationReqs.get(0); // Take first one
        WorldPoint targetLocation = locationReq.getTargetLocation();
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        
        // Calculate distance
        int distance = currentLocation != null && targetLocation != null ? 
            currentLocation.distanceTo(targetLocation) : -1;
        
        String contextLabel = context == ScheduleContext.PRE_SCHEDULE ? "Pre-Loc" : "Post-Loc";
        
        components.add(LineComponent.builder()
            .left(contextLabel + ":")
            .right(locationReq.getLocationName())
            .leftColor(INFO_COLOR)
            .rightColor(distance <= 10 ? SUCCESS_COLOR : ACTIVE_COLOR)
            .build());
        
        if (distance >= 0) {
            components.add(LineComponent.builder()
                .left("Distance:")
                .right(distance + " tiles")
                .leftColor(INFO_COLOR)
                .rightColor(distance <= 10 ? SUCCESS_COLOR : (distance <= 50 ? ACTIVE_COLOR : ERROR_COLOR))
                .build());
        }
        
        return components;
    }
    
    /**
     * Creates line components showing current spellbook requirement status.
     * 
     * @param requirements The requirements instance
     * @param context The schedule context
     * @return List of LineComponents showing spellbook status
     */
    public static List<LineComponent> createSpellbookStatusComponents(PrePostScheduleRequirements requirements, ScheduleContext context) {
        List<LineComponent> components = new ArrayList<>();
        
        if (requirements == null) {
            return components;
        }
        
        List<SpellbookRequirement> spellbookReqs = requirements.getRequirements(SpellbookRequirement.class, context);
        if (spellbookReqs.isEmpty()) {
            return components;
        }
        
        SpellbookRequirement spellbookReq = spellbookReqs.get(0); // Take first one
        String contextLabel = context == ScheduleContext.PRE_SCHEDULE ? "Pre-Spell" : "Post-Spell";
        
        components.add(LineComponent.builder()
            .left(contextLabel + ":")
            .right(spellbookReq.getRequiredSpellbook().name())
            .leftColor(INFO_COLOR)
            .rightColor(ACTIVE_COLOR)
            .build());
        
        return components;
    }
    
    /**
     * Creates line components showing current loot requirement status.
     * 
     * @param requirements The requirements instance
     * @param context The schedule context
     * @return List of LineComponents showing loot status
     */
    public static List<LineComponent> createLootStatusComponents(PrePostScheduleRequirements requirements, ScheduleContext context) {
        List<LineComponent> components = new ArrayList<>();
        
        if (requirements == null) {
            return components;
        }
        
        List<LootRequirement> lootReqs = requirements.getRequirements(LootRequirement.class, context);
        if (lootReqs.isEmpty()) {
            return components;
        }
        
        String contextLabel = context == ScheduleContext.PRE_SCHEDULE ? "Pre-Loot" : "Post-Loot";
        
        for (LootRequirement lootReq : lootReqs) {
            components.add(LineComponent.builder()
                .left(contextLabel + ":")
                .right(lootReq.getName() + " (" + lootReq.getAmount() + ")")
                .leftColor(INFO_COLOR)
                .rightColor(ACTIVE_COLOR)
                .build());
        }
        
        return components;
    }
    
    /**
     * Creates line components showing current item requirement status.
     * Only shows the most critical items to avoid clutter.
     * 
     * @param requirements The requirements instance
     * @param context The schedule context
     * @return List of LineComponents showing item status
     */
    public static List<LineComponent> createItemStatusComponents(PrePostScheduleRequirements requirements, ScheduleContext context) {
        List<LineComponent> components = new ArrayList<>();
        
        if (requirements == null) {
            return components;
        }
        
        List<ItemRequirement> itemReqs = requirements.getRequirements(ItemRequirement.class, context);
        if (itemReqs.isEmpty()) {
            return components;
        }
        
        String contextLabel = context == ScheduleContext.PRE_SCHEDULE ? "Pre-Items" : "Post-Items";
        
        // Only show first few items to avoid clutter
        int maxItems = 3;
        int count = 0;
        
        for (ItemRequirement itemReq : itemReqs) {
            if (count >= maxItems) {
                break;
            }
            
            // For now, use a simplified approach to get item name
            String itemName = "Item";
            try {
                itemName = itemReq.toString(); // Fallback to toString if getName() is not accessible
                if (itemName.length() > 30) {
                    itemName = itemName.substring(0, 27) + "...";
                }
            } catch (Exception e) {
                itemName = "Unknown Item";
            }
            
            components.add(LineComponent.builder()
                .left(contextLabel + ":")
                .right(itemName + " (" + itemReq.getAmount() + ")")
                .leftColor(INFO_COLOR)
                .rightColor(ACTIVE_COLOR)
                .build());
            
            count++;
        }
        
        if (itemReqs.size() > maxItems) {
            components.add(LineComponent.builder()
                .left("")
                .right("+" + (itemReqs.size() - maxItems) + " more items")
                .leftColor(INFO_COLOR)
                .rightColor(DISABLED_COLOR)
                .build());
        }
        
        return components;
    }
    
    /**
     * Creates line components showing the current requirement being processed.
     * Only shows information about the specific requirement currently being fulfilled.
     * 
     * @param requirements The requirements instance
     * @return List of LineComponents showing current requirement details
     */
    public static List<LineComponent> createCurrentRequirementComponents(PrePostScheduleRequirements requirements) {
        List<LineComponent> components = new ArrayList<>();
        
        if (requirements == null || !requirements.isFulfilling()) {
            return components;
        }
        
        TaskExecutionState state = requirements.getExecutionState();
        
        // Show the current step being processed
        if (state.getCurrentStep() != null) {
            components.add(LineComponent.builder()
                .left("Current Step:")
                .right(state.getCurrentStep().getDisplayName())
                .leftColor(INFO_COLOR)
                .rightColor(ACTIVE_COLOR)
                .build());
        }
        
        // Show current details if available
        String details = state.getCurrentDetails();
        if (details != null && !details.isEmpty()) {
            // Shorten details if too long
            if (details.length() > 30) {
                details = details.substring(0, 27) + "...";
            }
            
            components.add(LineComponent.builder()
                .left("Details:")
                .right(details)
                .leftColor(INFO_COLOR)
                .rightColor(INFO_COLOR)
                .build());
        }
        
        return components;
    }
    
    /**
     * Creates a complete set of overlay components for the current requirement status.
     * This is the main method that should be called from plugin overlays.
     * 
     * @param pluginName The name of the plugin
     * @param tasks The task manager instance
     * @param requirements The requirements instance
     * @return List of all overlay components
     */
    public static List<Object> createAllComponents(String pluginName, AbstractPrePostScheduleTasks tasks, PrePostScheduleRequirements requirements) {
        List<Object> components = new ArrayList<>();
        
        // Add title
        components.add(createTitleComponent(pluginName, tasks));
        
        // Add execution status
        components.addAll(createExecutionStatusComponents(tasks, requirements));
        
        if (requirements != null && tasks != null && (tasks.isExecuting() || requirements.isFulfilling())) {
            // Show only the current requirement being processed
            components.addAll(createCurrentRequirementComponents(requirements));
        }
        
        return components;
    }
}
