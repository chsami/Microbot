package net.runelite.client.plugins.microbot.autoworldhopper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autoworldhopper.scripts.WorldHopScript;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoWorldHopperInfoOverlay extends OverlayPanel {
    private final AutoWorldHopperConfig config;
    private final AutoWorldHopperPlugin plugin;

    @Inject
    AutoWorldHopperInfoOverlay(AutoWorldHopperPlugin plugin, AutoWorldHopperConfig config) {
        super(plugin);
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            if (!config.enabled()) {
                return null;
            }

            WorldHopScript script = plugin.getWorldHopScript();
            if (script == null) {
                return null;
            }

            panelComponent.setPreferredSize(new Dimension(200, 120));
            
            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Auto World Hopper v" + AutoWorldHopperPlugin.version)
                    .color(Color.CYAN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Status
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(script.isRunning() ? (script.isPaused() ? "Paused" : "Running") : "Stopped")
                    .rightColor(script.isRunning() ? (script.isPaused() ? Color.YELLOW : Color.GREEN) : Color.RED)
                    .build());

            // Startup delay status
            if (script.isRunning() && !script.isPaused()) {
                int remainingDelay = script.getRemainingStartupDelay();
                if (remainingDelay > 0) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Startup Delay:")
                            .right(remainingDelay + "s remaining")
                            .rightColor(Color.ORANGE)
                            .build());
                }
            }

            // Current world
            if (Microbot.getClient().getLocalPlayer() != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current World:")
                        .right(String.valueOf(Microbot.getClient().getWorld()))
                        .build());
            }

            // Time until next hop (if time-based hopping is enabled)
            if (config.enableTimeHopping() && script.isRunning() && !script.isPaused()) {
                long nextHopTime = script.getNextTimeHop();
                if (nextHopTime > 0) {
                    long timeLeft = Math.max(0, nextHopTime - System.currentTimeMillis());
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft);
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60;
                    
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Next time hop:")
                            .right(String.format("%d:%02d", minutes, seconds))
                            .build());
                }
            }

            // Players nearby (if player detection is enabled)
            if (config.enablePlayerDetection()) {
                int playersNearby = script.getPlayersNearby();
                int max = config.maxPlayers();
                Color playerColor = (max == 0 ? playersNearby > 0 : playersNearby >= max)
                        ? Color.RED : Color.WHITE;
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Players nearby:")
                        .right(playersNearby + "/" + max)
                        .rightColor(playerColor)
                        .build());
            }

            // Debug information
            if (config.debugMode()) {
                panelComponent.getChildren().add(LineComponent.builder().build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Last hop reason:")
                        .right(script.getLastHopReason())
                        .build());

                long lastHopTime = script.getLastHopTime();
                if (lastHopTime > 0) {
                    long timeSinceHop = System.currentTimeMillis() - lastHopTime;
                    long secondsAgo = TimeUnit.MILLISECONDS.toSeconds(timeSinceHop);
                    
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Last hop:")
                            .right(secondsAgo + "s ago")
                            .build());
                }
            }

        } catch (Exception ex) {
            log.error("Error rendering overlay", ex);
        }

        return super.render(graphics);
    }
}
