package net.runelite.client.plugins.microbot.rsagent.agent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.runelite.api.*;
// Removed unused import: net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
// Removed unused import: net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory; // Added import for Rs2Inventory
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.event.Level;

import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Removed unused imports: net.runelite.api.Item, net.runelite.api.ItemContainer, net.runelite.api.InventoryID
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment; // Added import for Rs2Equipment
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject; // Added import for Rs2GameObject


import static net.runelite.client.plugins.microbot.util.Global.*;

public class RsAgentTools {

    private static Map<String, List<SimpleCoord>> npcSpawnData;
    private static boolean npcSpawnDataLoaded = false;
    private static String npcSpawnDataError = null;

    private static Map<String, WorldPoint> locationCoordsData;
    private static boolean locationDataLoaded = false;
    private static String locationDataError = null;

    private static final Gson gson = new Gson();
    private static final int LEVENSHTEIN_THRESHOLD = 3; // Threshold for "Did you mean?" suggestion

    // Helper class for NPC JSON deserialization
    private static class SimpleCoord {
        int x;
        int y;
        int z;

        public WorldPoint toWorldPoint() {
            return new WorldPoint(x, y, z);
        }
    }

    // Helper classes for Location JSON deserialization
    private static class LocationDef {
        String name;
        List<Integer> coords;

        public WorldPoint toWorldPoint() {
            if (coords != null && coords.size() == 3) {
                return new WorldPoint(coords.get(0), coords.get(1), coords.get(2));
            }
            return null;
        }
    }

    private static class LocationsRoot {
        List<LocationDef> locations;
    }


