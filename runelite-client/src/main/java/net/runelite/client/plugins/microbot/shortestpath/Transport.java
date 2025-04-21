package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class represents a travel point between two WorldPoints.
 */
public class Transport {
    //START microbot variables
    @Getter
    private String action;
    @Getter
    private int objectId;
    @Getter
    private String name;
    //END microbot variables

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
    private final int[] skillLevels = new int[Skill.values().length];

    /**
     * The quests required to use this transport
     */
    @Getter
    private Set<Quest> quests = new HashSet<>();

    /**
     * The ids of items required to use this transport.
     * If the player has **any** of the matching list of items,
     * this transport is valid
     */
    @Getter
    @Setter
    private Set<Set<Integer>> itemIdRequirements = new HashSet<>();

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
    Transport(Transport origin, Transport destination) {
        this.origin = origin.origin;
        this.destination = destination.destination;

        for (int i = 0; i < skillLevels.length; i++) {
            this.skillLevels[i] = Math.max(
                    origin.skillLevels[i],
                    destination.skillLevels[i]);
        }

        this.quests.addAll(origin.quests);
        this.quests.addAll(destination.quests);

        this.itemIdRequirements.addAll(origin.itemIdRequirements);
        this.itemIdRequirements.addAll(destination.itemIdRequirements);

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

            // Regex pattern for semicolon-separated values
            String regex = "^([^;]+);([^;]+);(\\d+)$";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(value);

            if (matcher.matches()) {
                // Extract matched groups
                action = matcher.group(1).trim();   // First group: menuOption (action)
                name = matcher.group(2).trim();    // Second group: menuTarget (name)
                objectId = Integer.parseInt(matcher.group(3).trim()); // Third group: objectID
            } else {
                System.out.println("Skipped invalid value: " + value);
            }
        }
        
        if ((value = fieldMap.get("Currency")) != null) {
            // Split the string by space
            String[] parts = value.split(DELIM);
            if (parts.length > 1) {
                // Parse the first part as an integer amount
                currencyAmount = Integer.parseInt(parts[0]);
                currencyName = parts[1];
            }
        }
        //END microbot variables

        if ((value = fieldMap.get("Skills")) != null && !value.trim().isEmpty()) {
            String[] skillRequirements = value.split(DELIM_MULTI);

            for (String requirement : skillRequirements) {
                String[] levelAndSkill = requirement.split(DELIM);

                if (levelAndSkill.length < 2) {
                    continue;
                }

                int level = Integer.parseInt(levelAndSkill[0]);
                String skillName = levelAndSkill[1];

                Skill[] skills = Skill.values();
                for (int i = 0; i < skills.length; i++) {
                    if (skills[i].getName().equals(skillName)) {
                        skillLevels[i] = level;
                        break;
                    }
                }
            }
        }

        if ((value = fieldMap.get("Item IDs")) != null && !value.trim().isEmpty()) {
            String[] itemIdsList = value.split(DELIM_MULTI);
            for (String listIds : itemIdsList) {
                Set<Integer> multiitemList = new HashSet<>();
                String[] itemIds = listIds.split(DELIM);
                for (String item : itemIds) {
                    int itemId = Integer.parseInt(item);
                    multiitemList.add(itemId);
                }
                itemIdRequirements.add(multiitemList);
            }
        }

        if ((value = fieldMap.get("Quests")) != null && !value.trim().isEmpty()) {
            this.quests = findQuests(value);
        }

        if ((value = fieldMap.get("Duration")) != null && !value.trim().isEmpty()) {
            this.duration = Integer.parseInt(value);
        }

        if (TransportType.isTeleport(transportType)) {
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
                    throw new IllegalArgumentException("Invalid varbit format: " + varbitCheck);
                }

                int varbitId = Integer.parseInt(parts[0]);
                int varbitValue = Integer.parseInt(parts[1]);
                varbits.add(new TransportVarbit(varbitId, varbitValue, operator));
            }
        }

        if ((value = fieldMap.get("Varplayers")) != null && !value.trim().isEmpty()) {
            for (String varplayerCheck : value.split(DELIM_MULTI)) {
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
                    throw new IllegalArgumentException("Invalid varplayer format: " + varplayerCheck);
                }

                int varplayerId = Integer.parseInt(parts[0]);
                int varplayerValue = Integer.parseInt(parts[1]);
                varplayers.add(new TransportVarPlayer(varplayerId, varplayerValue, operator));
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

    /**
     * Whether the transport has one or more quest requirements
     */
    public boolean isQuestLocked() {
        return !quests.isEmpty();
    }

    private static Set<Quest> findQuests(String questNamesCombined) {
        String[] questNames = questNamesCombined.split(";");
        Set<Quest> quests = new HashSet<>();
        for (String questName : questNames) {
            for (Quest quest : Quest.values()) {
                if (quest.getName().equalsIgnoreCase(questName.trim())) {
                    quests.add(quest);
                    break;
                }
            }
        }
        return quests;
    }

    private static void addTransports(Map<WorldPoint, Set<Transport>> transports, String path, TransportType transportType) {
        addTransports(transports, path, transportType, 0);
    }

    private static void addTransports(Map<WorldPoint, Set<Transport>> transports, String path, TransportType transportType, int radiusThreshold) {
        final String DELIM_COLUMN = "\t";
        final String PREFIX_COMMENT = "#";

        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);

            // Header line is the first line in the file and will start with either '#' or '# '
            String headerLine = scanner.nextLine();
            headerLine = headerLine.startsWith(PREFIX_COMMENT + " ") ? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT) : headerLine;
            headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
            String[] headers = headerLine.split(DELIM_COLUMN);

            Set<Transport> newTransports = new HashSet<>();

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

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


                Transport transport = new Transport(fieldMap, transportType);
                newTransports.add(transport);

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
            Microbot.log(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static HashMap<WorldPoint, Set<Transport>> loadAllFromResources() {
        HashMap<WorldPoint, Set<Transport>> transports = new HashMap<>();
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
        addTransports(transports, "npcs.tsv", TransportType.NPC);
        return transports;
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
