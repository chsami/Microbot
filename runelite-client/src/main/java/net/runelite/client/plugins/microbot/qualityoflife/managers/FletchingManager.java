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
import net.runelite.client.plugins.microbot.qualityoflife.enums.FletchingArrow;
import net.runelite.client.plugins.microbot.qualityoflife.enums.FletchingBolt;
import net.runelite.client.plugins.microbot.qualityoflife.enums.FletchingDarts;
import net.runelite.client.plugins.microbot.qualityoflife.enums.FletchingLogs;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt.WintertodtScript.isInWintertodtRegion;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class FletchingManager
{
    private final QoLConfig config;

    @Inject
    public FletchingManager(QoLConfig config)
    {
        this.config = config;
    }

    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!Microbot.isLoggedIn()) return;

        String option = event.getOption();
        String target = event.getTarget();

        // Quick Fletch Kindling (Wintertodt)
        if (config.quickFletchKindling()
                && isInWintertodtRegion()
                && event.getItemId() == ItemID.KNIFE
                && "Use".equals(event.getOption()))
        {
            modifyMenuEntry(event, "<col=FFA500>Fletch Kindle</col>", "", this::fletchBrumaRootsOnClick);
        }

        // Quick Fletch Items (Logs -> Bows, etc.)
        if (config.quickFletchItems()
                && !isInWintertodtRegion()
                && event.getItemId() == ItemID.KNIFE
                && "Use".equals(event.getOption()))
        {
            modifyMenuEntry(event, "<col=FFA500>Quick fletch: </col>", config.fletchingItem().getName(), this::quickFletchLogsOnClick);
        }

        // Quick Fletch Headless Arrows
        if (config.quickFletchHeadlessArrows()
                && event.getItemId() == ItemID.ARROW_SHAFT
                && "Use".equals(event.getOption()))
        {
            modifyMenuEntry(event, "<col=FFA500>Quick fletch: </col>", "Headless arrow", this::quickFletchHeadlessArrowOnClick);
        }

        // Quick Fletch Darts
        if (config.quickFletchDarts() && Arrays.stream(FletchingDarts.values()).anyMatch(dart -> dart.getDartTipId() == event.getItemId() && dart.meetsLevelRequirement()) && "Use".equals(option)) {

            FletchingDarts dart = FletchingDarts.getDartByDartTipId(event.getItemId());
            if (dart != null && dart.meetsLevelRequirement()) {
                modifyMenuEntry(event, "<col=FFA500>Quick fletch: </col>", dart.getDart(), e -> quickFletchDartsOnClick(e, dart));
            }
        }

        // Quick Fletch Arrows
        if (config.quickFletchArrows() && Arrays.stream(FletchingArrow.values()).anyMatch(arrow -> arrow.getArrowTipId() == event.getItemId() && arrow.meetsLevelRequirement()) && "Use".equals(option)) {

            FletchingArrow arrow = FletchingArrow.getArrowByArrowTipId(event.getItemId());
            if (arrow != null && arrow.meetsLevelRequirement()) {
                modifyMenuEntry(event, "<col=FFA500>Quick fletch: </col>", arrow.getArrow(), e -> quickFletchArrowOnClick(e, arrow));
            }
        }

        // Quick Fletch Bolts
        if( config.quickFletchBolts() && Arrays.stream(FletchingBolt.values()).anyMatch(bolt -> bolt.getBoltTipId() == event.getItemId() && bolt.meetsLevelRequirement()) && "Use".equals(option))
        {
            FletchingBolt bolt = FletchingBolt.getBoltByBoltTipId(event.getItemId());
            if (bolt != null && bolt.meetsLevelRequirement()) {
                modifyMenuEntry(event, "<col=FFA500>Quick fletch: </col>", bolt.getBolt(), e -> quickFletchBoltOnClick(e, bolt));
            }
        }
    }

    private void modifyMenuEntry(MenuEntryAdded event, String newOption, String newTarget, java.util.function.Consumer<MenuEntry> onClick)
    {
        MenuEntry menuEntry = event.getMenuEntry();
        menuEntry.setOption(newOption);
        menuEntry.setTarget(newTarget);
        menuEntry.onClick(onClick);
    }

    private void fletchBrumaRootsOnClick(MenuEntry event)
    {
        int brumaRootSlot = Rs2Inventory.slot(ItemID.BRUMA_ROOT);
        if (brumaRootSlot == -1)
        {
            Microbot.log("<col=5F1515>Bruma root not found in inventory</col>");
            return;
        }
        Microbot.log("<col=245C2D>Fletching Kindling</col>");
        NewMenuEntry combined = createWidgetOnWidgetEntry("Fletch", "Bruma root", brumaRootSlot, event.getParam1(), ItemID.BRUMA_ROOT);
        Microbot.doInvoke(combined, new Rectangle(1, 1));
    }

    private void quickFletchLogsOnClick(MenuEntry event)
    {
        List<Rs2ItemModel> fletchableLogs = FletchingLogs.getFletchableLogs(config.fletchingItem());
        if (fletchableLogs.isEmpty())
        {
            Microbot.log("<col=5F1515>No fletchable logs found in inventory</col>");
            return;
        }
        Rs2ItemModel logToFletch = fletchableLogs.get(0);
        if (logToFletch.getSlot() == -1)
        {
            Microbot.log("<col=5F1515>Couldn't get item slot</col>");
            return;
        }

        Microbot.log("<col=245C2D>Fletching: " + logToFletch.getName() + " To: " + config.fletchingItem().getName() + "</col>");
        NewMenuEntry combined = createWidgetOnWidgetEntry("Fletch", logToFletch.getName(), logToFletch.getSlot(), event.getParam1(), logToFletch.getId());
        Microbot.doInvoke(combined, new Rectangle(1, 1));

        Microbot.getClientThread().runOnSeperateThread(() -> {
            sleepUntil(Rs2Widget::isProductionWidgetOpen, 1000);
            if (Rs2Widget.isProductionWidgetOpen()) {
                Rs2Widget.clickWidget(config.fletchingItem().getContainsInventoryName(), Optional.of(270),13,false);
            }
            return null;
        });
    }

    private void quickFletchHeadlessArrowOnClick(MenuEntry event)
    {
        int featherSlot = Rs2Inventory.slot(ItemID.FEATHER);
        if (featherSlot == -1) {
            Microbot.log("<col=5F1515>Feather not found in inventory</col>");
            return;
        }

        Microbot.log("<col=245C2D>Fletching: Headless Arrow</col>");
        NewMenuEntry combined = createWidgetOnWidgetEntry("Fletch", "Feather", featherSlot, event.getParam1(), ItemID.FEATHER);
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), combined);

        Microbot.getClientThread().runOnSeperateThread(() -> {
            sleepUntil(Rs2Widget::isProductionWidgetOpen, 1000);
            if (Rs2Widget.isProductionWidgetOpen()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }
            return null;
        });
    }

    private void quickFletchDartsOnClick(MenuEntry event, FletchingDarts dart)
    {
        int featherSlot = Rs2Inventory.slot(ItemID.FEATHER);
        if (featherSlot == -1) {
            Microbot.log("<col=5F1515>Feather not found in inventory</col>");
            return;
        }

        Microbot.log("<col=245C2D>Fletching: " + dart.getDartTip() + " To: " + dart.getDart() + "</col>");
        NewMenuEntry combined = createWidgetOnWidgetEntry("Fletch", "Feather", featherSlot, event.getParam1(), ItemID.FEATHER);
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), combined);
    }

    private void quickFletchBoltOnClick(MenuEntry event, FletchingBolt bolt)
    {
        int featherSlot = Rs2Inventory.slot(ItemID.FEATHER);
        if (featherSlot == -1) {
            Microbot.log("<col=5F1515>Feather not found in inventory</col>");
            return;
        }

        Microbot.log("<col=245C2D>Fletching: " + bolt.getBoltTip() + " To: " + bolt.getBolt() + "</col>");
        NewMenuEntry combined = createWidgetOnWidgetEntry("Fletch", "Feather", featherSlot, event.getParam1(), ItemID.FEATHER);
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), combined);
    }

    private void quickFletchArrowOnClick(MenuEntry event, FletchingArrow arrow)
    {
        int headlessSlot = Rs2Inventory.slot(ItemID.HEADLESS_ARROW);
        if (headlessSlot == -1) {
            Microbot.log("<col=5F1515>Headless Arrow not found in inventory</col>");
            return;
        }

        Microbot.log("<col=245C2D>Fletching: " + arrow.getArrow() + "</col>");
        NewMenuEntry combined = createWidgetOnWidgetEntry("Fletch", "Feather", headlessSlot, event.getParam1(), ItemID.HEADLESS_ARROW);
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), combined);

        Microbot.getClientThread().runOnSeperateThread(() -> {
            sleepUntil(Rs2Widget::isProductionWidgetOpen, 1000);
            if (Rs2Widget.isProductionWidgetOpen()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }
            return null;
        });
    }

    /**
     * Helper method to create a MenuEntry for widget-on-widget interactions (like using one item on another)
     */
    private NewMenuEntry createWidgetOnWidgetEntry(String option, String target, int param0, int param1, int itemId)
    {
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
