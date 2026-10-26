package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(DDChopperConfig.GROUP)
public interface DDChopperConfig extends Config
{
	String GROUP = "ddchopper";

	@ConfigSection(name = "Axe", description = "Which axe to use and whether to buy one.", position = 0)
	String axeSection = "axeSection";

	@ConfigSection(name = "Trees & spots", description = "Which tree to cut and where.", position = 1)
	String treeSection = "treeSection";

	@ConfigSection(name = "Mode & goal", description = "What to do with logs and when to stop.", position = 2)
	String modeSection = "modeSection";

	@ConfigSection(name = "Breaks", description = "Random breaks through the Break Handler.", position = 3, closedByDefault = true)
	String breakSection = "breakSection";

	@ConfigSection(name = "World hopping", description = "Hop away from crowded spots.", position = 4, closedByDefault = true)
	String hopSection = "hopSection";

	@ConfigSection(name = "Display", description = "What is drawn in the game.", position = 6)
	String displaySection = "displaySection";

	@ConfigSection(name = "Forestry", description = "Forestry random events while chopping.", position = 7, closedByDefault = true)
	String forestrySection = "forestrySection";

	@ConfigSection(name = "Humanizer", description = "Human-like delays, fatigue and idle pauses.", position = 5)
	String humanSection = "humanSection";

	@ConfigItem(keyName = "axe", name = "Axe", position = 0, section = axeSection,
		description = "Auto uses the best axe you own that your Woodcutting level allows, or pick one specific axe.")
	default AxeChoice axe()
	{
		return AxeChoice.AUTO;
	}

	@ConfigItem(keyName = "buyAxe", name = "Buy axe", position = 1, section = axeSection,
		description = "Buy the best affordable usable axe at the Grand Exchange when none is owned, and buy upgrades as levels unlock them.")
	default boolean buyAxe()
	{
		return true;
	}

	// ---- Goal ----
	@ConfigItem(keyName = "goalType", name = "Goal", position = 2, section = modeSection,
		description = "Stop at a target level, after a run time, or never.")
	default GoalType goalType()
	{
		return GoalType.TARGET_LEVEL;
	}

	@Range(min = 2, max = 99)
	@ConfigItem(keyName = "targetLevel", name = "Target lvl", position = 3, section = modeSection,
		description = "Stop when Woodcutting reaches this level (goal type: Target level).")
	default int targetLevel()
	{
		return 30;
	}

	@Range(min = 0, max = 99)
	@ConfigItem(keyName = "runHours", name = "Run hours", position = 4, section = modeSection,
		description = "Hours of active play before stopping (goal type: Run time). Breaks do not count.")
	default int runHours()
	{
		return 1;
	}

	@Range(min = 0, max = 59)
	@ConfigItem(keyName = "runMinutes", name = "Run mins", position = 5, section = modeSection,
		description = "Minutes added to the run time hours.")
	default int runMinutes()
	{
		return 0;
	}

	// ---- What and where ----
	@ConfigItem(keyName = "mode", name = "Mode", position = 6, section = modeSection,
		description = "Power-chop drops the logs (fastest XP). Bank logs walks to the nearest bank when full.")
	default ChopMode mode()
	{
		return ChopMode.POWER_CHOP;
	}

	@ConfigItem(keyName = "tree", name = "Tree", position = 7, section = treeSection,
		description = "Auto picks the best XP/hour tree for your level and axe and moves up as you level.")
	default TreeChoice tree()
	{
		return TreeChoice.AUTO;
	}

	@ConfigItem(keyName = "spot", name = "Spot", position = 8, section = treeSection,
		description = "Auto picks the optimal spot for the tree (names read like Draynor willows, Edgeville yews; the full list with locations is in the DD Chopper log). A specific spot is used when it suits the tree and is usable, otherwise Auto.")
	default Spot spot()
	{
		return Spot.AUTO;
	}

	@ConfigItem(keyName = "rotateSpots", name = "Rotate", position = 9, section = treeSection,
		description = "Switch between the eligible spots of the current tree to vary where you chop.")
	default boolean rotateSpots()
	{
		return false;
	}

