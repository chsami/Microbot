package net.runelite.client.plugins.microbot.commandcenter.scripts.mining;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class CCMiningOverlay extends OverlayPanel {
    private final CCMiningScript script;

    @Inject
    public CCMiningOverlay(CCMiningScript script) {
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!Microbot.isLoggedIn() || !script.isRunning()) return null;

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("CC Miner")
            .color(Color.GREEN)
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("State:")
            .right(script.getCurrentState() != null ? script.getCurrentState().name() : "—")
            .build());

        return super.render(graphics);
    }
}
