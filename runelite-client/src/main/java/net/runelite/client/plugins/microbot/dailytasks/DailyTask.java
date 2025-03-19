package net.runelite.client.plugins.microbot.dailytasks;

import lombok.Getter;
import net.runelite.api.MenuAction;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptItem;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static net.runelite.client.plugins.microbot.Microbot.doInvoke;
import static net.runelite.client.plugins.microbot.util.Global.*;


public enum DailyTask {

    HERB_BOXES(
            "Herb Boxes",
            new WorldPoint(2608, 3114, 0),
            () -> Microbot.getClient().getVarbitValue(Varbits.ACCOUNT_TYPE) == 0
                    && Microbot.getClient().getVarpValue(VarPlayer.NMZ_REWARD_POINTS) >= 9500
                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_HERB_BOXES_COLLECTED) < 15,
            () -> {
                Rs2GameObject.interact(26273, "Search");
                sleepUntil(() -> Rs2Widget.findWidget("Dom Onion") != null);
                doInvoke(new NewMenuEntry("Buy-50", "Herb box", 4, MenuAction.CC_OP, 20, 13500420, false), new Rectangle(1, 1));
                Rs2Inventory.waitForInventoryChanges(1000);
                Rs2Inventory.interact("Herb box", "Bank-all");
                sleepUntil(() -> !Rs2Inventory.hasItem("Herb box"), 20000);

            },
            DailyTasksConfig::collectHerbBoxes,
            List.of(ScriptItem.builder().name("Yanille teleport").build())
    ),

    BATTLESTAVES(
            "Battlestaves",
            new WorldPoint(3201, 3436, 0),
            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_VARROCK_EASY) == 1
                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_STAVES_COLLECTED) == 0,
            () -> {
                Rs2GameObject.interact(30357);
                sleepUntil(() -> Rs2Widget.findWidget("discounted battlestaves") != null);
                Rs2Widget.clickWidget("Click here to continue");
                sleepUntil(() -> Rs2Widget.findWidget("Yes") != null);
                Rs2Widget.clickWidget("Yes");
                Rs2Inventory.waitForInventoryChanges(1000);
            },
            DailyTasksConfig::collectStaves,
            List.of(
                    ScriptItem.builder().name("Coins").quantity(210000).build(),
                    ScriptItem.builder().name("Varrock teleport").build()
            )
    ),

    PURE_ESSENCE(
            "Pure Essence",
            new WorldPoint(2684, 3323, 0),
            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_ARDOUGNE_MEDIUM) == 1
                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_ESSENCE_COLLECTED) == 0,
            () -> {
                Rs2Npc.interact(8481, "Claim");
                Rs2Inventory.waitForInventoryChanges(1000);
            },
            DailyTasksConfig::collectEssence,
            List.of(
                    ScriptItem.builder().name("Ardougne teleport").build()
            )
    ),

//    FREE_RUNES(
//            "Free Runes",
//            new WorldPoint(3253, 3401, 0),
//            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_WILDERNESS_EASY) == 1
//                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_RUNES_COLLECTED) == 0,
//            () -> {
//            },
//            DailyTasksConfig::collectRunes,
//            List.of()
//    ),

    FLAX(
            "Flax",
            new WorldPoint(2738, 3444, 0),
            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_KANDARIN_EASY) == 1
                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_FLAX_STATE) == 0,
            () -> {
                Rs2Npc.interact(5522, "Exchange");
                sleepUntil(Rs2Dialogue::isInDialogue);
                Rs2Dialogue.clickOption("Agree");
                Rs2Inventory.waitForInventoryChanges(1000);
            },
            DailyTasksConfig::collectFlax,
            List.of(
                    ScriptItem.builder().name("Flax").noted(true).quantity(30).build(),
                    ScriptItem.builder().name("Camelot teleport").build()
            )
    ),

