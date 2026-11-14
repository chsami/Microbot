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
		assertEquals(Integer.valueOf(KeyEvent.VK_ESCAPE), InputPrivacy.keyCode(KeyEvent.VK_ESCAPE, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_F4), InputPrivacy.keyCode(KeyEvent.VK_F4, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_LEFT), InputPrivacy.keyCode(KeyEvent.VK_LEFT, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_SPACE), InputPrivacy.keyCode(KeyEvent.VK_SPACE, false));
	}

	@Test
	public void typedCharactersNeverKeepTheirCode()
	{
		assertNull(InputPrivacy.keyCode(KeyEvent.VK_A, false));
		assertNull(InputPrivacy.keyCode(KeyEvent.VK_A, true));
		assertNull(InputPrivacy.keyCode(KeyEvent.VK_PERIOD, false));
		assertNull(InputPrivacy.keyCode(KeyEvent.VK_0, true));
	}

	@Test
	public void digitsKeepTheirCodeOnlyWhileDialogueOptionsAreOpen()
	{
		assertNull(InputPrivacy.keyCode(KeyEvent.VK_1, false));
		assertEquals(Integer.valueOf(KeyEvent.VK_1), InputPrivacy.keyCode(KeyEvent.VK_1, true));
		assertEquals(Integer.valueOf(KeyEvent.VK_9), InputPrivacy.keyCode(KeyEvent.VK_9, true));
	}

	@Test
	public void playerNamesAreReplaced()
	{
		assertEquals(InputPrivacy.PLAYER, InputPrivacy.target("<col=ffffff>Zezima<col=00ff00>  (level-126)", MenuAction.PLAYER_FIRST_OPTION.getId()));
		assertEquals(InputPrivacy.PLAYER, InputPrivacy.target("Zezima", MenuAction.PLAYER_EIGHTH_OPTION.getId()));
		assertEquals(InputPrivacy.PLAYER, InputPrivacy.target("Zezima", MenuAction.WIDGET_TARGET_ON_PLAYER.getId()));
	}

	@Test
	public void otherTargetsLoseTagsAndAreCapped()
	{
		assertEquals("Tithe patch", InputPrivacy.target("<col=ffff>Tithe patch", MenuAction.GAME_OBJECT_FIRST_OPTION.getId()));
		assertEquals("", InputPrivacy.target(null, MenuAction.GAME_OBJECT_FIRST_OPTION.getId()));
		assertEquals(64, InputPrivacy.target("x".repeat(100), MenuAction.NPC_FIRST_OPTION.getId()).length());
		assertEquals("Water", InputPrivacy.option("<col=ff9040>Water</col>"));
	}
}
