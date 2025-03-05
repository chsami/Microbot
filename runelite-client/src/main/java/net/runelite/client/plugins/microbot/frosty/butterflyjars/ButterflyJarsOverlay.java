package net.runelite.client.plugins.microbot.frosty.butterflyjars;

import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.plugins.microbot.Script;

import javax.inject.Inject;
import java.awt.*;

public class ButterflyJarsOverlay extends OverlayPanel {
    private final ButterflyJarsPlugin plugin;
    private ButterflyJarsScript script;

    @Inject
    public ButterflyJarsOverlay(ButterflyJarsPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        this.script = script;

        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        if (plugin.isRunning()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Butterfly Jars: Running")
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Jars bought" + script.getJarsBought())
                    .build());
        } else {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Butterfly Jars: Stopped")
                    .build());
        }
        return super.render(graphics);
    }
}
