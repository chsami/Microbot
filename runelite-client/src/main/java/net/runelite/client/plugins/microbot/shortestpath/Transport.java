package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class represents a travel point between two WorldPoints.
 */
@Slf4j
public class Transport {
    public static final int TOTAL_LEVEL_INDEX = Skill.values().length;
    public static final int COMBAT_LEVEL_INDEX = TOTAL_LEVEL_INDEX + 1;
    public static final int QUEST_POINTS_INDEX = COMBAT_LEVEL_INDEX + 1;
    public static final int REQUIREMENT_LEVEL_COUNT = QUEST_POINTS_INDEX + 1;

    //START microbot variables
    @Getter
	@Setter
    private String action;
    @Getter
    private int objectId;
    @Getter
    private String name;

    /**
     * A location placeholder different from null to use for permutation transports
     */
    private static final WorldPoint LOCATION_PERMUTATION = new WorldPoint(-1, -1, -1);

    /**
     * The starting point of this transport
     */
    @Getter
    private WorldPoint origin = null;

    /**
     * The ending point of this transport
     */
    @Getter
    private WorldPoint destination = null;

    /**
     * The skill levels required to use this transport
     */
    @Getter
    private final int[] skillLevels = new int[REQUIREMENT_LEVEL_COUNT];

    /**
     * The quests required to use this transport
     */
    @Getter
    private Map<Quest, QuestState> quests = new HashMap<>();

    /**
     * Compatibility view of the item IDs required to use this transport. New code should use
     * {@link #getItemRequirements()} so AND groups and quantities are not discarded.
     */
    @Getter
    private Set<Set<Integer>> itemIdRequirements = new HashSet<>();

    /**
     * Lossless item requirements. Entries are AND-ed; alternatives within an entry are OR-ed.
     * {@link #itemIdRequirements} remains as the compatibility view used by older callers.
     */
    @Getter
    private List<TransportItemRequirement> itemRequirements = new ArrayList<>();

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
    private String displayInfo;

    /**
     * If this is an item transport, this tracks if it is consumable (as opposed to having infinite uses)
     */
    @Getter
    private boolean isConsumable = false;

    /**
     * The maximum wilderness level that the transport can be used in
     */
    @Getter
    private int maxWildernessLevel = -1;

    /**
     * Any varbits to check for the transport to be valid. All must pass for a transport to be valid
     */
    @Getter
    private final Set<TransportVarbit> varbits = new HashSet<>();

    /**
     * Any varplayers to check for the transport to be valid. All must pass for a transport to be valid
     */
    @Getter
    private final Set<TransportVarPlayer> varplayers = new HashSet<>();

    @Getter
    private String currencyName = "";
    @Getter
    private int currencyAmount = 0;

    /**
     * Transport requires player to be in a members world
     */
    @Getter
    private boolean isMembers = false;



    /**
     * Creates a new transport from an origin-only transport
     * and a destination-only transport, and merges requirements
     */
    public Transport(Transport origin, Transport destination) {
        this.origin = origin.origin;
        this.destination = destination.destination;

        for (int i = 0; i < skillLevels.length; i++) {
            this.skillLevels[i] = Math.max(
                    origin.skillLevels[i],
                    destination.skillLevels[i]);
        }

        this.quests.putAll(origin.quests);
        this.quests.putAll(destination.quests);

        this.itemIdRequirements.addAll(origin.itemIdRequirements);
        this.itemIdRequirements.addAll(destination.itemIdRequirements);
        this.itemRequirements.addAll(origin.itemRequirements);
        this.itemRequirements.addAll(destination.itemRequirements);

        this.type = origin.type;

        this.duration = Math.max(
                origin.duration,
                destination.duration);

        this.displayInfo = destination.displayInfo;

        this.isConsumable |= origin.isConsumable;
        this.isConsumable |= destination.isConsumable;

        this.maxWildernessLevel = Math.max(
                origin.maxWildernessLevel,
                destination.maxWildernessLevel);

        this.varbits.addAll(origin.varbits);
        this.varbits.addAll(destination.varbits);

        this.varplayers.addAll(origin.varplayers);
        this.varplayers.addAll(destination.varplayers);

        //START microbot variables
        this.name = origin.getName();
        this.objectId = origin.getObjectId();
        this.action = origin.getAction();
        this.currencyName = origin.getCurrencyName();
        this.currencyAmount = origin.getCurrencyAmount();
        this.isMembers = origin.isMembers;
        //END microbot variables
    }

