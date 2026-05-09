package net.runelite.client.plugins.microbot.util.leaguetransport;

/**
 * Interface group + child ids for {@link Rs2LeaguesTransport} (Activities → Leagues → View Areas → teleport row).
 * Use {@link #pack(int, int)} for {@link net.runelite.api.Client#menuAction} {@code param1} (packed component id).
 * <p>
 * Optional per-region overrides and areas-menu shield tabs: {@link LeaguesRegion}.
 */
final class LeagueTransportWidgets
{
	private LeagueTransportWidgets()
	{
	}

	/** Activities panel: open Leagues. */
	static final int ACTIVITIES_GROUP = 161;
	static final int ACTIVITIES_CHILD = 61;

	/** Tab strip row child with {@code Actions=[Leagues,...]} (packed id e.g. {@code 41222178}). */
	static final int LEAGUES_GROUP = 629;
	static final int LEAGUES_CHILD = 34;

	static final int VIEW_AREAS_GROUP = 656;
	static final int VIEW_AREAS_CHILD = 40;

	/** Root panel for areas view (e.g. {@code 33554433}). */
	static final int AREAS_PANEL_GROUP = 512;
	static final int AREAS_PANEL_CHILD = 1;

	/**
	 * Parent of teleport rows in the areas menu ({@code 33554474}). Visibility gate + parent for
	 * {@link LeaguesRegion#getTeleportListRowDynamicIndex()}.
	 */
	static final int AREAS_LIST_CONTAINER_GROUP = 512;
	static final int AREAS_LIST_CONTAINER_CHILD = 42;

	/**
	 * Row widget template id ({@code 33554516}); multiple instances share this id under the container —
	 * pick the correct row by dynamic index, not by {@link net.runelite.api.Client#getWidget(int, int)} alone.
	 */
	static final int TELEPORT_ROW_GROUP = 512;
	static final int TELEPORT_ROW_CHILD = 84;

	static int pack(int group, int child)
	{
		return (group << 16) | (child & 0xFFFF);
	}
}
