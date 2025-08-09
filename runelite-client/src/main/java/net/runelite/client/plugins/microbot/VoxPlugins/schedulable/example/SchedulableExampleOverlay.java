package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;

import javax.inject.Inject;
import java.awt.*;

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
        setNaughty();
        setDragTargetable(true);
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        // Clear previous components
        panelComponent.getChildren().clear();
        
        // Get the task manager and requirements
        SchedulableExamplePrePostScheduleTasks tasks = (SchedulableExamplePrePostScheduleTasks)plugin.getPrePostScheduleTasks();
        
        // Only show overlay if pre/post requirements are enabled or tasks are running
        if (!plugin.getConfig().enablePrePostRequirements() && 
            (tasks == null || !tasks.isExecuting())) {
            return null; // Don't show overlay when not needed
        }
        
        try {
            // Show concise information only
            boolean isExecuting = tasks != null && tasks.isExecuting();
            boolean hasPrePostRequirements = plugin.getConfig().enablePrePostRequirements();
            
            // Main title with status indication
            String titleText = "SchedulableExample";
            Color titleColor = Color.CYAN;
            
            if (isExecuting) {
                TaskExecutionState executionState = tasks.getExecutionState();
                if (executionState.isInErrorState()) {
                    titleText += " (ERROR)";
                    titleColor = Color.RED;
                } else {
                    titleText += " (ACTIVE)";
                    titleColor = Color.YELLOW;
                }
            } else if (hasPrePostRequirements) {
                titleText += " (READY)";
                titleColor = Color.CYAN;
            }
            
            panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder()
                .text(titleText)
                .color(titleColor)
                .build());
            
            // Show current status
            if (isExecuting) {
                TaskExecutionState executionState = tasks.getExecutionState();
                String phase = executionState.getCurrentPhase() != null ? 
                    executionState.getCurrentPhase().toString() : "EXECUTING";
                int progress = executionState.getProgressPercentage();
                String statusText = progress > 0 ? phase + " (" + progress + "%)" : phase;
                
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Phase:")
                    .right(statusText)
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());
                    
                // Show detailed status if available and short enough
                String detailedStatus = executionState.getDetailedStatus();
                if (detailedStatus != null && !detailedStatus.isEmpty() && detailedStatus.length() <= 25) {
                    panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                        .left("Status:")
                        .right(detailedStatus)
                        .leftColor(Color.WHITE)
                        .rightColor(Color.CYAN)
                        .build());
                }
            } else {
                // Show requirements status when not executing
                String requirementsText = hasPrePostRequirements ? "ENABLED" : "DISABLED";
                Color requirementsColor = hasPrePostRequirements ? Color.GREEN : Color.GRAY;
                
                panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                    .left("Pre/Post:")
                    .right(requirementsText)
                    .leftColor(Color.WHITE)
                    .rightColor(requirementsColor)
                    .build());
            }
            
            // Show essential controls hint
            panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
                .left("Hotkeys:")
                .right("See config")
                .leftColor(Color.WHITE)
                .rightColor(Color.LIGHT_GRAY)
                .build());
            
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
}
