package net.runelite.client.plugins.microbot.construction;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ConstructionOverlay extends OverlayPanel {
    private final ConstructionPlugin plugin;

    @Inject
    ConstructionOverlay(ConstructionPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            Color lardersColor = plugin.constructionScript.lardersBuilt == 0
                    ? Color.RED
                    : Color.GREEN;

            Color stateColor = plugin.constructionScript.state == ConstructionState.Stopped
                    ? Color.RED
                    : Color.GREEN;

            panelComponent.setPreferredSize(new Dimension(300, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Construction v" + ConstructionScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Microbot Status:")
                    .right(Microbot.status)
                    .rightColor(new Color(255, 215, 0)) // Gold
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Plugin State:")
                    .right(plugin.constructionScript.state.toString())
                    .rightColor(stateColor)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Oak Larders Made:")
                    .right(plugin.constructionScript.lardersBuilt.toString())
                    .rightColor(lardersColor)
                    .build());

        } catch(Exception ex) {
            System.out.println("Exception occurred rendering the Construction plugin overlay");
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
