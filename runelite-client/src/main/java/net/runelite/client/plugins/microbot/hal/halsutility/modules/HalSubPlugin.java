package net.runelite.client.plugins.microbot.hal.halsutility.modules;

import com.google.inject.Injector;
import com.google.inject.Module;
import net.runelite.client.config.Config;
import net.runelite.client.plugins.microbot.hal.halsutility.HalsUtilityandPluginsPlugin;

public interface HalSubPlugin extends Module {
    void start();
    void stop();
    void setInjector(Injector injector);
    Injector getInjector();
    void setPlugin(HalsUtilityandPluginsPlugin plugin);
    Config getConfig();
    
    // Additional required methods
    Class<? extends Config> getConfigClass();
    HalModuleCategory getCategory();
    boolean isRunning();
    String getDisplayName();
}
