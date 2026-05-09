package net.runelite.client.plugins.microbot.util.leaguetransport;

/**
 * Failure classification for {@link LeaguesTeleportResult}. Callers that switch on this enum should treat
 * {@link #TELEPORT_TIMEOUT} like other non-success outcomes unless a dedicated UX is required.
 * {@link #CLIENT_UNAVAILABLE} and {@link #INVOKED_ON_CLIENT_THREAD} are caller/environment issues (same UX as other hard
 * failures unless you special-case UI) and are not fixed by blind retry; {@link #CLIENT_THREAD_UNAVAILABLE} is an empty
 * client-thread gate callback, distinct from those two.
 */
public enum LeaguesTeleportFailureReason
{
	CLIENT_UNAVAILABLE,
	NOT_SEASONAL_WORLD,
	LEAGUE_ACCOUNT_INACTIVE,
	CLIENT_THREAD_UNAVAILABLE,
	/** Caller invoked {@link net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport#leaguesTeleport} on the RuneLite client thread (disallowed; blocks UI). */
	INVOKED_ON_CLIENT_THREAD,
	REGION_LOCKED,
	UI_TIMEOUT,
	TELEPORT_TIMEOUT,
	UNKNOWN
}

