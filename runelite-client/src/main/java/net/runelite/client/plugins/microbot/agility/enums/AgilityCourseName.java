package net.runelite.client.plugins.microbot.agility.enums;

import net.runelite.api.coords.WorldPoint;

public enum AgilityCourseName
{
    AL_KHARID_ROOFTOP_COURSE("Al Kharid Rooftop Course", new WorldPoint(3272, 3195, 0)),
    APE_ATOLL_AGILITY_COURSE("Ape Atoll Agility Course", new WorldPoint(2752, 2742, 0)),
    ARDOUGNE_ROOFTOP_COURSE("Ardougne Rooftop Course", new WorldPoint(2673, 3298, 0)),
    COLOSSAL_WYRM_ADVANCED_COURSE("Colossal Wyrm Advanced Course", new WorldPoint(1652, 2931, 0)),
    COLOSSAL_WYRM_BASIC_COURSE("Colossal Wyrm Basic Course", new WorldPoint(1652, 2931, 0)),
    CANIFIS_ROOFTOP_COURSE("Canifis Rooftop Course", new WorldPoint(3506, 3490, 0)),
    DRAYNOR_VILLAGE_ROOFTOP_COURSE("Draynor Village Rooftop Course", new WorldPoint(3103, 3279, 0)),
    FALADOR_ROOFTOP_COURSE("Falador Rooftop Course", new WorldPoint(3035, 3342, 0)),
    GNOME_STRONGHOLD_AGILITY_COURSE("Gnome Stronghold Agility Course", new WorldPoint(2474, 3436, 0)),
    POLLNIVNEACH_ROOFTOP_COURSE("Pollnivneach Rooftop Course", new WorldPoint(3350, 2963, 0)),
    PRIFDDINAS_AGILITY_COURSE("Prifddinas Agility Course", new WorldPoint(3253, 6109, 0)),
    RELLEKKA_ROOFTOP_COURSE("Rellekka Rooftop Course", new WorldPoint(2624, 3677, 0)),
    SEERS_VILLAGE_ROOFTOP_COURSE("Seers' Village Rooftop Course", new WorldPoint(2728, 3488, 0)),
    SHAYZIEN_BASIC_COURSE("Shayzien Basic Agility Course", new WorldPoint(1551, 3632, 0)),
    SHAYZIEN_ADVANCED_COURSE("Shayzien Advanced Agility Course", new WorldPoint(1551, 3632, 0)),
    VARROCK_ROOFTOP_COURSE("Varrock Rooftop Course", new WorldPoint(3219, 3414, 0)),
    WILDERNESS_CURSE_COURSE("Wilderness Curse Course", new WorldPoint(3030, 3920, 0));

    private final String tooltip;
    private final WorldPoint location;
    private final boolean rooftopCourse;

    AgilityCourseName(String tooltip, WorldPoint location)
    {
        this.tooltip = tooltip;
        this.location = location;
        this.rooftopCourse = this.name().contains("ROOFTOP_COURSE");
    }
}
