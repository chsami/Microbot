package net.runelite.client.plugins.microbot.runecrafting.arceuus;


import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ArceuusRcOverlay extends OverlayPanel {

    private final ArceuusRcPlugin plugin;

    @Inject
    ArceuusRcOverlay(ArceuusRcPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 Arceuus Runecrafting \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status: " + Microbot.status).right("Version: " + ArceuusRcScript.version)
                    .build());

            if (plugin.getArceuusRcScript() != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("State: " + plugin.getArceuusRcScript().getState()).build()
                );
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Dense/Dark/Frags: " + getDenseDarkFragCountString()).build()
                );
            }
        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private String getDenseDarkFragCountString() {
        return Rs2Inventory.count(ArceuusRcScript.DENSE_ESSENCE_BLOCK) + "/" +
                Rs2Inventory.count(ArceuusRcScript.DARK_ESSENCE_BLOCK) + "/" +
                Rs2Inventory.itemQuantity(ArceuusRcScript.DARK_ESSENCE_FRAGMENTS);

    }
}
