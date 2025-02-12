package net.runelite.client.plugins.microbot.qualityoflife.managers;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.qualityoflife.enums.FiremakingLogs;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

import java.util.List;

@Slf4j
public class FiremakingManager {

    private final QoLConfig config;

    @Inject
    public FiremakingManager(QoLConfig config) {
        this.config = config;
    }

    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event) {
        if (!Microbot.isLoggedIn()) return;

        String option = event.getOption();
        int itemId = event.getItemId();

        if (itemId == ItemID.TINDERBOX && "Use".equals(option) && config.quickFiremakeLogs()) {
            modifyMenuEntry(event, "<col=FFA500>Light Logs</col>", "", this::lightLogsOnClick);
        }
    }

    private void modifyMenuEntry(MenuEntryAdded event, String newOption, String newTarget, java.util.function.Consumer<MenuEntry> onClick) {
        MenuEntry menuEntry = event.getMenuEntry();
        menuEntry.setOption(newOption);
        menuEntry.setTarget(newTarget);
        menuEntry.onClick(onClick);
    }

    private void lightLogsOnClick(MenuEntry event) {
        List<Rs2ItemModel> logToLight = FiremakingLogs.getLogs();

        if (logToLight.isEmpty()) {
            Microbot.log("<col=5F1515>No logs available or level too low to light logs</col>");
            return;
        }

        Rs2ItemModel logItem = logToLight.get(0);
        if (logItem == null) {
            Microbot.log("<col=5F1515>Could not find the selected log in inventory</col>");
            return;
        }

        Microbot.log("<col=245C2D>Lighting: " + logItem.getName() + "</col>");
        NewMenuEntry combined = createWidgetOnWidgetEntry("Light", logItem.getName(), logItem.getSlot(), event.getParam1(), logItem.getId());
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), combined);
    }

    private NewMenuEntry createWidgetOnWidgetEntry(String option, String target, int param0, int param1, int itemId) {
        NewMenuEntry combined = new NewMenuEntry();
        combined.setOption(option);
        combined.setTarget(target);
        combined.setParam0(param0);
        combined.setParam1(param1);
        combined.setIdentifier(0);
        combined.setType(MenuAction.WIDGET_TARGET_ON_WIDGET);
        combined.setItemId(itemId);
        return combined;
    }
}
