package net.runelite.client.plugins.microbot.TaF.RoyalTitans;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class RoyalTitansOverlay extends OverlayPanel {
    private final RoyalTitansPlugin plugin;
    private final RoyalTitansConfig config;

    @Inject
    public RoyalTitansOverlay(RoyalTitansPlugin plugin, RoyalTitansConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("TaF's Royal Titan Plugin v" + RoyalTitansScript.version)
                    .color(Color.decode("#a4ffff"))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Running: " + plugin.getTimeRunning())
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:" + plugin.royalTitansScript.state.name())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Substate: " + plugin.royalTitansScript.subState)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Duo partner: " + config.teammateName())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills: " + plugin.royalTitansScript.kills)
                    .build());
            if (plugin.royalTitansScript.enrageTile != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Enrage tile: " + plugin.royalTitansScript.enrageTile.getWorldLocation())
                        .build());
            }


        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
