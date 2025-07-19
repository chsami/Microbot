package net.runelite.client.plugins.microbot.thieving;

import java.time.Duration;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

public class ThievingOverlay extends OverlayPanel {
    private final ThievingPlugin plugin;

    @Inject
    ThievingOverlay(ThievingPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 160));

            panelComponent.getChildren().add(
                    TitleComponent.builder()
                            .text("Micro Thieving V" + plugin.version)
                            .color(Color.ORANGE)
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("XP:")
                            .right(String.valueOf(plugin.xpGained()))
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("STATE:")
                            .right(plugin.getState())
                            .build()
            );

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("RUNTIME:")
                            .right(getFormattedDuration(plugin.getRunTime()))
                            .build()
            );
        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

	private String getFormattedDuration(Duration duration)
	{
		long hours = duration.toHours();
		long minutes = duration.toMinutes() % 60;
		long seconds = duration.getSeconds() % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}
}