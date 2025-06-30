package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.overlay.PrePostScheduleTasksOverlayComponents;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

/**
 * Overlay for the SchedulableExample plugin that displays the current state of
 * pre/post schedule requirements and task execution.
 * 
 * This overlay demonstrates how to integrate the RequirementOverlayComponentFactory
 * to provide real-time feedback about requirement fulfillment progress.
 */
public class SchedulableExampleOverlay extends OverlayPanel {
    
    private final SchedulableExamplePlugin plugin;
    
    @Inject
    public SchedulableExampleOverlay(SchedulableExamplePlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPreferredSize(new Dimension(ComponentConstants.STANDARD_WIDTH, 200));
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        // Clear previous components
        panelComponent.getChildren().clear();
        
        // Get the task manager and requirements
        SchedulableExamplePrePostScheduleTasks tasks = plugin.getPrePostScheduleTasks();
        SchedulableExamplePrePostScheduleRequirements requirements = plugin.getPrePostScheduleRequirements();
        
        // Only show overlay if pre/post requirements are enabled or tasks are running
        if (!plugin.getConfig().enablePrePostRequirements() && 
            (tasks == null || !tasks.isExecuting())) {
            return null; // Don't show overlay when not needed
        }
        
        try {
            // Generate overlay components using the factory
            List<Object> components = PrePostScheduleTasksOverlayComponents.createAllComponents(
                "SchedulableExample", 
                tasks, 
                requirements
            );
            
            // Add all components to the panel
            for (Object component : components) {
                if (component instanceof net.runelite.client.ui.overlay.components.LayoutableRenderableEntity) {
                    panelComponent.getChildren().add((net.runelite.client.ui.overlay.components.LayoutableRenderableEntity) component);
                }
            }
            
            // Show configuration status when not executing
            if (tasks == null || !tasks.isExecuting()) {
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Pre/Post Requirements:")
                    .right(plugin.getConfig().enablePrePostRequirements() ? "ENABLED" : "DISABLED")
                    .leftColor(Color.WHITE)
                    .rightColor(plugin.getConfig().enablePrePostRequirements() ? Color.GREEN : Color.GRAY)
                    .build());
                    
                // Show current dropdown selections if requirements are enabled
                if (plugin.getConfig().enablePrePostRequirements()) {
                    // Pre-schedule selections
                    if (!plugin.getConfig().preScheduleSpellbook().isNone()) {
                        panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                            .left("Pre-Spellbook:")
                            .right(plugin.getConfig().preScheduleSpellbook().getDisplayName())
                            .leftColor(Color.WHITE)
                            .rightColor(Color.CYAN)
                            .build());
                    }
                    
                    if (!plugin.getConfig().preScheduleLocation().equals(net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example.enums.UnifiedLocation.NONE)) {
                        panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                            .left("Pre-Location:")
                            .right(plugin.getConfig().preScheduleLocation().getDisplayName())
                            .leftColor(Color.WHITE)
                            .rightColor(Color.CYAN)
                            .build());
                    }
                    
                    // Post-schedule selections
                    if (!plugin.getConfig().postScheduleSpellbook().isNone()) {
                        panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                            .left("Post-Spellbook:")
                            .right(plugin.getConfig().postScheduleSpellbook().getDisplayName())
                            .leftColor(Color.WHITE)
                            .rightColor(Color.ORANGE)
                            .build());
                    }
                    
