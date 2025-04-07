package net.runelite.client.plugins.microbot.collector;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.api.TileObject;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.client.eventbus.Subscribe;
import static net.runelite.client.plugins.microbot.Microbot.log;

import java.util.concurrent.TimeUnit;

public class CollectorScript extends Script {
    public enum State {
        IDLE,
        // Snape Grass
        WALKING_TO_SG_AREA,
        COLLECTING_SG,
        BANKING_SG,
        RETURNING_SG_AREA,
        // Super Anti-Poison
        WALKING_TO_SAP_AREA,
        COLLECTING_SAP,
        BANKING_SAP,
        RETURNING_SAP_AREA,
        HOPPING_SAP,
        // Mort Myre Fungus
        WALKING_TO_MMF_AREA,
        COLLECTING_MMF,
        BANKING_MMF,
        RETURNING_MMF_AREA,
        //Seaweed
        COLLECTING_SW,
        // Normal Planks
        WALKING_TO_PL_AREA,
        COLLECTING_PL,
        BANKING_PL,
        RETURNING_PL_AREA,
        HOPPING_PL,
        // Blue Dragon Scales
        WALKING_TO_BDS_AREA,
        COLLECTING_BDS,
        BANKING_BDS,
        RETURNING_BDS_AREA
    }

    public static State currentState = State.IDLE;
    // Snape Grass
    private static final WorldPoint COLLECTION_AREA_SG = new WorldPoint(1839, 3640, 0);
    private int previousSnapeGrassCount = 0;
    public static int totalSnapeGrassCollected = 0;
    public static long startTimeSnapeGrass = 0;
    // Super Anti-Poison
    private static final WorldPoint COLLECTION_AREA_SAP = new WorldPoint(2467, 3176, 0); 
    private int previousSAPCount = 0;
    public static int totalSAPCollected = 0;
    public static long startTimeSAP = 0;
    // Mort Myre Fungus
    private static final WorldPoint COLLECTION_AREA_MMF = new WorldPoint(3474, 3419, 0);
    private int previousMMFCount = 0;
    public static int totalMMFCollected = 0;
    public static long startTimeMMF = 0;
    // Normal Planks
    private static final WorldPoint COLLECTION_AREA_PL = new WorldPoint(2554, 3575, 0);
    private int previousPlankCount = 0;
    public static int totalPlanksCollected = 0;
    public static long startTimePlanks = 0;
    // Blue Dragon Scales
    private static final WorldPoint COLLECTION_AREA_BD = new WorldPoint(2918, 9794, 0);
    private int previousBDSCount = 0;
    public static int totalBDSCollected = 0;
    public static long startTimeBDS = 0;
    // Seaweed
    private int previousSeaweedCount = 0;
    public static int totalSeaweedCollected = 0;
    public static long startTimeSeaweed = 0;
    public static int totalSporesCollected = 0;

