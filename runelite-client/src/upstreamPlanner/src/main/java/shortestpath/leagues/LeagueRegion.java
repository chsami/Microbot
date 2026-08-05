package shortestpath.leagues;

/**
 * Identifies the Demonic Pacts League area that a tile belongs to.
 *
 * <p>
 * Per the wiki ({@code Demonic_Pacts_League/Areas}), all players start locked
 * to {@link #VARLAMORE}; {@link #KARAMJA} is the first region unlock awarded
 * for free at 80 tasks; the remaining regions are picked at 200/300/450
 * tasks. Karamja is therefore not always-unlocked — it flows through the
 * same area-slot varbits as the other player picks.
 * </p>
 *
 * <p>
 * {@link #NEUTRAL} captures areas reachable from anywhere regardless of the
 * player's unlocks: Death's office, POH, Zanaris, the Abyssal Area, random
 * events, instances, dynamic regions, tutorial island, and Sailing-skill
 * content (e.g. The Great Conch, The Summer Shore, The Node) which is not
 * part of the league at all. The Great Conch is explicitly blocked as
 * {@link #MISTHALIN} rather than left NEUTRAL because it has walkable tiles
 * reachable via charter ship and fairy ring — leaving it NEUTRAL would allow
 * the pathfinder to route through it.
 * </p>
 *
 * <p>
 * {@link #MISTHALIN} is permanently inaccessible during the league.
 * </p>
 *
 * <p>
 * Region geometry is sourced from {@code leagues/regions.tsv}: a mapping
 * from OSRS map region id to one of these enum names. Tiles with no
 * mapping fall back to {@link #NEUTRAL}.
 * </p>
 */
public enum LeagueRegion
{
	VARLAMORE,
	KARAMJA,
	ASGARNIA,
	KANDARIN,
	FREMENNIK,
	KOUREND,
	WILDERNESS,
	MORYTANIA,
	DESERT,
	TIRANNWN,
	MISTHALIN,
	NEUTRAL;

	/**
	 * Whether this region is reachable regardless of which area unlocks the
	 * player has chosen. Only Varlamore (the starting region) and the
	 * NEUTRAL bucket are always-unlocked; every other region — including
	 * Karamja — depends on the player's slot picks.
	 */
	public boolean isAlwaysUnlocked()
	{
		return this == VARLAMORE || this == NEUTRAL;
	}

	/**
	 * Whether this region is permanently blocked during the league. Tiles in
	 * always-blocked regions reject both walking and transport traversal
	 * (see {@code LeagueRegionChecker} and {@code PathfinderConfig.useTransport}).
	 */
	public boolean isAlwaysBlocked()
	{
		return this == MISTHALIN;
	}
}
