package net.runelite.client.plugins.microbot;

public interface BlockingEvent {
    boolean validate();
    boolean execute();
    BlockingEventPriority priority();
}
