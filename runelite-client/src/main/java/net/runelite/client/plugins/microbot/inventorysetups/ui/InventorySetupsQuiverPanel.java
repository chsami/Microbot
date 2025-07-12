package net.runelite.client.plugins.microbot.inventorysetups.ui;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsSlotID;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

import javax.swing.JPopupMenu;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class InventorySetupsQuiverPanel
{
	// Shows up when a quiver is equipped or in inventory
	@Getter
	private InventorySetupsSlot quiverSlot;

	@Getter
	private final int QUIVER_SLOT_IDX = 0;

	private JPopupMenu quiverSlotRightClickMenu;
	private final JPopupMenu emptyJPopMenu = new JPopupMenu();
	private final MInventorySetupsPlugin plugin;
	private final ItemManager itemManager;

	public static final List<Integer> DIZANA_QUIVER_IDS = Arrays.asList(ItemID.DIZANAS_QUIVER_CHARGED,
			ItemID.DIZANAS_QUIVER_CHARGED_TROUVER,
			ItemID.DIZANAS_QUIVER_UNCHARGED,
			ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER,
			ItemID.SKILLCAPE_MAX_DIZANAS,
			ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER,
			ItemID.DIZANAS_QUIVER_INFINITE,
			ItemID.DIZANAS_QUIVER_INFINITE_TROUVER);

	public static final Set<Integer> DIZANA_QUIVER_IDS_SET = new HashSet<>(DIZANA_QUIVER_IDS);


	InventorySetupsQuiverPanel(final ItemManager itemManager, final MInventorySetupsPlugin plugin)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		quiverSlot = new InventorySetupsSlot(ColorScheme.DARKER_GRAY_COLOR, InventorySetupsSlotID.QUIVER, QUIVER_SLOT_IDX);
		InventorySetupsSlot.addFuzzyMouseListenerToSlot(plugin, quiverSlot);
		InventorySetupsSlot.addStackMouseListenerToSlot(plugin, quiverSlot);
		InventorySetupsSlot.addUpdateFromContainerMouseListenerToSlot(plugin, quiverSlot);
		InventorySetupsSlot.addUpdateFromSearchMouseListenerToSlot(plugin, quiverSlot, true);
		InventorySetupsSlot.addRemoveMouseListenerToSlot(plugin, quiverSlot);
		this.quiverSlotRightClickMenu = quiverSlot.getRightClickMenu();
		quiverSlot.setComponentPopupMenu(new JPopupMenu());
	}

	public void handleQuiverHighlighting(final InventorySetup setup, boolean doesCurrentInventoryHaveQuiver)
	{
		this.quiverSlot.setParentSetup(setup);
		// This must be run on the client thread!
		if (setup.getQuiver() != null)
		{
			InventorySetupsSlot.setSlotImageAndText(itemManager, quiverSlot, setup, setup.getQuiver().get(0));
			quiverSlot.setComponentPopupMenu(quiverSlotRightClickMenu);

			if (!setup.isHighlightDifference() || !plugin.isHighlightingAllowed())
			{
				quiverSlot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
			else if (doesCurrentInventoryHaveQuiver)
			{
				List<InventorySetupsItem> currentQuiverDataInInvEqp = plugin.getAmmoHandler().getQuiverData();
				final int indexInSlot = quiverSlot.getIndexInSlot();
				InventorySetupsSlot.highlightSlot(setup, setup.getQuiver().get(indexInSlot), currentQuiverDataInInvEqp.get(indexInSlot), quiverSlot);
			}
			else
			{
				quiverSlot.setBackground(setup.getHighlightColor());
			}
		}
		else
		{
			InventorySetupsSlot.setSlotImageAndText(itemManager, quiverSlot, setup, InventorySetupsItem.getDummyItem());
			quiverSlot.setBackground(ColorScheme.DARK_GRAY_COLOR);
			quiverSlot.setComponentPopupMenu(emptyJPopMenu);
		}
	}


}