    /**
     * Base Transport constructor
     */
    public Transport(WorldPoint origin, WorldPoint destination, String displayInfo, TransportType transportType, boolean isMember, int duration) {
        this.origin = origin;
        this.destination = destination;
        this.displayInfo = displayInfo;
        this.type = transportType;
        this.isMembers = isMember;
        this.duration = duration;
    }

    /**
     * Object interaction Transport constructor
     */
    public Transport(WorldPoint origin, WorldPoint destination, String displayInfo, TransportType transportType, boolean isMember, String action, String target, int objectId) {
		this(origin, destination, displayInfo, transportType, isMember, action, target, objectId, 1);
	}

	/** Object interaction transport with an explicit planner cost in ticks. */
	public Transport(WorldPoint origin, WorldPoint destination, String displayInfo, TransportType transportType,
	                 boolean isMember, String action, String target, int objectId, int duration) {
		this(origin, destination, displayInfo, transportType, isMember, duration);
        this.action = action;
        this.name = target;
        this.objectId = objectId;
    }

    /**
     * Transport constructor with item requirements
     */
    public Transport(WorldPoint destination, String displayInfo, TransportType transportType, boolean isMember, int maxWildernessLevel, Set<Set<Integer>> itemIdRequirements) {
        this(null, destination, displayInfo, transportType, isMember, 1);
        this.maxWildernessLevel = maxWildernessLevel;
        setItemIdRequirements(itemIdRequirements);
    }

    /**
     * Transport constructor with skill requirements
     */
    public Transport(WorldPoint destination, String displayInfo, TransportType transportType, boolean isMember, int maxWildernessLevel, Map<Skill, Integer> skillRequirement) {
        this(null, destination, displayInfo, transportType, isMember, 1);
        this.maxWildernessLevel = maxWildernessLevel;
        if (skillRequirement != null) {
            for (Map.Entry<Skill, Integer> entry : skillRequirement.entrySet()) {
                this.skillLevels[entry.getKey().ordinal()] = entry.getValue();
            }
        }
    }

