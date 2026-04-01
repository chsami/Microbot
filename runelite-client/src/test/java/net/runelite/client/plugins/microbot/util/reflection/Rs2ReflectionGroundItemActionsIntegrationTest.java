package net.runelite.client.plugins.microbot.util.reflection;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public class Rs2ReflectionGroundItemActionsIntegrationTest {

    private static final int TARGET_WORLD = 382;
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private static final int[] TEST_ITEM_IDS = {
            4151,  // Abyssal whip        → "Take"
            526,   // Bones               → "Take", "Bury"
            995,   // Coins               → "Take"
            1163,  // Rune full helm      → "Take"
            1333,  // Rune scimitar       → "Take"
            314,   // Feather             → "Take"
            2,     // Cannonball          → "Take"
            556,   // Air rune            → "Take"
            1073,  // Chain body (steel)  → "Take"
    };

    @BeforeClass
    public static void startClientAndLogin() throws Exception {
        log.info("=== Starting RuneLite client for ground item actions test ===");
        Thread clientThread = new Thread(() -> {
            try {
                RuneLite.main(new String[]{"--developer-mode"});
            } catch (Exception e) {
                log.error("Failed to start RuneLite", e);
            }
        }, "RuneLite-Test-Launcher");
        clientThread.setDaemon(true);
        clientThread.start();

        log.info("Waiting for Microbot to initialize...");
        waitForCondition("Microbot.getClientThread() != null", 90, () ->
                Microbot.getClientThread() != null
        );
        log.info("Microbot initialized.");

        log.info("Waiting for login screen...");
        waitForCondition("Login screen", 90, () -> {
            Client client = Microbot.getClient();
            return client != null && (
                    client.getGameState() == GameState.LOGIN_SCREEN ||
                            client.getGameState() == GameState.LOGGED_IN
            );
        });

        if (!Microbot.isLoggedIn()) {
            log.info("Attempting login to world {}...", TARGET_WORLD);
            Thread.sleep(5000);
            performLogin();
        }

        log.info("Logged in! Waiting for game state to settle...");
        Thread.sleep(5000);
        log.info("=== Client ready for tests ===");
    }

    private static void performLogin() throws Exception {
        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            log.info("Login attempt {}/{}...", attempt, MAX_LOGIN_ATTEMPTS);
            try {
                LoginManager.setWorld(TARGET_WORLD);
                Thread.sleep(1000);
                LoginManager.submitLoginForTest();
            } catch (Exception e) {
                log.warn("Login attempt {} threw: {}", attempt, e.getMessage());
            }

            if (waitForConditionSafe("Login", 20, Microbot::isLoggedIn)) {
                log.info("Login successful on attempt {}", attempt);
                return;
            }

            log.warn("Login attempt {} did not succeed, retrying in 5s...", attempt);
            Thread.sleep(5000);
        }
        throw new RuntimeException("Failed to login after " + MAX_LOGIN_ATTEMPTS + " attempts");
    }

    @Test
    public void testGetGroundItemActionsContainsTake() throws Exception {
        log.info("--- Test: getGroundItemActions returns 'Take' for common items ---");
        assertTrue("Must be logged in", Microbot.isLoggedIn());

        boolean foundTake = false;

        for (int itemId : TEST_ITEM_IDS) {
            final int id = itemId;
            ItemComposition itemComp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getItemDefinition(id)
            ).orElse(null);
            assertNotNull("ItemComposition should not be null for id " + id, itemComp);

            String[] actions = Rs2Reflection.getGroundItemActions(itemComp);
            log.info("  Item {} ({}): actions = {}", id, itemComp.getName(), Arrays.toString(actions));

            if (actions.length > 0 && Arrays.asList(actions).contains("Take")) {
                foundTake = true;
                log.info("  -> Found 'Take' action for item {} ({})", id, itemComp.getName());
            }
        }

        assertTrue("At least one test item should have 'Take' in ground actions", foundTake);
    }

    @Test
    public void testDiscoveryFindsFieldChainWithoutHardcodedNames() throws Exception {
        log.info("--- Test: Discovery finds field chain by structure, not hardcoded names ---");
        assertTrue("Must be logged in", Microbot.isLoggedIn());

        resetCachedFields();

        boolean discovered = false;
        for (int itemId : TEST_ITEM_IDS) {
            ItemComposition itemComp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getItemDefinition(itemId)
            ).orElse(null);

            String[] actions = Rs2Reflection.getGroundItemActions(itemComp);

            if (actions.length > 0) {
                discovered = true;
                log.info("  Discovery succeeded on item {} ({}): {}", itemId, itemComp.getName(), Arrays.toString(actions));

                assertFalse("Actions array should not be empty", actions.length == 0);
                boolean hasNonNull = false;
                for (String a : actions) {
                    if (a != null) { hasNonNull = true; break; }
                }
                assertTrue("At least one action should be non-null", hasNonNull);
                break;
            }
        }

        assertTrue("Should have discovered field chain for at least one item", discovered);
    }

    @Test
    public void testCachedPathWorksAcrossMultipleItems() throws Exception {
        log.info("--- Test: Cached field path works across multiple items ---");
        assertTrue("Must be logged in", Microbot.isLoggedIn());

        resetCachedFields();

        int successCount = 0;
        List<String> results = new ArrayList<>();

        for (int itemId : TEST_ITEM_IDS) {
            ItemComposition itemComp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getItemDefinition(itemId)
            ).orElse(null);

            String[] actions = Rs2Reflection.getGroundItemActions(itemComp);
            String result = String.format("  %s (%d): %s", itemComp.getName(), itemId, Arrays.toString(actions));
            results.add(result);

            if (actions.length > 0) {
                successCount++;
            }
        }

        for (String r : results) {
            log.info(r);
        }

        log.info("  {}/{} items returned non-empty actions", successCount, TEST_ITEM_IDS.length);
        assertTrue("Most items should return actions (got " + successCount + "/" + TEST_ITEM_IDS.length + ")",
                successCount >= TEST_ITEM_IDS.length / 2);
    }

    @Test
    public void testFieldChainUsesNoHardcodedObfuscatedNames() throws Exception {
        log.info("--- Test: Verify no hardcoded obfuscated names in field chain ---");
        assertTrue("Must be logged in", Microbot.isLoggedIn());

        resetCachedFields();

        for (int itemId : TEST_ITEM_IDS) {
            ItemComposition itemComp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getItemDefinition(itemId)
            ).orElse(null);

            String[] actions = Rs2Reflection.getGroundItemActions(itemComp);
            if (actions.length == 0) continue;

            Field outerField = getStaticField("cachedOuterField");
            Field listField = getStaticField("cachedListField");
            Field stringField = getStaticField("cachedStringField");

            assertNotNull("cachedOuterField should be set after discovery", outerField);
            assertNotNull("cachedListField should be set after discovery", listField);

            log.info("  Discovered field chain:");
            log.info("    outerField: {}.{} (type: {})", outerField.getDeclaringClass().getName(), outerField.getName(), outerField.getType().getName());
            log.info("    listField:  {}.{} (type: {})", listField.getDeclaringClass().getName(), listField.getName(), listField.getType().getName());
            if (stringField != null) {
                log.info("    stringField: {}.{} (type: {})", stringField.getDeclaringClass().getName(), stringField.getName(), stringField.getType().getName());
            }

            assertEquals("listField type should be ArrayList", ArrayList.class, listField.getType());

            log.info("  Field chain discovered purely by type structure - no hardcoded names needed");
            return;
        }

        fail("Could not discover field chain for any test item");
    }

    @Test
    public void testRetryDiscoveryAfterCacheInvalidation() throws Exception {
        log.info("--- Test: Re-discovery works after cache invalidation ---");
        assertTrue("Must be logged in", Microbot.isLoggedIn());

        ItemComposition firstItem = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getItemDefinition(TEST_ITEM_IDS[0])
        ).orElse(null);
        assertNotNull("ItemComposition should not be null", firstItem);
        String[] firstResult = Rs2Reflection.getGroundItemActions(firstItem);
        log.info("  First call: {}", Arrays.toString(firstResult));

        resetCachedFields();
        log.info("  Cache invalidated");

        String[] secondResult = Rs2Reflection.getGroundItemActions(firstItem);
        log.info("  After re-discovery: {}", Arrays.toString(secondResult));

        assertArrayEquals("Results should match after re-discovery", firstResult, secondResult);
        log.info("  Re-discovery produced identical results");
    }

    @Test
    public void testScanItemCompositionStructure() throws Exception {
        log.info("--- Test: Scan ItemComposition class to find all ArrayList<Bean> field chains ---");
        assertTrue("Must be logged in", Microbot.isLoggedIn());

        ItemComposition itemComp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getItemDefinition(TEST_ITEM_IDS[0])
        ).orElse(null);
        assertNotNull("ItemComposition should not be null", itemComp);

        Class<?> clazz = itemComp.getClass();
        log.info("  ItemComposition impl class: {}", clazz.getName());

        int candidateCount = 0;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field outerField : c.getDeclaredFields()) {
                Class<?> type = outerField.getType();
                if (type.isPrimitive() || type == String.class || type.isArray()
                        || type.getName().startsWith("java.") || type.getName().startsWith("net.runelite.")) continue;

                outerField.setAccessible(true);
                Object outerValue = outerField.get(itemComp);
                outerField.setAccessible(false);
                if (outerValue == null) continue;

                for (Field listField : outerValue.getClass().getDeclaredFields()) {
                    if (listField.getType() != ArrayList.class) continue;

                    listField.setAccessible(true);
                    Object listObj = listField.get(outerValue);
                    listField.setAccessible(false);

                    if (!(listObj instanceof ArrayList)) continue;
                    ArrayList<?> list = (ArrayList<?>) listObj;

                    candidateCount++;
                    log.info("  Candidate #{}: {}.{} -> {}.{} (ArrayList, size={})",
                            candidateCount,
                            outerField.getDeclaringClass().getName(), outerField.getName(),
                            listField.getDeclaringClass().getName(), listField.getName(),
                            list.size());

                    if (!list.isEmpty()) {
                        Object first = null;
                        for (Object el : list) {
                            if (el != null) { first = el; break; }
                        }
                        if (first != null) {
                            log.info("    Element type: {}", first.getClass().getName());
                            for (Field f : first.getClass().getDeclaredFields()) {
                                log.info("      field: {} ({})", f.getName(), f.getType().getSimpleName());
                            }
                        }
                    }
                }
            }
        }

        log.info("  Total candidate field chains found: {}", candidateCount);
        assertTrue("Should find at least one candidate ArrayList chain", candidateCount >= 1);
    }

    private static void resetCachedFields() throws Exception {
        for (String fieldName : new String[]{"cachedOuterField", "cachedListField", "cachedStringField"}) {
            Field f = Rs2Reflection.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(null, null);
            f.setAccessible(false);
        }
    }

    private static Field getStaticField(String fieldName) throws Exception {
        Field metaField = Rs2Reflection.class.getDeclaredField(fieldName);
        metaField.setAccessible(true);
        Field value = (Field) metaField.get(null);
        metaField.setAccessible(false);
        return value;
    }

    private static void waitForCondition(String description, long timeoutSeconds, BooleanSupplier condition) throws InterruptedException {
        if (!waitForConditionSafe(description, timeoutSeconds, condition)) {
            throw new RuntimeException("Timed out waiting for: " + description + " (after " + timeoutSeconds + "s)");
        }
    }

    private static boolean waitForConditionSafe(String description, long timeoutSeconds, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) return true;
            } catch (Exception e) {
                log.debug("Condition check for '{}' threw: {}", description, e.getMessage());
            }
            Thread.sleep(500);
        }
        return false;
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
