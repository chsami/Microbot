package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;

import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public class AutoWoodcuttingOverlay extends OverlayPanel {
    private static final Color WHITE_TRANSLUCENT = new Color(255, 255, 255, 127);
    private static final Color TITLE_COLOR = new Color(0, 170, 0);
    private static final Color HEADER_COLOR = new Color(140, 220, 140);
    private static final Color NORMAL_TEXT_COLOR = Color.WHITE;
    private static final Color HIGHLIGHT_COLOR = new Color(255, 235, 145);

    private final AutoWoodcuttingConfig config;
    private final Client client;
    private final AutoWoodcuttingPlugin plugin;

    private Instant startTime;
    private int startXp;
    private int startLevel;
    private int logsChopped;
    private boolean firstRun = false;

    @Inject
    AutoWoodcuttingOverlay(AutoWoodcuttingPlugin plugin, AutoWoodcuttingConfig config, Client client) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    public void resetStats() {
        startTime = Instant.now();
        startXp = client.getSkillExperience(Skill.WOODCUTTING);
        startLevel = client.getRealSkillLevel(Skill.WOODCUTTING);
        logsChopped = 0;
    }

    public void incrementLogsChopped() {
        logsChopped++;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (Microbot.isLoggedIn() && !firstRun) {
            resetStats();
            firstRun = true;
        }
        try {
            panelComponent.setPreferredSize(new Dimension(240, 350));
            panelComponent.getChildren().clear();

            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Woodcutting v" + AutoWoodcuttingScript.version)
                    .color(TITLE_COLOR)
                    .build());

            // Status
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(this.plugin.autoWoodcuttingScript.woodcuttingScriptState.toString())
                    .rightColor(HIGHLIGHT_COLOR)
                    .build());
            var forestry = config.enableForestry() ? plugin.currentForestryEvent != ForestryEvents.NONE ? plugin.currentForestryEvent.toString() : "None" : "Disabled";
            if (forestry != null && !forestry.equals("None") && !forestry.equals("Disabled")) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Forestry Event:")
                        .right(forestry)
                        .rightColor(HIGHLIGHT_COLOR)
                        .build());
            }
            // Current tree
            if (config.TREE() != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Tree:")
                        .right(config.TREE().toString())
                        .rightColor(HIGHLIGHT_COLOR)
                        .build());
            }

            // Add a separator
            panelComponent.getChildren().add(LineComponent.builder().left("").build());

            // Stats Header
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Statistics")
                    .leftColor(HEADER_COLOR)
                    .build());

            // Current level
            int currentLevel = client.getRealSkillLevel(Skill.WOODCUTTING);
            int currentXp = client.getSkillExperience(Skill.WOODCUTTING);
            int xpGained = currentXp - startXp;

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Level:")
                    .right(currentLevel + (currentLevel > startLevel ? " (+" + (currentLevel - startLevel) + ")" : ""))
                    .rightColor(NORMAL_TEXT_COLOR)
                    .build());

            // XP Info
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained:")
                    .right(NumberFormat.getInstance().format(xpGained))
                    .rightColor(NORMAL_TEXT_COLOR)
                    .build());

            // Calculate XP per hour
            long secondsElapsed = Duration.between(startTime, Instant.now()).getSeconds();
            if (secondsElapsed > 0) {
                double xpPerHour = (double) xpGained / secondsElapsed * 3600;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("XP/Hour:")
                        .right(NumberFormat.getInstance().format((long) xpPerHour))
                        .rightColor(NORMAL_TEXT_COLOR)
                        .build());
            }

            // Logs chopped
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Logs Chopped:")
                    .right(String.valueOf(logsChopped))
                    .rightColor(NORMAL_TEXT_COLOR)
                    .build());

            // Time running
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time Running:")
                    .right(formatDuration(Duration.between(startTime, Instant.now())))
                    .rightColor(NORMAL_TEXT_COLOR)
                    .build());

            // Add a separator
            panelComponent.getChildren().add(LineComponent.builder().left("").build());

            // Script Settings
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Settings")
                    .leftColor(HEADER_COLOR)
                    .build());

            // Reset option
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Reset Method:")
                    .right(config.resetOptions().toString())
                    .rightColor(NORMAL_TEXT_COLOR)
                    .build());

            // Display area boundary
            if (config.distanceToStray() < 21) {
                LocalPoint lp = LocalPoint.fromWorld(client, AutoWoodcuttingScript.getReturnPoint(config));
                if (lp != null) {
                    Polygon poly = Perspective.getCanvasTileAreaPoly(client, lp, config.distanceToStray() * 2);

                    if (poly != null) {
                        renderPolygon(graphics, poly, WHITE_TRANSLUCENT);
                    }
                }
            }

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}