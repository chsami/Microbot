package net.runelite.client.plugins.microbot.mixology;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public enum LyeHerbs {
    Ranarr("ranarr weed"),
    Toadflax("toadflax"),
    Avantoe("avantoe"),
    Kwuarm("kwuarm"),
    Snapdragon("snapdragon"),
    RanarrUnf("ranarr potion (unf)"),
    ToadflaxUnf("toadflax potion (unf)"),
    AvantoeUnf("avantoe potion (unf)"),
    KwuarmUnf("kwuarm potion (unf)"),
    SnapdragonUnf("snapdragon potion (unf)");

    private final String itemName;

    @Override
    public String toString() {
        return itemName;
    }
}
