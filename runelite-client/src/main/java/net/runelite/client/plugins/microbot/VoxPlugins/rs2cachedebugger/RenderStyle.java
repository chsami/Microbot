package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Different rendering styles for entity overlays.
 * Based on RuneLite's NPC and Object indicator patterns.
 */
@Getter
@RequiredArgsConstructor
public enum RenderStyle {
    HULL("Hull", "Render convex hull outline"),
    TILE("Tile", "Render tile area"),
    TRUE_TILE("True Tile", "Render true tile (centered)"),
    SW_TILE("SW Tile", "Render southwest tile"),
    CLICKBOX("Clickbox", "Render clickbox area"),
    OUTLINE("Outline", "Render model outline"),
    NAME("Name", "Show entity name"),
    MIXED("Mixed", "Combination of hull + name"),
    MINIMAL("Minimal", "Just a small indicator"),
    DETAILED("Detailed", "Full information display"),
    BOTH("Both", "Render both hull and tile");

    private final String displayName;
    private final String description;

    @Override
    public String toString() {
        return displayName;
    }
}