    Transport(Map<String, String> fieldMap, TransportType transportType) {
        final String DELIM = " ";
        final String DELIM_MULTI = ";";
        final String DELIM_STATE = "=";

        String value;

        // If the origin field is null the transport is a teleportation item or spell
        // If the origin field has 3 elements it is a coordinate of a transport
        // Otherwise it is a transport that needs to be expanded into all permutations (e.g. fairy ring)
        if ((value = fieldMap.get("Origin")) != null) {
            String[] originArray = value.split(DELIM);
            origin = originArray.length == 3 ? new WorldPoint(
                    Integer.parseInt(originArray[0]),
                    Integer.parseInt(originArray[1]),
                    Integer.parseInt(originArray[2])) : LOCATION_PERMUTATION;
        }

        if ((value = fieldMap.get("Destination")) != null) {
            String[] destinationArray = value.split(DELIM);
            destination = destinationArray.length == 3 ? new WorldPoint(
                    Integer.parseInt(destinationArray[0]),
                    Integer.parseInt(destinationArray[1]),
                    Integer.parseInt(destinationArray[2])) : LOCATION_PERMUTATION;
        }

        //START microbot variables
        if ((value = fieldMap.get("menuOption menuTarget objectID")) != null && !value.trim().isEmpty()) {
            value = value.trim(); // Remove leading/trailing spaces

            // Microbot historically used semicolons while upstream uses whitespace. In the
            // whitespace form the option is one token, the object id is the final numeric token,
            // and the target may contain spaces.
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^([^;]+);([^;]+);(\\d+)$")
                    .matcher(value);
            if (!matcher.matches()) {
                matcher = java.util.regex.Pattern
                        .compile("^(\\S+)\\s+(.+?)\\s+(\\d+)$")
                        .matcher(value);
            }

            if (matcher.matches()) {
                // Extract matched groups
                action = matcher.group(1).trim();   // First group: menuOption (action)
                name = matcher.group(2).trim();    // Second group: menuTarget (name)
                objectId = Integer.parseInt(matcher.group(3).trim()); // Third group: objectID
            } else {
                log.debug("Skipped invalid menuOption/menuTarget/objectID value: {}", value);
            }
        }

        if ((value = fieldMap.get("Currency")) != null) {
            String[] parts = value.split(DELIM);
            if (parts.length > 1) {
                try {
                    currencyAmount = Integer.parseInt(parts[0].trim());
                    currencyName = parts[1].trim();
                } catch (NumberFormatException e) {
                    log.debug("Skipping invalid Currency field: {}", value);
                }
            }
        }
        //END microbot variables

        if ((value = fieldMap.get("Skills")) != null && !value.trim().isEmpty()) {
            String[] skillRequirements = value.split(DELIM_MULTI);

            for (String requirement : skillRequirements) {
                String[] levelAndSkill = requirement.trim().split("\\s+", 2);

                if (levelAndSkill.length < 2) {
                    continue;
                }

                int level = Integer.parseInt(levelAndSkill[0]);
                String skillName = levelAndSkill[1].trim();

                Skill[] skills = Skill.values();
                for (int i = 0; i < skills.length; i++) {
                    if (skills[i].getName().equals(skillName)) {
                        skillLevels[i] = level;
                        break;
                    }
                }
                String normalizedSkillName = skillName.toLowerCase(Locale.ROOT);
                if (normalizedSkillName.startsWith("total")) {
                    skillLevels[TOTAL_LEVEL_INDEX] = level;
                } else if (normalizedSkillName.startsWith("combat")) {
                    skillLevels[COMBAT_LEVEL_INDEX] = level;
                } else if (normalizedSkillName.startsWith("quest")) {
                    skillLevels[QUEST_POINTS_INDEX] = level;
                }
            }
        }

        if ((value = fieldMap.get("Items")) != null && !value.trim().isEmpty()) {
            setItemRequirements(TransportItemRequirement.parseRequirements(value));
        } else if ((value = fieldMap.get("Item IDs")) != null && !value.trim().isEmpty()) {
            if (value.contains("=") || value.contains("&") || value.contains("|")) {
                setItemRequirements(TransportItemRequirement.parseRequirements(value));
            } else {
                Set<Set<Integer>> legacyGroups = new LinkedHashSet<>();
                for (String listIds : value.split(DELIM_MULTI)) {
                    Set<Integer> group = new LinkedHashSet<>();
                    for (String item : listIds.trim().split("\\s+")) {
                        if (!item.isEmpty()) {
                            group.add(Integer.parseInt(item));
                        }
                    }
                    if (!group.isEmpty()) {
                        legacyGroups.add(group);
                    }
                }
                setItemIdRequirements(legacyGroups);
            }
        }

        if ((value = fieldMap.get("Quests")) != null && !value.trim().isEmpty()) {
            this.quests = parseQuestStates(value);
        }

        if ((value = fieldMap.get("Duration")) != null && !value.trim().isEmpty()) {
            this.duration = Integer.parseInt(value);
        }

        if (TransportType.isTeleport(transportType, origin)) {
            // Teleports should always have a non-zero wait,
            // so the pathfinder doesn't calculate the cost by distance
            this.duration = Math.max(this.duration, 1);
        }

        if ((value = fieldMap.get("Display info")) != null) {
            this.displayInfo = value;
        }

        if ((value = fieldMap.get("Consumable")) != null) {
            this.isConsumable = "T".equals(value) || "yes".equals(value.toLowerCase());
        }

        if ((value = fieldMap.get("Wilderness level")) != null && !value.trim().isEmpty()) {
            this.maxWildernessLevel = Integer.parseInt(value);
        }

        if ((value = fieldMap.get("isMembers")) != null && !value.trim().isEmpty()) {
            this.isMembers = "Y".equals(value.trim()) || "yes".equals(value.trim().toLowerCase());
        }

        if ((value = fieldMap.get("Varbits")) != null && !value.trim().isEmpty()) {
            for (String varbitCheck : value.split(DELIM_MULTI)) {
                if (varbitCheck.isBlank()) {
                    continue;
                }
                String[] parts;
                TransportVarbit.Operator operator;

                if (varbitCheck.contains(">")) {
                    parts = varbitCheck.split(">");
                    operator = TransportVarbit.Operator.GREATER_THAN;
                } else if (varbitCheck.contains("<")) {
                    parts = varbitCheck.split("<");
                    operator = TransportVarbit.Operator.LESS_THAN;
                } else if (varbitCheck.contains("=")) {
                    parts = varbitCheck.split("=");
                    operator = TransportVarbit.Operator.EQUAL;
                } else if (varbitCheck.contains("&")) {
                    parts = varbitCheck.split("&");
                    operator = TransportVarbit.Operator.BIT_SET;
                } else if (varbitCheck.contains("@")) {
                    parts = varbitCheck.split("@");
                    operator = TransportVarbit.Operator.COOLDOWN_MINUTES;
                } else {
                    log.debug("Skipping invalid varbit token: {}", varbitCheck);
                    continue;
                }

                try {
                    int varbitId = Integer.parseInt(parts[0].trim());
                    int varbitValue = Integer.parseInt(parts[1].trim());
                    varbits.add(new TransportVarbit(varbitId, varbitValue, operator));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    log.debug("Skipping malformed varbit token: {}", varbitCheck);
                }
            }
        }

        value = fieldMap.get("Varplayers");
        if ((value == null || value.trim().isEmpty())) {
            value = fieldMap.get("VarPlayers");
        }
        if (value != null && !value.trim().isEmpty()) {
            for (String varplayerCheck : value.split(DELIM_MULTI)) {
                if (varplayerCheck.isBlank()) {
                    continue;
                }
                String[] parts;
                TransportVarPlayer.Operator operator;

                if (varplayerCheck.contains(">")) {
                    parts = varplayerCheck.split(">");
                    operator = TransportVarPlayer.Operator.GREATER_THAN;
                } else if (varplayerCheck.contains("<")) {
                    parts = varplayerCheck.split("<");
                    operator = TransportVarPlayer.Operator.LESS_THAN;
                } else if (varplayerCheck.contains("=")) {
                    parts = varplayerCheck.split("=");
                    operator = TransportVarPlayer.Operator.EQUAL;
                } else if (varplayerCheck.contains("&")) {
                    parts = varplayerCheck.split("&");
                    operator = TransportVarPlayer.Operator.BIT_SET;
                } else if (varplayerCheck.contains("@")) {
                    parts = varplayerCheck.split("@");
                    operator = TransportVarPlayer.Operator.COOLDOWN_MINUTES;
                } else {
                    log.debug("Skipping invalid varplayer token: {}", varplayerCheck);
                    continue;
                }

                try {
                    int varplayerId = Integer.parseInt(parts[0].trim());
                    int varplayerValue = Integer.parseInt(parts[1].trim());
                    varplayers.add(new TransportVarPlayer(varplayerId, varplayerValue, operator));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    log.debug("Skipping malformed varplayer token: {}", varplayerCheck);
                }
            }
        }

        this.type = transportType;
        if (TransportType.AGILITY_SHORTCUT.equals(transportType) &&
                (getRequiredLevel(Skill.RANGED) > 1 || getRequiredLevel(Skill.STRENGTH) > 1)) {
            this.type = TransportType.GRAPPLE_SHORTCUT;
        }
    }

