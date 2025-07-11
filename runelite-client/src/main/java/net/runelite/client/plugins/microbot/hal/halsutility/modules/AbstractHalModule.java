package net.runelite.client.plugins.microbot.hal.halsutility.modules;

import com.google.inject.Binder;
import com.google.inject.Injector;
import net.runelite.client.config.Config;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.hal.halsutility.HalsUtilityandPluginsPlugin;

/**
 * Abstract base class for HalSubPlugin modules.
 * Handles injector binding and config interface access.
 */
public abstract class AbstractHalModule<T extends Config> implements HalSubPlugin {
    protected T config;
    protected Injector injector;
    protected HalsUtilityandPluginsPlugin plugin;
    protected boolean running = false;
    protected final HalModuleCategory category;
    protected final String displayName;
    protected final Class<T> configClass;

    protected AbstractHalModule(HalModuleCategory category, String displayName, Class<T> configClass) {
        this.category = category;
        this.displayName = displayName;
        this.configClass = configClass;
    }

    @Override
    public void configure(Binder binder) {
        // Default implementation - subclasses can override if needed
    }

    @Override
    public void setInjector(Injector injector) {
        this.injector = injector;
        injector.injectMembers(this); // Performs @Inject resolution
    }

    @Override
    public Injector getInjector() {
        return injector;
    }

    @Override
    public void setPlugin(HalsUtilityandPluginsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public Class<? extends Config> getConfigClass() {
        return configClass;
    }

    @Override
    public HalModuleCategory getCategory() {
        return category;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void start() {
        if (!running) {
            running = true;
            onStart();
        }
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            onStop();
        }
    }

    /**
     * Set the config directly (replaces reflection-based approach)
     * @param config The config instance to set
     */
    @SuppressWarnings("unchecked")
    public void setConfigDirectly(Config config) {
        if (configClass.isInstance(config)) {
            this.config = (T) config;
        }
    }

    /**
     * Handle configuration changes for this module
     * @param event The config change event
     */
    public void onConfigChanged(ConfigChanged event) {
        // Default implementation - subclasses can override to handle specific config changes
        // This method is called when a config value for this module's config group changes
    }

    // Template methods for subclasses to override
    protected abstract void onStart();
    protected abstract void onStop();
}
