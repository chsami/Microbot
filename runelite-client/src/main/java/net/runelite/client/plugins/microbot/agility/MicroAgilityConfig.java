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
	String summerPieThreshold = "summerPieThreshold";

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
		keyName = summerPieThreshold,
		name = "Summer Pie Boost",
		description = "Use Summer pies to boost agility level at start, set it to 0 to disable, set it to 5 to eat pie when you loose 1 level",
		position = 3,
		section = generalSection
	)
	default int pieThreshold()
	{
		return 5;
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
}