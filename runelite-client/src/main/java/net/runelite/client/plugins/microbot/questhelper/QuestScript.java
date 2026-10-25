package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.util.Text;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatDetector;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler;
import net.runelite.client.plugins.microbot.questhelper.logic.PiratesTreasure;
import net.runelite.client.plugins.microbot.questhelper.logic.QuestRegistry;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.managers.ItemAndLastUpdated;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestContainerManager;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.steps.*;
import net.runelite.client.plugins.microbot.questhelper.steps.widget.WidgetHighlight;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.questhelper.helpers.quests.priestinperil.MonumentKeyStep;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemQueryable;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.event.Level;
import net.runelite.api.coords.WorldArea;

public class QuestScript extends Script {
    public static double version = 0.6;

    private static final long MISSING_REQUIREMENT_NOTIFY_INTERVAL_MS = 60_000L;
    private static final Map<Integer, Long> lastMissingRequirementNotice = new HashMap<>();

    public static List<ItemRequirement> itemRequirements = new ArrayList<>();

    public static List<ItemRequirement> itemsMissing = new ArrayList<>();
    public static List<ItemRequirement> grandExchangeItems = new ArrayList<>();

	private static final int INVENTORY_SLOTS = 28;
	/** Unstackable quest-level requirements above this are only gathered by the step that needs them. */
	private static final int QUEST_LEVEL_PREGATHER_MAX = 20;
	private final Map<Integer, Boolean> stackableCache = new HashMap<>();
	private List<ItemRequirement> missingBatch = new ArrayList<>();
	private List<Requirement> currentRequirements = new ArrayList<>();
	private final Set<Long> monumentsTried = new HashSet<>();

	private static final int PREFLIGHT_MAX_BANK_ATTEMPTS = 3;
	private static final int PREFLIGHT_MAX_BUY_ROUNDS = 10;
	private int preflightNonce = Integer.MIN_VALUE;
	private String preflightQuestKey;
	private boolean preflightFinished;
	private boolean preflightSummaryLogged;
	private int preflightBankAttempts;
	private int preflightBuyRounds;

    private static final AtomicBoolean valeTotemsPromptInFlight = new AtomicBoolean(false);
    private static volatile QuestHelperConfig.ValeTotemsWoodType valeTotemsSessionWoodType;

    private static final AtomicBoolean obtainItemsPromptInFlight = new AtomicBoolean(false);
    private static volatile Boolean obtainItemsSessionChoice;

    private final QuestCombatHandler combat = new QuestCombatHandler(
            () -> this.config, () -> mainScheduledFuture, this::stopQuesterWithReason);
    private ScheduledFuture<?> combatSupportFuture;
    private long nextBreakAtMs;
    private long breakEndsAtMs;

    boolean unreachableTarget = false;
    int unreachableTargetCheckDist = 1;

    private QuestHelperConfig config;
    private QuestHelperPlugin mQuestPlugin;
    private static Set<Integer> npcsHandled = new HashSet<>();
    private static Set<Long> objectsHandeled = new HashSet<>();

    private int heldTrackingQuestId = -1;
    private final Set<Integer> everHeldItemRequirementIds = new HashSet<>();

    QuestStep dialogueStartedStep = null;

    private final MultiNpcTargetTracker multiNpcTarget = new MultiNpcTargetTracker();

    /**
     * Epoch millis at which the post-dialogue cooldown expires. While
     * {@code System.currentTimeMillis() < dialogueCooldownEndsAt}, the main tick
     * returns early to avoid re-clicking the quest NPC and interrupting scripted
     * animations or cutscenes that play between dialogue exchanges. Set on the
     * transition from in-dialogue to not-in-dialogue; zero means no cooldown.
     */
    private long dialogueCooldownEndsAt = 0;



    public boolean run(QuestHelperConfig config, QuestHelperPlugin mQuestPlugin) {
        this.config = config;
        this.mQuestPlugin = mQuestPlugin;


        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!config.startStopQuestHelper()) {
                    combat.reset();
                    return;
                }
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (getQuestHelperPlugin().getSelectedQuest() == null) return;

                if (handleShortBreak()) return;

                if (Rs2Player.isAnimating())
                    Rs2Player.waitForAnimation();

                QuestStep questStep = getQuestHelperPlugin().getSelectedQuest().getCurrentStep().getActiveStep();

                if (Rs2Dialogue.isInDialogue() && dialogueStartedStep == null)
                    dialogueStartedStep = questStep;

                if (questStep != null && Rs2Widget.isWidgetVisible(ComponentID.DIALOG_OPTION_OPTIONS)) {
                    var dialogOptions = Rs2Widget.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
                    var dialogChoices = dialogOptions.getDynamicChildren();

                    if (dialogChoices != null) {
                        for (var choice : questStep.getChoices().getChoices()) {
                            if (choice.getExpectedPreviousLine() != null)
                                continue; // TODO

                            if (choice.getExcludedStrings() != null && choice.getExcludedStrings().stream().anyMatch(Rs2Widget::hasWidget))
                                continue;

                            if (selectDialogChoice(dialogChoices, choice.getChoice())) {
                                return;
                            }
                        }
                    }
                }

                if (questStep != null && !questStep.getWidgetsToHighlight().isEmpty()) {
                    var widgetHighlight = questStep.getWidgetsToHighlight().stream()
                            .filter(x -> x instanceof WidgetHighlight)
                            .map(x -> (WidgetHighlight) x)
                            .filter(x -> Rs2Widget.isWidgetVisible(x.getInterfaceID()))
                            .findFirst().orElse(null);

                    if (widgetHighlight != null) {
                        var widget = Rs2Widget.getWidget(widgetHighlight.getInterfaceID());
                        if (widget != null) {
                            if (widgetHighlight.getChildChildId() != -1) {
                                var childWidget = widget.getChildren()[widgetHighlight.getChildChildId()];
                                if (childWidget != null) {
                                    Rs2Widget.clickWidget(childWidget.getId());
                                    return;
                                }
                            } else {
                                if (widgetHighlight.getNameToCheckFor() != null && !widgetHighlight.getNameToCheckFor().isEmpty()) {
                                    Rs2Widget.clickWidget(widgetHighlight.getNameToCheckFor());
                                } else {
                                    Rs2Widget.clickWidget(widget.getId());
                                    if (Rs2Shop.isOpen() && getQuestHelperPlugin().getSelectedQuest().getQuest().getId() == Quest.PIRATES_TREASURE.getId()) {
                                        Rs2Shop.buyItemOptimally("karamjan rum", 1);
                                    }
                                }
                                return;
                            }
                        }
                    }
                }

                /**
                 * Execute custom logic for the quest
                 */
                var questLogic = QuestRegistry.getQuest(getQuestHelperPlugin().getSelectedQuest().getQuest().getId());
                if (questLogic instanceof PiratesTreasure) {
                    ((PiratesTreasure) questLogic).setMQuestPlugin(mQuestPlugin);
                }
                if (questLogic != null) {
                    if (!questLogic.executeCustomLogic()) {
                        return;
                    }
                }

