package net.runelite.client.plugins.microbot.util.tabs;

import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.VarcIntValues;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public class Rs2Tab {
    public static InterfaceTab getCurrentTab() {
        int varcIntValue = Microbot.getClient().getVarcIntValue(VarClientInt.INVENTORY_TAB);
        switch (VarcIntValues.valueOf(varcIntValue)) {
            case TAB_COMBAT_OPTIONS:
                return InterfaceTab.COMBAT;
            case TAB_SKILLS:
                return InterfaceTab.SKILLS;
            case TAB_QUEST_LIST:
                return InterfaceTab.QUESTS;
            case TAB_INVENTORY:
                return InterfaceTab.INVENTORY;
            case TAB_WORN_EQUIPMENT:
                return InterfaceTab.EQUIPMENT;
            case TAB_PRAYER:
                return InterfaceTab.PRAYER;
            case TAB_SPELLBOOK:
                return InterfaceTab.MAGIC;
            case TAB_FRIEND_LIST:
                return InterfaceTab.FRIENDS;
            case TAB_LOGOUT:
                return InterfaceTab.LOGOUT;
            case TAB_SETTINGS:
                return InterfaceTab.SETTINGS;
            case TAB_MUSIC:
                return InterfaceTab.MUSIC;
            case TAB_CHAT_CHANNEL:
                return InterfaceTab.CHAT;
            case TAB_ACC_MANAGEMENT:
                return InterfaceTab.ACC_MAN;
            case TAB_EMOTES:
                return InterfaceTab.EMOTES;
            case TAB_NOT_SELECTED:
                return InterfaceTab.NOTHING_SELECTED;
            default:
                throw new IllegalStateException("Unexpected value: " + VarcIntValues.valueOf(varcIntValue));
        }
    }


    public static boolean switchToInventoryTab() {
        if (getCurrentTab() == InterfaceTab.INVENTORY) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4678), InterfaceTab.INVENTORY));
        return getCurrentTab() == InterfaceTab.INVENTORY;
    }

    public static boolean switchToCombatOptionsTab() {
        if (getCurrentTab() == InterfaceTab.COMBAT) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4675), InterfaceTab.COMBAT));
        return getCurrentTab() == InterfaceTab.COMBAT;
    }

    public static boolean switchToSkillsTab() {
        if (getCurrentTab() == InterfaceTab.SKILLS) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4676), InterfaceTab.SKILLS));
        return getCurrentTab() == InterfaceTab.SKILLS;
    }

    public static boolean switchToQuestTab() {
        if (getCurrentTab() == InterfaceTab.QUESTS) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4677), InterfaceTab.QUESTS));
        return getCurrentTab() == InterfaceTab.QUESTS;
    }


    public static boolean switchToEquipmentTab() {
        if (getCurrentTab() == InterfaceTab.EQUIPMENT) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4679), InterfaceTab.EQUIPMENT));
        return getCurrentTab() == InterfaceTab.EQUIPMENT;
    }

    public static boolean switchToPrayerTab() {
        if (getCurrentTab() == InterfaceTab.PRAYER) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4680), InterfaceTab.PRAYER));
        return getCurrentTab() == InterfaceTab.PRAYER;
    }

    public static boolean switchToMagicTab() {
        if (getCurrentTab() == InterfaceTab.MAGIC) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4682), InterfaceTab.MAGIC));
        return getCurrentTab() == InterfaceTab.MAGIC;
    }

    public static boolean switchToGroupingTab() {
        if (getCurrentTab() == InterfaceTab.CHAT) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4683), InterfaceTab.CHAT));
        return getCurrentTab() == InterfaceTab.CHAT;
    }

    public static boolean switchToFriendsTab() {
        if (getCurrentTab() == InterfaceTab.FRIENDS) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4684), InterfaceTab.FRIENDS));
        return getCurrentTab() == InterfaceTab.FRIENDS;
    }

    public static boolean switchToAccountManagementTab() {
        if (getCurrentTab() == InterfaceTab.ACC_MAN) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(6517),  InterfaceTab.ACC_MAN));
        return getCurrentTab() == InterfaceTab.ACC_MAN;
    }


    public static boolean switchToSettingsTab() {
        if (getCurrentTab() == InterfaceTab.SETTINGS) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4686),InterfaceTab.SETTINGS));
        return getCurrentTab() == InterfaceTab.SETTINGS;
    }

    public static boolean switchToEmotesTab() {
        if (getCurrentTab() == InterfaceTab.EMOTES) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4687), InterfaceTab.EMOTES));
        return getCurrentTab() == InterfaceTab.EMOTES;
    }

    public static boolean switchToMusicTab() {
        if (getCurrentTab() == InterfaceTab.MUSIC) {
            return true;
        }
        Rs2Keyboard.keyPress(getKeyBind(Microbot.getVarbitValue(4688), InterfaceTab.MUSIC));
        return getCurrentTab() == InterfaceTab.MUSIC;
    }

    public static boolean switchToLogout() {
        if (getCurrentTab() == InterfaceTab.LOGOUT) return true;

        int logout_widget_id = getLogoutWidgetId();

        if (logout_widget_id == 0) return false;

        Widget tab = Microbot.getClient().getWidget(logout_widget_id);
        if (tab == null) return false;

        Microbot.getMouse().click(tab.getBounds());
        sleep(200, 600);

        return getCurrentTab() == InterfaceTab.LOGOUT;
    }

    private static int getLogoutWidgetId() {
        /* Widget Ids - These may change during Runelite updates */
        final int FIXED_CLASSIC_DISPLAY__FIXED_VIEWPORT_OPTIONS_TAB = 35913778;
        final int RESIZABLE_CLASSIC_DISPLAY__RESIZABLE_VIEWPORT_LOGOUT_ICON = 10551342;

        try {
            if (Rs2Widget.getWidget(FIXED_CLASSIC_DISPLAY__FIXED_VIEWPORT_OPTIONS_TAB) != null) {
                return FIXED_CLASSIC_DISPLAY__FIXED_VIEWPORT_OPTIONS_TAB;
            } else if (Rs2Widget.getWidget(RESIZABLE_CLASSIC_DISPLAY__RESIZABLE_VIEWPORT_LOGOUT_ICON) != null) {
                return RESIZABLE_CLASSIC_DISPLAY__RESIZABLE_VIEWPORT_LOGOUT_ICON;
            } else {
                Microbot.showMessage("Logout for modern layout is not supported!");
            }
        } catch (Exception ex) {
            // Rs2Widget.getWidget returns null if the game isn't finished loading
            ex.printStackTrace();
        }

        return 0;
    }

    private static int getKeyBind(int value, InterfaceTab tab) {
        if (value == 1) return KeyEvent.VK_F1;
        if (value == 2) return KeyEvent.VK_F2;
        if (value == 3) return KeyEvent.VK_F3;
        if (value == 4) return KeyEvent.VK_F4;
        if (value == 5) return KeyEvent.VK_F5;
        if (value == 6) return KeyEvent.VK_F6;
        if (value == 7) return KeyEvent.VK_F7;
        if (value == 8) return KeyEvent.VK_F8;
        if (value == 9) return KeyEvent.VK_F9;
        if (value == 10) return KeyEvent.VK_F10;
        if (value == 11) return KeyEvent.VK_F11;
        if (value == 12) return KeyEvent.VK_F12;
        if (value == 13) return KeyEvent.VK_ESCAPE;

        if (value == 0 && Microbot.isLoggedIn()) {
            Microbot.showMessage("Keybinding not found for tab " + tab.getName() + ". Please fill in the keybinding in your settings");
            sleep(5000);
        }

        return -1;
    }


}
