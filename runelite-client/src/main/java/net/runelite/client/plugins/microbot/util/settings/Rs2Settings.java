package net.runelite.client.plugins.microbot.util.settings;

import java.awt.Rectangle;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.globval.VarbitIndices.TOGGLE_ROOFS;
import static net.runelite.client.plugins.microbot.util.Global.*;

@Slf4j
@NoArgsConstructor
public class Rs2Settings
{

	static final int SETTINGS_INTERFACE = InterfaceID.Settings.UNIVERSE;
	static final int SETTINGS_CLICKABLE = InterfaceID.Settings.SETTINGS_CLICKZONE;
	static final int SETTINGS_CATEGORIES = InterfaceID.Settings.CATEGORIES_CLICKZONE;
	static final int ALL_SETTINGS_BUTTON = 7602208;

	public static boolean openSettings()
	{
		boolean isSettingsInterfaceVisible = Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE);
		if (!isSettingsInterfaceVisible)
		{
			Rs2Tab.switchTo(InterfaceTab.SETTINGS);
			Rs2Widget.clickWidget(ALL_SETTINGS_BUTTON);
			sleepUntil(() -> Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE));
		}
		return true;
	}

	public static boolean isDropShiftSettingEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.DESKTOP_SHIFTCLICKDROP_ENABLED) == 1;
	}

	public static boolean isEscCloseInterfaceSettingEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.KEYBINDING_ESC_TO_CLOSE) == 1;
	}

	public static boolean isWorldSwitcherConfirmationEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.WORLDSWITCHER_DISABLE_CONFIRMATION) == 0;
	}

	public static boolean enableDropShiftSetting(boolean closeInterface)
	{
		if (isDropShiftSettingEnabled())
		{
			return true;
		}
		if (!openSettings())
		{
			return false;
		}

		if (!switchToSettingsTab("Controls"))
		{
			return false;
		}
		sleepGaussian(800, 100);
		Widget widget = Rs2Widget.getWidget(SETTINGS_CLICKABLE);
		if (widget == null)
		{
			return false;
		}

		// MenuEntryImpl(getOption=Toggle, getTarget=, getIdentifier=1, getType=CC_OP, getParam0=8, getParam1=8781843, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		NewMenuEntry menuEntry = new NewMenuEntry("Toggle", "", 1, MenuAction.CC_OP, 8, widget.getId(), false);
		Microbot.doInvoke(menuEntry, Rs2UiHelper.getDefaultRectangle());
		boolean success = sleepUntil(Rs2Settings::isDropShiftSettingEnabled);

		if (closeInterface)
		{
			closeSettingsMenu();
			Rs2Tab.switchTo(InterfaceTab.INVENTORY);
		}
		return success;
	}

	public static boolean enableDropShiftSetting()
	{
		return enableDropShiftSetting(true);
	}

	public static boolean isHideRoofsEnabled()
	{
		return Microbot.getVarbitValue(TOGGLE_ROOFS) == 1;
	}

	public static boolean hideRoofs(boolean closeInterface)
	{
		if (isHideRoofsEnabled())
		{
			return true;
		}
		if (!openSettings())
		{
			return false;
		}

		if (!switchToSettingsTab("Display"))
		{
			return false;
		}
		sleepGaussian(800, 100);
		Widget widget = Rs2Widget.getWidget(SETTINGS_CLICKABLE);
		if (widget == null)
		{
			return false;
		}

		// MenuEntryImpl(getOption=Toggle, getTarget=, getIdentifier=1, getType=CC_OP, getParam0=4, getParam1=8781843, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		NewMenuEntry menuEntry = new NewMenuEntry("Toggle", "", 1, MenuAction.CC_OP, 4, widget.getId(), false);
		Microbot.doInvoke(menuEntry, Rs2UiHelper.getDefaultRectangle());
		boolean success = sleepUntil(Rs2Settings::isHideRoofsEnabled);

		if (closeInterface)
		{
			closeSettingsMenu();
			Rs2Tab.switchTo(InterfaceTab.INVENTORY);
		}
		return success;
	}

	public static boolean hideRoofs()
	{
		return hideRoofs(true);
	}

	public static boolean isLevelUpNotificationsEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.OPTION_LEVEL_UP_MESSAGE) == 0;
	}

	public static boolean disableLevelUpNotifications(boolean closeInterface)
	{
		if (!isLevelUpNotificationsEnabled())
		{
			return true;
		}
		if (!openSettings())
		{
			return false;
		}
		if (!switchToSettingsTab("Interfaces"))
		{
			return false;
		}
		sleepGaussian(800, 100);
		Widget widget = Rs2Widget.getWidget(SETTINGS_CLICKABLE);
		if (widget == null)
		{
			return false;
		}

		// MenuEntryImpl(getOption=Toggle, getTarget=, getIdentifier=1, getType=CC_OP, getParam0=14, getParam1=8781843, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		NewMenuEntry menuEntry = new NewMenuEntry("Toggle", "", 1, MenuAction.CC_OP, 14, widget.getId(), false);
		Microbot.doInvoke(menuEntry, Rs2UiHelper.getDefaultRectangle());
		boolean success = sleepUntil(() -> !isLevelUpNotificationsEnabled());

		if (closeInterface)
		{
			closeSettingsMenu();
			Rs2Tab.switchTo(InterfaceTab.INVENTORY);
		}
		return success;
	}

	public static boolean disableLevelUpNotifications()
	{
		return disableLevelUpNotifications(true);
	}

	public static void turnOffMusic()
	{
		Rs2Tab.switchTo(InterfaceTab.SETTINGS);
		Rs2Widget.clickWidget(116, 67);
		sleepGaussian(800, 100);
		var musicBtn = Rs2Widget.getWidget(ComponentID.SETTINGS_SIDE_MUSIC_SLIDER).getStaticChildren()[0];
		var soundEffectBtn = Rs2Widget.getWidget(ComponentID.SETTINGS_SIDE_SOUND_EFFECT_SLIDER).getStaticChildren()[0];
		var areaSoundBtn = Rs2Widget.getWidget(ComponentID.SETTINGS_SIDE_AREA_SOUND_SLIDER).getStaticChildren()[0];
		if (musicBtn == null || soundEffectBtn == null || areaSoundBtn == null)
		{
			log.info("Music settings buttons not found");
			return;
		}

		if (musicBtn.getActions() == null || soundEffectBtn.getActions() == null || areaSoundBtn.getActions() == null)
		{
			log.info("Music settings buttons actions not found");
			return;
		}

		boolean isMusicOn = musicBtn.getActions()[0].toLowerCase().equalsIgnoreCase("mute");
		boolean isSoundEffectOn = soundEffectBtn.getActions()[0].equalsIgnoreCase("mute");
		boolean isAreaSoundEffectOn = areaSoundBtn.getActions()[0].equalsIgnoreCase("mute");
		if (!isMusicOn && !isSoundEffectOn && !isAreaSoundEffectOn)
		{
			return;
		}

		if (isMusicOn)
		{
			Rs2Widget.clickWidget(musicBtn);
			sleepGaussian(600, 150);
		}
		if (isSoundEffectOn)
		{
			Rs2Widget.clickWidget(soundEffectBtn);
			sleepGaussian(600, 150);
		}
		if (isAreaSoundEffectOn)
		{
			Rs2Widget.clickWidget(areaSoundBtn);
			sleepGaussian(600, 150);
		}
	}

	public static boolean disableWorldSwitcherConfirmation(boolean closeInterface) {
		if (!isWorldSwitcherConfirmationEnabled()) return true;

		if (!openSettings()) return false;

		if (!switchToSettingsTab("Warnings")) return false;

		sleepGaussian(800, 100);
		Widget widget = Rs2Widget.getWidget(SETTINGS_CLICKABLE);
		if (widget == null) return false;

		// MenuEntryImpl(getOption=Toggle, getTarget=, getIdentifier=1, getType=CC_OP, getParam0=33, getParam1=8781843, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		NewMenuEntry menuEntry = new NewMenuEntry("Toggle", "", 1, MenuAction.CC_OP, 33, widget.getId(), false);
		Microbot.doInvoke(menuEntry, Rs2UiHelper.getDefaultRectangle());
		boolean success = sleepUntil(() -> !isWorldSwitcherConfirmationEnabled());

		if (closeInterface)
		{
			closeSettingsMenu();
			Rs2Tab.switchTo(InterfaceTab.INVENTORY);
		}
		return success;
	}

	public static boolean disableWorldSwitcherConfirmation()
	{
		return disableWorldSwitcherConfirmation(true);
	}

	/**
	 * When casting alchemy spells on items in your inventory
	 * if the item is worth more than this value, a warning will be shown
	 *
	 * @return
	 */
	public static int getMinimumItemValueAlchemyWarning()
	{
		return Microbot.getVarbitValue(6091);
	}

	/**
	 * Checks if spell filtering is enabled in the magic spellbook.
	 *
	 * @return {@code true} if spell filtering is enabled, {@code false} otherwise
	 */
	public static boolean isSpellFilteringEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.MAGIC_SPELLBOOK_HIDEFILTERBUTTON) == 0;
	}

	/**
	 * Enables spell filtering if it's currently disabled.
	 */
	public static void enableSpellFiltering()
	{
		if(isSpellFilteringEnabled()) return;
		Widget spellbookInterfaceWidget = Rs2Tab.getSpellBookTab();
		if (spellbookInterfaceWidget == null)
		{
			log.info("Spellbook interface widget not found, cannot toggle spell filter.");
			return;
		}
		else
		{
			log.info("Spellbook widget found, enabling spell filters.");
		}

		Rectangle spellbookBounds = spellbookInterfaceWidget.getBounds();
		NewMenuEntry spellFilterEntry = new NewMenuEntry("Enable spell filtering", "", 2, MenuAction.CC_OP, -1, spellbookInterfaceWidget.getId(), false);
		Microbot.doInvoke(spellFilterEntry, spellbookBounds != null && Rs2UiHelper.isRectangleWithinCanvas(spellbookBounds) ? spellbookBounds : Rs2UiHelper.getDefaultRectangle());
		sleepUntil(Rs2Settings::isSpellFilteringEnabled, 2000);
	}

	private static boolean closeSettingsMenu()
	{
		if (isEscCloseInterfaceSettingEnabled())
		{
			Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
		}
		else
		{
			Rs2Widget.clickWidget(InterfaceID.Settings.CLOSE);
		}

		return sleepUntil(() -> !Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE));
	}

	private static boolean switchToSettingsTab(String tabName)
	{
		Widget widget = Rs2Widget.getWidget(SETTINGS_CATEGORIES);
		if (widget == null)
		{
			return false;
		}

		Map<String, Integer> tabIndices = Map.of(
			"Activities", 0,
			"Audio", 1,
			"Chat", 2,
			"Controls", 3,
			"Display", 4,
			"Gameplay", 5,
			"Interfaces", 6,
			"Warnings", 7
		);

		Integer index = tabIndices.get(tabName);
		if (index == null)
		{
			return false;
		}

		NewMenuEntry menuEntry = new NewMenuEntry("Select <col=ff981f> " + tabName, "", 1, MenuAction.CC_OP, index, widget.getId(), false);

		Microbot.doInvoke(menuEntry, Rs2UiHelper.getDefaultRectangle());
		return true;
	}
}
