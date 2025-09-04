package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.events.*;
import net.runelite.client.ui.SplashScreen;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class BlockingEventManager
{
    private static final int MAX_QUEUE_SIZE = 10;
    private final List<BlockingEvent> blockingEvents = new CopyOnWriteArrayList<>();
    // Track which events are already in the queue
    private final Set<BlockingEvent> pendingEvents = ConcurrentHashMap.newKeySet();

    // Change the queue to hold just the event references
    private final BlockingQueue<BlockingEvent> eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final ExecutorService blockingExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Getter
    private final ThreadFactory threadFactory = runnable -> {
        Thread t = new Thread(runnable, "Microbot-BlockingEvent");
        t.setDaemon(true);
        return t;
    };

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(threadFactory);
    private ScheduledFuture<?> loopFuture;
    private static final long INITIAL_DELAY_MS = 300;
    private volatile long currentDelay = INITIAL_DELAY_MS;
    private static final long MAX_DELAY_MS = 5000; // Maximum delay of 5 seconds
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public BlockingEventManager()
    {
        // single-threaded executor for running event.execute()
        this.blockingExecutor = Executors.newSingleThreadExecutor(threadFactory);

        // scheduler for periodic validate() calls
        startLoop();

        // pre-register core events
        blockingEvents.add(new WelcomeScreenEvent());
        blockingEvents.add(new DisableLevelUpInterfaceEvent());
        blockingEvents.add(new BankTutorialEvent());
        blockingEvents.add(new DeathEvent());
        blockingEvents.add(new BankJagexPopupEvent());
        blockingEvents.add(new PluginPauseEvent());
		blockingEvents.add(new EnjoyRSChatboxEvent());
		blockingEvents.add(new DisableWorldSwitcherConfirmationEvent());

        sortBlockingEvents();
    }

    public void shutdown() {
        if (loopFuture != null) loopFuture.cancel(true);
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

    private void startLoop() {
        loopFuture = scheduler.schedule(this::loopOnce, 0, TimeUnit.MILLISECONDS);
    }

    private void loopOnce() {
        try {
            validateAndEnqueueWithBackoff();
        } catch (Throwable t) {
            Microbot.log(Level.ERROR, "BlockingEvent loop error: %s", t);
        } finally {
            // re-schedule using the latest currentDelay (volatile)
            loopFuture = scheduler.schedule(this::loopOnce, currentDelay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Runs every 300ms on the scheduler thread: tries each event.validate()
     * and, if true, offers it into the queue (drops if full).
     */
    private void validateAndEnqueueWithBackoff() {
        boolean hasValidEvents = false;
        if (!SplashScreen.isOpen()) {
            for (BlockingEvent event : blockingEvents) {
                try {
                    if (event.validate()) {
                        hasValidEvents = true;
                        if (pendingEvents.add(event)) {
                            if (!eventQueue.offer(event)) {
                                pendingEvents.remove(event);
                            }
                        }
                    }
                } catch (Exception ex) {
                    Microbot.log(Level.ERROR,
                            "Error validating BlockingEvent (%s): %s",
                            event.getName(),
                            ex);
                }
            }
        }

        if (!hasValidEvents) {
            // Increase delay exponentially
            int failures = failureCount.incrementAndGet();
            currentDelay = Math.min(INITIAL_DELAY_MS * (1L << Math.min(failures, 4)), MAX_DELAY_MS);
        } else {
            // Reset on success
            failureCount.set(0);
            currentDelay = INITIAL_DELAY_MS;
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
            boolean executedSuccess = false;
            try
            {
                executedSuccess = event.execute();
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
                if (executedSuccess){
                    log.debug("BlockingEvent {} executed successfully", event.getName());                    
                    pendingEvents.remove(event);
                }else{
                    //queue it back if execution failed
                    log.debug("BlockingEvent {} execution failed, re-queuing", event.getName());
                    if (!eventQueue.offer(event)){
                        log.debug("BlockingEvent queue is full, dropping event: {}", event.getName());
                        pendingEvents.remove(event);
                    }
                }
                isRunning.set(false);
            }
        });

        return true;
    }
}