	@ConfigItem(keyName = "rotateSpotNames", name = "Rotate list", position = 10, section = treeSection,
		description = "Comma separated spot names (as shown in the Spot dropdown, or the enum name such as EDGEVILLE_YEWS). Only spots that suit the current tree are used. Empty rotates through every eligible spot of the tree.")
	default String rotateSpotNames()
	{
		return "";
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "rotateMinMinutes", name = "Spot min (m)", position = 10, section = treeSection,
		description = "Shortest stay at each spot, in minutes of active play. Set min and max equal for a fixed time (90 = an hour and a half).")
	default int rotateMinMinutes()
	{
		return 60;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "rotateMaxMinutes", name = "Spot max (m)", position = 10, section = treeSection,
		description = "Longest stay at each spot, in minutes of active play.")
	default int rotateMaxMinutes()
	{
		return 90;
	}

	@ConfigItem(keyName = "members", name = "Members", position = 11, section = treeSection,
		description = "Allow members-only trees and spots.")
	default boolean members()
	{
		return false;
	}

	@ConfigItem(keyName = "allowRestricted", name = "Gated spots", position = 12, section = treeSection,
		description = "Allow spots that need a boat, a quest gate or a guild unlock. Off keeps the walker on foot only.")
	default boolean allowRestricted()
	{
		return false;
	}

	@ConfigItem(keyName = "pickupNests", name = "Bird nests", position = 13, section = treeSection,
		description = "Pick up bird nests that drop while chopping.")
	default boolean pickupNests()
	{
		return false;
	}

	// ---- Crowd hopping ----
	@ConfigItem(keyName = "hopWhenCrowded", name = "Hop if crowded", position = 14, section = hopSection,
		description = "Hop worlds when too many other players are at the spot.")
	default boolean hopWhenCrowded()
	{
		return true;
	}

	@Range(min = 0, max = 50)
	@ConfigItem(keyName = "maxOthers", name = "Max others", position = 15, section = hopSection,
		description = "Hop when more than this many other players stay near the spot for about 10 seconds.")
	default int maxOthers()
	{
		return 2;
	}

