package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

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
    @Inject
    private ItemManager itemManager;

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();

            panelComponent.setPreferredSize(new Dimension(200, 275));

            //BufferedImage bloodRuneImage = itemManager.getImage(ItemID.BLOODRUNE);
            //panelComponent.getChildren().add(new ImageComponent(bloodRuneImage));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("❄️ True Blood: " + BloodsPlugin.getVersion()).color((Color.red)).build());

            panelComponent.getChildren().add(LineComponent.builder().left("Xp gained:")
                    .right(String.valueOf(plugin.getTotalXpGained())).build());

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


