package net.runelite.client.plugins.microbot.breakhandler;

import net.runelite.client.plugins.microbot.pluginscheduler.util.SchedulerPluginUtil;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

public class BreakHandlerOverlay extends OverlayPanel {
    private final BreakHandlerConfig config;

    @Inject
    BreakHandlerOverlay(BreakHandlerPlugin plugin, BreakHandlerConfig config)
    {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("BreakHandler V" + BreakHandlerScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total breaks: " + BreakHandlerScript.totalBreaks)
                    .build());

            // Display lock state information
            if (BreakHandlerScript.isLockState()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status: LOCKED")
                        .right("Breaks Prevented")
                        .leftColor(Color.RED)
                        .rightColor(Color.RED)
                        .build());
                
                // Show specific lock reason if it's manual lock vs plugin lock
                if (BreakHandlerScript.lockState.get() && !SchedulerPluginUtil.hasLockedSchedulablePlugins()) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Reason: Manual Lock")
                            .leftColor(Color.ORANGE)
                            .build());
                } else {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Reason: Plugin Lock Condition Active")
                            .leftColor(Color.ORANGE)
                            .build());
                }
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

        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
