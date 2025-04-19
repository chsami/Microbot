package net.runelite.client.plugins.microbot.mining.motherloadmine;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.microbot.mining.motherloadmine.MotherloadMineScript.status;


public class MotherloadMineOverlay extends OverlayPanel {
    @Inject
    MotherloadMineOverlay(MotherloadMinePlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setSnappable(true);
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {

            panelComponent.setPreferredSize(new Dimension(275, 900));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 Motherlode Mine \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());


            if(Rs2AntibanSettings.devDebug)
                Rs2Antiban.renderAntibanOverlayComponents(panelComponent);

            addEmptyLine();


            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Mining Location: " + MotherloadMineScript.miningSpot.name())
                    .build());

            addEmptyLine();

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(status.toString())
                    .right("Version: " + MotherloadMineScript.VERSION)
                    .build());
        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private void addEmptyLine() {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }
}
