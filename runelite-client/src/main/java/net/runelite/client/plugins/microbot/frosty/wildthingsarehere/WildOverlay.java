package net.runelite.client.plugins.microbot.frosty.wildthingsarehere;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import javax.inject.Inject;
import java.awt.*;

public class WildOverlay extends Overlay {
    private final WildScript script;

    @Inject
    public WildOverlay(WildScript script) {
        this.script = script;
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        graphics.setColor(Color.RED);
        graphics.drawString("Current State: " + script.getCurrentState(), 10, 10);
        return null;
    }
}
