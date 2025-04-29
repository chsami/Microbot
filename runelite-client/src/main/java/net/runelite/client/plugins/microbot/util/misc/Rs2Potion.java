package net.runelite.client.plugins.microbot.util.misc;

import java.util.Arrays;
import java.util.List;

/**
 * A utility class for returning various Runescape potion names and variants.
 */
public class Rs2Potion
{
    private Rs2Potion()
    {
        // Utility class; prevent instantiation
    }

    /*
     * ======================================
     * Prayer-related potions
     * ======================================
     */

    /**
     * Returns a list of potions that restore Prayer points.
     *
     * @return a List of prayer-restoring potion names
     */
    public static List<String> getPrayerPotionsVariants()
    {
        return Arrays.asList(
                "Prayer potion",
                "Super restore",
                "Moonlight potion",
                "Moonlight moth mix",
                "Egniol potion"
        );
    }

    /**
     * Returns a single prayer regeneration potion name.
     *
     * @return the name of the prayer regeneration potion
     */
    public static String getPrayerRegenerationPotion()
    {
        return "Prayer regeneration potion";
    }

    /*
     * ======================================
     * Combat-boosting potions
     * ======================================
     */

    /**
     * Returns a list of potions that boost Ranged.
     *
     * @return a List of Ranged-boosting potion names
     */
    public static List<String> getRangePotionsVariants()
    {
        return Arrays.asList(
                "Ranging potion",
                "Divine ranging potion",
                "Bastion potion",
                "Divine bastion potion"
        );
    }

    /**
     * Returns a list of potions that boost Melee combat (Attack, Strength, Defence).
     *
     * @return a List of combat potion names
     */
    public static List<String> getCombatPotionsVariants()
    {
        return Arrays.asList(
                "combat potion",
                "super combat potion",
                "divine super combat potion"
        );
    }

    /**
     * Returns a list of potions that boost Magic.
     *
     * @return a List of Magic-boosting potion names
     */
    public static List<String> getMagicPotionsVariants()
    {
        return Arrays.asList(
                "magic potion",
                "divine magic potion",
                "battlemage potion",
                "divine battlemage potion"
        );
    }

    /**
     * Returns a list of potions that boost Attack.
     *
     * @return a List of Attack-boosting potion names
     */
    public static List<String> getAttackPotionsVariants()
    {
        return Arrays.asList(
                "Attack potion",            // +10% +3
                "Super attack",             // +15% +5
                "Divine super attack potion"
        );
    }

    /**
     * Returns a list of potions that boost Strength.
     *
     * @return a List of Strength-boosting potion names
     */
    public static List<String> getStrengthPotionsVariants()
    {
        return Arrays.asList(
                "Strength potion",          // +10% +3
                "Super strength",           // +15% +5
                "Divine super strength potion"
        );
    }

    /**
     * Returns a list of potions that boost Defence.
     *
     * @return a List of Defence-boosting potion names
     */
    public static List<String> getDefencePotionsVariants()
    {
        return Arrays.asList(
                "Defence potion",
                "Super defence",
                "Divine super defence potion"
        );
    }

    /*
     * ======================================
     * Stat restore potions
     * ======================================
     */

    /**
     * Returns a list of potions that restore non-Prayer combat stats.
     *
     * @return a List of restore potion names
     */
    public static List<String> getRestorePotionsVariants()
    {
        return Arrays.asList(
                "Restore potion", // +30% of level +10
                "Super restore"   // Also used in getPrayerPotionsVariants
        );
    }

    /*
     * ======================================
     * Health & stamina potions
     * ======================================
     */

    /**
     * Returns the name of the stamina potion.
     *
     * @return the name of the stamina potion
     */
    public static String getStaminaPotion()
    {
        return "Stamina potion";
    }

    /**
     * Returns a list of potions that restore run energy.
     *
     * @return a List of energy potion names
     */
    public static List<String> getRestoreEnergyPotionsVariants()
    {
        return Arrays.asList(
                "Super energy",
                "Super energy mix",
                "Energy potion",
                "Energy mix"
        );
    }

    /**
     * Returns the name of the Saradomin brew, which boosts Defence/HP but lowers other combat stats.
     *
     * @return the name of Saradomin brew
     */
    public static String getSaradominBrew()
    {
        return "Saradomin brew";
    }

    /*
     * ======================================
     * Poison/venom cure potions
     * ======================================
     */

