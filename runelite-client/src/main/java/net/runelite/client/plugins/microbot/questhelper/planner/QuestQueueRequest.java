package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions;

/**
 * A POST /quest-helper/queue body, validated. Pure so the Agent Server's rules are testable: quests are plain names or
 * objects with a per-quest profile; unknown names, enum values and shapes are rejected with a message.
 */
public final class QuestQueueRequest
{
	/** One queued quest and the optional raw combat setup ("skip" or an object) the handler stores. */
	public static final class Entry
	{
		public final QuestProfile profile;
		public final Object combat;

		Entry(QuestProfile profile, Object combat)
		{
			this.profile = profile;
			this.combat = combat;
		}
	}

	public String error;
	public final List<Entry> entries = new ArrayList<>();
	public boolean hasQuests;
	public QuestQueue.Mode mode;
	public List<String> pool;
	public Integer pauseMin;
	public Integer pauseMax;
	public Integer defaultMinStamina;
	public boolean start;
	public boolean clear;
	/** Fully automated switch and the max spend per quest, when the body sets them. */
	public Boolean fullyAutomated;
	public Integer maxSpendPerQuest;
	/** True (the default for the Agent Server) approves every purchase up front; false keeps the normal dialogs. */
	public boolean approve = true;
	/** Put each quest's unmet requirements (prerequisites, training, boosts) in front of it; default true. */
	public boolean resolveRequirements = true;
	/** Skill to Hub plugin name, when the body sets it. */
	public java.util.Map<String, Object> skillPlugins;

	private QuestQueueRequest()
	{
	}

	private static QuestQueueRequest fail(QuestQueueRequest request, String message)
	{
		request.error = message;
		return request;
	}

	/**
	 * @param known    whether a quest name exists
	 * @param defaults the profile a plain name gets (saved, else the suggested default)
	 */
	@SuppressWarnings("unchecked")
	public static QuestQueueRequest parse(Map<String, Object> body, Predicate<String> known,
		Function<String, QuestProfile> defaults)
	{
		QuestQueueRequest request = new QuestQueueRequest();
		request.clear = Boolean.TRUE.equals(body.get("clear"));
		request.start = Boolean.TRUE.equals(body.get("start"));
		if (body.get("approve") instanceof Boolean)
		{
			request.approve = (Boolean) body.get("approve");
		}
		if (body.get("resolveRequirements") instanceof Boolean)
		{
			request.resolveRequirements = (Boolean) body.get("resolveRequirements");
		}
		if (body.get("skillPlugins") instanceof Map)
		{
			request.skillPlugins = (Map<String, Object>) body.get("skillPlugins");
		}
		if (body.get("fullyAutomated") instanceof Boolean)
		{
			request.fullyAutomated = (Boolean) body.get("fullyAutomated");
		}
		request.maxSpendPerQuest = intOf(body.get("maxSpendPerQuest"));
		if (request.maxSpendPerQuest != null && request.maxSpendPerQuest < 0)
		{
			return fail(request, "maxSpendPerQuest must be 0 or more");
		}
		Object mode = body.get("mode");
		if (mode != null)
		{
			String text = String.valueOf(mode).trim().toUpperCase(Locale.ROOT);
			if (!text.equals("FIXED") && !text.equals("RANDOM"))
			{
				return fail(request, "mode must be fixed or random");
			}
			request.mode = QuestQueue.Mode.valueOf(text);
		}
		request.pauseMin = intOf(body.get("pauseMinMinutes"));
		request.pauseMax = intOf(body.get("pauseMaxMinutes"));
		request.defaultMinStamina = intOf(body.get("defaultMinStamina"));
		if (request.defaultMinStamina != null
			&& (request.defaultMinStamina < 0 || request.defaultMinStamina > QuestProfile.MAX_MIN_STAMINA))
		{
			return fail(request, "defaultMinStamina must be 0 to " + QuestProfile.MAX_MIN_STAMINA);
		}
		Object pool = body.get("pool");
		if (pool != null)
		{
			if (!(pool instanceof List))
			{
				return fail(request, "pool must be a list of quest names");
			}
			request.pool = new ArrayList<>();
			for (Object name : (List<Object>) pool)
			{
				if (!known.test(String.valueOf(name)))
				{
					return fail(request, "Unknown quest in pool: " + name);
				}
				request.pool.add(String.valueOf(name));
			}
		}
		Object quests = body.get("quests");
		if (quests == null)
		{
			return request;
		}
		if (!(quests instanceof List))
		{
			return fail(request, "quests must be a list");
		}
		request.hasQuests = true;
		for (Object item : (List<Object>) quests)
		{
			String name;
			Map<String, Object> fields = null;
			if (item instanceof String)
			{
				name = (String) item;
			}
			else if (item instanceof Map)
			{
				fields = (Map<String, Object>) item;
				name = fields.get("name") == null ? "" : String.valueOf(fields.get("name"));
			}
			else
			{
				return fail(request, "each quest must be a name or an object");
			}
			if (!known.test(name))
			{
				return fail(request, "Unknown quest: " + name);
			}
			QuestProfile profile = defaults.apply(name);
			Object combat = null;
			if (fields != null)
			{
				String problem = applyFields(profile, fields);
				if (problem != null)
				{
					return fail(request, name + ": " + problem);
				}
				combat = fields.get("combat");
				if (combat != null && !"skip".equals(combat) && !(combat instanceof Map))
				{
					return fail(request, name + ": combat must be \"skip\" or an object");
				}
			}
			request.entries.add(new Entry(profile, combat));
		}
		return request;
	}

