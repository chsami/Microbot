package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsPluginPanel;
import org.junit.Before;
import org.junit.Test;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.item;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.setup;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsBoltPouchPanel.BOLT_POUCH_AMOUNT_VARBIT_IDS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsBoltPouchPanel.BOLT_POUCH_BOLT_VARBIT_IDS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_AMOUNT_VARBITS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_RUNE_VARBITS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventorySetupsSpecialContainersTest
{
	private Client client;
	private ItemManager itemManager;
	private MInventorySetupsConfig config;
	private InventorySetupsAmmoHandler handler;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		itemManager = mock(ItemManager.class);
		when(itemManager.getItemComposition(anyInt())).thenAnswer(call -> {
			ItemComposition composition = mock(ItemComposition.class);
			when(composition.getName()).thenReturn("Item " + call.getArgument(0));
			return composition;
		});
		config = mock(MInventorySetupsConfig.class);
		when(config.stackCompareType()).thenReturn(InventorySetupsStackCompareID.Less_Than);
		handler = new InventorySetupsAmmoHandler(mock(MInventorySetupsPlugin.class), client, itemManager,
			mock(InventorySetupsPluginPanel.class), config);
	}

	@Test
	public void boltVarbitValuesMapToTheirBolts()
	{
		assertEquals(ItemID.DRAGON_BOLTS, Bolts.getBolt(58).getItemId());
		assertEquals(ItemID.BARROWS_KARIL_AMMO, Bolts.getBolt(82).getItemId());
		assertNull(Bolts.getBolt(0));
		assertNull(Bolts.getBolt(999));
	}

	@Test
	public void everyBoltVarbitValueIsUnique()
	{
		Set<Integer> ids = new HashSet<>();
		for (Bolts bolt : Bolts.values())
		{
			assertTrue(ids.add(bolt.getId()));
			assertSame(bolt, Bolts.getBolt(bolt.getId()));
		}
	}

	@Test
	public void boltPouchReadsBoltsAndLeavesUnknownSlotsEmpty()
	{
		when(client.getVarbitValue(BOLT_POUCH_BOLT_VARBIT_IDS.get(0))).thenReturn(58);
		when(client.getVarbitValue(BOLT_POUCH_AMOUNT_VARBIT_IDS.get(0))).thenReturn(300);
		when(client.getVarbitValue(BOLT_POUCH_BOLT_VARBIT_IDS.get(2))).thenReturn(999);
		when(client.getVarbitValue(BOLT_POUCH_AMOUNT_VARBIT_IDS.get(2))).thenReturn(50);

		List<InventorySetupsItem> bolts = handler.getBoltPouchData();

		assertEquals(4, bolts.size());
		assertEquals(ItemID.DRAGON_BOLTS, bolts.get(0).getId());
		assertEquals(300, bolts.get(0).getQuantity());
		assertEquals("Item " + ItemID.DRAGON_BOLTS, bolts.get(0).getName());
		assertTrue(InventorySetupsItem.itemIsDummy(bolts.get(1)));
		assertTrue(InventorySetupsItem.itemIsDummy(bolts.get(2)));
		assertTrue(InventorySetupsItem.itemIsDummy(bolts.get(3)));
	}

	@Test
	public void runePouchReadsRunesThroughTheGameEnum()
	{
		EnumComposition runeEnum = mock(EnumComposition.class);
		when(runeEnum.getIntValue(4)).thenReturn(ItemID.FIRERUNE);
		when(client.getEnum(982)).thenReturn(runeEnum);
		when(client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS.get(1))).thenReturn(4);
		when(client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS.get(1))).thenReturn(500);

		List<InventorySetupsItem> normal = handler.getRunePouchData(InventorySetupsRunePouchType.NORMAL);
		List<InventorySetupsItem> divine = handler.getRunePouchData(InventorySetupsRunePouchType.DIVINE);

		assertEquals(3, normal.size());
		assertEquals(4, divine.size());
		assertTrue(InventorySetupsItem.itemIsDummy(normal.get(0)));
		assertEquals(ItemID.FIRERUNE, normal.get(1).getId());
		assertEquals(500, normal.get(1).getQuantity());
		assertEquals(InventorySetupsStackCompareID.None, normal.get(1).getStackCompare());
	}

	@Test
	public void quiverIsEmptyWithoutAmmo()
	{
		when(client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)).thenReturn(-1);
		assertTrue(InventorySetupsItem.itemIsDummy(handler.getQuiverData().get(0)));

		when(client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)).thenReturn(ItemID.AMETHYST_ARROW);
		when(client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)).thenReturn(-5);
		assertTrue(InventorySetupsItem.itemIsDummy(handler.getQuiverData().get(0)));
	}

	@Test
	public void quiverReadsItsAmmo()
	{
		when(client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)).thenReturn(ItemID.AMETHYST_ARROW);
		when(client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)).thenReturn(750);

		List<InventorySetupsItem> quiver = handler.getQuiverData();

		assertEquals(1, quiver.size());
		assertEquals(ItemID.AMETHYST_ARROW, quiver.get(0).getId());
		assertEquals(750, quiver.get(0).getQuantity());
	}

	@Test
	public void replacingTheRunePouchDropsItsRunes()
	{
		InventorySetup setup = setup("Barrage", emptyContainer(28), emptyContainer(14));
		setup.updateRunePouch(Arrays.asList(item(ItemID.BLOODRUNE, "Blood rune", 1000, -1)));

		handler.handleSpecialAmmo(setup, item(ItemID.BH_RUNE_POUCH, "Rune pouch", 1, 3), item(ItemID.SHARK, "Shark", 1, 3));

		assertNull(setup.getRune_pouch());
	}

	@Test
	public void addingADivineRunePouchReadsAllFourSlots()
	{
		when(client.getEnum(982)).thenReturn(mock(EnumComposition.class));
		InventorySetup setup = setup("Barrage", emptyContainer(28), emptyContainer(14));

		handler.handleSpecialAmmo(setup, InventorySetupsItem.getDummyItem(), item(ItemID.DIVINE_RUNE_POUCH, "Divine rune pouch", 1, 3));

		assertEquals(4, setup.getRune_pouch().size());
	}

	@Test
	public void replacingTheBoltPouchAndQuiverDropsTheirAmmo()
	{
		InventorySetup setup = setup("Ranged", emptyContainer(28), emptyContainer(14));
		setup.updateBoltPouch(Arrays.asList(item(ItemID.DRAGON_BOLTS, "Dragon bolts", 100, -1)));
		setup.updateQuiver(Arrays.asList(item(ItemID.AMETHYST_ARROW, "Amethyst arrow", 100, -1)));

		handler.handleSpecialAmmo(setup, item(ItemID.XBOWS_BOLT_POUCH, "Bolt pouch", 1, 0), InventorySetupsItem.getDummyItem());
		handler.handleSpecialAmmo(setup, item(ItemID.DIZANAS_QUIVER_CHARGED, "Dizana's quiver", 1, 1), InventorySetupsItem.getDummyItem());

		assertNull(setup.getBoltPouch());
		assertNull(setup.getQuiver());
	}

	@Test
	public void unrelatedItemsLeaveSpecialContainersAlone()
	{
		InventorySetup setup = setup("Melee", emptyContainer(28), emptyContainer(14));
		List<InventorySetupsItem> pouch = Arrays.asList(item(ItemID.BLOODRUNE, "Blood rune", 1000, -1));
		setup.updateRunePouch(pouch);

		handler.handleSpecialAmmo(setup, item(ItemID.SHARK, "Shark", 1, 0), item(ItemID.MANTARAY, "Manta ray", 1, 0));

		assertSame(pouch, setup.getRune_pouch());
	}
}
