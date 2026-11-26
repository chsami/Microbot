package net.runelite.client.plugins.microbot.util.bank;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilNotNull;

/**
 * What {@link Rs2Bank#handleBankPin(String)} needs beyond reading the pad: counting the PINs it sends, so a wrong one
 * is not sent into the lockout, and finding a digit's button. The game sends nothing until the fourth digit, reshuffles
 * the buttons after each one, writes their text 100 ms late, leaves the button under the cursor without text, and shows "Submitting..."
 * until the server answers.
 */
@Slf4j
final class BankPinEntry
{
	private static final String ACCEPTED = "You have correctly entered your PIN.";
	private static final int STEP_TIMEOUT_MS = 2_000;
	private static final int SUBMIT_TIMEOUT_MS = 5_000;
	private static final int CONFIRM_TIMEOUT_MS = 1_500;
	/** The game locks the bank for 10 minutes after four failures. */
	private static final int MAX_SENDS = 3;
	/** The game locks the bank for 15 s after each failure. */
	static int retryGapMs = 20_000;

	/** Per account, whatever PIN was sent: the game counts every failure toward its lockout. Kept across logins, or auto login would walk into it. */
	private static final Map<Long, Sends> UNCONFIRMED = new ConcurrentHashMap<>();
	private static volatile int logins;

	// Guarded by TOLD.
	private static final Set<String> TOLD = new HashSet<>();
	/** Messages {@link #note} already showed: each problem once, until the PIN is accepted or the next login. */
	private static final Set<String> NOTED = ConcurrentHashMap.newKeySet();

	private static final class Sends
	{
		private final int count;
		private volatile long at = System.nanoTime();

		private Sends(int count)
		{
			this.count = count;
		}
	}

	/** One press of the fourth digit, counted before it happens. */
	static final class Send
	{
		private final long account;
		private final Sends before;
		private final Sends sent;

		private Send(long account, Sends before, Sends sent)
		{
			this.account = account;
			this.before = before;
			this.sent = sent;
		}
	}

	private BankPinEntry()
	{
	}

	/** On the client thread, so it must not wait. */
	static void onLoginScreen()
	{
		logins++; // only written on the client thread
		synchronized (TOLD)
		{
			TOLD.clear();
		}
		NOTED.clear();
	}

	/** On the client thread, so it must not wait. */
	static void onGameMessage(String message)
	{
		if (ACCEPTED.equals(message))
		{
			confirmed();
		}
	}

	/** On the client thread. The bank only opens behind a PIN the game has accepted. */
	static void onBankOpened()
	{
		confirmed();
	}

	/** The script loop skips its ticks while a person uses the mouse or keyboard, and comes back here after. */
	static boolean personHasInput()
	{
		return InputArbiter.isHuman();
	}

	/** Logs a message and shows it as the script status, once until the PIN is accepted or the next login: a script retries every tick. */
	static void note(String message)
	{
		if (NOTED.add(message))
		{
			Microbot.log(message);
			Microbot.status = message;
		}
	}

	static boolean onClientThread()
	{
		return Microbot.getClient().isClientThread();
	}

	/** Changes on every visit to the login screen: an entry that sees it change stops, it may be another account. */
	static int login()
	{
		return logins;
	}

