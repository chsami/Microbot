package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)
@ConfigInformation("Press 'CTRL + X' to stop the webwalker automatically.")
public interface ShortestPathConfig extends Config {
    @ConfigSection(
            name = "Settings",
            description = "Options for the pathfinding",
            position = 0
    )
    String sectionSettings = "sectionSettings";

    @ConfigItem(
            keyName = "avoidWilderness",
            name = "Avoid wilderness",
            description = "Whether the wilderness should be avoided if possible<br>" +
                    "(otherwise, will e.g. use wilderness lever from Edgeville to Ardougne)",
            position = 0,
            section = sectionSettings
    )
    default boolean avoidWilderness() {
        return true;
    }

    @ConfigItem(
            keyName = "useAgilityShortcuts",
            name = "Use agility shortcuts",
            description = "Whether to include agility shortcuts in the path.<br>" +
                    "You must also have the required agility level",
            position = 1,
            section = sectionSettings
    )
    default boolean useAgilityShortcuts() {
        return true;
    }

    @ConfigItem(
            keyName = "useGrappleShortcuts",
            name = "Use grapple shortcuts",
            description = "Whether to include crossbow grapple agility shortcuts in the path.<br>" +
                    "You must also have the required agility, ranged and strength levels",
            position = 2,
            section = sectionSettings
    )
    default boolean useGrappleShortcuts() {
        return false;
    }

    @ConfigItem(
            keyName = "useBoats",
            name = "Use boats",
            description = "Whether to include small boats in the path<br>" +
                    "(e.g. the boat to Fishing Platform)",
            position = 3,
            section = sectionSettings
    )
    default boolean useBoats() {
        return true;
    }

    @ConfigItem(
            keyName = "useCanoes",
            name = "Use canoes",
            description = "Whether to include canoes in the path",
            position = 4,
            section = sectionSettings
    )
    default boolean useCanoes() {
        return true;
    }

    @ConfigItem(
            keyName = "useCharterShips",
            name = "Use charter ships",
            description = "Whether to include charter ships in the path",
            position = 5,
            section = sectionSettings
    )
    default boolean useCharterShips() {
        return true;
    }

    @ConfigItem(
            keyName = "useShips",
            name = "Use ships",
            description = "Whether to include passenger ships in the path<br>" +
                    "(e.g. the customs ships to Karamja)",
            position = 6,
            section = sectionSettings
    )
    default boolean useShips() {
        return true;
    }

    @ConfigItem(
            keyName = "useFairyRings",
            name = "Use fairy rings",
            description = "Whether to include fairy rings in the path.<br>" +
                    "You must also have completed the required quests or miniquests",
            position = 7,
            section = sectionSettings
    )
    default boolean useFairyRings() {
        return true;
    }

    @ConfigItem(
            keyName = "useGnomeGliders",
            name = "Use gnome gliders",
            description = "Whether to include gnome gliders in the path",
            position = 8,
            section = sectionSettings
    )
    default boolean useGnomeGliders() {
        return true;
    }

    @ConfigItem(
            keyName = "useMinecarts",
            name = "Use minecarts",
            description = "Whether to include minecarts in the path<br>" +
                    "(e.g. the Keldagrim and Lovakengj minecart networks)",
            position = 9,
            section = sectionSettings
    )
    default boolean useMinecarts() {
        return true;
    }

    @ConfigItem(
            keyName = "useSpiritTrees",
            name = "Use spirit trees",
            description = "Whether to include spirit trees in the path",
            position = 10,
            section = sectionSettings
    )
    default boolean useSpiritTrees() {
        return true;
    }

    @ConfigItem(
            keyName = "useTeleportationItems",
            name = "Use teleportation items",
            description = "Whether to include teleportation items from the player's inventory and equipment.<br>" +
                    "Options labelled (perm) only use permanent non-charge items.",
            position = 11,
            section = sectionSettings
    )
    default TeleportationItem useTeleportationItems() {
        return TeleportationItem.INVENTORY;
    }

    @ConfigItem(
            keyName = "useTeleportationLevers",
            name = "Use teleportation levers",
            description = "Whether to include teleportation levers in the path<br>" +
                    "(e.g. the lever from Edgeville to Wilderness)",
            position = 12,
            section = sectionSettings
    )
    default boolean useTeleportationLevers() {
        return true;
    }

