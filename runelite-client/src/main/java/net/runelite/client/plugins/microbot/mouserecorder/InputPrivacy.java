package net.runelite.client.plugins.microbot.mouserecorder;

import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.MenuAction;

final class InputPrivacy
{
	static final String PLAYER = "<player>";

	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Set<Integer> GAME_KEYS = Set.of(
		KeyEvent.VK_ESCAPE, KeyEvent.VK_SPACE, KeyEvent.VK_TAB, KeyEvent.VK_ENTER, KeyEvent.VK_BACK_SPACE,
		KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META,
		KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
		KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END,
		KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
		KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12);
	private static final Set<Integer> PLAYER_ACTIONS = Set.of(
		MenuAction.PLAYER_FIRST_OPTION.getId(), MenuAction.PLAYER_SECOND_OPTION.getId(), MenuAction.PLAYER_THIRD_OPTION.getId(),
		MenuAction.PLAYER_FOURTH_OPTION.getId(), MenuAction.PLAYER_FIFTH_OPTION.getId(), MenuAction.PLAYER_SIXTH_OPTION.getId(),
		MenuAction.PLAYER_SEVENTH_OPTION.getId(), MenuAction.PLAYER_EIGHTH_OPTION.getId(), MenuAction.RUNELITE_PLAYER.getId(),
		MenuAction.WIDGET_TARGET_ON_PLAYER.getId(), MenuAction.ITEM_USE_ON_PLAYER.getId());

	private InputPrivacy()
	{
	}

	static Integer keyCode(int keyCode, boolean dialogueOptionsOpen)
	{
		if (GAME_KEYS.contains(keyCode))
		{
			return keyCode;
		}
		if (dialogueOptionsOpen && keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9)
		{
			return keyCode;
		}
		return null;
	}

	static String target(String target, int actionType)
	{
		if (PLAYER_ACTIONS.contains(actionType))
		{
			return PLAYER;
		}
		if (target == null)
		{
			return "";
		}
		String plain = TAGS.matcher(target).replaceAll("").trim();
		return plain.length() > 64 ? plain.substring(0, 64) : plain;
	}

	static String option(String option)
	{
		if (option == null)
		{
			return "";
		}
		String plain = TAGS.matcher(option).replaceAll("").trim();
		return plain.length() > 32 ? plain.substring(0, 32) : plain;
	}
}
