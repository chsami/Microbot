package net.runelite.client.plugins.microbot;

import net.runelite.api.events.MenuEntryAdded;

public interface AddedMenuOptionEvent extends MenuOptionEvent {
    void execute(MenuEntryAdded event);

    @Override
    default EventType getEventType() {
        return EventType.ON_ADDED;
    }
}
