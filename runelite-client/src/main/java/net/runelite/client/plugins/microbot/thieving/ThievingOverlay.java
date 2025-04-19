package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;


public class ThievingOverlay extends OverlayPanel {
    @Inject
    ThievingOverlay(ThievingPlugin plugin)
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
                    .text("Micro Thieving V" + ThievingScript.version)
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
