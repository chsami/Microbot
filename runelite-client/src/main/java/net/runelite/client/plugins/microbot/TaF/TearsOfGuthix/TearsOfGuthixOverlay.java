package net.runelite.client.plugins.microbot.TaF.TearsOfGuthix;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class TearsOfGuthixOverlay extends OverlayPanel {
    private final TearsOfGuthixPlugin plugin;

    @Inject
    TearsOfGuthixOverlay(TearsOfGuthixPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }


    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("TaF's Tears of Guthix script - v" + TearsOfGuthixScript.VERSION)
                    .color(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status: " + TearsOfGuthixScript.BOT_STATUS)
                    .leftColor(Color.WHITE)
                    .build());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
