package net.runelite.client.plugins.microbot.questhelper.planner;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import net.runelite.client.plugins.microbot.questhelper.QuestInventoryRules;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions;

/**
 * How one queued quest is run: where missing items may come from, whether it is a Travel or a Combat quest, the gear
 * to wear and its own supplies. Pure data. The Combat gear, prayer and style live in the quest's
 * {@link QuestCombatLoadout} (QuestCombatStore); this class decides which loadout the quest runs with.
 */
@Data
public class QuestProfile
{
	private static final Gson GSON = new Gson();

	/** Highest "keep at least N stamina potions" value. */
	public static final int MAX_MIN_STAMINA = 4;

	/** Food a combat quest brings when nothing says how weak its fights are. */
	public static final int DEFAULT_COMBAT_FOOD_QTY = QuestInventoryRules.DEFAULT_FOOD_QTY;

	/** Where missing quest items may come from. */
	public enum Obtain
	{
		GRAND_EXCHANGE, BANK_ONLY
	}

	/** Travel brings stamina potions and a gear set; Combat uses a full combat loadout. */
	public enum Type
	{
		TRAVEL, COMBAT
	}

	/** Potion kinds the planner offers a count for (prayer first, then boosts). */
	public static final List<QuestPotions.Kind> POTION_KINDS = Arrays.asList(
		QuestPotions.Kind.PRAYER_POTION, QuestPotions.Kind.SUPER_RESTORE, QuestPotions.Kind.SUPER_COMBAT,
		QuestPotions.Kind.SUPER_ATTACK, QuestPotions.Kind.SUPER_STRENGTH, QuestPotions.Kind.SUPER_DEFENCE,
		QuestPotions.Kind.RANGING, QuestPotions.Kind.MAGIC);

	private String questName = "";
	private Obtain obtain = Obtain.GRAND_EXCHANGE;
	private Type type = Type.TRAVEL;
	/** Worn items for a Travel quest; empty leaves the gear alone. */
	private List<QuestCombatLoadout.GearItem> travelGear = new ArrayList<>();
	private Supplies supplies = new Supplies();
	/** Keep at least this many stamina potions (0-4); -1 uses the queue's global default. */
	private int minStamina = -1;
	/** Bank the items the quest forbids before the steps that need it. */
	private boolean bankForbidden = true;
	/** True once the user went through this quest in the wizard. */
	private boolean reviewed;
	/** Show the supplies section of the purchase dialog when this quest starts; off after Do not ask again. */
	private boolean askSupplies = true;
	/** Requirement-plan step instead of a quest: "TRAIN" or "BOOST"; null for a quest. */
	private String stepKind;
	private String stepSkill = "";
	private int stepLevel;
	/** The quest this step was added for. */
	private String stepFor = "";

	public boolean isStep()
	{
		return stepKind != null && !stepKind.isEmpty();
	}

	/** A queue entry that trains or boosts a skill ahead of {@code forQuest}. */
	public static QuestProfile step(String kind, String skill, int level, String forQuest)
	{
		QuestProfile profile = new QuestProfile();
		profile.stepKind = kind;
		profile.stepSkill = skill;
		profile.stepLevel = level;
		profile.stepFor = forQuest;
		profile.questName = ("TRAIN".equals(kind) ? "Train " : "Boost ") + skill.charAt(0)
			+ skill.substring(1).toLowerCase(java.util.Locale.ROOT) + " to " + level;
		profile.reviewed = true;
		profile.askSupplies = false;
		return profile;
	}

	/** One thing a supply wants bought: {@code ids} is empty when only the name is known. */
	public static final class SupplyLine
	{
		public final String key;
		public final String name;
		public final List<Integer> ids;
		public final int qty;

		public SupplyLine(String key, String name, List<Integer> ids, int qty)
		{
			this.key = key;
			this.name = name;
			this.ids = ids;
			this.qty = qty;
		}
	}

