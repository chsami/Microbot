package net.runelite.client.plugins.microbot.externalplugins;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import javax.swing.SwingUtilities;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Resources owned by one external plugin loader. The registry holds weak references so registration
 * does not keep an otherwise unused plugin alive. Plugins must retain their registered resources.
 * Normal plugin stops remain restartable; disposal is reserved for permanent loader removal.
 */
public final class PluginResources {
    private static final Map<ClassLoader, Scope> SCOPES = new WeakHashMap<>();

    private PluginResources() {
    }

    public static void registerScript(Script script) {
        ClassLoader owner = script.getClass().getClassLoader();
        if (isCoreLoader(owner)) {
            return;
        }
        synchronized (SCOPES) {
            Scope scope = SCOPES.computeIfAbsent(owner, ignored -> new Scope());
            if (scope.disposalRequested) {
                script.disposeForUnload();
                throw new IllegalStateException("The plugin is being unloaded");
            }
            addWeak(scope.scripts, script);
        }
    }

    /** Register an additional plugin-owned executor before submitting work to it. */
    public static <T extends ExecutorService> T registerExecutor(ClassLoader owner, T executor) {
        Objects.requireNonNull(executor, "executor");
        if (isCoreLoader(owner)) {
            return executor;
        }
        synchronized (SCOPES) {
            Scope scope = SCOPES.computeIfAbsent(owner, ignored -> new Scope());
            if (scope.disposalRequested) {
                executor.shutdownNow();
                throw new IllegalStateException("The plugin is being unloaded");
            }
            addWeak(scope.executors, executor);
        }
        return executor;
    }

    /** Register a plugin-owned thread before starting it. Never register a shared client thread. */
    public static <T extends Thread> T registerThread(ClassLoader owner, T thread) {
        Objects.requireNonNull(thread, "thread");
        if (isCoreLoader(owner)) {
            return thread;
        }
        if (thread == Thread.currentThread()) {
            throw new IllegalArgumentException("Register plugin-owned threads before starting them");
        }
        synchronized (SCOPES) {
            Scope scope = SCOPES.computeIfAbsent(owner, ignored -> new Scope());
            if (scope.disposalRequested) {
                thread.interrupt();
                throw new IllegalStateException("The plugin is being unloaded");
            }
            addWeak(scope.threads, thread);
        }
        return thread;
    }

    /**
     * Register a resource whose close method may run on a background thread. Keep the returned
     * resource in the plugin; close must tolerate a retry after an exception. UI/client-thread
     * resources should instead be removed by the plugin's normal shutdown callback.
     */
    public static <T extends AutoCloseable> T registerCloseable(ClassLoader owner, T resource) {
        Objects.requireNonNull(resource, "resource");
        if (isCoreLoader(owner)) {
            return resource;
        }
        synchronized (SCOPES) {
            Scope scope = SCOPES.computeIfAbsent(owner, ignored -> new Scope());
            if (scope.disposalRequested) {
                throw new IllegalStateException("The plugin is being unloaded");
            }
            scope.closeables.removeIf(entry -> entry.resource.get() == null);
            for (CloseableEntry entry : scope.closeables) {
                if (entry.resource.get() == resource) {
                    return resource;
                }
            }
            scope.closeables.add(new CloseableEntry(resource));
        }
        return resource;
    }

