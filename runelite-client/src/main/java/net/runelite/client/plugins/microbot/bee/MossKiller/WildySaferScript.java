package net.runelite.client.plugins.microbot.bee.MossKiller;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.AttackStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.api.EquipmentInventorySlot.AMMO;
import static net.runelite.api.ItemID.*;
import static net.runelite.api.NpcID.MOSS_GIANT_2093;
import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.getNpcs;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.eatAt;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkFastCanvas;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo;

public class WildySaferScript extends Script {
    
    @Inject
    MossKillerPlugin mossKillerPlugin;

    @Inject
    WildyKillerScript wildyKillerScript;

    @Inject
    Client client;

    @Inject
    private MossKillerConfig mossKillerConfig;

    private static MossKillerConfig config;

    @Inject
    public WildySaferScript(MossKillerConfig config) {
        WildySaferScript.config = config;
    }

    private static int[] LOOT_LIST = new int[]{MOSSY_KEY, LAW_RUNE, AIR_RUNE, COSMIC_RUNE, CHAOS_RUNE, DEATH_RUNE, NATURE_RUNE, UNCUT_RUBY, UNCUT_DIAMOND, MITHRIL_ARROW};
    private static int[] ALCHABLES = new int[]{STEEL_KITESHIELD, MITHRIL_SWORD, BLACK_SQ_SHIELD};

    private static final WorldArea SAFE_ZONE_AREA = new WorldArea(3130, 3822, 30, 20, 0);
    public static final WorldPoint SAFESPOT = new WorldPoint(3137, 3833, 0);
    public static final WorldPoint SAFESPOT1 = new WorldPoint(3137, 3831, 0);

    public boolean move = false;
    public boolean safeSpot1Attack = false;
    public boolean iveMoved = false;

