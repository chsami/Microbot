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

    public Agent(String apiKey) {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}
