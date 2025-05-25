package net.runelite.client.plugins.microbot.kebbitHunter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.enums.Kebbits;

/**
 * Enum representing different types of Kebbits with their hunting locations and NPC names.
 */
@Getter
@RequiredArgsConstructor
public enum KebbitHunting {
    SPOTTED("Spotted Kebbit", Kebbits.SPOTTED_KEBBIT.getWorldPoint()),
    DASHING("Dashing Kebbit", Kebbits.DASHING_KEBBIT.getWorldPoint()),
    DARK("Dark Kebbit", Kebbits.DARK_KEBBIT.getWorldPoint());

    private final String name;
    private final WorldPoint huntingPoint;

    @Override
    public String toString() {
        return name;
    }

    /**
     * Gets the in-game NPC name for this Kebbit type.
     *
     * @return Exact NPC name as it appears in-game
     */
    public String getNpcName() {
        switch (this) {
            case SPOTTED:
                return "Spotted kebbit";
            case DASHING:
                return "Dashing kebbit";
            case DARK:
                return "Dark kebbit";
            default:
                return "Spotted kebbit";
        }
    }
}