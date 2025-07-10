/**
 * Credits to Jrod7938
 */

package net.runelite.client.plugins.microbot.vorkath;

import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.loottracker.LootTrackerRecord;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;


enum State {
    BANKING,
    TELEPORT_TO_RELLEKKA,
    WALK_TO_VORKATH_ISLAND,
    WALK_TO_VORKATH,
    PREPARE_FIGHT,
    FIGHT_VORKATH,
    ZOMBIE_SPAWN,
    ACID,
    LOOT_ITEMS,
    TELEPORT_AWAY,
    DEAD_WALK,
    SELLING_ITEMS
}

public class VorkathScript extends Script {
    public static String version = "1.3.9";
    private final VorkathConfig config;
    @Getter
    public final int acidProjectileId = 1483;
    final String ZOMBIFIED_SPAWN = "Zombified Spawn";
    private final int whiteProjectileId = 395;
    private final int redProjectileId = 1481;
    private final int acidRedProjectileId = 1482;
    @Getter
    private final HashSet<WorldPoint> acidPools = new HashSet<>();
    public int vorkathSessionKills = 0;
    public int tempVorkathKills = 0;
    public int kcPerTrip = 0;
    State state = State.ZOMBIE_SPAWN;
    Rs2NpcModel vorkath;
    boolean hasEquipment = false;
    boolean hasInventory = false;
    boolean init = true;
    String primaryBolts = "";
    Rs2InventorySetup rs2InventorySetup;

    private static void walkToCenter() {
        Rs2Walker.walkFastLocal(
                LocalPoint.fromScene(48, 58, Microbot.getClient().getTopLevelWorldView().getScene())
        );
    }

