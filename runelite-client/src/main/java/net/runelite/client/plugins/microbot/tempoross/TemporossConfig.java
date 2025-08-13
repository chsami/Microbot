package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.tempoross.enums.HarpoonType;

@ConfigGroup("microbot-tempoross")
@ConfigInformation("<h2>S-1D Tempoross</h2>\n" +
        "<h3>Version: " + TemporossScript.VERSION + "</h3>\n" +
        "<p>1. <strong>Start the bot outside of the minigame area</strong> to ensure proper functionality.</p>\n" +
        "<p></p>\n" +
        "<p>2. <strong>Do not toggle rope/hammer if Spirit Angler's/Imcando hammer is toggled</strong></p>\n" +
        "<p></p>\n" +
        "<p>3. <strong>Solo Mode:</strong> GPU plugin recommended to be on for quick path detection, <em>6 buckets</em> is recommended as a minimum, recommended to start solo mode with full config options in inventory as time is of the essence. You also need a MINIMUM of <strong>19</strong>  free inv slots at all time.</p>\n" +
        "<p></p>\n"
)

public interface TemporossConfig extends Config {
    //sections
    // Solo
    // Inventory
    // Equipment
    // Tools
    // Overlay

    @ConfigSection(
        name = "Solo",
        description = "Solo settings",
        position = 1,
        closedByDefault = true
    )
    String soloSection = "Solo";

    @ConfigSection(
        name = "Inventory",
        description = "Inventory settings",
        position = 2,
        closedByDefault = true
    )
    String generalSection = "Inventory";

    @ConfigSection(
        name = "Equipment",
        description = "Equipment settings",
        position = 3,
        closedByDefault = true
    )
    String equipmentSection = "Equipment";

    @ConfigSection(
        name = "Harpoon",
        description = "Harpoon settings",
        position = 4,
        closedByDefault = true
    )
    String harpoonSection = "Harpoon";
    
    @ConfigSection(
        name = "Overlay",
        description = "Overlay settings",
        position = 5,
        closedByDefault = true
    )
    String overlaySection = "Overlay";

    // Inventory settings
    // number of buckets to bring (default 6)
    @ConfigItem(
        keyName = "buckets",
        name = "Buckets",
        description = "Number of buckets to bring",
        position = 1,
        section = generalSection
    )
    default int buckets() {
        return 6;
    }


    // boolean to bring a hammer
    @ConfigItem(
        keyName = "hammer",
        name = "Hammer",
        description = "Bring a hammer",
        position = 2,
        section = generalSection
    )
    default boolean hammer() {
        return false;
    }


    // boolean to bring a rope
    @ConfigItem(
        keyName = "rope",
        name = "Rope",
        description = "Bring a rope",
        position = 3,
        section = generalSection
    )
    default boolean rope() {
        return false;
    }
    // Solo settings
    // boolean to play solo
    @ConfigItem(
        keyName = "solo",
        name = "Solo",
        description = "Play solo",
        position = 1,
        section = soloSection
    )
    default boolean solo() {
        return false;
    }




    // Equipment settings
    // boolean if we have Spirit Angler's outfit
    @ConfigItem(
            keyName = "spiritAnglers",
            name = "Spirit Angler's",
            description = "Spirit Angler's outfit",
            position = 1,
            section = equipmentSection
    )
    default boolean spiritAnglers() {
        return false;
    }

    // boolean if we are using Imcando hammer (off-hand)
    @ConfigItem(
            keyName = "imcandoHammerOffHand",
            name = "Imcando hammer (off-hand)",
            description = "Imcando Hammer Offhand act as hammer when equipped",
            position = 2,
            section = equipmentSection
    )
    default boolean imcandoHammerOffHand() {
        return false;
    }


    // Harpoon settings
    // Harpoon type to use
    @ConfigItem(
        keyName = "harpoonType",
        name = "Harpoon",
        description = "Harpoon type to use",
        position = 1,
        section = harpoonSection
    )
    default HarpoonType harpoonType() {
        return HarpoonType.INFERNAL_HARPOON;
    }

    @ConfigItem(
            keyName = "enableHarpoonSpec",
            name = "Use Harpoon Special",
            description = "Use the harpoon's special attack when attacking Tempoross.",
            position = 2,
            section = harpoonSection
    )
    default boolean enableHarpoonSpec() {
        return false;
    }
    
    @ConfigItem(
        keyName = "enableOverlay",
        name = "Show Area Overlay",
        description = "Toggle the Tempoross Area overlay on/off",
        position = 1,
        section = overlaySection
    )
    default boolean enableOverlay() {
        return false;
    }

    @ConfigItem(
        keyName = "showProgressionOverlay",
        name = "Show Progression Overlay",
        description = "Show or hide the Tempoross Progression Overlay",
        position = 2,
        section = overlaySection
    )
    default boolean showProgressionOverlay() {
        return false;
    }
    
    @ConfigItem(
        keyName = "showStatsOverlay",
        name = "Show Stats Overlay",
        description = "Show or hide the Tempoross Stats Overlay",
        position = 3,
        section = overlaySection
    )
    default boolean showStatsOverlay() {
        return true;
    }
}