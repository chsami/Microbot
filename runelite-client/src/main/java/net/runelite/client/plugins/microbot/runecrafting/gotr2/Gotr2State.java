package net.runelite.client.plugins.microbot.runecrafting.gotr2;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Gotr2State {
    INITIALIZE,
    INITIALIZED,
    ENTER_GAME,
    WAITING,
    MINE_LARGE_GUARDIAN_REMAINS,
    LEAVING_LARGE_MINE,
    ENTER_ALTAR,
    CRAFTING_RUNES,
    LEAVING_ALTAR,
    POWERING_UP,
    CRAFT_GUARDIAN_ESSENCE,
    PORTAL,
    OPTIMIZATION_LOOP,
    END_GAME,
    BANKING,
    SHUTDOWN;
}
