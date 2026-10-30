package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Dragon Slayer I: wearing the anti-dragon shield pushes the rune kiteshield into the inventory; it goes back to the bank. */
public class QuestEquipSwapTest
{
	private static final int LOBSTER = 379;
	private static final int ANTI_DRAGON_SHIELD = 1540;
	private static final int RUNE_KITESHIELD = 1201;

	@Test
	public void theDisplacedShieldIsTheOnlyNewItem()
	{
		List<Integer> before = List.of(LOBSTER, LOBSTER, ANTI_DRAGON_SHIELD);
		List<Integer> after = List.of(LOBSTER, LOBSTER, RUNE_KITESHIELD);
		Set<Integer> displaced = QuestEquipSwap.displaced(before, after);
		assertEquals(Set.of(RUNE_KITESHIELD), displaced);
	}

	@Test
	public void nothingIsDisplacedWhenTheSlotWasEmpty()
	{
		assertTrue(QuestEquipSwap.displaced(List.of(LOBSTER, ANTI_DRAGON_SHIELD), List.of(LOBSTER)).isEmpty());
	}

	@Test
	public void anItemAlreadyCarriedIsNotDisplaced()
	{
		assertTrue(QuestEquipSwap.displaced(List.of(RUNE_KITESHIELD, ANTI_DRAGON_SHIELD), List.of(RUNE_KITESHIELD)).isEmpty());
	}

	@Test
	public void teleportJewelleryIsNeverSentBack()
	{
		assertTrue(QuestEquipSwap.isTeleportJewellery("Ring of wealth (5)"));
		assertTrue(QuestEquipSwap.isTeleportJewellery("Amulet of glory(4)"));
		assertTrue(QuestEquipSwap.isTeleportJewellery("Skills necklace(6)"));
		assertTrue(!QuestEquipSwap.isTeleportJewellery("Rune kiteshield"));
		assertTrue(!QuestEquipSwap.isTeleportJewellery(null));
	}
}
