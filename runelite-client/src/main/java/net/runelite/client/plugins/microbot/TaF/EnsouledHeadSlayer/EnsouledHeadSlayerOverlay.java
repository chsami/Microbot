package net.runelite.client.plugins.microbot.TaF.EnsouledHeadSlayer;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class EnsouledHeadSlayerOverlay extends OverlayPanel {
    private final EnsouledHeadSlayerPlugin plugin;

    @Inject
    EnsouledHeadSlayerOverlay(EnsouledHeadSlayerPlugin plugin) {
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

            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Ensouled Slayer by TaF - v" + EnsouledHeadSlayerScript.VERSION)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Time running
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Running:")
                    .right(plugin.getTimeRunning())
                    .leftColor(Color.WHITE)
                    .rightColor(Color.WHITE)
                    .build());

            // Prayer level gained
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer Level:")
                    .right(plugin.getLevelGained())
                    .leftColor(Color.ORANGE)
                    .rightColor(Color.ORANGE)
                    .build());

            // XP gained
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained:")
                    .right(plugin.getTotalXpGained())
                    .leftColor(Color.YELLOW)
                    .rightColor(Color.YELLOW)
                    .build());

            // XP per hour
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/hr:")
                    .right(plugin.getXpAnHour())
                    .leftColor(Color.CYAN)
                    .rightColor(Color.CYAN)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}