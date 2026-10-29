package net.runelite.client.plugins.microbot.questhelper;

import java.util.Random;

/**
 * Compatibility shim: the model now lives in util.antiban.HumanizedTiming so other scripts (DD Chopper) can share it.
 * Static constants are inherited, so existing references keep compiling.
 */
public final class HumanizedTiming extends net.runelite.client.plugins.microbot.util.antiban.HumanizedTiming {
	public HumanizedTiming(Random random, boolean fatigueEnabled) {
		super(random, fatigueEnabled);
	}
}
