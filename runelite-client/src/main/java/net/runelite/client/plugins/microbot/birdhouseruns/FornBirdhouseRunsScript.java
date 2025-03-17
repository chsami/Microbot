package net.runelite.client.plugins.microbot.birdhouseruns;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptItem;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsInfo.*;

public class FornBirdhouseRunsScript extends Script {
    private static final WorldPoint birdhouseLocation1 = new WorldPoint(3763, 3755, 0);
    private static final WorldPoint birdhouseLocation2 = new WorldPoint(3768, 3761, 0);
    private static final WorldPoint birdhouseLocation3 = new WorldPoint(3677, 3882, 0);
    private static final WorldPoint birdhouseLocation4 = new WorldPoint(3679, 3815, 0);
    public static double version = 1.0;
    @Inject
    private Notifier notifier;


    public boolean run(FornBirdhouseRunsConfig config) {
        Microbot.enableAutoRunOn = true;
        super.requiredItems = List.of(
                ScriptItem.builder()
                        .id(config.LOG().getItemId())
                        .quantity(4)
                        .build(),
                ScriptItem.builder()
                        .id(ItemID.HAMMER)
                        .build(),
                ScriptItem.builder()
                        .id(ItemID.CHISEL)
                        .build(),
                ScriptItem.builder()
                        .id(config.SEED().getItemId())
                        .quantity(config.SEED().getAmountPerHouse() * 4)
                        .build(),
                ScriptItem.builder()
                        .name("Digsite pendant")  // Using name since there are multiple IDs (1-5)
                        .build()
        );

        if (config.TELEPORT()) {
            super.requiredItems = new ArrayList<>(super.requiredItems);
            super.requiredItems.addAll(List.of(
                    ScriptItem.builder()
                            .id(ItemID.LAW_RUNE)
                            .build(),
                    ScriptItem.builder()
                            .id(ItemID.FIRE_RUNE)
                            .build(),
                    ScriptItem.builder()
                            .id(ItemID.AIR_RUNE)
                            .quantity(3)
                            .build()
            ));
        }

        if (config.GRACEFUL()) {
            super.requiredItems.addAll(List.of(
                    ScriptItem.builder()
                            .name("Graceful hood")
                            .equipped(true)
                            .build(),
                    ScriptItem.builder()
                            .name("Graceful cape")
                            .equipped(true)
                            .build(),
                    ScriptItem.builder()
                            .name("Graceful top")
                            .equipped(true)
                            .build(),
                    ScriptItem.builder()
                            .name("Graceful legs")
                            .equipped(true)
                            .build(),
                    ScriptItem.builder()
                            .name("Graceful gloves")
                            .equipped(true)
                            .build(),
                    ScriptItem.builder()
                            .name("Graceful boots")
                            .equipped(true)
                            .build()
            ));
        }

        botStatus = states.TELEPORTING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                switch (botStatus) {
                    case TELEPORTING:
                        boolean arrivedThere = Rs2Walker.walkTo(new WorldPoint(3764, 3869, 1));
                        sleepUntil(() -> arrivedThere);
                        botStatus = states.VERDANT_TELEPORT;
                        break;
                    case VERDANT_TELEPORT:
                        if (interactWithObject(30920)) {
                            if (Rs2Widget.clickWidget(39845895)) {
                                sleep(3000, 4000);
                                botStatus = states.DISMANTLE_HOUSE_1;
                            }
                        }
                        break;
                    case DISMANTLE_HOUSE_1:
                        dismantleBirdhouse(30568, states.BUILD_HOUSE_1);
                        break;
                    case BUILD_HOUSE_1:
                        buildBirdhouse(birdhouseLocation1, states.SEED_HOUSE_1);
                        break;
                    case SEED_HOUSE_1:
                        seedHouse(birdhouseLocation1, states.DISMANTLE_HOUSE_2);
                    case DISMANTLE_HOUSE_2:
                        dismantleBirdhouse(30567, states.BUILD_HOUSE_2);
                        break;
                    case BUILD_HOUSE_2:
                        buildBirdhouse(birdhouseLocation2, states.SEED_HOUSE_2);
                        break;
                    case SEED_HOUSE_2:
                        seedHouse(birdhouseLocation2, states.MUSHROOM_TELEPORT);
                        break;
                    case MUSHROOM_TELEPORT:
                        if (interactWithObject(30924)) {
                            if (Rs2Widget.clickWidget(39845903)) {
                                sleep(2000, 3000);
                                botStatus = states.DISMANTLE_HOUSE_3;
                            }
                        }
                        break;
                    case DISMANTLE_HOUSE_3:
                        dismantleBirdhouse(30565, states.BUILD_HOUSE_3);
                        break;
                    case BUILD_HOUSE_3:
                        buildBirdhouse(birdhouseLocation3, states.SEED_HOUSE_3);
                        break;
                    case SEED_HOUSE_3:
                        seedHouse(birdhouseLocation3, states.DISMANTLE_HOUSE_4);
                        break;
                    case DISMANTLE_HOUSE_4:
                        Rs2Walker.walkTo(new WorldPoint(3680, 3813, 0));
                        dismantleBirdhouse(30566, states.BUILD_HOUSE_4);
                        break;
                    case BUILD_HOUSE_4:
                        buildBirdhouse(birdhouseLocation4, states.SEED_HOUSE_4);
                        break;
                    case SEED_HOUSE_4:
                        seedHouse(birdhouseLocation4, states.FINISHING);
                        break;
                    case FINISHING:
                        if (config.TELEPORT()) {
                            Rs2Magic.cast(MagicAction.VARROCK_TELEPORT);
                            sleep(2500);
                        }

                        emptyNests();

                        botStatus = states.FINISHED;
                        notifier.notify(Notification.ON, "Birdhouse run is finished.");
                        super.shutdown();
                        break;
                    case FINISHED:

                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void emptyNests() {
        do {
            Rs2Inventory.interact(ItemID.BIRD_NEST, "search");
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        while (Rs2Inventory.contains(ItemID.BIRD_NEST));

        do {
            Rs2Inventory.interact(ItemID.BIRD_NEST_5071, "search");
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        while (Rs2Inventory.contains(ItemID.BIRD_NEST_5071));

        do {
            Rs2Inventory.interact(ItemID.BIRD_NEST_5072, "search");
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        while (Rs2Inventory.contains(ItemID.BIRD_NEST_5072));

        do {
            Rs2Inventory.interact(ItemID.BIRD_NEST_5073, "search");
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        while (Rs2Inventory.contains(ItemID.BIRD_NEST_5073));

        do {
            Rs2Inventory.interact(ItemID.BIRD_NEST_5074, "search");
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        while (Rs2Inventory.contains(ItemID.BIRD_NEST_5074));

        do {
            Rs2Inventory.interact(ItemID.BIRD_NEST_22798, "search");
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        while (Rs2Inventory.contains(ItemID.BIRD_NEST_22798));

        do {
            Rs2Inventory.interact(ItemID.BIRD_NEST_22800, "search");
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        while (Rs2Inventory.contains(ItemID.BIRD_NEST_22800));
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private boolean interactWithObject(int objectId) {
        Rs2GameObject.interact(objectId);
        sleepUntil(Rs2Player::isInteracting);
        sleepUntil(() -> !Rs2Player.isInteracting());
        return true;
    }

    private void seedHouse(WorldPoint worldPoint, states status) {
        Rs2Inventory.use(selectedSeed);
        sleep(100);
        Rs2GameObject.interact(worldPoint);
        sleepUntil(Rs2Dialogue::isInDialogue, 1000);
        botStatus = status;
    }

    private void buildBirdhouse(WorldPoint worldPoint, states status) {
        if (!Rs2Inventory.hasItem(birdhouseType) && Rs2Inventory.hasItem(ItemID.CLOCKWORK)) {
            Rs2Inventory.use(ItemID.HAMMER);
            Rs2Inventory.use(selectedLogs);
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        Rs2GameObject.interact(worldPoint, "build");
        sleepUntil(Rs2Player::isAnimating);
        botStatus = status;
    }

    private void dismantleBirdhouse(int itemId, states status) {
        Rs2GameObject.interact(itemId, "empty");
        Rs2Player.waitForXpDrop(Skill.HUNTER);
        botStatus = status;
    }
}
