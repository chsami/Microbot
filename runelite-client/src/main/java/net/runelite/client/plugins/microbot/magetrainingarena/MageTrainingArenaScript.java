package net.runelite.client.plugins.microbot.magetrainingarena;

import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.magetrainingarena.enums.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.*;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.mta.MTAPlugin;
import net.runelite.client.plugins.mta.alchemy.AlchemyRoomTimer;
import net.runelite.client.plugins.mta.telekinetic.TelekineticRoom;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static net.runelite.client.plugins.microbot.util.magic.Rs2Magic.getRs2Staff;
import static net.runelite.client.plugins.microbot.util.magic.Rs2Magic.getRs2Tome;

public class MageTrainingArenaScript extends Script {
    public static String version = "1.1.2";

    private static boolean firstTime = false;

    private static final WorldPoint portalPoint = new WorldPoint(3363, 3318, 0);
    private static final WorldPoint bankPoint = new WorldPoint(3365, 3318, 1);

    private MageTrainingArenaConfig config;
    private Rooms currentRoom;
    private int nextHpThreshold = 50;
    private Boolean btp = null;
    private int shapesToPick = 3;

    @Getter
    private static MTAPlugin mtaPlugin;
    @Getter
    private static final Map<Points, Integer> currentPoints = Arrays.stream(Points.values()).collect(Collectors.toMap(x -> x, x -> -1));
    @Getter
    private static int bought;
    @Getter
    private static int buyable;

    public boolean run(MageTrainingArenaConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = true;
        bought = 0;
        buyable = 0;
        Rs2Walker.disableTeleports = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (mtaPlugin != null && !Microbot.getPluginManager().isActive(mtaPlugin)) return;

                if (!Rs2Magic.isSpellbook(Rs2Spellbook.MODERN)) {
                    Microbot.log("Wrong spellbook found...please use the modern spellbook for this script.");
                    sleep(5000);
                    return;
                }

                if (mtaPlugin == null) {
                    if (Microbot.getPluginManager() == null) return;

                    mtaPlugin = (MTAPlugin) Microbot.getPluginManager().getPlugins()
                            .stream().filter(x -> x instanceof net.runelite.client.plugins.mta.MTAPlugin)
                            .findFirst().orElse(null);

                    return;
                }

                if (handleFirstTime())
                    return;

                currentRoom = getCurrentRoom();
                updatePoints();
                if (initPoints())
                    return;

                if (currentRoom == null) {
                    if (ensureInventory())
                        return;

                    if (currentPoints.entrySet().stream().allMatch(x -> getRequiredPoints().get(x.getKey()) * (config.buyRewards() ? 1 : (buyable + 1)) <= x.getValue())) {
                        if (config.buyRewards()) {
                            var rewardToBuy = config.reward();
                            while (rewardToBuy.getPreviousReward() != null && !Rs2Inventory.contains(rewardToBuy.getPreviousReward().getItemId()))
                                rewardToBuy = rewardToBuy.getPreviousReward();

                            buyReward(rewardToBuy);
                        } else {
                            buyable = getRequiredPoints().entrySet().stream()
                                    .mapToInt(x -> currentPoints.get(x.getKey()) / x.getValue())
                                    .min().orElseThrow();
                        }
                        return;
                    }

                    var missingPoints = currentPoints.entrySet().stream()
                            .filter(entry -> {
                                var required = getRequiredPoints().get(entry.getKey()) * (config.buyRewards() ? 1 : (buyable + 1));
                                return required > entry.getValue()
                                        && Arrays.stream(Rooms.values())
                                        .anyMatch(room -> room.getPoints() == entry.getKey()
                                                && room.getRequirements().getAsBoolean());
                            })
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());

                    if (!missingPoints.isEmpty()) {
                        var index = Rs2Random.between(0, missingPoints.size());
                        var nextRooms = Arrays.stream(Rooms.values())
                                .filter(room -> room.getPoints() == missingPoints.get(index))
                                .collect(Collectors.toList());

                        enterRoom(nextRooms.get(0));
                    } else {
                        Microbot.showMessage("MTA: Out of runes! Please restart the plugin after you restocked on runes.");
                        sleep(500);
                        shutdown();
                    }
                } else if (!currentRoom.getRequirements().getAsBoolean()
                        || currentPoints.get(currentRoom.getPoints()) >= getRequiredPoints().get(currentRoom.getPoints()) * (config.buyRewards() ? 1 : (buyable + 1))) {
                    leaveRoom();
            } else {
                    switch (currentRoom) {
                        case ALCHEMIST:
                            handleAlchemistRoom();
                            break;
                        case GRAVEYARD:
                            handleGraveyardRoom();
                            break;
                        case ENCHANTMENT:
                            handleEnchantmentRoom();
                            break;
                        case TELEKINETIC:
                            handleTelekineticRoom();
                            break;
                    }
                }

