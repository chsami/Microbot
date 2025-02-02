package net.runelite.client.plugins.microbot.frostyastrals;


import lombok.Getter;
import lombok.Setter;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("astralrunes")
public interface FrostyAstralsConfig extends Config {

    @ConfigItem(
            keyName = "outfit",
            name = "Outfit",
            description = "Choose the outfit to wear (Graceful/Raiments of the Eye)",
            position = 1
    )

    default OutfitOption outfit() {
        return OutfitOption.GRACEFUL;
    }

    @ConfigItem(
            keyName = "useStaminaPotions",
            name = "Use Stamina Potions",
            description = "Enable or disable the use of stamina potions",
            position = 2
    )
    default boolean useStaminaPotions() {
        return true;
    }

    @ConfigItem(
            keyName = "essencePouches",
            name = "Use Essence Pouches",
            description = "Enable or disable the use of essence pouches",
            position = 3
    )
    default boolean useEssencePouches() {
        return true;
    }

    @ConfigItem(
            keyName = "callNPCRepair",
            name = "Call NPC Repair",
            description = "Enable the use of the NPC Contact spell to repair pouches",
            position = 4
    )
    default boolean callNPCRepair() {
        return true;
    }

    @ConfigItem(
            keyName = "moonclanTeleport",
            name = "Moonclan Teleport",
            description = "Enable the use of Moonclan teleport to return to the bank",
            position = 5
    )
    default boolean moonclanTeleport() {
        return true;
    }

    @ConfigItem(
            keyName = "maxEssence",
            name = "Max Essence to Carry",
            description = "Specify the maximum amount of pure essence to carry",
            position = 6
    )
    default int maxEssence() {
        return 28;
    }

    @ConfigItem(
            keyName = "useRunePouch",
            name = "Use Rune Pouch",
            description = "Enable or disable the use of rune pouch",
            position = 7
    )
    default boolean useRunePouch() {
        return true;
    }

    @ConfigItem(
            keyName = "useDustBattlestaff",
            name = "Use Dust Battlestaff",
            description = "Enable or disable the use of dust battlestaff",
            position = 8
    )
    default boolean useDustBattlestaff() {
        return true;
    }

    enum OutfitOption {
        GRACEFUL,
        RAIMENTS_OF_THE_EYE,
    }
}

