package net.runelite.client.plugins.microbot.util.settings;

import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.globval.VarbitIndices.TOGGLE_ROOFS;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class Rs2Settings {

    static final int DROP_SHIFT_SETTING = 5542;
    static final int SETTINGS_INTERFACE = 8781825;
    static final int SETTINGS_SEARCHBAR = 8781834;
    static final int ALL_SETTINGS_BUTTON = 7602208;

    public static boolean openSettings() {
        boolean isSettingsInterfaceVisible = Rs2Widget.isWidgetVisible(ComponentID.SETTINGS_INIT);
        if (!isSettingsInterfaceVisible) {
            Rs2Tab.switchToSettingsTab();
            Rs2Widget.clickWidget(ALL_SETTINGS_BUTTON);
            return false;
        }
        return true;
    }

    public static boolean isDropShiftSettingEnabled() {
        return Microbot.getVarbitValue(DROP_SHIFT_SETTING) == 1;
    }

    public static boolean enableDropShiftSetting(boolean closeInterface) {
        if (Rs2Widget.hasWidget("Click here to continue")) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }
        if (!isDropShiftSettingEnabled()) {
            Rs2Tab.switchToSettingsTab();

            boolean isSettingsInterfaceVisible = Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE);
            if (!isSettingsInterfaceVisible) {
                Rs2Widget.clickWidget(ALL_SETTINGS_BUTTON);
                return false;
            }

            Rs2Widget.clickWidget("controls", true);
            sleep(600);
            Rs2Widget.clickWidget("Shift click to drop items");
            sleep(600);
            if (closeInterface) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                Rs2Tab.switchToInventoryTab();
            }
        }
        return isDropShiftSettingEnabled();
    }

    public static boolean enableDropShiftSetting() {
        return enableDropShiftSetting(true);
    }

    public static boolean isHideRoofsEnabled() {
        return Microbot.getVarbitValue(TOGGLE_ROOFS) == 1;
    }

    public static boolean hideRoofs(boolean closeInterface) {
        if (!isHideRoofsEnabled()) {
            Rs2Tab.switchToSettingsTab();

            boolean isSettingsInterfaceVisible = Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE);
            if (!isSettingsInterfaceVisible) {
                Rs2Widget.clickWidget(ALL_SETTINGS_BUTTON);
                return false;
            }

            Rs2Widget.clickWidget(SETTINGS_SEARCHBAR);
            Rs2Keyboard.typeString("roofs");
            sleep(600);
            Rs2Widget.clickWidget("Hide roofs");
            sleep(600);
            if (closeInterface) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                Rs2Tab.switchToInventoryTab();
            }
        }
        return isHideRoofsEnabled();
    }

    public static boolean hideRoofs() {
        return hideRoofs(true);
    }

    public static boolean isLevelUpNotificationsEnabled() {
        return Microbot.getVarbitValue(Varbits.DISABLE_LEVEL_UP_INTERFACE) == 1;
    }

    public static boolean disableLevelUpNotifications(boolean closeInterface) {
        if (!isLevelUpNotificationsEnabled()) {
            Rs2Tab.switchToSettingsTab();

            boolean isSettingsInterfaceVisible = Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE);
            if (!isSettingsInterfaceVisible) {
                Rs2Widget.clickWidget(ALL_SETTINGS_BUTTON);
                return false;
            }

            Rs2Widget.clickWidget(SETTINGS_SEARCHBAR);
            Rs2Keyboard.typeString("level-");
            sleep(600);
            Rs2Widget.clickWidget("Disable level-up interface");
            sleep(600);
            if (closeInterface) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                Rs2Tab.switchToInventoryTab();
            }
        }
        return isLevelUpNotificationsEnabled();
    }

    public static boolean disableLevelUpNotifications() {
        return disableLevelUpNotifications(true);
    }

    public static void turnOffMusic() {
        Rs2Tab.switchToSettingsTab();
        sleep(600);
        Rs2Widget.clickWidget(116, 68);
        sleep(600);
        boolean isMusicTurnedOff = !Rs2Widget.getWidget(116, 93).getChildren()[1].isSelfHidden();
        boolean isSoundEffectOff = !Rs2Widget.getWidget(116, 107).getChildren()[1].isSelfHidden();
        boolean isAreaSoundEffectOff = !Rs2Widget.getWidget(116, 122).getChildren()[1].isSelfHidden();
        if (isMusicTurnedOff && isSoundEffectOff && isAreaSoundEffectOff)
            return;
        Rs2Widget.clickWidget(7602244);
        sleep(1000);
        if (!isMusicTurnedOff)
            Rs2Widget.clickWidget(7602269);
        if (!isSoundEffectOff)
            Rs2Widget.clickWidget(7602283);
        if (!isAreaSoundEffectOff)
            Rs2Widget.clickWidget(7602298);
    }

    /**
     * When casting alchemy spells on items in your inventory
     * if the item is worth more than this value, a warning will be shown
     *
     * @return
     */
    public static int getMinimumItemValueAlchemyWarning() {
        return Microbot.getVarbitValue(6091);
    }

    /**
     * disables levelup interfaces
     *
     * @return
     */
    public static boolean disableLevelUpInterface() {
        if (Microbot.getVarbitValue(Varbits.DISABLE_LEVEL_UP_INTERFACE) == 1) return true;
        if (!openSettings()) return false;

        int tries = 0;

        while (Microbot.getVarbitValue(Varbits.DISABLE_LEVEL_UP_INTERFACE) == 0 || tries > 3) {
            Rs2Widget.clickWidget(SETTINGS_SEARCHBAR);
            Rs2Keyboard.typeString("disable level");
            sleepUntil(() -> Rs2Widget.hasWidget("Disable level-up"));
            if (Rs2Widget.clickWidget("Disable level-up interface")) {
                sleepUntil(() -> Microbot.getVarbitValue(Varbits.DISABLE_LEVEL_UP_INTERFACE) == 1);
            }
            tries++;
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        Rs2Tab.switchToInventoryTab();

        return Microbot.getVarbitValue(Varbits.DISABLE_LEVEL_UP_INTERFACE) == 1;
    }
}
