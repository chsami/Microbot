package net.runelite.client.plugins.microbot.mining.fxminer;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;

public class FxminerOverlay extends OverlayPanel {
    @Inject
    FxminerOverlay(FxminerPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
    }
}
