package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.annotations.Component;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.List;

public class BankJagexPopupEvent implements BlockingEvent {
    
    @Component
    private final int WANT_MORE_BANK_SPACE_COMPONENT_ID = 18939909;
    
    @Override
    public boolean validate() {
        return Rs2Widget.isWidgetVisible(WANT_MORE_BANK_SPACE_COMPONENT_ID);
    }

    @Override
    public boolean execute() {
        Widget parentWidget = Rs2Widget.getWidget(WANT_MORE_BANK_SPACE_COMPONENT_ID);
        if (parentWidget == null) return false;
        
        Widget notNowButton = Rs2Widget.findWidget("Not now", List.of(parentWidget), false);
        if (notNowButton == null) return false;
        
        Rs2Widget.clickWidget(notNowButton);
        Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(WANT_MORE_BANK_SPACE_COMPONENT_ID), 10000);
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
