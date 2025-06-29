package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isEquipped;

public class ThievingScript extends Script {

    public static String version = "1.6.5";
    ThievingConfig config;

    public boolean run(ThievingConfig config) {
        this.config = config;
        Microbot.isCantReachTargetDetectionEnabled = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (Rs2Player.isStunned())
                    return;


                List<Rs2ItemModel> foods = Rs2Inventory.getInventoryFood();

                if (config.useFood()) {
                    boolean hasFood = handleFood(foods);
                    if (!hasFood) return;
                }

                if (Rs2Inventory.isFull()) {
                    Rs2Player.eatAt(99);
                    dropItems(foods);
                }

                if (config.shadowVeil()) {
                    handleShadowVeil();
                }

                int threshold = config.coinPouchTreshHold();
                threshold += (int) (Math.random() * 7 - 3);
                threshold = Math.max(1, Math.min(28, threshold));

                openCoinPouches(threshold);
                wearDodgyNecklace();
                pickpocket();
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean handleFood(List<Rs2ItemModel> food) {
        if (food.isEmpty()) {
            openCoinPouches(1);
            bank();
            return false;
        }

        Rs2Player.eatAt(config.hitpoints());

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Walker.setTarget(null);
        Microbot.isCantReachTargetDetectionEnabled = false;
    }

    private void handleElves() {
        List<String> names = Arrays.asList(
                "Anaire", "Aranwe", "Aredhel", "Caranthir", "Celebrian", "Celegorm",
                "Cirdan", "Curufin", "Earwen", "Edrahil", "Elenwe", "Elladan", "Enel",
                "Erestor", "Enerdhil", "Enelye", "Feanor", "Findis", "Finduilas",
                "Fingolfin", "Fingon", "Galathil", "Gelmir", "Glorfindel", "Guilin",
                "Hendor", "Idril", "Imin", "Iminye", "Indis", "Ingwe", "Ingwion",
                "Lenwe", "Lindir", "Maeglin", "Mahtan", "Miriel", "Mithrellas",
                "Nellas", "Nerdanel", "Nimloth", "Oropher", "Orophin", "Saeros",
                "Salgant", "Tatie", "Thingol", "Turgon", "Vaire", "Goreu"
        );
        var npc = Rs2Npc.getNpcs()
                .filter(x -> names.stream()
                        .anyMatch(n -> n.equalsIgnoreCase(x.getName())))
                .findFirst()
                .orElse(null);
        Map<NPC, HighlightedNpc> highlightedNpcs =  net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs();
        if (highlightedNpcs.isEmpty()) {
            if (Rs2Npc.pickpocket(npc)) {
                Rs2Walker.setTarget(null);
                sleep(50, 250);
            }
        } else {
            if (Rs2Npc.pickpocket(highlightedNpcs)) {
                sleep(50, 250);
            }
        }
    }

    private void openCoinPouches(int amt) {
        if (config.THIEVING_NPC() == ThievingNpc.WEALTHY_CITIZEN && Rs2Player.isAnimating(3000)) return;
        if (Rs2Inventory.hasItemAmount("coin pouch", amt, true)) {
            Rs2Inventory.interact("coin pouch", "Open-all");
        }
    }

    private void wearDodgyNecklace() {
        if (!Rs2Equipment.isWearing("dodgy necklace")) {
            Rs2Inventory.wield("dodgy necklace");
        }
    }

    private void pickpocket() {
        WorldArea ardougneArea = new WorldArea(2649, 3280, 7, 8, 0);
        Map<NPC, HighlightedNpc> highlightedNpcs = new HashMap<>();

        try {
            highlightedNpcs = net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs();

            if (config.ardougneAreaCheck() && !highlightedNpcs.isEmpty()) {
                for (Map.Entry<NPC, HighlightedNpc> entry : highlightedNpcs.entrySet()) {
                    NPC npc = entry.getKey();

                    try {
                        String npcCompositionName = npc.getTransformedComposition().getName();

                        if (npcCompositionName != null && npcCompositionName.toLowerCase().contains("knight of ardougne")) {
                            if (!ardougneArea.contains(npc.getWorldLocation())) {
                                Microbot.log("Highlighted Knight is NOT in Ardougne area - shutting down");
                                shutdown();

                                return;
                            }
                        }
                    } catch (Exception e) {
                        Microbot.log("Error getting NPC composition: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Microbot.log("Error getting highlighted NPCs: " + e.getMessage());
        }

        if (config.ardougneAreaCheck() && config.THIEVING_NPC() == ThievingNpc.ARDOUGNE_KNIGHT) {
            NPC knight = Rs2Npc.getNpc("knight of ardougne");

            if (knight != null && !ardougneArea.contains(knight.getWorldLocation())) {
                Microbot.log(" Knight not in Ardougne area - shutting down");
                shutdown();
                return;
            } else if (knight == null) {
                Microbot.log("No regular Knight of Ardougne found - shutting down");
                shutdown();
                return;
            }
        }

        if (config.THIEVING_NPC() == ThievingNpc.WEALTHY_CITIZEN) {
            handleWealthyCitizen();
        } else if (config.THIEVING_NPC() == ThievingNpc.ELVES) {
            handleElves();
        } else {
            if (highlightedNpcs.isEmpty()) {
                if (Rs2Npc.getNpc(config.THIEVING_NPC().getName()) == null) {
                    Rs2Walker.walkTo(initialPlayerLocation);
                } else if (Rs2Npc.pickpocket(config.THIEVING_NPC().getName())) {
                    Rs2Walker.setTarget(null);
                    sleep(50, 250);
                }
            } else {
                if (Rs2Npc.pickpocket(highlightedNpcs)) {
                    sleep(50, 250);
                }
            }
        }
    }

        private void handleWealthyCitizen() {
            try {
                if (Rs2Player.isAnimating(3000)) {
                    return;
                }
                List<Rs2NpcModel> wealthyCitizenInteracting = new ArrayList<>();
                try {
                    Stream<Rs2NpcModel> npcStream = Rs2Npc.getNpcs("Wealthy citizen", true);
                    if (npcStream != null) {
                        wealthyCitizenInteracting = npcStream
                                .filter(x -> x != null && x.isInteracting() && x.getInteracting() != null)
                                .collect(Collectors.toList());
                    }
                } catch (Exception ex) {
                    Microbot.log("Error retrieving Wealthy citizens: " + ex.getMessage());
                    return;
                }

                Optional<Rs2NpcModel> wealthyCitizenToPickpocket = wealthyCitizenInteracting.stream().findFirst();
                if (wealthyCitizenToPickpocket.isPresent()) {
                    Rs2NpcModel pickpocketnpc = wealthyCitizenToPickpocket.get();
                    if (!Rs2Player.isAnimating(3000) && Rs2Npc.pickpocket(pickpocketnpc)) {
                        Microbot.status = "Pickpocketing " + pickpocketnpc.getName();
                        sleep(300, 600);
                    }
                }
            } catch (Exception ex) {
               Microbot.log("Error in handleWealthyCitizen: " + ex.getMessage());
            }
        }

    private void handleShadowVeil() {
        if (!Rs2Magic.isShadowVeilActive() && config.shadowVeil()) {
            if (Rs2Magic.canCast(MagicAction.SHADOW_VEIL)) {
                Rs2Magic.cast(MagicAction.SHADOW_VEIL);
            } else {
                Microbot.showMessage("Please check, unable to cast Shadow Veil");
            }
        }
    }

    private void bank() {
        Microbot.status = "Getting food from bank...";

        BankLocation nearestBank = Rs2Bank.getNearestBank();
        boolean isBankOpen = Rs2Bank.isNearBank(nearestBank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(nearestBank);
        if (!isBankOpen || !Rs2Bank.isOpen()) return;
        Rs2Bank.depositAll();

        Map<String, EquipmentInventorySlot> rogueEquipment = new HashMap<>();
        rogueEquipment.put("Rogue mask", EquipmentInventorySlot.HEAD);
        rogueEquipment.put("Rogue top", EquipmentInventorySlot.BODY);
        rogueEquipment.put("Rogue trousers", EquipmentInventorySlot.LEGS);
        rogueEquipment.put("Rogue boots", EquipmentInventorySlot.BOOTS);
        rogueEquipment.put("Rogue gloves", EquipmentInventorySlot.GLOVES);
        rogueEquipment.put("Thieving cape(t)",EquipmentInventorySlot.CAPE);

        for (Map.Entry<String, EquipmentInventorySlot> entry : rogueEquipment.entrySet()) {
            String itemName = entry.getKey();
            EquipmentInventorySlot slot = entry.getValue();
            if (!isEquipped(itemName, slot) && Rs2Bank.hasBankItem(itemName)) {
                Rs2Bank.withdrawAndEquip(itemName);
                Rs2Inventory.waitForInventoryChanges(1200);
            }
        }

        if (config.shadowVeil()) {
            if (!isEquipped("Lava battlestaff", EquipmentInventorySlot.WEAPON)) {
                if (Rs2Bank.hasBankItem("Lava battlestaff")) {
                    Rs2Bank.withdrawItem("Lava battlestaff");
                    Rs2Inventory.waitForInventoryChanges(3000);
                    if (Rs2Inventory.contains("Lava battlestaff")) {
                        Rs2Inventory.wear("Lava battlestaff");
                        Rs2Inventory.waitForInventoryChanges(3000);
                    } else {
                        Rs2Bank.withdrawAll(true, "Fire rune", true);
                        Rs2Inventory.waitForInventoryChanges(3000);
                        Rs2Bank.withdrawAll(true, "Earth rune", true);
                        Rs2Inventory.waitForInventoryChanges(3000);
                    }
                }
            }
            Rs2Bank.withdrawAll(true, "Cosmic rune", true);
            Rs2Inventory.waitForInventoryChanges(3000);
        }

        boolean successfullyWithdrawFood = Rs2Bank.withdrawX(true, config.food().getName(), config.foodAmount(), true);
        if (!successfullyWithdrawFood) {
            Microbot.showMessage(config.food().getName() + " not found in bank");
            shutdown();
        }

        Rs2Inventory.waitForInventoryChanges(3000);
        Rs2Bank.withdrawDeficit("dodgy necklace", config.dodgyNecklaceAmount());

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    private void dropItems(List<Rs2ItemModel> food) {
        List<String> doNotDropItemList = Arrays.stream(config.DoNotDropItemList().split(",")).collect(Collectors.toList());

        List<String> foodNames = food.stream().map(Rs2ItemModel::getName).collect(Collectors.toList());

        doNotDropItemList.addAll(foodNames);

        doNotDropItemList.add(config.food().getName());
        doNotDropItemList.add("dodgy necklace");
        doNotDropItemList.add("coins");
        doNotDropItemList.add("book of the dead");
        if (config.shadowVeil()) {
            doNotDropItemList.add("Fire rune");
            doNotDropItemList.add("Earth rune");
            doNotDropItemList.add("Cosmic rune");
        }
        Rs2Inventory.dropAllExcept(config.keepItemsAboveValue(), doNotDropItemList);
    }
}
