package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What the quest preflight is about to buy and whether the player can pay for it. Pure data and decisions, plus the
 * small amount of static state the Agent Server reads; the game and Swing parts live in {@link QuestScript} and
 * the quest window.
 */
public final class QuestPurchasePlan
{
	/** Grand Exchange offers are placed this far above the reference price, so the estimate uses the same factor. */
	public static final double OFFER_FACTOR = 1.2;

	/** What to do with a plan. */
	public enum Decision
	{
		/** Affordable and no confirmation wanted: buy. */
		BUY,
		/** Confirmation wanted: show the dialog. */
		ASK,
		/** Cannot pay and no dialog to explain it: stop before walking to the Grand Exchange. */
		INSUFFICIENT
	}

	/** One item to buy. */
	public static final class Line
	{
		public final String name;
		public final int quantity;
		public final int unitPrice;

		public Line(String name, int quantity, int unitPrice)
		{
			this.name = name;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}

		public long total()
		{
			return (long) quantity * unitPrice;
		}
	}

	private final List<Line> lines;
	private final long coinsInventory;
	/** Coins in the bank, or -1 when the bank has not been read. */
	private final long coinsBank;

	public QuestPurchasePlan(List<Line> lines, long coinsInventory, long coinsBank)
	{
		this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
		this.coinsInventory = Math.max(0, coinsInventory);
		this.coinsBank = coinsBank < 0 ? -1 : coinsBank;
	}

	/** The price an offer for an item with the given reference price is placed at. */
	public static int offerPrice(int referencePrice)
	{
		return Math.max(1, (int) Math.ceil(referencePrice * OFFER_FACTOR));
	}

	public List<Line> lines()
	{
		return lines;
	}

	public long total()
	{
		long sum = 0;
		for (Line line : lines)
		{
			sum += line.total();
		}
		return sum;
	}

	public boolean bankChecked()
	{
		return coinsBank >= 0;
	}

	public long coinsInventory()
	{
		return coinsInventory;
	}

	public long coinsBank()
	{
		return coinsBank;
	}

	/** Coins held in the inventory and, when it was read, the bank. */
	public long coins()
	{
		return coinsInventory + Math.max(0, coinsBank);
	}

	public boolean affordable()
	{
		return coins() >= total();
	}

	/** Gp still missing, 0 when affordable. */
	public long shortfallGp()
	{
		return Math.max(0, total() - coins());
	}

	/**
	 * @param confirm whether the player wants to approve purchases first
	 */
	public Decision decide(boolean confirm)
	{
		if (confirm)
		{
			return Decision.ASK;
		}
		return affordable() ? Decision.BUY : Decision.INSUFFICIENT;
	}

	/** "Rope x1, Air rune x6" */
	public String describeItems()
	{
		StringBuilder sb = new StringBuilder();
		for (Line line : lines)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(line.name).append(" x").append(line.quantity);
		}
		return sb.toString();
	}

	/** Stop message used when purchases cannot be paid for. */
	public String insufficientMessage()
	{
		return String.format(Locale.ROOT, "Not enough coins to buy quest items: need ~%,d gp, have %,d gp%s. Items: %s. "
				+ "Add about %,d gp to your bank or inventory, or start without buying.",
			total(), coins(), bankChecked() ? "" : " (bank not checked)", describeItems(), shortfallGp());
	}

	/** Shape reported by {@code GET /quest-helper/status}. */
	public Map<String, Object> toStatus()
	{
		Map<String, Object> out = new LinkedHashMap<>();
		List<Map<String, Object>> items = new ArrayList<>();
		for (Line line : lines)
		{
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("name", line.name);
			item.put("quantity", line.quantity);
			item.put("estimatedEach", line.unitPrice);
			item.put("estimatedTotal", line.total());
			items.add(item);
		}
		out.put("items", items);
		out.put("estimatedTotal", total());
		out.put("coins", coins());
		out.put("bankChecked", bankChecked());
		out.put("affordable", affordable());
		return out;
	}

	// ---- state read by the Agent Server ----

	private static final AtomicBoolean AWAITING = new AtomicBoolean(false);
	private static final AtomicReference<QuestPurchasePlan> CURRENT = new AtomicReference<>();
	private static final AtomicReference<Boolean> CONFIRM_OVERRIDE = new AtomicReference<>();

	/** True while the purchase confirmation dialog is open. */
	public static boolean isAwaitingConfirmation()
	{
		return AWAITING.get();
	}

	static void setAwaiting(boolean awaiting)
	{
		AWAITING.set(awaiting);
	}

	/** The plan of the current quest start, or null. */
	public static QuestPurchasePlan current()
	{
		return CURRENT.get();
	}

	static void setCurrent(QuestPurchasePlan plan)
	{
		CURRENT.set(plan);
	}

	/** Called by the Agent Server before a start: false auto-approves affordable purchases, null uses the config. */
	public static void overrideNextStart(Boolean confirm)
	{
		CONFIRM_OVERRIDE.set(confirm);
	}

	/** Reads and clears the override set by {@link #overrideNextStart(Boolean)}. */
	static Boolean consumeOverride()
	{
		return CONFIRM_OVERRIDE.getAndSet(null);
	}
}
