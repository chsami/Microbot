package net.runelite.client.plugins.microbot.util.magic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum Rs2Spells {
    VARROCK_TELEPORT(MagicAction.VARROCK_TELEPORT, Map.of(
            Runes.FIRE, 1,
            Runes.AIR, 3,
            Runes.LAW, 1
    )),
    LUMBRIDGE_TELEPORT(MagicAction.LUMBRIDGE_TELEPORT, Map.of(
            Runes.EARTH, 1,
            Runes.AIR, 3,
            Runes.LAW, 1
    )),
    FALADOR_TELEPORT(MagicAction.FALADOR_TELEPORT, Map.of(
            Runes.WATER, 1,
            Runes.AIR, 3,
            Runes.LAW, 1
    )),
    CAMELOT_TELEPORT(MagicAction.CAMELOT_TELEPORT, Map.of(
            Runes.AIR, 5,
            Runes.LAW, 1
    )),
    ARDOUGNE_TELEPORT(MagicAction.ARDOUGNE_TELEPORT, Map.of(
            Runes.WATER, 2,
            Runes.LAW, 2
    )),
    WATCHTOWER_TELEPORT(MagicAction.WATCHTOWER_TELEPORT, Map.of(
            Runes.EARTH, 2,
            Runes.LAW, 2
    )),
    TROLLHEIM_TELEPORT(MagicAction.TROLLHEIM_TELEPORT, Map.of(
            Runes.FIRE, 2,
            Runes.LAW, 2
    )),
    LOW_LEVEL_ALCHEMY(MagicAction.LOW_LEVEL_ALCHEMY, Map.of(
            Runes.FIRE, 3,
            Runes.NATURE, 1
    )),
    HIGH_LEVEL_ALCHEMY(MagicAction.HIGH_LEVEL_ALCHEMY, Map.of(
            Runes.FIRE, 5,
            Runes.NATURE, 1
    )),
    NPC_CONTACT(MagicAction.NPC_CONTACT, Map.of(
            Runes.AIR, 2,
            Runes.COSMIC, 1,
            Runes.ASTRAL, 1
    )),
    TELEPORT_TO_HOUSE(MagicAction.TELEPORT_TO_HOUSE, Map.of(
            Runes.AIR, 1,
            Runes.EARTH, 1,
            Runes.LAW, 1
    ));

    private final MagicAction action;
    private final Map<Runes, Integer> requiredRunes;
}
