package net.runelite.client.plugins.microbot.agility;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(

	name = PluginDescriptor.Mocrosoft + "Agility",
	description = "Microbot agility plugin",
	tags = {"agility", "microbot"},
	enabledByDefault = false
)
@Slf4j
public class MicroAgilityPlugin extends Plugin
{
	@Inject
	private MicroAgilityConfig config;

	@Provides
	MicroAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MicroAgilityConfig.class);
	}

	@Inject
	private OverlayManager overlayManager;
	@Inject
	private MicroAgilityOverlay agilityOverlay;

	@Inject
	private AgilityScript agilityScript;


	@Override
	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(agilityOverlay);
		}

		agilityScript.run();
		agilityScript.handleAlch();
	}

	protected void shutDown()
	{
		overlayManager.remove(agilityOverlay);
		agilityScript.shutdown();
	}

	public AgilityCourseHandler getCourseHandler()
	{
		return config.agilityCourse().getHandler();
	}

	public List<Rs2ItemModel> getInventoryFood()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> !(i.getName().toLowerCase().contains("summer pie"))).collect(Collectors.toList());
	}

	public List<Rs2ItemModel> getSummerPies()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> i.getName().toLowerCase().contains("summer pie")).collect(Collectors.toList());
	}
}
