package shortestpath.transport;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueRegion;
import shortestpath.transport.parser.FieldParser;
import shortestpath.transport.parser.ItemRequirementParser;
import shortestpath.transport.parser.QuestParser;
import shortestpath.transport.parser.SkillRequirementParser;
import shortestpath.transport.parser.TransportRecord;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.parser.VarRequirementParser;
import shortestpath.transport.parser.WorldPointParser;
import shortestpath.transport.requirement.TransportItems;

/**
 * This class represents a travel point between two WorldPoints.
 */
@Slf4j
public class Transport
{
	public static final int UNDEFINED_ORIGIN = WorldPointUtil.UNDEFINED;
	public static final int UNDEFINED_DESTINATION = WorldPointUtil.UNDEFINED;
	/**
	 * A location placeholder different from null to use for permutation transports
	 */
	public static final int LOCATION_PERMUTATION = WorldPointUtil.packWorldPoint(-1, -1, 1);
	/**
	 * Shared, never-mutated all-zero skill array. Most transports require no skills, so they point
	 * at this singleton instead of each allocating their own {@code int[]} (issue #491).
	 */
	private static final int[] NO_SKILLS = new int[Skill.values().length + 3];
	/**
	 * The skill levels, total level, combat level and quest points required to use
	 * this transport. Defaults to {@link #NO_SKILLS} until real requirements are set.
	 */
	@Getter
	private int[] skillLevels = NO_SKILLS;
	/**
	 * Variable requirements (varbits and varplayers) for the transport to be valid.
	 * All must pass.
	 */
	// Defaults to a shared immutable empty set; only transports that actually declare var
	// requirements pay for a real Set. Most transports declare none, so this avoids ~one
	// HashSet (+ its HashMap) per transport (issue #491).
	@Getter
	private Set<VarRequirement> varRequirements = Collections.emptySet();
	/**
	 * The starting point of this transport
	 */
	@Getter
	private int origin = UNDEFINED_ORIGIN;
	/**
	 * The ending point of this transport
	 */
	@Setter
	@Getter
	private int destination = UNDEFINED_DESTINATION;

	/**
	 * The quests required to use this transport
	 */
	// Shared immutable empty set by default; see varRequirements above.
	@Getter
	private Set<Quest> quests = Collections.emptySet();
	/**
	 * The item requirements to use this transport
	 */
	@Getter
	private TransportItems itemRequirements;
	/**
	 * The type of transport
	 */
	@Getter
	private TransportType type;
	/**
	 * The travel waiting time in number of ticks
	 */
	@Getter
	private int duration;
	/**
	 * Info to display for this transport. For spirit trees, fairy rings,
	 * and others, this is the destination option to pick.
	 */
	@Getter
	private String displayInfo = null;
	/**
	 * If this is an item transport, this tracks if it is consumable (as opposed to
	 * having infinite uses)
	 */
	@Getter
	private boolean isConsumable = false;
	/**
	 * The maximum wilderness level that the transport can be used in
	 */
	@Getter
	private int maxWildernessLevel = -1;
	/**
	 * Object information for this transport
	 */
	@Getter
	private String objectInfo = null;
	/**
	 * Per-transport seasonal-league region override. When set, the league-mode
	 * region gate ({@code PathfinderConfig#isTransportRegionAllowed}) uses
	 * this region for the destination chunk instead of the result of
	 * {@code LeagueRegionChecker#getRegion(destination)}. Used for shortcuts
	 * whose destination tile sits in a chunk that the wiki classifies under
	 * a different region (e.g. Trollheim Wilderness climb — destination chunk
	 * is Wilderness, but the wiki lists the shortcut under Asgarnia).
	 */
	@Getter
	private LeagueRegion regionOverride = null;

	/**
	 * Creates a new transport from an origin-only transport
	 * and a destination-only transport, and merges requirements
	 */
	Transport(Transport origin, Transport destination)
	{
		TransportBuilder builder = new TransportBuilder()
			.origin(origin.origin)
			.destination(destination.destination)
			.type(origin.type)
			.startSkillLevels(origin.skillLevels)
			.startSkillLevels(destination.skillLevels)
			.quests(origin.quests)
			.quests(destination.quests)
			.itemRequirements(TransportItems.merge(origin.itemRequirements, destination.itemRequirements))
			.duration(Math.max(origin.duration, destination.duration))
			.displayInfo(destination.displayInfo)
			.isConsumable(origin.isConsumable || destination.isConsumable)
			.maxWildernessLevel(Math.max(origin.maxWildernessLevel, destination.maxWildernessLevel))
			.objectInfo(origin.objectInfo)
			.varRequirements(origin.varRequirements)
			.varRequirements(destination.varRequirements)
			.regionOverride(destination.regionOverride != null ? destination.regionOverride : origin.regionOverride);

		Transport builtTransport = builder.build();

		this.origin = builtTransport.origin;
		this.destination = builtTransport.destination;
		this.skillLevels = builtTransport.skillLevels;
		this.quests = builtTransport.quests;
		this.itemRequirements = builtTransport.itemRequirements;
		this.type = builtTransport.type;
		this.duration = builtTransport.duration;
		this.displayInfo = builtTransport.displayInfo;
		this.isConsumable = builtTransport.isConsumable;
		this.maxWildernessLevel = builtTransport.maxWildernessLevel;
		this.objectInfo = builtTransport.objectInfo;
		this.varRequirements = builtTransport.varRequirements;
	}

