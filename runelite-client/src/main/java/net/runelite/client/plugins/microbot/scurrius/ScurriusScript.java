package net.runelite.client.plugins.microbot.scurrius;

import com.google.inject.Inject;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.scurrius.enums.State;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

public class ScurriusScript extends Script {

    @Inject
    private ScurriusConfig config;

    public static double version = 1.0;

    private long lastEatTime = -1;
    private long lastPrayerTime = -1;
    private static final int EAT_COOLDOWN_MS = 2000;
    private static final int PRAYER_COOLDOWN_MS = 2000;

    final WorldPoint bossLocation = new WorldPoint(3279, 9869, 0);
    final WorldArea fightRoom = new WorldArea(new WorldPoint(3290, 9860, 0), 17, 17);
    public static State state = State.BANKING;
    Rs2NpcModel scurrius = null;
    private State previousState = null;
    private boolean hasLoggedRespawnWait = false;
    private Boolean previousInFightRoom = null;
    private Rs2PrayerEnum currentDefensivePrayer = Rs2PrayerEnum.PROTECT_MELEE;

    // Animation constants
    private static final int MELEE_ATTACK_ANIMATION = 10693;
    private static final int RANGE_ATTACK_ANIMATION = 10695;
    private static final int MAGIC_ATTACK_ANIMATION = 10697;

    public boolean run(ScurriusConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = true;
        List<Integer> importantItems = List.of(config.foodSelection().getId(), config.potionSelection().getItemId(), ItemID.VARROCK_TELEPORT);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();
                long currentTime = System.currentTimeMillis();

                if (state != previousState) {
                    Microbot.log("State changed to: " + getStateDescription(state));
                    previousState = state;
                }

                scurrius = Rs2Npc.getNpc("Scurrius", true);

                boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
                boolean hasPrayerPotions = Rs2Inventory.hasItem("prayer potion") || Rs2Inventory.hasItem("super restore");
                boolean isScurriusPresent = scurrius != null;
                boolean isInFightRoom = isInFightRoom();
                boolean hasLineOfSightWithScurrius = Rs2Npc.hasLineOfSight(scurrius);

                if (previousInFightRoom == null || isInFightRoom != previousInFightRoom) {
                    Microbot.log(isInFightRoom ? "Player has entered the boss room." : "Player has exited the boss room.");
                    previousInFightRoom = isInFightRoom;
                }

                if (!isScurriusPresent && !hasFood && !hasPrayerPotions) {
                    if (isInFightRoom) {
                        if (Rs2Inventory.hasItem(ItemID.VARROCK_TELEPORT)) {
                            state = State.TELEPORT_AWAY;
                        } else {
                            Microbot.log("No teleport available. Attempting to walk to bank (will likely fail).");
                        }
                    } else {
                        state = State.BANKING;
                    }
                }

                if (state == State.FIGHTING) {
                    if (!isScurriusPresent && hasFood && hasPrayerPotions && isInFightRoom) {
                        state = State.WAITING_FOR_BOSS;
                        hasLoggedRespawnWait = false;
                    }
                }

                if (state != State.WAITING_FOR_BOSS) {
                    if (isScurriusPresent && hasFood && hasLineOfSightWithScurrius) {
                        state = State.FIGHTING;
                    }

                    if (isScurriusPresent && !hasFood && Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < 25) {
                        state = State.TELEPORT_AWAY;
                    }

                    if (!isScurriusPresent && !isInFightRoom && hasFood && hasPrayerPotions) {
                        if (!hasRequiredSupplies()) {
                            Microbot.log("Missing supplies, returning to BANKING.");
                            state = State.BANKING;
                        } else {
                            state = State.WALK_TO_BOSS;
                        }
                    }
                }

                switch (state) {
                    case BANKING:
                        boolean isCloseToBank = Rs2Bank.walkToBank();
                        if (isCloseToBank) {
                            Rs2Bank.useBank();
                        }
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.depositAllExcept(importantItems.toArray(new Integer[0]));
                            
                            int requiredFoodAmount = config.foodAmount();
                            int requiredPotionAmount = config.prayerPotionAmount();
                            int requiredTeleports = config.teleportAmount();
                            
                            if (!Rs2Bank.withdrawDeficit(config.foodSelection().getId(), requiredFoodAmount)) {
                                Microbot.showMessage("Missing Food in Bank");
                                shutdown();
                                return;
                            }
                            if (!Rs2Bank.withdrawDeficit(config.potionSelection().getItemId(), requiredPotionAmount)) {
                                Microbot.showMessage("Missing Potion in Bank");
                                shutdown();
                                return;
                            }
                            if (!Rs2Bank.withdrawDeficit(ItemID.VARROCK_TELEPORT, requiredTeleports)) {
                                Microbot.showMessage("Missing Teleports in Bank");
                                shutdown();
                                return;
                            }

                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                        }
                        break;

                    case FIGHTING:
                        handlePrayerLogic();
                        List<WorldPoint> dangerousWorldPoints = Rs2Tile.getDangerousGraphicsObjectTiles()
                                .stream()
                                .map(Pair::getKey)
                                .collect(Collectors.toList());

                        if (!dangerousWorldPoints.isEmpty()) {
                            for (WorldPoint worldPoint : dangerousWorldPoints) {
                                if (Rs2Player.getWorldLocation().equals(worldPoint)) {
                                    final WorldPoint safeTile = findSafeTile(Rs2Player.getWorldLocation(), dangerousWorldPoints);
                                    if (safeTile != null) {
                                        Rs2Walker.walkFastCanvas(safeTile);
                                        Microbot.log("Dodging dangerous area, moving to safe tile at: " + safeTile);
                                    }
                                }
                            }
                        }

                        if (currentTime - lastEatTime > EAT_COOLDOWN_MS) {
                            int minEat = config.minEatPercent();
                            int maxEat = config.maxEatPercent();
                            int randomEatThreshold = ThreadLocalRandom.current().nextInt(minEat, maxEat + 1);

                            if (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < randomEatThreshold && !Rs2Player.isAnimating()) {
                                Rs2Player.eatAt(randomEatThreshold);
                                lastEatTime = currentTime;
                                Microbot.log("Eating food at " + randomEatThreshold + "% health.");
                            }
                        }

                        if (currentTime - lastPrayerTime > PRAYER_COOLDOWN_MS) {
                            int minPrayer = config.minPrayerPercent();
                            int maxPrayer = config.maxPrayerPercent();
                            int randomPrayerThreshold = ThreadLocalRandom.current().nextInt(minPrayer, maxPrayer + 1);

                            if (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) < randomPrayerThreshold && !Rs2Player.isAnimating()) {
                                Rs2Player.drinkPrayerPotionAt(randomPrayerThreshold);
                                lastPrayerTime = currentTime;
                                Microbot.log("Drinking prayer potion at " + randomPrayerThreshold + "% prayer points.");
                            }
                        }

