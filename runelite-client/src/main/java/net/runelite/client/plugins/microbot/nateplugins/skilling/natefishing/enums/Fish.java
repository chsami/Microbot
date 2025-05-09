package net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.FishingSpot;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum Fish {
    SHRIMP("shrimp", FishingSpot.SHRIMP.getIds(), List.of("net", "small net")),
    SARDINE("sardine/herring", FishingSpot.SHRIMP.getIds(),List.of("bait")),
    MACKEREL("mackerel/cod/bass", FishingSpot.SHARK.getIds(),List.of("big net")),
    TROUT("trout/salmon", FishingSpot.SALMON.getIds(),List.of("lure")),
    PIKE("pike", FishingSpot.SALMON.getIds(), List.of("bait")),
    TUNA("tuna/swordfish", FishingSpot.LOBSTER.getIds(),List.of("harpoon")),
    CAVE_EEL("cave eel", FishingSpot.CAVE_EEL.getIds(), List.of("bait")),
    LOBSTER("lobster", FishingSpot.LOBSTER.getIds(),List.of("cage")),
    MONKFISH("monkfish", FishingSpot.MONKFISH.getIds(), List.of("net")),
    KARAMBWANJI("karambwanji", FishingSpot.KARAMBWANJI.getIds(), List.of("net")),
    LAVA_EEL("lava eel", FishingSpot.LAVA_EEL.getIds(), List.of("lure")),
    SHARK("shark", FishingSpot.SHARK.getIds(),List.of("harpoon")),
    ANGLERFISH("anglerfish", FishingSpot.ANGLERFISH.getIds(), List.of("sandworms", "bait")),
    KARAMBWAN("karambwan", FishingSpot.KARAMBWAN.getIds(), List.of("fish"));

    private final String name;
    private final int[] fishingSpot;
    private final List<String> actions;

    @Override
    public String toString() {
        return name;
    }
}
