package net.runelite.client.plugins.microbot.util.inventory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

import java.util.Arrays;

@RequiredArgsConstructor
public enum RunePouchType {
    STANDARD(ItemID.BH_RUNE_POUCH),
    UPGRADED(ItemID.BR_RUNE_REPLACEMENT),
    LARGE(ItemID.BH_RUNE_POUCH_TROUVER),
    DIVINE(ItemID.DIVINE_RUNE_POUCH),
    DIVINE_LARGE(ItemID.DIVINE_RUNE_POUCH_TROUVER);

    @Getter
    private final int itemId;

    public static int[] getPouchIds() {
        return Arrays.stream(RunePouchType.values())
                .mapToInt(RunePouchType::getItemId)
                .toArray();
    }
}
