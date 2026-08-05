package net.runelite.client.plugins.microbot.util.walker;

/**
 * Planner-independent transport categories understood by the Microbot walker boundary.
 *
 * <p>The superset includes names used by both the local planner and the tracked upstream planner so
 * an adapter can normalize either engine without exposing its enum.</p>
 */
public enum Rs2TransportType
{
	TRANSPORT,
	AGILITY_SHORTCUT,
	GRAPPLE_SHORTCUT,
	BOAT,
	CANOE,
	CHARTER_SHIP,
	SHIP,
	FAIRY_RING,
	QUETZAL,
	QUETZAL_WHISTLE,
	GNOME_GLIDER,
	MINECART,
	POH,
	SPIRIT_TREE,
	TELEPORTATION_BOX,
	TELEPORTATION_LEVER,
	TELEPORTATION_PORTAL,
	TELEPORTATION_PORTAL_POH,
	TELEPORTATION_MINIGAME,
	TELEPORTATION_ITEM,
	TELEPORTATION_SPELL,
	TELEPORTATION_SPELL_HOME,
	WILDERNESS_OBELISK,
	MAGIC_CARPET,
	HOT_AIR_BALLOON,
	MAGIC_MUSHTREE,
	SEASONAL_TRANSPORT,
	NPC,
	UNKNOWN
}
