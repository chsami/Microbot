package net.runelite.client.plugins.microbot.bga.autofishing.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.worldmap.FishingSpotLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum Fish {
    SHRIMP("Shrimp", List.of("Raw shrimps"), FishingMethod.NET, FishingSpot.SHRIMP.getIds()),
    ANCHOVIES("Anchovies", List.of("Raw anchovies"), FishingMethod.NET, FishingSpot.SHRIMP.getIds()),
    HERRING("Herring", List.of("Raw herring"), FishingMethod.BAIT, FishingSpot.SHRIMP.getIds()),
    LOBSTER("Lobster", List.of("Raw lobster"), FishingMethod.CAGE, FishingSpot.LOBSTER.getIds()),
    SHARK("Shark", List.of("Raw shark"), FishingMethod.HARPOON, FishingSpot.SHARK.getIds()),
    SALMON("Salmon", List.of("Raw salmon"), FishingMethod.LURE, FishingSpot.SALMON.getIds()),
    TROUT("Trout", List.of("Raw trout"), FishingMethod.LURE, FishingSpot.SALMON.getIds()),
    MONKFISH("Monkfish", List.of("Raw monkfish"), FishingMethod.NET, FishingSpot.MONKFISH.getIds()),
    KARAMBWAN("Karambwan", List.of("Raw karambwan"), FishingMethod.KARAMBWAN_VESSEL, FishingSpot.KARAMBWAN.getIds()),
    KARAMBWANJI("Karambwanji", List.of("Raw karambwanji"), FishingMethod.NET, FishingSpot.KARAMBWANJI.getIds()),
    LAVA_EEL("Lava eel", List.of("Raw lava eel"), FishingMethod.OILY_ROD, FishingSpot.LAVA_EEL.getIds()),
    CAVE_EEL("Cave eel", List.of("Raw cave eel"), FishingMethod.BAIT, FishingSpot.CAVE_EEL.getIds()),
    BARBARIAN_FISH("Barbarian fish", List.of("Leaping trout", "Leaping salmon", "Leaping sturgeon"), FishingMethod.BARBARIAN_ROD, FishingSpot.BARB_FISH.getIds()),
    ANGLERFISH("Anglerfish", List.of("Raw anglerfish"), FishingMethod.SANDWORMS, FishingSpot.ANGLERFISH.getIds());

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

    public List<FishingSpotLocation> getAvailableLocations() {
        FishingSpot spotType = getFishingSpotType();
        if (spotType == null) return new ArrayList<>();
        
        return Arrays.stream(FishingSpotLocation.values())
                .filter(location -> {
                    // Check if this location supports our fishing spot type
                    String tooltip = location.getTooltip();
                    String fishingSpotName = spotType.getWorldMapTooltip();
                    return tooltip.contains(fishingSpotName);
                })
                .collect(Collectors.toList());
    }

    public WorldPoint getClosestLocation(WorldPoint playerLocation) {
        List<FishingSpotLocation> availableLocations = getAvailableLocations();
        if (availableLocations.isEmpty()) return null;
        
        WorldPoint closestPoint = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (FishingSpotLocation location : availableLocations) {
            for (WorldPoint point : location.getLocations()) {
                int distance = playerLocation.distanceTo(point);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = point;
                }
            }
        }
        
        return closestPoint;
    }

    public List<String> getLocationNames() {
        return getAvailableLocations().stream()
                .map(location -> location.name().replace("_", " "))
                .collect(Collectors.toList());
    }

    private FishingSpot getFishingSpotType() {
        switch (this) {
            case SHRIMP:
            case ANCHOVIES:
            case HERRING:
                return FishingSpot.SHRIMP;
            case LOBSTER:
                return FishingSpot.LOBSTER;
            case SHARK:
                return FishingSpot.SHARK;
            case SALMON:
            case TROUT:
                return FishingSpot.SALMON;
            case MONKFISH:
                return FishingSpot.MONKFISH;
            case KARAMBWAN:
                return FishingSpot.KARAMBWAN;
            case KARAMBWANJI:
                return FishingSpot.KARAMBWANJI;
            case LAVA_EEL:
                return FishingSpot.LAVA_EEL;
            case CAVE_EEL:
                return FishingSpot.CAVE_EEL;
            case BARBARIAN_FISH:
                return FishingSpot.BARB_FISH;
            case ANGLERFISH:
                return FishingSpot.ANGLERFISH;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}