package net.runelite.client.plugins.microbot.util.death;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Death's Office entrances, one beside each major respawn point. Each is marked by a tombstone icon on
 * the minimap and leads to the same office, so the nearest is always the right choice.
 * <p>
 * Entry is through a {@code Death's Domain} object
 * ({@link net.runelite.api.gameval.ObjectID1#DEATH_OFFICE_ACCESS_GRAVE}, id 38426) with the action
 * {@code Enter Death's Domain} — see {@link Rs2Death#enterDeathsOffice()}.
 * <p>
 * {@link #LUMBRIDGE} is verified in-game against the actual object. The other seven come from the wiki's
 * map data, cross-checked against it: every x matches the wiki exactly, and every y sits a constant two
 * tiles south of the wiki's figure (four at Lumbridge). A uniform offset across all eight, on the one
 * entry with a known ground truth, says the wiki centres its map slightly north of the object rather than
 * on it — so these values are the better estimate of the object tile, not a worse one.
 * <p>
 * Either way the margin is irrelevant: {@link Rs2Death#walkToDeathsOffice()} only has to get close enough
 * for the entrance object to load into the scene, and {@link Rs2Death#enterDeathsOffice()} then finds it
 * by <b>id</b>, never by coordinate. A few tiles of drift costs nothing. The {@code landmark} field
 * records what each point is meant to sit beside.
 */
@Getter
@RequiredArgsConstructor
public enum DeathsOfficeLocation {
    /** Verified in-game: the {@code Death's Domain} object sits here. */
    LUMBRIDGE(new WorldPoint(3238, 3192, 0), "Graveyard by the church"),
    FALADOR(new WorldPoint(2964, 3331, 0), "White Knights' Castle Crypt"),
    EDGEVILLE(new WorldPoint(3096, 3475, 0), "Edgeville Mausoleum"),
    SEERS_VILLAGE(new WorldPoint(2715, 3466, 0), "Graveyard by the church"),
    FEROX_ENCLAVE(new WorldPoint(3127, 3630, 0), "Ferox Enclave"),
    KOUREND_CASTLE(new WorldPoint(1622, 3663, 0), "Kourend Castle"),
    PRIFDDINAS(new WorldPoint(3256, 6118, 0), "Hefin district, north of the bank"),
    CIVITAS_ILLA_FORTIS(new WorldPoint(1654, 3135, 0), "West of the Sunrise Palace");

    private final WorldPoint entrance;

    /** What the entrance sits next to, for verifying the coordinate above. */
    private final String landmark;

    /**
     * @return the entrance closest to the player, or {@code null} when the player's position is
     * unavailable.
     */
    public static DeathsOfficeLocation getNearest() {
        return getNearest(Rs2Player.getWorldLocation());
    }

    public static DeathsOfficeLocation getNearest(WorldPoint from) {
        if (from == null) return null;
        return Arrays.stream(values())
                .min(Comparator.comparingInt(location -> location.entrance.distanceTo(from)))
                .orElse(null);
    }
}
