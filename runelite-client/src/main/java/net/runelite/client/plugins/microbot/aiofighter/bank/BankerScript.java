package net.runelite.client.plugins.microbot.aiofighter.bank;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.constants.Constants;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

enum ItemToKeep {
    TELEPORT(Constants.TELEPORT_IDS, AIOFighterConfig::ignoreTeleport, AIOFighterConfig::staminaValue),
    STAMINA(Constants.STAMINA_POTION_IDS, AIOFighterConfig::useStamina, AIOFighterConfig::staminaValue),
    PRAYER(Constants.PRAYER_RESTORE_POTION_IDS, AIOFighterConfig::usePrayer, AIOFighterConfig::prayerValue),
    FOOD(Rs2Food.getIds(), AIOFighterConfig::useFood, AIOFighterConfig::foodValue),
    ANTIPOISON(Constants.ANTI_POISON_POTION_IDS, AIOFighterConfig::useAntipoison, AIOFighterConfig::antipoisonValue),
    ANTIFIRE(Constants.ANTI_FIRE_POTION_IDS, AIOFighterConfig::useAntifire, AIOFighterConfig::antifireValue),
    COMBAT(Constants.STRENGTH_POTION_IDS, AIOFighterConfig::useCombat, AIOFighterConfig::combatValue),
    RESTORE(Constants.RESTORE_POTION_IDS, AIOFighterConfig::useRestore, AIOFighterConfig::restoreValue);

    @Getter
    private final List<Integer> ids;
    private final Function<AIOFighterConfig, Boolean> useConfig;
    private final Function<AIOFighterConfig, Integer> valueConfig;

    ItemToKeep(Set<Integer> ids, Function<AIOFighterConfig, Boolean> useConfig, Function<AIOFighterConfig, Integer> valueConfig) {
        this.ids = new ArrayList<>(ids);
        this.useConfig = useConfig;
        this.valueConfig = valueConfig;
    }

    public boolean isEnabled(AIOFighterConfig config) {
        return useConfig.apply(config);
    }

    public int getValue(AIOFighterConfig config) {
        return valueConfig.apply(config);
    }
}

@Slf4j
public class BankerScript extends Script {
    AIOFighterConfig config;


    boolean initialized = false;

    public boolean run(AIOFighterConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (Microbot.isLoggedIn() && config.bank() && needsBanking()) {
                    if (config.eatFoodForSpace())
                        if (Rs2Player.eatAt(100))
                            return;

                    if(handleBanking()){
                        AIOFighterPlugin.setState(State.IDLE);
                    }
                } else if (!needsBanking() && config.centerLocation().distanceTo(Rs2Player.getWorldLocation()) > config.attackRadius()) {
                    AIOFighterPlugin.setState(State.WALKING);
                    Rs2Walker.walkTo(config.centerLocation());
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean needsBanking() {
        return (isUpkeepItemDepleted(config) && config.bank()) || (Rs2Inventory.getEmptySlots() <= config.minFreeSlots() && config.bank());
    }

    public boolean withdrawUpkeepItems(AIOFighterConfig config) {
        if (config.useInventorySetup()) {
            Rs2InventorySetup inventorySetup = new Rs2InventorySetup(config.inventorySetup().getName(), mainScheduledFuture);
            if (!inventorySetup.doesEquipmentMatch()) {
                inventorySetup.loadEquipment();
            }
            inventorySetup.loadInventory();
            return true;
        }

        for (ItemToKeep item : ItemToKeep.values()) {
            if (item.isEnabled(config)) {
                int count = item.getIds().stream().mapToInt(Rs2Inventory::count).sum();
                log.info("Item: {} Count: {}", item.name(), count);
                if (count < item.getValue(config)) {
                    log.info("Withdrawing {} {}(s)", item.getValue(config) - count, item.name());
                    if (item.name().equals("FOOD")) {
                        for (Rs2Food food : Arrays.stream(Rs2Food.values()).sorted(Comparator.comparingInt(Rs2Food::getHeal).reversed()).collect(Collectors.toList())) {
                            log.info("Checking bank for food: {}", food.getName());
                            if (Rs2Bank.hasBankItem(food.getId(), item.getValue(config) - count)) {
                                Rs2Bank.withdrawX(true, food.getId(), item.getValue(config) - count);
                                break;
                            }
                        }
                    } else {
                        ArrayList<Integer> ids = new ArrayList<>(item.getIds());
                        Collections.reverse(ids);
                        for (int id : ids) {
                            log.info("Checking bank for item: {}", id);
                            if (Rs2Bank.hasBankItem(id, item.getValue(config) - count)) {
                                Rs2Bank.withdrawX(true, id, item.getValue(config) - count);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return !isUpkeepItemDepleted(config);
    }

    public boolean depositAllExcept(AIOFighterConfig config) {
        List<Integer> ids = Arrays.stream(ItemToKeep.values())
                .filter(item -> item.isEnabled(config))
                .flatMap(item -> item.getIds().stream())
                .collect(Collectors.toList());
        Rs2Bank.depositAllExcept(ids.toArray(new Integer[0]));
        return Rs2Bank.isOpen();
    }

    public boolean isUpkeepItemDepleted(AIOFighterConfig config) {
        return Arrays.stream(ItemToKeep.values())
                .filter(item -> item != ItemToKeep.TELEPORT && item.isEnabled(config))
                .anyMatch(item -> item.getIds().stream().mapToInt(Rs2Inventory::count).sum() == 0);
    }

    public boolean goToBank() {
        return Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 8);
    }

    public boolean handleBanking() {
        AIOFighterPlugin.setState(State.BANKING);
        Rs2Prayer.disableAllPrayers();
        if (Rs2Bank.walkToBankAndUseBank()) {
            depositAllExcept(config);
            withdrawUpkeepItems(config);
        }
        return !needsBanking();
    }


    public void shutdown() {
        super.shutdown();
        // reset the initialized flag
        initialized = false;

    }
}
