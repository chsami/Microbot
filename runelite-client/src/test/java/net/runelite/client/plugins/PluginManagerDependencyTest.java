package net.runelite.client.plugins;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.util.Providers;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteDebug;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class PluginManagerDependencyTest {
    private Injector previousInjector;
    private Injector previousDebugInjector;
    private PluginManager manager;

    @Before
    public void setUp() {
        previousInjector = RuneLite.getInjector();
        previousDebugInjector = RuneLiteDebug.getInjector();
        RuneLite.setInjector(Guice.createInjector());
        RuneLiteDebug.setInjector(null);
        manager = new PluginManager(false, null, null, null, null, new PluginModuleFactory());
        InventoryDependency.constructions = 0;
        ServiceDependency.constructions = 0;
    }

    @After
    public void tearDown() {
        RuneLite.setInjector(previousInjector);
        RuneLiteDebug.setInjector(previousDebugInjector);
    }

    @Test
    public void dependencyWithoutPublicModuleReusesLoadedInstanceAndPrivateInjection() throws Exception {
        ServiceDependency service = prepare(ServiceDependency.class);
        InventoryDependency inventory = prepare(InventoryDependency.class);
        Injector inventoryInjector = inventory.getInjector();

        InventoryConsumer consumer = prepare(InventoryConsumer.class);

        assertSame(inventory, consumer.inventory);
        assertSame(inventory, consumer.getInjector().getInstance(InventoryDependency.class));
        assertSame(inventoryInjector, inventory.getInjector());
        assertSame(service, inventory.servicePlugin);
        assertSame(service.service, inventory.service);
        assertEquals(1, InventoryDependency.constructions);
        assertEquals(1, inventory.injections);
        assertEquals(1, service.injections);
        assertNull("Private config bindings must not be copied into the dependent plugin",
                consumer.getInjector().getExistingBinding(Key.get(PrivateInventoryConfig.class)));
    }

    @Test
    public void publicModuleServicesAndPluginIdentityArePreservedWithoutReinjection() throws Exception {
        ServiceDependency service = prepare(ServiceDependency.class);
        ServiceConsumer consumer = prepare(ServiceConsumer.class);

        assertSame(service, consumer.plugin);
        assertSame(service.service, consumer.service);
        assertEquals(1, ServiceDependency.constructions);
        assertEquals(1, service.injections);
        assertNull("Private services must stay in their owning plugin injector",
                consumer.getInjector().getExistingBinding(Key.get(PrivateServiceConfig.class)));
    }

    @Test
    public void missingDependencyFailsInsteadOfConstructingAnotherPlugin() throws Exception {
        try {
            manager.preparePlugin(InventoryConsumer.class, Collections.emptyList());
            fail("A dependency must already be available");
        } catch (PluginInstantiationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Unmet dependency"));
        }
        assertEquals(0, InventoryDependency.constructions);
        assertTrue(manager.getPlugins().isEmpty());
    }

    private <T extends Plugin> T prepare(Class<T> type) throws Exception {
        T plugin = type.cast(manager.preparePlugin(type, manager.getPlugins()));
        manager.add(plugin);
        return plugin;
    }

    interface PublicService { }
    interface PrivateServiceConfig { }
    interface PrivateInventoryConfig { }

    @PluginDescriptor(name = "Service dependency")
    public static class ServiceDependency extends Plugin {
        static int constructions;
        int injections;
        final PublicService service = new PublicService() { };

        public ServiceDependency() {
            constructions++;
        }

        @Inject
        void initialize(PrivateServiceConfig config) {
            injections++;
        }

        @Provides
        PrivateServiceConfig providePrivateConfig() {
            return new PrivateServiceConfig() { };
        }

        @Override
        protected Module getPublicModule() {
            // Mirrors BankTagsPlugin: exports its API and canonical plugin instance, but no config.
            return binder -> {
                binder.bind(ServiceDependency.class).toProvider(Providers.of(this));
                binder.bind(PublicService.class).toProvider(Providers.of(service));
            };
        }
    }

    @PluginDescriptor(name = "Inventory dependency")
    @PluginDependency(ServiceDependency.class)
    public static class InventoryDependency extends Plugin {
        static int constructions;
        int injections;
        @Inject ServiceDependency servicePlugin;
        @Inject PublicService service;

        public InventoryDependency() {
            constructions++;
        }

        @Inject
        void initialize(PrivateInventoryConfig config) {
            injections++;
        }

        @Provides
        PrivateInventoryConfig providePrivateConfig() {
            return new PrivateInventoryConfig() { };
        }
    }

    @PluginDescriptor(name = "Inventory consumer")
    @PluginDependency(InventoryDependency.class)
    public static class InventoryConsumer extends Plugin {
        @Inject InventoryDependency inventory;
    }

    @PluginDescriptor(name = "Service consumer")
    @PluginDependency(ServiceDependency.class)
    public static class ServiceConsumer extends Plugin {
        @Inject ServiceDependency plugin;
        @Inject PublicService service;
    }
}
