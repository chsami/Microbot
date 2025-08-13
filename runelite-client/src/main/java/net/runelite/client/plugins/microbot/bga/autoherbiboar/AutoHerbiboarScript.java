package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import lombok.Setter;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.herbiboars.HerbiboarPlugin;
import net.runelite.client.plugins.herbiboars.HerbiboarSearchSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoHerbiboarScript extends Script {
    @Setter
    private HerbiboarPlugin herbiboarPlugin;
    private AutoHerbiboarState state = AutoHerbiboarState.INITIALIZING;
    private boolean attackedTunnel;
    private static final WorldPoint BANK_LOCATION = new WorldPoint(3769, 3898, 0);
    private static final WorldPoint RETURN_LOCATION = new WorldPoint(3727, 3892, 0);
    

    public AutoHerbiboarState getCurrentState() {
        return state;
    }
    
    
    private boolean isNearBank() {
        return Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION) <= 5;
    }
    
    private void manageRunEnergy(AutoHerbiboarConfig config) {
        if (Microbot.getClient().getEnergy() >= 20) return;
        
        AutoHerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();
        switch (energyOption) {
            case STAMINA_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA)) {
                    Rs2Inventory.interact(ItemID._4DOSESTAMINA, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._3DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._2DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._1DOSESTAMINA, "Drink");
                    }
                }
                break;
            case SUPER_ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE2ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE2ENERGY, "Drink");
                    }
                }
                break;
            case ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE1ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE1ENERGY, "Drink");
                    }
                }
                break;
            case STRANGE_FRUIT:
                if (Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT)) {
                    Rs2Inventory.interact(ItemID.MACRO_TRIFFIDFRUIT, "Eat");
                }
                break;
            case NONE:
            default:
                break;
        }
    }
    
    private void dropConfiguredItems(AutoHerbiboarConfig config) {
        if (config.dropSmallFossil()) {
            dropIfPresent(ItemID.FOSSIL_SMALL_UNID, ItemID.FOSSIL_SMALL_1, ItemID.FOSSIL_SMALL_2, 
                         ItemID.FOSSIL_SMALL_3, ItemID.FOSSIL_SMALL_4, ItemID.FOSSIL_SMALL_5);
        }
        if (config.dropMediumFossil()) {
            dropIfPresent(ItemID.FOSSIL_MEDIUM_UNID, ItemID.FOSSIL_MEDIUM_1, ItemID.FOSSIL_MEDIUM_2, 
                         ItemID.FOSSIL_MEDIUM_3, ItemID.FOSSIL_MEDIUM_4, ItemID.FOSSIL_MEDIUM_5);
        }
        if (config.dropLargeFossil()) {
            dropIfPresent(ItemID.FOSSIL_LARGE_UNID, ItemID.FOSSIL_LARGE_1, ItemID.FOSSIL_LARGE_2, 
                         ItemID.FOSSIL_LARGE_3, ItemID.FOSSIL_LARGE_4, ItemID.FOSSIL_LARGE_5);
        }
        if (config.dropRareFossil()) {
            dropIfPresent(ItemID.FOSSIL_RARE_UNID, ItemID.FOSSIL_RARE_1, ItemID.FOSSIL_RARE_2, 
                         ItemID.FOSSIL_RARE_3, ItemID.FOSSIL_RARE_4, ItemID.FOSSIL_RARE_5, ItemID.FOSSIL_RARE_6);
        }
        if (config.dropGuam()) {
            dropIfPresent(ItemID.UNIDENTIFIED_GUAM);
        }
        if (config.dropMarrentill()) {
            dropIfPresent(ItemID.UNIDENTIFIED_MARENTILL);
        }
        if (config.dropTarromin()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TARROMIN);
        }
        if (config.dropHarralander()) {
            dropIfPresent(ItemID.UNIDENTIFIED_HARRALANDER);
        }
        if (config.dropRanarr()) {
            dropIfPresent(ItemID.UNIDENTIFIED_RANARR);
        }
        if (config.dropToadflax()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TOADFLAX);
        }
        if (config.dropIrit()) {
            dropIfPresent(ItemID.UNIDENTIFIED_IRIT);
        }
        if (config.dropAvantoe()) {
            dropIfPresent(ItemID.UNIDENTIFIED_AVANTOE);
        }
        if (config.dropKwuarm()) {
            dropIfPresent(ItemID.UNIDENTIFIED_KWUARM);
        }
        if (config.dropSnapdragon()) {
            dropIfPresent(ItemID.UNIDENTIFIED_SNAPDRAGON);
        }
        if (config.dropCadantine()) {
            dropIfPresent(ItemID.UNIDENTIFIED_CADANTINE);
        }
        if (config.dropLantadyme()) {
            dropIfPresent(ItemID.UNIDENTIFIED_LANTADYME);
        }
        if (config.dropDwarfWeed()) {
            dropIfPresent(ItemID.UNIDENTIFIED_DWARF_WEED);
        }
        if (config.dropTorstol()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TORSTOL);
        }
    }
    
    private void dropIfPresent(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) {
                Rs2Inventory.drop(itemId);
            }
        }
    }

    private boolean needsToBank(AutoHerbiboarConfig config) {
        return Rs2Inventory.isFull() || !hasRequiredInventorySetup(config);
    }

    private boolean hasRequiredInventorySetup(AutoHerbiboarConfig config) {
        AutoHerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();
        boolean hasEnergyItems = true;
        switch (energyOption) {
            case STAMINA_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA);
                break;
            case SUPER_ENERGY_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY);
                break;
            case ENERGY_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY);
                break;
            case STRANGE_FRUIT:
                hasEnergyItems = Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT);
                break;
            case NONE:
            default:
                hasEnergyItems = true;
                break;
        }
        boolean hasHerbSack = true;
        if (config.useHerbSack()) {
            hasHerbSack = Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN);
        }
        boolean hasMagicSecateurs = true;
        if (config.useMagicSecateurs()) {
            hasMagicSecateurs = Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS) ||
                               Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS);
        }
        
        return hasEnergyItems && hasHerbSack && hasMagicSecateurs;
    }

    public boolean run(AutoHerbiboarConfig config) {
        Microbot.enableAutoRunOn = false;
        state = AutoHerbiboarState.INITIALIZING;
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (herbiboarPlugin == null) return;
                
                if (!Rs2Player.isMoving() && !Rs2Player.isInteracting()) {
                    dropConfiguredItems(config);
                    manageRunEnergy(config);
                }
                
                if (needsToBank(config)) {
                    state = AutoHerbiboarState.BANK;
                } else if (hasRequiredInventorySetup(config) && isNearBank() && 
                          (state == AutoHerbiboarState.START || state == AutoHerbiboarState.INITIALIZING)) {
                    state = AutoHerbiboarState.RETURN_FROM_ISLAND;
                }
                if ((Rs2Player.isMoving() || Rs2Player.isInteracting()) && 
                    (state == AutoHerbiboarState.START || state == AutoHerbiboarState.TRAIL || 
                     state == AutoHerbiboarState.TUNNEL || state == AutoHerbiboarState.HARVEST)) {
                    return;
                }
                
                switch (state) {
                    case INITIALIZING:
                        Microbot.status = "Starting...";
                        state = AutoHerbiboarState.START;
                        break;
                    case START:
                        Microbot.status = "Finding start location";
                        if (herbiboarPlugin.getCurrentGroup() == null) {
                            TileObject start = herbiboarPlugin.getStarts().values().stream().findFirst().orElse(null);
                            if (start != null) {
                                WorldPoint loc = start.getWorldLocation();
                                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), loc);
                                if (localPoint == null) {
                                    Rs2Walker.walkTo(loc);
                                } else if (Rs2Player.getWorldLocation().distanceTo(loc) >= 50) {
                                    Rs2Walker.walkTo(loc);
                                } else {
                                    Rs2GameObject.interact(start, "Search");
                                    Rs2Player.waitForAnimation();
                                    sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 1000);
                                }
                            }
                        } else {
                            state = AutoHerbiboarState.TRAIL;
                        }
                        break;
                    case TRAIL:
                        Microbot.status = "Following trail";
                        if (herbiboarPlugin.getFinishId() > 0) { 
                            state = AutoHerbiboarState.TUNNEL; 
                            break; 
                        }
                        List<HerbiboarSearchSpot> path = herbiboarPlugin.getCurrentPath();
                        if (!path.isEmpty()) {
                            WorldPoint loc = path.get(path.size() - 1).getLocation();
                            LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), loc);
                            TileObject object = herbiboarPlugin.getTrailObjects().get(loc);
                            if (localPoint == null) {
                                Rs2Walker.walkTo(loc);
                            } else if (Rs2Player.getWorldLocation().distanceTo(loc) >= 50) {
                                Rs2Walker.walkTo(loc);
                            } else {
                                Rs2GameObject.interact(object, "Search");
                                Rs2Player.waitForAnimation();
                                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 1000);
                            }
                        }
                        break;
                    case TUNNEL:
                        Microbot.status = "Attacking tunnel";
                        if (!attackedTunnel) {
                            int finishId = herbiboarPlugin.getFinishId();
                            if (finishId > 0) {
                                WorldPoint finishLoc = herbiboarPlugin.getEndLocations().get(finishId - 1);
                                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), finishLoc);
                                TileObject tunnel = herbiboarPlugin.getTunnels().get(finishLoc);
                                if (localPoint == null) {
                                    Rs2Walker.walkTo(finishLoc);
                                } else if (Rs2Player.getWorldLocation().distanceTo(finishLoc) >= 50) {
                                    Rs2Walker.walkTo(finishLoc);
                                } else {
                                    attackedTunnel = Rs2GameObject.interact(tunnel, "Attack") || Rs2GameObject.interact(tunnel, "Search");
                                    if (attackedTunnel) {
                                        Rs2Player.waitForAnimation();
                                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 5000);
                                    }
                                }
                            }
                        } else {
                            Rs2NpcModel herb = Rs2Npc.getNpc("Herbiboar");
                            if (herb != null) state = AutoHerbiboarState.HARVEST;
                        }
                        break;
                    case HARVEST:
                        Microbot.status = "Harvesting herbiboar";
                        Rs2NpcModel herb = Rs2Npc.getNpc("Herbiboar");
                        if (herb != null) {
                            WorldPoint loc = herb.getWorldLocation();
                            if (Rs2Player.getWorldLocation().distanceTo(loc) <= 8) {
                                Rs2Npc.interact(herb, "Harvest");
                                Rs2Player.waitForAnimation();
                                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                                TileObject start = herbiboarPlugin.getStarts().values().stream().findFirst().orElse(null);
                                if (start != null) {
                                    WorldPoint startLoc = start.getWorldLocation();
                                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), startLoc);
                                    if (localPoint == null) {
                                        Rs2Walker.walkTo(startLoc);
                                    } else if (Rs2Player.getWorldLocation().distanceTo(startLoc) >= 50) {
                                        Rs2Walker.walkTo(startLoc);
                                    } else {
                                        Rs2GameObject.interact(start, "Search");
                                    }
                                }
                                attackedTunnel = false;
                                if (needsToBank(config)) {
                                    state = AutoHerbiboarState.BANK;
                                } else {
                                    state = AutoHerbiboarState.START;
                                }
                            }
                        }
                        break;
                    case BANK:
                        Microbot.status = "Banking items";
                        LocalPoint bankLocalPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), BANK_LOCATION);
                        if (bankLocalPoint == null) {
                            Rs2Walker.walkTo(BANK_LOCATION);
                        } else if (Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION) >= 5) {
                            Rs2Walker.walkTo(BANK_LOCATION);
                        } else if (!Rs2Bank.isOpen()) {
                            Rs2Bank.openBank();
                        } else {
                            Rs2Bank.depositAll();
                            sleepUntil(Rs2Inventory::isEmpty, 1000);
                            if (config.useHerbSack() && !Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN)) {
                                if (Rs2Bank.hasItem(ItemID.SLAYER_HERB_SACK)) {
                                    Rs2Bank.withdrawX(ItemID.SLAYER_HERB_SACK, 1);
                                } else if (Rs2Bank.hasItem(ItemID.SLAYER_HERB_SACK_OPEN)) {
                                    Rs2Bank.withdrawX(ItemID.SLAYER_HERB_SACK_OPEN, 1);
                                }
                                sleepUntil(() -> Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN), 1000);
                            }
                            if (config.useMagicSecateurs() && !Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS) && !Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                                Rs2Bank.withdrawX(ItemID.FAIRY_ENCHANTED_SECATEURS, 1);
                                sleepUntil(() -> Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS), 1000);
                            }
                            AutoHerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();
                            switch (energyOption) {
                                case STAMINA_POTION:
                                    Rs2Bank.withdrawX(ItemID._4DOSESTAMINA, 6);
                                    sleepUntil(() -> (Rs2Inventory.count(ItemID._4DOSESTAMINA) + Rs2Inventory.count(ItemID._3DOSESTAMINA) + Rs2Inventory.count(ItemID._2DOSESTAMINA) + Rs2Inventory.count(ItemID._1DOSESTAMINA)) >= 24, 3000);
                                    break;
                                case SUPER_ENERGY_POTION:
                                    Rs2Bank.withdrawX(ItemID._4DOSE2ENERGY, 6);
                                    sleepUntil(() -> (Rs2Inventory.count(ItemID._4DOSE2ENERGY) + Rs2Inventory.count(ItemID._3DOSE2ENERGY) + Rs2Inventory.count(ItemID._2DOSE2ENERGY) + Rs2Inventory.count(ItemID._1DOSE2ENERGY)) >= 24, 3000);
                                    break;
                                case ENERGY_POTION:
                                    Rs2Bank.withdrawX(ItemID._4DOSE1ENERGY, 6);
                                    sleepUntil(() -> (Rs2Inventory.count(ItemID._4DOSE1ENERGY) + Rs2Inventory.count(ItemID._3DOSE1ENERGY) + Rs2Inventory.count(ItemID._2DOSE1ENERGY) + Rs2Inventory.count(ItemID._1DOSE1ENERGY)) >= 24, 3000);
                                    break;
                                case STRANGE_FRUIT:
                                    Rs2Bank.withdrawX(ItemID.MACRO_TRIFFIDFRUIT, 6);
                                    sleepUntil(() -> Rs2Inventory.count(ItemID.MACRO_TRIFFIDFRUIT) >= 6, 1000);
                                    break;
                                case NONE:
                                default:
                                    break;
                            }
                            
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                            state = AutoHerbiboarState.RETURN_FROM_ISLAND;
                        }
                        break;
                    case RETURN_FROM_ISLAND:
                        Microbot.status = "Returning to island";
                        LocalPoint returnLocalPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), RETURN_LOCATION);
                        if (returnLocalPoint == null) {
                            Rs2Walker.walkTo(RETURN_LOCATION);
                        } else if (Rs2Player.getWorldLocation().distanceTo(RETURN_LOCATION) >= 5) {
                            Rs2Walker.walkTo(RETURN_LOCATION);
                        } else {
                            state = AutoHerbiboarState.START;
                        }
                        break;
                }
            } catch (Exception ex) {
                System.err.println("AutoHerbiboar error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override
    public void shutdown() {
        Microbot.status = "IDLE";
        super.shutdown();
    }
}