package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class WelcomeScreenEvent implements BlockingEvent {
    
    @Override
    public boolean validate() {
        return Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY);
    }

    @Override
    public boolean execute() {
        Widget welcomeScreenWidget = Rs2Widget.getWidget(InterfaceID.WelcomeScreen.PLAY);
        
        if (welcomeScreenWidget != null) {
            Rs2Widget.clickWidget(welcomeScreenWidget);
        } else {
            System.out.println("WelcomeScreenEvent execute: WelcomeScreenWidget is null");
        }

        return Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY), 10000);
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.HIGHEST;
    }
}