                    if (!plugin.getConfig().postScheduleLocation().equals(net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example.enums.UnifiedLocation.NONE)) {
                        panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                            .left("Post-Location:")
                            .right(plugin.getConfig().postScheduleLocation().getDisplayName())
                            .leftColor(Color.WHITE)
                            .rightColor(Color.ORANGE)
                            .build());
                    }
                }
                    
                // Show all keyboard hotkeys section
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("--- Keyboard Controls ---")
                    .right("")
                    .leftColor(Color.YELLOW)
                    .rightColor(Color.YELLOW)
                    .build());
                    
                // Area marking hotkey
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Mark Area:")
                    .right(formatKeybind(plugin.getConfig().areaMarkHotkey()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.CYAN)
                    .build());
                
                // Plugin finish hotkeys
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Finish Success:")
                    .right(formatKeybind(plugin.getConfig().finishPluginSuccessfulHotkey()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.GREEN)
                    .build());
                    
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Finish Fail:")
                    .right(formatKeybind(plugin.getConfig().finishPluginNotSuccessfulHotkey()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.RED)
                    .build());
                
                // Lock condition hotkey
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Toggle Lock:")
                    .right(formatKeybind(plugin.getConfig().lockConditionHotkey()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.MAGENTA)
                    .build());
                
                // Pre/Post schedule test hotkeys
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Test Pre:")
                    .right(formatKeybind(plugin.getConfig().testPreScheduleTasksHotkey()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());
                    
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Test Post:")
                    .right(formatKeybind(plugin.getConfig().testPostScheduleTasksHotkey()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());
            }
            
            // Show task execution state information if tasks are running or have been executed
            if (tasks != null) {
                TaskExecutionState executionState = tasks.getExecutionState();
                
                // Show current execution state
                if (executionState.isExecuting()) {
                    panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                        .left("Execution State:")
                        .right(formatTaskExecutionState(executionState))
                        .leftColor(Color.WHITE)
                        .rightColor(getTaskExecutionStateColor(executionState))
                        .build());
                        
                    // Show detailed status if available
                    String detailedStatus = executionState.getDetailedStatus();
                    if (detailedStatus != null && !detailedStatus.isEmpty()) {
                        panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                            .left("Progress:")
                            .right(detailedStatus.length() > 30 ? detailedStatus.substring(0, 27) + "..." : detailedStatus)
                            .leftColor(Color.WHITE)
                            .rightColor(Color.CYAN)
                            .build());
                    }
                    
                    // Show progress percentage if available
                    int progress = executionState.getProgressPercentage();
                    if (progress > 0) {
                        panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                            .left("Progress:")
                            .right(progress + "%")
                            .leftColor(Color.WHITE)
                            .rightColor(Color.YELLOW)
                            .build());
                    }
                } else if (executionState.isInErrorState()) {
                    // Show error state even when not actively executing
                    panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                        .left("Last State:")
                        .right("ERROR")
                        .leftColor(Color.WHITE)
                        .rightColor(Color.RED)
                        .build());
                }
                
                // Show running status
                if (tasks.isRunning()) {
                    panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                        .left("Tasks Status:")
                        .right("RUNNING")
                        .leftColor(Color.WHITE)
                        .rightColor(Color.YELLOW)
                        .build());
                }
            }
            
        } catch (Exception e) {
            // Show error in overlay
            panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder()
                .text("SchedulableExample - ERROR")
                .color(Color.RED)
                .build());
                
            panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                .left("Error:")
                .right(e.getMessage() != null ? e.getMessage() : "Unknown error")
                .leftColor(Color.WHITE)
                .rightColor(Color.RED)
                .build());
        }
        
        return super.render(graphics);
    }
    
    /**
     * Formats a keybind for display in the overlay
     */
    private String formatKeybind(net.runelite.client.config.Keybind keybind) {
        if (keybind == null || keybind == net.runelite.client.config.Keybind.NOT_SET) {
            return "Not Set";
        }
        
        StringBuilder result = new StringBuilder();
        
        // Add modifiers
        if ((keybind.getModifiers() & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) {
            result.append("Ctrl+");
        }
        if ((keybind.getModifiers() & java.awt.event.InputEvent.ALT_DOWN_MASK) != 0) {
            result.append("Alt+");
        }
        if ((keybind.getModifiers() & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0) {
            result.append("Shift+");
        }
        
        // Add main key
        String keyText = java.awt.event.KeyEvent.getKeyText(keybind.getKeyCode());
        result.append(keyText);
        
        return result.toString();
    }
    
    /**
     * Formats task execution state for display
     */
    private String formatTaskExecutionState(TaskExecutionState state) {
        if (state == null) {
            return "Unknown";
        }
        
        String displayStatus = state.getDisplayStatus();
        if (displayStatus != null) {
            return displayStatus;
        }
        
        if (state.isExecuting()) {
            return "Executing";
        }
        
        return "Idle";
    }
    
    /**
     * Gets appropriate color for task execution state
     */
    private Color getTaskExecutionStateColor(TaskExecutionState state) {
        if (state == null) {
            return Color.GRAY;
        }
        
        if (state.isInErrorState()) {
            return Color.RED;
        }
        
        if (state.isExecuting()) {
            return Color.YELLOW;
        }
        
        return Color.GREEN;
    }
}
