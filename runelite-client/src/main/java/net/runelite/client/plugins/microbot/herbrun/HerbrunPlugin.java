//package net.runelite.client.plugins.microbot.herbrun;
//
//import com.google.inject.Provides;
//import lombok.extern.slf4j.Slf4j;
//import net.runelite.api.events.GameTick;
//import net.runelite.client.config.ConfigManager;
//import net.runelite.client.eventbus.Subscribe;
//import net.runelite.client.plugins.Plugin;
//import net.runelite.client.plugins.PluginDescriptor;
//import net.runelite.client.plugins.microbot.Microbot;
//import net.runelite.client.plugins.microbot.pluginscheduler.event.ScheduledStopEvent;
//import net.runelite.client.ui.overlay.OverlayManager;
//
//import javax.inject.Inject;
//import java.awt.*;
//
//@Slf4j
//@PluginDescriptor(
//        name = PluginDescriptor.Mocrosoft + "Herb runner",
//        description = "Herb runner",
//        tags = {"herb", "farming", "money making", "skilling"},
//        enabledByDefault = false,
//        canBeScheduled = true
//)
//public class HerbrunPlugin extends Plugin {
//    @Inject
//    private HerbrunConfig config;
//    @Provides
//    HerbrunConfig provideConfig(ConfigManager configManager) {
//        return configManager.getConfig(HerbrunConfig.class);
//    }
//
//    @Inject
//    private OverlayManager overlayManager;
//    @Inject
//    private HerbrunOverlay HerbrunOverlay;
//
//    @Inject
//    HerbrunScript herbrunScript;
//
//    static String status;
//
//
//    @Override
//    protected void startUp() throws AWTException {
//        if (overlayManager != null) {
//            overlayManager.add(HerbrunOverlay);
//        }
//        herbrunScript.run();
//    }
//
//    protected void shutDown() {
//        herbrunScript.shutdown();
//        overlayManager.remove(HerbrunOverlay);
//    }
//
//    @Subscribe
//    public void onScheduledStopEvent(ScheduledStopEvent event) {
//        if (event.getPlugin() == this) {
//            Microbot.stopPlugin(this);
//        }
//    }
//
//}
