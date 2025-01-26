package net.runelite.client.plugins.microbot.bee.MossKiller;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

public class MossKillerOverlay extends OverlayPanel {

    private final MossKillerPlugin plugin;

    @Inject
    MossKillerOverlay(MossKillerPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            // Set the overlay size and title
            panelComponent.setPreferredSize(new Dimension(100, 200));
            // Add the death counter
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Deaths:")
                    .right(String.valueOf(plugin.getDeathCounter())) // Access death counter from plugin
                    .leftFont(new Font("Arial", Font.BOLD, 13))
                    .rightFont(new Font("Arial", Font.BOLD, 13))
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}