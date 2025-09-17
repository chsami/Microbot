package net.runelite.client.plugins.microbot.globval;

public class WidgetIndices {
    /**
     * Describes silver crafting window widgets
     * Last reviewed: 7/4/2022 0:21 am UTC+1
     */
    public static class SilverCraftingWindow {
        public static final int GROUP_INDEX = 6;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; // 0 entire sprite, 1 title label, 2-10 frame border/corners sprites, 11 close button sprite
        public static final int VERTICAL_SEPARATOR_LINE_SPRITE = 2;
        public static final int HORIZONTAL_SEPARATOR_LINE_SPRITE = 3;
        public static final int INNER_FRAME_PARENT_CONTAINER = 4;
        public static final int LEFT_SIDE_PARENT_CONTAINER = 5;
        public static final int FIRST_ROW_PARENT_CONTAINER = 6;
        public static final int OPAL_RING_DYNAMIC_CONTAINER = 7; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int JADE_RING_DYNAMIC_CONTAINER = 8; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int TOPAZ_RING_DYNAMIC_CONTAINER = 9; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int SECOND_ROW_PARENT_CONTAINER = 10;
        public static final int OPAL_NECKLACE_DYNAMIC_CONTAINER = 11; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int JADE_NECKLACE_DYNAMIC_CONTAINER = 12; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int TOPAZ_NECKLACE_DYNAMIC_CONTAINER = 13; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int THIRD_ROW_PARENT_CONTAINER = 14;
        public static final int OPAL_AMULET_DYNAMIC_CONTAINER = 15; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int JADE_AMULET_DYNAMIC_CONTAINER = 16; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int TOPAZ_AMULET_DYNAMIC_CONTAINER = 17; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int FOURTH_ROW_PARENT_CONTAINER = 18;
        public static final int OPAL_BRACELET_DYNAMIC_CONTAINER = 19; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int JADE_BRACELET_DYNAMIC_CONTAINER = 20; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int TOPAZ_BRACELET_DYNAMIC_CONTAINER = 21; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int RIGHT_SIDE_PARENT_CONTAINER = 22;
        public static final int HOLY_SYMBOL_DYNAMIC_CONTAINER = 23; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int UNHOLY_SYMBOL_DYNAMIC_CONTAINER = 24; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int SILVER_SICKLE_DYNAMIC_CONTAINER = 25; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int LIGHTING_ROD_DYNAMIC_CONTAINER = 26; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int SILVER_CROSSBOW_BOLTS_DYNAMIC_CONTAINER = 27; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int TIARA_DYNAMIC_CONTAINER = 28; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int SILVTHRILL_ROD_DYNAMIC_CONTAINER = 29; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int DEMONIC_SIGIL_DYNAMIC_CONTAINER = 30; // 0 item sprite, 1 item name label, 2 unknown label
        public static final int BUTTONS_PARENT_CONTAINER = 31;
        public static final int BUTTON_1_DYNAMIC_CONTAINER = 32; // 0 entire sprite, 1-8 corners/edges sprites, 9 text label
        public static final int BUTTON_X_DYNAMIC_CONTAINER = 35; // 0 entire sprite, 1-8 corners/edges sprites, 9 text label
        public static final int BUTTON_ALL_DYNAMIC_CONTAINER = 36; // 0 entire sprite, 1-8 corners/edges sprites, 9 text label
    }

    /**
     * Describes chat channel widgets
     * Last reviewed: 10/3/2022 0:09 am UTC+1
     */
    public static class ChatChannel {
        public static final int GROUP_INDEX = 7;
        public static final int PARENT_CONTAINER = 0;
        public static final int TITLE_LABEL = 1;
        public static final int OWNER_LABEL = 2;
        public static final int FRAME_DYNAMIC_CONTAINER = 3; // 0 - 7, frame sprites
        public static final int FRIENDS_LIST = 4;
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 5; // 0 - 2, frame sprites
        public static final int INNER_FRAME_TOP_BAR_DYNAMIC_CONTAINER = 6; // 0 - 1, bar sprites
        public static final int INNER_FRAME_SORT_BY_RANK_DYNAMIC_CONTAINER = 7; // 0, button sprite
        public static final int INNER_FRAME_SORT_BY_NAME_DYNAMIC_CONTAINER = 8; // 0, button sprite
        public static final int INNER_FRAME_SORT_BY_LAST_WORLD_DYNAMIC_CONTAINER = 9; // 0, button sprite
        public static final int INNER_FRAME_SORT_BY_WORLD_DYNAMIC_CONTAINER = 10; // 0, button sprite
        public static final int INNER_FRAME_SORT_BY_LEGACY_SORT_DYNAMIC_CONTAINER = 11; // 0, button sprite
        public static final int INNER_FRAME_FRIENDS_LIST_CONTAINER = 12;
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 13; // 0 - 5, sprites
        public static final int JOIN_DYNAMIC_CONTAINER = 17; // 0 - 7, button sprites
        public static final int JOIN_LABEL = 18;
        public static final int SETUP_DYNAMIC_CONTAINER = 19; // 0 - 7, button sprites
        public static final int SETUP_LABEL = 20;
        // 21
    }

    /**
     * Describes bank widgets
     * Last reviewed: 7/4/2022 1:57 am UTC+1
     */
    public static class Bank {
        public static final int GROUP_INDEX = 12;
        public static final int PARENT_CONTAINER = 0;
        public static final int CONTAINER = 1;
        public static final int FRAME_DYNAMIC_CONTAINER = 2; // Contains stone border sprites 0-11, 11 is close button
        public static final int TITLE_LABEL = 3;
        public static final int BUTTON_SHOW_TUTORIAL_CONTAINER = 4;
        public static final int ITEM_SLOTS_USED_LABEL = 5;
        public static final int ITEM_SLOTS_SEPARATOR_LINE = 6;
        public static final int ITEM_SLOTS_CONTAINER= 7;
        public static final int BUTTON_GROUP_STORAGE_CONTAINER = 8;
        public static final int ITEM_SLOTS_MAX_LABEL = 9;
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 10; // Contains 0 sprite tab add
        public static final int TABS_DYNAMIC_CONTAINER = 11; // Contains TAB sprites 0-19
        public static final int TABS_SEPARATOR_LINE = 12;
        public static final int ITEMS_DYNAMIC_CONTAINER = 13; // Contains 0-x bank items
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 14; // Contains 0-5 sprites
        public static final int FOOTER_CONTAINER = 15;
        public static final int REARRANGE_MODE_LABEL = 16;
        public static final int BUTTON_SWAP_CONTAINER = 17;
        public static final int BUTTON_SWAP_LABEL = 18;
        public static final int BUTTON_INSERT_CONTAINER = 19;
        public static final int BUTTON_INSERT_LABEL = 20;
        public static final int WITHDRAW_AS_LABEL = 21;
        public static final int BUTTON_ITEM_CONTAINER = 22;
        public static final int BUTTON_ITEM_LABEL = 23;
        public static final int BUTTON_NOTE_CONTAINER = 24;
        public static final int BUTTON_NOTE_LABEL = 25;
        public static final int BOTTOM_BAR_CONTAINER = 26;
        public static final int QUANTITY_LABEL = 27;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_1_CONTAINER = 28;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_1_LABEL = 29;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_5_CONTAINER = 30;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_5_LABEL = 31;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_10_CONTAINER = 32;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_10_LABEL = 33;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_X_CONTAINER = 34;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_X_LABEL = 35;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_ALL_CONTAINER = 36;
        public static final int BUTTON_WITHDRAWAL_QUANTITY_ALL_LABEL = 37;
        public static final int BUTTON_PLACEHOLDERS_SPRITE = 38;
        public static final int BUTTON_PLACEHOLDERS_ICON_SPRITE = 39;
        public static final int BUTTON_SEARCH_SPRITE = 40;
        public static final int BUTTON_SEARCH_ICON_SPRITE = 41;
        public static final int BUTTON_DEPOSIT_CARRIED_ITEMS_SPRITE = 42;
        public static final int BUTTON_DEPOSIT_CARRIED_ITEMS_ICON_SPRITE = 43;
        public static final int BUTTON_DEPOSIT_WORN_ITEMS_SPRITE = 44;
        public static final int BUTTON_DEPOSIT_WORN_ITEMS_ICON_SPRITE = 45;
        public static final int INCINERATOR_DYNAMIC_CONTAINER = 46;
        public static final int SETTINGS_PARENT_CONTAINER = 49;
        public static final int SETTINGS_TAB_DISPLAY_DYNAMIC_CONTAINER = 50;
        public static final int SETTINGS_INVENTORY_ITEM_OPTIONS_DYNAMIC_CONTAINER = 51; // 0 checkbox sprite, 1 text label
        public static final int SETTINGS_BANK_TUTORIAL_BUTTON_DYNAMIC_CONTAINER = 52; // 0 checkbox sprite, 1 text label
        public static final int SETTINGS_INCINERATOR_DYNAMIC_CONTAINER = 53;
        public static final int SETTINGS_DEPOSIT_WORN_ITEMS_BUTTON_DYNAMIC_CONTAINER = 54;
        public static final int SETTINGS_DEPOSIT_INVENTORY_ITEMS_BUTTON_DYNAMIC_CONTAINER = 55;
        public static final int SETTINGS_BUTTON_RELEASE_ALL_PLACEHOLDERS_DYNAMIC_CONTAINER = 56;
        public static final int SETTINGS_BANK_FILLERS_DYNAMIC_CONTAINER = 57;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_1_SPRITE = 58;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_1_LABEL = 59;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_10_SPRITE = 60;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_10_LABEL = 61;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_50_SPRITE = 62;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_50_LABEL = 63;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_X_SPRITE = 64;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_X_LABEL = 65;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_ALL_SPRITE = 66;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_ALL_LABEL = 67;
        public static final int SETTINGS_BANK_FILLERS_BUTTON_FILL_DYNAMIC_CONTAINER = 68; //0 main sprite, 1 left sprite, 2 right sprite, 3 text label
        public static final int WORN_EQUIPMENT_CONTAINER = 69;
        public static final int WORN_EQUIPMENT_CHARACTER_MODEL = 70;
        public static final int WORN_EQUIPMENT_MAIN_VERTICAL_LINE_SPRITE = 71;
        public static final int WORN_EQUIPMENT_LEFT_VERTICAL_LINE_SPRITE = 72;
        public static final int WORN_EQUIPMENT_RIGHT_VERTICAL_LINE_SPRITE = 73;
        public static final int WORN_EQUIPMENT_MAIN_HORIZONTAL_LINE_SPRITE = 74;
        public static final int WORN_EQUIPMENT_TOP_HORIZONTAL_LINE_SPRITE = 75;
        public static final int WORN_EQUIPMENT_HELMET_DYNAMIC_CONTAINER = 76;
        public static final int WORN_EQUIPMENT_CAPE_DYNAMIC_CONTAINER = 77;
        public static final int WORN_EQUIPMENT_AMULET_DYNAMIC_CONTAINER = 78;
        public static final int WORN_EQUIPMENT_WEAPON_DYNAMIC_CONTAINER = 79;
        public static final int WORN_EQUIPMENT_BODY_DYNAMIC_CONTAINER = 80;
        public static final int WORN_EQUIPMENT_SHIELD_DYNAMIC_CONTAINER = 81;
        public static final int WORN_EQUIPMENT_LEGS_DYNAMIC_CONTAINER = 82;
        public static final int WORN_EQUIPMENT_GLOVES_DYNAMIC_CONTAINER = 83;
        public static final int WORN_EQUIPMENT_BOOTS_DYNAMIC_CONTAINER = 84;
        public static final int WORN_EQUIPMENT_RING_DYNAMIC_CONTAINER = 85;
        public static final int WORN_EQUIPMENT_AMMO_DYNAMIC_CONTAINER = 86;
        public static final int WORN_EQUIPMENT_TEXT_CONTAINER = 87;
        public static final int WORN_EQUIPMENT_UNKNOWN_LABEL = 88;
        public static final int WORN_EQUIPMENT_ATTACK_BONUS_TITLE_LABEL = 89;
        public static final int WORN_EQUIPMENT_ATTACK_STAB_BONUS_LABEL = 90;
        public static final int WORN_EQUIPMENT_ATTACK_SLASH_BONUS_LABEL = 91;
        public static final int WORN_EQUIPMENT_ATTACK_CRUSH_BONUS_LABEL = 92;
        public static final int WORN_EQUIPMENT_ATTACK_MAGIC_BONUS_LABEL = 93;
        public static final int WORN_EQUIPMENT_ATTACK_RANGE_BONUS_LABEL = 94;
        public static final int WORN_EQUIPMENT_DEFENCE_BONUS_TITLE_LABEL = 95;
        public static final int WORN_EQUIPMENT_DEFENCE_STAB_BONUS_LABEL = 96;
        public static final int WORN_EQUIPMENT_DEFENCE_SLASH_BONUS_LABEL = 97;
        public static final int WORN_EQUIPMENT_DEFENCE_CRUSH_BONUS_LABEL = 98;
        public static final int WORN_EQUIPMENT_DEFENCE_MAGIC_BONUS_LABEL = 99;
        public static final int WORN_EQUIPMENT_DEFENCE_RANGE_BONUS_LABEL = 100;
        public static final int WORN_EQUIPMENT_OTHER_BONUSES_TITLE_LABEL = 101;
        public static final int WORN_EQUIPMENT_MELEE_STRENGTH_BONUS_LABEL = 102;
        public static final int WORN_EQUIPMENT_RANGED_STRENGTH_BONUS_LABEL = 103;
        public static final int WORN_EQUIPMENT_MAGIC_DAMAGE_BONUS_LABEL = 104;
        public static final int WORN_EQUIPMENT_PRAYER_BONUS_LABEL = 105;
        public static final int WORN_EQUIPMENT_TARGET_SPECIFIC_TITLE_LABEL = 106;
        public static final int WORN_EQUIPMENT_UNDEAD_TITLE_LABEL = 107;
        public static final int WORN_EQUIPMENT_SLAYER_TITLE_LABEL = 108;
        // 109
        public static final int WORN_EQUIPMENT_WEIGHT_ICON_SPRITE = 110;
        public static final int WORN_EQUIPMENT_WEIGHT_TEXT_LABEL = 111;
        public static final int BUTTON_SETTINGS_DYNAMIC_CONTAINER = 112; // Contains 0-1 sprites
        public static final int BUTTON_WORN_EQUIPMENT_DYNAMIC_CONTAINER = 113; // Contains 0-1 sprites
        // 114
        public static final int UNKNOWN_CONTAINER = 115;
    }

    /**
     * Describes inventory widgets when bank is open
     * Last reviewed: 10/3/2022 0:01 am UTC+1
     */
    public static class BankInventoryItems {
        public static final int GROUP_INDEX = 15;
        public static final int PARENT_CONTAINER = 0;
        public static final int CONTAINER = 1;
        public static final int UNKNOWN_CONTAINER = 2;
        public static final int ITEMS_DYNAMIC_CONTAINER = 3; // 0-27 items sprites
    }

    /**
     * Describes random event pillory widget
     * Last reviewed: 11/4/2022 0:42 am UTC+1
     * TODO: obtain the missing keyhole shape modelIds, diamond shape, square shape
     */
    public static class Pillory {
        public static final int GROUP_INDEX = 27;
        public static final int PARENT_DYNAMIC_CONTAINER = 0; // 0 box colour #0C0E1E , 1 box colour #474745
        public static final int OPACITY_BOX = 1;
        public static final int UNKNOWN_MODEL1 = 2; // modelId 9748
        public static final int CURRENT_LOCK_SHAPED_HOLE_MODEL = 3; // modelId 13393 - triangle, modelId 13382 - round
        public static final int SWINGING_KEY1_MODEL = 4; // modelId 4141 - square, modelId 11032 - triangle, modelId 13395 - diamond, modelId 13396 - round
        public static final int SWINGING_KEY2_MODEL = 5; // modelId 4141 - square, modelId 11032 - triangle, modelId 13395 - diamond, modelId 13396 - round
        public static final int SWINGING_KEY3_MODEL = 6; // modelId 4141 - square, modelId 11032 - triangle, modelId 13395 - diamond, modelId 13396 - round
        public static final int SWINGING_KEY1_DYNAMIC_CONTAINER = 7; // 0 box, colour #FFFFFF, opacity 255, likely selection box
        public static final int SWINGING_KEY2_DYNAMIC_CONTAINER = 8; // 0 box, colour #FFFFFF, opacity 255, likely selection box
        public static final int SWINGING_KEY3_DYNAMIC_CONTAINER = 9; // 0 box, colour #FFFFFF, opacity 255, likely selection box
        public static final int SHAPED_LOCK1_MODEL = 10; // modelId 9757 - locked, modelId 9758 - unlocked
        public static final int SHAPED_LOCK2_MODEL = 11; // modelId 9757 - locked, modelId 9758 - unlocked
        public static final int SHAPED_LOCK3_MODEL = 12; // modelId 9757 - locked, modelId 9758 - unlocked
        public static final int UNKNOWN_MODEL2 = 13; // modelId 4730
        public static final int UNKNOWN_MODEL3 = 14; // modelId 4730
        public static final int UNKNOWN_MODEL4 = 15; // modelId 4730
        public static final int UNKNOWN_MODEL5 = 16; // modelId 15272
        public static final int UNKNOWN_MODEL6 = 17; // modelId 15272
        public static final int UNKNOWN_MODEL7 = 18; // modelId 15272
        public static final int UNKNOWN_MODEL8 = 19; // modelId 15272
        public static final int UNKNOWN_MODEL9 = 20; // modelId 15272
        public static final int UNKNOWN_MODEL10 = 21; // modelId 15272
    }

    /**
     * Describes world switcher widget
     * Last reviewed: 10/4/2022 10:27 pm UTC+1
     */
    public static class WorldSwitcher {
        public static final int GROUP_INDEX = 69;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_SPRITE = 1;
        public static final int CURRENT_WORLD_LABEL = 2;
        public static final int BUTTON_CLOSE_SPRITE = 3;
        public static final int INNER_FRAME_PARENT_CONTAINER = 4;
        public static final int INNER_FRAME_SHADOW_BOX = 5;
        public static final int INNER_FRAME_TOP_SEPARATOR_BOX = 6;
        public static final int INNER_FRAME_BOTTOM_SEPARATOR_BOX = 7;
        public static final int INNER_FRAME_CONTAINER = 8;
        public static final int INNER_FRAME_TOP_BAR_CONTAINER = 9;
        public static final int INNER_FRAME_SORT_BY_STAR_DYNAMIC_CONTAINER = 10; // 0 button sprite
        public static final int INNER_FRAME_SORT_BY_WORLD_NUM_DYNAMIC_CONTAINER = 11; // 0 button sprite
        public static final int INNER_FRAME_SORT_BY_WORLD_FLAG_CONTAINER = 12; // 0 button sprite
        public static final int INNER_FRAME_SORT_BY_PLAYER_COUNT_CONTAINER = 13; // 0 button sprite
        public static final int INNER_FRAME_SORT_DESCRIPTION_CONTAINER = 14; // 0 button sprite
        public static final int INNER_FRAME_WORLD_LIST_CONTAINER = 15;
        public static final int INNER_FRAME_WORLD_LIST_DYNAMIC_CONTAINER = 16; // 301-580 world row sprites
        public static final int INNER_FRAME_WORLD_LIST_ROWS_DYNAMIC_CONTAINER = 17; // 0 row box, 1 star sprite, 2 world num, 3 world flag, 4 world player cnt, 5 world description, repeats ...
        public static final int INNER_FRAME_SCROLLBAR_DYNAMIC_CONTAINER = 18;
        public static final int INNER_FRAME_BOTTOM_BAR_CONTAINER = 19;
        public static final int INNER_FRAME_BOTTOM_BAR_TEXT_LABEL = 20;
        public static final int INNER_FRAME_BOTTOM_BAR_FIRST_ROW_CONTAINER = 21;
        public static final int INNER_FRAME_BOTTOM_BAR_SECOND_ROW_CONTAINER = 22;
        public static final int INNER_FRAME_LOGOUT_SPRITE = 23;
        public static final int INNER_FRAME_MOUSEOVER_TOOLTIP_CONTAINER = 24;
    }

