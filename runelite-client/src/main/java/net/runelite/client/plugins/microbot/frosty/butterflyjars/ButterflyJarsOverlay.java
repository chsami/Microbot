package net.runelite.client.plugins.microbot.frosty.butterflyjars;

import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import javax.inject.Inject;
import java.awt.*;

public class ButterflyJarsOverlay extends OverlayPanel {
    private final ButterflyJarsPlugin plugin;

    @Inject
    public ButterflyJarsOverlay(ButterflyJarsPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.isRunning()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Butterfly Jars: Running")
                    .build());
        } else {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Butterfly Jars: Stopped")
                    .build());
        }
        return super.render(graphics);
    }
}
