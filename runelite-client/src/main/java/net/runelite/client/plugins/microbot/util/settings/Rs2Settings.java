package net.runelite.client.plugins.microbot.util.settings;

import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.globval.VarbitIndices.TOGGLE_ROOFS;
import static net.runelite.client.plugins.microbot.util.Global.*;

public class Rs2Settings {

    static final int DROP_SHIFT_SETTING = 11556;
    static final int ESC_CLOSE_INTERFACE_SETTING = 4681;
    static final int SETTINGS_INTERFACE = 8781825;
    static final int SETTINGS_SEARCHBAR = 8781834;
    static final int ALL_SETTINGS_BUTTON = 7602208;

    public static boolean openSettings() {
        boolean isSettingsInterfaceVisible = Rs2Widget.isWidgetVisible(ComponentID.SETTINGS_INIT);
        if (!isSettingsInterfaceVisible) {
            if (Rs2Tab.getCurrentTab() != InterfaceTab.SETTINGS) {
                Rs2Tab.switchToSettingsTab();
                sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.SETTINGS);
            }
            Rs2Widget.clickWidget(ALL_SETTINGS_BUTTON);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(ComponentID.SETTINGS_INIT));
        }
        return true;
    }

    public static boolean isDropShiftSettingEnabled() {
        return Microbot.getVarbitValue(DROP_SHIFT_SETTING) == 1;
    }
    
    public static boolean isEscCloseInterfaceSettingEnabled() {
        return Microbot.getVarbitValue(ESC_CLOSE_INTERFACE_SETTING) == 1;
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
        return Microbot.getVarbitValue(Varbits.DISABLE_LEVEL_UP_INTERFACE) == 0;
    }

    public static boolean disableLevelUpNotifications(boolean closeInterface) {
        if (!isLevelUpNotificationsEnabled()) return true;
        if (!openSettings()) return false;

        Rs2Widget.clickWidget(SETTINGS_SEARCHBAR);
        Rs2Keyboard.typeString("level-");
        Rs2Widget.sleepUntilHasWidget("Disable level-up interface");
        Rs2Widget.clickWidget("Disable level-up interface");
        sleepUntil(() -> !isLevelUpNotificationsEnabled());

        if (closeInterface) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            Rs2Tab.switchToInventoryTab();
        }
        return isLevelUpNotificationsEnabled();
    }

    public static boolean disableLevelUpNotifications() {
        return disableLevelUpNotifications(true);
    }

    public static void turnOffMusic() {
        Rs2Tab.switchToSettingsTab();
        sleepGaussian(800, 100);
        Rs2Widget.clickWidget(116, 67);
        sleepGaussian(800, 100);
        var musicBtn = Rs2Widget.getWidget(ComponentID.SETTINGS_SIDE_MUSIC_SLIDER).getStaticChildren()[0];
        var soundEffectBtn = Rs2Widget.getWidget(ComponentID.SETTINGS_SIDE_SOUND_EFFECT_SLIDER).getStaticChildren()[0];
        var areaSoundBtn = Rs2Widget.getWidget(ComponentID.SETTINGS_SIDE_AREA_SOUND_SLIDER).getStaticChildren()[0];
        if (musicBtn == null || soundEffectBtn == null|| areaSoundBtn == null)
        {
            Microbot.log("Music settings buttons not found");
            return;
        }

        if (musicBtn.getActions() == null || soundEffectBtn.getActions() == null || areaSoundBtn.getActions() == null)
        {
            Microbot.log("Music settings buttons actions not found");
            return;
        }

        boolean isMusicOn = musicBtn.getActions()[0].toLowerCase().equalsIgnoreCase("mute");
        boolean isSoundEffectOn = soundEffectBtn.getActions()[0].equalsIgnoreCase("mute");
        boolean isAreaSoundEffectOn = areaSoundBtn.getActions()[0].equalsIgnoreCase("mute");
        if (!isMusicOn && !isSoundEffectOn && !isAreaSoundEffectOn)
            return;

        if (isMusicOn) {
            Rs2Widget.clickWidget(musicBtn);
            sleepGaussian(600, 150);
        }
        if (isSoundEffectOn) {
            Rs2Widget.clickWidget(soundEffectBtn);
            sleepGaussian(600, 150);
        }
        if (isAreaSoundEffectOn) {
            Rs2Widget.clickWidget(areaSoundBtn);
            sleepGaussian(600, 150);
        }
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
}
