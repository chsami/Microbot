package net.runelite.client.plugins.microbot.commandcenter.scripts.mining;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MiningRock {
    COPPER("Copper rocks", "Copper ore", "Mine"),
    TIN("Tin rocks", "Tin ore", "Mine"),
    IRON("Iron rocks", "Iron ore", "Mine"),
    COAL("Coal rocks", "Coal", "Mine"),
    GOLD("Gold rocks", "Gold ore", "Mine");

    private final String objectName;
    private final String oreName;
    private final String action;
}