    /**
     * Request cancellation for every resource before waiting, using a single timeout for the whole
     * loader. Call only after PluginManager.stopPlugin has returned, from a background thread.
     * On failure, retain the loader and retry this method; never start another version concurrently.
     * Closing a loader cannot forcibly stop arbitrary plugin code that ignores interruption.
     */
    public static void dispose(ClassLoader owner, long timeoutMillis)
        throws DisposalException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()
            || (Microbot.getClient() != null && Microbot.getClient().isClientThread())) {
            throw new IllegalStateException("Plugin disposal must run on a background thread");
        }
        if (isCoreLoader(owner)) {
            throw new IllegalArgumentException("The core client loader cannot be disposed");
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis must not be negative");
        }
        Scope scope;
        List<Script> scripts;
        List<Thread> workers;
        Set<ExecutorService> executors = Collections.newSetFromMap(new IdentityHashMap<>());
        List<CloseableEntry> closeables;
        synchronized (SCOPES) {
            scope = SCOPES.computeIfAbsent(owner, ignored -> new Scope());
            if (scope.disposing) {
                throw new DisposalException("Plugin disposal is already in progress");
            }
            workers = live(scope.threads);
            if (workers.contains(Thread.currentThread())) {
                throw new IllegalStateException("A plugin cannot dispose its own worker thread");
            }
            scope.disposalRequested = true;
            scope.disposing = true;
            scripts = live(scope.scripts);
            executors.addAll(live(scope.executors));
            closeables = new ArrayList<>(scope.closeables);
        }

        long started = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        List<String> failures = new ArrayList<>();
        try {
            for (Script script : scripts) {
                try {
                    ExecutorService executor = script.disposeForUnload();
                    if (executor != null) {
                        executors.add(executor);
                        synchronized (SCOPES) {
                            addWeak(scope.executors, executor);
                        }
                    }
                } catch (RuntimeException e) {
                    failures.add("A script could not be cancelled: " + e.getClass().getSimpleName());
                }
            }
            for (ExecutorService executor : executors) {
                try {
                    executor.shutdownNow();
                } catch (RuntimeException e) {
                    failures.add("An executor could not be stopped: " + e.getClass().getSimpleName());
                }
            }
            for (Thread worker : workers) {
                worker.interrupt();
            }
            for (CloseableEntry entry : closeables) {
                entry.startClose();
            }

            for (ExecutorService executor : executors) {
                if (!executor.awaitTermination(remaining(started, timeoutNanos), TimeUnit.NANOSECONDS)) {
                    failures.add("A plugin executor is still running");
                }
            }
            for (Thread worker : workers) {
                awaitThread(worker, started, timeoutNanos);
            }
            for (CloseableEntry entry : closeables) {
                entry.awaitClose(started, timeoutNanos, failures);
            }
            if (workers.stream().anyMatch(Thread::isAlive)) {
                failures.add("Plugin worker threads are still running");
            }
            if (!failures.isEmpty()) {
                throw new DisposalException(String.join("; ", failures));
            }
            synchronized (SCOPES) {
                scope.scripts.clear();
                scope.executors.clear();
                scope.threads.clear();
                scope.closeables.clear();
            }
        } finally {
            synchronized (SCOPES) {
                scope.disposing = false;
            }
        }
    }

    private static boolean isCoreLoader(ClassLoader owner) {
        return owner == null || owner == PluginResources.class.getClassLoader();
    }

    private static <T> void addWeak(List<WeakReference<T>> entries, T value) {
        entries.removeIf(entry -> entry.get() == null);
        for (WeakReference<T> entry : entries) {
            if (entry.get() == value) {
                return;
            }
        }
        entries.add(new WeakReference<>(value));
    }

    private static <T> List<T> live(List<WeakReference<T>> entries) {
        List<T> result = new ArrayList<>();
        for (WeakReference<T> entry : entries) {
            T value = entry.get();
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private static long remaining(long started, long timeoutNanos) {
        return Math.max(0, timeoutNanos - (System.nanoTime() - started));
    }

    private static void awaitThread(Thread worker, long started, long timeoutNanos)
        throws InterruptedException {
        long remaining = remaining(started, timeoutNanos);
        if (remaining > 0 && worker.isAlive()) {
            TimeUnit.NANOSECONDS.timedJoin(worker, remaining);
        }
    }

    private static final class Scope {
        private final List<WeakReference<Script>> scripts = new ArrayList<>();
        private final List<WeakReference<ExecutorService>> executors = new ArrayList<>();
        private final List<WeakReference<Thread>> threads = new ArrayList<>();
        private final List<CloseableEntry> closeables = new ArrayList<>();
        private boolean disposalRequested;
        private boolean disposing;
    }

    private static final class CloseableEntry {
        private final WeakReference<AutoCloseable> resource;
        private Thread worker;
        private volatile boolean closed;
        private volatile String failure;

        private CloseableEntry(AutoCloseable resource) {
            this.resource = new WeakReference<>(resource);
        }

        private void startClose() {
            if (closed || (worker != null && worker.isAlive())) {
                return;
            }
            AutoCloseable value = resource.get();
            if (value == null) {
                closed = true;
                return;
            }
            failure = null;
            worker = new Thread(() -> {
                try {
                    value.close();
                    closed = true;
                } catch (Exception | LinkageError e) {
                    failure = "A plugin resource could not be closed: " + e.getClass().getSimpleName();
                }
            }, "Plugin-resource-disposal");
            worker.setDaemon(true);
            worker.setContextClassLoader(PluginResources.class.getClassLoader());
            worker.start();
        }

        private void awaitClose(long started, long timeoutNanos, List<String> failures)
            throws InterruptedException {
            if (worker != null) {
                awaitThread(worker, started, timeoutNanos);
                if (worker.isAlive()) {
                    worker.interrupt();
                    failures.add("A plugin resource is still closing");
                    return;
                }
                worker = null;
            }
            if (!closed) {
                failures.add(failure != null ? failure : "A plugin resource was not closed");
            }
        }
    }

    public static final class DisposalException extends Exception {
        public DisposalException(String message) {
            super(message);
        }
    }
}
