package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static net.runelite.client.plugins.microbot.ddchopper.ScriptTimings.ACTION_TIMEOUT_MS;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Gets the player an axe: finds one already owned (worn, carried or banked), wields it, and buys a better one at the
 * Grand Exchange when the config asks for it. Advances one step per call so the script's tick loop stays in charge.
 */
final class AxeSupplier
{
	enum State
	{
		/** A step was taken (or is still needed); call again next tick. */
		WORKING,
		/** {@link Result#axe} is worn or carried and usable. */
		READY,
		/** Nothing usable owned and nothing could be bought. */
		MISSING
	}

	/** Outcome of one {@link #check}; the axe is only set when the state is READY. */
	static final class Result
	{
		private static final Result WORKING = new Result(State.WORKING, null);
		private static final Result MISSING = new Result(State.MISSING, null);

		final State state;
		final Axe axe;

		private Result(State state, Axe axe)
		{
			this.state = state;
			this.axe = axe;
		}
	}

	private static final int COINS = 995;
	private static final int OFFER_POLL_MS = 600;
	private static final int OFFER_TIMEOUT_MS = 60_000;
	/** Price margin over the GE guide price so the offer fills quickly. */
	private static final double BUY_PRICE_MARGIN = 1.15;

	private final DDChopperConfig config;
	private final Consumer<String> status;
	private final Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
	private boolean bankChecked;
	private boolean depositLeftover;
	private int buyAttemptLevel = -1;
	private final AxePlanner.Attempts attempts = new AxePlanner.Attempts();
	/** Axes given up on buying for this run (coins, bank or Grand Exchange kept failing). */
	private final Set<Axe> abandoned = EnumSet.noneOf(Axe.class);
	/** Owned axes that could not be taken out of the bank; the next best owned axe is used instead. */
	private final Set<Axe> unusable = EnumSet.noneOf(Axe.class);
	/** The bank would not open repeatedly: stop relying on it for the rest of the run. */
	private boolean bankUnavailable;
	private volatile Axe currentAxe;

	AxeSupplier(DDChopperConfig config, Consumer<String> status)
	{
		this.config = config;
		this.status = status;
	}

	/** The axe last reported READY (for the overlay and the chop planner); null until one is ready. */
	Axe currentAxe()
	{
		return currentAxe;
	}

	Result check(int level)
	{
		if (depositLeftover)
		{
			depositLeftoverCoins();
			return Result.WORKING;
		}
		Set<Axe> owned = ownedAxes();
		Axe target = AxePlanner.chooseOwned(config.axe(), owned, level);
		Axe desired = config.axe().axe() != null ? config.axe().axe() : AxePlanner.bestUnlocked(level, false);
		boolean wantBuy = config.buyAxe() && desired != null && desired.isBuyable() && desired.canChop(level)
			&& (target == null || target.ordinal() < desired.ordinal()) && buyAttemptLevel != level
			&& !abandoned.contains(desired);

		if (!bankChecked && (target == null || wantBuy))
		{
			checkBank();
			return Result.WORKING;
		}
		if (wantBuy)
		{
			tryBuy(level, target);
			return Result.WORKING;
		}
		if (target == null)
		{
			return Result.MISSING;
		}
		return equip(target);
	}

	/** Opens the bank (blocking walk); after repeated failures banking is given up for the run. */
	private boolean openBank()
	{
		if (bankUnavailable)
		{
			return false;
		}
		if (BankTrip.walkToAndOpen())
		{
			attempts.progress(AxePlanner.Step.OPEN_BANK);
			return true;
		}
		if (!attempts.tryStep(AxePlanner.Step.OPEN_BANK))
		{
			bankUnavailable = true;
			Microbot.log("DD Chopper: the bank will not open, no longer using it for axes");
		}
		return false;
	}

	/** Ends the buy for good this run: logs the fallback axe and sends leftover coins back to the bank. */
	private void abandonBuy(Axe wanted, String reason, int level)
	{
		abandoned.add(wanted);
		attempts.clear();
		depositLeftover = true;
		Axe using = AxePlanner.fallbackAxe(ownedAxes(), unusable, level);
		Microbot.log("DD Chopper: can't get " + wanted.getItemName() + " (" + reason + "), using "
			+ (using == null ? "no axe" : using.getItemName()));
	}

