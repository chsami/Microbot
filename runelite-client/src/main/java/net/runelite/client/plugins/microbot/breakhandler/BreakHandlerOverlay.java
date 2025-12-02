package net.runelite.client.plugins.microbot.breakhandler;

import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
@Slf4j
public class BreakHandlerOverlay extends OverlayPanel {
    private final BreakHandlerConfig config;

    @Inject
    BreakHandlerOverlay(BreakHandlerPlugin plugin, BreakHandlerConfig config)
    {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPreferredSize(new Dimension(ComponentConstants.STANDARD_WIDTH, 180));
        setNaughty();
        setDragTargetable(true);
        setLayer(OverlayLayer.UNDER_WIDGETS);        
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(180, 280));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("BreakHandler V" + BreakHandlerScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total breaks: " + BreakHandlerScript.totalBreaks)
                    .build());

            // Display current state information
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State: " + BreakHandlerScript.getCurrentState().toString().replace("_", " "))
                    .leftColor(getStateColor(BreakHandlerScript.getCurrentState()))
                    .build());

            // Display lock state information
            if (BreakHandlerScript.isLockState()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status: LOCKED")
                        .right("Breaks Prevented")
                        .leftColor(Color.RED)
                        .rightColor(Color.RED)
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Reason: Plugin Lock Condition Active")
                        .leftColor(Color.ORANGE)
                        .build());
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status: UNLOCKED")
                        .right("Breaks Allowed")
                        .leftColor(Color.GREEN)
                        .rightColor(Color.GREEN)
                        .build());
            }

            if (BreakHandlerScript.breakIn > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left((Rs2AntibanSettings.takeMicroBreaks && config.onlyMicroBreaks()) ? "Only Micro Breaks" : BreakHandlerScript.formatDuration(Duration.ofSeconds(BreakHandlerScript.breakIn), "Break in:"))
                        .build());
            }
            if (BreakHandlerScript.breakDuration > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(BreakHandlerScript.formatDuration(Duration.ofSeconds(BreakHandlerScript.breakDuration), "Break duration:"))
                        .build());
            }
            
            // Display extended sleep countdown when in LOGIN_EXTENDED_SLEEP state
            if (BreakHandlerScript.getCurrentState() == BreakHandlerState.LOGIN_EXTENDED_SLEEP && 
                BreakHandlerScript.getExtendedSleepStartTime() != null) {
                
                long elapsedMinutes = Duration.between(BreakHandlerScript.getExtendedSleepStartTime(), Instant.now()).toMinutes();
                long remainingMinutes = Math.max(0, config.extendedSleepDuration() - elapsedMinutes);
                Duration remainingDuration = Duration.ofMinutes(remainingMinutes);
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Extended sleep: " + BreakHandlerScript.formatDuration(remainingDuration))
                        .leftColor(Color.BLUE)
                        .build());
            }

        } catch(Exception ex) {            
            log.warn("BreakHandler overlay render error", ex);
        }
        return super.render(graphics);
    }

    /**
     * Returns appropriate color for the current break handler state.
     */
    private Color getStateColor(BreakHandlerState state) {
        switch (state) {
            case WAITING_FOR_BREAK:
                return Color.GREEN;
            case BREAK_REQUESTED:
                return Color.YELLOW;
            case INITIATING_BREAK:
                return Color.ORANGE;
            case LOGOUT_REQUESTED:
            case LOGGING_IN:
                return Color.CYAN;
            case LOGGED_OUT:
            case INGAME_BREAK_ACTIVE:
                return Color.RED;
            case LOGIN_REQUESTED:
                return Color.MAGENTA;
            case LOGIN_EXTENDED_SLEEP:
                return Color.BLUE;
            case BREAK_ENDING:
                return Color.LIGHT_GRAY;
            default:
                return Color.WHITE;
        }
    }
}