    public static boolean test = false;
    public boolean run(MossKillerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                //if you're at moss giants and your inventory is not prepared, prepare inventory
                if (isInMossGiantArea() && !isInventoryPrepared()) {
                    doBankingLogic();
                }
                // If you're not at moss giants but have prepared inventory, go to moss giants
                if (!isInMossGiantArea() && isInventoryPrepared()) {
                    System.out.println("not in moss giant area but inventory is prepared");
                    walkTo(SAFESPOT);
                    return;
                    // if you're not at moss giants but don't have prepared inventory, prepare inventory
                } else if (!isInMossGiantArea() && !isInventoryPrepared()) {
                    doBankingLogic();
                    return;
                }
                // If at safe area of moss giants and there is items to loot, loot them
                if (isInMossGiantArea() && itemsToLoot()) {
                    lootItems();
                }
                // If not at the safe spot but in the safe zone area, go to the safe spot
                if (!isAtSafeSpot() && !iveMoved && isInMossGiantArea()) {
                    System.out.println("not as safe spot but in moss giant area");
                    walkFastCanvas(SAFESPOT);
                    sleep(1200,2000);
                    return;
                }

                //if using magic make sure autocast is on
                if (config.attackStyle() == AttackStyle.MAGIC && Rs2Equipment.isWearing(STAFF_OF_FIRE)
                        && !mossKillerPlugin.getAttackStyle()) {
                    wildyKillerScript.config = mossKillerConfig;
                    wildyKillerScript.setAutocastFireStrike();
                }
                //if using magic make sure staff is equipped
                if (config.attackStyle() == AttackStyle.MAGIC && !Rs2Equipment.isWearing(STAFF_OF_FIRE) && Rs2Inventory.contains(STAFF_OF_FIRE)) {
                    Rs2Inventory.equip(STAFF_OF_FIRE);
                }
                //if using magic make sure staff you have a staff in your possesion
                if (config.attackStyle() == AttackStyle.MAGIC && !Rs2Equipment.isWearing(STAFF_OF_FIRE) && !Rs2Inventory.contains(STAFF_OF_FIRE)) {
                    doBankingLogic();
                }

                // if at the safe spot attack the moss giant and run to the safespot
                if (isAtSafeSpot() && !Rs2Player.isInteracting() && desired2093Exists()) {
                    attackMossGiant();
                }

                if (isAtSafeSpot() && move) {
                    walkFastCanvas(SAFESPOT1);
                    sleep(900,1400);
                    Rs2Npc.interact(MOSS_GIANT_2093,"attack");
                    move = false;
                    iveMoved = true;
                }

                if (adjacentToSafeSpot1()) {
                    walkFastCanvas(SAFESPOT);
                }


                if (!Rs2Player.isInteracting() && iveMoved && isAtSafeSpot1() && !isAnyMossGiantInteractingWithMe()) {
                    System.out.println("ive moved is false");
                    iveMoved = false;
                }

                if (Rs2Player.isInteracting() && isAtSafeSpot1() && isAnyMossGiantInteractingWithMe() && safeSpot1Attack) {
                    walkFastCanvas(SAFESPOT);
                    iveMoved = true;
                }


                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean isInMossGiantArea() {
        return SAFE_ZONE_AREA.contains(Rs2Player.getWorldLocation());
    }

    private boolean attackMossGiant() {
        Rs2NpcModel mossGiant = Rs2Npc.getNpc(MOSS_GIANT_2093);

        if (mossGiant != null && Rs2Player.getWorldLocation() != null) {
            double distance = mossGiant.getWorldLocation().distanceTo(Rs2Player.getWorldLocation());
            System.out.println("Distance to moss giant: " + distance);
        }

        if (!mossGiant.isDead() && isInMossGiantArea()) {
            Rs2Camera.turnTo(mossGiant);
            Rs2Npc.interact(mossGiant, "attack");
            sleep(100,300);
            sleepUntil(Rs2Player::isAnimating);

            if (Rs2Player.isInteracting()) {
                if (!isAtSafeSpot() && !iveMoved && !move && !safeSpot1Attack) walkFastCanvas(SAFESPOT);
                sleepUntil(this::isAtSafeSpot);
                sleepUntil(() -> !Rs2Npc.isMoving(mossGiant));
                if (!mossGiant.isDead()) {
                    Rs2Npc.attack(MOSS_GIANT_2093);
                }
            }

            return true;
        }

        return false;
    }

    public boolean isAnyMossGiantInteractingWithMe() {
        Stream<Rs2NpcModel> mossGiantStream = Rs2Npc.getNpcs("Moss giant");

        if (mossGiantStream == null) {
            System.out.println("No Moss Giants found (Stream is null).");
            return false;
        }

        var player = Rs2Player.getLocalPlayer();
        if (player == null) {
            System.out.println("Local player not found!");
            return false;
        }

        String playerName = player.getName();
        System.out.println("Local Player Name: " + playerName);

        List<Rs2NpcModel> mossGiants = mossGiantStream.collect(Collectors.toList());

        for (Rs2NpcModel mossGiant : mossGiants) {
            if (mossGiant != null) {
                var interacting = mossGiant.getInteracting();
                String interactingName = interacting != null ? interacting.getName() : "None";

                System.out.println("Moss Giant interacting with: " + interactingName);

                if (interacting != null && interactingName.equals(playerName)) {
                    System.out.println("A Moss Giant is interacting with YOU!");
                    return true;
                }
            }
        }

        System.out.println("No Moss Giant is interacting with you.");
        return false;
    }

    private boolean itemsToLoot() {
        RS2Item[] items = Rs2GroundItem.getAllFromWorldPoint(5, SAFESPOT);
        if (items.length == 0) return false;

        for (int lootItem : LOOT_LIST) {
            for (RS2Item item : items) {
                if (item.getItem().getId() == lootItem) {
                    return true; // Lootable item found
                }
            }
        }

        if (config.buryBones()) {
            for (RS2Item item : items) {
                if (item.getItem().getId() == BIG_BONES) {
                    return true;
                }
            }
        }

        if (config.alchLoot()) {
            for (int lootItem : ALCHABLES) {
                for (RS2Item item : items) {
                    if (item.getItem().getId() == lootItem) {
                        return true; // Lootable item found
                    }
                }
            }

        }

        return false; // No lootable items found
    }

    public boolean isAtSafeSpot() {
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        return playerPos.equals(SAFESPOT);
    }

    public boolean isAtSafeSpot1() {
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        return playerPos.equals(SAFESPOT1);
    }

    public boolean adjacentToSafeSpot1() {
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        WorldPoint westOfSafeSpot = new WorldPoint(
                SAFESPOT1.getX() - 1,
                SAFESPOT1.getY(),
                SAFESPOT1.getPlane()
        );
        return playerPos.equals(westOfSafeSpot);
    }

    private void lootItems() {
        if (Rs2Player.getInteracting() == null && !Rs2Player.isInCombat()) {
            RS2Item[] items = Rs2GroundItem.getAllFromWorldPoint(5, SAFESPOT);
            System.out.println("entering loot items");

            // Loot items from the predefined list
            for (RS2Item item : items) {
                if (Rs2Inventory.isFull()) {
                    break;
                }

                for (int lootItem : LOOT_LIST) {
                    if (item.getItem().getId() == lootItem) {
                        Rs2GroundItem.loot(lootItem);
                        sleep(1000, 3000); // Simulate human-like delay
                        break;
                    }
                }

                // Handle bones separately if enabled
                if (config.alchLoot() && !Rs2Inventory.isFull()) {
                    for (int lootItem : ALCHABLES) {
                        if (item.getItem().getId() == lootItem) {
                            Rs2GroundItem.loot(lootItem);
                            sleep(1000, 3000);
                            break;
                        }
                    }
                }

                // Handle bones separately if enabled
                if (config.buryBones() && !Rs2Inventory.isFull()) {
                        if (item.getItem().getId() == BIG_BONES) {
                            Rs2GroundItem.loot(BIG_BONES);
                            sleep(1000, 3000);
                            break;
                        }
                    }
                }
            }
        }

    private boolean desired2093Exists() {
        Stream<Rs2NpcModel> mossGiantsStream = getNpcs(MOSS_GIANT_2093);
        List<Rs2NpcModel> mossGiants = mossGiantsStream.collect(Collectors.toList());

        for (Rs2NpcModel mossGiant : mossGiants) {
            if (SAFE_ZONE_AREA.contains(mossGiant.getWorldLocation())) {
                return true;
            }
        }

        return false;
    }

    private void doBankingLogic() {
        int amuletId = config.rangedAmulet().getItemId();
        int torsoId = config.rangedTorso().getItemId();
        int chapsId = config.rangedChaps().getItemId();
        int capeId = config.cape().getItemId();

        if (config.attackStyle() == AttackStyle.RANGE) {
            if (Rs2Bank.isOpen()) {
                Rs2Bank.depositAll();
                Rs2Bank.closeBank();}

            if (!Rs2Bank.isOpen()) {
                eatAt(100);
                Rs2Equipment.unEquip(AMMO);
                Rs2Bank.walkToBankAndUseBank();
                sleep(2000,4000);}
        }

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.walkToBankAndUseBank();
            sleep(1000);
            return;
        }

        if (config.attackStyle() == AttackStyle.RANGE) {
            if (Rs2Bank.count(APPLE_PIE) < 16 ||
                    Rs2Bank.count(MITHRIL_ARROW) < config.mithrilArrowAmount() ||
                    !Rs2Bank.hasItem(MAPLE_SHORTBOW)) {

                Microbot.log("Missing required items in the bank. Shutting down script.");
                shutdown(); // Stop script
                return;
            }
        }

        if (config.attackStyle() == AttackStyle.MAGIC) {// Check if required consumables exist in the bank with the correct amounts
            if (Rs2Bank.count(APPLE_PIE) < 16 ||
                    Rs2Bank.count(MIND_RUNE) < 750 ||
                    Rs2Bank.count(AIR_RUNE) < 1550 ||
                    !Rs2Bank.hasItem(STAFF_OF_FIRE)) {

                Microbot.log("Missing required consumables in the bank. Shutting down script.");
                shutdown(); // Stop script
                return;
            } }

        // Deposit all items
        Rs2Bank.depositAll();
        sleep(600);

        // Withdraw required consumables
        Rs2Bank.withdrawX(APPLE_PIE, 16);
        sleep(300);
        if (config.attackStyle() == AttackStyle.MAGIC) {
            Rs2Bank.withdrawX(MIND_RUNE, 750);
            sleep(300);
            Rs2Bank.withdrawX(AIR_RUNE, 1550);
            sleep(300);
            Rs2Bank.withdrawX(LAW_RUNE, 6);
            sleep(300);

            // Check if equipped with necessary items
            if (!isEquippedWithRequiredItems()) {
                // Ensure all required equipment is in the bank before proceeding
                if (!Rs2Bank.hasItem(AMULET_OF_MAGIC) ||
                        !Rs2Bank.hasItem(STAFF_OF_FIRE) ||
                        !Rs2Bank.hasItem(capeId)) {

                    Microbot.log("Missing required equipment in the bank. Shutting down script.");
                    shutdown();
                    return;
                }

                if (!Rs2Equipment.isNaked()) {
                    Rs2Bank.depositEquipment();
                    sleep(400,900);
                }

                OutfitHelper.equipOutfit(OutfitHelper.OutfitType.NAKED_MAGE);
                Rs2Bank.withdrawOne(STAFF_OF_FIRE);
                sleep(400,800);
                Rs2Inventory.equip(STAFF_OF_FIRE);
                sleep(400,800);
                Rs2Bank.withdrawAndEquip(capeId);
            }
        }

        if (config.attackStyle() == AttackStyle.RANGE) {
            Rs2Bank.withdrawX(MITHRIL_ARROW, config.mithrilArrowAmount());
            sleep(400,800);
            Rs2Inventory.equip(MITHRIL_ARROW);
            sleep(300);
            Rs2Bank.withdrawX(AIR_RUNE, 30);
            sleep(300);
            Rs2Bank.withdrawX(LAW_RUNE, 6);
            sleep(300);
            Rs2Bank.withdrawX(FIRE_RUNE, 4);
            sleep(300);


            if (!isEquippedWithRequiredItemsRange()) {
                if (!Rs2Equipment.isNaked()) {
                    Rs2Bank.depositEquipment();
                    sleep(400,900);
                }

                int[] equipItems = {
                        amuletId,
                        torsoId,
                        chapsId,
                        capeId,
                        MAPLE_SHORTBOW,
                        LEATHER_VAMBRACES,
                        LEATHER_BOOTS
                };

                for (int itemId : equipItems) {
                    Rs2Bank.withdrawAndEquip(itemId);
                    sleepUntil(() -> Rs2Equipment.isWearing(itemId), 5000);
                }
            }
        }

        Rs2Bank.closeBank();
    }


