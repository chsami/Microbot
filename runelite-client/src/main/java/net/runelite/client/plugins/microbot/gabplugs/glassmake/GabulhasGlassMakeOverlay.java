package net.runelite.client.plugins.microbot.gabplugs.glassmake;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class GabulhasGlassMakeOverlay extends OverlayPanel {
    @Inject
    GabulhasGlassMakeOverlay(GabulhasGlassMakePlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Status: " + GabulhasGlassMakeInfo.botStatus.toString().replace("_", " "))
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Version: " + GabulhasGlassMakeScript.version)
                    .build());


        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
