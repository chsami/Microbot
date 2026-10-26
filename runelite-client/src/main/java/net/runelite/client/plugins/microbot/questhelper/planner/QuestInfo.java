package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestInventoryRules;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import net.runelite.client.plugins.microbot.questhelper.QuestPurchasePlan;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatDetector;
import net.runelite.client.plugins.microbot.questhelper.managers.ItemAndLastUpdated;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestContainerManager;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import org.slf4j.event.Level;

/**
 * Everything the planner wizard shows about one quest: required items with what the player has and what buying the
 * rest costs, recommended items plus our own suggestions, and what the quest forbids. Built off the EDT.
 */
public final class QuestInfo
{
	/** One item line of the wizard. */
	public static final class ItemLine
	{
		public final String name;
		public final int quantity;
		public final List<Integer> ids;
		public final boolean tradable;
		public final int onPlayer;
		public final int inBank;
		/** Estimated Grand Exchange price of one item (offer price), 0 when unknown or not tradable. */
		public final int unitPrice;
		/** Where the line came from: "quest" or "suggested". */
		public final String source;
		public final String note;

		public ItemLine(String name, int quantity, List<Integer> ids, boolean tradable, int onPlayer, int inBank,
			int unitPrice, String source, String note)
		{
			this.name = name;
			this.quantity = quantity;
			this.ids = ids;
			this.tradable = tradable;
			this.onPlayer = onPlayer;
			this.inBank = inBank;
			this.unitPrice = unitPrice;
			this.source = source;
			this.note = note == null ? "" : note;
		}

		public int missing()
		{
			return Math.max(0, quantity - onPlayer - inBank);
		}

		public long cost()
		{
			return tradable ? (long) missing() * unitPrice : 0;
		}

		public String status()
		{
			if (missing() == 0)
			{
				return onPlayer >= quantity ? "have" : "bank";
			}
			if (!tradable)
			{
				return "missing (not tradable)";
			}
			return "buy " + missing();
		}
	}

	public final String questName;
	public final String questKey;
	public final boolean hasCombat;
	public final List<ItemLine> required = new ArrayList<>();
	public final List<ItemLine> recommended = new ArrayList<>();
	public final List<QuestRestrictions.Restriction> restrictions = new ArrayList<>();

	private QuestInfo(String questName, String questKey, boolean hasCombat)
	{
		this.questName = questName;
		this.questKey = questKey;
		this.hasCombat = hasCombat;
	}

	/** Cost of buying everything required and missing. */
	public long requiredCost()
	{
		long sum = 0;
		for (ItemLine line : required)
		{
			sum += line.cost();
		}
		return sum;
	}

	/** Suggested extras: stamina for travel, food for combat, teleports. Ticking one adds it to the profile. */
	static List<String[]> suggestionDefs(boolean hasCombat)
	{
		return suggestionDefs(hasCombat, QuestInventoryRules.DEFAULT_FOOD_QTY);
	}

	static List<String[]> suggestionDefs(boolean hasCombat, int foodQty)
	{
		List<String[]> out = new ArrayList<>();
		out.add(new String[] {"Stamina potion(4)", "2", "Travel-heavy quests drain run energy"});
		if (hasCombat && foodQty > 0)
		{
			out.add(new String[] {"Lobster", String.valueOf(foodQty), "Food for the fights"});
		}
		out.add(new String[] {"Varrock teleport", "3", "Teleport back to a bank"});
		out.add(new String[] {"Falador teleport", "3", "Teleport back to a bank"});
		return out;
	}

	/** Builds the info for a quest. Call off the EDT and off the client thread. */
	public static QuestInfo build(QuestHelper helper, List<QuestPreflight.Need> needs)
	{
		String name = helper.getQuest().getName();
		boolean combat = safeHasCombat(helper);
		QuestInfo info = new QuestInfo(name, helper.getQuest().name(), combat);
		try
		{
			Microbot.getClientThread().runOnClientThreadOptional(() ->
			{
				helper.initializeRequirements();
				return true;
			});
			Map<Integer, Integer> player = new HashMap<>();
			Map<Integer, Integer> bank = new HashMap<>();
			addContainer(player, QuestContainerManager.getInventoryData());
			addContainer(player, QuestContainerManager.getEquippedData());
			addContainer(bank, QuestContainerManager.getBankData());

			if (needs != null)
			{
				for (QuestPreflight.Need need : needs)
				{
					int onPlayer = 0;
					int inBank = 0;
					for (int id : need.ids)
					{
						onPlayer += player.getOrDefault(id, 0);
						inBank += bank.getOrDefault(id, 0);
					}
					int base = need.ids.isEmpty() ? 0 : priceOf(need.ids.get(0));
					info.required.add(new ItemLine(need.name, need.quantity, need.ids, true,
						Math.min(onPlayer, need.quantity), Math.min(inBank, Math.max(0, need.quantity - onPlayer)),
						base > 0 ? QuestPurchasePlan.offerPrice(base) : 0, "quest", ""));
				}
			}
			List<ItemLine> upfront = new ArrayList<>();
			collect(upfront, helper.getItemRequirements(), player, bank, "quest");
			for (ItemLine line : upfront)
			{
				if (!line.tradable)
				{
					info.required.add(line);
				}
			}
			collect(info.recommended, helper.getItemRecommended(), player, bank, "quest");
			for (String[] suggestion : suggestionDefs(combat, safeFoodQty(helper, combat)))
			{
				info.recommended.add(new ItemLine(suggestion[0], Integer.parseInt(suggestion[1]),
					new ArrayList<>(), true, 0, 0, 0, "suggested", suggestion[2]));
			}

			info.restrictions.addAll(QuestRestrictions.forQuest(info.questKey, restrictionTexts(helper)));
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest planner: could not read the requirements of " + name + ": " + e, Level.WARN);
		}
		return info;
	}