                        Optional<Rs2NpcModel> giantRat = Rs2Npc.getNpcs("giant rat").filter(npc -> !npc.isDead()).findFirst();
                        if (giantRat.isPresent()) {
                            Rs2NpcModel giantRatModel = giantRat.get();
                            boolean didWeAttackAGiantRat = scurrius != null && config.prioritizeRats() && Rs2Npc.attack(giantRatModel);
                            if (didWeAttackAGiantRat) return;
                        }
                        
                        if (!Microbot.getClient().getLocalPlayer().isInteracting()) {
                            Rs2Npc.attack(scurrius);
                        }
                        break;

                    case TELEPORT_AWAY:
                        Rs2Inventory.interact("Varrock teleport", "break");
                        sleepUntil(() -> !isInFightRoom());
                        sleep(600 * 2);
                        disableAllPrayers();
                        state = State.BANKING;
                        break;

                    case WALK_TO_BOSS:
                        if (!hasRequiredSupplies()) {
                            Microbot.log("Missing supplies, restarting pathfinding and returning to Bank.");
                            Rs2Walker.setTarget(null);
                            state = State.BANKING;
                            return;
                        }

                        Rs2Walker.walkTo(bossLocation);
                        String interactionType = config.bossRoomEntryType().getInteractionText();
                        Rs2GameObject.interact(ObjectID.BROKEN_BARS, interactionType);
                        sleepUntil(this::isInFightRoom);
                        break;

