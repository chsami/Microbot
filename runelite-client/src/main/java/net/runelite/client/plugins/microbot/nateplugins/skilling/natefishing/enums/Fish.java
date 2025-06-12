package net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.FishingSpot;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum Fish
{
    SHRIMP(
            "shrimp/anchovies",
            FishingSpot.SHRIMP.getIds(),
            List.of("net", "small net"),
            List.of("raw shrimps","raw anchovies"),
            List.of(317,321),
            List.of(1, 1)
    ),
    SARDINE(
            "sardine/herring",
            FishingSpot.SHRIMP.getIds(),
            List.of("bait"),
            List.of("raw sardine", "raw herring"),
            List.of(327,345),
            List.of(1, 5)
    ),
    MACKEREL(
            "mackerel/cod/bass",
            FishingSpot.SHARK.getIds(),
            List.of("big net"),
            List.of("raw mackerel", "raw cod", "raw bass"),
            List.of(131),
            List.of(353, 341, 363)
    ),
    TROUT(
            "trout/salmon",
            FishingSpot.SALMON.getIds(),
            List.of("lure"),
            List.of("raw trout", "raw salmon"),
            List.of(335, 331),
            List.of(15, 25)
    ),
    PIKE(
            "pike",
            FishingSpot.SALMON.getIds(),
            List.of("bait"),
            List.of("raw pike"),
            List.of(349),
            List.of(20)
    ),
    TUNA(
            "tuna/swordfish",
            FishingSpot.LOBSTER.getIds(),
            List.of("harpoon"),
            List.of("raw tuna", "raw swordfish"),
            List.of(359,371),
            List.of(30,45)
    ),
    CAVE_EEL(
            "cave eel",
            FishingSpot.CAVE_EEL.getIds(),
            List.of("bait"),
            List.of("raw cave eel"),
            List.of(5001),
            List.of(38)
    ),
    LOBSTER(
            "lobster",
            FishingSpot.LOBSTER.getIds(),
            List.of("cage"),
            List.of("raw lobster"),
            List.of(377),
            List.of(40)
    ),
    MONKFISH(
            "monkfish",
            FishingSpot.MONKFISH.getIds(),
            List.of("net"),
            List.of("raw monkfish"),
            List.of(7944),
            List.of(62)
    ),
    KARAMBWANJI(
            "karambwanji",
            FishingSpot.KARAMBWANJI.getIds(),
            List.of("net"),
            List.of("raw karambwanji"),
            List.of(3150),
            List.of(1)
    ),
    LAVA_EEL(
            "lava eel",
            FishingSpot.LAVA_EEL.getIds(),
            List.of("lure"),
            List.of("raw lava eel"),
            List.of(2148),
            List.of(53)
    ),
    SHARK(
            "shark",
            FishingSpot.SHARK.getIds(),
            List.of("harpoon"),
            List.of("raw shark"),
            List.of(383),
            List.of(80)
    ),
    ANGLERFISH(
            "anglerfish",
            FishingSpot.ANGLERFISH.getIds(),
            List.of("sandworms", "bait"),
            List.of("raw anglerfish"),
            List.of(13439),
            List.of(84)
    ),
    KARAMBWAN(
            "karambwan",
            FishingSpot.KARAMBWAN.getIds(),
            List.of("fish"),
            List.of("raw karambwan"),
            List.of(3142),
            List.of(30)
    );

    private final String name;
    private final int[] fishingSpot;
    private final List<String> actions;
    private final List<String> rawNames;
    private final List<Integer> inventory_id;
    private final List<Integer> required_cooking_level;

    @Override
    public String toString()
    {
        return name;
    }
}