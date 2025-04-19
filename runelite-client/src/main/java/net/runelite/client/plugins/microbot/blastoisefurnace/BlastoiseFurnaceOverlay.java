package net.runelite.client.plugins.microbot.blastoisefurnace;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BlastoiseFurnaceOverlay extends OverlayPanel {
    @Inject
    BlastoiseFurnaceOverlay(BlastoiseFurnacePlugin plugin) {
        super(plugin);
        this.setPosition(OverlayPosition.TOP_LEFT);
        this.setNaughty();
    }

    public Dimension render(Graphics2D graphics) {
        try {
            this.panelComponent.setPreferredSize(new Dimension(200, 300));
            this.panelComponent.getChildren().add(TitleComponent.builder().text("Blastoise" + BlastoiseFurnaceScript.version).color(Color.GREEN).build());
            this.panelComponent.getChildren().add(LineComponent.builder().build());
            this.panelComponent.getChildren().add(LineComponent.builder().left(Microbot.status).build());
        } catch (Exception var3) {
            Exception ex = var3;
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }

        return super.render(graphics);
    }
}