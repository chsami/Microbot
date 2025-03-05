package net.runelite.client.plugins.microbot.runecrafting.bloods;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.runecrafting.bloodx.BloodxScript;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.*;

public class BloodsOverlay extends Overlay {
    private final BloodsScript script;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public BloodsOverlay(BloodsScript script) {
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        if (script == null || !script.isRunning()) {
            return null;
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Bloods RC Status:")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(script.getState() != null ? script.getState().toString() : "N/A")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Blood Runes Crafted:")
                .right(String.valueOf(script.getBloodRunesCrafted()))
                .build());

        return panelComponent.render(graphics);
    }
}
