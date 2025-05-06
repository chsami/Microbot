package net.runelite.client.plugins.microbot.bee.salamanders;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.enums.Salamanders;

@Getter
@RequiredArgsConstructor
public enum SalamanderHunting {


    GREEN("Green salamander", 9341, Salamanders.GREEN_SALAMANDER.getWorldPoint()),
    ORANGE("Orange salamander", 8732, Salamanders.ORANGE_SALAMANDER_2.getWorldPoint()),
    RED("Red salamander", 8990, Salamanders.RED_SALAMANDER.getWorldPoint()),
    BLACK("Black salamander", 9000, Salamanders.BLACK_SALAMANDER.getWorldPoint()),
    TECU("Tecu salamander", 50721, Salamanders.TECU_SALAMANDER.getWorldPoint());

    private final String name;
    private final int treeId;
    private final WorldPoint huntingPoint;

    @Override
    public String toString() {
        return name;
    }
}