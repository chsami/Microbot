package net.runelite.client.plugins.microbot.frosty.threatdetector;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.overlay.OverlayLayer;

@Getter
@RequiredArgsConstructor
public enum flashLayer {
    ABOVE_SCENE("Above scene", OverlayLayer.ABOVE_SCENE),
    UNDER_WIDGETS("Under widgets", OverlayLayer.UNDER_WIDGETS),
    ABOVE_WIDGETS("Above widgets", OverlayLayer.ABOVE_WIDGETS),
    ALWAYS_ON_TOP("Always on top", OverlayLayer.ALWAYS_ON_TOP);

    private final String type;
    private final OverlayLayer layer;

    @Override
    public String toString() {
        return type;
    }
}