    /**
     * Describes contact npc widgets
     * Last reviewed: 10/3/2022 1:17 am UTC+1
     */
    public static class ContactNpc {
        public static final int GROUP_INDEX = 75;
        public static final int PARENT_CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; // Contains thick border sprites [0-11]
        public static final int CHARACTERS_CONTAINER = 2;
        public static final int HONEST_JIMMY_CONTAINER = 3;
        public static final int HONEST_JIMMY_MODEL = 4;
        public static final int HONEST_JIMMY_LABEL = 5;
        public static final int BERT_THE_SANDMAN_CONTAINER = 6;
        public static final int BERT_THE_SANDMAN_MODEL = 7;
        public static final int BERT_THE_SANDMAN_LABEL = 8;
        public static final int ADVISOR_GHRIM_CONTAINER = 9;
        public static final int ADVISOR_GHRIM_MODEL = 10;
        public static final int ADVISOR_GHRIM_LABEL = 11;
        public static final int DARK_MAGE_ABYSS_CONTAINER = 12;
        public static final int DARK_MAGE_ABYSS_MODEL = 13;
        public static final int DARK_MAGE_ABYSS_LABEL = 14;
        public static final int LANTHUS_CONTAINER = 15;
        public static final int LANTHUS_MODEL = 16;
        public static final int LANTHUS_LABEL = 17;
        public static final int SPRIA_CONTAINER = 18;
        public static final int TURAEL_CONTAINER = 19;
        public static final int TURAEL_MODEL = 20;
        public static final int TURAEL_LABEL = 21;
        public static final int MAZCHNA_CONTAINER = 22;
        public static final int MAZCHNA_MODEL = 23;
        public static final int MAZCHNA_LABEL = 24;
        public static final int VANNAKA_CONTAINER = 25;
        public static final int VANNAKA_MODEL = 26;
        public static final int VANNAKA_LABEL = 27;
        public static final int CHAELDAR_CONTAINER = 28;
        public static final int CHAELDAR_MODEL = 29;
        public static final int CHAELDAR_LABEL = 30;
        public static final int NIEVE_CONTAINER = 31;
        public static final int NIEVE_MODEL = 32;
        public static final int NIEVE_LABEL = 33;
        public static final int DURADEL_CONTAINER = 34;
        public static final int DURADEL_MODEL = 35;
        public static final int DURADEL_LABEL = 36;
        public static final int KONAR_CONTAINER = 37;
        public static final int KRYSTILIA_CONTAINER = 38;
        public static final int KRYSTILIA_MODEL = 39;
        public static final int KRYSTILIA_LABEL = 40;
        public static final int MURPHY_CONTAINER = 41;
        public static final int MURPHY_MODEL = 42;
        public static final int MURPHY_LABEL = 43;
        public static final int CYRISUS_CONTAINER = 44;
        public static final int CYRISUS_MODEL = 45;
        public static final int CYRISUS_LABEL = 46;
        public static final int SMOGGY_CONTAINER = 47;
        public static final int SMOGGY_MODEL = 48;
        public static final int SMOGGY_LABEL = 49;
        public static final int CAPTAIN_GINEA_CONTAINER = 50;
        public static final int CAPTAIN_GINEA_MODEL = 51;
        public static final int CAPTAIN_GINEA_LABEL = 52;
        public static final int WATSON_CONTAINER = 53;
        public static final int WATSON_MODEL = 54;
        public static final int WATSON_LABEL = 55;
        public static final int BARBARIAN_GUARD_CONTAINER = 56;
        public static final int BARBARIAN_GUARD_MODEL = 57;
        public static final int BARBARIAN_GUARD_LABEL = 58;
        public static final int RANDOM_CONTAINER = 59;
        public static final int RANDOM_MODEL = 60;
        public static final int RANDOM_LABEL = 61;
        public static final int AMY_CONTAINER = 62;
        public static final int AMY_MODEL = 63;
        public static final int AMY_LABEL = 64;
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 65; // Contains scrollbar sprites [0-5]
        public static final int KONAR_MODEL = 66;
        public static final int KONAR_LABEL = 67;
        public static final int SPRIA_MODEL = 68;
        public static final int SPRIA_LABEL = 69;
    }

    /**
     * Describes grouping tab widgets
     * Last reviewed: 10/3/2022 2:06 am UTC+1
     */
    public static class Grouping {
        public static final int GROUP_INDEX = 76;
        public static final int PARENT_CONTAINER = 0;
        public static final int TITLE_LABEL = 1;
        public static final int ENTIRE_FRAME_LABEL = 2;
        public static final int ACTIVITY_DROPDOWN_CONTAINER = 3;
        public static final int ACTIVITY_DROPDOWN_SPRITE = 4;
        public static final int ACTIVITY_DROPDOWN_WHITE_BOX = 5;
        public static final int ACTIVITY_DROPDOWN_YELLOW_BOX = 6;
        public static final int ACTIVITY_DROPDOWN_ORANGE_BOX = 7;
        public static final int ACTIVITY_DROPDOWN_LABEL = 8;
        public static final int INNER_FRAME_CONTAINER = 9;
        public static final int INNER_FRAME_YELLOW_BOX = 10;
        public static final int INNER_FRAME_ORANGE_BOX = 11;
        public static final int INNER_FRAME_SCROLLBAR_DYNAMIC_CONTAINER = 13; // 0 - 5 scrollbar sprite
        public static final int ACTIVITY_DROPDOWN_ICON_SPRITE = 20;
        public static final int SUGGESTED_WORLD_TEXT_LABEL = 21;
        public static final int SUGGESTED_WORLD_LABEL = 22;
        public static final int JOIN_CONTAINER  = 23;
        public static final int JOIN_SPRITE = 24;
        public static final int JOIN_LABEL  = 25;
        public static final int TELEPORT_BUTTON_DYNAMIC_CONTAINER = 26; // 0 teleport button box
        public static final int TELEPORT_BUTTON_SPRITE = 27;
        public static final int TELEPORT_BUTTON_LABEL = 28;
    }

    /**
     * Describes grouping tab widgets
     * Last reviewed: 12/3/2022 1:51 am UTC+1
     */
    public static class QuickPrayers {
        public static final int GROUP_INDEX = 77;
        public static final int PARENT_CONTAINER = 0;
        public static final int BG_FIRST_WHITE_BOX = 1; // 245 opacity layers in background
        public static final int BG_SECOND_WHITE_BOX = 2; // 245 opacity layers in background
        public static final int BG_THIRD_WHITE_BOX = 3; // 245 opacity layers in background
        public static final int PRAYERS_DYNAMIC_CONTAINER = 4; // 0-28 prayer sprites, other sprites
        public static final int BUTTON_DONE_SPRITE = 5;
        public static final int BUTTON_DONE_LABEL = 6;
    }

    /**
     * Describes hairdresser widgets
     * Last reviewed: 10/3/2022 2:18 am UTC+1
     */
    public static class HairdresserSalon {
        public static final int GROUP_INDEX = 82;
        public static final int PARENT_CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; //0-11 stone borders sprite
        public static final int HAIR_STYLES_DYNAMIC_CONTAINER = 2; //0-71 models/sprites
        public static final int HAIR_STYLES_TITLE_LABEL = 3;
        public static final int SELECT_COLOUR_CONTAINER = 4;
        public static final int SELECT_COLOUR_TITLE_LABEL = 5;
        public static final int SELECT_COLOUR_TOP_BOX = 6;
        public static final int SELECT_COLOUR_BOTTOM_BOX = 7;
        public static final int SELECT_COLOUR_DYNAMIC_CONTAINER = 8; //0-49 colors sprites
        public static final int CONFIRM_TEXT_LABEL = 9;
    }

    /**
     * Describes hairdresser widgets
     * Last reviewed: 12/3/2022 10:27 pm UTC+1
     */
    public static class PvPScreen {
        public static final int GROUP_INDEX = 90;
        public static final int PARENT_CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; //0-11 stone borders sprite
        public static final int KD_COUNTER_DYNAMIC_CONTAINER = 26; //1 KD label
        public static final int SKULL_GROUP_CONTAINER = 44;
        public static final int SKULL_CONTAINER = 45;
        public static final int SKULL_SPRITE = 46;
        public static final int UNKNOWN_SPRITE = 48;
        public static final int WILDNERESS_LEVEL_LABEL = 50;
    }

    /**
     * Describes account management tab
     * Last reviewed: 10/3/2022 2:48 am UTC+1
     */
    public static class AccountManagementTab {
        public static final int GROUP_INDEX = 109;
        public static final int PARENT_CONTAINER = 0;
        public static final int TOP_BAR_CONTAINER = 1;
        public static final int SEPARATOR_FIRST_LINE = 2;
        public static final int SEPARATOR_SECOND_LINE = 3;
        public static final int SEPARATOR_THIRD_LINE = 4;
        public static final int SEPARATOR_FOURTH_LINE = 5;
        public static final int TAB_ACCOUNT_CONTAINER = 6;
        public static final int TAB_ACCOUNT_LEFT_SIDE_SPRITE = 7;
        public static final int TAB_ACCOUNT_MID_SIDE_SPRITE = 8;
        public static final int TAB_ACCOUNT_RIGHT_SIDE_SPRITE = 9;
        public static final int TAB_ACCOUNT_ICON_SPRITE = 10;
        public static final int TAB_COMMUNITY_CONTAINER = 11;
        public static final int TAB_COMMUNITY_LEFT_SIDE_SPRITE = 12;
        public static final int TAB_COMMUNITY_MID_SIDE_SPRITE = 13;
        public static final int TAB_COMMUNITY_RIGHT_SIDE_SPRITE = 14;
        public static final int TAB_COMMUNITY_ICON_SPRITE = 15;
        public static final int TAB_USEFUL_LINKS_CONTAINER = 16;
        public static final int TAB_USEFUL_LINKS_LEFT_SIDE_SPRITE = 17;
        public static final int TAB_USEFUL_LINKS_MID_SIDE_SPRITE = 18;
        public static final int TAB_USEFUL_LINKS_RIGHT_SIDE_SPRITE = 19;
        public static final int TAB_USEFUL_LINKS_ICON_SPRITE = 20;
        public static final int FRAME_PARENT_CONTAINER = 21;
        public static final int ACCOUNT_FRAME_CONTAINER = 22;
        public static final int ACCOUNT_FRAME_TITLE_LABEL = 23;
        public static final int ACCOUNT_SEGMENT_FIRST_CONTAINER = 24;
        public static final int ACCOUNT_SEGMENT_FIRST_TITLE_LABEL = 25;
        public static final int ACCOUNT_SEGMENT_FIRST_STORE_CONTAINER = 26;
        public static final int ACCOUNT_SEGMENT_FIRST_STORE_SPRITE = 27;
        public static final int ACCOUNT_SEGMENT_FIRST_STORE_ICON_LEFT_SPRITE = 28;
        public static final int ACCOUNT_SEGMENT_FIRST_STORE_ICON_RIGHT_SPRITE = 29;
        public static final int ACCOUNT_SEGMENT_FIRST_STORE_LABEL = 30;
        public static final int ACCOUNT_SEGMENT_FIRST_BOND_CONTAINER = 31;
        public static final int ACCOUNT_SEGMENT_FIRST_BOND_SPRITE = 32;
        public static final int ACCOUNT_SEGMENT_FIRST_BOND_ICON_LEFT_SPRITE = 33;
        public static final int ACCOUNT_SEGMENT_FIRST_BOND_ICON_RIGHT_SPRITE = 34;
        public static final int ACCOUNT_SEGMENT_FIRST_BOND_LABEL = 35;
        public static final int ACCOUNT_SEGMENT_SECOND_CONTAINER = 36;
        public static final int ACCOUNT_SEGMENT_SECOND_TITLE_LABEL = 37;
        public static final int ACCOUNT_SEGMENT_SECOND_INBOX_CONTAINER = 38;
        public static final int ACCOUNT_SEGMENT_SECOND_INBOX_SPRITE = 39;
        public static final int ACCOUNT_SEGMENT_SECOND_INBOX_ICON_LEFT_SPRITE = 40;
        public static final int ACCOUNT_SEGMENT_SECOND_INBOX_ICON_RIGHT_SPRITE = 41;
        public static final int ACCOUNT_SEGMENT_SECOND_INBOX_LABEL = 42;
        public static final int ACCOUNT_SEGMENT_THIRD_CONTAINER = 43;
        public static final int ACCOUNT_SEGMENT_THIRD_TITLE_LABEL = 44;
        public static final int ACCOUNT_SEGMENT_THIRD_NAMECHANGER_CONTAINER = 45;
        public static final int ACCOUNT_SEGMENT_THIRD_NAMECHANGER_SPRITE = 46;
        public static final int ACCOUNT_SEGMENT_THIRD_NAMECHANGER_ICON_LEFT_SPRITE = 47;
        public static final int ACCOUNT_SEGMENT_THIRD_NAMECHANGER_ICON_RIGHT_SPRITE = 48;
        public static final int ACCOUNT_SEGMENT_THIRD_NAMECHANGER_LABEL = 49;
        public static final int COMMUNITY_FRAME_CONTAINER = 50;
        public static final int COMMUNITY_FRAME_TITLE_LABEL = 51;
        public static final int COMMUNITY_SEGMENT_FIRST_CONTAINER = 52;
        public static final int COMMUNITY_SEGMENT_FIRST_TITLE_LABEL = 53;
        public static final int COMMUNITY_SEGMENT_FIRST_POLL_CONTAINER = 54;
        public static final int COMMUNITY_SEGMENT_FIRST_POLL_SPRITE = 55;
        public static final int COMMUNITY_SEGMENT_FIRST_POLL_ICON_LEFT_SPRITE = 56;
        public static final int COMMUNITY_SEGMENT_FIRST_POLL_ICON_RIGHT_SPRITE = 57;
        public static final int COMMUNITY_SEGMENT_FIRST_POLL_LABEL = 58;
        public static final int COMMUNITY_SEGMENT_FIRST_POLL_OPACITY_SPRITE = 59;
        public static final int COMMUNITY_SEGMENT_FIRST_HISTORY_CONTAINER = 60;
        public static final int COMMUNITY_SEGMENT_FIRST_HISTORY_SPRITE = 61;
        public static final int COMMUNITY_SEGMENT_FIRST_HISTORY_ICON_LEFT_SPRITE = 62;
        public static final int COMMUNITY_SEGMENT_FIRST_HISTORY_ICON_RIGHT_SPRITE = 63;
        public static final int COMMUNITY_SEGMENT_FIRST_HISTORY_LABEL = 64;
        public static final int COMMUNITY_SEGMENT_SECOND_CONTAINER = 65;
        public static final int COMMUNITY_SEGMENT_SECOND_TITLE_LABEL = 66;
        public static final int COMMUNITY_SEGMENT_SECOND_NEWSPOST_CONTAINER = 67;
        public static final int COMMUNITY_SEGMENT_SECOND_NEWSPOST_SPRITE = 68;
        public static final int COMMUNITY_SEGMENT_SECOND_NEWSPOST_ICON_LEFT_SPRITE = 69;
        public static final int COMMUNITY_SEGMENT_SECOND_NEWSPOST_ICON_RIGHT_SPRITE = 70;
        public static final int COMMUNITY_SEGMENT_SECOND_NEWSPOST_LABEL = 71;
        public static final int COMMUNITY_SEGMENT_SECOND_ARCHIVE_CONTAINER = 72;
        public static final int COMMUNITY_SEGMENT_SECOND_ARCHIVE_SPRITE = 73;
        public static final int COMMUNITY_SEGMENT_SECOND_ARCHIVE_ICON_LEFT_SPRITE = 74;
        public static final int COMMUNITY_SEGMENT_SECOND_ARCHIVE_ICON_RIGHT_SPRITE = 75;
        public static final int COMMUNITY_SEGMENT_SECOND_ARCHIVE_LABEL = 76;
        public static final int USEFUL_LINKS_FRAME_CONTAINER = 77;
        public static final int USEFUL_LINKS_FRAME_TITLE_LABEL = 78;
        public static final int USEFUL_LINKS_CONTAINER = 79;
        public static final int USEFUL_LINKS_WEBSITE_DYNAMIC_CONTAINER = 80; // 0-9 , 8 button sprites, 1 button label
        public static final int USEFUL_LINKS_SUPPORT_DYNAMIC_CONTAINER = 81; // 0-9
        public static final int USEFUL_LINKS_EXCHANGE_DYNAMIC_CONTAINER = 82; // 0-9
        public static final int USEFUL_LINKS_HISCORES_DYNAMIC_CONTAINER = 83; // 0-9
        public static final int USEFUL_LINKS_WIKI_DYNAMIC_CONTAINER = 84; // 0-9
        public static final int USEFUL_LINKS_MERCHANDISE_DYNAMIC_CONTAINER = 85; // 0-9
        public static final int USEFUL_LINKS_EMPTY_LINK_DYNAMIC_CONTAINER = 86; // 0-9
    }

    /**
     * Describes settings tab
     * Last reviewed: 10/3/2022 3:09 am UTC+1
     */
    public static class Settings {
        public static final int GROUP_INDEX = 116;
        public static final int PARENT_CONTAINER = 0;
        public static final int TOP_BAR_CONTAINER = 1;
        public static final int TITLE_LABEL = 2;
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 3; //0-7 thick border sprites
        public static final int INNER_FRAME_INNER_CONTAINER = 4;
        public static final int PK_SKULL_PREVENTION_DYNAMIC_CONTAINER = 5; // 0-1 label, checkbox
        public static final int PLAYER_ATTACK_OPTIONS_DYNAMIC_CONTAINER = 6; // 0-5 dropdown, button, boxes, sprites
        public static final int NPC_ATTACK_OPTIONS_DYNAMIC_CONTAINER = 7; // 0-5 dropdown, button, boxes, sprites
        public static final int BOTTOM_BAR_CONTAINER = 8;
        // gap 9 - 71
        public static final int TAB_ACCEPT_AID_DYNAMIC_CONTAINER = 72; // 0-1 tab sprite, icon sprite
        public static final int TAB_TOGGLE_RUN_DYNAMIC_CONTAINER = 73; // 0-2 tab sprite, icon sprite, label percentage
        public static final int TAB_VIEW_HOUSE_OPTIONS_DYNAMIC_CONTAINER = 74; // 0-1 tab sprite, icon sprite
        public static final int ALL_SETTINGS_DYNAMIC_CONTAINER = 75; // 0-9 sprites of button
        public static final int TAB_OPEN_BOUND_POUCH_DYNAMIC_CONTAINER = 76; // 0-1 tab sprite, icon sprite
        // gap 77 - 85
        public static final int MOUSEOVER_TOOLTIP_CONTAINER = 86;
        // gap 87 - 101
        public static final int TOP_BAR_FIRST_SEPARATOR_LINE = 102;
        public static final int TOP_BAR_SECOND_SEPARATOR_LINE = 103;
        public static final int TOP_BAR_THIRD_SEPARATOR_LINE = 104;
        public static final int TOP_BAR_FOURTH_SEPARATOR_LINE = 105;
        public static final int TAB_CONTROLS_SETTINGS_CONTAINER = 106;
        public static final int TAB_CONTROLS_SETTINGS_LEFT_SIDE_SPRITE = 107;
        public static final int TAB_CONTROLS_SETTINGS_MID_SIDE_SPRITE = 108;
        public static final int TAB_CONTROLS_SETTINGS_RIGHT_SIDE_SPRITE = 109;
        public static final int TAB_CONTROLS_SETTINGS_ICON_SPRITE = 110;
        public static final int TAB_TAB_AUDIO_SETTINGS_CONTAINER = 111;
        public static final int TAB_TAB_DISPLAY_SETTINGS_CONTAINER = 112;
        public static final int TAB_TAB_AUDIO_SETTINGS_LEFT_SIDE_SPRITE = 113;
        public static final int TAB_TAB_AUDIO_SETTINGS_MID_SIDE_SPRITE = 114;
        public static final int TAB_TAB_AUDIO_SETTINGS_RIGHT_SIDE_SPRITE = 115;
        public static final int TAB_TAB_AUDIO_SETTINGS_ICON_SPRITE = 116;
        public static final int TAB_TAB_DISPLAY_SETTINGS_LEFT_SIDE_SPRITE = 117;
        public static final int TAB_TAB_DISPLAY_SETTINGS_MID_SIDE_SPRITE = 118;
        public static final int TAB_TAB_DISPLAY_SETTINGS_RIGHT_SIDE_SPRITE = 119;
        public static final int TAB_TAB_DISPLAY_SETTINGS_ICON_SPRITE = 120;
    }

    /**
     * Describes current quest diary (the quest scroll) widgets
     * Last reviewed: 1/3/2022 0:14 am UTC+1
     */
    public static class QuestDiary {
        public static final int GROUP_INDEX = 119;
        public static final int PARENT_CONTAINER = 0;
        public static final int SCROLL_MODEL = 1;
        public static final int TITLE_LABEL = 2;
        public static final int TEXT_CONTAINER = 3;
        public static final int FIRST_TEXT_LINE_LABEL = 4;
        // .
        // .
        // .
        public static final int LAST_TEXT_LINE_LABEL = 203;
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 204; // contains 0-3 scrollbar sprites, 4 button arrow up sprite, 5 button down sprite
        public static final int BUTTON_CLOSE_SPRITE = 205;
    }

    /**
     * Describes inventory widget
     * Last reviewed: 1/3/2022 0:27 am UTC+1
     */
    public static class Inventory {
        public static final int GROUP_INDEX = 149;
        public static final int SPRITE_GRID = 0; // contains 0-27, sprite grids with itemIDs
    }

