package net.runelite.client.plugins.microbot.bee.salamanders;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class SalamanderOverlay extends OverlayPanel {
    private final Client client;
    private final SalamanderConfig config;
    private final SalamanderPlugin plugin;
    private final SalamanderScript script;
    private int startingLevel = 0;

    @Inject
    public SalamanderOverlay(Client client, SalamanderConfig config, SalamanderPlugin plugin, SalamanderScript script) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        if (startingLevel == 0) {
            startingLevel = client.getRealSkillLevel(Skill.HUNTER);
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(200, 300));

        // Title with version
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Salamander Hunter by Bee & TaF")
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

        // Basic information
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Running: ")
                .right(plugin.getTimeRunning())
                .leftColor(Color.WHITE)
                .rightColor(Color.WHITE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Hunter Level:")
                .right(startingLevel + "/" + client.getRealSkillLevel(Skill.HUNTER))
                .leftColor(Color.WHITE)
                .rightColor(Color.ORANGE)
                .build());

        // Salamander type
        if (config.salamanderHunting() != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hunting:")
                    .right(config.salamanderHunting().getName())
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());
        }

        // Traps information
        int maxTraps = script.getMaxTrapsForHunterLevel(config);
        int currentTraps = plugin.getTraps().size();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Traps:")
                .right(currentTraps + "/" + maxTraps)
                .leftColor(Color.WHITE)
                .rightColor(currentTraps == maxTraps ? Color.GREEN : Color.CYAN)
                .build());

        // Statistics
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Salamanders Caught:")
                .right(String.valueOf(SalamanderScript.SalamandersCaught))
                .leftColor(Color.WHITE)
                .rightColor(Color.GREEN)
                .build());

        return super.render(graphics);
    }
}