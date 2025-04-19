package net.runelite.client.plugins.microbot.nateplugins.moneymaking.natehumidifier;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;



public class HumidifierOverlay extends OverlayPanel {

    @Inject
    HumidifierOverlay(HumidifierPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {

            panelComponent.setPreferredSize(new Dimension(275, 800));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Nate's Humidifier V" + HumidifierScript.version)
                    .color(Color.magenta)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(HumidifierScript.itemsProcessedMessage)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(HumidifierScript.profitMessage)
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
