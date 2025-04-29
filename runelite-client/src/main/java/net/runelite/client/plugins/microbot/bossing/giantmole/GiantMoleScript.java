package net.runelite.client.plugins.microbot.bossing.giantmole;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.bosstimer.Boss;
import net.runelite.client.plugins.bosstimer.BossTimersPlugin;
import net.runelite.client.plugins.bosstimer.RespawnTimer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.bossing.giantmole.enums.State;
import net.runelite.client.plugins.microbot.qualityoflife.QoLPlugin;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.player.Rs2Player.isMember;

@Slf4j
public class GiantMoleScript extends Script
{
    public static State state = State.IDLE;

    public static final String VERSION = "0.0.3";

    // Region IDs
    public static final List<Integer> FAlADOR_REGIONS = List.of(11828, 12084);
    public static final List<Integer> MOLE_TUNNEL_REGIONS = List.of(6993, 6992);

    // Important WorldPoints
    public static final WorldPoint FALADOR_PARK = new WorldPoint(2989, 3378, 0);

    public static boolean isWorldOccupied = true;
    public static boolean checkedIfWorldOccupied = false;

    private GiantMoleConfig localConfig;
    private int rockCakeHp = Rs2Random.between(2, 5);

    /**
     * Main runner method for the Giant Mole script.
     */
    public boolean run(GiantMoleConfig config)
    {
        localConfig = config;
        Microbot.enableAutoRunOn = true;
        Microbot.useStaminaPotsIfNeeded = true;
        Microbot.runEnergyThreshold = 5000;
        isWorldOccupied = true;
        checkedIfWorldOccupied = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                long startTime = System.currentTimeMillis();

                // Make sure BossTimersPlugin is running for mole kill timers
                if (!Microbot.isPluginEnabled(BossTimersPlugin.class))
                {
                    Plugin bossingInfo = Microbot.getPluginManager().getPlugins().stream()
                            .filter(x -> x.getClass().getName().equals(BossTimersPlugin.class.getName()))
                            .findFirst()
                            .orElse(null);
                    Microbot.startPlugin(bossingInfo);
                }

                updateState(config);
                handlePotions();
                handleFood(config);
                handleDamageItems(config);

                switch (state)
                {
                    case IDLE:
                        handlePrayer(config);
                        if (isInFalador())
                        {
                            if (!checkedIfWorldOccupied)
                            {
                                checkWorldOccupied();
                            }
                        }
                        else if (isInMoleTunnel())
                        {
                            handleLooting(config);
                        }
                        break;

                    case HOPPING:
                        hopWorlds();
                        break;

                    case ENTERING_MOLE_LAIR:
                        goInsideMoleHill();
                        break;

                    case COMBAT:
                        handlePrayer(config);
                        attackMole();
                        break;

                    case HUNTING:
                        handlePrayer(config);
                        walkToMole();
                        break;

                    case BANKING:
                        handlePrayer(config);
                        handleBanking(config);
                        break;

                    case WALKING_TO_MOLE_HOLE:
                        Rs2Walker.walkTo(FALADOR_PARK, 5);
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                // Optionally log totalTime if you want to measure loop duration
            }
            catch (Exception ex)
            {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }
    @Override
    public void shutdown()
    {
        super.shutdown();
        Rs2Walker.disableTeleports = false;
    }

    /**
     * Updates the script's state based on current conditions.
     */
    public void updateState(GiantMoleConfig config)
    {
        if (needBanking(config))
        {
            state = State.BANKING;
            return;
        }

        if (isInMoleTunnel())
        {
            if (isMoleDead())
            {
                state = State.IDLE;
            }
            else if (isMoleNearby())
            {
                state = State.COMBAT;
            }
            else
            {
                state = State.HUNTING;
            }
        }

        if (isInFalador())
        {
            if (Rs2Player.getWorldLocation().distanceTo2D(FALADOR_PARK) > 10)
            {
                state = State.WALKING_TO_MOLE_HOLE;
                return;
            }

            if (checkedIfWorldOccupied && isWorldOccupied)
            {
                state = State.HOPPING;
                return;
            }

            if (checkedIfWorldOccupied)
            {
                state = State.ENTERING_MOLE_LAIR;
            }
            else
            {
                state = State.IDLE;
            }
        }
    }

    /**
     * Checks if current region is Falador.
     */
    public boolean isInFalador()
    {
        return FAlADOR_REGIONS.contains(Rs2Player.getWorldLocation().getRegionID());
    }

    /**
     * Checks if player is in the Giant Mole tunnel region.
     */
    public static boolean isInMoleTunnel()
    {
        return MOLE_TUNNEL_REGIONS.contains(Rs2Player.getWorldLocation().getRegionID());
    }

    /**
     * Hops to a random world (member/free based on current account).
     */
    public void hopWorlds()
    {
        int randomWorld = Login.getRandomWorld(isMember());
        Microbot.hopToWorld(randomWorld);
        checkedIfWorldOccupied = false;
        state = State.IDLE;
    }

    /**
     * Retrieves the Mole Hill tile object by ID.
     */
    public TileObject getMoleHill()
    {
        return Rs2GameObject.getTileObject(ObjectID.MOLE_HILL);
    }

    /**
     * Uses "Look-inside" on the Mole Hill to check occupancy.
     */
    public void checkWorldOccupied()
    {
        TileObject moleHill = getMoleHill();
        if (moleHill != null)
        {
            Rs2GameObject.interact(moleHill, "Look-inside");
            Global.sleepUntilTrue(() -> checkedIfWorldOccupied, 200, 7000);
        }
    }

    /**
     * Enters the mole lair by digging on the Mole Hill with a spade.
     */
    public void goInsideMoleHill()
    {
        TileObject moleHill = getMoleHill();
        if (moleHill != null)
        {
            if (Rs2Walker.walkTo(moleHill.getWorldLocation(), 0))
            {
                Rs2ItemModel spade = Rs2Inventory.get("Spade");
                Rs2Inventory.interact(spade, "Dig");
            }
        }
    }

    /**
     * Returns the current world point of the mole if in the tunnel (hint arrow).
     */
    public static WorldPoint getMoleLocation()
    {
        if (isInMoleTunnel())
        {
            return Microbot.getClient().getHintArrowPoint();
        }
        return null;
    }

    /**
     * Checks if the mole is within immediate range (i.e. no hint arrow or not needed).
     */
    public static boolean isMoleNearby()
    {
        // If there's no hint arrow, we treat it as "nearby" or not tracked.
        return getMoleLocation() == null;
    }

    /**
     * Checks if the Mole is currently considered 'dead' by the BossTimers plugin (respawn timer).
     */
    public static boolean isMoleDead()
    {
        return Microbot.getInfoBoxManager().getInfoBoxes().stream()
                .anyMatch(t -> t instanceof RespawnTimer && ((RespawnTimer) t).getBoss() == Boss.GIANT_MOLE);
    }

    /**
     * Returns the NPC representing the Giant Mole (via hint arrow).
     */
    public NPC getMole()
    {
        return Microbot.getClient().getHintArrowNpc();
    }

    /**
     * Walks toward the hinted Giant Mole location.
     */
    public void walkToMole()
    {
        WorldPoint moleLocation = getMoleLocation();
        if (moleLocation != null)
        {
            if (!Rs2Walker.disableTeleports)
            {
                Rs2Walker.disableTeleports = true;
            }

            if (ShortestPathPlugin.getPathfinder() != null)
            {
                for (WorldPoint target : ShortestPathPlugin.getPathfinder().getTargets()) {
                    if (!isRunning()) break;
                    if (target.distanceTo2D(moleLocation) > 1) {
                        log.info("Current target: " + target);
                        log.info("New target: " + moleLocation);
                        Rs2Walker.setTarget(moleLocation);
                    }
                }
            }
            else
            {
                Microbot.getClientThread().runOnSeperateThread(() ->
                {
                    Rs2Walker.walkTo(moleLocation);
                    return null;
                });
            }
        }
    }

    /**
     * Attacks the Giant Mole if not already in combat.
     */
    public void attackMole()
    {
        var mole = getMole();
        if (mole != null && !Rs2Combat.inCombat())
        {
            // Mole's "dig" animation is 3314; if it's mid-dig or dead, skip
            if (mole.getAnimation() == 3314 || isMoleDead())
            {
                return;
            }

            // If pathfinder is active, exit it before attacking
            if (ShortestPathPlugin.getPathfinder() != null)
            {
                ShortestPathPlugin.exit();
                sleep(600, 800);
            }

            Rs2Npc.interact(mole, "Attack");
            sleep(600, 800);
        }
    }

    /**
     * Toggles quick prayers when in combat, if enabled by config.
     */
    public void handlePrayer(GiantMoleConfig config)
    {
        if (!config.useQuickPrayer())
        {
            return;
        }

        boolean underAttack = Rs2Npc.getNpcsForPlayer().findAny().isPresent() || Rs2Combat.inCombat();
        Rs2Prayer.toggleQuickPrayer(!isInFalador() && underAttack);
    }

    // Handles food consumption
    public void handleFood(GiantMoleConfig config)
    {
        boolean usingQoLFood = Microbot.getConfigManager().getConfiguration("QoL", "autoEatFood", Boolean.class);
        if (!(Microbot.isPluginEnabled(QoLPlugin.class) && usingQoLFood) && !config.useRockCake())
        {
                Rs2Player.eatAt(Rs2Random.randomGaussian(50, 10));
        }
    }

    /**
     * Handles prayer/combat potion usage if the QoL plugin isn't auto-drinking.
     */
    public void handlePotions()
    {
        boolean usingQoLPrayer = Microbot.getConfigManager().getConfiguration("QoL", "autoDrinkPrayerPot", Boolean.class);

        if (!(Microbot.isPluginEnabled(QoLPlugin.class) && usingQoLPrayer))
        {
            Rs2Player.drinkPrayerPotionAt(Rs2Random.randomGaussian(20, 10));
        }

        // Covers overload-like potions for RANGED, ATTACK, STRENGTH, DEFENCE, MAGIC
        Rs2Player.drinkCombatPotionAt(Skill.RANGED);
        Rs2Player.drinkCombatPotionAt(Skill.ATTACK);
        Rs2Player.drinkCombatPotionAt(Skill.STRENGTH);
        Rs2Player.drinkCombatPotionAt(Skill.DEFENCE);
        Rs2Player.drinkCombatPotionAt(Skill.MAGIC);
    }

    /**
     * Self damages to 1hp
     */
    public void handleDamageItems(GiantMoleConfig config) {
        if (!config.useRockCake()) return;

        if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > rockCakeHp)
            if (!Rs2Inventory.interact(ItemID.DWARVEN_ROCK_CAKE_7510, "Guzzle"))
                if (!Rs2Inventory.interact(ItemID.LOCATOR_ORB, "Feel"))
                    return;

        rockCakeHp = Rs2Random.between(2, 5);
    }

