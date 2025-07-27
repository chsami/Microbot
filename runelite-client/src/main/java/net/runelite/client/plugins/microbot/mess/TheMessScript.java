package net.runelite.client.plugins.microbot.mess;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.event.Level;

import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class TheMessScript extends Script {

    private static final WorldPoint UTENSIL_CUPBOARD_LOC = new WorldPoint(1644, 3624, 0);
    private static final WorldPoint FOOD_CUPBOARD_LOC = new WorldPoint(1645, 3623, 0);
    private static final WorldPoint SINK_LOC = new WorldPoint(1644, 3628, 0);
    private static final WorldPoint MEAT_TABLE_LOC = new WorldPoint(1645, 3630, 0);
    private static final WorldPoint CLAY_OVEN_LOC = new WorldPoint(1648, 3627, 0);
    private static final WorldPoint BUFFET_TABLE_LOC = new WorldPoint(1640, 3629, 0);
    private static final int STEW_WIDGET_BAR_ID = 15400974;
    private static final int PIE_WIDGET_BAR_ID = 15400966;
    private static final int PIZZA_WIDGET_BAR_ID = 15400970;
    private TheMessOverlay overlay;
    private TheMessConfig config;

    @Getter
    @Setter
    private State currentState = State.WAITING;

    public boolean run(TheMessConfig config, TheMessOverlay overlay) {
        debug("The Mess Script is starting up...");
        Microbot.enableAutoRunOn = false;
        this.overlay = overlay;
        this.config = config;

        setCurrentState(State.WAITING);
        setOrderOfStates();

        Rs2Antiban.setActivity(Activity.GENERAL_COOKING);
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.actionCooldownActive = true;
        Rs2Antiban.setTIMEOUT(Rs2Random.betweenInclusive(1, 4));

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isRunning()) return;
                if (BreakHandlerScript.isBreakActive() && getCurrentState() == State.WAITING) return;

                handleState();

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleState() {
        switch (getCurrentState()) {
            case WAITING:
                if (!Microbot.isLoggedIn()) return;
                if (!super.run() || !isRunning()) return;
                if (BreakHandlerScript.isBreakActive()) return;
                break;
            case GET_EMPTY_BOWLS:
            case GET_KNIFE:
            case GET_EMPTY_PIE_DISHES:
                sleepUntil(getUtensils());
                break;
            case RETURN_EMPTY_BOWLS:
                sleepUntil(returnEmptyBowls());
                break;
            case USE_SINK:
                sleepUntil(fillBowl());
                break;
            case GET_MEAT:
                sleepUntil(getMeat());
                break;
            case USE_CLAY_OVEN:
            case FINISH_COOKING:
                sleepUntil(cook());
                break;
            case COMBINE_MEAT_PIE:
            case COMBINE_MEAT_WATER:
            case COMBINE_STEW:
            case COMBINE_PASTRY_DOUGH:
            case COMBINE_PIE_SHELL:
            case COMBINE_PIZZA_BASE:
            case COMBINE_PIZZA_TOMATO:
            case COMBINE_PIZZA_CHEESE:
            case COMBINE_PIZZA_PINEAPPLE:
            case CUT_PINEAPPLE:
                sleepUntil(combineItems(), 60000);
                break;
            case GET_CHEESE:
            case GET_TOMATOES:
            case GET_PINEAPPLES:
            case GET_FLOUR:
            case GET_POTATOES:
                sleepUntil(getFood());
                break;
            case USE_BUFFET_TABLE:
                if (isUnderAppreciationThreshold()) {
                    setCurrentState(State.HOP_WORLD);
                    return;
                }
                if (Rs2GameObject.canReach(BUFFET_TABLE_LOC)) {
                    Rs2GameObject.interact("Buffet table", "Serve");
                    sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.COOKING), 10000);
                } else {
                    debug("Cannot reach the buffet table, waiting...");
                }
                break;
            case GETTING_READY:
                if (!inTheMess()) return;
                sleepUntil(cleanInventory());
                break;
            case HOP_WORLD:
                if (!hopWorlds()) return;
                break;
            default:
                break;
        }
        if (currentState.getNext() != null) {
            setCurrentState(currentState.getNext());
            overlay.setStatus(currentState.getStatus());
        }
    }

    private BooleanSupplier returnEmptyBowls() {
        return () -> {
            if (Rs2Inventory.hasItem(ItemID.BOWL_EMPTY)) {
                Rs2Inventory.useItemOnObject(ItemID.BOWL_EMPTY, Rs2GameObject.getGameObject(UTENSIL_CUPBOARD_LOC).getId());
                sleepUntil(() -> Rs2Inventory.count(ItemID.BOWL_EMPTY) == 0, 10000);
                Rs2Antiban.actionCooldown();
                return !Rs2Inventory.hasItem(ItemID.BOWL_EMPTY);
            }
            return true;
        };
    }

    private boolean hopWorlds() {
        info("Hopping worlds...");
        int currentWorld = Microbot.getClient().getWorld();
        Microbot.hopToWorld(Login.getRandomWorld(true));
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
        if (Microbot.getClient().getWorld() != currentWorld) {
            info("Successfully hopped to a new world.");
            return true;
        } else {
            debug("Failed to hop worlds, retrying...");
        }
        return false;
    }

    private boolean isUnderAppreciationThreshold() {
        Widget appreciationBarWidget;
        switch (config.dish()) {
            case MEAT_PIE:
                appreciationBarWidget = Rs2Widget.getWidget(PIE_WIDGET_BAR_ID);
                break;
            case STEW:
                appreciationBarWidget = Rs2Widget.getWidget(STEW_WIDGET_BAR_ID);
                break;
            case PIZZA:
                appreciationBarWidget = Rs2Widget.getWidget(PIZZA_WIDGET_BAR_ID);
                break;
            default:
                debug("Unknown dish selected, cannot check appreciation.");
                return false;
        }

        Widget filledBar = appreciationBarWidget.getChild(0);

        if (filledBar == null) {
            debug("Appreciation bar widget not found, cannot check appreciation.");
            return false;
        }

        int filledPercentage = filledBar.getWidth() * 100 / appreciationBarWidget.getWidth();

        if (filledPercentage < config.appreciation_threshold()) {
            debug("Appreciation is below threshold.");
            return true;
        } else {
            debug("Appreciation is above threshold.");
        }
        return false;
    }

    private boolean inTheMess() {
        if (Rs2Widget.getWidget(InterfaceID.HosidiusServeryHud.CONTENT) != null) {
            debug("Already in the area, no need to move location.");
            return true;
        }
        info("Walking to the Hosidius Servery area...");
        return Rs2Walker.walkTo(new WorldPoint(1645, 3627, 0), 4);
    }

    private BooleanSupplier cleanInventory() {
        return () -> {
            if (!Rs2Inventory.isEmpty()) {
                if (Rs2Inventory.count() == 2 && Rs2Inventory.count(ItemID.KNIFE) == 2 && config.dish() == Dish.PIZZA) {
                    debug("Only knife in inventory, skipping inventory cleanup.");
                    return true;
                }

                Set<Integer> itemsToDrop = Set.of(
                        ItemID.BOWL_EMPTY,
                        ItemID.KNIFE,
                        ItemID.HOSIDIUS_SERVERY_PIEDISH,
                        ItemID.BURNT_PIZZA,
                        ItemID.BURNT_PIE,
                        ItemID.BURNT_STEW,
                        ItemID.BURNT_MEAT,
                        ItemID.HOSIDIUS_SERVERY_RAW_MEAT,
                        ItemID.HOSIDIUS_SERVERY_PINEAPPLE,
                        ItemID.HOSIDIUS_SERVERY_PINEAPPLE_CHUNKS,
                        ItemID.HOSIDIUS_SERVERY_TOMATO,
                        ItemID.HOSIDIUS_SERVERY_CHEESE,
                        ItemID.HOSIDIUS_SERVERY_POTATO,
                        ItemID.HOSIDIUS_SERVERY_POT_FLOUR,
                        ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH,
                        ItemID.HOSIDIUS_SERVERY_PIZZA_BASE,
                        ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA,
                        ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA,
                        ItemID.HOSIDIUS_SERVERY_PIE_SHELL,
                        ItemID.HOSIDIUS_SERVERY_COOKED_MEAT,
                        ItemID.HOSIDIUS_SERVERY_UNCOOKED_MEAT_PIE,
                        ItemID.HOSIDIUS_SERVERY_UNCOOKED_STEW,
                        ItemID.HOSIDIUS_SERVERY_UNCOOKED_PIZZA,
                        ItemID.HOSIDIUS_SERVERY_MEATWATER
                );

                int droppedItemsCount = (int) Rs2Inventory.items()
                        .filter(item -> itemsToDrop.contains(item.getId()))
                        .peek(item -> {
                            Rs2Inventory.drop(item.getId());
                            sleepGaussian(120, 40);
                        })
                        .count();

                if (droppedItemsCount > 0) {
                    debug("Dropped " + droppedItemsCount + " items from inventory.");
                    Rs2Antiban.actionCooldown();
                    return false;
                }
                info("Walking to the bank to deposit items...");
                return Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                        Rs2Inventory.items().map(Rs2ItemModel::getName).collect(Collectors.toList()),
                        false,
                        BankLocation.HOSIDIUS_KITCHEN,
                        Rs2Player.getWorldLocation(),
                        28,
                        3
                );
            }

            debug("Inventory is clean, no action needed");
            return true;
        };
    }

    private BooleanSupplier getUtensils() {
        int itemId;
        switch (getCurrentState()) {
            case GET_EMPTY_BOWLS:
                itemId = ItemID.BOWL_EMPTY;
                break;
            case GET_KNIFE:
                if (Rs2Inventory.count(ItemID.KNIFE) >= 2) {
                    debug("Already have 2 knives, skipping getting knife.");
                    return () -> true;
                }
                itemId = ItemID.KNIFE;
                break;
            case GET_EMPTY_PIE_DISHES:
                itemId = ItemID.HOSIDIUS_SERVERY_PIEDISH;
                break;
            default:
                debug("Unknown state for getting utensils, cannot proceed.");
                return () -> false;
        }
        return () -> {
            if (Rs2GameObject.canReach(UTENSIL_CUPBOARD_LOC)) {
                Rs2GameObject.interact(UTENSIL_CUPBOARD_LOC, "Search");
                sleepUntil(() -> Rs2Widget.isWidgetVisible(15859715));
                if (Rs2Widget.isWidgetVisible(15859715)) {
                    Widget utensilShop = Rs2Widget.getWidget(15859715);
                    if (utensilShop != null) {
                        Widget[] children = utensilShop.getDynamicChildren();
                        int index = IntStream.range(0, children.length)
                                .filter(i -> children[i].getItemId() == itemId)
                                .findFirst()
                                .orElse(-1);
                        if (index != -1) {
                            Rs2Widget.clickWidgetFast(children[index], index, 5);
                            sleepUntil(() -> Rs2Widget.hasWidget("Enter amount"));
                            if (Rs2Widget.hasWidget("Enter amount")) {
                                String amount;
                                switch (getCurrentState()) {
                                    case GET_KNIFE:
                                        amount = "2";
                                        break;
                                    case GET_EMPTY_BOWLS:
                                        if (config.dish() == Dish.PIZZA) {
                                            amount = "13";
                                            break;
                                        }
                                    default:
                                        amount = "14";
                                }
                                Rs2Keyboard.typeString(amount);
                                Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                                Rs2Antiban.actionCooldown();
                                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                                Rs2Inventory.waitForInventoryChanges(2000);
                                return Rs2Inventory.hasItem(itemId);
                            }
                        }
                    }
                }
            }
            return false;
        };
    }

    private BooleanSupplier fillBowl() {
        return () -> {
            if (Rs2Inventory.hasItem(ItemID.BOWL_EMPTY)) {
                Rs2Inventory.useItemOnObject(ItemID.BOWL_EMPTY, Rs2GameObject.getGameObject(SINK_LOC).getId());
                sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.BOWL_EMPTY),
                        10000);
                Rs2Antiban.actionCooldown();
                return true;
            }
            return false;
        };
    }

    private BooleanSupplier getMeat() {
        return () -> {
            if (Rs2GameObject.canReach(MEAT_TABLE_LOC)) {
                Rs2GameObject.interact(MEAT_TABLE_LOC, "Take-X");
                sleepUntil(() -> Rs2Widget.hasWidget("Enter amount"));
                if (Rs2Widget.hasWidget("Enter amount")) {
                    Rs2Keyboard.typeString("14");
                    Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                    Rs2Antiban.actionCooldown();
                }
                Rs2Inventory.waitForInventoryChanges(2000);
                Rs2Antiban.actionCooldown();
                return Rs2Inventory.hasItem(ItemID.HOSIDIUS_SERVERY_RAW_MEAT);
            }
            return false;
        };
    }

    private BooleanSupplier cook() {
        int itemId;
        switch (getCurrentState()) {
            case USE_CLAY_OVEN:
                itemId = ItemID.HOSIDIUS_SERVERY_RAW_MEAT;
                break;
            case FINISH_COOKING:
                if (config.dish() == Dish.MEAT_PIE) {
                    itemId = ItemID.HOSIDIUS_SERVERY_UNCOOKED_MEAT_PIE;
                } else if (config.dish() == Dish.STEW) {
                    itemId = ItemID.HOSIDIUS_SERVERY_UNCOOKED_STEW;
                } else if (config.dish() == Dish.PIZZA) {
                    itemId = ItemID.HOSIDIUS_SERVERY_UNCOOKED_PIZZA;
                } else {
                    debug("Unknown dish selected, cannot finish cooking.");
                    return () -> false;
                }
                break;
            default:
                debug("Unknown state for cooking, cannot proceed.");
                return () -> false;
        }
        return () -> {
            if (Rs2GameObject.canReach(CLAY_OVEN_LOC)) {
                Rs2Inventory.useItemOnObject(itemId, Rs2GameObject.getGameObject(CLAY_OVEN_LOC).getId());
                sleepUntil(() -> Rs2Widget.hasWidget("How many would you like to cook?"));
                if (Rs2Widget.hasWidget("How many would you like to cook?")) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                    Rs2Antiban.actionCooldown();
                }
                Rs2Inventory.waitForInventoryChanges(5000);
                sleepUntil(() -> !Rs2Player.isAnimating(1000), 50000);
                Rs2Antiban.actionCooldown();
            }
            return false;
        };

    }

    private BooleanSupplier combineItems() {
        int item1;
        int item2;
        switch (getCurrentState()) {
            case COMBINE_PASTRY_DOUGH:
            case COMBINE_PIZZA_BASE:
                item1 = ItemID.BOWL_WATER;
                item2 = ItemID.HOSIDIUS_SERVERY_POT_FLOUR;
                break;
            case COMBINE_PIE_SHELL:
                item1 = ItemID.HOSIDIUS_SERVERY_PIEDISH;
                item2 = ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH;
                break;
            case COMBINE_MEAT_PIE:
                item1 = ItemID.HOSIDIUS_SERVERY_PIE_SHELL;
                item2 = ItemID.HOSIDIUS_SERVERY_COOKED_MEAT;
                break;
            case COMBINE_MEAT_WATER:
                item1 = ItemID.BOWL_WATER;
                item2 = ItemID.HOSIDIUS_SERVERY_COOKED_MEAT;
                break;
            case COMBINE_STEW:
                item1 = ItemID.HOSIDIUS_SERVERY_MEATWATER;
                item2 = ItemID.HOSIDIUS_SERVERY_POTATO;
                break;
            case COMBINE_PIZZA_TOMATO:
                item1 = ItemID.HOSIDIUS_SERVERY_PIZZA_BASE;
                item2 = ItemID.HOSIDIUS_SERVERY_TOMATO;
                break;
            case COMBINE_PIZZA_CHEESE:
                item1 = ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA;
                item2 = ItemID.HOSIDIUS_SERVERY_CHEESE;
                break;
            case COMBINE_PIZZA_PINEAPPLE:
                item1 = ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA;
                item2 = ItemID.HOSIDIUS_SERVERY_PINEAPPLE_CHUNKS;
                break;
            case CUT_PINEAPPLE:
                item1 = ItemID.KNIFE;
                item2 = ItemID.HOSIDIUS_SERVERY_PINEAPPLE;
                break;
            default:
                debug("Unknown state for combining items, cannot proceed.");
                return () -> false;
        }
        return () -> {
            if (Rs2Inventory.hasItem(item1) && Rs2Inventory.hasItem(item2)) {
                boolean alreadyInRightSlots = (Rs2Inventory.slotContains(26, item1) && Rs2Inventory.slotContains(27, item2)) ||
                        (Rs2Inventory.slotContains(26, item2) && Rs2Inventory.slotContains(27, item1));

                if (!alreadyInRightSlots) {
                    Rs2ItemModel lastOfItem1 = Rs2Inventory.getLast(item1);
                    Rs2ItemModel lastOfItem2 = Rs2Inventory.getLast(item2);

                    if (lastOfItem1 != null && lastOfItem2 != null) {
                        if (lastOfItem1.getSlot() != 26 && lastOfItem1.getSlot() != 27) {
                            if (lastOfItem2.getSlot() == 26) {
                                Rs2Inventory.moveItemToSlot(lastOfItem1, 27);
                            } else if (lastOfItem2.getSlot() == 27) {
                                Rs2Inventory.moveItemToSlot(lastOfItem1, 26);
                            } else {
                                Rs2Inventory.moveItemToSlot(lastOfItem1, 26);
                                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(2000));
                                Rs2Inventory.moveItemToSlot(lastOfItem2, 27);
                                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(2000));
                            }
                        }
                    } else {
                        debug("Failed to find items in inventory, cannot combine.");
                        return false;
                    }
                }

                Widget item1Widget = Rs2Inventory.getInventoryWidget().getChild(26);
                Widget item2Widget = Rs2Inventory.getInventoryWidget().getChild(27);
                if (getCurrentState() == State.COMBINE_PASTRY_DOUGH || getCurrentState() == State.COMBINE_PIZZA_BASE) {
                    String option = (getCurrentState() == State.COMBINE_PASTRY_DOUGH) ? "Pastry dough" : "Pizza base";
                    Rs2Widget.clickWidget(item1Widget);
                    Rs2Widget.clickWidget(item2Widget);
                    Rs2Dialogue.sleepUntilSelectAnOption();
                    Rs2Dialogue.keyPressForDialogueOption(option);
                    sleepUntil(() -> !Rs2Inventory.hasItem(item1), 30000);
                    Rs2Antiban.actionCooldown();
                    return true;
                } else if (getCurrentState() == State.CUT_PINEAPPLE) {
                    while (Rs2Inventory.hasItem(ItemID.HOSIDIUS_SERVERY_PINEAPPLE) || !isRunning()) {
                        Rs2Widget.clickWidget(item1Widget);
                        sleepGaussian(120, 40);
                        Rs2Widget.clickWidget(item2Widget);
                        sleepGaussian(120, 40);
                    }

                    Rs2Inventory.moveItemToSlot(Rs2Inventory.getLast(ItemID.KNIFE), (Rs2Inventory.slotContains(0, ItemID.KNIFE))? 1 : 0);
                    sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(2000));
                    return true;
                } else {
                    while (Rs2Inventory.hasItem(item1) || !isRunning()) {
                        Rs2Widget.clickWidget(item1Widget);
                        sleepGaussian(120, 40);
                        Rs2Widget.clickWidget(item2Widget);
                        sleepGaussian(120, 40);
                    }
                    return true;
                }

            }
            return false;
        };
    }

    private BooleanSupplier getFood() {
        int itemId;
        switch (getCurrentState()) {
            case GET_POTATOES:
                itemId = ItemID.HOSIDIUS_SERVERY_POTATO;
                break;
            case GET_PINEAPPLES:
                itemId = ItemID.HOSIDIUS_SERVERY_PINEAPPLE;
                break;
            case GET_TOMATOES:
                itemId = ItemID.HOSIDIUS_SERVERY_TOMATO;
                break;
            case GET_CHEESE:
                itemId = ItemID.HOSIDIUS_SERVERY_CHEESE;
                break;
            case GET_FLOUR:
                itemId = ItemID.HOSIDIUS_SERVERY_POT_FLOUR;
                break;
            default:
                debug("Unknown state for getting food, cannot proceed.");
                return () -> false;
        }
        return () -> {
            if (Rs2GameObject.canReach(FOOD_CUPBOARD_LOC)) {
                Rs2GameObject.interact(FOOD_CUPBOARD_LOC, "Search");
                sleepUntil(() -> Rs2Widget.isWidgetVisible(15859715));
                if (Rs2Widget.isWidgetVisible(15859715)) {
                    Widget foodShop = Rs2Widget.getWidget(15859715);
                    if (foodShop != null) {
                        Widget[] children = foodShop.getDynamicChildren();
                        int index = IntStream.range(0, children.length)
                                .filter(i -> children[i].getItemId() == itemId)
                                .findFirst()
                                .orElse(-1);
                        if (index != -1) {
                            Rs2Widget.clickWidgetFast(children[index], index, 4);
                            Rs2Inventory.waitForInventoryChanges(2000);
                            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                            Rs2Antiban.actionCooldown();
                            return Rs2Inventory.hasItem(itemId);
                        }
                    }
                }
            }
            return false;
        };
    }

    private void setOrderOfStates() {
        switch (config.dish()) {
            case MEAT_PIE:
                State.GETTING_READY.setNext(State.GET_EMPTY_BOWLS);
                State.GET_EMPTY_BOWLS.setNext(State.GET_FLOUR);
                State.GET_FLOUR.setNext(State.USE_SINK);
                State.USE_SINK.setNext(State.COMBINE_PASTRY_DOUGH);
                State.COMBINE_PASTRY_DOUGH.setNext(State.RETURN_EMPTY_BOWLS);
                State.RETURN_EMPTY_BOWLS.setNext(State.GET_EMPTY_PIE_DISHES);
                State.GET_EMPTY_PIE_DISHES.setNext(State.COMBINE_PIE_SHELL);
                State.COMBINE_PIE_SHELL.setNext(State.GET_MEAT);
                State.GET_MEAT.setNext(State.USE_CLAY_OVEN);
                State.USE_CLAY_OVEN.setNext(State.COMBINE_MEAT_PIE);
                State.COMBINE_MEAT_PIE.setNext(State.FINISH_COOKING);
                State.FINISH_COOKING.setNext(State.USE_BUFFET_TABLE);
                break;
            case STEW:
                State.GETTING_READY.setNext(State.GET_EMPTY_BOWLS);
                State.GET_EMPTY_BOWLS.setNext(State.USE_SINK);
                State.USE_SINK.setNext(State.GET_MEAT);
                State.GET_MEAT.setNext(State.USE_CLAY_OVEN);
                State.USE_CLAY_OVEN.setNext(State.COMBINE_MEAT_WATER);
                State.COMBINE_MEAT_WATER.setNext(State.GET_POTATOES);
                State.GET_POTATOES.setNext(State.COMBINE_STEW);
                State.COMBINE_STEW.setNext(State.FINISH_COOKING);
                State.FINISH_COOKING.setNext(State.USE_BUFFET_TABLE);
                break;
            case PIZZA:
                State.GETTING_READY.setNext(State.GET_KNIFE);
                State.GET_KNIFE.setNext(State.GET_EMPTY_BOWLS);
                State.GET_EMPTY_BOWLS.setNext(State.GET_FLOUR);
                State.GET_FLOUR.setNext(State.USE_SINK);
                State.USE_SINK.setNext(State.COMBINE_PIZZA_BASE);
                State.COMBINE_PIZZA_BASE.setNext(State.RETURN_EMPTY_BOWLS);
                State.RETURN_EMPTY_BOWLS.setNext(State.GET_TOMATOES);
                State.GET_TOMATOES.setNext(State.COMBINE_PIZZA_TOMATO);
                State.COMBINE_PIZZA_TOMATO.setNext(State.GET_CHEESE);
                State.GET_CHEESE.setNext(State.COMBINE_PIZZA_CHEESE);
                State.COMBINE_PIZZA_CHEESE.setNext(State.GET_PINEAPPLES);
                State.GET_PINEAPPLES.setNext(State.CUT_PINEAPPLE);
                State.CUT_PINEAPPLE.setNext(State.FINISH_COOKING);
                State.FINISH_COOKING.setNext(State.COMBINE_PIZZA_PINEAPPLE);
                State.COMBINE_PIZZA_PINEAPPLE.setNext(State.USE_BUFFET_TABLE);
                break;
            default:
                debug("Unknown dish selected, cannot set order of states.");
        }
        State.USE_BUFFET_TABLE.setNext(State.WAITING);
        State.WAITING.setNext(State.GETTING_READY);
        State.HOP_WORLD.setNext(State.USE_BUFFET_TABLE);
    }

    private void info(String message) {
        Microbot.log(Level.INFO, message);
    }

    private void debug(String message) {
        Microbot.log(Level.DEBUG, message);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        setCurrentState(State.WAITING);
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        debug("The Mess script has been shut down.");
    }

    public enum State {
        WAITING("Waiting", null),
        GETTING_READY("Getting ready", null),

        GET_EMPTY_BOWLS("Getting empty bowls", null),
        GET_EMPTY_PIE_DISHES("Getting empty pie dishes", null),
        GET_KNIFE("Getting knife", null),

        RETURN_EMPTY_BOWLS("Returning empty bowls", null),

        GET_MEAT("Getting raw meat", null),

        GET_POTATOES("Getting supplies", null),
        GET_PINEAPPLES("Getting supplies", null),
        GET_TOMATOES("Getting supplies", null),
        GET_CHEESE("Getting supplies", null),
        GET_FLOUR("Getting supplies", null),

        USE_SINK("Using sink", null),

        USE_CLAY_OVEN("Cooking", null),
        FINISH_COOKING("Finishing cooking", null),

        COMBINE_PASTRY_DOUGH("Combining ingredients", null),
        COMBINE_PIE_SHELL("Combining ingredients", null),
        COMBINE_MEAT_PIE("Combining ingredients", null),

        COMBINE_MEAT_WATER("Combining ingredients", null),
        COMBINE_STEW("Combining ingredients", null),

        COMBINE_PIZZA_BASE("Combining ingredients", null),
        COMBINE_PIZZA_TOMATO("Combining ingredients", null),
        COMBINE_PIZZA_CHEESE("Combining ingredients", null),
        COMBINE_PIZZA_PINEAPPLE("Combining ingredients", null),

        CUT_PINEAPPLE("Cutting the pineapples", null),

        USE_BUFFET_TABLE("Serving the food", null),
        HOP_WORLD("Hopping worlds", null);


        @Getter
        private final String status;

        @Setter
        @Getter
        private State next;

        State(String status, State next) {
            this.status = status;
            this.next = next;
        }
    }

    public enum Dish {
        MEAT_PIE("Servery Meat Pie"),
        STEW("Servery Stew"),
        PIZZA("Servery Pineapple Pizza");

        @Getter
        private final String name;

        Dish(String name) {
            this.name = name;
        }
    }
}
