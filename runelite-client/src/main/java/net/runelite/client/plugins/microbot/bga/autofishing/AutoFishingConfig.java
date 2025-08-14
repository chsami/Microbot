package net.runelite.client.plugins.microbot.bga.autofishing;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.worldmap.FishingSpotLocation;

@ConfigGroup("AutoFishing")
public interface AutoFishingConfig extends Config {
    
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String GENERAL_SECTION = "general";

    @ConfigSection(
            name = "Anchovies",
            description = "Anchovies fishing locations",
            position = 1,
            closedByDefault = true
    )
    String ANCHOVIES_SECTION = "anchovies";

    @ConfigSection(
            name = "Anglerfish",
            description = "Anglerfish fishing locations", 
            position = 2,
            closedByDefault = true
    )
    String ANGLERFISH_SECTION = "anglerfish";

    @ConfigSection(
            name = "Barbarian Fish",
            description = "Barbarian fish fishing locations",
            position = 3,
            closedByDefault = true
    )
    String BARBARIAN_FISH_SECTION = "barbarianFish";

    @ConfigSection(
            name = "Cave Eel",
            description = "Cave eel fishing locations",
            position = 4,
            closedByDefault = true
    )
    String CAVE_EEL_SECTION = "caveEel";

    @ConfigSection(
            name = "Herring",
            description = "Herring fishing locations",
            position = 5,
            closedByDefault = true
    )
    String HERRING_SECTION = "herring";

    @ConfigSection(
            name = "Karambwan",
            description = "Karambwan fishing locations",
            position = 6,
            closedByDefault = true
    )
    String KARAMBWAN_SECTION = "karambwan";

    @ConfigSection(
            name = "Karambwanji",
            description = "Karambwanji fishing locations",
            position = 7,
            closedByDefault = true
    )
    String KARAMBWANJI_SECTION = "karambwanji";

    @ConfigSection(
            name = "Lava Eel",
            description = "Lava eel fishing locations",
            position = 8,
            closedByDefault = true
    )
    String LAVA_EEL_SECTION = "lavaEel";

    @ConfigSection(
            name = "Lobster",
            description = "Lobster fishing locations",
            position = 9,
            closedByDefault = true
    )
    String LOBSTER_SECTION = "lobster";

    @ConfigSection(
            name = "Monkfish",
            description = "Monkfish fishing locations",
            position = 10,
            closedByDefault = true
    )
    String MONKFISH_SECTION = "monkfish";

    @ConfigSection(
            name = "Salmon",
            description = "Salmon fishing locations",
            position = 11,
            closedByDefault = true
    )
    String SALMON_SECTION = "salmon";

    @ConfigSection(
            name = "Shark",
            description = "Shark fishing locations",
            position = 12,
            closedByDefault = true
    )
    String SHARK_SECTION = "shark";

    @ConfigSection(
            name = "Shrimp",
            description = "Shrimp fishing locations",
            position = 13,
            closedByDefault = true
    )
    String SHRIMP_SECTION = "shrimp";

    @ConfigSection(
            name = "Trout",
            description = "Trout fishing locations",
            position = 14,
            closedByDefault = true
    )
    String TROUT_SECTION = "trout";

    // GENERAL SECTION
    @ConfigItem(
            keyName = "fishToCatch",
            name = "Fish to catch",
            description = "Choose the fish type to catch",
            position = 0,
            section = GENERAL_SECTION
    )
    default Fish fishToCatch() {
        return Fish.SHRIMP;
    }

    @ConfigItem(
            keyName = "useBank",
            name = "Use bank",
            description = "Use bank and walk back to fishing location",
            position = 1,
            section = GENERAL_SECTION
    )
    default boolean useBank() {
        return false;
    }

    @ConfigItem(
            keyName = "harpoonSpec",
            name = "Harpoon spec",
            description = "Choose the harpoon type for special attacks",
            position = 2,
            section = GENERAL_SECTION
    )
    default HarpoonType harpoonSpec() {
        return HarpoonType.NONE;
    }

    // ANCHOVIES SECTION
    @ConfigItem(
            keyName = "anchoviesLocation",
            name = "Anchovies location",
            description = "Choose fishing location for anchovies",
            position = 0,
            section = ANCHOVIES_SECTION
    )
    default AnchoviesLocation anchoviesLocation() {
        return AnchoviesLocation.AUTO;
    }

    // ANGLERFISH SECTION
    @ConfigItem(
            keyName = "anglerfishLocation", 
            name = "Anglerfish location",
            description = "Choose fishing location for anglerfish",
            position = 0,
            section = ANGLERFISH_SECTION
    )
    default AnglerfishLocation anglerfishLocation() {
        return AnglerfishLocation.AUTO;
    }

    // BARBARIAN FISH SECTION
    @ConfigItem(
            keyName = "barbarianFishLocation",
            name = "Barbarian fish location", 
            description = "Choose fishing location for barbarian fish",
            position = 0,
            section = BARBARIAN_FISH_SECTION
    )
    default BarbarianFishLocation barbarianFishLocation() {
        return BarbarianFishLocation.AUTO;
    }

