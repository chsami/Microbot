package net.runelite.client.plugins.microbot.bans;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

class AccountActivity
{
	long firstSeen;
	long lastBot;
	long sessionStart;
	long reportedAt;
	Boolean member;
	Integer totalLevel;
	Map<String, Day> days = new TreeMap<>();

	static class Day
	{
		long botSeconds;
		long longestSession;
		boolean breaks;
		Map<String, Long> plugins = new HashMap<>();
	}
}
