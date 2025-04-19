package net.runelite.client.plugins.microbot.nmz;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.combat.PrayerPotionScript;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.api.ObjectID.OVERLOAD_POTION;
import static net.runelite.api.Varbits.NMZ_ABSORPTION;

public class NmzScript extends Script {

    public static double version = 2.2;

    private NmzConfig config;
    private NmzPlugin plugin;

    public static boolean useOverload = false;

    public static PrayerPotionScript prayerPotionScript;

    public static int maxHealth = Rs2Random.between(2, 8);
    public static int minAbsorption = Rs2Random.between(100, 300);

    private WorldPoint center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);

    @Getter
    @Setter
    private static boolean hasSurge = false;
    private boolean initialized = false;
    private long lastCombatTime = 0;

    public boolean canStartNmz() {
        return Rs2Inventory.count("overload (4)") == config.overloadPotionAmount() ||
                (Rs2Inventory.hasItem("prayer potion") && config.togglePrayerPotions());
    }

    @Inject
    public NmzScript(NmzPlugin plugin, NmzConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    public boolean run() {
        prayerPotionScript = new PrayerPotionScript();
        Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!initialized) {
                    initialized = true;
                    if (config.inventorySetup() != null) {
                        var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                        if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                            Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                            if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                                Microbot.log("Failed to load inventory setup");
                                Microbot.stopPlugin(plugin);
                                return;
                            }
                            Rs2Bank.closeBank();
                        }
                    }
                    Rs2Walker.walkTo(new WorldPoint(2609, 3114, 0), 5);
                }
                if (!super.run()) return;
                Rs2Combat.enableAutoRetialiate();
                if (Rs2Random.between(1, 50) == 1 && config.randomMouseMovements()) {
                    Microbot.getMouse().click(Rs2Random.between(0, Microbot.getClient().getCanvasWidth()), Rs2Random.between(0, Microbot.getClient().getCanvasHeight()), true);
                }
                boolean isOutsideNmz = isOutside();
                useOverload = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) == Microbot.getClient().getRealSkillLevel(Skill.RANGED) && config.overloadPotionAmount() > 0;
                if (isOutsideNmz) {
                    Rs2Walker.setTarget(null);
                    handleOutsideNmz();
                } else {
                    handleInsideNmz();
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        initialized = false;
    }

    public boolean isOutside() {
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2602, 3116, 0)) < 20;
    }

    public void handleOutsideNmz() {
        boolean hasStartedDream = Microbot.getVarbitValue(3946) > 0;
        if (config.togglePrayerPotions())
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        if (!hasStartedDream) {
            startNmzDream();
        } else {
            final String overload = "Overload (4)";
            final String absorption = "Absorption (4)";
            storePotions(OVERLOAD_POTION, "overload", config.overloadPotionAmount());
            storePotions(ObjectID.ABSORPTION_POTION, "absorption", config.absorptionPotionAmount());
            handleStore();
            fetchOverloadPotions(OVERLOAD_POTION, overload, config.overloadPotionAmount());
            if (Rs2Inventory.hasItemAmount(overload, config.overloadPotionAmount())) {
                fetchPotions(ObjectID.ABSORPTION_POTION, absorption, config.absorptionPotionAmount());
            }
        }
        if (canStartNmz()) {
            consumeEmptyVial();
        } else {
            sleep(2000);
        }
    }

    public void handleInsideNmz() {
        if (Rs2Player.isInCombat()) {
            lastCombatTime = System.currentTimeMillis();
        }
        if (!Rs2Player.isInCombat() && System.currentTimeMillis() - lastCombatTime > 20000) {
            Rs2NpcModel closestNpc = Rs2Npc.getNearestNpcWithAction("Attack");

            if (closestNpc != null) {
                Rs2Npc.interact(closestNpc, "Attack");
            }
        }
        prayerPotionScript.run();
        if (config.togglePrayerPotions())
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        if (!useOrbs() && config.walkToCenter()) {
            walkToCenter();
        }
        useOverloadPotion();
        manageSelfHarm();
        useAbsorptionPotion();
    }

    private void walkToCenter() {
        if (center.distanceTo(Rs2Player.getWorldLocation()) > 4) {
            Rs2Walker.walkTo(center, 6);
        }
    }

    public void startNmzDream() {
        // Set new center so that it is random for every time joining the dream
        center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);
        Rs2Npc.interact(NpcID.DOMINIC_ONION, "Dream");
        sleepUntil(() -> Rs2Widget.hasWidget("Which dream would you like to experience?"));
        Rs2Widget.clickWidget("Previous:");
        sleepUntil(() -> Rs2Widget.hasWidget("Click here to continue"));
        Rs2Widget.clickWidget("Click here to continue");
        sleepUntil(() -> Rs2Widget.hasWidget("Agree to pay"));
        if (Rs2Widget.hasWidget("Agree to pay")) {
            Rs2Keyboard.typeString("1");
            Rs2Keyboard.enter();
        }
    }

    public boolean useOrbs() {
        boolean orbHasSpawned = false;
        if (config.useZapper()) {
            orbHasSpawned = interactWithObject(ObjectID.ZAPPER_26256);
        }
        if (config.useReccurentDamage()) {
            orbHasSpawned = interactWithObject(ObjectID.RECURRENT_DAMAGE);
        }

        if (config.usePowerSurge()) {
            orbHasSpawned = interactWithObject(ObjectID.POWER_SURGE);
        }

        return orbHasSpawned;
    }

    public boolean interactWithObject(int objectId) {
        TileObject rs2GameObject = Rs2GameObject.findObjectById(objectId);
        if (rs2GameObject != null) {
            Rs2Walker.walkFastLocal(rs2GameObject.getLocalLocation());
            sleepUntil(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(rs2GameObject.getWorldLocation()) < 5);
            Rs2GameObject.interact(objectId);
            return true;
        }
        return false;
    }

    private void fetchOverloadPotions(int objectId, String itemName, int requiredAmount) {
        int currentAmount = Rs2Inventory.count(itemName);

        if (currentAmount == requiredAmount) return;

        int neededAmount = requiredAmount - currentAmount;

        Rs2GameObject.interact(objectId, "Take");
        String widgetText = "How many doses of ";
        sleepUntil(() -> Rs2Widget.hasWidget(widgetText));

        if (Rs2Widget.hasWidget(widgetText)) {
            // Each potion has 4 doses, so request the correct number of doses
            Rs2Keyboard.typeString(Integer.toString(neededAmount * 4));
            Rs2Keyboard.enter();
            sleepUntil(() -> Rs2Inventory.count(itemName) == requiredAmount);
        }
    }


    public void manageSelfHarm() {
        int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentRangedLevel = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED);
        int realRangedLevel = Microbot.getClient().getRealSkillLevel(Skill.RANGED);
        boolean hasOverloadPotions = config.overloadPotionAmount() > 0;

        if (currentHP >= maxHealth
                && !useOverload
                && (!hasOverloadPotions || currentRangedLevel != realRangedLevel)) {
            maxHealth = 1;

            if (Rs2Inventory.hasItem(ItemID.LOCATOR_ORB)) {
                Rs2Inventory.interact(ItemID.LOCATOR_ORB, "feel");
            } else if (Rs2Inventory.hasItem(ItemID.DWARVEN_ROCK_CAKE_7510)) {
                Rs2Inventory.interact(ItemID.DWARVEN_ROCK_CAKE_7510, "guzzle");
            }

            if (currentHP == 1) {
                maxHealth = Rs2Random.between(2, 4);
            }
        }

        if (config.randomlyTriggerRapidHeal()) {
            randomlyToggleRapidHeal();
        }
    }

    public void randomlyToggleRapidHeal() {
        if (Rs2Random.between(1, 50) == 2) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, true);
            sleep(300, 600);
            Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, false);
        }
    }

    public void useOverloadPotion() {
        if (useOverload && Rs2Inventory.hasItem("overload") && Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) > 50) {
            Rs2Inventory.interact(x -> x.name.toLowerCase().contains("overload"), "drink");
            sleep(10000);
        }
    }

    public void useAbsorptionPotion() {
        if (Microbot.getVarbitValue(NMZ_ABSORPTION) < minAbsorption && Rs2Inventory.hasItem("absorption")) {
            for (int i = 0; i < Rs2Random.between(4, 8); i++) {
                Rs2Inventory.interact(x -> x.name.toLowerCase().contains("absorption"), "drink");
                sleep(600, 1000);
            }
            minAbsorption = Rs2Random.between(100, 300);
        }
    }

    private void storePotions(int objectId, String itemName, int requiredAmount) {
        if (Rs2Inventory.count(itemName) == requiredAmount) return;
        if (Rs2Inventory.get(itemName) == null) return;

        Rs2GameObject.interact(objectId, "Store");
        String storeWidgetText = "Store all your ";
        sleepUntil(() -> Rs2Widget.hasWidget(storeWidgetText));
        if (Rs2Widget.hasWidget(storeWidgetText)) {
            Rs2Keyboard.typeString("1");
            Rs2Keyboard.enter();
            sleepUntil(() -> !Rs2Inventory.hasItem(objectId));
            Rs2Inventory.dropAll(itemName);
        }
    }

    private void fetchPotions(int objectId, String itemName, int requiredAmount) {
        if (Rs2Inventory.count(itemName) == requiredAmount) return;

        Rs2GameObject.interact(objectId, "Take");
        String widgetText = "How many doses of ";
        sleepUntil(() -> Rs2Widget.hasWidget(widgetText));
        if (Rs2Widget.hasWidget(widgetText)) {
            Rs2Keyboard.typeString(Integer.toString(requiredAmount * 4));
            Rs2Keyboard.enter();
            sleepUntil(() -> Rs2Inventory.count(itemName) == requiredAmount);
        }
    }

    public void consumeEmptyVial() {
        final int EMPTY_VIAL = 26291;
        if (Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2Widget.getWidget(129, 6) == null || Rs2Widget.getWidget(129, 6).isHidden())
                .orElse(false)) {
            Rs2GameObject.interact(EMPTY_VIAL, "drink");
        }
        sleep(2000,4000);
        Widget widget = Rs2Widget.getWidget(129, 6);
        if (!Microbot.getClientThread().runOnClientThreadOptional(widget::isHidden).orElse(false)) {
            Rs2Widget.clickWidget(widget.getId());
            sleep(300);
            Rs2Widget.clickWidget(widget.getId());
        }
        sleep(2000,4000);
    }

    public void handleStore() {
        if (canStartNmz()) return;
        int varbitOverload = 3953;
        int varbitAbsorption = 3954;
        int overloadAmt = Microbot.getVarbitValue(varbitOverload);
        int absorptionAmt = Microbot.getVarbitValue(varbitAbsorption);
        int nmzPoints = Microbot.getVarbitPlayerValue(VarPlayer.NMZ_REWARD_POINTS);

        if (absorptionAmt > config.absorptionPotionAmount() * 4 && overloadAmt > config.overloadPotionAmount() * 4)
            return;

        if (!Rs2Inventory.isFull()) {
            if ((absorptionAmt < (config.absorptionPotionAmount() * 4) || overloadAmt < config.overloadPotionAmount() * 4) && nmzPoints < 100000) {
                Microbot.showMessage("BOT SHUTDOWN: Not enough points to buy potions");
                Microbot.stopPlugin(plugin);
                return;
            }
        }

        Rs2GameObject.interact(26273);
        sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418) || Rs2Bank.isBankPinWidgetVisible(), 10000);
        if (Rs2Bank.isBankPinWidgetVisible()) {
            try {
                Rs2Bank.handleBankPin(Encryption.decrypt(Login.activeProfile.getBankPin()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418), 10000);
        }

        Widget benefitsBtn = Rs2Widget.getWidget(13500418);
        if (benefitsBtn == null) return;
        boolean notSelected = benefitsBtn.getSpriteId() != 813;
        if (notSelected) {
            Rs2Widget.clickWidgetFast(benefitsBtn, 4, 4);
        }
        int count = 0;
        while (count < Rs2Random.between(3, 5)) {
            Widget nmzRewardShop = Rs2Widget.getWidget(206, 6);
            if (nmzRewardShop == null) break;
            Widget overload = nmzRewardShop.getChild(6);
            Rs2Widget.clickWidgetFast(overload, 6, 4);
            Widget absorption = nmzRewardShop.getChild(9);
            Rs2Widget.clickWidgetFast(absorption, 9, 4);
            sleep(600, 1200);
            count++;
        }
    }

}
