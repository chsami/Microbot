package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import org.junit.Before;
import org.junit.Test;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.fuzzy;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.item;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InventorySetupLayoutUtilitiesTest
{
	private TagManager tagManager;
	private MInventorySetupsConfig config;
	private InventorySetupLayoutUtilities layouts;

	@Before
	public void setUp()
	{
		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		tagManager = mock(TagManager.class);
		config = mock(MInventorySetupsConfig.class);
		when(config.layoutDuplicates()).thenReturn(true);
		when(config.defaultLayout()).thenReturn(InventorySetupLayoutType.PRESET);
		layouts = new InventorySetupLayoutUtilities(itemManager, tagManager, mock(LayoutManager.class), config, mock(Client.class));
	}

	private static InventorySetup vorkathSetup()
	{
		List<InventorySetupsItem> equipment = emptyContainer(14);
		equipment.set(EquipmentInventorySlot.HEAD.getSlotIdx(), item(ItemID.NEITIZNOT_FACEGUARD, "Neitiznot faceguard", 1, 0));
		equipment.set(EquipmentInventorySlot.WEAPON.getSlotIdx(), item(ItemID.DRAGONHUNTER_LANCE, "Dragon hunter lance", 1, 3));
		equipment.set(EquipmentInventorySlot.RING.getSlotIdx(), item(ItemID.RING_OF_RECOIL, "Ring of recoil", 1, 12));
		List<InventorySetupsItem> inventory = emptyContainer(28);
		inventory.set(0, item(ItemID.SHARK, "Shark", 1, 0));
		inventory.set(1, item(ItemID.SHARK, "Shark", 1, 1));
		inventory.set(4, item(ItemID.LAWRUNE, "Law rune", 100, 4));
		inventory.set(27, item(ItemID.ANGLERFISH, "Anglerfish", 1, 27));
		return setup("Vorkath", inventory, equipment);
	}

	@Test
	public void presetPutsEquipmentOnTheLeftLikeTheWornPanel()
	{
		Layout layout = layouts.getPresetLayout(vorkathSetup(), false);

		assertEquals(ItemID.NEITIZNOT_FACEGUARD, layout.getItemAtPos(1));
		assertEquals(ItemID.DRAGONHUNTER_LANCE, layout.getItemAtPos(16));
		assertEquals(ItemID.RING_OF_RECOIL, layout.getItemAtPos(34));
	}

	@Test
	public void presetPutsInventoryInAFourWideGridOnTheRight()
	{
		Layout layout = layouts.getPresetLayout(vorkathSetup(), false);

		assertEquals(ItemID.SHARK, layout.getItemAtPos(4));
		assertEquals(ItemID.SHARK, layout.getItemAtPos(5));
		assertEquals(ItemID.LAWRUNE, layout.getItemAtPos(12));
		assertEquals(ItemID.ANGLERFISH, layout.getItemAtPos(55));
		assertEquals(-1, layout.getItemAtPos(6));
	}

	@Test
	public void presetLeavesRoomForAdditionalItemsBelow()
	{
		Layout layout = layouts.getPresetLayout(vorkathSetup(), false);

		assertTrue(layout.size() >= 56);
	}

	@Test
	public void presetPlacesPouchesQuiverAndAdditionalItems()
	{
		InventorySetup setup = vorkathSetup();
		setup.updateRunePouch(Arrays.asList(item(ItemID.AIRRUNE, "Air rune", 1000, -1), InventorySetupsItem.getDummyItem(), item(ItemID.FIRERUNE, "Fire rune", 500, -1)));
		setup.updateBoltPouch(Arrays.asList(item(ItemID.DRAGON_BOLTS, "Dragon bolts", 200, -1)));
		setup.updateQuiver(Arrays.asList(item(ItemID.AMETHYST_ARROW, "Amethyst arrow", 500, -1)));
		Map<Integer, InventorySetupsItem> additional = new HashMap<>();
		additional.put(ItemID.MANTARAY, item(ItemID.MANTARAY, "Manta ray", 1, -1));
		setup.updateAdditionalItems(additional);

		Layout layout = layouts.getPresetLayout(setup, false);

		assertEquals(ItemID.AMETHYST_ARROW, layout.getItemAtPos(2));
		assertEquals(ItemID.AIRRUNE, layout.getItemAtPos(40));
		assertEquals(-1, layout.getItemAtPos(41));
		assertEquals(ItemID.FIRERUNE, layout.getItemAtPos(42));
		assertEquals(ItemID.DRAGON_BOLTS, layout.getItemAtPos(48));
		assertEquals(ItemID.MANTARAY, layout.getItemAtPos(56));
	}

	@Test
	public void duplicatesAreSkippedWhenDisabled()
	{
		when(config.layoutDuplicates()).thenReturn(false);

		Layout layout = layouts.getPresetLayout(vorkathSetup(), false);

		assertEquals(ItemID.SHARK, layout.getItemAtPos(4));
		assertEquals(-1, layout.getItemAtPos(5));
	}

	@Test
	public void zigZagFillsTwoRowsColumnByColumn()
	{
		Layout layout = layouts.getZigZagLayout(vorkathSetup(), false);

		assertEquals(ItemID.NEITIZNOT_FACEGUARD, layout.getItemAtPos(0));
		assertEquals(ItemID.DRAGONHUNTER_LANCE, layout.getItemAtPos(8));
		assertEquals(ItemID.RING_OF_RECOIL, layout.getItemAtPos(1));
		assertEquals(ItemID.SHARK, layout.getItemAtPos(16));
		assertEquals(ItemID.SHARK, layout.getItemAtPos(24));
		assertEquals(ItemID.LAWRUNE, layout.getItemAtPos(17));
		assertEquals(ItemID.ANGLERFISH, layout.getItemAtPos(25));
	}

	@Test
	public void zigZagNeverPlacesTwoInventoryItemsOnTheSameSpot()
	{
		List<InventorySetupsItem> inventory = emptyContainer(28);
		for (int i = 0; i < 28; i++)
		{
			inventory.set(i, item(1000 + i, "Item " + i, 1, i));
		}
		InventorySetup setup = setup("Full", inventory, emptyContainer(14));

		Layout layout = layouts.getZigZagLayout(setup, false);

		for (int i = 0; i < 28; i++)
		{
			assertEquals(1, layout.count(1000 + i));
		}
	}

	@Test
	public void layoutTagsItemsOnlyWhenAsked()
	{
		InventorySetup setup = vorkathSetup();
		setup.getInventory().set(2, fuzzy(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 2));
		String tag = InventorySetupLayoutUtilities.getTagNameForLayout("Vorkath");

		layouts.getPresetLayout(setup, false);
		verify(tagManager, never()).addTag(anyInt(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());

		layouts.createSetupLayout(setup);
		verify(tagManager, atLeastOnce()).addTag(ItemID.SHARK, tag, false);
		verify(tagManager).addTag(eq(ItemID._4DOSEPRAYERRESTORE), eq(tag), eq(true));
	}

	@Test
	public void tagNameIsStablePerSetupName()
	{
		String tag = InventorySetupLayoutUtilities.getTagNameForLayout("Vorkath");

		assertTrue(tag.startsWith(MInventorySetupsPlugin.LAYOUT_PREFIX_MARKER));
		assertEquals(tag, InventorySetupLayoutUtilities.getTagNameForLayout("Vorkath"));
		assertNotEquals(tag, InventorySetupLayoutUtilities.getTagNameForLayout("vorkath"));
	}

	@Test
	public void hubBankTagLayoutIsConverted()
	{
		Layout layout = layouts.convertHubBankTagLayoutToCoreBankTagLayout(ItemID.SHARK + ":0," + ItemID.LAWRUNE + ":9", "tag");

		assertEquals(ItemID.SHARK, layout.getItemAtPos(0));
		assertEquals(ItemID.LAWRUNE, layout.getItemAtPos(9));
		assertEquals(-1, layout.getItemAtPos(5));
	}

	@Test
	public void brokenHubBankTagLayoutIsRejected()
	{
		assertNull(layouts.convertHubBankTagLayoutToCoreBankTagLayout("not a layout", "tag"));
		assertNull(layouts.convertHubBankTagLayoutToCoreBankTagLayout(ItemID.SHARK + ":", "tag"));
	}
}
