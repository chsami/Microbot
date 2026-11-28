package net.runelite.client.plugins.microbot.util.bank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class Rs2BankWithdrawAndEquipTest
{
	private static final int WEAR = 9;

	private final List<Rs2ItemModel> inventory = new ArrayList<>();
	private final List<Rs2ItemModel> worn = new ArrayList<>();
	private MockedStatic<Microbot> microbot;
	private MockedStatic<Global> global;
	private MockedStatic<Rs2Inventory> rs2Inventory;
	private MockedStatic<Rs2Equipment> rs2Equipment;
	private MockedStatic<Rs2Bank> rs2Bank;

	@Before
	public void setUp()
	{
		Client client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		ItemComposition wornHood = mock(ItemComposition.class);
		when(wornHood.getLinkedNoteId()).thenReturn(-1);
		when(wornHood.getName()).thenReturn("Graceful hood");
		when(client.getItemDefinition(ItemID.GRACEFUL_HOOD_WORN)).thenReturn(wornHood);
		microbot = mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);
		global = mockStatic(Global.class);
		global.when(() -> Global.sleepUntil(any(BooleanSupplier.class))).thenAnswer(call -> call.<BooleanSupplier>getArgument(0).getAsBoolean());
		rs2Inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
		rs2Inventory.when(Rs2Inventory::items).thenAnswer(call -> new ArrayList<>(inventory).stream());
		rs2Inventory.when(Rs2Inventory::isFull).thenReturn(false);
		rs2Equipment = mockStatic(Rs2Equipment.class, CALLS_REAL_METHODS);
		rs2Equipment.when(Rs2Equipment::items).thenAnswer(call -> new ArrayList<>(worn));
		rs2Bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS);
		rs2Bank.when(Rs2Bank::isOpen).thenReturn(true);
		Rs2ItemModel bankRow = item(ItemID.GRACEFUL_HOOD, "Graceful hood");
		rs2Bank.when(Rs2Bank::getAll).thenAnswer(call -> Collections.singletonList(bankRow).stream());
		rs2Bank.when(() -> Rs2Bank.invokeMenu(anyInt(), any())).thenAnswer(call -> {
			Rs2ItemModel target = call.getArgument(1);
			if ((int) call.getArgument(0) == WEAR)
			{
				inventory.remove(target);
				return worn.add(item(ItemID.GRACEFUL_HOOD_WORN, "Graceful hood"));
			}
			return inventory.add(item(target.getId(), target.getName()));
		});
	}

	@After
	public void tearDown()
	{
		rs2Bank.close();
		rs2Equipment.close();
		rs2Inventory.close();
		global.close();
		microbot.close();
	}

	private static Rs2ItemModel item(int id, String name)
	{
		Rs2ItemModel model = mock(Rs2ItemModel.class);
		when(model.getId()).thenReturn(id);
		when(model.getName()).thenReturn(name);
		when(model.getQuantity()).thenReturn(1);
		return model;
	}

	@Test
	public void withdrawAndEquipWearsTheBankRowWithdrawnForAWornId()
	{
		assertTrue(Rs2Bank.withdrawAndEquip(ItemID.GRACEFUL_HOOD_WORN));

		rs2Bank.verify(() -> Rs2Bank.invokeMenu(eq(WEAR), any()));
		assertTrue(inventory.isEmpty());
		assertEquals(1, worn.size());
	}

	@Test
	public void withdrawXAndEquipWearsTheBankRowWithdrawnForAWornId()
	{
		microbot.when(() -> Microbot.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY)).thenReturn(1);

		assertTrue(Rs2Bank.withdrawXAndEquip(ItemID.GRACEFUL_HOOD_WORN, 1));

		rs2Bank.verify(() -> Rs2Bank.invokeMenu(eq(WEAR), any()));
		assertTrue(inventory.isEmpty());
		assertEquals(1, worn.size());
	}
}