    /**
     * Describes minimap widgets
     * Last reviewed: 28/2/2022 1:57 am UTC+1
     */
    public static class Minimap {
        public static final int GROUP_INDEX = 160;
        public static final int XP_ORB_SPRITE = 1; // Interactions: "Hide", "Setup"
        public static final int HEALTH_ORB_PARENT_CONTAINER = 2;
        public static final int HEALTH_ORB_PARENT_SPRITE = 3;
        public static final int HEALTH_ORB_CONTAINER = 4; // Interactions: "Cure"
        public static final int HEALTH_ORB_LABEL = 5;
        public static final int HEALTH_ORB_SPRITE = 6;
        // 7 - 8 gap
        public static final int HEALTH_ORB_ENERGY_CONSUMED_CONTAINER = 9;
        public static final int HEALTH_ORB_ENERGY_CONSUMED_SPRITE = 10;
        public static final int HEALTH_ORB_ICON_CONTAINER = 11;
        public static final int HEALTH_ORB_ICON_SPRITE = 12;
        public static final int QUICK_PRAYER_ORB_PARENT_CONTAINER = 13;
        public static final int QUICK_PRAYER_ORB_PARENT_SPRITE = 14;
        public static final int QUICK_PRAYER_ORB_CONTAINER = 15; // Interactions: "Activate", "Setup"
        public static final int QUICK_PRAYER_ORB_LABEL = 16;
        public static final int QUICK_PRAYER_ORB_SPRITE = 17;
        public static final int QUICK_PRAYER_ORB_ENERGY_CONSUMED_CONTAINER = 18;
        public static final int QUICK_PRAYER_ORB_ENERGY_CONSUMED_SPRITE = 19;
        public static final int QUICK_PRAYER_ORB_ICON_SPRITE = 20;
        public static final int RUN_ORB_PARENT_CONTAINER = 21;
        public static final int RUN_ORB_PARENT_SPRITE = 22;
        public static final int RUN_ORB_CONTAINER = 23; // Interactions: "Toggle"
        public static final int RUN_ORB_LABEL = 24;
        public static final int RUN_ORB_SPRITE = 25;
        public static final int RUN_ORB_ENERGY_CONSUMED_CONTAINER = 26;
        public static final int RUN_ORB_ENERGY_CONSUMED_SPRITE = 27;
        public static final int RUN_ORB_ICON_SPRITE = 28;
        public static final int SPEC_ORB_PARENT_CONTAINER = 29;
        public static final int SPEC_ORB_PARENT_SPRITE = 30;
        public static final int SPEC_ORB_CONTAINER = 31; // Interactions: "Use"
        public static final int SPEC_ORB_LABEL = 32;
        public static final int SPEC_ORB_ICON_SPRITE = 33;
        public static final int SPEC_ORB_ENERGY_CONSUMED_CONTAINER = 34;
        public static final int SPEC_ORB_ENERGY_CONSUMED_SPRITE = 35;
        public static final int SPEC_ORB_RECHARGE_CONTAINER = 36; // NOTE: unclear
        public static final int SPEC_ORB_SPRITE = 37;
        public static final int BOND_ORB_PARENT_CONTAINER = 38;
        public static final int BOND_ORB_PARENT_SPRITE = 39;
        public static final int BOND_ORB_ICON_SPRITE = 40;
        public static final int BOND_ORB_CONTAINER = 41; // Interactions: "Open Store", "Bond Pouch"
        public static final int BOND_ORB_SPRITE = 42;
        public static final int WORLDMAP_ORB_CONTAINER = 43;
        public static final int WIKI_BANNER_PARENT_CONTAINER = 44;
        public static final int WIKI_BANNER_CONTAINER = 45; // Interactions: ... 10 unknown
        public static final int WIKI_BANNER_SPRITE = 46;
        public static final int WORLDMAP_ORB_PARENT_SPRITE = 47;
        public static final int WORLDMAP_ORB_SPRITE = 48; // Interactions: "Floating World Map", "Fullscreen World Map"
    }

    /**
     * Describes resizable classic viewport widgets
     * Last reviewed: 22/3/2022 1:12 am UTC+1
     */
    public static class ResizableClassicViewport {
        public static final int GROUP_INDEX = 161;
        public static final int DIALOG_WINDOW_CONTAINER = 16;
        public static final int MINIMAP_CONTAINER = 21;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_ONE_CONTAINER = 22;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_TWO_CONTAINER = 23;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_THREE_CONTAINER = 24;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_FOUR_CONTAINER = 25;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_FIVE_CONTAINER = 26;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_SIX_CONTAINER = 27;
        public static final int MINIMAP_COMPASS_SPRITE = 28;
        public static final int MINIMAP_DRAW_AREA_SPRITE = 29;
        public static final int MINIMAP_COMPASS_DYNAMIC_CONTAINER = 30; //label[0],label[1]
        public static final int MINIMAP_SPRITE = 31;
        public static final int MINIMAP_ORB_HOLDER_CONTAINER = 32;
        public static final int INVENTORY_SPRITE = 37;
        public static final int RIGHT_SIDE_COLUMN_SPRITE = 38;
        public static final int LEFT_SIDE_COLUMN_SPRITE = 39;
        public static final int BOTTOM_BAR_SPRITE = 40;
        public static final int BOTTOM_BAR_CONTAINER = 41;
        public static final int TAB_CHAT_CHANNEL_SPRITE = 42;     // Interactions: Chat-channel
        public static final int TAB_ACC_MANAGEMENT_SPRITE = 43;   // Interactions: Account Management
        public static final int TAB_FRIENDS_LIST_SPRITE = 44;     // Interactions: Friends List
        public static final int TAB_LOGOUT_SPRITE = 45;           // Interactions: Logout
        public static final int TAB_SETTINGS_SPRITE = 46;         // Interactions: Settings
        public static final int TAB_EMOTES_SPRITE = 47;           // Interactions: Emotes
        public static final int TAB_MUSIC_PLAYER_SPRITE = 48;     // Interactions: Music Player
        public static final int TAB_CHAT_CHANNEL_ICON_SPRITE = 49;
        public static final int TAB_ACC_MANAGEMENT_ICON_SPRITE = 50;
        public static final int TAB_FRIENDS_LIST_ICON_SPRITE = 51;
        public static final int TAB_LOGOUT_ICON_SPRITE = 52;
        public static final int TAB_SETTINGS_ICON_SPRITE = 53;
        public static final int TAB_EMOTES_ICON_SPRITE = 54;
        public static final int TAB_MUSIC_PLAYER_ICON_SPRITE = 55;
        public static final int TOP_BAR_SPRITE = 56;
        public static final int TOP_BAR_CONTAINER = 57;
        public static final int TAB_COMBAT_OPTIONS_SPRITE = 58;   // Interactions: Combat Options
        public static final int TAB_SKILLS_SPRITE = 59;           // Interactions: Skills
        public static final int TAB_QUEST_LIST_SPRITE = 60;       // Interactions: Leagues
        public static final int TAB_INVENTORY_SPRITE = 61;        // Interactions: Inventory
        public static final int TAB_WORN_EQUIPMENT_SPRITE = 62;   // Interactions: Worn Equipment
        public static final int TAB_PRAYER_SPRITE = 63;           // Interactions: Prayer
        public static final int TAB_MAGIC_SPRITE = 64;            // Interactions: Magic, Disable spell filtering
        public static final int TAB_COMBAT_ICON_SPRITE = 65;
        public static final int TAB_SKILLS_ICON_SPRITE = 66;
        public static final int TAB_QUEST_LIST_ICON_SPRITE = 67;
        public static final int TAB_INVENTORY_ICON_SPRITE = 68;
        public static final int TAB_WORN_EQUIPMENT_ICON_SPRITE = 69;
        public static final int TAB_PRAYER_ICON_SPRITE = 70;
        public static final int TAB_MAGIC_ICON_SPRITE = 71;
        public static final int INVENTORY_PARENT_FIRST_CONTAINER = 72;
        public static final int INVENTORY_SPECIAL_CONTAINER = 73; // content type 1354, Contains the elements of inventory [15,0] if bank open
        public static final int INVENTORY_PARENT_SECOND_CONTAINER = 74;
        public static final int INVENTORY_CONTAINER = 78; // Contains the elements of inventory [149,0], 0-27
        public static final int UNKNOWN_CONTAINER = 89; // content type 1337, LEET lol?
        public static final int PRIVATE_CHAT_MESSAGE_CONTAINER = 91;
        public static final int VIEWPORT_PARENT_CONTAINER = 92;
        public static final int MINIMAP_PARENT_CONTAINER = 93;
        public static final int CHATBOX_PARENT_CONTAINER = 94;
        public static final int INVENTORY_ROOT_CONTAINER = 95;
    }

    /**
     * Describes chatbox widgets
     * TODO: review
     */
    public static class ChatBox {
        public static final int GROUP_INDEX = 162;
        public static final int PARENT_CONTAINER = 0;
        public static final int BOTTOM_BAR_PARENT_CONTAINER = 1;
        public static final int BOTTOM_BAR_CONTAINER = 2;
        public static final int BOTTOM_BAR_SPRITE = 3;
        public static final int TAB_ALL_CONTAINER = 4;
        public static final int TAB_ALL_SPRITE = 5;
        public static final int TAB_ALL_LABEL = 6;
        public static final int TAB_GAME_CONTAINER = 7;
        public static final int TAB_GAME_SPRITE = 8;
        public static final int TAB_GAME_LABEL = 9;
        public static final int TAB_GAME_STATE_LABEL = 10;
        public static final int TAB_PUBLIC_CONTAINER = 11;
        public static final int TAB_PUBLIC_SPRITE = 12;
        public static final int TAB_PUBLIC_LABEL = 13;
        public static final int TAB_PUBLIC_STATE_LABEL = 14;
        public static final int TAB_PRIVATE_CONTAINER = 15;
        public static final int TAB_PRIVATE_SPRITE= 16;
        public static final int TAB_PRIVATE_LABEL = 17;
        public static final int TAB_PRIVATE_STATE_LABEL = 18;
        public static final int TAB_CHANNEL_CONTAINER = 19;
        public static final int TAB_CHANNEL_SPRITE = 20;
        public static final int TAB_CHANNEL_LABEL = 21;
        public static final int TAB_CHANNEL_STATE_LABEL = 22;
        public static final int TAB_CLAN_CONTAINER = 23;
        public static final int TAB_CLAN_SPRITE = 24;
        public static final int TAB_CLAN_LABEL = 25;
        public static final int TAB_CLAN_STATE_LABEL = 26;
        public static final int TAB_TRADE_CONTAINER = 27;
        public static final int TAB_TRADE_SPRITE = 28;
        public static final int TAB_TRADE_LABEL = 29;
        public static final int TAB_TRADE_STATE_LABEL = 30;
        public static final int REPORT_TAB_CONTAINER = 31;
        public static final int REPORT_TAB_LABEL = 32;
        public static final int REPORT_TAB_TEXT = 33;
        public static final int FRAME_CONTAINER = 34;
        public static final int TRANSPARENT_BACKGROUND_CONTAINER = 35;
        // 36
        public static final int CONTAINER = 37;
        // 38 - 40 gap
        public static final int TITLE = 41;
        public static final int FULL_INPUT = 42;
        // 43 - 49 gap
        public static final int GE_SEARCH_RESULTS = 50;
        // 51 - 52 gap
        public static final int MESSAGES_CONTAINER = 53;
        public static final int TRANSPARENT_BACKGROUND_LINES_CONTAINER = 54;
        public static final int INPUT_LABEL = 55;
        public static final int MESSAGE_LINES_CONTAINER = 56;
        public static final int FIRST_MESSAGE_LABEL = 57;
        public static final int LAST_MESSAGE_LABEL = 556;
        public static final int SCROLLBAR_CONTAINER = 557;
        public static final int DIALOG_CONTAINER = 559;
    }

    /**
     * Describes private messages widget
     * Last reviewed: 1/3/2022 0:41 am UTC+1
     */
    public static class PrivateMessages {
        public static final int GROUP_INDEX = 163;
        public static final int PARENT_CONTAINER = 0;
    }

    /**
     * Describes resizable classic viewport widgets
     * Last reviewed: 22/3/2022 2:06 am UTC+1
     */
    public static class ResizableModernViewport {
        public static final int GROUP_INDEX = 164;
        // 0 - 14 gap
        public static final int ENTIRE_VIEWPORT_CONTAINER = 14;
        public static final int INNER_VIEWPORT_PARENT_CONTAINER = 15;
        public static final int DIALOG_WINDOW_CONTAINER = 16;
        public static final int INNER_VIEWPORT_CONTAINER = 17;
        // 18
        public static final int MULTICOMBAT_AREA_ICON_SPRITE = 19;
        // 20 pvp related?
        public static final int MINIMAP_CONTAINER = 21;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_ONE_CONTAINER = 22;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_TWO_CONTAINER = 23;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_THREE_CONTAINER = 24;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_FOUR_CONTAINER = 25;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_FIVE_CONTAINER = 26;
        public static final int MINIMAP_DRAW_AREA_SEGMENT_SIX_CONTAINER = 27;
        public static final int MINIMAP_COMPASS_SPRITE = 28;
        public static final int MINIMAP_DRAW_AREA_SPRITE = 29;
        public static final int MINIMAP_COMPASS_DYNAMIC_CONTAINER = 30;
        public static final int MINIMAP_SPRITE = 31;
        public static final int MINIMAP_ORB_HOLDER = 32;
        public static final int LOGOUT_SPRITE = 33;               // Interactions: Logout
        public static final int LOGOUT_BUTTON_SPRITE = 34;
        public static final int BOTTOM_BAR_SPRITE = 35;
        public static final int BOTTOM_BAR_CONTAINER = 36;
        public static final int TAB_CHAT_CHANNEL_SPRITE = 37;     // Interactions: Chat Channel
        public static final int TAB_ACC_MANAGEMENT_SPRITE = 38;   // Interactions: Account Management
        public static final int TAB_FRIENDS_LIST_SPRITE = 39;     // Interactions: Friends List
        public static final int TAB_SETTINGS_SPRITE = 40;         // Interactions: Settings
        public static final int TAB_EMOTES_SPRITE = 41;           // Interactions: Emotes
        public static final int TAB_MUSIC_PLAYER_SPRITE = 42;     // Interactions: Music Player
        public static final int TAB_GROUPING_ICON_SPRITE = 43;
        public static final int TAB_ACC_MANAGEMENT_ICON_SPRITE  = 44;
        public static final int TAB_FRIENDS_LIST_ICON_SPRITE  = 45;
        public static final int TAB_SETTINGS_ICON_SPRITE  = 46;
        public static final int TAB_EMOTES_ICON_SPRITE  = 47;
        public static final int TAB_MUSIC_PLAYER_ICON_SPRITE = 48;
        public static final int TOP_BAR_SPRITE = 49;
        public static final int TOP_BAR_CONTAINER = 50;
        public static final int TAB_COMBAT_OPTIONS_SPRITE = 51;   // Interactions: Combat Options
        public static final int TAB_SKILLS_SPRITE = 52;           // Interactions: Skills
        public static final int TAB_QUEST_LIST_SPRITE = 53;           // Interactions: Leagues
        public static final int TAB_INVENTORY_SPRITE = 54;        // Interactions: Inventory
        public static final int TAB_WORN_EQUIPMENT_SPRITE= 55;    // Interactions: Worn Equipment
        public static final int TAB_PRAYER_SPRITE = 56;           // Interactions: Prayer
        public static final int TAB_MAGIC_SPRITE = 57;            // Interactions: Magic, Disable spell filtering
        public static final int TAB_COMBAT_ICON_SPRITE = 58;
        public static final int TAB_SKILLS_ICON_SPRITE  = 59;
        public static final int TAB_QUEST_LIST_ICON_SPRITE  = 60;
        public static final int TAB_INVENTORY_ICON_SPRITE  = 61;
        public static final int TAB_WORN_EQUIPMENT_ICON_SPRITE  = 62;
        public static final int TAB_PRAYER_ICON_SPRITE = 63;
        public static final int TAB_MAGIC_ICON_SPRITE = 64;
        // 65 - 68 gap
        public static final int INVENTORY_SPRITE = 69;
        public static final int INVENTORY_SPECIAL_CONTAINER = 70; // content type 1354
        public static final int INVENTORY_PARENT_CONTAINER = 71;
        // 72 - 74 gap
        public static final int INVENTORY_CONTAINER = 75; // Contains the elements of inventory [149,0]
        // 76 - 90 gap
        public static final int MINIMAP_WIDGET_CONTAINER = 90;
        public static final int CHATBOX_PARENT_CONTAINER = 91;
        public static final int BOTTOM_BAR_PARENT = 92;
        public static final int TOP_BAR_PARENT = 93;
        public static final int INVENTORY_DYNAMIC_PARENT_CONTAINER = 94; // Contains dynamically placed sprites and other inventory parents
    }

    /**
     * Describes deposit box widgets
     * Last reviewed: 12/3/2022 1:26 am UTC+1
     */
    public static class Logout {
        public static final int GROUP_INDEX = 182;
        public static final int PARENT_CONTAINER = 0;
        public static final int BOTTOM_BAR_CONTAINER = 1;
        public static final int BOTTOM_BAR_TITLE_LABEL = 2;
        public static final int BUTTON_WORLD_SWITCHER_CONTAINER = 3;
        public static final int BUTTON_WORLD_SWITCHER_MAIN_SPRITE= 4;
        public static final int BUTTON_WORLD_SWITCHER_LEFT_SPRITE= 5;
        public static final int BUTTON_WORLD_SWITCHER_RIGHT_SPRITE= 6;
        public static final int BUTTON_WORLD_SWITCHER_LABEL = 7;
        public static final int BUTTON_LOGOUT_CONTAINER = 8;
        public static final int BUTTON_LOGOUT_MAIN_SPRITE= 9;
        public static final int BUTTON_LOGOUT_LEFT_SPRITE= 10;
        public static final int BUTTON_LOGOUT_RIGHT_SPRITE= 11;
        public static final int BUTTON_LOGOUT_LABEL = 12;
        public static final int TOP_BAR_CONTAINER = 13;
        public static final int TOP_BAR_TITLE_LABEL = 14;
        public static final int TOP_BAR_BUTTONS_CONTAINER = 15;
        public static final int THUMB_UP_CONTAINER = 16;
        public static final int THUMB_UP_LEFT_SPRITE = 17;
        public static final int THUMB_UP_RIGHT_SPRITE = 18;
        public static final int THUMB_UP_MAIN_SPRITE = 19;
        public static final int THUMB_DOWN_CONTAINER = 20;
        public static final int THUMB_DOWN_LEFT_SPRITE = 21;
        public static final int THUMB_DOWN_RIGHT_SPRITE = 22;
        public static final int THUMB_DOWN_MAIN_SPRITE = 23;
    }

    /**
     * Describes adventure log widget
     * Last reviewed: 1/3/2022 0:59 am UTC+1
     */
    public static class AdventureLog {
        public static final int GROUP_INDEX = 187;
        public static final int PARENT_DYNAMIC_CONTAINER = 0; //0 model, 1 title
        public static final int UNKNOWN_DYNAMIC_CONTAINER = 1;
        public static final int SCROLLBAR_CONTAINER = 2; // NOTE: unclear
        public static final int CHOICES_DYNAMIC_CONTAINER = 3; // contains choices 0-12
        public static final int CLOSE_BUTTON_SPRITE = 4;
    }

    /**
     * Describes mime random event widgets
     * Last reviewed: 1/3/2022 1:14 am UTC+1
     */
    public static class Mime {
        public static final int GROUP_INDEX = 188;
        public static final int PARENT_CONTAINER = 0;
        public static final int CONTAINER = 1;
        public static final int BUTTON_THINK_DYNAMIC_CONTAINER = 2; // 0-8 sprites, 0 main sprite,  9 label
        public static final int BUTTON_LAUGH_DYNAMIC_CONTAINER = 3;
        public static final int BUTTON_CLIMB_ROPE_DYNAMIC_CONTAINER = 4;
        public static final int BUTTON_GLASS_BOX_DYNAMIC_CONTAINER = 5;
        public static final int BUTTON_CRY_DYNAMIC_CONTAINER = 6;
        public static final int BUTTON_DANCE_DYNAMIC_CONTAINER = 7;
        public static final int BUTTON_LEAN_DYNAMIC_CONTAINER = 8;
        public static final int BUTTON_GLASS_WALL_DYNAMIC_CONTAINER = 9;
        public static final int LEFT_SIDE_FACEMASK_MODEL = 10;
        public static final int RIGHT_SIDE_FACEMASK_MODEL = 11;
    }

