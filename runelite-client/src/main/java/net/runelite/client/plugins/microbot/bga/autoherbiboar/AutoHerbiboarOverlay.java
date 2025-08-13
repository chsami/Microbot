package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Singleton
public class AutoHerbiboarOverlay extends OverlayPanel
{
    private final AutoHerbiboarScript script;

    @Inject
    public AutoHerbiboarOverlay(AutoHerbiboarScript script)
    {
        this.script = script;

        setPosition(OverlayPosition.TOP_LEFT);  // top-left corner
        setPriority(OverlayPriority.HIGH);
        panelComponent.setPreferredSize(new Dimension(210, 0)); // keep it compact
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        panelComponent.getChildren().clear();

        // Header
        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text("Auto Herbiboar")
                        .color(Color.WHITE)
                        .build()
        );

        // Status display with yellow status text
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Status:")
                        .right(net.runelite.client.plugins.microbot.Microbot.status)
                        .rightColor(Color.YELLOW)
                        .build()
        );

        return super.render(g);
    }
}

