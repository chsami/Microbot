package net.runelite.client.plugins.microbot;

public interface MenuOptionEvent {
    enum EventType {
        ON_CLICK,
        ON_ADDED
    }

    EventType getEventType();

    int priority();
}
