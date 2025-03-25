package net.runelite.client.plugins.microbot.crafting;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

public class CraftingOverlay extends OverlayPanel {

    public long startTime = 0;

    @Inject
    CraftingOverlay(CraftingPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(150, 400));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("[ Micro Crafter ]")
                    .color(Color.ORANGE)
                    .build());

            addLine("");

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Script Version: ")
                    .right(CraftingPlugin.version)
                    .build());

            addLine("");

            long s = Duration.ofMillis(System.currentTimeMillis() - startTime).toSeconds();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Run Time: ")
                    .right(
                            String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60)))
                    .build());

            addLine("");

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status: ")
                    .right(Microbot.status)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private void addLine(final String left) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).build());
    }
}