    /**
     * Describes deposit box widgets
     * Last reviewed: 28/2/2022 2:51 am UTC+1
     */
    public static class DepositBox {
        public static final int GROUP_INDEX = 192;
        public static final int PARENT_CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; // Contains stone border sprites 0-11
        public static final int ITEMS_DYNAMIC_CONTAINER = 2; // Contains sprites of items inside deposit box 0-27
        public static final int BOTTOM_BAR_RIGHT_SIDE_CONTAINER = 3;
        public static final int BUTTON_DEPOSIT_INVENTORY_SPRITE = 4;
        public static final int BUTTON_DEPOSIT_INVENTORY_ICON_SPRITE = 5;
        public static final int BUTTON_DEPOSIT_WORN_ITEMS_SPRITE = 6;
        public static final int BUTTON_DEPOSIT_WORN_ITEMS_ICON_SPRITE = 7;
        public static final int BUTTON_DEPOSIT_LOOT_SPRITE = 8;
        public static final int BUTTON_DEPOSIT_LOOT_ICON_SPRITE = 9;
        public static final int BOTTOM_BAR_LEFT_SIDE_CONTAINER = 10;
        public static final int BUTTON_DEFAULT_QUANTITY_1_DYNAMIC_CONTAINER = 11; // Contains button sprites 0-8
        public static final int BUTTON_DEFAULT_QUANTITY_1_LABEL = 12;
        public static final int BUTTON_DEFAULT_QUANTITY_5_DYNAMIC_CONTAINER = 13; // Contains button sprites 0-8
        public static final int BUTTON_DEFAULT_QUANTITY_5_LABEL = 14;
        public static final int BUTTON_DEFAULT_QUANTITY_10_DYNAMIC_CONTAINER = 15; // Contains button sprites 0-8
        public static final int BUTTON_DEFAULT_QUANTITY_10_LABEL = 16;
        public static final int BUTTON_DEFAULT_QUANTITY_X_DYNAMIC_CONTAINER = 17; // Contains button sprites 0-8
        public static final int BUTTON_DEFAULT_QUANTITY_X_LABEL = 18;
        public static final int BUTTON_DEFAULT_QUANTITY_ALL_DYNAMIC_CONTAINER = 19; // Contains button sprites 0-8
        public static final int BUTTON_DEFAULT_QUANTITY_ALL_LABEL = 20;
    }

    /**
     * Describes dialog which appears during quests giving/receiving item
     * Last reviewed: 1/3/2022 1:37 am UTC+1
     * TODO: Unclear if solely related to quests
     */
    public static class Dialog {
        public static final int GROUP_INDEX = 193;
        public static final int PARENT_DYNAMIC_CONTAINER = 0; // 2 option_text_label "Click here to continue"
        public static final int LEFT_SIDE_ITEM_MODEL = 1;
        public static final int TEXT_LABEL = 2;
    }

    /**
     * Describes spell autocast widgets
     * Last reviewed: 3/3/2022 4:46 am UTC+1
     */
    public static class SpellAutocast {
        public static final int GROUP_INDEX = 201;
        public static final int PARENT_CONTAINER = 0;
        public static final int SPELLS_DYNAMIC_CONTAINER = 1;
        public static final int SPELL_INFO_BOX_DYNAMIC_CONTAINER = 2;
    }

    /**
     * Describes emotes tab
     * Last reviewed: 18/3/2022 4:59 am UTC+1
     */
    public static class EmotesTab {
        public static final int GROUP_INDEX = 216;
        public static final int PARENT_CONTAINER = 0;
        public static final int EMOTES_DYNAMIC_CONTAINER = 1; // 0-50 emote sprites
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 2; // 0-5 scrollbar sprites
        public static final int TOOLTIP_CONTAINER = 3;
    }

    /**
     * Describes dialog which appears during quests giving/receiving item
     * Last reviewed: 1/3/2022 1:57 am UTC+1
     */
    public static class DialogPlayer {
        public static final int GROUP_INDEX = 217;
        public static final int PARENT_DYNAMIC_CONTAINER = 0; //2 option_text_label "Click here to continue"
        public static final int PLAYER_HEAD_MODEL = 2;
        public static final int TEXT_CONTAINER = 3;
        public static final int PLAYER_NAME_LABEL = 4;
        public static final int OPTION_CONTINUE_LABEL = 5;
        public static final int PLAYER_TEXT_LABEL = 6;
    }

    /**
     * Describes spellbook tab widgets
     * Last reviewed: 3/3/2022 4:21 am UTC+1
     */
    public static class SpellbookTab {
        public static final int GROUP_INDEX = 218;
        public static final int PARENT_CONTAINER = 0;
        public static final int INNER_FRAME_CONTAINER = 1;
        // 2
        public static final int SPELLS_CONTAINER = 3;
        // 4
        public static final int CATHERBY_HOME_TELEPORT_SPRITE = 5; // on Leagues
        public static final int LUMBRIDGE_HOME_TELEPORT_SPRITE = 6;
        public static final int WIND_STRIKE_SPRITE = 7;
        public static final int CONFUSE_SPRITE = 8;
        public static final int ENCHANT_CROSSBOW_BOLT_SPRITE = 9;
        public static final int WATER_STRIKE_SPRITE = 10;
        public static final int LVL_1_ENCHANT_SPRITE = 11;
        public static final int EARTH_STRIKE_SPRITE = 12;
        public static final int WEAKEN_SPRITE = 13;
        public static final int FIRE_STRIKE_SPRITE = 14;
        public static final int BONES_TO_BANANAS_SPRITE = 15;
        public static final int WIND_BOLT_SPRITE = 16;
        public static final int CURSE_SPRITE = 17;
        public static final int BIND_SPRITE = 18;
        public static final int LOW_LEVEL_ALCHEMY_SPRITE = 19;
        public static final int WATER_BOLT_SPRITE = 20;
        public static final int VARROCK_TELEPORT_SPRITE = 21;
        public static final int LVL_2_ENCHANT_SPRITE = 22;
        public static final int EARTH_BOLT_SPRITE = 23;
        public static final int LUMBRIDGE_TELEPORT_SPRITE = 24;
        public static final int TELEKINETIC_GRAB_SPRITE = 25;
        public static final int FIRE_BOLT_SPRITE = 26;
        public static final int FALADOR_TELEPORT_SPRITE = 27;
        public static final int CRUMBLE_UNDEAD_SPRITE = 28;
        public static final int TELEPORT_TO_HOUSE_SPRITE = 29;
        public static final int WIND_BLAST_SPRITE = 30;
        public static final int SUPERHEAT_ITEM_SPRITE = 31;
        public static final int CAMELOT_TELEPORT_SPRITE = 32;
        public static final int WATER_BLAST_SPRITE = 33;
        public static final int LVL_3_ENCHANT_SPRITE = 34;
        public static final int IBAN_BLAST_SPRITE = 35;
        public static final int SNARE_SPRITE = 36;
        public static final int MAGIC_DART_SPRITE = 37;
        public static final int ARDOUGNE_TELEPORT_SPRITE = 38;
        public static final int EARTH_BLAST_SPRITE = 39;
        public static final int HIGH_LEVEL_ALCHEMY_SPRITE = 40;
        public static final int CHARGE_WATER_ORB_SPRITE = 41;
        public static final int LVL_4_ENCHANT_SPRITE = 42;
        public static final int WATCHTOWER_TELEPORT_SPRITE = 43;
        public static final int FIRE_BLAST_SPRITE = 44;
        public static final int CHARGE_EARTH_ORB_SPRITE = 45;
        public static final int BONES_TO_PEACHES_SPRITE = 46;
        public static final int SARADOMIN_STRIKE_SPRITE = 47;
        public static final int CLAWS_OF_GUTHIX_SPRITE = 48;
        public static final int FLAMES_OF_ZAMORAK_SPRITE = 49;
        public static final int TROLLHEIM_TELEPORT_SPRITE = 50;
        public static final int WIND_WAVE_SPRITE = 51;
        public static final int CHARGE_FIRE_ORB_SPRITE = 52;
        public static final int APE_ATOLL_TELEPORT_SPRITE = 53;
        public static final int WATER_WAVE_SPRITE = 54;
        public static final int CHARGE_AIR_ORB_SPRITE = 55;
        public static final int VULNERABILITY_SPRITE = 56;
        public static final int LVL_5_ENCHANT_SPRITE = 57;
        public static final int KOUREND_CASTLE_TELEPORT_SPRITE = 58;
        public static final int EARTH_WAVE_SPRITE = 59;
        public static final int ENFEEBLE_SPRITE = 60;
        public static final int TELEOTHER_LUMBRIDGE_SPRITE = 61;
        public static final int FIRE_WAVE_SPRITE = 62;
        public static final int ENTANGLE_SPRITE = 63;
        public static final int STUN_SPRITE = 64;
        public static final int CHARGE_SPRITE = 65;
        public static final int WIND_SURGE_SPRITE = 66;
        public static final int TELEOTHER_FALADOR_SPRITE = 67;
        public static final int WATER_SURGE_SPRITE = 68;
        public static final int TELE_BLOCK_SPRITE = 69;
        public static final int TELEPORT_TO_TARGET_SPRITE = 70;
        public static final int LVL_6_ENCHANT_SPRITE = 71;
        public static final int TELEOTHER_CAMELOT_SPRITE = 72;
        public static final int EARTH_SURGE_SPRITE = 73;
        public static final int LVL_7_ENCHANT_SPRITE = 74;
        public static final int FIRE_SURGE_SPRITE = 75;
        // Ancient
        public static final int ICE_RUSH_SPRITE = 76;
        public static final int ICE_BLITZ_SPRITE = 77;
        public static final int ICE_BURST_SPRITE = 78;
        public static final int ICE_BARRAGE_SPRITE = 79;
        public static final int BLOOD_RUSH_SPRITE = 80;
        public static final int BLOOD_BLITZ_SPRITE = 81;
        public static final int BLOOD_BURST_SPRITE = 82;
        public static final int BLOOD_BARRAGE_SPRITE = 83;
        public static final int SMOKE_RUSH_SPRITE = 84;
        public static final int SMOKE_BLITZ_SPRITE = 85;
        public static final int SMOKE_BURST_SPRITE = 86;
        public static final int SMOKE_BARRAGE_SPRITE = 87;
        public static final int SHADOW_RUSH_SPRITE = 88;
        public static final int SHADOW_BLITZ_SPRITE = 89;
        public static final int SHADOW_BURST_SPRITE = 90;
        public static final int SHADOW_BARRAGE_SPRITE = 91;
        public static final int PADDEWWA_TELEPORT_SPRITE = 92;
        public static final int SENNTISTEN_TELEPORT_SPRITE = 93;
        public static final int KHARYRLL_TELEPORT_SPRITE = 94;
        public static final int LASSAR_TELEPORT_SPRITE = 95;
        public static final int DAREEYAK_TELEPORT_SPRITE = 96;
        public static final int CARRALLANGAR_TELEPORT_SPRITE = 97;
        public static final int ANNAKARL_TELEPORT_SPRITE = 98;
        public static final int GHORROCK_TELEPORT_SPRITE = 99;
        public static final int EDGEVILLE_HOME_TELEPORT_SPRITE = 100;
        // Lunar
        public static final int LUNAR_HOME_TELEPORT_SPRITE = 101;
        public static final int BAKE_PIE_SPRITE = 102;
        public static final int CURE_PLANT_SPRITE = 103;
        public static final int MONSTER_EXAMINE_SPRITE = 104;
        public static final int NPC_CONTACT_SPRITE = 105;
        public static final int CURE_OTHER_SPRITE = 106;
        public static final int HUMIDIFY_SPRITE = 107;
        public static final int MOONCLAN_TELEPORT_SPRITE = 108;
        public static final int TELE_GROUP_MOONCLAN_SPRITE = 109;
        public static final int CURE_ME_SPRITE = 110;
        public static final int HUNTER_KIT_SPRITE = 111;
        public static final int WATERBIRTH_TELEPORT_SPRITE = 112;
        public static final int TELE_GROUP_WATERBIRTH_SPRITE = 113;
        public static final int CURE_GROUP_SPRITE = 114;
        public static final int STAT_SPY_SPRITE = 115;
        public static final int BARBARIAN_TELEPORT_SPRITE = 116;
        public static final int TELE_GROUP_BARBARIAN_SPRITE = 117;
        public static final int SUPERGLASS_MAKE_SPRITE = 118;
        public static final int TAN_LEATHER_SPRITE = 119;
        public static final int KHAZARD_TELEPORT_SPRITE = 120;
        public static final int TELE_GROUP_KHAZARD_SPRITE = 121;
        public static final int DREAM_SPRITE = 122;
        public static final int STRING_JEWELLERY_SPRITE = 123;
        public static final int STAT_RESTORE_POT_SHARE_SPRITE = 124;
        public static final int MAGIC_IMBUE_SPRITE = 125;
        public static final int FERTILE_SOIL_SPRITE = 126;
        public static final int BOOST_POTION_SHARE_SPRITE = 127;
        public static final int FISHING_GUILD_TELEPORT_SPRITE = 128;
        public static final int TELE_GROUP_FISHING_GUILD_SPRITE = 129;
        public static final int PLANK_MAKE_SPRITE = 130;
        public static final int CATHERBY_TELEPORT_SPRITE = 131;
        public static final int TELE_GROUP_CATHERBY_SPRITE = 132;
        public static final int RECHARGE_DRAGONSTONE_SPRITE = 133;
        public static final int ICE_PLATEAU_TELEPORT_SPRITE = 134;
        public static final int TELE_GROUP_ICE_PLATEAU_SPRITE = 135;
        public static final int ENERGY_TRANSFER_SPRITE = 136;
        public static final int HEAL_OTHER_SPRITE = 137;
        public static final int VENGEANCE_OTHER_SPRITE = 138;
        public static final int VENGEANCE_SPRITE = 139;
        public static final int HEAL_GROUP_SPRITE = 140;
        public static final int SPELLBOOK_SWAP_SPRITE = 141;
        public static final int GEOMANCY_SPRITE = 142;
        public static final int SPIN_FLAX_SPRITE = 143;
        public static final int OURANIA_TELEPORT_SPRITE = 144;
        //  Arceeus book
        public static final int ARCEEUS_HOME_TELEPORT_SPRITE = 145;
        public static final int BASIC_REANIMATION = 146;
        // TODO: arceeus spells 147 - 188
        public static final int RESSURECT_GREATER_ZOMBIE = 189;
        // 190 - 191
        public static final int SPELL_FILTERS_DYNAMIC_CONTAINER = 192;
        public static final int SPELL_FILTERS_FRAME_SPRITE = 193;
        public static final int SPELL_FILTERS_TITLE_LABEL = 194;
        public static final int SPELL_FILTERS_INNER_MSG_DYNAMIC_CONTAINER = 195;
        public static final int BOTTOM_BAR_CONTAINER = 196;
        // 197
        public static final int FILTERS_DYNAMIC_CONTAINER = 198; // Contains 0-8 button sprites 9 button label
    }

    /**
     * Describes npc dialog options widgets
     * Last reviewed: 19/3/2022 2:21 am UTC+1
     */
    public static class DialogOptions {
        public static final int GROUP_INDEX = 219;
        public static final int CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; //0 select an option title, 1-x options
    }

    /**
     * Describes npc dialog widgets
     * Last reviewed: 19/3/2022 0:19 am UTC+1
     */
    public static class DialogNPC {
        public static final int GROUP_INDEX = 231;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_CONTAINER = 1;
        public static final int NPC_HEAD_MODEL = 2;
        public static final int DIALOG_CONTAINER = 3;
        public static final int DIALOG_TITLE_LABEL = 4;
        public static final int DIALOG_CONTINUE_LABEL = 5;
        public static final int DIALOG_TEXT_LABEL = 6;
    }

    /**
     * Describes music player tab widgets
     * Last reviewed: 18/3/2022 4:49 am UTC+1
     */
    public static class MusicPlayerTab {
        public static final int GROUP_INDEX = 239;
        public static final int PARENT_DYNAMIC_CONTAINER = 0; //0-1 toggle all sprite, search sprite
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 1;
        public static final int INNER_FRAME_BOX = 2;
        public static final int MUSIC_TRACK_LIST_DYNAMIC_CONTAINER = 3; //0-679, music labels
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 4; //0-5, scrollbar sprites
        public static final int PLAYING_TEXT_LABEL = 5;
        public static final int CURRENT_TRACK_LABEL = 6;
        public static final int AUTO_BUTTON_SPRITE = 7;
        public static final int AUTO_BUTTON_LABEL = 8;
        public static final int MANUAL_BUTTON_SPRITE = 9;
        public static final int MANUAL_BUTTON_LABEL = 10;
        public static final int LOOP_BUTTON_SPRITE = 11;
        public static final int LOOP_BUTTON_LABEL = 12;
        public static final int UNLOCKED_TRACKS_COUNT = 13;
    }

    /**
     * Describes genie lamp skill selection widget
     * Last reviewed: 2/4/2022 9:48 pm UTC+1
     */
    public static class GenieLampWindow {
        public static final int GROUP_INDEX = 240;
        public static final int PARENT_CONTAINER = 0;
        public static final int BACKGROUND_STONE_MODEL = 1;
        public static final int ATTACK_DYNAMIC_CONTAINER = 2; // contains 0-8 lines/corners of skill frame, 9 skill icon
        public static final int STRENGHT_DYNAMIC_CONTAINER = 3;
        public static final int RANGED_DYNAMIC_CONTAINER = 4;
        public static final int MAGIC_DYNAMIC_CONTAINER = 5;
        public static final int DEFENSE_DYNAMIC_CONTAINER = 6;
        public static final int HITPOINTS_DYNAMIC_CONTAINER = 7;
        public static final int PRAYER_DYNAMIC_CONTAINER = 8;
        public static final int AGILITY_DYNAMIC_CONTAINER = 9;
        public static final int HERBOLORE_DYNAMIC_CONTAINER = 10;
        public static final int THIEVING_DYNAMIC_CONTAINER = 11;
        public static final int CRAFTING_DYNAMIC_CONTAINER = 12;
        public static final int RUNECRAFTING_DYNAMIC_CONTAINER = 13;
        public static final int SLAYER_DYNAMIC_CONTAINER = 14;
        public static final int FARMING_DYNAMIC_CONTAINER = 15;
        public static final int MINING_DYNAMIC_CONTAINER = 16;
        public static final int SMITHING_DYNAMIC_CONTAINER = 17;
        public static final int FISHING_DYNAMIC_CONTAINER = 18;
        public static final int COOKING_DYNAMIC_CONTAINER = 19;
        public static final int FIREMAKING_DYNAMIC_CONTAINER = 20;
        public static final int WOODCUTTING_DYNAMIC_CONTAINER = 21;
        public static final int FLETCHING_DYNAMIC_CONTAINER = 22;
        public static final int CONSTRUCTION_DYNAMIC_CONTAINER = 23;
        public static final int HUNTER_DYNAMIC_CONTAINER = 24;
        public static final int TITLE_LABEL = 25;
        public static final int CONFIRM_DYNAMIC_CONTAINER = 26; // 0 text label
        public static final int CLOSE_BUTTON_SPIRTE = 27;
    }

    /**
     * Describes kourend favor widget
     * Last reviewed: 2/4/2022 9:58 pm UTC+1
     */
    public static class KourendFavor {
        public static final int GROUP_INDEX = 245;
        public static final int PARENT_CONTAINER = 0;
        public static final int KOUREND_FAVOR_TITLE_LABEL = 1;
    }

    /**
     * Describes music player tab widgets
     * Last reviewed: 21/3/2022 11:06 pm UTC+1
     */
    public static class AchievementDiaries {
        public static final int GROUP_INDEX = 259;
        public static final int PARENT_CONTAINER = 0;
        public static final int ACHIEVEMENT_DIARIES_TITLE_LABEL = 1;
        public static final int ACHIEVEMENT_DIARIES_DYNAMIC_CONTAINER = 2;
        public static final int SPRITES_DYNAMIC_CONTAINER = 3;
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 4;
    }

    /**
     * Describes store widgets
     * Last reviewed: 20/3/2022 10:58 pm UTC+1
     */
    public static class MakeDialog {
        public static final int GROUP_INDEX = 270;
        public static final int PARENT_CONTAINER = 0;
        public static final int SPRITE = 1;
        public static final int TOP_BAR_CONTAINER = 2;
        public static final int TOP_BAR_TITLE_CONTAINER = 3;
        public static final int TOP_BAR_FIRST_TITLE_LABEL = 4;
        public static final int TOP_BAR_SECOND_TITLE_LABEL = 5;
        public static final int TOP_BAR_BUTTON_ALL_CONTAINER = 6;
        public static final int BUTTON_ONE_DYNAMIC_CONTAINER = 7; // contains sprites, label[9]
        public static final int BUTTON_FIVE_DYNAMIC_CONTAINER = 8; // contains sprites, label[9]
        // 9 container
        // 10
        public static final int BUTTON_X_DYNAMIC_CONTAINER = 11; // contains sprites, label[9]
        public static final int BUTTON_ALL_DYNAMIC_CONTAINER = 12; // contains sprites, label[9]
        public static final int BOTTOM_BAR_DYN_CONTAINER = 13; // 0-8 choices labels/keys
        public static final int BOTTOM_BAR_FIRST_CHOICE_BAR_DYN_CONTAINER = 14; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_SECOND_CHOICE_BAR_DYN_CONTAINER = 15; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_THIRD_CHOICE_BAR_DYN_CONTAINER = 16; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_FOURTH_CHOICE_BAR_DYN_CONTAINER = 17; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_FIFTH_CHOICE_BAR_DYN_CONTAINER = 18; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_SIXTH_CHOICE_BAR_DYN_CONTAINER = 19; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_SEVENTH_CHOICE_BAR_DYN_CONTAINER = 20; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_EIGHT_CHOICE_BAR_DYN_CONTAINER = 21; // 29-37 sprites, 38 model
        public static final int BOTTOM_BAR_NINTH_CHOICE_BAR_DYN_CONTAINER = 22; // 29-37 sprites, 38 model
        public static final int UNKNOWN1_CONTAINER = 23;
        public static final int UNKNOWN2_CONTAINER = 24;
    }

