package net.runelite.client.plugins.microbot.frosty.trueblood;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.*;

public class TrueBloodOverlay extends Overlay {
    private final Client client;
    private final TrueBloodScript script;
    private final PanelComponent panelComponent = new PanelComponent();
    private int xpGained = 0;
    private int bloodRunes = 0;

    public void updateXpGained(int newXp) {
        this.xpGained = newXp;
    }
    public void updateBloodRune(int count) {
        this.bloodRunes = count;
    }

    @Inject
    public TrueBloodOverlay(Client client, TrueBloodScript script) {
        this.client = client;
        this.script = script;

        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.MED);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        // Display Plugin Title
        panelComponent.getChildren().add(LineComponent.builder()
                .left("True Blood")
                .build());

        // Display XP Gained
        int currentXp = client.getSkillExperience(Skill.RUNECRAFT);
        int xpGained = currentXp - script.getInitialRcXp(); // Corrected calculation

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP Gained:")
                .right(String.valueOf(xpGained))
                .build());

        // Display Blood Runes Crafted
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Blood Runes Crafted:")
                .right(String.valueOf(script.getBloodRunesCrafted()))
                .build());

        // Display Current Script State
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(script.getState().toString())
                .build());

        return panelComponent.render(graphics);
    }
}
