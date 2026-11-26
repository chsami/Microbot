package net.runelite.client.plugins.microbot.util.bank;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.event.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives the real entry against a pad that behaves like the game's scripts 653, 679, 683 and 684: only the fourth digit
 * is sent, the buttons reshuffle after every digit, their text appears 100 ms late and the button under the cursor has none.
 */
public class Rs2BankPinTest
{
	private static final String[] HINTS = {"First click the FIRST digit.", "Now click the SECOND digit.",
		"Time for the THIRD digit.", "Finally, the FOURTH digit."};
	private static final String[] FIELDS = {"client", "clientThread", "mouse", "configManager", "pluginManager"};

	private final Map<String, Object> previous = new HashMap<>();
	private final Pad pad = new Pad();
	private final Client client = mock(Client.class);
	private final ConfigManager config = mock(ConfigManager.class);
	private final PluginManager plugins = mock(PluginManager.class);
	private volatile Thread clientThreadOwner;
	private int previousGap;

	@Before
	public void setUp() throws Exception
	{
		for (String name : FIELDS)
		{
			previous.put(name, field(name).get(null));
		}
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.isClientThread()).thenAnswer(i -> Thread.currentThread() == clientThreadOwner);
		when(client.getWidget(anyInt())).thenAnswer(i -> pad.widget(i.getArgument(0)));
		when(client.getWidget(anyInt(), anyInt())).thenAnswer(i -> pad.widget(((int) i.getArgument(0)) << 16 | (int) i.getArgument(1)));
		ClientThread thread = mock(ClientThread.class);
		when(thread.runOnClientThreadOptional(any())).thenAnswer(i -> Optional.ofNullable(((Callable<?>) i.getArgument(0)).call()));
		Mouse mouse = mock(Mouse.class);
		when(mouse.click(any(Rectangle.class))).thenAnswer(i ->
		{
			pad.click(i.getArgument(0));
			return mouse;
		});
		when(plugins.getPlugins()).thenReturn(Collections.emptyList());
		field("client").set(null, client);
		field("clientThread").set(null, thread);
		field("mouse").set(null, mouse);
		field("configManager").set(null, config);
		field("pluginManager").set(null, plugins);
		previousGap = BankPinEntry.retryGapMs;
		BankPinEntry.retryGapMs = 0;
		Rs2Bank.onGameMessage("You have correctly entered your PIN."); // clears the sends of the test before
		newLogin();
	}

	@After
	public void tearDown() throws Exception
	{
		for (String name : FIELDS)
		{
			field(name).set(null, previous.get(name));
		}
		BankPinEntry.retryGapMs = previousGap;
	}

	@Test(timeout = 30_000)
	public void clicksEveryDigitThoughTextIsLateAndHiddenUnderTheCursor()
	{
		pad.open();
		assertTrue(Rs2Bank.handleBankPin("1123"));
		assertEquals(Collections.singletonList("1123"), pad.submitted);
	}

	@Test(timeout = 30_000)
	public void successMeansThePadHasGone()
	{
		pad.closeDelayMs = 900;
		pad.open();
		assertTrue(Rs2Bank.handleBankPin("1234"));
		assertFalse("pad still up", Rs2Bank.isBankPinWidgetVisible());
	}

	@Test(timeout = 30_000)
	public void aPadThatNeverClosesIsNotASuccess()
	{
		pad.closeDelayMs = Long.MAX_VALUE / 4;
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertEquals(1, pad.submitted.size());
	}

	@Test(timeout = 30_000)
	public void aMissedPressIsPressedAgain()
	{
		pad.dropClickAtStep = 2;
		pad.open();
		assertTrue(Rs2Bank.handleBankPin("1234"));
		assertEquals(Collections.singletonList("1234"), pad.submitted);
	}

	@Test(timeout = 30_000)
	public void entryGoesOnFromTheDigitThePadAsksFor()
	{
		pad.open();
		pad.press(1);
		assertTrue(Rs2Bank.handleBankPin("1234"));
		assertEquals(Collections.singletonList("1234"), pad.submitted);
	}

	@Test(timeout = 60_000)
	public void givingUpAfterRepeatedMissesIsLoggedOnce()
	{
		pad.dropClickAtStep = 0;
		pad.dropsLeft = Integer.MAX_VALUE;
		pad.open();
		try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			assertFalse(Rs2Bank.handleBankPin("1234"));
			microbot.verify(() -> Microbot.log(contains("could not finish entering your bank PIN and will try again")), Mockito.times(1));
		}
		assertEquals(0, pad.step);
	}

	@Test(timeout = 30_000)
	public void nothingIsPressedWhileThePadAsksForNoDigit()
	{
		pad.hintShown = false;
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertEquals(0, pad.step);
	}

	@Test(timeout = 60_000)
	public void anUnconfirmedPinIsTriedThreeTimesThenLeftAlone()
	{
		pad.correctPin = "9999";
		for (int i = 0; i < 6; i++)
		{
			pad.open();
			assertFalse(Rs2Bank.handleBankPin("1234"));
		}
		assertEquals("three sends, one below the lockout", 3, pad.submitted.size());
	}

	@Test(timeout = 60_000)
	public void aFourthDigitSentAsAPersonTakesOverStillCounts()
	{
		pad.correctPin = "9999";
		pad.closeDelayMs = 1_000;
		pad.open();
		try (MockedStatic<InputArbiter> input = Mockito.mockStatic(InputArbiter.class))
		{
			input.when(InputArbiter::isHuman).thenAnswer(i -> pad.step == 4);
			Microbot.status = "";
			assertFalse(Rs2Bank.handleBankPin("1234"));
			assertFalse("a person took over: nothing says the PIN was wrong", Microbot.status.contains("not confirmed"));
		}
		for (int i = 0; i < 4; i++)
		{
			pad.open();
			Rs2Bank.handleBankPin("1234");
		}
		assertEquals("three sends, one below the lockout", 3, pad.submitted.size());
	}

	@Test(timeout = 30_000)
	public void aRetryWaitsForTheLockoutToPass() throws Exception
	{
		BankPinEntry.retryGapMs = 60_000;
		pad.correctPin = "9999";
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertEquals(1, pad.submitted.size());
		BankPinEntry.retryGapMs = 300;
		Thread.sleep(400);
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertEquals("sent again once the gap passed", 2, pad.submitted.size());
	}

	@Test(timeout = 60_000)
	public void aRelogDoesNotGiveARefusedPinMoreTries()
	{
		pad.correctPin = "9999";
		for (int i = 0; i < 6; i++)
		{
			pad.open();
			Rs2Bank.handleBankPin("1234");
			newLogin();
		}
		assertEquals(3, pad.submitted.size());
	}

	@Test(timeout = 60_000)
	public void anotherAccountHasItsOwnTries()
	{
		pad.correctPin = "9999";
		for (int i = 0; i < 4; i++)
		{
			pad.open();
			Rs2Bank.handleBankPin("1234");
		}
		when(client.getAccountHash()).thenReturn(77L);
		newLogin();
		pad.correctPin = "1234";
		pad.open();
		assertTrue(Rs2Bank.handleBankPin("1234"));
		assertEquals(4, pad.submitted.size());
	}

	@Test(timeout = 60_000)
	public void aFourthDigitThePadNeverTookIsNotCountedAsASend()
	{
		Microbot.status = "";
		pad.correctPin = "9999";
		pad.closesWhenRefused = true;
		pad.open();
		pad.dropClickAtStep = 3;
		pad.dropsLeft = Integer.MAX_VALUE;
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertFalse("nothing was sent", String.valueOf(Microbot.status).contains("of 3"));
		assertTrue(pad.submitted.isEmpty());
		pad.dropClickAtStep = -1;
		try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			assertFalse(Rs2Bank.handleBankPin("1234"));
			microbot.verify(() -> Microbot.log(contains("1 of 3"), eq(Level.WARN)), Mockito.times(1));
		}
		assertEquals(1, pad.submitted.size());
	}

	@Test(timeout = 60_000)
	public void anotherWrongPinSharesTheAccountsTries()
	{
		pad.correctPin = "9999";
		for (String pin : new String[]{"1234", "1234", "1234", "5678", "1234", "1234"})
		{
			pad.open();
			Rs2Bank.handleBankPin(pin);
		}
		assertEquals(Arrays.asList("1234", "1234", "1234"), pad.submitted);
	}

	@Test(timeout = 30_000)
	public void aLoginInTheMiddleOfAnEntryEndsIt()
	{
		pad.atStepTwo = Rs2BankPinTest::newLogin;
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertEquals(2, pad.step);
	}

	@Test(timeout = 30_000)
	public void aPadClosedByALogoutIsNotAHandledPin()
	{
		pad.atStepTwo = () ->
		{
			newLogin();
			pad.step = -1;
		};
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
	}

	@Test(timeout = 60_000)
	public void givingUpIsToldAgainAfterThePinWasAccepted()
	{
		pad.correctPin = "9999";
		try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			for (int round = 0; round < 2; round++)
			{
				for (int i = 0; i < 4; i++)
				{
					pad.open();
					Rs2Bank.handleBankPin("1234");
				}
				Rs2Bank.onGameMessage("You have correctly entered your PIN."); // entered by hand
			}
			microbot.verify(() -> Microbot.log(contains("not sending a PIN again"), eq(Level.WARN)), Mockito.times(2));
		}
	}

	@Test(timeout = 30_000)
	public void nothingIsTypedWhileTheAccountIsNotKnown()
	{
		when(client.getAccountHash()).thenReturn(-1L);
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertEquals(0, pad.step);
	}

	@Test(timeout = 30_000)
	public void aPinWhoseAnswerWasLostInADisconnectIsTriedAgain()
	{
		pad.confirms = false;
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		newLogin();
		pad.confirms = true;
		pad.open();
		assertTrue(Rs2Bank.handleBankPin("1234"));
		assertEquals(Arrays.asList("1234", "1234"), pad.submitted);
	}

	@Test(timeout = 60_000)
	public void aLateConfirmationStillCounts()
	{
		pad.closeDelayMs = 7_000;
		pad.open();
		assertFalse("gave up waiting", Rs2Bank.handleBankPin("1234"));
		Rs2Bank.onGameMessage("You have correctly entered your PIN.");
		pad.confirms = false; // only the late confirmation above may reset the count
		pad.closeDelayMs = 300;
		for (int i = 0; i < 4; i++)
		{
			pad.open();
			Rs2Bank.handleBankPin("1234");
		}
		assertEquals("one before the confirmation, three after", 4, pad.submitted.size());
	}

	@Test(timeout = 60_000)
	public void anOpenBankConfirmsThePinWhenTheMessageIsMissed()
	{
		pad.confirms = false;
		for (int i = 0; i < 4; i++)
		{
			pad.open();
			Rs2Bank.handleBankPin("1234");
			BankPinEntry.onBankOpened();
		}
		assertEquals(4, pad.submitted.size());
	}

	@Test(timeout = 30_000)
	public void aCorrectedPinIsSentInTheSameLogin()
	{
		pad.correctPin = "5678";
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		assertTrue(Rs2Bank.handleBankPin("5678"));
		assertEquals(Arrays.asList("1234", "5678"), pad.submitted);
	}

	@Test(timeout = 30_000)
	public void nothingIsTypedWhileAPersonHasTheMouse()
	{
		allowKeyboard("true");
		pad.open();
		try (MockedStatic<InputArbiter> input = Mockito.mockStatic(InputArbiter.class);
			MockedStatic<Rs2Keyboard> keyboard = Mockito.mockStatic(Rs2Keyboard.class))
		{
			keyboard.when(() -> Rs2Keyboard.typeString(anyString())).thenAnswer(i ->
			{
				pad.press(1);
				return null;
			});
			input.when(InputArbiter::isHuman).thenReturn(true);
			assertFalse(Rs2Bank.handleBankPin("1234"));
		}
		assertEquals(0, pad.step);
	}

	@Test(timeout = 30_000)
	public void aProblemIsLoggedOnceUntilThePinIsAcceptedOrTheNextLogin()
	{
		try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			for (int i = 0; i < 5; i++)
			{
				BankPinEntry.note("stuck");
			}
			microbot.verify(() -> Microbot.log("stuck"), Mockito.times(1));
			assertEquals("stuck", Microbot.status);
			Rs2Bank.onGameMessage("You have correctly entered your PIN.");
			BankPinEntry.note("stuck");
			microbot.verify(() -> Microbot.log("stuck"), Mockito.times(2));
			newLogin();
			BankPinEntry.note("stuck");
			microbot.verify(() -> Microbot.log("stuck"), Mockito.times(3));
		}
	}

	@Test(timeout = 30_000)
	public void aPausedEntryCarriesOnOnceThePersonStops()
	{
		pad.open();
		try (MockedStatic<InputArbiter> input = Mockito.mockStatic(InputArbiter.class);
			MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			input.when(InputArbiter::isHuman).thenAnswer(i -> pad.step == 1);
			assertFalse("the person has the mouse after the first digit", Rs2Bank.handleBankPin("1234"));
			assertFalse(Rs2Bank.handleBankPin("1234")); // openBank, then isOpen
			microbot.verify(() -> Microbot.log(contains("paused while you use the mouse or keyboard")), Mockito.times(1));
			microbot.verify(() -> Microbot.log(contains("did not respond")), Mockito.never());
			assertEquals(1, pad.step);
			input.when(InputArbiter::isHuman).thenReturn(false);
			assertTrue("the next tick carries on", Rs2Bank.handleBankPin("1234"));
		}
		assertEquals(Collections.singletonList("1234"), pad.submitted);
	}

	@Test(timeout = 30_000)
	public void aRefusalIsLoggedAtOnceWithTheAttempt()
	{
		pad.correctPin = "9999";
		pad.closesWhenRefused = true;
		pad.open();
		try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			assertFalse("the pad is gone, but the bank will not open", Rs2Bank.handleBankPin("1234"));
			microbot.verify(() -> Microbot.log(contains("1 of 3"), eq(Level.WARN)), Mockito.times(1));
			assertTrue(Microbot.status, Microbot.status.contains("1 of 3"));
		}
	}

	@Test(timeout = 60_000)
	public void givingUpIsLoggedOnce()
	{
		pad.correctPin = "9999";
		try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			for (int i = 0; i < 6; i++)
			{
				pad.open();
				Rs2Bank.handleBankPin("1234");
			}
			microbot.verify(() -> Microbot.log(contains("3 of 3"), eq(Level.WARN)), Mockito.times(1));
			microbot.verify(() -> Microbot.log(contains("not sending a PIN again"), eq(Level.WARN)), Mockito.times(1));
		}
	}

	@Test(timeout = 30_000)
	public void otherMessagesDoNotConfirmThePin()
	{
		pad.confirms = false;
		pad.open();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		Rs2Bank.onGameMessage("You have not correctly entered your PIN.");
		Rs2Bank.onGameMessage(null);
		for (int i = 0; i < 4; i++)
		{
			pad.open();
			Rs2Bank.handleBankPin("1234");
		}
		assertEquals(3, pad.submitted.size());
	}

	@Test(timeout = 30_000)
	public void theUserIsToldOnceThatNoPinIsSaved()
	{
		ConfigProfile profile = mock(ConfigProfile.class);
		when(config.getProfile()).thenReturn(profile);
		pad.open();
		try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Answers.CALLS_REAL_METHODS))
		{
			assertFalse(Rs2Bank.handleBankPin());
			assertFalse(Rs2Bank.handleBankPin());
			microbot.verify(() -> Microbot.log(contains("no bank PIN"), eq(Level.WARN)), Mockito.times(1));
			assertTrue(Microbot.status, Microbot.status.contains("no bank PIN"));
		}
	}

	@Test(timeout = 30_000)
	public void theSavedPinIsUsed() throws Exception
	{
		ConfigProfile profile = mock(ConfigProfile.class);
		when(config.getProfile()).thenReturn(profile);
		when(profile.getBankPin()).thenReturn(Encryption.encrypt("1234"));
		pad.open();
		assertTrue(Rs2Bank.handleBankPin());
		assertEquals(Collections.singletonList("1234"), pad.submitted);
	}

	@Test(timeout = 30_000)
	public void onlyFourAsciiDigitsAreTyped()
	{
		pad.open();
		for (String bad : new String[]{null, "", "123", "12345", "12a4", "12" + (char) 0x0663 + (char) 0x0664})
		{
			assertFalse(String.valueOf(bad), Rs2Bank.handleBankPin(bad));
		}
		assertEquals(0, pad.step);
	}

	@Test(timeout = 30_000)
	public void theKeyboardTypesEachDigitWhenTheBankPluginAllowsIt()
	{
		allowKeyboard("true");
		pad.open();
		try (MockedStatic<Rs2Keyboard> keyboard = Mockito.mockStatic(Rs2Keyboard.class))
		{
			keyboard.when(() -> Rs2Keyboard.typeString(anyString())).thenAnswer(i ->
			{
				pad.press(Integer.parseInt(i.getArgument(0)));
				return null;
			});
			assertTrue(Rs2Bank.handleBankPin("1123"));
		}
		assertEquals(Collections.singletonList("1123"), pad.submitted);
	}

	@Test(timeout = 30_000)
	public void anUnsetKeyboardOptionFallsBackToClicks()
	{
		allowKeyboard(null);
		pad.open();
		assertTrue(Rs2Bank.handleBankPin("1234"));
	}

	@Test(timeout = 30_000)
	public void theClientThreadIsNeverHeldUpByAnEntryInProgress() throws Exception
	{
		pad.clickTakesMs = 1_500;
		pad.open();
		CompletableFuture<Boolean> entry = CompletableFuture.supplyAsync(() -> Rs2Bank.handleBankPin("1234"));
		assertTrue(pad.clicking.await(5, TimeUnit.SECONDS));
		clientThreadOwner = Thread.currentThread();
		long start = System.nanoTime();
		assertFalse(Rs2Bank.handleBankPin("1234"));
		long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		clientThreadOwner = null;
		assertTrue("held the client thread for " + tookMs + " ms", tookMs < 500);
		assertTrue(entry.get(20, TimeUnit.SECONDS));
	}

	@Test(timeout = 30_000)
	public void twoScriptsAtOnceSendThePinOnce() throws Exception
	{
		pad.open();
		CompletableFuture<Boolean> one = CompletableFuture.supplyAsync(() -> Rs2Bank.handleBankPin("1234"));
		CompletableFuture<Boolean> two = CompletableFuture.supplyAsync(() -> Rs2Bank.handleBankPin("1234"));
		assertTrue(one.get(20, TimeUnit.SECONDS));
		assertTrue(two.get(20, TimeUnit.SECONDS));
		assertEquals(Collections.singletonList("1234"), pad.submitted);
	}

	private void allowKeyboard(String option)
	{
		BankPlugin bank = mock(BankPlugin.class);
		when(plugins.getPlugins()).thenReturn(Collections.singletonList(bank));
		when(plugins.isPluginEnabled(bank)).thenReturn(true);
		when(config.getConfiguration("bank", "bankPinKeyboard")).thenReturn(option);
	}

	private static Field field(String name) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static void newLogin()
	{
		Rs2Bank.onLoginScreen();
	}

	/** The game's pad. The next digit asked for always lands under the cursor, the worst case of its shuffle. */
	private static final class Pad
	{
		private static final int FIRST_BUTTON = InterfaceID.BankpinKeypad.A;
		final List<String> submitted = Collections.synchronizedList(new ArrayList<>());
		final CountDownLatch clicking = new CountDownLatch(1);
		String correctPin;
		boolean closesWhenRefused;
		boolean confirms = true;
		long closeDelayMs = 300;
		long clickTakesMs;
		Runnable atStepTwo;
		int dropClickAtStep = -1;
		int dropsLeft = 1;
		boolean hintShown = true;
		volatile int step = -1; // -1 = closed, 0..3 = digit asked for, 4 = waiting for the server
		private String typed = "";
		private long drawnAt;
		private long answersAt;
		private int hovered = -1;
		private int shift;

		synchronized void open()
		{
			step = 0;
			typed = "";
			hovered = -1;
			drawnAt = System.currentTimeMillis();
		}

		synchronized void press(int digit)
		{
			if (step < 0 || step > 3)
			{
				return;
			}
			typed += digit;
			step++;
			if (step == 2 && atStepTwo != null)
			{
				atStepTwo.run();
			}
			drawnAt = System.currentTimeMillis();
			if (step == 4)
			{
				submitted.add(typed);
				answersAt = drawnAt + closeDelayMs;
			}
		}

		void click(Rectangle bounds)
		{
			clicking.countDown();
			if (clickTakesMs > 0)
			{
				try
				{
					Thread.sleep(clickTakesMs);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
			synchronized (this)
			{
				int position = bounds.x / 100;
				if (step == dropClickAtStep)
				{
					if (--dropsLeft <= 0)
					{
						dropClickAtStep = -1;
						dropsLeft = 1;
					}
					return;
				}
				int digit = digitAt(position);
				press(digit);
				hovered = position;
				if (step > 0 && step < 4)
				{
					shift = position - ("1123".startsWith(typed) ? "1123" : "1234").charAt(step) + '0';
				}
			}
		}

		private int digitAt(int position)
		{
			return Math.floorMod(position - shift, 10);
		}

		private void settle()
		{
			if (step == 4 && System.currentTimeMillis() >= answersAt)
			{
				boolean correct = correctPin == null || correctPin.equals(typed);
				if (correct && confirms)
				{
					Rs2Bank.onGameMessage("You have correctly entered your PIN."); // before the pad closes, as seen live
				}
				if (correct || closesWhenRefused)
				{
					step = -1;
				}
				else
				{
					open();
				}
			}
		}

		synchronized Widget widget(int id)
		{
			settle();
			if (step < 0)
			{
				return null;
			}
			if (id == InterfaceID.BankpinKeypad.UNIVERSE)
			{
				Widget root = widget("", false, new Rectangle(0, 0, 1000, 50));
				Widget[] buttons = new Widget[10];
				for (int i = 0; i < 10; i++)
				{
					buttons[i] = button(i);
				}
				when(root.getStaticChildren()).thenReturn(buttons);
				return root;
			}
			if (id == InterfaceID.BankpinKeypad.DIGITHINT)
			{
				return widget(step > 3 ? "Submitting..." : hintShown ? HINTS[step] : "", false, new Rectangle(0, 60, 100, 10));
			}
			int offset = id - FIRST_BUTTON;
			return offset >= 0 && offset < 20 && offset % 2 == 0 ? button(offset / 2) : null;
		}

		private Widget button(int position)
		{
			boolean drawn = System.currentTimeMillis() - drawnAt >= 100;
			Rectangle bounds = new Rectangle(position * 100, 0, 64, 64);
			Widget rect = widget("", false, bounds);
			Widget text = widget(drawn && step <= 3 ? String.valueOf(digitAt(position)) : "", false, bounds);
			// Only child 0 takes the click; the component around it is drawn elsewhere here to tell them apart.
			Widget button = widget("", false, new Rectangle(((position + 1) % 10) * 100, 0, 64, 64));
			// The game leaves the button under the cursor without its text component.
			Widget[] children = position == hovered ? new Widget[]{rect} : new Widget[]{rect, text};
			when(button.getDynamicChildren()).thenReturn(children);
			when(button.getChild(anyInt())).thenAnswer(i -> (int) i.getArgument(0) < children.length ? children[(int) i.getArgument(0)] : null);
			return button;
		}

		private static Widget widget(String text, boolean hidden, Rectangle bounds)
		{
			Widget widget = mock(Widget.class);
			when(widget.getText()).thenReturn(text);
			when(widget.getName()).thenReturn("");
			when(widget.isHidden()).thenReturn(hidden);
			when(widget.getBounds()).thenReturn(bounds);
			return widget;
		}
	}
}