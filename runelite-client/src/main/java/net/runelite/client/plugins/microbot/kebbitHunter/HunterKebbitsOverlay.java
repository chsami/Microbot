package net.runelite.client.plugins.microbot.kebbitHunter;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

/**
 * Overlay displaying hunting statistics
 */
public class HunterKebbitsOverlay extends OverlayPanel {

    private final Client client;
    private final HunterKebbitsConfig config;
    private final HunterKebbitsPlugin plugin;
    private final HunterKabbitsScript script;
    private int startingLevel = -1;

    @Inject
    public HunterKebbitsOverlay(Client client,
                                HunterKebbitsConfig config,
                                HunterKebbitsPlugin plugin,
                                HunterKabbitsScript script) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) return null;

        // Initialize starting level
        if (startingLevel < 0) {
            startingLevel = client.getRealSkillLevel(Skill.HUNTER);
        }

        panelComponent.getChildren().clear();

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Kebbit Hunter")
                .color(Color.GREEN)
                .build());

        // Statistics
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:").right(plugin.getTimeRunning()).build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Hunter Level:")
                .right(startingLevel + " → " + client.getRealSkillLevel(Skill.HUNTER))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Target:").right(config.kebbitType().getName()).build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Caught:").right(String.valueOf(script.KebbitCaught)).build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:").right(script.getCurrentState().name()).build());

        return super.render(graphics);
    }
}