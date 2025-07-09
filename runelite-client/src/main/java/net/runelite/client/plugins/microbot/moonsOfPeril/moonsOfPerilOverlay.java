package net.runelite.client.plugins.microbot.moonsOfPeril;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.moonsOfPeril.handlers.RewardHandler;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.time.Duration;

public class moonsOfPerilOverlay extends OverlayPanel {
    private Instant startTime;

    @Inject
    moonsOfPerilOverlay(moonsOfPerilPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setSnappable(true);
        setNaughty();
    }

    @Inject
    private RewardHandler rewardHandler;

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 900));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83C\uDF19 Moons Of Peril V1.0.0 \uD83C\uDF19")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State: " + moonsOfPerilScript.CURRENT_STATE)
                    .build());

            // Runtime section
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Session Time:")
                    .right(formatDuration(Duration.between(moonsOfPerilPlugin.scriptStartTime, Instant.now())))
                    .rightColor(new Color(255, 215, 0)) // Gold
                    .build());

            // Rewards Chest section
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Rewards opened:")
                    .right(String.valueOf(rewardHandler.getRewardChestCount()))
                    .rightColor(new Color(66, 245, 84)) // Green //
                    .build());

        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
