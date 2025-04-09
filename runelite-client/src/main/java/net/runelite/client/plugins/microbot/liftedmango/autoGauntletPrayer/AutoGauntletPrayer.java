package net.runelite.client.plugins.microbot.liftedmango.autoGauntletPrayer;

import com.google.inject.Provides;
import net.runelite.api.HeadIcon;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;

import javax.inject.Inject;
import java.util.Set;

import static net.runelite.client.plugins.microbot.Microbot.log;

@PluginDescriptor(
        name = PluginDescriptor.LiftedMango + "Auto Gauntlet Prayer",
        description = "Auto Gauntlet Prayer plugin",
        tags = {"liftedmango", "Gauntlet", "pvm", "prayer", "money making", "auto", "boss"},
        enabledByDefault = false
)

public class AutoGauntletPrayer extends Plugin {
    @Inject
    private AutoGauntletConfig config;
    @Provides
    AutoGauntletConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoGauntletConfig.class);
    }

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

    private static final Set<Integer> DANGEROUS_TILES = Set.of(
            36047, 36048, // Corrupted tiles (Ground object)
            36150, 36151 // Gauntlet tiles (Ground object)
    );

    @Override
    protected void startUp() throws Exception {
        log("Auto gauntlet prayer plugin started!");
    }

    @Override
    protected void shutDown() throws Exception {
        log("Gauntlet plugin stopped!");
        Rs2Prayer.disableAllPrayers();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        System.out.println("Next prayer: " + nextPrayer);

        if (nextPrayer != null && !Rs2Prayer.isPrayerActive(nextPrayer)) {
            Rs2Prayer.toggle(nextPrayer, true);
        }

        Rs2NpcModel hunllef = Rs2Npc.getNpcs()
                .filter(npc -> HUNLLEF_IDS.contains(npc.getId()))
                .findFirst()
                .orElse(null);

        if (hunllef == null) {
            nextPrayer = null;
            return;
        }

        HeadIcon headIcon = Rs2Reflection.getHeadIcon(hunllef);
        System.out.println("Headicon: " + headIcon);

        switch (headIcon) {
            case RANGED:
                handleRangedHeadIcon();
                break;
            case MAGIC:
                handleMagicHeadIcon();
                break;
            case MELEE:
                handleMeleeHeadIcon();
                break;
            default:
                break;
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
            default:
                break;
        }

        checkAndTogglePrayers();
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
            default:
                break;
        }
    }

    private void handleRangedHeadIcon()
    {
        if (hasStaffInInventory() && !isHalberdEquipped())
        {
            equipStaff();
        }
        else if (hasHalberdInInventory() && !isStaffEquipped())
        {
            equipHalberd();
        }
    }

    private void handleMagicHeadIcon()
    {
        if (hasBowInInventory() && !isHalberdEquipped())
        {
            equipBow();
        }
        else if (hasHalberdInInventory() && !isBowEquipped())
        {
            equipHalberd();
        }
    }

    private void handleMeleeHeadIcon()
    {
        if (hasStaffInInventory() && !isBowEquipped())
        {
            equipStaff();
        }
        else if (hasBowInInventory() && !isStaffEquipped())
        {
            equipBow();
        }
    }

    private boolean hasBowInInventory() {
        return Rs2Inventory.contains(ItemID.CRYSTAL_BOW_BASIC)
                || Rs2Inventory.contains(ItemID.CRYSTAL_BOW_ATTUNED)
                || Rs2Inventory.contains(ItemID.CRYSTAL_BOW_PERFECTED)
                || Rs2Inventory.contains(ItemID.CORRUPTED_BOW_BASIC)
                || Rs2Inventory.contains(ItemID.CORRUPTED_BOW_ATTUNED)
                || Rs2Inventory.contains(ItemID.CORRUPTED_BOW_PERFECTED);
    }

    private boolean hasStaffInInventory() {
        return Rs2Inventory.contains(ItemID.CRYSTAL_STAFF_BASIC)
                || Rs2Inventory.contains(ItemID.CRYSTAL_STAFF_ATTUNED)
                || Rs2Inventory.contains(ItemID.CRYSTAL_STAFF_PERFECTED)
                || Rs2Inventory.contains(ItemID.CORRUPTED_STAFF_BASIC)
                || Rs2Inventory.contains(ItemID.CORRUPTED_STAFF_ATTUNED)
                || Rs2Inventory.contains(ItemID.CORRUPTED_STAFF_PERFECTED);
    }

    private boolean hasHalberdInInventory() {
        return Rs2Inventory.contains(ItemID.CRYSTAL_HALBERD_BASIC)
                || Rs2Inventory.contains(ItemID.CRYSTAL_HALBERD_ATTUNED)
                || Rs2Inventory.contains(ItemID.CRYSTAL_HALBERD_PERFECTED)
                || Rs2Inventory.contains(ItemID.CORRUPTED_HALBERD_BASIC)
                || Rs2Inventory.contains(ItemID.CORRUPTED_HALBERD_ATTUNED)
                || Rs2Inventory.contains(ItemID.CORRUPTED_HALBERD_PERFECTED);
    }

    private void equipBow() {
        Rs2Inventory.equip(ItemID.CORRUPTED_BOW_ATTUNED);
        Rs2Inventory.equip(ItemID.CRYSTAL_BOW_ATTUNED);
        Rs2Inventory.equip(ItemID.CRYSTAL_BOW_PERFECTED);
        Rs2Inventory.equip(ItemID.CORRUPTED_BOW_PERFECTED);
    }

    private void equipStaff() {
        Rs2Inventory.equip(ItemID.CRYSTAL_STAFF_BASIC);
        Rs2Inventory.equip(ItemID.CRYSTAL_STAFF_ATTUNED);
        Rs2Inventory.equip(ItemID.CRYSTAL_STAFF_PERFECTED);
        Rs2Inventory.equip(ItemID.CORRUPTED_STAFF_BASIC);
        Rs2Inventory.equip(ItemID.CORRUPTED_STAFF_ATTUNED);
        Rs2Inventory.equip(ItemID.CORRUPTED_STAFF_PERFECTED);
    }

    private void equipHalberd() {
        Rs2Inventory.equip(ItemID.CRYSTAL_HALBERD_BASIC);
        Rs2Inventory.equip(ItemID.CRYSTAL_HALBERD_ATTUNED);
        Rs2Inventory.equip(ItemID.CRYSTAL_HALBERD_PERFECTED);
        Rs2Inventory.equip(ItemID.CORRUPTED_HALBERD_BASIC);
        Rs2Inventory.equip(ItemID.CORRUPTED_HALBERD_ATTUNED);
        Rs2Inventory.equip(ItemID.CORRUPTED_HALBERD_PERFECTED);
    }

    private void checkAndTogglePrayers() {
        if (isBowEquipped() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
            toggleRigourPrayer();
        }
        if (isStaffEquipped() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
            toggleAuguryPrayer();
        }
        if (isHalberdEquipped() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            togglePietyPrayer();
        }
    }

    private boolean isBowEquipped() {
        return Rs2Equipment.hasEquipped(ItemID.CRYSTAL_BOW_PERFECTED)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_BOW_ATTUNED)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_BOW_BASIC)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_BOW_PERFECTED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_BOW_ATTUNED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_BOW_BASIC);
    }

    private boolean isStaffEquipped() {
        return Rs2Equipment.hasEquipped(ItemID.CRYSTAL_STAFF_PERFECTED)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_STAFF_BASIC)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_STAFF_ATTUNED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_STAFF_PERFECTED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_STAFF_ATTUNED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_STAFF_BASIC);
    }

    private boolean isHalberdEquipped() {
        return Rs2Equipment.hasEquipped(ItemID.CRYSTAL_HALBERD_PERFECTED)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_HALBERD_BASIC)
                || Rs2Equipment.hasEquipped(ItemID.CRYSTAL_HALBERD_ATTUNED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_HALBERD_PERFECTED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_HALBERD_ATTUNED)
                || Rs2Equipment.hasEquipped(ItemID.CORRUPTED_HALBERD_BASIC);
    }

    private void toggleRigourPrayer() {
        if (!config.MysticMight()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
        } else {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.STEEL_SKIN, true);
            }
            Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, true);
        }
    }

    private void toggleAuguryPrayer() {
        if (!config.MysticMight()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.AUGURY, true);
        } else {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.STEEL_SKIN, true);
            }
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_MIGHT, true);
        }
    }

    private void togglePietyPrayer() {
        if (!config.MysticMight()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        } else {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.STEEL_SKIN, true);
            }
            Rs2Prayer.toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, true);
            Rs2Prayer.toggle(Rs2PrayerEnum.INCREDIBLE_REFLEXES, true);
        }
    }
}