    /**
     * Describes prison pete random event widgets after pulling lever
     * Last reviewed: 11/4/2022 11:06 pm UTC+1
     */
    public static class PrisonPete {
        public static final int GROUP_INDEX = 273;
        public static final int PARENT_CONTAINER = 0;
        public static final int IRON_BARS_MODEL = 1;
        public static final int TEXT_LABEL = 2;
        public static final int MODEL_CONTAINER = 3;
        public static final int BALLOON_ANIMAL_MODEL = 4; // Note: holds the modelId of balloon animals to pop
        public static final int BUTTON_CLOSE_SPRITE = 5;
    }

    /**
     * Describes become member ad widget
     * Last reviewed: 6/4/2022 0:17 am UTC+1
     */
    public static class BecomeMemberAd {
        public static final int GROUP_INDEX = 278;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_CONTAINER = 1;
        public static final int AD_SPRITE = 2;
        public static final int BUTTON_CLOSE_DYNAMIC_CONTAINER = 3; //0 close sprite
        public static final int BUTTON_UPGRADE_DYNAMIC_CONTAINER = 4; //0 upgrade now sprite
    }

    /**
     * Describes sandwich lady random event widgets
     * Last reviewed: 10/4/2022 0:12 am UTC+1
     */
    public static class SandwichLady {
        public static final int GROUP_INDEX = 297;
        public static final int PARENT_CONTAINER = 0;
        public static final int ENTIRE_MODEL = 1;
        public static final int FOOD_GIVEN_TEXT_LABEL = 2;
        public static final int BUTTON_CLOSE_SPRITE = 3;
        public static final int CHOICES_CONTAINER = 5;
        public static final int FIRST_CHOICE_MODEL = 6;
        public static final int SECOND_CHOICE_MODEL = 7;
        public static final int THIRD_CHOICE_MODEL = 8;
        public static final int FOURTH_CHOICE_MODEL = 9;
        public static final int FIFTH_CHOICE_MODEL = 10;
        public static final int SIXTH_CHOICE_MODEL = 11;
        public static final int SEVENTH_CHOICE_MODEL = 12;
        public static final int KEY_TEXT_LABEL = 13;
        // NOTE: choices are randomized every time read the modelIds
        // baguette_modelId = 10726
        // bread_modelId = 10727
        // chocolate_modelId = 10728
        // kebab_modelId = 10729
        // pie_modelId = 10730
        // square_sandwich_modelId = 10731
        // triangle_sandwich_modelId = 10732
    }

    /**
     * Describes store widgets
     * Last reviewed: 2/3/2022 1:47 am UTC+1
     */
    public static class Store {
        public static final int GROUP_INDEX = 300;
        public static final int PARENT_CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; // Contains stone border sprites 0-11
        public static final int ITEMS_DYNAMIC_CONTAINER = 16; // Contains items sold sprites 0-16
        public static final int BOTTOM_TEXT_LABEL = 18;
    }

    /**
     * Describes poll history widget
     * Last reviewed: 9/4/2022 11:17 pm UTC+1
     */
    public static class PollHistory {
        public static final int GROUP_INDEX = 310;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_PARENT_CONTAINER = 1;
        public static final int FRAME_DYNAMIC_CONTAINER = 2; // 0 entire sprite, 1 title label, 2-10 corners/edges sprites, 11 close button sprite
        public static final int CHOOSE_POLL_TEXT_LABEL = 3;
        public static final int POLLS_DYNAMIC_CONTAINER = 4; // 30 box of first poll, 31 title label of first poll, 32 open from till label, this repeats ...
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 5; // 0 entire scrollbar sprite, 1 actual scrollbar sprite, 2 top separator sprite, 3 bottom separator sprite, 4 arrow up sprite, 5 arrow down sprite
    }

    /**
     * Describes smithing window widgets
     * Last reviewed: 18/3/2022 4:02 am UTC+1
     */
    public static class Smithing {
        public static final int GROUP_INDEX = 312;
        public static final int PARENT_CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; // borders sprites, title 1, close button 11
        public static final int BUTTONS_CONTAINER = 2;
        public static final int BUTTON_ONE_DYNAMIC_CONTAINER = 3;
        public static final int BUTTON_X_DYNAMIC_CONTAINER = 6;
        public static final int BUTTON_ALL_DYNAMIC_CONTAINER = 7;
        public static final int ITEM_ONE_DYNAMIC_CONTAINER = 9;
        public static final int ITEM_TWO_DYNAMIC_CONTAINER = 10;
        public static final int ITEM_THREE_DYNAMIC_CONTAINER = 11;
        public static final int ITEM_FOUR_DYNAMIC_CONTAINER = 12;
        public static final int ITEM_FIFE_DYNAMIC_CONTAINER = 13;
        public static final int ITEM_SIX_DYNAMIC_CONTAINER = 14;
        public static final int ITEM_SEVEN_DYNAMIC_CONTAINER = 15;
        public static final int ITEM_EIGHT_DYNAMIC_CONTAINER = 16;
        public static final int ITEM_NINE_DYNAMIC_CONTAINER = 17;
        public static final int ITEM_TEN_DYNAMIC_CONTAINER = 18;
        public static final int ITEM_ELEVEN_DYNAMIC_CONTAINER = 19;
        public static final int ITEM_TWELVE_DYNAMIC_CONTAINER = 20;
        public static final int ITEM_THIRTEEN_DYNAMIC_CONTAINER = 21;
        public static final int ITEM_FOURTEEN_DYNAMIC_CONTAINER = 22;
        public static final int ITEM_FIFTEEN_DYNAMIC_CONTAINER = 23;
        public static final int ITEM_SIXTEEN_DYNAMIC_CONTAINER = 24;
        public static final int ITEM_SEVENTEEN_DYNAMIC_CONTAINER = 25;
        public static final int ITEM_EIGHTEEN_DYNAMIC_CONTAINER = 26;
        public static final int ITEM_NINETEEN_DYNAMIC_CONTAINER = 27;
        public static final int ITEM_TWENTIETH_DYNAMIC_CONTAINER = 28;
        public static final int ITEM_TWENTYFIRST_DYNAMIC_CONTAINER = 29;
        public static final int ITEM_TWENTYSECOND_DYNAMIC_CONTAINER = 30;
        public static final int ITEM_TWENTYTHIRD_DYNAMIC_CONTAINER = 31;
        public static final int ITEM_TWENTYFOURTH_DYNAMIC_CONTAINER = 32;
    }

    /**
     * Describes skill tab widgets
     * Last reviewed: 28/2/2022 2:18 am UTC+1
     */
    public static class SkillsTab {
        public static final int GROUP_INDEX = 320;
        public static final int CONTAINER = 0;
        public static final int ATTACK_CONTAINER = 1;
        public static final int STRENGTH_CONTAINER = 2;
        public static final int DEFENCE_CONTAINER = 3;
        public static final int RANGED_CONTAINER = 4;
        public static final int PRAYER_CONTAINER = 5;
        public static final int MAGIC_CONTAINER = 6;
        public static final int RUNECRAFT_CONTAINER = 7;
        public static final int CONSTRUCTION_CONTAINER = 8;
        public static final int HITPOINTS_CONTAINER = 9;
        public static final int AGILITY_CONTAINER = 10;
        public static final int HERBLORE_CONTAINER = 11;
        public static final int THIEVING_CONTAINER = 12;
        public static final int CRAFTING_CONTAINER = 13;
        public static final int FLETCHING_CONTAINER = 14;
        public static final int SLAYER_CONTAINER = 15;
        public static final int HUNTER_CONTAINER = 16;
        public static final int MINING_CONTAINER = 17;
        public static final int SMITHING_CONTAINER = 18;
        public static final int FISHING_CONTAINER = 19;
        public static final int COOKING_CONTAINER = 20;
        public static final int FIREMAKING_CONTAINER = 21;
        public static final int WOODCUTTING_CONTAINER = 22;
        public static final int FARMING_CONTAINER = 23;
        public static final int TOTAL_LEVEL_CONTAINER = 24;
        public static final int TOTAL_LEVEL_LEFT_SIDE_SPRITE = 25;
        public static final int TOTAL_LEVEL_RIGHT_SIDE_SPRITE = 26;
        public static final int TOTAL_LEVEL_LABEL = 27;
        public static final int MOUSEOVER_TOOLTIP_CONTAINER = 28;
    }

    /**
     * Describes tanning window widget
     * Last reviewed: 6/4/2022 0:37 am UTC+1
     * TODO: will need more attention, seems to be wrongly resolved by WidgetInspector
     */
    public static class TanningWindow {
        public static final int GROUP_INDEX = 324;
        public static final int FIRST_CHOICE_CONTAINER = 92;   // Or dynamic container?
        public static final int SECOND_CHOICE_CONTAINER = 93;  // Or dynamic container?
        public static final int THIRD_CHOICE_CONTAINER = 94;   // Or dynamic container?
        public static final int FOURTH_CHOICE_CONTAINER = 95;  // Or dynamic container?
        public static final int FIFTH_CHOICE_CONTAINER = 96;   // Or dynamic container?
        public static final int SIXTH_CHOICE_CONTAINER = 97;   // Or dynamic container?
        public static final int SEVENTH_CHOICE_CONTAINER = 98; // Or dynamic container?
        public static final int EIGHT_CHOICE_CONTAINER = 99;   // Or dynamic container?
    }

    /**
     * Describes last man standing stat info widget
     * Last reviewed: 10/4/2022 0:42 am UTC+1
     */
    public static class LastManStandingStats {
        public static final int GROUP_INDEX = 333;
        public static final int VIEWPORT_CONTAINER = 0;
        public static final int LMS_PARENT_CONTAINER = 1;
        public static final int INFO_CONTAINER = 2;
        public static final int INFO_BOX = 3;
        public static final int INFO_INNER_BOX = 4;
        public static final int INFO_RANK_TEXT_LABEL = 5;
        public static final int INFO_CURRENT_RANK_LABEL = 6;
        public static final int INFO_COFFER_TEXT_LABEL = 7;
        public static final int INFO_CURRENT_DEPOSIT_LABEL = 8;
        public static final int INFO_CASUAL_TEXT_LABEL = 9;
        public static final int INFO_CURRENT_CASUAL_GAMES_LABEL = 10;
        public static final int INFO_COMPETITIVE_TEXT_LABEL = 11;
        public static final int INFO_CURRENT_COMPETITIVE_GAMES_LABEL = 12;
        public static final int INFO_HIGH_STAKES_TEXT_LABEL = 13;
        public static final int INFO_CURRENT_HIGH_STAKES_GAMES_LABEL = 14;
    }

    /**
     * Describe second trade screen widgets
     * Last reviewed: 5/3/2022 1:29 am UTC+1
     */
    public static class TradeSecondScreen {
        public static final int GROUP_INDEX = 334;
        public static final int TRADE_WINDOW_SHADOW_BOX = 0;
        public static final int PARENT_CONTAINER = 1;
        public static final int TRADE_WINDOW_DYNAMIC_CONTAINER = 2; //0-7 steel border sprites
        public static final int TRADE_WINDOW_SPRITE = 3;
        public static final int TRADE_WINDOW_TITLE_FIRST_ROW = 4;
        public static final int TRADE_WINDOW_TITLE_SECOND_ROW = 5;
        // 6-12 steel border sprites
        public static final int BUTTON_ACCEPT_SHADOW_BOX = 13;
        public static final int BUTTON_DECLINE_SHADOW_BOX = 14;
        // 15-22 steel border sprites
        public static final int MY_OFFER_VALUE_LABEL = 23;
        public static final int PARTNER_OFFER_VALUE_LABEL = 24;
        public static final int BUTTON_ACCEPT_LABEL = 25;
        public static final int BUTTON_DECLINE_LABEL = 26;
        public static final int PARTNER_UNKNOWN_CONTAINER = 27;
        public static final int MY_OFFER_ITEM_LIST_DYNAMIC_CONTAINER = 28;
        public static final int MY_PARTNER_OFFER_ITEM_LIST_DYNAMIC_CONTAINER = 29;
        public static final int NAME_OF_TRADE_PARTNER_LABEL = 30;
        // 31
        public static final int CLOSE_BUTTON_SPRITE = 32;
    }

    /**
     * Describes first trade screen widgets
     * Last reviewed: 5/3/2022 1:07 am UTC+1
     */
    public static class TradeFirstScreen {
        public static final int GROUP_INDEX = 335;
        public static final int PARENT_CONTAINER = 0;
        public static final int TRADE_WINDOW_SHADOW_BOX = 1;
        public static final int TRADE_WINDOW_CONTAINER = 2;
        public static final int TRADE_WINDOW_DYNAMIC_CONTAINER = 3;
        // 4-6 gap
        public final static int BUTTON_FREE_INV_SLOTS_DYNAMIC_CONTAINER = 7; // Contains 0-7 steel border sprites
        public final static int BUTTON_FREE_INV_SLOTS_BOX = 8;
        public final static int BUTTON_FREE_INV_SLOTS_LABEL = 9;
        public static final int BUTTON_ACCEPT_DYNAMIC_CONTAINER = 10; // Contains 0-7 steel border sprites
        public static final int BUTTON_ACCEPT_SHADOW_BOX = 11;
        public static final int BUTTON_ACCEPT_LABEL = 12;
        public static final int BUTTON_DECLINE_DYNAMIC_CONTAINER = 13; // Contains 0-7 steel border sprites
        public static final int BUTTON_DECLINE_SHADOW_BOX = 14;
        public static final int BUTTON_DECLINE_LABEL = 15;
        // 16-23 trade window steel border sprites
        public static final int MY_OFFER_VALUE_LABEL = 24;
        public static final int MY_OFFER_DYNAMIC_CONTAINER = 25;
        public static final int MY_OFFER_TRADE_STATUS_LABEL = 26;
        public static final int PARTNER_OFFER_VALUE_LABEL = 27;
        public static final int PARTNER_OFFER_DYNAMIC_CONTAINER = 28;
        public static final int PARTNER_OFFER_TRADE_STATUS_LABEL = 29;
        public static final int TRADE_STATUS_LABEL = 30;
        public static final int NAME_OF_TRADE_PARTNER_LABEL = 31;
    }

    /**
     *  Describes the player's inventory widget when the trade screen is open
     */
    public static class PlayerTradeInventory {
        public static final int GROUP_INDEX = 336;
        public static final int ITEMS_CONTAINER = 0; // contains 0 - 28 (yes: 0 through 28)
    }

    /**
     * Describes a concrete poll details widget
     * Last reviewed: 9/4/2022 11:17 pm UTC+1
     */
    public static class CurrentPollDetails {
        public static final int GROUP_INDEX = 345;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_PARENT_CONTAINER = 1;
        public static final int FRAME_DYNAMIC_CONTAINER = 2; // 0 entire sprite, 1 title label, 2-10 corners/edges sprites, 11 close button sprite
        public static final int POLL_STATE_TEXT_LABEL = 3; // Note: closed/open
        public static final int BUTTON_HISTORY_DYNAMIC_CONTAINER = 8; // 0 sprite, 1 text label
        public static final int POLLS_DETAILS_DYNAMIC_CONTAINER = 11; // 0 poll description text label, 1 close information label, 2 votes obtained label, 3 question 1 box, 4 shadow box, 5 green colour box, 6 q1 label, 7 q1 details label, 8 q1 yes label, 9 q1 yes percentage/votes total label, 10 q1 yes bar sprite, 11/12/13 same for no, 14 q1 skip label, 15 q1 skip votes
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 12; // 0 entire scrollbar sprite, 1 actual scrollbar sprite, 2 top separator sprite, 3 bottom separator sprite, 4 arrow up sprite, 5 arrow down sprite
    }

    /**
     * Describes click to play screen widgets
     * Last reviewed: 2/3/2022 2:28 am UTC+1
     */
    public static class ClickToPlayScreen {
        public static final int GROUP_INDEX = 378;
        public static final int PARENT_FIRST_CONTAINER = 0;
        public static final int PARENT_SECOND_CONTAINER = 1;
        public static final int INNER_FRAME_CONTAINER = 2;
        public static final int UNKNOWN_CONTAINER = 3;
        public static final int MOTW_CONTAINER = 4;
        public static final int UNKNOWN_MODEL = 5;
        public static final int MOTW_TITLE_LABEL = 6;
        public static final int MOTW_TEXT_LABEL = 7;
        // 8 - 55 gap
        public static final int MOTW_LEFT_SIDE_MODEL = 56;
        public static final int MOTW_RIGHT_SIDE_MODEL = 57;
        public static final int ABOVE_MOTW_CONTAINER = 58;
        public static final int SWITCH_STYLE_DYNAMIC_CONTAINER = 59; // Contains label[0]
        public static final int WELCOME_MSG_CONTAINER = 60;
        public static final int WELCOME_MSG_SPRITE = 61;
        public static final int WELCOME_MSG_TITLE_LABEL = 62;
        public static final int WELCOME_MSG_TEXT_LABEL = 63;
        // 64 - 73 gap
        public static final int BUTTON_MEMBERSHIP_SPRITE = 74;
        // 75
        public static final int BUTTON_MEMBERSHIP_ICON_SPRITE = 76;
        public static final int BUTTON_MEMBERSHIP_TEXT_LABEL = 77;
        public static final int BUTTON_CLICK_HERE_TO_PLAY_CONTAINER = 78;
        public static final int BUTTON_MEMBERSHIP_CONTAINER = 79;
        public static final int BUTTON_CLICK_HERE_TO_PLAY_SPRITE = 80;
        public static final int BUTTON_BANK_PIN_CONTAINER = 81;
        // 82
        public static final int BUTTON_UNREAD_MSG_CONTAINER = 83;
        public static final int BUTTON_CLICK_HERE_TO_PLAY_ICON_SPRITE = 84;
        public static final int BUTTON_PASSWORD_CONTAINER = 85;
        public static final int BANNER_CONTAINER = 86;
        public static final int BUTTON_CLICK_HERE_TO_PLAY_LABEL = 87;
    }

    /**
     * Describes bank widgets
     * Last reviewed: 8/4/2022 10:42 pm UTC+1
     */
    public static class GrandExchangeTradeHistory {
        public static final int GROUP_INDEX = 383;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; //0 entire sprite, 1 title label, 2-10 edges/corners sprites, close button sprite
        public static final int BUTTON_EXCHANGE_DYNAMIC_CONTAINER = 2;// 0-8 sprites, 1 text label
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 2;// 0 entire scrollbar sprite, 1 actual scrollbar sprite, 2 top separator sprite, 3 bottom separator sprite, 4 arrow up sprite, 5 arrow down sprite
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 2; // contains entries in history list
        // entry structure:
        // 0 entire row sprite, contains name of item
        // 1 backpack icon sprite
        // 2 completed offer type label
        // 3 item name label
        // 4 item sprite
        // 5 price text label
        // this repeats in ascending order so next row sprite is 6
    }