    @ConfigItem(
            keyName = "useTeleportationPortals",
            name = "Use teleportation portals",
            description = "Whether to include teleportation portals in the path<br>" +
                    "(e.g. the portal from Ferox Enclave to Castle Wars)",
            position = 13,
            section = sectionSettings
    )
    default boolean useTeleportationPortals() {
        return true;
    }

    @ConfigItem(
            keyName = "useTeleportationSpells",
            name = "Use teleportation spells",
            description = "Whether to include teleportation spells in the path",
            position = 14,
            section = sectionSettings
    )
    default boolean useTeleportationSpells() {
        return true;
    }
    
    @ConfigItem(
            keyName = "useTeleportationMinigames",
            name = "Use teleportation to minigames",
            description = "Whether to include teleportation to minigames/activities/grouping in the path<br>" +
                    "(e.g. the Nightmare Zone minigame teleport). These teleports share a 20 minute cooldown.",
            position = 15,
            section = sectionSettings
    )
    default boolean useTeleportationMinigames() {
        return false;
    }

    @ConfigItem(
            keyName = "useWildernessObelisks",
            name = "Use wilderness obelisks",
            description = "Whether to include wilderness obelisks in the path",
            position = 16,
            section = sectionSettings
    )
    default boolean useWildernessObelisks() {
        return true;
    }

    @ConfigItem(
            keyName = "useNpcs",
            name = "Use npcs",
            description = "Whether to include npc transports in the path<br>(e.g. Tree gnome village maze or Lumbridge cellar)",
            position =  17,
            section = sectionSettings
    )
    default boolean useNpcs() {
        return true;
    }

    @ConfigItem(
            keyName = "useQuetzals",
            name = "Use quetzals",
            description = "Whether to include quetzals in the path.<br>",
            position = 18,
            section = sectionSettings
    )
    default boolean useQuetzals() {
        return true;
    }

    @ConfigItem(
            keyName = "useMagicCarpets",
            name = "Use Magic Carpets",
            description = "Whether to include magic carpets in the path.<br>",
            position = 19,
            section = sectionSettings
    )
    default boolean useMagicCarpets() {
        return true;
    }

    @ConfigItem(
            keyName = "cancelInstead",
            name = "Cancel instead of recalculating",
            description = "Whether the path should be cancelled rather than recalculated " +
                    "when the recalculate distance limit is exceeded",
            position = 20,
            section = sectionSettings
    )
    default boolean cancelInstead() {
        return false;
    }

    @ConfigItem(
            keyName = "showTransportInfo",
            name = "Show transport info",
            description = "Whether to display transport destination hint info, e.g. which chat option and text to click",
            position = 21,
            section = sectionSettings
    )
    default boolean showTransportInfo() {
        return true;
    }

    @ConfigItem(
            keyName = "distanceBeforeUsingTeleports",
            name = "Teleport distance",
            description = "Distance before using a teleport<br>" +
                    "(This is to avoid using teleports when you are to close",
            position = 22,
            section = sectionSettings
    )
    default int distanceBeforeUsingTeleport() {
        return 20;
    }

    @Range(
            min = -1,
            max = 20000
    )
    @ConfigItem(
            keyName = "recalculateDistance",
            name = "Recalculate distance",
            description = "Distance from the path the player should be for it to be recalculated (-1 for never)",
            position = 23,
            section = sectionSettings
    )
    default int recalculateDistance() {
        return 10;
    }

    @Range(
            min = -1,
            max = 50
    )
    @ConfigItem(
            keyName = "finishDistance",
            name = "Finish distance",
            description = "Distance from the target tile at which the path should be ended (-1 for never)",
            position = 24,
            section = sectionSettings
    )
    default int reachedDistance() {
        return 5;
    }

    @ConfigItem(
            keyName = "showTileCounter",
            name = "Show tile counter",
            description = "Whether to display the number of tiles travelled, number of tiles remaining or disable counting",
            position = 25,
            section = sectionSettings
    )
    default TileCounter showTileCounter() {
        return TileCounter.REMAINING;
    }

    @ConfigItem(
            keyName = "tileCounterStep",
            name = "Tile counter step",
            description = "The number of tiles between the displayed tile counter numbers",
            position = 26,
            section = sectionSettings
    )
    default int tileCounterStep()
    {
        return 1;
    }

    @Units(
            value = Units.TICKS
    )
    @Range(
            min = 1,
            max = 30
    )
    @ConfigItem(
            keyName = "calculationCutoff",
            name = "Calculation cutoff",
            description = "The cutoff threshold in number of ticks (0.6 seconds) of no progress being<br>" +
                    "made towards the path target before the calculation will be stopped",
            position = 27,
            section = sectionSettings
    )
    default int calculationCutoff()
    {
        return 5;
    }

