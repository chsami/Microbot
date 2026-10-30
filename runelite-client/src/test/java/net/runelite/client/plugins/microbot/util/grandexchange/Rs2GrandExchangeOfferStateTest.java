package net.runelite.client.plugins.microbot.util.grandexchange;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2GrandExchangeOfferStateTest {
	private Client client;
	private Field clientField;
	private Field threadField;
	private Object previousClient;
	private Object previousThread;

	@Before
	public void setUp() throws Exception {
		client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
			Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
		clientField = Microbot.class.getDeclaredField("client");
		threadField = Microbot.class.getDeclaredField("clientThread");
		clientField.setAccessible(true);
		threadField.setAccessible(true);
		previousClient = clientField.get(null);
		previousThread = threadField.get(null);
		clientField.set(null, client);
		threadField.set(null, clientThread);
	}

	@After
	public void tearDown() throws Exception {
		clientField.set(null, previousClient);
		threadField.set(null, previousThread);
	}

	@Test
	public void offerPriceIsReadFromTheLongNewOfferVarp() {
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenReturn(3_000_000_000L);

		assertEquals(3_000_000_000L, Rs2GrandExchange.getOfferPrice());
		verify(client, never()).getVarbitValue(anyInt());
	}

	@Test
	public void offerPriceFallsBackToTheIntGetterWhenTheVarpIsStoredAsAnInt() {
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP))
			.thenThrow(new IllegalArgumentException("varp 5753 is an int"));
		when(client.getVarpValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenReturn(1_500_000);

		assertEquals(1_500_000L, Rs2GrandExchange.getOfferPrice());
	}

	@Test
	public void offerPriceDoesNotReadTheIntGetterWhenTheVarpIsStoredAsALong() {
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenReturn(1234L);

		assertEquals(1234L, Rs2GrandExchange.getOfferPrice());
		verify(client, never()).getVarpValue(anyInt());
	}

	@Test
	public void offerPriceChangeIsMeasuredAgainstTheIntVarp() {
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP))
			.thenThrow(new IllegalArgumentException("varp 5753 is an int"));
		when(client.getVarpValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenReturn(1234);

		assertFalse(GrandExchangeWidget.hasOfferPriceChanged(1234L));
		assertTrue(GrandExchangeWidget.hasOfferPriceChanged(1175L));
	}

	@Test
	public void offerPriceChangeIsMeasuredAgainstTheVarp() {
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenReturn(1234L);

		assertFalse(GrandExchangeWidget.hasOfferPriceChanged(1234L));
		assertTrue(GrandExchangeWidget.hasOfferPriceChanged(1175L));
	}

	@Test
	public void offerSetupStateFollowsTheSetupPanelNotTheDetailsPanel() {
		Widget setup = mock(Widget.class);
		Widget details = mock(Widget.class);
		when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(setup);
		when(client.getWidget(InterfaceID.GeOffers.DETAILS)).thenReturn(details);
		when(client.getWidget(InterfaceID.GE_OFFERS, 15)).thenReturn(details);
		when(details.isHidden()).thenReturn(true);

		when(setup.isHidden()).thenReturn(false);
		assertFalse(Rs2GrandExchange.isOfferScreenOpen());
		assertTrue(Rs2GrandExchange.isOfferSetupOpen());

		when(setup.isHidden()).thenReturn(true);
		assertFalse(Rs2GrandExchange.isOfferSetupOpen());
	}
}
