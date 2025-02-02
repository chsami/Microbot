package net.runelite.client.plugins.microbot.mixology;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public enum AgaHerbs {
    Irit("irit leaf"),
    Cadantine("cadantine"),
    Lantadyme("Lantadyme"),
    Dwarf_Weed("dwarf weed"),
    Torstol("torstol"),
    IritUnf("irit potion (unf)"),
    CadantineUnf("cadantine potion (unf)"),
    LantadymeUnf("lantadyme potion (unf)"),
    Dwarf_WeedUnf("dwarf weed potion (unf)"),
    TorstolUnf("torstol potion (unf)");

    private final String itemName;

    @Override
    public String toString() {
        return itemName;
    }
}
