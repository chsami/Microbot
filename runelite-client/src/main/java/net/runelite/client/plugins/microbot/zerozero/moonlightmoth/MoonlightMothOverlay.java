package net.runelite.client.plugins.microbot.zerozero.moonlightmoth;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.TaF.GemCrabKiller.GemCrabKillerPlugin;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;

public class MoonlightMothOverlay extends OverlayPanel {

    public final MoonlightMothPlugin plugin;
    private final ImageComponent imageComponent;

    @Inject
    public MoonlightMothOverlay(MoonlightMothPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        imageComponent = new ImageComponent(getImageFromResources());
        this.plugin = plugin;
    }

    private BufferedImage getImageFromResources() {
        try {
            var img = ImageUtil.loadImageResource(GemCrabKillerPlugin.class, "/net/runelite/client/plugins/microbot/MoonlightMoth/Moonlight_moth.png");
            return ImageUtil.resizeImage(img, 24, 24, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            final LineComponent title = LineComponent.builder()
                    .left("           00 Moonlight Moth")
                    .leftColor(Color.RED)
                    .build();
            final SplitComponent iconTitleSplit = SplitComponent.builder()
                    .first(imageComponent)
                    .second(title)
                    .orientation(ComponentOrientation.HORIZONTAL)
                    .gap(new Point(2, 0))
                    .build();
            panelComponent.getChildren().add(iconTitleSplit);
            // Script running time
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(Color.GREEN)
                    .build());
            // State information
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(Microbot.status)
                    .rightColor(Color.GREEN)
                    .build());
            // Rate calculations
            long runtimeMs = Instant.now().toEpochMilli() - plugin.scriptStartTime.toEpochMilli();
            double hoursElapsed = runtimeMs / (1000.0 * 60.0 * 60.0); // Convert ms to hours

            // Avoid division by zero
            int caughtPerHour = hoursElapsed > 0 ?
                    (int) (plugin.script.totalCaught / hoursElapsed) : 0;
            int profitPerHour = hoursElapsed > 0 ?
                    (int) ((plugin.script.totalCaught * plugin.script.pricePerMoth) / hoursElapsed) : 0;

            // Total caught moths
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total moths caught:")
                    .right(plugin.script.totalCaught + " (" + caughtPerHour + "/hr)")
                    .rightColor(Color.YELLOW)
                    .build());
            // Total profit
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total profit:")
                    .right(String.format("%,d", plugin.script.totalCaught * plugin.script.pricePerMoth) + " (" + (profitPerHour / 1000) + "k/hr)")
                    .rightColor(Color.YELLOW)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
