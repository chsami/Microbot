package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.plugins.microbot.util.cache.Rs2PohCache;
import net.runelite.client.plugins.microbot.util.poh.data.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test class for PohTeleportDataAdapter serialization and deserialization functionality.
 * This class provides comprehensive testing for the adapter with various PohTransportable enums.
 */
public class PohTeleportDataAdapterTest {

    /**
     * Creates a Gson instance configured with the PohTeleportDataAdapter.
     */
    private static Gson createTestGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Rs2PohCache.TYPE_TOKEN, new PohTeleportDataAdapter())
                .setPrettyPrinting()
                .create();
    }

    public static void testMap() {
        System.out.println("\n=== Map Serialization Test ===");
        Map<String, List<PohTeleport>> map = new HashMap<>();
        map.put(NexusPortal.class.getSimpleName(), List.of(NexusPortal.ARDOUGNE, NexusPortal.LUMBRIDGE, NexusPortal.VARROCK));
        map.put(PohPortal.class.getSimpleName(), List.of(PohPortal.values()));
        map.put(MountedDigsite.class.getSimpleName(), List.of(MountedDigsite.values()));
        map.put(MountedGlory.class.getSimpleName(), List.of(MountedGlory.values()));

        Gson gson = createTestGson();
        try {
            String json = gson.toJson(map, Rs2PohCache.TYPE_TOKEN);
            System.out.println("Map Serialzied to: " + json);

            Map<String, List<PohTeleport>> result = gson.fromJson(json, Rs2PohCache.TYPE_TOKEN);
            System.out.println("Map deserialized to: " + result);
            if (result.equals(map)) {
                System.out.println("✓ PASS - Map handling works correctly");
            } else {
                System.out.println("✗ FAIL - Null handling failed");
            }
        } catch (Exception e) {
            System.out.println("✗ FAIL - Null handling failed: " + e.getMessage());
        }
    }

    /**
     * Main method to run all tests.
     */
    public static void main(String[] args) {
        System.out.println("Starting PohTeleportDataAdapter Tests...\n");

        testMap();

        System.out.println("\n=== All Tests Complete ===");
    }
}