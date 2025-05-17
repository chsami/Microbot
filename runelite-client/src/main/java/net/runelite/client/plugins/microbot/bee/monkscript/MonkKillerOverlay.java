package net.runelite.client.plugins.microbot.bee.monkscript;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class MonkKillerOverlay extends OverlayPanel {

    private final MonkKillerPlugin plugin;

    @Inject
    public MonkKillerOverlay(MonkKillerPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));

            // Add a title to the overlay
            panelComponent.getChildren().add(
                    TitleComponent.builder()
                            .text("Monk Killer v1.2.5")
                            .color(Color.GREEN)
                            .build()
            );

            // Add a line with the current status of the bot
            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left("Status:")
                            .right(Microbot.status)
                            .build()
            );

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return super.render(graphics);
    }
}
