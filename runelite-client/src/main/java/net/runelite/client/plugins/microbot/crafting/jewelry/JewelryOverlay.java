package net.runelite.client.plugins.microbot.crafting.jewelry;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class JewelryOverlay extends OverlayPanel {
    JewelryPlugin _plugin;

    @Inject
    JewelryOverlay(JewelryPlugin plugin) {
        super(plugin);
        this._plugin = plugin;
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(100, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("[ Jewelry Crafter ]")
                    .color(Color.ORANGE)
                    .build());

            addLine("");

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Script Version: ")
                    .right(JewelryPlugin.version)
                    .build());

            addLine("");

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Run Time: ")
                    .right(_plugin.getTimeRunning())
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
