package net.runelite.client.plugins.microbot.questhelper.helpers.quests.priestinperil;

import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;

import java.util.Collections;

public class BringDrezelPureEssenceStep extends NpcStep
{
	ItemRequirement essence = new ItemRequirement("Rune/Pure essence",ItemID.BLANKRUNE, 50);

	public BringDrezelPureEssenceStep(QuestHelper questHelper)
	{
		super(questHelper, NpcID.PRIESTPERILTRAPPEDMONK_VIS, new WorldPoint(3439, 9896, 0), "Bring Drezel 50 UNNOTED rune/pure essence in the underground of the Salve Temple to finish the quest!");
		essence.addAlternates(ItemID.BLANKRUNE_HIGH);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateSteps();
	}

	protected void updateSteps()
	{
		int stillNeeded = 60 - client.getVarpValue(302);
		int[] held = essenceInInventory();
		// Whatever essence is carried gets handed in, even if it is less than Drezel still wants.
		// With none carried the full amount is asked for and the automation banks as much as fits per trip.
		int numEssence = held[0] > 0 ? Math.min(held[0], stillNeeded) : stillNeeded;
		essence.setQuantity(numEssence);
		this.setRequirements(Collections.singletonList(essence));
		this.setText("Bring Drezel " + stillNeeded + " UNNOTED rune/pure essence in the underground of the Salve Temple.");
	}

	/** Returns {count, itemId of the essence type held} for unnoted essence in the inventory. */
	private int[] essenceInInventory()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return new int[]{0, -1};
		}
		int count = 0;
		int itemId = -1;
		for (int id : essence.getAllIds())
		{
			int amount = inventory.count(id);
			if (amount > 0)
			{
				count += amount;
				if (itemId == -1)
				{
					itemId = id;
				}
			}
		}
		return new int[]{count, itemId};
	}
}
