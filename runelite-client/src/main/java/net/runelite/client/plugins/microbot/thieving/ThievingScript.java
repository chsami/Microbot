package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ThievingScript extends Script {

    public static String version = "1.6.2";
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


                List<Rs2Item> foods = Rs2Inventory.getInventoryFood();

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

                openCoinPouches(config.coinPouchTreshHold());
                wearDodgyNecklace();
                pickpocket();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean handleFood(List<Rs2Item> food) {
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
        net.runelite.api.NPC npc = Rs2Npc.getNpcs()
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
        if (config.THIEVING_NPC() == ThievingNpc.WEALTHY_CITIZEN) {
            handleWealthyCitizen();
        } else if (config.THIEVING_NPC() == ThievingNpc.ELVES) {
            handleElves();
        } else {
            Map<NPC, HighlightedNpc> highlightedNpcs =  net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs();
            if (highlightedNpcs.isEmpty()) {
                if (Rs2Npc.pickpocket(config.THIEVING_NPC().getName())) {
                    Rs2Walker.setTarget(null);
                    sleep(50, 250);
                } else if (Rs2Npc.getNpc(config.THIEVING_NPC().getName()) == null){
                    Rs2Walker.walkTo(initialPlayerLocation);
                }
            } else {
                if (Rs2Npc.pickpocket(highlightedNpcs)) {
                    sleep(50, 250);
                }
            }
        }
    }

    private void handleWealthyCitizen() {
        List<NPC> wealthyCitizenInteracting = Rs2Npc.getNpcs("Wealthy citizen")
                .filter(x -> x.isInteracting()
                        && x.getInteracting() != null
                        && x.getInteracting().getCombatLevel() == 0)
                .collect(Collectors.toList());
        NPC wealthyCitizenToPickpocket = wealthyCitizenInteracting.stream().findFirst().orElse(null);
        if (wealthyCitizenToPickpocket != null) {
            if (!Rs2Player.isAnimating(3000) && Rs2Npc.pickpocket(wealthyCitizenToPickpocket)) {
                Microbot.status = "Pickpocketting " + wealthyCitizenToPickpocket.getName();
                sleep(300, 600);
            }
        }
    }

    private void handleShadowVeil() {
        if (!Rs2Magic.isShadowVeilActive() && Rs2Magic.isArceeus() &&
            Rs2Player.getBoostedSkillLevel(Skill.MAGIC) >= MagicAction.SHADOW_VEIL.getLevel() &&
            Microbot.getVarbitValue(Varbits.SHADOW_VEIL_COOLDOWN) == 0
        ) {
            Rs2Magic.cast(MagicAction.SHADOW_VEIL);
        }
    }

    private void bank() {
        Microbot.status = "Getting food from bank...";
        if (Rs2Bank.walkToBank()) {
            boolean isBankOpen = Rs2Bank.useBank();
            if (!isBankOpen) return;
            Rs2Bank.depositAll();
            boolean successfullyWithdrawFood = Rs2Bank.withdrawX(true, config.food().getName(), config.foodAmount(), true);
            if (!successfullyWithdrawFood) {
                Microbot.showMessage(config.food().getName() + " not found in bank");
                sleep(5000);
                return;
            }
            Rs2Bank.withdrawX(true, "dodgy necklace", config.dodgyNecklaceAmount());
            if (config.shadowVeil()) {
                Rs2Bank.withdrawAll(true,"Fire rune", true);
                sleep(75,200);
                Rs2Bank.withdrawAll(true,"Earth rune", true);
                sleep(75,200);
                Rs2Bank.withdrawAll(true,"Cosmic rune", true);
                sleep(75,200);
            }
            Rs2Bank.closeBank();
        }
    }

    private void dropItems(List<Rs2Item> food) {
        List<String> doNotDropItemList = Arrays.stream(config.DoNotDropItemList().split(",")).collect(Collectors.toList());

        List<String> foodNames = food.stream().map(x -> x.name).collect(Collectors.toList());

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
