package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.GameObject;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingScript;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;

import java.util.Comparator;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.*;

public class StrugglingSaplingEvent implements BlockingEvent {
    private final AutoWoodcuttingPlugin plugin;

    public StrugglingSaplingEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        var strugglingSaplings = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("Struggling sapling", false));
        if (strugglingSaplings == null) return false;
        if (strugglingSaplings.isEmpty()) return false;
        return strugglingSaplings.stream().anyMatch(obj ->
                Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Add-mulch") &&
                        obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= AutoWoodcuttingScript.FORESTRY_DISTANCE
        );
    }

    @Override
    public boolean execute() {
        plugin.autoWoodcuttingScript.breakPlayersAnimation();

        // Find the struggling sapling
        var sapling = Rs2GameObject.getTileObjects(Rs2GameObject.nameMatches("Struggling sapling", false))
                .stream()
                .filter(obj ->
                        Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Add-mulch") &&
                                obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= AutoWoodcuttingScript.FORESTRY_DISTANCE
                )
                .findFirst()
                .orElse(null);

        if (sapling == null) {
            plugin.autoWoodcuttingScript.currentForestryEvent = ForestryEvents.NONE;
            return true;
        }

        // Find all available leaf ingredients
        var ingredients = Rs2GameObject.getTileObjects(Rs2GameObject.nameMatches("leaves", false))
                .stream()
                .filter(obj -> Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Collect"))
                .collect(Collectors.toList());

        if (ingredients.isEmpty()) {
            plugin.autoWoodcuttingScript.currentForestryEvent = ForestryEvents.NONE;
            return true;
        }

        // If we have leaves in inventory, add them to the sapling
        if (Rs2Inventory.contains("leaves")) {
            Rs2GameObject.interact(sapling, "Add-mulch");
            Rs2Player.waitForAnimation();
            sleep(1000, 2000);
            return true;
        }

        // Check if we have knowledge of correct ingredients from previous attempts
        for (int i = 0; i < 3; i++) {
            GameObject correctIngredient = plugin.autoWoodcuttingScript.saplingOrder[i];
            if (correctIngredient != null) {
                // Look for matching ingredient in our available ingredients
                for (TileObject ingredient : ingredients) {
                    if (ingredient.getId() == correctIngredient.getId()) {
                        // Collect this ingredient as it's known to be correct
                        Rs2GameObject.interact(ingredient, "Collect");
                        Rs2Player.waitForAnimation();
                        sleep(800, 1500);
                        return true;
                    }
                }
            }
        }

        // If we don't know the correct order yet or the correct ingredients aren't available,
        // collect the closest ingredient to try it
        TileObject closestIngredient = ingredients.stream()
                .min(Comparator.comparingInt(i ->
                        i.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);

        if (closestIngredient != null) {
            Rs2GameObject.interact(closestIngredient, "Collect");
            Rs2Player.waitForAnimation();
            sleep(800, 1500);
        }
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