	/** @return the account hash, or -1 while it is not known */
	static long account()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getAccountHash()).orElse(-1L);
	}

	/** A correct PIN resets the game's failure count too. */
	private static void confirmed()
	{
		NOTED.clear();
		synchronized (TOLD)
		{
			TOLD.clear();
		}
		UNCONFIRMED.remove(Microbot.getClient().getAccountHash());
	}

	static boolean isValid(String pin)
	{
		return pin != null && pin.matches("[0-9]{4}");
	}

	/** False once this account had {@value #MAX_SENDS} unconfirmed sends, any PIN, and for {@link #retryGapMs} after each one. */
	static boolean maySend(long account)
	{
		Sends earlier = UNCONFIRMED.get(account);
		if (earlier == null)
		{
			return true;
		}
		if (earlier.count >= MAX_SENDS)
		{
			tellOnce("Bank PIN not confirmed " + MAX_SENDS + " times, not sending a PIN again on this account: enter it by hand and check the Bank PIN field in the Profiles tab");
			return false;
		}
		return System.nanoTime() - earlier.at >= TimeUnit.MILLISECONDS.toNanos(retryGapMs);
	}

	static Send sending(long account)
	{
		Sends before = UNCONFIRMED.get(account);
		Sends sent = new Sends(before == null ? 1 : before.count + 1);
		UNCONFIRMED.put(account, sent);
		return new Send(account, before, sent);
	}

	/** The pad still asks for the fourth digit: nothing went to the server. */
	static void notSent(Send send)
	{
		if (send.before == null)
		{
			UNCONFIRMED.remove(send.account, send.sent);
		}
		else
		{
			UNCONFIRMED.replace(send.account, send.sent, send.before);
		}
	}

	/** @return true once the pad has closed and the game confirmed the PIN */
	static boolean submitted(Send send)
	{
		send.sent.at = System.nanoTime();
		try
		{
			// "Submitting..." until the server answers; then the pad closes, or asks for the first digit again.
			sleepUntil(() -> !Rs2Bank.isBankPinWidgetVisible() || Rs2Bank.currentBankPinDigitIndex() >= 0, SUBMIT_TIMEOUT_MS);
			if (Rs2Bank.isBankPinWidgetVisible())
			{
				return false;
			}
			return sleepUntil(() -> UNCONFIRMED.get(send.account) != send.sent, CONFIRM_TIMEOUT_MS);
		}
		finally
		{
			// A wait a person cut short says nothing about the answer; the send stays counted until confirmed.
			if (UNCONFIRMED.get(send.account) == send.sent && !personHasInput())
			{
				tell("Bank PIN sent (" + send.sent.count + " of " + MAX_SENDS + ") but not confirmed: it is wrong, or the answer was lost");
			}
		}
	}

	/** Logs a warning and shows it as the script status, once until the PIN is accepted or the next login. */
	static void tellOnce(String message)
	{
		synchronized (TOLD)
		{
			if (!TOLD.add(message))
			{
				return;
			}
		}
		tell(message);
	}

	private static void tell(String message)
	{
		Microbot.log(message, Level.WARN);
		Microbot.status = message;
	}

	/** Clicks the button showing the digit while the pad asks for digit {@code index}. */
	static boolean click(int index, String digit)
	{
		Rectangle button = sleepUntilNotNull(() -> Rs2Bank.currentBankPinDigitIndex() == index ? buttonShowing(digit) : null, STEP_TIMEOUT_MS);
		if (button == null)
		{
			return false;
		}
		// From this thread, so the click has happened before the pad is read again.
		Microbot.getMouse().click(button);
		return true;
	}

	private static Rectangle bounds(Widget button)
	{
		Widget clickArea = button.getChild(0);
		return clickArea == null ? null : clickArea.getBounds();
	}

	private static Rectangle buttonShowing(String digit)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			// The button under the cursor has no text: when the other nine show every other digit, it is ours.
			Widget unlabelled = null;
			int labels = 0;
			// Buttons A to J are every other component: each is followed by its graphic.
			for (int id = InterfaceID.BankpinKeypad.A; id <= InterfaceID.BankpinKeypad.J; id += 2)
			{
				Widget button = Microbot.getClient().getWidget(id);
				if (button == null)
				{
					return null;
				}
				Widget text = button.getChild(1);
				String shown = text == null ? null : text.getText();
				if (digit.equals(shown))
				{
					return bounds(button);
				}
				if (shown != null && shown.matches("[0-9]"))
				{
					labels |= 1 << (shown.charAt(0) - '0');
				}
				else
				{
					unlabelled = button;
				}
			}
			// Only when the nine labels are exactly the other nine digits: a half-redrawn pad could repeat one.
			return unlabelled != null && labels == (0x3FF & ~(1 << (digit.charAt(0) - '0'))) ? bounds(unlabelled) : null;
		}).orElse(null);
	}
}