package net.runelite.client.plugins.microbot.mouserecorder;

import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.MenuAction;

final class InputPrivacy
{
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Set<Integer> GAME_KEYS = Set.of(
		KeyEvent.VK_ESCAPE, KeyEvent.VK_SPACE, KeyEvent.VK_TAB, KeyEvent.VK_ENTER, KeyEvent.VK_BACK_SPACE,
		KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META,
		KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
		KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END,
		KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
		KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12);
	private static final Set<Integer> NAMED_TARGETS = Set.of(
		MenuAction.NPC_FIRST_OPTION.getId(), MenuAction.NPC_SECOND_OPTION.getId(), MenuAction.NPC_THIRD_OPTION.getId(),
		MenuAction.NPC_FOURTH_OPTION.getId(), MenuAction.NPC_FIFTH_OPTION.getId(), MenuAction.EXAMINE_NPC.getId(),
		MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), MenuAction.GAME_OBJECT_SECOND_OPTION.getId(), MenuAction.GAME_OBJECT_THIRD_OPTION.getId(),
		MenuAction.GAME_OBJECT_FOURTH_OPTION.getId(), MenuAction.GAME_OBJECT_FIFTH_OPTION.getId(), MenuAction.EXAMINE_OBJECT.getId(),
		MenuAction.GROUND_ITEM_FIRST_OPTION.getId(), MenuAction.GROUND_ITEM_SECOND_OPTION.getId(), MenuAction.GROUND_ITEM_THIRD_OPTION.getId(),
		MenuAction.GROUND_ITEM_FOURTH_OPTION.getId(), MenuAction.GROUND_ITEM_FIFTH_OPTION.getId(), MenuAction.EXAMINE_ITEM_GROUND.getId(),
		MenuAction.WIDGET_TARGET_ON_NPC.getId(), MenuAction.WIDGET_TARGET_ON_GAME_OBJECT.getId(), MenuAction.WIDGET_TARGET_ON_GROUND_ITEM.getId(),
		MenuAction.ITEM_USE_ON_NPC.getId(), MenuAction.ITEM_USE_ON_GAME_OBJECT.getId(), MenuAction.ITEM_USE_ON_GROUND_ITEM.getId());
	private static final Set<Integer> ITEM_TARGETS = Set.of(
		MenuAction.CC_OP.getId(), MenuAction.CC_OP_LOW_PRIORITY.getId(), MenuAction.WIDGET_TARGET_ON_WIDGET.getId(),
		MenuAction.ITEM_USE_ON_ITEM.getId(), MenuAction.EXAMINE_ITEM.getId());

	private InputPrivacy()
	{
	}

	static Integer gameKey(int keyCode, boolean dialogueOptionsOpen)
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

	static String target(String target, int actionType, int itemId)
	{
		boolean named = NAMED_TARGETS.contains(actionType) || ITEM_TARGETS.contains(actionType) && itemId > 0;
		if (!named || target == null)
		{
			return "";
		}
		return cap(TAGS.matcher(target).replaceAll("").trim(), 64);
	}

	static String option(String option)
	{
		return option == null ? "" : cap(TAGS.matcher(option).replaceAll("").trim(), 32);
	}

	static String plain(String text)
	{
		return text == null ? "" : TAGS.matcher(text).replaceAll("").trim();
	}

	private static String cap(String text, int max)
	{
		return text.length() > max ? text.substring(0, max) : text;
	}
}
