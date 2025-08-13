package net.runelite.client.plugins.microbot.bga.autochompykiller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AutoChompyKillerOverlay extends OverlayPanel {
    @Inject
    AutoChompyKillerOverlay(AutoChompyKillerPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Auto Chompy Killer " + AutoChompyKillerScript.version)
                    .color(Color.GREEN)
                    .build());

            long elapsed = System.currentTimeMillis() - AutoChompyKillerScript.startTime;
            double hours = elapsed / 3600000.0;
            double killsPerHour = AutoChompyKillerScript.chompyKills / hours;

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text(String.format("Chompy Kills: " + AutoChompyKillerScript.chompyKills + " [%.1f kph]", killsPerHour))
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return super.render(graphics);
    }
}