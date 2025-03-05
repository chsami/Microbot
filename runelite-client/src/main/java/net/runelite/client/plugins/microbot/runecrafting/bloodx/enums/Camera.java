package net.runelite.client.plugins.microbot.runecrafting.bloodx.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Camera {
    BANKING(297, 1410, 551),
    HOME(287, 1541, 551),
    CAVES1(256, 1110, 806),
    CAVES2(256, 1409, 806),
    CAVES4(156, 1109, 806),
    FINAL(336, 1374, 551);

    private final int pitch;
    private final int yaw;
    private final int zoom;
}
