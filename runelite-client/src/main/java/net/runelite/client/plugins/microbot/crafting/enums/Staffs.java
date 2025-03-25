package net.runelite.client.plugins.microbot.crafting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum Staffs {
        NONE(
                        " ",
                        0,
                        0,
                        "",
                        0),
        PROGRESSIVE(
                        "Progressive Mode",
                        0,
                        1,
                        "",
                        0),
        WATER_BATTLESTAFF(
                        "Water Battlestaff",
                        ItemID.WATER_BATTLESTAFF,
                        54,
                        "Water Orb",
                        ItemID.WATER_ORB),
        EARTH_BATTLESTAFF(
                        "Earth Battlestaff",
                        ItemID.EARTH_BATTLESTAFF,
                        58,
                        "Earth Orb",
                        ItemID.EARTH_ORB),
        FIRE_BATTLESTAFF(
                        "Fire Battlestaff",
                        ItemID.FIRE_BATTLESTAFF,
                        62,
                        "Fire Orb",
                        ItemID.FIRE_ORB),
        AIR_BATTLESTAFF(
                        "Air Battlestaff",
                        ItemID.AIR_BATTLESTAFF,
                        66,
                        "Air Orb",
                        ItemID.AIR_ORB);

        private final String label;
        private final int itemID;
        private final int levelRequired;
        private final String orbName;
        private final int orbID;

        @Override
        public String toString() {
                return label;
        }
}
