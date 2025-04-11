package net.runelite.client.plugins.microbot.mining.motherloadmine;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class MotherloadMineOverlay extends OverlayPanel {
    @Inject
    MotherloadMineOverlay(MotherloadMinePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setSnappable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(225, 750));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("[ Motherlode Mine ]")
                    .color(Color.ORANGE)
                    .build());

            addEmptyLine();

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Version: ")
                    .right(MotherloadMineScript.VERSION)
                    .build());

            addEmptyLine();

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Running Time: ")
                    .right(MotherloadMinePlugin.getTimeRunning())
                    .build());

            if (Rs2AntibanSettings.devDebug) {
                addEmptyLine();
                Rs2Antiban.renderAntibanOverlayComponents(panelComponent);
            }

            addEmptyLine();

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Mining Location: ")
                    .right(MotherloadMineScript.miningSpot.name())
                    .build());

            addEmptyLine();

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status: ")
                    .right(MotherloadMineScript.status.name())
                    .build());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private void addEmptyLine() {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }
}
