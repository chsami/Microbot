package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure planner-side description of the Microbot executor capabilities.
 *
 * <p>A transport must not be offered to automated pathfinding unless the walker has a concrete
 * execution branch for it. Keeping that decision here prevents catalog convergence from silently
 * turning data coverage into routes that the runtime cannot complete.</p>
 */
public final class TransportExecutionRegistry
{
	public enum Executor
	{
		BARROWS_DIG,
		CANOE,
		CHARTER_SHIP,
		FAIRY_RING,
		GNOME_GLIDER,
		HOT_AIR_BALLOON,
		ITEM_TELEPORT,
		MAGIC_CARPET,
		MAGIC_MUSHTREE,
		MINIGAME_TELEPORT,
		OBJECT,
		POH,
		QUETZAL,
		SEASONAL,
		SPELL_TELEPORT,
		SPIRIT_TREE,
		TERMINAL_TRAVEL,
		WILDERNESS_OBELISK
	}

	/** Planner-visible interaction sequence supported by the terminal-travel executor. */
	public enum TerminalTravelMode
	{
		DIRECT,
		DIALOGUE_DESTINATION
	}

	/** Exact destination labels presented by the unlocked balloon network map. */
	public enum BalloonDestination
	{
		CASTLE_WARS("Castle Wars"),
		GRAND_TREE("Grand Tree"),
		CRAFTING_GUILD("Crafting Guild"),
		ENTRANA("Entrana"),
		TAVERLEY("Taverley"),
		VARROCK("Varrock");

		private final String displayName;

		BalloonDestination(String displayName)
		{
			this.displayName = displayName;
		}

		public String getDisplayName()
		{
			return displayName;
		}
	}

	/** Home teleports are zero-rune spellbook widgets rather than ordinary {@link MagicAction}s. */
	public enum HomeTeleport
	{
		LUMBRIDGE("Lumbridge Home Teleport"),
		EDGEVILLE("Edgeville Home Teleport"),
		LUNAR("Lunar Home Teleport"),
		ARCEUUS("Arceuus Home Teleport");

		private final String displayName;

		HomeTeleport(String displayName)
		{
			this.displayName = displayName;
		}

		public String getDisplayName()
		{
			return displayName;
		}
	}

	private static final Set<TransportType> GENERIC_OBJECT_EXECUTORS = EnumSet.of(
		TransportType.TRANSPORT,
		TransportType.AGILITY_SHORTCUT,
		TransportType.GRAPPLE_SHORTCUT,
		TransportType.MINECART,
		TransportType.TELEPORTATION_LEVER,
		TransportType.TELEPORTATION_PORTAL,
		TransportType.MAGIC_MUSHTREE);

	/**
	 * Barrows mound digs are inventory-item interactions, not scene-object interactions. Keep the
	 * six deterministic surface-to-crypt mappings exact so an arbitrary object-less transport row
	 * cannot acquire the spade executor by naming itself "Dig".
	 */
	private static final Map<WorldPoint, WorldPoint>
		BARROWS_DIG_DESTINATIONS = Map.of(
			new WorldPoint(3564, 3291, 0), new WorldPoint(3559, 9703, 3),
			new WorldPoint(3575, 3299, 0), new WorldPoint(3558, 9718, 3),
			new WorldPoint(3578, 3281, 0), new WorldPoint(3534, 9706, 3),
			new WorldPoint(3567, 3274, 0), new WorldPoint(3546, 9686, 3),
			new WorldPoint(3553, 3281, 0), new WorldPoint(3566, 9683, 3),
			new WorldPoint(3556, 3297, 0), new WorldPoint(3578, 9704, 3));

	private TransportExecutionRegistry()
	{
	}

	public static boolean canExecute(Transport transport)
	{
		return executorFor(transport).isPresent();
	}

	/** Resolve the walker branch without reading live client state. */
	public static Optional<Executor> executorFor(Transport transport)
	{
		if (transport == null || transport.getType() == null || transport.getDestination() == null)
		{
			return Optional.empty();
		}

		TransportType type = transport.getType();
		if (isBarrowsDig(transport))
		{
			return Optional.of(Executor.BARROWS_DIG);
		}
		if (type == TransportType.TELEPORTATION_SPELL)
		{
			return hasRegisteredSpell(transport.getDisplayInfo())
				? Optional.of(Executor.SPELL_TELEPORT)
				: Optional.empty();
		}
		if (type == TransportType.POH)
		{
			return transport instanceof PohTransport
				? Optional.of(Executor.POH)
				: Optional.empty();
		}
		if (type == TransportType.HOT_AIR_BALLOON)
		{
			return hasRegisteredBalloon(transport)
				? Optional.of(Executor.HOT_AIR_BALLOON)
				: Optional.empty();
		}
		if (isTerminalTravelType(type))
		{
			return terminalTravelModeFor(transport).isPresent()
				? Optional.of(Executor.TERMINAL_TRAVEL)
				: Optional.empty();
		}
		if (GENERIC_OBJECT_EXECUTORS.contains(type))
		{
			return hasObjectInteraction(transport)
				? Optional.of(type == TransportType.MAGIC_MUSHTREE
					? Executor.MAGIC_MUSHTREE
					: Executor.OBJECT)
				: Optional.empty();
		}

		return Optional.ofNullable(specializedExecutor(type));
	}

