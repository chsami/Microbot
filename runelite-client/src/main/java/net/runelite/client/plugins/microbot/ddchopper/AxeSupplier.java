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
		/** {@link #readyAxe()} is usable. */
		READY,
		/** Nothing usable owned and nothing could be bought. */
		MISSING
	}

	static final Integer[] AXE_IDS = new Integer[Axe.values().length];

	private static final int COINS = 995;
	private static final int ACTION_TIMEOUT_MS = 3_000;
	private static final int OFFER_POLL_MS = 600;
	private static final int OFFER_TIMEOUT_MS = 60_000;
	/** Price margin over the GE guide price so the offer fills quickly. */
	private static final double BUY_PRICE_MARGIN = 1.15;

	static
	{
		for (Axe axe : Axe.values())
		{
			AXE_IDS[axe.ordinal()] = axe.getItemId();
		}
	}

	private final DDChopperConfig config;
	private final Consumer<String> status;
	private final Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
	private boolean bankChecked;
	private boolean depositLeftover;
	private int buyAttemptLevel = -1;
	private Axe readyAxe;

	AxeSupplier(DDChopperConfig config, Consumer<String> status)
	{
		this.config = config;
		this.status = status;
	}

	Axe readyAxe()
	{
		return readyAxe;
	}

	State check(int level)
	{
		readyAxe = null;
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

	private void depositLeftoverCoins()
	{
		status.accept("Banking leftover coins");
		if (!BankTrip.ensureOpen())
		{
			return;
		}
		long coins = Rs2Inventory.count(COINS);
		if (coins > 0)
		{
			Rs2Bank.depositAll(COINS);
			sleepUntil(() -> Rs2Inventory.count(COINS) == 0, ACTION_TIMEOUT_MS);
		}
		Microbot.log("DD Chopper: deposited " + coins + " leftover coins");
		depositLeftover = false;
		Rs2Bank.closeBank();
	}

	private void checkBank()
	{
		status.accept("Checking bank for axes");
		if (BankTrip.ensureOpen())
		{
			// Wait for the bank snapshot so the coin count below is not read from an empty cache.
			sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), ACTION_TIMEOUT_MS);
			bankChecked = true;
		}
	}

	private Set<Axe> ownedAxes()
	{
		Set<Axe> owned = EnumSet.noneOf(Axe.class);
		for (Axe axe : Axe.values())
		{
			int id = axe.getItemId();
			if (Rs2Equipment.isWearing(id) || Rs2Inventory.hasItem(id) || (bankChecked && Rs2Bank.hasItem(id)))
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
			if (BankTrip.ensureOpen())
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
		else
		{
			bankChecked = false;
		}
		return State.WORKING;
	}

	private State ready(Axe axe)
	{
		readyAxe = axe;
		return State.READY;
	}

	private void tryBuy(int level, Axe owned)
	{
		long inventoryCoins = Rs2Inventory.count(COINS);
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
		if (Rs2Inventory.count(COINS) < budget)
		{
			withdrawCoins(axe, price, budget);
			return;
		}
		if (!Rs2GrandExchange.isOpen())
		{
			Rs2GrandExchange.walkToGrandExchange();
			Rs2GrandExchange.openExchange();
			return;
		}
		buyAttemptLevel = level;
		if (!Rs2GrandExchange.buyItem(axe.getItemName(), price, 1))
		{
			Microbot.log("DD Chopper: could not place the offer for " + axe.getItemName());
			Rs2GrandExchange.closeExchange();
			return;
		}
		sleepUntilTrue(Rs2GrandExchange::hasFinishedBuyingOffers, OFFER_POLL_MS, OFFER_TIMEOUT_MS);
		Rs2GrandExchange.collectAllToInventory();
		Rs2GrandExchange.closeExchange();
		bankChecked = false;
		depositLeftover = true;
		Microbot.log("DD Chopper: bought " + axe.getItemName() + " (offer " + price + " gp), leftover coins go back to the bank");
	}

	private void withdrawCoins(Axe axe, int price, long budget)
	{
		if (!BankTrip.ensureOpen())
		{
			return;
		}
		long have = Rs2Inventory.count(COINS);
		long take = AxePlanner.coinsToWithdraw(price, have, Rs2Bank.count(COINS));
		Microbot.log("DD Chopper: withdrawing " + take + " coins for " + axe.getItemName() + " (price " + price
			+ ", budget " + budget + ", had " + have + ")");
		if (take > 0)
		{
			Rs2Bank.withdrawX(COINS, (int) take);
			sleepUntil(() -> Rs2Inventory.count(COINS) >= Math.min(budget, have + take), ACTION_TIMEOUT_MS);
		}
		Rs2Bank.closeBank();
	}
}