    // CAVE EEL SECTION
    @ConfigItem(
            keyName = "caveEelLocation",
            name = "Cave eel location",
            description = "Choose fishing location for cave eel",
            position = 0,
            section = CAVE_EEL_SECTION
    )
    default CaveEelLocation caveEelLocation() {
        return CaveEelLocation.AUTO;
    }

    // HERRING SECTION
    @ConfigItem(
            keyName = "herringLocation",
            name = "Herring location", 
            description = "Choose fishing location for herring",
            position = 0,
            section = HERRING_SECTION
    )
    default HerringLocation herringLocation() {
        return HerringLocation.AUTO;
    }

    // KARAMBWAN SECTION
    @ConfigItem(
            keyName = "karambwanLocation",
            name = "Karambwan location",
            description = "Choose fishing location for karambwan", 
            position = 0,
            section = KARAMBWAN_SECTION
    )
    default KarambwanLocation karambwanLocation() {
        return KarambwanLocation.AUTO;
    }

    // KARAMBWANJI SECTION
    @ConfigItem(
            keyName = "karambwanjiLocation",
            name = "Karambwanji location",
            description = "Choose fishing location for karambwanji",
            position = 0,
            section = KARAMBWANJI_SECTION
    )
    default KarambwanjiLocation karambwanjiLocation() {
        return KarambwanjiLocation.AUTO;
    }

    // LAVA EEL SECTION
    @ConfigItem(
            keyName = "lavaEelLocation",
            name = "Lava eel location",
            description = "Choose fishing location for lava eel",
            position = 0,
            section = LAVA_EEL_SECTION
    )
    default LavaEelLocation lavaEelLocation() {
        return LavaEelLocation.AUTO;
    }

    // LOBSTER SECTION
    @ConfigItem(
            keyName = "lobsterLocation",
            name = "Lobster location",
            description = "Choose fishing location for lobster",
            position = 0,
            section = LOBSTER_SECTION
    )
    default LobsterLocation lobsterLocation() {
        return LobsterLocation.AUTO;
    }

    // MONKFISH SECTION
    @ConfigItem(
            keyName = "monkfishLocation",
            name = "Monkfish location",
            description = "Choose fishing location for monkfish",
            position = 0,
            section = MONKFISH_SECTION
    )
    default MonkfishLocation monkfishLocation() {
        return MonkfishLocation.AUTO;
    }

    // SALMON SECTION
    @ConfigItem(
            keyName = "salmonLocation", 
            name = "Salmon location",
            description = "Choose fishing location for salmon",
            position = 0,
            section = SALMON_SECTION
    )
    default SalmonLocation salmonLocation() {
        return SalmonLocation.AUTO;
    }

    // SHARK SECTION
    @ConfigItem(
            keyName = "sharkLocation",
            name = "Shark location",
            description = "Choose fishing location for shark",
            position = 0,
            section = SHARK_SECTION
    )
    default SharkLocation sharkLocation() {
        return SharkLocation.AUTO;
    }

    // SHRIMP SECTION
    @ConfigItem(
            keyName = "shrimpLocation",
            name = "Shrimp location",
            description = "Choose fishing location for shrimp",
            position = 0,
            section = SHRIMP_SECTION
    )
    default ShrimpLocation shrimpLocation() {
        return ShrimpLocation.AUTO;
    }

    // TROUT SECTION
    @ConfigItem(
            keyName = "troutLocation",
            name = "Trout location", 
            description = "Choose fishing location for trout",
            position = 0,
            section = TROUT_SECTION
    )
    default TroutLocation troutLocation() {
        return TroutLocation.AUTO;
    }

    // LOCATION ENUMS (alphabetical order within each)
    
    enum AnchoviesLocation {
        AUTO("Auto (Closest)"),
        AL_KHARID("Al Kharid"),
        AVIUM_SAVANNAH_EAST_1("Avium Savannah East 1"),
        BARBARIAN_OUTPOST("Barbarian Outpost"),
        CATHERBY("Catherby"),
        DRAYNOR_VILLAGE("Draynor Village"),
        ENTRANA_DOCK("Entrana Dock"),
        FELDIP_HILLS_SOUTH("Feldip Hills South"),
        FISHING_PLATFORM("Fishing Platform"),
        HOSIDIUS_EAST("Hosidius East"),
        ISLE_OF_SOULS_SOUTH_WEST("Isle Of Souls South West"),
        KASTORI("Kastori"),
        LANDS_END_EAST("Lands End East"),
        LANDS_END_WEST("Lands End West"),
        LUMBRIDGE_SWAMP_SOUTH_EAST("Lumbridge Swamp South East"),
        MUDSKIPPER_POINT("Mudskipper Point"),
        MUSA_POINT("Musa Point"),
        PORT_PISCARILIUS_WEST("Port Piscarilius West"),
        RELLEKKA_WEST("Rellekka West"),
        SAND_CRAB_PENINSULA_SOUTH("Sand Crab Peninsula South"),
        SHIPWRECK_COVE_NORTH("Shipwreck Cove North"),
        WILDERNESS_BANDIT_CAMP("Wilderness Bandit Camp");

