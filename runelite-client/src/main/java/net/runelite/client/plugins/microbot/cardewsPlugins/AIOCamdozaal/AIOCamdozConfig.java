package net.runelite.client.plugins.microbot.cardewsPlugins.AIOCamdozaal;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.ItemID;
import net.runelite.client.config.*;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.grounditems.GroundItem;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ConfigGroup("AIOCamdozaal")
@ConfigInformation("<center>Mine & Smith: Handles mining and crushing barronite." +
        "<br>Fish & Cook: Fishes and processes the fish for prayer xp." +
        "<br>Fight Golem: Basic fight and loot functionality. </center>")
public interface AIOCamdozConfig extends Config {

    enum Activities{
        MINE_AND_SMITH,
        FISH_AND_PROCESS,
        FIGHT_GOLEM
    }

    enum Pickaxes{
        BRONZE,
        IRON,
        STEEL,
        BLACK,
        MITHRIL,
        ADAMANT,
        RUNE,
        GILDED_PICKAXE,
        DRAGON_PICKAXE,
        DRAGON_PICKAXE_OR,
        INFERNAL_PICKAXE,
        INFERNAL_PICKAXE_OR,
        CRYSTAL_PICKAXE,
        TRAILBLAZER_PICKAXE
    }

    enum Nets{
        SMALL_NET,
        BIG_NET
    }

    enum GolemType{
        FLAWED,
        MIND,
        BODY,
        CHAOS
    }

    enum PrayType{
        OFF,
        ALWAYS_ON,
        FLICK
    }

    @ConfigItem(
            name = "Activity",
            keyName = "activity",
            description = "",
            position = 0
    )
    default Activities CurrentActivity()
    {
        return Activities.MINE_AND_SMITH;
    }

    @ConfigSection(
            name = "Mine And Smith Settings",
            description = "Configure Settings for the Mine And Smith activity",
            position = 0,
            closedByDefault = true
    )
    String mineAndSmithSettingSection = "Configure Settings for the Mine And Smith activity";

    @ConfigItem(
            name = "Pickaxe",
            keyName ="pickaxe",
            description = "The pickaxe that the plugin will try to use",
            position = 1,
            section = mineAndSmithSettingSection
    )
    default Pickaxes SelectedPickaxe() {return Pickaxes.BRONZE; }

    @ConfigSection(
            name = "Fish And Cook Settings",
            description = "Configure settings for the Fish And Cook activity",
            position = 1,
            closedByDefault = true
    )
    String fishAndCookSettingSection = "Configure settings for the Fish And Cook activity";

    @ConfigItem(
            name = "Net Type",
            keyName = "netType",
            description = "Whether we will be fishing with a Big net or Small net",
            position = 1,
            section = fishAndCookSettingSection
    )
    default Nets SelectedNet() { return Nets.SMALL_NET; }

    @ConfigSection(
            name = "Golem Fighter Settings",
            description = "Configure settings for fighting Golems in Camdozaal",
            position = 2,
            closedByDefault = true
    )
    String golemFighterSettingSection = "Configure settings for fighting Golems in Camdozaal";

    @ConfigItem(
            name = "Target Golem",
            keyName = "targetGolem",
            description = "Which golem type you want to fight",
            position = 2,
            section = golemFighterSettingSection
    )
    default GolemType SelectedGolemType() { return GolemType.FLAWED; }

    @ConfigItem(
            name = "Loot only my drops",
            keyName = "lootOnlyMyDrops",
            description = "Whether to check for ownership when looting",
            position = 3,
            section = golemFighterSettingSection
    )
    default boolean LootOnlyMyDrops() { return true; }

    @ConfigItem(
            name = "Get food",
            keyName = "getFood",
            description = "Gets food when banking",
            position = 4,
            section = golemFighterSettingSection
    )
    default boolean GetFood() { return true; }

    @ConfigItem(
            name = "Number of food",
            keyName = "numberOfFood",
            description = "How much food should be withdrawn",
            position = 5,
            section = golemFighterSettingSection
    )
    default int NumberOfFood() { return 5; }

    @ConfigItem(
            name = "Eat Food at HP %",
            keyName = "eatFoodPercent",
            description = "What percent of HP you may drop down to before it will eat",
            position = 7,
            section = golemFighterSettingSection
    )
    default int EatFoodAtPercent() { return 60; }

    @ConfigItem(
            name = "Pickup Untradeables",
            keyName = "pickupUntradeables",
            description = "Whether you loot untradeables. [Barronite shards, Barronite guard, cores, clue scroll]",
            position = 8,
            section = golemFighterSettingSection
    )
    default boolean PickupUntradeables() { return true; }

    @ConfigItem(
            name = "Pickup Runes",
            keyName = "pickupRunes",
            description = "Whether you loot runes.",
            position = 9,
            section = golemFighterSettingSection
    )
    default boolean PickupRunes() { return true; }

    @ConfigItem(
            name = "Pickup Gems",
            keyName = "pickupGems",
            description = "Whether you loot gems.",
            position = 10,
            section = golemFighterSettingSection
    )
    default boolean PickupGems() { return true; }
}