	/** The quest's own supplies; each supply can opt out of Grand Exchange buying. */
	@Data
	public static class Supplies
	{
		/** True once the user edited the supplies; a Combat quest then overrides its loadout supplies with these. */
		private boolean customised;
		private boolean staminaOn = true;
		private int staminaQty = 2;
		private int drinkStaminaAtEnergy = 30;
		private boolean energyFallback;
		private int energyQty = 2;
		private String food = "";
		private int foodQty;
		/** Eat below this HP percent; 0 keeps the global setting. */
		private int eatAtPercent;
		/** Potion count by {@link QuestPotions.Kind#key()}. */
		private Map<String, Integer> potions = new LinkedHashMap<>();
		/** Teleports, runes and other things to bring. */
		private List<QuestCombatLoadout.InventoryItem> items = new ArrayList<>();
		/** Supply keys that must not be bought from the Grand Exchange. */
		private Set<String> noBuy = new LinkedHashSet<>();

		public boolean buys(String key)
		{
			return noBuy == null || !noBuy.contains(key);
		}

		public void setBuys(String key, boolean buy)
		{
			if (noBuy == null)
			{
				noBuy = new LinkedHashSet<>();
			}
			if (buy)
			{
				noBuy.remove(key);
			}
			else
			{
				noBuy.add(key);
			}
		}

		public int potionQty(QuestPotions.Kind kind)
		{
			Integer qty = potions == null ? null : potions.get(kind.key());
			return qty == null ? 0 : qty;
		}

		public void setPotionQty(QuestPotions.Kind kind, int qty)
		{
			if (potions == null)
			{
				potions = new LinkedHashMap<>();
			}
			if (qty <= 0)
			{
				potions.remove(kind.key());
			}
			else
			{
				potions.put(kind.key(), Math.min(27, qty));
			}
		}

		/** How many stamina potions to bring: the supply count and the keep-at-least minimum, whichever is more. */
		public int staminaToBring(int effectiveMin)
		{
			return Math.max(staminaOn ? Math.max(0, staminaQty) : 0, effectiveMin);
		}

		/** Writes these supplies into the loadout (stamina, energy fallback, food, potions, items). */
		public void applyTo(QuestCombatLoadout loadout, int effectiveMin)
		{
			int stamina = staminaToBring(effectiveMin);
			loadout.setUseStamina(stamina > 0);
			loadout.setStaminaType(QuestCombatLoadout.StaminaType.STAMINA);
			loadout.setStaminaQty(stamina);
			loadout.setDrinkStaminaAtEnergy(drinkStaminaAtEnergy);
			String foodName = food == null ? "" : food.trim();
			loadout.setFood(foodQty > 0 ? foodName : "");
			loadout.setFoodQty(foodName.isEmpty() ? 0 : foodQty);
			if (eatAtPercent > 0)
			{
				loadout.setEatAtPercent(eatAtPercent);
			}
			List<QuestPotions.Row> rows = new ArrayList<>();
			for (QuestPotions.Row row : loadout.potionRows())
			{
				QuestPotions.Kind kind = row.kind();
				if (kind != null && kind.role() != QuestPotions.Role.PRAYER && kind.role() != QuestPotions.Role.BOOST
					&& kind.role() != QuestPotions.Role.ENERGY)
				{
					rows.add(row); // antifire and antipoison stay as the quest setup had them
				}
			}
			for (QuestPotions.Kind kind : POTION_KINDS)
			{
				if (potionQty(kind) > 0)
				{
					rows.add(new QuestPotions.Row(kind.key(), potionQty(kind)));
				}
			}
			if (energyFallback && energyQty > 0)
			{
				rows.add(new QuestPotions.Row(QuestPotions.Kind.ENERGY.key(), energyQty));
			}
			loadout.setPotions(rows);
			List<QuestCombatLoadout.InventoryItem> bring = new ArrayList<>();
			if (items != null)
			{
				for (QuestCombatLoadout.InventoryItem item : items)
				{
					bring.add(new QuestCombatLoadout.InventoryItem(item.getName(), item.getId(), item.getQty(),
						item.isStackable()));
				}
			}
			loadout.setInventoryItems(bring);
		}

