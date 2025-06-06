package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import javax.inject.Inject;
import java.awt.*;

public class WildernessAgilityOverlay extends OverlayPanel {
    private boolean active = false;
    private WildernessAgilityScript script;

    @Inject
    public WildernessAgilityOverlay(WildernessAgilityPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setScript(WildernessAgilityScript script) {
        this.script = script;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!active || script == null) return null;
        panelComponent.setPreferredSize(new Dimension(200, 120));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("\uD83D\uDC2C Wilderness Agility v" + (script != null ? script.VERSION : "1.5.0") + " \uD83D\uDC2C")
                .color(new Color(0x00B4D8))
                .build());
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dispensers Looted")
                .right(Integer.toString(script.getDispenserLoots()))
                .leftColor(Color.YELLOW)
                .rightColor(Color.YELLOW)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running")
                .right(script.getRunningTime())
                .leftColor(new Color(0xFFA726)) // light orange
                .rightColor(new Color(0xFFA726))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Inventory Value")
                .right(String.format("%,d gp", script.getInventoryValue()))
                .leftColor(new Color(0x2ECC40)) // money green
                .rightColor(new Color(0x2ECC40))
                .build());
        // Previous Lap Time (white)
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Previous Lap Time")
                .right(script.getPreviousLapTime())
                .leftColor(Color.WHITE)
                .rightColor(Color.WHITE)
                .build());
        // Fastest Lap Time (race car yellow)
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Fastest Lap Time")
                .right(script.getFastestLapTime())
                .leftColor(new Color(0xFFD600)) // race car yellow
                .rightColor(new Color(0xFFD600))
                .build());
        // Optionally show current obstacle if you track it
        // panelComponent.getChildren().add(LineComponent.builder()
        //         .left("Current Obstacle")
        //         .right(script.getCurrentObstacleName())
        //         .build());
        return super.render(graphics);
    }
} 