    /**
     * The skill level required to use this transport
     */
    private int getRequiredLevel(Skill skill) {
        return skillLevels[skill.ordinal()];
    }

    public int getRequiredTotalLevel() {
        return skillLevels[TOTAL_LEVEL_INDEX];
    }

    public int getRequiredCombatLevel() {
        return skillLevels[COMBAT_LEVEL_INDEX];
    }

    public int getRequiredQuestPoints() {
        return skillLevels[QUEST_POINTS_INDEX];
    }

    /**
     * Updates the legacy compatibility view. Historically every ID in this structure was treated as
     * an alternative, regardless of its nested set, so preserve that behavior as one OR requirement.
     */
    public void setItemIdRequirements(Set<Set<Integer>> requirements) {
        Set<Set<Integer>> copied = new LinkedHashSet<>();
        Set<Integer> alternatives = new LinkedHashSet<>();
        if (requirements != null) {
            for (Set<Integer> group : requirements) {
                if (group == null || group.isEmpty()) {
                    continue;
                }
                Set<Integer> copiedGroup = new LinkedHashSet<>(group);
                copied.add(Collections.unmodifiableSet(copiedGroup));
                alternatives.addAll(copiedGroup);
            }
        }
        this.itemIdRequirements = copied;
        this.itemRequirements = alternatives.isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(Collections.singletonList(
                        TransportItemRequirement.legacyAlternatives(alternatives)));
    }

    private void setItemRequirements(List<TransportItemRequirement> requirements) {
        this.itemRequirements = requirements == null
                ? new ArrayList<>()
                : new ArrayList<>(requirements);
        Set<Set<Integer>> compatibility = new LinkedHashSet<>();
        for (TransportItemRequirement requirement : this.itemRequirements) {
            compatibility.add(Collections.unmodifiableSet(new LinkedHashSet<>(requirement.getAllItemIds())));
        }
        this.itemIdRequirements = compatibility;
    }

    /**
     * Whether the transport has one or more quest requirements
     */
    public boolean isQuestLocked() {
        return !quests.isEmpty();
    }

