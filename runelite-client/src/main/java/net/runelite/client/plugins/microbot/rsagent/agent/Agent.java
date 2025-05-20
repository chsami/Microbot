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

        // Initialize the tool registry when the agent is created
        ToolRegistry.initialize();
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

        // Get player skill levels
        List<String> skillList = RsAgentTools.getPlayerSkills();
        String skillInfo;
        if (skillList.isEmpty()) {
            skillInfo = "Could not retrieve player skill levels at the start of the task.";
            log.warn("Initial getPlayerSkills tool failed: Empty list");
        } else {
            skillInfo = "Current Skill Levels:\n" + String.join("\n", skillList);
        }

        augmentedSystemInstruction.append("\n\nInitial Player Status (provided at the beginning of the task):\n");
        augmentedSystemInstruction.append(inventoryInfo).append("\n");
        augmentedSystemInstruction.append(equippedInfo).append("\n");
        augmentedSystemInstruction.append(skillInfo);

        String finalSystemInstruction = augmentedSystemInstruction.toString();
        log.debug("Augmented System Prompt:\n{}", finalSystemInstruction);

        boolean done = false;

        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1)
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

                // Use the ToolRegistry to execute the tool instead of the switch statement
                toolResult = ToolRegistry.executeTool(action, parameters);

                // Special handling for "finish" action
                if ("finish".equals(action)) {
                    done = true;
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
        // Get the dynamically generated prompt from the PromptGenerator
        return PromptGenerator.generateToolDocumentation();
    }

}
