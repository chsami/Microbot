package net.runelite.client.plugins.microbot.bee;

import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;

import static net.runelite.api.ItemID.*;
import static net.runelite.api.ItemID.BLACK_PLATELEGS_G;
import static net.runelite.api.gameval.ItemID.*;
import static net.runelite.api.gameval.ItemID.ADAMANT_FULL_HELM;
import static net.runelite.api.gameval.ItemID.ADAMANT_PLATEBODY;
import static net.runelite.api.gameval.ItemID.ADAMANT_PLATEBODY_H1;
import static net.runelite.api.gameval.ItemID.ADAMANT_PLATEBODY_H2;
import static net.runelite.api.gameval.ItemID.ADAMANT_PLATEBODY_H3;
import static net.runelite.api.gameval.ItemID.ADAMANT_PLATEBODY_H4;
import static net.runelite.api.gameval.ItemID.ADAMANT_PLATEBODY_H5;
import static net.runelite.api.gameval.ItemID.ADAMANT_PLATELEGS;
import static net.runelite.api.gameval.ItemID.BLACK_FULL_HELM;
import static net.runelite.api.gameval.ItemID.BLACK_PLATEBODY;
import static net.runelite.api.gameval.ItemID.BLACK_PLATEBODY_H1;
import static net.runelite.api.gameval.ItemID.BLACK_PLATEBODY_H2;
import static net.runelite.api.gameval.ItemID.BLACK_PLATEBODY_H3;
import static net.runelite.api.gameval.ItemID.BLACK_PLATEBODY_H4;
import static net.runelite.api.gameval.ItemID.BLACK_PLATEBODY_H5;
import static net.runelite.api.gameval.ItemID.BLACK_PLATELEGS;
import static net.runelite.api.gameval.ItemID.MITHRIL_FULL_HELM;
import static net.runelite.api.gameval.ItemID.MITHRIL_PLATEBODY;
import static net.runelite.api.gameval.ItemID.MITHRIL_PLATELEGS;

public class EquipmentIdentifier {

