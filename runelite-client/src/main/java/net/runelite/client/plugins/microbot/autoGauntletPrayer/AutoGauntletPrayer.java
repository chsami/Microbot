package net.runelite.client.plugins.microbot.autoGauntletPrayer;

import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;

import java.util.Set;

import static net.runelite.client.plugins.microbot.Microbot.log;

@PluginDescriptor(
        name = PluginDescriptor.LiftedMango + "Auto Gauntlet Prayer",
        description = "Auto Gauntlet Prayer plugin",
        tags = {"Gauntlet", "pvm", "prayer", "money making", "auto", "boss"},
        enabledByDefault = false
)
public class AutoGauntletPrayer extends Plugin {
    private final int RANGE_PROJECTILE_MINIBOSS = 1705;
    private final int MAGE_PROJECTILE_MINIBOSS = 1701;
    private final int RANGE_PROJECTILE = 1711;
    private final int MAGE_PROJECTILE = 1707;
    private final int CG_RANGE_PROJECTILE = 1712;
    private final int CG_MAGE_PROJECTILE = 1708;
    private final int DEACTIVATE_MAGE_PROJECTILE = 1713;
    private final int CG_DEACTIVATE_MAGE_PROJECTILE = 1714;
    private final int MAGE_ANIMATION = 8754;
    private final int RANGE_ANIMATION = 8755;
    private Rs2PrayerEnum nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;

    private static final Set<Integer> HUNLLEF_IDS = Set.of(
            9035, 9036, 9037, 9038, // Corrupted Hunllef variants
            9021, 9022, 9023, 9024  // Crystalline Hunllef variants
    );

    @Override
    protected void startUp() throws Exception {
        log("Auto gauntlet prayer plugin started!");
    }

    @Override
    protected void shutDown() throws Exception {
        log("Gauntlet plugin stopped!");
        // Deactivate all protective prayers on shutdown
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        System.out.println("Next prayer: " + nextPrayer);
        if (nextPrayer != null && !Rs2Prayer.isPrayerActive(nextPrayer)) {
            Rs2Prayer.toggle(nextPrayer, true);
//            nextPrayer = null;
        }

        NPC hunllef = Microbot.getClient().getNpcs().stream()
                .filter(npc -> HUNLLEF_IDS.contains(npc.getId()))
                .findFirst()
                .orElse(null);

        if (hunllef == null) {
            nextPrayer = null;
            return;
        }

        HeadIcon headIcon = Rs2Reflection.getHeadIcon(hunllef);
        if (headIcon == HeadIcon.RANGED && !Rs2Inventory.contains("Halberd")) {
            Rs2Inventory.equip("Crystal staff");
            Rs2Inventory.equip("Corrupted staff");
        } else if (headIcon == HeadIcon.MAGIC && !Rs2Inventory.contains("Halberd")) {
            Rs2Inventory.equip(ItemID.CORRUPTED_BOW_ATTUNED);
            Rs2Inventory.equip(ItemID.CRYSTAL_BOW_ATTUNED);
            Rs2Inventory.equip(ItemID.CRYSTAL_BOW_PERFECTED);
            Rs2Inventory.equip(ItemID.CORRUPTED_BOW_PERFECTED);
        } else if (headIcon == HeadIcon.MELEE && !Rs2Inventory.contains("bow")) {
            Rs2Inventory.equip("Crystal staff");
            Rs2Inventory.equip("Corrupted staff");
        } else if (headIcon == HeadIcon.MAGIC && !Rs2Inventory.contains("bow")
//        !Rs2Inventory.contains(ItemID.CORRUPTED_BOW_PERFECTED)
//                && !Rs2Inventory.contains(ItemID.CRYSTAL_BOW_PERFECTED)
//                && !Rs2Inventory.contains(ItemID.CORRUPTED_BOW_ATTUNED)
//                && !Rs2Inventory.contains(ItemID.CRYSTAL_BOW_ATTUNED)
                ) {
//            Rs2Inventory.equip("Corrupted halberd");
//            Rs2Inventory.equip("Crystal halberd");
            Rs2Inventory.equip(23896); //CRYSTAL_HALBERD_ATTUNED
            Rs2Inventory.equip(23897); //CRYSTAL_HALBERD_PERFECTED
            Rs2Inventory.equip(23850); //CORRUPTED_HALBERD_ATTUNED
            Rs2Inventory.equip(23851); //CORRUPTED_HALBERD_PERFECTED
        } else if (headIcon == HeadIcon.RANGED && !Rs2Inventory.contains("staff")) {
            Rs2Inventory.equip(23896); //CRYSTAL_HALBERD_ATTUNED
            Rs2Inventory.equip(23897); //CRYSTAL_HALBERD_PERFECTED
            Rs2Inventory.equip(23850); //CORRUPTED_HALBERD_ATTUNED
            Rs2Inventory.equip(23851); //CORRUPTED_HALBERD_PERFECTED
        } else if (headIcon == HeadIcon.MELEE && !Rs2Inventory.contains("staff")) {
            Rs2Inventory.equip(23856); //CORRUPTED_BOW_ATTUNED
            Rs2Inventory.equip(23856); //CRYSTAL_BOW_ATTUNED
            Rs2Inventory.equip(23903); //CRYSTAL_BOW_PERFECTED
            Rs2Inventory.equip(23857); //CORRUPTED_BOW_PERFECTED
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        int projectileId = event.getProjectile().getId();
        switch (projectileId) {
            case MAGE_PROJECTILE:
            case CG_MAGE_PROJECTILE:
            case MAGE_PROJECTILE_MINIBOSS:
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                break;
            case RANGE_PROJECTILE:
            case CG_RANGE_PROJECTILE:
            case RANGE_PROJECTILE_MINIBOSS:
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                break;
        }

        if (Rs2Equipment.hasEquipped(ItemID.CRYSTAL_BOW_PERFECTED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_BOW_ATTUNED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_BOW_PERFECTED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_BOW_ATTUNED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
        }
        if (Rs2Equipment.hasEquipped(ItemID.CRYSTAL_STAFF_PERFECTED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_STAFF_BASIC) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_STAFF_ATTUNED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_STAFF_PERFECTED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_STAFF_ATTUNED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.AUGURY, true);
        }
        if (Rs2Equipment.hasEquipped(ItemID.CRYSTAL_HALBERD_PERFECTED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_HALBERD_BASIC) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_HALBERD_ATTUNED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_HALBERD_PERFECTED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_HALBERD_ATTUNED) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        }
    }


    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        if (!HUNLLEF_IDS.contains(npc.getId())) return;

        int animationID = npc.getAnimation();
        switch (animationID) {
            case MAGE_ANIMATION:
                nextPrayer = Rs2PrayerEnum.PROTECT_MAGIC;
                Rs2Prayer.toggle(nextPrayer, true);
                break;
            case RANGE_ANIMATION:
                nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;
                Rs2Prayer.toggle(nextPrayer, true);
                break;
        }
    }
}
