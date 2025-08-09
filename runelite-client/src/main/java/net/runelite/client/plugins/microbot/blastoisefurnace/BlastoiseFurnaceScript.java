package net.runelite.client.plugins.microbot.blastoisefurnace;

import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.Bars;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.State;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.api.gameval.ItemID;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.concurrent.TimeUnit;

import static net.runelite.api.gameval.ItemID.*;
import static net.runelite.api.gameval.ObjectID.*;
import static net.runelite.api.gameval.VarbitID.*;
import static net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.ITEM_NAME_SUFFIX_PATTERN;

@Slf4j
public class BlastoiseFurnaceScript extends Script {
    static final int coalBag = 12019;
    private static final int MAX_ORE_PER_INTERACTION = 27;
    private static final int MAX_ORE_PER_HYBRID_INTERACTION = 26;
    public static double version = 1.1;
    public static State state = State.BANKING;
    static boolean coalBagEmpty;
    static boolean primaryOreEmpty;
    static boolean secondaryOreEmpty;
    private boolean init = false;

	private final BlastoiseFurnacePlugin plugin;
    private final BlastoiseFurnaceConfig config;

    private boolean hasRequiredOresForSmithing() {
        int primaryOre = config.getBars().getPrimaryOre();
        int secondaryOre = config.getBars().getSecondaryOre() == null ? -1 : config.getBars().getSecondaryOre();
        boolean hasPrimaryOre = Rs2Bank.hasItem(primaryOre);
        boolean hasSecondaryOre = secondaryOre != -1 && Rs2Bank.hasItem(secondaryOre);
        return hasPrimaryOre && hasSecondaryOre;
    }