    public static boolean isWearingRuneArmor (Rs2PlayerModel player){
        int[] equipmentIds = player.getPlayerComposition().getEquipmentIds();
        if (equipmentIds == null) {
            return false;
        }

        // Initialize a counter for melee gear
        int meleeGearCount = 0;

        // Full helms (Rune, Gilded, God)
        if (equipmentIds[0] == 3211 ||  // Rune full helm
                equipmentIds[0] == 4667 ||  // Rune full helm (g)
                equipmentIds[0] == 4675 ||  // Rune full helm (t)
                equipmentIds[0] == 4705 ||  // Zamorak full helm
                equipmentIds[0] == 4721 ||  // Guthix full helm
                equipmentIds[0] == 4713 ||  // Saradomin full helm
                equipmentIds[0] == 14534 ||  // Bandos full helm
                equipmentIds[0] == 14524 ||  // Armadyl full helm
                equipmentIds[0] == 14514 ||  // Ancients full helm
                equipmentIds[0] == 12334 ||  // Rune helm (h1)
                equipmentIds[0] == 12336 ||  // Rune helm (h2)
                equipmentIds[0] == 12338 ||  // Rune helm (h3)
                equipmentIds[0] == 12340 ||  // Rune helm (h4)
                equipmentIds[0] == 12342 ||  // Rune helm (h5)
                equipmentIds[0] == 5534 || // Gilded full helm
                equipmentIds[0] == ADAMANT_FULL_HELM ||
                equipmentIds[0] == ADAMANT_FULL_HELM_GOLD ||
                equipmentIds[0] == ADAMANT_FULL_HELM_TRIM ||
                equipmentIds[0] == ADAMANT_HELM_H1 ||
                equipmentIds[0] == ADAMANT_HELM_H2 ||
                equipmentIds[0] == ADAMANT_HELM_H3 ||
                equipmentIds[0] == ADAMANT_HELM_H4 ||
                equipmentIds[0] == ADAMANT_HELM_H5 ||
                equipmentIds[0] == BLACK_HELM_H1 ||
                equipmentIds[0] == BLACK_HELM_H2 ||
                equipmentIds[0] == BLACK_HELM_H3 ||
                equipmentIds[0] == BLACK_HELM_H4 ||
                equipmentIds[0] == BLACK_HELM_H5 ||
                equipmentIds[0] == MITHRIL_FULL_HELM ||
                equipmentIds[0] == MITHRIL_FULL_HELM_T ||
                equipmentIds[0] == MITHRIL_FULL_HELM_G ||
                equipmentIds[0] == BLACK_FULL_HELM ||
                equipmentIds[0] == BLACK_FULL_HELM_G ||
                equipmentIds[0] == BLACK_FULL_HELM_T) {

            meleeGearCount++;
        }

        // Bodies (Rune, Gilded, God, Chainbody)
        if (equipmentIds[4] == 3175 ||  // Rune platebody
                equipmentIds[4] == 4663 ||  // Rune platebody (g)
                equipmentIds[4] == 4671 ||  // Rune platebody (t)
                equipmentIds[4] == 4701 ||  // Zamorak platebody
                equipmentIds[4] == 4717 ||  // Guthix platebody
                equipmentIds[4] == 4709 ||  // Saradomin platebody
                equipmentIds[4] == 14528 ||  // Bandos platebody
                equipmentIds[4] == 14518 ||  // Armadyl platebody
                equipmentIds[4] == 14508 ||  // Ancients platebody
                equipmentIds[4] == 5529 ||  // Gilded platebody
                equipmentIds[4] == 22197 ||
                equipmentIds[4] == ADAMANT_PLATEBODY ||
                equipmentIds[4] == ADAMANT_PLATEBODY_G ||
                equipmentIds[4] == ADAMANT_PLATEBODY_T ||
                equipmentIds[4] == ADAMANT_PLATEBODY_H1 ||
                equipmentIds[4] == ADAMANT_PLATEBODY_H2 ||
                equipmentIds[4] == ADAMANT_PLATEBODY_H3 ||
                equipmentIds[4] == ADAMANT_PLATEBODY_H4 ||
                equipmentIds[4] == ADAMANT_PLATEBODY_H5 ||
                equipmentIds[4] == BLACK_PLATEBODY ||
                equipmentIds[4] == BLACK_PLATEBODY_T ||
                equipmentIds[4] == BLACK_PLATEBODY_G ||
                equipmentIds[4] == BLACK_PLATEBODY_H1 ||
                equipmentIds[4] == BLACK_PLATEBODY_H2 ||
                equipmentIds[4] == BLACK_PLATEBODY_H3 ||
                equipmentIds[4] == BLACK_PLATEBODY_H4 ||
                equipmentIds[4] == BLACK_PLATEBODY_H5 ||
                equipmentIds[4] == MITHRIL_PLATEBODY ||
                equipmentIds[4] == MITHRIL_PLATEBODY_T ||
                equipmentIds[4] == MITHRIL_PLATEBODY_G) {  // Gilded chainbody
            meleeGearCount++;
        }

        // Legs/Skirts (Rune, Gilded, God)
        if (equipmentIds[7] == 3127 ||  // Rune platelegs
                equipmentIds[7] == 4665 ||  // Rune platelegs (g)
                equipmentIds[7] == 4673 ||  // Rune platelegs (t)
                equipmentIds[7] == 4703 ||  // Zamorak platelegs
                equipmentIds[7] == 4719 ||  // Guthix platelegs
                equipmentIds[7] == 4711 ||  // Saradomin platelegs
                equipmentIds[7] == 14530 ||  // Bandos platelegs
                equipmentIds[7] == 14520 ||  // Armadyl platelegs
                equipmentIds[7] == 14510 ||  // Ancients platelegs
                equipmentIds[7] == 5531 || // gilded platelegs
                equipmentIds[7] == 5524 ||  // Rune plateskirt (g)
                equipmentIds[7] == 5533 ||  // Gilded plateskirt
                equipmentIds[7] == 5525 ||  // Trimmed plateskirt
                equipmentIds[7] == 5526 ||  // Zamorak plateskirt
                equipmentIds[7] == 5527 ||  // Saradomin plateskirt
                equipmentIds[7] == 5528 ||  // Guthix plateskirt
                equipmentIds[7] == 14532 ||  // Bandos plateskirt
                equipmentIds[7] == 14522 ||  // Armadyl plateskirt
                equipmentIds[7] == 14512 ||
                equipmentIds[7] == ADAMANT_PLATELEGS ||
                equipmentIds[7] == ADAMANT_PLATELEGS_G ||
                equipmentIds[7] == ADAMANT_PLATELEGS_T ||
                equipmentIds[7] == MITHRIL_PLATELEGS ||
                equipmentIds[7] == MITHRIL_PLATELEGS_T ||
                equipmentIds[7] == MITHRIL_PLATELEGS_G ||
                equipmentIds[7] == BLACK_PLATELEGS ||
                equipmentIds[7] == BLACK_PLATELEGS_T ||
                equipmentIds[7] == BLACK_PLATELEGS_G) {  // Ancients plateskirt
            meleeGearCount++;
        }

        // If the player is wearing at least 2 pieces of melee gear, classify them as wearing melee armor
        return meleeGearCount >= 2;
    }

