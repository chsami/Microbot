package net.runelite.client.plugins.microbot.bga.autofishing.enums;

import lombok.Getter;
import net.runelite.client.game.FishingSpot;

import java.util.List;

@Getter
public enum Fish {
    ANCHOVIES("Anchovies", List.of("Raw anchovies"), FishingMethod.NET, FishingSpot.SHRIMP.getIds()),
    ANGLERFISH("Anglerfish", List.of("Raw anglerfish"), FishingMethod.SANDWORMS, FishingSpot.ANGLERFISH.getIds()),
    BARBARIAN_FISH("Barbarian fish", List.of("Leaping trout", "Leaping salmon", "Leaping sturgeon"), FishingMethod.BARBARIAN_ROD, FishingSpot.BARB_FISH.getIds()),
    BASS("Bass", List.of("Raw bass"), FishingMethod.BIG_NET, FishingSpot.SHARK.getIds()),
    CAVE_EEL("Cave eel", List.of("Raw cave eel"), FishingMethod.BAIT, FishingSpot.CAVE_EEL.getIds()),
    COD("Cod", List.of("Raw cod"), FishingMethod.BIG_NET, FishingSpot.SHARK.getIds()),
    HERRING("Herring", List.of("Raw herring"), FishingMethod.BAIT, FishingSpot.SHRIMP.getIds()),
    KARAMBWAN("Karambwan", List.of("Raw karambwan"), FishingMethod.KARAMBWAN_VESSEL, FishingSpot.KARAMBWAN.getIds()),
    KARAMBWANJI("Karambwanji", List.of("Raw karambwanji"), FishingMethod.NET, FishingSpot.KARAMBWANJI.getIds()),
    LAVA_EEL("Lava eel", List.of("Raw lava eel"), FishingMethod.OILY_ROD, FishingSpot.LAVA_EEL.getIds()),
    LOBSTER("Lobster", List.of("Raw lobster"), FishingMethod.CAGE, FishingSpot.LOBSTER.getIds()),
    MACKEREL("Mackerel", List.of("Raw mackerel"), FishingMethod.BIG_NET, FishingSpot.SHARK.getIds()),
    MONKFISH("Monkfish", List.of("Raw monkfish"), FishingMethod.NET, FishingSpot.MONKFISH.getIds()),
    PIKE("Pike", List.of("Raw pike"), FishingMethod.BAIT, FishingSpot.SALMON.getIds()),
    SALMON("Salmon", List.of("Raw salmon"), FishingMethod.LURE, FishingSpot.SALMON.getIds()),
    SARDINE("Sardine", List.of("Raw sardine"), FishingMethod.BAIT, FishingSpot.SHRIMP.getIds()),
    SHARK("Shark", List.of("Raw shark"), FishingMethod.HARPOON, FishingSpot.SHARK.getIds()),
    SHRIMP("Shrimp", List.of("Raw shrimps"), FishingMethod.NET, FishingSpot.SHRIMP.getIds()),
    SWORDFISH("Swordfish", List.of("Raw swordfish"), FishingMethod.HARPOON, FishingSpot.LOBSTER.getIds()),
    TROUT("Trout", List.of("Raw trout"), FishingMethod.LURE, FishingSpot.SALMON.getIds()),
    TUNA("Tuna", List.of("Raw tuna"), FishingMethod.HARPOON, FishingSpot.LOBSTER.getIds());

    private final String name;
    private final List<String> rawNames;
    private final FishingMethod method;
    private final int[] fishingSpot;

    Fish(String name, List<String> rawNames, FishingMethod method, int[] fishingSpot) {
        this.name = name;
        this.rawNames = rawNames;
        this.method = method;
        this.fishingSpot = fishingSpot;
    }

    public List<String> getActions() {
        return method.getActions();
    }

    public List<String> getRequiredItems() {
        return method.getRequiredItems();
    }

    @Override
    public String toString()
    {
        return name;
    }
}