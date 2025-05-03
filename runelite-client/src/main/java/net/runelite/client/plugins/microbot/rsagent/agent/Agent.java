package net.runelite.client.plugins.microbot.rsagent.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
                .model(ChatModel.GPT_4_1_MINI)
                .build();

        for (int step = 0; step < 8; step++) {
            List<ResponseOutputMessage> messages = openAIClient.responses().create(createParams).output().stream()
                    .flatMap(item -> item.message().stream())
                    .collect(toList());

            String fullText = messages.stream()
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(ResponseOutputText::text)
                    .collect(java.util.stream.Collectors.joining("\n"));

            System.out.println("Agent step " + (step + 1) + " output:\n" + fullText);
            System.out.println("\n-----------------------------------\n");

            if (fullText.contains("\"action\": \"finish\"")) break;

            // Tool execution
            String toolResult;
            try {
                var ob = gson.fromJson(fullText, JsonObject.class);
                String action = ob.get("action").getAsString();
                JsonObject parameters = ob.getAsJsonObject("action_parameters");

                switch (action) {
                    case "walkTo":
                        toolResult =  RsAgentTools.walkTo(parameters.get("x").getAsInt(),
                            parameters.get("y").getAsInt(),
                            parameters.get("z").getAsInt()) ? "Walk successful" : "Walk failed";

                    default: toolResult = "Unknown tool: " + action;
                };
            } catch (Exception e) {
                toolResult = "Error parsing agent output: " + e.getMessage();
                e.printStackTrace();
            }

            // Add LLM output to context
            messages.forEach(message -> inputItems.add(ResponseInputItem.ofResponseOutputMessage(message)));

            // Feed back tool output
            inputItems.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(EasyInputMessage.Role.USER)
                    .content("Tool result: " + toolResult)
                    .build()));

            createParams = createParams.toBuilder().inputOfResponse(inputItems).build();
        }
    }

    private static String loadPrompt() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Agent.class.getClassLoader().getResourceAsStream("rsagent/SystemPrompt.txt"),
                StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load system prompt: " + "rsagent/SystemPrompt.txt", e);
        }
    }

}