    /**
     * Describes worn equipment tab widgets
     * Last reviewed: 1/3/2022 1:21 am UTC+1
     */
    public static class WornEquipmentTab {
        public static final int GROUP_INDEX = 387;
        public static final int PARENT_CONTAINER = 0;
        public static final int EQUIPMENT_STATS_DYNAMIC_CONTAINER = 1;     // Contains button sprites 0-8 Interactions: View equipment stats
        public static final int EQUIPMENT_STATS_ICON_SPRITE = 2;
        public static final int GUIDE_PRICES_DYNAMIC_CONTAINER = 3;        // Contains button sprites 0-8 Interactions: View guide prices
        public static final int GUIDE_PRICES_ICON_SPRITE = 4;
        public static final int ITEMS_KEPT_ON_DEATH_DYNAMIC_CONTAINER = 5; // Contains button sprites 0-8 Interactions  View items kept on death
        public static final int ITEMS_KEPT_ON_DEATH_SPRITE = 6;
        public static final int CALL_FOLLOWER_DYNAMIC_CONTAINER = 7;       // Contains button sprites 0-8 Interactions  Call follower
        public static final int CALL_FOLLOWER_SPRITE = 8;
        public static final int MIDDLE_STRAIGHT_LINE_SPRITE = 9;
        public static final int LEFT_STRAIGHT_LINE_SPRITE = 10;
        public static final int RIGHT_STRAIGHT_LINE_SPRITE = 11;
        public static final int MIDDLE_HORIZONTAL_STRAIGHT_LINE_SPRITE = 12;
        public static final int TOP_HORIZONTAL_STRAIGHT_LINE_SPRITE = 13;
        // 14
        public static final int HELMET_DYNAMIC_CONTAINER = 15;  // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int CAPE_DYNAMIC_CONTAINER = 16;  // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int AMULET_DYNAMIC_CONTAINER = 17; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int WEAPON_DYNAMIC_CONTAINER = 18; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int BODY_DYNAMIC_CONTAINER = 19; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int SHIELD_DYNAMIC_CONTAINER = 20; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int LEGS_DYNAMIC_CONTAINER = 21; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int GLOVES_DYNAMIC_CONTAINER = 22; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int BOOTS_DYNAMIC_CONTAINER = 23; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int RING_DYNAMIC_CONTAINER = 24; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int AMMO_DYNAMIC_CONTAINER = 25; // Contains slot[0] sprite and item[1] sprite which has itemID
        public static final int UNKNOWN1_CONTAINER = 26;
        public static final int UNKNOWN2_CONTAINER = 27;
    }

    /**
     * Describes quest list sub tab widgets
     * Last reviewed: 18/3/2022 3:42 am UTC+1
     */
    public static class QuestListSubTab {
        public static final int GROUP_INDEX = 399;
        public static final int PARENT_CONTAINER = 0;
        public static final int QUEST_LIST_TITLE_LABEL = 1;
        public static final int DYNAMIC_CONTAINER = 2;
        public static final int QUEST_LIST_LIST = 3;
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 4;
        public static final int QUEST_LIST_CONTAINER = 5;
        public static final int FREE_QUESTS_DYNAMIC_CONTAINER = 6; // labels
        public static final int MEMBER_QUESTS_DYNAMIC_CONTAINER = 7; // labels
        public static final int MINIQUESTS_DYNAMIC_CONTAINER = 8; // labels
    }

    /**
     * Describes collection box widgets
     * Last reviewed: 7/4/2022 11:02 pm UTC+1
     */
    public static class CollectionBox {
        public static final int GROUP_INDEX = 402;
        public static final int PARENT_CONTAINER = 0;
        public static final int CONTAINER = 1;
        public static final int DYNAMIC_CONTAINER = 2; //0 entire sprite, 1 title label, 2-10 borders/edges sprites, 11 close button sprite
        public static final int BUTTON_INVENTORY_DYNAMIC_CONTAINER = 3; // 0 sprite, 1 text label
        public static final int BUTTON_BANK_DYNAMIC_CONTAINER = 4; // 0 sprite, 1 text label
        public static final int SLOT_FIRST_DYNAMIC_CONTAINER = 5; //0 entire sprite, 1 item sprite, 2 extra coins sprite, 5-12 edges/corners sprite, 13 status box, 14 status opacity layer, 16 status top shadow layer, 17 status left side shadow layer, 18 bag icon layer, 20 item minimized icon layer
        public static final int SLOT_SECOND_DYNAMIC_CONTAINER = 6; // sprites, layers, steel border 0-20
        public static final int SLOT_THIRD_DYNAMIC_CONTAINER = 7; // sprites, layers, steel border 0-20
        public static final int SLOT_FOURTH_DYNAMIC_CONTAINER = 8; // sprites, layers, steel border 0-20
        public static final int SLOT_FIFTH_DYNAMIC_CONTAINER = 9; // sprites, layers, steel border 0-20
        public static final int SLOT_SIXTH_DYNAMIC_CONTAINER = 10; // sprites, layers, steel border 0-20
        public static final int SLOT_SEVENTH_DYNAMIC_CONTAINER = 11; // sprites, layers, steel border 0-20
        public static final int SLOT_EIGHT_DYNAMIC_CONTAINER = 12; // sprites, layers, steel border 0-20
        public static final int UNKNOWN_CONTAINER = 13;
    }

    /**
     * Describes build canoe selection window
     * Last reviewed: 6/4/2022 0:56 am UTC+1
     */
    public static class BuildCanoeWindow {
        public static final int GROUP_INDEX = 416;
        public static final int PARENT_CONTAINER = 0;
        public static final int MODELS_CONTAINER = 1;
        public static final int WATER_MODEL = 2;
        public static final int FRAME_CONTAINER = 3;
        public static final int TRAIL_MODEL_CONTAINER = 4;
        public static final int STABLE_DUGOUT_TRAIL_MODEL = 5;
        public static final int DUGOUT_TRAIL_MODEL = 6;
        public static final int LOG_TRAIL_MODEL = 7;
        public static final int WAKA_TRAIL_MODEL = 8;
        public static final int BUTTON_CLOSE_DYNAMIC_CONTAINER = 9; // 0 close button sprite
        public static final int TEXT_DYNAMIC_CONTAINER = 10; // 0 TEXT_LABEL
        public static final int WAKA_MODEL_DYNAMIC_CONTAINER = 11; // 0 TEXT_LABEL
        public static final int STABLE_DUGOUT_MODEL_DYNAMIC_CONTAINER = 12; // 0 TEXT_LABEL
        // 13 - 16 unknown models
        public static final int STABLE_DUGOUT_MODEL = 17;
        public static final int DUGOUT_MODEL_DYNAMIC_CONTAINER = 18; // 0 TEXT_LABEL
        public static final int DUGOUT_MODEL = 19;
        public static final int LOG_MODEL_DYNAMIC_CONTAINER = 20; // 0 TEXT_LABEL
        public static final int LOG_MODEL = 21;
        public static final int WAKA_MODEL = 22;
    }

    /**
     * Describes friend list tab widgets
     * Last reviewed: 18/3/2022 2:46 am UTC+1
     */
    public static class FriendlistTab {
        public static final int GROUP_INDEX = 429;
        public static final int PARENT_CONTAINER = 0;
        public static final int VIEW_IGNORE_LIST_CONTAINER = 1;
        public static final int VIEW_IGNORE_LIST_SPRITE = 2;
        public static final int TITLE_LABEL = 3;
        public static final int DYNAMIC_CONTAINER = 4; // Contains dynamic components [0-7]
        // 5 - 12 gap
        public static final int ADD_FRIEND_TEXT_LABEL = 13;
        public static final int ADD_FRIEND_SPRITE = 14;
        public static final int ADD_FRIEND_LABEL = 15;
        public static final int DEL_FRIEND_SPRITE = 16;
        public static final int DEL_FRIEND_LABEL = 17;
        public static final int PREVIOUS_NAME_CONTAINER = 18;
    }

    /**
     * Describes silver crafting window widgets
     * Last reviewed: 7/4/2022 0:43 am UTC+1
     * TODO: bracelets were inferred not checked
     * TODO: actually name these models by a proper item name?
     */
    public static class GoldCraftingWindow {
        public static final int GROUP_INDEX = 446;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; // 0 entire sprite, 1 title label, 2-10 frame border/corners sprites, 11 close button sprite
        public static final int RINGS_LABEL = 2;
        public static final int RINGS_ROW_CONTAINER = 6;
        public static final int FIRST_RING_MODEL = 7;
        public static final int SECOND_RING_MODEL = 8;
        public static final int THIRD_RING_MODEL = 9;
        public static final int FOURTH_RING_MODEL = 10;
        public static final int FIFTH_RING_MODEL = 11;
        public static final int SIXTH_RING_MODEL = 12;
        public static final int SEVENTH_RING_MODEL = 13;
        public static final int NINTH_RING_MODEL = 14;
        public static final int EIGHT_RING_MODEL = 15;
        public static final int NECKLACES_LABEL = 16;
        public static final int NECKLACES_ROW_CONTAINER = 20;
        public static final int FIRST_NECKLACE_MODEL = 21;
        public static final int SECOND_NECKLACE_MODEL = 22;
        public static final int THIRD_NECKLACE_MODEL = 23;
        public static final int FOURTH_NECKLACE_MODEL = 24;
        public static final int FIFTH_NECKLACE_MODEL = 25;
        public static final int SIXTH_NECKLACE_MODEL = 26;
        public static final int SEVENTH_NECKLACE_MODEL = 27;
        public static final int EIGHT_NECKLACE_MODEL = 28;
        public static final int AMULETS_LABEL = 29;
        public static final int AMULETS_ROW_CONTAINER = 33;
        public static final int FIRST_AMULET_MODEL = 34;
        public static final int SECOND_AMULET_MODEL = 35;
        public static final int THIRD_AMULET_MODEL = 36;
        public static final int FOURTH_AMULET_MODEL = 37;
        public static final int FIFTH_AMULET_MODEL = 38;
        public static final int SIXTH_AMULET_MODEL = 39;
        public static final int SEVENTH_AMULET_MODEL = 40;
        public static final int EIGHT_AMULET_MODEL = 41;
        public static final int BRACELETS_LABEL = 42;
        public static final int BRACELETS_ROW_CONTAINER = 43;
        public static final int FIRST_BRACELET_MODEL = 44;
        public static final int SECOND_BRACELET_MODEL = 45;
        public static final int THIRD_BRACELET_MODEL = 46;
        public static final int FOURTH_BRACELET_MODEL = 47;
        public static final int FIFTH_BRACELET_MODEL = 48;
        public static final int SIXTH_BRACELET_MODEL = 49;
        public static final int BUTTONS_PARENT_CONTAINER = 55;
        public static final int BUTTON_1_DYNAMIC_CONTAINER = 56; // 0 entire sprite, 1-8 corners/edges sprites, 9 text label
        public static final int BUTTON_X_DYNAMIC_CONTAINER = 59; // 0 entire sprite, 1-8 corners/edges sprites, 9 text label
        public static final int BUTTON_ALL_DYNAMIC_CONTAINER = 60; // 0 entire sprite, 1-8 corners/edges sprites, 9 text label
    }

    /**
     * Describes grand exchange widgets
     * Last reviewed: 8/4/2022 0:14 am UTC+1
     */
    public static class ItemSetsWindow {
        public static final int GROUP_INDEX = 451;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; // 0 frame sprite, 1 title label, 2-10 corners/edges sprites, 11 close button sprite
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 2; // 0-104 set sprites
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 3; // 0 entire scrollbar sprite, 1 actual scrollbar sprite, 2 top separator sprite, 3 bottom separator sprite, 4 arrow up sprite, 5 arrow down sprite
    }

    /**
     * Describes grand exchange widgets
     * Last reviewed: 3/3/2022 0:14 am UTC+1
     */
    public static class GrandExchange {
        public static final int GROUP_INDEX = 465;
        public static final int PARENT_CONTAINER = GROUP_INDEX;
        public static final int CONTAINER = 1;
        public static final int DYNAMIC_CONTAINER = 2; // Contains stone borders close button 0-11
        public static final int BUTTON_HISTORY_DYNAMIC_CONTAINER = 3; // Contains sprites, label 0-9
        public static final int OFFER_STATUS_BUTTON_BACK_SPRITE = 4;
        public static final int INNER_FRAME_CONTAINER = 5;
        public static final int TITLE_DYNAMIC_CONTAINER = 6; // Contains label[0]
        public static final int FIRST_SLOT_DYNAMIC_CONTAINER = 7; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int SECOND_SLOT_DYNAMIC_CONTAINER = 8; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int THIRD_SLOT_DYNAMIC_CONTAINER = 9; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int FOURTH_SLOT_DYNAMIC_CONTAINER = 10; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int FIFTH_SLOT_DYNAMIC_CONTAINER = 11; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int SIXTH_SLOT_DYNAMIC_CONTAINER = 12; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int SEVENTH_SLOT_DYNAMIC_CONTAINER = 13; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int EIGHT_SLOT_DYNAMIC_CONTAINER = 14; // Contains 0-4 icon layers, 5-15 steel borders, 16 title label, 26 left icon sprite, 27 right icon sprite
        public static final int OFFER_STATUS_DYNAMIC_CONTAINER = 15;
        public static final int OFFER_STATUS_ITEM_DESCRIPTION_LABEL = 16;
        public static final int OFFER_STATUS_ABOVE_PRICE_TEXT_LABEL = 17;
        public static final int OFFER_STATUS_INFO_ICON_DYNAMIC_CONTAINER = 18; // 0 icon sprite
        public static final int OFFER_STATUS_COLLECTION_AREA_SPRITE = 19;
        public static final int OFFER_STATUS_STEEL_BORDER_SEPARATOR_LINE = 20;
        public static final int OFFER_STATUS_STEEL_BORDER_VERTICAL_SEPARATOR_LINE = 21;
        public static final int UNKNOWN_SPRITE = 22;
        public static final int OFFER_STATUS_BOTTOM_BAR_DYNAMIC_CONTAINER = 23; // 0 cancel button sprite, 1 sold text
        public static final int OFFER_STATUS_COLLECTION_AREA_DYNAMIC_CONTAINER = 24; // 0 bg sprite left item, 1 bg sprite right item, 2 item sprite, 3 item sprite
    }

    /**
     * Describes prayers tab widgets
     * Last reviewed: 4/3/2022 0:34 am UTC+1
     */
    public static class PrayersTab {
        public static final int GROUP_ID = 541;
        public static final int PARENT_CONTAINER = 0;
        // 1
        public static final int BOTTOM_BAR_DYNAMIC_CONTAINER = 2;
        // 3
        public static final int PRAYERS_CONTAINER = 4;
        public static final int THICK_SKIN = 5;
        public static final int BURST_OF_STRENGTH = 6;
        public static final int CLARITY_OF_THOUGHT = 7;
        public static final int ROCK_SKIN = 8;
        public static final int SUPERHUMAN_STRENGTH = 9;
        public static final int IMPROVED_REFLEXES = 10;
        public static final int RAPID_RESTORE = 11;
        public static final int RAPID_HEAL = 12;
        public static final int PROTECT_ITEM = 13;
        public static final int STEEL_SKIN = 14;
        public static final int ULTIMATE_STRENGTH = 15;
        public static final int INCREDIBLE_REFLEXES = 16;
        public static final int PROTECT_FROM_MAGIC = 17;
        public static final int PROTECT_FROM_MISSILES = 18;
        public static final int PROTECT_FROM_MELEE = 19;
        public static final int RETRIBUTION = 20;
        public static final int REDEMPTION = 21;
        public static final int SMITE = 22;
        public static final int SHARP_EYE = 23;
        public static final int MYSTIC_WILL = 24;
        public static final int HAWK_EYE = 25;
        public static final int MYSTIC_LORE = 26;
        public static final int EAGLE_EYE = 27;
        public static final int DEAD_EYE = 27; // members version of Eagle Eye
        public static final int MYSTIC_MIGHT = 28;
        public static final int MYSTIC_VIGOUR = 28; // members version of Mystic Might
        public static final int CHIVALRY = 29;
        public static final int PIETY = 30;
        public static final int RIGOUR = 31;
        public static final int AUGURY = 32;
        public static final int PRESERVE = 33;
        public static final int MOUSEOVER_TOOLTIP_CONTAINER = 34;
    }

    /**
     * Describes fixed classic viewport widgets
     * Last reviewed: 23/3/2022 0:17 am UTC+1
     */
    public static class FixedClassicViewport {
        public static final int GROUP_INDEX = 548;
        public static final int PARENT_CONTAINER = 0;
        public static final int LEFT_SIDE_SHADOW_SPRITE = 1;
        public static final int MAIN_CONTAINER = 2;
        public static final int TOP_SIDE_SHADOW_SPRITE = 6;
        public static final int MINIMAP_CONTAINER = 8;
        public static final int VIEWPORT_CONTAINER = 9;
        public static final int CHATBOX_CONTAINER = 10;
        public static final int LEFT_SIDE_TOP_COLUMN_SPRITE = 11;
        public static final int LEFT_SIDE_BOTTOM_COLUMN_SPRITE = 12;
        public static final int RIGHT_SIDE_COLUMN_SPRITE = 13;
        public static final int BOTTOM_BAR_PARENT_CONTAINER = 14;
        public static final int TOP_BAR_PARENT_CONTAINER = 15;
        public static final int ROOT_INTERFACE_CONTAINER = 16;
        public static final int MINIMAP_BOTTOM_SPRITE = 17; // Note: extra sprite which obscure viewport on classic layout
        public static final int MINIMAP_LEFT_SPRITE = 18; // Note: extra sprite which obscure viewport on classic layout
        public static final int MINIMAP_RIGHT_SPRITE = 19; // Note: extra sprite which obscure viewport on classic layout
        public static final int MINIMAP_COMPASS_SPRITE = 20;
        public static final int MINIMAP_DRAW_AREA_SPRITE = 21;
        public static final int MINIMAP_AREA_SPRITE = 22;
        public static final int MINIMAP_COMPASS_DYNAMIC_CONTAINER = 23;
        public static final int MINIMAP_ORBS_CONTAINER = 24;
        public static final int DIALOG_WINDOW_CONTAINER = 40;
        public static final int BOTTOM_BAR_SPRITE = 44;
        public static final int BOTTOM_BAR_CONTAINER = 45;
        public static final int TAB_CHAT_CHANNEL_SPRITE = 46;
        public static final int TAB_ACC_MANAGEMENT_SPRITE = 47;
        public static final int TAB_FRIENDS_LIST_SPRITE = 48;
        public static final int TAB_LOGOUT_SPRITE = 49;
        public static final int TAB_SETTINGS_SPRITE = 50;
        public static final int TAB_EMOTES_SPRITE = 51;
        public static final int TAB_MUSIC_PLAYER_SPRITE = 52;
        public static final int TAB_CHAT_CHANNEL_ICON_SPRITE = 53;
        public static final int TAB_ACC_MANAGEMENT_ICON_SPRITE = 54;
        public static final int TAB_FRIENDS_LIST_ICON_SPRITE = 55;
        public static final int TAB_LOGOUT_ICON_SPRITE = 56;
        public static final int TAB_SETTINGS_ICON_SPRITE = 57;
        public static final int TAB_EMOTES_ICON_SPRITE = 58;
        public static final int TAB_MUSIC_PLAYER_ICON_SPRITE = 59;
        public static final int TOP_BAR_SPRITE = 60;
        public static final int TOP_BAR_CONTAINER = 61;
        public static final int TAB_COMBAT_OPTIONS_SPRITE = 62;
        public static final int TAB_SKILLS_SPRITE = 63;
        public static final int TAB_QUEST_LIST_SPRITE = 64;
        public static final int TAB_INVENTORY_SPRITE = 65;
        public static final int TAB_WORN_EQUIPMENT_SPRITE = 66;
        public static final int TAB_PRAYER_SPRITE = 67;
        public static final int TAB_MAGIC_SPRITE = 68;
        public static final int TAB_COMBAT_OPTIONS_ICON_SPRITE = 69;
        public static final int TAB_SKILLS_ICON_SPRITE = 70;
        public static final int TAB_QUEST_LIST_ICON_SPRITE = 71;
        public static final int TAB_INVENTORY_ICON_SPRITE = 72;
        public static final int TAB_EQUIPMENT_ICON_SPRITE = 73;
        public static final int TAB_PRAYER_ICON_SPRITE = 74;
        public static final int TAB_MAGIC_ICON_SPRITE = 75;
    }

    /**
     * Describes misthalin manor piano widgets
     * Last reviewed: 11/4/2022 1:01 pm UTC+1
     * TODO: Are those models rly at same position or just widget inspector going nuts?
     */
    public static class ManorPiano {
        public static final int GROUP_INDEX = 554;
        public static final int PARENT_CONTAINER = 0;
        public static final int CONTAINER = 1;
        public static final int MODEL_CONTAINER = 2; // likely the piano model and some other one
        public static final int UNKNOWN_MODEL1 = 3; // modelId 25889
        public static final int UNKNOWN_MODEL2 = 4; // modelId 32344
        public static final int KEYNOTES_CONTAINER = 5;
        // 6 - 19 models, likely the key notes models
        public static final int KEYNOTE_C_LABEL = 20;
        public static final int KEYNOTE_D_LABEL = 21;
        public static final int KEYNOTE_E_LABEL = 22;
        public static final int KEYNOTE_F_LABEL = 23;
        public static final int KEYNOTE_G_LABEL = 24;
        public static final int KEYNOTE_A_LABEL = 25;
        public static final int KEYNOTE_B_LABEL = 26;
        public static final int SECOND_KEYNOTE_C_LABEL = 27;
        public static final int SECOND_KEYNOTE_D_LABEL = 28;
        public static final int SECOND_KEYNOTE_E_LABEL = 29;
        public static final int SECOND_KEYNOTE_F_LABEL = 30;
        public static final int SECOND_KEYNOTE_G_LABEL = 31;
        public static final int SECOND_KEYNOTE_A_LABEL = 32;
        public static final int SECOND_KEYNOTE_B_LABEL = 33;
        public static final int CLOSE_BUTTON_CONTAINER = 34;
        public static final int CLOSE_BUTTON_SPRITE = 35;
    }