    private static synchronized void loadNpcSpawnData() {
        if (npcSpawnDataLoaded) {
            return;
        }

        String path = "rsagent/npc_locations.json";
        try (InputStream inputStream = RsAgentTools.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                npcSpawnDataError = "NPC spawn data file not found: " + path;
                Microbot.log(Level.ERROR, npcSpawnDataError);
                npcSpawnDataLoaded = true;
                npcSpawnData = new HashMap<>(); // Ensure not null
                return;
            }
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, List<SimpleCoord>>>() {}.getType();
            npcSpawnData = gson.fromJson(reader, type);
            if (npcSpawnData == null) {
                npcSpawnData = new HashMap<>();
                npcSpawnDataError = "NPC spawn data file was empty or malformed: " + path;
                Microbot.log(Level.WARN, npcSpawnDataError);
            }
        } catch (Exception e) {
            npcSpawnDataError = "Error loading NPC spawn data from " + path + ": " + e.getMessage();
            Microbot.log(Level.ERROR, npcSpawnDataError, e);
            npcSpawnData = new HashMap<>();
        } finally {
            npcSpawnDataLoaded = true;
        }
    }

    private static synchronized void loadLocationData() {
        if (locationDataLoaded) {
            return;
        }
        locationCoordsData = new HashMap<>();
        String path = "rsagent/locations.json";
        try (InputStream inputStream = RsAgentTools.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                locationDataError = "Location data file not found: " + path;
                Microbot.log(Level.ERROR, locationDataError);
                locationDataLoaded = true;
                return;
            }
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            LocationsRoot locationsRoot = gson.fromJson(reader, LocationsRoot.class);

            if (locationsRoot != null && locationsRoot.locations != null) {
                for (LocationDef locDef : locationsRoot.locations) {
                    if (locDef.name != null && !locDef.name.trim().isEmpty() && locDef.toWorldPoint() != null) {
                        locationCoordsData.put(locDef.name.toLowerCase(), locDef.toWorldPoint());
                    } else {
                        Microbot.log(Level.WARN, "Invalid location entry in " + path + ": " + (locDef.name == null ? "null name" : locDef.name));
                    }
                }
            } else {
                locationDataError = "Location data file was empty or malformed: " + path;
                Microbot.log(Level.WARN, locationDataError);
            }
        } catch (Exception e) {
            locationDataError = "Error loading location data from " + path + ": " + e.getMessage();
            Microbot.log(Level.ERROR, locationDataError, e);
        } finally {
            locationDataLoaded = true;
        }
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     *
     * @param s1 The first string.
     * @param s2 The second string.
     * @return The Levenshtein distance.
     */
    private static int calculateLevenshteinDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    /**
     * Retrieves the WorldPoint for a named location from the 'locations.json' file.
     * If an exact match is not found, it suggests the closest match using Levenshtein distance.
     *
     * @param locationName The name of the location.
     * @return A string indicating the location's coordinates, a suggestion, or an error/not found message.
     */
    static public String getLocationCoords(String locationName) {
        loadLocationData();

        if (locationDataError != null && (locationCoordsData == null || locationCoordsData.isEmpty())) {
            Microbot.log(Level.ERROR, "Failed to load location data, cannot serve request for: " + locationName + ". Error: " + locationDataError);
            return "Error: Failed to load location data. Cannot find '" + locationName + "'.";
        }

        if (locationCoordsData == null) {
            Microbot.log(Level.WARN, "Location data map is null.");
            return "Error: Location data is not available. Cannot find '" + locationName + "'.";
        }

        if (locationName == null || locationName.trim().isEmpty()) {
            Microbot.log(Level.WARN, "Location name is null or empty.");
            return "Error: Location name not provided.";
        }

        String normalizedLocationName = locationName.toLowerCase();
        WorldPoint point = locationCoordsData.get(normalizedLocationName);

        if (point != null) {
            return "Location '" + locationName + "' found at (" + point.getX() + ", " + point.getY() + ", " + point.getPlane() + ").";
        } else {
            if (locationCoordsData.isEmpty()) {
                Microbot.log(Level.INFO, "No locations loaded. Cannot find '" + locationName + "'.");
                return "Location '" + locationName + "' not found. No location data available.";
            }

            String closestMatch = null;
            int minDistance = Integer.MAX_VALUE;

            // Find the original casing of keys for user-friendly suggestions
            Map<String, String> originalCaseMap = new HashMap<>();
            // Re-load or iterate through original names if needed for proper casing,
            // for now, we'll use the lowercase keys for matching and suggestion.
            // A better approach would be to store original names alongside lowercase ones if casing matters for output.
            // For simplicity, we'll suggest the lowercase key if we don't have original casing easily.
            // To improve: when loading, store a map of lowercaseName -> originalName.
            // For now, we'll iterate through the keys of locationCoordsData which are already lowercase.

            for (Map.Entry<String, WorldPoint> entry : locationCoordsData.entrySet()) {
                // The keys in locationCoordsData are already lowercase due to loadLocationData()
                String knownLocationKey = entry.getKey();
                int distance = calculateLevenshteinDistance(normalizedLocationName, knownLocationKey);

                if (distance < minDistance) {
                    minDistance = distance;
                    closestMatch = knownLocationKey; // This will be lowercase
                }
            }

            // Attempt to find original casing for the closest match if possible.
            // This part is tricky without storing original names. We'll find an entry that matches the lowercase closestMatch.
            // This is a placeholder. Ideally, you'd have a map from lowercase to original case.
            // For now, we find the first key that, when lowercased, matches `closestMatch`.
            // This is inefficient and assumes `closestMatch` itself is a key.
            // A better way: iterate original `LocationDef` list if accessible, or store original names.
            // Given current structure, `closestMatch` IS the key from `locationCoordsData`.
            // We need to find the original name that produced this lowercase key.
            // This requires changing how data is stored or re-parsing.
            // For now, we'll capitalize the first letter of the lowercase key as a simple heuristic.
            String bestSuggestionDisplay = closestMatch; // Default to lowercase
            if (closestMatch != null) {
                 // This is a placeholder. Ideally, you'd have a map from lowercase to original case.
                 // For now, we find the first key that, when lowercased, matches `closestMatch`.
                 // This is inefficient and assumes `closestMatch` itself is a key.
                 // A better way: iterate original `LocationDef` list if accessible, or store original names.
                 // Given current structure, `closestMatch` IS the key from `locationCoordsData`.
                 // We need to find the original name that produced this lowercase key.
                 // This requires changing how data is stored or re-parsing.
                 // For now, we'll capitalize the first letter of the lowercase key as a simple heuristic.
                if (bestSuggestionDisplay != null && !bestSuggestionDisplay.isEmpty()) {
                    bestSuggestionDisplay = Character.toUpperCase(bestSuggestionDisplay.charAt(0)) + bestSuggestionDisplay.substring(1);
                }
            }


            if (closestMatch != null && minDistance > 0 && minDistance <= LEVENSHTEIN_THRESHOLD) {
                Microbot.log(Level.INFO, "Location name '" + locationName + "' not found. Closest match: '" + bestSuggestionDisplay + "' with distance " + minDistance + ".");
                return "Location '" + locationName + "' not found. Did you mean '" + bestSuggestionDisplay + "'?";
            } else {
                Microbot.log(Level.INFO, "Location name '" + locationName + "' not found in location data. No close match found or data empty.");
                return "Location '" + locationName + "' not found in location data.";
            }
        }
    }


    /**
     * Finds the closest spawn location for a given NPC name from the npc_locations.json file.
     *
     * @param npcName The name of the NPC.
     * @return The WorldPoint of the closest spawn, or null if not found or an error occurred.
     * @throws RuntimeException if there was an error loading the spawn data initially.
     */
    static public WorldPoint getClosestNpcSpawnLocation(String npcName) {
        loadNpcSpawnData();

        if (npcSpawnDataError != null && (npcSpawnData == null || npcSpawnData.isEmpty())) {
            throw new RuntimeException("Failed to load NPC spawn data: " + npcSpawnDataError);
        }
        if (npcSpawnData == null || npcName == null || !npcSpawnData.containsKey(npcName)) {
            Microbot.log(Level.INFO,"NPC name '" + npcName + "' not found in spawn data or NPC name is null.");
            return null;
        }

        List<SimpleCoord> spawns = npcSpawnData.get(npcName);
        if (spawns == null || spawns.isEmpty()) {
            Microbot.log(Level.INFO,"No spawn locations listed for NPC '" + npcName + "'.");
            return null;
        }

        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            Microbot.log(Level.WARN,"Player is null, cannot determine closest NPC spawn.");
            return null;
        }
        WorldPoint playerLocation = player.getWorldLocation();
        if (playerLocation == null) {
            Microbot.log(Level.WARN,"Player location is null, cannot determine closest NPC spawn.");
            return null;
        }

        WorldPoint closestPoint = null;
        int minDistance = Integer.MAX_VALUE;

        for (SimpleCoord coord : spawns) {
            WorldPoint spawnPoint = coord.toWorldPoint();
            int distance = playerLocation.distanceTo(spawnPoint);
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = spawnPoint;
            }
        }
        return closestPoint;
    }


    /**
     * Walks to the specified world coordinates.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The plane (z-coordinate).
     * @return true if the walk action was initiated, false otherwise.
     */
    static public boolean walkTo(int x, int y, int z){
        return Rs2Walker.walkTo(x, y, z);
    }

    /**
     * Placeholder method for navigating to a specific city.
     * Currently does nothing.
     *
     * @param name The name of the city.
     */
    static public void goToCity(String name){
        // Implementation needed
    }

    /**
     * Gets a WorldPoint representing a specific tile within a given region ID.
     * Defaults to tile (1, 1) on plane 0 within that region.
     *
     * @param regionId The ID of the region.
     * @return A WorldPoint within the specified region.
     */
    static public WorldPoint getPointFromRegionId(int regionId)
    {
        return WorldPoint.fromRegion(regionId,1 ,1,0);
    }

    /**
     * Finds the WorldPoint from an array that is closest to the player's current location.
     *
     * @param points An array of WorldPoints to check.
     * @return The WorldPoint from the array closest to the player, or null if the input array is empty.
     */
    static public WorldPoint getClosestPointFromPlayer(WorldPoint[] points){
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return null;
        WorldPoint playerPoint = player.getWorldLocation();
        WorldPoint closestPoint = null;
        int closestDistance = Integer.MAX_VALUE;
        if (points == null || points.length == 0) {
            return null;
        }
        for (WorldPoint point : points) {
            int distance = playerPoint.distanceTo(point);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPoint = point;
            }
        }
        return closestPoint;
    }

    /**
     * Attempts to follow another player specified by their in-game name.
     *
     * @param name The name of the player to follow.
     * @return true if the follow action was initiated, false if the player was not found or couldn't be followed.
     */
    static public boolean followPlayerByName(String name){
        var player  = Rs2Player.getPlayer(name);
        return Rs2Player.follow(player);
    }

    /**
     * Finds an NPC or Object by name and interacts with the specified action.
     * Does not handle dialogue.
     *
     * @param name The name of the NPC or Object to interact with.
     * @param action The action to perform (e.g., "Trade", "Attack").
     * @return true if the interaction was successful, false otherwise.
     */
    static public boolean interactWith(String name, String action) {
        NPC npc = Rs2Npc.getNpc(name);
        if (npc != null) {
            Rs2Walker.walkTo(npc.getWorldLocation(),1);
            boolean interacted = Rs2Npc.interact(new Rs2NpcModel(npc), action);
            Rs2Player.waitForAnimation();
            sleep(500,1000);
            if (interacted) {
                Microbot.log(Level.INFO, "Interacted with NPC: " + name + " using action: " + action);
                return true;
            } else {
                Microbot.log(Level.WARN, "Failed to interact with NPC: " + name + " using action: " + action);
                return false;
            }

        }

        GameObject object = Rs2GameObject.getGameObject(obj -> {
            var compName = Rs2GameObject.convertToObjectComposition(obj).getName();
            if (compName == null) return false;
            return compName.equalsIgnoreCase(name);
        }, 20);
        assert object != null;
        Rs2Walker.walkTo(object.getWorldLocation(),1);
        var success = Rs2GameObject.interact(object, action);
        Rs2Player.waitForAnimation();
        sleep(500,1000);

        return success;
    }

    /**
     * Gets the available interaction options for a given NPC or Object by its name.
     * This method checks for NPCs first, then for GameObjects.
     *
     * @param name The name of the NPC or Object.
     * @return A comma-separated string of available actions (e.g., "Talk-to,Attack,Trade" or "Open,Examine").
     *         Returns "No actions available" if the entity is not found or has no actions.
     */
    static public String getInteractActions(String name){
        var npc = Rs2Npc.getNpc(name);
        List<String> actions = new ArrayList<>();
        if (npc != null){
            actions = Arrays.stream(npc.getComposition().getActions()).filter(Objects::nonNull).collect(Collectors.toList());
        }else{
            var object = Rs2GameObject.getGameObject(name);
            if (object != null){
                try{
                    actions = Arrays.stream(Rs2GameObject.convertToObjectComposition(object).getActions()).filter(Objects::nonNull).collect(Collectors.toList());
                }catch (Exception e){
                    Microbot.log(Level.WARN, "Failed to convert object to composition: " + name);
                }
            }
        }

        if (actions.isEmpty()){
            return "No actions available";
        }

        return actions.stream().filter(Objects::nonNull).collect(Collectors.joining(","));
    }

    /**
     * Finds an NPC by name, interacts with "Talk-to", and handles the initial dialogue.
     *
     * @param name The name of the NPC to talk to.
     * @return A DialogueResult containing the initial conversation, or null if the NPC wasn't found or dialogue didn't start.
     */
    static public DialogueResult talkToNpc(String name) {
        NPC npc = Rs2Npc.getNpc(name);
        if (npc == null) {
            throw new RuntimeException("NPC " + name + " not found");
        }
        boolean walkedTo = Rs2Walker.walkTo(npc.getWorldLocation(), 1);
        if (!walkedTo) {
            throw  new RuntimeException("Cannot walk to NPC " + name);
        }
        boolean interacted = Rs2Npc.interact(new Rs2NpcModel(npc), "Talk-to");
        if (!interacted) {
            throw new RuntimeException("Couldn't interact with NPC"); // Interaction failed
        }

        // Wait for dialogue to appear
        boolean dialogueStarted = sleepUntil(Rs2Dialogue::isInDialogue, 5000); // Wait up to 5 seconds
        if (!dialogueStarted) {
            throw  new RuntimeException("Couldn't start dialogue");
        }

        // Handle the dialogue that appears
        return handleDialogue();
    }


    /**
     * Finds an item on the ground by name within a default radius and interacts with it using the "Take" action.
     *
     * @param name The name of the item to pick up.
     * @return true if the item was found and the "Take" action was initiated, false otherwise.
     */
    static public boolean pickupGroundItem(String name) {
        return Rs2GroundItem.loot(name, 255); // 255 tile radius
    }

    /**
     * Helper class to store the result of handling a dialogue.
     */
    @Getter
    public static class DialogueResult {
        public final List<String> dialogueTexts;
        public final List<String> options;

        DialogueResult(List<String> dialogueTexts, List<String> options) {
            this.dialogueTexts = dialogueTexts != null ? dialogueTexts : Collections.emptyList();
            this.options = options != null ? options : Collections.emptyList();
        }

        public boolean hasOptions() {
            return !options.isEmpty();
        }
    }

    /**
     * Chooses the option passed in by index 1-n and then continues dialogue
     * @param option Index of option to choose
     * @return A DialogueResult containing conversation
     */
    static public DialogueResult chooseOptionAndContinueDialogue(int option){
        Rs2Dialogue.keyPressForDialogueOption(option);
        sleep(800,1200);
        return handleDialogue();
    }

    /**
     * Handles the flow of dialogue by clicking "continue" until options are presented or the dialogue ends.
     * Captures the text of each dialogue screen that is continued through, prefixed by the speaker's name.
     *
     * @return A DialogueResult containing the list of captured dialogue texts (prefixed) and the list of options if presented,
     *         or an empty list for options if the dialogue ended without choices. Returns null if not in dialogue.
     */
    static public DialogueResult handleDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return null; // Not in dialogue
        }

        List<String> dialogueTexts = new ArrayList<>();

        // Keep clicking continue while available
        while (Rs2Dialogue.hasContinue()) {
            String npcName = Rs2Dialogue.getNpcNameInDialogue();
            String playerText = Rs2Dialogue.getPlayerDialogueText();
            String generalText = Rs2Dialogue.getDialogueText(); // Fallback or for other types
            String speaker = "Game"; // Default speaker if not identified
            String currentText = null;

            if (npcName != null) {
                speaker = npcName;
                currentText = generalText;
            } else if (playerText != null) {
                speaker = "Player";
                currentText = playerText;
            } else if (generalText != null) {
                currentText = generalText;
            }

            if (currentText != null && !currentText.trim().isEmpty()) {
                String prefixedText = speaker + ": " + currentText;
                dialogueTexts.add(prefixedText);
                System.out.println(prefixedText);
            } else {
                 System.out.println("Could not capture dialogue text this iteration.");
            }


            Rs2Dialogue.clickContinue();
            sleep(500, 1000);
            sleepUntil(() -> !Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption() || !Rs2Dialogue.isInDialogue(), 2000);
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            List<String> options = Rs2Dialogue.getDialogueOptions().stream()
                    .map(Widget::getText)
                    .collect(Collectors.toList());
            return new DialogueResult(dialogueTexts, options);
        } else {
            String npcName = Rs2Dialogue.getNpcNameInDialogue();
            String playerText = Rs2Dialogue.getPlayerDialogueText();
            String generalText = Rs2Dialogue.getDialogueText();
            String speaker = "System";
            String finalText = null;

             if (npcName != null) {
                speaker = npcName;
                finalText = generalText;
            } else if (playerText != null) {
                speaker = "Player";
                finalText = playerText;
            } else if (generalText != null) {
                finalText = generalText;
            }

            if (finalText != null && !finalText.trim().isEmpty()) {
                 String prefixedText = speaker + ": " + finalText;
                 dialogueTexts.add(prefixedText);
                 System.out.println(prefixedText);
            }
            return new DialogueResult(dialogueTexts, Collections.emptyList());
        }
    }

    static public String checkQuestStatus(String questName){
        Rs2Tab.switchToQuestTab();
        sleep(100,200);
        var questBox = Rs2Widget.getWidget(ComponentID.QUEST_LIST_BOX);
        var searchQuests = questBox.getChild(0);
        Rs2Widget.clickWidget(searchQuests);
        sleepUntilTrue(()->Rs2Widget.isWidgetVisible(ComponentID.CHATBOX_CONTAINER), 100, 1000);
        Rs2Keyboard.typeString(questName);
        sleepUntilTrue(()->Rs2Widget.isWidgetVisible(399,7),100,1000);

        Rs2Widget.clickWidget(questName, Optional.of(399), 7, false);
        sleepUntilTrue(()->Rs2Widget.isWidgetVisible(119,5),100,1000);

        var widget = Rs2Widget.getWidget(119,5);
        if (widget == null) {
            Microbot.log(Level.ERROR,
                    "Quest log not opened");
            throw new RuntimeException("Quest log not opened");
        }
        List<String> texts =  new ArrayList<>();

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<Widget[]> childGroups = Stream.of(widget.getChildren(), widget.getNestedChildren(), widget.getDynamicChildren(), widget.getStaticChildren())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            for (Widget[] childGroup : childGroups) {
                if (childGroup != null) {
                    for (Widget nestedChild : Arrays.stream(childGroup).filter(w -> w != null && !w.isHidden()).collect(Collectors.toList())) {
                        String clean = Rs2UiHelper.stripColTags(nestedChild.getText());

                        if (!clean.isEmpty()){
                            texts.add(clean);
                        }
                    }
                }
            }
            return texts;
        });




        sleep(1000,2000);

        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleep(200,500);
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);

        return String.join("",texts);
    }

    /**
     * Retrieves the player's inventory contents using Rs2Inventory helper.
     *
     * @return A list of strings, where each string represents an inventory slot
     *         (e.g., "Slot 0: Item Name: Quantity" or "Slot 0: Empty slot").
     *         Returns a list with an error message if inventory cannot be accessed.
     */
    static public List<String> getPlayerInventory() {
        List<String> inventoryContents = new ArrayList<>();
        List<Rs2ItemModel> items = Rs2Inventory.items();

        if (items == null) {
            inventoryContents.add("Could not access inventory items via Rs2Inventory.");
            Microbot.log(Level.WARN, "getPlayerInventory: Rs2Inventory.items() returned null.");
            return inventoryContents;
        }

        for (int i = 0; i < 28; i++) {
                Rs2ItemModel itemModel = Rs2Inventory.getItemInSlot(i);
                if  (itemModel == null) continue;
                String itemName = itemModel.getName();
                int quantity = itemModel.getQuantity();
                inventoryContents.add("Slot " + i + ": " + itemName + ": " + quantity);
        }

        if (inventoryContents.isEmpty()) {
            inventoryContents.add("Inventory appears to be completely empty.");
        }

        return inventoryContents;
    }

    /**
     * Equips an item from the player's inventory by its name.
     *
     * @param itemName The name of the item to equip.
     * @return true if the equip action was successfully initiated, false otherwise.
     */
    static public boolean equipItem(String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            Microbot.log(Level.WARN, "equipItem: Item name is null or empty.");
            return false;
        }
        return Rs2Inventory.wear(itemName);
    }

    /**
     * Retrieves the items currently equipped by the player.
     *
     * @return A list of strings, where each string represents an equipment slot
     *         (e.g., "HEAD: Dragon helm" or "WEAPON: Empty slot").
     *         Returns a list with an error message if equipment cannot be accessed.
     */
    static public List<String> getEquippedItems() {
        List<String> equippedItems = new ArrayList<>();
        if (Microbot.getClient() == null || Microbot.getItemManager() == null) {
            equippedItems.add("Could not access client or item manager.");
            Microbot.log(Level.ERROR, "getEquippedItems: Client or ItemManager is null.");
            return equippedItems;
        }

        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
            Rs2ItemModel itemModel = Rs2Equipment.get(slot);
            String slotName = slot.name();

            if (itemModel != null) {
                String itemName = itemModel.getName();
                if (itemName == null || itemName.equalsIgnoreCase("null") || itemName.trim().isEmpty()) {
                    itemName = "Item ID " + itemModel.getId();
                }
                equippedItems.add(slotName + ": " + itemName);
            } else {
                equippedItems.add(slotName + ": Empty slot");
            }
        }

        if (equippedItems.isEmpty()) {
            equippedItems.add("Equipment appears to be completely empty or inaccessible.");
        }

        return equippedItems;
    }

    /**
     * Retrieves a list of nearby game objects (e.g., trees, rocks, doors) and NPCs (e.g. Man, Guard, Goblin).
     *
     * @return A string describing nearby objects and NPCs, with their names.
     *         Example: "Objects: Tree, Rock\nNPCs: Man, Guard"
     */
    static public String getNearbyObjectsAndNpcs() {
        List<String> collectedObjectNames = Rs2GameObject.getAll(o -> true, 20)
                .stream()
                .map(obj -> {
                    ObjectComposition comp = Rs2GameObject.convertToObjectComposition(obj);
                    return comp != null ? comp.getName() : null;
                })
                .filter(name -> name != null && !name.equalsIgnoreCase("null") && !name.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        List<String> collectedNpcNames = Rs2Npc.getNpcs() // Stream<Rs2NpcModel>
                .map(Rs2NpcModel::getName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        String objectsOutput;
        if (collectedObjectNames.isEmpty()) {
            objectsOutput = "No valid game objects found nearby.";
        } else {
            objectsOutput = String.join(", ", collectedObjectNames);
        }

        String npcsOutput;
        if (collectedNpcNames.isEmpty()) {
            npcsOutput = "No valid NPCs found nearby.";
        } else {
            npcsOutput = String.join(", ", collectedNpcNames);
        }

        return "Objects: " + objectsOutput + "\nNPCs: " + npcsOutput;
    }



    /**
     * Finds the nearest accessible bank within a 500-tile radius and returns its location and name.
     *
     * @return A string describing the nearest bank, or an error/not found message.
     */
    static public String getNearestBank() {
        Player player = Microbot.getClient().getLocalPlayer();
        if (Microbot.getClient() == null || player == null || player.getWorldLocation() == null) {
            return "Error: Client or player not available to determine current location.";
        }
        BankLocation nearestBank = Rs2Bank.getNearestBank(player.getWorldLocation(), 500);
        if (nearestBank != null) {
            WorldPoint bankPoint = nearestBank.getWorldPoint();
            return "Nearest bank found at (" + bankPoint.getX() + ", " + bankPoint.getY() + ", " + bankPoint.getPlane() + ").";
        } else {
            return "No accessible bank location found nearby within 500 tiles.";
        }
    }

    /**
     * Opens the bank interface.
     *
     * @return A string indicating whether the bank was opened successfully.
     */
    static public String openBank() {
        if (Rs2Bank.isOpen()) {
            return "Bank is already open.";
        }
        boolean success = Rs2Bank.openBank();
        if (success) {
            boolean isOpen = sleepUntil(Rs2Bank::isOpen, 5000);
            if (!isOpen) {
                return "Failed to open bank";
            }
            var bankItems = Rs2Bank.bankItems();
            StringBuilder bankContents = new StringBuilder();
            if (bankItems.isEmpty()) {
                return "Bank opened succesfully, bank is empty";
            }
            for (Rs2ItemModel item : bankItems) {
                bankContents.append(item.getName()).append("- qty: ").append(item.getQuantity()).append("\n");
            }
            return  "Bank opened successfully. \n Bank contents:\n" + bankContents;
        } else {
            return "Failed to initiate bank opening (e.g., no bank nearby or interaction failed).";
        }
    }

    /**
     * Closes the bank interface.
     *
     * @return A string indicating whether the bank was closed successfully.
     */
    static public String closeBank() {
        if (!Rs2Bank.isOpen()) {
            return "Bank is already closed.";
        }
        boolean success = Rs2Bank.closeBank();
        return success ? "Bank closed successfully." : "Failed to close bank (or was not open).";
    }

    /**
     * Deposits a specific quantity of an item from the inventory into the bank.
     *
     * @param itemName The name of the item to deposit.
     * @param quantity The amount of the item to deposit.
     * @return A string indicating the result of the deposit attempt.
     */
    static public String depositXItems(String itemName, int quantity) {
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
        }
        if (itemName == null || itemName.trim().isEmpty()) {
            return "Failed to deposit: Item name is invalid.";
        }
        if (quantity <= 0) {
            return "Failed to deposit: Quantity must be positive.";
        }
        if (!Rs2Inventory.hasItem(itemName)) {
            return "Failed to deposit: Item '" + itemName + "' not found in inventory.";
        }

        Rs2Bank.depositX(itemName, quantity);
        sleep(600, 1000);
        return "Attempted to deposit " + quantity + " of '" + itemName + "'.";
    }

    /**
     * Withdraws a specific quantity of an item from the bank into the inventory.
     *
     * @param itemName The name of the item to withdraw.
     * @param quantity The amount of the item to withdraw.
     * @return A string indicating the result of the withdrawal attempt.
     */
    static public String withdrawXItems(String itemName, int quantity) {
        if (!Rs2Bank.isOpen()) {
            return "Failed to withdraw: Bank is not open.";
        }
        if (itemName == null || itemName.trim().isEmpty()) {
            return "Failed to withdraw: Item name is invalid.";
        }
        if (quantity <= 0) {
            return "Failed to withdraw: Quantity must be positive.";
        }
        if (!Rs2Bank.hasItem(itemName)) {
             return "Failed to withdraw: Bank does not have " + quantity + " of '" + itemName + "'.";
        }
        if (Rs2Inventory.isFull() && Rs2Bank.getBankItem(itemName) != null && !Rs2Bank.getBankItem(itemName).isStackable() && !Rs2Inventory.hasItem(itemName)) {
            return "Failed to withdraw: Inventory is full and item is not stackable/already in inventory.";
        }

        boolean success = Rs2Bank.withdrawX(itemName, quantity);
        if (success) {
            Rs2ItemModel bankItem = Rs2Bank.getBankItem(itemName);
            boolean itemReceived;
            if (bankItem != null && bankItem.isStackable()) {
                itemReceived = sleepUntil(() -> Rs2Inventory.hasItemAmount(itemName, quantity, true), 5000);
            } else {
                itemReceived = sleepUntil(() -> Rs2Inventory.hasItem(itemName), 5000);
            }
            return itemReceived ? "Successfully withdrew " + quantity + " of '" + itemName + "'." : "Withdrawal action sent, but item not confirmed in inventory with specified quantity.";
        } else {
            return "Failed to withdraw " + quantity + " of '" + itemName + "' (e.g., item not found in bank, inventory full, or other issue).";
        }
    }
}
