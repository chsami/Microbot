package net.runelite.client.plugins.microbot;

import net.runelite.api.events.MenuOptionClicked;

public interface ClickedMenuOptionEvent extends MenuOptionEvent {
    void execute(MenuOptionClicked event);

    @Override
    default EventType getEventType() {
        return EventType.ON_CLICK;
    }
}
