package net.runelite.client.plugins.microbot.mixology;

import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;


import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.mixology.AlchemyObject.MIXING_VESSEL;


public class MixologyScript extends Script {

    public final static String version = "1.0.2-beta";
    private static final Integer DIGWEED = ItemID.MM_LAB_SPECIAL_HERB;

    public java.util.List<PotionOrder> potionOrders = Collections.emptyList();

    public static MixologyState mixologyState = MixologyState.IDLE;
    public static int lyePasteAmount, agaPasteAmount, moxPasteAmount = 0;
    public static int startLyePoints, startAgaPoints, startMoxPoints = 0;
    public static int currentLyePoints, currentAgaPoints, currentMoxPoints = 0;
    public int agitatorQuickActionTicks = 0;
    public int alembicQuickActionTicks = 0;
    public AlchemyObject digweed;
    public int leverRetries = 0;
    public List<PotionModifier> customOrder = Arrays.asList(
            PotionModifier.CRYSTALISED,
            PotionModifier.CONCENTRATED,
            PotionModifier.HOMOGENOUS
    );

    public boolean run(MixologyConfig config) {
        Microbot.enableAutoRunOn = false;
        currentMoxPoints = 0;
        currentAgaPoints = 0;
        currentLyePoints = 0;
        leverRetries = 0;
        if (!Rs2AntibanSettings.naturalMouse) {
            Microbot.log("Hey! Did you know this script works really well with natural mouse? Feel free to enable it in the antiban settings.");
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (leverRetries >= 20) {
                    Microbot.log("Failed to create a potion. Please do this step manually and restart the script.");
                    return;
                }


                boolean isInMinigame = Rs2Widget.getWidget(882, 2) != null;


                if (!isInMinigame && mixologyState != MixologyState.REFINER) {
                    Rs2Walker.walkTo(1395, 9322, 0, 2);
                    return;
                }

                if (isInMinigame) {

                    if (startLyePoints == 0 && startAgaPoints == 0 && startMoxPoints == 0) {
                        startMoxPoints = getMoxPoints();
                        startAgaPoints = getAgaPoints();
                        startLyePoints = getLyePoints();
                    }

                    if (digweed != null && !Rs2Player.isAnimating() && !Rs2Inventory.hasItem(DIGWEED)
                            && config.pickDigWeed()) {
                        Rs2GameObject.interact(digweed.coordinate());
                        Rs2Player.waitForWalking();
                        Rs2Player.waitForAnimation();
                        return;
                    }

                    if (Rs2Inventory.hasItem(DIGWEED) && !Rs2Player.isAnimating()) {
                        Optional<Integer> potionItemId = potionOrders
                                .stream()
                                .filter(x -> !x.fulfilled() && Rs2Inventory.hasItem(x.potionType().itemId()))
                                .map(x -> x.potionType().itemId())
                                .findFirst();
                        if (potionItemId.isPresent()) {
                            Rs2Inventory.interact(DIGWEED, "use");
                            Rs2Inventory.interact(potionItemId.get(), "use");
                            Rs2Player.waitForAnimation();
                            return;
                        }
                    }

                    moxPasteAmount = Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[8].getText()) + Rs2Inventory.itemQuantity(ItemID.MM_MOX_PASTE);
                    agaPasteAmount = Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[11].getText()) + Rs2Inventory.itemQuantity(ItemID.MM_AGA_PASTE);
                    lyePasteAmount = Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[14].getText()) + Rs2Inventory.itemQuantity(ItemID.MM_LYE_PASTE);

