package net.runelite.client.plugins.microbot.maxxin.astralrc;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AstralRunesOverlay extends OverlayPanel {
    private final AstralRunesConfig config;

    @Inject
    AstralRunesOverlay(AstralRunesPlugin plugin, AstralRunesConfig config) {
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
                    .text("Starter " + AstralRunesScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());
            if( getPlugin() instanceof AstralRunesPlugin ) {
                var plugin = (AstralRunesPlugin) getPlugin();
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total Trips: " + AstralRunesScript.totalTrips)
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total Runes: " + AstralRunesScript.runesForSession)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("State: " + plugin.getDebugText2())
                        .build());
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
