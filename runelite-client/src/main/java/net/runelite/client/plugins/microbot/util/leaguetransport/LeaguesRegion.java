package net.runelite.client.plugins.microbot.util.leaguetransport;

import java.util.Objects;

/**
 * Leagues area teleport targets. {@link #getAreaId()} matches values stored in
 * {@link net.runelite.api.gameval.VarbitID#LEAGUE_AREA_SELECTION_0}
 * through {@code LEAGUE_AREA_SELECTION_5}
 * for seasonal leagues; values may vary by league season.
 * Area ids are authoritative game values (not contiguous).
 *
 * <p>Optional widget overrides ({@link #getAreasListRootGroup()} / {@link #getAreasListRootChild()},
 * {@link #getTeleportCcOpGroup()} / {@link #getTeleportCcOpChild()}):
 * list container {@code group == 0} uses {@link LeagueTransportWidgets#AREAS_LIST_CONTAINER_GROUP};
 * teleport {@code group == 0} uses {@link #getTeleportListRowDynamicIndex()} under that container.
 *
 * <p>The areas menu rebuilds its list when a <em>shield</em> tab is selected. Use {@link #getAreasMenuShield()}
 * with {@link AreasMenuShield#isActive()} so transport clicks the correct tab before waiting on the row / teleport.
 */
public enum LeaguesRegion
{
	/**
	 * Indices are positions in the areas list {@link net.runelite.api.widgets.Widget#getDynamicChildren()} under
	 * the list container — tune when client layout or shield tab changes list order.
	 */
	MISTHALIN(1, "Misthalin", shieldTab(46, "<col=ff981f>Misthalin</col>"), 0),
	KARAMJA(2, "Karamja", shieldTab(47, "<col=ff981f>Karamja</col>"), 1),
	ASGARNIA(3, "Asgarnia", shieldTab(50, "<col=ff981f>Asgarnia</col>"), 2),
	KANDARIN(4, "Kandarin", shieldTab(51, "<col=ff981f>Kandarin</col>"), 3),
	MORYTANIA(5, "Morytania", shieldTab(49, "<col=ff981f>Morytania</col>"), 4),
	DESERT(6, "Kharidian Desert", shieldTab(48, "<col=ff981f>Kharidian Desert</col>"), 5),
	TIRANNWN(7, "Tirannwn", shieldTab(53, "<col=ff981f>Tirannwn</col>"), 6),
	FREMENNIK(8, "Fremennik Province", shieldTab(52, "<col=ff981f>Fremennik Province</col>"), 7),
	WILDERNESS(11, "Wilderness", shieldTab(54, "<col=ff981f>Wilderness</col>"), 8),
	KEBOS_AND_KOUREND(20, "Great Kourend and Kebos Lowlands", shieldTab(55, "<col=ff981f>Great Kourend and Kebos Lowlands</col>"), 9),
	VARLAMORE(21, "Varlamore", shieldTab(57, "<col=ff981f>Varlamore</col>"), 10);

	/**
	 * Shield tab on the Leagues areas menu: interface group/child + {@code CC_OP} strings.
	 * {@link #none()} skips the click (prototype-style flow when only one tab is shown).
	 */
	public static final class AreasMenuShield
	{
		private final int group;
		private final int child;
		private final String ccOpOption;
		private final String ccOpTarget;

		public AreasMenuShield(int group, int child, String ccOpOption, String ccOpTarget)
		{
			this.group = group;
			this.child = child;
			this.ccOpOption = ccOpOption != null ? ccOpOption : "";
			this.ccOpTarget = ccOpTarget != null ? ccOpTarget : "";
		}

		public static AreasMenuShield none()
		{
			return new AreasMenuShield(0, 0, "", "");
		}

		/** Inactive when {@code group == 0} ({@code child} ignored). */
		public boolean isActive()
		{
			return group != 0;
		}

		public int getGroup()
		{
			return group;
		}

		public int getChild()
		{
			return child;
		}

		public String getCcOpOption()
		{
			return ccOpOption;
		}

		public String getCcOpTarget()
		{
			return ccOpTarget;
		}
	}

	private final int areaId;
	private final String displayName;
	/** Areas list container; {@code group == 0} = default {@link LeagueTransportWidgets#AREAS_LIST_CONTAINER_GROUP}. */
	private final int areasListRootGroup;
	private final int areasListRootChild;
	/** Final {@link net.runelite.api.MenuAction#CC_OP} target; {@code group == 0} = use row from list + index. */
	private final int teleportCcOpGroup;
	private final int teleportCcOpChild;
	private final String teleportCcOpOption;
	private final AreasMenuShield areasMenuShield;
	/**
	 * Which row under the list container’s dynamic/static children (same packed row id for every row in IF3).
	 */
	private final int teleportListRowDynamicIndex;

