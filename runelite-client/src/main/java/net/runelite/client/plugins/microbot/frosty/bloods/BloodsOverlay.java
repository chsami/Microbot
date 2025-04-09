package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class BloodsOverlay extends OverlayPanel {
    private final BloodsPlugin plugin;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    BloodsOverlay(BloodsPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(200, 275));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("True Blood Rc v1.0.0").color((Color.red)).build());
            //panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder().left("Xp gained:")
                    .right(String.valueOf(plugin.getTotalXpGained())).build());
            /*panelComponent.getChildren().add(LineComponent.builder().left("Bloods crafted: ")
                    .right(String.valueOf(plugin.getBloodRunesCrafted())).build());*/
            panelComponent.getChildren().add(LineComponent.builder().left("Time ran:")
                    .right(TimeUtils.getFormattedDurationBetween(plugin.getStartTime(),
                            Instant.now())).build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:").right(Microbot.status).build());
        } catch (
                Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}


