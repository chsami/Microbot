package net.runelite.client.plugins.microbot.fletching.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FletchingMaterial
{
    LOG(""),
    WOOD("Wood"),
    OAK("Oak"),
    WILLOW("Willow"),
    MAPLE("Maple"),
    YEW("Yew"),
    MAGIC("Magic"),
    REDWOOD("Redwood");

    private final String name;


    @Override
    public String toString()
    {
        return name;
    }
}
