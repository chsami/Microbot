package net.runelite.client.plugins.microbot.rsagent.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.rsagent.RsAgentPlugin;
import net.runelite.client.plugins.microbot.rsagent.util.WikiScraper;

@Slf4j
public class Agent {
    private static final Gson gson = new Gson();

    private OpenAIClient openAIClient;
    private String apiKey;
    public int currentStep = 0;
    public String currentAction;
    public String currentThought;

    public Agent(String apiKey) {
        this.apiKey = apiKey;
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    /*
     * Sets new api key and rebuild openAI client to use it
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public void run(String task) {
        String baseSystemInstruction = loadPrompt();
        StringBuilder augmentedSystemInstruction = new StringBuilder(baseSystemInstruction);
        this.currentThought = "Initializing and gathering initial player status...";

        // Get player inventory
        List<String> inventoryList = RsAgentTools.getPlayerInventory();
        String inventoryInfo;
        if (inventoryList.isEmpty()
                || (inventoryList.size() == 1 && inventoryList.get(0).startsWith("Could not access"))) {
            inventoryInfo = "Could not retrieve inventory contents at the start of the task.";
            log.warn("Initial getPlayerInventory tool failed: {}",
                    inventoryList.isEmpty() ? "Empty list" : inventoryList.get(0));
        } else {
            inventoryInfo = "Current Inventory contents:\n" + String.join("\n", inventoryList);
        }

        // Get equipped items
        List<String> equippedList = RsAgentTools.getEquippedItems();
        String equippedInfo;
        if (equippedList.isEmpty()
                || (equippedList.size() == 1 && equippedList.get(0).startsWith("Could not access"))) {
            equippedInfo = "Could not retrieve equipped items at the start of the task.";
            log.warn("Initial getEquippedItems tool failed: {}",
                    equippedList.isEmpty() ? "Empty list" : equippedList.get(0));
        } else {
            equippedInfo = "Current Equipped items:\n" + String.join("\n", equippedList);
        }

        augmentedSystemInstruction.append("\n\nInitial Player Status (provided at the beginning of the task):\n");
        augmentedSystemInstruction.append(inventoryInfo).append("\n");
        augmentedSystemInstruction.append(equippedInfo);

        String finalSystemInstruction = augmentedSystemInstruction.toString();
        log.debug("Augmented System Prompt:\n{}", finalSystemInstruction);

        boolean done = false;

        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_MINI)
                .maxCompletionTokens(512) // Increased token limit for potentially complex JSON outputs
                .stopOfStrings(Arrays.asList("}}", "}\n}")) // Add stop sequence for the JSON closing brackets
                .temperature(0)
                .addSystemMessage(finalSystemInstruction) // Use the augmented prompt
                .addUserMessage(task);

        for (int step = 0; step < 100; step++) {
            currentStep = step;
            if (!Microbot.isLoggedIn())
                return;

            if (done) {
                break;
            }
            log.info("Agent Step {}/15", step + 1);
            String toolResult = "No action performed.";
            this.currentThought = "Thinking about the next action...";

            ChatCompletion chatCompletion;
            try {
                chatCompletion = openAIClient.chat().completions().create(paramsBuilder.build());
            } catch (Exception e) {
                log.error("Error calling OpenAI API: {}", e.getMessage(), e);
                toolResult = "Error: Failed to get response from LLM.";
                paramsBuilder.addUserMessage("Tool result: " + toolResult); // Add error to history
                break;
            }

            if (chatCompletion.choices().isEmpty()) {
                log.error("No choices returned from OpenAI API.");
                toolResult = "Error: No response from LLM (no choices).";
                paramsBuilder.addUserMessage("Tool result: " + toolResult); // Add error to history
                break;
            }

            ChatCompletionMessage assistantResponse = chatCompletion.choices().get(0).message();
            String fullText = assistantResponse.content().orElse("").trim();

            if (chatCompletion.choices().get(0).finishReason().equals(ChatCompletion.Choice.FinishReason.STOP)
                    && !fullText.endsWith("}}")) {
                if (fullText.endsWith("}")) {
                    fullText += "}";
                } else {
                    fullText += "}}";
                }
            }

            log.debug("Agent step {} output:\n{}", step + 1, fullText);
            System.out.println("Agent step " + (step + 1) + " output:\n" + fullText);
            System.out.println("\n-----------------------------------\n");

            paramsBuilder.addMessage(assistantResponse);

            try {
                String cleanedJson = fullText;
                if (cleanedJson.startsWith("```json")) {
                    cleanedJson = cleanedJson.substring(7);
                }
                if (cleanedJson.endsWith("```")) {
                    cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
                }
                cleanedJson = cleanedJson.trim();

                JsonObject ob = gson.fromJson(cleanedJson, JsonObject.class);
                if (ob == null || !ob.has("action") || !ob.has("action_parameters")) {
                    throw new JsonSyntaxException(
                            "Missing 'action' or 'action_parameters' in JSON response. LLM Output: " + fullText);
                }

                String action = ob.get("action").getAsString();
                JsonObject parameters = ob.getAsJsonObject("action_parameters");

                currentAction = "Executing action " + action + " with parameters: " + parameters;
                if (ob.has("thought") && ob.get("thought").isJsonPrimitive()
                        && ob.get("thought").getAsJsonPrimitive().isString()) {
                    this.currentThought = ob.get("thought").getAsString();
                } else {
                    this.currentThought = "Preparing to execute: " + action;
                }
                log.info(currentAction);
                switch (action) {
                    case "walkTo": {
                        int x = parameters.get("x").getAsInt();
                        int y = parameters.get("y").getAsInt();
                        int z = parameters.get("z").getAsInt();
                        boolean success = RsAgentTools.walkTo(x, y, z);
                        toolResult = success ? "Successfully initiated walk to (" + x + ", " + y + ", " + z + ")."
                                : "Failed to initiate walk to (" + x + ", " + y + ", " + z + ").";
                        break;
                    }
                    case "interactWith": {
                        String targetName = parameters.get("name").getAsString();
                        String interactionAction = parameters.get("action").getAsString();
                        Integer x = parameters.has("x") ? parameters.get("x").getAsInt() : null;
                        Integer y = parameters.has("y") ? parameters.get("y").getAsInt() : null;
                        Integer z = parameters.has("z") ? parameters.get("z").getAsInt() : null;

                        boolean success = RsAgentTools.interactWith(targetName, interactionAction, x, y, z);
                        String locationInfo = (x != null && y != null && z != null)
                                ? " at (" + x + ", " + y + ", " + z + ")"
                                : "";
                        toolResult = success
                                ? "Successfully interacted with '" + targetName + "'" + locationInfo + " using action '"
                                        + interactionAction + "'."
                                : "Failed to interact with '" + targetName + "'" + locationInfo + " using action '"
                                        + interactionAction + "'. Might not be present or interaction is invalid.";
                        break;
                    }
                    case "getInteractActions": {
                        String targetName = parameters.get("name").getAsString();
                        toolResult = RsAgentTools.getInteractActions(targetName);
                        break;
                    }
                    case "talkToNpc": {
                        String npcName = parameters.get("name").getAsString();
                        RsAgentTools.DialogueResult result = RsAgentTools.talkToNpc(npcName);
                        if (result != null) {
                            toolResult = "Initiated conversation with '" + npcName + "'. ";
                            if (!result.dialogueTexts.isEmpty()) {
                                toolResult += "Initial dialogue: [" + String.join(" | ", result.dialogueTexts) + "]. ";
                            }
                            if (result.hasOptions()) {
                                toolResult += "Presented options: [" + String.join(" | ", result.options) + "]";
                            } else {
                                toolResult += "Dialogue ended or waiting for next step.";
                            }
                        } else {
                            toolResult = "Failed to talk to NPC '" + npcName
                                    + "' (NPC not found, interaction failed, or dialogue did not start).";
                        }
                        break;
                    }
                    case "pickupGroundItem": {
                        String itemName = parameters.get("name").getAsString();
                        boolean success = RsAgentTools.pickupGroundItem(itemName);
                        toolResult = success ? "Successfully attempted to pick up item: " + itemName + "."
                                : "Failed to find or pick up item: " + itemName + ".";
                        break;
                    }
                    case "chooseOptionAndContinueDialogue": {
                        int optionIndex = parameters.get("option").getAsInt();
                        RsAgentTools.DialogueResult result = RsAgentTools.chooseOptionAndContinueDialogue(optionIndex);
                        if (result != null) {
                            toolResult = "Chose dialogue option " + optionIndex + ". ";
                            if (!result.dialogueTexts.isEmpty()) {
                                toolResult += "Dialogue continued: [" + String.join(" | ", result.dialogueTexts)
                                        + "]. ";
                            }
                            if (result.hasOptions()) {
                                toolResult += "New options: [" + String.join(" | ", result.options) + "]";
                            } else {
                                toolResult += "Dialogue ended or waiting for next step.";
                            }
                        } else {
                            toolResult = "Failed to choose dialogue option " + optionIndex
                                    + " (maybe not in dialogue or invalid option).";
                        }
                        break;
                    }
                    case "handleDialogue": {
                        RsAgentTools.DialogueResult result = RsAgentTools.handleDialogue();
                        if (result != null) {
                            toolResult = "Handling dialogue. ";
                            if (!result.dialogueTexts.isEmpty()) {
                                toolResult += "Dialogue text: [" + String.join(" | ", result.dialogueTexts) + "]. ";
                            }
                            if (result.hasOptions()) {
                                toolResult += "Presented options: [" + String.join(" | ", result.options) + "]";
                            } else {
                                toolResult += "Dialogue ended or waiting for next step.";
                            }
                        } else {
                            toolResult = "Not currently in dialogue.";
                        }
                        break;
                    }
                    case "checkQuestStatus": {
                        String questName = parameters.get("questName").getAsString();
                        String status = RsAgentTools.checkQuestStatus(questName);
                        toolResult = "Quest status for '" + questName + "': " + status;
                        break;
                    }
                    case "getClosestNpcSpawn": {
                        String npcName = parameters.get("npcName").getAsString();
                        try {
                            WorldPoint closestSpawn = RsAgentTools.getClosestNpcSpawnLocation(npcName);
                            if (closestSpawn != null) {
                                toolResult = "Closest spawn for '" + npcName + "' is at (" + closestSpawn.getX() + ", "
                                        + closestSpawn.getY() + ", " + closestSpawn.getPlane() + ").";
                            } else {
                                toolResult = "No spawn location found for NPC '" + npcName + "' or NPC not in data.";
                            }
                        } catch (RuntimeException e) {
                            toolResult = "Error processing NPC spawn location for '" + npcName + "': " + e.getMessage();
                            log.error("Error in getClosestNpcSpawn tool: {}", e.getMessage());
                        }
                        break;
                    }
                    case "getPlayerInventory": {
                        List<String> inventoryListResult = RsAgentTools.getPlayerInventory();
                        if (inventoryListResult.isEmpty() || (inventoryListResult.size() == 1
                                && inventoryListResult.get(0).startsWith("Could not access"))) {
                            toolResult = "Error: Could not retrieve inventory contents.";
                            log.warn("getPlayerInventory tool failed: {}",
                                    inventoryListResult.isEmpty() ? "Empty list" : inventoryListResult.get(0));
                        } else {
                            toolResult = "Inventory contents:\n" + String.join("\n", inventoryListResult);
                        }
                        break;
                    }
                    case "equipItem": {
                        String itemName = parameters.get("itemName").getAsString();
                        boolean success = RsAgentTools.equipItem(itemName);
                        toolResult = success ? "Successfully equipped '" + itemName + "'."
                                : "Failed to equip '" + itemName
                                        + "'. Item might not be in inventory or is not equippable.";
                        break;
                    }
                    case "getEquippedItems": {
                        List<String> equippedListResult = RsAgentTools.getEquippedItems();
                        if (equippedListResult.isEmpty() || (equippedListResult.size() == 1
                                && equippedListResult.get(0).startsWith("Could not access"))) {
                            toolResult = "Error: Could not retrieve equipped items.";
                            log.warn("getEquippedItems tool failed: {}",
                                    equippedListResult.isEmpty() ? "Empty list" : equippedListResult.get(0));
                        } else {
                            toolResult = "Equipped items:\n" + String.join("\n", equippedListResult);
                        }
                        break;
                    }
                    case "getNearbyObjectsAndNpcs": {
                        toolResult = RsAgentTools.getNearbyObjectsAndNpcs();
                        break;
                    }
                    case "getNearestBank": {
                        toolResult = RsAgentTools.getNearestBank();
                        break;
                    }
                    case "openBank": {
                        toolResult = RsAgentTools.openBank();
                        break;
                    }
                    case "closeBank": {
                        toolResult = RsAgentTools.closeBank();
                        break;
                    }
                    case "depositXItems": {
                        String itemName = parameters.get("itemName").getAsString();
                        int quantity = parameters.get("quantity").getAsInt();
                        toolResult = RsAgentTools.depositXItems(itemName, quantity);
                        break;
                    }
                    case "withdrawXItems": {
                        String itemName = parameters.get("itemName").getAsString();
                        int quantity = parameters.get("quantity").getAsInt();
                        toolResult = RsAgentTools.withdrawXItems(itemName, quantity);
                        break;
                    }
                    case "getLocationCoords": {
                        String locationName = parameters.get("locationName").getAsString();
                        // RsAgentTools.getLocationCoords is now expected to return a String.
                        // This string will either be the coordinates, a "Did you mean?" suggestion,
                        // or a "not found" message, all pre-formatted by
                        // RsAgentTools.getLocationCoords.
                        toolResult = RsAgentTools.getLocationCoords(locationName);
                        break;
                    }
                    case "buyInGrandExchange": {
                        toolResult = RsAgentTools.buyInGrandExchange(parameters.get("itemName").getAsString(),
                                parameters.get("quantity").getAsInt());
                        break;
                    }
                    case "combine": {
                        toolResult = RsAgentTools.combine(parameters.get("item1").getAsString(),
                                parameters.get("item2").getAsString());
                        break;
                    }
                    case "getWikiPageContent": {
                        String pageTitle = parameters.get("pageTitle").getAsString();
                        toolResult = WikiScraper.fetchWikiPageContent(pageTitle);
                        break;
                    }
                    case "finish": {
                        String finishResponse = "Task finished.";
                        if (parameters.has("response") && parameters.get("response").isJsonPrimitive()
                                && parameters.get("response").getAsJsonPrimitive().isString()) {
                            finishResponse = parameters.get("response").getAsString();
                        }
                        toolResult = "Finish action acknowledged by agent: " + finishResponse;
                        this.currentThought = "Task finished: " + finishResponse;
                        log.info("Finish action processed: {}", finishResponse);
                        done = true;
                        break;
                    }
                    default:
                        toolResult = "Unknown tool requested: " + action;
                        log.warn("Unknown tool requested: {}", action);
                        break;
                }
            } catch (JsonSyntaxException e) {
                toolResult = "Error: LLM response was not valid JSON or missing required fields. Response: " + fullText;
                log.error("Error parsing agent JSON output: {}", e.getMessage());
            } catch (Exception e) {
                toolResult = "Error executing tool or parsing parameters: " + e.getMessage();
                log.error("Error during tool execution/parameter parsing: {}", e.getMessage(), e);
            }

            String capturedGameMessages = RsAgentPlugin.getAndClearGameMessages();
            System.out.println(capturedGameMessages);
            if (capturedGameMessages != null && !capturedGameMessages.isEmpty()) {
                toolResult += "\n" + capturedGameMessages;
            }

            log.info("Tool Result: {}", toolResult);
            paramsBuilder.addUserMessage("Tool result: " + toolResult);

            if (done) {
                break;
            }
        }
        this.currentThought = "Agent run finished.";
        log.info("Agent run finished.");
    }

    private static String loadPrompt() {
        String promptPath = "rsagent/SystemPrompt.txt";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Agent.class.getClassLoader().getResourceAsStream(promptPath),
                StandardCharsets.UTF_8))) {
            if (reader == null) {
                throw new RuntimeException("Failed to load system prompt: Resource not found at " + promptPath);
            }
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load system prompt: " + promptPath, e);
        }
    }

}