	Transport(TransportRecord record, TransportType transportType)
	{
		TransportBuilder builder = new TransportBuilder();
		builder.type(transportType);

		// Origin/Destination use hasKey because empty string means LOCATION_PERMUTATION
		if (record.hasKey(TransportRecord.Fields.ORIGIN))
		{
			builder.origin(record.getOrigin());
		}
		if (record.hasKey(TransportRecord.Fields.DESTINATION))
		{
			builder.destination(record.getDestination());
		}
		if (record.has(TransportRecord.Fields.SKILLS))
		{
			builder.skillLevels(record.getSkills());
		}
		if (record.has(TransportRecord.Fields.ITEMS))
		{
			builder.itemRequirements(record.getItems());
		}
		if (record.has(TransportRecord.Fields.QUESTS))
		{
			builder.quests(record.getQuests());
		}
		if (record.has(TransportRecord.Fields.DURATION))
		{
			builder.duration(record.getDuration());
		}
		if (record.has(TransportRecord.Fields.DISPLAY_INFO))
		{
			builder.displayInfo(record.getDisplayInfo());
		}
		if (record.has(TransportRecord.Fields.CONSUMABLE))
		{
			builder.isConsumable(record.getConsumable());
		}
		if (record.has(TransportRecord.Fields.WILDERNESS_LEVEL))
		{
			builder.maxWildernessLevel(record.getWildernessLevel());
		}
		if (record.has(TransportRecord.Fields.OBJECT_INFO))
		{
			builder.objectInfo(record.getObjectInfo());
		}
		if (record.has(TransportRecord.Fields.VARBITS))
		{
			builder.varbits(record.getVarbits());
		}
		if (record.has(TransportRecord.Fields.VAR_PLAYERS))
		{
			builder.varPlayers(record.getVarPlayers());
		}
		if (record.has(TransportRecord.Fields.REGION_OVERRIDE))
		{
			builder.regionOverride(record.getRegionOverride());
		}

		Transport builtTransport = builder.build();
		this.origin = builtTransport.origin;
		this.destination = builtTransport.destination;
		this.skillLevels = builtTransport.skillLevels;
		this.quests = builtTransport.quests;
		this.itemRequirements = builtTransport.itemRequirements;
		this.type = builtTransport.type;
		this.duration = builtTransport.duration;
		this.displayInfo = builtTransport.displayInfo;
		this.isConsumable = builtTransport.isConsumable;
		this.maxWildernessLevel = builtTransport.maxWildernessLevel;
		this.objectInfo = builtTransport.objectInfo;
		this.varRequirements = builtTransport.varRequirements;
		this.regionOverride = builtTransport.regionOverride;
	}

	private Transport()
	{
	}

	/**
	 * Hands back a shared immutable empty set when the builder accumulated nothing, so empty
	 * requirement sets do not allocate a {@code HashSet}/{@code HashMap} per transport. A non-empty
	 * builder set is handed over directly (the single-use builder is discarded afterwards).
	 */
	private static <T> Set<T> compact(Set<T> set)
	{
		return set.isEmpty() ? Collections.emptySet() : set;
	}

	/**
	 * Quest requirements are keyed on the {@link Quest} enum, so a non-empty set is stored as a
	 * compact {@link EnumSet} (a single bitmask object) rather than a HashSet plus its HashMap and
	 * per-element nodes (issue #491). The empty case keeps the shared immutable empty set.
	 */
	private static Set<Quest> compactQuests(Set<Quest> quests)
	{
		return quests.isEmpty() ? Collections.emptySet() : EnumSet.copyOf(quests);
	}

	/**
	 * Hands back the shared {@link #NO_SKILLS} singleton when no skill requirement is set, so
	 * all-zero skill arrays do not allocate a per-transport {@code int[]}. A non-empty builder array
	 * is handed over directly (the single-use builder is discarded afterwards).
	 */
	private static int[] compactSkills(int[] skills)
	{
		for (int level : skills)
		{
			if (level != 0)
			{
				return skills;
			}
		}
		return NO_SKILLS;
	}

