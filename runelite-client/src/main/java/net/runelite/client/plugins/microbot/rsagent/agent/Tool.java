package net.runelite.client.plugins.microbot.rsagent.agent;

public interface Tool {
    String getName();
    String getDescription();
    String execute(String input);
}
