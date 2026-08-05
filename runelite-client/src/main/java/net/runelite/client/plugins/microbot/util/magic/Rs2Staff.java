package net.runelite.client.plugins.microbot.util.magic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum Rs2Staff {

    NONE(0, Collections.emptyList()),
    STAFF_OF_AIR(ItemID.STAFF_OF_AIR, List.of(Runes.AIR)),
    STAFF_OF_WATER(ItemID.STAFF_OF_WATER, List.of(Runes.WATER)),
    STAFF_OF_EARTH(ItemID.STAFF_OF_EARTH, List.of(Runes.EARTH)),
    STAFF_OF_FIRE(ItemID.STAFF_OF_FIRE, List.of(Runes.FIRE)),
    AIR_BATTLESTAFF(ItemID.AIR_BATTLESTAFF, List.of(Runes.AIR)),
    WATER_BATTLESTAFF(ItemID.WATER_BATTLESTAFF, List.of(Runes.WATER)),
    EARTH_BATTLESTAFF(ItemID.EARTH_BATTLESTAFF, List.of(Runes.EARTH)),
    FIRE_BATTLESTAFF(ItemID.FIRE_BATTLESTAFF, List.of(Runes.FIRE)),
    DUST_BATTLESTAFF(ItemID.DUST_BATTLESTAFF, List.of(Runes.AIR, Runes.EARTH)),
    LAVA_BATTLESTAFF(ItemID.LAVA_BATTLESTAFF, List.of(Runes.FIRE, Runes.EARTH)),
    MIST_BATTLESTAFF(ItemID.MIST_BATTLESTAFF, List.of(Runes.AIR, Runes.WATER)),
    MUD_BATTLESTAFF(ItemID.MUD_BATTLESTAFF, List.of(Runes.WATER, Runes.EARTH)),
    SMOKE_BATTLESTAFF(ItemID.SMOKE_BATTLESTAFF, List.of(Runes.AIR, Runes.FIRE)),
    STEAM_BATTLESTAFF(ItemID.STEAM_BATTLESTAFF, List.of(Runes.WATER, Runes.FIRE)),
    MYSTIC_AIR_STAFF(ItemID.MYSTIC_AIR_STAFF, List.of(Runes.AIR)),
    MYSTIC_WATER_STAFF(ItemID.MYSTIC_WATER_STAFF, List.of(Runes.WATER)),
    MYSTIC_EARTH_STAFF(ItemID.MYSTIC_EARTH_STAFF, List.of(Runes.EARTH)),
    MYSTIC_FIRE_STAFF(ItemID.MYSTIC_FIRE_STAFF, List.of(Runes.FIRE)),
    MYSTIC_DUST_STAFF(ItemID.MYSTIC_DUST_BATTLESTAFF, List.of(Runes.AIR, Runes.EARTH)),
    MYSTIC_LAVA_STAFF(ItemID.MYSTIC_LAVA_STAFF, List.of(Runes.FIRE, Runes.EARTH)),
    MYSTIC_MIST_STAFF(ItemID.MYSTIC_MIST_BATTLESTAFF, List.of(Runes.AIR, Runes.WATER)),
    MYSTIC_MUD_STAFF(ItemID.MYSTIC_MUD_STAFF, List.of(Runes.WATER, Runes.EARTH)),
    MYSTIC_SMOKE_STAFF(ItemID.MYSTIC_SMOKE_BATTLESTAFF, List.of(Runes.AIR, Runes.FIRE)),
    MYSTIC_STEAM_STAFF(ItemID.MYSTIC_STEAM_BATTLESTAFF, List.of(Runes.WATER, Runes.FIRE)),
    TWINFLAME_STAFF(ItemID.TWINFLAME_STAFF, List.of(Runes.FIRE, Runes.WATER)),
    BRYOPHYTAS_STAFF(ItemID.NATURE_STAFF_CHARGED, List.of(Runes.NATURE)),
    SHADOWFLAME_QUADRANT(ItemID.SHADOWFLAME_QUADRANT,
            List.of(Runes.AIR, Runes.WATER, Runes.EARTH, Runes.FIRE));

    private final int itemID;
    private final List<Runes> runes;

    private static final Map<Integer, Rs2Staff> BY_ITEM_ID = Arrays.stream(values())
            .filter(s -> s != NONE)
            .collect(Collectors.toMap(Rs2Staff::getItemID, Function.identity()));

    public boolean provides(Runes rune) {
        if (rune == null) return false;
        if (runes.contains(rune)) return true;
        Runes[] baseRunes = rune.getBaseRunes();
        return baseRunes.length > 0 && runes.containsAll(Arrays.asList(baseRunes));
    }

    public static Set<Integer> itemIdsProviding(Runes rune) {
        LinkedHashSet<Integer> itemIds = Arrays.stream(values())
                .filter(staff -> staff != NONE && staff.provides(rune))
                .map(Rs2Staff::getItemID)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(itemIds);
    }

    public static Rs2Staff byItemId(int itemID) {
        return BY_ITEM_ID.getOrDefault(itemID, NONE);
    }
}