//    BONEMEAL(
//            "Bonemeal and Slime",
//            new WorldPoint(3442, 3489, 0),
//            () -> {
//                if (Microbot.getClient().getVarbitValue(Varbits.DIARY_MORYTANIA_MEDIUM) != 1) {
//                    return false;
//                }
//                int collected = Microbot.getClient().getVarbitValue(Varbits.DAILY_BONEMEAL_STATE);
//                int max = 13;
//                if (Microbot.getClient().getVarbitValue(Varbits.DIARY_MORYTANIA_HARD) == 1) {
//                    max += 13;
//                    if (Microbot.getClient().getVarbitValue(Varbits.DIARY_MORYTANIA_ELITE) == 1) {
//                        max += 13;
//                    }
//                }
//                return collected < max;
//            },
//            () -> {
//            },
//            DailyTasksConfig::collectBonemeal,
//            List.of()
//    ),
//
//    DYNAMITE(
//            "Dynamite",
//            new WorldPoint(1630, 3742, 0),
//            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_KOUREND_MEDIUM) == 1
//                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_DYNAMITE_COLLECTED) == 0,
//            () -> {
//            },
//            DailyTasksConfig::collectDynamite,
//            List.of()
//    ),

    MISCELLANIA(
            "Miscellania",
            new WorldPoint(2532, 3863, 0),
            () -> Microbot.getClient().getVarpValue(VarPlayer.THRONE_OF_MISCELLANIA) > 0,
            () -> {
                Rs2GameObject.interact(15079);
                sleepUntilOnClientThread(() -> Microbot.getClient().getVarbitValue(Varbits.KINGDOM_APPROVAL) == 127);
                boolean arrived = Rs2Walker.walkTo(new WorldPoint(2502, 3858, 1), 5);
                sleepUntil(() -> arrived, () -> Rs2Inventory.drop("Weeds"), 100, 20000);
                Rs2Npc.interact(5448, "Collect");
                sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Collect resources"));
                Rs2Dialogue.clickOption("Collect resources");
                sleepUntil(() -> Rs2Widget.findWidget("Resources Collected") != null);
                var closeBtn = Rs2Widget.findWidget(537, null);
                Rs2Widget.clickWidget(closeBtn);
                sleepUntil(Rs2Dialogue::isInDialogue);
                Rs2Dialogue.clickContinue();
                sleepUntil(Rs2Dialogue::hasContinue);
                Rs2Dialogue.clickContinue();
                sleepUntil(() -> !Rs2Dialogue.isInDialogue());
                var depositBtn = Rs2Widget.findWidget("Deposit");
                Rs2Widget.clickWidget(depositBtn);
                sleepUntil(() -> Rs2Widget.findWidget("Click here to continue") != null);
                Rs2Widget.clickWidget("Click here to continue");
                sleepUntil(() -> Rs2Widget.findWidget("Enter amount") != null);
                Rs2Keyboard.keyPress(50000);
                Rs2Keyboard.enter();
            },
            DailyTasksConfig::handleMiscellania,
            List.of(
                    ScriptItem.builder().name("Ring of wealth (").build(),
                    ScriptItem.builder().name("Rake").build(),
                    ScriptItem.builder().name("Pickaxe").build(),
                    ScriptItem.builder().name("Coins").quantity(50000).build()
            )

    );

    @Getter
    private final String name;
    @Getter
    private final WorldPoint location;
    private final BooleanSupplier isAvailable;
    private final Runnable executeTask;
    @Getter
    private final Function<DailyTasksConfig, Boolean> configEnabled;
    @Getter
    private final List<ScriptItem> requiredItems;

    DailyTask(String name, WorldPoint location, BooleanSupplier isAvailable,
              Runnable executeTask, Function<DailyTasksConfig, Boolean> configEnabled,
              List<ScriptItem> requiredItems) {
        this.name = name;
        this.location = location;
        this.isAvailable = isAvailable;
        this.executeTask = executeTask;
        this.configEnabled = configEnabled;
        this.requiredItems = requiredItems;
    }

    public boolean isAvailable() {

        return isAvailable.getAsBoolean();
    }


    public void execute() {
        executeTask.run();
    }

    public boolean isEnabled(DailyTasksConfig config) {
        return configEnabled.apply(config);
    }

    public boolean isInRange() {
        return Rs2Player.distanceTo(location) < 20;
    }
}