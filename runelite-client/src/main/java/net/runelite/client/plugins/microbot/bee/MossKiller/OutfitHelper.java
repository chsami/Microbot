package net.runelite.client.plugins.microbot.bee.MossKiller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Getter
@RequiredArgsConstructor
public class OutfitHelper {

    @Getter
    public enum OutfitType {
        MAGE("Moss Mage", new String[]{"Leather vambraces",
                "Leather boots",
                "Studded chaps", "Rune Chainbody",
                "Amulet of power"});

        private final String name;
        private final String[] outfitItems;

        OutfitType(String name, String[] outfitItems){
            this.name = name;
            this.outfitItems = outfitItems;
        }

    }

    public static boolean equipOutfit(OutfitType outfitType) {
        String[] outfitItems = outfitType.getOutfitItems();
        String outfitName = outfitType.getName();
        if (outfitItems == null || outfitItems.length == 0) {
            Microbot.log("Outfit items list is empty or null, can't equip " + outfitName);
            return false;
        }
        Microbot.log("Starting to equip: " + outfitName);
        for (String item : outfitItems) {
            Rs2Bank.withdrawAndEquip(item); // Assuming this method doesn't need a result
            if (!sleepUntil(() -> Rs2Equipment.isWearing(item), 5000)) {
                Microbot.log("Failed to equip: " + item + " from " + outfitName);
                return false; // Fail fast - if one item fails, the whole thing fails
            }
            Microbot.log(item + " from " + outfitName + " has been equipped.");
        }
        Microbot.log("Successfully equipped: " + outfitName);
        return true; // All items equipped
    }

}
