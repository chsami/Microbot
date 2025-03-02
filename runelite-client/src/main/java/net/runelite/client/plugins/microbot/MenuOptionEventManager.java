package net.runelite.client.plugins.microbot;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class MenuOptionEventManager {
    private final List<ClickedMenuOptionEvent> clickEvents = new ArrayList<>();
    private final List<AddedMenuOptionEvent> addedEvents = new ArrayList<>();

    public void add(MenuOptionEvent event) {
        if (event instanceof ClickedMenuOptionEvent) {
            clickEvents.add((ClickedMenuOptionEvent) event);
            sortClickEvents();
        } else if (event instanceof AddedMenuOptionEvent) {
            addedEvents.add((AddedMenuOptionEvent) event);
            sortAddedEvents();
        }
    }

    public void remove(MenuOptionEvent event) {
        if (event instanceof ClickedMenuOptionEvent) {
            clickEvents.remove(event);
        } else if (event instanceof AddedMenuOptionEvent) {
            addedEvents.remove(event);
        }
    }

    private void sortClickEvents() {
        clickEvents.sort(Comparator.comparingInt(MenuOptionEvent::priority));
    }

    private void sortAddedEvents() {
        addedEvents.sort(Comparator.comparingInt(MenuOptionEvent::priority));
    }

}
