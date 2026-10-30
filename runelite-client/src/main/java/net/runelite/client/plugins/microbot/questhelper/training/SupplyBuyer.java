package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.slf4j.event.Level;

/**
 * Gets a trainer's supplies into the inventory, one small step per call: bank first, then (when the run allows it)
 * the Grand Exchange for the shortfall, then the bank again. Game-bound.
 */
public final class SupplyBuyer
{
	public enum Status
	{
		WORKING, READY, FAILED
	}

	private static final int MAX_BUY_ROUNDS = 3;

	private int buyRounds;
	private String message = "";

	public String message()
	{
		return message;
	}

	public void reset()
	{
		buyRounds = 0;
		message = "";
	}

	/** One step towards having every supply in the inventory. */
	public Status ensure(List<SkillTrainer.Supply> wants, TrainerContext context)
	{
		if (wants.isEmpty() || haveAll(wants))
		{
			return Status.READY;
		}
		Microbot.status = "Training: fetching supplies";
		if (!Rs2Bank.isOpen() && !Rs2Bank.walkToBankAndUseBank())
		{
			return Status.WORKING;
		}
		if (!Rs2Bank.isOpen())
		{
			return Status.WORKING;
		}
		Rs2Bank.depositAll();
		List<SkillTrainer.Supply> short_ = new ArrayList<>();
		for (SkillTrainer.Supply want : wants)
		{
			int inBank = Rs2Bank.count(want.name);
			if (inBank >= want.quantity)
			{
				Rs2Bank.withdrawX(want.name, want.quantity);
			}
			else
			{
				if (inBank > 0)
				{
					Rs2Bank.withdrawAll(want.name);
				}
				short_.add(new SkillTrainer.Supply(want.name, want.id, want.quantity - inBank));
			}
		}
		if (short_.isEmpty())
		{
			Rs2Bank.closeBank();
			return Status.WORKING;
		}
		if (buyRounds >= MAX_BUY_ROUNDS)
		{
			return fail("could not get " + names(short_) + " after " + MAX_BUY_ROUNDS + " Grand Exchange rounds");
		}
		long total = 0;
		List<Integer> prices = new ArrayList<>();
		for (SkillTrainer.Supply item : short_)
		{
			int price = Rs2GrandExchange.getAdaptiveBuyPrice(item.id, 1.15, buyRounds);
			price = price <= 0 ? 500 : price;
			prices.add(price);
			total += (long) price * item.quantity;
		}
		List<String> names = new ArrayList<>();
		for (SkillTrainer.Supply item : short_)
		{
			names.add(item.name);
		}
		if (!context.mayBuy(names, total))
		{
			return fail("needs " + names(short_) + " (about " + total + " gp) that is not in the bank and buying is not allowed");
		}
		Rs2Bank.closeBank();
		Microbot.status = "Training: buying " + names(short_);
		if (!Rs2GrandExchange.isOpen())
		{
			Rs2GrandExchange.walkToGrandExchange();
			Rs2GrandExchange.openExchange();
		}
		if (!Rs2GrandExchange.isOpen())
		{
			return Status.WORKING;
		}
		for (int i = 0; i < short_.size(); i++)
		{
			SkillTrainer.Supply item = short_.get(i);
			if (!Rs2GrandExchange.buyItem(item.name, prices.get(i), item.quantity))
			{
				Microbot.log("Training: could not place an offer for " + item.name, Level.WARN);
			}
		}
		Global.sleepUntilTrue(Rs2GrandExchange::hasFinishedBuyingOffers, 600, 60_000);
		Rs2GrandExchange.collectAllToBank();
		Rs2GrandExchange.closeExchange();
		context.spent(total);
		buyRounds++;
		return Status.WORKING;
	}

	private Status fail(String why)
	{
		message = why;
		return Status.FAILED;
	}

	private static boolean haveAll(List<SkillTrainer.Supply> wants)
	{
		for (SkillTrainer.Supply want : wants)
		{
			if (Rs2Inventory.count(want.name) < want.quantity)
			{
				return false;
			}
		}
		return true;
	}

	private static String names(List<SkillTrainer.Supply> items)
	{
		StringBuilder sb = new StringBuilder();
		for (SkillTrainer.Supply item : items)
		{
			sb.append(sb.length() == 0 ? "" : ", ").append(item.quantity).append(' ').append(item.name);
		}
		return sb.toString();
	}
}
