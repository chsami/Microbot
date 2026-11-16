package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathfinderConfigRefreshItemQuantitiesTest {

    @Test
    public void sumsQuantitiesAcrossSourcesIntoCallerOwnedMap() {
        Map<Integer, Integer> quantities = new HashMap<>();

        PathfinderConfig.addItemQuantities(quantities, Stream.of(item(995, 400), item(563, 10)));
        PathfinderConfig.addItemQuantities(quantities, Stream.of(item(995, 600), item(772, 1)));

        assertEquals(3, quantities.size());
        assertEquals(1000, (int) quantities.get(995));
        assertEquals(10, (int) quantities.get(563));
        assertEquals(1, (int) quantities.get(772));
    }

    @Test
    public void negativeQuantitiesCountAsZero() {
        Map<Integer, Integer> quantities = new HashMap<>();

        PathfinderConfig.addItemQuantities(quantities, Stream.of(item(995, -1), item(995, 5)));

        assertEquals(5, (int) quantities.get(995));
    }

    @Test
    public void refreshScratchFieldsArePublishedSafely() throws Exception {
        for (String name : new String[]{
                "refreshAvailableItemQuantities",
                "refreshAvailableRuneQuantities",
                "refreshBoostedLevels",
                "refreshCurrencyCache",
                "refreshVarplayerValues"}) {
            Field field = PathfinderConfig.class.getDeclaredField(name);
            assertTrue(name + " must be volatile", Modifier.isVolatile(field.getModifiers()));
        }
    }

    private static Rs2ItemModel item(int id, int quantity) {
        return new Rs2ItemModel(id, quantity, 0, null);
    }
}
