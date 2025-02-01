package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.annotations.Component;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class WelcomeScreenEvent implements BlockingEvent {
    @Component
    private static final int WELCOME_SCREEN_COMPONENT_ID = 24772680;
    
    @Override
    public boolean validate() {
        return Rs2Widget.isWidgetVisible(WELCOME_SCREEN_COMPONENT_ID);
    }

    @Override
    public boolean execute() {
        Widget welcomeScreenWidget = Rs2Widget.getWidget(WELCOME_SCREEN_COMPONENT_ID);
        
        Rs2Widget.clickWidget(welcomeScreenWidget.getId());

        Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(WELCOME_SCREEN_COMPONENT_ID), 10000);
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.HIGHEST;
    }
}
