package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.VirtualKeyboard;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.lang.reflect.InvocationTargetException;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilOnClientThread;

public class BetterBank {
    private static final int BANK_WIDGET_ID = 786445;
    private static final int INVENTORY_WIDGET_ID = 983043;

    private static final int X_AMOUNT_VARBIT = 3960;
    private static final int SELECTED_OPTION_VARBIT = 6590;

    private static final int HANDLE_X_SET = 5;
    private static final int HANDLE_X_UNSET = 6;
    private static final int HANDLE_ALL = 7;


    private static int widgetId;
    private static int entryIndex;
    private static Widget widget;

    public static void handleMenuSwapper(MenuEntry menuEntry) throws InvocationTargetException, IllegalAccessException {
        if (widgetId == 0 || widget == null) return;
        Rs2Reflection.setItemId(menuEntry, widget.getItemId());
        menuEntry.setOption("Withdraw-1"); // Should probably be changed. Doesn't matter though.

        if (widgetId == INVENTORY_WIDGET_ID) {
            menuEntry.setIdentifier(entryIndex + 1);
        } else {
            menuEntry.setIdentifier(entryIndex);
        }

        menuEntry.setParam0(widget.getIndex());
        menuEntry.setParam1(widgetId);
        menuEntry.setTarget(widget.getName());
        menuEntry.setType(MenuAction.CC_OP);
    }

    public static void execMenuSwapper(int widgetId, int entryIndex, Widget widget) {
        BetterBank.widgetId = widgetId;
        BetterBank.entryIndex = entryIndex;
        BetterBank.widget = widget;

        Microbot.getMouse().clickFast(1, 1);
        sleep(50);

        BetterBank.widgetId = 0;
        BetterBank.entryIndex = 0;
        BetterBank.widget = null;
    }

    // UTILS
    public static boolean isOpen() {
        if (Rs2Widget.hasWidget("Please enter your PIN")) {
            Microbot.getNotifier().notify("[ATTENTION] Please enter your bankpin so the script can continue.");
            sleep(5000);
            return false;
        }
        return Rs2Widget.findWidget("Rearrange mode", null) != null;
    }

    public static boolean close() {
        if (!isOpen()) return false;
        Rs2Widget.clickChildWidget(786434, 11);
        sleepUntilOnClientThread(() -> !isOpen());

        return true;
    }

    public static Widget findBankItem(int id) {
        Widget w = Microbot.getClient().getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        if (w == null) return null;

        for (Widget item : w.getDynamicChildren()) {
            if (item.getItemId() == id) {
                return item;
            }
        }

        return null;
    }

    public static Widget findBankItem(String name, boolean exact) {
        Widget w = Microbot.getClient().getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        if (w == null) return null;

        for (Widget item : w.getDynamicChildren()) {
            String itemNameInItem = item.getName().split(">")[1].split("</")[0].toLowerCase();
            String targetItemName = name.toLowerCase();

            if (exact ? itemNameInItem.equals(targetItemName) : itemNameInItem.contains(targetItemName)) {
                return item;
            }
        }
        return null;
    }

    public static Widget findBankItem(String name) {
        return findBankItem(name, false);
    }


    // DEPOSIT
    public static boolean depositEquipment() {
        Widget widget = Rs2Widget.findWidget(SpriteID.BANK_DEPOSIT_EQUIPMENT, null);
        if (widget == null) return false;

        Microbot.getMouse().click(widget.getBounds());
        return true;
    }

    private static boolean depositOneFast(Widget w) {
        if (!isOpen()) return false;
        if (w == null) return false;
        if (!Rs2Inventory.hasItem(w.getItemId())) return false;

        if (Microbot.getVarbitValue(SELECTED_OPTION_VARBIT) == 0) {
            execMenuSwapper(INVENTORY_WIDGET_ID, 1, w);
        } else {
            execMenuSwapper(INVENTORY_WIDGET_ID, 2, w);
        }

        return true;
    }

    public static boolean depositOneFast(int id) {
        return depositOneFast(Rs2Inventory.findItem(id));
    }

    public static boolean depositOneFast(String name, boolean exact) {
        return depositOneFast(Rs2Inventory.findItem(name, exact));
    }
    public static boolean depositOneFast(String name) {
        return depositOneFast(name, false);
    }


    private static boolean depositXFast(Widget w, int amount) {
        if (!isOpen()) return false;
        if (w == null) return false;
        if (!Rs2Inventory.hasItem(w.getItemId())) return false;

        if (Microbot.getVarbitValue(X_AMOUNT_VARBIT) == amount) {
            execMenuSwapper(INVENTORY_WIDGET_ID, HANDLE_X_SET, w);
        } else {
            execMenuSwapper(INVENTORY_WIDGET_ID, HANDLE_X_UNSET, w);

            sleep(600, 1000);
            VirtualKeyboard.typeString(String.valueOf(amount));
            VirtualKeyboard.enter();
            sleep(50, 100);
        }

        return true;
    }