    private boolean isEquippedWithRequiredItems() {
        int capeId = config.cape().getItemId();
        // Check if player is wearing the required items
        return Rs2Equipment.hasEquipped(AMULET_OF_MAGIC)
                && Rs2Equipment.hasEquipped(STAFF_OF_FIRE)
                && Rs2Equipment.hasEquipped(capeId)
                && Rs2Equipment.hasEquipped(LEATHER_BOOTS)
                && Rs2Equipment.hasEquipped(LEATHER_VAMBRACES);
    }


    private boolean isEquippedWithRequiredItemsRange() {
        int amuletId = config.rangedAmulet().getItemId();
        int torsoId = config.rangedTorso().getItemId();
        int chapsId = config.rangedChaps().getItemId();
        int capeId = config.cape().getItemId();
        return Rs2Equipment.hasEquipped(amuletId)
                && Rs2Equipment.hasEquipped(chapsId)
                && Rs2Equipment.hasEquipped(capeId)
                && Rs2Equipment.hasEquipped(torsoId)
                && Rs2Equipment.hasEquipped(LEATHER_BOOTS)
                && Rs2Equipment.hasEquipped(LEATHER_VAMBRACES);
    }

    private boolean isInventoryPrepared() {
        return Rs2Inventory.hasItemAmount(MIND_RUNE, 15) &&
                Rs2Inventory.hasItemAmount(AIR_RUNE, 30) &&
                Rs2Inventory.hasItemAmount(APPLE_PIE, 1);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        move = false;
        iveMoved = false;
    }
}

