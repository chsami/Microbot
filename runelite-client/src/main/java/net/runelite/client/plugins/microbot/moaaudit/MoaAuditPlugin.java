package net.runelite.client.plugins.microbot.moaaudit;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2MapOfAlacrityTransport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Temporary stub: {@link #startUp()} spawns a daemon thread that runs {@link Rs2MapOfAlacrityTransport#runMoaAudit()} so
 * plugin startup stays light even if the audit grows heavier later.
 * Stub logs at debug once per JVM; toggling the plugin again does not re-log unless
 * {@link Rs2MapOfAlacrityTransport#resetMoaAuditStubLogForTests()} is used (e.g. tests).
 * <p>The plugin list only shows {@link PluginDescriptor#description()} — details live here, not in the UI blurb.
 */
@PluginDescriptor(
    name = PluginDescriptor.Default + "MoA Audit",
    description = "[TEMP] Debug stub: no gameplay effect; see class Javadoc",
    tags = {"temp", "debug", "league", "microbot"},
    enabledByDefault = false
)
@Slf4j
public class MoaAuditPlugin extends Plugin {

    /**
     * On each {@link #stopWorker} evaluation where the audit thread is still alive shortly after {@link Thread#interrupt()},
     * the counter increments. Counter drives {@link Rs2LogRateLimit#everyN} (not raw warn count).
     */
    private static final AtomicInteger MOA_AUDIT_STOPWORKER_THREAD_ALIVE_EVALS_FOR_WARN = new AtomicInteger(0);
    private static final int MOA_AUDIT_THREAD_ALIVE_WARN_INTERVAL = 20;
    private static final AtomicInteger MOA_STOP_REAPER_SEQ = new AtomicInteger(0);
    private static final AtomicInteger MOA_RESTART_BLOCKED_LOG = new AtomicInteger(0);
    private static final int MOA_RESTART_BLOCKED_LOG_INTERVAL = 25;
    private static final Object MOA_STOP_REAPER_LOCK = new Object();
    private static final AtomicReference<ExecutorService> MOA_STOP_REAPER_REF = new AtomicReference<>();

    private static ExecutorService moaStopReaper()
    {
        synchronized (MOA_STOP_REAPER_LOCK)
        {
            ExecutorService existing = MOA_STOP_REAPER_REF.get();
            if (existing != null && !existing.isShutdown())
            {
                return existing;
            }
            ExecutorService created = newStopReaperExecutor();
            MOA_STOP_REAPER_REF.set(created);
            return created;
        }
    }

    private static ExecutorService newStopReaperExecutor()
    {
        return Executors.newSingleThreadExecutor(r ->
        {
            Thread t = new Thread(r, "moa-audit-stop-reaper");
            t.setDaemon(true);
            return t;
        });
    }

    private volatile Thread worker;

    /**
     * Interrupt + background bounded join: never blocks the caller thread.
     * If {@link Rs2MapOfAlacrityTransport#runMoaAudit()} grows loops/sleeps, it must honor {@link Thread#interrupt()}.
     * When the thread is still alive after interrupt, a background reaper thread performs a bounded join and logs if it
     * still fails to stop.
     */
    private static boolean stopWorker(Thread t)
    {
        if (t == null || !t.isAlive())
        {
            return true;
        }
        t.interrupt();
        if (!t.isAlive())
        {
            return true;
        }

        // Async bounded join: avoid ever blocking plugin startup/shutdown (client thread).
        int seq = MOA_STOP_REAPER_SEQ.incrementAndGet();
        moaStopReaper().execute(() ->
        {
            try
            {
                t.join(2500);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            if (t.isAlive() && Rs2LogRateLimit.everyN(MOA_AUDIT_STOPWORKER_THREAD_ALIVE_EVALS_FOR_WARN, MOA_AUDIT_THREAD_ALIVE_WARN_INTERVAL))
            {
                log.warn("MoA audit thread still alive after interrupt + join(2500ms) — interrupt ignored or long-running audit "
                                + "(warn every {} evaluations; seq={})",
                        MOA_AUDIT_THREAD_ALIVE_WARN_INTERVAL,
                        seq);
            }
        });
        return false;
    }

    @Override
    protected void startUp()
    {
        Thread prev = worker;
        if (!stopWorker(prev))
        {
            // Avoid spawning a second worker while the previous thread is still alive.
            // Stop is async; if the audit ignores interrupt, restart is intentionally blocked.
            if (Rs2LogRateLimit.everyN(MOA_RESTART_BLOCKED_LOG, MOA_RESTART_BLOCKED_LOG_INTERVAL))
            {
                int evals = MOA_RESTART_BLOCKED_LOG.get();
                log.debug("MoA audit worker still alive; restart blocked (rate-limited per stopWorker eval, every {} evals)",
                        MOA_RESTART_BLOCKED_LOG_INTERVAL);
                log.debug("MoA audit restart blocked evalCount={}", evals);
            }
            return;
        }
        worker = new Thread(Rs2MapOfAlacrityTransport::runMoaAudit, "moa-audit");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    protected void shutDown()
    {
        Thread t = worker;
        if (stopWorker(t))
        {
            worker = null;
        }
        shutdownStopReaperIfIdle();
    }

    private void shutdownStopReaperIfIdle()
    {
        if (worker != null)
        {
            return;
        }
        ExecutorService ex = MOA_STOP_REAPER_REF.get();
        if (ex == null || ex.isShutdown())
        {
            return;
        }
        // Best-effort cleanup: plugin is disabled; stop reaper thread so it doesn't linger in long-running IDE sessions.
        // Use shutdown (not shutdownNow) so queued join/log tasks can complete.
        ex.shutdown();
        MOA_STOP_REAPER_REF.compareAndSet(ex, null);
    }
}
