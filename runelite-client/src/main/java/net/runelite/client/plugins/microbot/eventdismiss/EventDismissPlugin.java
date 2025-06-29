package net.runelite.client.plugins.microbot.eventdismiss;

import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Event Dismiss</html>",
        description = "Random Event Dismisser",
        tags = {"random", "events", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class EventDismissPlugin extends Plugin {
    @Inject
    private ConfigManager configManager;
    @Inject
    private EventDismissConfig config;

    private DismissNpcEvent dismissNpcEvent;

    @Provides
    EventDismissConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EventDismissConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        dismissNpcEvent = new DismissNpcEvent(config);
        Microbot.getBlockingEventManager().add(dismissNpcEvent);
    }

    protected void shutDown() {
        Microbot.getBlockingEventManager().remove(dismissNpcEvent);
        dismissNpcEvent = null;
    }
}
