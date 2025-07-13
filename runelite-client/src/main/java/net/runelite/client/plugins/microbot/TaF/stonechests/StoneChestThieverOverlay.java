package net.runelite.client.plugins.microbot.TaF.stonechests;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class StoneChestThieverOverlay extends OverlayPanel {
    private final StoneChestThieverPlugin plugin;

    @Inject
    StoneChestThieverOverlay(StoneChestThieverPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.setBackgroundColor(new Color(32, 32, 32, 220));

            // Title section
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Stone Chest Thiever by TaF")
                    .color(new Color(255, 215, 0)) // Gold color
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Status section
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status)
                    .rightColor(ColorScheme.PROGRESS_INPROGRESS_COLOR)
                    .build());

            // Stats section
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time running:")
                    .right(plugin.getTimeRunning())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Chests opened:")
                    .right(String.valueOf(plugin.chestOpenend))
                    .build());

            Duration runTime = Duration.between(plugin.scriptStartTime, Instant.now());
            if (runTime.getSeconds() > 0) {
                double hoursElapsed = runTime.toMillis() / (1000.0 * 60.0 * 60.0);
                int chestsPerHour = (int) (plugin.chestOpenend / hoursElapsed);

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Chests/hr:")
                        .right(String.valueOf(chestsPerHour))
                        .build());
            }

            // Inventory section
            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Inventory")
                    .color(new Color(173, 216, 230)) // Light blue
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Free slots:")
                    .right(28 - (int) Rs2Inventory.items().count() + "/28")
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}