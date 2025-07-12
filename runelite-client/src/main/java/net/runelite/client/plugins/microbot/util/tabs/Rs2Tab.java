package net.runelite.client.plugins.microbot.util.tabs;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.VarClientInt;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.VarcIntValues;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.Objects;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class Rs2Tab {
    private static final int TAB_SWITCH_SCRIPT = 915;

    public static InterfaceTab getCurrentTab() {
        final int varcIntValue = Microbot.getClient().getVarcIntValue(VarClientInt.INVENTORY_TAB);
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

    public static boolean isCurrentTab(InterfaceTab tab) {
        return getCurrentTab() == tab;
    }

    public static boolean switchTo(InterfaceTab tab) {
        if (isCurrentTab(tab)) return true;

        if (tab == InterfaceTab.NOTHING_SELECTED && Microbot.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 0)
            return false;

        Microbot.getClientThread().invokeLater(() -> Microbot.getClient().runScript(TAB_SWITCH_SCRIPT, tab.getIndex()));
        return sleepUntil(() -> isCurrentTab(tab));
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToInventoryTab() {
        return switchTo(InterfaceTab.INVENTORY);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToCombatOptionsTab() {
        return switchTo(InterfaceTab.COMBAT);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToSkillsTab() {
        return switchTo(InterfaceTab.SKILLS);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToQuestTab() {
        return switchTo(InterfaceTab.QUESTS);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToEquipmentTab() {
        return switchTo(InterfaceTab.EQUIPMENT);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToPrayerTab() {
        return switchTo(InterfaceTab.PRAYER);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToMagicTab() {
        return switchTo(InterfaceTab.MAGIC);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToGroupingTab() {
        return switchTo(InterfaceTab.CHAT);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToFriendsTab() {
        return switchTo(InterfaceTab.FRIENDS);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToAccountManagementTab() {
        return switchTo(InterfaceTab.ACC_MAN);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToSettingsTab() {
        return switchTo(InterfaceTab.SETTINGS);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToEmotesTab() {
        return switchTo(InterfaceTab.EMOTES);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToMusicTab() {
        return switchTo(InterfaceTab.MUSIC);
    }

    @Deprecated(since = "Use switchTo")
    public static boolean switchToLogout() {
        return switchTo(InterfaceTab.LOGOUT);
    }

    private final static int[] LOGOUT_WIDGET_ID_VARIATIONS = {
            35913778, // Fixed Classic Display
            10551342, // Resizable Classic Display
            10747938  // Resizable Modern Display
    };
    private static Widget getLogoutWidget() {
        return Arrays.stream(LOGOUT_WIDGET_ID_VARIATIONS).mapToObj(Rs2Widget::getWidget).filter(Objects::nonNull)
                .findFirst().orElseGet(() -> {
                    Microbot.showMessage("Unable to find logout button widget!");
                    return null;
                });
    }
}
