package net.runelite.client.plugins.microbot.util.settings;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Optional;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;
import static net.runelite.client.plugins.microbot.util.widget.Rs2Widget.*;

/**
 * Rs2SpellBookSettings allows to enable/disable spell book filters
 */
public class Rs2SpellBookSettings {
    /**
     * toggle spell filter
     * @param text
     * @param toggle
     * @param varbitId
     * @param closeInterface
     * @return
     */
    public static boolean toggleSpellFilter(String text, boolean toggle, int varbitId, boolean closeInterface) {
        int expectedValue = toggle ? 0 : 1;
        if (Microbot.getVarbitValue(varbitId) == expectedValue) {
            return true;
        }

        Rs2Tab.switchToMagicTab();

        if (!sleepUntilTrue(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC, 300, 2000)) {
            return false;
        }

        Microbot.log((toggle ? "Turning on" : "Turning off") + " following spell filter: " + text);
        if (!hasWidgetText(text, 218, 0, true)) {
            Rs2Widget.clickWidget("Filters", Optional.of(218), 0, true);

            boolean result = sleepUntilHasWidgetText(text, 218, 0, true, 2000);
            if (!result) {
                Microbot.log("did not find widget with text : " + text);
                return false;
            }
        }

        Rs2Widget.clickWidget(text, Optional.of(218), 0, true);
        sleepUntil(() -> Microbot.getVarbitValue(varbitId) == expectedValue, 2000);

        if (closeInterface) {
            closeSpellBookFilter();
        }

        return Microbot.getVarbitValue(varbitId) == expectedValue;
    }

    /**
     * close spell book filter
     */
    private static void closeSpellBookFilter() {
        if ( hasWidgetText("Spell Filters", 218, 0, true)) {
            Rs2Widget.clickWidget("Filters", Optional.of(218), 0, true);
            sleepUntilHasNotWidgetText("Spell Filters", 218, 0, true, 2000);
        }
    }

    /**
     * toggle spell filter
     * @param text
     * @param toggle
     * @param varbitId
     * @return
     */
    public static boolean toggleSpellFilter(String text, boolean toggle, int varbitId) {
        return toggleSpellFilter(text, toggle, varbitId, true);
    }

    /**
     * toggleCombatSpells
     * @param toggle
     * @param closeInterface
     * @return
     */
    public static boolean toggleCombatSpells(boolean toggle, boolean closeInterface) {
        return toggleSpellFilter("Show Combat spells", toggle, 6605, closeInterface);
    }

    /**
     * toggleTeleportSpells
     * @param toggle
     * @param closeInterface
     * @return
     */
    public static boolean toggleTeleportSpells(boolean toggle, boolean closeInterface) {
        return toggleSpellFilter("Show Teleport spells", toggle, 6609, closeInterface);
    }

    /**
     * @param toggle
     * @param closeInterface
     * @return
     */
    public static boolean toggleUtilitySpells(boolean toggle, boolean closeInterface) {
        return toggleSpellFilter("Show Utility spells", toggle, 6606, closeInterface);
    }

    /**
     * @param toggle
     * @param closeInterface
     * @return
     */
    public static boolean toggleLackMagicLevel(boolean toggle, boolean closeInterface) {
        return toggleSpellFilter("Show spells you lack the Magic level to cast", toggle, 6607, closeInterface);
    }

    /**
     * @param toggle
     * @param closeInterface
     * @return
     */
    public static boolean toggleLackRunes(boolean toggle, boolean closeInterface) {
        return toggleSpellFilter("Show spells you lack the runes to cast", toggle, 6608, closeInterface);
    }

    /**
     * @param toggle
     * @param closeInterface
     * @return
     */
    public static boolean toggleLackRequirements(boolean toggle, boolean closeInterface) {
        return toggleSpellFilter("Show spells you lack the requirements to cast", toggle, 12137, closeInterface);
    }

    /**
     * @param toggle
     * @param closeInterface
     * @return
     */
    public static boolean toggleIconResizing(boolean toggle, boolean closeInterface) {
        return toggleSpellFilter("Enable icon resizing (outside PvP areas)", toggle, 6548, closeInterface);
    }

    /**
     * @param toggle
     * @return
     */
    public static boolean toggleCombatSpells(boolean toggle) {
        return toggleSpellFilter("Show Combat spells", toggle, 6605);
    }

    /**
     * @param toggle
     * @return
     */
    public static boolean toggleTeleportSpells(boolean toggle) {
        return toggleSpellFilter("Show Teleport spells", toggle, 6609);
    }

    /**
     * @param toggle
     * @return
     */
    public static boolean toggleUtilitySpells(boolean toggle) {
        return toggleSpellFilter("Show Utility spells", toggle, 6606);
    }

    /**
     * @param toggle
     * @return
     */
    public static boolean toggleLackMagicLevel(boolean toggle) {
        return toggleSpellFilter("Show spells you lack the Magic level to cast", toggle, 6607);
    }

    /**
     * @param toggle
     * @return
     */
    public static boolean toggleLackRunes(boolean toggle) {
        return toggleSpellFilter("Show spells you lack the runes to cast", toggle, 6608);
    }

    /**
     * @param toggle
     * @return
     */
    public static boolean toggleLackRequirements(boolean toggle) {
        return toggleSpellFilter("Show spells you lack the requirements to cast", toggle, 12137, true);
    }

    /**
     * @param toggle
     * @return
     */
    public static boolean toggleIconResizing(boolean toggle) {
        return toggleSpellFilter("Enable icon resizing (outside PvP areas)", toggle, 6548, true);
    }

    /**
     * @return
     */
    public static boolean setAllFiltersOn() {
            boolean success = toggleCombatSpells(true, false) &&
                    toggleTeleportSpells(true, false) &&
                    toggleUtilitySpells(true, false) &&
                    toggleLackMagicLevel(true, false) &&
                    toggleLackRunes(true, false) &&
                    toggleLackRequirements(true, false) &&
                    toggleIconResizing(true, false);

            if (success) {
                closeSpellBookFilter();
            }
            return success;
    }

	public static boolean configureSpellbookSettings() {
		boolean success = toggleCombatSpells(true, false) &&
			toggleTeleportSpells(true, false) &&
			toggleUtilitySpells(true, false) &&
			toggleLackMagicLevel(true, false) &&
			toggleLackRunes(true, false) &&
			toggleLackRequirements(true, false) &&
			toggleIconResizing(false, false);

		if (success) {
			closeSpellBookFilter();
		}
		return success;
	}

    /**
     * @return
     */
    public static boolean setAllFiltersOff() {
        boolean success = toggleCombatSpells(false, false) &&
                toggleTeleportSpells(false, false) &&
                toggleUtilitySpells(false, false) &&
                toggleLackMagicLevel(false, false) &&
                toggleLackRunes(false, false) &&
                toggleLackRequirements(false, false) &&
                toggleIconResizing(false, false);

        if (success) {
            closeSpellBookFilter();
        }
        return success;
    }
}
