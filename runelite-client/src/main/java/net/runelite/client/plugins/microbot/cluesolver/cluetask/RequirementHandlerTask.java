package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class RequirementHandlerTask extends ClueTask {
    private final List<ItemRequirement> requirements;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private Iterator<ItemRequirement> requirementIterator;
    private CompletableFuture<Boolean> itemReceivedFuture;
    private ItemRequirement currentRequirement;

    @Inject
    private ClientThread clientThread;

    public RequirementHandlerTask(Client client, List<ItemRequirement> requirements, EventBus eventBus, ClueScrollPlugin plugin, ClueSolverPlugin clueSolverPlugin, ExecutorService backgroundExecutor) {
        super(client, plugin, clueSolverPlugin);
        this.eventBus = eventBus;
        this.requirements = requirements;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    protected boolean executeTask() {
        eventBus.register(this);
        log.info("Checking for missing items.");
        checkForMissingItemsAsync();
        return true;
    }

    /**
     * Retrieves a list of missing items based on the requirements asynchronously on the client thread.
     */
    private void checkForMissingItemsAsync() {
        Microbot.getClientThread().invokeLater(() -> {
            List<ItemRequirement> missingItems = getMissingItems(requirements);
            if (missingItems.isEmpty()) {
                completeTask(true);
            } else {
                fetchMissingItemsFromBank(missingItems);
            }
        });
    }

    private List<ItemRequirement> getMissingItems(List<ItemRequirement> requirements) {
        Item[] inventoryItems = client.getItemContainer(InventoryID.INVENTORY) != null ? client.getItemContainer(InventoryID.INVENTORY).getItems() : new Item[0];
        Item[] equippedItems = client.getItemContainer(InventoryID.EQUIPMENT) != null ? client.getItemContainer(InventoryID.EQUIPMENT).getItems() : new Item[0];

        List<Item> allItems = new CopyOnWriteArrayList<>();
        Collections.addAll(allItems, inventoryItems);
        Collections.addAll(allItems, equippedItems);

        return requirements.stream()
                .filter(req -> !req.fulfilledBy(allItems.toArray(new Item[0])))
                .collect(Collectors.toList());
    }

    private void fetchMissingItemsFromBank(List<ItemRequirement> missingItems) {
        this.requirementIterator = missingItems.iterator();
        log.info("Missing items: {}", missingItems.stream().map(e -> e.getCollectiveName(client)).collect(Collectors.joining(", ")));

        if (requirementIterator == null || !requirementIterator.hasNext()) {
            completeTask(true);
            return;
        }
        walkToBank();
    }

    private void walkToBank() {
        if (Rs2Bank.isOpen()) {
            fetchNextItem();
        } else if (client.getLocalPlayer().getWorldLocation().distanceTo(Rs2Bank.getNearestBank().getWorldPoint()) > 10) {
            Rs2Bank.walkToBankAndUseBank();
        } else {
            openBank();
        }
    }

    private void openBank() {
        if (!Rs2Bank.openBank()) {
            log.warn("Failed to open the bank.");
            completeTask(false);
        }
    }

    private void fetchNextItem() {
        if (requirementIterator == null || !requirementIterator.hasNext()) {
            Rs2Bank.closeBank();
            completeTask(true);
            return;
        }

        currentRequirement = requirementIterator.next();
        String itemName = currentRequirement.getCollectiveName(client);

        if (!Rs2Bank.hasItem(itemName)) {
            log.warn("Item {} not found in bank.", itemName);
            Rs2Bank.closeBank();
            completeTask(false);
            return;
        }

        Rs2Bank.withdrawAndEquip(itemName);
        itemReceivedFuture = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            try {
                boolean received = itemReceivedFuture.get(5, TimeUnit.SECONDS);

                if (received) {
                    Microbot.log("Item " + itemName + " received: " + received);
                    currentRequirement = null;

                } else {
                    log.warn("Item {} not received in time.", itemName);
                    Rs2Bank.closeBank();
                    completeTask(false);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.warn("Error waiting for item {}: {}", itemName, e.getMessage());
                Rs2Bank.closeBank();
                completeTask(false);
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (Rs2Bank.isOpen() && currentRequirement == null) {
            fetchNextItem();
        } else if (!Rs2Bank.isOpen() && client.getLocalPlayer().getWorldLocation().distanceTo(Rs2Bank.getNearestBank().getWorldPoint()) <= 5) {
            openBank();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId() && currentRequirement != null && itemReceivedFuture != null && !itemReceivedFuture.isDone()) {
            if (currentRequirement.fulfilledBy(event.getItemContainer().getItems())) {
                itemReceivedFuture.complete(true);
            }
        }
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Requirement handling task completed with status: {}", success ? "Success" : "Failure");
    }
}
