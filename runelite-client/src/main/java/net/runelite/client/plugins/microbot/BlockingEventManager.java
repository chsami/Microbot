package net.runelite.client.plugins.microbot;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.events.DisableLevelUpInterfaceEvent;
import net.runelite.client.plugins.microbot.util.events.WelcomeScreenEvent;

import java.util.ArrayList;
import java.util.List;

@Getter
public class BlockingEventManager {
    private final List<BlockingEvent> blockingEvents = new ArrayList<>();
    
    public BlockingEventManager() {
        blockingEvents.add(new WelcomeScreenEvent());
        blockingEvents.add(new DisableLevelUpInterfaceEvent());
    }
    
    public void remove(BlockingEvent blockingEvent) {
        blockingEvents.remove(blockingEvent);
    }
    
    public void add(BlockingEvent blockingEvent) {
        blockingEvents.add(blockingEvent);
    }
}