    /**
     * Handles banking logic at Falador East, loading inventory setups, etc.
     */
    public boolean handleBanking(GiantMoleConfig config)
    {
        Rs2Walker.disableTeleports = false;
        if (Rs2Bank.walkToBankAndUseBank(BankLocation.FALADOR_EAST))
        {
            Rs2InventorySetup setup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
            if (setup.loadEquipment() && setup.loadInventory())
            {
                Rs2Bank.closeBank();
                state = State.WALKING_TO_MOLE_HOLE;
            }
        }
        return false;
    }

    /**
     * Returns true if the player needs to bank (e.g., missing potions, full inventory).
     */
    public boolean needBanking(GiantMoleConfig config)
    {
        Rs2InventorySetup inventorySetup = new Rs2InventorySetup(
                config.inventorySetup().getName(),
                mainScheduledFuture
        );


        // (1) If inventory is full, we need to bank
        if (Rs2Inventory.isFull())
        {
            Microbot.log("Inventory is full, banking...");
            return true;
        }

        // (2) For each potion type, check if setup requires it and if we have it
        if (needsPotion(inventorySetup, Rs2Potion.getPrayerPotionsVariants())) {
            Microbot.log("need banking: missing prayer potions");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getRangePotionsVariants())) {
            Microbot.log("need banking: missing range potions");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getCombatPotionsVariants())) {
            Microbot.log("need banking: missing combat potions");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getMagicPotionsVariants())) {
            Microbot.log("need banking: missing magic potions");
            return true;
        }
        if (needsPotion(inventorySetup, Collections.singletonList(Rs2Potion.getStaminaPotion()))) {
            Microbot.log("need banking: missing stamina potion");
            return true;
        }
        boolean restoreNeeded = needsPotion(inventorySetup, Rs2Potion.getRestoreEnergyPotionsVariants());
        if (restoreNeeded) {
            Microbot.log("need banking: missing restore energy potions");
        }
        return restoreNeeded;
    }

    /**
     * Checks if the given setup requires any variant of a specific potion but the player does not have it.
     */
    private static boolean needsPotion(Rs2InventorySetup setup, List<String> potionVariants)
    {
        // If the setup doesn't contain any of these variants, no need to bank for them
        if (!setupHasPotion(setup, potionVariants))
        {
            return false;
        }
        // The setup does require it; if we don't have it, we need to bank
        return !Rs2Inventory.hasItem(potionVariants);
    }

    /**
     * Checks if the given setup has any item matching any of the given potion variants.
     */
    private static boolean setupHasPotion(Rs2InventorySetup setup, List<String> potionVariants)
    {
        return setup.getInventoryItems().stream().anyMatch(item ->
        {
            if (item.getName() == null)
            {
                return false;
            }
            // Strip off any "(x doses)" part
            String itemBaseName = item.getName().split("\\(")[0].trim().toLowerCase(Locale.ENGLISH);

            // Check if that base name matches any potion variant
            return potionVariants.stream().anyMatch(variant ->
            {
                String variantLower = variant.toLowerCase(Locale.ENGLISH);
                return variantLower.contains(itemBaseName);
            });
        });
    }

    // check if we need food
    private static boolean needsFood(Rs2InventorySetup setup)
    {
        // if setup doesn't have any food, no need to bank for it
        if (!setupHasFood(setup))
        {
            return false;
        }
        // setup does require food; if we don't have it, we need to bank
        return Rs2Inventory.getInventoryFood().isEmpty();
    }

    // check if setup has any food
    private static boolean setupHasFood(Rs2InventorySetup setup)
    {
        return setup.getInventoryItems().stream().anyMatch(item ->
        {
            if (item.getName() == null)
            {
                return false;
            }
            // get id of the item
            int itemId = item.getId();
            return
                    Rs2Food.getIds().contains(itemId);
        });
    }



    /**
     * Central method to handle all looting logic.
     */
    private void handleLooting(GiantMoleConfig config)
    {
        // Loot “by name” first
        if (config.looterStyle() == DefaultLooterStyle.MIXED || config.looterStyle() == DefaultLooterStyle.ITEM_LIST)
        {
            lootItemsOnName(config);
        }

        // Loot by value
        if (config.looterStyle() == DefaultLooterStyle.MIXED || config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE)
        {
            lootItemsByValue(config);
        }

        // Loot arrows
        lootArrows(config);

        // Loot bones
        lootBones(config);

        // Loot runes
        lootRunes(config);

        // Loot coins
        lootCoins(config);

        // Loot untradables
        lootUntradeableItems(config);
    }

    private void lootArrows(GiantMoleConfig config)
    {
        if (config.toggleLootArrows())
        {
            LootingParameters arrowParams = new LootingParameters(
                    10, 1, 10, 0, false, config.toggleOnlyLootMyItems(), "arrow"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(arrowParams))
            {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootBones(GiantMoleConfig config)
    {
        if (config.toggleBuryBones())
        {
            LootingParameters bonesParams = new LootingParameters(
                    10, 1, 1, 0, false, config.toggleOnlyLootMyItems(), "bones"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(bonesParams))
            {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootRunes(GiantMoleConfig config)
    {
        if (config.toggleLootRunes())
        {
            LootingParameters runesParams = new LootingParameters(
                    10, 1, 1, 0, false, config.toggleOnlyLootMyItems(), " rune"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(runesParams))
            {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootCoins(GiantMoleConfig config)
    {
        if (config.toggleLootCoins())
        {
            LootingParameters coinsParams = new LootingParameters(
                    10, 1, 1, 0, false, config.toggleOnlyLootMyItems(), "coins"
            );
            if (Rs2GroundItem.lootCoins(coinsParams))
            {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootUntradeableItems(GiantMoleConfig config)
    {
        if (config.toggleLootUntradables())
        {
            LootingParameters untradeableItemsParams = new LootingParameters(
                    10, 1, 1, 0, false, config.toggleOnlyLootMyItems(), "untradeable"
            );
            if (Rs2GroundItem.lootUntradables(untradeableItemsParams))
            {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootItemsByValue(GiantMoleConfig config)
    {
        LootingParameters valueParams = new LootingParameters(
                config.minPriceOfItemsToLoot(),
                config.maxPriceOfItemsToLoot(),
                10,
                1,
                0,
                false,
                config.toggleOnlyLootMyItems()
        );
        if (Rs2GroundItem.lootItemBasedOnValue(valueParams))
        {
            Microbot.pauseAllScripts = false;
        }
    }

    private void lootItemsOnName(GiantMoleConfig config)
    {
        LootingParameters valueParams = new LootingParameters(
                10,
                1,
                1,
                0,
                false,
                config.toggleOnlyLootMyItems(),
                config.listOfItemsToLoot().trim().split(",")
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams))
        {
            Microbot.pauseAllScripts = false;
        }
    }
}
