package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;

/**
 * Turns the supplies the player ticked in the quest setup (stamina, food, custom items) into preflight needs, so the
 * bank is checked first and only the shortfall is bought. Potion rows (prayer, boosts, ...) are added elsewhere.
 * Only what the user selected is ever returned; nothing is added on the quest's or the code's own initiative.
 */
public final class QuestSupplyNeeds
{
	private QuestSupplyNeeds()
	{
	}

	/**
	 * @param resolveId item id for an exact item name, or <= 0 when unknown (that supply is then not bought)
	 * @param tradable whether an item id can be bought on the Grand Exchange
	 */
	public static List<QuestPreflight.Need> fromLoadout(QuestCombatLoadout loadout,
		ToIntFunction<String> resolveId, IntPredicate tradable)
	{
		List<QuestPreflight.Need> out = new ArrayList<>();
		if (loadout == null || loadout.isSkip())
		{
			return out;
		}
		if (loadout.wantsLegacyStamina())
		{
			QuestPotions.Kind kind = loadout.getStaminaType() == QuestCombatLoadout.StaminaType.ENERGY
				? QuestPotions.Kind.ENERGY : QuestPotions.Kind.STAMINA;
			out.add(QuestPreflight.supplyNeed(kind.itemName(), kind.doseIds(), loadout.getStaminaQty(), true));
		}
		if (loadout.wantsFood())
		{
			int id = resolveId.applyAsInt(loadout.getFood());
			if (id > 0 && tradable.test(id))
			{
				out.add(QuestPreflight.supplyNeed(loadout.getFood(), Collections.singletonList(id),
					loadout.getFoodQty(), true));
			}
		}
		for (QuestCombatLoadout.InventoryItem item : loadout.getInventoryItems())
		{
			if (item == null || item.getQty() <= 0)
			{
				continue;
			}
			int id = item.getId() > 0 ? item.getId() : resolveId.applyAsInt(item.getName());
			if (id > 0 && tradable.test(id))
			{
				String name = item.getName().isEmpty() ? "item " + id : item.getName();
				out.add(QuestPreflight.supplyNeed(name, Collections.singletonList(id), item.getQty(), true));
			}
		}
		return out;
	}
}
