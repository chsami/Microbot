package net.runelite.client.plugins.microbot.shortestpath;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/** Transient feedback for explicit manual walker actions; route recalculations stay silent. */
public class WalkingNoticeOverlay extends OverlayPanel {
    private static final long DISPLAY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private String message;
    private long shownAt;

    @Inject
    WalkingNoticeOverlay() {
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 0));
        panelComponent.setPreferredSize(new Dimension(480, 0));
    }

    void show(String message) {
        this.message = message;
        shownAt = System.nanoTime();
    }

    void clear() {
        message = null;
    }

    static String messageFor(ShortestPathScript.ManualWalkingNotice notice, Keybind toggleKey) {
        switch (notice) {
            case PATH_SET:
                return toggleKey == null || Keybind.NOT_SET.equals(toggleKey)
                        ? "Web Walker: Path set - use the panel's Automatic walking button to start walking."
                        : "Web Walker: Path set - press [" + toggleKey + "] to start walking.";
            case ENABLED:
                return "Web Walker: Walking enabled.";
            case PAUSED:
                return "Web Walker: Walking paused.";
            case CLEARED:
                return "Web Walker: Current path cleared.";
            default:
                throw new IllegalArgumentException("Unknown manual walking notice: " + notice);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (message == null || System.nanoTime() - shownAt >= DISPLAY_NANOS) {
            return null;
        }
        panelComponent.getChildren().add(LineComponent.builder()
                .left(message)
                .leftColor(Color.YELLOW)
                .build());
        return super.render(graphics);
    }
}
