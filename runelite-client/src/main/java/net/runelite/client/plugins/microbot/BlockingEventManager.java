package net.runelite.client.plugins.microbot;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.events.*;
import net.runelite.client.ui.SplashScreen;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingEventManager
{
    private static final int MAX_QUEUE_SIZE = 10;
    private final List<BlockingEvent> blockingEvents = new CopyOnWriteArrayList<>();
    // Track which events are already in the queue
    private final Set<BlockingEvent> pendingEvents = ConcurrentHashMap.newKeySet();

    // Change the queue to hold just the event references
    private final BlockingQueue<BlockingEvent> eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final ScheduledExecutorService scheduler;
    private final ExecutorService blockingExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Getter
    private final ThreadFactory threadFactory = runnable -> {
        Thread t = new Thread(runnable, "Microbot-BlockingEvent");
        t.setDaemon(true);
        return t;
    };

    public BlockingEventManager()
    {
        // single-threaded executor for running event.execute()
        this.blockingExecutor = Executors.newSingleThreadExecutor(threadFactory);

        // scheduler for periodic validate() calls
        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        this.scheduler.scheduleWithFixedDelay(
                this::validateAndEnqueue,
                0,
                300,
                TimeUnit.MILLISECONDS
        );

        // pre-register core events
        blockingEvents.add(new WelcomeScreenEvent());
        blockingEvents.add(new DisableLevelUpInterfaceEvent());
        blockingEvents.add(new BankTutorialEvent());
        blockingEvents.add(new DeathEvent());
        blockingEvents.add(new BankJagexPopupEvent());
        blockingEvents.add(new PluginPauseEvent());
		blockingEvents.add(new EnjoyRSChatboxEvent());

        sortBlockingEvents();
    }

    public void shutdown()
    {
        scheduler.shutdownNow();
        blockingExecutor.shutdownNow();
    }

    public void add(BlockingEvent event)
    {
        blockingEvents.add(event);
        sortBlockingEvents();
    }

    public void remove(BlockingEvent event)
    {
        blockingEvents.remove(event);
    }

    public List<BlockingEvent> getEvents()
    {
        return Collections.unmodifiableList(blockingEvents);
    }

    private void sortBlockingEvents()
    {
        blockingEvents.sort(
                Comparator.comparingInt((BlockingEvent e) -> e.priority().getLevel())
                        .reversed()
        );
    }

    /**
     * Runs every 300ms on the scheduler thread: tries each event.validate()
     * and, if true, offers it into the queue (drops if full).
     */
    private void validateAndEnqueue()
    {
        if(SplashScreen.isOpen())
        {
            return;
        }
        for (BlockingEvent event : blockingEvents)
        {
            try
            {
                if (event.validate())
                {
                    // only enqueue if it wasn't already pending
                    if (pendingEvents.add(event))
                    {
                        // offer; if the queue is full, drop and remove from pending
                        if (!eventQueue.offer(event))
                        {
                            pendingEvents.remove(event);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Microbot.log(Level.ERROR,
                        "Error validating BlockingEvent (%s): %s",
                        event.getName(),
                        ex);
            }
        }
    }

    /**
     * If an event is already running, returns true immediately.
     * Otherwise poll the queue; if we get an event, mark running and execute.
     */
    public boolean shouldBlockAndProcess()
    {
        if (isRunning.get())
        {
            return true;
        }

        BlockingEvent event = eventQueue.poll();
        if (event == null)
        {
            return false;
        }


        if (!isRunning.compareAndSet(false, true))
        {
            // if somebody else started in the meantime, we consider it “busy”
            return true;
        }

        blockingExecutor.execute(() -> {
            try
            {
                event.execute();
            }
            catch (Exception ex)
            {
                Microbot.log(Level.ERROR,
                        "Error executing BlockingEvent (%s): %s",
                        event.getName(),
                        ex);
            }
            finally
            {
                pendingEvents.remove(event);
                isRunning.set(false);
            }
        });

        return true;
    }
}
