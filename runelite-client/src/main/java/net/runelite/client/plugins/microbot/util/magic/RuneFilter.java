package net.runelite.client.plugins.microbot.util.magic;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RuneFilter {
    @Builder.Default
    private final boolean includeInventory = true;
    @Builder.Default
    private final boolean includeEquipment = true;
    @Builder.Default
    private final boolean includeRunePouch = true;
    @Builder.Default
    private final boolean includeBank = false;
    @Builder.Default
    private final boolean includeComboRunes = true;
}