	private static Map<Quest, QuestState> parseQuestStates(String questStatesCombined)
	{
		Map<Quest, QuestState> questStateMap = new HashMap<>();
		String[] entries = questStatesCombined.split(";");
		for (String entry : entries)
		{
			String questName;
			String stateStr;
			if (entry.contains("=")) {
				String[] parts = entry.split("=");
				if (parts.length != 2) continue;
				questName = parts[0].trim();
				stateStr = parts[1].trim();
			} else {
				questName = entry.trim();
				stateStr = QuestState.FINISHED.name();
			}
			for (Quest quest : Quest.values())
			{
				if (quest.getName().equalsIgnoreCase(questName))
				{
					try
					{
						QuestState state = QuestState.valueOf(stateStr);
						questStateMap.put(quest, state);
					}
					catch (IllegalArgumentException e)
					{
						// Invalid state string, skip
					}
					break;
				}
			}
		}
		return questStateMap;
	}

    private static void addTransports(Map<WorldPoint, Set<Transport>> transports, String path, TransportType transportType) {
        addTransports(transports, path, transportType, 0);
    }

    private static void addTransports(Map<WorldPoint, Set<Transport>> transports, String path, TransportType transportType, int radiusThreshold) {
        final String DELIM_COLUMN = "\t";
        final String PREFIX_COMMENT = "#";

        try {
            java.io.InputStream stream = ShortestPathPlugin.class.getResourceAsStream(path);
            if (stream == null) {
                log.warn("Transport resource missing, skipping: {}", path);
                return;
            }
            String s = new String(Util.readAllBytes(stream), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            if (!scanner.hasNextLine()) {
                scanner.close();
                log.warn("Transport resource empty, skipping: {}", path);
                return;
            }

            // Header line is the first line in the file and will start with either '#' or '# '
            String headerLine = scanner.nextLine();
            if (headerLine.endsWith("\r")) {
                headerLine = headerLine.substring(0, headerLine.length() - 1);
            }
            headerLine = headerLine.startsWith(PREFIX_COMMENT + " ") ? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT) : headerLine;
            headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
            String[] headers = headerLine.split(DELIM_COLUMN);

            Set<Transport> newTransports = new HashSet<>();

            int lineNumber = 1;
            while (scanner.hasNextLine()) {
                lineNumber++;
                String line = scanner.nextLine();
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }

                if (line.startsWith(PREFIX_COMMENT) || line.isBlank()) {
                    continue;
                }

                String[] fields = line.split(DELIM_COLUMN);
                Map<String, String> fieldMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length) {
                        fieldMap.put(headers[i], fields[i]);
                    }
                }