    /**
     * Returns a list of potions that cure poison/venom and can provide immunity.
     *
     * @return a List of anti-poison potion names
     */
    public static List<String> getAntiPoisonVariants()
    {
        return Arrays.asList(
                "Antipoison",            // Cures poison
                "Superantipoison",       // Cures poison, 6 min immunity
                "Antidote+",             // 9 min immunity
                "Antidote++",            // Also cures venom if taken twice
                "Anti-venom",            // Instantly cures venom
                "Anti-venom+",           // Cures venom + 3 min immunity
                "Extended anti-venom+",  // Cures venom + 6 min immunity
                "Sanfew serum"           // Also cures disease and restores stats
        );
    }

    /**
     * Returns a list of potions that cure venom and provide venom immunity.
     *
     * @return a List of anti-venom potion names
     */
    public static List<String> getAntiVenomVariants() {
        return Arrays.asList(
                "Anti-venom",            // Instantly cures venom
                "Anti-venom+",           // Cures venom + 3 min immunity
                "Extended anti-venom+"   // Cures venom + 6 min immunity
        );
    }

    /*
     * ======================================
     * Skilling-related potions
     * ======================================
     */

    /**
     * Returns a list of potions that boost various skilling stats (e.g., Fishing, Agility, Hunter).
     *
     * @return a List of skilling potion names
     */
    public static List<String> getSkillingPotionsVariants()
    {
        return Arrays.asList(
                "Fishing potion",  // +3 Fishing
                "Agility potion",  // +3 Agility
                "Hunter potion"    // +3 Hunter
                // Add more skilling potions here if desired
        );
    }

    /*
     * ======================================
     * Dragonfire protection potions
     * ======================================
     */

    /**
     * Returns a list of potions that protect partially or completely against dragonfire.
     *
     * @return a List of antifire potion names
     */
    public static List<String> getAntifirePotionsVariants()
    {
        return Arrays.asList(
                "Antifire potion",       // ~6 min partial
                "Extended antifire",     // ~12 min partial
                "Super antifire potion", // ~3 min complete immunity
                "Extended super antifire"
        );
    }

    /*
     * ======================================
     * Weapon poisons
     * ======================================
     */

    /**
     * Returns a list of poisons that can be applied to weapons.
     *
     * @return a List of weapon poison names
     */
    public static List<String> getWeaponPoisonVariants()
    {
        return Arrays.asList(
                "Weapon poison",   // 4 dmg melee, 2 dmg ranged
                "Weapon poison+",  // 5 dmg melee, 3 dmg ranged
                "Weapon poison++"  // 6 dmg melee, 4 dmg ranged
        );
    }

    /*
     * ======================================
     * Miscellaneous potions
     * ======================================
     */

    public static String getImpRepellent()
    {
        return "Imp repellent"; // Used in imp-hunting
    }

    public static String getRelicymsBalm()
    {
        return "Relicym's balm"; // Cures disease
    }

    public static String getSerum207()
    {
        return "Serum 207"; // Temporarily cures Afflicted in Mort'ton
    }

    public static String getGuthixRestTea()
    {
        return "Guthix rest tea"; // Cures poison, etc.
    }

    public static String getCompostPotion()
    {
        return "Compost potion"; // Used to convert Compost into Supercompost
    }

    public static String getGuthixBalance()
    {
        return "Guthix balance"; // Used against Vampyre Juvinates
    }

    public static String getBlamishOil()
    {
        return "Blamish oil"; // Used to make oily fishing rods
    }

    public static String getShrinkMeQuick()
    {
        return "Shrink-me-quick"; // Reduces player size (Grim Tales quest)
    }

    public static String getGoadingPotion()
    {
        return "Goading potion"; // Causes aggression around the player
    }

    public static String getGoblinPotion()
    {
        return "Goblin potion"; // Transforms the player into a goblin
    }

    public static String getMagicEssence()
    {
        return "Magic essence"; // Temporarily raises Magic by 3 (Fairytale II quest)
    }

    public static String getMenaphiteRemedy()
    {
        return "Menaphite remedy"; // Slowly restores combat stats over 5 minutes
    }

    public static String getForgottenBrew()
    {
        return "Forgotten brew";
        // Upgraded version of Ancient brew:
        // Boosts Magic, restores Prayer, but lowers Attack/Strength/Defence
    }
}
