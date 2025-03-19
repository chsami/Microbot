package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class HerbrunOverlay extends OverlayPanel {

    @Inject
    HerbrunOverlay(HerbrunPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(HerbrunPlugin.status)
                    .build());


        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