    /**
     * Describes misthalin manor gemstone switch panel widgets
     * Last reviewed: 11/4/2022 1:28 pm UTC+1
     */
    public static class ManorGemstoneSwitchPanel {
        public static final int GROUP_INDEX = 555;
        public static final int PARENT_CONTAINER = 0;
        public static final int DYNAMIC_CONTAINER = 1; // 0 sprite, 1 title label, 2-12 edges/corners sprites, 13 button close sprite
        public static final int INNER_FRAME_PARENT_CONTAINER = 2;
        public static final int BUTTON_DIAMOND_PARENT_CONTAINER = 3;
        public static final int BUTTON_DIAMOND_DYNAMIC_CONTAINER = 4; // 0 sprite, 1-8 edges/corners sprites
        public static final int BUTTON_DIAMOND_LABEL = 5;
        public static final int BUTTON_DIAMOND_MODEL = 6;
        public static final int BUTTON_ONYX_PARENT_CONTAINER = 7;
        public static final int BUTTON_ONYX_DYNAMIC_CONTAINER = 8; // 0 sprite, 1-8 edges/corners sprites
        public static final int BUTTON_ONYX_LABEL = 9;
        public static final int BUTTON_ONYX_MODEL = 10;
        public static final int BUTTON_ZENYTE_PARENT_CONTAINER = 11;
        public static final int BUTTON_ZENYTE_DYNAMIC_CONTAINER = 12; // 0 sprite, 1-8 edges/corners sprites
        public static final int BUTTON_ZENYTE_LABEL = 13;
        public static final int BUTTON_ZENYTE_MODEL = 14;
        public static final int BUTTON_RUBY_PARENT_CONTAINER = 15;
        public static final int BUTTON_RUBY_DYNAMIC_CONTAINER = 16; // 0 sprite, 1-8 edges/corners sprites
        public static final int BUTTON_RUBY_LABEL = 17;
        public static final int BUTTON_RUBY_MODEL = 18;
        public static final int BUTTON_SAPPHIRE_PARENT_CONTAINER = 19;
        public static final int BUTTON_SAPPHIRE_DYNAMIC_CONTAINER = 20; // 0 sprite, 1-8 edges/corners sprites
        public static final int BUTTON_SAPPHIRE_LABEL = 21;
        public static final int BUTTON_SAPPHIRE_MODEL = 22;
        public static final int BUTTON_EMERALD_PARENT_CONTAINER = 23;
        public static final int BUTTON_EMERALD_DYNAMIC_CONTAINER = 24; // 0 sprite, 1-8 edges/corners sprites
        public static final int BUTTON_EMERALD_LABEL = 25;
        public static final int BUTTON_EMERALD_MODEL = 26;
        public static final int GEMS_CHOSEN_ORDER_PARENT_CONTAINER = 27;
        public static final int GEMS_CHOSEN_ORDER_CONTAINER = 28;
        public static final int FIRST_GEM_CHOSEN_LABEL = 29;
        public static final int SECOND_GEM_CHOSEN_LABEL = 30;
        public static final int THIRD_GEM_CHOSEN_LABEL = 31;
        public static final int FOURTH_GEM_CHOSEN_LABEL = 32;
        public static final int FIFTH_GEM_CHOSEN_LABEL = 33;
        public static final int SIXTH_GEM_CHOSEN_LABEL = 34;
        public static final int FIRST_GEM_CHOSEN_MODEL = 35;
        public static final int SECOND_GEM_CHOSEN_MODEL = 36;
        public static final int THIRD_GEM_CHOSEN_MODEL = 37;
        public static final int FOURTH_GEM_CHOSEN_MODEL = 38;
        public static final int FIFTH_GEM_CHOSEN_MODEL = 39;
        public static final int SIXTH_GEM_CHOSEN_MODEL = 40;
    }

    /**
     * Describes apothecary's potions widget
     * Last reviewed: 9/4/2022 11:04 pm UTC+1
     */
    public static class ApothecarysPotions {
        public static final int GROUP_INDEX = 556;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; // 0 entire sprite, 1 title label, 2-10 corners/edges sprites, 11 close button sprite
        public static final int FIRST_POTION_PARENT_CONTAINER = 2;
        public static final int FIRST_POTION_DYNAMIC_CONTAINER = 3; // 0 sprite, 1-8 corners/edges sprites
        public static final int FIRST_POTION_MODEL = 4;
        public static final int FIRST_POTION_NAME_LABEL = 5;
        public static final int FIRST_POTION_REQUIREMENTS_TEXT_LABEL = 6;
        public static final int SECOND_POTION_PARENT_CONTAINER = 7;
        public static final int SECOND_POTION_DYNAMIC_CONTAINER = 8; // 0 sprite, 1-8 corners/edges sprites
        public static final int SECOND_POTION_MODEL = 9;
        public static final int SECOND_POTION_NAME_LABEL = 10;
        public static final int SECOND_POTION_REQUIREMENTS_TEXT_LABEL = 11;
        public static final int THIRD_POTION_PARENT_CONTAINER = 12;
        public static final int THIRD_POTION_DYNAMIC_CONTAINER = 13; // 0 sprite, 1-8 corners/edges sprites
        public static final int THIRD_POTION_MODEL = 14;
        public static final int THIRD_POTION_NAME_LABEL = 15;
        public static final int THIRD_POTION_REQUIREMENTS_TEXT_LABEL = 16;
    }

    /**
     * Describes set display name widget
     * Last reviewed: 10/4/2022 2:14 am UTC+1
     */
    public static class SetDisplayName {
        public static final int GROUP_INDEX = 558;
        public static final int PARENT_CONTAINER = 0;
        public static final int UNKNOWN_CONTAINER = 1;
        public static final int FRAME_PARENT_CONTAINER = 2;
        public static final int FRAME_DYNAMIC_CONTAINER = 3;  // 0 sprite, 1 title label, 2-10 corners/edges sprites
        public static final int INNER_FRAME_PARENT_CONTAINER = 4;
        public static final int INNER_FRAME_DESCRIPTION_TEXT_LABEL = 5;
        public static final int INNER_FRAME_TITLE_TEXT_LABEL = 6;
        public static final int INNER_FRAME_INPUT_BOX_CONTAINER = 7;
        public static final int INNER_FRAME_INPUT_BOX_SHADOW_BOX = 8;
        public static final int INNER_FRAME_INPUT_BOX_MAIN_SPRITE = 9;
        public static final int INNER_FRAME_INPUT_BOX_LEFT_SPRITE = 10;
        public static final int INNER_FRAME_INPUT_BOX_RIGHT_SPRITE = 11;
        public static final int INNER_FRAME_INPUT_BOX_INPUTTED_NAME_LABEL = 12; // Note: * is no input present
        public static final int INNER_FRAME_LOOKUP_NAME_TEXT_LABEL = 13; // Note: changes upon clicking button lookup name
        public static final int INNER_FRAME_AVAILABLE_NAMES_CONTAINER = 14;
        public static final int INNER_FRAME_AVAILABLE_NAME1_LABEL = 15;
        public static final int INNER_FRAME_AVAILABLE_NAME2_LABEL = 16;
        public static final int INNER_FRAME_AVAILABLE_NAME3_LABEL = 17;
        public static final int INNER_FRAME_BUTTON_LOOKUP_NAME_DYNAMIC_CONTAINER = 18; // 0 sprite, 1-8 corners/edges sprites, 9 text label
    }

    /**
     * Describes combat options tab widgets
     * Last reviewed: 2/4/2022 0:02 am UTC+1
     */
    public static class DialogDestroyItem {
        public static final int GROUP_INDEX = 584;
        public static final int PARENT_DYNAMIC_CONTAINER = 0; // contains 2 destroy warning text label, 3 left side sword sprite, 4 right side sword sprite
        public static final int BUTTON_YES_DYNAMIC_CONTAINER = 1; // contains 0 YES_LABEL
        public static final int BUTTON_YES_SPRITE = 2;
        public static final int BUTTON_NO_DYNAMIC_CONTAINER = 3; // contains 0 NO_LABEL
        public static final int BUTTON_NO_SPRITE = 4;
        public static final int ITEM_SPRITE = 5;
        public static final int ITEM_NAME_LABEL = 6;
        public static final int POSSIBLE_RECOVERY_TEXT_LABEL = 7;
    }

    /**
     * Describes makeovers offered by thessalia's widget
     * Last reviewed: 9/4/2022 11:04 pm UTC+1
     */
    public static class ThessaliasMakeovers {
        public static final int GROUP_INDEX = 591;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; // 0 entire sprite, 1 title label, 2-10 corners/edges sprites, 11 close button sprite
        public static final int STYLE_PARENT_CONTAINER = 2;
        public static final int SELECT_BODY_STYLE_DYNAMIC_CONTAINER = 3; // 0-13 boxes, 14 torso model of first choice, 15 cloth model of first choice, 16 cloth sprite, this repeats ...
        public static final int SELECT_BODY_STYLE_TITLE_LABEL = 4;
        public static final int SELECT_ARMS_STYLE_DYNAMIC_CONTAINER = 5; // 0-11 boxes, 12 torso model of first choice, 13 cloth model of first choice, 14 cloth sprite, this repeats ...
        public static final int SELECT_ARMS_STYLE_TITLE_LABEL = 6;
        public static final int SELECT_LEG_STYLE_DYNAMIC_CONTAINER = 7; // 0-10 boxes, 11 leg model of first choice, 12 sprite of first choice this repeats ...
        public static final int SELECT_LEG_STYLE_TITLE_LABEL = 8;
        public static final int SELECT_COLOUR_PARENT_CONTAINER = 9;
        public static final int SELECT_COLOUR_TITLE_LABEL = 10;
        public static final int SELECT_COLOUR_FIRST_ROW_BOX = 11;
        public static final int SELECT_COLOUR_SECOND_ROW_BOX = 12;
        public static final int SELECT_COLOUR_COLORS_DYNAMIC_CONTAINER = 13; //1-28 colour boxes, 29-57 colour sprites
        public static final int CONFIRM_TEXT_LABEL = 14;
    }

    /**
     * Describes combat options tab widgets
     * Last reviewed: 1/3/2022 0:31 am UTC+1
     */
    public static class CombatOptionsTab {
        public static final int GROUP_INDEX = 593;
        public static final int PARENT_CONTAINER = 0;
        public static final int WEAPON_NAME_LABEL = 1;
        public static final int WEAPON_CATEGORY_LABEL = 2;
        public static final int COMBAT_LEVEL_LABEL = 3;
        public static final int WEAPON_STYLE_ONE_CONTAINER = 4;
        public static final int WEAPON_STYLE_ONE_DYNAMIC_CONTAINER = 5; // Contains button sprites 0-8
        public static final int WEAPON_STYLE_ONE_SPRITE = 6;
        public static final int WEAPON_STYLE_ONE_LABEL = 7;
        public static final int WEAPON_STYLE_TWO_CONTAINER = 8;
        public static final int WEAPON_STYLE_TWO_DYNAMIC_CONTAINER = 9;
        public static final int WEAPON_STYLE_TWO_SPRITE = 10;
        public static final int WEAPON_STYLE_TWO_LABEL = 11;
        public static final int WEAPON_STYLE_THREE_CONTAINER = 12;
        public static final int WEAPON_STYLE_THREE_DYNAMIC_CONTAINER = 13;
        public static final int WEAPON_STYLE_THREE_SPRITE = 14;
        public static final int WEAPON_STYLE_THREE_LABEL = 15;
        public static final int WEAPON_STYLE_FOUR_CONTAINER = 16;
        public static final int WEAPON_STYLE_FOUR_DYNAMIC_CONTAINER = 17;
        public static final int WEAPON_STYLE_FOUR_SPRITE = 18;
        public static final int WEAPON_STYLE_FOUR_LABEL = 19;
        public static final int AUTO_CAST_SPELL_PARENT_CONTAINER = 20;
        public static final int AUTO_CAST_DEFENSIVE_SPELL_DYNAMIC_CONTAINER = 21; // Contains button sprites 0-8
        public static final int AUTO_CAST_DEFENSIVE_SPELL_CONTAINER = 22;
        public static final int AUTO_CAST_DEFENSIVE_SPELL_ICON_SPRITE = 23;
        public static final int AUTO_CAST_DEFENSIVE_SPELL_SHIELD_SPRITE = 24;
        public static final int AUTO_CAST_DEFENSIVE_SPELL_LABEL = 25;
        public static final int AUTO_CAST_SPELL_DYNAMIC_CONTAINER = 26;  // Contains button sprites 0-8
        public static final int AUTO_CAST_SPELL_CONTAINER = 27;
        public static final int AUTO_CAST_SPELL_ICON_SPRITE = 28;
        public static final int AUTO_CAST_SPELL_LABEL = 29;
        public static final int AUTO_RETALIATE_CONTAINER = 30;
        public static final int AUTO_RETALIATE_ICON_LABEL = 31;
        public static final int AUTO_RETALIATE_DYNAMIC_CONTAINER = 32;  // Contains button sprites 0-8
        public static final int AUTO_RETALIATE_SPRITE = 33;
        public static final int AUTO_RETALIATE_LABEL = 34;
        public static final int SPECIAL_ATTACK_CONTAINER = 35;
        public static final int SPECIAL_ATTACK_DYNAMIC_CONTAINER = 36;  // Contains button sprites 0-8
        public static final int SPECIAL_ATTACK_BAR = 37;
        public static final int SPECIAL_ATTACK_GREEN_BAR_CONTAINER = 38;
        public static final int SPECIAL_ATTACK_GREEN_BAR_GREEN_BOX = 39;
        public static final int SPECIAL_ATTACK_LABEL = 40;
        public static final int SPECIAL_ATTACK_BROWN_GREEN_BOX = 41;
        public static final int MOUSEOVER_TOOLTIP_CONTAINER = 42;
    }

    /**
     * Describes world map widget
     * Last reviewed: 18/3/2022 0:04 am UTC+1
     */
    public static class WorldMap {
        public static final int GROUP_INDEX = 595;
        public static final int PARENT_CONTAINER = 0;
        public static final int MAIN_CONTAINER = 1;
        public static final int DYNAMIC_CONTAINER = 2; // 0-7 borders,corners sprites
        // 3
        public static final int UNKNOWN_CONTAINER = 4;
        public static final int MAIN_FRAME_CONTAINER = 5;
        public static final int MAPVIEW_BOX = 6;
        public static final int MAPVIEW_CONTAINER = 7;
        public static final int CONTAINER = 8; // Content Type 1400
        public static final int MAPVIEW_DYNAMIC_CONTAINER = 9; // 1 your position icon
        public static final int SIDEBAR_DYNAMIC_CONTAINER = 14;
        public static final int KEY_DYNAMIC_CONTAINER = 16;
        public static final int LEGENDS_DYNAMIC_CONTAINER = 21;
        public static final int BOTTOM_BAR_CONTAINER = 22;
        public static final int BOTTOM_BAR_SPRITE = 23;
        public static final int HIDE_MENU_DYNAMIC_SPRITE = 24;
        public static final int SEARCH_INPUT_BOX_DYNAMIC_SPRITE = 25;
        public static final int SURFACE_DYNAMIC_CONTAINER = 26;
        public static final int BUTTON_CLOSE_SPRITE = 38;
        public static final int RESIZE_CORNER_CONTAINER = 39;
        public static final int RESIZE_CORNER_SPRITE = 40;
        public static final int MOUSEOVER_TOOLTIP_CONTAINER = 41;
    }

    /**
     * Describes character summary tab widget
     * Last reviewed: 18/3/2022 0:29 am UTC+1
     */
    public static class CharacterSummaryTab {
        public static final int GROUP_INDEX = 629;
        public static final int PARENT_CONTAINER = 0;
        public static final int TOP_BAR_CONTAINER = 1;
        public static final int TOP_SEPARATOR_LINE = 2;
        public static final int TAB_CHARACTER_SUMMARY_CONTAINER = 3;
        public static final int TAB_CHARACTER_SUMMARY_LEFT_SIDE_SPRITE = 4;
        public static final int TAB_CHARACTER_SUMMARY_MID_SIDE_SPRITE = 5;
        public static final int TAB_CHARACTER_SUMMARY_RIGHT_SIDE_SPRITE = 6;
        public static final int TAB_CHARACTER_SUMMARY_ICON_SPRITE = 7;
        public static final int TAB_QUEST_LIST_CONTAINER = 8;
        public static final int TAB_QUEST_LIST_LEFT_SIDE_SPRITE = 9;
        public static final int TAB_QUEST_LIST_MID_SIDE_SPRITE = 10;
        public static final int TAB_QUEST_LIST_RIGHT_SIDE_SPRITE = 11;
        public static final int TAB_QUEST_LIST_ICON_SPRITE = 12;
        public static final int TAB_ACHIEVEMENT_DIARIES_CONTAINER = 13;
        public static final int TAB_ACHIEVEMENT_DIARIES_LEFT_SIDE_SPRITE = 14;
        public static final int TAB_ACHIEVEMENT_DIARIES_MID_SIDE_SPRITE = 15;
        public static final int TAB_ACHIEVEMENT_DIARIES_RIGHT_SIDE_SPRITE = 16;
        public static final int TAB_ACHIEVEMENT_DIARIES_ICON_SPRITE = 17;
        public static final int TAB_KOUREND_FAVOR_CONTAINER = 18;
        public static final int TAB_KOUREND_FAVOR_LEFT_SIDE_SPRITE = 19;
        public static final int TAB_KOUREND_FAVOR_MID_SIDE_SPRITE = 20;
        public static final int TAB_KOUREND_FAVOR_RIGHT_SIDE_SPRITE = 21;
        public static final int TAB_KOUREND_FAVOR_ICON_SPRITE = 22;
        // 23 - 27 gap
        public static final int TAB_LEAGUES_CONTAINER = 28;
        public static final int TAB_LEAGUES_LEFT_SIDE_SPRITE = 29;
        public static final int TAB_LEAGUES_MID_SIDE_SPRITE = 30;
        public static final int TAB_LEAGUES_RIGHT_SIDE_SPRITE = 31;
        public static final int TAB_LEAGUES_ICON_SPRITE = 32;
        public static final int INNER_FRAME_CONTAINER = 33;
    }

    /**
     * Describes adventure paths window
     * Last reviewed: 6/4/2022 1:21 am UTC+1
     */
    public static class AdventurePaths {
        public static final int GROUP_INDEX = 642;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; // contains 0 entire sprite, frame borders,corners, 13 close button sprite
        public static final int INNER_FRAME_PARENT_CONTIANER = 2;
        public static final int INNER_FRAME_CONTAINER = 3;
        // 4 ??
        public static final int PATH_PARENT_CONTAINER = 5;
        public static final int PATH_TASK_LIST_PARENT_DYNAMIC_CONTAINER = 6; // contains borders,corners,lines of task list
        public static final int PATH_TASK_LIST_DYNAMIC_CONTAINER = 7; // task sprites, 0-11 a first single task
        public static final int MORE_INFO_PARENT_CONTAINER = 8;
        public static final int MORE_INFO_DETAIL_PARENT_DYNAMIC_CONTAINER = 9; // 0-7 frame sprites
        public static final int MORE_INFO_DETAIL_TASK_TITLE_DYNAMIC_CONTAINER = 10;  // 0-7 frame sprites
        public static final int MORE_INFO_DETAIL_TASK_TITLE_LABEL = 11;
        public static final int UNKNOWN_CONTAINER = 12;
        public static final int MORE_INFO_DETAIL_TASK_ADVICE_DYNAMIC_CONTAINER = 13; // point sprite + text_label depends on number of advices given per task
        public static final int MORE_INFO_DETAIL_TASK_PICTURE_DYNAMIC_CONTAINER = 14; // 0-7 frame sprites
        public static final int MORE_INFO_DETAIL_TASK_PICTURE_DESCRIPTION_DYNAMIC_CONTAINER = 15; // 0-7 frame sprites,
        public static final int MORE_INFO_DETAIL_TASK_PICTURE_TEXT_LABEL = 16;
        public static final int MORE_INFO_DETAIL_TASK_PICTURE_SPRITE = 17;
        public static final int MORE_INFO_TOP_BAR_CONTAINER = 18;
        public static final int MORE_INFO_TASK_TITLE_LABEL = 19;
        public static final int MORE_INFO_BUTTON_BACK_DYNAMIC_CONTAINER = 20; // 0 entire sprite, 1-8 frame sprites, 9 text_label
        public static final int PATH_TOP_BAR_PARENT_CONTAINER = 21;
        public static final int PATH_TOP_BAR_WORLD_MAP_DYNAMIC_CONTAINER = 22; // 9 icon
        public static final int BUTTON_BACK_DYNAMIC_CONTAINER = 23; // 0 sprite, 9 text_LABEL
        public static final int BUTTON_SHOW_PATH_REWARD_DYNAMIC_CONTAINER = 24; // 0 sprite, 9 text_LABEL
        public static final int COMPLETED_TASK_DYNAMIC_CONTAINER = 25; // 0 text_label
        public static final int BUTTON_CHECKBOX_DYNAMIC_CONTAINER = 26; //0 entire sprite, 9 icon sprite, 10 shaodow box
        public static final int PATH_SCROLLBAR_DYNAMIC_CONTAINER = 27; // 0 entire scrollbar sprite, 1 actual scrollbar sprite, 2,3 separator between arrows up/down, 4 arrow up, 5 arrow down
        public static final int PATH_TASK_DESCRIPTION_DYNAMIC_CONTAINER = 28; // 0-10 frame sprites
        public static final int PATH_TASK_DESCRIPTION_DETAIL_DYNAMIC_CONTAINER = 29; // 0 box, 1 detail_text_LABEL, 2 moreinfo_text_label
        public static final int PATH_TASK_DESCRIPTION_TITLE_LABEL = 30;
        public static final int PATH_TASK_DESCRIPTION_REQUIREMENT_LABEL = 31;
        public static final int PATH_TASK_DESCRIPTION_REWARDS_DYNAMIC_CONTAINER = 32; // 0 f2p text_label, 1 p2p text_label ,2 first f2p reward .... x rewards
        // 33 ??
        public static final int SCROLLBAR_DYNAMIC_CONTAINER = 34; // 0 entire scrollbar sprite, 1 actual scrollbar sprite, 2,3 separator between arrows up/down, 4 arrow up, 5 arrow down
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 35;
    }

