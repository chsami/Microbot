package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DDChopperConfig.GROUP)
public interface DDChopperConfig extends Config
{
	String GROUP = "ddchopper";

	// ---- Axe ----
	@ConfigItem(keyName = "axe", name = "Axe", position = 0,
		description = "Auto uses the best axe you own that your Woodcutting level allows, or pick one specific axe.")
	default AxeChoice axe()
	{
		return AxeChoice.AUTO;
	}

	@ConfigItem(keyName = "buyAxe", name = "Buy axe if missing", position = 1,
		description = "Buy the best affordable usable axe at the Grand Exchange when none is owned, and buy upgrades as levels unlock them.")
	default boolean buyAxe()
	{
		return true;
	}

	// ---- Goal ----
	@ConfigItem(keyName = "goalType", name = "Goal type", position = 2,
		description = "Stop at a target level, after a run time, or never.")
	default GoalType goalType()
	{
		return GoalType.TARGET_LEVEL;
	}

	@Range(min = 2, max = 99)
	@ConfigItem(keyName = "targetLevel", name = "Target level", position = 3,
		description = "Stop when Woodcutting reaches this level (goal type: Target level).")
	default int targetLevel()
	{
		return 30;
	}

	@Range(min = 0, max = 99)
	@ConfigItem(keyName = "runHours", name = "Run time hours", position = 4,
		description = "Hours of active play before stopping (goal type: Run time). Breaks do not count.")
	default int runHours()
	{
		return 1;
	}

	@Range(min = 0, max = 59)
	@ConfigItem(keyName = "runMinutes", name = "Run time minutes", position = 5,
		description = "Minutes added to the run time hours.")
	default int runMinutes()
	{
		return 0;
	}

	// ---- What and where ----
	@ConfigItem(keyName = "mode", name = "Mode", position = 6,
		description = "Power-chop drops the logs (fastest XP). Bank logs walks to the nearest bank when full.")
	default ChopMode mode()
	{
		return ChopMode.POWER_CHOP;
	}

	@ConfigItem(keyName = "tree", name = "Tree", position = 7,
		description = "Auto picks the best XP/hour tree for your level and axe and moves up as you level.")
	default TreeChoice tree()
	{
		return TreeChoice.AUTO;
	}

	@ConfigItem(keyName = "spot", name = "Spot", position = 8,
		description = "Auto picks the optimal spot for the tree. A specific spot is used when it suits the tree and is usable, otherwise Auto.")
	default Spot spot()
	{
		return Spot.AUTO;
	}

	@ConfigItem(keyName = "rotateSpots", name = "Rotate spots", position = 9,
		description = "Switch between the eligible spots of the current tree to vary where you chop.")
	default boolean rotateSpots()
	{
		return false;
	}

	@ConfigItem(keyName = "rotateSpotNames", name = "Spots to rotate", position = 10,
		description = "Comma separated spot names (as shown in the Spot dropdown, or the enum name such as EDGEVILLE_YEWS). Only spots that suit the current tree are used. Empty rotates through every eligible spot of the tree.")
	default String rotateSpotNames()
	{
		return "";
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "rotateMinMinutes", name = "Time per spot min (min)", position = 10,
		description = "Shortest stay at each spot, in minutes of active play. Set min and max equal for a fixed time (90 = an hour and a half).")
	default int rotateMinMinutes()
	{
		return 60;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "rotateMaxMinutes", name = "Time per spot max (min)", position = 10,
		description = "Longest stay at each spot, in minutes of active play.")
	default int rotateMaxMinutes()
	{
		return 90;
	}

	@ConfigItem(keyName = "members", name = "Members world", position = 11,
		description = "Allow members-only trees and spots.")
	default boolean members()
	{
		return false;
	}

	@ConfigItem(keyName = "allowRestricted", name = "Allow boat / gated spots", position = 12,
		description = "Allow spots that need a boat, a quest gate or a guild unlock. Off keeps the walker on foot only.")
	default boolean allowRestricted()
	{
		return false;
	}

	@ConfigItem(keyName = "pickupNests", name = "Pick up bird nests", position = 13,
		description = "Pick up bird nests that drop while chopping.")
	default boolean pickupNests()
	{
		return false;
	}

	// ---- Crowd hopping ----
	@ConfigItem(keyName = "hopWhenCrowded", name = "Hop when crowded", position = 14,
		description = "Hop worlds when too many other players are at the spot.")
	default boolean hopWhenCrowded()
	{
		return true;
	}

	@Range(min = 0, max = 50)
	@ConfigItem(keyName = "maxOthers", name = "Max other players at spot", position = 15,
		description = "Hop when more than this many other players stay near the spot for about 10 seconds.")
	default int maxOthers()
	{
		return 2;
	}

	@Range(min = 3, max = 30)
	@ConfigItem(keyName = "crowdRadius", name = "Crowd check radius", position = 16,
		description = "Tiles around you that count as the spot.")
	default int crowdRadius()
	{
		return 10;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(keyName = "maxHopsPerHour", name = "Max hops per hour", position = 17,
		description = "Upper limit on crowd hops in any rolling hour.")
	default int maxHopsPerHour()
	{
		return 6;
	}

	@ConfigItem(keyName = "avoidWorlds", name = "Worlds to avoid", position = 18,
		description = "World numbers never to hop to, separated by commas. PvP, high risk, total level and other restricted worlds are always avoided.")
	default String avoidWorlds()
	{
		return "";
	}

	// ---- Breaks ----
	@ConfigItem(keyName = "enableBreaks", name = "Enable breaks", position = 19,
		description = "Take random breaks using Microbot's Break Handler.")
	default boolean enableBreaks()
	{
		return true;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "minPlay", name = "Play time min (min)", position = 20,
		description = "Shortest play time between breaks, in minutes.")
	default int minPlay()
	{
		return 30;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "maxPlay", name = "Play time max (min)", position = 21,
		description = "Longest play time between breaks, in minutes.")
	default int maxPlay()
	{
		return 90;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "minBreak", name = "Break length min (min)", position = 22,
		description = "Shortest break, in minutes.")
	default int minBreak()
	{
		return 5;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(keyName = "maxBreak", name = "Break length max (min)", position = 23,
		description = "Longest break, in minutes.")
	default int maxBreak()
	{
		return 20;
	}

	@ConfigItem(keyName = "logoutOnBreak", name = "Log out on break", position = 24,
		description = "Log out during breaks. Off just stops clicking.")
	default boolean logoutOnBreak()
	{
		return false;
	}
}
