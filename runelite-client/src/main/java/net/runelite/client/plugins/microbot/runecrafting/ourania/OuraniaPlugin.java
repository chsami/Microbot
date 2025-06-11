package net.runelite.client.plugins.microbot.runecrafting.ourania;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.AWTException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = PluginDescriptor.GMason + "Ourania Altar",
	description = "Microbot Ourania Altar plugin",
	tags = {"runecrafting", "microbot", "skilling"},
	enabledByDefault = false
)
public class OuraniaPlugin extends Plugin
{

	public static String version = "1.4.0";
	@Inject
	private OuraniaConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private OuraniaOverlay ouraniaOverlay;
	@Inject
	private OuraniaScript ouraniaScript;
	@Getter
	private boolean ranOutOfAutoPay = false;
	@Getter
	private int profit;
	@Getter
	private final WorldArea ouraniaAltarArea = new WorldArea(new WorldPoint(3054, 5574, 0), 12, 12);
	@Getter
	private List<Rs2ItemModel> runesCrafted = new ArrayList<>();
	private List<Rs2ItemModel> previousInventoryRunes = new ArrayList<>();

	@Provides
	OuraniaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OuraniaConfig.class);
	}

	@Override
	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(ouraniaOverlay);
		}

		ouraniaScript.run();
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(ouraniaOverlay);
		ouraniaScript.shutdown();
		ranOutOfAutoPay = false;
		runesCrafted.clear();
		previousInventoryRunes.clear();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (event.getMessage().toLowerCase().contains("you could not afford to pay with your quick payment"))
		{
			ranOutOfAutoPay = true;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(OuraniaConfig.configGroup))
		{
			return;
		}

		if (event.getKey().equals(OuraniaConfig.toggleOverlay))
		{
			toggleOverlay(config.toggleOverlay());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		if (!getOuraniaAltarArea().contains(Microbot.getClient().getLocalPlayer().getWorldLocation()))
		{
			previousInventoryRunes.clear();
			return;
		}

		final ItemContainer itemContainer = event.getItemContainer();
		if (itemContainer == null)
		{
			return;
		}

		List<Rs2ItemModel> currentRunes = new ArrayList<>();

		for (int i = 0; i < itemContainer.getItems().length; i++)
		{
			Item item = itemContainer.getItems()[i];
			if (item.getId() == -1 || Runes.byItemId(item.getId()) == null)
			{
				continue;
			}

			ItemComposition itemComposition = Microbot.getClient().getItemDefinition(item.getId());
			currentRunes.add(new Rs2ItemModel(item, itemComposition, i));
		}

		for (Rs2ItemModel current : currentRunes)
		{
			Rs2ItemModel previous = previousInventoryRunes.stream()
				.filter(p -> p.getId() == current.getId())
				.findFirst()
				.orElse(null);

			int previousQty = previous != null ? previous.getQuantity() : 0;
			int gain = current.getQuantity() - previousQty;

			if (gain > 0)
			{
				Rs2ItemModel existing = runesCrafted.stream()
					.filter(r -> r.getId() == current.getId())
					.findFirst()
					.orElse(null);

				if (existing != null)
				{
					existing.setQuantity(existing.getQuantity() + gain);
				}
				else
				{
					runesCrafted.add(current);
				}
			}
		}

		// Save snapshot for next comparison
		previousInventoryRunes = currentRunes;
	}

	public boolean isBreakHandlerEnabled()
	{
		return Microbot.isPluginEnabled(BreakHandlerPlugin.class);
	}

	public void calcuateProfit()
	{
		int teleportCost = Rs2GrandExchange.getPrice(ItemID.LAW_RUNE) + (Rs2GrandExchange.getPrice(ItemID.ASTRAL_RUNE) * 2);
		int runesCrafted = Rs2Inventory.items().stream()
			.filter(rs2Item -> rs2Item.getName().toLowerCase().contains("rune") && !rs2Item.getName().toLowerCase().contains("rune pouch"))
			.mapToInt(rs2Item -> Rs2GrandExchange.getPrice(rs2Item.getId()))
			.sum();
		int profitFromRun = teleportCost - runesCrafted;

		profit += profitFromRun;
	}

	private void toggleOverlay(boolean hideOverlay)
	{
		if (overlayManager != null)
		{
			boolean hasOverlay = overlayManager.anyMatch(ov -> ov.getName().equalsIgnoreCase(OuraniaOverlay.class.getSimpleName()));

			if (hideOverlay)
			{
				if (!hasOverlay)
				{
					return;
				}

				overlayManager.remove(ouraniaOverlay);
			}
			else
			{
				if (hasOverlay)
				{
					return;
				}

				overlayManager.add(ouraniaOverlay);
			}
		}
	}

	public Duration getStartTime()
	{
		return ouraniaScript.getRunTime();
	}
}
