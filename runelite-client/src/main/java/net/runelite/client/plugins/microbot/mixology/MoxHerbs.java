package net.runelite.client.plugins.microbot.mixology;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MoxHerbs {
    GUAM("guam leaf"),
    Marrentill("marrentill"),
    Tarromin("tarromin"),
    Harralander("harralander"),
    GuamUnf("guam potion (unf)"),
    MarrentillUnf("marrentill potion (unf)"),
    TarrominUnf("tarromin potion (unf)"),
    HarralanderUnf("harralander potion (unf)");

    private final String itemName;

    @Override
    public String toString() {
        return itemName;
    }
}
