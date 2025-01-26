package net.runelite.client.plugins.microbot.plankrunner.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

@Getter
@RequiredArgsConstructor
public enum SawmillLocation {
    VARROCK(new WorldPoint(3302, 3491, 0), BankLocation.VARROCK_EAST),
    WOODCUTTING_GUILD(new WorldPoint(1624, 3500, 0), BankLocation.WOODCUTTING_GUILD);
    
    private final WorldPoint worldPoint;
    private final BankLocation bankLocation;
}
