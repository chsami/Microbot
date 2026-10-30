package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.plugins.microbot.questhelper.QuestJunkPicker.Item;
import org.junit.Test;

/** The Dig Site: a full inventory of finds from the earlier dig and pan steps while the dig spot needs a free slot. */
public class QuestJunkPickerTest
{
	private static Item junk(int id, String name, long value)
	{
		return new Item(id, name, value, false, false, true, true);
	}

	private static List<String> names(List<Item> items)
	{
		List<String> out = new ArrayList<>();
		for (Item item : items)
		{
			out.add(item.name());
		}
		return out;
	}

	private static List<Item> digSiteInventory()
	{
		List<Item> items = new ArrayList<>();
		items.add(new Item(995, "Coins", 1, true, false, true, true));
		items.add(junk(1, "Spade", 5));
		items.add(junk(2, "Rock pick", 3));
		items.add(junk(3, "Ceramic remains", 0));
		items.add(junk(4, "Black med helm", 80));
		items.add(junk(5, "Pie dish", 3));
		items.add(junk(6, "Old tooth", 1));
		items.add(junk(6, "Old tooth", 1));
		items.add(junk(7, "Needle", 1));
		items.add(junk(8, "Broken armour", 1));
		items.add(junk(8, "Broken armour", 1));
		items.add(junk(9, "Bones", 60));
		return items;
	}

	@Test
	public void theQuestsOwnToolsAreNeverJunk()
	{
		Set<Integer> protectedIds = new HashSet<>(Arrays.asList(1, 2, 995));
		List<String> picked = names(QuestJunkPicker.pickJunk(digSiteInventory(), protectedIds, 0, QuestJunkPicker.TARGET_FREE_SLOTS));
		assertEquals(3, picked.size());
		assertTrue(picked.stream().noneMatch(n -> n.equals("Spade") || n.equals("Rock pick")));
	}

	@Test
	public void cheapestGoFirstAndOnlyAsManyAsNeeded()
	{
		Set<Integer> protectedIds = new HashSet<>(Arrays.asList(1, 2, 995));
		assertEquals(Arrays.asList("Broken armour", "Broken armour", "Needle"),
			names(QuestJunkPicker.pickJunk(digSiteInventory(), protectedIds, 0, QuestJunkPicker.TARGET_FREE_SLOTS)));
		assertEquals(Arrays.asList("Broken armour"),
			names(QuestJunkPicker.pickJunk(digSiteInventory(), protectedIds, 2, QuestJunkPicker.TARGET_FREE_SLOTS)));
		assertTrue(QuestJunkPicker.pickJunk(digSiteInventory(), protectedIds, 3, 3).isEmpty());
	}

	@Test
	public void valuablesStacksNotedAndUntradeablesStay()
	{
		List<Item> items = new ArrayList<>();
		items.add(junk(10, "Rune scimitar", QuestJunkPicker.VALUABLE_VALUE));
		items.add(new Item(11, "Arrow shaft", 1, true, false, true, true));
		items.add(new Item(12, "Logs", 1, false, true, true, true));
		items.add(new Item(13, "Odd letter", 0, false, false, false, true));
		items.add(new Item(14, "Glowing thing", 0, false, false, true, false));
		assertTrue(QuestJunkPicker.pickJunk(items, Collections.emptySet(), 0, 3).isEmpty());
	}

	@Test
	public void aBankTripOnlyOffersWhatNothingClaimsButTheDropRulesKeep()
	{
		List<Item> items = new ArrayList<>();
		items.add(junk(10, "Rune scimitar", 15_000));
		items.add(new Item(13, "Odd letter", 0, false, false, false, true));
		items.add(junk(1, "Spade", 5));
		items.add(new Item(11, "Arrow shaft", 1, true, false, true, true));
		items.add(junk(3, "Ceramic remains", 0));
		List<String> bankable = names(QuestJunkPicker.bankable(items, new HashSet<>(Arrays.asList(1))));
		assertEquals(Arrays.asList("Rune scimitar"), bankable);
	}

	@Test
	public void everythingProtectedLeavesNothingToDrop()
	{
		Set<Integer> all = new HashSet<>();
		for (Item item : digSiteInventory())
		{
			all.add(item.id());
		}
		assertTrue(QuestJunkPicker.pickJunk(digSiteInventory(), all, 0, 3).isEmpty());
		assertTrue(QuestJunkPicker.bankable(digSiteInventory(), all).isEmpty());
	}

	@Test
	public void anUnpricedItemIsNotJunk()
	{
		List<Item> items = new ArrayList<>();
		items.add(junk(20, "Mystery find", 0));
		assertTrue(QuestJunkPicker.pickJunk(items, Collections.emptySet(), 0, 3).isEmpty());
	}

	@Test
	public void gearThePlayerStartedWithIsProtectedByTheCaller()
	{
		List<Item> items = new ArrayList<>();
		items.add(junk(30, "Bronze axe", 12));
		items.add(junk(31, "Pie dish", 3));
		Set<Integer> userBrought = new HashSet<>(Arrays.asList(30));
		assertEquals(Arrays.asList("Pie dish"), names(QuestJunkPicker.pickJunk(items, userBrought, 0, 3)));
	}

	@Test
	public void aKillDropNeedsOneSlotButARefusedPickupFreesSeveral()
	{
		Set<Integer> protectedIds = new HashSet<>(Arrays.asList(1, 2, 995));
		assertEquals(1, QuestJunkPicker.pickJunk(digSiteInventory(), protectedIds, 0, 1).size());
		assertEquals(3, QuestJunkPicker.pickJunk(digSiteInventory(), protectedIds, 0, 3).size());
	}

	@Test
	public void onlyTheGamesRefusalOrAKillDropCountsAsBlocked()
	{
		long now = 100_000;
		assertTrue(QuestJunkPicker.pickupBlocked(now, now - 3_000, false));
		assertTrue(QuestJunkPicker.pickupBlocked(now, 0, true));
		assertTrue(!QuestJunkPicker.pickupBlocked(now, now - 20_000, false));
		assertTrue(!QuestJunkPicker.pickupBlocked(now, 0, false));
		assertTrue(!QuestJunkPicker.pickupRefused(now, 0));
	}
}