                sleepGaussian(600, 150);
            } catch (Exception ex) {
                if (ex instanceof InterruptedException)
                    return;

                System.out.println(ex.getMessage());
                ex.printStackTrace(System.out);
                Microbot.log("MTA Exception: " + ex.getMessage());
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean ensureInventory() {
        var reward = config.reward();
        var previousRewards = new ArrayList<Integer>();
        while (reward.getPreviousReward() != null) {
            reward = reward.getPreviousReward();
            previousRewards.add(reward.getItemId());
        }

        Predicate<Rs2ItemModel> additionalItemPredicate = x -> !x.getName().toLowerCase().contains("rune")
                && !x.getName().toLowerCase().contains("staff")
                && !x.getName().toLowerCase().contains("tome")
                && !previousRewards.contains(x.getId());

        if (Rs2Inventory.contains(additionalItemPredicate)) {
            if (!Rs2Bank.walkToBankAndUseBank())
                return true;

            Rs2Bank.depositAll(additionalItemPredicate);
            return true;
        }

        return false;
    }

    private boolean initPoints() {
        if (currentPoints.values().stream().anyMatch(x -> x == -1)) {
            if (currentRoom != null)
                leaveRoom();
            else
                Rs2Walker.walkTo(portalPoint);

            return true;
        }

        return false;
    }

    private void updatePoints() {
        for (var points : currentPoints.entrySet()) {
            int gain = 0;
            if (points.getKey() == Points.ALCHEMIST && currentRoom == Rooms.ALCHEMIST && Rs2Inventory.hasItem("Coins"))
                gain = Rs2Inventory.get("Coins").getQuantity() / 100;

            var widget = Rs2Widget.getWidget(points.getKey().getWidgetId(), points.getKey().getChildId());
            if (widget != null && !Microbot.getClientThread().runOnClientThreadOptional(widget::isHidden).orElse(false))
                currentPoints.put(points.getKey(), Integer.parseInt(widget.getText().replace(",", "")));
            else {
                var roomWidget = Rs2Widget.getWidget(points.getKey().getRoomWidgetId(), points.getKey().getRoomChildId());
                if (roomWidget != null)
                    currentPoints.put(points.getKey(), Integer.parseInt(roomWidget.getText().replace(",", "")) + gain);
            }
        }
    }

    private Map<Points, Integer> getRequiredPoints() {
        return getRequiredPoints(config);
    }

    public static Map<Points, Integer> getRequiredPoints(MageTrainingArenaConfig config) {
        var currentReward = config.reward();
        var requiredPoints = currentReward.getPoints().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        while (currentReward.getPreviousReward() != null) {
            currentReward = currentReward.getPreviousReward();
            if (Rs2Inventory.contains(currentReward.getItemId()))
                break;

            for (var points : requiredPoints.entrySet())
                points.setValue(points.getValue() + currentReward.getPoints().get(points.getKey()));
        }

        return requiredPoints;
    }

    private void handleEnchantmentRoom() {
        MagicAction enchant;
        var magicLevel = Microbot.getClient().getBoostedSkillLevel(Skill.MAGIC);

        if (magicLevel >= 87 && currentRoom.getRequirements().getAsBoolean()) {
            enchant = MagicAction.ENCHANT_ONYX_JEWELLERY;
        } else if (magicLevel >= 68 && currentRoom.getRequirements().getAsBoolean()) {
            enchant = MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY;
        } else if (magicLevel >= 57 && currentRoom.getRequirements().getAsBoolean()) {
            enchant = MagicAction.ENCHANT_DIAMOND_JEWELLERY;
        } else if (magicLevel >= 49 && currentRoom.getRequirements().getAsBoolean()) {
            enchant = MagicAction.ENCHANT_RUBY_JEWELLERY;
        } else if (magicLevel >= 27 && currentRoom.getRequirements().getAsBoolean()) {
            enchant = MagicAction.ENCHANT_EMERALD_JEWELLERY;
        } else {
            enchant = MagicAction.ENCHANT_SAPPHIRE_JEWELLERY;
        }

        if (areRoomRequirementsInvalid()) return;

        if (Rs2Inventory.isFull()) {
            if (!Rs2Walker.walkTo(new WorldPoint(3363, 9640, 0)))
                return;

            Rs2GameObject.interact(ObjectID.MAGICTRAINING_ENCHA_HOLE, "Deposit");
            Rs2Player.waitForWalking();
            return;
        }

        boolean successFullLoot = Rs2Inventory.waitForInventoryChanges(() -> {
            Rs2GroundItem.loot(ItemID.MAGICTRAINING_DRAGONSTONE, 12);
            sleepUntil(() -> !Rs2Player.isMoving());
        });

        if (successFullLoot && Rs2Inventory.emptySlotCount() > 0)
            return;

        var bonusShape = getBonusShape();
        if (bonusShape == null) return;

        var object = Rs2GameObject.getGameObject(obj -> (obj.getId() == bonusShape.getObjectId()) && Rs2Camera.isTileOnScreen(obj));

        if (object == null) {
            var index = Rs2Random.between(0, 4);
            Rs2Walker.walkTo(new WorldPoint[]{
                    new WorldPoint(3347, 9655, 0),
                    new WorldPoint(3378, 9655, 0),
                    new WorldPoint(3379, 9624, 0),
                    new WorldPoint(3346, 9624, 0)
            }[index]);
            Rs2Player.waitForWalking();
            return;
        }

        int itemId;
        if (Rs2Inventory.contains(ItemID.MAGICTRAINING_DRAGONSTONE))
            itemId = ItemID.MAGICTRAINING_DRAGONSTONE;
        else
            itemId = bonusShape.getItemId();

        if (Rs2Inventory.contains(ItemID.MAGICTRAINING_DRAGONSTONE) || Rs2Inventory.count(itemId) >= shapesToPick) {
            shapesToPick = Rs2Random.between(2, 4);

            Rs2Magic.cast(enchant);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY);
            sleepGaussian(600, 150);
            Rs2Inventory.interact(itemId);

            sleepUntil(() -> !Rs2Inventory.contains(itemId) || itemId != ItemID.MAGICTRAINING_DRAGONSTONE && bonusShape != getBonusShape(), 20_000);
        } else if (Rs2Inventory.waitForInventoryChanges(() -> Rs2GameObject.interact(object, "Take-from")) && Rs2Player.getWorldLocation().distanceTo(object.getWorldLocation()) > 10) {
            Rs2Walker.setTarget(null);
        } else
            Rs2Walker.walkFastCanvas(object.getWorldLocation());
    }

    private EnchantmentShapes getBonusShape() {
        for (var shape : EnchantmentShapes.values())
            if (Rs2Widget.isWidgetVisible(shape.getWidgetId(), shape.getWidgetChildId()))
                return shape;

        return null;
    }

    private void handleTelekineticRoom() {
        if (areRoomRequirementsInvalid()) return;

        var room = mtaPlugin.getTelekineticRoom();
        var teleRoom = Arrays.stream(TelekineticRooms.values())
                .filter(x -> Rs2Player.getWorldLocation().distanceTo(x.getArea()) == 0)
                .findFirst().orElseThrow();

        // Walk to maze if guardian is not visible
        WorldPoint target;
        if (room.getTarget() != null)
            target = room.getTarget();
        else {
            Rs2Walker.walkTo(teleRoom.getMaze(), 2);
            sleepUntil(() -> room.getTarget() != null, 10_000);
            // MageTrainingArenaScript is dependent on the official mage arena plugin of runelite
            // In some cases it glitches out and target is not defined by an arrow, in this case we will reset them room
            if (room.getTarget() == null) {
                Microbot.log("Something seems wrong, room target was still not found...leaving room to reset.");
                leaveRoom();
                return;
            }
            target = room.getTarget();
            sleep(400, 600);
        }

        var localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        var targetConverted = WorldPoint.fromLocalInstance(Microbot.getClient(), Objects.requireNonNull(localTarget));

        if (Microbot.getClient().getScale() < 400) {
            Rs2Camera.setZoom(Rs2Random.between(400, 430));
        }

        if (room.getGuardian().getWorldLocation().equals(room.getFinishLocation())) {
            sleepUntil(() -> room.getGuardian().getId() == NpcID.MAGICTRAINING_GUARD_MAZE_COMPLETE);
            sleep(200, 400);
            Rs2Npc.interact(new Rs2NpcModel(room.getGuardian()), "New-maze");
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(teleRoom.getArea()) != 0);
        } else {
            if (!Rs2Player.getWorldLocation().equals(targetConverted)
                    && (Microbot.getClient().getLocalDestinationLocation() == null
                    || !Microbot.getClient().getLocalDestinationLocation().equals(localTarget))) {
                if (Rs2Camera.isTileOnScreen(localTarget) && Rs2Player.getWorldLocation().distanceTo(targetConverted) < 10) {
                    Rs2Walker.walkFastCanvas(targetConverted);
                    sleepGaussian(600, 150);
                } else {
                    Rs2Walker.walkTo(targetConverted);
                }
            }

            if (!Rs2Player.isAnimating()
                    && StreamSupport.stream(Microbot.getClient().getProjectiles().spliterator(), false).noneMatch(x -> x.getId() == SpotanimID.TELEGRAB_TRAVEL)
                    && !TelekineticRoom.getMoves().isEmpty()
                    && TelekineticRoom.getMoves().peek() == room.getPosition()
                    && room.getGuardian().getId() != NpcID.MAGICTRAINING_GUARD_MAZE_MOVING
                    && !room.getGuardian().getLocalLocation().equals(room.getDestination())) {
                Rs2Magic.cast(MagicAction.TELEKINETIC_GRAB);
                sleepGaussian(600, 150);
                if (!Rs2Camera.isTileOnScreen(room.getGuardian().getLocalLocation())) {
                    Rs2Camera.turnTo(room.getGuardian());
                }
                Rs2Npc.interact(new Rs2NpcModel(room.getGuardian()));
            }
        }
    }

    private void handleGraveyardRoom() {
        if (areRoomRequirementsInvalid()) {
            leaveRoom();
            return;
        }

        if (btp == null)
            btp = Rs2Magic.canCast(MagicAction.BONES_TO_PEACHES);

        var boneGoal = 28 - Rs2Inventory.items().filter(x -> x.getName().equalsIgnoreCase("Animals' bones")).count();
        if (mtaPlugin.getGraveyardRoom().getCounter() != null && mtaPlugin.getGraveyardRoom().getCounter().getCount() >= boneGoal) {
            Rs2Magic.cast(btp ? MagicAction.BONES_TO_PEACHES : MagicAction.BONES_TO_BANANAS);
            Rs2Player.waitForAnimation();
            return;
        }

        if (Rs2Inventory.contains(ItemID.BANANA, ItemID.PEACH)) {
            var currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            var maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
            if ((currentHp * 100) / maxHp < nextHpThreshold) {
                var maxAmountToEat = (maxHp - currentHp) / (btp ? 8 : 2);
                var amountToEat = Rs2Random.between(Math.min(2, maxAmountToEat), Math.min(6, maxAmountToEat));
                nextHpThreshold = Rs2Random.between(config.healingThresholdMin(), config.healingThresholdMax());
                for (int i = 0; i < amountToEat; i++) {
                    Rs2Inventory.interact(btp ? ItemID.PEACH : ItemID.BANANA, "eat");

                    if (i < amountToEat - 1)
                        sleepGaussian(1400, 350);
                }
            }

            Rs2Inventory.waitForInventoryChanges(() -> Rs2GameObject.interact(new WorldPoint(3354, 9639, 1), "Deposit"));
            return;
        }

        Rs2GameObject.interact(new WorldPoint(3352, 9637, 1), "Grab");
        Rs2Player.waitForWalking();
    }

    private void handleAlchemistRoom() {
        if (areRoomRequirementsInvalid()) return;

        var room = mtaPlugin.getAlchemyRoom();
        var best = room.getBest();
        var item = Rs2Inventory.get(best.getId());
        if (item != null) {
            Rs2Magic.alch(item);
            return;
        }else {
            Rs2Inventory.dropAll(6897,6896,6895,6894,6893);
        }

        var timer = (AlchemyRoomTimer) Microbot.getInfoBoxManager().getInfoBoxes().stream()
                .filter(x -> x instanceof AlchemyRoomTimer)
                .findFirst().orElse(null);
        if (timer == null || Integer.parseInt(timer.getText().split(":")[1]) < 2) {
            Rs2Walker.walkTo(3364, 9636, 2, 2);
            return;
        }

        if (room.getSuggestion() == null) {
            Rs2GameObject.interact("Cupboard", "Search");

            if (sleepUntilTrue(Rs2Player::isMoving, 100, 1000))
                sleepUntil(() -> !Rs2Player.isMoving());
        } else {
            Rs2Inventory.waitForInventoryChanges(() -> Rs2GameObject.interact(room.getSuggestion().getGameObject(), "Take-5"));
        }
    }

    private boolean areRoomRequirementsInvalid() {
        if (!currentRoom.getRequirements().getAsBoolean()) {
            Microbot.log("You're missing room requirements. Please restock or fix your staves settings.");
            sleep(5000);
            return true;
        }
        return false;
    }

    private void buyReward(Rewards reward) {
        if (!Rs2Walker.walkTo(bankPoint))
            return;

        if (!Rs2Widget.isWidgetVisible(197, 0)) {
            Rs2Npc.interact(NpcID.MAGICTRAINING_GUARD_REWARDS, "Trade-with");
            sleepUntil(() -> Rs2Widget.isWidgetVisible(197, 0));
            sleepGaussian(600, 150);
            return;
        }

        Rs2Inventory.waitForInventoryChanges(() -> {
            var rewardWidgets = Rs2Widget.getWidget(197, 11).getDynamicChildren();
            if (rewardWidgets == null) return;
            var widget = Arrays.stream(rewardWidgets).filter(x -> x.getItemId() == reward.getItemId()).findFirst().orElse(null);
            Rs2Widget.clickWidgetFast(widget, Arrays.asList(rewardWidgets).indexOf(widget));
            sleepGaussian(600, 150);
            Rs2Widget.clickWidget(197, 9);
            sleepGaussian(600, 150);
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        });

        if (reward == config.reward())
            bought++;
    }

    public static Rooms getCurrentRoom() {
        for (var room : Rooms.values()) {
            if (room == Rooms.TELEKINETIC && Arrays.stream(TelekineticRooms.values()).anyMatch(x -> Rs2Player.getWorldLocation().distanceTo(x.getArea()) == 0)
                    || room.getArea() != null && Rs2Player.getWorldLocation().distanceTo(room.getArea()) == 0)
                return room;
        }

        return null;
    }

    public static void enterRoom(Rooms room) {
        if (!Rs2Walker.walkTo(portalPoint))
            return;

        Rs2GameObject.interact(room.getTeleporter(), "Enter");
        Rs2Player.waitForAnimation();
        if (Rs2Widget.hasWidget("You must talk to the Entrance Guardian"))
            firstTime = true;
    }

    public static void leaveRoom() {
        var room = getCurrentRoom();

        if (room == null)
            return;

        WorldPoint exit = null;
        if (room != Rooms.TELEKINETIC)
            exit = room.getExit();
        else {
            for (var teleRoom : TelekineticRooms.values()) {
                if (Rs2Player.getWorldLocation().distanceTo(teleRoom.getArea()) == 0) {
                    exit = teleRoom.getExit();
                    break;
                }
            }
        }

        if (!Rs2Walker.walkTo(exit))
            return;

        Rs2GameObject.interact(ObjectID.MAGICTRAINING_RETURNDOOR, "Enter");
        Rs2Player.waitForWalking();
    }

    private boolean handleFirstTime() {
        if (firstTime) {
            if (!Rs2Walker.walkTo(new WorldPoint(3363, 3304, 0)))
                return true;

            if (!Rs2Dialogue.isInDialogue())
                Rs2Npc.interact(NpcID.MAGICTRAINING_GUARD_ENTRANCE, "Talk-to");
            else if (Rs2Dialogue.hasSelectAnOption() && Rs2Widget.hasWidget("I'm new to this place"))
                Rs2Widget.clickWidget("I'm new to this place");
            else if (Rs2Dialogue.hasSelectAnOption() && Rs2Widget.hasWidget("Thanks, bye!")) {
                Rs2Widget.clickWidget("Thanks, bye!");
                firstTime = false;
            } else
                Rs2Dialogue.clickContinue();

            return true;
        }

        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    /**
     * Attempts to equip the best available {@code staff} from inventory that reduces rune cost for the specified {@link Rs2Spells} spell.
     * This method also accounts for any equipped or equippable {@code tome} in the shield slot which may provide passive rune substitution.
     *
     * <p>The method evaluates all available staff+tome combinations, selects the one with the highest effective rune savings,
     * equips the staff if not already equipped, and verifies that the spell is now castable with the resulting equipment and runes.
     *
     * <p><strong>Logic Summary:</strong>
     * <ul>
     *     <li>Scans equipped weapon/shield and inventory for candidate staff and tome combinations</li>
     *     <li>Simulates rune savings provided by each staff+tome combo</li>
     *     <li>Verifies castability after accounting for free runes and available inventory/pouch supply</li>
     *     <li>Equips the best staff (if not already equipped) and confirms the spell is ready for casting</li>
     * </ul>
     *
     * <p>This method <strong>does not</strong> return {@code true} unless a valid staff was equipped (or already equipped)
     * and the spell is verifiably castable after staff+tome rune substitution.
     *
     * @param spell The {@link Rs2Spells} spell to evaluate for castability.
     * @param hasRunePouch {@code true} if the player's rune pouch is available and should be included in rune calculations.
     * @return {@code true} if the spell is castable after equipping the best available staff (and considering any tome); {@code false} otherwise.
     *
     * <p><strong>Side effects:</strong> May trigger staff equipping from inventory. No tome swapping is performed — only passively recognized if equipped.</p>
     */
    public static boolean tryEquipBestStaffAndCast(Rs2Spells spell, boolean hasRunePouch) {
        Map<Runes, Integer> requiredRunes = spell.getRequiredRunes();

        List<Rs2ItemModel> candidates = new ArrayList<>();
        Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equipped != null) candidates.add(equipped);
        candidates.addAll(Rs2Inventory.items().collect(Collectors.toList()));

        Rs2Tome equippedTome = Rs2Tome.NONE;
        Rs2ItemModel shield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (shield != null) equippedTome = getRs2Tome(shield.getId());

        Map<Runes, Integer> inventoryRunes = new EnumMap<>(Runes.class);
        Rs2Inventory.items().forEach(item -> {
            Runes rune = Runes.byItemId(item.getId());
            if (rune != null) {
                inventoryRunes.merge(rune, item.getQuantity(), Integer::sum);
            }
        });

        if (hasRunePouch) {
            Rs2RunePouch.getRunes().forEach((id, qty) -> {
                Runes rune = Runes.byItemId(id.getItemId());
                if (rune != null) {
                    inventoryRunes.merge(rune, qty, Integer::sum);
                }
            });
        }

        int maxSavings = -1;
        Integer bestStaffId = null;
        Set<Runes> bestProvidedRunes = null;

        for (Rs2ItemModel staffItem : candidates) {
            Rs2Staff staff = getRs2Staff(staffItem.getId());
            if (staff == Rs2Staff.NONE) continue;

            Set<Runes> providedRunes = new HashSet<>(staff.getRunes());
            if (equippedTome != Rs2Tome.NONE) providedRunes.addAll(equippedTome.getRunes());

            boolean castable = true;
            int savings = 0;

            for (Map.Entry<Runes, Integer> entry : requiredRunes.entrySet()) {
                if (providedRunes.contains(entry.getKey())) {
                    savings += entry.getValue();
                    continue;
                }
                if (inventoryRunes.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    castable = false;
                    break;
                }
            }

            if (castable && savings > maxSavings) {
                maxSavings = savings;
                bestStaffId = staff.getItemID();
                bestProvidedRunes = providedRunes;
            }
        }

        if (bestStaffId != null) {
            if (!Rs2Equipment.isWearing(bestStaffId)) {
                Rs2Inventory.wear(bestStaffId);
            }

            Set<Runes> activeRunes = new HashSet<>(bestProvidedRunes);
            Rs2ItemModel activeShield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
            if (activeShield != null) {
                Rs2Tome newTome = getRs2Tome(activeShield.getId());
                if (newTome != Rs2Tome.NONE) activeRunes.addAll(newTome.getRunes());
            }

            for (Map.Entry<Runes, Integer> entry : requiredRunes.entrySet()) {
                if (activeRunes.contains(entry.getKey())) continue;
                if (inventoryRunes.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
            }
            return true;
        }

        return false;
    }

}
