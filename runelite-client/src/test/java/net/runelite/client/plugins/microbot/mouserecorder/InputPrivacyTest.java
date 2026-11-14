package net.runelite.client.plugins.microbot.mouserecorder;

import java.awt.event.KeyEvent;
import net.runelite.api.MenuAction;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InputPrivacyTest
{
	@Test
	public void gameKeysKeepTheirCode()
	{
		assertEquals(Integer.valueOf(KeyEvent.VK_ESCAPE), InputPrivacy.gameKey(KeyEvent.VK_ESCAPE, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_F4), InputPrivacy.gameKey(KeyEvent.VK_F4, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_LEFT), InputPrivacy.gameKey(KeyEvent.VK_LEFT, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_SPACE), InputPrivacy.gameKey(KeyEvent.VK_SPACE, false));
	}

	@Test
	public void typedCharactersNeverKeepTheirCode()
	{
		assertNull(InputPrivacy.gameKey(KeyEvent.VK_A, false));
		assertNull(InputPrivacy.gameKey(KeyEvent.VK_A, true));
		assertNull(InputPrivacy.gameKey(KeyEvent.VK_PERIOD, false));
		assertNull(InputPrivacy.gameKey(KeyEvent.VK_0, true));
	}

	@Test
	public void digitsKeepTheirCodeOnlyWhileDialogueOptionsAreOpen()
	{
		assertNull(InputPrivacy.gameKey(KeyEvent.VK_1, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_1), InputPrivacy.gameKey(KeyEvent.VK_1, true));
		assertEquals(Integer.valueOf(KeyEvent.VK_9), InputPrivacy.gameKey(KeyEvent.VK_9, true));
	}

	@Test
	public void onlyNpcObjectGroundItemAndItemTargetsKeepTheirName()
	{
		assertEquals("Tithe patch", InputPrivacy.target("<col=ffff>Tithe patch", MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), -1));
		assertEquals("Zulrah", InputPrivacy.target("<col=ffff00>Zulrah<col=ff00>  (level-725)".replace("  (level-725)", ""), MenuAction.NPC_SECOND_OPTION.getId(), -1));
		assertEquals("Logavano fruit", InputPrivacy.target("<col=ff9040>Logavano fruit", MenuAction.CC_OP.getId(), 13423));
		assertEquals("Bones", InputPrivacy.target("Bones", MenuAction.GROUND_ITEM_THIRD_OPTION.getId(), -1));
	}

	@Test
	public void playerAndListNamesAreBlanked()
	{
		assertEquals("", InputPrivacy.target("<col=ffffff>Zezima<col=00ff00>  (level-126)", MenuAction.PLAYER_FIRST_OPTION.getId(), -1));
		assertEquals("", InputPrivacy.target("Zezima", MenuAction.RUNELITE_PLAYER.getId(), -1));
		assertEquals("", InputPrivacy.target("Zezima", MenuAction.CC_OP.getId(), -1));
		assertEquals("", InputPrivacy.target("Zezima", MenuAction.CC_OP_LOW_PRIORITY.getId(), -1));
		assertEquals("", InputPrivacy.target("Zezima", MenuAction.RUNELITE.getId(), -1));
		assertEquals("", InputPrivacy.target("Zezima", MenuAction.WIDGET_TARGET_ON_PLAYER.getId(), -1));
		assertEquals("", InputPrivacy.target("Zezima", MenuAction.WALK.getId(), -1));
		assertEquals("", InputPrivacy.target(null, MenuAction.NPC_FIRST_OPTION.getId(), -1));
	}

	@Test
	public void namesAndOptionsLoseTagsAndAreCapped()
	{
		assertEquals(64, InputPrivacy.target("x".repeat(100), MenuAction.NPC_FIRST_OPTION.getId(), -1).length());
		assertEquals("Water", InputPrivacy.option("<col=ff9040>Water</col>"));
	}
}
