package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.*;

/**
 * Displays information about location-based start conditions
 */
public class LocationStartNotificationOverlay extends Overlay {
    private final SchedulableExamplePlugin plugin;
    private final SchedulableExampleConfig config;
    private final PanelComponent panelComponent = new PanelComponent();
    
    public LocationStartNotificationOverlay(SchedulableExamplePlugin plugin, SchedulableExampleConfig config) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.MED);
        this.plugin = plugin;
        this.config = config;
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!Microbot.isLoggedIn() || !config.enableLocationStartCondition()) {
            return null;
        }
        
        panelComponent.getChildren().clear();
        
        // Show title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Location Conditions")
            .color(Color.WHITE)
            .build());
        
        if (config.locationStartType() == SchedulableExampleConfig.LocationStartType.BANK) {
            // Bank location information
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Type:")
                .right("Bank Location")
                .build());
                
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Target:")
                .right(config.bankStartLocation().name())
                .build());
                
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Distance:")
                .right(config.bankDistance() + " tiles")
                .build());
                
            // Check and show if condition is met
            boolean inRange = isNearBank();
            Color statusColor = inRange ? Color.GREEN : Color.RED;
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(inRange ? "In Range" : "Out of Range")
                .rightColor(statusColor)
                .build());
                
        } else if (config.locationStartType() == SchedulableExampleConfig.LocationStartType.CUSTOM_AREA) {
            // Custom area information
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Type:")
                .right("Custom Area")
                .build());
                
            if (config.customAreaActive() && config.customAreaCenter() != null) {
                WorldPoint center = config.customAreaCenter();
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Center:")
                    .right(center.getX() + ", " + center.getY() + ", " + center.getPlane())
                    .build());
                    
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Radius:")
                    .right(config.customAreaRadius() + " tiles")
                    .build());
                    
                // Check and show if condition is met
                boolean inArea = plugin.isPlayerInCustomArea();
                Color statusColor = inArea ? Color.GREEN : Color.RED;
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(inArea ? "In Area" : "Out of Area")
                    .rightColor(statusColor)
                    .build());
                    
                // Show distance to center if not in area
                if (!inArea) {
                    
                    WorldPoint playerPos = Rs2Player.getWorldLocation();
                    if (playerPos != null) {
                        int distance = playerPos.distanceTo(center);
                        panelComponent.getChildren().add(LineComponent.builder()
                            .left("Distance:")
                            .right(distance + " tiles away")
                            .build());
                    }
                }
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right("No Area Defined")
                    .rightColor(Color.YELLOW)
                    .build());
                    
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Help:")
                    .right("Press hotkey to mark area")
                    .build());
            }
        }
        
        return panelComponent.render(graphics);
    }
    
    /**
     * Checks if the player is near the configured bank
     */
    private boolean isNearBank() {
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        if (playerPos == null) {
            return false;
        }
        
        WorldPoint bankPos = config.bankStartLocation().getWorldPoint();
        int maxDistance = config.bankDistance();
        
        return (playerPos.getPlane() == bankPos.getPlane() && 
                playerPos.distanceTo(bankPos) <= maxDistance);
    }
}