	/** Every Quest Helper text that may state what the quest forbids: notes, general requirements, item tooltips. */
	public static List<String> restrictionTexts(QuestHelper helper)
	{
		List<String> texts = new ArrayList<>();
		try
		{
			addAll(texts, helper.getCombatRequirements());
			addAll(texts, helper.getNotes());
			if (helper.getGeneralRequirements() != null)
			{
				for (Requirement requirement : helper.getGeneralRequirements())
				{
					if (requirement != null)
					{
						texts.add(requirement.getDisplayText());
					}
				}
			}
			for (List<ItemRequirement> list : java.util.Arrays.asList(helper.getItemRequirements(),
				helper.getItemRecommended()))
			{
				if (list != null)
				{
					for (ItemRequirement ir : list)
					{
						if (ir != null)
						{
							texts.add(ir.getTooltip());
						}
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest planner: could not read the notes of " + helper.getQuest().getName() + ": " + e, Level.WARN);
		}
		return texts;
	}

	private static int safeFoodQty(QuestHelper helper, boolean combat)
	{
		if (!combat)
		{
			return 0;
		}
		try
		{
			return QuestCombatDetector.defaultFoodQty(helper);
		}
		catch (RuntimeException e)
		{
			return QuestInventoryRules.DEFAULT_FOOD_QTY;
		}
	}

	private static boolean safeHasCombat(QuestHelper helper)
	{
		try
		{
			return QuestCombatDetector.hasFights(helper);
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	private static void addAll(List<String> out, List<String> more)
	{
		if (more != null)
		{
			out.addAll(more);
		}
	}

	private static void collect(List<ItemLine> out, List<ItemRequirement> requirements, Map<Integer, Integer> player,
		Map<Integer, Integer> bank, String source)
	{
		if (requirements == null)
		{
			return;
		}
		for (ItemRequirement ir : requirements)
		{
			if (ir == null || ir.getClass() != ItemRequirement.class || ir.getQuantity() <= 0)
			{
				continue;
			}
			List<Integer> ids = new ArrayList<>();
			for (Integer id : ir.getAllIds())
			{
				if (id != null && id > 0)
				{
					ids.add(id);
				}
			}
			if (ids.isEmpty())
			{
				continue;
			}
			int onPlayer = 0;
			int inBank = 0;
			for (int id : ids)
			{
				onPlayer += player.getOrDefault(id, 0);
				inBank += bank.getOrDefault(id, 0);
			}
			boolean tradable = isTradable(ids.get(0));
			int price = 0;
			if (tradable)
			{
				int base = priceOf(ids.get(0));
				price = base > 0 ? QuestPurchasePlan.offerPrice(base) : 0;
			}
			out.add(new ItemLine(ir.getName(), ir.getQuantity(), ids, tradable, Math.min(onPlayer, ir.getQuantity()),
				Math.min(inBank, Math.max(0, ir.getQuantity() - onPlayer)), price, source, ir.getTooltip()));
		}
	}

	private static boolean isTradable(int id)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			ItemComposition def = Microbot.getClient().getItemDefinition(id);
			return def != null && def.isTradeable();
		}).orElse(false);
	}

	private static int priceOf(int id)
	{
		try
		{
			int price = Rs2GrandExchange.getPrice(id);
			return price > 0 && price < Integer.MAX_VALUE ? price : 0;
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}

	private static void addContainer(Map<Integer, Integer> counts, ItemAndLastUpdated container)
	{
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0 && item.getQuantity() > 0)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	/** A supply as a priced item line with what the player holds; resolves the item by name when no id is known. */
	public static ItemLine supplyLine(QuestProfile.SupplyLine supply)
	{
		List<Integer> ids = new ArrayList<>(supply.ids);
		if (ids.isEmpty())
		{
			int id = net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.resolveItemId(supply.name);
			if (id > 0)
			{
				ids.add(id);
			}
		}
		Map<Integer, Integer> player = new HashMap<>();
		Map<Integer, Integer> bank = new HashMap<>();
		addContainer(player, QuestContainerManager.getInventoryData());
		addContainer(bank, QuestContainerManager.getBankData());
		int onPlayer = 0;
		int inBank = 0;
		for (int id : ids)
		{
			onPlayer += player.getOrDefault(id, 0);
			inBank += bank.getOrDefault(id, 0);
		}
		boolean tradable = !ids.isEmpty() && isTradable(ids.get(0));
		int base = tradable ? priceOf(ids.get(0)) : 0;
		return new ItemLine(supply.name, supply.qty, ids, tradable, Math.min(onPlayer, supply.qty),
			Math.min(inBank, Math.max(0, supply.qty - onPlayer)), base > 0 ? QuestPurchasePlan.offerPrice(base) : 0,
			"supply", "");
	}

	/** Coins in the inventory and the bank. */
	public static long coins()
	{
		long sum = 0;
		for (ItemAndLastUpdated container : java.util.Arrays.asList(QuestContainerManager.getInventoryData(),
			QuestContainerManager.getBankData()))
		{
			for (Item item : container.getItems())
			{
				if (item != null && item.getId() == net.runelite.api.ItemID.COINS_995)
				{
					sum += item.getQuantity();
				}
			}
		}
		return sum;
	}

	/** Lines whose item is not tradable or unknown are never bought; handy for the wizard totals. */
	public static List<ItemLine> buyable(List<ItemLine> lines)
	{
		List<ItemLine> out = new ArrayList<>();
		for (ItemLine line : lines)
		{
			if (line.tradable && line.missing() > 0)
			{
				out.add(line);
			}
		}
		return Collections.unmodifiableList(out);
	}
}