	/**
	 * Load-time flyweight: replaces this transport's requirement objects with shared canonical
	 * instances from the supplied pools, so transports with identical item or var requirements share
	 * one {@code TransportItems}/{@code VarRequirement} instance (and the int[] arrays inside them)
	 * rather than each holding a distinct copy (issue #491). Identical requirements are extremely
	 * common across the permuted transport rows. The pools are local to loading and discarded after.
	 */
	void internRequirements(LoadInterner interner)
	{
		itemRequirements = interner.intern(itemRequirements);
		displayInfo = interner.internString(displayInfo);
		objectInfo = interner.internString(objectInfo);
		if (!varRequirements.isEmpty())
		{
			Set<VarRequirement> interned = new HashSet<>(varRequirements.size() * 2);
			for (VarRequirement requirement : varRequirements)
			{
				interned.add(interner.intern(requirement));
			}
			// Transports with identical var requirements (very common across permutations) share one
			// read-only Set instead of each keeping a copy.
			varRequirements = interner.internVarSet(interned);
		}
		if (!quests.isEmpty())
		{
			quests = interner.internQuestSet(quests);
		}
	}

	@Override
	public String toString()
	{
		return ("(" +
			WorldPointUtil.unpackWorldX(origin) + ", " +
			WorldPointUtil.unpackWorldY(origin) + ", " +
			WorldPointUtil.unpackWorldPlane(origin) + ") to (" +
			WorldPointUtil.unpackWorldX(destination) + ", " +
			WorldPointUtil.unpackWorldY(destination) + ", " +
			WorldPointUtil.unpackWorldPlane(destination) + ")");
	}

	/**
	 * Whether the transport has one or more quest requirements
	 */
	public boolean isQuestLocked()
	{
		return !quests.isEmpty();
	}

	/**
	 * Whether this transport is of the given type.
	 */
	public boolean isType(TransportType type)
	{
		return type.equals(this.type);
	}

	/**
	 * Whether this transport's display info contains the given substring.
	 * Returns false if displayInfo is null.
	 */
	public boolean hasDisplayInfo(String substring)
	{
		return displayInfo != null && displayInfo.contains(substring);
	}

	/**
	 * Whether this transport can be used at the given wilderness level.
	 */
	public boolean isUsableAtWildernessLevel(int wildernessLevel)
	{
		return !type.isTeleport() || wildernessLevel <= maxWildernessLevel;
	}

	/**
	 * Gets varbit requirements (filtered from varRequirements).
	 * For backward compatibility with code that needs separate varbit access.
	 */
	public Set<VarRequirement> getVarbits()
	{
		Set<VarRequirement> varbits = new HashSet<>();
		for (VarRequirement req : varRequirements)
		{
			if (req.isVarbit())
			{
				varbits.add(req);
			}
		}
		return varbits;
	}

