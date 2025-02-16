package net.runelite.client.plugins.microbot.frosty.threatdetector;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FlashSpeed
{
    OFF("Off", -1),
    SLOW("Slow", 80),
    NORMAL("Normal", 40),
    FAST("Fast", 20),
    SOLID("Solid color", -1);

    private final String type;
    private final int    rate;

    @Override
    public String toString()
    {
        return type;
    }
}