    @ConfigSection(
            name = "Display",
            description = "Options for displaying the path on the world map, minimap and scene tiles",
            position = 1
    )
    String sectionDisplay = "sectionDisplay";

    @ConfigItem(
            keyName = "drawMap",
            name = "Draw path on world map",
            description = "Whether the path should be drawn on the world map",
            position = 0,
            section = sectionDisplay
    )
    default boolean drawMap() {
        return true;
    }

    @ConfigItem(
            keyName = "drawMinimap",
            name = "Draw path on minimap",
            description = "Whether the path should be drawn on the minimap",
            position = 1,
            section = sectionDisplay
    )
    default boolean drawMinimap() {
        return true;
    }

    @ConfigItem(
            keyName = "drawTiles",
            name = "Draw path on tiles",
            description = "Whether the path should be drawn on the game tiles",
            position = 2,
            section = sectionDisplay
    )
    default boolean drawTiles() {
        return true;
    }

    @ConfigItem(
            keyName = "pathStyle",
            name = "Path style",
            description = "Whether to display the path as tiles or a segmented line",
            position = 3,
            section = sectionDisplay
    )
    default TileStyle pathStyle() {
        return TileStyle.TILES;
    }

    @ConfigItem(
            keyName = "showETA",
            name = "Show ETA Overlay",
            description = "Whether to display the ETA in an overlay to your destination",
            position = 4,
            section = sectionDisplay
    )
    default boolean showETA() {
        return false;
    }

    @ConfigItem(
            keyName = "showETAInSeconds",
            name = "Show ETA in Seconds",
            description = "Whether to display the ETA in seconds vs mins:seconds",
            position = 5,
            section = sectionDisplay
    )
    default boolean showInSeconds() {
        return false;
    }

    @ConfigSection(
            name = "Colours",
            description = "Colours for the path map, minimap and scene tiles",
            position = 2
    )
    String sectionColours = "sectionColours";

    @Alpha
    @ConfigItem(
            keyName = "colourPath",
            name = "Path",
            description = "Colour of the path tiles on the world map, minimap and in the game scene",
            position = 0,
            section = sectionColours
    )
    default Color colourPath() {
        return new Color(255, 0, 0);
    }

    @Alpha
    @ConfigItem(
            keyName = "colourPathCalculating",
            name = "Calculating",
            description = "Colour of the path tiles while the pathfinding calculation is in progress",
            position = 1,
            section = sectionColours
    )
    default Color colourPathCalculating() {
        return new Color(0, 0, 255);
    }

    @Alpha
    @ConfigItem(
            keyName = "colourTransports",
            name = "Transports",
            description = "Colour of the transport tiles",
            position = 2,
            section = sectionColours
    )
    default Color colourTransports() {
        return new Color(0, 255, 0, 128);
    }

    @Alpha
    @ConfigItem(
            keyName = "colourCollisionMap",
            name = "Collision map",
            description = "Colour of the collision map tiles",
            position = 3,
            section = sectionColours
    )
    default Color colourCollisionMap() {
        return new Color(0, 128, 255, 128);
    }

    @Alpha
    @ConfigItem(
            keyName = "colourText",
            name = "Text",
            description = "Colour of the text of the tile counter and fairy ring codes",
            position = 4,
            section = sectionColours
    )
    default Color colourText() {
        return Color.WHITE;
    }

    @ConfigSection(
            name = "Debug Options",
            description = "Various options for debugging",
            position = 3,
            closedByDefault = true
    )
    String sectionDebug = "sectionDebug";

    @ConfigItem(
            keyName = "drawTransports",
            name = "Draw transports",
            description = "Whether transports should be drawn",
            position = 0,
            section = sectionDebug
    )
    default boolean drawTransports() {
        return false;
    }

    @ConfigItem(
            keyName = "drawCollisionMap",
            name = "Draw collision map",
            description = "Whether the collision map should be drawn",
            position = 1,
            section = sectionDebug
    )
    default boolean drawCollisionMap() {
        return false;
    }

    @ConfigItem(
            keyName = "drawDebugPanel",
            name = "Show debug panel",
            description = "Toggles displaying the pathfinding debug stats panel",
            position = 2,
            section = sectionDebug
    )
    default boolean drawDebugPanel() {
        return false;
    }
}
