package net.runelite.client.plugins.microbot.maxxin.astralrc;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.maxxin.MXUtil;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AstralRunesScript extends Script {
    public static String version = "0.0.1";
    private final AstralRunesPlugin plugin;

    private final static List<Integer> LUNAR_ISLE_REGION_IDS = List.of(8509, 8508, 8253);
    private static final WorldPoint SEAL_OF_PASSAGE_BANKER = new WorldPoint(2098, 3920, 0);
    private static final WorldPoint DREAM_MENTOR_BANKER = new WorldPoint(2099, 3920, 0);
    private final static WorldPoint LUNAR_ISLE_BANK_WORLD_POINT = new WorldPoint(2099, 3918, 0);
    private final static WorldPoint LUNAR_ISLE_CRAFT_WORLD_POINT = new WorldPoint(2156, 3864, 0);

    public final int runeItemId = ItemID.ASTRALRUNE;
    public static int runesForSession = 0;
    public static int totalTrips = 0;

    private enum State {
        BANKING,
        CRAFTING,
        REPAIRING;
    }

    private State state = State.BANKING;

    public AstralRunesScript(final AstralRunesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(AstralRunesConfig config) {
        Microbot.pauseAllScripts = false;
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                var regionId = Rs2Player.getWorldLocation().getRegionID();
                if( !LUNAR_ISLE_REGION_IDS.contains(regionId) ) {
                    plugin.setDebugText1("Region ID: " + regionId);
                    Microbot.showMessage("Start bot in Lunar Isle");
                    shutdown();
                    return;
                }

                if(!Rs2Magic.isLunar()) {
                    plugin.setDebugText1("Is Lunar Spellbook: " + Rs2Magic.isLunar());
                    Microbot.showMessage("Set spellbook to Lunar Spellbook");
                    shutdown();
                    return;
                }

                var dreamMentorComplete = Rs2Player.getQuestState(Quest.DREAM_MENTOR) == QuestState.FINISHED;
                if(!dreamMentorComplete && !(Rs2Inventory.hasItem(ItemID.LUNAR_SEAL_OF_PASSAGE) || Rs2Equipment.hasEquipped(ItemID.LUNAR_SEAL_OF_PASSAGE))) {
                    plugin.setDebugText1("Has Seal of Passage: " + Rs2Inventory.hasItem(ItemID.LUNAR_SEAL_OF_PASSAGE));
                    Microbot.showMessage("No Seal of Passage found equipped or in inventory");
                    shutdown();
                    return;
                }

                if( !Rs2Inventory.hasRunePouch() ) {
                    Microbot.showMessage("No Rune pouch found");
                    shutdown();
                    return;
                }

                if( !Rs2Magic.canCast(MagicAction.MOONCLAN_TELEPORT) ) {
                    Microbot.showMessage("Required runes not found, make sure Dust staff is equipped and Rune pouch contains Law and Astral runes.");
                    shutdown();
                    return;
                }

                if( !Rs2Magic.canCast(MagicAction.MOONCLAN_TELEPORT) && Rs2Inventory.contains(ItemID.RCU_POUCH_COLOSSAL) ) {
                    Microbot.showMessage("Required runes not found, make sure Dust staff is equipped and Rune pouch contains Law, Astral, and Cosmic runes.");
                    shutdown();
                    return;
                }

                plugin.setDebugText2(state.toString());

                var playerLoc = Rs2Player.getWorldLocation();

                MXUtil.switchInventoryTabIfNeeded();
                MXUtil.closeWorldMapIfNeeded();

                var distToCraftPoint = playerLoc.distanceTo(LUNAR_ISLE_CRAFT_WORLD_POINT);

                switch (state) {
                    case REPAIRING:
                        if( Rs2Inventory.hasDegradedPouch() ) {
                            if( Rs2Bank.isOpen() ) {
                                Rs2Bank.closeBank();
                            }

                            MXUtil.closeWorldMapIfNeeded();
                            if( Rs2Magic.hasRequiredRunes(Rs2Spells.NPC_CONTACT) ) {
                                Rs2Magic.repairPouchesWithLunar();
                                Rs2Inventory.checkPouches();
                            } else {
                                Microbot.showMessage("No runes found for NPC Contact");
                                shutdown();
                                return;
                            }
                        } else {
                            state = State.BANKING;
                        }
                        break;
                    case BANKING:
                        if( Rs2Inventory.hasDegradedPouch() ) {
                            state = State.REPAIRING;
                            return;
                        }

                        if( Rs2Inventory.allPouchesFull() && Rs2Inventory.getEmptySlots() < 1 && Rs2Inventory.hasItem(ItemID.BLANKRUNE_HIGH) ) {
                            state = State.CRAFTING;
                            return;
                        }

                        MXUtil.closeWorldMapIfNeeded();

                        if( !Rs2Bank.isOpen() ) {
                            if( playerLoc.distanceTo(LUNAR_ISLE_BANK_WORLD_POINT) > 20 ) {
                                Rs2Magic.cast(MagicAction.MOONCLAN_TELEPORT);
                                sleep(2200);
                                Rs2Walker.walkFastCanvas(new WorldPoint(2107, 3915, 0));
                            }

                            MXUtil.switchInventoryTabIfNeeded();
                            var bankTileLoc = !dreamMentorComplete ? SEAL_OF_PASSAGE_BANKER : DREAM_MENTOR_BANKER;
                            var bankTile = Rs2GameObject.findGameObjectByLocation(bankTileLoc);
                            Rs2Walker.walkFastCanvas(LUNAR_ISLE_BANK_WORLD_POINT);
                            if( bankTile != null && !Rs2Bank.isOpen() ) {
                                Rs2Bank.openBank(bankTile);
                                if( Rs2Inventory.hasItem(runeItemId) ) {
                                    updateRuneStates();
                                    Rs2Bank.depositAll(runeItemId);
                                }
                            } else if( Rs2Player.distanceTo(bankTileLoc) > 2 ) {
                                Rs2Walker.walkFastCanvas(bankTileLoc);
                            } else if( bankTile == null ) {
                                Rs2Bank.openBank();
                            }
                            return;
                        }

                        var hasEmptySlots = Rs2Inventory.getEmptySlots() > 0;
                        if( hasEmptySlots && Rs2Inventory.hasItem(runeItemId) ) {
                            Rs2Bank.depositAll(runeItemId);
                        }

                        if( Rs2Inventory.hasItem(ItemID.VIAL_EMPTY) ) {
                            Rs2Bank.depositAll(ItemID.VIAL_EMPTY);
                        }

                        if( Rs2Inventory.hasItem(ItemID.LOBSTER) ) {
                            Rs2Bank.depositAll(ItemID.LOBSTER);
                        }

                        if( !Rs2Bank.hasItem(ItemID.BLANKRUNE_HIGH) ) {
                            Microbot.showMessage("No pure essence found in bank");
                            shutdown();
                            return;
                        }

                        boolean staminaPotNeeded = Rs2Bank.isOpen() && Microbot.getClient().getEnergy() < 6000 && !Rs2Player.hasStaminaBuffActive();
                        boolean foodNeeded = Rs2Player.getHealthPercentage() < 70;

                        if( staminaPotNeeded ) {
                            if( !Rs2Bank.hasItem("Stamina Potion") ) {
                                Microbot.showMessage("No Stamina Potions found in bank");
                                shutdown();
                                return;
                            }
                            Rs2Bank.withdrawOne("Stamina Potion", 1);
                            Rs2Inventory.waitForInventoryChanges(600);
                        }

                        if( foodNeeded ) {
                            if( !Rs2Bank.hasItem(ItemID.LOBSTER) ) {
                                Microbot.showMessage("No lobsters found in bank");
                                shutdown();
                                return;
                            }
                            Rs2Bank.withdrawOne(ItemID.LOBSTER);
                            Rs2Inventory.waitForInventoryChanges(400);
                        }

                        if( staminaPotNeeded ) {
                            Rs2Inventory.interact("Stamina Potion", "Drink");
                            Rs2Inventory.waitForInventoryChanges(400);
                            sleep(600);
                        }

                        if( foodNeeded ) {
                            Rs2Inventory.interact(ItemID.LOBSTER, "Eat");
                            Rs2Inventory.waitForInventoryChanges(400);
                            if( Rs2Inventory.hasItem(ItemID.LOBSTER) ) {
                                Rs2Inventory.interact(ItemID.LOBSTER, "Eat");
                                Rs2Inventory.waitForInventoryChanges(400);
                            }
                        }

                        if( staminaPotNeeded ) {
                            Rs2Bank.depositAll("Stamina Potion");
                            Rs2Inventory.waitForInventoryChanges(600);
                            if( Rs2Inventory.hasItem(ItemID.VIAL_EMPTY) ) {
                                Rs2Bank.depositAll(ItemID.VIAL_EMPTY);
                            }
                            Rs2Bank.depositAll("Stamina Potion");
                            Rs2Inventory.waitForInventoryChanges(600);
                        }

                        var colossalPouch = Rs2Inventory.get(ItemID.RCU_POUCH_COLOSSAL);
                        if( hasEmptySlots && Rs2Bank.hasItem(ItemID.BLANKRUNE_HIGH) ) {
                            Rs2Bank.withdrawAll(ItemID.BLANKRUNE_HIGH);
                            sleep(600);
                            if( Rs2Inventory.getRemainingCapacityInPouches() > 0 )
                                Rs2Inventory.interact(colossalPouch, "Fill");
                            Rs2Inventory.waitForInventoryChanges(200);
                        }

                        MXUtil.handlePouchOutOfSync(hasEmptySlots, colossalPouch);

                        if( Rs2Inventory.allPouchesFull() && Rs2Inventory.getEmptySlots() < 1 ) {
                            state = State.CRAFTING;
                        }

                        break;
                    case CRAFTING:
                        plugin.setDebugText1("distance to craft - " + distToCraftPoint);
                        if( distToCraftPoint >= 3 && !Rs2Player.isMoving() ) {
                            Rs2Walker.walkTo(LUNAR_ISLE_CRAFT_WORLD_POINT, 2);
                            MXUtil.closeWorldMapIfNeeded();
                            doAltarCraft();
                        }

                        doAltarCraft();

                        if( Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.hasItem(ItemID.BLANKRUNE_HIGH) ) {
                            state = State.BANKING;
                        }

                        break;
                    default:
                        System.out.println("This shouldn't happen");
                        state = State.BANKING;
                        break;
                }

            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private static void doAltarCraft() {
        var altarLoc = new WorldPoint(2158, 3864, 0);
        var altarTile = Rs2GameObject.findGameObjectByLocation(altarLoc);
        if( altarTile != null && Rs2Player.getWorldLocation().distanceTo(altarLoc) < 5) {
            if( Rs2Inventory.hasItem(ItemID.BLANKRUNE_HIGH) ) {
                Rs2GameObject.interact(altarTile);
                Rs2Inventory.waitForInventoryChanges(800);
            }
            if( !Rs2Inventory.allPouchesEmpty() ) {
                Rs2Inventory.interact(Rs2Inventory.get(ItemID.RCU_POUCH_COLOSSAL), "Empty");
                Rs2Inventory.waitForInventoryChanges(800);
            }
        }
    }

    private void updateRuneStates() {
        var runes = Rs2Inventory.get(this.runeItemId);
        if( runes != null ) runesForSession += runes.getQuantity();
        totalTrips++;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
