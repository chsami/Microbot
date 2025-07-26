package net.runelite.client.plugins.microbot.cardewsPlugins.AIOCamdozaal;

import net.runelite.api.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cardewsPlugins.CUtil;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AIOCamdozScript extends Script {
    enum State{
        BANKING,
        MINING,
        WALKING_TO_ROCKS,
        WALKING_TO_ANVIL,
        WALKING_TO_BANK,
        WALKING_TO_FISH,
        WALKING_TO_GOLEM,
        FISHING,
        PROCESSING
    }
    State state = State.WALKING_TO_BANK;

    List<WallObject> mineableRocks = new ArrayList<>();

    int pickaxeToUse;
    int netToUse;

    int lastInteractedBarroniteID = 41548;
    int targetMiningAnimationID = 0;

    int targetFishingAnimationID = 0;
    boolean processTwice = false;

    WorldPoint bankLocation = new WorldPoint(2978, 5798, 0);

    List<Rs2NpcModel> golems = new ArrayList<>();
    List<Rs2NpcModel> rubbles = new ArrayList<>();
    List<Rs2NpcModel> golemsAttackingPlayer = new ArrayList<>();

    public boolean run(AIOCamdozConfig config) {
        Microbot.enableAutoRunOn = false;
        CUtil.SetMyAntiban(0.08, 2, 15, 0.6);
        state = State.WALKING_TO_BANK;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Microbot.pauseAllScripts.get()) return;
                if (Rs2AntibanSettings.microBreakActive) return;
                long startTime = System.currentTimeMillis();

                switch (config.CurrentActivity())
                {
                    case MINE_AND_SMITH:
                        GetConfigPickaxe(config);
                        MineAndSmith();
                        break;
                    case FISH_AND_PROCESS:
                        GetConfigNet(config);
                        FishAndCook();
                        break;
                    case FIGHT_GOLEM:
                        FightGolem(config);
                        break;
                }

                //Microbot.log("State: " + state);

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                //System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private void MineAndSmith()
    {
        boolean hasPickaxe, hasHammer;
        WorldPoint barroniteLocation = new WorldPoint(2938, 5810, 0);
        int tileDistance = 5;

        int[] miningObjectIDs = { 41547, 41548 };
        int[] miningObjectIDsReverse = { 41548, 41547 };
        List<WorldPoint> barroniteLocations = new ArrayList<>();
        barroniteLocations.add(new WorldPoint(2936, 5806, 0));
        barroniteLocations.add(new WorldPoint(2937, 5806, 0));
        barroniteLocations.add(new WorldPoint(2941, 5809, 0));
        barroniteLocations.add(new WorldPoint(2941, 5810, 0));

        WorldPoint barroniteCrusherLocation = new WorldPoint(2957, 5807, 0);
        int crusherID = ObjectID.CAMDOZAAL_ANVIL;
        int smithingAnimationID = 898;

        int bankObjectID = 41493;

        int[] possibleBarroniteDepositLoots = {
                ItemID.BARRONITE_MACE_1,
                ItemID.IMCANDO_HAMMER_BROKEN,
                ItemID.CAMDOZAAL_RELIC_3,
                ItemID.CAMDOZAAL_RELIC_5,
                ItemID.CAMDOZAAL_RELIC_1,
                ItemID.CAMDOZAAL_RELIC_2,
                ItemID.CAMDOZAAL_RELIC_4
        };

        switch(state)
        {
            case WALKING_TO_ROCKS:
                if (!Rs2Equipment.isWearing(pickaxeToUse))
                {
                    state = State.WALKING_TO_BANK;
                }
                if (Rs2Inventory.isFull())
                {
                    if (Rs2Inventory.hasItem(ItemID.CAMDOZAAL_BARRONITE_DEPOSIT))
                    {
                        state = State.WALKING_TO_ANVIL;
                    }
                    else
                    {
                        state = State.WALKING_TO_BANK;
                    }
                }

                if (!Rs2Walker.isInArea(barroniteLocation, tileDistance))
                {
                    Rs2Walker.walkTo(barroniteLocation);
                    Microbot.status = "Walking to Barronite rocks";
                }
                else
                {
                    state = State.MINING;
                }
                break;

            case MINING:
                if (!Rs2Equipment.isWearing(pickaxeToUse) && !Rs2Inventory.hasItem(pickaxeToUse))
                {
                    state = State.WALKING_TO_BANK;
                    break;
                }
                // If inventory full, go and smith
                if (Rs2Inventory.isFull())
                {
                    if (Rs2Inventory.hasItem(ItemID.CAMDOZAAL_BARRONITE_DEPOSIT))
                    {
                        state = State.WALKING_TO_ANVIL;
                    }
                    else
                    {
                        state = State.WALKING_TO_BANK;
                    }
                }

                // Look at our chosen rock
                if (!mineableRocks.isEmpty())
                {
                    LocalPoint mineLocation = mineableRocks.get(0).getLocalLocation();

                    Rs2Camera.turnTo(mineLocation);
                    if (Rs2Camera.cameraPitchPercentage() < 0.75f)
                    {
                        Rs2Camera.adjustPitch(0.75f);
                    }
                    //Rs2Camera.tu
                    //}
                    //System.out.println(("Rolled chance: " + chanceRoll));
                }

                if (!Rs2Player.isMoving()) {
                    if (Rs2Player.getAnimation() != targetMiningAnimationID) {
                        // Wait a moment. If we get an xp drop, we will be mining. otherwise will continue to click on rock.
                        Rs2Player.waitForXpDrop(Skill.MINING, 1000);
                        int sleepBetween = 2200 + (int) (Math.random() * (4000 - 2200));
                        if (sleepUntil(() -> Rs2Player.getAnimation() != targetMiningAnimationID, sleepBetween)) {
                            // Mine the available rocks
                            mineableRocks.clear();
                            if (lastInteractedBarroniteID == 41547) {
                                //System.out.println(("Last Interacted Rock: LEFT Barronite Vein"));
                                for (int miningObjectID : miningObjectIDsReverse) {
                                    //for (int j = 0; j < barroniteLocations.size(); ++j)
                                    //{
                                    //mineableRocks.addAll(Rs2GameObject.getWallObjects(miningObjectID));
                                    mineableRocks.addAll(Rs2GameObject.getWallObjects(object -> object != null && (object.getId() == 41548)));
                                    //}
                                }
                            } else if (lastInteractedBarroniteID == 41548) {
                                //System.out.println(("Last Interacted Rock: RIGHT Barronite Vein"));
                                for (int miningObjectID : miningObjectIDs) {
                                    //for (int j = 0; j < barroniteLocations.size(); ++j)
                                    //{
                                    //mineableRocks.addAll(Rs2GameObject.getWallObjects(miningObjectID));
                                    mineableRocks.addAll(Rs2GameObject.getWallObjects(object -> object != null && (object.getId() == 41547)));
                                    //}
                                }
                            }

                            //System.out.println("Trying to interact with rocks!");
                            if (!mineableRocks.isEmpty()) {
                                Rs2GameObject.interact(mineableRocks.get(0), "Mine");
                                lastInteractedBarroniteID = mineableRocks.get(0).getId();
                                Microbot.status = "Mining Barronite rocks";
                            }
                        }
                        //System.out.println(("RockList: " + mineableRocks));
                    }
                }

                break;

            case WALKING_TO_ANVIL:
                // ExactLocation = 2956, 5807, 0
                // In front = 2957, 5807, 0

                if (!Rs2Walker.isInArea(barroniteCrusherLocation, tileDistance))
                {
                    Rs2Walker.walkTo(barroniteCrusherLocation);
                    Microbot.status = "Walking to Barronite Crusher";
                }
                else
                {
                    state = State.PROCESSING;
                }
                break;

            case PROCESSING:
                // ObjectName = "Barronite Crusher"
                // Check if the inventory contains barronite to crush
                // "Barronite deposit" ItemID = 25684
                // "Barronite shards" ItemID = 25676
                if (Rs2Player.getAnimation() != smithingAnimationID)
                {
                    if (Rs2Inventory.hasItem(ItemID.CAMDOZAAL_BARRONITE_DEPOSIT))
                    {
                        //int sleepBetween = 10000 + (int)(Math.random() * (20000 - 10000));
                        //if (sleepUntil(() -> Rs2Player.getAnimation() != smithingAnimationID, sleepBetween)) {
                        //if (Rs2Player.waitForXpDrop(Skill.SMITHING, 20000))
                        //{
                        if (!sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.SMITHING), 3000))
                        {
                            Rs2GameObject.interact(crusherID, "Smith");
                            Microbot.status = "Smithing Barronite deposits";
                        }
                        //}
                        //}

                    }
                    else
                    {
                        state = State.WALKING_TO_BANK;
                    }
                }

                break;

            case WALKING_TO_BANK:
                // Walkable location = 2978, 5798, 0
                // Exact object location = 2979, 5798, 0
                if (!Rs2Inventory.contains(possibleBarroniteDepositLoots))
                {
                    if (Rs2Inventory.hasItem(ItemID.HAMMER) || Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER) || Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER_OFFHAND)
                            || Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER) || Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER_OFFHAND))
                    {
                        // We have some form of hammer
                        if (Rs2Inventory.hasItem(pickaxeToUse) || Rs2Equipment.isWearing(pickaxeToUse))
                        {
                            // We have a pickaxe
                            state = State.WALKING_TO_ROCKS;
                            break;
                        }
                    }
                }

                if (!Rs2Walker.isInArea(bankLocation, tileDistance))
                {
                    Rs2Walker.walkTo(bankLocation);
                    Microbot.status = "Walking to Bank";
                }
                else
                {
                    state = State.BANKING;
                }
                break;

            case BANKING:
                // Bank objectID = 41493
                // "Bank chest"
                if (Rs2Inventory.hasItem(ItemID.HAMMER) || Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER)
                        || Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER_OFFHAND) || Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER)
                        || Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER_OFFHAND))
                {
                    hasHammer = true;
                }
                else
                {
                    hasHammer = false;
                }
                if (Rs2Equipment.isWearing(pickaxeToUse))
                {
                    hasPickaxe = true;
                }
                else
                {
                    if (Rs2Inventory.hasItem(pickaxeToUse))
                    {
                        hasPickaxe = true;
                        Rs2Inventory.equip(pickaxeToUse);
                    }
                    else
                    {
                        hasPickaxe = false;
                    }
                }

                // Do I have a pickaxe?
                // 1. Is one equipped?
                // Yes? Okay done.
                // No? Do we have one in our inventory?

                if (!Rs2Bank.isOpen())
                {
                    if (!Rs2Widget.isWidgetVisible(213, 0))
                    {
                        Rs2Bank.openBank();
                    }
                }
                else
                {
                    Rs2Bank.depositAllExcept(ItemID.HAMMER, ItemID.CAMDOZAAL_BARRONITE_SHARD);
                    if (!hasHammer)
                    {
                        if (Rs2Bank.hasItem(ItemID.IMCANDO_HAMMER))
                        {
                            Rs2Bank.withdrawOne(ItemID.IMCANDO_HAMMER);
                        }
                        else if (Rs2Bank.hasItem(ItemID.IMCANDO_HAMMER_OFFHAND))
                        {
                            Rs2Bank.withdrawOne(ItemID.IMCANDO_HAMMER_OFFHAND);
                        }
                        else
                        {
                            Rs2Bank.withdrawOne(ItemID.HAMMER);
                        }
                    }
                    if (!hasPickaxe)
                    {
                        Rs2Bank.withdrawOne(pickaxeToUse);
                    }
                    Rs2Bank.closeBank();
                    if (Rs2Inventory.hasItem(pickaxeToUse))
                    {
                        hasPickaxe = true;
                        Rs2Inventory.equip(pickaxeToUse);
                    }
                    if (Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER))
                    {
                        Rs2Inventory.interact(ItemID.IMCANDO_HAMMER, "Swap");
                        Rs2Inventory.equip(ItemID.IMCANDO_HAMMER_OFFHAND);
                    }
                    else if (Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER_OFFHAND))
                    {
                        Rs2Inventory.equip(ItemID.IMCANDO_HAMMER_OFFHAND);
                    }

                    state = State.WALKING_TO_ROCKS;
                }
                Microbot.status = "Banking";

                break;
            case FISHING:
            case WALKING_TO_FISH:
            case WALKING_TO_GOLEM:
                state = State.WALKING_TO_BANK;
                break;
        }
    }

    private void FishAndCook()
    {
        WorldPoint fishingLocation = new WorldPoint(2930, 5776, 0);
        WorldPoint processingLocation = new WorldPoint(2934, 5772, 0);
        //Microbot.log(state.name());
        switch (state)
        {
            case WALKING_TO_FISH:
                if (Rs2Inventory.isFull())
                {
                    if (Rs2Inventory.hasItem(ItemID.RAW_GUPPY) || Rs2Inventory.hasItem(ItemID.RAW_CAVEFISH) || Rs2Inventory.hasItem(ItemID.RAW_TETRA))
                    {
                        state = State.PROCESSING;
                        break;
                    }
                }
                if (!Rs2Inventory.hasItem(netToUse))
                {
                    state = State.WALKING_TO_BANK;
                    break;
                }

                if (!Rs2Walker.isInArea(fishingLocation, 5))
                {
                    //Microbot.log("Trying to walk to fishingLocation");
                    Rs2Walker.walkTo(fishingLocation);
                }
                else
                {
                    state = State.FISHING;
                }
                Microbot.status = "Walking to fishing location";
                break;

            case FISHING:
                if (!Rs2Inventory.hasItem(netToUse))
                {
                    state = State.WALKING_TO_BANK;
                    break;
                }
                if (!Rs2Walker.isInArea(fishingLocation, 5))
                {
                    state = State.WALKING_TO_FISH;
                    break;
                }
                if (Rs2Inventory.isFull())
                {
                    state = State.PROCESSING;
                }
                if (!Rs2Player.isInteracting() && !Rs2Player.isMoving())
                {
                    if (netToUse == 303)
                    {
                        Rs2Npc.interact(10686, "Small Net");
                    }
                    else if (netToUse == 305)
                    {
                        Rs2Npc.interact(10686, "Big Net");

                    }
                }
                Microbot.status = "Fishing";
                break;

            case PROCESSING:
                if (!Rs2Walker.isInArea(processingLocation, 5))
                {
                    Rs2Walker.walkTo(processingLocation);
                }
                else
                {
                    if (!processTwice) {
                        if (Rs2Player.getAnimation() != AnimationID.HUMAN_COOKING) {
                            if (Rs2Inventory.hasItem(ItemID.RAW_GUPPY) || Rs2Inventory.hasItem(ItemID.RAW_CAVEFISH)
                                    || Rs2Inventory.hasItem(ItemID.RAW_TETRA) || Rs2Inventory.hasItem(ItemID.RAW_CATFISH)) {
                                if (Rs2Widget.isProductionWidgetOpen()) {
                                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                                    Rs2Player.waitForXpDrop(Skill.COOKING, 3000);
                                }
                                else if (!Rs2Player.isMoving()) {
                                    Rs2GameObject.interact(41545, "Prepare-fish");

                                }
                            }
                            else
                            {
                                processTwice = true;
                            }
                        }
                    }
                    else
                    {
                        if (Rs2Player.getAnimation() != AnimationID.HUMAN_BONE_SACRIFICE)
                        {
                            if (Rs2Inventory.hasItem(ItemID.RUINED_GUPPY) || Rs2Inventory.hasItem(ItemID.RUINED_CAVEFISH)
                                    || Rs2Inventory.hasItem(ItemID.RUINED_TETRA) || Rs2Inventory.hasItem(ItemID.RUINED_CATFISH))
                            {
                                Rs2Inventory.dropAll(ItemID.RUINED_GUPPY, ItemID.RUINED_CAVEFISH, ItemID.RUINED_TETRA, ItemID.RUINED_CATFISH);
                            }
                            else if (Rs2Inventory.hasItem(ItemID.GUPPY) || Rs2Inventory.hasItem(ItemID.CAVEFISH)
                                    || Rs2Inventory.hasItem(ItemID.TETRA) || Rs2Inventory.hasItem(ItemID.CATFISH))
                            {
                                if (Rs2Widget.isProductionWidgetOpen())
                                {
                                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                                    Rs2Player.waitForXpDrop(Skill.PRAYER, 3000);
                                }
                                else if (!Rs2Player.isMoving())
                                {
                                    Rs2GameObject.interact(41546, "Offer-fish");
                                }
                            }
                            else
                            {
                                if (Rs2Inventory.hasItem(ItemID.BARRONITE_MACE_2)) {
                                    state = State.WALKING_TO_BANK;
                                } else {
                                    state = State.WALKING_TO_FISH;
                                }
                                processTwice = false;
                            }
                        }
                    }
                }
                Microbot.status = "Processing fish";
                break;

            case WALKING_TO_BANK:
                if (Rs2Inventory.hasItem(netToUse) && Rs2Inventory.hasItem(ItemID.KNIFE) && !Rs2Inventory.hasItem(ItemID.BARRONITE_MACE_2))
                {
                    state = State.WALKING_TO_FISH;
                    break;
                }
                if (!Rs2Walker.isInArea(bankLocation, 5))
                {
                    Rs2Walker.walkTo(bankLocation);
                }
                else
                {
                    state = State.BANKING;
                }
                Microbot.status = "Walking to bank";
                break;

            case BANKING:
                boolean hasNet = false;
                boolean hasKnife = false;
                if (Rs2Inventory.hasItem(netToUse))
                {
                    hasNet = true;
                }
                if (Rs2Inventory.hasItem(ItemID.KNIFE))
                {
                    hasKnife = true;
                }
                if (!Rs2Inventory.hasItem(ItemID.BARRONITE_MACE_2) && !Rs2Inventory.isFull() && hasNet && hasKnife)
                {
                    state = State.WALKING_TO_FISH;
                    break;
                }

                if (!Rs2Bank.isOpen())
                {
                    Rs2Bank.openBank();
                }
                else
                {
                    Rs2Bank.depositAllExcept(netToUse, ItemID.KNIFE, ItemID.CAMDOZAAL_BARRONITE_SHARD);
                    if (!hasNet)
                    {
                        Rs2Bank.withdrawOne(netToUse);
                    }
                    if (!hasKnife)
                    {
                        Rs2Bank.withdrawOne(ItemID.KNIFE);
                    }
                    Rs2Bank.closeBank();
                }
                Microbot.status = "Banking";
                break;
            case WALKING_TO_ROCKS:
            case WALKING_TO_ANVIL:
            case MINING:
            case WALKING_TO_GOLEM:
                state = State.WALKING_TO_BANK;
                break;
        }
    }

    private void FightGolem(AIOCamdozConfig _config)
    {
        // FLAWED GOLEM IDs
        // Rubble: 10696 | Golem: 10695
        WorldPoint flawedGolemLocation = new WorldPoint(2981, 5777, 0);

        // MIND GOLEM IDs
        // Rubble: 10694 | Golem: 10693
        WorldPoint mindGolemLocation = new WorldPoint(3001, 5784, 0);

        // BODY GOLEM IDs
        // Rubble: 10692 | Golem: 10691
        WorldPoint bodyGolemLocation = new WorldPoint(3019, 5806, 0);

        // CHAOS GOLEM IDs
        // Rubble: 10690 | Golem: 10689
        WorldPoint chaosGolemLocation = new WorldPoint(3021, 5786, 0);

        // Waking anim ID: 8944 (Rubble NPC is animating)
        // Death anim ID: 8943
        // Golem Attack anim ID: 8942
        // Player wake up anim ID: 1606

        // If health is below our eat threshold -> Eat food
        if (!Rs2Player.eatAt(_config.EatFoodAtPercent()))
        {
            // If FALSE then we didn't eat
            // Do we have food in inventory?
            // If yes, resolve nothing
            // If no, run away to bank
            List<Rs2ItemModel> foods = Rs2Inventory.getInventoryFood();
            double healthPercent = (double) Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) / Rs2Player.getRealSkillLevel(Skill.HITPOINTS) * 100;
            //Microbot.log("[AIO Camdozaal] HP Percent: " + healthPercent);
            if (foods.isEmpty() && (state != State.WALKING_TO_BANK && state != State.BANKING) && healthPercent < 50)
            {
                state = State.WALKING_TO_BANK;
                Microbot.log("[AIO Camdozaal]: Out of food & less than half HP! Walking to bank!]");
            }
            else
            {
                // Only respect the actionCooldown if we aren't wanting to run for our life
                if (Rs2AntibanSettings.actionCooldownActive) return;
            }
        }

        switch (_config.SelectedGolemType())
        {
            case FLAWED:
                HandleGolemFightingState(flawedGolemLocation, 10696, 10695, _config);
                break;
            case MIND:
                HandleGolemFightingState(mindGolemLocation, 10694, 10693, _config);
                break;
            case BODY:
                HandleGolemFightingState(bodyGolemLocation, 10692, 10691, _config);
                break;
            case CHAOS:
                HandleGolemFightingState(chaosGolemLocation, 10690, 10689, _config);
                break;
        }
    }

    private void HandleGolemFightingState(WorldPoint _golemLocation, int _rubbleID, int _golemID, AIOCamdozConfig _config)
    {
        switch (state)
        {
            case WALKING_TO_GOLEM:
                //if (BreakHandlerScript.lockState)
                //{
                //BreakHandlerScript.lockState = false;
                //}

                // Walk to _golemLocation
                if (!Rs2Walker.isInArea(_golemLocation, 10))
                {
                    Rs2Walker.walkTo(_golemLocation);
                }
                else
                {
                    state = State.PROCESSING;
                }
                break;

            case PROCESSING:
                // FIGHTING
                //if (!BreakHandlerScript.lockState)
                //{
                //BreakHandlerScript.lockState = true;
                //}

                if (!Rs2Inventory.isFull())
                {
                    LootItems(_config);
                }
                else
                {
                    state = State.WALKING_TO_BANK;
                }

                golemsAttackingPlayer = Rs2Npc.getNpcsForPlayer().collect(Collectors.toList());

                if (!Rs2Combat.inCombat())
                {
                    // Not in combat
                    if (!golemsAttackingPlayer.isEmpty())
                    {
                        Microbot.log("Not in combat. golemsAttackingPlayer list is NOT empty.");
                        Microbot.log("List: " + golemsAttackingPlayer);
                        // Filter by things with an Attack option
                        Rs2NpcModel targetGolem = golemsAttackingPlayer.stream()
                                .filter(npc -> npc.getComposition() != null &&
                                        Arrays.stream(npc.getComposition().getActions())
                                                .anyMatch(action -> action != null && action.toLowerCase().contains("attack")))
                                .findFirst().orElse(null);

                        if (targetGolem != null)
                        {
                            if (!Rs2Camera.isTileOnScreen(targetGolem.getLocalLocation()))
                            {
                                Rs2Camera.turnTo(targetGolem);
                            }

                            Rs2Npc.interact(targetGolem, "Attack");
                            Rs2Antiban.actionCooldown();
                        }
                        break;
                    }

                    golems = Rs2Npc.getNpcs(_golemID).collect(Collectors.toList());
                    rubbles = Rs2Npc.getNpcs(_rubbleID).collect(Collectors.toList());

                    //Microbot.log("Golem List: " + golems.toString());
                    if (!golems.isEmpty())
                    {
                        Rs2NpcModel golem = golems.stream().findFirst().orElse(null);

                        if (!Rs2Camera.isTileOnScreen(golem.getLocalLocation()))
                        {
                            Rs2Camera.turnTo(golem);
                        }

                        Rs2Npc.interact(golem, "Attack");
                        Rs2Antiban.actionCooldown();
                    }
                    else
                    {
                        if (!rubbles.isEmpty())
                        {
                            Rs2NpcModel rubble = rubbles.stream().findFirst().orElse(null);

                            if (!Rs2Camera.isTileOnScreen(rubble.getLocalLocation()))
                            {
                                Rs2Camera.turnTo(rubble);
                            }

                            Rs2Npc.interact(rubble, "Awaken");
                            Rs2Antiban.actionCooldown();
                            sleepUntil(() -> !golems.isEmpty(), 2000);
                        }

                    }
                }
                break;

            case WALKING_TO_BANK:
                //if (BreakHandlerScript.lockState)
                //{
                //BreakHandlerScript.lockState = false;
                //}

                if (!Rs2Walker.isInArea(bankLocation, 5))
                {
                    Rs2Walker.walkTo(bankLocation);
                }
                else
                {
                    Rs2Antiban.takeMicroBreakByChance();
                    state = State.BANKING;
                }
                break;

            case BANKING:
                if (!Rs2Bank.isOpen())
                {
                    Rs2Bank.openBank();
                }
                else
                {
                    Rs2Bank.depositAll();

                    boolean foodWithdrawn = false;
                    if (_config.GetFood())
                    {

                        for (Rs2Food food : Arrays.stream(Rs2Food.values()).sorted(Comparator.comparingInt(Rs2Food::getHeal)).collect(Collectors.toList()))
                        {
                            if (Rs2Bank.hasBankItem(food.getId(), _config.NumberOfFood()))
                            {
                                Rs2Bank.withdrawX(food.getId(), _config.NumberOfFood());
                                foodWithdrawn = true;
                                break;
                            }
                        }
                        Rs2Bank.closeBank();
                        if (foodWithdrawn)
                        {
                            state = State.WALKING_TO_GOLEM;
                            Rs2Antiban.takeMicroBreakByChance();
                        }
                        else
                        {
                            Microbot.showMessage("Not enough food found in bank!");
                            shutdown();
                        }
                    }
                    else
                    {
                        Rs2Bank.closeBank();
                        state = State.WALKING_TO_GOLEM;
                        Rs2Antiban.takeMicroBreakByChance();
                    }
                }
                break;

            case WALKING_TO_ROCKS:
            case WALKING_TO_ANVIL:
            case WALKING_TO_FISH:
            case MINING:
            case FISHING:
                state = State.WALKING_TO_BANK;
                break;
        }
    }

    private void LootItems(AIOCamdozConfig _config)
    {
        if (_config.PickupUntradeables())
        {
            LootingParameters untradeableItemParams = new LootingParameters(
                    10,
                    1,
                    1,
                    1,
                    false,
                    _config.LootOnlyMyDrops(),
                    "untradeable"
            );
            Rs2GroundItem.lootUntradables(untradeableItemParams);
        }

        if (_config.PickupRunes())
        {
            LootingParameters runesItemParams = new LootingParameters(
                    10,
                    1,
                    1,
                    1,
                    false,
                    _config.LootOnlyMyDrops(),
                    " rune"
            );
            Rs2GroundItem.lootItemsBasedOnNames(runesItemParams);
        }

        if (_config.PickupGems())
        {
            LootingParameters gemItemParams = new LootingParameters(
                    10,
                    1,
                    1,
                    1,
                    false,
                    _config.LootOnlyMyDrops(),
                    "uncut "
            );
            Rs2GroundItem.lootItemsBasedOnNames(gemItemParams);
        }
    }

    private void GetConfigPickaxe(AIOCamdozConfig _config)
    {
        switch (_config.SelectedPickaxe())
        {
            case BRONZE:
                pickaxeToUse = ItemID.BRONZE_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL;
                break;
            case IRON:
                pickaxeToUse = ItemID.IRON_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL;
                break;
            case STEEL:
                pickaxeToUse = ItemID.STEEL_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL;
                break;
            case BLACK:
                pickaxeToUse = ItemID.BLACK_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL;
                break;
            case MITHRIL:
                pickaxeToUse = ItemID.MITHRIL_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL;
                break;
            case ADAMANT:
                pickaxeToUse = ItemID.ADAMANT_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL;
                break;
            case RUNE:
                pickaxeToUse = ItemID.RUNE_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL;
                break;
            case GILDED_PICKAXE:
                pickaxeToUse = ItemID.TRAIL_GILDED_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_GILDED_PICKAXE_WALL;
                break;
            case DRAGON_PICKAXE:
                pickaxeToUse = ItemID.DRAGON_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL;
                break;
            case DRAGON_PICKAXE_OR:
                pickaxeToUse = ItemID.DRAGON_PICKAXE_PRETTY;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL;
                break;
            case INFERNAL_PICKAXE:
                pickaxeToUse = ItemID.INFERNAL_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL;
                break;
            case INFERNAL_PICKAXE_OR:
                pickaxeToUse = ItemID.TRAILBLAZER_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_WALL;
                break;
            case CRYSTAL_PICKAXE:
                pickaxeToUse = ItemID.CRYSTAL_PICKAXE;
                targetMiningAnimationID = AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL;
                break;
        }
    }

    private void GetConfigNet(AIOCamdozConfig config)
    {
        switch (config.SelectedNet())
        {
            case SMALL_NET:
                netToUse = ItemID.NET;
                targetFishingAnimationID = AnimationID.HUMAN_SMALLNET;
                break;
            case BIG_NET:
                netToUse = ItemID.BIG_NET;
                targetFishingAnimationID = AnimationID.HUMAN_LARGENET;
                break;
        }
    }
}
