package net.runelite.client.plugins.microbot.crafting;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.crafting.scripts.ICraftingScript;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.Map;

public class CraftingOverlay extends OverlayPanel {
    private final CraftingPlugin plugin;

    @Inject
    CraftingOverlay(CraftingPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }


    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            ICraftingScript script = plugin.currentScript;

            if (script == null) {
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("Microbot Crafting")
                        .color(Color.GREEN)
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("No script running.")
                        .build());
                return super.render(graphics);
            }

            panelComponent.setPreferredSize(new Dimension(275, 0));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro " + script.getName() + " v" + script.getVersion())
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(script.getState())
                    .build());

            Map<String, String> customProperties = script.getCustomProperties();
            if (customProperties != null) {
                for (Map.Entry<String, String> prop : customProperties.entrySet()) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left(prop.getKey())
                            .right(prop.getValue())
                            .build());
                }
            }

        } catch (Exception ex) {
            System.out.println("Error in Crafting Overlay: " + ex.getMessage());
        }
        return super.render(graphics);
    }
}