	@Inject
	public BlastoiseFurnaceScript(BlastoiseFurnacePlugin plugin, BlastoiseFurnaceConfig config) {
		this.plugin = plugin;
		this.config = config;
	}

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        state = State.BANKING;
        primaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getPrimaryOre());
        secondaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getSecondaryOre());
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }

                if (!Rs2GameObject.exists(BLAST_FURNACE_DISPENSER)) {
                    if (Rs2Player.isAnimating()) {
                        return;
                    }
                    Rs2Walker.walkTo(new WorldPoint(2931, 10197, 0));
                    Rs2GameObject.interact(DWARF_KELDAGRIM_FACTORY_STAIRS);
                    return;
                }

                if (!init) {
                    int inCoffer = Microbot.getVarbitValue(BLAST_FURNACE_COFFER);
                    int req      = evaluateCofferDeposit();
                    if (inCoffer >= req) {
                        init = true;
                        return;
                    }
                    checkAndTopOffCoffer();
                    init = true;
                    return;
                }

                if (!fullCoffer()) {
                    checkAndTopOffCoffer();
                }

                boolean hasGauntlets;
                switch (state) {
                    case BANKING:
                        Microbot.status = "Banking";
                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.openBank();
                            sleepUntil(Rs2Bank::isOpen, 20000);
                        }

                        if (config.getBars().isRequiresCoalBag() && !Rs2Inventory.contains(coalBag)) {
                            if (!Rs2Bank.hasItem(coalBag)) {
                                Microbot.showMessage("No coal bag found in inventory and bank.");
                                Microbot.stopPlugin(plugin);
                                return;
                            }

                            Rs2Bank.withdrawItem(coalBag);
                        }

                        if (config.getBars().isRequiresGoldsmithGloves()) {
                            hasGauntlets = Rs2Inventory.contains(GAUNTLETS_OF_GOLDSMITHING) || Rs2Equipment.isWearing(GAUNTLETS_OF_GOLDSMITHING);
                            if (!hasGauntlets) {
                                if (!Rs2Bank.hasItem(GAUNTLETS_OF_GOLDSMITHING)) {
                                    Microbot.showMessage("No goldsmith gauntlets found.");
                                    Microbot.stopPlugin(plugin);
                                    return;
                                }

                                Rs2Bank.withdrawItem(GAUNTLETS_OF_GOLDSMITHING);
                            }
                        } else Rs2Bank.depositAll(GAUNTLETS_OF_GOLDSMITHING);

                        if (Rs2Inventory.hasItem("bar")) {
                            Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
                        }

                        if (!hasRequiredOresForSmithing()) {
                            log.warn("Out of ores. Walking you out for coffer safety");
                            Rs2Walker.walkTo(new WorldPoint(2930, 10196, 0));
                            Rs2Player.logout();
                            Microbot.stopPlugin(plugin);
                        }

                        if (Microbot.getClient().getEnergy() < 8100) {
                            useStaminaPotions();
                        }

                        // Check here if dispenser contains bars. If so we need to clean-up
                        if (dispenserContainsBars()) {
                            Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
                            handleDispenserLooting();
                            return;
                        } else {
                            retrieveItemsForCurrentFurnaceInteraction();
                            state = State.SMITHING;
                        }
                        break;
                    case SMITHING:
						log.info("clicking conveyor");
                        if (barsInDispenser(config.getBars()) > 0) {
                            handleDispenserLooting();
                        }

                        state = State.BANKING;
                        break;
                }
            } catch (Exception ex) {
                log.trace("Error in main loop: {} - ", ex.getMessage(), ex);
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleTax() {
        log.info("Paying noob smithing tax");
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 20000);
        }
        Rs2Bank.depositOne(config.getBars().getPrimaryOre());
        sleep(500, 1200);
        Rs2Bank.depositOne(COAL);
        sleep(500, 1200);
        Rs2Bank.withdrawX(COINS,2500);
        sleep(500, 1200);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        Rs2NpcModel blastie = Rs2Npc.getNpc("Blast Furnace Foreman");
        Rs2Npc.interact(blastie, "Pay");
        sleepUntil(Rs2Dialogue::isInDialogue,10000);
        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("Yes");
            sleep(1000, 1850);
            Rs2Dialogue.clickContinue();
            sleep(500, 1300);

        }
    }

    private void handleDispenserLooting() {
        if (!Rs2Inventory.isFull()) {
            if (!dispenserContainsBars()) {
                sleepUntil(this::dispenserContainsBars, Rs2Random.between(3000, 5000));
            }

            if (!Rs2Equipment.isWearing(ICE_GLOVES) && !Rs2Equipment.isWearing(SMITHING_UNIFORM_GLOVES_ICE)) {
                boolean equipped = Rs2Inventory.interact(ICE_GLOVES, "Wear")
                        || Rs2Inventory.interact(SMITHING_UNIFORM_GLOVES_ICE, "Wear");
                if (!equipped) {
                    Microbot.showMessage("Ice gloves or smith gloves required to loot the hot bars.");
                    Rs2Player.logout();
                    Microbot.stopPlugin(plugin);
                    return;
                }
            }

            Rs2GameObject.interact(BLAST_FURNACE_DISPENSER, "Take");

            sleepUntil(() ->
                    Rs2Widget.hasWidget("What would you like to take?") ||
                            Rs2Widget.hasWidget("How many would you like") ||
                            Rs2Widget.hasWidget("The bars are still molten!"), 5000);

            sleepUntil(() ->
                    Rs2Widget.hasWidget("What would you like to take?") ||
                            Rs2Widget.hasWidget("How many would you like"), 3000);

            boolean multipleBarTypes = Rs2Widget.hasWidget("What would you like to take?");
            boolean canLootBar = Rs2Widget.hasWidget("How many would you like");

            if (super.run()) {
                if (canLootBar || multipleBarTypes) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                }
                Rs2Inventory.waitForInventoryChanges(5000);
                Rs2Bank.openBank();
                equipGoldSmithGauntlets();
            }
        }

        state = State.BANKING;
    }

    private void retrievePrimary() {
        int ore = config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(ore)) {
            Rs2Bank.withdrawAll(ore);
            return;
        }
        doOreRun(false, false);
    }

    private void retrieveDoubleCoal() {
        if (!Rs2Inventory.hasItem(COAL)) {
            Rs2Bank.withdrawAll(COAL);
            return;
        }
        if (!Rs2Inventory.interact(coalBag, "Fill"))
            return;
        depositOre();
    }

    private void retrieveCoalAndPrimary() {
        int ore = config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(ore)) {
            Rs2Bank.withdrawAll(ore);
            sleep(500, 1200);
            return;
        }
        if (!Rs2Inventory.interact(coalBag, "Fill"))
            return;

        sleep(500, 1200);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        depositOre();
        doOreRun(false, false);
    }

    private void retrieveCoalAndGold() {
        if (!Rs2Inventory.hasItem(GOLD_ORE)) {
            Rs2Bank.withdrawAll(GOLD_ORE);
            return;
        }
        if (!Rs2Inventory.interact(coalBag, "Fill"))
            return;

        sleep(500, 1200);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        depositOre();
        doOreRun(true, true);
    }

    private void retrieveGold() {
        if (!Rs2Inventory.hasItem(GOLD_ORE)) {
            Rs2Bank.withdrawAll(GOLD_ORE);
            return;
        }
        depositOre();
        doOreRun(true, true);
    }

    private void doOreRun(boolean useIceGloves, boolean waitInventoryChange) {
        Rs2Walker.walkFastCanvas(new WorldPoint(1940, 4962, 0));
        sleep(3400);
        sleepUntil(() -> barsInDispenser(config.getBars()) > 0, 10000);

        if (useIceGloves) {
            if (!Rs2Equipment.isWearing(ICE_GLOVES) && !Rs2Equipment.isWearing(SMITHING_UNIFORM_GLOVES_ICE)) {
                boolean equipped = Rs2Inventory.interact(ICE_GLOVES, "Wear")
                        || Rs2Inventory.interact(SMITHING_UNIFORM_GLOVES_ICE, "Wear");
                if (!equipped) {
                    Microbot.showMessage("Ice gloves or smith gloves required to loot the hot bars.");
                    Rs2Player.logout();
                    Microbot.stopPlugin(plugin);
                    return;
                }
            }
            if (waitInventoryChange)
                Rs2Inventory.waitForInventoryChanges(2000);
        } else {
            sleep(400, 700);
        }
    }

    private void retrieveItemsForCurrentFurnaceInteraction() {
        final Bars bar = config.getBars();
        final int coal = Microbot.getVarbitValue(
                bar == Bars.GOLD_BAR ? BLAST_FURNACE_GOLD_ORE : BLAST_FURNACE_COAL
        );
        final int divisor = isHybrid(bar) ? MAX_ORE_PER_HYBRID_INTERACTION : MAX_ORE_PER_INTERACTION;
        final int batch = coal / divisor;

        switch (bar) {
            case GOLD_BAR:
                retrieveGold();
                break;

            case STEEL_BAR:
            case MITHRIL_BAR:
                dispatchStandard(batch, 0, retrieveDoubleCoal, retrieveCoalAndPrimary, retrievePrimary);
                break;

            case ADAMANTITE_BAR:
            case RUNITE_BAR:
                dispatchStandard(batch, 2, retrieveDoubleCoal, retrieveCoalAndPrimary, retrievePrimary);
                break;

            case HYBRID_MITHRIL_BAR:
                dispatchHybrid(batch, 0, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            case HYBRID_ADAMANTITE_BAR:
                dispatchHybrid(batch, 2, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            case HYBRID_RUNITE_BAR:
                dispatchHybrid(batch, 3, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            default:
                assert false : "unhandled bar type: " + bar;
        }
    }

    private boolean isHybrid(Bars bar) {
        return bar.name().startsWith("HYBRID");
    }

    private final Runnable retrievePrimary = this::retrievePrimary;
    private final Runnable retrieveCoalAndPrimary = this::retrieveCoalAndPrimary;
    private final Runnable retrieveCoalAndGold = this::retrieveCoalAndGold;
    private final Runnable retrieveDoubleCoal = this::retrieveDoubleCoal;

    private void dispatchStandard(int batch, int doubleCoalMax, Runnable doubleCoal, Runnable coalAndPrimary, Runnable primary) {
        if (batch <= doubleCoalMax) {
            doubleCoal.run();
        } else if (batch <= 6) {
            coalAndPrimary.run();
        } else {
            primary.run();
        }
    }

    private void dispatchHybrid(int batch, int goldThreshold, Runnable coalAndGold, Runnable coalAndPrimary) {
        if (batch <= goldThreshold) {
            coalAndGold.run();
        } else {
            coalAndPrimary.run();
        }
    }

    private void useStaminaPotions() {
        if (!Rs2Bank.isOpen()) return;

        boolean hasStaminaPotion = Rs2Bank.hasItem(Rs2Potion.getStaminaPotion());
        boolean hasEnergyPotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());

        if ((Rs2Player.hasStaminaBuffActive() && hasEnergyPotion) || (!hasStaminaPotion && hasEnergyPotion)) {
            String potionName = getLowestDosePotionName(Rs2Potion.getRestoreEnergyPotionsVariants());
            if (potionName != null) {
                withdrawAndDrink(potionName);
            }
        } else if (hasStaminaPotion) {
            String potionName = getLowestDosePotionName(List.of(Rs2Potion.getStaminaPotion()));
            if (potionName != null) {
                withdrawAndDrink(potionName);
            }
        }
    }

    private String getLowestDosePotionName(List<String> variants) {
        return Rs2Bank.getAll(item -> variants.stream().anyMatch(variant -> item.getName().toLowerCase().contains(variant.toLowerCase())))
                .min(Comparator.comparingInt(item -> getDoseFromName(item.getName())))
                .map(Rs2ItemModel::getName)
                .orElse(null);
    }

    private int getDoseFromName(String potionItemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(potionItemName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return 0;
    }

    private String getBaseName(String itemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(itemName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return itemName;
    }

    private void withdrawAndDrink(String potionItemName) {
        String baseName = getBaseName(potionItemName);
        Rs2Bank.withdrawOne(potionItemName);
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Inventory.interact(potionItemName, "drink");
        Rs2Inventory.waitForInventoryChanges(1800);
        if (Rs2Inventory.hasItem(baseName)) {
            Rs2Bank.depositOne(baseName);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
            Rs2Bank.depositOne(ItemID.VIAL_EMPTY);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
    }

    private int getInventoryOreCount() {
        return Rs2Inventory.count("ore") + Rs2Inventory.count("coal");
    }

    private boolean putOreOnConveyorBelt() {
        final int oreCount = getInventoryOreCount();
        if (oreCount <= 0) {
            log.error("No ore in Inventory");
            return false;
        }
        if (!Rs2GameObject.interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on")) {
            log.error("Failed to interact with conveyor belt");
            return false;
        }
        sleepUntil(() -> Rs2Dialogue.isInDialogue() || getInventoryOreCount() < oreCount, 10_000);
        if (Rs2Widget.hasWidget("foreman")) {
            log.info("Need to pay the noob tax");
            handleTax();
            return putOreOnConveyorBelt();
        }
        return true;
    }

    private void depositOre() {
        putOreOnConveyorBelt();
        if (config.getBars().isRequiresCoalBag()) {
            if (Rs2Inventory.interact(coalBag, "Empty")) Rs2Inventory.waitForInventoryChanges(3_000);
            else log.error("Failed to empty coal bag 1");
            putOreOnConveyorBelt();
        }
        if (config.getBars().isRequiresCoalBag() &&
                (Rs2Inventory.hasItem(SMITHING_UNIFORM_GLOVES_ICE)
                        || Rs2Inventory.hasItem(GAUNTLETS_OF_GOLDSMITHING)
                        || Rs2Inventory.hasItem(ICE_GLOVES))) {
            if (Rs2Inventory.interact(coalBag, "Empty")) Rs2Inventory.waitForInventoryChanges(3_000);
            else log.error("Failed to empty coal bag 2");
            putOreOnConveyorBelt();
        }
    }


    public int barsInDispenser(Bars bar) {
        switch (bar) {
            case GOLD_BAR:
                return getBars(BLAST_FURNACE_GOLD_BARS);
            case STEEL_BAR:
                return getBars(BLAST_FURNACE_STEEL_BARS);
            case MITHRIL_BAR:
                return getBars(BLAST_FURNACE_MITHRIL_BARS);
            case ADAMANTITE_BAR:
                return getBars(BLAST_FURNACE_ADAMANTITE_BARS);
            case RUNITE_BAR:
                return getBars(BLAST_FURNACE_RUNITE_BARS);
            case HYBRID_MITHRIL_BAR:
                return fallbackBar(
                        BLAST_FURNACE_MITHRIL_BARS
                );
            case HYBRID_ADAMANTITE_BAR:
                return fallbackBar(
                        BLAST_FURNACE_ADAMANTITE_BARS
                );
            case HYBRID_RUNITE_BAR:
                return fallbackBar(
                        BLAST_FURNACE_RUNITE_BARS
                );
            default:
                return -1;
        }
    }

    private int fallbackBar(int primary) {
        int value = Microbot.getVarbitValue(primary);
        return value > 0 ? value : Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.BLAST_FURNACE_GOLD_BARS);
    }

    private int getBars(int varbitId) {
        return Microbot.getVarbitValue(varbitId);
    }

    public boolean dispenserContainsBars() {
        return Arrays.stream(new int[] {
                BLAST_FURNACE_IRON_BARS,
                BLAST_FURNACE_STEEL_BARS,
                BLAST_FURNACE_GOLD_BARS,
                BLAST_FURNACE_MITHRIL_BARS,
                BLAST_FURNACE_ADAMANTITE_BARS,
                BLAST_FURNACE_RUNITE_BARS
        }).anyMatch(id -> Microbot.getVarbitValue(id) > 0);
    }

    private void equipGoldSmithGauntlets() {
        if (config.getBars().isRequiresGoldsmithGloves()) {
            Rs2Inventory.interact(GAUNTLETS_OF_GOLDSMITHING, "Wear");
        }
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.devDebug = true;
    }

    public void shutdown() {
        init = false;
        state = State.BANKING;
        primaryOreEmpty = false;
        secondaryOreEmpty = false;
        super.shutdown();
    }

    public int evaluateCofferDeposit()
    {
        Plugin plugin = Microbot.getPlugin(SchedulerPlugin.class.getName());
        assert plugin instanceof SchedulerPlugin : "Invalid scheduler plugin";
        SchedulerPlugin scheduler = (SchedulerPlugin) plugin;
        Optional<Duration> estimatedTime = scheduler.getUpComingEstimatedScheduleTime();
        if (estimatedTime.isPresent())
        {
            Duration untilNext = estimatedTime.get();
            if (untilNext == null || untilNext.isZero() || untilNext.isNegative())
            {
                untilNext = scheduler.getTimeUntilNextBreak();
            }
            if (untilNext != null && !untilNext.isZero() && !untilNext.isNegative())
            {
                long millis = untilNext.toMillis();
                assert millis >= 0 : "Negative duration";
                return (int) Math.ceil(millis * 0.2); // 1 ms = 0.2 gold
            }
        }
        if (BreakHandlerScript.breakIn > 0) {
            return BreakHandlerScript.breakIn * 20;
        }

        return 72000;
    }

    public void checkAndTopOffCoffer()
    {
        int lvl = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        if (lvl < 60) return;

        int inCoffer = Microbot.getVarbitValue(BLAST_FURNACE_COFFER);
        int required = evaluateCofferDeposit();
        int delta = required - inCoffer;

        if (delta == 0) return;

        boolean underfilled = delta > 0;
        int amount = Math.abs(delta);

        if (Rs2Inventory.isFull()) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
            }
        }

        if (underfilled) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
            }

            if (!Rs2Bank.hasBankItem(COINS, amount)) {
                Microbot.stopPlugin(plugin);
                return;
            }

            Rs2Bank.withdrawX(COINS, amount);
            sleep(600, 900);
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
        }

        Rs2GameObject.interact(BLAST_FURNACE_AUTOMATA_COFFER, "use");
        Rs2Player.waitForWalking(2400);

        if (underfilled && Rs2Dialogue.hasDialogueOption("deposit", false)) {
            Rs2Widget.clickWidget("deposit");
            sleepUntil(() -> Rs2Dialogue.hasQuestion("Deposit how much?"), 2400);
            Rs2Keyboard.typeString(String.valueOf(amount));
            Rs2Keyboard.enter();
            Rs2Inventory.waitForInventoryChanges(1200);
        } else if (!underfilled && Rs2Dialogue.hasDialogueOption("withdraw", false)) {
            Rs2Widget.clickWidget("withdraw");
            sleepUntil(() -> Rs2Dialogue.hasQuestion("Withdraw how much?"), 2400);
            Rs2Keyboard.typeString(String.valueOf(amount));
            Rs2Keyboard.enter();
            Rs2Inventory.waitForInventoryChanges(1200);
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
            }
        } else {
            log.warn("Unexpected coffer dialogue state. delta = {}", delta);
            Rs2Dialogue.clickContinue();
        }
    }

    public boolean fullCoffer() {
        int coffer = Microbot.getVarbitValue(BLAST_FURNACE_COINSINCOFFER);
        return coffer == 1;
    }
}