	LeaguesRegion(int areaId, String displayName, AreasMenuShield areasMenuShield, int teleportListRowDynamicIndex)
	{
		// Empirical: widget dump shows Actions=[Teleport to], but menuAction requires option "Teleport".
		this(areaId, displayName, 0, 0, 0, 0, "Teleport", areasMenuShield, teleportListRowDynamicIndex);
	}

	LeaguesRegion(
			int areaId,
			String displayName,
			int areasListRootGroup,
			int areasListRootChild,
			int teleportCcOpGroup,
			int teleportCcOpChild,
			int teleportListRowDynamicIndex)
	{
		// Empirical: widget dump shows Actions=[Teleport to], but menuAction requires option "Teleport".
		this(areaId, displayName, areasListRootGroup, areasListRootChild, teleportCcOpGroup, teleportCcOpChild, "Teleport", AreasMenuShield.none(), teleportListRowDynamicIndex);
	}

	LeaguesRegion(
			int areaId,
			String displayName,
			int areasListRootGroup,
			int areasListRootChild,
			int teleportCcOpGroup,
			int teleportCcOpChild,
			String teleportCcOpOption,
			int teleportListRowDynamicIndex)
	{
		this(areaId, displayName, areasListRootGroup, areasListRootChild, teleportCcOpGroup, teleportCcOpChild, teleportCcOpOption, AreasMenuShield.none(), teleportListRowDynamicIndex);
	}

	LeaguesRegion(
			int areaId,
			String displayName,
			int areasListRootGroup,
			int areasListRootChild,
			int teleportCcOpGroup,
			int teleportCcOpChild,
			String teleportCcOpOption,
			AreasMenuShield areasMenuShield,
			int teleportListRowDynamicIndex)
	{
		this.areaId = areaId;
		this.displayName = displayName;
		this.areasListRootGroup = areasListRootGroup;
		this.areasListRootChild = areasListRootChild;
		this.teleportCcOpGroup = teleportCcOpGroup;
		this.teleportCcOpChild = teleportCcOpChild;
		this.teleportCcOpOption = Objects.requireNonNull(teleportCcOpOption, "teleportCcOpOption");
		this.areasMenuShield = areasMenuShield != null ? areasMenuShield : AreasMenuShield.none();
		this.teleportListRowDynamicIndex = teleportListRowDynamicIndex;
	}

	public int getAreaId()
	{
		return areaId;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/**
	 * Interface group for the areas list container (visibility wait + row parent).
	 * {@code 0} means use the shared default from {@link LeagueTransportWidgets#AREAS_LIST_CONTAINER_GROUP}.
	 */
	public int getAreasListRootGroup()
	{
		return areasListRootGroup;
	}

	/**
	 * Child id paired with {@link #getAreasListRootGroup()}; ignored when group is {@code 0}.
	 */
	public int getAreasListRootChild()
	{
		return areasListRootChild;
	}

	/**
	 * Interface group for the final {@code CC_OP} when fixed (non-resolved) row.
	 * {@code 0} means use {@link #getTeleportListRowDynamicIndex()} under the list container.
	 */
	public int getTeleportCcOpGroup()
	{
		return teleportCcOpGroup;
	}

	/**
	 * Child id paired with {@link #getTeleportCcOpGroup()}; ignored when group is {@code 0}.
	 */
	public int getTeleportCcOpChild()
	{
		return teleportCcOpChild;
	}

	/**
	 * Menu option for the final {@code CC_OP} (e.g. {@code Teleport to} vs {@code Teleport}).
	 */
	public String getTeleportCcOpOption()
	{
		return teleportCcOpOption;
	}

	/**
	 * Index into {@link net.runelite.api.widgets.Widget#getDynamicChildren()} (else {@link net.runelite.api.widgets.Widget#getChildren()})
	 * under the areas list container for this region’s teleport row.
	 */
	public int getTeleportListRowDynamicIndex()
	{
		return teleportListRowDynamicIndex;
	}

	/**
	 * Tab (“shield”) on the areas menu that must be selected so the list is rebuilt for this region.
	 */
	public AreasMenuShield getAreasMenuShield()
	{
		return areasMenuShield;
	}

	/**
	 * Menu target string for the {@link net.runelite.api.MenuAction#CC_OP} chain.
	 * Built from {@link #getDisplayName()} (not raw {@link #getAreaId()});
	 * must match the areas list row {@code Name}.
	 * {@literal <col=ff981f>} markup must stay aligned with the client UI.
	 */
	public String toMenuTarget()
	{
		return "<col=ff981f>" + displayName + "</col>";
	}

	private static AreasMenuShield shieldTab(int child, String ccOpTarget)
	{
		Objects.requireNonNull(ccOpTarget, "ccOpTarget");
		return new AreasMenuShield(
				LeagueTransportWidgets.AREAS_LIST_CONTAINER_GROUP,
				child,
				"View",
				ccOpTarget);
	}
}
