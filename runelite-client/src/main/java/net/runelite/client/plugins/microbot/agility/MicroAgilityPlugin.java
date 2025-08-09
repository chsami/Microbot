package net.runelite.client.plugins.microbot.agility;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(

	name = PluginDescriptor.Mocrosoft + "Agility",
	description = "Microbot agility plugin",
	tags = {"agility", "microbot"},
	enabledByDefault = false
)
@Slf4j
public class MicroAgilityPlugin extends Plugin implements SchedulablePlugin
{
	@Inject
	private MicroAgilityConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private MicroAgilityOverlay agilityOverlay;
	@Inject
	private AgilityScript agilityScript;

	private LockCondition lockCondition;
	private LogicalCondition stopCondition = null;

	@Provides
	MicroAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MicroAgilityConfig.class);
	}

	@Override
	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(agilityOverlay);
		}

		agilityScript.run();
	}

	protected void shutDown()
	{
		overlayManager.remove(agilityOverlay);
		agilityScript.shutdown();
	}

	@Subscribe
	public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
		try{
			if (event.getPlugin() == this) {
				Microbot.stopPlugin(this);
			}
		} catch (Exception e) {
			log.error("Error stopping plugin: ", e);
		}
	}

	@Override
	public LogicalCondition getStopCondition() {
		if (this.stopCondition == null) {
			this.lockCondition = new LockCondition("Agility in critical operation");
			AndCondition andCondition = new AndCondition();
			andCondition.addCondition(lockCondition);
			this.stopCondition = andCondition;
		}
		return this.stopCondition;
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

	public boolean hasRequiredLevel()
	{
		if (getSummerPies().isEmpty() || !getCourseHandler().canBeBoosted())
		{
			return Rs2Player.getRealSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
		}

		return Rs2Player.getBoostedSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
	}
}