    /**
     * Describes adventure paths rewards window
     * Last reviewed: 6/4/2022 1:34 am UTC+1
     */
    public static class AdventurePathsRewards {
        public static final int GROUP_INDEX = 643;
        public static final int PARENT_CONTAINER = 0;
        public static final int FRAME_DYNAMIC_CONTAINER = 1; // 0 entire sprite, 1 title_label, 2-12 frame sprites, 13 close button sprite
        public static final int INNER_FRAME_PARENT_CONTAINER = 2;
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 3; // 0 button back sprite, 1-8 button back frame sprites, 9 text_label, 10 title ,11 reward box, 29 reward description text_label
    }

    /**
     * Describes canoe destination window
     * Last reviewed: 6/4/2022 1:59 am UTC+1
     */
    public static class CanoeDestinationWindow {
        public static final int GROUP_INDEX = 647;
        public static final int PARENT_CONTAINER = 0;
        public static final int MODELS_CONTAINER = 1;
        public static final int WATER_MODEL = 2;
        public static final int FRAME_MAIN_CONTAINER = 3;
        public static final int MODELS_PARENT_CONTAINER = 4;
        public static final int MAP_MODELS_CONTAINER = 5;
        public static final int YOUR_DESTINATION_CONTAINER = 6;
        public static final int MAP_MODEL = 7;
        public static final int CAVE_MODEL = 8;
        public static final int SKULL_MODEL = 9;
        public static final int UNKNOWN_MODEL = 10;
        public static final int SELECT_DESTINAITON_DYNAMIC_CONTAINER = 11; // 0 TEXT_LABEL
        public static final int BUTTON_CLOSE_DYNAMIC_CONTAINER = 12;
        public static final int DESTINATIONS_CONTAINER = 13;
        // TODO: this actually depends where you are at ...
        // 14 DEST_GRAND_EXCHANGE_DYNAMIC_CONTAINER // 0 box, 1 dest_label, 2, canoe_model
        // 15 DEST_LUMBRIDGE_DYMAIC_CONTAINER // 0 box, 1 dest_label, 2, canoe_model
        // 16 DEST_CHAMPS_GUILD_DYNAMIC_CONTAINER // 0 box, 1 dest_label, 2, canoe_model
        // 17 DEST_BARB_VILLAGE_DYNAMIC_CONTAINER // 0 box, 1 dest_label, 2, canoe_model
        // 18 DEST_EDGEVILLE_DYNAMIC_CONTAINER // 0 box, 1 dest_label, 2, canoe_model
        // 19 DEST_FEROX_ENCLAVE_DYNAMIC_CONTAINER // 0 box, 1 dest_label, 2, canoe_model
        // 20 DEST_WILDY_POND_DYNAMIC_CONTAINER // 0 box, 1 dest_label, 2, canoe_model
    }

    /**
     * Describes character creator widget
     * Last reviewed: 10/4/2022 3:42 am UTC+1
     */
    public static class CharacterCreator {
        public static final int GROUP_INDEX = 679;
        public static final int PARENT_CONTAINER = 0;
        public static final int UNKNOWN_CONTAINER = 1;
        public static final int FRAME_PARENT_CONTAINER = 2;
        public static final int FRAME_DYNAMIC_CONTAINER = 3; // 0 sprite, 1 title label, 2-10 edges/cornes sprites
        public static final int LEFT_VERICAL_LINE_SPRITE = 4;
        public static final int RIGHT_VERICAL_LINE_SPRITE = 5;
        public static final int COLOUR_GENDER_HORIZONTAL_LINE_SPRITE = 6;
        public static final int DESIGN_PARENT_CONTAINER = 7;
        public static final int DESIGN_LABEL = 8;
        public static final int DESIGN_INNER_FRAME_PARENT_CONTAINER = 9;
        public static final int DESIGN_HEAD_PARENT_CONTAINER = 10;
        public static final int DESIGN_HEAD_LABEL = 11;
        public static final int DESIGN_HEAD_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 12; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_HEAD_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 13; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_JAW_PARENT_CONTAINER = 14;
        public static final int DESIGN_JAW_LABEL = 15;
        public static final int DESIGN_JAW_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 16; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_JAW_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 17; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_TORSO_PARENT_CONTAINER = 18;
        public static final int DESIGN_TORSO_LABEL = 19;
        public static final int DESIGN_TORSO_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 20; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_TORSO_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 21; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_ARMS_PARENT_CONTAINER = 22;
        public static final int DESIGN_ARMS_LABEL = 23;
        public static final int DESIGN_ARMS_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 24; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_ARMS_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 25; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_HANDS_PARENT_CONTAINER = 26;
        public static final int DESIGN_HANDS_LABEL = 27;
        public static final int DESIGN_HANDS_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 28; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_HANDS_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 29; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_LEGS_PARENT_CONTAINER = 30;
        public static final int DESIGN_LEGS_LABEL = 31;
        public static final int DESIGN_LEGS_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 32; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_LEGS_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 33; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_FEET_PARENT_CONTAINER = 34;
        public static final int DESIGN_FEET_LABEL = 35;
        public static final int DESIGN_FEET_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 36; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int DESIGN_FEET_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 37; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_PARENT_CONTAINER = 38;
        public static final int COLOUR_LABEL = 39;
        public static final int COLOUR_INNER_FRAME_PARENT_CONTAINER = 40;
        public static final int COLOUR_HAIR_PARENT_CONTAINER = 41;
        public static final int COLOUR_HAIR_LABEL = 42;
        public static final int COLOUR_HAIR_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 43; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_HAIR_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 44; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_TORSO_PARENT_CONTAINER = 45;
        public static final int COLOUR_TORSO_LABEL = 46;
        public static final int COLOUR_TORSO_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 47; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_TORSO_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 48; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_LEGS_PARENT_CONTAINER = 49;
        public static final int COLOUR_LEGS_LABEL = 50;
        public static final int COLOUR_LEGS_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 51; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_LEGS_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 52; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_FEET_PARENT_CONTAINER = 53;
        public static final int COLOUR_FEET_LABEL = 54;
        public static final int COLOUR_FEET_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 55; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_FEET_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 56; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_SKIN_PARENT_CONTAINER = 57;
        public static final int COLOUR_SKIN_LABEL = 58;
        public static final int COLOUR_SKIN_BUTTON_ARROW_LEFT_DYNAMIC_CONTAINER = 59; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int COLOUR_SKIN_BUTTON_ARROW_RIGHT_DYNAMIC_CONTAINER = 60; // 0 sprite, 1-8 corner/edges sprites, 9 arrow sprite
        public static final int GENDER_PARENT_CONTAINER = 61;
        public static final int GENDER_LABEL = 62;
        public static final int GENDER_INNER_FRAME_PARENT_CONTAINER = 63;
        public static final int GENDER_BUTTONS_CONTAINER = 64;
        public static final int GENDER_BUTTON_MALE_DYNAMIC_CONTAINER = 65; // 0 sprite, 1-8 corner/edges sprites, 9 text label
        public static final int GENDER_BUTTON_FEMALE_DYNAMIC_CONTAINER = 66; // 0 sprite, 1-8 corner/edges sprites, 9 text label
        public static final int CHARACTER_MODEL = 67;
        public static final int CONFIRM_BUTTON_DYNAMIC_CONTAINER = 68; // 0 sprite, 1-8 corner/edges sprites, 9 text label
    }

    /**
     * Describes your clan sub-tab widget under social tab widget
     * Last reviewed: 18/3/2022 1:19 am UTC+1
     */
    public static class YourClanSubTab {
        public static final int GROUP_INDEX = 701;
        public static final int PARENT_CONTAINER = 0;
        public static final int HEADER_DYNAMIC_CONTAINER = 1; // 0-1 header titles
        public static final int REFRESH_DYNAMIC_CONTAINER = 2;
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 3;
        public static final int JOIN_DYNAMIC_CONTAINER = 8;
        public static final int SETUP_DYNAMIC_CONTAINER = 9;
    }

    /**
     * Describes another clan sub-tab widget under social tab widget
     * Last reviewed: 18/3/2022 1:47 am UTC+1
     */
    public static class AnotherClanSubTab {
        public static final int GROUP_INDEX = 702;
        public static final int PARENT_CONTAINER = 0;
        public static final int HEADER_DYNAMIC_CONTAINER = 1; // 0-1 header titles
        public static final int REFRESH_DYNAMIC_CONTAINER = 2;
        public static final int INNER_FRAME_DYNAMIC_CONTAINER = 3;
        public static final int BUTTON1_DYNAMIC_CONTAINER = 8;
        public static final int BUTTON2_DYNAMIC_CONTAINER = 9;
        public static final int FIND_DYNAMIC_CONTAINER = 10;
    }

    /**
     * Describes social tab widget
     * Last reviewed: 18/3/2022 0:57 am UTC+1
     */
    public static class SocialTab {
        public static final int GROUP_INDEX = 707;
        public static final int PARENT_CONTAINER = 0;
        public static final int TOP_BAR_CONTAINER = 1;
        public static final int TOP_BAR_SEPARATOR_LINE = 2;
        public static final int TAB_CHAT_CHANNEL_CONTAINER = 3;
        public static final int TAB_YOUR_CLAN_CONTAINER = 4;
        public static final int TAB_VIEW_ANOTHER_CLAN_CONTAINER = 5;
        public static final int TAB_CHAT_GROUPING_CONTAINER = 6;
        public static final int FRAME_CONTAINER = 7;
        public static final int FRAME_TIME_LABEL = 8;
    }

    /**
     * Describes character summary sub-tab widget under character summary tab widget
     * Last reviewed: 18/3/2022 2:34 am UTC+1
     */
    public static class CharacterSummarySubTab {
        public static final int GROUP_INDEX = 712;
        public static final int PARENT_CONTAINER = 0;
        public static final int CHARACTER_NAME_LABEL = 1;
        public static final int SPRITES_LABELS_DYNAMIC_CONTAINER = 2;
        public static final int BUTTONS_DYNAMIC_CONTAINER = 3;
    }

    /**
     * Describes dynamic widgets
     * Last reviewed: 3/3/2022 0:37 am UTC+1
     */
    public static class DynamicComponents {
        public static class PrayerWidget {
            public static final int BG_LIGHT_SPRITE = 0;
            public static final int ICON_SPRITE = 1;
        }
        public static class GrandExchangeOfferTittle {
            public static final int BUTTON_COLLECT_SPRITE = 0;
            public static final int BUTTON_COLLECT_LABEL = 1;
            public static final int TITLE_LABEL = 2;
        }
        public static class GrandExchangeSlot {
            public static final int BUTTON_BUY_BG_BOX = 1;
            public static final int BUTTON_SELL_BG_BOX = 2;
            public static final int BUTTON_BUY_ICON_BOX = 3;
            public static final int BUTTON_SELL_ICON_BOX = 4;
            // 5-15 steel border sprites
            public static final int TITLE_LABEL = 16;
            public static final int ITEM_SPRITE = 17;
            public static final int ITEM_ID_SPRITE = 18; // id, quantity, quantity mode
            public static final int ITEM_NAME_LABEL = 19;
            public static final int PROGRESS_BAR_BOX = 20;
            public static final int ITEM_EACH_PRICE_LABEL = 25;
            public static final int BUY_ICON_SPRITE = 26;
            public static final int SELL_ICON_SPRITE = 27;
        }
        public static class GrandExchangeOfferDetails {
            public static final int ITEM_QUANTITY_LABEL = 18;
            public static final int ITEM_EACH_PRICE_LABEL = 25;
            public static final int ITEM_TOTAL_PRICE_LABEL = 29;
        }
        public static class GrandExchangeCollectionArea {
            public static final int LEFT_ITEM_BG_SPRITE = 0;
            public static final int RIGHT_ITEM_BG_SPRITE = 1;
            public static final int LEFT_ITEM_SPRITE = 2;
            public static final int RIGHT_ITEM_SPRITE = 3;
        }
        public static class AutocastSpells {
            public static final int CANCEL_LABEL = 0;
            public static final int WIND_STRIKE_SPRITE = 1;
            public static final int WATER_STRIKE_SPRITE = 2;
            public static final int EARTH_STRIKE_SPRITE = 3;
            public static final int FIRE_STRIKE_SPRITE = 4;
            public static final int WIND_BOLT_SPRITE = 5;
            public static final int WATER_BOLT_SPRITE = 6;
            public static final int EARTH_BOLT_SPRITE = 7;
            public static final int FIRE_BOLT_SPRITE = 8;
            public static final int WIND_BLAST_SPRITE = 9;
            public static final int WATER_BLAST_SPRITE = 10;
            public static final int EARTH_BLAST_SPRITE = 11;
            public static final int FIRE_BLAST_SPRITE = 12;
            public static final int WIND_WAVE_SPRITE = 13;
            public static final int WATER_WAVE_SPRITE = 14;
            public static final int EARTH_WAVE_SPRITE = 15;
            public static final int FIRE_WAVE_SPRITE = 16;
            public static final int CRUMBLE_UNDEAD_SPRITE = 17;
            public static final int MAGIC_DART_SPRITE = 18;
            // TODO: flames of zamorak, saradomin strike, claws of guthix
            // 19 - 30
            public static final int SMOKE_RUSH_SPRITE = 31;
            public static final int SHADOW_RUSH_SPRITE = 32;
            public static final int BLOOD_RUSH_SPRITE = 33;
            public static final int ICE_RUSH_SPRITE = 34;
            public static final int SMOKE_BURST_SPRITE = 35;
            public static final int SHADOW_BURST_SPRITE = 36;
            public static final int BLOOD_BURST_SPRITE = 37;
            public static final int ICE_BURST_SPRITE = 38;
            public static final int SMOKE_BLITZ_SPRITE = 39;
            public static final int SHADOW_BLITZ_SPRITE = 40;
            public static final int BLOOD_BLITZ_SPRITE = 41;
            public static final int ICE_BLITZ_SPRITE = 42;
            public static final int SMOKE_BARRAGE_SPRITE = 43;
            public static final int SHADOW_BARRAGE_SPRITE = 44;
            public static final int BLOOD_BARRAGE_SPRITE = 45;
            public static final int ICE_BARRAGE_SPRITE = 46;
            public static final int IBAN_BLAST_SPRITE = 47;
            public static final int WIND_SURGE_SPRITE = 48;
            public static final int WATER_SURGE_SPRITE = 49;
            public static final int EARTH_SURGE_SPRITE = 50;
            public static final int FIRE_SURGE_SPRITE = 51;
            // TODO: arceeus autocast
        }
        public static class TradeWindowDynamicContainer {
            public static final int TRADE_WINDOW_SPRITE = 0;
            public static final int TRADE_WINDOW_TITLE_LABEL = 1;
            // 2-12 steel border sprites
            public static final int TRADE_WINDOW_CLOSE_BUTTON = 13;
        }
        public static class TradeOfferDynamicContainer {
            public static final int USED_ITEM_SLOT_ONE = 0;
            public static final int USED_ITEM_SLOT_TWO = 1;
            public static final int USED_ITEM_SLOT_THREE = 2;
            public static final int USED_ITEM_SLOT_FOUR = 3;
            public static final int USED_ITEM_SLOT_FIVE = 4;
            public static final int USED_ITEM_SLOT_SIX = 5;
            public static final int USED_ITEM_SLOT_SEVEN = 6;
            public static final int USED_ITEM_SLOT_EIGHT = 7;
            public static final int USED_ITEM_SLOT_NINE = 8;
            public static final int USED_ITEM_SLOT_TEN = 9;
            public static final int USED_ITEM_SLOT_ELEVEN = 10;
            public static final int USED_ITEM_SLOT_TWELVE = 11;
            public static final int USED_ITEM_SLOT_THIRTEEN = 12;
            public static final int USED_ITEM_SLOT_FOURTEEN = 13;
            public static final int USED_ITEM_SLOT_FIFTEEN = 14;
            public static final int USED_ITEM_SLOT_SIXTEEN = 15;
            public static final int USED_ITEM_SLOT_SEVENTEEN = 16;
            public static final int USED_ITEM_SLOT_EIGHTEEN = 17;
            public static final int USED_ITEM_SLOT_NINETEEN = 18;
            public static final int USED_ITEM_SLOT_TWENTY = 19;
            public static final int USED_ITEM_SLOT_TWENTYONE = 20;
            public static final int USED_ITEM_SLOT_TWENTYTWO = 21;
            public static final int USED_ITEM_SLOT_TWENTYTHREE = 22;
            public static final int USED_ITEM_SLOT_TWENTYFOUR = 23;
            public static final int USED_ITEM_SLOT_TWENTYFIVE = 24;
            public static final int USED_ITEM_SLOT_TWENTYSIX = 25;
            public static final int USED_ITEM_SLOT_TWENTYSEVEN = 26;
            public static final int USED_ITEM_SLOT_TWENTYEIGHT = 27;
            public static final int UNUSED_ITEM_SLOT_ONE = 28;
            public static final int UNUSED_ITEM_SLOT_TWO = 29;
            public static final int UNUSED_ITEM_SLOT_THREE = 30;
            public static final int UNUSED_ITEM_SLOT_FOUR = 31;
            public static final int UNUSED_ITEM_SLOT_FIVE = 32;
            public static final int UNUSED_ITEM_SLOT_SIX = 33;
            public static final int UNUSED_ITEM_SLOT_SEVEN = 34;
            public static final int UNUSED_ITEM_SLOT_EIGHT = 35;
            public static final int UNUSED_ITEM_SLOT_NINE = 36;
            public static final int UNUSED_ITEM_SLOT_TEN = 37;
            public static final int UNUSED_ITEM_SLOT_ELEVEN = 38;
            public static final int UNUSED_ITEM_SLOT_TWELVE = 39;
            public static final int UNUSED_ITEM_SLOT_THIRTEEN = 40;
            public static final int UNUSED_ITEM_SLOT_FOURTEEN = 41;
            public static final int UNUSED_ITEM_SLOT_FIFTEEN = 42;
            public static final int UNUSED_ITEM_SLOT_SIXTEEN = 43;
            public static final int UNUSED_ITEM_SLOT_SEVENTEEN = 44;
            public static final int UNUSED_ITEM_SLOT_EIGHTEEN = 45;
            public static final int UNUSED_ITEM_SLOT_NINETEEN = 46;
            public static final int UNUSED_ITEM_SLOT_TWENTY = 47;
            public static final int UNUSED_ITEM_SLOT_TWENTYONE = 48;
            public static final int UNUSED_ITEM_SLOT_TWENTYTWO = 49;
            public static final int UNUSED_ITEM_SLOT_TWENTYTHREE = 50;
            public static final int UNUSED_ITEM_SLOT_TWENTYFOUR = 51;
            public static final int UNUSED_ITEM_SLOT_TWENTYFIVE = 52;
            public static final int UNUSED_ITEM_SLOT_TWENTYSIX = 53;
            public static final int UNUSED_ITEM_SLOT_TWENTYSEVEN = 54;
            public static final int UNUSED_ITEM_SLOT_TWENTYEIGHT = 55;
        }
        // NOTE: this is rubbish not every interface will implement close sprite under 11
        public static class Global {
            public static final int DYNAMIC_CLOSE_BUTTON = 11;
        }
    }
}