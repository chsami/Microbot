package net.runelite.client.plugins.microbot;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.events.*;
import org.slf4j.event.Level;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class BlockingEventManager {
    private final List<BlockingEvent> blockingEvents = new CopyOnWriteArrayList<>();
    private final ExecutorService blockingExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

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

    /**
     * Checks and runs a blocking event if needed.
     * @return true if an event was triggered and is now executing, false otherwise.
     */
    public boolean shouldBlockAndProcess() {
        // Check if we are still running a blocking event
        if (isRunning.get()) return true;

        for (BlockingEvent event : blockingEvents) {
            try {
                if (event.validate()) {
                    if (!isRunning.compareAndSet(false, true)) return true;

                    blockingExecutor.submit(() -> {
                        try {
                            event.execute();
                        } catch (Exception e) {
                            Microbot.log(Level.ERROR, "Error processing BlockingEvent (%s): %s", event.getName(), e.getMessage());
                        } finally {
                            isRunning.set(false);
                        }
                    });

                    return true;
                }
            } catch (Exception e) {
                Microbot.log(Level.ERROR, "Error validating BlockingEvent (%s): %s", event.getName(), e.getMessage());
            }
        }

        return false;
    }
}