                    case WAITING_FOR_BOSS:
                        attemptLooting(config);
                        if (!hasLoggedRespawnWait) {
                            Microbot.log("Waiting for Scurris to respawn...");
                            hasLoggedRespawnWait = true;
                            disableAllPrayers();
                        }
                        if (isScurriusPresent) {
                            state = State.FIGHTING;
                            Microbot.log("Scurris has respawned, switching to FIGHTING.");
                        }
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 400, TimeUnit.MILLISECONDS);
        return true;
    }

    private String getStateDescription(State state) {
        switch (state) {
            case BANKING:
                return "Out of food or prayer potions. Banking.";
            case TELEPORT_AWAY:
                return "No food, no prayer potions, and low health. Teleporting away.";
            case WALK_TO_BOSS:
                return "Scurris is not present, walking to boss.";
            case FIGHTING:
                return "Engaging with Scurris.";
            case WAITING_FOR_BOSS:
                return "Waiting for Scurris to respawn.";
            default:
                return "Unknown state.";
        }
    }

    private boolean isInFightRoom() {
        return fightRoom.contains(Rs2Player.getWorldLocation());
    }

    private WorldPoint findSafeTile(WorldPoint playerLocation, List<WorldPoint> dangerousWorldPoints) {
        List<WorldPoint> nearbyTiles = List.of(
                new WorldPoint(playerLocation.getX() + 1, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() - 1, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() + 1, playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() - 1, playerLocation.getPlane())
        );

        for (WorldPoint tile : nearbyTiles) {
            if (!dangerousWorldPoints.contains(tile) && Rs2Tile.isWalkable(Rs2LocalPoint.fromWorldInstance(tile))) {
                Microbot.log("Found safe tile: " + tile);
                return tile;
            }
        }
        Microbot.log("No safe tile found!");
        return null;
    }

    private boolean hasRequiredSupplies() {
        int foodAmount = config.foodAmount();
        int foodItemId = config.foodSelection().getId();
        int prayerPotionAmount = config.prayerPotionAmount();
        int potionItemId = config.potionSelection().getItemId();

        int currentFoodCount = Rs2Inventory.count(foodItemId);
        if (currentFoodCount < foodAmount) {
            Microbot.log("Not enough food in inventory. Expected: " + foodAmount + ", Found: " + currentFoodCount);
            return false;
        }

        int currentPrayerPotionCount = Rs2Inventory.count(potionItemId);
        if (currentPrayerPotionCount < prayerPotionAmount) {
            Microbot.log("Not enough prayer potions in inventory. Expected: " + prayerPotionAmount + ", Found: " + currentPrayerPotionCount);
            return false;
        }

        return true;
    }

    private void attemptLooting(ScurriusConfig config) {
        List<String> lootItems = parseLootItems(config.lootItems());
        LootingParameters nameParams = new LootingParameters(10, 1, 1, 0, false, true, lootItems.toArray(new String[0]));
        Rs2GroundItem.lootItemsBasedOnNames(nameParams);
        LootingParameters valueParams = new LootingParameters(10, 1, config.lootValueThreshold(), 0, false, true);
        Rs2GroundItem.lootItemBasedOnValue(valueParams);
    }
    private List<String> parseLootItems(String lootFilter) {
        return Arrays.asList(lootFilter.toLowerCase().split(","));
    }

    private void handlePrayerLogic() {
        if (scurrius == null) return;

        int npcAnimation = scurrius.getAnimation();
        Rs2PrayerEnum newDefensivePrayer = null;

        switch (npcAnimation) {
            case MELEE_ATTACK_ANIMATION:
                newDefensivePrayer = Rs2PrayerEnum.PROTECT_MELEE;
                break;
            case RANGE_ATTACK_ANIMATION:
                newDefensivePrayer = Rs2PrayerEnum.PROTECT_RANGE;
                break;
            case MAGIC_ATTACK_ANIMATION:
                newDefensivePrayer = Rs2PrayerEnum.PROTECT_MAGIC;
                break;
        }

        if (newDefensivePrayer != null && newDefensivePrayer != currentDefensivePrayer) {
            switchDefensivePrayer(newDefensivePrayer);
        }
    }

    private void switchDefensivePrayer(Rs2PrayerEnum newDefensivePrayer) {
        if (currentDefensivePrayer != null) {
            Rs2Prayer.toggle(currentDefensivePrayer, false);
        }
        Rs2Prayer.toggle(newDefensivePrayer, true);
        currentDefensivePrayer = newDefensivePrayer;
    }

    public void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        Microbot.log("All prayers disabled to preserve prayer points.");
        currentDefensivePrayer = null;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        disableAllPrayers();
    }
}