		/** Lower-case names of the supplies switched off for the Grand Exchange (no dose suffix). */
		public Set<String> noBuyNames()
		{
			Set<String> out = new LinkedHashSet<>();
			if (noBuy == null)
			{
				return out;
			}
			for (String key : noBuy)
			{
				String name = null;
				if (key.equals("stamina"))
				{
					name = QuestPotions.Kind.STAMINA.itemName();
				}
				else if (key.equals("energy"))
				{
					name = QuestPotions.Kind.ENERGY.itemName();
				}
				else if (key.equals("food"))
				{
					name = food;
				}
				else if (key.startsWith("potion:"))
				{
					QuestPotions.Kind kind = QuestPotions.kindOf(key.substring(7));
					name = kind == null ? null : kind.itemName();
				}
				else if (key.startsWith("item:"))
				{
					name = key.substring(5);
				}
				if (name != null && !name.trim().isEmpty())
				{
					out.add(name.trim().toLowerCase(java.util.Locale.ROOT));
				}
			}
			return out;
		}

		/** Supplies that may be bought from the Grand Exchange, ready for the combined preflight. */
		public List<SupplyLine> buyable(int effectiveMin)
		{
			List<SupplyLine> out = new ArrayList<>();
			int stamina = staminaToBring(effectiveMin);
			if (stamina > 0 && buys("stamina"))
			{
				out.add(new SupplyLine("stamina", QuestPotions.Kind.STAMINA.itemName(),
					QuestPotions.Kind.STAMINA.doseIds(), stamina));
			}
			if (energyFallback && energyQty > 0 && buys("energy"))
			{
				out.add(new SupplyLine("energy", QuestPotions.Kind.ENERGY.itemName(),
					QuestPotions.Kind.ENERGY.doseIds(), energyQty));
			}
			if (food != null && !food.trim().isEmpty() && foodQty > 0 && buys("food"))
			{
				out.add(new SupplyLine("food", food.trim(), new ArrayList<>(), foodQty));
			}
			for (QuestPotions.Kind kind : POTION_KINDS)
			{
				if (potionQty(kind) > 0 && buys("potion:" + kind.key()))
				{
					out.add(new SupplyLine("potion:" + kind.key(), kind.itemName(), kind.doseIds(), potionQty(kind)));
				}
			}
			if (items != null)
			{
				for (QuestCombatLoadout.InventoryItem item : items)
				{
					String name = item.getName() == null ? "" : item.getName().trim();
					if (!name.isEmpty() && item.getQty() > 0 && buys("item:" + name))
					{
						List<Integer> ids = new ArrayList<>();
						if (item.getId() > 0)
						{
							ids.add(item.getId());
						}
						out.add(new SupplyLine("item:" + name, name, ids, item.getQty()));
					}
				}
			}
			return out;
		}
	}

	public QuestProfile()
	{
	}

	/** The profile a quest starts with: Combat when it has fights, Travel otherwise. */
	public static QuestProfile defaultFor(String questName, boolean hasCombat)
	{
		return defaultFor(questName, hasCombat, DEFAULT_COMBAT_FOOD_QTY);
	}

	/** As {@link #defaultFor(String, boolean)} with the food count the quest's fights call for (0 for none). */
	public static QuestProfile defaultFor(String questName, boolean hasCombat, int foodQty)
	{
		QuestProfile profile = new QuestProfile();
		profile.questName = questName == null ? "" : questName;
		profile.type = hasCombat ? Type.COMBAT : Type.TRAVEL;
		profile.supplies = defaultSupplies(profile.type, foodQty);
		return profile;
	}

	/** Travel quests bring stamina potions only; combat quests also bring food (the armour comes from the setup). */
	public static Supplies defaultSupplies(Type type)
	{
		return defaultSupplies(type, DEFAULT_COMBAT_FOOD_QTY);
	}

