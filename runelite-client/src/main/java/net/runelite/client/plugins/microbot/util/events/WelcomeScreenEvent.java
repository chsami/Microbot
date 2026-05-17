package net.runelite.client.plugins.microbot.util.events;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.function.Supplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class WelcomeScreenEvent implements BlockingEvent {
    
    @Override
    public boolean validate() {
        if (Microbot.isLoggedIn()) {
            return false;
        }
        return Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY);
    }

    @Override
    public boolean execute() {
        // Widget mutations must run on the client thread; this event executes on Microbot-BlockingEvent.
        Boolean clickedPlay = Microbot.getClientThread().invoke((Supplier<Boolean>) () -> {
            Client client = Microbot.getClient();
            Widget updateBottomRibbon = client.getWidget(InterfaceID.WelcomeScreen.URL);
            if (updateBottomRibbon != null) {
                updateBottomRibbon.setOnClickListener((Object[]) null);
                updateBottomRibbon.setOnOpListener((Object[]) null);
                log.info("WelcomeScreenEvent execute: Cleared update ribbon listener to avoid accidental page opening.");
            } else {
                log.info("WelcomeScreenEvent execute: Update ribbon widget is null");
            }

            Widget newsBanner = client.getWidget(InterfaceID.WelcomeScreen.BANNER);
            if (newsBanner != null) {
                newsBanner.setHidden(true);
                log.info("WelcomeScreenEvent execute: Cleared banner to avoid accidental page openings.");
            } else {
                log.info("WelcomeScreenEvent execute: Banner widget is null");
            }

            Widget playWidget = client.getWidget(InterfaceID.WelcomeScreen.PLAY);
            boolean isPlayWidgetVisible = playWidget != null && !playWidget.isHidden();
            boolean wasNewsBannerHandled = newsBanner == null || newsBanner.isHidden();
            boolean wasUpdateRibbonHandled = updateBottomRibbon == null || updateBottomRibbon.getOnOpListener() == null;

            if (playWidget != null && isPlayWidgetVisible && wasUpdateRibbonHandled && wasNewsBannerHandled) {
                log.info("WelcomeScreenEvent execute: Clicking play button.");
                Rs2Widget.clickWidget(playWidget);
                return true;
            }
            log.info("WelcomeScreenEvent execute: required UI not ready (playWidgetNull={} playWidgetVisible={} bannerHandled={} ribbonHandled={})",
                    playWidget == null, isPlayWidgetVisible, wasNewsBannerHandled, wasUpdateRibbonHandled);
            return false;
        });

        if (Boolean.TRUE.equals(clickedPlay)) {
            sleepUntil(() -> !validate());
        }

        return !validate();
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.HIGHEST;
    }
}
