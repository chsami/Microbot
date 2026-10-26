package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QuestRestrictionsTest
{
	private static List<QuestRestrictions.Restriction> of(String... texts)
	{
		return QuestRestrictions.fromTexts(Arrays.asList(texts));
	}

	@Test
	public void emptyInventoryIsFoundAndEnforced()
	{
		List<QuestRestrictions.Restriction> found = of("You need an empty inventory to enter.");
		assertEquals(1, found.size());
		assertEquals(QuestRestrictions.Kind.EMPTY_INVENTORY, found.get(0).kind);
		assertTrue(found.get(0).enforce);
		assertTrue(QuestRestrictions.forbidsCarrying(found));
	}

	@Test
	public void weaponsArmourAndFoodBans()
	{
		assertEquals(QuestRestrictions.Kind.NO_WEAPONS_OR_ARMOUR, of("No weapons or armour allowed").get(0).kind);
		assertEquals(QuestRestrictions.Kind.NO_WEAPONS_OR_ARMOUR, of("You cannot wear any armour here").get(0).kind);
		assertEquals(QuestRestrictions.Kind.NO_FOOD, of("Do not bring any food").get(0).kind);
	}

	@Test
	public void freeSlotsCountsDigitsAndWords()
	{
		assertEquals(10, QuestRestrictions.requiredFreeSlots(of("Have 10 free inventory slots")));
		assertEquals(3, QuestRestrictions.requiredFreeSlots(of("at least three empty spaces")));
		assertEquals(0, QuestRestrictions.requiredFreeSlots(of("Bring a hammer")));
	}

	@Test
	public void namedItemsAreExtractedWithoutHtml()
	{
		List<QuestRestrictions.Restriction> found = of("<col=ff0000>Do not bring a cat</col> into the cave.");
		assertEquals(1, found.size());
		assertEquals(QuestRestrictions.Kind.ITEM, found.get(0).kind);
		assertEquals("cat", found.get(0).item);
	}

	@Test
	public void ordinaryTextAndNullsGiveNothing()
	{
		assertTrue(of("Talk to the Cook in Lumbridge", null, "").isEmpty());
		assertTrue(QuestRestrictions.fromTexts(null).isEmpty());
	}

	@Test
	public void curatedRulesAreWarningsAndDoNotDuplicateTextRules()
	{
		List<QuestRestrictions.Restriction> curated = QuestRestrictions.forQuest("MONKEY_MADNESS_I", Arrays.asList());
		assertEquals(1, curated.size());
		assertFalse(curated.get(0).enforce);
		List<QuestRestrictions.Restriction> both = QuestRestrictions.forQuest("MONKEY_MADNESS_I",
			Arrays.asList("No weapons"));
		assertEquals(1, both.size());
		assertTrue(both.get(0).enforce);
		assertTrue(QuestRestrictions.forQuest("COOKS_ASSISTANT", null).isEmpty());
	}
}
