package net.runelite.client.plugins.microbot.cluesolver;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.*;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cluesolver.cluetask.*;
import net.runelite.client.plugins.microbot.cluesolver.util.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
public class ClueSolverScript extends Script {
    private Future<?> currentTask;
    private Future<?> itemRequirementsTask;
    private ClueScroll currentClue;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);

    @Inject
    Client client;
    @Inject
    EventBus eventBus;
    @Inject
    ClueScrollPlugin clueScrollPlugin;
    @Inject
    ClueSolverOverlay overlay;
    @Inject
    ClueSolverPlugin clueSolverPlugin;

    // Factory map to link ClueScroll subclasses to ClueTask suppliers
    private final Map<Class<? extends ClueScroll>, Supplier<ClueTask>> taskFactoryMap = new HashMap<>();

    public ClueSolverScript() {
        initializeTaskFactoryMap();
    }

    private void initializeTaskFactoryMap() {
        taskFactoryMap.put(CoordinateClue.class, () -> new CoordinateClueTask(client, (CoordinateClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(EmoteClue.class, () -> new EmoteClueTask(client, (EmoteClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(CrypticClue.class, () -> new CrypticClueTask(client, (CrypticClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(MapClue.class, () -> new MapClueTask(client, (MapClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(FairyRingClue.class, () -> new FairyRingClueTask(client, (FairyRingClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(FaloTheBardClue.class, () -> new FaloTheBardClueTask(client, (FaloTheBardClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(MusicClue.class, () -> new MusicClueTask(client, (MusicClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(SkillChallengeClue.class, () -> new SkillChallengeClueTask(client, (SkillChallengeClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(AnagramClue.class, () -> new AnagramClueTask(client, (AnagramClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(ThreeStepCrypticClue.class, () -> new ThreeStepCrypticClueTask(client, (ThreeStepCrypticClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(HotColdClue.class, () -> new HotColdClueTask(client, (HotColdClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
        taskFactoryMap.put(CipherClue.class, () -> new CipherClueTask(client, (CipherClue) currentClue, clueScrollPlugin, clueSolverPlugin, eventBus, executorService));
    }

    public boolean start() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                processClues();
            } catch (Exception e) {
                log.error("Error in main scheduled task", e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        return true;
    }



    private void processClues() {
        if (executorService.isShutdown()) {
            log.warn("Executor service is shut down; skipping task submission.");
            return;
        }

        ClueScroll clue = clueScrollPlugin.getClue();
        if (clue != null && !clue.equals(currentClue)) {
            currentClue = clue;
            overlay.updateTaskStatus("New Clue Detected: " + clue.getClass().getSimpleName());
            log.info("New Clue Detected: {}", clue.getClass().getSimpleName());

            List<ItemRequirement> requiredItems = determineRequiredItems(clue);
            if (!requiredItems.isEmpty()) {
                if (itemRequirementsTask != null && !itemRequirementsTask.isDone()) {
                    log.warn("Previous item requirements task is still running, skipping this clue.");
                    return;
                }
                RequirementHandlerTask requirementHandlerTask = new RequirementHandlerTask(client, requiredItems, eventBus, clueScrollPlugin, clueSolverPlugin, executorService);

                CompletableFuture<Boolean> requirementFuture = new CompletableFuture<>();
                requirementHandlerTask.setFuture(requirementFuture);

                requirementFuture.thenRun(this::onRequirementsMet)
                        .exceptionally(ex -> {
                            log.error("Failed to fulfill requirements", ex);
                            overlay.updateTaskStatus("Requirement fulfillment failed");
                            return null;
                        });

                if (!executorService.isShutdown()) {
                   itemRequirementsTask = executorService.submit(requirementHandlerTask);
                }
            } else {
                startClueTask(createClueTaskForClue(clue));
            }
        } else {
            log.debug("No new clue detected or clue already being processed.");
        }
    }


    private List<ItemRequirement> determineRequiredItems(ClueScroll clue) {
        List<ItemRequirement> requiredItems = new ArrayList<>();

        try {
            Field itemRequirementsField = ReflectionHelper.getFieldFromClassHierarchy(clue.getClass(), "itemRequirements");
            if (itemRequirementsField != null) {
                itemRequirementsField.setAccessible(true);
                ItemRequirement[] clueItemRequirements = (ItemRequirement[]) itemRequirementsField.get(clue);

                if (clueItemRequirements != null) {
                    log.info("Added item requirements via reflection. Number of items: {}", clueItemRequirements.length);
                    for (ItemRequirement req : clueItemRequirements) {
                        if (req != null) {
                            requiredItems.add(req);
                            log.info("Item requirement detected: {}", req);
                        }
                    }
                } else {
                    log.warn("The itemRequirements field is null.");
                }
            } else {
                log.info("The clue does not have an 'itemRequirements' field.");
            }
        } catch (Exception e) {
            log.error("Error determining required items via reflection", e);
        }

        return requiredItems;
    }

    private ClueTask createClueTaskForClue(ClueScroll clue) {
        Supplier<ClueTask> taskSupplier = taskFactoryMap.get(clue.getClass());
        return (taskSupplier != null) ? taskSupplier.get() : null;
    }

    private void onRequirementsMet() {
        if (currentClue != null) {
            overlay.updateTaskStatus("Starting clue task...");
            startClueTask(createClueTaskForClue(currentClue));
        }
    }

    private void startClueTask(ClueTask task) {
        if (task == null) {
            log.warn("No task found for clue type: {}", currentClue.getClass().getSimpleName());
            return;
        }

        currentTask = executorService.submit(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            task.setFuture(future);
            task.run();

            try {
                boolean result = future.get();
                overlay.updateTaskStatus("Clue Task completed: " + (result ? "Success" : "Failed"));
                return result;
            } catch (Exception e) {
                log.error("Error executing clue task", e);
                return false;
            } finally {
                resetCurrentClue();
            }
        });
    }

    private void resetCurrentClue() {
        overlay.updateTaskStatus("Waiting for new clue...");
        currentClue = null;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
        if (itemRequirementsTask != null && !itemRequirementsTask.isDone()) {
            itemRequirementsTask.cancel(true);
        }
        eventBus.unregister(this);

        overlay.updateTaskStatus("Clue Solver Script stopped");
        log.info("Clue Solver Script stopped.");
    }

}
