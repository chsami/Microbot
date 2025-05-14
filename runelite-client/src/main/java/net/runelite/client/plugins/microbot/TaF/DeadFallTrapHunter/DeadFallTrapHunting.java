package net.runelite.client.plugins.microbot.TaF.DeadFallTrapHunter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum DeadFallTrapHunting {


    WILD_KEBBIT("Wild kebbit", 19215, new WorldPoint(2328, 3554, 0), List.of(10113, 526), 29104),
    BARBTAILED_KEBBIT("Barb-tailed kebbit", 19215, new WorldPoint(2574, 2913, 0), List.of(10129, 526), 29101),
    PRICKLY_KEBBIT("Prickly kebbit", 19215, new WorldPoint(2321, 3614, 0), List.of(526, 10105), null),
    SABRE_TOOTHED_KEBBIT("Sabre-toothed kebbit", 19215, new WorldPoint(2712, 3775, 0), List.of(526, 10109), null),
    PYRE_FOX("Pyre fox", 19215, new WorldPoint(1615, 2999, 0), List.of(526, 29163), 29110);

    private final String name;
    private final int trapId;
    private final WorldPoint huntingPoint;
    private final List<Integer> itemsToDrop;
    private final Integer lootId;

    @Override
    public String toString() {
        return name;
    }
}