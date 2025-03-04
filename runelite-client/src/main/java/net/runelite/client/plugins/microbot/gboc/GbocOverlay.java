package net.runelite.client.plugins.microbot.gboc;

import net.runelite.client.plugins.microbot.gboc.scripts.NMZScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.Objects;

public class GbocOverlay extends OverlayPanel {

    private final GbocPlugin plugin;
    private final GbocConfig config;
    private static final String PLUGIN_NAME = "OneClick Scripts by Gb";

    @Inject
    public GbocOverlay(GbocPlugin plugin, GbocConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
        setMovable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text(PLUGIN_NAME)
                        .color(JagexColors.DARK_ORANGE_INTERFACE_TEXT)
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Script:")
                        .right(config.selectedScript() == GbocConfig.AvailableScript.NONE ? "None" : String.valueOf(config.selectedScript()))
                        .rightColor(config.selectedScript() != GbocConfig.AvailableScript.NONE ? Color.GREEN : Color.GRAY)
                        .build()
        );
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .right(GbocPlugin.currentAction.getName())
                        .rightColor(GbocPlugin.currentAction.isRunning() ? JagexColors.YELLOW_INTERFACE_TEXT : Color.GRAY)
                        .build()
        );

        if (this.plugin.currentScript != null) {
            if (this.plugin.currentScript.getClass() == NMZScript.class) {
                if (NMZScript.getCurrentState() == NMZScript.State.UNKNOWN) {
                    panelComponent.getChildren().add(
                            LineComponent.builder()
                                    .left(NMZScript.scriptDescription)
                                    .build()
                    );
                } else {
                    panelComponent.getChildren().add(
                            LineComponent.builder()
                                    .left("Next guzzle at:")
                                    .right(NMZScript.getMinGuzzle() == 1 ? "Now" : String.valueOf(NMZScript.getMinGuzzle() + 1))
                                    .build()
                    );

                    panelComponent.getChildren().add(
                            LineComponent.builder()
                                    .left("Next absorption at:")
                                    .right(NMZScript.getMinAbsorption() == 600 ? "Now" : String.valueOf(NMZScript.getMinAbsorption() - 1))
                                    .build()
                    );
                }
            }
        }


        return super.render(graphics);
    }
}
