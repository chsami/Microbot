package net.runelite.client.plugins.microbot.rsagent.agent;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

public class Agent {
    private OpenAIClient openAIClient;
    public Agent(String apiKey) {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}