                    if (mixologyState != MixologyState.REFINER && (moxPasteAmount < 100 || agaPasteAmount < 100 || lyePasteAmount < 100)) {
                        mixologyState = MixologyState.REFINER;
                    } else if (Rs2Inventory.hasItem(ItemID.MM_MOX_PASTE) || Rs2Inventory.hasItem(ItemID.MM_LYE_PASTE) || Rs2Inventory.hasItem(ItemID.MM_AGA_PASTE)) {
                        if (Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[8].getText()) >= 3000 && Rs2Inventory.hasItem(ItemID.MM_MOX_PASTE)) {
                            mixologyState = MixologyState.BANK;
                        } else if (Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[11].getText()) >= 3000 && Rs2Inventory.hasItem(ItemID.MM_AGA_PASTE)) {
                            mixologyState = MixologyState.BANK;

                        } else if (Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[14].getText()) >= 3000 && Rs2Inventory.hasItem(ItemID.MM_LYE_PASTE)) {
                            mixologyState = MixologyState.BANK;
                        } else {
                            mixologyState = MixologyState.DEPOSIT_HOPPER;
                        }
                    }
                }

                if (mixologyState == MixologyState.IDLE) {
                    mixologyState = MixologyState.MIX_POTION_STAGE_1;
                }

                if (hasAllFulFilledItems()) {
                    mixologyState = MixologyState.CONVEYER_BELT;
                }

                switch (mixologyState) {
                    case BANK:
                        if (Rs2Inventory.hasItem("paste")) {
                            if (Rs2Bank.openBank()) {
                                Rs2Bank.depositAll();
                            }
                            return;
                        }
                        mixologyState = MixologyState.MIX_POTION_STAGE_1;
                        break;
                    case REFINER:
                        String herb = "";
                        WorldPoint bankLocation = new WorldPoint(1398, 9313, 0);
                        if (Rs2Player.getWorldLocation().distanceTo(bankLocation) > 10) {
                            Rs2Walker.walkTo(bankLocation);
                            return;
                        }

                        if (Rs2Inventory.hasItem(config.agaHerb().toString()) || Rs2Inventory.hasItem(config.lyeHerb().toString()) || Rs2Inventory.hasItem(config.moxHerb().toString())) {
                            Rs2GameObject.interact(ObjectID.MM_LAB_MILL);
                            Rs2Player.waitForAnimation();
                            sleepGaussian(450, 150);
                            if (!config.useQuickActionRefiner()) {
                                sleepUntil(() -> !Microbot.isGainingExp, 30000);
                            }
                            return;
                        }
                        if (Rs2Bank.openBank()) {
                            sleepUntil(Rs2Bank::isOpen);
                            moxPasteAmount = Rs2Bank.count(ItemID.MM_MOX_PASTE);
                            lyePasteAmount = Rs2Bank.count(ItemID.MM_LYE_PASTE);
                            agaPasteAmount = Rs2Bank.count(ItemID.MM_AGA_PASTE);
                            if (moxPasteAmount < config.amtMoxHerb()) {
                                herb = config.moxHerb().toString();
                            } else if (lyePasteAmount < config.amtLyeHerb()) {
                                herb = config.lyeHerb().toString();
                            } else if (agaPasteAmount < config.amtAgaHerb()) {
                                herb = config.agaHerb().toString();
                            } else {
                                if (Rs2Bank.openBank()) {
                                    Rs2Bank.depositAll();
                                    Rs2Bank.withdrawAll(ItemID.MM_MOX_PASTE);
                                    Rs2Bank.withdrawAll(ItemID.MM_LYE_PASTE);
                                    Rs2Bank.withdrawAll(ItemID.MM_AGA_PASTE);
                                    mixologyState = MixologyState.DEPOSIT_HOPPER;
                                    return;
                                }
                            }
                            Rs2Bank.depositAll();
                            if (!Rs2Bank.hasItem(herb, true)) {
                                Microbot.showMessage("Failed to find " + herb + " in your bank. Shutting down script...");
                                shutdown();
                                return;
                            }
                            Rs2Bank.withdrawAll(herb, true);
                            Rs2Bank.closeBank();
                            sleepGaussian(600, 150);
                        }
                        break;
                    case DEPOSIT_HOPPER:
                        if (Rs2GameObject.interact(ObjectID.MM_LAB_HOPPER)) {
                            Rs2Player.waitForWalking();
                            Rs2Inventory.waitForInventoryChanges(10000);
                            mixologyState = MixologyState.MIX_POTION_STAGE_1;
                        }
                        break;
                    case MIX_POTION_STAGE_1:

                        Map<Integer, Integer> itemsToCheck = new HashMap<>();
                        PotionOrder potionToMake = null;

                        for (PotionOrder _potionOrder : potionOrders) {
                            int key = _potionOrder.potionType().itemId();
                            int value = itemsToCheck.getOrDefault(key, 0);
                            itemsToCheck.put(key, value + 1);
                        }

                        for (int itemId : itemsToCheck.keySet()) {
                            PotionOrder _potionOrder = potionOrders
                                    .stream()
                                    .filter(x -> x.potionType().itemId() == itemId)
                                    .findFirst()
                                    .orElse(null);

                            if (_potionOrder == null) continue;

                            int itemAmount = itemsToCheck.getOrDefault(itemId, 1);

                            if (!Rs2Inventory.hasItemAmount(itemId, itemAmount)) {
                                potionToMake = _potionOrder;
                            }
                        }

                        if (potionToMake == null) {
                            mixologyState = MixologyState.MIX_POTION_STAGE_2;
                            return;
                        }

                        if (canCreatePotion(potionToMake)) {
                            mixologyState = MixologyState.TAKE_FROM_MIXIN_VESSEL;
                            leverRetries = 0;
                        } else {
                            createPotion(potionToMake, config);
                        }
                        break;
                    case TAKE_FROM_MIXIN_VESSEL:
                        Rs2GameObject.interact(MIXING_VESSEL.objectId());
                        boolean result = Rs2Inventory.waitForInventoryChanges(5000);
                        if (result) {
                            mixologyState = MixologyState.MIX_POTION_STAGE_1;
                        }
                        break;
                    case MIX_POTION_STAGE_2:

                        // Sort using a custom comparator
                        List<PotionOrder> nonFulfilledPotions = potionOrders
                                .stream()
                                .filter(x -> !x.fulfilled())
                                .sorted(Comparator.comparingInt(customOrder::indexOf))
                                .collect(Collectors.toList());

                        if (nonFulfilledPotions.isEmpty()) {
                            mixologyState = MixologyState.CONVEYER_BELT;
                            return;
                        }

                        PotionOrder nonFulfilledPotion = nonFulfilledPotions.get(0);

                        if (Rs2Player.isAnimating()) {
                            if (agitatorQuickActionTicks > 0 && config.useQuickActionOnAgitator()) {
                                int clicks =  Rs2AntibanSettings.naturalMouse ? Rs2Random.between(4, 6) : Rs2Random.between(6, 10);
                                for (int i = 0; i < clicks; i++) {
                                    quickActionProcessPotion(nonFulfilledPotion);
                                }
                                agitatorQuickActionTicks = 0;
                            } else if (alembicQuickActionTicks > 0  && config.useQuickActionOnAlembic()) {
                                quickActionProcessPotion(nonFulfilledPotion);
                                alembicQuickActionTicks = 0;
                            }
                            if (nonFulfilledPotion.potionModifier().alchemyObject() == AlchemyObject.RETORT && config.useQuickActionOnRetort()&&Microbot.getVarbitValue(11327)<15&&Microbot.getVarbitValue(11327)!=0) {
                                quickActionProcessPotion(nonFulfilledPotion);
                                sleep(350, 400);
                            }
                            return;
                        }

                        if (nonFulfilledPotion == null || !Rs2Inventory.hasItem(nonFulfilledPotion.potionType().itemId())) {
                            mixologyState = MixologyState.MIX_POTION_STAGE_1;
                            return;
                        }

                        processPotion(nonFulfilledPotion);
                        sleepUntil(Rs2Player::isAnimating);
                        break;
                    case CONVEYER_BELT:
                        if (potionOrders.stream().noneMatch(x -> Rs2Inventory.hasItem(x.potionType().getFulfilledItemId()))) {
                            mixologyState = MixologyState.MIX_POTION_STAGE_1;
                            return;
                        }
                        if (Rs2GameObject.interact(AlchemyObject.CONVEYOR_BELT.objectId())) {
                            Rs2Inventory.waitForInventoryChanges(5000);
                            currentAgaPoints = getAgaPoints();
                            currentLyePoints = getLyePoints();
                            currentMoxPoints = getMoxPoints();
                        }
                        break;
                }


                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasAllFulFilledItems() {
        Map<Integer, Integer> itemsToCheck = new HashMap<>();
        boolean hasAllFulFilledItems = true;

        for (PotionOrder _potionOrder : potionOrders) {
            int key = _potionOrder.potionType().getFulfilledItemId();
            int value = itemsToCheck.getOrDefault(key, 0);
            itemsToCheck.put(key, value + 1);
        }

        for (int itemId : itemsToCheck.keySet()) {
            PotionOrder _potionOrder = potionOrders
                    .stream()
                    .filter(x -> x.potionType().getFulfilledItemId() == itemId)
                    .findFirst()
                    .orElse(null);

            if (_potionOrder == null) continue;

            int itemAmount = itemsToCheck.getOrDefault(itemId, 1);

            if (!Rs2Inventory.hasItemAmount(itemId, itemAmount)) {
                hasAllFulFilledItems = false;
            }
        }
        return hasAllFulFilledItems;
    }

    private static void processPotion(PotionOrder nonFulfilledPotion) {
        switch (nonFulfilledPotion.potionModifier()) {
            case HOMOGENOUS:
                GameObject agitator = (GameObject) Rs2GameObject.findObjectById(AlchemyObject.AGITATOR.objectId());
                if (agitator != null && (((DynamicObject) agitator.getRenderable()).getAnimation().getId() == 11633 || ((DynamicObject) agitator.getRenderable()).getAnimation().getId() == 11632)) {
                    Rs2GameObject.interact(AlchemyObject.AGITATOR.objectId());
                } else {
                    Rs2Inventory.useItemOnObject(nonFulfilledPotion.potionType().itemId(), AlchemyObject.AGITATOR.objectId());
                }
                break;
            case CONCENTRATED:
                GameObject retort = (GameObject) Rs2GameObject.findObjectById(AlchemyObject.RETORT.objectId());
                if (retort != null && (((DynamicObject) retort.getRenderable()).getAnimation().getId() == 11643 || ((DynamicObject) retort.getRenderable()).getAnimation().getId() == 11642)) {
                    Rs2GameObject.interact(AlchemyObject.RETORT.objectId());
                } else {
                    Rs2Inventory.useItemOnObject(nonFulfilledPotion.potionType().itemId(), AlchemyObject.RETORT.objectId());
                }
                break;
            case CRYSTALISED:
                GameObject alembic = (GameObject) Rs2GameObject.findObjectById(AlchemyObject.ALEMBIC.objectId());
                if (alembic != null && (((DynamicObject) alembic.getRenderable()).getAnimation().getId() == 11638 || ((DynamicObject) alembic.getRenderable()).getAnimation().getId() == 11637)) {
                    Rs2GameObject.interact(AlchemyObject.ALEMBIC.objectId());
                } else {
                    Rs2Inventory.useItemOnObject(nonFulfilledPotion.potionType().itemId(), AlchemyObject.ALEMBIC.objectId());
                }
                break;
        }
    }

    private static void quickActionProcessPotion(PotionOrder nonFulfilledPotion) {
        switch (nonFulfilledPotion.potionModifier()) {
            case HOMOGENOUS:
                Rs2GameObject.interact(AlchemyObject.AGITATOR.objectId());
                break;
            case CONCENTRATED:
                Rs2GameObject.interact(AlchemyObject.RETORT.objectId());
                break;
            case CRYSTALISED:
                Rs2GameObject.interact(AlchemyObject.ALEMBIC.objectId());
                break;
        }
    }

    private void createPotion(PotionOrder potionOrder, MixologyConfig config) {
        for (PotionComponent component : potionOrder.potionType().components()) {
            if (canCreatePotion(potionOrder)) break;
            if (component.character() == 'A') {
                Rs2GameObject.interact(AlchemyObject.AGA_LEVER.objectId());
            } else if (component.character() == 'L') {
                Rs2GameObject.interact(AlchemyObject.LYE_LEVER.objectId());
            } else if (component.character() == 'M') {
                Rs2GameObject.interact(AlchemyObject.MOX_LEVER.objectId());
            }
            if (config.useQuickActionLever()) {
                Rs2Player.waitForAnimation();
            } else {
                sleepUntil(Rs2Player::isAnimating);
                final int sleep = Rs2Random.between(300, 600);
                sleepGaussian(sleep, sleep / 4);
            }
            leverRetries++;
        }
    }

    private boolean canCreatePotion(PotionOrder potionOrder) {
        // Get the mixer game objects
        GameObject[] mixers = {
                (GameObject) Rs2GameObject.findObjectById(ObjectID.MM_LAB_MIXER_03), // mixer3
                (GameObject) Rs2GameObject.findObjectById(ObjectID.MM_LAB_MIXER_02), // mixer2
                (GameObject) Rs2GameObject.findObjectById(ObjectID.MM_LAB_MIXER_01)  // mixer1
        };

        // Check if any mixers are missing
        if (Arrays.stream(mixers).anyMatch(Objects::isNull)) {
            return false;
        }

        // Get animations in correct order
        int[] currentAnimations = Arrays.stream(mixers)
                .map(mixer -> ((DynamicObject) mixer.getRenderable()).getAnimation().getId())
                .mapToInt(Integer::intValue)
                .toArray();

        // Map components to their valid animations
        Map<Character, int[]> componentAnimations = Map.of(
                'A', new int[]{11615, 11609, 11612}, // AGA animations
                'M', new int[]{11617, 11614, 11607}, // MOX animations
                'L', new int[]{11608, 11611, 11618}  // LYE animations
        );

        // Check each position
        for (int i = 0; i < potionOrder.potionType().components().length; i++) {
            char expectedComponent = potionOrder.potionType().components()[i].character();
            int currentAnimation = currentAnimations[i];

            boolean isValid = Arrays.stream(componentAnimations.get(expectedComponent))
                    .anyMatch(validAnim -> validAnim == currentAnimation);

            if (!isValid) {
                return false;
            }
        }

        return true;
    }

    private int getMoxPoints() {
        return Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[16].getText());
    }

    private int getAgaPoints() {
        return Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[17].getText());
    }

    private int getLyePoints() {
        return Integer.parseInt(Rs2Widget.getWidget(882, 2).getDynamicChildren()[18].getText());
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