        private final String displayName;
        AnchoviesLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case AL_KHARID: return FishingSpotLocation.AL_KHARID;
                case AVIUM_SAVANNAH_EAST_1: return FishingSpotLocation.AVIUM_SAVANNAH_EAST_1;
                case BARBARIAN_OUTPOST: return FishingSpotLocation.BARBARIAN_OUTPOST;
                case CATHERBY: return FishingSpotLocation.CATHERBY;
                case DRAYNOR_VILLAGE: return FishingSpotLocation.DRAYNOR_VILLAGE;
                case ENTRANA_DOCK: return FishingSpotLocation.ENTRANA_DOCK;
                case FELDIP_HILLS_SOUTH: return FishingSpotLocation.FELDIP_HILLS_SOUTH;
                case FISHING_PLATFORM: return FishingSpotLocation.FISHING_PLATFORM;
                case HOSIDIUS_EAST: return FishingSpotLocation.HOSIDIUS_EAST;
                case ISLE_OF_SOULS_SOUTH_WEST: return FishingSpotLocation.ISLE_OF_SOULS_SOUTH_WEST;
                case KASTORI: return FishingSpotLocation.KASTORI;
                case LANDS_END_EAST: return FishingSpotLocation.LANDS_END_EAST;
                case LANDS_END_WEST: return FishingSpotLocation.LANDS_END_WEST;
                case LUMBRIDGE_SWAMP_SOUTH_EAST: return FishingSpotLocation.LUMBRIDGE_SWAMP_SOUTH_EAST;
                case MUDSKIPPER_POINT: return FishingSpotLocation.MUDSKIPPER_POINT;
                case MUSA_POINT: return FishingSpotLocation.MUSA_POINT;
                case PORT_PISCARILIUS_WEST: return FishingSpotLocation.PORT_PISCARILIUS_WEST;
                case RELLEKKA_WEST: return FishingSpotLocation.RELLEKKA_WEST;
                case SAND_CRAB_PENINSULA_SOUTH: return FishingSpotLocation.SAND_CRAB_PENINSULA_SOUTH;
                case SHIPWRECK_COVE_NORTH: return FishingSpotLocation.SHIPWRECK_COVE_NORTH;
                case WILDERNESS_BANDIT_CAMP: return FishingSpotLocation.WILDERNESS_BANDIT_CAMP;
                default: return null;
            }
        }
    }

    enum AnglerfishLocation {
        AUTO("Auto (Closest)"),
        PORT_PISCARILIUS_EAST("Port Piscarilius East");

        private final String displayName;
        AnglerfishLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case PORT_PISCARILIUS_EAST: return FishingSpotLocation.PORT_PISCARILIUS_EAST;
                default: return null;
            }
        }
    }

    enum BarbarianFishLocation {
        AUTO("Auto (Closest)"),
        MOUNT_QUIDAMORTEM("Mount Quidamortem"),
        OTTOS_GROTTO("Ottos Grotto");

        private final String displayName;
        BarbarianFishLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case MOUNT_QUIDAMORTEM: return FishingSpotLocation.MOUNT_QUIDAMORTEM;
                case OTTOS_GROTTO: return FishingSpotLocation.OTTOS_GROTTO;
                default: return null;
            }
        }
    }

    enum CaveEelLocation {
        AUTO("Auto (Closest)"),
        CAMDOZAAL_NORTH("Camdozaal North"),
        LUMBRIDGE_SWAMP_CAVE_EAST("Lumbridge Swamp Cave East"),
        LUMBRIDGE_SWAMP_CAVE_WEST("Lumbridge Swamp Cave West");

        private final String displayName;
        CaveEelLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case CAMDOZAAL_NORTH: return FishingSpotLocation.CAMDOZAAL_NORTH;
                case LUMBRIDGE_SWAMP_CAVE_EAST: return FishingSpotLocation.LUMBRIDGE_SWAMP_CAVE_EAST;
                case LUMBRIDGE_SWAMP_CAVE_WEST: return FishingSpotLocation.LUMBRIDGE_SWAMP_CAVE_WEST;
                default: return null;
            }
        }
    }

    enum HerringLocation {
        AUTO("Auto (Closest)"),
        AL_KHARID("Al Kharid"),
        AVIUM_SAVANNAH_EAST_1("Avium Savannah East 1"),
        BARBARIAN_OUTPOST("Barbarian Outpost"),
        CATHERBY("Catherby"),
        DRAYNOR_VILLAGE("Draynor Village"),
        ENTRANA_DOCK("Entrana Dock"),
        FELDIP_HILLS_SOUTH("Feldip Hills South"),
        FISHING_PLATFORM("Fishing Platform"),
        HOSIDIUS_EAST("Hosidius East"),
        ISLE_OF_SOULS_SOUTH_WEST("Isle Of Souls South West"),
        KASTORI("Kastori"),
        LANDS_END_EAST("Lands End East"),
        LANDS_END_WEST("Lands End West"),
        LUMBRIDGE_SWAMP_SOUTH_EAST("Lumbridge Swamp South East"),
        MUDSKIPPER_POINT("Mudskipper Point"),
        MUSA_POINT("Musa Point"),
        PORT_PISCARILIUS_WEST("Port Piscarilius West"),
        RELLEKKA_WEST("Rellekka West"),
        SAND_CRAB_PENINSULA_SOUTH("Sand Crab Peninsula South"),
        SHIPWRECK_COVE_NORTH("Shipwreck Cove North"),
        WILDERNESS_BANDIT_CAMP("Wilderness Bandit Camp");

        private final String displayName;
        HerringLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case AL_KHARID: return FishingSpotLocation.AL_KHARID;
                case AVIUM_SAVANNAH_EAST_1: return FishingSpotLocation.AVIUM_SAVANNAH_EAST_1;
                case BARBARIAN_OUTPOST: return FishingSpotLocation.BARBARIAN_OUTPOST;
                case CATHERBY: return FishingSpotLocation.CATHERBY;
                case DRAYNOR_VILLAGE: return FishingSpotLocation.DRAYNOR_VILLAGE;
                case ENTRANA_DOCK: return FishingSpotLocation.ENTRANA_DOCK;
                case FELDIP_HILLS_SOUTH: return FishingSpotLocation.FELDIP_HILLS_SOUTH;
                case FISHING_PLATFORM: return FishingSpotLocation.FISHING_PLATFORM;
                case HOSIDIUS_EAST: return FishingSpotLocation.HOSIDIUS_EAST;
                case ISLE_OF_SOULS_SOUTH_WEST: return FishingSpotLocation.ISLE_OF_SOULS_SOUTH_WEST;
                case KASTORI: return FishingSpotLocation.KASTORI;
                case LANDS_END_EAST: return FishingSpotLocation.LANDS_END_EAST;
                case LANDS_END_WEST: return FishingSpotLocation.LANDS_END_WEST;
                case LUMBRIDGE_SWAMP_SOUTH_EAST: return FishingSpotLocation.LUMBRIDGE_SWAMP_SOUTH_EAST;
                case MUDSKIPPER_POINT: return FishingSpotLocation.MUDSKIPPER_POINT;
                case MUSA_POINT: return FishingSpotLocation.MUSA_POINT;
                case PORT_PISCARILIUS_WEST: return FishingSpotLocation.PORT_PISCARILIUS_WEST;
                case RELLEKKA_WEST: return FishingSpotLocation.RELLEKKA_WEST;
                case SAND_CRAB_PENINSULA_SOUTH: return FishingSpotLocation.SAND_CRAB_PENINSULA_SOUTH;
                case SHIPWRECK_COVE_NORTH: return FishingSpotLocation.SHIPWRECK_COVE_NORTH;
                case WILDERNESS_BANDIT_CAMP: return FishingSpotLocation.WILDERNESS_BANDIT_CAMP;
                default: return null;
            }
        }
    }

    enum KarambwanLocation {
        AUTO("Auto (Closest)"),
        FAIRY_RING_DKP("Fairy Ring DKP");

        private final String displayName;
        KarambwanLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case FAIRY_RING_DKP: return FishingSpotLocation.FAIRY_RING_DKP;
                default: return null;
            }
        }
    }

    enum KarambwanjiLocation {
        AUTO("Auto (Closest)"),
        FAIRY_RING_CKR("Fairy Ring CKR");

        private final String displayName;
        KarambwanjiLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case FAIRY_RING_CKR: return FishingSpotLocation.FAIRY_RING_CKR;
                default: return null;
            }
        }
    }

    enum LavaEelLocation {
        AUTO("Auto (Closest)"),
        TAVERLEY_DUNGEON("Taverley Dungeon"),
        WILDERNESS_LAVA_MAZE("Wilderness Lava Maze");

        private final String displayName;
        LavaEelLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case TAVERLEY_DUNGEON: return FishingSpotLocation.TAVERLEY_DUNGEON;
                case WILDERNESS_LAVA_MAZE: return FishingSpotLocation.WILDERNESS_LAVA_MAZE;
                default: return null;
            }
        }
    }

    enum LobsterLocation {
        AUTO("Auto (Closest)"),
        AVIUM_SAVANNAH_SOUTH_WEST_2("Avium Savannah South West 2"),
        CATHERBY("Catherby"),
        ETCETERIA_DOCK("Etceteria Dock"),
        FISHING_GUILD("Fishing Guild"),
        HOSIDIUS_EAST("Hosidius East"),
        ISLE_OF_SOULS_NORTH("Isle Of Souls North"),
        JATISZO("Jatiszo"),
        KHARIDIAN_DESERT_SOUTH_WEST("Kharidian Desert South West"),
        LANDS_END_WEST("Lands End West"),
        MISTROCK("Mistrock"),
        MUSA_POINT("Musa Point"),
        MYTHS_GUILD_NORTH("Myths Guild North"),
        PORT_PISCARILIUS_WEST("Port Piscarilius West"),
        PRIFDDINAS_INSIDE_EAST("Prifddinas Inside East"),
        PRIFDDINAS_INSIDE_NORTH("Prifddinas Inside North"),
        PRIFDDINAS_OUTSIDE_EAST("Prifddinas Outside East"),
        PRIFDDINAS_OUTSIDE_NORTH("Prifddinas Outside North"),
        RELLEKKA_CENTER("Rellekka Center"),
        SAND_CRAB_PENINSULA_WEST("Sand Crab Peninsula West");

        private final String displayName;
        LobsterLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case AVIUM_SAVANNAH_SOUTH_WEST_2: return FishingSpotLocation.AVIUM_SAVANNAH_SOUTH_WEST_2;
                case CATHERBY: return FishingSpotLocation.CATHERBY;
                case ETCETERIA_DOCK: return FishingSpotLocation.ETCETERIA_DOCK;
                case FISHING_GUILD: return FishingSpotLocation.FISHING_GUILD;
                case HOSIDIUS_EAST: return FishingSpotLocation.HOSIDIUS_EAST;
                case ISLE_OF_SOULS_NORTH: return FishingSpotLocation.ISLE_OF_SOULS_NORTH;
                case JATISZO: return FishingSpotLocation.JATISZO;
                case KHARIDIAN_DESERT_SOUTH_WEST: return FishingSpotLocation.KHARIDIAN_DESERT_SOUTH_WEST;
                case LANDS_END_WEST: return FishingSpotLocation.LANDS_END_WEST;
                case MISTROCK: return FishingSpotLocation.MISTROCK;
                case MUSA_POINT: return FishingSpotLocation.MUSA_POINT;
                case MYTHS_GUILD_NORTH: return FishingSpotLocation.MYTHS_GUILD_NORTH;
                case PORT_PISCARILIUS_WEST: return FishingSpotLocation.PORT_PISCARILIUS_WEST;
                case PRIFDDINAS_INSIDE_EAST: return FishingSpotLocation.PRIFDDINAS_INSIDE_EAST;
                case PRIFDDINAS_INSIDE_NORTH: return FishingSpotLocation.PRIFDDINAS_INSIDE_NORTH;
                case PRIFDDINAS_OUTSIDE_EAST: return FishingSpotLocation.PRIFDDINAS_OUTSIDE_EAST;
                case PRIFDDINAS_OUTSIDE_NORTH: return FishingSpotLocation.PRIFDDINAS_OUTSIDE_NORTH;
                case RELLEKKA_CENTER: return FishingSpotLocation.RELLEKKA_CENTER;
                case SAND_CRAB_PENINSULA_WEST: return FishingSpotLocation.SAND_CRAB_PENINSULA_WEST;
                default: return null;
            }
        }
    }

    enum MonkfishLocation {
        AUTO("Auto (Closest)"),
        PISCATORIS("Piscatoris");

        private final String displayName;
        MonkfishLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case PISCATORIS: return FishingSpotLocation.PISCATORIS;
                default: return null;
            }
        }
    }

    enum SalmonLocation {
        AUTO("Auto (Closest)"),
        AUBURNVALE("Auburnvale"),
        BARBARIAN_VILLAGE("Barbarian Village"),
        CUSTODIA("Custodia"),
        ENTRANA_CENTER("Entrana Center"),
        FARMING_GUILD_SOUTH_EAST("Farming Guild South East"),
        HOSIDIUS_CENTER("Hosidius Center"),
        INFIRMARY_SOUTH("Infirmary South"),
        IORWERTH_CAMP_INSIDE("Iorwerth Camp Inside"),
        IORWERTH_CAMP_OUTSIDE("Iorwerth Camp Outside"),
        ISAFDAR_NORTH_EAST_INSIDE("Isafdar North East Inside"),
        ISAFDAR_NORTH_EAST_OUTSIDE("Isafdar North East Outside"),
        KINGSTOWN_EAST("Kingstown East"),
        LUMBRIDGE_RIVER("Lumbridge River"),
        OBSERVATORY_EAST("Observatory East"),
        RIVER_ARDOUGNE("River Ardougne"),
        RIVER_ORTUS("River Ortus"),
        SEERS_VILLAGE("Seers Village"),
        SHILO_VILLAGE("Shilo Village"),
        TAL_TEKLAN_EAST("Tal Teklan East"),
        TOWER_OF_ASCENSION("Tower Of Ascension"),
        TREE_GNOME_STRONGHOLD("Tree Gnome Stronghold"),
        WATSON_HOUSE_SOUTH("Watson House South");

        private final String displayName;
        SalmonLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case AUBURNVALE: return FishingSpotLocation.AUBURNVALE;
                case BARBARIAN_VILLAGE: return FishingSpotLocation.BARBARIAN_VILLAGE;
                case CUSTODIA: return FishingSpotLocation.CUSTODIA;
                case ENTRANA_CENTER: return FishingSpotLocation.ENTRANA_CENTER;
                case FARMING_GUILD_SOUTH_EAST: return FishingSpotLocation.FARMING_GUILD_SOUTH_EAST;
                case HOSIDIUS_CENTER: return FishingSpotLocation.HOSIDIUS_CENTER;
                case INFIRMARY_SOUTH: return FishingSpotLocation.INFIRMARY_SOUTH;
                case IORWERTH_CAMP_INSIDE: return FishingSpotLocation.IORWERTH_CAMP_INSIDE;
                case IORWERTH_CAMP_OUTSIDE: return FishingSpotLocation.IORWERTH_CAMP_OUTSIDE;
                case ISAFDAR_NORTH_EAST_INSIDE: return FishingSpotLocation.ISAFDAR_NORTH_EAST_INSIDE;
                case ISAFDAR_NORTH_EAST_OUTSIDE: return FishingSpotLocation.ISAFDAR_NORTH_EAST_OUTSIDE;
                case KINGSTOWN_EAST: return FishingSpotLocation.KINGSTOWN_EAST;
                case LUMBRIDGE_RIVER: return FishingSpotLocation.LUMBRIDGE_RIVER;
                case OBSERVATORY_EAST: return FishingSpotLocation.OBSERVATORY_EAST;
                case RIVER_ARDOUGNE: return FishingSpotLocation.RIVER_ARDOUGNE;
                case RIVER_ORTUS: return FishingSpotLocation.RIVER_ORTUS;
                case SEERS_VILLAGE: return FishingSpotLocation.SEERS_VILLAGE;
                case SHILO_VILLAGE: return FishingSpotLocation.SHILO_VILLAGE;
                case TAL_TEKLAN_EAST: return FishingSpotLocation.TAL_TEKLAN_EAST;
                case TOWER_OF_ASCENSION: return FishingSpotLocation.TOWER_OF_ASCENSION;
                case TREE_GNOME_STRONGHOLD: return FishingSpotLocation.TREE_GNOME_STRONGHOLD;
                case WATSON_HOUSE_SOUTH: return FishingSpotLocation.WATSON_HOUSE_SOUTH;
                default: return null;
            }
        }
    }

    enum SharkLocation {
        AUTO("Auto (Closest)"),
        APE_ATOLL_SOUTH_WEST("Ape Atoll South West"),
        AVIUM_SAVANNAH_EAST_2("Avium Savannah East 2"),
        AVIUM_SAVANNAH_SOUTH_WEST_1("Avium Savannah South West 1"),
        AVIUM_SAVANNAH_SOUTH_WEST_2("Avium Savannah South West 2"),
        BURGH_DE_ROTT_SOUTH("Burgh De Rott South"),
        CATHERBY("Catherby"),
        FARMING_GUILD_SOUTH_WEST1("Farming Guild South West 1"),
        FARMING_GUILD_SOUTH_WEST2("Farming Guild South West 2"),
        FARMING_GUILD_WEST("Farming Guild West"),
        FISHING_GUILD("Fishing Guild"),
        HOSIDIUS_EAST("Hosidius East"),
        IORWERTH_CAMP_NORTH_INSIDE("Iorwerth Camp North Inside"),
        IORWERTH_CAMP_NORTH_OUTSIDE("Iorwerth Camp North Outside"),
        ISLE_OF_SOULS_EAST("Isle Of Souls East"),
        JATISZO("Jatiszo"),
        LANDS_END_WEST("Lands End West"),
        MARIM("Marim"),
        PRIFDDINAS_INSIDE_EAST("Prifddinas Inside East"),
        PRIFDDINAS_INSIDE_NORTH("Prifddinas Inside North"),
        PRIFDDINAS_OUTSIDE_EAST("Prifddinas Outside East"),
        PRIFDDINAS_OUTSIDE_NORTH("Prifddinas Outside North"),
        RELLEKKA_NORTH_EAST("Rellekka North East"),
        SAND_CRAB_PENINSULA_NORTH("Sand Crab Peninsula North"),
        SHIPWRECK_COVE_SOUTH("Shipwreck Cove South"),
        TAL_TEKLAN_WEST("Tal Teklan West");

        private final String displayName;
        SharkLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case APE_ATOLL_SOUTH_WEST: return FishingSpotLocation.APE_ATOLL_SOUTH_WEST;
                case AVIUM_SAVANNAH_EAST_2: return FishingSpotLocation.AVIUM_SAVANNAH_EAST_2;
                case AVIUM_SAVANNAH_SOUTH_WEST_1: return FishingSpotLocation.AVIUM_SAVANNAH_SOUTH_WEST_1;
                case AVIUM_SAVANNAH_SOUTH_WEST_2: return FishingSpotLocation.AVIUM_SAVANNAH_SOUTH_WEST_2;
                case BURGH_DE_ROTT_SOUTH: return FishingSpotLocation.BURGH_DE_ROTT_SOUTH;
                case CATHERBY: return FishingSpotLocation.CATHERBY;
                case FARMING_GUILD_SOUTH_WEST1: return FishingSpotLocation.FARMING_GUILD_SOUTH_WEST1;
                case FARMING_GUILD_SOUTH_WEST2: return FishingSpotLocation.FARMING_GUILD_SOUTH_WEST2;
                case FARMING_GUILD_WEST: return FishingSpotLocation.FARMING_GUILD_WEST;
                case FISHING_GUILD: return FishingSpotLocation.FISHING_GUILD;
                case HOSIDIUS_EAST: return FishingSpotLocation.HOSIDIUS_EAST;
                case IORWERTH_CAMP_NORTH_INSIDE: return FishingSpotLocation.IORWERTH_CAMP_NORTH_INSIDE;
                case IORWERTH_CAMP_NORTH_OUTSIDE: return FishingSpotLocation.IORWERTH_CAMP_NORTH_OUTSIDE;
                case ISLE_OF_SOULS_EAST: return FishingSpotLocation.ISLE_OF_SOULS_EAST;
                case JATISZO: return FishingSpotLocation.JATISZO;
                case LANDS_END_WEST: return FishingSpotLocation.LANDS_END_WEST;
                case MARIM: return FishingSpotLocation.MARIM;
                case PRIFDDINAS_INSIDE_EAST: return FishingSpotLocation.PRIFDDINAS_INSIDE_EAST;
                case PRIFDDINAS_INSIDE_NORTH: return FishingSpotLocation.PRIFDDINAS_INSIDE_NORTH;
                case PRIFDDINAS_OUTSIDE_EAST: return FishingSpotLocation.PRIFDDINAS_OUTSIDE_EAST;
                case PRIFDDINAS_OUTSIDE_NORTH: return FishingSpotLocation.PRIFDDINAS_OUTSIDE_NORTH;
                case RELLEKKA_NORTH_EAST: return FishingSpotLocation.RELLEKKA_NORTH_EAST;
                case SAND_CRAB_PENINSULA_NORTH: return FishingSpotLocation.SAND_CRAB_PENINSULA_NORTH;
                case SHIPWRECK_COVE_SOUTH: return FishingSpotLocation.SHIPWRECK_COVE_SOUTH;
                case TAL_TEKLAN_WEST: return FishingSpotLocation.TAL_TEKLAN_WEST;
                default: return null;
            }
        }
    }

    enum ShrimpLocation {
        AUTO("Auto (Closest)"),
        AL_KHARID("Al Kharid"),
        AVIUM_SAVANNAH_EAST_1("Avium Savannah East 1"),
        BARBARIAN_OUTPOST("Barbarian Outpost"),
        CATHERBY("Catherby"),
        DRAYNOR_VILLAGE("Draynor Village"),
        ENTRANA_DOCK("Entrana Dock"),
        FELDIP_HILLS_SOUTH("Feldip Hills South"),
        FISHING_PLATFORM("Fishing Platform"),
        HOSIDIUS_EAST("Hosidius East"),
        ISLE_OF_SOULS_SOUTH_WEST("Isle Of Souls South West"),
        KASTORI("Kastori"),
        LANDS_END_EAST("Lands End East"),
        LANDS_END_WEST("Lands End West"),
        LUMBRIDGE_SWAMP_SOUTH_EAST("Lumbridge Swamp South East"),
        MUDSKIPPER_POINT("Mudskipper Point"),
        MUSA_POINT("Musa Point"),
        PORT_PISCARILIUS_WEST("Port Piscarilius West"),
        RELLEKKA_WEST("Rellekka West"),
        SAND_CRAB_PENINSULA_SOUTH("Sand Crab Peninsula South"),
        SHIPWRECK_COVE_NORTH("Shipwreck Cove North"),
        WILDERNESS_BANDIT_CAMP("Wilderness Bandit Camp");

        private final String displayName;
        ShrimpLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case AL_KHARID: return FishingSpotLocation.AL_KHARID;
                case AVIUM_SAVANNAH_EAST_1: return FishingSpotLocation.AVIUM_SAVANNAH_EAST_1;
                case BARBARIAN_OUTPOST: return FishingSpotLocation.BARBARIAN_OUTPOST;
                case CATHERBY: return FishingSpotLocation.CATHERBY;
                case DRAYNOR_VILLAGE: return FishingSpotLocation.DRAYNOR_VILLAGE;
                case ENTRANA_DOCK: return FishingSpotLocation.ENTRANA_DOCK;
                case FELDIP_HILLS_SOUTH: return FishingSpotLocation.FELDIP_HILLS_SOUTH;
                case FISHING_PLATFORM: return FishingSpotLocation.FISHING_PLATFORM;
                case HOSIDIUS_EAST: return FishingSpotLocation.HOSIDIUS_EAST;
                case ISLE_OF_SOULS_SOUTH_WEST: return FishingSpotLocation.ISLE_OF_SOULS_SOUTH_WEST;
                case KASTORI: return FishingSpotLocation.KASTORI;
                case LANDS_END_EAST: return FishingSpotLocation.LANDS_END_EAST;
                case LANDS_END_WEST: return FishingSpotLocation.LANDS_END_WEST;
                case LUMBRIDGE_SWAMP_SOUTH_EAST: return FishingSpotLocation.LUMBRIDGE_SWAMP_SOUTH_EAST;
                case MUDSKIPPER_POINT: return FishingSpotLocation.MUDSKIPPER_POINT;
                case MUSA_POINT: return FishingSpotLocation.MUSA_POINT;
                case PORT_PISCARILIUS_WEST: return FishingSpotLocation.PORT_PISCARILIUS_WEST;
                case RELLEKKA_WEST: return FishingSpotLocation.RELLEKKA_WEST;
                case SAND_CRAB_PENINSULA_SOUTH: return FishingSpotLocation.SAND_CRAB_PENINSULA_SOUTH;
                case SHIPWRECK_COVE_NORTH: return FishingSpotLocation.SHIPWRECK_COVE_NORTH;
                case WILDERNESS_BANDIT_CAMP: return FishingSpotLocation.WILDERNESS_BANDIT_CAMP;
                default: return null;
            }
        }
    }

    enum TroutLocation {
        AUTO("Auto (Closest)"),
        AUBURNVALE("Auburnvale"),
        BARBARIAN_VILLAGE("Barbarian Village"),
        CUSTODIA("Custodia"),
        ENTRANA_CENTER("Entrana Center"),
        FARMING_GUILD_SOUTH_EAST("Farming Guild South East"),
        HOSIDIUS_CENTER("Hosidius Center"),
        INFIRMARY_SOUTH("Infirmary South"),
        IORWERTH_CAMP_INSIDE("Iorwerth Camp Inside"),
        IORWERTH_CAMP_OUTSIDE("Iorwerth Camp Outside"),
        ISAFDAR_NORTH_EAST_INSIDE("Isafdar North East Inside"),
        ISAFDAR_NORTH_EAST_OUTSIDE("Isafdar North East Outside"),
        KINGSTOWN_EAST("Kingstown East"),
        LUMBRIDGE_RIVER("Lumbridge River"),
        OBSERVATORY_EAST("Observatory East"),
        RIVER_ARDOUGNE("River Ardougne"),
        RIVER_ORTUS("River Ortus"),
        SEERS_VILLAGE("Seers Village"),
        SHILO_VILLAGE("Shilo Village"),
        TAL_TEKLAN_EAST("Tal Teklan East"),
        TOWER_OF_ASCENSION("Tower Of Ascension"),
        TREE_GNOME_STRONGHOLD("Tree Gnome Stronghold"),
        WATSON_HOUSE_SOUTH("Watson House South");

        private final String displayName;
        TroutLocation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
        
        public FishingSpotLocation toFishingSpotLocation() {
            switch (this) {
                case AUBURNVALE: return FishingSpotLocation.AUBURNVALE;
                case BARBARIAN_VILLAGE: return FishingSpotLocation.BARBARIAN_VILLAGE;
                case CUSTODIA: return FishingSpotLocation.CUSTODIA;
                case ENTRANA_CENTER: return FishingSpotLocation.ENTRANA_CENTER;
                case FARMING_GUILD_SOUTH_EAST: return FishingSpotLocation.FARMING_GUILD_SOUTH_EAST;
                case HOSIDIUS_CENTER: return FishingSpotLocation.HOSIDIUS_CENTER;
                case INFIRMARY_SOUTH: return FishingSpotLocation.INFIRMARY_SOUTH;
                case IORWERTH_CAMP_INSIDE: return FishingSpotLocation.IORWERTH_CAMP_INSIDE;
                case IORWERTH_CAMP_OUTSIDE: return FishingSpotLocation.IORWERTH_CAMP_OUTSIDE;
                case ISAFDAR_NORTH_EAST_INSIDE: return FishingSpotLocation.ISAFDAR_NORTH_EAST_INSIDE;
                case ISAFDAR_NORTH_EAST_OUTSIDE: return FishingSpotLocation.ISAFDAR_NORTH_EAST_OUTSIDE;
                case KINGSTOWN_EAST: return FishingSpotLocation.KINGSTOWN_EAST;
                case LUMBRIDGE_RIVER: return FishingSpotLocation.LUMBRIDGE_RIVER;
                case OBSERVATORY_EAST: return FishingSpotLocation.OBSERVATORY_EAST;
                case RIVER_ARDOUGNE: return FishingSpotLocation.RIVER_ARDOUGNE;
                case RIVER_ORTUS: return FishingSpotLocation.RIVER_ORTUS;
                case SEERS_VILLAGE: return FishingSpotLocation.SEERS_VILLAGE;
                case SHILO_VILLAGE: return FishingSpotLocation.SHILO_VILLAGE;
                case TAL_TEKLAN_EAST: return FishingSpotLocation.TAL_TEKLAN_EAST;
                case TOWER_OF_ASCENSION: return FishingSpotLocation.TOWER_OF_ASCENSION;
                case TREE_GNOME_STRONGHOLD: return FishingSpotLocation.TREE_GNOME_STRONGHOLD;
                case WATSON_HOUSE_SOUTH: return FishingSpotLocation.WATSON_HOUSE_SOUTH;
                default: return null;
            }
        }
    }
}