package net.runelite.client.plugins.microbot.runecrafting.LunarAltar.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public enum Path {
    SHORT(new WorldPoint(2156, 3864, 0)),
    LONG(new WorldPoint(2156, 3864, 0));
    
    private final WorldPoint worldPoint;
}