    private static void drinkPrayer() {
        if ((Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / Microbot.getClient().getRealSkillLevel(Skill.PRAYER) < Rs2Random.between(25, 30)) {
            Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new), "drink");
        }
    }

    private void calculateState() {
        if (Rs2Npc.getNpc(NpcID.VORKATH) != null) {
            state = State.FIGHT_VORKATH;
            return;
        }
        if (Rs2Npc.getNpc(NpcID.VORKATH_SLEEPING) != null) {
            state = State.PREPARE_FIGHT;
            return;
        }
        if (Rs2GameObject.findObjectById(ObjectID.UNGAEL_CRATER_ENTRANCE) != null) {
            state = State.WALK_TO_VORKATH;
            return;
        }
        if (isCloseToRelleka()) {
            state = State.WALK_TO_VORKATH_ISLAND;
            return;
        }
        if (Rs2Npc.getNpc(NpcID.TORFINN_COLLECT_UNGAEL) != null) {
            state = State.WALK_TO_VORKATH;
        }
    }

	@Inject
	public VorkathScript(VorkathConfig config)
	{
		this.config = config;
	}

    public boolean run() {
        Microbot.enableAutoRunOn = false;
		Microbot.pauseAllScripts.compareAndSet(true, false);
        init = true;
        state = State.BANKING;
        hasEquipment = false;
        hasInventory = false;
        tempVorkathKills = config.SellItemsAtXKills();
        Microbot.getSpecialAttackConfigs().setSpecialAttack(true);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.naturalMouse) {
                    Rs2AntibanSettings.naturalMouse = false;
                    log("Woox walk is not compatible with natural mouse.");
                }

                if (init) {
                    rs2InventorySetup = new Rs2InventorySetup("vorkath", mainScheduledFuture);
                    if (!rs2InventorySetup.hasSpellBook()) {
                        Microbot.showMessage("Your spellbook is not matching the inventory setup.");
                        sleep(10000);
                        return;
                    }
                    calculateState();
                    primaryBolts = Rs2Equipment.get(EquipmentInventorySlot.AMMO) != null ? Rs2Equipment.get(EquipmentInventorySlot.AMMO).getName() : "";
                }

                if (state == State.FIGHT_VORKATH  && Rs2Equipment.get(EquipmentInventorySlot.AMMO) == null) {
                    leaveVorkath();
                }

                switch (state) {
                    case BANKING:
                        kcPerTrip = 0;
                        if (checkSellingItems(config)) return;
                        if (!init && Rs2Equipment.get(EquipmentInventorySlot.AMMO) == null) {
                            Microbot.showMessage("Out of ammo!");
                            sleep(5000);
                            return;
                        }
                        if (isCloseToRelleka() && Rs2Inventory.count() >= 27) {
                            state = State.WALK_TO_VORKATH_ISLAND;
                        }
                        hasEquipment = rs2InventorySetup.doesEquipmentMatch();
                        hasInventory = rs2InventorySetup.doesInventoryMatch();
                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.walkToBankAndUseBank();
                        }
                        if (!hasEquipment) {
                            hasEquipment = rs2InventorySetup.loadEquipment();
                        }
                        if (!hasInventory && rs2InventorySetup.doesEquipmentMatch()) {
                            hasInventory = rs2InventorySetup.loadInventory();
                            sleep(1000);
                        }
                        if (hasEquipment && hasInventory) {
                            healAndDrinkPrayerPotion();
                            if (hasEquipment && hasInventory) {
                                state = State.TELEPORT_TO_RELLEKKA;
                            }
                        }
                        break;
                    case TELEPORT_TO_RELLEKKA:
                        if (!config.pohInRellekka() && !Rs2Inventory.hasItem("Rellekka teleport")) {
                            state = State.BANKING;
                            return;
                        }
                        if(config.pohInRellekka() && (!Rs2Magic.hasRequiredRunes(Rs2Spells.TELEPORT_TO_HOUSE) && !Rs2Inventory.hasItem("Teleport to house"))){
                            state = State.BANKING;
                            return;
                        }
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                        }
                        if (!isCloseToRelleka()) {

                            if(config.pohInRellekka()) {
                            teleToPoh();
                            sleepUntil(() -> Rs2GameObject.findObjectById(4525) != null);
                            Rs2GameObject.interact(4525, "Enter");
                            sleepUntil(this::isCloseToRelleka);

                            }
                            else
                            {
                            Rs2Inventory.interact("Rellekka teleport", "break");
                            sleepUntil(this::isCloseToRelleka);
                            }
                        }
                        if (isCloseToRelleka()) {
                            state = State.WALK_TO_VORKATH_ISLAND;
                        }
                        break;
                    case WALK_TO_VORKATH_ISLAND:
                        Rs2Player.toggleRunEnergy(true);
                        Rs2Walker.walkTo(new WorldPoint(2640, 3693, 0));
                        var torfin = Rs2Npc.getNpc(NpcID.TORFINN_COLLECT_RELLEKKA);
                        if (torfin != null) {
                            Rs2Npc.interact(torfin, "Ungael");
                            sleepUntil(() -> Rs2Npc.getNpc(NpcID.TORFINN_COLLECT_UNGAEL) != null);
                        }
                        if (Rs2Npc.getNpc(NpcID.TORFINN_COLLECT_UNGAEL) != null) {
                            state = State.WALK_TO_VORKATH;
                        }
                        break;
                    case WALK_TO_VORKATH:
                        kcPerTrip = 0;
                        Rs2Walker.walkTo(new WorldPoint(2272, 4052, 0));
                        TileObject iceChunks = Rs2GameObject.findObjectById(ObjectID.UNGAEL_CRATER_ENTRANCE);
                        if (iceChunks != null) {
                            Rs2GameObject.interact(ObjectID.UNGAEL_CRATER_ENTRANCE, "Climb-over");
                            sleepUntil(() -> Rs2GameObject.findObjectById(ObjectID.UNGAEL_CRATER_ENTRANCE) == null);
                        }
                        if (Rs2GameObject.findObjectById(ObjectID.UNGAEL_CRATER_ENTRANCE) == null) {
                            state = State.PREPARE_FIGHT;
                        }
                        break;
                    case PREPARE_FIGHT:
                        Rs2Player.toggleRunEnergy(false);

                        boolean result = drinkPotions();

                        if (result) {
                            Rs2Npc.interact(NpcID.VORKATH_SLEEPING, "Poke");
                            Rs2Player.waitForWalking();
                            Rs2Npc.interact(NpcID.VORKATH_SLEEPING, "Poke");
                            Rs2Player.waitForAnimation(1000);
                            walkToCenter();
                            Rs2Player.waitForWalking();
                            handlePrayer();
                            sleepUntil(() -> Rs2Npc.getNpc(NpcID.VORKATH) != null);
                            if (doesProjectileExistById(redProjectileId)) {
                                handleRedBall();
                                sleep(300);
                            }
                            state = State.FIGHT_VORKATH;
                        }
                        break;
                    case FIGHT_VORKATH:
                        vorkath = Rs2Npc.getNpc(NpcID.VORKATH);
                        if (vorkath == null || vorkath.isDead()) {
                            vorkathSessionKills++;
                            tempVorkathKills--;
                            kcPerTrip++;
                            state = State.LOOT_ITEMS;
                            sleep(300, 600);
                            Rs2Inventory.wield(primaryBolts);
                            togglePrayer(false);
                            sleepUntil(() -> Rs2GroundItem.exists("Superior dragon bones", 20), 15000);
                            return;
                        }
                        if (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) <= 0) {
                            state = State.DEAD_WALK;
                            return;
                        }
                        if (config.KillsPerTrip() > 0 && kcPerTrip>= config.KillsPerTrip()) {
                            leaveVorkath();
                            return;
                        }
                        if (Rs2Inventory.getInventoryFood().isEmpty()) {
                            double treshHold = (double) (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100) / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
                            if (treshHold < 50) {
                                leaveVorkath();
                                return;
                            }
                        }

                        if (Rs2Npc.attack(vorkath))
                            sleep(600);
                        if (Microbot.getClient().getLocalPlayer().getLocalLocation().getSceneY() >= 59) {
                            walkToCenter();
                        }
                        drinkPotions();
                        handlePrayer();
                        Rs2Player.eatAt(75);
                        handleRedBall();
                        if (doesProjectileExistById(whiteProjectileId)) {
                            state = State.ZOMBIE_SPAWN;
                            walkToCenter();
                            Rs2Tab.switchToMagicTab();
                        }
                        if ((doesProjectileExistById(acidProjectileId) || doesProjectileExistById(acidRedProjectileId))) {
                            state = State.ACID;
                        }
                        if (vorkath.getHealthRatio() < 60 && vorkath.getHealthRatio() != -1) {
                            Rs2Inventory.wield("diamond bolts (e)", "diamond dragon bolts (e)");
                        } else if (vorkath.getHealthRatio() >= 60 && !Rs2Equipment.isWearing(primaryBolts)) {
                            Rs2Inventory.wield(primaryBolts);
                        }
                        break;
                    case ZOMBIE_SPAWN:
                        if (Rs2Npc.getNpc(NpcID.VORKATH) == null) {
                            state = State.FIGHT_VORKATH;
                        }
                        togglePrayer(false);
                        Rs2Player.eatAt(80);
                        drinkPrayer();
                        NPC zombieSpawn = Rs2Npc.getNpc(ZOMBIFIED_SPAWN);
                        if (zombieSpawn != null) {
                            while (Rs2Npc.getNpc(ZOMBIFIED_SPAWN) != null && !Rs2Npc.getNpc(ZOMBIFIED_SPAWN).isDead()
                                    && !doesProjectileExistById(146)) {
                                Rs2Magic.castOn(MagicAction.CRUMBLE_UNDEAD, zombieSpawn);
                                sleep(600);
                            }
                            Rs2Player.eatAt(75);
                            togglePrayer(true);
                            Rs2Tab.switchToInventoryTab();
                            state = State.FIGHT_VORKATH;
                            sleepUntil(() -> Rs2Npc.getNpc("Zombified Spawn") == null);
                            if (doesProjectileExistById(redProjectileId)) {
                                handleRedBall();
                                sleep(300);
                            }

                        }
                        break;
                    case ACID:
                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
                        handleAcidWalk();
                        break;
                    case LOOT_ITEMS:
                        if (Rs2Inventory.isFull()) {
                            boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
                            if (hasFood) {
                                Rs2Player.eatAt(100);
                                Rs2Player.waitForAnimation();
                            } else {
                                state = State.PREPARE_FIGHT;
                            }
                        }
                        togglePrayer(false);
                        LootingParameters valueParams = new LootingParameters(
                                config.priceOfItemsToLoot(),
                                Integer.MAX_VALUE,
                                20,
                                1,
                                0,
                                false,
                                false
                        );

                        Rs2GroundItem.loot("Vorkath's head", 20);
                        Rs2GroundItem.lootItemBasedOnValue(valueParams);
                        int foodInventorySize = Rs2Inventory.getInventoryFood().size();
                        boolean hasVenom = Rs2Inventory.hasItem("venom");
                        boolean hasSuperAntifire = Rs2Inventory.hasItem("super antifire");
                        boolean hasPrayerPotion = Rs2Inventory.hasItem(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new));
                        boolean hasRangePotion = Rs2Inventory.hasItem(Rs2Potion.getRangePotionsVariants().toArray(String[]::new));
                        sleep(600, 2000);
                        if (!Rs2GroundItem.isItemBasedOnValueOnGround(config.priceOfItemsToLoot(), 20) && !Rs2GroundItem.exists("Vorkath's head", 20)) {
                            if (config.KillsPerTrip() > 0 && kcPerTrip >= config.KillsPerTrip()){
                                leaveVorkath();
                            }
                            if (foodInventorySize < 3 || !hasVenom || !hasSuperAntifire || !hasRangePotion || (!hasPrayerPotion && !Rs2Player.hasPrayerPoints())) {
                                leaveVorkath();
                            } else {
                                calculateState();
                            }

                        }
                        break;
                    case TELEPORT_AWAY:
                        togglePrayer(false);
                        Rs2Player.toggleRunEnergy(true);
                        Rs2Inventory.wield(primaryBolts);
                        boolean reachedDestination = Rs2Bank.walkToBank();
                        if (reachedDestination) {
                            healAndDrinkPrayerPotion();
                            state = State.BANKING;
                        }
                        break;
                    case DEAD_WALK:
                        if (isCloseToRelleka()) {
                            Rs2Walker.walkTo(new WorldPoint(2640, 3693, 0));
                            torfin = Rs2Npc.getNpc(NpcID.TORFINN_COLLECT_RELLEKKA);
                            if (torfin != null) {
                                Rs2Npc.interact(torfin, "Collect");
                                sleepUntil(() -> Rs2Widget.hasWidget("Retrieval Service"), 1500);
                                if (Rs2Widget.hasWidget("I'm afraid I don't have anything")) { // this means we looted all our stuff
                                    leaveVorkath();
                                    return;
                                }
                                final int invSize = Rs2Inventory.count();
                                Rs2Widget.clickWidget(39452678);
                                sleep(600);
                                Rs2Widget.clickWidget(39452678);
                                sleepUntil(() -> Rs2Inventory.count() != invSize);
                                boolean isWearingOriginalEquipment = rs2InventorySetup.wearEquipment();
                                if (!isWearingOriginalEquipment) {
                                    int finalInvSize = Rs2Inventory.count();
                                    Rs2Widget.clickWidget(39452678);
                                    sleepUntil(() -> Rs2Inventory.count() != finalInvSize);
                                    rs2InventorySetup.wearEquipment();
                                }
                            }
                        } else {
                            togglePrayer(false);
                            if (Rs2Inventory.hasItem("Rellekka teleport")) {
                                Rs2Inventory.interact("Rellekka teleport", "break");
                                Rs2Player.waitForAnimation();
                                return;
                            }
                            Rs2Bank.walkToBank();
                            Rs2Bank.openBank();
                            Rs2Bank.withdrawItem("Rellekka teleport");
                            sleep(150, 400);
                            Rs2Bank.closeBank();
                            sleepUntil(() -> Rs2Inventory.hasItem("Rellekka teleport"), 1000);
                        }
                        break;
                    case SELLING_ITEMS:
                        boolean soldAllItems = Rs2GrandExchange.sellLoot("vorkath", Arrays.stream(config.ItemsToNotSell().split(",")).collect(Collectors.toList()));
                        if (soldAllItems) {
                            state = State.BANKING;
                        }
                        break;
                }

                init = false;

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 80, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Checks if we have killed x amount of vorkaths based on a config to sell items
     * @param config
     * @return true if we need to sell items
     */
    private boolean checkSellingItems(VorkathConfig config) {
        if (tempVorkathKills > 0) return false;
        LootTrackerRecord lootRecord = Microbot.getAggregateLootRecords("vorkath");
        if (lootRecord != null) {
            if (tempVorkathKills % config.SellItemsAtXKills() == 0) {
                state = State.SELLING_ITEMS;
                tempVorkathKills = config.SellItemsAtXKills();
                return true;
            }
        }
        return false;
    }

    /**
     * will heal and drink pray pots
     */
    private void healAndDrinkPrayerPotion() {
        while (!Rs2Player.isFullHealth() && !Rs2Inventory.getInventoryFood().isEmpty()) {
            Rs2Bank.closeBank();
            Rs2Player.eatAt(99);
            Rs2Player.waitForAnimation();
            hasInventory = false;
        }
        while (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) != Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) && Rs2Inventory.hasItem(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new))) {
            Rs2Bank.closeBank();
            Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new), "drink");
            Rs2Player.waitForAnimation();
            hasInventory = false;
        }
    }

    private void leaveVorkath() {
            kcPerTrip = 0;
            togglePrayer(false);
            Rs2Player.toggleRunEnergy(true);
            switch(config.teleportMode()) {
                case VARROCK_TAB:
                    Rs2Inventory.interact(config.teleportMode().getItemName(), config.teleportMode().getAction());
                    break;
                case CRAFTING_CAPE:
                    if (Rs2Equipment.isWearing("crafting cape")) {
                        Rs2Equipment.interact("crafting cape", "teleport");
                    } else {
                        Rs2Inventory.interact("crafting cape", "teleport");
                    }
                    break;
                case JEWELLERY_BOX:
                    teleToPoh();
                    if(config.rejuvinationPool()){
                        Rs2GameObject.interact(29241, "Drink");
                        sleepUntil(() -> Rs2Player.isFullHealth());
                    }
                    Rs2GameObject.interact(29156, "Teleport Menu");
                    sleepUntil(()->Rs2Widget.hasWidget("Castle Wars"));
                    Rs2Widget.clickWidget("Castle Wars");
                    break;
            }
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Microbot.getClient().isInInstancedRegion());
            state = State.TELEPORT_AWAY;

    }

    private boolean drinkPotions() {
        if (Rs2Player.isAnimating()) return false;
        boolean drinkRangePotion = !Rs2Player.hasDivineBastionActive() && !Rs2Player.hasDivineRangedActive() && !Rs2Player.hasRangingPotionActive(5);
        boolean drinkAntiFire = !Rs2Player.hasAntiFireActive() && !Rs2Player.hasSuperAntiFireActive();
        boolean drinkAntiVenom = !Rs2Player.hasAntiVenomActive();

        if (drinkRangePotion) {
            Rs2Inventory.interact(Rs2Potion.getRangePotionsVariants().toArray(String[]::new), "drink");
        }
        if (drinkAntiFire) {
            Rs2Inventory.interact("super antifire", "drink");
        }
        if (drinkAntiVenom) {
            Rs2Inventory.interact("venom", "drink");
        }

        if (!Microbot.getClient().getLocalPlayer().isInteracting() && state == State.PREPARE_FIGHT && (drinkRangePotion || drinkAntiFire || drinkAntiVenom))
            Rs2Player.waitForAnimation();

        return !drinkRangePotion && !drinkAntiFire && !drinkAntiVenom;
    }

    public void togglePrayer(boolean onOff) {
        if (Rs2Prayer.isOutOfPrayer()) return;
        if (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 74 && Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) >= 70 && config.activateRigour()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, onOff);
        } else {
            Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, onOff);
        }
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, onOff);
    }

    private void handleRedBall() {
        if (doesProjectileExistById(redProjectileId)) {
            redBallWalk();
            Rs2Npc.interact("Vorkath", "attack");
        }
    }

    private void handlePrayer() {
        drinkPrayer();
        togglePrayer(true);
    }

    private boolean doesProjectileExistById(int id) {
        for (Projectile projectile : Microbot.getClient().getProjectiles()) {
            if (projectile.getId() == id) {
                //println("Projectile $id found")
                return true;
            }
        }
        return false;
    }

    private boolean isCloseToRelleka() {
        if (Microbot.getClient().getLocalPlayer() == null) return false;
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2670, 3634, 0)) < 80;
    }

    private boolean teleToPoh(){
        if(Rs2Magic.canCast(MagicAction.TELEPORT_TO_HOUSE)){
            Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
            sleepUntil(()->Rs2GameObject.findObjectById(4525) != null);
            return true;
        }else
        if(Rs2Inventory.hasItem("Teleport to house")){
            Rs2Inventory.interact("Teleport to house", "break");
            sleepUntil(()->Rs2GameObject.findObjectById(4525) != null);
            return true;
    }
    return false;
}

    private void redBallWalk() {
        WorldPoint currentPlayerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        WorldPoint sideStepLocation = new WorldPoint(currentPlayerLocation.getX() + 2, currentPlayerLocation.getY(), 0);
        if (Rs2Random.between(0, 2) == 1) {
            sideStepLocation = new WorldPoint(currentPlayerLocation.getX() - 2, currentPlayerLocation.getY(), 0);
        }
        final WorldPoint _sideStepLocation = sideStepLocation;
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), _sideStepLocation));
        Rs2Player.waitForWalking();
        sleepUntil(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().equals(_sideStepLocation));
    }

    WorldPoint findSafeTile() {
        WorldPoint swPoint = new WorldPoint(vorkath.getWorldLocation().getX() + 1, vorkath.getWorldLocation().getY() - 8, 0);
        WorldArea wooxWalkArea = new WorldArea(swPoint, 5, 1);

        List<WorldPoint> safeTiles = wooxWalkArea.toWorldPointList().stream().filter(this::isTileSafe).collect(Collectors.toList());

        // Find the closest safe tile by x-coordinate to the player
        return safeTiles.stream().min(Comparator.comparingInt(tile -> Math.abs(tile.getX() - Microbot.getClient().getLocalPlayer().getWorldLocation().getX()))).orElse(null);
    }


    boolean isTileSafe(WorldPoint tile) {
        return !acidPools.contains(tile)
                && !acidPools.contains(new WorldPoint(tile.getX(), tile.getY() + 1, tile.getPlane()));

    }

    private void handleAcidWalk() {
        if (!doesProjectileExistById(acidProjectileId) && !doesProjectileExistById(acidRedProjectileId) && Rs2GameObject.getGameObjects(obj -> obj.getId() == ObjectID.VORKATH_ACID).isEmpty()) {
            Rs2Npc.interact(vorkath, "attack");
            state = State.FIGHT_VORKATH;
            acidPools.clear();
            return;
        }

        Rs2GameObject.getGameObjects(obj -> obj.getId() == ObjectID.VORKATH_ACID).forEach(tileObject -> acidPools.add(tileObject.getWorldLocation()));
        Rs2GameObject.getGameObjects(obj -> obj.getId() == ObjectID.OLM_ACID_POOL).forEach(tileObject -> acidPools.add(tileObject.getWorldLocation()));
        Rs2GameObject.getGameObjects(obj -> obj.getId() == ObjectID.MYQ5_ACID_POOL).forEach(tileObject -> acidPools.add(tileObject.getWorldLocation()));

        WorldPoint safeTile = findSafeTile();
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

        if (safeTile != null) {
            if (playerLocation.equals(safeTile)) {
                Rs2Npc.interact(vorkath, "attack");
            } else {
                Rs2Player.eatAt(60);
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), safeTile));
            }
        }
    }
    //Only use this for testing purpose on sleeping vorkath
    private void testWooxWalk() {
        vorkath = Rs2Npc.getNpc(NpcID.VORKATH_SLEEPING);
        WorldPoint safeTile = findSafeTile();
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

        if (safeTile != null) {
            if (playerLocation.equals(safeTile)) {
                Rs2Npc.interact(vorkath, "attack");
            } else {
                Rs2Player.eatAt(60);
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), safeTile));
            }
        }
    }
}