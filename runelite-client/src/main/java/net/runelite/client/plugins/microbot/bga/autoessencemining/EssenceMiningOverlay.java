package net.runelite.client.plugins.microbot.bga.autoessencemining;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class EssenceMiningOverlay extends OverlayPanel {
    private final EssenceMiningPlugin plugin;
    private final EssenceMiningConfig config;
    private long startTime;

    @Inject
    EssenceMiningOverlay(EssenceMiningPlugin plugin, EssenceMiningConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.startTime = System.currentTimeMillis();
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Auto Essence Mining " + EssenceMiningScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status: " + Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Mining Lvl: " + Rs2Player.getRealSkillLevel(Skill.MINING))
                    .right("(" + Microbot.getClient().getSkillExperience(Skill.MINING) + " xp)")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime: " + formatTime(System.currentTimeMillis() - startTime))
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    public void resetStartTime() {
        this.startTime = System.currentTimeMillis();
    }

    private String formatTime(long duration) {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        return String.format("%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
    }
}