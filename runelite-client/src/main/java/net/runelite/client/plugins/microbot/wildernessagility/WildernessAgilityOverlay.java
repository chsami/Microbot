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
                .text("Wilderness Agility")
                .color(Color.GREEN)
                .build());
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dispensers Looted")
                .right(Integer.toString(script.dispenserLoots))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running")
                .right(script.getRunningTime())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Inventory Value")
                .right(Integer.toString(script.getInventoryValue()) + " gp")
                .build());
        // Optionally show current obstacle if you track it
        // panelComponent.getChildren().add(LineComponent.builder()
        //         .left("Current Obstacle")
        //         .right(script.getCurrentObstacleName())
        //         .build());
        return super.render(graphics);
    }
} 