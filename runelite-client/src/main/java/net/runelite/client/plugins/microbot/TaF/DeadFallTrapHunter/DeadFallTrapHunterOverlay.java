package net.runelite.client.plugins.microbot.TaF.DeadFallTrapHunter;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class DeadFallTrapHunterOverlay extends OverlayPanel {
    private final Client client;
    private final DeadFallTrapHunterConfig config;
    private final DeadFallTrapHunterPlugin plugin;
    private final DeadFallTrapHunterScript script;
    private int startingLevel = 0;

    @Inject
    public DeadFallTrapHunterOverlay(Client client, DeadFallTrapHunterConfig config, DeadFallTrapHunterPlugin plugin, DeadFallTrapHunterScript script) {
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
                .text("Deadfall creature hunter by TaF")
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

        if (config.deadFallTrapHunting() != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hunting:")
                    .right(config.deadFallTrapHunting().getName())
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());
        }

        // Traps information
        int currentTraps = plugin.getTraps().size();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Traps:")
                .right(String.valueOf(currentTraps))
                .leftColor(Color.WHITE)
                .rightColor(currentTraps > 0 ? Color.GREEN : Color.CYAN)
                .build());

        // Statistics
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Creatures Caught:")
                .right(String.valueOf(DeadFallTrapHunterScript.creaturesCaught))
                .leftColor(Color.WHITE)
                .rightColor(Color.GREEN)
                .build());

        return super.render(graphics);
    }
}