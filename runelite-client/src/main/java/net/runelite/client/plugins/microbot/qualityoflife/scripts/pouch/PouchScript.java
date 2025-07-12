package net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch;

import com.google.common.collect.ImmutableMap;
import net.runelite.api.ChatMessageType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.min;

public class PouchScript extends Script {
    private final int INVENTORY_SIZE = 28;

    private final Pattern POUCH_CHECK_MESSAGE = Pattern.compile("^There (?:is|are) ([a-z-]+)(?: pure| daeyalt)? essences? in this pouch\\.$");
    private final ImmutableMap<String, Integer> TEXT_TO_NUMBER = ImmutableMap.<String, Integer>builder()
            .put("no", 0)
            .put("one", 1)
            .put("two", 2)
            .put("three", 3)
            .put("four", 4)
            .put("five", 5)
            .put("six", 6)
            .put("seven", 7)
            .put("eight", 8)
            .put("nine", 9)
            .put("ten", 10)
            .put("eleven", 11)
            .put("twelve", 12)
            .put("thirteen", 13)
            .put("fourteen", 14)
            .put("fifteen", 15)
            .put("sixteen", 16)
            .put("seventeen", 17)
            .put("eighteen", 18)
            .put("nineteen", 19)
            .put("twenty", 20)
            .put("twenty-one", 21)
            .put("twenty-two", 22)
            .put("twenty-three", 23)
            .put("twenty-four", 24)
            .put("twenty-five", 25)
            .put("twenty-six", 26)
            .put("twenty-seven", 27)
            .put("twenty-eight", 28)
            .put("twenty-nine", 29)
            .put("thirty", 30)
            .put("thirty-one", 31)
            .put("thirty-two", 32)
            .put("thirty-three", 33)
            .put("thirty-four", 34)
            .put("thirty-five", 35)
            .put("thirty-six", 36)
            .put("thirty-seven", 37)
            .put("thirty-eight", 38)
            .put("thirty-nine", 39)
            .put("forty", 40)
            .build();

    private final Deque<ClickOperation> clickedItems = new ArrayDeque<>();
    private final Deque<ClickOperation> checkedPouches = new ArrayDeque<>();
    private int lastEssence;
    private int lastSpace;
    private boolean isRunning = false;

    public void startUp() {
        // Reset pouch state
        for (Pouch pouch : Pouch.values()) {
            pouch.setHolding(0);
            pouch.setUnknown(true);
            pouch.degrade(false);
        }

        lastEssence = lastSpace = -1;
        isRunning = true;
    }


    @Override
    public void shutdown() {
        isRunning = false;
        super.shutdown();
    }