	private static boolean isBarrowsDig(Transport transport)
	{
		if (transport.getType() != TransportType.TRANSPORT
			|| transport.getObjectId() != 0
			|| !"Dig".equalsIgnoreCase(transport.getAction())
			|| !"Barrow".equalsIgnoreCase(transport.getName())
			|| !transport.getDestination().equals(BARROWS_DIG_DESTINATIONS.get(transport.getOrigin()))
			|| transport.getItemRequirements().size() != 1)
		{
			return false;
		}
		TransportItemRequirement spade = transport.getItemRequirements().get(0);
		return spade.getAllItemIds().equals(Set.of(ItemID.SPADE))
			&& spade.getRequiredQuantity(ItemID.SPADE) == 1;
	}

	private static Executor specializedExecutor(TransportType type)
	{
		switch (type)
		{
			case CANOE:
				return Executor.CANOE;
			case CHARTER_SHIP:
				return Executor.CHARTER_SHIP;
			case FAIRY_RING:
				return Executor.FAIRY_RING;
			case GNOME_GLIDER:
				return Executor.GNOME_GLIDER;
			case MAGIC_CARPET:
				return Executor.MAGIC_CARPET;
			case QUETZAL:
				return Executor.QUETZAL;
			case SPIRIT_TREE:
				return Executor.SPIRIT_TREE;
			case TELEPORTATION_ITEM:
				return Executor.ITEM_TELEPORT;
			case TELEPORTATION_MINIGAME:
				return Executor.MINIGAME_TELEPORT;
			case WILDERNESS_OBELISK:
				return Executor.WILDERNESS_OBELISK;
			case SEASONAL_TRANSPORT:
				return Executor.SEASONAL;
			default:
				return null;
		}
	}

	/**
	 * Resolve the complete interaction flow, not merely the catalog family.
	 *
	 * <p>The SHIP/NPC/BOAT files describe journeys and contain both NPC and scene-object targets.
	 * Target kind is therefore resolved live. Interaction sequence is catalog policy, however, and must
	 * be known before planning. Unknown or currently unimplemented sequences fail closed here.</p>
	 */
	public static Optional<TerminalTravelMode> terminalTravelModeFor(Transport transport)
	{
		if (transport == null
			|| !isTerminalTravelType(transport.getType())
			|| transport.getOrigin() == null
			|| transport.getDestination() == null
			|| transport.getObjectId() <= 0
			|| isBlank(transport.getName())
			|| isBlank(transport.getAction()))
		{
			return Optional.empty();
		}

		if (requiresUnsupportedTerminalDestinationSelection(transport))
		{
			return Optional.empty();
		}
		if ("Mountain Guide".equalsIgnoreCase(transport.getName()))
		{
			return isBlank(transport.getDisplayInfo())
				? Optional.empty()
				: Optional.of(TerminalTravelMode.DIALOGUE_DESTINATION);
		}
		return Optional.of(TerminalTravelMode.DIRECT);
	}

	private static boolean isTerminalTravelType(TransportType type)
	{
		return type == TransportType.SHIP || type == TransportType.NPC || type == TransportType.BOAT;
	}

	private static boolean requiresUnsupportedTerminalDestinationSelection(Transport transport)
	{
		String action = transport.getAction();
		String target = transport.getName();
		if (transport.getType() == TransportType.BOAT && !isBlank(transport.getDisplayInfo()))
		{
			return ("Board".equalsIgnoreCase(action)
				&& ("Boaty".equalsIgnoreCase(target) || "Boat".equalsIgnoreCase(target)))
				|| ("Travel".equalsIgnoreCase(action) && "Rowboat".equalsIgnoreCase(target));
		}
		return "Talk-to".equalsIgnoreCase(action)
			&& ("Captain Shanks".equalsIgnoreCase(target) || "Pirate Pete".equalsIgnoreCase(target));
	}

	private static boolean hasObjectInteraction(Transport transport)
	{
		return transport.getOrigin() != null
			&& transport.getObjectId() > 0
			&& !isBlank(transport.getAction());
	}

	private static boolean hasRegisteredSpell(String displayInfo)
	{
		if (isBlank(displayInfo))
		{
			return false;
		}
		if (homeTeleportFor(displayInfo).isPresent())
		{
			return true;
		}
		String spellName = displayInfo.contains(":")
			? displayInfo.substring(0, displayInfo.indexOf(':')).trim()
			: displayInfo.trim();
		return Arrays.stream(MagicAction.values())
			.anyMatch(action -> action.getName().toLowerCase(Locale.ROOT)
				.contains(spellName.toLowerCase(Locale.ROOT)));
	}

	/** Resolve the exact home-teleport widget family shared by planning and execution. */
	public static Optional<HomeTeleport> homeTeleportFor(String displayInfo)
	{
		if (isBlank(displayInfo))
		{
			return Optional.empty();
		}
		String normalized = displayInfo.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(HomeTeleport.values())
			.filter(teleport -> teleport.getDisplayName().toLowerCase(Locale.ROOT).equals(normalized))
			.findFirst();
	}

	/** Resolve an exact balloon-map destination shared by capability filtering and runtime dispatch. */
	public static Optional<BalloonDestination> balloonDestinationFor(String displayInfo)
	{
		if (isBlank(displayInfo))
		{
			return Optional.empty();
		}
		String normalized = displayInfo.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(BalloonDestination.values())
			.filter(destination -> destination.getDisplayName().toLowerCase(Locale.ROOT).equals(normalized))
			.findFirst();
	}

	private static boolean hasRegisteredBalloon(Transport transport)
	{
		return hasObjectInteraction(transport)
			&& (transport.getObjectId() == 19128 || transport.getObjectId() == 19129)
			&& "Use".equalsIgnoreCase(transport.getAction())
			&& "Basket".equalsIgnoreCase(transport.getName())
			&& balloonDestinationFor(transport.getDisplayInfo()).isPresent();
	}

	private static boolean isBlank(String value)
	{
		return value == null || value.trim().isEmpty();
	}
}