	private void depositLeftoverCoins()
	{
		status.accept("Banking leftover coins");
		if (!openBank())
		{
			depositLeftover = !bankUnavailable;
			return;
		}
		long coins = Rs2Inventory.itemQuantity(COINS);
		if (coins > 0)
		{
			Rs2Bank.depositAll(COINS);
			sleepUntil(() -> Rs2Inventory.itemQuantity(COINS) == 0, ACTION_TIMEOUT_MS);
		}
		Microbot.log("DD Chopper: deposited " + coins + " leftover coins");
		depositLeftover = false;
		Rs2Bank.closeBank();
	}

	private void checkBank()
	{
		status.accept("Checking bank for axes");
		if (openBank())
		{
			// Wait for the bank snapshot so the coin count below is not read from an empty cache.
			sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), ACTION_TIMEOUT_MS);
			bankChecked = true;
		}
		else if (bankUnavailable)
		{
			bankChecked = true;
		}
	}

	private Set<Axe> ownedAxes()
	{
		Set<Axe> owned = EnumSet.noneOf(Axe.class);
		for (Axe axe : Axe.values())
		{
			int id = axe.getItemId();
			if (!unusable.contains(axe)
				&& (Rs2Equipment.isWearing(id) || Rs2Inventory.hasItem(id) || (bankChecked && Rs2Bank.hasItem(id))))
			{
				owned.add(axe);
			}
		}
		return owned;
	}

	/** Wields (or fetches from the bank) the chosen axe; READY once it is worn or carried. */
	private Result equip(Axe target)
	{
		int id = target.getItemId();
		int attack = Rs2Player.getRealSkillLevel(Skill.ATTACK);
		if (Rs2Equipment.isWearing(id))
		{
			return ready(target);
		}
		if (Rs2Inventory.hasItem(id))
		{
			if (target.canWield(attack))
			{
				status.accept("Wielding " + target.getItemName());
				Rs2Inventory.wield(id);
				sleepUntil(() -> Rs2Equipment.isWearing(id), ACTION_TIMEOUT_MS);
			}
			return ready(target);
		}
		if (bankChecked && Rs2Bank.hasItem(id))
		{
			status.accept("Fetching " + target.getItemName());
			if (!openBank())
			{
				if (bankUnavailable)
				{
					giveUpOn(target);
				}
			}
			else if (!attempts.tryStep(AxePlanner.Step.FETCH_AXE))
			{
				giveUpOn(target);
			}
			else
			{
				if (target.canWield(attack))
				{
					Rs2Bank.withdrawAndEquip(id);
				}
				else
				{
					Rs2Bank.withdrawOne(id);
				}
				sleepUntil(() -> Rs2Equipment.isWearing(id) || Rs2Inventory.hasItem(id), ACTION_TIMEOUT_MS);
				Rs2Bank.closeBank();
			}
		}
		else if (attempts.tryStep(AxePlanner.Step.RESCAN_BANK))
		{
			bankChecked = false;
		}
		else
		{
			giveUpOn(target);
		}
		return Result.WORKING;
	}

	/** An owned axe that cannot be taken out of the bank: ignore it so the next best owned axe is chosen. */
	private void giveUpOn(Axe axe)
	{
		unusable.add(axe);
		attempts.clear();
		Microbot.log("DD Chopper: can't get " + axe.getItemName() + " (not retrievable from the bank), using the next best axe");
	}

	private Result ready(Axe axe)
	{
		if (currentAxe != axe)
		{
			Microbot.log("DD Chopper: using " + axe.getItemName());
		}
		currentAxe = axe;
		return new Result(State.READY, axe);
	}

	private void tryBuy(int level, Axe owned)
	{
		long inventoryCoins = Rs2Inventory.itemQuantity(COINS);
		long bankCoins = Rs2Bank.count(COINS);
		fillPrices(level, owned);
		AxePlanner.BuyDecision decision = AxePlanner.decideBuy(config.axe(), level, owned, inventoryCoins, bankCoins,
			bankChecked, prices);
		if (decision.step == AxePlanner.BuyStep.SCAN_BANK)
		{
			bankChecked = false;
			return;
		}
		if (decision.step == AxePlanner.BuyStep.NONE)
		{
			buyAttemptLevel = level;
			Microbot.log("DD Chopper: cannot afford a better axe at level " + level + " (coins " + inventoryCoins
				+ " inventory + " + bankCoins + " bank, prices " + prices + ")");
			if (owned != null)
			{
				Microbot.log("DD Chopper: can't get a better axe (not enough coins), using " + owned.getItemName());
			}
			return;
		}
		Microbot.log("DD Chopper: buying " + decision.axe.getItemName() + " for " + decision.price + " gp (coins "
			+ inventoryCoins + " inventory + " + bankCoins + " bank)");
		buyStep(decision.axe, decision.price, level);
	}

	private void fillPrices(int level, Axe owned)
	{
		for (Axe axe : Axe.values())
		{
			if (axe.isBuyable() && axe.canChop(level) && (owned == null || axe.ordinal() > owned.ordinal())
				&& !prices.containsKey(axe))
			{
				int live = Rs2GrandExchange.getAdaptiveBuyPrice(axe.getItemId(), BUY_PRICE_MARGIN, 0);
				if (live <= 0)
				{
					Microbot.log("DD Chopper: price lookup failed for " + axe.getItemName() + ", using fallback "
						+ AxePlanner.fallbackPrice(axe));
				}
				prices.put(axe, AxePlanner.effectivePrice(axe, live));
			}
		}
	}

	/** One step of getting coins out of the bank and buying the axe at the GE. */
	private void buyStep(Axe axe, int price, int level)
	{
		status.accept("Buying " + axe.getItemName());
		long budget = AxePlanner.coinBudget(price);
		if (Rs2Inventory.itemQuantity(COINS) < budget)
		{
			withdrawCoins(axe, price, budget, level);
			return;
		}
		attempts.progress(AxePlanner.Step.WITHDRAW_COINS);
		if (!Rs2GrandExchange.isOpen())
		{
			if (!attempts.tryStep(AxePlanner.Step.OPEN_GRAND_EXCHANGE))
			{
				abandonBuy(axe, "the Grand Exchange will not open", level);
				return;
			}
			Rs2GrandExchange.walkToGrandExchange();
			Rs2GrandExchange.openExchange();
			return;
		}
		buyAttemptLevel = level;
		if (!Rs2GrandExchange.buyItem(axe.getItemName(), price, 1))
		{
			Rs2GrandExchange.closeExchange();
			abandonBuy(axe, "the offer could not be placed", level);
			return;
		}
		sleepUntilTrue(Rs2GrandExchange::hasFinishedBuyingOffers, OFFER_POLL_MS, OFFER_TIMEOUT_MS);
		Rs2GrandExchange.collectAllToInventory();
		Rs2GrandExchange.closeExchange();
		bankChecked = false;
		depositLeftover = true;
		attempts.clear();
		if (Rs2Inventory.hasItem(axe.getItemId()))
		{
			Microbot.log("DD Chopper: bought " + axe.getItemName() + " (offer " + price + " gp), leftover coins go back to the bank");
		}
		else
		{
			abandonBuy(axe, "the offer did not fill in time", level);
		}
	}

	private void withdrawCoins(Axe axe, int price, long budget, int level)
	{
		if (!openBank())
		{
			if (bankUnavailable)
			{
				abandonBuy(axe, "the bank will not open", level);
			}
			return;
		}
		if (!attempts.tryStep(AxePlanner.Step.WITHDRAW_COINS))
		{
			abandonBuy(axe, "withdrawing coins did not cover the price", level);
			return;
		}
		long have = Rs2Inventory.itemQuantity(COINS);
		long take = AxePlanner.coinsToWithdraw(price, have, Rs2Bank.count(COINS));
		Microbot.log("DD Chopper: withdrawing " + take + " coins for " + axe.getItemName() + " (price " + price
			+ ", budget " + budget + ", had " + have + ")");
		if (take > 0)
		{
			Rs2Bank.withdrawX(COINS, (int) take);
			sleepUntil(() -> Rs2Inventory.itemQuantity(COINS) >= Math.min(budget, have + take), ACTION_TIMEOUT_MS);
		}
		Rs2Bank.closeBank();
	}
}