    public void onChatMessage(ChatMessage event) {
        if (!isRunning || event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        if (!checkedPouches.isEmpty()) {
            Matcher matcher = POUCH_CHECK_MESSAGE.matcher(event.getMessage());
            if (matcher.matches()) {
                final int num = TEXT_TO_NUMBER.get(matcher.group(1));
                // Keep getting operations until we get a valid one
                do {
                    final ClickOperation op = checkedPouches.pop();
                    if (op.tick >= Microbot.getClient().getTickCount()) {
                        Pouch pouch = op.pouch;
                        pouch.setHolding(num);
                        pouch.setUnknown(false);
                        break;
                    }
                }
                while (!checkedPouches.isEmpty());
            }
        } else if (!clickedItems.isEmpty()) {
            if (event.getMessage().contains("You cannot add any more")) {
                do {
                    final ClickOperation op = clickedItems.pop();
                    if (op.tick >= Microbot.getClient().getTickCount()) {
                        Pouch pouch = op.pouch;
                        pouch.setHolding(pouch.getHoldAmount());
                        System.out.println(pouch.getHolding());
                        pouch.setUnknown(false);
                        break;
                    }
                }
                while (!clickedItems.isEmpty());
            } else if (event.getMessage().contains("There is no")) {
                do {
                    final ClickOperation op = clickedItems.pop();
                    if (op.tick >= Microbot.getClient().getTickCount()) {
                        Pouch pouch = op.pouch;
                        pouch.setHolding(0);
                        pouch.setUnknown(false);
                        break;
                    }
                }
                while (!clickedItems.isEmpty());
            }
        }
    }

    public void onItemContainerChanged(ItemContainerChanged event) {
        if (!isRunning)
            return;

        if (InventoryID.INV != event.getContainerId()) {
            return;
        }

        final Item[] items = event.getItemContainer().getItems();

        int newEss = 0;
        int newSpace = 0;

        // Count ess/space, and change pouch states
        for (Item item : items) {
            switch (item.getId()) {
                case ItemID.BLANKRUNE_HIGH:
                case ItemID.BLANKRUNE_DAEYALT:
                case ItemID.GOTR_GUARDIAN_ESSENCE:
                    newEss += 1;
                    break;
                case -1:
                    newSpace += 1;
                    break;
                case ItemID.RCU_POUCH_MEDIUM:
                case ItemID.RCU_POUCH_LARGE:
                case ItemID.RCU_POUCH_GIANT:
                case ItemID.RCU_POUCH_COLOSSAL:
                    Pouch pouch = Pouch.forItem(item.getId());
                    pouch.degrade(false);
                    break;
                case ItemID.RCU_POUCH_MEDIUM_DEGRADE:
                case ItemID.RCU_POUCH_LARGE_DEGRADE:
                case ItemID.RCU_POUCH_GIANT_DEGRADE:
                case ItemID.RCU_POUCH_COLOSSAL_DEGRADE:
                    pouch = Pouch.forItem(item.getId());
                    pouch.degrade(true);
                    break;
            }
        }
        if (items.length < INVENTORY_SIZE) {
            // Pad newSpace for unallocated inventory slots
            newSpace += INVENTORY_SIZE - items.length;
        }

        if (clickedItems.isEmpty()) {
            lastSpace = newSpace;
            lastEssence = newEss;
            return;
        }

        if (lastEssence == -1 || lastSpace == -1) {
            lastSpace = newSpace;
            lastEssence = newEss;
            clickedItems.clear();
            return;
        }

        final int tick = Microbot.getClient().getTickCount();

        int essence = lastEssence;
        int space = lastSpace;


        while (essence != newEss) {
            ClickOperation op = clickedItems.poll();
            if (op == null) {
                break;
            }

            if (tick > op.tick) {
                continue;
            }

            Pouch pouch = op.pouch;

            final boolean fill = op.delta > 0;
            System.out.println(pouch.getRemaining() + " " + pouch.getHolding());
            // How much ess can either be deposited or withdrawn
            final int required = fill ? pouch.getRemaining() : pouch.getHolding();
            // Bound to how much ess or free space we actually have, and optionally negate
            final int essenceGot = op.delta * min(required, fill ? essence : space);

            // if we have enough essence or space to fill or empty the entire pouch, it no
            // longer becomes unknown
            if (pouch.isUnknown() && (fill ? essence : space) >= pouch.getHoldAmount()) {
                pouch.setUnknown(false);
            }

            essence -= essenceGot;
            space += essenceGot;

            pouch.addHolding(essenceGot);
        }


        lastSpace = newSpace;
        lastEssence = newEss;
    }

    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!isRunning) {
            return;
        }

        int itemId = event.getItemId();

        if (itemId == -1) {
            return;
        }

        final Pouch pouch = Pouch.forItem(itemId);
        if (pouch == null) {
            return;
        }
        final int tick = Microbot.getClient().getTickCount() + 3;
        System.out.println(event.getMenuOption());
        switch (event.getMenuOption().toLowerCase()) {
            case "fill":
                clickedItems.add(new ClickOperation(pouch, tick, 1));
                break;
            case "empty":
                clickedItems.add(new ClickOperation(pouch, tick, -1));
                break;
            case "check":
                checkedPouches.add(new ClickOperation(pouch, tick));
                break;
            case "take":
                // Dropping pouches clears them, so clear when picked up
                pouch.setHolding(0);
                break;
        }
    }
}