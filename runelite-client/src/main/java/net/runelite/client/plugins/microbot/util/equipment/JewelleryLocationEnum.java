package net.runelite.client.plugins.microbot.util.equipment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.worldmap.TeleportLocationData;
import net.runelite.client.plugins.worldmap.TeleportType;
@AllArgsConstructor
@Getter
public enum JewelleryLocationEnum {
    // Jewellery
    BARBARIAN_ASSAULT(TeleportType.JEWELLERY, "Games Necklace" , "Barbarian Assault", new WorldPoint(2520, 3571, 0), "games_necklace_teleport_icon.png", 3),
    BURTHORPE_GAMES_ROOM(TeleportType.JEWELLERY, "Games Necklace" , "Burthorpe Games Room", new WorldPoint(2898, 3554, 0), "games_necklace_teleport_icon.png", 2),
    TEARS_OF_GUTHIX(TeleportType.JEWELLERY, "Games Necklace" , "Tears of Guthix", new WorldPoint(3245, 9500, 0), "games_necklace_teleport_icon.png", 5),
    CORPOREAL_BEAST(TeleportType.JEWELLERY, "Games Necklace" , "Corporeal Beast", new WorldPoint(2967, 4384, 0), "games_necklace_teleport_icon.png", 4),
    WINTERTODT_CAMP(TeleportType.JEWELLERY, "Games Necklace" , "Wintertodt Camp", new WorldPoint(1624, 3938, 0), "games_necklace_teleport_icon.png", 6),
    PVP_ARENA(TeleportType.JEWELLERY, "Ring of Dueling" , "Al Kharid PvP Arena", new WorldPoint(3315, 3235, 0), "ring_of_dueling_teleport_icon.png", 2),
    FEROX_ENCLAVE(TeleportType.JEWELLERY, "Ring of Dueling" , "Ferox Enclave", new WorldPoint(3151, 3636, 0), "ring_of_dueling_teleport_icon.png", 4),
    CASTLE_WARS(TeleportType.JEWELLERY, "Ring of Dueling" , "Castle Wars", new WorldPoint(2441, 3091, 0), "ring_of_dueling_teleport_icon.png", 3),
    FORTIS_COLOSSEUM(TeleportType.JEWELLERY, "Ring of Dueling" , "Fortis Colosseum", TeleportLocationData.FORTIS_COLOSSEUM.getLocation(), "ring_of_dueling_teleport_icon.png", 4),
    WARRIORS_GUILD(TeleportType.JEWELLERY, "Combat Bracelet" , "Warriors' Guild", new WorldPoint(2883, 3549, 0), "combat_bracelet_teleport_icon.png", 2),
    CHAMPIONS_GUILD(TeleportType.JEWELLERY, "Combat Bracelet" , "Champions' Guild", new WorldPoint(3189, 3368, 0), "combat_bracelet_teleport_icon.png", 3),
    EDGEVILLE_MONASTERY(TeleportType.JEWELLERY, "Combat Bracelet" , "Edgeville Monastery", new WorldPoint(3053, 3487, 0), "combat_bracelet_teleport_icon.png", 4),
    RANGING_GUILD(TeleportType.JEWELLERY, "Combat Bracelet" , "Ranging Guild", new WorldPoint(2654, 3441, 0), "combat_bracelet_teleport_icon.png", 5),
    FISHING_GUILD_NECK(TeleportType.JEWELLERY, "Skills Necklace" , "Fishing Guild", new WorldPoint(2613, 3390, 0), "skills_necklace_teleport_icon.png", 2),
    MINING_GUILD(TeleportType.JEWELLERY, "Skills Necklace" , "Mining Guild", new WorldPoint(3049, 9762, 0), "skills_necklace_teleport_icon.png", 3),
    CRAFTING_GUILD(TeleportType.JEWELLERY, "Skills Necklace" , "Crafting Guild", new WorldPoint(2934, 3294, 0), "skills_necklace_teleport_icon.png", 4),
    COOKING_GUILD(TeleportType.JEWELLERY, "Skills Necklace" , "Cooking Guild", new WorldPoint(3145, 3438, 0), "skills_necklace_teleport_icon.png", 5),
    WOODCUTTING_GUILD(TeleportType.JEWELLERY, "Skills Necklace" , "Woodcutting Guild", new WorldPoint(1662, 3505, 0), "skills_necklace_teleport_icon.png", 6),
    FARMING_GUILD(TeleportType.JEWELLERY, "Skills Necklace" , "Farming Guild", new WorldPoint(1249, 3717, 0), "skills_necklace_teleport_icon.png", 7),
    EDGEVILLE(TeleportType.JEWELLERY, "Amulet of Glory" , "Edgeville", new WorldPoint(3087, 3496, 0), "amulet_of_glory_teleport_icon.png", 2),
    KARAMJA(TeleportType.JEWELLERY, "Amulet of Glory" , "Karamja", new WorldPoint(2918, 3176, 0), "amulet_of_glory_teleport_icon.png", 3),
    DRAYNOR_VILLAGE(TeleportType.JEWELLERY, "Amulet of Glory" , "Draynor Village", new WorldPoint(3105, 3251, 0), "amulet_of_glory_teleport_icon.png", 4),
    AL_KHARID(TeleportType.JEWELLERY, "Amulet of Glory" , "Al Kharid", new WorldPoint(3293, 3163, 0), "amulet_of_glory_teleport_icon.png", 5),
    MISCELLANIA(TeleportType.JEWELLERY, "Ring of Wealth" , "Miscellania", new WorldPoint(2535, 3862, 0), "ring_of_wealth_teleport_icon.png", 2),
    GRAND_EXCHANGE(TeleportType.JEWELLERY, "Ring of Wealth" , "Grand Exchange", new WorldPoint(3162, 3480, 0), "ring_of_wealth_teleport_icon.png", 3),
    FALADOR_PARK(TeleportType.JEWELLERY, "Ring of Wealth" , "Falador Park", new WorldPoint(2995, 3375, 0), "ring_of_wealth_teleport_icon.png", 4),
    DONDAKAN(TeleportType.JEWELLERY, "Ring of Wealth" , "Dondakan", new WorldPoint(2831, 10165, 0), "ring_of_wealth_teleport_icon.png", 5),
    SLAYER_TOWER(TeleportType.JEWELLERY, "Slayer Ring" , "Slayer Tower", new WorldPoint(3423, 3536, 0), "slayer_ring_teleport_icon.png", 2),
    FREMENNIK_SLAYER_DUNGEON(TeleportType.JEWELLERY, "Slayer Ring" , "Fremennik Slayer Dungeon", new WorldPoint(2800, 9998, 0), "slayer_ring_teleport_icon.png", 3),
    FREMENNIK_SLAYER_DUNGEON_OUTSIDE(TeleportType.JEWELLERY, "Slayer Ring" , "Fremennik Slayer Dungeon (inside)", new WorldPoint(2800, 3615, 0), "slayer_ring_teleport_icon.png", 4),
    TARNS_LAIR(TeleportType.JEWELLERY, "Slayer Ring" , "Tarn's Lair", new WorldPoint(3187, 4601, 0), "slayer_ring_teleport_icon.png", 5),
    STRONGHOLD_SLAYER_CAVE(TeleportType.JEWELLERY, "Slayer Ring" , "Stronghold Slayer Cave", new WorldPoint(2433, 3421, 0), "slayer_ring_teleport_icon.png", 6),
    DARK_BEASTS(TeleportType.JEWELLERY, "Slayer Ring" , "Dark Beasts", new WorldPoint(2028, 4638, 0), "slayer_ring_teleport_icon.png", 7),
    DIGSITE(TeleportType.JEWELLERY, "Digsite Pendant" , "Digsite", new WorldPoint(3339, 3445, 0), "digsite_pendant_teleport_icon.png", 2),
    HOUSE_ON_THE_HILL(TeleportType.JEWELLERY, "Digsite Pendant" , "House on the Hill", new WorldPoint(3763, 3869, 0), "digsite_pendant_teleport_icon.png", 3),
    LITHKREN(TeleportType.JEWELLERY, "Digsite Pendant" , "Lithkren", new WorldPoint(3547, 10456, 0), "digsite_pendant_teleport_icon.png", 4),
    WIZARDS_TOWER(TeleportType.JEWELLERY, "Necklace of Passage" , "Wizards' Tower", new WorldPoint(3114, 3181, 0), "necklace_of_passage_teleport_icon.png", 2),
    JORRALS_OUTPOST(TeleportType.JEWELLERY, "Necklace of Passage" , "Jorral's Outpost", new WorldPoint(2431, 3348, 0), "necklace_of_passage_teleport_icon.png", 3),
    DESERT_EAGLE_STATION(TeleportType.JEWELLERY, "Necklace of Passage" , "Desert eagle station of the eagle transport system", new WorldPoint(3406, 3157, 0), "necklace_of_passage_teleport_icon.png", 4),
    CHAOS_TEMPLE(TeleportType.JEWELLERY, "Burning Amulet" , "Chaos Temple (lvl 15)", new WorldPoint(3234, 3637, 0), "burning_amulet_teleport_icon.png", 2),
    BANDIT_CAMP(TeleportType.JEWELLERY, "Burning Amulet" , "Bandit Camp (lvl 17)", new WorldPoint(3038, 3651, 0), "burning_amulet_teleport_icon.png", 3),
    LAVA_MAZE(TeleportType.JEWELLERY, "Burning Amulet" , "Lava Maze (lvl 41)", new WorldPoint(3028, 3840, 0), "burning_amulet_teleport_icon.png", 4);
    private final TeleportType type;
    private final String tooltip;
    private final String destination;
    private final WorldPoint location;
    private final String iconPath;
    private final int identifier;



}