	@SuppressWarnings("unchecked")
	private static String applyFields(QuestProfile profile, Map<String, Object> fields)
	{
		QuestProfile.Supplies supplies = profile.getSupplies();
		Object obtain = fields.get("obtain");
		if (obtain != null)
		{
			String text = String.valueOf(obtain).trim().toUpperCase(Locale.ROOT);
			if (text.equals("GE") || text.equals("YES"))
			{
				text = "GRAND_EXCHANGE";
			}
			else if (text.equals("BANK") || text.equals("NO"))
			{
				text = "BANK_ONLY";
			}
			try
			{
				profile.setObtain(QuestProfile.Obtain.valueOf(text));
			}
			catch (IllegalArgumentException e)
			{
				return "obtain must be GRAND_EXCHANGE or BANK_ONLY";
			}
		}
		Object type = fields.get("type");
		if (type != null)
		{
			try
			{
				profile.setType(QuestProfile.Type.valueOf(String.valueOf(type).trim().toUpperCase(Locale.ROOT)));
			}
			catch (IllegalArgumentException e)
			{
				return "type must be TRAVEL or COMBAT";
			}
		}
		Integer minStamina = intOf(fields.get("minStamina"));
		if (minStamina != null)
		{
			if (minStamina < -1 || minStamina > QuestProfile.MAX_MIN_STAMINA)
			{
				return "minStamina must be 0 to " + QuestProfile.MAX_MIN_STAMINA;
			}
			profile.setMinStamina(minStamina);
		}
		Integer stamina = intOf(fields.get("staminaDoses"));
		if (stamina != null)
		{
			supplies.setStaminaOn(stamina > 0);
			supplies.setStaminaQty(Math.max(0, Math.min(27, stamina)));
			supplies.setCustomised(true);
		}
		if (fields.get("food") != null)
		{
			supplies.setFood(String.valueOf(fields.get("food")));
			Integer qty = intOf(fields.get("foodQty"));
			supplies.setFoodQty(qty == null ? 10 : Math.max(0, Math.min(27, qty)));
			supplies.setCustomised(true);
		}
		Integer eat = intOf(fields.get("eatAtPercent"));
		if (eat != null)
		{
			supplies.setEatAtPercent(Math.max(0, Math.min(99, eat)));
			supplies.setCustomised(true);
		}
		Integer prayer = intOf(fields.get("prayerPotions"));
		if (prayer != null)
		{
			supplies.setPotionQty(QuestPotions.Kind.PRAYER_POTION, prayer);
			supplies.setCustomised(true);
		}
		Object boosts = fields.get("boostPotions");
		if (boosts != null)
		{
			if (!(boosts instanceof Map))
			{
				return "boostPotions must be an object of potion key to count";
			}
			for (Map.Entry<String, Object> boost : ((Map<String, Object>) boosts).entrySet())
			{
				QuestPotions.Kind kind = QuestPotions.kindOf(boost.getKey());
				Integer qty = intOf(boost.getValue());
				if (kind == null || qty == null || !QuestProfile.POTION_KINDS.contains(kind))
				{
					return "unknown boost potion: " + boost.getKey();
				}
				supplies.setPotionQty(kind, qty);
			}
			supplies.setCustomised(true);
		}
		Object teleports = fields.get("teleports");
		if (teleports != null)
		{
			if (!(teleports instanceof List))
			{
				return "teleports must be a list of {name, qty}";
			}
			for (Object teleport : (List<Object>) teleports)
			{
				if (!(teleport instanceof Map) || ((Map<String, Object>) teleport).get("name") == null)
				{
					return "each teleport needs a name";
				}
				Map<String, Object> map = (Map<String, Object>) teleport;
				Integer qty = intOf(map.get("qty"));
				supplies.getItems().add(new QuestCombatLoadout.InventoryItem(String.valueOf(map.get("name")), -1,
					qty == null ? 1 : Math.max(1, qty), false));
			}
			supplies.setCustomised(true);
		}
		Object noBuy = fields.get("noBuy");
		if (noBuy instanceof List)
		{
			for (Object key : (List<Object>) noBuy)
			{
				supplies.setBuys(String.valueOf(key), false);
			}
		}
		return null;
	}

	private static Integer intOf(Object value)
	{
		return value instanceof Number ? ((Number) value).intValue() : null;
	}
}
