package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

/**
 * Why a pathfinder run stopped.
 *
 * <p>The first four values intentionally match the tracked shortest-path upstream contract. Microbot
 * adds {@link #FAILED} because its legacy pathfinder catches runtime failures in order to keep the
 * client alive; callers must be able to distinguish that case from an exhausted graph.</p>
 */
public enum PathTerminationReason
{
	TARGET_REACHED,
	SEARCH_EXHAUSTED,
	CUTOFF_REACHED,
	CANCELLED,
	FAILED
}
