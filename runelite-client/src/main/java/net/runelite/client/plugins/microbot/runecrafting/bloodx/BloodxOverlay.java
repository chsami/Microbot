package net.runelite.client.plugins.microbot.runecrafting.bloodx;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.*;

public class BloodxOverlay extends Overlay {
    private final Client client;
    private final BloodxScript script;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public BloodxOverlay(Client client, BloodxScript script) {
        this.client = client;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Bloodx Runecrafting")
                .leftColor(Color.RED)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(script.getState().name())
                .rightColor(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Blood Runes:")
                .right(String.valueOf(script.getBloodRunesCrafted()))
                .rightColor(Color.YELLOW)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP Gained:")
                .right(String.valueOf(client.getSkillExperience(Skill.RUNECRAFT) - script.getInitialRcXp()))
                .rightColor(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("RC Level:")
                .right(String.valueOf(client.getRealSkillLevel(Skill.RUNECRAFT)))
                .rightColor(Color.ORANGE)
                .build());

        return panelComponent.render(graphics);
    }
}