    public static boolean depositXFast(int id, int amount) {
        return depositXFast(Rs2Inventory.findItem(id), amount);
    }

    public static boolean depositXFast(String name, int amount, boolean exact) {
        return depositXFast(Rs2Inventory.findItem(name, exact), amount);
    }

    public static boolean depositXFast(String name, int amount) {
        return depositXFast(Rs2Inventory.findItem(name), amount);
    }

    private static boolean depositAllFast(Widget w) {
        if (!isOpen()) return false;
        if (w == null) return false;
        if (!Rs2Inventory.hasItem(w.getItemId())) return false;

        execMenuSwapper(INVENTORY_WIDGET_ID, HANDLE_ALL, w);

        return true;
    }

    public static boolean depositAllFast(int id) {
        return depositAllFast(Rs2Inventory.findItem(id));
    }

    public static boolean depositAllFast(String name, boolean exact) {
        return depositAllFast(Rs2Inventory.findItem(name, exact));
    }
    public static boolean depositAllFast(String name) {
        return depositAllFast(name, false);
    }

    public static boolean depositAll() {
        Microbot.status = "Deposit all";
        if (Rs2Inventory.count() == 0) return true;

        Widget widget = Rs2Widget.findWidget(SpriteID.BANK_DEPOSIT_INVENTORY, null);
        if (widget == null) return false;

        Microbot.getMouse().click(widget.getBounds());
        return true;
    }


    // WITHDRAW
    private static boolean withdrawOneFast(Widget w) {
        if (!isOpen()) return false;
        if (w == null) return false;
        if (Rs2Inventory.isFull()) return false;

        if (Microbot.getVarbitValue(SELECTED_OPTION_VARBIT) == 0) {
            execMenuSwapper(BANK_WIDGET_ID, 1, w);
        } else {
            execMenuSwapper(BANK_WIDGET_ID, 2, w);
        }

        return true;
    }

    public static boolean withdrawOneFast(int id) {
        return withdrawOneFast(findBankItem(id));
    }

    public static boolean withdrawOneFast(String name, boolean exact) {
        return withdrawOneFast(findBankItem(name, exact));
    }

    public static boolean withdrawOneFast(String name) {
        return withdrawOneFast(name, false);
    }

    private static boolean withdrawXFast(Widget w, int amount) {
        if (!isOpen()) return false;
        if (w == null) return false;
        if (Rs2Inventory.isFull()) return false;

        if (Microbot.getVarbitValue(X_AMOUNT_VARBIT) == amount) {
            execMenuSwapper(BANK_WIDGET_ID, HANDLE_X_SET, w);
        } else {
            execMenuSwapper(BANK_WIDGET_ID, HANDLE_X_UNSET, w);

            sleep(600, 1000);
            VirtualKeyboard.typeString(String.valueOf(amount));
            VirtualKeyboard.enter();
            sleep(50, 100);
        }

        return false;
    }

    public static boolean withdrawXFast(int id, int amount) {
        return withdrawXFast(findBankItem(id), amount);
    }

    public static boolean withdrawXFast(String name, int amount, boolean exact) {
        return withdrawXFast(findBankItem(name, exact), amount);
    }
    public static boolean withdrawXFast(String name, int amount) {
        return withdrawXFast(findBankItem(name, false), amount);
    }

    private static boolean withdrawAllFast(Widget w) {
        if (!isOpen()) return false;
        if (w == null) return false;
        if (Rs2Inventory.isFull()) return false;

        execMenuSwapper(BANK_WIDGET_ID, HANDLE_ALL, w);

        return true;
    }

    public static boolean withdrawAllFast(int id) {
        return withdrawAllFast(findBankItem(id));
    }

    public static boolean withdrawAllFast(String name, boolean exact) {
        return withdrawAllFast(findBankItem(name, exact));
    }

    public static boolean withdrawAllFast(String name) {
        return withdrawAllFast(findBankItem(name, false));
    }

    private static boolean wearItemFast(Widget w) {
        if (!isOpen()) return false;
        if (w == null) return false;

        execMenuSwapper(INVENTORY_WIDGET_ID, 8, w);

        return true;
    }

    public static boolean wearItemFast(int id) {
        return wearItemFast(Rs2Inventory.findItem(id));
    }

    public static boolean wearItemFast(String name) {
        return wearItemFast(Rs2Inventory.findItem(name));
    }

    public static void withdrawItems(int... ids) {
        for (int id : ids) {
            withdrawOneFast(id);
        }
    }

    public static void depositItems(int... ids) {
        for (int id : ids) {
            depositOneFast(id);
        }
    }
}
