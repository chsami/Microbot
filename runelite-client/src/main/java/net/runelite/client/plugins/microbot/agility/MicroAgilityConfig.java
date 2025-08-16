package net.runelite.client.plugins.microbot.agility;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;

@ConfigGroup("MicroAgility")
@ConfigInformation("Enable the plugin near the start of your selected agility course. <br />" +
	"<b>Course requirements:</b>" +
	"<ul>" +
	"<li> Ape Atoll - Kruk or Ninja greegree equipped. Stamina pots recommended. </li>" +
	"<li>Shayzien Advanced - Crossbow and Mith Grapple equipped.</li>" +
	"</ul>")
public interface MicroAgilityConfig extends Config
{

	String selectedCourse = "course";
	String hitpointsThreshold = "hitpointsThreshold";
	String shouldAlch = "shouldAlch";
	String itemsToAlch = "itemsToAlch";

	@ConfigSection(
		name = "General",
		description = "General",
		position = 0,
		closedByDefault = false
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = selectedCourse,
		name = "Course",
		description = "Choose your agility course",
		position = 1,
		section = generalSection
	)
	default AgilityCourse agilityCourse()
	{
		return AgilityCourse.CANIFIS_ROOFTOP_COURSE;
	}

	@ConfigItem(
		keyName = hitpointsThreshold,
		name = "Eat at",
		description = "Use food below certain hitpoint percent. If there's no food in the inventory, the script stops. Set to 0 in order to disable.",
		position = 2,
		section = generalSection
	)
	default int hitpoints()
	{
		return 20;
	}

	@ConfigItem(
		keyName = shouldAlch,
		name = "Alch",
		description = "Use Low/High Alchemy while doing agility",
		position = 4,
		section = generalSection
	)
	default boolean alchemy()
	{
		return false;
	}

	@ConfigItem(
		keyName = itemsToAlch,
		name = "Items to Alch",
		description = "Enter items to alch, separated by commas (e.g., Rune sword, Dragon dagger, Mithril platebody)",
		position = 5,
		section = generalSection
	)
	default String itemsToAlch()
	{
		return "";
	}

	@ConfigItem(
		keyName = "efficientAlching",
		name = "Efficient Alching",
		description = "Click obstacle first, then alch, then click again (for obstacles 5+ tiles away)",
		position = 6,
		section = generalSection
	)
	default boolean efficientAlching()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alchSkipChance",
		name = "Alch Skip Chance",
		description = "Percentage chance to skip alching on any obstacle (0-100)",
		position = 7,
		section = generalSection
	)
	default int alchSkipChance()
	{
		return 5;
	}
}