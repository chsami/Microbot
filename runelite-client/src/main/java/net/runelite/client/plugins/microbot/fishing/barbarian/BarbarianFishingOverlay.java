package net.runelite.client.plugins.microbot.fishing.barbarian;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class BarbarianFishingOverlay extends OverlayPanel {
    @Inject
    BarbarianFishingOverlay(BarbarianFishingPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(250, 400));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 Barbarian Fisher \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());
            // check if player is in the correct region(10038)
            String region = Rs2Player.getWorldLocation() != null ? Rs2Player.getWorldLocation().getRegionID() == 10038 ? "In Region" : "Not in Region" : "Not in Region";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Region: " + region)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Barbarian rod: " + (Rs2Inventory.hasItem("Barbarian rod") ? "Present" : "Not Present"))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Feathers: " + (Rs2Inventory.hasItem("feather") ? String.valueOf(Rs2Inventory.get("feather").quantity) : "Not Present"))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());
            Rs2Antiban.renderAntibanOverlayComponents(panelComponent);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .right("Version:" + BarbarianFishingScript.version)
                    .build());


        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}