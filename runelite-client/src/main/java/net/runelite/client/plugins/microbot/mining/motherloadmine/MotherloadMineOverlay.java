package net.runelite.client.plugins.microbot.mining.motherloadmine;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.plugins.microbot.Microbot;

public class MotherloadMineOverlay extends OverlayPanel {
    private MotherloadMinePlugin _plugin;

    @Inject
    MotherloadMineOverlay(MotherloadMinePlugin plugin) {
        super(plugin);
        _plugin = plugin;
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(225, 200));

            panelComponent
                    .getChildren()
                    .add(TitleComponent.builder()
                            .text("[ Motherlode Mine ]")
                            .color(Color.ORANGE)
                            .build());

            addEmptyLine();

            panelComponent
                    .getChildren()
                    .add(LineComponent.builder()
                            .left("Version: ")
                            .right(_plugin.VERSION)
                            .build());

            addEmptyLine();

            panelComponent
                    .getChildren()
                    .add(LineComponent.builder()
                            .left("Run Time: ")
                            .right(_plugin.getTimeRunning())
                            .build());

            addEmptyLine();

            panelComponent
                    .getChildren()
                    .add(LineComponent.builder()
                            .left("Mining Location: ")
                            .right(MotherloadMineScript.oreVein != null
                                    ? MotherloadMineScript.oreVein.toString()
                                    : "NULL")
                            .build());

            addEmptyLine();

            panelComponent
                    .getChildren()
                    .add(LineComponent.builder()
                            .left("Status: ")
                            .right(MotherloadMinePlugin.getStatus().name())
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
