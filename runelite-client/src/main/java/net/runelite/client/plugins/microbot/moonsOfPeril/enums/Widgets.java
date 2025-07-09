package net.runelite.client.plugins.microbot.moonsOfPeril.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Widgets {
    ECLIPSE_MOON_ID(56950789),
    BLOOD_MOON_ID(56950788),
    BLUE_MOON_ID(56950787),
    BOSS_HEALTH_BAR(19857413);

    public final int ID;
}