	/**
	 * Whether this transport has a varbit requirement with the given ID.
	 */
	public boolean hasVarbit(int varbitId)
	{
		for (VarRequirement req : varRequirements)
		{
			if (req.isVarbit() && req.getId() == varbitId)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets varplayer requirements (filtered from varRequirements).
	 * For backward compatibility with code that needs separate varplayer access.
	 */
	public Set<VarRequirement> getVarPlayers()
	{
		Set<VarRequirement> varPlayers = new HashSet<>();
		for (VarRequirement req : varRequirements)
		{
			if (req.isVarPlayer())
			{
				varPlayers.add(req);
			}
		}
		return varPlayers;
	}

	public static class TransportBuilder
	{
		private final int[] skillLevels = new int[Skill.values().length + 3];
		private final Set<VarRequirement> varRequirements = new HashSet<>();
		private final FieldParser<int[]> skillParser = new SkillRequirementParser();
		private final FieldParser<TransportItems> itemParser = new ItemRequirementParser();
		private final FieldParser<Set<Quest>> questParser = new QuestParser();
		private final VarRequirementParser varbitParser = VarRequirementParser.forVarbits();
		private final VarRequirementParser varPlayerParser = VarRequirementParser.forVarPlayers();
		private final FieldParser<Integer> worldPointParser = new WorldPointParser();
		private final Set<Quest> quests = new HashSet<>();
		private int origin = UNDEFINED_ORIGIN;
		private int destination = UNDEFINED_DESTINATION;
		private TransportItems itemRequirements;
		private TransportType type;
		private int duration;
		private String displayInfo = null;
		private boolean isConsumable = false;
		private int maxWildernessLevel = -1;
		private String objectInfo = null;
		private LeagueRegion regionOverride = null;

		public TransportBuilder origin(int origin)
		{
			this.origin = origin;
			return this;
		}

		public TransportBuilder origin(String value)
		{
			this.origin = worldPointParser.parse(value);
			return this;
		}

		public TransportBuilder destination(int destination)
		{
			this.destination = destination;
			return this;
		}

		public TransportBuilder destination(String value)
		{
			this.destination = worldPointParser.parse(value);
			return this;
		}

		public TransportBuilder skillLevels(String value)
		{
			int[] parsedSkills = skillParser.parse(value);
			for (int i = 0; i < skillLevels.length; i++)
			{
				if (parsedSkills[i] > 0)
				{
					skillLevels[i] = parsedSkills[i];
				}
			}
			return this;
		}

		public TransportBuilder startSkillLevels(int[] otherSkillLevels)
		{
			for (int i = 0; i < skillLevels.length; i++)
			{
				this.skillLevels[i] = Math.max(this.skillLevels[i], otherSkillLevels[i]);
			}
			return this;
		}

		public TransportBuilder quests(Set<Quest> quests)
		{
			this.quests.addAll(quests);
			return this;
		}

		public TransportBuilder quests(String value)
		{
			this.quests.addAll(questParser.parse(value));
			return this;
		}

		public TransportBuilder itemRequirements(TransportItems itemRequirements)
		{
			this.itemRequirements = itemRequirements;
			return this;
		}

		public TransportBuilder itemRequirements(String value)
		{
			this.itemRequirements = itemParser.parse(value);
			return this;
		}

		public TransportBuilder type(TransportType type)
		{
			this.type = type;
			return this;
		}

		public TransportBuilder duration(int duration)
		{
			this.duration = Math.max(this.duration, duration);
			return this;
		}

		public TransportBuilder duration(String value)
		{
			if (value != null && !value.isEmpty())
			{
				try
				{
					this.duration = Integer.parseInt(value);
				}
				catch (NumberFormatException e)
				{
					log.error("Invalid tick duration: {}", value);
				}
			}
			return this;
		}

		public TransportBuilder displayInfo(String displayInfo)
		{
			this.displayInfo = displayInfo;
			return this;
		}

		public TransportBuilder isConsumable(boolean isConsumable)
		{
			this.isConsumable |= isConsumable;
			return this;
		}

		public TransportBuilder isConsumable(String value)
		{
			this.isConsumable = "T".equals(value) || "yes".equalsIgnoreCase(value);
			return this;
		}

		public TransportBuilder maxWildernessLevel(int maxWildernessLevel)
		{
			this.maxWildernessLevel = Math.max(this.maxWildernessLevel, maxWildernessLevel);
			return this;
		}

		public TransportBuilder maxWildernessLevel(String value)
		{
			if (value != null && !value.isEmpty())
			{
				try
				{
					this.maxWildernessLevel = Integer.parseInt(value);
				}
				catch (NumberFormatException e)
				{
					log.error("Invalid wilderness level: {}", value);
				}
			}
			return this;
		}

		public TransportBuilder objectInfo(String objectInfo)
		{
			this.objectInfo = objectInfo;
			return this;
		}

		public TransportBuilder regionOverride(LeagueRegion regionOverride)
		{
			if (regionOverride != null)
			{
				this.regionOverride = regionOverride;
			}
			return this;
		}

		public TransportBuilder regionOverride(String value)
		{
			if (value != null && !value.isEmpty())
			{
				try
				{
					this.regionOverride = LeagueRegion.valueOf(value.trim().toUpperCase());
				}
				catch (IllegalArgumentException e)
				{
					log.error("Invalid region override: {}", value);
				}
			}
			return this;
		}

		public TransportBuilder varRequirements(Set<VarRequirement> requirements)
		{
			this.varRequirements.addAll(requirements);
			return this;
		}

		public TransportBuilder varbits(String value)
		{
			this.varRequirements.addAll(varbitParser.parse(value));
			return this;
		}

		public TransportBuilder varPlayers(String value)
		{
			this.varRequirements.addAll(varPlayerParser.parse(value));
			return this;
		}

		public Transport build()
		{
			Transport transport = new Transport();
			transport.origin = this.origin;
			transport.destination = this.destination;
			transport.skillLevels = compactSkills(this.skillLevels);
			transport.quests = compactQuests(this.quests);
			transport.itemRequirements = this.itemRequirements;
			transport.type = this.type;
			transport.duration = this.duration;
			transport.displayInfo = this.displayInfo;
			transport.isConsumable = this.isConsumable;
			transport.maxWildernessLevel = this.maxWildernessLevel;
			transport.objectInfo = this.objectInfo;
			transport.varRequirements = compact(this.varRequirements);
			transport.regionOverride = this.regionOverride;

			// Post-build validation/refinement
			if (transport.type != null && transport.type.isTeleport())
			{
				transport.duration = Math.max(transport.duration, 1);
			}

			if (transport.type != null)
			{
				transport.type = transport.type.refine(transport.skillLevels);
			}

			return transport;
		}
	}
}
