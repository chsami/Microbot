package net.runelite.client.plugins.microbot.api;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A real, serial client executor; tests call the facade from the test/script thread. */
public final class ApiTestClient implements AutoCloseable
{
    public final Client client = mock(Client.class);
    public final ClientThread bridge = mock(ClientThread.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Thread owner;
    private final Map<Field, Object> originals = new LinkedHashMap<>();
    private final Set<Integer> views = new HashSet<>(Microbot.getWorldViewIds());
    private final Injector injector;

    public ApiTestClient() throws Exception
    {
        owner = executor.submit(Thread::currentThread).get();
        when(client.isClientThread()).thenAnswer(i -> Thread.currentThread() == owner);
        when(bridge.invoke(any(Supplier.class))).thenAnswer(i -> call(((Supplier<?>) i.getArgument(0))::get));
        when(bridge.runOnClientThreadOptional(any())).thenAnswer(i -> Optional.ofNullable(call(i.getArgument(0))));
        install("client", client);
        install("clientThread", bridge);
        Microbot.getWorldViewIds().clear();
        injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(ClientThread.class).toInstance(bridge);
            }
        });
    }

    public void requireClientThread()
    {
        if (Thread.currentThread() != owner) throw new AssertionError("Live state accessed off client thread");
    }

    public <T> T call(Callable<T> callable) throws Exception
    {
        if (Thread.currentThread() == owner) return callable.call();
        try {
            return executor.submit(callable).get(5, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Error) throw (Error) failure.getCause();
            throw (Exception) failure.getCause();
        }
    }

    public void install(String name, Object value) throws Exception
    {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        if (!originals.containsKey(field)) originals.put(field, field.get(null));
        field.set(null, value);
    }

    public <T> void installCache(String name, Class<T> type) throws Exception
    {
        install(name, injector.getInstance(type));
    }

    @Override public void close() throws Exception
    {
        for (Map.Entry<Field, Object> entry : originals.entrySet()) entry.getKey().set(null, entry.getValue());
        Microbot.getWorldViewIds().clear();
        Microbot.getWorldViewIds().addAll(views);
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
