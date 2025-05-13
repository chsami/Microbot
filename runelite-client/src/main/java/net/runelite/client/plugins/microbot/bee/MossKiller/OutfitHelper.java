package net.runelite.client.plugins.microbot.bee.MossKiller;

import lombok.Getter;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.GearEnums;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class OutfitHelper {

    @Getter
    public enum OutfitType {
        MOSS_MAGE("Moss Mage",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Studded chaps",
                        "Rune Chainbody",
                        "Amulet of power"
                }),
        NAKED_MAGE("Naked Moss Mage",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of magic"
                }),
        MAX_MAGE("Max Mage",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of magic",
                        "Blue wizard hat",
                        "Blue wizard top",
                        "Team-50 cape",
                        "Leather chaps"
                }),
        RUNE_MAGE("Rune Mage",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Rune med helm",
                        "Rune chainbody",
                        "Rune kiteshield",
                        "Team-50 cape",
                        "Studded chaps"
                }),
        ADDY_MAGE("Addy Mage",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Blue wizard hat",
                        "Adamant chainbody",
                        "Adamant sq shield",
                        "Team-50 cape",
                        "Studded chaps"
                }),
        ADDY_MAGE_ALT("Addy Mage Alt",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of magic",
                        "Adamant med helm",
                        "Studded body",
                        "Adamant sq shield",
                        "Team-50 cape",
                        "Studded chaps"
                }),
        FULL_RUNE("Full Rune",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Rune platebody",
                        "Rune platelegs",
                        "Team-50 cape",
                        "Rune kiteshield",
                        "Rune full helm"
                }),
        FULL_RUNE_SKIRT("Full Rune Skirt",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Rune platebody",
                        "Rune plateskirt",
                        "Team-50 cape",
                        "Rune kiteshield",
                        "Rune full helm"
                }),
        FULL_RUNE_CHAIN_SKIRT("Full Rune Chain(S)",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Rune chainbody",
                        "Rune plateskirt",
                        "Team-50 cape",
                        "Rune kiteshield",
                        "Rune full helm"
                }),
        FULL_RUNE_CHAIN("Full Rune Chain",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Rune chainbody",
                        "Rune platelegs",
                        "Team-50 cape",
                        "Rune kiteshield",
                        "Rune full helm"
                }),
        FULL_RUNE_CHAPS("Full Rune Chaps",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Rune platebody",
                        "Studded chaps",
                        "Team-50 cape",
                        "Rune kiteshield",
                        "Rune full helm"
                }),
        FULL_ADDY("Full Addy",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Adamant platebody",
                        "Adamant platelegs",
                        "Team-50 cape",
                        "Adamant kiteshield",
                        "Adamant full helm"
                }),
        FULL_ADDY_PRAY("Addy Prayer",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Holy symbol",
                        "Adamant platebody",
                        "Studded chaps",
                        "Team-50 cape",
                        "Adamant kiteshield",
                        "Adamant full helm"
                }),
        FULL_ADDY_CHAPS("Full Addy Chaps",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Adamant platebody",
                        "Studded chaps",
                        "Team-50 cape",
                        "Adamant kiteshield",
                        "Adamant full helm"
                }),
        BASIC_ARCHER("Basic Archer",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Leather body",
                        "Studded chaps",
                        "Team-50 cape",
                        "Coif"
                }),
        DHIDE_ARCHER("Dragonhide Archer",
                new String[]{
                        "Green d'hide vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Green d'hide body",
                        "Green d'hide chaps",
                        "Team-50 cape",
                        "Rune med helm"
                }),
        ADDY_ARCHER("Addy Archer",
                new String[]{
                        "Leather vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Adamant chainbody",
                        "Studded chaps",
                        "Team-50 cape",
                        "Adamant med helm"
                }),
        RUNE_ARCHER("Rune Archer",
                new String[]{
                        "Green d'hide vambraces",
                        "Leather boots",
                        "Amulet of power",
                        "Rune chainbody",
                        "Studded chaps",
                        "Team-50 cape",
                        "Rune med helm"
                });


        private final String name;
        private final String[] outfitItems;

        OutfitType(String name, String[] outfitItems){
            this.name = name;
            this.outfitItems = outfitItems;
        }

        public String getName() {
            return name;
        }

        public String[] getOutfitItems() {
            return outfitItems;
        }
    }

    public static boolean equipOutfit(OutfitType outfitType) {
        // Use default config behavior with TEAM_CAPE_50 as fallback
        MossKillerConfig defaultConfig = new MossKillerConfig() {
            @Override
            public GearEnums.Cape capePreference() {
                return GearEnums.Cape.TEAM_CAPE_50;
            }
        };

        return equipOutfit(outfitType, defaultConfig);
    }

    public static boolean equipOutfit(OutfitType outfitType, MossKillerConfig config) {
        String[] outfitItems = outfitType.getOutfitItems();
        String outfitName = outfitType.getName();
        if (outfitItems == null || outfitItems.length == 0) {
            Microbot.log("Outfit items list is empty or null, can't equip " + outfitName);
            return false;
        }

        String preferredCapeName = config.capePreference().toString();

        Microbot.log("Starting to equip: " + outfitName);
        for (String item : outfitItems) {
            // Replace Team-50 cape with preferred cape
            if ("Team-50 cape".equalsIgnoreCase(item) &&
                    !"Team-50 cape".equalsIgnoreCase(preferredCapeName)) {
                Microbot.log("Replacing Team-50 cape with: " + preferredCapeName);
                item = preferredCapeName;
            }

            Rs2Bank.withdrawAndEquip(item);
            String finalItem = item;
            if (!sleepUntil(() -> Rs2Equipment.isWearing(finalItem), 5000)) {
                Microbot.log("Failed to equip: " + item + " from " + outfitName);
                return false;
            }
            Microbot.log(item + " from " + outfitName + " has been equipped.");
        }

        Microbot.log("Successfully equipped: " + outfitName);
        return true;
    }
}