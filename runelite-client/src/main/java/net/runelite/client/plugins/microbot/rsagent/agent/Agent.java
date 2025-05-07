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

@Slf4j
public class Agent {
    private static final Gson gson = new Gson();

    private OpenAIClient openAIClient;
    private String apiKey;
    public Agent(String apiKey) {
        this.apiKey = apiKey;
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    /*
    Sets new api key and rebuild openAI client to use it
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public void run(String task){
        String systemInstruction = loadPrompt();
        boolean done = false;

        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1)
                .maxCompletionTokens(512) // Increased token limit for potentially complex JSON outputs
                .stopOfStrings(Arrays.asList("}}", "}\n}")) // Add stop sequence for the JSON closing brackets
                .temperature(0)
                .addSystemMessage(systemInstruction)
                .addUserMessage(task);

        for (int step = 0; step < 15; step++) {
            if (done){
                break;
            }
            log.info("Agent Step {}/15", step + 1);
            String toolResult = "No action performed.";

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

            // If the model stopped due to the stop sequence, it might not include the sequence itself.
            // We append it here to ensure the JSON is complete for parsing.
            if (chatCompletion.choices().get(0).finishReason().equals(ChatCompletion.Choice.FinishReason.STOP) && !fullText.endsWith("}}")) {
                if (fullText.endsWith("}") && !fullText.endsWith("}}")) {
                    fullText += "}";
                } else if (!fullText.endsWith("}")) {
                    // This case is less likely if the model is trying to output JSON, but as a fallback.
                    // It might indicate a more significant formatting issue.
                    log.warn("LLM output did not end with '}' before stop sequence. Appending '}}'. Output: {}", fullText);
                    fullText += "}}";
                }
            }


            log.debug("Agent step {} output:\n{}", step + 1, fullText);
            System.out.println("Agent step " + (step + 1) + " output:\n" + fullText);
            System.out.println("\n-----------------------------------\n");

            // Add LLM's response to the conversation history
            paramsBuilder.addMessage(assistantResponse);

            // Tool execution
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
                     throw new JsonSyntaxException("Missing 'action' or 'action_parameters' in JSON response. LLM Output: " + fullText);
                }

                String action = ob.get("action").getAsString();
                JsonObject parameters = ob.getAsJsonObject("action_parameters");

                log.info("Executing action: {} with parameters: {}", action, parameters);

                switch (action) {
                    case "walkTo": {
                        int x = parameters.get("x").getAsInt();
                        int y = parameters.get("y").getAsInt();
                        int z = parameters.get("z").getAsInt();
                        boolean success = RsAgentTools.walkTo(x, y, z);
                        toolResult = success ? "Successfully initiated walk to (" + x + ", " + y + ", " + z + ")." : "Failed to initiate walk to (" + x + ", " + y + ", " + z + ").";
                        break;
                    }
                    case "interactWith": {
                        String targetName = parameters.get("name").getAsString();
                        String interactionAction = parameters.get("action").getAsString();
                        boolean success = RsAgentTools.interactWith(targetName, interactionAction);
                        toolResult = success ? "Successfully interacted with '" + targetName + "' using action '" + interactionAction + "'." : "Failed to interact with '" + targetName + "' using action '" + interactionAction + "'. Target might not be present or interaction invalid.";
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
                            toolResult = "Failed to talk to NPC '" + npcName + "' (NPC not found, interaction failed, or dialogue did not start).";
                        }
                        break;
                    }
                    case "pickupGroundItem": {
                        String itemName = parameters.get("name").getAsString();
                        boolean success = RsAgentTools.pickupGroundItem(itemName);
                        toolResult = success ? "Successfully attempted to pick up item: " + itemName + "." : "Failed to find or pick up item: " + itemName + ".";
                        break;
                    }
                    case "chooseOptionAndContinueDialogue": {
                        int optionIndex = parameters.get("option").getAsInt();
                        RsAgentTools.DialogueResult result = RsAgentTools.chooseOptionAndContinueDialogue(optionIndex);
                        if (result != null) {
                            toolResult = "Chose dialogue option " + optionIndex + ". ";
                            if (!result.dialogueTexts.isEmpty()) {
                                toolResult += "Dialogue continued: [" + String.join(" | ", result.dialogueTexts) + "]. ";
                            }
                            if (result.hasOptions()) {
                                toolResult += "New options: [" + String.join(" | ", result.options) + "]";
                            } else {
                                toolResult += "Dialogue ended or waiting for next step.";
                            }
                        } else {
                            toolResult = "Failed to choose dialogue option " + optionIndex + " (maybe not in dialogue or invalid option).";
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
                                toolResult = "Closest spawn for '" + npcName + "' is at (" + closestSpawn.getX() + ", " + closestSpawn.getY() + ", " + closestSpawn.getPlane() + ").";
                            } else {
                                toolResult = "No spawn location found for NPC '" + npcName + "' or NPC not in data.";
                            }
                        } catch (RuntimeException e) {
                            toolResult = "Error processing NPC spawn location for '" + npcName + "': " + e.getMessage();
                            log.error("Error in getClosestNpcSpawn tool: {}", e.getMessage());
                        }
                        break;
                    }
                    case "finish": {
                        String finishResponse = "Task finished."; // Default
                        if (parameters.has("response") && parameters.get("response").isJsonPrimitive() && parameters.get("response").getAsJsonPrimitive().isString()) {
                            finishResponse = parameters.get("response").getAsString();
                        }
                        toolResult = "Finish action acknowledged by agent: " + finishResponse;
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

            log.info("Tool Result: {}", toolResult);

            paramsBuilder.addUserMessage("Tool result: " + toolResult);

            if (done) {
                 break;
            }
        }
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
