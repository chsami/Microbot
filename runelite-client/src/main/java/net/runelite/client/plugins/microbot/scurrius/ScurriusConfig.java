package net.runelite.client.plugins.microbot.scurrius;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup("Scurrius")
public interface ScurriusConfig extends Config {

    @ConfigSection(
            name = "General Settings",
            description = "General settings for the script",
            position = 0
    )
    String generalSettings = "generalSettings";

    @ConfigSection(
            name = "Food Settings",
            description = "Settings for food selection and usage",
            position = 1
    )
    String foodSettings = "foodSettings";

    @ConfigSection(
            name = "Prayer Settings",
            description = "Settings for prayer potions and prayer usage",
            position = 2
    )
    String prayerSettings = "prayerSettings";

    @ConfigSection(
            name = "Loot Settings",
            description = "Settings for looting items",
            position = 3
    )
    String lootSettings = "lootSettings";

    @ConfigItem(
            keyName = "bossRoomEntryType",
            name = "Public or Private",
            description = "Select whether to enter through Private or Normal entrance",
            position = 0,
            section = generalSettings
    )
    default BossRoomEntryType bossRoomEntryType() {
        return BossRoomEntryType.PRIVATE;
    }

    @ConfigItem(
            keyName = "prioritizeGiantRats",
            name = "Prioritize Giant Rats",
            description = "Prioritize Giant Rats over other targets",
            position = 1,
            section = generalSettings
    )
    default boolean prioritizeRats() {
        return false;
    }

    @ConfigItem(
            keyName = "teleportAmount",
            name = "Teleport Item Amount",
            description = "Amount of Teleports to withdraw",
            position = 2,
            section = generalSettings
    )
    default int teleportAmount() {
        return 5;
    }

    @ConfigItem(
            keyName = "shutdownAfterDeath",
            name = "Shutdown after death",
            description = "If enabled, script will stop after dying",
            position = 2,
            section = generalSettings
    )
    default boolean shutdownAfterDeath() {
        return false;
    }
    
    @ConfigItem(
            keyName = "foodSelection",
            name = "Select Food",
            description = "Select the type of food you want to use",
            position = 0,
            section = foodSettings
    )
    default Rs2Food foodSelection() {
        return Rs2Food.SHARK;
    }

    @ConfigItem(
            keyName = "foodAmount",
            name = "Food Amount",
            description = "Number of food items to withdraw",
            position = 1,
            section = foodSettings
    )
    @Range(
            min = 1,
            max = 28
    )
    default int foodAmount() {
        return 20;
    }

    @ConfigItem(
            keyName = "minEatPercent",
            name = "Min Eat Percent",
            description = "The minimum health percent at which to eat",
            position = 2,
            section = foodSettings
    )
    @Range(
            min = 1,
            max = 99
    )
    default int minEatPercent() {
        return 30;
    }

    @ConfigItem(
            keyName = "maxEatPercent",
            name = "Max Eat Percent",
            description = "The maximum health percent at which to eat",
            position = 3,
            section = foodSettings
    )
    @Range(
            min = 2,
            max = 100
    )
    default int maxEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "potionSelection",
            name = "Select Prayer Potion",
            description = "Select whether to use Prayer Potion or Super Restore",
            position = 0,
            section = prayerSettings
    )
    default PotionSelection potionSelection() {
        return PotionSelection.PRAYERPOTION;
    }

    @ConfigItem(
            keyName = "prayerPotionAmount",
            name = "Prayer Potion Amount",
            description = "Number of prayer potions to withdraw",
            position = 1,
            section = prayerSettings
    )
    @Range(
            min = 1,
            max = 28
    )
    default int prayerPotionAmount() {
        return 2;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Min Prayer Percent",
            description = "The minimum prayer percent at which to drink a prayer potion",
            position = 2,
            section = prayerSettings
    )
    @Range(
            min = 1,
            max = 99
    )
    default int minPrayerPercent() {
        return 10;
    }

    @ConfigItem(
            keyName = "maxPrayerPercent",
            name = "Max Prayer Percent",
            description = "The maximum prayer percent at which to drink a prayer potion",
            position = 3,
            section = prayerSettings
    )
    @Range(
            min = 2,
            max = 100
    )
    default int maxPrayerPercent() {
        return 30;
    }

    @ConfigItem(
            keyName = "lootItems",
            name = "Loot Items",
            description = "Comma-separated list of item names to loot regardless of value",
            position = 0,
            section = lootSettings
    )
    default String lootItems() {
        return "";
    }

    @ConfigItem(
            keyName = "lootValueThreshold",
            name = "Loot Value Threshold",
            description = "Minimum value of items to loot if not specified in the loot list",
            position = 1,
            section = lootSettings
    )
    default int lootValueThreshold() {
        return 1000;
    }

    @Getter
    enum PotionSelection {
        PRAYERPOTION(ItemID._4DOSEPRAYERRESTORE),
        SUPERRESTORE(ItemID._4DOSE2RESTORE);

        private final int itemId;

        PotionSelection(int itemId) {
            this.itemId = itemId;
        }
    }

    @Getter
    enum BossRoomEntryType {
        PRIVATE("Climb-through (private)"),
        NORMAL("Climb-through (normal)");

        private final String interactionText;

        BossRoomEntryType(String interactionText) {
            this.interactionText = interactionText;
        }
    }
}