	@Range(min = 3, max = 30)
	@ConfigItem(keyName = "crowdRadius", name = "Crowd radius", position = 16, section = hopSection,
		description = "Tiles around you that count as the spot.")
	default int crowdRadius()
	{
		return 10;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(keyName = "maxHopsPerHour", name = "Max hops/hr", position = 17, section = hopSection,
		description = "Upper limit on crowd hops in any rolling hour.")
	default int maxHopsPerHour()
	{
		return 6;
	}

	@ConfigItem(keyName = "avoidWorlds", name = "Avoid worlds", position = 18, section = hopSection,
		description = "World numbers never to hop to, separated by commas. PvP, high risk, total level and other restricted worlds are always avoided.")
	default String avoidWorlds()
	{
		return "";
	}

	// ---- Breaks ----
	@ConfigItem(keyName = "enableBreaks", name = "Breaks", position = 19, section = breakSection,
		description = "Take random breaks using Microbot's Break Handler.")
	default boolean enableBreaks()
	{
		return true;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "minPlay", name = "Play min (m)", position = 20, section = breakSection,
		description = "Shortest play time between breaks, in minutes.")
	default int minPlay()
	{
		return 30;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "maxPlay", name = "Play max (m)", position = 21, section = breakSection,
		description = "Longest play time between breaks, in minutes.")
	default int maxPlay()
	{
		return 90;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "minBreak", name = "Break min (m)", position = 22, section = breakSection,
		description = "Shortest break, in minutes.")
	default int minBreak()
	{
		return 5;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "maxBreak", name = "Break max (m)", position = 23, section = breakSection,
		description = "Longest break, in minutes.")
	default int maxBreak()
	{
		return 20;
	}

	@ConfigItem(keyName = "logoutOnBreak", name = "Logout", position = 24, section = breakSection,
		description = "Log out during breaks. Off just stops clicking.")
	default boolean logoutOnBreak()
	{
		return false;
	}

	// ---- Human behaviour ----
	@ConfigItem(keyName = "humanizedTiming", name = "Humanized timing", position = 25, section = humanSection,
		description = "Wait a skewed random reaction delay (about 0.3 to 3 s) before clicking the next tree, and vary the pace of drops and mouse speed.")
	default boolean humanizedTiming()
	{
		return true;
	}

	@ConfigItem(keyName = "humanizedFatigue", name = "Fatigue", position = 26, section = humanSection,
		description = "Fatigue builds over continuous play and fades on breaks. More fatigue means slower, sloppier timing and more idle pauses.")
	default boolean humanizedFatigue()
	{
		return true;
	}

	@Range(min = 0, max = 50)
	@ConfigItem(keyName = "idlePauseChance", name = "Idle pause chance %", position = 27, section = humanSection,
		description = "Chance, each time a tree falls, of a 5 to 20 second pause doing nothing (like checking the inventory). Rises with fatigue.")
	default int idlePauseChance()
	{
		return 2;
	}

	// ---- Display ----
	@ConfigItem(keyName = "showOverlay", name = "Show overlay", position = 28, section = displaySection,
		description = "Show the in-game overlay with level, XP/hr, tree, axe and status.")
	default boolean showOverlay()
	{
		return true;
	}

	// ---- Forestry ----
	@ConfigItem(keyName = "forestryEnabled", name = "Forestry events", position = 29, section = forestrySection,
		description = "Master switch: handle Forestry random events while chopping. Each event has its own switch below.")
	default boolean forestryEnabled()
	{
		return false;
	}

	@ConfigItem(keyName = "forestryBusyWorld", name = "Busy world", position = 30, section = forestrySection,
		description = "Prefer a busy world: Forestry events are more frequent and easier with more players chopping nearby. Hops when fewer than Min players are at the spot, with the usual hop limits. Overrides Hop if crowded while Forestry events are on.")
	default boolean forestryBusyWorld()
	{
		return false;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(keyName = "forestryMinPlayers", name = "Min players", position = 31, section = forestrySection,
		description = "Players wanted at the spot when Busy world is on.")
	default int forestryMinPlayers()
	{
		return 8;
	}

	@ConfigItem(keyName = "forestryRoots", name = "Rising Roots", position = 32, section = forestrySection,
		description = "Chop the glowing root.")
	default boolean forestryRoots()
	{
		return true;
	}

	@ConfigItem(keyName = "forestrySapling", name = "Sapling", position = 33, section = forestrySection,
		description = "Struggling Sapling: add ingredients to the mulch.")
	default boolean forestrySapling()
	{
		return true;
	}

	@ConfigItem(keyName = "forestryEntlings", name = "Entlings", position = 34, section = forestrySection,
		description = "Friendly Entlings: prune to the request.")
	default boolean forestryEntlings()
	{
		return true;
	}

	@ConfigItem(keyName = "forestryBeehives", name = "Beehives", position = 35, section = forestrySection,
		description = "Build beehives with logs.")
	default boolean forestryBeehives()
	{
		return true;
	}

	@ConfigItem(keyName = "forestryPheasant", name = "Pheasant", position = 36, section = forestrySection,
		description = "Pheasant Control: return eggs.")
	default boolean forestryPheasant()
	{
		return true;
	}

	@ConfigItem(keyName = "forestryPoachers", name = "Poachers / Fox", position = 37, section = forestrySection,
		description = "Disarm traps.")
	default boolean forestryPoachers()
	{
		return true;
	}

	@ConfigItem(keyName = "forestryRitual", name = "Ritual", position = 38, section = forestrySection,
		description = "Enchantment Ritual: click the matching circle.")
	default boolean forestryRitual()
	{
		return true;
	}

	@ConfigItem(keyName = "forestryLeprechaun", name = "Leprechaun", position = 39, section = forestrySection,
		description = "Deposit logs with the Woodcutting Leprechaun.")
	default boolean forestryLeprechaun()
	{
		return true;
	}

	@ConfigItem(keyName = "forestryFlowering", name = "Flowering Tree", position = 40, section = forestrySection,
		description = "Pollinate the flowering tree.")
	default boolean forestryFlowering()
	{
		return true;
	}
}
