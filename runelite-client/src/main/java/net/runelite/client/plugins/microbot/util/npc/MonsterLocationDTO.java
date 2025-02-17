package net.runelite.client.plugins.microbot.util.npc;

import lombok.Data;

import java.util.List;

/**
 * Helper class to parse the raw JSON location data as-is.
 */
@Data
class MonsterLocationDTO
{
    private String location_name;
    private Integer mapID;
    private List<List<Integer>> coords;
}