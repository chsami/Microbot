package net.runelite.client.plugins.microbot.util.events;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Slf4j
public class WelcomeScreenEvent implements BlockingEvent {
    
    @Override
    public boolean validate() {
        return Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY);
    }

    @Override
    public boolean execute() {
        Widget updateBottomRibbon = Rs2Widget.getWidget(InterfaceID.WelcomeScreen.URL);
        if (updateBottomRibbon != null) {
            updateBottomRibbon.setOnClickListener(null);
            updateBottomRibbon.setOnOpListener(null);
            log.info("WelcomeScreenEvent execute: Cleared update ribbon listener to avoid accidental page opening.");
        } else {
            log.info("WelcomeScreenEvent execute: Update ribbon widget is null");
        }

        Widget newsBanner = Rs2Widget.getWidget(InterfaceID.WelcomeScreen.BANNER);
        if (newsBanner != null) {
            newsBanner.setHidden(true);
            log.info("WelcomeScreenEvent execute: Cleared banner to avoid accidental page openings.");
        } else {
            log.info("WelcomeScreenEvent execute: Banner widget is null");
        }

        Widget playWidget = Rs2Widget.getWidget(InterfaceID.WelcomeScreen.PLAY);
        boolean isPlayWidgetVisible = Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY);
        boolean wasNewsBannerHandled = (newsBanner == null || Rs2Widget.isHidden(InterfaceID.WelcomeScreen.BANNER));
        boolean wasUpdateRibbonHandled = (updateBottomRibbon == null || updateBottomRibbon.getOnOpListener() == null);

        if (playWidget != null && isPlayWidgetVisible && wasUpdateRibbonHandled && wasNewsBannerHandled) {
            log.info("WelcomeScreenEvent execute: Clicking play button.");
            Rs2Widget.clickWidget(playWidget);
        } else {
            log.info("WelcomeScreenEvent execute: Play button is null");
        }

        return Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY), 10000);
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.HIGHEST;
    }
}
