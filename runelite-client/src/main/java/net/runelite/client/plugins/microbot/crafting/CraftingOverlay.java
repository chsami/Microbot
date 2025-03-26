package net.runelite.client.plugins.microbot.crafting;

import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;

import java.awt.*;
import java.time.Duration;

public class CraftingOverlay extends OverlayPanel {

    CraftingPlugin _plugin;

    @Inject
    CraftingOverlay(CraftingPlugin plugin) {
        super(plugin);
        this._plugin = plugin;
        setPreferredPosition(OverlayPosition.BOTTOM_LEFT);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(175, 400));
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
