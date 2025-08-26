package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.GameObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingScript;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.ObjectID.*;

public class StrugglingSaplingEvent implements BlockingEvent {
    private final AutoWoodcuttingPlugin plugin;
    private final List<Integer> ingredientIds = List.of(
        GATHERING_EVENT_SAPLING_INGREDIENT_1,
        GATHERING_EVENT_SAPLING_INGREDIENT_2,
        GATHERING_EVENT_SAPLING_INGREDIENT_3,
        GATHERING_EVENT_SAPLING_INGREDIENT_4A,
        GATHERING_EVENT_SAPLING_INGREDIENT_4B,
        GATHERING_EVENT_SAPLING_INGREDIENT_4C,
        GATHERING_EVENT_SAPLING_INGREDIENT_5
    );

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
        try {
            Microbot.log("StrugglingSaplingEvent: Executing Struggling Sapling event");
            plugin.currentForestryEvent = ForestryEvents.STRUGGLING_SAPLING;
            // Find the struggling sapling
            var sapling = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("Struggling sapling", false))
                    .stream()
                    .filter(obj ->
                            Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Add-mulch") &&
                                    obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= AutoWoodcuttingScript.FORESTRY_DISTANCE
                    )
                    .findFirst()
                    .orElse(null);

            // Find all available leaf ingredients
            var ingredients = Rs2GameObject.getGameObjects(gameObject -> ingredientIds.contains(gameObject.getId()))
                    .stream()
                    .filter(obj -> Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Collect"))
                    .collect(Collectors.toList());

            if (ingredients.isEmpty()) {
                Microbot.log("StrugglingSaplingEvent: No leaf ingredients available to collect.");
                return false;
            }

            Set<Integer> triedFirstIngredients = new HashSet<>();
            Set<Integer> triedSecondIngredients = new HashSet<>();
            Set<Integer> triedThirdIngredients = new HashSet<>();

            while (this.validate()) {
                // If we have mulch stage 3 in inventory, add them to the sapling
                if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE3)) {
                    Microbot.log("StrugglingSaplingEvent: Adding mulch to the struggling sapling.");
                    Rs2GameObject.interact(sapling, "Add-mulch");
                    Rs2Player.waitForAnimation();
                    continue;
                }

                //if we have mulch in inventory, check if we know the correct ingredient or pick a random one
                // Determine current stage
                int stage;
                if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE2)) {
                    stage = 2;
                } else if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE1)) {
                    stage = 1;
                } else {
                    stage = 0;
                }
                var correctIngredient = plugin.saplingOrder[stage];

                if (correctIngredient != null) {
                    // Look for matching ingredient in our available ingredients
                    for (GameObject ingredient : ingredients) {
                        if (ingredient.getId() == correctIngredient.getId()) {
                            // Collect this ingredient as it's known to be correct
                            Microbot.log("StrugglingSaplingEvent: Collecting known correct ingredient: " + ingredient.getWorldLocation());
                            Rs2GameObject.interact(ingredient, "Collect");
                            Rs2Player.waitForAnimation();
                        }
                    }
                    continue;
                }

                // If we don't know the correct ingredient, try to collect a random one
                Set<Integer> triedIngredients;
                if (stage == 2) triedIngredients = triedThirdIngredients;
                else if (stage == 1) triedIngredients = triedSecondIngredients;
                else triedIngredients = triedFirstIngredients;

                var availableIngredients = ingredients.stream()
                        .filter(ingredient -> !triedIngredients.contains(ingredient.getId()))
                        .collect(Collectors.toList());
                if (availableIngredients.isEmpty()) {
                    Microbot.log("StrugglingSaplingEvent: All ingredients have been tried for stage " + stage + ".");
                    break;
                }

                Microbot.log("StrugglingSaplingEvent: No known correct ingredient, collecting a random one.");
                var randomIngredient = availableIngredients.get((int) (Math.random() * availableIngredients.size()));
                Microbot.log("StrugglingSaplingEvent: Collecting random ingredient: " + randomIngredient.getWorldLocation());
                Rs2GameObject.interact(randomIngredient, "Collect");
                Rs2Player.waitForAnimation();
            }

            Microbot.log("StrugglingSaplingEvent: Finished processing struggling sapling.");
            plugin.saplingOrder[0] = null; // Reset the sapling order after processing
            plugin.saplingOrder[1] = null;
            plugin.saplingOrder[2] = null;
            return true;
        }
        catch (Exception e) {
            Microbot.log("StrugglingSaplingEvent: Error during execution: " + e.getMessage() + Arrays.toString(e.getStackTrace()));
            return false;
        }
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

}
