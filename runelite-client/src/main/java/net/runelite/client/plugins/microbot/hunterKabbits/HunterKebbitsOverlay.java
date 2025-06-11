package net.runelite.client.plugins.microbot.hunterKabbits;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

/**
 * Overlay for the Hunter Kebbits plugin.
 * Displays runtime stats, hunter level, caught count, and current script state.
 */
public class HunterKebbitsOverlay extends OverlayPanel {

    private final Client client;
    private final HunterKebbitsConfig config;
    private final HunterKebbitsPlugin plugin;
    private final HunterKabbitsScript script;
    private int startingLevel = -1;

    /**
     * Constructs the overlay panel for displaying live plugin data.
     *
     * @param client  The game client instance.
     * @param config  Configuration settings for the plugin.
     * @param plugin  The main plugin class instance.
     * @param script  The script instance providing state information.
     */
    @Inject
    public HunterKebbitsOverlay(Client client, HunterKebbitsConfig config, HunterKebbitsPlugin plugin, HunterKabbitsScript script) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        this.script = script;

        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty(); // Allow overlay rendering during cutscenes or restricted areas
    }

    /**
     * Renders the overlay with live status and metrics related to the hunting script.
     *
     * @param graphics The graphics context.
     * @return The overlay dimension or null if overlay is disabled.
     */
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        if (startingLevel < 0) {
            startingLevel = client.getRealSkillLevel(Skill.HUNTER);
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(200, 300));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Kebbit Hunter by VIP")
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

        // Display script runtime
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Running:")
                .right(plugin.getTimeRunning())
                .leftColor(Color.WHITE)
                .rightColor(Color.WHITE)
                .build());

        // Display current and starting Hunter level
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Hunter Level:")
                .right(startingLevel + " / " + client.getRealSkillLevel(Skill.HUNTER))
                .leftColor(Color.WHITE)
                .rightColor(Color.ORANGE)
                .build());

        // Display current kebbit being hunted
        if (config.kebbitType() != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hunting:")
                    .right(config.kebbitType().getName())
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());
        }

        // Display number of successful catches
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Kebbits Caught:")
                .right(String.valueOf(HunterKabbitsScript.KebbitCaught))
                .leftColor(Color.WHITE)
                .rightColor(Color.GREEN)
                .build());

        // Display current bot state (e.g., CATCHING, DROPPING)
        String currentStatus = script.getCurrentState() != null ? script.getCurrentState().name() : "UNKNOWN";
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(currentStatus)
                .leftColor(Color.WHITE)
                .rightColor(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Bury Bones:")
                .right(config.buryBones() ? "Yes" : "No")
                .build());

        return super.render(graphics);

    }


}
