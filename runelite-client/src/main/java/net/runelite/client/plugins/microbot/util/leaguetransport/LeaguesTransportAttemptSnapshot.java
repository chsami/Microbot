package net.runelite.client.plugins.microbot.util.leaguetransport;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Bind chat messages to last attempted transport. Immutable snapshot for the recent-attempt ring.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class LeaguesTransportAttemptSnapshot
{
	private final Integer packedDest;
	private final String method;
	private final long tsMs;
}