                QuestHelper selectedQuest = getQuestHelperPlugin().getSelectedQuest();
                if (selectedQuest != null && !Microbot.getClientThread().runOnClientThreadOptional(selectedQuest::isCompleted).orElse(true)) {
                    if (Rs2Widget.isWidgetVisible(ComponentID.DIALOG_OPTION_OPTIONS) && selectedQuest.getQuest().getId() != Quest.COOKS_ASSISTANT.getId() && !Rs2Bank.isOpen()) {
                        boolean hasOption = Rs2Dialogue.handleQuestOptionDialogueSelection();
                        //if there is no quest option in the dialogue, just click player location to remove
                        // the dialogue to avoid getting stuck in an infinite loop of dialogues
                        if (!hasOption) {
                            if (Rs2Dialogue.acceptQuestStartDialogue()) {
                                return;
                            }
                            if (selectedQuest != null &&
                                    selectedQuest.getQuest().getId() == Quest.IMP_CATCHER.getId()
                                    && Microbot.getClient().getTopLevelWorldView().getPlane() == 1) {
                                Rs2Dialogue.keyPressForDialogueOption(1); // presses option 1
                                sleep(1200,1800);
                            }
                            Rs2Walker.walkFastCanvas(Rs2Player.getWorldLocation());
                        }
                        return;
                    }

                    if (selectedQuest != null &&
                            selectedQuest.getQuest().getId() == Quest.COOKS_ASSISTANT.getId() &&
                            Rs2Dialogue.isInDialogue()) {
                        dialogueStartedStep = questStep;  // Force this to be true for Cook's Assistant
                    }

                    if (selectedQuest != null &&
                            selectedQuest.getQuest().getId() == Quest.PIRATES_TREASURE.getId() &&
                            Rs2Dialogue.isInDialogue()) {
                        dialogueStartedStep = questStep;
                    }

                    // Never space through a menu: space only advances "Click here to continue" and would
                    // leave an options widget (e.g. the quest-start choice) unanswered forever.
                    if (Rs2Dialogue.isInDialogue() && dialogueStartedStep == questStep
                            && !Rs2Widget.isWidgetVisible(ComponentID.DIALOG_OPTION_OPTIONS)) {
                        Rs2Walker.clearWalkingRoute("quest-helper:dialogue-space-step");
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        return;
                    } else {
                        if (dialogueStartedStep != null) {
                            dialogueCooldownEndsAt = System.currentTimeMillis() + Rs2Random.between(4000, 7000);
                            multiNpcTarget.release();
                        }
                        dialogueStartedStep = null;
                    }

                    if (System.currentTimeMillis() < dialogueCooldownEndsAt) {
                        return;
                    }

                    boolean isInCutscene = Microbot.getVarbitValue(4606) > 0;
                    if (isInCutscene) {
                        if (Rs2PathApi.getMarker() != null)
                            Rs2PathApi.exit();
                        return;
                    }

					if (combat.beginStep(selectedQuest)) {
						return;
					}

					if (runPreflight(selectedQuest)) {
						return;
					}

					if (questStep instanceof DetailedQuestStep && handleRequirements((DetailedQuestStep) questStep)) {
						sleep(500, 1000);
						return;
					}

					if (questStep instanceof DetailedQuestStep && shouldObtainMissingItems() && handleMissingItemRequirements((DetailedQuestStep) questStep)) {
						return;
					}

					if (combat.recoverHealth(questStep, slotsReservedForStep(questStep))) {
						return;
					}

					if (combat.prepareQuestSupplies(slotsReservedForStep(questStep))) {
						return;
					}

					if ((QuestCombatDetector.isKillStep(questStep) || QuestCombatDetector.hasFights(selectedQuest))
							&& combat.prepareForKill(questStep, slotsReservedForStep(questStep))) {
						return;
					}

					/**
					 * This portion is needed when using item on another item in your inventory.
					 * If we do not prioritize this, the script will think we are missing items
					 */
					if (questStep instanceof DetailedQuestStep && !(questStep instanceof NpcStep || questStep instanceof ObjectStep || questStep instanceof DigStep)) {
                        boolean result = applyDetailedQuestStep((DetailedQuestStep) selectedQuest.getCurrentStep().getActiveStep());
                        if (result) {
                            sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue(), 500);
                            sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isMoving() && !Rs2Player.isAnimating());
                            return;
                        }
                    }

                    if (selectedQuest.getCurrentStep() instanceof ConditionalStep) {
                        QuestStep conditionalStep = selectedQuest.getCurrentStep().getActiveStep();
                        applyStep(conditionalStep);
                    } else if (selectedQuest.getCurrentStep() instanceof NpcStep) {
                        applyNpcStep((NpcStep) selectedQuest.getCurrentStep());
                    } else if (selectedQuest.getCurrentStep() instanceof ObjectStep) {
                        applyObjectStep((ObjectStep) selectedQuest.getCurrentStep());
                    } else if (selectedQuest.getCurrentStep() instanceof DigStep) {
                        applyDigStep((DigStep) selectedQuest.getCurrentStep());
                    } else if (selectedQuest.getCurrentStep() instanceof PuzzleStep) {
                        applyPuzzleStep((PuzzleStep) selectedQuest.getCurrentStep());
                    }

                    sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue(), 500);
                    sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isMoving() && !Rs2Player.isAnimating());
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }, 0, Rs2Random.between(400, 1000), TimeUnit.MILLISECONDS);

        combatSupportFuture = scheduledExecutorService.scheduleWithFixedDelay(this::combatSupportTick,
                600, 600, TimeUnit.MILLISECONDS);
        return true;
    }

	/**
	 * One-time check per quest start: every tradable item the rest of the quest needs is on the player or in the bank,
	 * and the shortfall is bought from the Grand Exchange into the bank. Runs after the setup dialog is answered.
	 *
	 * @return true while the preflight used this tick and the quest must not act yet
	 */
	private boolean runPreflight(QuestHelper quest) {
		int nonce = QuestCombatHandler.startNonce();
		String key = quest.getQuest().name();
		if (nonce != preflightNonce || !key.equals(preflightQuestKey)) {
			boolean freshStart = nonce != preflightNonce;
			preflightNonce = nonce;
			preflightQuestKey = key;
			preflightFinished = false;
			preflightSummaryLogged = false;
			preflightBankAttempts = 0;
			preflightBuyRounds = 0;
			QuestPreflight.setStatus(QuestPreflight.PENDING);
			if (freshStart && QuestPreflight.consumeSkip()) {
				finishPreflight(QuestPreflight.DONE);
				return false;
			}
		}
		if (preflightFinished) {
			return false;
		}

		QuestState state = Microbot.getClientThread()
				.runOnClientThreadOptional(() -> quest.getQuest().getState(Microbot.getClient())).orElse(null);
		if (!config.preflightQuestItems() || Rs2Player.isIronman()
				|| (state != QuestState.NOT_STARTED && state != QuestState.IN_PROGRESS)) {
			finishPreflight(QuestPreflight.DONE);
			return false;
		}
		if (!shouldObtainMissingItems()) {
			if (config.obtainMissingItems() == QuestHelperConfig.ObtainMissingItemsOption.ASK
					&& obtainItemsSessionChoice == null) {
				return true; // the consent prompt is still open
			}
			finishPreflight(QuestPreflight.DONE);
			return false;
		}

		QuestPreflight.setStatus(QuestPreflight.CHECKING);
		Microbot.status = "Quest helper: checking quest items";

		ItemAndLastUpdated bank = QuestContainerManager.getBankData();
		if (bank.getLastUpdated() < 0 && bank.getItems().length == 0) {
			if (preflightBankAttempts >= PREFLIGHT_MAX_BANK_ATTEMPTS) {
				Microbot.log("Quest helper preflight skipped: could not open the bank to count quest items", Level.WARN);
				finishPreflight(QuestPreflight.FAILED_PREFIX + "could not open the bank");
				return false;
			}
			preflightBankAttempts++;
			Microbot.status = "Quest helper: opening the bank to count quest items";
			Rs2Bank.walkToBankAndUseBank();
			if (sleepUntil(Rs2Bank::isOpen, 15_000)) {
				sleepUntil(() -> QuestContainerManager.getBankData().getLastUpdated() >= 0, 3_000);
				Rs2Bank.closeBank();
				sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
			}
			return true;
		}

		List<QuestPreflight.Need> needs = collectPreflightNeeds(quest, state);
		if (needs == null) {
			return true; // waiting on the Vale Totems wood type
		}

		Map<Integer, Integer> onPlayer = new HashMap<>();
		addContainer(onPlayer, QuestContainerManager.getEquippedData());
		addContainer(onPlayer, QuestContainerManager.getInventoryData());
		Map<Integer, Integer> owned = new HashMap<>(onPlayer);
		addContainer(owned, bank);
		for (QuestPreflight.Need need : needs) {
			for (Integer id : need.ids) {
				int notedId = notedVariantId(id);
				if (notedId > 0) {
					int noted = onPlayer.getOrDefault(notedId, 0);
					if (noted > 0) {
						onPlayer.merge(id, noted, Integer::sum);
						owned.merge(id, noted, Integer::sum);
					}
				}
			}
		}

		List<QuestPreflight.Shortfall> shortfalls = QuestPreflight.compute(needs, owned);
		List<ItemRequirement> toBuy = new ArrayList<>();
		StringBuilder summary = new StringBuilder();
		for (QuestPreflight.Shortfall shortfall : shortfalls) {
			if (summary.length() > 0) {
				summary.append(", ");
			}
			summary.append(shortfall.name).append(" x").append(shortfall.needed);
			if (shortfall.missing > 0) {
				summary.append(shortfall.owned > 0 ? " (buying " + shortfall.missing + " more)" : " (buying)");
				toBuy.add(new ItemRequirement(shortfall.name, shortfall.ids, shortfall.missing));
			} else if (QuestPreflight.countOwned(shortfall.ids, onPlayer) >= shortfall.needed) {
				summary.append(" (on you)");
			} else {
				summary.append(" (in bank)");
			}
		}
		if (!preflightSummaryLogged && summary.length() > 0) {
			preflightSummaryLogged = true;
			Microbot.log("Quest helper preflight for " + quest.getQuest().getName() + ": " + summary, Level.INFO);
		}

		if (toBuy.isEmpty()) {
			finishPreflight(QuestPreflight.DONE);
			return false;
		}
		if (++preflightBuyRounds > PREFLIGHT_MAX_BUY_ROUNDS) {
			String reason = "could not buy " + describeItems(toBuy);
			Microbot.log("Quest helper preflight gave up: " + reason, Level.WARN);
			finishPreflight(QuestPreflight.FAILED_PREFIX + reason);
			return false;
		}

		QuestPreflight.setStatus(QuestPreflight.BUYING);
		Microbot.status = "Quest helper: buying missing quest items";
		acquireMissingTradableItems(toBuy, true);
		return true;
	}

	private void finishPreflight(String status) {
		preflightFinished = true;
		QuestPreflight.setStatus(status);
	}

	private static String describeItems(List<ItemRequirement> items) {
		return items.stream()
				.map(ir -> ir.getName() + " x" + ir.getQuantity())
				.collect(Collectors.joining(", "));
	}

	private static void addContainer(Map<Integer, Integer> counts, ItemAndLastUpdated container) {
		for (Item item : container.getItems()) {
			if (item != null && item.getId() > 0 && item.getQuantity() > 0) {
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	/** Tradable, not step-supplied item requirements of the rest of the quest; null while the Vale Totems wood is unset. */
	private List<QuestPreflight.Need> collectPreflightNeeds(QuestHelper quest, QuestState state) {
		List<Requirement> candidates = new ArrayList<>();
		// Once the quest is underway its start items are spent or placed, so only the steps ahead count
		if (state == QuestState.NOT_STARTED && quest.getItemRequirements() != null) {
			for (ItemRequirement ir : quest.getItemRequirements()) {
				if (ir != null) {
					candidates.add(ir);
				}
			}
		}
		List<DetailedQuestStep> steps = QuestCombatDetector.remainingDetailedSteps(quest);
		if (steps != null) {
			for (DetailedQuestStep step : steps) {
				if (step.getRequirements() == null) {
					continue;
				}
				for (Requirement requirement : step.getRequirements()) {
					if (requirement instanceof ItemRequirement && !isPickedUpByStep(step, (ItemRequirement) requirement)) {
						candidates.add(requirement);
					}
				}
			}
		}

		if (quest.getQuest().getId() == Quest.VALE_TOTEMS.getId()) {
			QuestHelperConfig.ValeTotemsWoodType configured = config.valeTotemsWoodType();
			boolean unresolved = (configured == null || configured == QuestHelperConfig.ValeTotemsWoodType.ASK)
					&& valeTotemsSessionWoodType == null;
			candidates = applyValeTotemsWoodType(candidates);
			if (unresolved) {
				return null;
			}
		}

		Map<Integer, Boolean> tradableByPrimaryId = new HashMap<>();
		List<QuestPreflight.Need> needs = new ArrayList<>();
		for (Requirement requirement : candidates) {
			// Subclasses (keyrings, followers, composites) have their own ownership rules; leave them to the steps
			if (requirement == null || requirement.getClass() != ItemRequirement.class) {
				continue;
			}
			ItemRequirement ir = (ItemRequirement) requirement;
			List<Integer> ids = ir.getAllIds().stream().filter(id -> id != null && id > 0).collect(Collectors.toList());
			if (ir.getQuantity() <= 0 || ids.isEmpty() || ids.contains(ItemID.COINS_995)) {
				continue;
			}
			boolean tradable = tradableByPrimaryId.computeIfAbsent(ids.get(0), k -> isItemRequirementTradable(ir));
			if (!tradable) {
				continue;
			}
			needs.add(new QuestPreflight.Need(ir.getName(), ids, ir.getQuantity(), ir.isConsumed()));
		}
		return needs;
	}

    /** Idles for a few seconds now and then; true while a break is running. Timestamp based, never blocks. */
    private boolean handleShortBreak() {
        long now = System.currentTimeMillis();
        if (!config.takeShortBreaks()) {
            nextBreakAtMs = 0;
            breakEndsAtMs = 0;
            return false;
        }
        if (breakEndsAtMs > now) {
            return true;
        }
        if (breakEndsAtMs != 0 || nextBreakAtMs == 0) {
            breakEndsAtMs = 0;
            int min = Math.max(1, config.breakEveryMinMinutes());
            int max = Math.max(min, config.breakEveryMaxMinutes());
            nextBreakAtMs = now + TimeUnit.MINUTES.toMillis(Rs2Random.between(min, max));
            return false;
        }
        if (now < nextBreakAtMs) {
            return false;
        }

        QuestHelper selected = getQuestHelperPlugin().getSelectedQuest();
        QuestStep step = selected.getCurrentStep() == null ? null : selected.getCurrentStep().getActiveStep();
        if (Rs2Combat.inCombat() || Rs2Dialogue.isInDialogue() || QuestCombatDetector.isKillStep(step)
                || Rs2Bank.isOpen() || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return false;
        }

        int seconds = Math.max(1, config.breakLengthSeconds());
        int length = (int) Math.max(1, Math.round(seconds * (0.75 + Math.random() * 0.5)));
        breakEndsAtMs = now + TimeUnit.SECONDS.toMillis(length);
        Rs2Walker.clearWalkingRoute("quest-helper:short-break");
        Microbot.status = "Quest helper: short break (" + length + "s)";
        Microbot.log("Quest helper: short break for " + length + "s", Level.INFO);
        return true;
    }

    private void combatSupportTick() {
        try {
            if (config == null || !config.startStopQuestHelper() || !Microbot.isLoggedIn()) return;
            if (Microbot.pauseAllScripts.get()) return;
            QuestHelper selected = getQuestHelperPlugin().getSelectedQuest();
            if (selected == null || selected.getCurrentStep() == null) return;
            combat.fightTick(selected.getCurrentStep().getActiveStep());
        } catch (Exception ex) {
            Microbot.log("Quest helper combat support: " + ex.getMessage(), Level.WARN);
        }
    }

	/** Free slots a combat restock must leave for the unstackable quest items this step still needs. */
	private int slotsReservedForStep(QuestStep step) {
		if (!(step instanceof DetailedQuestStep)) {
			return 0;
		}
		int reserved = 0;
		for (Requirement requirement : ((DetailedQuestStep) step).getRequirements()) {
			if (requirement instanceof ItemRequirement) {
				ItemRequirement item = (ItemRequirement) requirement;
				if (!item.mustBeEquipped() && !hasItemRequirementOnPlayer(item)) {
					reserved += isStackable(item) ? 1 : Math.min(uncappedQuantityNeeded(item), INVENTORY_SLOTS);
				}
			}
		}
		return reserved;
	}

	private boolean handleRequirements(DetailedQuestStep questStep) {
		var requirements = questStep.getRequirements();

		for (var requirement : requirements) {
			if (requirement instanceof ItemRequirement) {
				var itemRequirement = (ItemRequirement) requirement;

				if (itemRequirement.mustBeEquipped()) {
					if (itemRequirement.getAllIds().stream().anyMatch(Rs2Equipment::isWearing)) {
						continue;
					}

					if (wearMustBeEquippedFromInventory(itemRequirement)) {
						return true;
					}

					if (!hasItemRequirementOnPlayer(itemRequirement)) {
						notifyMissingRequirement(itemRequirement);
						continue;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Wears/wields a must-be-equipped requirement that is sitting in the inventory.
	 * Returns true if an equip action was issued (caller should end the tick).
	 */
	private boolean wearMustBeEquippedFromInventory(ItemRequirement itemRequirement) {
		if (itemRequirement.getAllIds().stream().anyMatch(Rs2Equipment::isWearing)) {
			return false;
		}
		Integer invId = itemRequirement.getAllIds().stream().filter(Rs2Inventory::contains).findFirst().orElse(null);
		if (invId == null) {
			return false;
		}
		var item = Rs2Inventory.get(invId);
		String action = "Wear";
		if (item != null && item.getInventoryActions() != null) {
			for (String candidate : new String[]{"Wear", "Wield", "Equip"}) {
				if (Arrays.stream(item.getInventoryActions()).anyMatch(a -> a != null && a.equalsIgnoreCase(candidate))) {
					action = candidate;
					break;
				}
			}
		}
		Rs2Inventory.interact(invId, action);
		sleepUntil(() -> itemRequirement.getAllIds().stream().anyMatch(Rs2Equipment::isWearing), 2_000);
		return true;
	}

	private boolean handleMissingItemRequirements(DetailedQuestStep questStep) {
		List<ItemRequirement> missing = new ArrayList<>();
		List<ItemRequirement> needsUnnoting = new ArrayList<>();
		List<Requirement> allRequirements = collectAllItemRequirements(questStep);
		currentRequirements = allRequirements;

		// Slots for the other still-missing items are reserved before capping an unstackable one to what fits
		missingBatch = new ArrayList<>();
		for (Requirement requirement : allRequirements) {
			if (requirement instanceof ItemRequirement
					&& !((ItemRequirement) requirement).mustBeEquipped()
					&& !hasItemRequirementOnPlayer((ItemRequirement) requirement)) {
				missingBatch.add((ItemRequirement) requirement);
			}
		}

		for (Requirement requirement : allRequirements) {
			if (!(requirement instanceof ItemRequirement)) {
				continue;
			}

			ItemRequirement itemRequirement = (ItemRequirement) requirement;

			if (itemRequirement.mustBeEquipped() && wearMustBeEquippedFromInventory(itemRequirement)) {
				return true;
			}

			if (hasItemRequirementOnPlayer(itemRequirement) || isFilledToCarryCapacity(itemRequirement)) {
				continue;
			}

			if (hasNotedVersionInInventory(itemRequirement)) {
				needsUnnoting.add(itemRequirement);
				continue;
			}

			missing.add(itemRequirement);
		}

		if (!needsUnnoting.isEmpty()) {
			return unnoteItemsViaBank(needsUnnoting);
		}

		if (missing.isEmpty()) {
			return false;
		}

		List<ItemRequirement> nonTradableInBank = new ArrayList<>();
		for (ItemRequirement ir : missing) {
			if (!isItemRequirementTradable(ir) && isHeldInBank(ir)) {
				nonTradableInBank.add(ir);
			}
		}

		if (!nonTradableInBank.isEmpty()) {
			return withdrawNonTradablesFromBank(nonTradableInBank);
		}

		ItemRequirement nonTradable = missing.stream()
				.filter(ir -> !isItemRequirementTradable(ir))
				.findFirst()
				.orElse(null);

		if (nonTradable != null) {
			return attemptToAcquireRequirementItem(questStep, nonTradable);
		}

		return acquireMissingTradableItems(missing);
	}

	/** Only true once the bank has been opened this session, so an unknown bank falls back to the old behaviour. */
	private boolean isHeldInBank(ItemRequirement itemRequirement) {
		return itemRequirement.checkTotalMatchesInContainers(QuestContainerManager.getBankData()) > 0;
	}

	private boolean withdrawNonTradablesFromBank(List<ItemRequirement> inBank) {
		Microbot.status = "Quest helper: fetching " + inBank.get(0).getName() + " from the bank";

		if (!Rs2Bank.isOpen()) {
			Rs2Bank.walkToBankAndUseBank();
			sleepUntil(Rs2Bank::isOpen, 15_000);
			if (!Rs2Bank.isOpen()) {
				return true;
			}
		}

		clearInventoryForQuest();
		if (makeRoomForCarryLimited(inBank)) {
			return true;
		}

		for (ItemRequirement ir : inBank) {
			int idToWithdraw = -1;
			int bestCount = 0;
			for (Integer id : ir.getAllIds()) {
				if (id == null || id <= 0) {
					continue;
				}
				int count = Rs2Bank.count(id);
				if (count > bestCount) {
					bestCount = count;
					idToWithdraw = id;
				}
			}

			int qty = Math.min(remainingQuantityNeeded(ir), bestCount);
			if (idToWithdraw == -1 || qty <= 0) {
				continue;
			}

			Microbot.status = "Withdrawing " + ir.getName() + " x" + qty;
			Rs2Bank.withdrawX(idToWithdraw, qty);
			if (!sleepUntil(() -> hasItemRequirementOnPlayer(ir) || isFilledToCarryCapacity(ir), 2_000)) {
				Microbot.log("Quest helper: bank withdrawal for " + ir.getName() + " did not land in time", Level.WARN);
			}
		}

		return true;
	}

	/** With the bank open, banks what the rest of the quest no longer needs before any carry cap is computed. */
	private void clearInventoryForQuest() {
		QuestHelper quest = getQuestHelperPlugin().getSelectedQuest();
		if (quest == null) {
			return;
		}
		QuestStep current = quest.getCurrentStep();
		combat.prepareBankInventory(quest, current == null ? null : current.getActiveStep());
	}

	/** With the bank open, clears unrelated items when a carry-limited item has no room at all. Returns true if it deposited. */
	private boolean makeRoomForCarryLimited(List<ItemRequirement> candidates) {
		boolean noRoom = candidates.stream().anyMatch(ir -> isCarryLimited(ir)
				&& remainingQuantityNeeded(ir) <= 0
				&& inventoryQuantity(ir) == 0);
		if (!noRoom) {
			return false;
		}

		Set<Integer> keep = new HashSet<>();
		keep.add(ItemID.COINS_995);
		for (Requirement requirement : currentRequirements) {
			if (requirement instanceof ItemRequirement) {
				for (Integer id : ((ItemRequirement) requirement).getAllIds()) {
					if (id != null) {
						keep.add(id);
					}
				}
			}
		}

		Microbot.status = "Quest helper: banking unrelated items to make room";
		Predicate<Rs2ItemModel> isKept = item -> keep.contains(item.getId());
		return Rs2Bank.depositAllExcept(isKept);
	}

	private boolean acquireMissingTradableItems(List<ItemRequirement> missing) {
		return acquireMissingTradableItems(missing, false);
	}

	/**
	 * @param stockpile true for the preflight: every requirement is bought in full (it is already the shortfall),
	 *                  nothing is withdrawn to the inventory, and purchases stay in the bank
	 */
	private boolean acquireMissingTradableItems(List<ItemRequirement> missing, boolean stockpile) {
		notifyMissingRequirement(missing.get(0));

		List<ItemRequirement> actionable = new ArrayList<>();
		for (ItemRequirement ir : missing) {
			if (!hasMatchingGrandExchangeOffer(ir)) {
				actionable.add(ir);
			}
		}

		if (actionable.isEmpty()) {
			if (!Rs2GrandExchange.isOpen()) {
				Microbot.status = "Quest helper: heading to Grand Exchange for in-progress offers";
				Rs2GrandExchange.walkToGrandExchange();
				Rs2GrandExchange.openExchange();
				sleepUntil(Rs2GrandExchange::isOpen, 15_000);
				if (!Rs2GrandExchange.isOpen()) {
					return true;
				}
			}

			if (!Rs2GrandExchange.hasBoughtOffer()) {
				Microbot.status = "Waiting for Grand Exchange offers to fill";
				if (!sleepUntil(Rs2GrandExchange::hasBoughtOffer, 60_000)) {
					stopQuesterWithReason(
							"Grand Exchange offers for missing quest items did not fill within 60 seconds. "
									+ "They may be underpriced or low supply — cancel them manually and retry.");
					return true;
				}
			}

			collectPurchasedItemsViaBank(missing, stockpile);
			return true;
		}

		if (!Rs2Bank.isOpen()) {
			Microbot.status = "Quest helper: walking to bank for missing items";
			Rs2Bank.walkToBankAndUseBank();
			sleepUntil(Rs2Bank::isOpen, 15_000);
			if (!Rs2Bank.isOpen()) {
				return true;
			}
		}

		if (!stockpile) {
			clearInventoryForQuest();
			if (makeRoomForCarryLimited(actionable)) {
				return true;
			}
		}

		List<ItemRequirement> fromBank = new ArrayList<>();
		Map<ItemRequirement, Integer> bankWithdrawId = new HashMap<>();
		List<ItemRequirement> toBuy = new ArrayList<>();

		for (ItemRequirement ir : actionable) {
			if (stockpile) {
				toBuy.add(ir);
				continue;
			}
			int needed = remainingQuantityNeeded(ir);
			if (needed <= 0) {
				continue;
			}

			int bestBankId = -1;
			int bestBankCount = 0;
			for (Integer id : ir.getAllIds()) {
				if (id == null || id <= 0) {
					continue;
				}
				int count = Rs2Bank.count(id);
				if (count > bestBankCount) {
					bestBankCount = count;
					bestBankId = id;
				}
			}

			// An item that cannot all be carried is withdrawn in whatever amount the bank has; the rest is bought
			if (bestBankCount >= needed || (bestBankCount > 0 && isCarryLimited(ir))) {
				fromBank.add(ir);
				bankWithdrawId.put(ir, bestBankId);
			} else {
				toBuy.add(ir);
			}
		}

		long totalBuyCost = 0L;
		Map<ItemRequirement, Integer> offerPrices = new HashMap<>();
		Map<ItemRequirement, Integer> buyQuantities = new HashMap<>();
		Map<ItemRequirement, Integer> buyPrimaryIds = new HashMap<>();

		for (ItemRequirement ir : toBuy) {
			if (isCoinsRequirement(ir.getAllIds())) {
				Rs2Bank.closeBank();
				stopQuesterWithReason(String.format(
						"Not enough coins for this quest step (need %,d gp in total)", ir.getQuantity()), Level.WARN);
				return true;
			}

			int primaryId = tradablePrimaryId(ir);
			if (primaryId == -1) {
				Rs2Bank.closeBank();
				stopQuesterWithReason("Quest item is not tradable on the Grand Exchange: " + ir.getName());
				return true;
			}

			if (isMembersItem(primaryId) && !isOnMembersWorld()) {
				Rs2Bank.closeBank();
				stopQuesterWithReason(ir.getName()
						+ " is members-only and can't be bought on the Grand Exchange from a free-to-play world.", Level.WARN);
				return true;
			}

			int basePrice = fetchInstabuyReferencePrice(primaryId);
			if (basePrice <= 0) {
				Rs2Bank.closeBank();
				stopQuesterWithReason("No Grand Exchange price for " + ir.getName()
						+ ": it isn't sold on the Grand Exchange, or the price service is unreachable. Get it in-game and restart.",
						Level.WARN);
				return true;
			}

			int offerPrice = Math.max(1, (int) Math.ceil(basePrice * 1.2));
			// Buying the full amount is fine, it lands in the bank and is withdrawn a trip at a time
			int qty = stockpile ? ir.getQuantity() : uncappedQuantityNeeded(ir);
			buyPrimaryIds.put(ir, primaryId);
			offerPrices.put(ir, offerPrice);
			buyQuantities.put(ir, qty);
			totalBuyCost += (long) offerPrice * qty;
		}

		long invCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
		long bankCoins = Rs2Bank.count(ItemID.COINS_995);
		long availableGp = invCoins + bankCoins;

		if (availableGp < totalBuyCost) {
			Rs2Bank.closeBank();
			stopQuesterWithReason(String.format(
					"Not enough gp to buy missing quest items (need %,d gp, have %,d gp)%s",
					totalBuyCost, availableGp, stockpile ? ": " + describeItems(toBuy) : ""), Level.WARN);
			return true;
		}

		if (!toBuy.isEmpty()) {
			int freeSlots = Rs2GrandExchange.getAvailableSlotsCount();
			if (freeSlots <= 0) {
				Rs2Bank.closeBank();
				if (Rs2GrandExchange.hasBoughtOffer()) {
					Microbot.status = "Quest helper: collecting finished Grand Exchange offers";
					Rs2GrandExchange.walkToGrandExchange();
					Rs2GrandExchange.openExchange();
					sleepUntil(Rs2GrandExchange::isOpen, 15_000);
					if (Rs2GrandExchange.isOpen()) {
						collectPurchasedItemsViaBank(missing, stockpile);
						if (Rs2GrandExchange.getAvailableSlotsCount() <= 0) {
							stopQuesterWithReason(
									"Could not collect finished Grand Exchange offers (bank full?). Collect them manually and restart.");
						}
					}
					return true;
				}
				stopQuesterWithReason(
						"No free Grand Exchange slots for missing quest items. Free a slot and restart.", Level.WARN);
				return true;
			}
			int missingCount = toBuy.size();
			if (trimToGrandExchangeSlots(toBuy, freeSlots)) {
				Microbot.log(String.format(
						"Quest helper: %d missing quest items but only %d free Grand Exchange slots; buying %d now and the rest afterwards",
						missingCount, freeSlots, toBuy.size()), Level.INFO);
				totalBuyCost = 0L;
				for (ItemRequirement ir : toBuy) {
					totalBuyCost += (long) offerPrices.get(ir) * buyQuantities.get(ir);
				}
			}
		}

		if (!Rs2Bank.setWithdrawAsItem()) {
			Rs2Bank.closeBank();
			stopQuesterWithReason("Failed to set bank withdraw mode to Item. Toggle it manually and restart.");
			return true;
		}

		boolean bankWithdrawalMissed = false;
		for (ItemRequirement ir : fromBank) {
			Integer idToWithdraw = bankWithdrawId.get(ir);
			if (idToWithdraw == null || idToWithdraw <= 0) {
				continue;
			}
			int qty = Math.min(remainingQuantityNeeded(ir), Rs2Bank.count(idToWithdraw));
			if (qty <= 0) {
				continue;
			}
			Microbot.status = "Withdrawing " + ir.getName() + " x" + qty;
			Rs2Bank.withdrawX(idToWithdraw, qty);
			if (!sleepUntil(() -> hasItemRequirementOnPlayer(ir) || isFilledToCarryCapacity(ir), 2_000)) {
				Microbot.log("Quest helper: bank withdrawal for " + ir.getName() + " did not land in time",
						Level.WARN);
				bankWithdrawalMissed = true;
			}
		}

		if (bankWithdrawalMissed && Rs2Inventory.isFull()) {
			Rs2Bank.closeBank();
			stopQuesterWithReason("Inventory is full, so missing quest items can't be withdrawn. Free some slots and restart.",
					Level.WARN);
			return true;
		}

		if (!toBuy.isEmpty()) {
			final long requiredCoins = totalBuyCost;
			long currentInvCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
			if (requiredCoins > currentInvCoins) {
				long coinsStillNeeded = requiredCoins - currentInvCoins;
				int coinsToWithdraw = (int) Math.min(Integer.MAX_VALUE, coinsStillNeeded);
				Microbot.status = "Withdrawing " + coinsToWithdraw + " gp for Grand Exchange";
				Rs2Bank.withdrawX(ItemID.COINS_995, coinsToWithdraw);
				sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995) >= requiredCoins, 3_000);
			}

			long invCoinsAfter = Rs2Inventory.itemQuantity(ItemID.COINS_995);
			if (invCoinsAfter < requiredCoins) {
				Rs2Bank.closeBank();
				if (invCoinsAfter == 0 && Rs2Inventory.isFull()) {
					stopQuesterWithReason("Inventory is full, so coins for the Grand Exchange can't be withdrawn. Free a slot and restart.",
							Level.WARN);
				} else {
					stopQuesterWithReason(String.format(
							"Bank withdrawal failed to supply enough coins (need %,d gp, have %,d gp in inventory)",
							requiredCoins, invCoinsAfter));
				}
				return true;
			}
		}

		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);

		if (toBuy.isEmpty()) {
			return true;
		}

		if (!Rs2GrandExchange.isOpen()) {
			Microbot.status = "Quest helper: walking to Grand Exchange";
			Rs2GrandExchange.walkToGrandExchange();
			Rs2GrandExchange.openExchange();
			sleepUntil(Rs2GrandExchange::isOpen, 15_000);
			if (!Rs2GrandExchange.isOpen()) {
				return true;
			}
		}

		Map<ItemRequirement, Integer> heldBeforeBuying = new HashMap<>();
		if (stockpile) {
			for (ItemRequirement ir : toBuy) {
				heldBeforeBuying.put(ir, inventoryQuantityIncludingNoted(ir));
			}
		}
		int placedOffers = 0;
		for (ItemRequirement ir : toBuy) {
			int offerPrice = offerPrices.getOrDefault(ir, 0);
			int qty = buyQuantities.getOrDefault(ir, 0);
			int primaryId = buyPrimaryIds.getOrDefault(ir, -1);
			if (offerPrice <= 0 || qty <= 0 || primaryId == -1) {
				continue;
			}

			String canonicalName = canonicalItemName(primaryId);
			if (canonicalName == null || canonicalName.isEmpty() || "null".equalsIgnoreCase(canonicalName)) {
				stopQuesterWithReason("Unable to resolve in-game name for: " + ir.getName());
				return true;
			}

			Microbot.status = "Buying " + canonicalName + " x" + qty;
			if (Rs2GrandExchange.buyItem(canonicalName, offerPrice, qty)) {
				placedOffers++;
			}
			sleep(800, 1500);
		}

		if (placedOffers == 0) {
			stopQuesterWithReason("Failed to place any Grand Exchange offers for missing quest items");
			return true;
		}

		final int maxBuyAttempts = 5;
		final int perAttemptWaitMs = 15_000;

		for (int attempt = 1; attempt <= maxBuyAttempts; attempt++) {
			Microbot.status = String.format(
					"Waiting for Grand Exchange offers to fill (attempt %d/%d)",
					attempt, maxBuyAttempts);

			sleepUntil(() -> itemsStillBuying(toBuy, buyPrimaryIds).isEmpty(), perAttemptWaitMs);

			List<ItemRequirement> stillPending = itemsStillBuying(toBuy, buyPrimaryIds);
			if (stillPending.isEmpty()) {
				break;
			}

			if (attempt >= maxBuyAttempts) {
				Rs2GrandExchange.abortAllOffers(false);
				stopQuesterWithReason(String.format(
						"Grand Exchange offers did not fill after %d attempts with doubled prices. "
								+ "Aborted all offers — check your inventory for any partially-filled items "
								+ "and add more gp before restarting.",
						maxBuyAttempts));
				return true;
			}

			for (ItemRequirement ir : stillPending) {
				int primaryId = buyPrimaryIds.getOrDefault(ir, -1);
				if (primaryId == -1) {
					continue;
				}
				String canonicalName = canonicalItemName(primaryId);
				if (canonicalName == null || canonicalName.isEmpty()) {
					continue;
				}

				long preAbortCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);

				Microbot.status = "Cancelling unfilled " + canonicalName;
				if (!Rs2GrandExchange.abortOffer(canonicalName, false)) {
					Microbot.log("Quest helper: failed to abort offer for " + canonicalName
							+ "; skipping retry for this item this round", Level.WARN);
					continue;
				}

				sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995) > preAbortCoins, 3_000);

				int alreadyOnHand = inventoryQuantityIncludingNoted(ir) - heldBeforeBuying.getOrDefault(ir, 0);
				int qtyNeeded = Math.max(0, ir.getQuantity() - Math.max(0, alreadyOnHand));
				if (qtyNeeded <= 0) {
					Microbot.log("Quest helper: " + canonicalName
							+ " already obtained after abort (have " + alreadyOnHand
							+ "); skipping retry buy", Level.INFO);
					buyQuantities.put(ir, 0);
					continue;
				}
				buyQuantities.put(ir, qtyNeeded);

				int currentPrice = offerPrices.getOrDefault(ir, 0);
				long doubled = (long) currentPrice * 2L;
				int newPrice = doubled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) doubled;
				offerPrices.put(ir, newPrice);

				if (newPrice <= 0) {
					continue;
				}

				long retryInvCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
				long costNeeded = (long) newPrice * (long) qtyNeeded;
				if (retryInvCoins < costNeeded) {
					Rs2GrandExchange.abortAllOffers(false);
					stopQuesterWithReason(String.format(
							"Not enough gp to retry %s at %,d gp each (need %,d, have %,d)",
							canonicalName, newPrice, costNeeded, retryInvCoins), Level.WARN);
					return true;
				}

				Microbot.status = String.format(
						"Retry %d/%d: buying %s x%d at %,d gp",
						attempt + 1, maxBuyAttempts, canonicalName, qtyNeeded, newPrice);
				if (!Rs2GrandExchange.buyItem(canonicalName, newPrice, qtyNeeded)) {
					Microbot.log("Quest helper: retry buy for " + canonicalName + " failed to place",
							Level.WARN);
				}
				sleep(800, 1500);
			}
		}

		collectPurchasedItemsViaBank(toBuy, stockpile);
		return true;
	}

	private List<ItemRequirement> itemsStillBuying(List<ItemRequirement> toBuy,
			Map<ItemRequirement, Integer> primaryIds) {
		List<ItemRequirement> pending = new ArrayList<>();
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		if (offers == null) {
			return pending;
		}

		for (ItemRequirement ir : toBuy) {
			int primaryId = primaryIds.getOrDefault(ir, -1);
			if (primaryId == -1) {
				continue;
			}

			for (GrandExchangeOffer offer : offers) {
				if (offer == null) {
					continue;
				}
				if (offer.getItemId() == primaryId
						&& offer.getState() == GrandExchangeOfferState.BUYING) {
					pending.add(ir);
					break;
				}
			}
		}
		return pending;
	}

	private void collectPurchasedItemsViaBank(List<ItemRequirement> items, boolean stockpile) {
		Microbot.status = "Collecting purchased quest items to bank";
		Rs2GrandExchange.collectAllToBank();
		sleep(800, 1200);

		if (Rs2GrandExchange.isOpen()) {
			Rs2GrandExchange.closeExchange();
			sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_000);
		}

		if (!Rs2Bank.isOpen()) {
			Microbot.status = "Opening bank to retrieve purchased quest items";
			Rs2Bank.walkToBankAndUseBank();
			sleepUntil(Rs2Bank::isOpen, 15_000);
			if (!Rs2Bank.isOpen()) {
				Microbot.log("Quest helper: failed to open bank after Grand Exchange collection", Level.WARN);
				return;
			}
		}

		if (!Rs2Bank.setWithdrawAsItem()) {
			Rs2Bank.closeBank();
			stopQuesterWithReason("Failed to set bank withdraw mode to Item after Grand Exchange collection. Toggle it manually and restart.");
			return;
		}

		if (stockpile) {
			// Purchases stay in the bank; wait for the bank snapshot to show them so they are not bought twice
			sleepUntil(() -> items.stream().allMatch(this::isHeldInBank), 3_000);
			Rs2Bank.closeBank();
			sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
			return;
		}

		for (ItemRequirement ir : items) {
			int qty = remainingQuantityNeeded(ir);
			if (qty <= 0) {
				continue;
			}

			int idToWithdraw = -1;
			for (Integer id : ir.getAllIds()) {
				if (id == null || id <= 0) {
					continue;
				}
				if (Rs2Bank.count(id) > 0) {
					idToWithdraw = id;
					break;
				}
			}

			if (idToWithdraw == -1) {
				continue;
			}

			qty = Math.min(qty, Rs2Bank.count(idToWithdraw));
			Microbot.status = "Withdrawing " + ir.getName() + " x" + qty;
			Rs2Bank.withdrawX(idToWithdraw, qty);
			if (!sleepUntil(() -> hasItemRequirementOnPlayer(ir) || isFilledToCarryCapacity(ir), 2_000)) {
				Microbot.log("Quest helper: purchased item " + ir.getName() + " did not land in inventory after bank withdrawal",
						Level.WARN);
			}
		}

		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
	}

	private boolean isItemRequirementTradable(ItemRequirement itemRequirement) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			for (Integer id : itemRequirement.getAllIds()) {
				if (id == null || id <= 0) {
					continue;
				}
				ItemComposition def = Microbot.getClient().getItemDefinition(id);
				if (def != null && def.isTradeable()) {
					return true;
				}
			}
			return false;
		}).orElse(false);
	}

	private int tradablePrimaryId(ItemRequirement itemRequirement) {
		List<Integer> tradableIds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
			List<Integer> ids = new ArrayList<>();
			for (Integer id : itemRequirement.getAllIds()) {
				if (id == null || id <= 0) {
					continue;
				}
				ItemComposition def = Microbot.getClient().getItemDefinition(id);
				if (def != null && def.isTradeable()) {
					ids.add(id);
				}
			}
			return ids;
		}).orElse(new ArrayList<>());

		if (tradableIds.isEmpty()) {
			return -1;
		}

		// Pick the cheapest tradable variant to avoid league/cosmetic items priced at MAX_VALUE
		int bestId = tradableIds.get(0);
		int bestPrice = Integer.MAX_VALUE;
		for (int id : tradableIds) {
			int price = fetchInstabuyReferencePrice(id);
			if (price > 0 && price < bestPrice) {
				bestPrice = price;
				bestId = id;
			}
		}
		return bestId;
	}

	static boolean isCoinsRequirement(Collection<Integer> itemIds) {
		return itemIds != null && itemIds.contains(ItemID.COINS_995);
	}

	private boolean isMembersItem(int itemId) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			ItemComposition def = Microbot.getClient().getItemDefinition(itemId);
			return def != null && def.isMembers();
		}).orElse(false);
	}

	private boolean isOnMembersWorld() {
		return Microbot.getClientThread().runOnClientThreadOptional(
				() -> Microbot.getClient().getWorldType().contains(WorldType.MEMBERS)).orElse(true);
	}

	private int remainingQuantityNeeded(ItemRequirement itemRequirement) {
		int onPlayer = itemRequirement.checkTotalMatchesInContainers(
				QuestContainerManager.getEquippedData(),
				QuestContainerManager.getInventoryData());
		return Math.max(0, targetQuantity(itemRequirement) - onPlayer);
	}

	private int uncappedQuantityNeeded(ItemRequirement itemRequirement) {
		int onPlayer = itemRequirement.checkTotalMatchesInContainers(
				QuestContainerManager.getEquippedData(),
				QuestContainerManager.getInventoryData());
		return Math.max(0, itemRequirement.getQuantity() - onPlayer);
	}

	/**
	 * What can be carried of an unstackable item that cannot all fit, never more than the requirement itself.
	 *
	 * @param wanted        quantity the requirement asks for
	 * @param held          amount of it already in the inventory
	 * @param freeSlots     empty inventory slots
	 * @param reservedSlots free slots kept for the other items still missing
	 */
	static int carryCap(int wanted, int held, int freeSlots, int reservedSlots) {
		return Math.min(wanted, held + Math.max(0, freeSlots - reservedSlots));
	}

	/** Quantity of the requirement to aim for, lowered when an unstackable item cannot physically be carried. */
	private int targetQuantity(ItemRequirement itemRequirement) {
		int wanted = itemRequirement.getQuantity();
		if (wanted <= 1 || itemRequirement.mustBeEquipped() || isStackable(itemRequirement)) {
			return wanted;
		}

		int reserved = 0;
		for (ItemRequirement other : missingBatch) {
			if (other == itemRequirement || other.getQuantity() <= 0) {
				continue;
			}
			// Only items that fit on their own claim space, otherwise they would starve the rest
			int otherNeeded = uncappedQuantityNeeded(other);
			if (otherNeeded <= INVENTORY_SLOTS) {
				reserved += isStackable(other) ? 1 : otherNeeded;
			}
		}

		return carryCap(wanted, inventoryQuantity(itemRequirement), Rs2Inventory.emptySlotCount(), reserved);
	}

	private boolean isCarryLimited(ItemRequirement itemRequirement) {
		return targetQuantity(itemRequirement) < itemRequirement.getQuantity();
	}

	/** True when holding all that fits of an item that cannot be carried in full, so the step can proceed. */
	private boolean isFilledToCarryCapacity(ItemRequirement itemRequirement) {
		return isCarryLimited(itemRequirement)
				&& inventoryQuantity(itemRequirement) > 0
				&& remainingQuantityNeeded(itemRequirement) <= 0;
	}

	private int inventoryQuantity(ItemRequirement itemRequirement) {
		int total = 0;
		for (Integer id : itemRequirement.getAllIds()) {
			if (id != null && id > 0) {
				total += Rs2Inventory.itemQuantity(id);
			}
		}
		return total;
	}

	private boolean isStackable(ItemRequirement itemRequirement) {
		for (Integer id : itemRequirement.getAllIds()) {
			if (id == null || id <= 0) {
				continue;
			}
			Boolean cached = stackableCache.get(id);
			if (cached == null) {
				cached = Microbot.getClientThread().runOnClientThreadOptional(() -> {
					ItemComposition def = Microbot.getClient().getItemDefinition(id);
					return def != null && def.isStackable();
				}).orElse(null);
				if (cached == null) {
					return true;
				}
				stackableCache.put(id, cached);
			}
			return cached;
		}
		return true;
	}

	/** A plain "pick up X" step requires X only as the thing to pick up, so it must not be banked or bought. */
	private boolean isPickedUpByStep(DetailedQuestStep questStep, ItemRequirement itemRequirement) {
		// A kill step that lists its own drop (e.g. the golden key) must not try to fetch it first
		if (isKillNpcStep(questStep)) {
			return !itemRequirement.mustBeEquipped() && !isItemRequirementTradable(itemRequirement);
		}
		if (questStep.getClass() != DetailedQuestStep.class) {
			return false;
		}
		int icon = questStep.getIconItemID();
		if (icon != -1 && itemRequirement.getAllIds().contains(icon)) {
			return true;
		}
		return questStep.getText().stream().anyMatch(x -> x != null && x.toLowerCase().startsWith("pick up"));
	}

	private static boolean isKillNpcStep(QuestStep step) {
		return step instanceof NpcStep
				&& step.getText().stream().anyMatch(x -> x != null && x.toLowerCase().contains("kill"));
	}

	/** After the target dies, pick up the item the kill step is waiting for. */
	private boolean lootKillDrop(NpcStep step) {
		if (Rs2Combat.inCombat()) {
			return false;
		}
		for (Requirement requirement : step.getRequirements()) {
			if (!(requirement instanceof ItemRequirement)) {
				continue;
			}
			ItemRequirement item = (ItemRequirement) requirement;
			if (item.mustBeEquipped() || hasItemRequirementOnPlayer(item)) {
				continue;
			}
			for (Integer id : item.getAllIds()) {
				if (id != null && id > 0 && lootGroundItem(id, 8)) {
					return true;
				}
			}
		}
		return false;
	}

	private List<Requirement> collectAllItemRequirements(DetailedQuestStep questStep) {
		List<Requirement> combined = new ArrayList<>(questStep.getRequirements());

		Set<Integer> seenIds = new HashSet<>();
		for (Requirement req : questStep.getRequirements()) {
			if (req instanceof ItemRequirement) {
				seenIds.add(((ItemRequirement) req).getId());
			}
		}
		combined.removeIf(req -> req instanceof ItemRequirement && isPickedUpByStep(questStep, (ItemRequirement) req));

		QuestHelper selectedQuest = getQuestHelperPlugin().getSelectedQuest();
		if (selectedQuest != null) {
			updateEverHeldItemTracking(selectedQuest);

			List<ItemRequirement> questLevel = selectedQuest.getItemRequirements();
			if (questLevel != null) {
				for (ItemRequirement ir : questLevel) {
					if (ir == null) {
						continue;
					}
					if (!seenIds.add(ir.getId())) {
						continue;
					}
					if (everHeldItemRequirementIds.contains(ir.getId())) {
						continue;
					}
					// Bulk unstackables (e.g. 50 essence) would clog the inventory from step one; the step that needs them asks for them
					if (ir.getQuantity() > QUEST_LEVEL_PREGATHER_MAX && !ir.mustBeEquipped() && !isStackable(ir)) {
						continue;
					}
					combined.add(ir);
				}
			}
		}

		if (selectedQuest != null
				&& selectedQuest.getQuest() != null
				&& selectedQuest.getQuest().getId() == Quest.VALE_TOTEMS.getId()) {
			combined = applyValeTotemsWoodType(combined);
		}

		return combined;
	}

	private void updateEverHeldItemTracking(QuestHelper selectedQuest) {
		if (selectedQuest == null || selectedQuest.getQuest() == null) {
			return;
		}

		int questId = selectedQuest.getQuest().getId();
		if (questId != heldTrackingQuestId) {
			heldTrackingQuestId = questId;
			everHeldItemRequirementIds.clear();

			QuestState state = null;
			try {
				state = selectedQuest.getQuest().getState(Microbot.getClient());
			} catch (Exception ignored) {
			}

			if (state == QuestState.IN_PROGRESS) {
				List<ItemRequirement> questLevel = selectedQuest.getItemRequirements();
				if (questLevel != null) {
					for (ItemRequirement ir : questLevel) {
						if (ir != null) {
							everHeldItemRequirementIds.add(ir.getId());
						}
					}
				}
			}
		}

		List<ItemRequirement> questLevel = selectedQuest.getItemRequirements();
		if (questLevel == null) {
			return;
		}

		for (ItemRequirement ir : questLevel) {
			if (ir == null) {
				continue;
			}
			if (everHeldItemRequirementIds.contains(ir.getId())) {
				continue;
			}
			if (hasItemRequirementOnPlayer(ir)) {
				everHeldItemRequirementIds.add(ir.getId());
			}
		}
	}

	private boolean shouldObtainMissingItems() {
		if (Rs2Player.isIronman()) {
			return false;
		}
		QuestHelperConfig.ObtainMissingItemsOption option = config.obtainMissingItems();
		if (option == QuestHelperConfig.ObtainMissingItemsOption.YES) {
			return true;
		}
		if (option == QuestHelperConfig.ObtainMissingItemsOption.NO) {
			return false;
		}
		if (obtainItemsSessionChoice != null) {
			return obtainItemsSessionChoice;
		}
		promptObtainMissingItems();
		return false;
	}

	private void promptObtainMissingItems() {
		if (!obtainItemsPromptInFlight.compareAndSet(false, true)) {
			return;
		}

		SwingUtilities.invokeLater(() -> {
			try {
				int choice = JOptionPane.showConfirmDialog(
						null,
						"The quest helper has detected missing items.\n\n" +
								"Would you like the quest helper to automatically obtain\n" +
								"missing items from the bank and Grand Exchange?",
						"Obtain Missing Items",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE);

				obtainItemsSessionChoice = (choice == JOptionPane.YES_OPTION);
			} finally {
				obtainItemsPromptInFlight.set(false);
			}
		});
	}

	private void promptValeTotemsWoodType() {
		if (!valeTotemsPromptInFlight.compareAndSet(false, true)) {
			return;
		}

		stopQuesterWithReason("Vale Totems: pick a wood type in the dialog to continue.");

		SwingUtilities.invokeLater(() -> {
			try {
				QuestHelperConfig.ValeTotemsWoodType[] values = QuestHelperConfig.ValeTotemsWoodType.values();
				List<QuestHelperConfig.ValeTotemsWoodType> selectable = new ArrayList<>();
				for (QuestHelperConfig.ValeTotemsWoodType value : values) {
					if (value != QuestHelperConfig.ValeTotemsWoodType.ASK) {
						selectable.add(value);
					}
				}

				Object[] options = selectable.stream().map(Object::toString).toArray();

				int choice = JOptionPane.showOptionDialog(
						null,
						"Which wood type are you using for Vale Totems?\n\n" +
								"This must match the logs you used to build the totem.\n" +
								"The quester will source the matching logs and decorative items (shields/longbows/shortbows).",
						"Vale Totems - Wood Type",
						JOptionPane.DEFAULT_OPTION,
						JOptionPane.QUESTION_MESSAGE,
						null,
						options,
						options[0]);

				if (choice >= 0 && choice < selectable.size()) {
					QuestHelperConfig.ValeTotemsWoodType selected = selectable.get(choice);
					valeTotemsSessionWoodType = selected;
					Microbot.getConfigManager().setConfiguration(
							QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", true);
					Microbot.status = "Vale Totems: using " + selected + " (this session)";
					Microbot.log("Quest helper: Vale Totems wood type set to " + selected
							+ " for this session (config stays on 'Ask me')", Level.INFO);
				}
			} finally {
				valeTotemsPromptInFlight.set(false);
			}
		});
	}

	private List<Requirement> applyValeTotemsWoodType(List<Requirement> requirements) {
		QuestHelperConfig.ValeTotemsWoodType configured = config.valeTotemsWoodType();
		QuestHelperConfig.ValeTotemsWoodType woodType;

		if (configured != null && configured != QuestHelperConfig.ValeTotemsWoodType.ASK) {
			woodType = configured;
		} else {
			woodType = valeTotemsSessionWoodType;
			if (woodType == null) {
				promptValeTotemsWoodType();
				return requirements;
			}
		}

		if (woodType == QuestHelperConfig.ValeTotemsWoodType.OAK) {
			return requirements;
		}

		List<Requirement> transformed = new ArrayList<>(requirements.size());
		for (Requirement req : requirements) {
			if (!(req instanceof ItemRequirement)) {
				transformed.add(req);
				continue;
			}

			ItemRequirement ir = (ItemRequirement) req;
			List<Integer> allIds = ir.getAllIds();

			if (allIds.contains(ItemID.OAK_LOGS)) {
				transformed.add(new ItemRequirement(
						woodType + " log", woodType.getLogId(), ir.getQuantity()));
			} else if (allIds.contains(ItemID.OAK_SHIELD)
					|| allIds.contains(ItemID.OAK_LONGBOW)
					|| allIds.contains(ItemID.OAK_SHORTBOW)
					|| allIds.contains(ItemID.OAK_LONGBOW_U)
					|| allIds.contains(ItemID.OAK_SHORTBOW_U)) {
				transformed.add(new ItemRequirement(
						woodType + " shield/longbow/shortbow",
						woodType.getDecorativeIds(),
						ir.getQuantity()));
			} else {
				transformed.add(req);
			}
		}
		return transformed;
	}

	private String canonicalItemName(int itemId) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			ItemComposition def = Microbot.getClient().getItemDefinition(itemId);
			return def != null ? def.getName() : null;
		}).orElse(null);
	}

	private int fetchInstabuyReferencePrice(int itemId) {
		WikiPrice priceData = Rs2GrandExchange.getRealTimePrices(itemId);
		if (priceData != null && priceData.buyPrice > 0 && priceData.buyPrice < Integer.MAX_VALUE) {
			return priceData.buyPrice;
		}
		int price = Rs2GrandExchange.getPrice(itemId);
		return (price > 0 && price < Integer.MAX_VALUE) ? price : -1;
	}

	private int notedVariantId(int unnotedId) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			ItemComposition def = Microbot.getClient().getItemDefinition(unnotedId);
			if (def == null) {
				return -1;
			}
			if (def.getNote() == 799) {
				return -1;
			}
			int linked = def.getLinkedNoteId();
			return linked > 0 ? linked : -1;
		}).orElse(-1);
	}

	private boolean hasNotedVersionInInventory(ItemRequirement itemRequirement) {
		if (remainingQuantityNeeded(itemRequirement) <= 0) {
			return false;
		}
		for (Integer id : itemRequirement.getAllIds()) {
			if (id == null || id <= 0) {
				continue;
			}
			int notedId = notedVariantId(id);
			if (notedId > 0 && Rs2Inventory.itemQuantity(notedId) > 0) {
				return true;
			}
		}
		return false;
	}

	private int inventoryQuantityIncludingNoted(ItemRequirement itemRequirement) {
		int total = 0;
		for (Integer id : itemRequirement.getAllIds()) {
			if (id == null || id <= 0) {
				continue;
			}
			total += Rs2Inventory.itemQuantity(id);
			int notedId = notedVariantId(id);
			if (notedId > 0) {
				total += Rs2Inventory.itemQuantity(notedId);
			}
		}
		return total;
	}

	private boolean unnoteItemsViaBank(List<ItemRequirement> items) {
		if (!Rs2Bank.isOpen()) {
			Microbot.status = "Quest helper: walking to bank to un-note items";
			Rs2Bank.walkToBankAndUseBank();
			sleepUntil(Rs2Bank::isOpen, 15_000);
			if (!Rs2Bank.isOpen()) {
				return true;
			}
		}

		if (!Rs2Bank.setWithdrawAsItem()) {
			Rs2Bank.closeBank();
			stopQuesterWithReason("Failed to set bank withdraw mode to Item while un-noting quest items. Toggle it manually and restart.");
			return true;
		}

		for (ItemRequirement ir : items) {
			int needed = remainingQuantityNeeded(ir);
			if (needed <= 0) {
				continue;
			}

			for (Integer unnotedId : ir.getAllIds()) {
				if (unnotedId == null || unnotedId <= 0) {
					continue;
				}
				int notedId = notedVariantId(unnotedId);
				if (notedId <= 0) {
					continue;
				}

				int notesInInventory = Rs2Inventory.itemQuantity(notedId);
				if (notesInInventory <= 0) {
					continue;
				}

				Microbot.status = "Depositing noted " + ir.getName();
				Rs2Bank.depositAll(notedId);
				sleepUntil(() -> Rs2Inventory.itemQuantity(notedId) == 0, 2_000);

				int toWithdraw = Math.min(notesInInventory, needed);
				Microbot.status = "Withdrawing " + ir.getName() + " x" + toWithdraw;
				Rs2Bank.withdrawX(unnotedId, toWithdraw);
				if (!sleepUntil(() -> hasItemRequirementOnPlayer(ir), 2_000)) {
					Microbot.log("Quest helper: un-note withdrawal for " + ir.getName() + " did not land in time",
							Level.WARN);
				}
				break;
			}
		}

		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		return true;
	}

	private boolean hasMatchingGrandExchangeOffer(ItemRequirement itemRequirement) {
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		if (offers == null) {
			return false;
		}

		Set<Integer> ids = new HashSet<>();
		for (Integer id : itemRequirement.getAllIds()) {
			if (id != null && id > 0) {
				ids.add(id);
			}
		}

		for (GrandExchangeOffer offer : offers) {
			if (offer == null) {
				continue;
			}
			GrandExchangeOfferState state = offer.getState();
			if ((state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT)
					&& ids.contains(offer.getItemId())) {
				return true;
			}
		}

		return false;
	}

	private void stopQuesterWithReason(String reason) {
		stopQuesterWithReason(reason, Level.ERROR);
	}

	private void stopQuesterWithReason(String reason, Level level) {
		if (QuestPreflight.isInProgress()) {
			QuestPreflight.setStatus(QuestPreflight.FAILED_PREFIX + reason);
			preflightFinished = true;
		}
		Microbot.status = reason;
		Microbot.log("Quest helper stopped: " + reason, level);
		if (Microbot.getConfigManager() != null) {
			Microbot.getConfigManager().setConfiguration(
					QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", false);
		}
	}

	private boolean hasItemRequirementOnPlayer(ItemRequirement itemRequirement) {
		if (itemRequirement.mustBeEquipped()) {
			return itemRequirement.checkContainers(QuestContainerManager.getEquippedData());
		}

		return itemRequirement.checkContainers(
				QuestContainerManager.getEquippedData(),
				QuestContainerManager.getInventoryData());
	}

	private boolean attemptToAcquireRequirementItem(DetailedQuestStep questStep, ItemRequirement itemRequirement) {
		notifyMissingRequirement(itemRequirement);

		WorldPoint worldPoint = questStep.getDefinedPoint() != null ? questStep.getDefinedPoint().getWorldPoint() : null;
		int targetItemId = itemRequirement.getAllIds().stream().findFirst().orElse(itemRequirement.getId());

		if (worldPoint != null) {
			if ((Rs2Walker.canReach(worldPoint) && worldPoint.distanceTo(Rs2Player.getWorldLocation()) < 2)
					|| worldPoint.toWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Rs2Player.getWorldLocation().toWorldArea())
					&& Rs2Camera.isTileOnScreen(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint))) {
				lootGroundItem(targetItemId, 10);
			} else {
				Rs2Walker.walkTo(worldPoint, 2);
			}
		} else {
			lootGroundItem(targetItemId, 20);
		}

		return true;
	}

	private void notifyMissingRequirement(ItemRequirement itemRequirement) {
		int key = itemRequirement.getAllIds().stream().findFirst().orElse(itemRequirement.getId());
		long now = System.currentTimeMillis();
		Long lastNotified = lastMissingRequirementNotice.get(key);

		if (lastNotified != null && now - lastNotified < MISSING_REQUIREMENT_NOTIFY_INTERVAL_MS) {
			return;
		}

		lastMissingRequirementNotice.put(key, now);

		String itemName = itemRequirement.getName() != null && !itemRequirement.getName().isEmpty()
				? itemRequirement.getName()
				: "Item " + key;
		int quantity = Math.max(itemRequirement.getQuantity(), 1);

		Microbot.status = "Missing: " + itemName;
		Microbot.log(String.format("Quest helper missing required item: %s x%d", itemName, quantity), Level.WARN);
	}

	private boolean lootGroundItem(int itemId, int radius) {
		Rs2TileItemModel item = new Rs2TileItemQueryable()
				.withId(itemId)
				.within(radius)
				.nearest();

		if (item == null) {
			return false;
		}

		return item.click("");
	}

	@Override
	public void shutdown() {
		if (combatSupportFuture != null) {
			combatSupportFuture.cancel(true);
			combatSupportFuture = null;
		}
		combat.reset();
		super.shutdown();
		multiNpcTarget.release();
		reset();
	}

    public static void reset() {
        itemsMissing = new ArrayList<>();
        itemRequirements = new ArrayList<>();
        grandExchangeItems = new ArrayList<>();
        lastMissingRequirementNotice.clear();
        valeTotemsPromptInFlight.set(false);
        valeTotemsSessionWoodType = null;
        obtainItemsPromptInFlight.set(false);
        obtainItemsSessionChoice = null;
    }

    public boolean applyStep(QuestStep step) {
        if (step == null) return false;

        if (step instanceof ObjectStep) {
            return applyObjectStep((ObjectStep) step);
        } else if (step instanceof NpcStep) {
            return applyNpcStep((NpcStep) step);
        } else if (step instanceof WidgetStep) {
            return applyWidgetStep((WidgetStep) step);
        } else if (step instanceof DigStep) {
            return applyDigStep((DigStep) step);
        } else if (step instanceof PuzzleStep) {
            return applyPuzzleStep((PuzzleStep) step);
        } else if (step instanceof DetailedQuestStep) {
            return applyDetailedQuestStep((DetailedQuestStep) step);
        }
        return true;
    }

    public boolean applyNpcStep(NpcStep step) {
        boolean killStep = isKillNpcStep(step);
        if (killStep && lootKillDrop(step)) {
            return true;
        }
        if (killStep && fightOffAggressor(step)) {
            return true;
        }

        List<Rs2NpcModel> npcs = step.getNpcs().stream()
                .map(Rs2NpcModel::new)
                .collect(Collectors.toList());
        Rs2NpcModel npc = npcs.stream().findFirst().orElse(null);
        if (killStep) {
            Rs2NpcModel preferred = pickKillTarget(step, npcs);
            if (preferred != null) {
                npc = preferred;
            }
            if (npc != null && isOutsideKillArea(step, npc)) {
                // Do not follow the target through doorways: go back to the step's point and wait for it.
                WorldPoint home = step.getDefinedPoint().getWorldPoint();
                if (home.distanceTo(Rs2Player.getWorldLocation()) > 3) {
                    Rs2Walker.walkTo(home, 2);
                }
                return true;
            }
        }

        if (step.isAllowMultipleHighlights()) {
            npc = multiNpcTarget.select(step, npcs, Rs2NpcModel::getIndex, npcsHandled,
                    Comparator.comparing(x -> Rs2Player.getWorldLocation().distanceTo(x.getWorldLocation())),
                    System.currentTimeMillis());
        }

        // The step tracked nothing (wrong/cutscene-only id, NPC not yet scanned): look the NPC up by name
        // near the step's point instead of silently standing there.
        if (npc == null && npcs.isEmpty()) {
            npc = findNpcByStepName(step);
        }

        // canReach() pathfinds through closed doors (the walker opens them en route), so canReach==true
        // doesn't mean a direct click will succeed. Require line-of-sight too, or we walk instead.
        if (npc != null && npc.getLocalLocation() != null && Rs2Camera.isTileOnScreen(npc.getLocalLocation())
                && (Microbot.getClient().isInInstancedRegion() || (Rs2Walker.canReach(npc.getWorldLocation()) && npc.hasLineOfSight()))) {
            Rs2Walker.clearWalkingRoute("quest-helper:npc-step-visible-interact");

            if (step.getText().stream().anyMatch(x -> x.toLowerCase().contains("kill"))) {
                if (!Rs2Combat.inCombat()) {
                    npc.click("Attack");
                }
                return true;
            }

            if (step instanceof NpcEmoteStep) {
                var emoteStep = (NpcEmoteStep) step;

                for (Widget emoteWidget : Rs2Widget.getWidget(ComponentID.EMOTES_EMOTE_CONTAINER).getDynamicChildren()) {
                    if (emoteWidget.getSpriteId() == emoteStep.getEmote().getSpriteId()) {
                        var id = emoteWidget.getOriginalX() / 42 + ((emoteWidget.getOriginalY() - 6) / 49) * 4;

                        Microbot.doInvoke(new NewMenuEntry()
                                        .option("Perform")
                                        .target(emoteWidget.getText())
                                        .identifier(1)
                                        .type(MenuAction.CC_OP)
                                        .param0(id)
                                        .param1(ComponentID.EMOTES_EMOTE_CONTAINER)
                                , new Rectangle(0, 0, 1, 1));

                        Rs2Player.waitForAnimation();

                        if (Rs2Dialogue.isInDialogue())
                            return false;
                    }
                }
            }

            if (step.getIconItemID() == -1 && hasNoActions(npc)) {
                if (npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) > 3) {
                    Rs2Walker.walkTo(npc.getWorldLocation(), 2);
                }
                return true;
            }

            var itemId = step.getIconItemID();
            if (itemId != -1) {
                Rs2Inventory.use(itemId);
                npc.click("");
            } else {
                npc.click(chooseCorrectNPCOption(step, npc));
                if (step.isAllowMultipleHighlights()) {
                    multiNpcTarget.commit(step, npc.getIndex(), System.currentTimeMillis());
                }
            }

            if (step.isAllowMultipleHighlights()) {
                npcsHandled.add(npc.getIndex());
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (npc != null && npc.getLocalLocation() != null && !Rs2Camera.isTileOnScreen(npc.getLocalLocation())) {
            Rs2Walker.walkTo(npc.getWorldLocation(), 2);
        } else if (npc != null && (!npc.hasLineOfSight() || !Rs2Walker.canReach(npc.getWorldLocation()))) {
            Rs2Walker.walkTo(npc.getWorldLocation(), 2);
        } else {
            if (step.getDefinedPoint().getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) > 3) {
                Rs2Walker.walkTo(step.getDefinedPoint().getWorldPoint(), 2);
                return false;
            }
        }
        return true;
    }


    private static final int KILL_AREA_RADIUS = 12;
    private static final Pattern STEP_LEVEL = Pattern.compile("level\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static Set<Integer> stepNpcIds(NpcStep step) {
        Set<Integer> ids = new HashSet<>();
        ids.add(step.getNpcID());
        ids.addAll(step.getAlternateNpcIDs());
        return ids;
    }

    private static boolean isOutsideKillArea(NpcStep step, Rs2NpcModel npc) {
        if (step.getDefinedPoint() == null || step.getDefinedPoint().getWorldPoint() == null) {
            return false;
        }
        WorldPoint home = step.getDefinedPoint().getWorldPoint();
        WorldPoint at = npc.getWorldLocation();
        return at != null && home.getPlane() == at.getPlane() && home.distanceTo(at) > KILL_AREA_RADIUS;
    }

    /**
     * While a non-target NPC is attacking the player (e.g. the weaker aggressive monks in Priest in Peril), let that
     * fight finish instead of clicking the target, which just spams and drags the player out of the step's area.
     * Returns true when this tick was spent on that fight.
     */
    private boolean fightOffAggressor(NpcStep step) {
        if (Rs2Combat.inCombat()) {
            return true;
        }
        Set<Integer> ids = stepNpcIds(step);
        Rs2NpcModel attacker = Microbot.getRs2NpcCache().query()
                .where(n -> n.isInteractingWithPlayer() && n.getHealthRatio() != 0 && !ids.contains(n.getId()))
                .within(6)
                .nearestOnClientThread();
        if (attacker == null) {
            return false;
        }
        attacker.click("Attack");
        return true;
    }

    /**
     * Picks the live NPC for a kill step among the step's tracked NPCs plus same-id NPCs near its point,
     * preferring the combat level quoted in the step text "(level N)", else the highest level.
     */
    private Rs2NpcModel pickKillTarget(NpcStep step, List<Rs2NpcModel> tracked) {
        Set<Integer> ids = stepNpcIds(step);
        Map<Integer, Rs2NpcModel> candidates = new java.util.LinkedHashMap<>();
        for (Rs2NpcModel n : tracked) {
            if (n.getHealthRatio() != 0) {
                candidates.put(n.getIndex(), n);
            }
        }
        if (step.getDefinedPoint() != null && step.getDefinedPoint().getWorldPoint() != null) {
            int[] idArray = ids.stream().mapToInt(Integer::intValue).toArray();
            for (Rs2NpcModel n : Microbot.getRs2NpcCache().query()
                    .withIds(idArray)
                    .within(step.getDefinedPoint().getWorldPoint(), KILL_AREA_RADIUS)
                    .toListOnClientThread()) {
                if (n.getHealthRatio() != 0) {
                    candidates.putIfAbsent(n.getIndex(), n);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int wanted = -1;
        for (String line : step.getText()) {
            if (line == null) continue;
            java.util.regex.Matcher m = STEP_LEVEL.matcher(line);
            if (m.find()) {
                wanted = Integer.parseInt(m.group(1));
                break;
            }
        }
        final int wantedLevel = wanted;
        WorldPoint me = Rs2Player.getWorldLocation();
        return candidates.values().stream()
                .min(Comparator
                        .comparingInt((Rs2NpcModel n) -> wantedLevel > 0 && n.getCombatLevel() == wantedLevel ? 0 : 1)
                        .thenComparingInt((Rs2NpcModel n) -> -n.getCombatLevel())
                        .thenComparingInt(n -> n.getWorldLocation() == null ? Integer.MAX_VALUE : me.distanceTo(n.getWorldLocation())))
                .orElse(null);
    }

    private static final int FALLBACK_NPC_RADIUS = 10;
    private static final long NO_NPC_LOG_INTERVAL_MS = 30_000;
    private final Map<Integer, String> npcNameById = new HashMap<>();
    private QuestStep lastNoNpcLoggedStep = null;
    private long lastNoNpcLoggedAt = 0;

    /** Name of the NPC an {@link NpcStep} targets: the explicit step name, else the primary id's composition name. */
    private String resolveStepNpcName(NpcStep step) {
        if (step.getNpcName() != null && !step.getNpcName().isEmpty()) {
            return step.getNpcName();
        }
        int id = step.getNpcID();
        String cached = npcNameById.get(id);
        if (cached != null) {
            return cached;
        }
        String name = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPCComposition composition = Microbot.getClient().getNpcDefinition(id);
            return composition == null ? null : composition.getName();
        }).orElse(null);
        if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name)) {
            npcNameById.put(id, name);
            return name;
        }
        return null;
    }

    /** Finds the step's NPC by name within FALLBACK_NPC_RADIUS tiles of its defined point; logs (rate-limited) when absent. */
    private Rs2NpcModel findNpcByStepName(NpcStep step) {
        if (step.getDefinedPoint() == null || step.getDefinedPoint().getWorldPoint() == null) {
            return null;
        }
        WorldPoint point = step.getDefinedPoint().getWorldPoint();
        String name = resolveStepNpcName(step);
        Rs2NpcModel found = null;
        if (name != null) {
            found = Microbot.getRs2NpcCache().query()
                    .withName(name)
                    .nearestOnClientThread(point, FALLBACK_NPC_RADIUS);
        }
        if (found == null) {
            long now = System.currentTimeMillis();
            if (lastNoNpcLoggedStep != step || now - lastNoNpcLoggedAt > NO_NPC_LOG_INTERVAL_MS) {
                lastNoNpcLoggedStep = step;
                lastNoNpcLoggedAt = now;
                Microbot.log("Quest helper: no NPC found for step " + String.join(" ", step.getText()), Level.WARN);
            }
        }
        return found;
    }

    /**
     * Picks the dialogue option matching {@code wanted}: exact-suffix match first, then contains-ignore-case,
     * both after stripping colour tags and any leading "[n]" marker. Presses the option's own key binding and
     * falls back to Rs2Dialogue / a widget click when that binding cannot be read.
     *
     * @return true once an input was sent for a matching option
     */
    private boolean selectDialogChoice(Widget[] dialogChoices, String wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return false;
        }
        String target = normalizeDialogText(wanted);
        Widget match = null;
        for (Widget dialogChoice : dialogChoices) {
            String text = dialogChoice.getText();
            if (text == null) continue;
            if (text.endsWith(wanted) || normalizeDialogText(text).equalsIgnoreCase(target)) {
                match = dialogChoice;
                break;
            }
        }
        if (match == null) {
            for (Widget dialogChoice : dialogChoices) {
                String text = dialogChoice.getText();
                if (text != null && normalizeDialogText(text).toLowerCase().contains(target.toLowerCase())) {
                    match = dialogChoice;
                    break;
                }
            }
        }
        if (match == null) {
            return false;
        }
        Object[] keyListener = match.getOnKeyListener();
        if (keyListener != null && keyListener.length > 7 && keyListener[7] != null && !keyListener[7].toString().isEmpty()) {
            Rs2Keyboard.keyPress(keyListener[7].toString().charAt(0));
            return true;
        }
        return Rs2Dialogue.clickOption(target) || Rs2Widget.clickWidget(match);
    }

    private static String normalizeDialogText(String text) {
        return Text.removeTags(text).replaceFirst("^\\[\\d+\\]\\s*", "").trim();
    }

    /** Some quest NPCs (e.g. escorted guards) have no menu actions at all, so there is nothing to click. */
    private boolean hasNoActions(Rs2NpcModel npc) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (composition == null || composition.getActions() == null) {
                return false;
            }
            return Arrays.stream(composition.getActions()).noneMatch(x -> x != null && !x.isEmpty());
        }).orElse(false);
    }

    public boolean applyObjectStep(ObjectStep step) {
        Rs2TileObjectModel object = step.getObjects().stream()
                .filter(Objects::nonNull)
                .map(Rs2TileObjectModel::new)
                .findFirst().orElse(null);
        var itemId = step.getIconItemID();

        List<Rs2TileObjectModel> stepObjects = step.getObjects().stream()
                .filter(Objects::nonNull)
                .map(Rs2TileObjectModel::new)
                .collect(Collectors.toList());

        if (stepObjects.size() > 1) {
            object = stepObjects.stream()
                    .filter(x -> !objectsHandeled.contains(x.getHash()))
                    .findFirst()
                    .orElseGet(() -> stepObjects.stream()
                            .min(Comparator.comparing(x -> Rs2Player.getWorldLocation().distanceTo(x.getWorldLocation())))
                            .orElse(null));
        }

        // Stale object id (game update): nothing tracked near the step's point, so look the object up by name.
        boolean fallbackUsed = false;
        if (needsObjectNameFallback(step, stepObjects)) {
            Rs2TileObjectModel byName = findObjectByStepName(step);
            if (byName != null) {
                object = byName;
                fallbackUsed = true;
            }
        }

        if (object != null && unreachableTarget) {
            var tileObjects = new Rs2TileObjectQueryable()
                    .where(x -> x.getTileObjectType() == TileObjectType.WALL)
                    .toList();

            for (var tile : Rs2Tile.getWalkableTilesAroundTile(object.getWorldLocation(), unreachableTargetCheckDist)) {
                if (tileObjects.stream().noneMatch(x -> x.getWorldLocation().equals(tile))) {
                    if (!Rs2Walker.walkTo(tile) && !Rs2PathApi.getActiveRouteStatus().isPresent())
                        return false;

                    sleepUntil(() -> !Rs2PathApi.getActiveRouteStatus().isCalculating());
                    if (!Rs2PathApi.getActiveRouteStatus().isCalculating()) {
                        unreachableTarget = false;
                        unreachableTargetCheckDist = 1;
                    }
                    return false;
                }
            }

            unreachableTargetCheckDist++;
            return false;
        }

        // Walk first when more than one tile away AND no reachable tile beside the target shares an open edge
        // with it. The object's own tile is never the question (a boat on water or a door in a wall is never
        // walkable), and a closed door or a wall between us still makes it unreachable, so the walker gets us
        // to the right side before we interact.
        if (step.getDefinedPoint().getWorldPoint() != null && Rs2Player.getWorldLocation().distanceTo2D(step.getDefinedPoint().getWorldPoint()) > 1
                && (object == null || !isInteractableFromReachableTile(step, object))) {
            WorldPoint targetTile = null;
            WorldPoint stepLocation = object == null ? step.getDefinedPoint().getWorldPoint() : object.getWorldLocation();
            int radius = 0;
            while (targetTile == null) {
                if (mainScheduledFuture.isCancelled())
                    break;
                radius++;
                Rs2TileObjectModel finalObject = object;
                targetTile = Rs2Tile.getWalkableTilesAroundTile(stepLocation, radius)
                        .stream().filter(x -> hasLineOfSightFrom(x, finalObject))
                        .sorted(Comparator.comparing(x -> x.distanceTo(Rs2Player.getWorldLocation()))).findFirst().orElse(null);

                if (radius > 10 && targetTile == null)
                    targetTile = stepLocation;
            }

            WalkerState walkState = Rs2Walker.walkWithState(targetTile, 3);

            var activeRoute = Rs2PathApi.getActiveRouteStatus();
            if (activeRoute.isPresent()) {
                var endpoint = activeRoute.getEndpoint();
                if (endpoint.isPresent()
                        && endpoint.get().distanceTo(step.getDefinedPoint().getWorldPoint()) <= 1)
                    return false;
            } else if (walkState == WalkerState.ARRIVED || mainScheduledFuture.isCancelled() || !hasLineOfSightToObject(object))
                return false;
        }

        if (hasLineOfSightToObject(object) || object != null && (Rs2Camera.isTileOnScreen(object.getLocalLocation()) || object.getCanvasLocation() != null)) {
            Rs2Walker.clearWalkingRoute("quest-helper:object-step-interact");

            if (itemId == -1)
                object.click(fallbackUsed ? chooseFallbackObjectOption(step, object) : chooseCorrectObjectOption(step, object));
            else {
                Rs2Inventory.use(itemId);
                object.click("");
            }

            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating());
            sleep(100);
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());
            objectsHandeled.add(object.getHash());
        } else if (object != null) {
            Rs2Walker.walkTo(object.getWorldLocation(), 1);
            return false;
        }

        return true;
    }

    private static final int FALLBACK_OBJECT_RADIUS = 4;
    private static final int TRACKED_OBJECT_RADIUS = 15;
    private static final long NO_OBJECT_LOG_INTERVAL_MS = 30_000;
    private final Map<Integer, String> objectNameById = new HashMap<>();
    private QuestStep lastObjectFallbackLoggedStep = null;
    private long lastObjectFallbackLoggedAt = 0;
    private QuestStep lastNoObjectLoggedStep = null;
    private long lastNoObjectLoggedAt = 0;

    private static boolean needsObjectNameFallback(ObjectStep step, List<Rs2TileObjectModel> tracked) {
        if (step.getDefinedPoint() == null || step.getDefinedPoint().getWorldPoint() == null) {
            return false;
        }
        WorldPoint point = step.getDefinedPoint().getWorldPoint();
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null || me.getPlane() != point.getPlane()) {
            return false;
        }
        return tracked.stream().noneMatch(o -> o.getWorldLocation() != null
                && o.getWorldLocation().getPlane() == point.getPlane()
                && o.getWorldLocation().distanceTo(point) <= TRACKED_OBJECT_RADIUS);
    }

    private List<String> resolveStepObjectNames(ObjectStep step) {
        List<String> names = new ArrayList<>();
        for (int id : step.getAllObjectIds()) {
            String name = objectNameById.get(id);
            if (name == null) {
                name = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    ObjectComposition comp = Microbot.getClient().getObjectDefinition(id);
                    if (comp != null && comp.getImpostorIds() != null) {
                        ObjectComposition imp = comp.getImpostor();
                        if (imp != null) {
                            comp = imp;
                        }
                    }
                    return comp == null ? null : comp.getName();
                }).orElse(null);
                if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name)) {
                    objectNameById.put(id, name);
                } else {
                    name = null;
                }
            }
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Finds the step's object by name within FALLBACK_OBJECT_RADIUS of its point; logs (rate-limited). */
    private Rs2TileObjectModel findObjectByStepName(ObjectStep step) {
        WorldPoint point = step.getDefinedPoint().getWorldPoint();
        String stepText = String.join(" ", step.getText());
        String wantedAction = wantedObjectAction(step);
        for (String name : resolveStepObjectNames(step)) {
            List<Rs2TileObjectModel> candidates = Microbot.getRs2TileObjectCache().query()
                    .withName(name)
                    .within(point, FALLBACK_OBJECT_RADIUS)
                    .toListOnClientThread();
            candidates = candidates.stream()
                    .filter(o -> o.getWorldLocation() != null && o.getWorldLocation().getPlane() == point.getPlane())
                    .sorted(Comparator.comparingInt((Rs2TileObjectModel o) ->
                                    wantedAction != null && o.hasAction(wantedAction) ? 0 : 1)
                            .thenComparingInt(o -> o.getWorldLocation().distanceTo(point)))
                    .collect(Collectors.toList());
            if (!candidates.isEmpty()) {
                Rs2TileObjectModel found = candidates.get(0);
                long now = System.currentTimeMillis();
                if (lastObjectFallbackLoggedStep != step || now - lastObjectFallbackLoggedAt > NO_OBJECT_LOG_INTERVAL_MS) {
                    lastObjectFallbackLoggedStep = step;
                    lastObjectFallbackLoggedAt = now;
                    Microbot.log("Quest helper: object id " + step.getAllObjectIds() + " not found for step '" + stepText
                            + "', using '" + name + "' id " + found.getId(), Level.WARN);
                }
                return found;
            }
        }
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me != null && me.distanceTo(point) <= 1) {
            long now = System.currentTimeMillis();
            if (lastNoObjectLoggedStep != step || now - lastNoObjectLoggedAt > NO_OBJECT_LOG_INTERVAL_MS) {
                lastNoObjectLoggedStep = step;
                lastNoObjectLoggedAt = now;
                Microbot.log("Quest helper: no object found for step " + stepText, Level.WARN);
            }
        }
        return null;
    }

    /** Action implied by the step text (climb up/down, open), or null when the text says nothing specific. */
    private static String wantedObjectAction(ObjectStep step) {
        String text = step.getText() == null ? "" : String.join(" ", step.getText()).toLowerCase();
        if (text.contains("climb up") || text.contains("upstairs") || text.contains("go up")
                || text.contains("back up")) {
            return "Climb-up";
        }
        if (text.contains("climb down") || text.contains("downstairs") || text.contains("go down")) {
            return "Climb-down";
        }
        if (text.contains("open")) {
            return "Open";
        }
        return null;
    }

    private String chooseFallbackObjectOption(ObjectStep step, Rs2TileObjectModel object) {
        String wanted = wantedObjectAction(step);
        if (wanted != null && object.hasAction(wanted)) {
            return wanted;
        }
        return chooseCorrectObjectOption(step, object);
    }

    private static boolean isInteractableFromReachableTile(ObjectStep step, Rs2TileObjectModel object) {
        if (object.getTileObjectType() != TileObjectType.GAME) {
            return object.isReachable();
        }
        return step.getObjects().stream()
                .filter(x -> x instanceof GameObject && x.getHash() == object.getHash())
                .map(GameObject.class::cast)
                .findFirst()
                .map(Rs2Tile::isGameObjectInteractableFromReachableTile)
                .orElse(false);
    }

    private boolean applyDigStep(DigStep step) {
        if (!Rs2Walker.walkTo(step.getDefinedPoint().getWorldPoint()))
            return false;
        else if (!Rs2Player.getWorldLocation().equals(step.getDefinedPoint().getWorldPoint()))
            Rs2Walker.walkFastCanvas(step.getDefinedPoint().getWorldPoint());
        else {
            Rs2Inventory.interact(ItemID.SPADE, "Dig");
            return true;
        }

        return false;
    }

    private boolean applyPuzzleStep(PuzzleStep step) {
        if (!step.getHighlightedButtons().isEmpty()) {
            var widgetDetails = step.getHighlightedButtons().stream().filter(x -> Rs2Widget.isWidgetVisible(x.groupID, x.childID)).findFirst().orElse(null);
            if (widgetDetails != null) {
                Rs2Widget.clickWidget(widgetDetails.groupID, widgetDetails.childID);
                return true;
            }
        }

        return false;
    }

    private String chooseCorrectObjectOption(QuestStep step, Rs2TileObjectModel object) {
        ObjectComposition objComp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getObjectDefinition(object.getId())).orElse(null);

        if (objComp == null)
            return "";

        String[] actions;
        if (objComp.getImpostorIds() != null) {
            actions = objComp.getImpostor().getActions();
        } else {
            actions = objComp.getActions();
        }

        for (var action : actions) {
            if (action != null && step.getText().stream().anyMatch(x -> x.toLowerCase().contains(action.toLowerCase())))
                return action;
        }

        // Fallback: first non-empty action (the object's default left-click).
        for (var action : actions) {
            if (action != null && !action.isEmpty())
                return action;
        }

        return "";
    }

    private String chooseCorrectNPCOption(QuestStep step, Rs2NpcModel npc) {
        var npcComp = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getNpcDefinition(npc.getId()))
                .orElse(null);

        if (npcComp == null)
            return "Talk-to";

        var actions = npcComp.getActions();

        for (var action : actions) {
            if (action != null && step.getText().stream().anyMatch(x -> x.toLowerCase().contains(action.toLowerCase())))
                return action;
        }

        // Fallback: prefer Talk-to if the NPC has it, otherwise the first non-empty action.
        String fallback = null;
        for (var action : actions) {
            if (action == null || action.isEmpty()) continue;
            if ("Talk-to".equalsIgnoreCase(action)) return action;
            if (fallback == null) fallback = action;
        }
        return fallback != null ? fallback : "Talk-to";
    }

	private String chooseCorrectItemOption(QuestStep step, int itemId) {
		return chooseItemAction(step.getText(), Rs2Inventory.get(itemId).getInventoryActions());
	}

	static boolean trimToGrandExchangeSlots(List<?> toBuy, int freeSlots) {
		if (toBuy.size() <= freeSlots) {
			return false;
		}
		toBuy.subList(Math.max(0, freeSlots), toBuy.size()).clear();
		return true;
	}

	static String chooseItemAction(List<String> stepText, String[] actions) {
		if (stepText != null && actions != null) {
			for (var action : actions) {
				if (action == null || action.isEmpty()) continue;
				Pattern word = Pattern.compile("(?<![a-z])" + Pattern.quote(action.toLowerCase()) + "(?![a-z])");
				if (stepText.stream().anyMatch(x -> x != null && word.matcher(x.toLowerCase()).find()))
					return action;
			}
		}

		return "use";
	}

	private boolean hasLineOfSightToObject(Rs2TileObjectModel object) {
		if (object == null || object.getWorldLocation() == null || Microbot.getClient().getLocalPlayer() == null) {
			return false;
		}

		WorldArea objectArea = object.getWorldLocation().toWorldArea();
		WorldArea playerArea = Rs2Player.getWorldLocation().toWorldArea();

		return Microbot.getClient().getTopLevelWorldView() != null
				&& playerArea.hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), objectArea);
	}

	private boolean hasLineOfSightFrom(WorldPoint point, Rs2TileObjectModel object) {
		if (point == null || object == null || object.getWorldLocation() == null) {
			return false;
		}

		WorldArea fromArea = point.toWorldArea();
		WorldArea targetArea = object.getWorldLocation().toWorldArea();

		return Microbot.getClient().getTopLevelWorldView() != null
				&& fromArea.hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), targetArea);
	}

    private boolean applyDetailedQuestStep(DetailedQuestStep conditionalStep) {
        if (conditionalStep instanceof NpcStep) return false;
        if (conditionalStep instanceof MonumentKeyStep) return applyMonumentKeyStep((MonumentKeyStep) conditionalStep);

        WorldPoint definedPoint = conditionalStep.getDefinedPoint() == null ? null : conditionalStep.getDefinedPoint().getWorldPoint();

        if (conditionalStep.getIconItemID() != -1
                && definedPoint != null
                && !definedPoint.toWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Rs2Player.getWorldLocation())) {
            if (Rs2Tile.areSurroundingTilesWalkable(definedPoint, 1, 1)) {
                WorldPoint nearestUnreachableWalkableTile = Rs2Tile.getNearestWalkableTileWithLineOfSight(definedPoint);
                if (nearestUnreachableWalkableTile != null) {
                    return Rs2Walker.walkTo(nearestUnreachableWalkableTile, 0);
                }
            }
        }

        boolean usingItems = false;
        for (Requirement requirement : conditionalStep.getRequirements()) {
            if (requirement instanceof ItemRequirement) {
                ItemRequirement itemRequirement = (ItemRequirement) requirement;

				if (itemRequirement.shouldHighlightInInventory(Microbot.getClient())
						&& Rs2Inventory.contains(itemRequirement.getAllIds().stream().mapToInt(i -> i).toArray())) {
					var itemId = itemRequirement.getAllIds().stream().filter(Rs2Inventory::contains).findFirst().orElse(-1);
					Rs2Inventory.interact(itemId, chooseCorrectItemOption(conditionalStep, itemId));
					sleep(100, 200);
					usingItems = true;
					continue;
				}

				if (!hasItemRequirementOnPlayer(itemRequirement)) {
					return attemptToAcquireRequirementItem(conditionalStep, itemRequirement);
				}
			}
		}

        if (!usingItems && definedPoint != null && !Rs2Walker.walkTo(definedPoint))
            return true;

		if (conditionalStep.getIconItemID() != -1 && definedPoint != null
				&& definedPoint.toWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Rs2Player.getWorldLocation())) {
			if (conditionalStep.getQuestHelper().getQuest() == QuestHelperQuest.ZOGRE_FLESH_EATERS) {
				if (conditionalStep.getIconItemID() == 4836) { // strange potion
					lootGroundItem(ItemID.CUP_OF_TEA_4838, 20);
				}
			}
		}

		return usingItems;
	}

    private boolean applyMonumentKeyStep(MonumentKeyStep step) {
        WorldPoint centre = step.getDefinedPoint().getWorldPoint();
        if (Rs2Player.getWorldLocation().distanceTo(centre) > 6) {
            Rs2Walker.walkTo(centre, 3);
            return false;
        }

        if (Rs2Inventory.contains(net.runelite.api.gameval.ItemID.PIPKEY_IRON)) {
            monumentsTried.clear();
            return true;
        }

        Rs2TileObjectModel monument = new Rs2TileObjectQueryable()
                .withName("Monument")
                .within(centre, 12)
                .where(x -> !monumentsTried.contains(x.getHash()))
                .nearestOnClientThread();
        if (monument == null) {
            monumentsTried.clear();
            return false;
        }

        Rs2Inventory.use(net.runelite.api.gameval.ItemID.PIPKEY_GOLD);
        monument.click("");
        monumentsTried.add(monument.getHash());
        sleepUntil(() -> Rs2Inventory.contains(net.runelite.api.gameval.ItemID.PIPKEY_IRON) || Rs2Dialogue.isInDialogue(), 5_000);
        return true;
    }

    private boolean applyWidgetStep(WidgetStep step) {
        var widgetDetails = step.getWidgetDetails().get(0);
        var widget = Microbot.getClient().getWidget(widgetDetails.groupID, widgetDetails.childID);
        if (widget == null) {
            return false;
        }

        if (widgetDetails.childChildID != -1) {
            var tmpWidget = widget.getChild(widgetDetails.childChildID);

            if (tmpWidget != null)
                widget = tmpWidget;
        }

        return Rs2Widget.clickWidget(widget.getId());
    }

    protected QuestHelperPlugin getQuestHelperPlugin() {
        return (QuestHelperPlugin) Microbot.getPluginManager().getPlugins().stream().filter(x -> x instanceof QuestHelperPlugin).findFirst().orElse(null);
    }

    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getMessage().equalsIgnoreCase("I can't reach that!"))
            unreachableTarget = true;
    }
}
