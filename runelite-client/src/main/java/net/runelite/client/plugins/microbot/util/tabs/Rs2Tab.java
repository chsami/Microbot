package net.runelite.client.plugins.microbot.util.tabs;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class Rs2Tab {
    private static final int TAB_SWITCH_SCRIPT = 915;
    private static final long LIVE_TAB_READ_TIMEOUT_MS = 600;
    private static volatile InterfaceTab cachedTab = InterfaceTab.NOTHING_SELECTED;

    // Derived from InterfaceTab.varcIntIndex — single source of truth.
    public static final Map<Integer, InterfaceTab> INDEX_TO_TAB;
    static {
        Map<Integer, InterfaceTab> map = new HashMap<>();
        for (InterfaceTab tab : InterfaceTab.values()) {
            if (tab.getVarcIntIndex() < 0) continue;
            InterfaceTab existing = map.put(tab.getVarcIntIndex(), tab);
            if (existing != null) {
                throw new ExceptionInInitializerError(
                        "Duplicate varcIntIndex " + tab.getVarcIntIndex()
                                + " for " + existing + " and " + tab);
            }
        }
        INDEX_TO_TAB = Collections.unmodifiableMap(map);
    }

    public static void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() != VarClientID.TOPLEVEL_PANEL) return;
        int value = Microbot.getClient().getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
        cachedTab = INDEX_TO_TAB.getOrDefault(value, InterfaceTab.NOTHING_SELECTED);
    }

    public static InterfaceTab getCurrentTab() {
        return cachedTab;
    }

    public static boolean isCurrentTab(InterfaceTab tab) {
        return getCurrentTab() == tab;
    }

    private static InterfaceTab readCurrentTab() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            if (client.getGameState() != GameState.LOGGED_IN) return cachedTab;
            int value = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
            InterfaceTab current = INDEX_TO_TAB.getOrDefault(value, InterfaceTab.NOTHING_SELECTED);
            cachedTab = current;
            return current;
        }, LIVE_TAB_READ_TIMEOUT_MS).orElse(cachedTab);
    }

    public static boolean switchTo(InterfaceTab tab) {
        if (!Microbot.isLoggedIn()) return false;
        if (readCurrentTab() == tab) return true;

        if (tab == InterfaceTab.NOTHING_SELECTED && Microbot.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 0)
            return false;

        int varcIntIndex = tab.getVarcIntIndex();
        if (varcIntIndex != -1) {
            boolean scriptRan = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Client client = Microbot.getClient();
                if (client.getGameState() != GameState.LOGGED_IN || client.getTopLevelInterfaceId() == -1) {
                    return false;
                }
                client.runScript(TAB_SWITCH_SCRIPT, varcIntIndex);
                return true;
            }).orElse(false);
            if (!scriptRan) return false;
        } else {
            int hotkey = tab.getHotkey();
            if (hotkey == -1) {
                log.warn("Tab {} does not have a hotkey assigned, cannot switch to it.", tab.getName());
                return false;
            }
            Rs2Keyboard.keyPress(hotkey);
        }

        return sleepUntil(() -> readCurrentTab() == tab);
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

	public static Widget getSpellBookTab()
	{
		if (Microbot.getClient().isResized())
		{
			if (Microbot.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1)
			{
				return Rs2Widget.getWidget(InterfaceID.ToplevelPreEoc.STONE6);
			}
			return Rs2Widget.getWidget(InterfaceID.ToplevelOsrsStretch.STONE6);
		}
		return Rs2Widget.getWidget(InterfaceID.Toplevel.STONE6);
	}
}