	public static Supplies defaultSupplies(Type type, int foodQty)
	{
		Supplies supplies = new Supplies();
		if (type == Type.COMBAT && foodQty > 0)
		{
			supplies.setFood("Lobster");
			supplies.setFoodQty(foodQty);
		}
		return supplies;
	}

	public boolean geAllowed()
	{
		return obtain != Obtain.BANK_ONLY;
	}

	/** The stamina minimum in force: this quest's own value, else the queue's default. */
	public int effectiveMinStamina(int globalDefault)
	{
		int value = minStamina >= 0 ? minStamina : globalDefault;
		return Math.max(0, Math.min(MAX_MIN_STAMINA, value));
	}

	/** Deep copy. */
	public QuestProfile copy()
	{
		return GSON.fromJson(GSON.toJson(this), QuestProfile.class);
	}

	/** Takes every setting of {@code other} except the quest name. */
	public void copySettingsFrom(QuestProfile other)
	{
		if (other == null)
		{
			return;
		}
		QuestProfile clone = other.copy();
		obtain = clone.obtain;
		type = clone.type;
		travelGear = clone.travelGear;
		supplies = clone.supplies;
		minStamina = clone.minStamina;
		bankForbidden = clone.bankForbidden;
	}

	/** The loadout a Travel quest runs with: no combat supplies, its gear and its own supplies. */
	public QuestCombatLoadout travelLoadout(int globalMinStamina)
	{
		QuestCombatLoadout loadout = QuestCombatLoadout.bare();
		loadout.setSkip(false);
		loadout.setGearItems(new ArrayList<>(travelGear == null ? new ArrayList<>() : travelGear));
		supplies.applyTo(loadout, effectiveMinStamina(globalMinStamina));
		return loadout.normalised();
	}

	/**
	 * The loadout to store for this quest. Travel builds one; Combat keeps the saved quest loadout (or the last used
	 * one) and only overlays the supplies when the user edited them here.
	 */
	public QuestCombatLoadout loadoutFor(QuestCombatLoadout stored, QuestCombatLoadout global, int globalMinStamina)
	{
		if (type == Type.TRAVEL)
		{
			return travelLoadout(globalMinStamina);
		}
		QuestCombatLoadout base = stored != null ? stored : global;
		QuestCombatLoadout copy = base == null ? null : QuestCombatLoadout.fromJson(base.toJson());
		if (copy == null)
		{
			copy = QuestCombatLoadout.bare();
		}
		if (stored == null)
		{
			copy.setFights(new LinkedHashMap<>());
		}
		copy.setSkip(false);
		int min = effectiveMinStamina(globalMinStamina);
		if (supplies.isCustomised())
		{
			supplies.applyTo(copy, min);
		}
		else if (min > 0 && (!copy.isUseStamina() || copy.getStaminaQty() < min))
		{
			copy.setUseStamina(true);
			copy.setStaminaType(QuestCombatLoadout.StaminaType.STAMINA);
			copy.setStaminaQty(min);
		}
		return copy.normalised();
	}

	public String toJson()
	{
		return GSON.toJson(this);
	}

	public static QuestProfile fromJson(String json)
	{
		try
		{
			QuestProfile profile = GSON.fromJson(json, QuestProfile.class);
			return profile == null ? null : profile.normalised();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Fills nulls so a hand-edited or partial JSON cannot break the queue. */
	public QuestProfile normalised()
	{
		questName = questName == null ? "" : questName;
		obtain = obtain == null ? Obtain.GRAND_EXCHANGE : obtain;
		type = type == null ? Type.TRAVEL : type;
		travelGear = travelGear == null ? new ArrayList<>() : travelGear;
		supplies = supplies == null ? new Supplies() : supplies;
		if (supplies.potions == null)
		{
			supplies.potions = new LinkedHashMap<>();
		}
		if (supplies.items == null)
		{
			supplies.items = new ArrayList<>();
		}
		if (supplies.noBuy == null)
		{
			supplies.noBuy = new LinkedHashSet<>();
		}
		minStamina = minStamina < 0 ? -1 : Math.min(MAX_MIN_STAMINA, minStamina);
		return this;
	}
}