    public static boolean test = false;

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }

        String message = event.getMessage();
        if (message.contains("You've run out of prayer points")) {
            currentState = State.BANKING_MMF;
        }
    }

    public boolean run(CollectorConfig config) {
        Microbot.enableAutoRunOn = false;
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (currentState == State.IDLE) {
                    if (config.enableSeaweed()) {
                        currentState = State.COLLECTING_SW;
                    } else {
                        switch (config.collectionType()) {
                            case SNAPE_GRASS:
                                currentState = State.WALKING_TO_SG_AREA;
                                break;
                            case SUPERANTIPOISON:
                                currentState = State.WALKING_TO_SAP_AREA;
                                break;
                            case MORT_MYRE_FUNGUS:
                                currentState = State.WALKING_TO_MMF_AREA;
                                break;
                            case PLANKS:
                                currentState = State.WALKING_TO_PL_AREA;
                                break;
                            case BLUE_DRAGON_SCALES:
                                currentState = State.WALKING_TO_BDS_AREA;
                                break;
                            default:
                                currentState = State.IDLE;
                                break;
                        }
                    }
                } else if (currentState == State.COLLECTING_SW && !config.enableSeaweed()) {
                    currentState = State.IDLE;
                }

                switch (currentState) {
                    // Snape Grass
                    case WALKING_TO_SG_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_SG, 0)) {
                            currentState = State.COLLECTING_SG;
                        }
                        break;

                    case COLLECTING_SG:
                        if (Rs2Inventory.isFull()) {
                            currentState = State.BANKING_SG;
                            return;
                        }

                        if (startTimeSnapeGrass == 0) {
                            startTimeSnapeGrass = System.currentTimeMillis();
                        }

                        if (Rs2GroundItem.loot("Snape grass", 10)) {
                            int currentCount = Rs2Inventory.count("Snape grass");
                            sleepUntil(() -> Rs2Inventory.count("Snape grass") > currentCount, 5000);
                            totalSnapeGrassCollected += Rs2Inventory.count("Snape grass") - currentCount;
                            previousSnapeGrassCount = currentCount;
                        }
                        break;

                    case BANKING_SG:
                    
                        if (Rs2Bank.walkToBankAndUseBank(BankLocation.VINERY_BANK)) {
                                Rs2Bank.depositAll();
                                Rs2Bank.closeBank();
                                currentState = State.RETURNING_SG_AREA;
                        }
                        break;
                        
                    case RETURNING_SG_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_SG, 0)) {
                            currentState = State.COLLECTING_SG;
                        }
                        break;

                    // Super Anti-Poison
                    case WALKING_TO_SAP_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_SAP, 0)) {
                            currentState = State.COLLECTING_SAP;
                        }
                        break;

                    case COLLECTING_SAP:
                        if (Rs2Inventory.isFull()) {
                            currentState = State.BANKING_SAP;
                            return;
                        }

                        if (startTimeSAP == 0) {
                            startTimeSAP = System.currentTimeMillis();
                        }

                        // Check if there's any super anti-poison on the ground
                        if (!Rs2GroundItem.exists("Superantipoison(1)", 10)) {
                            currentState = State.HOPPING_SAP;
                            return;
                        }

                        if (Rs2GroundItem.loot("Superantipoison(1)", 10)) {
                            int currentCount = Rs2Inventory.count("Superantipoison(1)");
                            sleepUntil(() -> Rs2Inventory.count("Superantipoison(1)") > currentCount, 5000);
                            totalSAPCollected += Rs2Inventory.count("Superantipoison(1)") - currentCount;
                            previousSAPCount = currentCount;

                            // Decant potions if we have more than 1 slot left and other potions exist
                            if (Rs2Inventory.getEmptySlots() > 1) {
                                // Find a potion to decant into (1-3 dose)
                                String potionToDecantInto = null;
                                if (Rs2Inventory.hasItem("Superantipoison(3)")) {
                                    potionToDecantInto = "Superantipoison(3)";
                                } else if (Rs2Inventory.hasItem("Superantipoison(2)")) {
                                    potionToDecantInto = "Superantipoison(2)";
                                } else if (Rs2Inventory.count("Superantipoison(1)") > 1) {
                                    potionToDecantInto = "Superantipoison(1)";
                                }

                                if (potionToDecantInto != null) {
                                    Rs2Inventory.combine("Superantipoison(1)", potionToDecantInto);
                                    sleep(1000);
                                }
                            }

                            Rs2Inventory.drop("Vial");
                            sleep(1000);

                            currentState = State.HOPPING_SAP;
                        }
                        break;

                    case HOPPING_SAP:
                        int randomWorld = Login.getRandomWorld(true);
                        Microbot.hopToWorld(randomWorld);
                        sleep((int) (Math.random() * 2000) + 3000); // Random sleep between 3-5 seconds
                        currentState = State.COLLECTING_SAP;
                        break;

                    case BANKING_SAP:
                        if (Rs2Bank.walkToBankAndUseBank(BankLocation.CASTLE_WARS)) {
                            Rs2Bank.depositAll("Superantipoison(1)");
                            sleep((int) (Math.random() * 500) + 700);
                            // Equip new dueling ring if needed
                            if (!Rs2Equipment.isWearing("Ring of dueling")) {
                                Rs2Bank.withdrawAndEquip(2552);
                                sleepUntil(() -> Rs2Equipment.isWearing("Ring of dueling"));
                            }
                            sleep((int) (Math.random() * 500) + 700);
                            Rs2Bank.closeBank();
                            currentState = State.RETURNING_SAP_AREA;
                        }
                        break;
                        
                    case RETURNING_SAP_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_SAP, 0)) {
                            currentState = State.COLLECTING_SAP;
                        }
                        break;

                    // Mort Myre Fungus
                    case WALKING_TO_MMF_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_MMF, 0)) {
                            currentState = State.COLLECTING_MMF;
                        }
                        break;

                    case COLLECTING_MMF:

                        if (startTimeMMF == 0) {
                            startTimeMMF = System.currentTimeMillis();
                        }

                        // Check if we're out of prayer points or if full inventory
                        if (!Rs2Player.hasPrayerPoints() || Rs2Inventory.isFull()) {
                            currentState = State.BANKING_MMF;
                            return;
                        }

                        // Cast Bloom on Silver sickle (b) if no fungi are available
                        TileObject rottenLog = Rs2GameObject.findObjectById(3509);
                        if (rottenLog == null || !Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(rottenLog.getId()), "Pick")) {
                            // Make sure we're at the MMF location before casting
                            if (!Rs2Player.getWorldLocation().equals(COLLECTION_AREA_MMF)) {
                                currentState = State.WALKING_TO_MMF_AREA;
                                return;
                            }
                            if (Rs2Inventory.hasItem("Silver sickle (b)")) {
                                Rs2Inventory.interact("Silver sickle (b)", "Cast Bloom");
                                sleep(1000);
                            }
                            return;
                        }

                        // Keep picking fungi from rotten logs within 5 tiles
                        if (Rs2GameObject.interact(3509, "Pick")) {
                            int currentCount = Rs2Inventory.count("Mort myre fungus");
                            sleepUntil(() -> Rs2Inventory.count("Mort myre fungus") > currentCount, 5000);
                            totalMMFCollected += Rs2Inventory.count("Mort myre fungus") - currentCount;
                            previousMMFCount = currentCount;
                            // Add random delay between 200-400ms after picking
                            sleep((int) (Math.random() * 200) + 200);
                        }
                        break;

                    case BANKING_MMF:
                        if (Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE)) {
                            sleep((int) (Math.random() * 250) + 350);
                            Rs2Bank.depositAll("Mort myre fungus");
                            sleep((int) (Math.random() * 500) + 700);
                            // Equip new dueling ring if needed
                            if (!Rs2Equipment.isWearing("Ring of dueling")) {
                                Rs2Bank.withdrawAndEquip(2552);
                                sleepUntil(() -> Rs2Equipment.isWearing("Ring of dueling"));
                            }
                            Rs2Bank.closeBank();
                            sleep((int) (Math.random() * 500) + 700);
                            // Use Pool of Refreshment to restore prayer points
                            TileObject pool = Rs2GameObject.findObjectById(39651);
                            if (pool != null) {
                                int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
                                Rs2GameObject.interact(pool, "Drink");
                                sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > currentPrayer, 5000);
                            }
                            sleep((int) (Math.random() * 250) + 350);
                            currentState = State.RETURNING_MMF_AREA;
                        }
                        break;
                        
                    case RETURNING_MMF_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_MMF, 0)) {
                            currentState = State.COLLECTING_MMF;
                        }
                        break;

                    // Seaweed
                    case COLLECTING_SW:
                        if (startTimeSeaweed == 0) {
                            startTimeSeaweed = System.currentTimeMillis();
                        }

                        // First check for seaweed spores
                        if (Rs2GroundItem.exists("Seaweed spore", 30)) {
                            int initialSporeCount = Rs2Inventory.count("Seaweed spore");
                            if (Rs2GroundItem.loot("Seaweed spore", 30)) {
                                sleep(1000); // Give time for the item to be picked up
                                int newSporeCount = Rs2Inventory.count("Seaweed spore");
                                if (newSporeCount > initialSporeCount) {
                                    totalSporesCollected += (newSporeCount - initialSporeCount);
                                } else {
                                    totalSporesCollected++; 
                                }
                                sleepUntil(() -> !Rs2GroundItem.exists("Seaweed spore", 30), 5000);
                            }
                            return;
                        }

                        // If no spores, proceed with alching if enabled
                        if (config.enableSeaweedAlching() && !config.alchItemName().isEmpty()) {
                            Rs2ItemModel alchItem = Rs2Inventory.get(config.alchItemName());
                            if (alchItem != null) {
                                Rs2Magic.alch(alchItem);
                                sleep((int) (1800 + Math.random() * 218)); 
                            }
                        }
                        break;

                    // Normal Planks
                    case WALKING_TO_PL_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_PL, 0)) {
                            currentState = State.COLLECTING_PL;
                        }
                        break;

                    case COLLECTING_PL:
                        if (Rs2Inventory.isFull()) {
                            currentState = State.BANKING_PL;
                            return;
                        }

                        if (startTimePlanks == 0) {
                            startTimePlanks = System.currentTimeMillis();
                        }

                        // Check if there are planks on the ground
                        if (!Rs2GroundItem.exists("Plank", 10)) {
                            currentState = State.HOPPING_PL;
                            return;
                        }

                        if (Rs2GroundItem.loot("Plank", 20)) {
                            int currentCount = Rs2Inventory.count("Plank");
                            sleepUntil(() -> Rs2Inventory.count("Plank") > currentCount, 5000);
                            totalPlanksCollected += Rs2Inventory.count("Plank") - currentCount;
                            previousPlankCount = currentCount;
                        }
                        break;

                    case HOPPING_PL:
                        int randomWorld2 = Login.getRandomWorld(true);
                        Microbot.hopToWorld(randomWorld2);
                        sleep((int) (Math.random() * 2000) + 3000); // Random sleep between 3-5 seconds
                        currentState = State.COLLECTING_PL;
                        break;

                    case BANKING_PL:
                        if (Rs2Bank.walkToBankAndUseBank(BankLocation.BARBARIAN_OUTPOST)) {
                            Rs2Bank.depositAll("Plank");
                            sleep((int) (Math.random() * 500) + 700);
                            Rs2Bank.closeBank();
                            currentState = State.RETURNING_PL_AREA;
                        }
                        break;

                    case RETURNING_PL_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_PL, 0)) {
                            currentState = State.COLLECTING_PL;
                        }
                        break;

                        // Blue Dragon Scales
                    case WALKING_TO_BDS_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_BD, 0)) {
                            currentState = State.COLLECTING_BDS;
                        }
                        break;
                    case COLLECTING_BDS:
                        if (Rs2Inventory.isFull()) {
                            currentState = State.BANKING_BDS;
                            return;
                        }

                        if (startTimeBDS == 0) {
                            startTimeBDS = System.currentTimeMillis();
                        }

                        if (Rs2GroundItem.loot("Blue dragon scale", 35)) {
                            int currentCount = Rs2Inventory.count("Blue dragon scale");
                            sleepUntil(() -> Rs2Inventory.count("Blue dragon scale") > currentCount, 5000);
                            totalBDSCollected += Rs2Inventory.count("Blue dragon scale") - currentCount;
                            previousBDSCount = currentCount;
                        }
                        break;
                    case BANKING_BDS:
                        if (Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE)) {
                            sleep((int) (Math.random() * 250) + 350);
                            Rs2Bank.depositAll("Blue dragon scale");
                            sleep((int) (Math.random() * 500) + 700);
                            // Equip new dueling ring if needed
                            if (!Rs2Equipment.isWearing("Ring of dueling")) {
                                Rs2Bank.withdrawAndEquip(2552);
                                sleepUntil(() -> Rs2Equipment.isWearing("Ring of dueling"));
                            }
                            Rs2Bank.closeBank();
                            sleep((int) (Math.random() * 500) + 700);
                             // Use Pool of Refreshment to restore health
                             TileObject pool = Rs2GameObject.findObjectById(39651);
                             if (pool != null) {
                                 int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
                                 Rs2GameObject.interact(pool, "Drink");
                                 sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > currentPrayer, 5000);
                             }
                             sleep((int) (Math.random() * 250) + 350);
                            currentState = State.RETURNING_BDS_AREA;
                        }
                        break;
                    case RETURNING_BDS_AREA:
                        if (Rs2Walker.walkTo(COLLECTION_AREA_BD, 0)) {
                            currentState = State.COLLECTING_BDS;
                        }
                        break;
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0L, 200L, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
