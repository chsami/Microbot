package net.runelite.client.plugins.microbot.wildyrunite;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.NumberFormat;

public class WildernessRuniteMiningOverlay extends OverlayPanel {

    private final WildernessRuniteMiningPlugin plugin;

    @Inject
    WildernessRuniteMiningOverlay(WildernessRuniteMiningPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 150));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Wilderness Runite Miner")
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(Microbot.status)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Ore mined:")
                .right(plugin.getScript().getTotalMined() + "")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("In inventory:")
                .right(Rs2Inventory.count("Runite ore") + "")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit:")
                .right(NumberFormat.getIntegerInstance().format(
                        plugin.getScript().getTotalMined() * plugin.getScript().getOrePrice()
                ) + " gp")
                .build());


        if (plugin.getScript().getRecentAttackers() != null && !plugin.getScript().getRecentAttackers().isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Attackers:")
                    .build());

            plugin.getScript().getRecentAttackers().forEach(name ->
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("- " + name)
                            .build()));
        }

        return super.render(graphics);
    }
}
