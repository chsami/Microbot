package net.runelite.client.plugins.microbot.fishing.aerial;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AerialFishingOverlay extends OverlayPanel {
    @Inject
    AerialFishingOverlay(AerialFishingPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(250, 400));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 Aerial Fisher \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());
            // check if player is in the correct region(10038)
            String region = Rs2Player.getWorldLocation() != null ? Rs2Player.getWorldLocation().getRegionID() == 5432 ? "In Region" : "Not in Region" : "Not in Region";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Region: " + region)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Cormorant's glove: " + ((Rs2Equipment.isWearing(ItemID.CORMORANTS_GLOVE) || Rs2Equipment.isWearing(ItemID.CORMORANTS_GLOVE_22817)) ? "Present" : "Not Present"))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Bait: " + (Rs2Inventory.hasItem("fish chunks") ? String.valueOf(Rs2Inventory.get("fish chunks").getQuantity()) : "Not Present"))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());
            if(Rs2AntibanSettings.devDebug)
                Rs2Antiban.renderAntibanOverlayComponents(panelComponent);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .right("Version:" + AerialFishingScript.version)
                    .build());


        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
