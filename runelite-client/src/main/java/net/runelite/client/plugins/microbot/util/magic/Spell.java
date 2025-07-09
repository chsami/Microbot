package net.runelite.client.plugins.microbot.util.magic;

import java.util.HashMap;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Map;
import java.util.stream.Collectors;

public interface Spell {
    MagicAction getMagicAction();
    HashMap<Runes, Integer>  getRequiredRunes();
    default HashMap<Runes, Integer> getRequiredRunes(int casts) {
        final HashMap<Runes, Integer> reqRunes = getRequiredRunes();
        if (casts != 1) reqRunes.replaceAll((key, value) -> value * casts);
        return reqRunes;
    }
    Rs2Spellbook getSpellbook();
    int getRequiredLevel();

    static Map<Integer, Integer> convertRequiredRunes(Map<Runes, Integer> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getItemId(), Map.Entry::getValue));
    }
}
