package net.runelite.client.plugins.microbot.inventorysetups;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.fuzzy;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.item;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MInventorySetupsPluginFilterTest
{
	private static final int NOTED_SHARK = ItemID.SHARK + 1;

	private List<InventorySetup> savedSetups;
	private MInventorySetupsPlugin plugin;

	@Before
	public void setUp() throws Exception
	{
		savedSetups = MInventorySetupsPlugin.inventorySetups;
		plugin = new MInventorySetupsPlugin();
		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.canonicalize(anyInt())).thenAnswer(call -> {
			int id = call.getArgument(0);
			return id == NOTED_SHARK ? ItemID.SHARK : id;
		});
		Field field = MInventorySetupsPlugin.class.getDeclaredField("itemManager");
		field.setAccessible(true);
		field.set(plugin, itemManager);

		InventorySetup vorkath = setup("Vorkath", emptyContainer(28), emptyContainer(14));
		vorkath.getInventory().set(0, item(ItemID.SHARK, "Shark", 1, 0));
		vorkath.getEquipment().set(3, item(ItemID.DRAGON_HUNTER_LANCE, "Dragon hunter lance", 1, 3));
		vorkath.updateNotes("Bring antifire and stamina");
		InventorySetup barrage = setup("Barrage tasks", emptyContainer(28), emptyContainer(14));
		barrage.updateRunePouch(Arrays.asList(item(ItemID.BLOOD_RUNE, "Blood rune", 1000, -1)));
		InventorySetup cannon = setup("Cannon", emptyContainer(28), emptyContainer(14));
		cannon.getAdditionalFilteredItems().put(ItemID.STEEL_CANNONBALL, item(ItemID.STEEL_CANNONBALL, "Cannonball", 1, -1));
		MInventorySetupsPlugin.inventorySetups = new ArrayList<>(Arrays.asList(vorkath, barrage, cannon));
	}

	@After
	public void tearDown()
	{
		MInventorySetupsPlugin.inventorySetups = savedSetups;
	}

	private List<String> filter(String text)
	{
		return plugin.filterSetups(text).stream().map(InventorySetup::getName).collect(Collectors.toList());
	}

	@Test
	public void searchMatchesSetupNamesIgnoringCaseAndSpaces()
	{
		assertEquals(Arrays.asList("Vorkath"), filter("  VORK "));
		assertEquals(3, filter("").size());
	}

	@Test
	public void itemSearchFindsSetupsHoldingTheItem()
	{
		assertEquals(Arrays.asList("Vorkath"), filter("item:shark"));
		assertEquals(Arrays.asList("Vorkath"), filter("item: lance"));
		assertEquals(Arrays.asList("Barrage tasks"), filter("item:blood rune"));
		assertEquals(Arrays.asList("Cannon"), filter("item:cannonball"));
	}

	@Test
	public void notesSearchFindsSetupsByTheirNotes()
	{
		assertEquals(Arrays.asList("Vorkath"), filter("notes:antifire"));
		assertTrue(filter("notes:prayer").isEmpty());
	}

	@Test
	public void bareSearchTagFallsBackToNameSearch()
	{
		assertTrue(filter("item:").isEmpty());
	}

	@Test
	public void containerMatchesNotedItemOnlyWhenCanonicalized()
	{
		List<InventorySetupsItem> inventory = Arrays.asList(item(ItemID.SHARK, "Shark", 1, 0));

		assertTrue(plugin.containerContainsItem(NOTED_SHARK, inventory, false, true));
		assertFalse(plugin.containerContainsItem(NOTED_SHARK, inventory, false, false));
		assertFalse(plugin.containerContainsItem(ItemID.SHARK, null, false, true));
	}

	@Test
	public void fuzzySlotAcceptsAnyVariant()
	{
		List<InventorySetupsItem> equipment = Arrays.asList(fuzzy(ItemID.SARADOMIN_CAPE, "Saradomin cape", 1, 1));

		assertTrue(plugin.containerContainsItem(ItemID.ZAMORAK_CAPE, equipment, true, true));
		assertFalse(plugin.containerContainsItem(ItemID.ZAMORAK_CAPE, equipment, false, true));
	}

	@Test
	public void strictSlotRejectsOtherVariants()
	{
		List<InventorySetupsItem> equipment = Arrays.asList(item(ItemID.SARADOMIN_CAPE, "Saradomin cape", 1, 1));

		assertFalse(plugin.containerContainsItem(ItemID.ZAMORAK_CAPE, equipment, true, true));
	}

	@Test
	public void containerMatchesAnyIdFromASet()
	{
		List<InventorySetupsItem> inventory = Arrays.asList(item(ItemID.DIVINE_RUNE_POUCH, "Divine rune pouch", 1, 0));

		assertTrue(plugin.containerContainsItemFromSet(new java.util.HashSet<>(Arrays.asList(ItemID.DIVINE_RUNE_POUCH)), inventory, false, true));
		assertFalse(plugin.containerContainsItemFromSet(new java.util.HashSet<>(Arrays.asList(ItemID.RUNE_POUCH)), inventory, false, true));
		assertTrue(plugin.containerContainsItemFromSet(new java.util.HashSet<>(Arrays.asList(ItemID.RUNE_POUCH)),
			Arrays.asList(fuzzy(ItemID.DIVINE_RUNE_POUCH, "Divine rune pouch", 1, 0)), true, true));
	}

	@Test
	public void replacingEveryInstanceKeepsEachSlotItsOwnItem() throws Exception
	{
		Field ammo = MInventorySetupsPlugin.class.getDeclaredField("ammoHandler");
		ammo.setAccessible(true);
		ammo.set(plugin, mock(InventorySetupsAmmoHandler.class));
		InventorySetup setup = setup("Slayer", emptyContainer(28), emptyContainer(14));
		for (int slot = 3; slot <= 5; slot++)
		{
			setup.getInventory().set(slot, item(ItemID.SHARK, "Shark", 1, slot));
		}
		InventorySetupsItem manta = item(ItemID.MANTA_RAY, "Manta ray", 1, 3);
		java.lang.reflect.Method replaceAll = MInventorySetupsPlugin.class.getDeclaredMethod("updateAllInstancesInContainerSetupWithNewItem",
			InventorySetup.class, List.class, InventorySetupsItem.class, InventorySetupsItem.class, InventorySetupsSlotID.class);
		replaceAll.setAccessible(true);

		assertTrue((boolean) replaceAll.invoke(plugin, setup, setup.getInventory(), item(ItemID.SHARK, "Shark", 1, 3), manta, InventorySetupsSlotID.INVENTORY));

		for (int slot = 3; slot <= 5; slot++)
		{
			assertEquals(ItemID.MANTA_RAY, setup.getInventory().get(slot).getId());
			assertEquals(slot, setup.getInventory().get(slot).getSlot());
		}
		setup.getInventory().get(4).toggleIsFuzzy();
		assertFalse(setup.getInventory().get(5).isFuzzy());
		assertTrue(InventorySetupsItem.itemIsDummy(setup.getInventory().get(6)));
	}
}
