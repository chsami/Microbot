package net.runelite.client.plugins.microbot.rsagent.agent;

import com.google.gson.Gson;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.messages.Message;
import com.openai.client.threads.ThreadRequest;
import com.openai.client.threads.ThreadResponse;
import com.openai.client.threads.Runs;
import com.openai.client.threads.RunRequest;
import com.openai.client.threads.RunResponse;
import com.openai.client.threads.RunStep;
import com.openai.client.threads.StepListResponse;
import com.openai.client.threads.MessageListResponse;
import com.openai.client.threads.MessageRequest;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Agent {
    private OpenAIClient openAIClient;
    private List<Tool> tools;
    private List<String> conversationHistory;
    private String threadId;
    private String assistantId;

    public Agent(String apiKey, List<Tool> tools, String assistantId) {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.tools = tools;
        this.conversationHistory = new ArrayList<>();
        this.assistantId = assistantId;
        this.threadId = createThread();
    }

    private String createThread() {
        ThreadResponse thread = openAIClient.threads().createThread(ThreadRequest.builder().build());
        return thread.id();
    }

    public String run(String goal) {
        conversationHistory.add("User: " + goal);
        String llmResponse = getLLMResponse(goal);
        conversationHistory.add("Assistant: " + llmResponse);
        return llmResponse;
    }

    private String getLLMResponse(String input) {
        // 1. Create a message
        MessageRequest messageRequest = MessageRequest.builder()
                .role("user")
                .content(input)
                .build();

        Message message = openAIClient.threads().createMessage(threadId, messageRequest);

        // 2. Create a run
        RunRequest runRequest = RunRequest.builder()
                .assistantId(assistantId)
                .build();

        RunResponse run = openAIClient.threads().createRun(threadId, runRequest);

        // 3. Check the run status and handle tool calls
        while (!run.status().equals("completed") && !run.status().equals("failed") && !run.status().equals("requires_action")) {
            try {
                Thread.sleep(500); // Wait for 0.5 seconds before checking again
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            run = openAIClient.threads().retrieveRun(threadId, run.id());
            log.info("Run status: " + run.status());
        }

        if (run.status().equals("requires_action")) {
            // 4. Handle tool calls
            List<com.openai.client.threads.ToolCall> toolCalls = run.requiredAction().submitToolOutputs().toolCalls();
            Map<String, String> toolOutputs = new HashMap<>();

            for (com.openai.client.threads.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.function().name();
                String toolInput = toolCall.function().arguments();
                String toolOutput = executeTool(toolName, toolInput);
                toolOutputs.put(toolCall.id(), toolOutput);
            }

            // 5. Submit tool outputs
            openAIClient.threads().submitToolOutputsToRun(threadId, run.id(), toolOutputs);

            // 6. Retrieve the updated run
            run = openAIClient.threads().retrieveRun(threadId, run.id());
        }

        if (run.status().equals("completed")) {
            // 7. Get the response
            MessageListResponse messageListResponse = openAIClient.threads().listMessages(threadId);
            List<Message> messages = messageListResponse.data();

            // Assuming the last message is the response from the assistant
            for (Message m : messages) {
                if (m.role().equals("assistant")) {
                    return m.content().get(0).text().value();
                }
            }
        } else if (run.status().equals("failed")) {
            return "The assistant failed to provide a response.";
        }

        return "No response from the assistant.";
    }

    private String executeTool(String toolName, String toolInput) {
        for (Tool tool : tools) {
            if (tool.getName().equals(toolName)) {
                try {
                    return tool.execute(toolInput);
                } catch (Exception e) {
                    log.error("Error executing tool: " + toolName, e);
                    return "Error: " + e.getMessage();
                }
            }
        }
        return "Error: Tool not found: " + toolName;
    }
}
