package net.runelite.client.plugins.microbot.rsagent.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.openai.models.ChatModel;
import com.openai.models.responses.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import static java.util.stream.Collectors.toList;

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

        List<ResponseInputItem> inputItems = new ArrayList<>();
        inputItems.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.SYSTEM)
                .content(systemInstruction)
                .build()));

        inputItems.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER)
                .content(task)
                .build()));

        ResponseCreateParams createParams = ResponseCreateParams.builder()
                .inputOfResponse(inputItems)
                .model(ChatModel.GPT_4_1_MINI) // Consider making model configurable
                .build();

        for (int step = 0; step < 15; step++) { // Increased max steps slightly
            log.info("Agent Step {}/15", step + 1);
            List<ResponseOutputMessage> messages;
            String toolResult = "No action performed."; // Default result if parsing fails or no action taken

            try {
                 messages = openAIClient.responses().create(createParams).output().stream()
                        .flatMap(item -> item.message().stream())
                        .collect(toList());
            } catch (Exception e) {
                log.error("Error calling OpenAI API: {}", e.getMessage(), e);
                toolResult = "Error: Failed to get response from LLM.";
                break; // Exit loop on API error
            }


            String fullText = messages.stream()
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(ResponseOutputText::text)
                    .collect(java.util.stream.Collectors.joining("\n"));

            log.debug("Agent step {} output:\n{}", step + 1, fullText);
            System.out.println("Agent step " + (step + 1) + " output:\n" + fullText); // Keep console output for now
            System.out.println("\n-----------------------------------\n");

            // Add LLM's response to the conversation history *before* processing actions
            messages.forEach(message -> inputItems.add(ResponseInputItem.ofResponseOutputMessage(message)));


            // Check for finish action before attempting tool execution
            if (fullText.contains("\"action\": \"finish\"")) {
                 log.info("Agent received finish action.");
                 break; // Exit the loop
            }

            // Tool execution
            try {
                // Basic cleanup: Sometimes models wrap JSON in ```json ... ```
                String cleanedJson = fullText.trim();
                if (cleanedJson.startsWith("```json")) {
                    cleanedJson = cleanedJson.substring(7);
                }
                if (cleanedJson.endsWith("```")) {
                    cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
                }
                cleanedJson = cleanedJson.trim();

                JsonObject ob = gson.fromJson(cleanedJson, JsonObject.class);
                if (ob == null || !ob.has("action") || !ob.has("action_parameters")) {
                     throw new JsonSyntaxException("Missing 'action' or 'action_parameters' in JSON response");
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
                    case "finish": // Should be caught earlier, but handle defensively
                        toolResult = "Finish action acknowledged.";
                        log.info("Finish action processed in switch, loop should terminate.");
                        break; // Exit switch
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

            // Feed back tool output *after* processing the action
            inputItems.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(EasyInputMessage.Role.USER) // Using USER role to provide observation/result
                    .content("Tool result: " + toolResult)
                    .build()));

            // Update createParams for the next iteration
            createParams = createParams.toBuilder().inputOfResponse(inputItems).build();

             // Check again if the last action was finish, to ensure loop termination
             if (toolResult.startsWith("Finish action acknowledged.")) {
                 break;
             }
        }
        log.info("Agent run finished.");
    }

    private static String loadPrompt() {
        // It's good practice to specify the resource path relative to the classpath root
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
