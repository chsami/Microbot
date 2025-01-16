package net.runelite.client.plugins.microbot.magic.aiomagic;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.MagicActivity;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.SuperHeatItem;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.TeleportSpell;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;

@ConfigGroup(AIOMagicConfig.configGroup)
public interface AIOMagicConfig extends Config {
	String configGroup = "aio-magic";
	String activity = "magicActivity";
	String combatSpell = "magicCombatSpell";
	String alchItems = "alchItems";
	String superHeatItem = "superHeatItem";
	String npcName = "npcName";
	String staff = "staff";
	String teleportSpell = "teleportSpell";

	@ConfigSection(
			name = "General Settings",
			description = "Configure general plugin configuration & preferences",
			position = 0
	)
	String generalSection = "general";

	@ConfigSection(
			name = "Splashing Settings",
			description = "Configure splashing settings",
			position = 1
	)
	String splashSection = "splash";

	@ConfigSection(
			name = "Alch Settings",
			description = "Configure Alching settings",
			position = 2
	)
	String alchSection = "alch";

	@ConfigSection(
			name = "SuperHeat Settings",
			description = "Configure SuperHeat settings",
			position = 2
	)
	String superHeatSection = "superHeat";

	@ConfigSection(
			name = "Teleport Settings",
			description = "Configure teleport settings",
			position = 3
	)
	String teleportSection = "teleport";

	@ConfigItem(
			keyName = activity,
			name = "Activity",
			description = "Select the activity you would like to perform",
			position = 0,
			section = generalSection
	)
	default MagicActivity magicActivity() {
		return MagicActivity.SPLASHING;
	}

	@ConfigItem(
			keyName = npcName,
			name = "NPC Name",
			description = "Name of the NPC you would like to splash",
			position = 0,
			section = splashSection
	)
	default String npcName() {
		return "";
	}

	@ConfigItem(
			keyName = combatSpell,
			name = "Combat Spell",
			description = "Select the spell you would like to splash with",
			position = 1,
			section = splashSection
	)
	default Rs2CombatSpells combatSpell() {
		return Rs2CombatSpells.WIND_STRIKE;
	}

	@ConfigItem(
			keyName = alchItems,
			name = "Alch Items",
			description = "List of items you would like to alch",
			position = 0,
			section = alchSection
	)
	default String alchItems() {
		return "";
	}

	@ConfigItem(
			keyName = superHeatItem,
			name = "SuperHeat Items",
			description = "List of items you would like to superheat",
			position = 0,
			section = superHeatSection
	)
	default SuperHeatItem superHeatItem() {
		return SuperHeatItem.IRON;
	}

	@ConfigItem(
			keyName = teleportSpell,
			name = "Teleport Spell",
			description = "Select the teleport spell you would like to use",
			position = 0,
			section = teleportSection
	)
	default TeleportSpell teleportSpell() {
		return TeleportSpell.VARROCK_TELEPORT;
	}

	@ConfigItem(
			keyName = staff,
			name = "Staff",
			description = "Select the staff you would like to use",
			position = 1,
			section = teleportSection
	)
	default Rs2Staff staff() {
		return Rs2Staff.STAFF_OF_AIR;
	}
}
