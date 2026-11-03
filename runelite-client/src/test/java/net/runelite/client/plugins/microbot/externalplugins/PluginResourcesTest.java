package net.runelite.client.plugins.microbot.externalplugins;

import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.api.Client;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginResourcesTest {
    @Test(timeout = 10000)
    public void cancelsEveryExecutorBeforeWaitingForAny() throws Exception {
        ClassLoader owner = newOwner();
        ExecutorService first = PluginResources.registerExecutor(owner, Executors.newSingleThreadExecutor());
        ExecutorService second = PluginResources.registerExecutor(owner, Executors.newSingleThreadExecutor());
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch releasedBySecond = new CountDownLatch(1);
        try {
            first.submit(() -> {
                started.countDown();
                awaitUninterruptibly(releasedBySecond);
            });
            second.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    releasedBySecond.countDown();
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            PluginResources.dispose(owner, 2000);
            assertTrue(first.isTerminated());
            assertTrue(second.isTerminated());
        } finally {
            releasedBySecond.countDown();
            first.shutdownNow();
            second.shutdownNow();
        }
    }

    @Test(timeout = 10000)
    public void failedQuiescenceCanBeRetriedWithoutAllowingNewResources() throws Exception {
        ClassLoader owner = newOwner();
        ExecutorService executor = PluginResources.registerExecutor(owner, Executors.newSingleThreadExecutor());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                started.countDown();
                awaitUninterruptibly(release);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertDisposalFails(owner, 20);

            ExecutorService late = Executors.newSingleThreadExecutor();
            try {
                PluginResources.registerExecutor(owner, late);
                fail("An unloading plugin must not register new work");
            } catch (IllegalStateException expected) {
                assertTrue(late.isShutdown());
            } finally {
                late.shutdownNow();
            }

            release.countDown();
            PluginResources.dispose(owner, 2000);
            PluginResources.dispose(owner, 0);
            assertTrue(executor.isTerminated());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test(timeout = 10000)
    public void closeFailureCanRetryAndSuccessClosesOnlyOnce() throws Exception {
        ClassLoader owner = newOwner();
        AtomicInteger calls = new AtomicInteger();
        AutoCloseable resource = PluginResources.registerCloseable(owner, () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("First close failed");
            }
        });
        assertSame(resource, PluginResources.registerCloseable(owner, resource));
        assertDisposalFails(owner, 2000);
        PluginResources.dispose(owner, 2000);
        PluginResources.dispose(owner, 0);
        assertEquals(2, calls.get());
    }

    @Test(timeout = 10000)
    public void slowCloseIsBoundedAndRetryDoesNotCloseConcurrently() throws Exception {
        ClassLoader owner = newOwner();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        AutoCloseable resource = PluginResources.registerCloseable(owner, () -> {
            calls.incrementAndGet();
            awaitUninterruptibly(release);
        });
        try {
            assertDisposalFails(owner, 20);
            assertDisposalFails(owner, 20);
            release.countDown();
            PluginResources.dispose(owner, 2000);
            assertEquals(1, calls.get());
            assertNotNull(resource);
        } finally {
            release.countDown();
        }
    }

    @Test(timeout = 10000)
    public void cooperativelyStopsExplicitlyRegisteredThreads() throws Exception {
        ClassLoader owner = newOwner();
        CountDownLatch started = new CountDownLatch(1);
        Thread worker = PluginResources.registerThread(owner, new Thread(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        worker.start();
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            PluginResources.dispose(owner, 2000);
            assertFalse(worker.isAlive());
        } finally {
            worker.interrupt();
            worker.join(2000);
        }
    }

    @Test(timeout = 10000)
    public void leavesSharedLazyExecutorAliveEvenIfItInheritsPluginContextLoader() throws Exception {
        ClassLoader owner = newOwner();
        ExecutorService shared = Executors.newSingleThreadExecutor();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            // Default thread factories inherit the first submitter's context loader. It is not ownership.
            Thread.currentThread().setContextClassLoader(owner);
            Thread inheritedWorker = shared.submit(Thread::currentThread).get(2, TimeUnit.SECONDS);
            assertSame(owner, inheritedWorker.getContextClassLoader());
            Thread.currentThread().setContextClassLoader(original);
            PluginResources.dispose(owner, 2000);
            assertFalse(shared.isShutdown());
            assertFalse(inheritedWorker.isInterrupted());
            assertEquals(Integer.valueOf(42), shared.submit(() -> 42).get(2, TimeUnit.SECONDS));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            shared.shutdownNow();
            assertTrue(shared.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 10000)
    public void normalScriptStopCanRestartButUnloadPermanentlyStopsItsExecutor() throws Exception {
        String name = "net.runelite.client.plugins.microbot.externalplugins.fixture.ReloadableScriptFixture";
        ClassLoader owner = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(className)) {
                    return super.loadClass(className, resolve);
                }
                Class<?> loaded = findLoadedClass(className);
                if (loaded == null) {
                    try (InputStream stream = getParent().getResourceAsStream(className.replace('.', '/') + ".class")) {
                        if (stream == null) {
                            throw new ClassNotFoundException(className);
                        }
                        byte[] bytes = stream.readAllBytes();
                        loaded = defineClass(className, bytes, 0, bytes.length);
                    } catch (java.io.IOException e) {
                        throw new ClassNotFoundException(className, e);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        };
        Script script = (Script) owner.loadClass(name).getDeclaredConstructor().newInstance();
        ExecutorService executor = (ExecutorService) script.getClass().getMethod("executor").invoke(script);
        try {
            assertSame(Thread.currentThread().getContextClassLoader(),
                executor.submit(() -> Thread.currentThread().getContextClassLoader()).get(2, TimeUnit.SECONDS));
            script.shutdown();
            assertFalse(executor.isShutdown());
            assertEquals(Integer.valueOf(42), executor.submit(() -> 42).get(2, TimeUnit.SECONDS));
            // A staged candidate must not delete the running version's heartbeat if it never ran.
            ScriptHeartbeatRegistry.recordHeartbeat(name);
            PluginResources.dispose(owner, 2000);
            assertTrue(executor.isTerminated());
            assertFalse(script.isRunning());
            assertFalse("An unloaded script must not touch game state", script.run());
            assertNotNull(ScriptHeartbeatRegistry.getHealth(name));
        } finally {
            ScriptHeartbeatRegistry.remove(name);
            executor.shutdownNow();
        }
    }

    @Test
    public void coreLoaderCannotBeDisposed() throws Exception {
        try {
            PluginResources.dispose(PluginResources.class.getClassLoader(), 0);
            fail("The core client must remain alive");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("core"));
        }
    }

    @Test(timeout = 10000)
    public void eventDispatchThreadCannotWaitForDisposal() throws Exception {
        ClassLoader owner = newOwner();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                PluginResources.dispose(owner, 0);
            } catch (Exception e) {
                failure.set(e);
            }
        });
        assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    public void clientThreadCannotWaitForDisposal() throws Exception {
        Field field = Microbot.class.getDeclaredField("client");
        field.setAccessible(true);
        Object original = field.get(null);
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        try {
            field.set(null, client);
            PluginResources.dispose(newOwner(), 0);
            fail("The client thread must never wait for plugin workers");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("background"));
        } finally {
            field.set(null, original);
        }
    }

    private ClassLoader newOwner() {
        return new ClassLoader(getClass().getClassLoader()) { };
    }

    private static void assertDisposalFails(ClassLoader owner, long timeoutMillis) throws Exception {
        try {
            PluginResources.dispose(owner, timeoutMillis);
            fail("Disposal should report unfinished cleanup");
        } catch (PluginResources.DisposalException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
