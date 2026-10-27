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
		/** {@link #currentAxe()} is worn or carried and usable. */
		READY,
		/** Nothing usable owned and nothing could be bought. */
		MISSING
	}

	private static final int COINS = 995;
	private static final int OFFER_POLL_MS = 600;
	private static final int OFFER_TIMEOUT_MS = 60_000;
	/** Price margin over the GE guide price so the offer fills quickly. */
	private static final double BUY_PRICE_MARGIN = 1.15;

	private final DDChopperConfig config;
	private final Consumer<String> status;
	private final BankTrip bank;
	private final Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
	private boolean bankChecked;
	private boolean depositLeftover;
	private int buyAttemptLevel = -1;
	private final AxePlanner.Attempts attempts = new AxePlanner.Attempts();
	/** Axes given up on buying for this run (coins, bank or Grand Exchange kept failing). */
	private final Set<Axe> abandoned = EnumSet.noneOf(Axe.class);
	/** Owned axes that could not be taken out of the bank; the next best owned axe is used instead. */
	private final Set<Axe> unusable = EnumSet.noneOf(Axe.class);
	private volatile Axe currentAxe;

	AxeSupplier(DDChopperConfig config, Consumer<String> status, BankTrip bank)
	{
		this.config = config;
		this.status = status;
		this.bank = bank;
	}

	/** The axe last reported READY (for the overlay and the chop planner); null until one is ready. */
	Axe currentAxe()
	{
		return currentAxe;
	}

	State check(int level)
	{
		if (depositLeftover)
		{
			depositLeftoverCoins();
			return State.WORKING;
		}
		Set<Axe> owned = ownedAxes();
		Axe target = AxePlanner.chooseOwned(config.axe(), owned, level);
		Axe desired = config.axe().axe() != null ? config.axe().axe() : AxePlanner.bestUnlocked(level, false);
		boolean wantBuy = config.buyAxe() && desired != null && desired.isBuyable() && desired.canChop(level)
			&& (target == null || target.ordinal() < desired.ordinal()) && buyAttemptLevel != level;

		if (!bankChecked && (target == null || wantBuy))
		{
			checkBank();
			return State.WORKING;
		}
		if (wantBuy)
		{
			tryBuy(level, target);
			return State.WORKING;
		}
		if (target == null)
		{
			return State.MISSING;
		}
		return equip(target);
	}

	/**
	 * Ends the buy of {@code wanted}: it is never chosen again this run, no more buying is tried at this level, and
	 * leftover coins go back to the bank. Logs the axe the player keeps chopping with.
	 */
	private void abandonBuy(Axe wanted, String reason, int level)
	{
		abandoned.add(wanted);
		buyAttemptLevel = level;
		attempts.clear();
		depositLeftover = true;
		Axe using = AxePlanner.chooseOwned(config.axe(), ownedAxes(), level);
		Microbot.log("DD Chopper: can't get " + wanted.getItemName() + " (" + reason + "), using "
			+ (using == null ? "no axe" : using.getItemName()));
	}

	private void depositLeftoverCoins()
	{
		status.accept("Banking leftover coins");
		if (!bank.walkToAndOpen())
		{
			depositLeftover = !bank.isUnavailable();
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
		if (bank.walkToAndOpen())
		{
			// Wait for the bank snapshot so the coin count below is not read from an empty cache.
			sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), ACTION_TIMEOUT_MS);
			bankChecked = true;
		}
		else if (bank.isUnavailable())
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
	private State equip(Axe target)
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
			if (!bank.walkToAndOpen())
			{
				if (bank.isUnavailable())
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
		return State.WORKING;
	}

	/** An owned axe that cannot be taken out of the bank: ignore it so the next best owned axe is chosen. */
	private void giveUpOn(Axe axe)
	{
		unusable.add(axe);
		attempts.clear();
		Microbot.log("DD Chopper: can't get " + axe.getItemName() + " (not retrievable from the bank), using the next best axe");
	}

	private State ready(Axe axe)
	{
		if (currentAxe != axe)
		{
			Microbot.log("DD Chopper: using " + axe.getItemName());
		}
		currentAxe = axe;
		attempts.clear();
		return State.READY;
	}

	private void tryBuy(int level, Axe owned)
	{
		long inventoryCoins = Rs2Inventory.itemQuantity(COINS);
		long bankCoins = Rs2Bank.count(COINS);
		fillPrices(level, owned);
		AxePlanner.BuyDecision decision = AxePlanner.decideBuy(config.axe(), level, owned, inventoryCoins, bankCoins,
			bankChecked, prices, abandoned);
		if (decision.step == AxePlanner.BuyStep.SCAN_BANK)
		{
			bankChecked = false;
			return;
		}
		if (decision.step == AxePlanner.BuyStep.NONE)
		{
			buyAttemptLevel = level;
			Microbot.log("DD Chopper: can't afford a better axe at level " + level + " (coins " + inventoryCoins
				+ " inventory + " + bankCoins + " bank, prices " + prices + "), using "
				+ (owned == null ? "no axe" : owned.getItemName()));
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
		boolean bought = sleepUntil(() -> Rs2Inventory.hasItem(axe.getItemId()), ACTION_TIMEOUT_MS);
		if (!bought)
		{
			// Cancel the unfilled offer so the coins come back instead of staying locked in the exchange.
			Rs2GrandExchange.abortOffer(axe.getItemName(), false);
		}
		Rs2GrandExchange.closeExchange();
		bankChecked = false;
		depositLeftover = true;
		attempts.clear();
		if (bought)
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
		if (!bank.walkToAndOpen())
		{
			if (bank.isUnavailable())
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