    public static boolean isWearingGreenDhide (Rs2PlayerModel player){
        int[] equipmentIds = player.getPlayerComposition().getEquipmentIds();
        if (equipmentIds == null) {
            return false;
        }

        // Check against the correct Green D'hide armor IDs, including gilded and (g) variants
        return equipmentIds[4] == 3183 ||  // Green d'hide body
                equipmentIds[4] == 25312 ||  // Gilded d'hide body
                equipmentIds[4] == 9418 ||  // Green d'hide body (g)
                equipmentIds[7] == 3147 ||  // Green d'hide chaps
                equipmentIds[7] == 25315 ||  // Gilded d'hide chaps
                equipmentIds[7] == 9426;    // Green d'hide chaps (g)
    }

    public static boolean isWizard (Rs2PlayerModel player){
        int[] equipmentIds = player.getPlayerComposition().getEquipmentIds();
        if (equipmentIds == null) {
            return false;
        }

        // Check if the player is wearing Rune armor or a shield
        if (isWearingRuneArmor(player)) {
            return false; // Not a wizard if wearing Rune armor
        }

        // Count the number of mage-related equipment pieces the player is wearing
        int mageGearCount = 0;

        // Headgear
        if (equipmentIds[0] == 2627 ||  // Blue Wizard hat
                equipmentIds[0] == 3065 ||  // Black wizard hat
                equipmentIds[0] == 9442 ||  // Blue wizard hat (g)
                equipmentIds[0] == 9444 ||  // Blue wizard hat (t)
                equipmentIds[0] == 14502 ||  // Black wizard hat (g)
                equipmentIds[0] == 14503) {  // Black wizard hat (t)
            mageGearCount++;
        }

        // Weapons (staves)
        if (equipmentIds[3] == 3435 ||  // Staff of fire
                equipmentIds[3] == 3433 ||  // Staff of earth
                equipmentIds[3] == 3429 ||  // Staff of air
                equipmentIds[3] == 3431 ||  // Staff of water
                equipmentIds[3] == 24418) { // Bryophyta staff
            mageGearCount++;
        }

        // Shields (Mage shields or Sq shields)
        if (equipmentIds[5] == 3233 ||  // Rune sq shield
                equipmentIds[5] == 22200) {  // Gilded sq shield
            mageGearCount++;
        }

        // Robes and skirts (including trimmed and gilded versions)
        if (equipmentIds[4] == 3081 ||  // Zamorak robe bottom
                equipmentIds[4] == 2626 ||  // Blue wizard robe
                equipmentIds[4] == 2629 ||  // Zamorak robe top
                equipmentIds[4] == 2875 ||  // Black robe
                equipmentIds[4] == 9438 ||  // Blue wizard robe (g)
                equipmentIds[4] == 9440 ||  // Blue wizard robe (t)
                equipmentIds[4] == 14497 ||  // Black wizard robe (g)
                equipmentIds[4] == 14499 ||  // Black wizard robe (t)
                equipmentIds[7] == 3059 ||  // Blue skirt
                equipmentIds[7] == 3063 ||  // Black skirt
                equipmentIds[7] == 9434 ||  // Blue skirt (g)
                equipmentIds[7] == 9436 ||  // Blue skirt (t)
                equipmentIds[7] == 14493 ||  // Black skirt (g)
                equipmentIds[7] == 14495) {  // Black skirt (t)
            mageGearCount++;
        }

        // If the player is wearing at least 2 pieces of mage gear, classify them as a wizard
        return mageGearCount >= 2;
    }
}
