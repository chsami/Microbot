package net.runelite.client.plugins.microbot;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.events.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class BlockingEventManager {
    private final List<BlockingEvent> blockingEvents = new ArrayList<>();
    
    public BlockingEventManager() {
        blockingEvents.add(new WelcomeScreenEvent());
        blockingEvents.add(new DisableLevelUpInterfaceEvent());
        blockingEvents.add(new BankTutorialEvent());
        blockingEvents.add(new DeathEvent());
        blockingEvents.add(new BankJagexPopupEvent());
        sortBlockingEvents();
    }
    
    public void remove(BlockingEvent blockingEvent) {
        blockingEvents.remove(blockingEvent);
    }
    
    public void add(BlockingEvent blockingEvent) {
        blockingEvents.add(blockingEvent);
        sortBlockingEvents();
    }

    private void sortBlockingEvents() {
        blockingEvents.sort(Comparator.comparingInt(e -> -e.priority().getLevel()));
    }
}