                try {
                    Transport transport = new Transport(fieldMap, transportType);
                    newTransports.add(transport);
                } catch (RuntimeException e) {
                    log.warn("Skipping transport row {} in {}: {}", lineNumber, path, e.getMessage());
                }
            }
            scanner.close();

            /*
             * A transport with origin A and destination B is one-way and must
             * be duplicated as origin B and destination A to become two-way.
             * Example: key-locked doors
             *
             * A transport with origin A and a missing destination is one-way,
             * but can go from origin A to all destinations with a missing origin.
             * Example: fairy ring AIQ -> <blank>
             *
             * A transport with a missing origin and destination B is one-way,
             * but can go from all origins with a missing destination to destination B.
             * Example: fairy ring <blank> -> AIQ
             *
             * Identical transports from origin A to destination A are skipped, and
             * non-identical transports from origin A to destination A can be skipped
             * by specifying a radius threshold to ignore almost identical coordinates.
             * Example: fairy ring AIQ -> AIQ
             */
            Set<Transport> transportOrigins = new HashSet<>();
            Set<Transport> transportDestinations = new HashSet<>();
            for (Transport transport : newTransports) {
                WorldPoint origin = transport.getOrigin();
                WorldPoint destination = transport.getDestination();
                // Logic to determine ordinary transport vs teleport vs permutation (e.g. fairy ring)
                if ((origin == null && destination == null)
                        || (LOCATION_PERMUTATION.equals(origin) && LOCATION_PERMUTATION.equals(destination))) {
                    continue;
                } else if (!LOCATION_PERMUTATION.equals(origin) && origin != null
                        && LOCATION_PERMUTATION.equals(destination)) {
                    transportOrigins.add(transport);
                } else if (LOCATION_PERMUTATION.equals(origin)
                        && !LOCATION_PERMUTATION.equals(destination) && destination != null) {
                    transportDestinations.add(transport);
                }
                if (!LOCATION_PERMUTATION.equals(origin)
                        && destination != null && !LOCATION_PERMUTATION.equals(destination)
                        && (origin == null || !origin.equals(destination))) {
                    transports.computeIfAbsent(origin, k -> new HashSet<>()).add(transport);
                }
            }
            for (Transport origin : transportOrigins) {
                for (Transport destination : transportDestinations) {
                    if (origin.getOrigin().distanceTo2D(destination.getDestination()) > radiusThreshold) {
                        transports.computeIfAbsent(origin.getOrigin(), k -> new HashSet<>())
                                .add(new Transport(origin, destination));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read transport file {}: {}", path, e.getMessage());
        }
    }

    private static void appendStandardTransportFiles(HashMap<WorldPoint, Set<Transport>> transports) {
        addTransports(transports, "transports.tsv", TransportType.TRANSPORT);
        addTransports(transports, "agility_shortcuts.tsv", TransportType.AGILITY_SHORTCUT);
        addTransports(transports, "boats.tsv", TransportType.BOAT);
        addTransports(transports, "canoes.tsv", TransportType.CANOE);
        addTransports(transports, "charter_ships.tsv", TransportType.CHARTER_SHIP);
        addTransports(transports, "ships.tsv", TransportType.SHIP);
        addTransports(transports, "fairy_rings.tsv", TransportType.FAIRY_RING);
        addTransports(transports, "gnome_gliders.tsv", TransportType.GNOME_GLIDER, 6);
        addTransports(transports, "minecarts.tsv", TransportType.MINECART);
        addTransports(transports, "spirit_trees.tsv", TransportType.SPIRIT_TREE, 5);
        addTransports(transports, "quetzals.tsv", TransportType.QUETZAL, 6);
        addTransports(transports, "teleportation_items.tsv", TransportType.TELEPORTATION_ITEM);
        addTransports(transports, "teleportation_minigames.tsv", TransportType.TELEPORTATION_MINIGAME);
        addTransports(transports, "teleportation_levers.tsv", TransportType.TELEPORTATION_LEVER);
        addTransports(transports, "teleportation_portals.tsv", TransportType.TELEPORTATION_PORTAL);
        addTransports(transports, "teleportation_spells.tsv", TransportType.TELEPORTATION_SPELL);
        addTransports(transports, "wilderness_obelisks.tsv", TransportType.WILDERNESS_OBELISK);
        addTransports(transports, "magic_carpets.tsv", TransportType.MAGIC_CARPET);
        addTransports(transports, "hot_air_balloons.tsv", TransportType.HOT_AIR_BALLOON, 7);
        addTransports(transports, "magic_mushtrees.tsv", TransportType.MAGIC_MUSHTREE, 5);
        addTransports(transports, "seasonal_transports.tsv", TransportType.SEASONAL_TRANSPORT);
        addTransports(transports, "npcs.tsv", TransportType.NPC);
    }

    public static HashMap<WorldPoint, Set<Transport>> loadAllFromResources() {
        HashMap<WorldPoint, Set<Transport>> transports = new HashMap<>();
        appendStandardTransportFiles(transports);
        log.info("Loaded {} transports", transports.size());
        return transports;
    }

    /**
     * Reload transport TSVs from the plugin classpath (same as {@link #loadAllFromResources()}).
     * Apply to {@link PathfinderConfig} via {@link ShortestPathPlugin} config hot-reload.
     */
    public static HashMap<WorldPoint, Set<Transport>> reloadFromResources() {
        return loadAllFromResources();
    }

    // To string method for debugging
    @Override
    public String toString() {
        return "Transport{" +
                "action='" + action + '\'' +
                ", objectId=" + objectId +
                ", name='" + name + '\'' +
                ", origin=" + origin +
                ", destination=" + destination +
                ", skillLevels=" + Arrays.toString(skillLevels) +
                ", quests=" + quests +
                ", itemIdRequirements=" + itemIdRequirements +
                ", itemRequirements=" + itemRequirements +
                ", type=" + type +
                ", duration=" + duration +
                ", displayInfo='" + displayInfo + '\'' +
                ", isConsumable=" + isConsumable +
                ", maxWildernessLevel=" + maxWildernessLevel +
                ", varbits=" + varbits +
                ", varplayers=" + varplayers +
                ", currencyName='" + currencyName + '\'' +
                ", currencyAmount=" + currencyAmount +
                ", isMembers=" + isMembers +
                '}';
    }
}
