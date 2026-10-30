package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.questhelper.tools.QuestWidgets;
import net.runelite.client.util.Text;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatDetector;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestFightSupport;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions;
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
import net.runelite.client.plugins.microbot.util.dialogues.DialogueChoiceProvider;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.antiban.HumanMouseSpeed;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectOcclusion;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.event.Level;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.util.coords.InstanceCoordinates;

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
	private QuestPurchasePlan preflightPlan;
	private boolean purchaseApproved;
	private Boolean purchaseConfirmOverride;
	/** One buy at a time: a second script loop (after a plugin restart) must never run the Grand Exchange too. */
	private static final TickGuard BUY_GUARD = new TickGuard();
	private static final TickGuard TICK_GUARD = new TickGuard();
	private volatile boolean stopInterruptSent;
	/** Whether the last main-loop tick had automation on; a window is only dismissed on the running-to-stopped edge. */
	private boolean wasRunning;
	private int windowNonce = Integer.MIN_VALUE;
	private String windowQuestKey;
	private boolean windowDone = true;
	private final java.util.concurrent.atomic.AtomicReference<Object> windowAnswer = new java.util.concurrent.atomic.AtomicReference<>();

	private enum PurchaseGate { PROCEED, WAIT, STOPPED, SKIPPED }

    private static final AtomicBoolean valeTotemsPromptInFlight = new AtomicBoolean(false);
    private static volatile QuestHelperConfig.ValeTotemsWoodType valeTotemsSessionWoodType;

    private static final AtomicBoolean obtainItemsPromptInFlight = new AtomicBoolean(false);
    private static volatile Boolean obtainItemsSessionChoice;

    private final QuestCombatHandler combat = new QuestCombatHandler(
            () -> this.config, () -> mainScheduledFuture, this::stopQuesterWithReason);
    private ScheduledFuture<?> combatSupportFuture;
    private ScheduledFuture<?> supervisorFuture;
    private final QuestStuckWatchdog stuckWatchdog = new QuestStuckWatchdog(QuestStuckWatchdog.DEFAULT_STAGE_INTERVAL_MS);
    private static volatile String stuckStage = "NONE";
    private static volatile long stuckForMs;
    private volatile boolean automationWasOn;
    private QuestStep seenLinesStep;
    private final Set<String> seenDialogueLines = new HashSet<>();
    private volatile long pausedAtMs;
    private volatile WorldPoint lastWalkTarget;

    /** Watchdog stage for GET /quest-helper/status ("NONE" when making progress). */
    public static String getStuckStage()
    {
        return QuestWalkRule.unreachableTarget(Rs2Player.getWorldLocation()) != null ? "UNREACHABLE" : stuckStage;
    }

    public static long getStuckForMs() { return stuckForMs; }
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

    /** Last NPC/player dialogue line seen; used to gate choices that carry an expected previous line. */
    private volatile String lastDialogueLine = "";
    private long lastCombatAtMs;
    private long lastJournalSyncAtMs;

    /**
     * Whether a fight is really on: the player's target, or an NPC attacking the player, shows a health bar (it appears
     * on a hit and fades after a few ticks). Talking to a monster or standing in an aggressive area is not a fight.
     */
    private static boolean isFightingHostile() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            var me = client.getLocalPlayer();
            if (me == null) {
                return false;
            }
            net.runelite.api.Actor target = me.getInteracting();
            if (target instanceof net.runelite.api.NPC && target.getCombatLevel() > 0 && target.getHealthRatio() >= 0) {
                return true;
            }
            for (net.runelite.api.NPC npc : client.getTopLevelWorldView().npcs()) {
                if (npc.getInteracting() == me && npc.getCombatLevel() > 0 && npc.getHealthRatio() >= 0) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }
    /** The step a successful item-on-item combine was made on, whose consumed parts are no longer required. */
    private QuestStep combinedOnStep;
    private Set<Integer> combinedIds = new HashSet<>();
    private QuestStep menuStep;
    /** Conversation key (step text) the chosen options belong to; a new step starts a fresh conversation. */
    private String menuConversationKey;
    /** Menu options the fallback chose during the current step, so it rotates to the ones not yet asked. */
    private final Set<String> chosenFallbackOptions = new HashSet<>();
    private boolean menuWasVisible;
    private final Map<String, Integer> menuAppearances = new HashMap<>();
    private String menuLoggedKey;
    private static final int MENU_REPEATS_BEFORE_NEXT_OPTION = 3;
    /** True when this script turned auto-retaliate off and the player had it on. */
    private volatile boolean autoRetaliateDisabledByUs;

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
        cancelLoops();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(onQuestThread(guardedTick(() -> {
            try {
                net.runelite.client.plugins.microbot.util.walker.WalkPathVariation.configure(
                        config.pathVariation() && !escortActive, config.pathVariationStrength().getLevel());
                QuestWalkRule.setActive(config.startStopQuestHelper() && !QuestPause.isPaused());
                boolean running = config.startStopQuestHelper();
                boolean justStopped = QuestRunEdge.justStopped(wasRunning, running);
                wasRunning = running;
                if (!running) {
                    net.runelite.client.plugins.microbot.util.walker.WalkPathVariation.configure(false, 2);
                    restoreAutoRetaliate();
                    combat.reset();
                    resetPreflightOnStop();
                    if (justStopped) {
                        // only on the edge: the planner is opened while automation is off and must stay open
                        net.runelite.client.plugins.microbot.questhelper.planner.QuestWindow.dismiss();
                    }
                    return;
                }
                if (QuestPause.isPaused()) {
                    Microbot.status = "Quest helper: PAUSED";
                    return;
                }
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (queueRunner.tick(System.currentTimeMillis())) return;
                if (getQuestHelperPlugin().getSelectedQuest() == null) return;

                if (handleShortBreak()) return;
                humanizePause();

                QuestStep questStep = activeStepOf(getQuestHelperPlugin().getSelectedQuest());

                publishDialogueChoices(questStep);
                // Dialogue is answered first and at ~100 ms, regardless of combat or animation state.
                answerDialogueFast(questStep);

                // Quest-start confirmation ("Start <quest>?" / Yes. / No.): always accept while a quest is selected,
                // whether it was opened via NPC, object or item. isInDialogue() reports it, but the step's own
                // choices never match it, so answer it here before anything re-interacts with the start NPC.
                if (Rs2Dialogue.hasQuestStartMenu()) {
                    Rs2Walker.clearWalkingRoute("quest-helper:quest-start-menu");
                    Rs2Dialogue.acceptQuestStartMenu();
                    return;
                }

                // Being hit while a non-kill step is active must not stall it: skip the settle wait and keep acting.
                boolean attackedOnNonKillStep = isAttackedOnNonKillStep(questStep);
                if (Rs2Player.isAnimating() && !attackedOnNonKillStep)
                    Rs2Player.waitForAnimation();

                if (Rs2Dialogue.isInDialogue()) {
                    String dialogueLine = Rs2Dialogue.getDialogueText();
                    if (dialogueLine != null && !dialogueLine.isEmpty()) {
                        lastDialogueLine = dialogueLine;
                    }
                }
                if (!Rs2Widget.isWidgetVisible(ComponentID.DIALOG_OPTION_OPTIONS)) {
                    menuWasVisible = false;
                }

                if (Rs2Dialogue.isInDialogue() && dialogueStartedStep == null)
                    dialogueStartedStep = questStep;

                if (questStep != null && Rs2Widget.isWidgetVisible(ComponentID.DIALOG_OPTION_OPTIONS)) {
                    var dialogOptions = Rs2Widget.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
                    var dialogChoices = dialogOptions.getDynamicChildren();

                    if (dialogChoices != null) {
                        for (var choice : questStep.getChoices().getChoices()) {
                            if (choice.getExpectedPreviousLine() != null
                                    && !QuestDialogueRules.previousLineMatches(choice.getExpectedPreviousLine(), lastDialogueLine))
                                continue;

                            if (choice.getExcludedStrings() != null && choice.getExcludedStrings().stream().anyMatch(Rs2Widget::hasWidget))
                                continue;

                            if (selectDialogChoice(dialogChoices, choice.getChoice())) {
                                return;
                            }
                        }
                    }
                }

                if (questStep != null && Rs2Shop.isOpen() && handleShopPurchase(questStep)) {
                    return;
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
                            // a quest-start question is answered before any fallback guesses
                            if (Rs2Dialogue.acceptQuestStartDialogue()) {
                                return;
                            }
                            if (pickFallbackMenuOption(questStep)) {
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

					if (questWindowGate(selectedQuest)) {
						return;
					}

					if (combat.beginStep(selectedQuest)) {
						return;
					}

					if (runPreflight(selectedQuest)) {
						return;
					}

					if (ensureFare(selectedQuest)) {
						return;
					}

					if (questStep instanceof DetailedQuestStep && handleRequirements((DetailedQuestStep) questStep)) {
						sleep(500, 1000);
						return;
					}

					if (questStep instanceof DetailedQuestStep) {
						DetailedQuestStep detailedStep = (DetailedQuestStep) questStep;
						if (hasUnmetItemRequirements(detailedStep)) {
							boolean obtain = shouldObtainMissingItems();
							if (config.obtainMissingItems() == QuestHelperConfig.ObtainMissingItemsOption.ASK
									&& obtainItemsSessionChoice == null) {
								// the consent prompt is open: do nothing until it is answered
								Microbot.status = "Quest helper: waiting for your answer on buying missing items";
								return;
							}
							if (obtain && handleMissingItemRequirements(detailedStep)) {
								return;
							}
							if (blockedByMissingEquipment(detailedStep)) {
								return;
							}
						}
					}

					if (handleCarryRules(selectedQuest, questStep)) {
						return;
					}

					if (combat.recoverHealth(questStep, slotsReservedForStep(questStep))) {
						return;
					}

					if (combat.prepareQuestSupplies(slotsReservedForStep(questStep))) {
						return;
					}

					if (questStep instanceof DetailedQuestStep && handleBossPrep((DetailedQuestStep) questStep)) {
						return;
					}

					if ((QuestCombatDetector.isKillStep(questStep) || QuestCombatDetector.hasFights(selectedQuest))
							&& combat.prepareForKill(questStep, slotsReservedForStep(questStep))) {
						return;
					}

					if (handleSwapKit()) {
						return;
					}

					if (QuestCombatDetector.isKillStep(questStep) && combat.positionForKill(questStep)) {
						return;
					}

					/**
					 * This portion is needed when using item on another item in your inventory.
					 * If we do not prioritize this, the script will think we are missing items
					 */
					if (manageAutoRetaliate(questStep)) {
						return;
					}

					if (questStep instanceof DetailedQuestStep && handleJournalSync(selectedQuest, (DetailedQuestStep) questStep)) {
						return;
					}

					if (questStep instanceof WidgetStep && applyWidgetStep((WidgetStep) questStep)) {
						return;
					}

					if (questStep instanceof DetailedQuestStep && !(questStep instanceof NpcStep || questStep instanceof ObjectStep || questStep instanceof DigStep)) {
                        boolean result = applyDetailedQuestStep((DetailedQuestStep) questStep);
                        if (result) {
                            settleAfterAction(attackedOnNonKillStep);
                            return;
                        }
                    }

                    if (selectedQuest.getCurrentStep() instanceof ConditionalStep) {
                        QuestStep conditionalStep = conditionalCommitment.select(questStep);
                        if (applyStep(conditionalStep)) {
                            conditionalCommitment.markTried();
                        }
                    } else if (selectedQuest.getCurrentStep() instanceof NpcStep) {
                        applyNpcStep((NpcStep) selectedQuest.getCurrentStep());
                    } else if (selectedQuest.getCurrentStep() instanceof ObjectStep) {
                        applyObjectStep((ObjectStep) selectedQuest.getCurrentStep());
                    } else if (selectedQuest.getCurrentStep() instanceof DigStep) {
                        applyDigStep((DigStep) selectedQuest.getCurrentStep());
                    } else if (selectedQuest.getCurrentStep() instanceof PuzzleStep) {
                        applyPuzzleStep((PuzzleStep) selectedQuest.getCurrentStep());
                    }

                    settleAfterAction(attackedOnNonKillStep);
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace(System.out);
            }
        })), 0, Rs2Random.between(400, 1000), TimeUnit.MILLISECONDS);

        combatSupportFuture = scheduledExecutorService.scheduleWithFixedDelay(this::combatSupportTick,
                600, 600, TimeUnit.MILLISECONDS);
        supervisorFuture = scheduledExecutorService.scheduleWithFixedDelay(this::supervisorTick,
                1000, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

	private final RandomEventDismissRule randomEventRule = new RandomEventDismissRule();

	/** Dismisses a random-event NPC that targets us; rate-limited per NPC, and clears any dialogue it opened. */
	private void dismissRandomEventTick(long now) {
		if (config == null || !config.dismissRandomEvents()) return;
		net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel event = Rs2Npc.getRandomEventNPC();
		if (event == null) return;
		if (Rs2Dialogue.hasContinue()) {
			Rs2Dialogue.clickContinue();
			return;
		}
		if (!randomEventRule.shouldDismiss(event.getIndex(), now)) return;
		Microbot.log("[QuestHelper] Dismissing random event NPC " + event.getName(), Level.INFO);
		Rs2Npc.interact(event, "Dismiss");
	}

	/**
	 * Runs beside the main loop (which blocks inside walks): cancels the in-flight walk the moment
	 * automation is paused or the quest deselected, and drives the stuck watchdog.
	 */
	private void supervisorTick() {
		try {
			interruptOnStop();
			boolean on = config != null && config.startStopQuestHelper() && !QuestPause.isPaused();
			QuestHelper selected = getQuestHelperPlugin() == null ? null : getQuestHelperPlugin().getSelectedQuest();
			long now = System.currentTimeMillis();
			QuestWalkRule.setCrossingMode(on && selected != null && !selectedIsCompleted(selected));
			if (!on || selected == null) {
				if (automationWasOn) {
					automationWasOn = false;
					pausedAtMs = now;
				}
				// keep cancelling for a few seconds: the main loop may re-issue a walk before it sees the pause
				if (now - pausedAtMs < 10_000L && QuestPause.shouldCancelWalk(QuestWalkRule.isQuestWalkInFlight(), Rs2Walker.getCurrentTarget())) {
					Rs2Walker.clearWalkingRoute(on ? "quest-helper:quest-deselected" : "quest-helper:paused");
				}
				stuckWatchdog.reset(now);
				stuckStage = "NONE";
				stuckForMs = 0;
				return;
			}
			automationWasOn = true;
			if (!Microbot.isLoggedIn()) {
				stuckWatchdog.reset(now);
				return;
			}
			boolean fighting = isFightingHostile();
			if (fighting) lastCombatAtMs = now;
			QuestWalkRule.setLongTravelHeld(QuestWalkRule.shouldHoldLongTravel(fighting, lastCombatAtMs, now));
			dismissRandomEventTick(now);
			endEscortIfInactive(selected.getCurrentStep() == null ? null : selected.getCurrentStep().getActiveStep());
			WorldPoint walkTarget = Rs2Walker.getCurrentTarget();
			if (walkTarget != null && QuestWalkRule.isQuestWalkInFlight()) lastWalkTarget = walkTarget;
			if (!fetchingFare && QuestWalkRule.isQuestWalkInFlight() && QuestWalkRule.pendingFare() > 0
					&& Rs2Inventory.itemQuantity(ItemID.COINS_995) < QuestWalkRule.pendingFare()) {
				// the planned route pays a ship fare the inventory cannot cover: stop walking toward the ship
				Rs2Walker.clearWalkingRoute("quest-helper:fare");
			}
			QuestStep step = selected.getCurrentStep() == null ? null : selected.getCurrentStep().getActiveStep();
			int var = Microbot.getClientThread().runOnClientThreadOptional(selected::getVar).orElse(-1);
			boolean suppressed = (config.takeShortBreaks() && breakEndsAtMs > now)
					|| QuestCombatHandler.isAwaitingSetup() || QuestPreflight.isInProgress()
					|| Rs2Combat.inCombat() || Rs2Bank.isOpen();
			if (step != seenLinesStep) {
				seenLinesStep = step;
				seenDialogueLines.clear();
				lastWalkTarget = null;
				combinedOnStep = null;
				combinedIds = new HashSet<>();
			}
			if (Rs2Dialogue.isInDialogue() && lastDialogueLine != null && !lastDialogueLine.isEmpty()) {
				seenDialogueLines.add(lastDialogueLine);
			}
			suppressed = suppressed || Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.CUTSCENE_STATUS) == 1;
			// a dialogue line not seen before on this step is progress; the same menu over and over is not
			QuestStuckWatchdog.Stage entered = stuckWatchdog.tick(now,
					QuestStuckRecovery.signature(step, var, false, escortProgress * 1_000L + seenDialogueLines.size()),
					suppressed);
			stuckStage = stuckWatchdog.current().name();
			stuckForMs = stuckWatchdog.stuckForMs(now);
			if (entered == null) return;
			WorldPoint at = Rs2Player.getWorldLocation();
			Microbot.log("[QuestWatchdog] stuck " + (stuckForMs / 1000) + "s at " + at + " step=\""
					+ (step == null ? "?" : step.getText()) + "\" -> stage " + entered, Level.WARN);
			WorldPoint stepPoint = step instanceof DetailedQuestStep && ((DetailedQuestStep) step).getDefinedPoint() != null
					? ((DetailedQuestStep) step).getDefinedPoint().getWorldPoint() : lastWalkTarget;
			switch (entered) {
				case REPLAN:
					// read before replan() clears the route
					boolean walkInFlight = QuestWalkRule.isQuestWalkInFlight();
					QuestStuckRecovery.replan();
					// a quest walk going nowhere far from the current step's goal: an owned teleport beats waiting four
					// more minutes. Never from a stale goal, and not inside an instance (coordinates are not real there)
					if (walkInFlight && !Microbot.getClient().isInInstancedRegion()) {
						QuestStuckRecovery.teleportTowardFarGoal(stepPoint != null ? stepPoint : lastWalkTarget);
					}
					break;
				case BLACKLIST:
					QuestStuckRecovery.blacklist(lastWalkTarget != null ? lastWalkTarget : stepPoint);
					break;
				case PLANE_ESCAPE:
					QuestStuckRecovery.escapePlane();
					break;
				case TELEPORT:
					if (!QuestStuckRecovery.teleport(stepPoint)) {
						Microbot.log("[QuestWatchdog] no owned Ring of wealth or Amulet of glory to teleport out with; "
								+ "giving up soon", Level.WARN);
					}
					break;
				case GIVE_UP:
					stopQuesterWithReason("Stuck for " + (stuckForMs / 1000) + "s at " + at + " on step \""
							+ (step == null ? "?" : step.getText()) + "\" after all recovery stages");
					break;
				default:
					break;
			}
		} catch (Exception ex) {
			Microbot.log("[QuestWatchdog] supervisor tick failed: " + ex, Level.DEBUG);
		}
	}

	/** Publishes the active step's wanted dialogue options so the walker can answer doors' conversations itself. */
	private void publishDialogueChoices(QuestStep step) {
		if (step == null) {
			DialogueChoiceProvider.clear();
			return;
		}
		DialogueChoiceProvider.set(() -> stepChoiceTexts(step));
	}

	/** The step's choices that currently apply (expected previous line / excluded widgets honoured), in order. */
	private List<String> stepChoiceTexts(QuestStep step) {
		List<String> out = new ArrayList<>();
		for (var choice : step.getChoices().getChoices()) {
			if (choice.getChoice() == null) {
				continue;
			}
			if (choice.getExpectedPreviousLine() != null
					&& !QuestDialogueRules.previousLineMatches(choice.getExpectedPreviousLine(), lastDialogueLine)) {
				continue;
			}
			if (choice.getExcludedStrings() != null && choice.getExcludedStrings().stream().anyMatch(Rs2Widget::hasWidget)) {
				continue;
			}
			out.add(choice.getChoice());
		}
		return out;
	}

	/**
	 * While a conversation is open, answers the step's options and clicks continues every ~100 ms (bounded to 10 s).
	 * Leaves the rest to the normal tick when an options menu matches none of the step's choices.
	 */
	private void answerDialogueFast(QuestStep step) {
		if (step == null || !Rs2Dialogue.isInDialogue()) {
			return;
		}
		long end = System.currentTimeMillis() + 10_000L;
		int stuck = 0;
		String lastMenu = null;
		while (System.currentTimeMillis() < end && !Thread.currentThread().isInterrupted()
				&& config.startStopQuestHelper() && Rs2Dialogue.isInDialogue()) {
			String line = Rs2Dialogue.getDialogueText();
			if (line != null && !line.isEmpty()) {
				lastDialogueLine = line;
			}
			if (dialogueStartedStep == null) {
				dialogueStartedStep = step;
			}
			if (Rs2Dialogue.hasSelectAnOption()) {
				List<Widget> widgets = Rs2Dialogue.getDialogueOptions();
				List<String> texts = widgets.stream().map(Widget::getText).collect(Collectors.toList());
				if (DialogueChoiceProvider.pickIndex(texts, stepChoiceTexts(step)) < 0) {
					return;
				}
				String menu = String.join("|", texts);
				stuck = menu.equals(lastMenu) ? stuck + 1 : 0;
				if (stuck >= 3) {
					return;
				}
				lastMenu = menu;
				Widget[] children = Rs2Widget.getWidget(ComponentID.DIALOG_OPTION_OPTIONS).getDynamicChildren();
				boolean sent = false;
				for (String wanted : stepChoiceTexts(step)) {
					if (children != null && selectDialogChoice(children, wanted)) {
						sent = true;
						break;
					}
				}
				if (!sent) {
					return;
				}
				sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption()
						|| !menu.equals(Rs2Dialogue.getDialogueOptions().stream().map(Widget::getText).collect(Collectors.joining("|"))), 600);
			} else if (Rs2Dialogue.hasContinue()) {
				Rs2Walker.clearWalkingRoute("quest-helper:dialogue-fast-continue");
				Rs2Dialogue.clickContinue();
				sleepUntil(() -> !Rs2Dialogue.hasContinue(), 600);
			} else {
				return;
			}
		}
	}

	private boolean isAttackedOnNonKillStep(QuestStep step) {
		return step != null && !isKillNpcStep(step) && Rs2Combat.inCombat();
	}

	/**
	 * Waits for the last action to take effect. When an NPC is hitting us on a non-kill step, waiting for the
	 * fight to end would stall the step for as long as it lasts, so only wait for movement and re-act next tick.
	 */
	private void settleAfterAction(boolean attackedOnNonKillStep) {
		if (attackedOnNonKillStep) {
			sleepUntil(Rs2Player::isMoving, 600);
			sleepUntil(() -> !Rs2Player.isMoving(), 3_000);
			return;
		}
		sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue(), 500);
		sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isMoving() && !Rs2Player.isAnimating());
	}

	/**
	 * Quests never fight back on non-kill steps: auto-retaliate is switched off for them (and the player's setting is
	 * restored when the quest stops) and back on while a kill step is active.
	 *
	 * @return true when this tick was spent toggling the setting
	 */
	private boolean manageAutoRetaliate(QuestStep step) {
		if (step == null || Rs2Dialogue.isInDialogue() || Rs2Bank.isOpen() || Rs2Shop.isOpen()) {
			return false;
		}
		boolean retaliateOn = Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 0;
		boolean kill = isKillNpcStep(step);
		if (!kill && retaliateOn) {
			autoRetaliateDisabledByUs = true;
			Rs2Combat.setAutoRetaliate(false);
			return true;
		}
		if (kill && !retaliateOn) {
			// always on for kill steps, even if the player had it off: otherwise a target that hits first is never fought
			if (Rs2Combat.setAutoRetaliate(true)) {
				autoRetaliateDisabledByUs = false;
			}
			return true;
		}
		return false;
	}

	private void restoreAutoRetaliate() {
		if (!autoRetaliateDisabledByUs) {
			return;
		}
		autoRetaliateDisabledByUs = false;
		if (Microbot.isLoggedIn()) {
			Rs2Combat.setAutoRetaliate(true);
		}
	}

	/**
	 * A dialogue menu none of the step's choices matched. The old fallback closed it and re-clicked the NPC forever, so
	 * the best-matching option is chosen at once. Options that travel, teleport, buy, pay, sell, start a quest or leave
	 * are never chosen unless the step's own words match them, and each option is tried at most once (every third
	 * appearance of the same menu moves to the next one); when they run out the menu is closed as before.
	 */
	private boolean pickFallbackMenuOption(QuestStep step) {
		List<Widget> optionWidgets = Rs2Dialogue.getDialogueOptions();
		if (optionWidgets.isEmpty()) {
			return false;
		}
		List<String> options = optionWidgets.stream().map(Widget::getText).collect(Collectors.toList());
		String key = options.stream().map(QuestDialogueRules::normalize).collect(Collectors.joining("|"));
		// Count how often this menu has appeared since the step began, across the box closing and the NPC being
		// talked to again: a conversation that loops without progress then tries the next-best option.
		String conversationKey = step == null || step.getText() == null ? "" : String.join(" ", step.getText());
		if (step != menuStep) {
			menuStep = step;
			menuAppearances.clear();
		}
		// The options picked are remembered per step (by its text, not the object, which can be rebuilt) so an
		// informational option is never asked twice, however the menu changes between asks.
		if (!conversationKey.equals(menuConversationKey)) {
			menuConversationKey = conversationKey;
			chosenFallbackOptions.clear();
		}
		if (!menuWasVisible) {
			menuWasVisible = true;
			menuAppearances.merge(key, 1, Integer::sum);
		}
		int attempt = (menuAppearances.getOrDefault(key, 1) - 1) / MENU_REPEATS_BEFORE_NEXT_OPTION;
		List<String> hints = new ArrayList<>();
		List<String> questName = new ArrayList<>();
		var selected = getQuestHelperPlugin().getSelectedQuest();
		if (selected != null && selected.getQuest() != null) {
			questName.add(selected.getQuest().getName());
		}
		if (step != null) {
			if (step.getText() != null) {
				hints.addAll(step.getText());
			}
			for (var choice : step.getChoices().getChoices()) {
				if (choice.getChoice() != null) {
					hints.add(choice.getChoice());
				}
			}
		}
		// a missing required item makes "Can I have another ..." a wanted option (and nothing else)
		List<String> missingItems = new ArrayList<>();
		if (step instanceof DetailedQuestStep) {
			for (Requirement requirement : ((DetailedQuestStep) step).getRequirements()) {
				if (requirement instanceof ItemRequirement && ((ItemRequirement) requirement).getName() != null
						&& !Microbot.getClientThread().runOnClientThreadOptional(
						() -> requirement.check(Microbot.getClient())).orElse(true)) {
					missingItems.add(((ItemRequirement) requirement).getName());
				}
			}
		}
		// on a talk step, once something informational was asked, a polite exit ("That'll be all") ends it
		boolean preferExit = step instanceof NpcStep && !chosenFallbackOptions.isEmpty()
				&& QuestDialogueRules.firstExitOption(options) != null;
		String pick = QuestDialogueRules.pickOptionOrExit(options, hints, questName, missingItems, attempt,
				lastDialogueLine, chosenFallbackOptions, preferExit);
		if (pick == null) {
			return false;
		}
		if (!key.equals(menuLoggedKey)) {
			menuLoggedKey = key;
            Microbot.log("Quest helper: no configured dialogue option matched, choosing \"" + normalizeDialogText(pick)
					+ "\" (try " + (attempt + 1) + (QuestDialogueRules.isExitOption(pick) ? ", exit" : "") + ")", Level.INFO);
		}
		Widget[] children = Rs2Widget.getWidget(ComponentID.DIALOG_OPTION_OPTIONS).getDynamicChildren();
		boolean chose = children != null && selectDialogChoice(children, normalizeDialogText(pick));
		// only informational options are remembered: a "Yes" may have to be chosen again
		if (chose && !QuestDialogueRules.isConfirmOption(pick) && !QuestDialogueRules.isExitOption(pick)) {
			chosenFallbackOptions.add(QuestDialogueRules.normalize(pick));
		}
		return chose;
	}

	private static boolean isShopPurchaseStep(QuestStep step) {
		if (step == null) {
			return false;
		}
		for (var highlight : step.getWidgetsToHighlight()) {
			if (highlight instanceof WidgetHighlight
					&& ((WidgetHighlight) highlight).getInterfaceID() == net.runelite.api.gameval.InterfaceID.Shopmain.ITEMS) {
				return true;
			}
		}
		return step instanceof NpcStep && QuestDialogueRules.isBuyText(step.getText());
	}

	/**
	 * With a shop open, buys the step's highlighted shop item or its missing item requirements, then closes the shop.
	 *
	 * @return true when this tick was spent on the shop
	 */
	private boolean handleShopPurchase(QuestStep step) {
		if (!isShopPurchaseStep(step)) {
			return false;
		}
		Map<Integer, Integer> wanted = new java.util.LinkedHashMap<>();
		List<ItemRequirement> itemReqs = new ArrayList<>();
		if (step instanceof DetailedQuestStep) {
			for (Requirement requirement : ((DetailedQuestStep) step).getRequirements()) {
				if (requirement instanceof ItemRequirement) {
					itemReqs.add((ItemRequirement) requirement);
				}
			}
		}
		for (var highlight : step.getWidgetsToHighlight()) {
			if (highlight instanceof WidgetHighlight && ((WidgetHighlight) highlight).getItemIdRequirement() != null) {
				int id = ((WidgetHighlight) highlight).getItemIdRequirement();
				int qty = itemReqs.stream().filter(r -> r.getAllIds().contains(id))
						.mapToInt(ItemRequirement::getQuantity).filter(q -> q > 0).findFirst().orElse(1);
				wanted.put(id, qty);
			}
		}
		if (wanted.isEmpty()) {
			for (ItemRequirement ir : itemReqs) {
				if (ir.mustBeEquipped() || ir.getAllIds().isEmpty() || ir.getAllIds().get(0) == null) {
					continue;
				}
				wanted.put(ir.getAllIds().stream().filter(i -> i != null && Rs2Shop.hasStock(i)).findFirst()
						.orElse(ir.getAllIds().get(0)), Math.max(1, ir.getQuantity()));
			}
		}
		if (wanted.isEmpty()) {
			return false; // nothing derivable (e.g. Pirate's Treasure), the highlight block handles it
		}
		for (Map.Entry<Integer, Integer> entry : wanted.entrySet()) {
			int id = entry.getKey();
			int missing = entry.getValue() - Rs2Inventory.itemQuantity(id);
			if (missing <= 0) {
				continue;
			}
			if (!Rs2Shop.hasStock(id)) {
				Microbot.log("Quest helper: shop has no stock of item " + id, Level.WARN);
				Rs2Shop.closeShop();
				return true;
			}
			int chunk = QuestDialogueRules.shopBuyChunks(missing).get(0);
			int before = Rs2Inventory.itemQuantity(id);
			Microbot.status = "Buying item " + id + " x" + chunk;
			if (Rs2Shop.buyItem(id, String.valueOf(chunk))) {
				sleepUntil(() -> Rs2Inventory.itemQuantity(id) > before, 2_500);
			}
			return true;
		}
		Rs2Shop.closeShop();
		return true;
	}

	/**
	 * One-time check per quest start: every tradable item the rest of the quest needs is on the player or in the bank,
	 * and the shortfall is bought from the Grand Exchange into the bank. Runs after the setup dialog is answered.
	 *
	 * @return true while the preflight used this tick and the quest must not act yet
	 */
	private volatile boolean fetchingFare;
	private int fareAttempts;
	private static final int MAX_FARE_ATTEMPTS = 3;

	/**
	 * A route that crosses by a paid ship needs the fare in the inventory: the seaman refuses with "You don't have enough
	 * money" otherwise. When coins are short the quest goes to the bank for them first.
	 *
	 * @return true when this tick was spent on it
	 */
	private boolean ensureFare(QuestHelper quest) {
		int fare = QuestWalkRule.pendingFare();
		if (fare <= 0) {
			return false;
		}
		long coins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
		if (coins >= fare) {
			QuestWalkRule.clearFare();
			fareAttempts = 0;
			return false;
		}
		long bankCoins = bankCoinSnapshot();
		long take = QuestCoins.toWithdraw(fare, coins, bankCoins);
		if (take < 0) {
			QuestWalkRule.clearFare();
			stopQuesterWithReason("The route needs a " + fare + " coin fare, but the inventory has " + coins
					+ " and the bank has " + Math.max(0, bankCoins));
			return true;
		}
		if (++fareAttempts > MAX_FARE_ATTEMPTS) {
			fareAttempts = 0;
			QuestWalkRule.clearFare();
			stopQuesterWithReason("Could not get " + fare + " coins for the route's fare (" + MAX_FARE_ATTEMPTS
					+ " bank trips failed)");
			return true;
		}
		fetchingFare = true;
		try {
			Microbot.status = "Quest helper: fetching " + fare + " coins for the ship fare";
			Microbot.log("Quest walk: fetching " + take + " coins from the bank for a " + fare + " coin ship fare", Level.INFO);
			if (!Rs2Bank.isOpen()) {
				Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
				if (!sleepUntil(Rs2Bank::isOpen, 15_000)) {
					return true;
				}
			}
			if (!Rs2Bank.setWithdrawAsItem()) {
				Rs2Bank.closeBank();
				return true;
			}
			Rs2Bank.withdrawX(ItemID.COINS_995, (int) Math.min(Integer.MAX_VALUE, take));
			sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995) >= fare, 3_000);
			Rs2Bank.closeBank();
			sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		} finally {
			fetchingFare = false;
		}
		return true;
	}

	private boolean runPreflight(QuestHelper quest) {
		// another loop that is already buying keeps the quest waiting
		return BUY_GUARD.callOrElse(() -> runPreflightLocked(quest), true);
	}

	private boolean runPreflightLocked(QuestHelper quest) {
		String soloFailure = QuestPreflight.anotherPlayerFailure(quest.getQuest().name(), quest.getQuest().getName());
		if (soloFailure != null) {
			finishPreflight(soloFailure);
			stopQuesterWithReason(soloFailure.substring(QuestPreflight.FAILED_PREFIX.length()));
			return true;
		}
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
			preflightPlan = null;
			purchaseApproved = false;
			QuestPurchasePlan.setCurrent(null);
			Boolean confirmOverride = freshStart ? QuestPurchasePlan.consumeOverride() : null;
			purchaseConfirmOverride = confirmOverride;
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
			Microbot.log("Quest helper preflight: not buying quest items (Obtain Missing Items is off)", Level.INFO);
			finishPreflight(QuestPreflight.DONE);
			return false;
		}

		QuestPreflight.setStatus(QuestPreflight.CHECKING);
		Microbot.status = "Quest helper: checking quest items";

		ItemAndLastUpdated bank = QuestContainerManager.getBankData();
		if (!QuestPreflight.bankScanned(bank.getLastUpdated())) {
			if (preflightBankAttempts >= PREFLIGHT_MAX_BANK_ATTEMPTS) {
				Microbot.log("Quest helper preflight skipped: could not open the bank to count quest items", Level.WARN);
				finishPreflight(QuestPreflight.FAILED_PREFIX + "could not open the bank");
				return false;
			}
			preflightBankAttempts++;
			Microbot.status = "Quest helper: opening the bank to count quest items";
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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
		dropNoBuyNeeds(needs, quest);

		Map<Integer, Integer> onPlayer = new HashMap<>();
		addContainer(onPlayer, QuestContainerManager.getEquippedData());
		addContainer(onPlayer, QuestContainerManager.getInventoryData());
		Map<Integer, Integer> owned = new HashMap<>(onPlayer);
		addContainer(owned, bank);
		QuestPreflight.foldNoted(needs, this::notedVariantId, onPlayer, owned);

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
				toBuy.add(new ItemRequirement(QuestPreflight.purchaseLabel(shortfall), shortfall.ids, shortfall.missing));
				if (shortfall.supply && !preflightSummaryLogged) {
					Microbot.log("Quest helper: selected supply " + QuestPreflight.purchaseLabel(shortfall)
							+ " (not enough in the bank or on you)", Level.WARN);
				}
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
		if (!purchaseApproved) {
			boolean bankKnown = QuestPreflight.bankScanned(bank.getLastUpdated());
			switch (gatePurchases(quest, toBuy, onPlayer, owned, bankKnown)) {
				case WAIT:
				case STOPPED:
					return true;
				case SKIPPED:
					return false;
				default:
					break;
			}
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

	/**
	 * Before the preflight walks to the Grand Exchange: prices the shortfall, checks the coins and, unless the player
	 * turned confirmation off, waits (asynchronously, one tick at a time) for them to approve the purchase.
	 */
	private PurchaseGate gatePurchases(QuestHelper quest, List<ItemRequirement> toBuy,
			Map<Integer, Integer> onPlayer, Map<Integer, Integer> owned, boolean bankKnown) {
		if (preflightPlan == null) {
			List<QuestPurchasePlan.Line> lines = new ArrayList<>();
			for (ItemRequirement ir : toBuy) {
				int primaryId = tradablePrimaryId(ir);
				if (primaryId == -1) {
					Microbot.log("Quest helper: " + ir.getName() + " is not tradable on the Grand Exchange, "
							+ "not buying it (the quest steps will obtain it in game)", Level.WARN);
					continue;
				}
				int basePrice = fetchInstabuyReferencePrice(primaryId);
				if (basePrice <= 0) {
					stopQuesterWithReason("Failed to fetch Grand Exchange price for: " + ir.getName());
					return PurchaseGate.STOPPED;
				}
				lines.add(new QuestPurchasePlan.Line(ir.getName(), ir.getQuantity(), QuestPurchasePlan.offerPrice(basePrice)));
			}
			long invCoins = onPlayer.getOrDefault(ItemID.COINS_995, 0);
			long bankCoins = bankKnown ? Math.max(0, owned.getOrDefault(ItemID.COINS_995, 0) - invCoins) : -1;
			preflightPlan = new QuestPurchasePlan(lines, invCoins, bankCoins);
			QuestPurchasePlan.setCurrent(preflightPlan);
		}

		boolean automatedRun = config.fullyAutomated();
		if (queueRunner.hasApproval() || automatedRun) {
			List<String> planned = preflightPlan.lines().stream().map(l -> l.name).collect(Collectors.toList());
			if (queueRunner.hasApproval() && !queueRunner.covers(planned, preflightPlan.total())) {
				stopQuesterWithReason("Skipped " + quest.getQuest().getName() + ": "
						+ queueRunner.refusal(planned, preflightPlan.total()));
				return PurchaseGate.STOPPED;
			}
			if (automatedRun && QuestSpendRules.decide(preflightPlan.total(), config.maxSpendPerQuest(),
					preflightPlan.coins()) == QuestSpendRules.Decision.OVER_MAX_SPEND) {
				stopQuesterWithReason(String.format(
						"Skipped %s: its purchases (~%,d gp) are over the max spend of %,d gp per quest",
						quest.getQuest().getName(), preflightPlan.total(), config.maxSpendPerQuest()));
				return PurchaseGate.STOPPED;
			}
			if (!preflightPlan.affordable()) {
				stopQuesterWithReason(preflightPlan.insufficientMessage());
				return PurchaseGate.STOPPED;
			}
			queueRunner.recordSpend(preflightPlan.total());
			purchaseApproved = true;
			announce(String.format("Buying %s (~%,d gp)", preflightPlan.describeItems(), preflightPlan.total()));
			return PurchaseGate.PROCEED;
		}
		// the quest window was switched off for this quest: buy what is missing when it can be paid for
		if (!preflightPlan.affordable()) {
			stopQuesterWithReason(preflightPlan.insufficientMessage());
			return PurchaseGate.STOPPED;
		}
		purchaseApproved = true;
		Microbot.log(String.format("Quest helper: buying %s (~%,d gp, have %,d gp)", preflightPlan.describeItems(),
				preflightPlan.total(), preflightPlan.coins()), Level.INFO);
		return PurchaseGate.PROCEED;
	}

	/**
	 * With the bank open, makes sure at least one inventory slot is free (coins and bought items need one): deposits
	 * the least important item that is not a quest item, coins or something the user brought; if everything is
	 * protected, one stack of food.
	 */
	private void ensureFreeSlotInBank(List<ItemRequirement> keepFor) {
		if (Rs2Inventory.emptySlotCount() > 0 || !Rs2Bank.isOpen()) {
			return;
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
		for (ItemRequirement ir : keepFor) {
			for (Integer id : ir.getAllIds()) {
				if (id != null) {
					keep.add(id);
				}
			}
		}
		Rs2ItemModel victim = Rs2Inventory.get(item -> !keep.contains(item.getId()) && !combat.isUserBroughtProtected(item));
		if (victim == null) {
			victim = Rs2Inventory.get(Rs2ItemModel::isFood);
			if (victim != null) {
				Microbot.log("Quest helper: inventory full of protected items, banking one stack of "
						+ victim.getName() + " to make room for coins", Level.WARN);
			}
		}
		if (victim == null) {
			Microbot.log("Quest helper: inventory is full and nothing can be banked to make room", Level.WARN);
			return;
		}
		final int victimId = victim.getId();
		Microbot.status = "Quest helper: banking " + victim.getName() + " to free a slot";
		Rs2Bank.depositAll(victimId);
		sleepUntil(() -> Rs2Inventory.emptySlotCount() > 0, 3_000);
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
		final boolean notStarted = state == QuestState.NOT_STARTED;
		Set<Integer> upfrontIds = new HashSet<>();
		if (quest.getItemRequirements() != null) {
			for (ItemRequirement ir : quest.getItemRequirements()) {
				if (ir != null) {
					upfrontIds.addAll(ir.getAllIds());
				}
			}
		}
		final String questEnumName = quest.getQuest() == null ? null : quest.getQuest().name();
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
		if (steps != null && steps.stream().anyMatch(step -> QuestCombatDetector.isKillStep(step)
				&& QuestFightSupport.isDragonFight(QuestCombatDetector.fightKeyOf(step), step.getText()))) {
			needs.add(QuestPreflight.antifireNeed());
		}
		if (steps != null) {
			needs.addAll(combat.swapPreflightNeeds(steps));
			needs.addAll(combat.boostPreflightNeeds(steps));
		}
		needs.addAll(combat.supplyPreflightNeeds(this::isIdTradable));
		for (Requirement requirement : candidates) {
			// Subclasses (keyrings, followers, composites) have their own ownership rules; leave them to the steps
			if (requirement == null || requirement.getClass() != ItemRequirement.class) {
				continue;
			}
			ItemRequirement ir = (ItemRequirement) requirement;
			List<Integer> ids = ir.getAllIds().stream().filter(id -> id != null && id > 0).collect(Collectors.toList());
			boolean inUpfront = ids.stream().anyMatch(upfrontIds::contains);
			if (QuestPreflight.obtainedDuringQuest(questEnumName, ir.getName(), ir.getTooltip(), notStarted, inUpfront)) {
				continue;
			}
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

    private HumanizedTiming humanTiming;

    private String lastUnresolvedReport = "";

    /**
     * The step the automation acts on. A conditional step that answers with itself (not started, nothing resolved) has no
     * text and nothing to do; that is a bug in the step map, not a state to idle in. It is logged once with the quest
     * state and resolved by re-evaluating the conditions down to a leaf.
     */
    private QuestStep activeStepOf(QuestHelper quest) {
        QuestStep step = quest.getCurrentStep() == null ? null : quest.getCurrentStep().getActiveStep();
        if (!(step instanceof ConditionalStep)) {
            return step;
        }
        // the conditions are re-evaluated and steps registered with the event bus: client thread only
        QuestStep resolved = Microbot.getClientThread()
                .runOnClientThreadOptional(((ConditionalStep) step)::resolveActiveStep).orElse(step);
        String report = quest.getQuest().getName() + "|" + resolved.getClass().getSimpleName();
        if (!report.equals(lastUnresolvedReport)) {
            lastUnresolvedReport = report;
            int var = Microbot.getClientThread().runOnClientThreadOptional(quest::getVar).orElse(-1);
            Microbot.log("Quest helper: step map answered with an unresolved conditional step (quest "
                    + quest.getQuest().getName() + ", var " + var + "); resolved to "
                    + resolved.getClass().getSimpleName() + " \"" + String.join(" ", resolved.getText()) + "\"", Level.WARN);
        }
        return resolved;
    }

    /** Skewed random pause before an action, with fatigue and rare hesitations; skipped when timing matters. */
    private void humanizePause() {
        if (!config.humanizedTiming()) {
            humanTiming = null;
            return;
        }
        if (humanTiming == null) {
            humanTiming = new HumanizedTiming(new java.util.Random(), config.humanizedFatigue());
        }
        long now = System.currentTimeMillis();
        humanTiming.update(now, breakEndsAtMs > now);
        net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban.mouseFatigue.noiseAmplitude =
                5.0 + 10.0 * humanTiming.fatigue();
        QuestHelper selected = getQuestHelperPlugin().getSelectedQuest();
        QuestStep step = selected.getCurrentStep() == null ? null : selected.getCurrentStep().getActiveStep();
        if (Rs2Dialogue.isInDialogue() || Rs2Combat.inCombat() || QuestCombatDetector.isKillStep(step)
                || Rs2Player.getHealthPercentage() < 60) {
            return;
        }
        boolean idle = !Rs2Bank.isOpen() && !Rs2Player.isMoving() && !Rs2Player.isInteracting()
                && !Rs2Player.isAnimating() && !QuestCombatHandler.isAwaitingSetup() && !QuestPreflight.isInProgress();
        long hesitation = humanTiming.maybeHesitate(now, config.hesitationChancePercent() / 100.0, idle);
        if (hesitation > 0) {
            Microbot.status = "Quest helper: hesitating (" + (hesitation / 1000) + "s)";
            sleep((int) hesitation);
            return;
        }
        sleep((int) humanTiming.nextDelayMs(150));
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

	/** Where banking must come back to: the current step's defined point, else the player's tile. */
	private WorldPoint bankReturnPoint() {
		try {
			QuestHelper selected = getQuestHelperPlugin().getSelectedQuest();
			QuestStep step = selected == null || selected.getCurrentStep() == null ? null : selected.getCurrentStep().getActiveStep();
			if (step instanceof DetailedQuestStep && ((DetailedQuestStep) step).getDefinedPoint() != null) {
				WorldPoint p = ((DetailedQuestStep) step).getDefinedPoint().getWorldPoint();
				if (p != null) {
					return p;
				}
			}
		} catch (RuntimeException ignored) {
			// fall back to the player's tile
		}
		return Rs2Player.getWorldLocation();
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
		List<Integer> before = Rs2Bank.isOpen() ? Rs2Inventory.items().map(i -> i.getId()).collect(Collectors.toList()) : null;
		Rs2Inventory.interact(invId, action);
		sleepUntil(() -> itemRequirement.getAllIds().stream().anyMatch(Rs2Equipment::isWearing), 2_000);
		if (before != null && Rs2Bank.isOpen()) {
			depositDisplaced(before);
		}
		return true;
	}

	private QuestStep bossWearStep;
	private int bossWearAttempts;

	/**
	 * "Prepare to fight X (level N)": wears what the step asks for that can be worn (the anti-dragon shield), then tops
	 * the food up for the boss from the bank. The quest's own combat setup runs after this as before.
	 *
	 * @return true when this tick was spent on it
	 */
	private boolean handleBossPrep(DetailedQuestStep step) {
		int level = QuestBossPrep.bossLevel(step.getText());
		if (level < 0 || Rs2Combat.inCombat()) {
			return false;
		}
		if (step != bossWearStep) {
			bossWearStep = step;
			bossWearAttempts = 0;
		}
		for (Requirement requirement : step.getRequirements()) {
			if (!(requirement instanceof ItemRequirement) || bossWearAttempts >= 3) {
				continue;
			}
			ItemRequirement item = (ItemRequirement) requirement;
			boolean hasRealId = item.getAllIds().stream().anyMatch(id -> id != null && id > 0);
			if (hasRealId && !item.getAllIds().stream().anyMatch(Rs2Equipment::isWearing)
					&& isWearableInInventory(item)) {
				bossWearAttempts++;
				item.getAllIds().stream().filter(id -> id != null && id > 0).findFirst().ifPresent(combat::reserveWornSlot);
				if (equipForBoss(item, level)) {
					return true;
				}
			}
		}
		int hitpoints = Rs2Player.getRealSkillLevel(net.runelite.api.Skill.HITPOINTS);
		if (combat.prepareBossFood(step, slotsReservedForStep(step), QuestBossPrep.foodWanted(level, hitpoints))) {
			return true;
		}
		if (bossBankOpened && Rs2Bank.isOpen()) {
			Rs2Bank.closeBank();
		}
		bossBankOpened = false;
		return false;
	}

	private boolean bossBankOpened;

	/**
	 * Wears a boss-prep item. At the bank (open already, or when food or antifire needs a trip there anyway) the piece it
	 * replaces goes straight back; otherwise it is worn where the player stands, as before.
	 */
	private boolean equipForBoss(ItemRequirement item, int level) {
		boolean bankNeeded = Rs2Bank.isOpen()
				|| Rs2Inventory.getInventoryFood().stream().mapToInt(Rs2ItemModel::getQuantity).sum()
				< QuestBossPrep.foodWanted(level, Rs2Player.getRealSkillLevel(net.runelite.api.Skill.HITPOINTS))
				|| Rs2Inventory.items().noneMatch(i -> QuestFightSupport.isAntifire(i.getName()));
		return bankNeeded ? equipAtBank(item) : wearMustBeEquippedFromInventory(item);
	}

	/**
	 * Puts the item on at the bank and sends the piece it replaced back to the bank: bring exactly what is needed,
	 * never carry the swapped-out gear.
	 *
	 * @return true when this tick was spent on it
	 */
	private boolean equipAtBank(ItemRequirement item) {
		Microbot.status = "Quest helper: putting on " + item.getName();
		if (!Rs2Bank.isOpen()) {
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
			if (!sleepUntil(Rs2Bank::isOpen, 15_000)) {
				return true;
			}
		}
		bossBankOpened = true;
		List<Integer> before = Rs2Inventory.items().map(i -> i.getId()).collect(Collectors.toList());
		Integer invId = item.getAllIds().stream().filter(id -> id != null && id > 0 && Rs2Inventory.contains(id))
				.findFirst().orElse(null);
		if (invId == null) {
			return false;
		}
		Rs2Inventory.wear(invId);
		sleepUntil(() -> item.getAllIds().stream().anyMatch(Rs2Equipment::isWearing), 3_000);
		depositDisplaced(before);
		return true;
	}

	/**
	 * Sends what an equip pushed into the inventory back to the bank, unless the quest or the player needs it: the
	 * requirements of the quest and step, coins, owned teleport jewellery, and supplies the player brought themselves.
	 */
	private void depositDisplaced(List<Integer> before) {
		Set<Integer> keep = new HashSet<>();
		keep.add(ItemID.COINS_995);
		if (currentRequirements != null) {
			for (Requirement requirement : currentRequirements) {
				if (requirement instanceof ItemRequirement) {
					for (Integer id : ((ItemRequirement) requirement).getAllIds()) {
						if (id != null) {
							keep.add(id);
						}
					}
				}
			}
		}
		List<Rs2ItemModel> after = Rs2Inventory.items().collect(Collectors.toList());
		for (int displaced : QuestEquipSwap.displaced(before, after.stream().map(Rs2ItemModel::getId).collect(Collectors.toList()))) {
			Rs2ItemModel item = after.stream().filter(i -> i.getId() == displaced).findFirst().orElse(null);
			if (keep.contains(displaced) || item != null && (QuestEquipSwap.isTeleportJewellery(item.getName())
					|| combat.isUserBroughtProtected(item))) {
				continue;
			}
			Rs2Bank.depositAll(displaced);
		}
	}

	/** Whether the inventory holds the item and it offers a wear/wield action. */
	private static boolean isWearableInInventory(ItemRequirement item) {
		Integer invId = item.getAllIds().stream().filter(id -> id != null && id > 0 && Rs2Inventory.contains(id))
				.findFirst().orElse(null);
		if (invId == null) {
			return false;
		}
		var held = Rs2Inventory.get(invId);
		return held != null && held.getInventoryActions() != null && Arrays.stream(held.getInventoryActions())
				.anyMatch(a -> a != null && (a.equalsIgnoreCase("Wear") || a.equalsIgnoreCase("Wield")));
	}

	/**
	 * Steps like "Open the quest's journal to sync your current quest state" clear only while the
	 * journal is open, so nothing else the script does can finish them. Opens it, gives the helper
	 * a tick to read it, and closes it again. Returns true when this tick was spent on the journal.
	 */
	private boolean handleJournalSync(QuestHelper quest, DetailedQuestStep step) {
		if (!QuestJournalSync.isSyncStep(step.getText())) {
			return false;
		}
		String syncKey = quest.getQuest().getName() + "|" + String.join(" ", step.getText());
		if (!syncKey.equals(journalTabKey)) {
			journalTabKey = syncKey;
			journalTabAttempts = 0;
		}
		long now = System.currentTimeMillis();
		boolean gaveUp = journalTabAttempts >= JOURNAL_TAB_MAX_ATTEMPTS;
		if (!QuestJournalSync.mayRetry(now, lastJournalSyncAtMs, gaveUp)) {
			return false;
		}
		if (gaveUp) {
			journalTabAttempts = 0; // a new round of tries after the backoff
		}
		lastJournalSyncAtMs = now;
		String name = quest.getQuest().getName();
		Microbot.status = "Quest helper: reading the " + name + " journal";

		if (!Rs2Tab.switchTo(InterfaceTab.QUESTS) || !openQuestList()) {
			Microbot.log("[QuestHelper] journal sync: the quest list did not open", Level.WARN);
			return true;
		}
		if (!scrollQuestListTo(name)) {
			Microbot.log("[QuestHelper] journal sync: " + name + " is not in the quest list", Level.WARN);
			return true;
		}
		// the scroll script moves the entry on the next client tick
		sleep(300, 500);
		Widget entry = Microbot.getClientThread().runOnClientThreadOptional(() -> findQuestListEntry(name)).orElse(null);
		if (entry == null || !Rs2Widget.clickWidget(entry)) {
			Microbot.log("[QuestHelper] journal sync: could not click the " + name + " entry", Level.WARN);
			return true;
		}
		boolean open = sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget title = Microbot.getClient().getWidget(InterfaceID.Questjournal.TITLE);
			return title != null && !title.isHidden() && name.equals(Text.removeTags(title.getText()));
		}).orElse(false), 3_000);
		if (!open) {
			Microbot.log("[QuestHelper] journal sync: the " + name + " journal did not open", Level.WARN);
			return true;
		}
		// the helper reads the journal text on its own tick: wait for the step to move on or change its text
		List<String> textBefore = step.getText() == null ? null : new ArrayList<>(step.getText());
		boolean advanced = sleepUntil(() -> {
			QuestStep active = quest.getCurrentStep() == null ? null : quest.getCurrentStep().getActiveStep();
			return active != step || !Objects.equals(textBefore, step.getText());
		}, 1_500);
		if (!advanced) {
			Microbot.log("[QuestHelper] journal sync: the step did not advance after the " + name + " journal opened", Level.INFO);
		}
		Rs2Widget.clickWidget(InterfaceID.Questjournal.CLOSE >>> 16, InterfaceID.Questjournal.CLOSE & 0xFFFF);
		if (!sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.Questjournal.TITLE), 2_000)) {
			Microbot.log("[QuestHelper] journal sync: the " + name + " journal did not close", Level.WARN);
		}
		return true;
	}

	private static boolean questListVisible() {
		return Rs2Widget.isWidgetVisible(QuestWidgets.QUEST_CONTAINER.getGroupId(), QuestWidgets.QUEST_CONTAINER.getChildId());
	}

	private static final int JOURNAL_TAB_MAX_ATTEMPTS = 3;
	/** The quest and step the attempts below belong to; a different one starts again. */
	private String journalTabKey;
	private int journalTabAttempts;

	/**
	 * The quest tab remembers its last sub-tab (Character Summary, Achievement Diaries), which hides the quest list:
	 * clicks the "Quest List" button of the side journal (interface 629). After {@link #JOURNAL_TAB_MAX_ATTEMPTS}
	 * failed tries for the same step it gives up with one warning, and handleJournalSync stops running.
	 */
	private boolean openQuestList() {
		if (sleepUntil(QuestScript::questListVisible, 1_500)) {
			journalTabAttempts = 0;
			return true;
		}
		if (journalTabAttempts >= JOURNAL_TAB_MAX_ATTEMPTS) {
			return false;
		}
		journalTabAttempts++;
		int button = InterfaceID.SideJournal.QUEST_LIST;
		Rs2Widget.clickWidget(button >>> 16, button & 0xFFFF);
		if (sleepUntil(QuestScript::questListVisible, 3_000)) {
			journalTabAttempts = 0;
			return true;
		}
		if (journalTabAttempts >= JOURNAL_TAB_MAX_ATTEMPTS) {
			Microbot.log("[QuestHelper] journal sync: the Quest List button did not open the quest list; trying again in a minute", Level.WARN);
		}
		return false;
	}

	/** Client thread only. */
	private static Widget findQuestListEntry(String name) {
		Widget list = Microbot.getClient().getWidget(QuestWidgets.QUEST_CONTAINER.getPackedId());
		Widget[] entries = list == null ? null : list.getDynamicChildren();
		if (entries == null) {
			return null;
		}
		for (Widget entry : entries) {
			if (entry != null && entry.getText() != null && name.equals(Text.removeTags(entry.getText()))) {
				return entry;
			}
		}
		return null;
	}

	/** Scrolls the quest list so the quest's entry is in view; false when the list has no such entry. */
	private static boolean scrollQuestListTo(String name) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget entry = findQuestListEntry(name);
			Widget view = Microbot.getClient().getWidget(QuestWidgets.QUESTLIST_CONTAINER.getPackedId());
			if (entry == null || view == null) {
				return false;
			}
			int scroll = QuestJournalSync.scrollToCentre(entry.getRelativeY(), entry.getHeight(), view.getHeight(), view.getScrollHeight());
			Microbot.getClient().runScript(ScriptID.UPDATE_SCROLLBAR,
					QuestWidgets.QUESTLIST_SCROLLBAR.getId(), QuestWidgets.QUESTLIST_CONTAINER.getId(), scroll);
			return true;
		}).orElse(false);
	}

	/** After the bank trips: buys a style-swap kit the bank lacked, or stops the quest when it cannot be had. */
	private boolean handleSwapKit() {
		if (!combat.swapKitWanted()) {
			return false;
		}
		boolean mayBuy = shouldObtainMissingItems();
		if (config.obtainMissingItems() == QuestHelperConfig.ObtainMissingItemsOption.ASK
				&& obtainItemsSessionChoice == null) {
			mayBuy = false;
		}
		List<QuestPreflight.Need> needs = combat.swapKitToObtain(mayBuy);
		if (needs == null) {
			return false;
		}
		if (!needs.isEmpty()) {
			List<ItemRequirement> missing = new ArrayList<>();
			for (QuestPreflight.Need need : needs) {
				missing.add(new ItemRequirement(need.name, need.ids, need.quantity));
			}
			missingBatch = new ArrayList<>(missing);
			currentRequirements = new ArrayList<Requirement>(missing);
			acquireMissingTradableItems(missing);
		}
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

		// "X or Y" and "X and Y" requirements have no id or quantity of their own: fetch the concrete child
		if (questStep != bankFetchStep) {
			bankFetchStep = questStep;
			bankFetchFailures.clear();
			bankFetchBackoffUntil.clear();
		}
		stepOwnIds = new HashSet<>();
		for (Requirement own : questStep.getRequirements()) {
			if (own instanceof ItemRequirement) {
				stepOwnIds.addAll(((ItemRequirement) own).getAllIds());
			}
		}
		List<ItemRequirement> unobtainable = new ArrayList<>();
		missing = resolveComposites(missing, unobtainable);
		unobtainable.removeIf(ir -> !isStepOwn(ir) && isItemRequirementTradable(ir));
		if (!unobtainable.isEmpty()) {
			if (unobtainable.stream().anyMatch(this::isStepOwn) && QuestContainerManager.getBankData().getItems() == null) {
				// the bank has not been read this session: it may hold what the step needs
				Microbot.status = "Quest helper: checking the bank";
				Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
				sleepUntil(Rs2Bank::isOpen, 15_000);
				return true;
			}
			// the old path for things that cannot be bought (a drop lying at the step's point)
			List<ItemRequirement> ground = unobtainable.stream().filter(ir -> !isItemRequirementTradable(ir))
					.collect(Collectors.toList());
			if (!ground.isEmpty()) {
				missing.addAll(ground);
			} else if (unobtainable.stream().anyMatch(this::isStepOwn)) {
				stopQuesterWithReason("Missing " + unobtainable.stream().map(ItemRequirement::getName)
						.collect(Collectors.joining(" or ")) + ": none of it is in the bank and it cannot be bought");
				return true;
			}
		}
		// a bank trip that brought nothing is not repeated at once: the step waits for its own item; items only
		// gathered ahead of time for later steps are skipped, as before
		missing.removeIf(ir -> bankFetchBackedOff(ir) && !isStepOwn(ir));
		// an item only a later step earns (Ecto-tokens listed on the quest) never holds a step that does not use it
		missing.removeIf(ir -> {
			if (!QuestInventoryRules.deferToLaterStep(isItemRequirementTradable(ir), isStepOwn(ir), isHeldInBank(ir))) {
				return false;
			}
			notifyMissingRequirement(ir);
			return true;
		});
		if (missing.stream().anyMatch(this::bankFetchBackedOff)) {
			Microbot.status = "Quest helper: waiting before the next bank trip";
			return true;
		}

		if (!needsUnnoting.isEmpty()) {
			return unnoteItemsViaBank(needsUnnoting);
		}

		if (missing.isEmpty()) {
			return false;
		}

		for (ItemRequirement ir : missing) {
			if (ir.getAllIds().contains(ItemID.BURNT_MEAT) && !Rs2Inventory.contains(ItemID.BURNT_MEAT)) {
				return makeBurntMeat();
			}
		}

		// Whatever the bank already holds (bought earlier, or kept there) is fetched by walking to the bank,
		// rather than looping on "missing required item" or buying it again
		List<ItemRequirement> nonTradableInBank = new ArrayList<>();
		for (ItemRequirement ir : missing) {
			if (isHeldInBank(ir) && (!isItemRequirementTradable(ir) || bankCovers(ir))) {
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

	/** Whether the bank holds enough of the requirement to withdraw what is still needed. */
	private boolean bankCovers(ItemRequirement ir) {
		return QuestPreflight.bankCovers(ir.checkTotalMatchesInContainers(QuestContainerManager.getBankData()),
				remainingQuantityNeeded(ir), isCarryLimited(ir));
	}

	/** The concrete items to fetch for these missing requirements; the parts nothing can fetch go to {@code unobtainable}. */
	private List<ItemRequirement> resolveComposites(List<ItemRequirement> missing, List<ItemRequirement> unobtainable) {
		List<ItemRequirement> resolved = new ArrayList<>();
		for (ItemRequirement ir : missing) {
			QuestCompositeRequirements.Resolution resolution = QuestCompositeRequirements.resolve(ir,
					this::hasItemRequirementOnPlayer,
					child -> isHeldInBank(child) && bankCovers(child),
					// coins are never bought: they come from the bank or are earned
					child -> isItemRequirementTradable(child) && !child.getAllIds().contains(ItemID.COINS_995));
			resolved.addAll(resolution.fetch);
			unobtainable.addAll(resolution.unobtainable);
		}
		return resolved;
	}

	private static final long BANK_FETCH_BACKOFF_MS = 30_000;
	private static final int MAX_BANK_FETCH_FAILURES = 3;
	private QuestStep bankFetchStep;
	private Set<Integer> stepOwnIds = new HashSet<>();

	/** Whether the requirement is one of the current step's own (not an item gathered ahead for later steps). */
	private boolean isStepOwn(ItemRequirement ir) {
		return ir.getAllIds().stream().anyMatch(stepOwnIds::contains);
	}
	private final Map<Integer, Long> bankFetchBackoffUntil = new HashMap<>();
	private final Map<Integer, Integer> bankFetchFailures = new HashMap<>();

	private static int fetchKey(ItemRequirement ir) {
		return ir.getAllIds().stream().findFirst().orElse(ir.getId());
	}

	private boolean bankFetchBackedOff(ItemRequirement ir) {
		Long until = bankFetchBackoffUntil.get(fetchKey(ir));
		return until != null && System.currentTimeMillis() < until;
	}

	/**
	 * A bank trip that left the holdings of this requirement unchanged: wait before the next, and stop the quest with
	 * the reason once the same item has failed {@link #MAX_BANK_FETCH_FAILURES} times in a row.
	 */
	private boolean noteBankFetchFailed(ItemRequirement ir, String reason) {
		int failures = bankFetchFailures.merge(fetchKey(ir), 1, Integer::sum);
		if (failures >= MAX_BANK_FETCH_FAILURES && isStepOwn(ir)) {
			bankFetchFailures.remove(fetchKey(ir));
			bankFetchBackoffUntil.remove(fetchKey(ir));
			stopQuesterWithReason("Cannot get " + ir.getName() + " from the bank (" + failures + " trips): " + reason);
			return true;
		}
		Microbot.log("Quest helper: the bank trip for " + ir.getName() + " brought nothing (" + reason + "); waiting "
				+ BANK_FETCH_BACKOFF_MS / 1000 + " s before trying again", Level.WARN);
		bankFetchBackoffUntil.put(fetchKey(ir), System.currentTimeMillis() + BANK_FETCH_BACKOFF_MS);
		return false;
	}

	private void noteBankFetchSucceeded(ItemRequirement ir) {
		bankFetchFailures.remove(fetchKey(ir));
		bankFetchBackoffUntil.remove(fetchKey(ir));
	}

	private static final int[] BURNT_MEAT_SOURCES = {ItemID.COOKED_MEAT, ItemID.RAW_BEEF, ItemID.RAW_RAT_MEAT,
			ItemID.RAW_BEAR_MEAT};
	private static final WorldPoint LUMBRIDGE_RANGE = new WorldPoint(3211, 3215, 0);

	/** Burnt meat is not on the Grand Exchange: get (or buy) meat and burn it on a range or fire. */
	private boolean makeBurntMeat() {
		Microbot.status = "Quest helper: burning meat";
		Integer meatId = null;
		for (int id : BURNT_MEAT_SOURCES) {
			if (Rs2Inventory.contains(id)) {
				meatId = id;
				break;
			}
		}
		if (meatId == null) {
			List<Integer> ids = new ArrayList<>();
			for (int id : BURNT_MEAT_SOURCES) {
				ids.add(id);
			}
			return acquireMissingTradableItems(new ArrayList<>(java.util.Collections.singletonList(
					new ItemRequirement("Cooked meat", ids, 1))));
		}
		Rs2TileObjectModel heat = null;
		for (String name : new String[]{"Fire", "Range", "Cooking range"}) {
			WorldPoint here = Rs2Player.getWorldLocation();
			heat = Microbot.getRs2TileObjectCache().query()
					.withName(name).within(here, 15).toListOnClientThread().stream()
					.filter(o -> o.getWorldLocation() != null && o.getWorldLocation().getPlane() == here.getPlane())
					.min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(here)))
					.orElse(null);
			if (heat != null) {
				break;
			}
		}
		if (heat == null) {
			if (LUMBRIDGE_RANGE.distanceTo(Rs2Player.getWorldLocation()) > 3) {
				Rs2Walker.walkTo(LUMBRIDGE_RANGE, 2);
			}
			return true;
		}
		if (Rs2Inventory.use(meatId) && heat.click()) {
			sleepUntil(() -> Rs2Inventory.contains(ItemID.BURNT_MEAT), 6_000);
		}
		return true;
	}

	/** Only true once the bank has been opened this session, so an unknown bank falls back to the old behaviour. */
	private boolean isHeldInBank(ItemRequirement itemRequirement) {
		return itemRequirement.checkTotalMatchesInContainers(QuestContainerManager.getBankData()) > 0;
	}

	private boolean withdrawNonTradablesFromBank(List<ItemRequirement> inBank) {
		Microbot.status = "Quest helper: fetching " + inBank.get(0).getName() + " from the bank";

		if (!Rs2Bank.isOpen()) {
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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
			// the bank's items load a moment after it opens: a count of 0 straight away proves nothing
			if (isHeldInBank(ir)) {
				sleepUntil(() -> ir.getAllIds().stream().anyMatch(id -> id != null && id > 0 && Rs2Bank.count(id) > 0), 3_000);
			}
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
				if (noteBankFetchFailed(ir, "the bank shows none of it")) {
					return true;
				}
				continue;
			}
			qty = roomFor(ir, qty);
			if (qty <= 0) {
				if (noteBankFetchFailed(ir, "no room in the inventory")) {
					return true;
				}
				continue;
			}

			Microbot.status = "Withdrawing " + ir.getName() + " x" + qty;
			final int before = inventoryQuantity(ir);
			final BooleanSupplier landed = () -> inventoryQuantity(ir) > before
					|| isFilledToCarryCapacity(ir) || (before == 0 && hasItemInInventoryOrEquipped(ir));
			Rs2Bank.withdrawX(idToWithdraw, qty);
			if (sleepUntil(landed::getAsBoolean, 5_000)) {
				noteBankFetchSucceeded(ir);
			} else {
				if (noteBankFetchFailed(ir, "the withdrawal did not land")) {
					return true;
				}
			}
		}

		return true;
	}

	/**
	 * With the bank open, how much of a withdrawal fits in the inventory. Banks what the quest does not need first when
	 * the free slots are short, so a withdrawal never waits on a full inventory. 0 means no room even after that.
	 */
	private int roomFor(ItemRequirement ir, int qty) {
		if (qty <= 0) {
			return 0;
		}
		boolean stackable = isStackable(ir);
		boolean held = inventoryQuantity(ir) > 0;
		int need = QuestInventoryRules.slotsNeeded(stackable, held, qty);
		for (int attempt = 0; attempt < 4 && Rs2Bank.isOpen() && Rs2Inventory.emptySlotCount() < need; attempt++) {
			int before = Rs2Inventory.emptySlotCount();
			clearInventoryForQuest();
			sleepUntil(() -> Rs2Inventory.emptySlotCount() > before, 1_500);
			if (Rs2Inventory.emptySlotCount() < need) {
				ensureFreeSlotInBank(java.util.Collections.singletonList(ir));
			}
			if (Rs2Inventory.emptySlotCount() <= before) {
				break;
			}
		}
		int fit = QuestInventoryRules.fitQuantity(stackable, held, qty, Rs2Inventory.emptySlotCount());
		if (fit < qty) {
			Microbot.log("Quest helper: room for " + fit + " of " + qty + " " + ir.getName() + " in the inventory",
					Level.WARN);
		}
		return fit;
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
		// the player's own consumables stay unless nothing else frees a slot for the quest item
		Predicate<Rs2ItemModel> isKept = item -> keep.contains(item.getId()) || combat.isUserBroughtProtected(item);
		if (Rs2Bank.depositAllExcept(isKept)) {
			return true;
		}
		Microbot.log("Quest helper: no other slot to free, banking your own supplies for a quest item", Level.WARN);
		Predicate<Rs2ItemModel> questOnly = item -> keep.contains(item.getId());
		return Rs2Bank.depositAllExcept(questOnly);
	}

	private boolean acquireMissingTradableItems(List<ItemRequirement> missing) {
		return acquireMissingTradableItems(missing, false);
	}

	/**
	 * @param stockpile true for the preflight: every requirement is bought in full (it is already the shortfall),
	 *                  nothing is withdrawn to the inventory, and purchases stay in the bank
	 */
	private boolean acquireMissingTradableItems(List<ItemRequirement> missing, boolean stockpile) {
		return BUY_GUARD.callOrElse(() -> acquireMissingTradableItemsLocked(missing, stockpile), true);
	}

	private boolean acquireMissingTradableItemsLocked(List<ItemRequirement> missing, boolean stockpile) {
		if (!actionGate()) {
			return true;
		}
		if (!stockpile && queueRunner.hasApproval()) {
			List<String> wanted = missing.stream().map(ItemRequirement::getName).collect(Collectors.toList());
			if (!queueRunner.covers(wanted, 0)) {
				queueRunner.skipCurrent(queueRunner.refusal(wanted, 0));
				return true;
			}
		}
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
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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
				Microbot.log("Quest helper: " + ir.getName() + " is not tradable on the Grand Exchange, "
						+ "not buying it (the quest steps will obtain it in game)", Level.WARN);
				continue;
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
			int qty = roomFor(ir, Math.min(remainingQuantityNeeded(ir), Rs2Bank.count(idToWithdraw)));
			if (qty <= 0) {
				continue;
			}
			Microbot.status = "Withdrawing " + ir.getName() + " x" + qty;
			Rs2Bank.withdrawX(idToWithdraw, qty);
			if (!sleepUntil(() -> hasItemInInventoryOrEquipped(ir) || isFilledToCarryCapacity(ir), 2_000)) {
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
				ensureFreeSlotInBank(toBuy);
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
			if (!actionGate()) {
				return true;
			}
			int offerPrice = offerPrices.getOrDefault(ir, 0);
			int qty = buyQuantities.getOrDefault(ir, 0);
			int primaryId = buyPrimaryIds.getOrDefault(ir, -1);
			if (offerPrice <= 0 || qty <= 0 || primaryId == -1) {
				continue;
			}

			String canonicalName = canonicalItemName(primaryId);
			if (canonicalName == null || canonicalName.isEmpty() || "null".equalsIgnoreCase(canonicalName)) {
				Microbot.log("Quest helper: no in-game name for " + ir.getName() + ", skipping it (the steps will obtain it)",
						Level.WARN);
				continue;
			}

			Microbot.status = "Buying " + canonicalName + " x" + qty;
			if (Rs2GrandExchange.buyItem(canonicalName, offerPrice, qty)) {
				placedOffers++;
			} else {
				Microbot.log("Quest helper: could not place an offer for " + canonicalName + ", skipping it", Level.WARN);
			}
			sleep(800, 1500);
		}

		if (!actionGate()) {
			return true;
		}
		if (placedOffers == 0) {
			stopQuesterWithReason("Failed to place any Grand Exchange offers for missing quest items");
			return true;
		}

		final int maxBuyAttempts = 5;
		final int perAttemptWaitMs = 15_000;

		for (int attempt = 1; attempt <= maxBuyAttempts; attempt++) {
			if (!actionGate()) {
				return true;
			}
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
					long bankCoinsNow = bankCoinSnapshot();
					long take = QuestCoins.toWithdraw(costNeeded, retryInvCoins, bankCoinsNow);
					if (take < 0) {
						Rs2GrandExchange.abortAllOffers(false);
						stopQuesterWithReason(QuestCoins.shortMessage(canonicalName, newPrice, costNeeded,
								retryInvCoins, bankCoinsNow));
						return true;
					}
					if (!withdrawCoinsFromBank(take, costNeeded, toBuy)) {
						Rs2GrandExchange.abortAllOffers(false);
						stopQuesterWithReason(QuestCoins.shortMessage(canonicalName, newPrice, costNeeded,
								Rs2Inventory.itemQuantity(ItemID.COINS_995), bankCoinsNow));
						return true;
					}
					if (!actionGate()) {
						return true;
					}
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
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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
			// Nothing is withdrawn here: the items may be for several queued quests. Each quest withdraws its own at
			// its start, within the free slots, the carry cap and the reserved slots.
			if (!items.stream().allMatch(this::isHeldInBank)) {
				Microbot.log("Quest helper: purchased items are not in the bank yet; the next round counts them again",
						Level.WARN);
			}
			Rs2Bank.closeBank();
			sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
			return;
		}

		ensureFreeSlotInBank(items);
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

			qty = roomFor(ir, Math.min(qty, Rs2Bank.count(idToWithdraw)));
			if (qty <= 0) {
				continue;
			}
			Microbot.status = "Withdrawing " + ir.getName() + " x" + qty;
			Rs2Bank.withdrawX(idToWithdraw, qty);
			if (!sleepUntil(() -> hasItemInInventoryOrEquipped(ir) || isFilledToCarryCapacity(ir), 2_000)) {
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
				if (def != null && QuestInventoryRules.geTradable(id, def.isTradeable())) {
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
				if (def != null && QuestInventoryRules.geTradable(id, def.isTradeable())) {
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
		return step instanceof NpcStep && KillStepRule.isKillText(step.getText());
	}

	/** After the target dies, pick up the item the kill step is waiting for. */
	/** Whether the kill step lists an item it does not have yet: the kill is for its drop. */
	private boolean killStepWantsDrop(NpcStep step) {
		for (Requirement requirement : step.getRequirements()) {
			if (requirement instanceof ItemRequirement) {
				ItemRequirement item = (ItemRequirement) requirement;
				// the kill is for this item: a drop (not tradable) the player does not have yet
				if (!item.mustBeEquipped() && !hasItemRequirementOnPlayer(item) && !isItemRequirementTradable(item)) {
					return true;
				}
			}
		}
		return false;
	}

	private final Set<Integer> failedRoomItems = new HashSet<>();

	/**
	 * Coins and the item ids the rest of the quest still lists: the steps ahead and the quest's own requirements. With
	 * {@code strict} it returns null when the steps ahead cannot be read, since nothing can then be called unneeded.
	 */
	private Set<Integer> neededItemIds(QuestHelper quest, boolean strict) {
		Set<Integer> needed = new HashSet<>();
		needed.add(ItemID.COINS_995);
		List<DetailedQuestStep> ahead = quest == null ? null : QuestCombatDetector.remainingDetailedSteps(quest);
		if (strict && ahead == null) {
			return null;
		}
		if (ahead != null) {
			for (DetailedQuestStep ahead1 : ahead) {
				QuestCombatDetector.addItemIds(ahead1.getRequirements(), needed);
			}
		}
		if (quest != null) {
			QuestCombatDetector.addItemIds(quest.getItemRequirements(), needed);
		}
		return needed;
	}

	private static final long JUNK_WARN_INTERVAL_MS = 60_000;
	private long junkWarnedAtMs;
	private long junkIdleUntilMs;
	private String junkBankQuest;
	private boolean junkBankGaveUp;

	/**
	 * A full inventory blocks an object or NPC step that gains an item (a dig spot, a pan, a kill drop). Only when the
	 * game refused a pickup lately, or a kill step waits for its drop: drops what the quest and the player do not need,
	 * cheapest first (one slot, or a few after a refused pickup). Never an item the quest lists now or later, recommends
	 * or the step needs, anything the player started with (gear too) or set up for the fight, food, potions, teleports,
	 * a stack, a noted or untradeable item, an unpriced one, one worth {@link QuestJunkPicker#VALUABLE_VALUE} gp or
	 * more, or one with no Drop action. Without the starting inventory or the quest's step list it drops nothing.
	 *
	 * @return true when this tick was spent making room
	 */
	private boolean freeJunkForStep(DetailedQuestStep step, boolean killStepWantsDrop) {
		if (!Rs2Inventory.isFull()) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now < junkIdleUntilMs
				|| !QuestJunkPicker.pickupBlocked(now, Microbot.inventoryFullMessageAtMs, killStepWantsDrop)) {
			return false;
		}
		QuestHelper quest = getQuestHelperPlugin().getSelectedQuest();
		Set<Integer> protectedIds = quest == null || !combat.hasUserSnapshot() ? null : neededItemIds(quest, true);
		if (protectedIds == null) {
			return nothingToFree(now, "the starting inventory or the quest's steps are unknown", null);
		}
		QuestCombatDetector.addItemIds(step.getRequirements(), protectedIds);
		QuestCombatDetector.addItemIds(currentRequirements, protectedIds);
		QuestCombatDetector.addItemIds(quest.getItemRecommended(), protectedIds);
		net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile =
				quest.getQuest() == null ? null : activeProfile(quest);
		Set<String> supplies = QuestInventoryRules.selectedSupplyNames(profile == null ? null : profile.getSupplies());
		List<QuestJunkPicker.Item> slots = new ArrayList<>();
		Rs2Inventory.items().forEach(item -> {
			String name = item.getName();
			String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
			if (item.isFood() || QuestEquipSwap.isTeleportJewellery(name) || combat.isUserOrSetupItem(item)
					|| combat.isUserBroughtProtected(item) || QuestPotions.kindOfItem(name) != null
					|| lower.contains("teleport") || lower.contains("rune")
					|| supplies.contains(QuestInventoryRules.baseName(name))) {
				protectedIds.add(item.getId());
			}
			slots.add(new QuestJunkPicker.Item(item.getId(), name, item.getPrice(), item.isStackable(), item.isNoted(),
					item.isTradeable(), item.getAction("drop") != null));
		});
		int targetFree = QuestJunkPicker.pickupRefused(now, Microbot.inventoryFullMessageAtMs)
				? QuestJunkPicker.TARGET_FREE_SLOTS : 1;
		List<QuestJunkPicker.Item> junk = QuestJunkPicker.pickJunk(slots, protectedIds, Rs2Inventory.emptySlotCount(), targetFree);
		if (junk.isEmpty()) {
			return nothingToFree(now, "nothing is safe to drop", QuestJunkPicker.bankable(slots, protectedIds));
		}
		boolean droppedAny = false;
		for (QuestJunkPicker.Item item : junk) {
			int before = Rs2Inventory.emptySlotCount();
			Rs2Inventory.drop(item.id());
			// the game says nothing about a drop: only the slot count shows it went
			if (!sleepUntil(() -> Rs2Inventory.emptySlotCount() > before, 2_500)) {
				Microbot.log("Quest helper: dropping " + item.name() + " did not free a slot", Level.WARN);
				junkIdleUntilMs = System.currentTimeMillis() + QuestJunkPicker.IDLE_COOLDOWN_MS;
				break;
			}
			Microbot.log("Quest helper: inventory full, dropped " + item.name() + " (not needed by the quest)", Level.INFO);
			droppedAny = true;
		}
		return droppedAny;
	}

	/** Nothing may be dropped: backs off for a while, warns once a minute and banks valuables if that can help. */
	private boolean nothingToFree(long now, String why, List<QuestJunkPicker.Item> bankable) {
		junkIdleUntilMs = now + QuestJunkPicker.IDLE_COOLDOWN_MS;
		if (now - junkWarnedAtMs > JUNK_WARN_INTERVAL_MS) {
			junkWarnedAtMs = now;
			Microbot.log("Quest helper: inventory full and " + why, Level.WARN);
		}
		return bankable != null && bankValuablesForRoom(bankable);
	}

	/**
	 * Banks the valuable tradeable items nothing claims (never untradeables: they may be quest items). A trip that
	 * frees no slot is not tried again for the rest of the quest.
	 */
	private boolean bankValuablesForRoom(List<QuestJunkPicker.Item> bankable) {
		QuestHelper quest = getQuestHelperPlugin().getSelectedQuest();
		String questName = quest == null || quest.getQuest() == null ? null : quest.getQuest().getName();
		if (!java.util.Objects.equals(questName, junkBankQuest)) {
			junkBankQuest = questName;
			junkBankGaveUp = false;
		}
		if (bankable.isEmpty() || junkBankGaveUp || Rs2Combat.inCombat()) {
			return false;
		}
		Microbot.log("Quest helper: inventory full, banking " + bankable.stream().map(QuestJunkPicker.Item::name)
				.collect(Collectors.joining(", ")), Level.INFO);
		Microbot.status = "Quest helper: banking valuables to make room";
		int before = Rs2Inventory.emptySlotCount();
		if (!Rs2Bank.isOpen()) {
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
			if (!sleepUntil(Rs2Bank::isOpen, 15_000)) {
				Microbot.log("Quest helper: could not open the bank to make room; not trying again this quest", Level.WARN);
				junkBankGaveUp = true;
				return true;
			}
		}
		for (QuestJunkPicker.Item item : bankable) {
			Rs2Bank.depositAll(item.id());
		}
		if (!sleepUntil(() -> Rs2Inventory.emptySlotCount() > before, 3_000)) {
			Microbot.log("Quest helper: the bank trip freed no slot; not trying again this quest", Level.WARN);
			junkBankGaveUp = true;
		}
		if (!Rs2Bank.closeBank() || !sleepUntil(() -> !Rs2Bank.isOpen(), 3_000)) {
			Microbot.log("Quest helper: the bank did not close after making room", Level.WARN);
		}
		return true;
	}

	/**
	 * Frees one inventory slot when it is full: eats if hurt, otherwise drops junk (bones, vials, jugs...) or, at full
	 * health, food. Never a quest item, an untradeable or unpriced item, a noted item or a stack. A drop that did not
	 * free a slot is remembered and not tried again.
	 *
	 * @return true when this tick was spent making room
	 */
	private boolean makeRoom() {
		if (!Rs2Inventory.isFull()) {
			return false;
		}
		Set<Integer> needed = neededItemIds(getQuestHelperPlugin().getSelectedQuest(), false);
		boolean hurt = Rs2Player.getHealthPercentage() < 100;
		List<QuestMakeRoom.Slot> slots = new ArrayList<>();
		Rs2Inventory.items().forEach(item -> {
			if (!failedRoomItems.contains(item.getId())) {
				slots.add(new QuestMakeRoom.Slot(item.getId(), item.getName(), item.getPrice(), item.isFood(),
						needed.contains(item.getId()), item.isTradeable(), item.isNoted(), item.isStackable()));
			}
		});
		QuestMakeRoom.Decision decision = QuestMakeRoom.decide(0, hurt, slots);
		switch (decision.action) {
			case EAT:
				Microbot.log("Quest helper: inventory full, eating " + decision.slot.name + " to make room", Level.INFO);
				Rs2Inventory.interact(decision.slot.id, "Eat");
				return roomMade(decision.slot.id);
			case DROP:
				Microbot.log("Quest helper: inventory full, dropping " + decision.slot.name + " to make room", Level.INFO);
				Rs2Inventory.drop(decision.slot.id);
				return roomMade(decision.slot.id);
			case STUCK:
				Microbot.log("Quest helper: inventory full of quest items, cannot make room for a pickup", Level.WARN);
				return false;
			default:
				return false;
		}
	}

	/** Waits for the slot to free up; an item that did not go is remembered so it is not retried every tick. */
	private boolean roomMade(int itemId) {
		boolean freed = sleepUntil(() -> !Rs2Inventory.isFull(), 2_500);
		if (!freed) {
			failedRoomItems.add(itemId);
			Microbot.log("Quest helper: could not free a slot with item " + itemId + "; not trying it again", Level.WARN);
		}
		return freed;
	}

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
				if (id != null && id > 0 && lootGroundItem(id, 8, true)) {
					return true;
				}
			}
		}
		// a "Kill X for a key" step that lists no item: pick up the item its text names, when it lies near the kill
		String dropName = QuestKillDrops.dropName(step.getText());
		if (dropName != null && !Rs2Inventory.hasItem(dropName, true)) {
			Rs2TileItemModel drop = new Rs2TileItemQueryable().withName(dropName).within(8).nearestOnClientThread();
			// the item is guessed from the text: it never makes room by dropping things
			if (drop != null && lootGroundItem(drop.getId(), 8, false)) {
				return true;
			}
		}
		return false;
	}

	private static final Pattern STEP_USES_ITEM_TEXT = Pattern.compile(
			"\\b(bring|give|hand|use|deliver|show|buy|pay|purchase|bribe|trade|sell|fare|charter)\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * Whether a step-level item requirement must be on the player for the step to act. Equipped items, the step's
	 * icon item (use X on Y), highlighted items and steps whose text brings, gives, hands in, uses, delivers or shows
	 * the item qualify; plain carry-along requirements on ladders, stairs, doors and talk-to steps do not.
	 */
	static boolean stepNeedsItemRequirement(boolean stepHasIcon, String stepText, boolean mustBeEquipped, boolean highlighted) {
		return stepNeedsItemRequirement(stepHasIcon, stepText, mustBeEquipped, highlighted, false);
	}

	/**
	 * As above; {@code coinsForNpc} is a coins requirement on a step that deals with an NPC: you pay NPCs, whatever the
	 * step text says ("buy his boat").
	 */
	static boolean stepNeedsItemRequirement(boolean stepHasIcon, String stepText, boolean mustBeEquipped, boolean highlighted,
			boolean coinsForNpc) {
		if (mustBeEquipped || highlighted || stepHasIcon || coinsForNpc) {
			return true;
		}
		return stepText != null && STEP_USES_ITEM_TEXT.matcher(stepText).find();
	}

	private static String stepText(DetailedQuestStep questStep) {
		List<String> text = questStep.getText();
		return text == null ? "" : String.join(" ", text);
	}

	private List<Requirement> collectAllItemRequirements(DetailedQuestStep questStep) {
		List<Requirement> combined = new ArrayList<>(questStep.getRequirements());
		// Carry-along requirements on travel steps are informational; the step that uses the item fetches it
		combined.removeIf(req -> req instanceof ItemRequirement
				&& !stepNeedsItemRequirement(questStep.getIconItemID() != -1, stepText(questStep),
				((ItemRequirement) req).mustBeEquipped(), ((ItemRequirement) req).isHighlightInInventory(),
				questStep instanceof NpcStep && ((ItemRequirement) req).getAllIds().contains(ItemID.COINS_995)));

		// parts a combine on this step has used up are not missing: the step is about to advance on the product
		if (questStep == combinedOnStep) {
			combined.removeIf(req -> req instanceof ItemRequirement
					&& ((ItemRequirement) req).getAllIds().stream().anyMatch(combinedIds::contains)
					&& !hasItemRequirementOnPlayer((ItemRequirement) req));
		}

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
					// "any amount" requirements (quantity -1) count as one at a boss prep
					ItemRequirement toAdd = ir.getQuantity() < 1 && QuestBossPrep.bossLevel(questStep.getText()) >= 0
							? withQuantityOne(ir) : ir;
					if (toAdd != null) {
						combined.add(toAdd);
					}
				}
			}
			// the antifire for a dragon boss is a recommendation, not a requirement: a boss prep fetches it all the same
			if (QuestBossPrep.bossLevel(questStep.getText()) >= 0) {
				for (ItemRequirement antifire : recommendedAntifire(selectedQuest)) {
					if (seenIds.add(antifire.getId())) {
						combined.add(antifire);
					}
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

	/** A count-less requirement as one item; null for a placeholder with no real item id (id -1). */
	private static ItemRequirement withQuantityOne(ItemRequirement ir) {
		if (ir.getAllIds().stream().noneMatch(id -> id != null && id > 0)) {
			return null;
		}
		ItemRequirement one = ir.copy();
		one.setQuantity(1);
		return one;
	}

	private static final Set<Integer> ANTIFIRE_POTION_IDS = new HashSet<>(net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections.ANTIFIRE_POTIONS.getItems());

	/** The quest's recommended dragonfire protection (a potion), as one item: nothing else recommended is pulled in. */
	private static List<ItemRequirement> recommendedAntifire(QuestHelper quest) {
		List<ItemRequirement> out = new ArrayList<>();
		List<ItemRequirement> recommended = quest == null ? null : quest.getItemRecommended();
		if (recommended == null) {
			return out;
		}
		for (ItemRequirement ir : recommended) {
			if (ir != null && ir.getAllIds().stream().anyMatch(ANTIFIRE_POTION_IDS::contains)) {
				ItemRequirement one = ir.getQuantity() < 1 ? withQuantityOne(ir) : ir;
				if (one != null) {
					out.add(one);
				}
			}
		}
		return out;
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

	/** True when any item requirement of the step (or the quest) is not on the player. */
	private boolean hasUnmetItemRequirements(DetailedQuestStep questStep) {
		for (Requirement requirement : collectAllItemRequirements(questStep)) {
			if (requirement instanceof ItemRequirement
					&& !hasItemRequirementOnPlayer((ItemRequirement) requirement)
					&& !isFilledToCarryCapacity((ItemRequirement) requirement)) {
				return true;
			}
		}
		return false;
	}

	private static volatile String lastBlockedEquipmentNotice = "";

	/**
	 * A must-be-equipped requirement (a disguise, a key worn item) that is not on the player cannot be worked
	 * around by performing the step: the door/NPC refuses. Hold the step and say why, logging once per item.
	 */
	private boolean blockedByMissingEquipment(DetailedQuestStep questStep) {
		List<String> missing = new ArrayList<>();
		for (Requirement requirement : questStep.getRequirements()) {
			if (requirement instanceof ItemRequirement) {
				ItemRequirement item = (ItemRequirement) requirement;
				if (item.mustBeEquipped() && !hasItemRequirementOnPlayer(item)) {
					missing.add(item.getName() != null && !item.getName().isEmpty() ? item.getName() : "item " + item.getId());
				}
			}
		}
		if (missing.isEmpty()) {
			lastBlockedEquipmentNotice = "";
			return false;
		}
		String joined = String.join(", ", missing);
		Microbot.status = "Quest helper waiting: need to wear " + joined
				+ (config.obtainMissingItems() == QuestHelperConfig.ObtainMissingItemsOption.NO
				? " (buying is off)" : "");
		if (!joined.equals(lastBlockedEquipmentNotice)) {
			lastBlockedEquipmentNotice = joined;
			Microbot.log("Quest helper holding this step: required worn items are missing and could not be obtained: "
					+ joined, Level.WARN);
		}
		return true;
	}

	private boolean shouldObtainMissingItems() {
		if (Rs2Player.isIronman()) {
			return false;
		}
		Boolean queued = queueRunner.obtainOverride();
		if (queued != null) {
			return queued;
		}
		QuestHelperConfig.ObtainMissingItemsOption option = config.obtainMissingItems();
		if (config.fullyAutomated() && option == QuestHelperConfig.ObtainMissingItemsOption.ASK) {
			option = QuestHelperConfig.ObtainMissingItemsOption.YES;
		}
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
				JDialog parentDialog = new JDialog(clientFrameForPrompt());
				parentDialog.setAlwaysOnTop(true);
				parentDialog.setUndecorated(true);
				int choice;
				try {
					parentDialog.setLocationRelativeTo(parentDialog.getOwner());
					parentDialog.setVisible(true);
					parentDialog.toFront();
					choice = JOptionPane.showConfirmDialog(
							parentDialog,
							"The quest helper has detected missing items.\n\n" +
									"Would you like the quest helper to automatically obtain\n" +
									"missing items from the bank and Grand Exchange?",
							"Obtain Missing Items",
							JOptionPane.YES_NO_OPTION,
							JOptionPane.QUESTION_MESSAGE);
				} finally {
					parentDialog.dispose();
				}

				obtainItemsSessionChoice = (choice == JOptionPane.YES_OPTION);
			} finally {
				obtainItemsPromptInFlight.set(false);
			}
		});
	}

	private static java.awt.Frame clientFrameForPrompt() {
		java.awt.Frame best = null;
		for (java.awt.Frame frame : java.awt.Frame.getFrames()) {
			if (!frame.isShowing()) {
				continue;
			}
			if (frame.getTitle() != null && frame.getTitle().toLowerCase(java.util.Locale.ROOT).contains("runelite")) {
				return frame;
			}
			if (best == null) {
				best = frame;
			}
		}
		return best;
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
		int price = QuestPriceLookup.withRetry(() -> {
			WikiPrice priceData = Rs2GrandExchange.getRealTimePrices(itemId);
			if (priceData != null && priceData.buyPrice > 0 && priceData.buyPrice < Integer.MAX_VALUE) {
				return priceData.buyPrice;
			}
			return Rs2GrandExchange.getPrice(itemId);
		}, 3, 500L, ms -> sleep((int) ms), -1);
		if (price > 0) {
			return price;
		}
		int store = Microbot.getClientThread().runOnClientThreadOptional(() -> {
			ItemComposition def = Microbot.getClient().getItemDefinition(itemId);
			return def == null ? 0 : def.getPrice();
		}).orElse(0);
		int fallback = Math.max(QuestPriceLookup.FALLBACK_PRICE, store);
		Microbot.log("Quest helper: price lookup failed for item " + itemId + ", using fallback " + fallback + " gp", Level.WARN);
		return fallback;
	}

	private boolean isIdTradable(int id) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			ItemComposition def = Microbot.getClient().getItemDefinition(id);
			return def != null && QuestInventoryRules.geTradable(id, def.isTradeable());
		}).orElse(false);
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
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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
				if (!sleepUntil(() -> hasItemInInventoryOrEquipped(ir), 2_000)) {
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
		if (queueRunner.onStop(reason)) {
			return; // a queued quest is skipped, the queue goes on
		}
		Microbot.status = reason;
		Microbot.log("Quest helper stopped: " + reason, level);
		if (Microbot.getConfigManager() != null) {
			Microbot.getConfigManager().setConfiguration(
					QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", false);
		}
	}

	/** Landing check for withdrawn/purchased items: a must-be-worn item sits in the inventory until it is equipped. */
	private boolean hasItemInInventoryOrEquipped(ItemRequirement itemRequirement) {
		return itemRequirement.checkContainers(
				QuestContainerManager.getEquippedData(),
				QuestContainerManager.getInventoryData());
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
		return lootGroundItem(itemId, radius, false);
	}

	/** @param forKillDrop the item is the kill step's own drop; otherwise room is only made after the "no space" message */
	private boolean lootGroundItem(int itemId, int radius, boolean forKillDrop) {
		Rs2TileItemModel item = new Rs2TileItemQueryable()
				.withId(itemId)
				.within(radius)
				.nearest();

		if (item == null) {
			return false;
		}
		// "You don't have enough inventory space to hold that item" looped three clicks and sent the kill step back
		boolean noSpaceMessage = System.currentTimeMillis() - Microbot.inventoryFullMessageAtMs < 8_000;
		if (!Rs2Inventory.hasItem(itemId) && (forKillDrop || noSpaceMessage) && makeRoom()) {
			return true;
		}

		return item.click("");
	}

	// ---- quest planner queue ----

	private final QuestQueueRunner queueRunner = new QuestQueueRunner(new QuestQueueRunner.Host() {
		@Override
		public QuestHelperPlugin plugin() {
			return getQuestHelperPlugin();
		}

		@Override
		public boolean ironman() {
			return Rs2Player.isIronman();
		}

		@Override
		public boolean runQueuePreflight(List<net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile> remaining) {
			return QuestScript.this.runQueuePreflight(remaining);
		}

		@Override
		public boolean needsBlockedItems(QuestHelper helper,
				net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile) {
			return QuestScript.this.needsBlockedItems(helper, profile);
		}

		@Override
		public void rest(long untilMs) {
			breakEndsAtMs = untilMs;
		}

		@Override
		public boolean bankLeftovers(net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile current) {
			return bankLeftoversForQuest(current);
		}

		@Override
		public double fatigue() {
			return humanTiming == null ? 0 : humanTiming.fatigue();
		}

		@Override
		public void turnOff(String reason) {
			Microbot.status = reason;
			if (Microbot.getConfigManager() != null) {
				Microbot.getConfigManager().setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", false);
			}
		}
	});

	/**
	 * At a queued quest's start: banks what the inventory holds that this quest does not need and the user did not
	 * select (other quests' items, other quests' food). Coins, runes, this quest's items and its selected supplies stay.
	 *
	 * @return true while it is still working
	 */
	private boolean bankLeftoversForQuest(net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile) {
		QuestHelper helper = net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest
				.getByName(profile.getQuestName());
		if (helper == null || Rs2Player.isIronman()) {
			return false;
		}
		Set<Integer> needed = new HashSet<>();
		try {
			Microbot.getClientThread().runOnClientThreadOptional(() -> {
				helper.initializeRequirements();
				return true;
			});
			List<ItemRequirement> requirements = helper.getItemRequirements();
			if (requirements != null) {
				for (ItemRequirement requirement : requirements) {
					if (requirement != null) {
						for (Integer id : requirement.getAllIds()) {
							if (id != null) {
								needed.add(id);
							}
						}
					}
				}
			}
		} catch (RuntimeException e) {
			Microbot.log("Quest queue: could not read the items of " + profile.getQuestName() + ": " + e, Level.WARN);
			return false;
		}
		Set<String> supplies = QuestInventoryRules.selectedSupplyNames(profile.getSupplies());
		Predicate<Rs2ItemModel> leftover = item -> !QuestInventoryRules.keepAtQuestStart(item.getId(), item.getName(),
				needed, supplies);
		if (!Rs2Inventory.items(leftover).findAny().isPresent()) {
			return false;
		}
		Microbot.status = "Quest queue: banking items " + profile.getQuestName() + " does not need";
		if (!Rs2Bank.isOpen()) {
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
			sleepUntil(Rs2Bank::isOpen, 15_000);
			if (!Rs2Bank.isOpen()) {
				return true;
			}
		}
		Rs2Bank.depositAll(leftover);
		sleepUntil(() -> !Rs2Inventory.items(leftover).findAny().isPresent(), 3_000);
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		return true;
	}

	private int queuePreflightBankAttempts;
	private int queuePreflightBuyRounds;
	private boolean queuePreflightPlanned;

	/** The preflight shopping list of a quest (what the planner shows and the queue buys), empty when unknown. */
	public List<QuestPreflight.Need> preflightNeedsFor(QuestHelper quest) {
		QuestState state = Microbot.getClientThread()
				.runOnClientThreadOptional(() -> quest.getQuest().getState(Microbot.getClient())).orElse(null);
		if (state != QuestState.NOT_STARTED && state != QuestState.IN_PROGRESS) {
			return new ArrayList<>();
		}
		List<QuestPreflight.Need> needs = collectPreflightNeeds(quest, state);
		return needs == null ? new ArrayList<>() : needs;
	}

	/** What the quest window's combat tabs show for a quest. */
	public net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatSetupDialog.Context combatContextFor(
			QuestHelper quest) {
		return combat.contextFor(quest);
	}

	/** True when the quest needs items that may not be bought and that the player neither carries nor banks. */
	private boolean needsBlockedItems(QuestHelper helper,
			net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile) {
		if (profile.geAllowed() && !Rs2Player.isIronman()) {
			return false;
		}
		ItemAndLastUpdated bank = QuestContainerManager.getBankData();
		if (bank.getLastUpdated() < 0 && bank.getItems().length == 0) {
			return false; // bank never seen: cannot tell
		}
		List<QuestPreflight.Need> needs = preflightNeedsFor(helper);
		Map<Integer, Integer> owned = new HashMap<>();
		addContainer(owned, QuestContainerManager.getEquippedData());
		addContainer(owned, QuestContainerManager.getInventoryData());
		addContainer(owned, bank);
		return !QuestPreflight.compute(needs, owned).isEmpty()
				&& QuestPreflight.compute(needs, owned).stream().anyMatch(s -> s.missing > 0);
	}

	/**
	 * Once per queue run, before the first quest: counts what every queued quest (and its supplies) needs, pays for the
	 * shortfall at the Grand Exchange and banks it. The purchase was approved in the planner, so no dialog opens.
	 *
	 * @return true while it is still working
	 */
	private boolean runQueuePreflight(List<net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile> remaining) {
		return BUY_GUARD.callOrElse(() -> runQueuePreflightLocked(remaining), true);
	}

	private boolean runQueuePreflightLocked(
			List<net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile> remaining) {
		if (!config.preflightQuestItems() || Rs2Player.isIronman()) {
			return false;
		}
		ItemAndLastUpdated bank = QuestContainerManager.getBankData();
		QuestPreflight.setStatus(QuestPreflight.CHECKING);
		Microbot.status = "Quest queue: counting what the quests need";
		if (bank.getLastUpdated() < 0 && bank.getItems().length == 0) {
			if (queuePreflightBankAttempts >= PREFLIGHT_MAX_BANK_ATTEMPTS) {
				QuestPreflight.setStatus(QuestPreflight.DONE);
				return false;
			}
			queuePreflightBankAttempts++;
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
			if (sleepUntil(Rs2Bank::isOpen, 15_000)) {
				sleepUntil(() -> QuestContainerManager.getBankData().getLastUpdated() >= 0, 3_000);
				Rs2Bank.closeBank();
				sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
			}
			return true;
		}

		int globalMin = net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue.shared().defaultMinStamina();
		Map<String, QuestPreflight.Need> merged = new java.util.LinkedHashMap<>();
		for (net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile : remaining) {
			if (!profile.geAllowed()) {
				continue;
			}
			QuestHelper helper = net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest
					.getByName(profile.getQuestName());
			List<QuestPreflight.Need> needs = new ArrayList<>();
			if (helper != null) {
				needs.addAll(preflightNeedsFor(helper));
			}
			for (net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile.SupplyLine supply
					: profile.getSupplies().buyable(profile.effectiveMinStamina(globalMin))) {
				List<Integer> ids = new ArrayList<>(supply.ids);
				if (ids.isEmpty()) {
					int id = QuestCombatHandler.resolveItemId(supply.name);
					if (id > 0) {
						ids.add(id);
					}
				}
				if (!ids.isEmpty()) {
					needs.add(new QuestPreflight.Need(supply.name, ids, supply.qty, true));
				}
			}
			for (QuestPreflight.Need need : needs) {
				String key = need.name.toLowerCase();
				QuestPreflight.Need old = merged.get(key);
				merged.put(key, old == null ? need
						: new QuestPreflight.Need(old.name, old.ids, old.quantity + need.quantity, old.consumed || need.consumed));
			}
		}
		List<QuestPreflight.Need> needs = new ArrayList<>(merged.values());
		Map<Integer, Integer> onPlayer = new HashMap<>();
		addContainer(onPlayer, QuestContainerManager.getEquippedData());
		addContainer(onPlayer, QuestContainerManager.getInventoryData());
		Map<Integer, Integer> owned = new HashMap<>(onPlayer);
		addContainer(owned, bank);
		QuestPreflight.foldNoted(needs, this::notedVariantId, onPlayer, owned);

		List<ItemRequirement> toBuy = new ArrayList<>();
		for (QuestPreflight.Shortfall shortfall : QuestPreflight.compute(needs, owned)) {
			if (shortfall.missing > 0) {
				toBuy.add(new ItemRequirement(shortfall.name, shortfall.ids, shortfall.missing));
			}
		}
		if (toBuy.isEmpty()) {
			QuestPreflight.setStatus(QuestPreflight.DONE);
			return false;
		}
		if (!queuePreflightPlanned) {
			long total = 0;
			List<String> names = new ArrayList<>();
			for (ItemRequirement ir : toBuy) {
				int primaryId = tradablePrimaryId(ir);
				int price = primaryId == -1 ? -1 : fetchInstabuyReferencePrice(primaryId);
				if (price <= 0) {
					Microbot.log("Quest queue: no price for " + ir.getName() + ", not buying it up front", Level.WARN);
					continue;
				}
				total += (long) ir.getQuantity() * QuestPurchasePlan.offerPrice(price);
				names.add(ir.getName());
			}
			if (queueRunner.hasApproval() && !queueRunner.covers(names, total)) {
				Microbot.log("Quest queue: not buying up front, " + queueRunner.refusal(names, total), Level.WARN);
				QuestPreflight.setStatus(QuestPreflight.DONE);
				return false;
			}
			queueRunner.recordSpend(total);
			queuePreflightPlanned = true;
			Microbot.log(String.format("Quest queue: buying %s up front (~%,d gp)", describeItems(toBuy), total), Level.INFO);
		}
		if (++queuePreflightBuyRounds > PREFLIGHT_MAX_BUY_ROUNDS) {
			Microbot.log("Quest queue: gave up buying " + describeItems(toBuy), Level.WARN);
			QuestPreflight.setStatus(QuestPreflight.DONE);
			return false;
		}
		QuestPreflight.setStatus(QuestPreflight.BUYING);
		Microbot.status = "Quest queue: buying what the quests need";
		acquireMissingTradableItems(toBuy, true);
		return true;
	}

	// ---- one loop, pause, stop and the quest window ----

	private static boolean selectedIsCompleted(QuestHelper quest) {
		return Microbot.getClientThread().runOnClientThreadOptional(quest::isCompleted).orElse(true);
	}

	/** Marks the executor thread as the quest loop while {@code body} runs, so only its walks may cross water. */
	private static Runnable onQuestThread(Runnable body) {
		return () -> {
			QuestWalkRule.markQuestThread(true);
			try {
				body.run();
			} finally {
				QuestWalkRule.markQuestThread(false);
			}
		};
	}

	/** Wraps the main tick so only one tick runs at a time, even if a second loop was scheduled by mistake. */
	private Runnable guardedTick(Runnable body) {
		return TICK_GUARD.guard(body);
	}

	/** Cancels the loops of an earlier run() so a restart never leaves two of them going. */
	private void cancelLoops() {
		if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
			mainScheduledFuture.cancel(true);
		}
		if (combatSupportFuture != null) {
			combatSupportFuture.cancel(true);
			combatSupportFuture = null;
		}
		if (supervisorFuture != null) {
			supervisorFuture.cancel(true);
			supervisorFuture = null;
		}
	}

	/**
	 * Called between the actions of a long routine (buying, banking): waits while paused, and returns false when
	 * automation was stopped (or the thread interrupted), so the routine gives up at once.
	 */
	private boolean actionGate() {
		return QuestPause.awaitResume(() -> config != null && config.startStopQuestHelper(), 150);
	}

	/** Stop: the preflight goes back to idle and the plan of the run is dropped (idempotent, runs every stopped tick). */
	private void resetPreflightOnStop() {
		QuestPause.resume();
		QuestPreflight.resetToIdle();
		preflightQuestKey = null;
		preflightNonce = Integer.MIN_VALUE;
		preflightPlan = null;
		preflightFinished = false;
		purchaseApproved = false;
		queuePreflightBankAttempts = 0;
		queuePreflightBuyRounds = 0;
		queuePreflightPlanned = false;
		windowDone = true;
		windowQuestKey = null;
		queueRunner.setWindowResult(null, null);
		QuestPurchasePlan.setCurrent(null);
	}

	/** While automation is switched off, a main loop still inside the preflight or a Grand Exchange routine is interrupted. */
	private void interruptOnStop() {
		boolean stopRequested = config != null && !config.startStopQuestHelper();
		if (!stopRequested) {
			stopInterruptSent = false;
			return;
		}
		Thread running = TICK_GUARD.owner();
		if (!stopInterruptSent && running != null && running != Thread.currentThread()
				&& (QuestPreflight.isInProgress() || BUY_GUARD.isBusy())) {
			stopInterruptSent = true;
			Microbot.log("Quest helper: stopped, interrupting the buy in progress", Level.INFO);
			running.interrupt();
		}
	}

	/** Coins in the bank according to the last bank read, or -1 when the bank was never read. */
	private long bankCoinSnapshot() {
		ItemAndLastUpdated bank = QuestContainerManager.getBankData();
		if (bank.getLastUpdated() < 0 && bank.getItems().length == 0) {
			return -1;
		}
		long sum = 0;
		for (Item item : bank.getItems()) {
			if (item != null && item.getId() == ItemID.COINS_995) {
				sum += item.getQuantity();
			}
		}
		return sum;
	}

	/** Takes {@code take} gp out of the bank (bank first), then reopens the Grand Exchange. */
	private boolean withdrawCoinsFromBank(long take, long needed, List<ItemRequirement> toBuy) {
		if (take <= 0) {
			return true;
		}
		Microbot.status = String.format("Withdrawing %,d gp from the bank for the Grand Exchange retry", take);
		if (!Rs2Bank.isOpen()) {
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
			if (!sleepUntil(Rs2Bank::isOpen, 15_000)) {
				return false;
			}
		}
		if (!Rs2Bank.setWithdrawAsItem()) {
			Rs2Bank.closeBank();
			return false;
		}
		ensureFreeSlotInBank(toBuy);
		Rs2Bank.withdrawX(ItemID.COINS_995, (int) Math.min(Integer.MAX_VALUE, take));
		sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995) >= needed, 3_000);
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		if (Rs2Inventory.itemQuantity(ItemID.COINS_995) < needed) {
			return false;
		}
		if (!Rs2GrandExchange.isOpen()) {
			Rs2GrandExchange.walkToGrandExchange();
			Rs2GrandExchange.openExchange();
			sleepUntil(Rs2GrandExchange::isOpen, 15_000);
		}
		return Rs2GrandExchange.isOpen();
	}

	/** Drops the needs of supplies the player switched off for the Grand Exchange. */
	private void dropNoBuyNeeds(List<QuestPreflight.Need> needs,
			QuestHelper quest) {
		net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile = queueRunner.profileInForce();
		if (profile == null) {
			profile = net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue.shared()
					.savedProfile(quest.getQuest().getName());
		}
		if (profile == null) {
			return;
		}
		Set<String> blocked = profile.getSupplies().noBuyNames();
		if (blocked.isEmpty()) {
			return;
		}
		needs.removeIf(need -> blocked.contains(need.name.replaceAll("\\s*\\(\\d+\\)$", "").trim().toLowerCase()));
	}

	/** A short chat message that does not block anything. */
	private void announce(String message) {
		Microbot.log("Quest helper: " + message, Level.INFO);
		try {
			Microbot.getClientThread().invokeLater(() -> {
				if (Microbot.getClient() != null) {
					Microbot.getClient().addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
							"[Quest helper] " + message, null);
				}
			});
		} catch (RuntimeException e) {
			// chat is a convenience only
		}
	}

	/**
	 * Opens the quest window once for every quest that starts (the planner already did the review for a queue, but
	 * the window is quick: it is prefilled and Start accepts it). Skipped only when Fully automated is on, or the
	 * quest was set to "do not ask again". Waits, tick by tick, for the answer.
	 *
	 * @return true while the window is open and the quest must not act yet
	 */
	private boolean questWindowGate(QuestHelper quest) {
		int nonce = QuestCombatHandler.startNonce();
		String key = quest.getQuest().name();
		if (nonce != windowNonce || !key.equals(windowQuestKey)) {
			windowNonce = nonce;
			windowQuestKey = key;
			windowAnswer.set(null);
			queueRunner.setWindowResult(null, null);
			net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile known = queueRunner.current();
			if (known == null) {
				known = net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue.shared()
						.savedProfile(quest.getQuest().getName());
			}
			boolean skip = config.fullyAutomated() || (known != null && !known.isAskSupplies());
			QuestPurchasePlan.consumeOverride(); // the Agent Server confirmPurchases override never hides the window
			if (skip) {
				windowDone = true;
				return false;
			}
			windowDone = false;
			final int mine = nonce;
			Microbot.status = "Quest helper: review the quest window";
			net.runelite.client.plugins.microbot.questhelper.planner.QuestPlannerLauncher.openForQuest(
					quest.getQuest().getName(), result -> {
						if (mine == windowNonce) {
							windowAnswer.set(result == null ? "cancelled" : result);
						}
					});
			return true;
		}
		if (windowDone) {
			return false;
		}
		Object answer = windowAnswer.get();
		if (answer == null) {
			Microbot.status = "Quest helper: waiting for the quest window";
			return true;
		}
		windowDone = true;
		if (!(answer instanceof net.runelite.client.plugins.microbot.questhelper.planner.QuestWindow.Result)) {
			stopQuesterWithReason("Quest cancelled in the quest window");
			return true;
		}
		net.runelite.client.plugins.microbot.questhelper.planner.QuestWindow.Result result =
				(net.runelite.client.plugins.microbot.questhelper.planner.QuestWindow.Result) answer;
		if (!result.profiles.isEmpty()) {
			queueRunner.setWindowResult(result.profiles.get(0), result.approval);
		}
		if (result.skipBuying) {
			QuestPreflight.skipNextStart(true);
		}
		return false;
	}

	// ---- carry rules: forbidden items, restricted steps and stamina potions ----

	private final Map<String, List<net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction>>
			questRestrictionCache = new HashMap<>();
	private String carryEnforcedKey = "";
	private final Set<String> carryWarned = new HashSet<>();

	/** The profile the running quest follows: the queue's, else the one saved for it, else none. */
	private net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile activeProfile(QuestHelper quest) {
		net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile = queueRunner.current();
		if (profile != null) {
			return profile;
		}
		return net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue.shared()
				.savedProfile(quest.getQuest().getName());
	}

	/**
	 * Keeps stamina potions and forbidden items in line with the step: banks what the step forbids (empty inventory,
	 * no food, no weapons or armour, named items), leaves the stamina potions in the bank while a step forbids carrying
	 * them, and fetches them back only when a bank is open anyway. Never walks to a bank just for stamina potions.
	 *
	 * @return true when this tick was used
	 */
	private boolean handleCarryRules(QuestHelper quest, QuestStep step) {
		if (step == null) {
			return false;
		}
		net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile profile = activeProfile(quest);
		String enumName = quest.getQuest().name();
		List<net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction> questLevel =
				questRestrictionCache.computeIfAbsent(enumName, key ->
						net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.forQuest(key,
								net.runelite.client.plugins.microbot.questhelper.planner.QuestInfo.restrictionTexts(quest)));
		List<String> stepTexts = new ArrayList<>();
		if (step.getText() != null) {
			stepTexts.addAll(step.getText());
		}
		if (step instanceof DetailedQuestStep && ((DetailedQuestStep) step).getRequirements() != null) {
			for (Requirement requirement : ((DetailedQuestStep) step).getRequirements()) {
				if (requirement instanceof ItemRequirement) {
					stepTexts.add(((ItemRequirement) requirement).getTooltip());
				}
			}
		}
		List<net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction> stepLevel =
				net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.fromTexts(stepTexts);

		int reserved = slotsReservedForStep(step);
		int staminaHeld = Rs2Inventory.count("stamina potion", false);
		boolean restricted = net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions
				.restrictsStamina(stepLevel, Rs2Inventory.emptySlotCount() + staminaHeld);
		combat.setCarryRestricted(restricted);
		if (Rs2Bank.isOpen() && (combat.depositStamina(reserved) || combat.topUpStamina(reserved))) {
			return true;
		}

		for (net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction restriction : questLevel) {
			if (!restriction.enforce && carryWarned.add(enumName + "|" + restriction.text)) {
				Microbot.log("Quest helper: " + quest.getQuest().getName() + ": " + restriction.text, Level.WARN);
			}
		}
		if (profile != null && !profile.isBankForbidden()) {
			return false;
		}
		List<net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction> enforce = new ArrayList<>();
		for (net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction restriction : stepLevel) {
			if (restriction.enforce && restriction.kind
					!= net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Kind.FREE_SLOTS) {
				enforce.add(restriction);
			}
		}
		for (net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction restriction : questLevel) {
			if (restriction.enforce && restriction.kind
					== net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Kind.ITEM) {
				enforce.add(restriction);
			}
		}
		if (enforce.isEmpty()) {
			return false;
		}
		String key = enumName + "|" + String.join("/", step.getText() == null ? new ArrayList<>() : step.getText());
		if (key.equals(carryEnforcedKey)) {
			return false;
		}
		Set<Integer> stepItems = new HashSet<>();
		if (step instanceof DetailedQuestStep && ((DetailedQuestStep) step).getRequirements() != null) {
			for (Requirement requirement : ((DetailedQuestStep) step).getRequirements()) {
				if (requirement instanceof ItemRequirement) {
					stepItems.addAll(((ItemRequirement) requirement).getAllIds().stream()
							.filter(id -> id != null && id > 0).collect(Collectors.toList()));
				}
			}
		}
		if (!carriesForbidden(enforce, stepItems)) {
			carryEnforcedKey = key;
			return false;
		}
		Microbot.status = "Quest helper: banking items this step does not allow";
		if (!Rs2Bank.isOpen()) {
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
			if (!sleepUntil(Rs2Bank::isOpen, 15_000)) {
				return true;
			}
		}
		for (net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction restriction : enforce) {
			switch (restriction.kind) {
				case EMPTY_INVENTORY:
					Rs2Bank.depositAll();
					break;
				case NO_WEAPONS_OR_ARMOUR:
					Rs2Bank.depositEquipment();
					break;
				case NO_FOOD:
					for (Rs2ItemModel food : Rs2Inventory.items(Rs2ItemModel::isFood).collect(Collectors.toList())) {
						if (!stepItems.contains(food.getId())) {
							Rs2Bank.depositAll(food.getId());
						}
					}
					break;
				case ITEM:
					if (!restriction.item.isEmpty()) {
						Rs2Bank.depositAll(restriction.item);
					}
					break;
				default:
					break;
			}
			sleep(300, 600);
		}
		Microbot.log("Quest helper: banked what this step forbids (" + enforce.get(0).text + ")", Level.INFO);
		carryEnforcedKey = key;
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		return true;
	}

	private boolean carriesForbidden(
			List<net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction> enforce,
			Set<Integer> stepItems) {
		for (net.runelite.client.plugins.microbot.questhelper.planner.QuestRestrictions.Restriction restriction : enforce) {
			switch (restriction.kind) {
				case EMPTY_INVENTORY:
					if (Rs2Inventory.items(i -> !stepItems.contains(i.getId())).findAny().isPresent()) {
						return true;
					}
					break;
				case NO_WEAPONS_OR_ARMOUR:
					if (!Rs2Equipment.items().isEmpty()) {
						return true;
					}
					break;
				case NO_FOOD:
					if (Rs2Inventory.items(i -> i.isFood() && !stepItems.contains(i.getId())).findAny().isPresent()) {
						return true;
					}
					break;
				case ITEM:
					if (!restriction.item.isEmpty() && Rs2Inventory.hasItem(restriction.item)) {
						return true;
					}
					break;
				default:
					break;
			}
		}
		return false;
	}

	@Override
	public void shutdown() {
		QuestWalkRule.setActive(false);
		DialogueChoiceProvider.clear();
		if (combatSupportFuture != null) {
			combatSupportFuture.cancel(true);
			combatSupportFuture = null;
		}
		if (supervisorFuture != null) {
			supervisorFuture.cancel(true);
			supervisorFuture = null;
		}
		stuckWatchdog.reset(System.currentTimeMillis());
		stuckStage = "NONE";
		combat.reset();
		endEscort();
		if (autoRetaliateDisabledByUs) {
			// shutdown may run on a caller that must not block, so restore off-thread
			Thread restorer = new Thread(this::restoreAutoRetaliate, "quest-restore-retaliate");
			restorer.setDaemon(true);
			restorer.start();
		}
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
        net.runelite.client.plugins.microbot.questhelper.planner.QuestWindow.dismiss();
        QuestPurchasePlan.setCurrent(null);
        lastBlockedEquipmentNotice = "";
    }

    public boolean applyStep(QuestStep step) {
        if (step == null) return false;
        // a new step forgets which instance passages the scene walker used for the last one
        net.runelite.client.plugins.microbot.util.walker.Rs2SceneWalker.onQuestStep(
                step.getText() == null ? "" : String.join(" ", step.getText()));

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
        if (killStep && killStepWantsDrop(step) && makeRoom()) {
            return true; // a free slot first: the drop could not be picked up otherwise
        }
        if (freeJunkForStep(step, killStep && killStepWantsDrop(step))) {
            return true;
        }
        if (killStep && lootKillDrop(step)) {
            return true;
        }

        List<Rs2NpcModel> npcs = step.getNpcs().stream()
                .map(Rs2NpcModel::new)
                .collect(Collectors.toList());
        if (killStep) {
            // a dying NPC (health ratio 0, or flagged dead) can no longer be attacked, and one that is gone has no name
            npcs = npcs.stream().filter(n -> n.getName() != null && !n.isDead() && n.getHealthRatio() != 0)
                    .collect(Collectors.toList());
        }
        final boolean kittenSearch = isKittenSearchStep(step);
        if (kittenSearch) {
            // The kitten crates: those showing "Mew!" first, nearest first, and never wander to the others while
            // some show it. Only this step: other many-target steps (shop keepers behind counters, wandering
            // citizens) must not be held to it.
            WorldPoint me = Rs2Player.getWorldLocation();
            npcs = QuestObjectApproach.orderForSearch(npcs, QuestScript::showsMew,
                    Comparator.comparingInt(n -> me.distanceTo(n.getTemplateWorldLocation())));
        }
        if (killStep) {
            // a target another player is fighting answers "Someone else is fighting that"
            List<Rs2NpcModel> free = npcs.stream().filter(n -> !isFightingAnotherPlayer(n)).collect(Collectors.toList());
            if (!free.isEmpty()) {
                npcs = free;
            }
        }
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
                    Comparator.comparing(x -> Rs2Player.getWorldLocation().distanceTo(x.getTemplateWorldLocation())),
                    System.currentTimeMillis());
        }

        // The step tracked nothing (wrong/cutscene-only id, NPC not yet scanned): look the NPC up by name
        // near the step's point instead of silently standing there.
        if (npc == null && npcs.isEmpty()) {
            npc = findNpcByStepName(step);
        }

        // canReach() pathfinds through closed doors (the walker opens them en route), so canReach==true
        // doesn't mean a direct click will succeed. Require line-of-sight too, or we walk instead.
        // A kill target already in melee range is attacked even when pathfinding says "unreachable" (arena gates,
        // fight cages): otherwise we walk at it forever while it hits us and never click Attack.
        boolean npcOnScreen = npc != null && npc.getLocalLocation() != null && Rs2Camera.isTileOnScreen(npc.getLocalLocation());
        int npcDistance = npc == null || npc.getTemplateWorldLocation() == null
                ? Integer.MAX_VALUE : npc.getTemplateWorldLocation().distanceTo(Rs2Player.getWorldLocation());
        // A kill target in sight is attacked at once, on screen or not: walking back to the step's tile with the
        // monkeys right there only ever took the player away from them.
        boolean killClickFailed = npc != null && npc.getIndex() == failedClickNpcIndex
                && System.currentTimeMillis() < failedClickUntilMs;
        boolean closeKillTarget = killStep && !killClickFailed && npc != null && npc.getLocalLocation() != null
                && npc.getTemplateWorldLocation() != null
                && KillStepRule.attackAtOnce(npc.getTemplateWorldLocation().distanceTo(Rs2Player.getWorldLocation()),
                npc.hasLineOfSight());
        boolean npcKnown = npc != null && npc.getLocalLocation() != null;
        if (npcKnown && kittenSearch && !Microbot.getClient().isInInstancedRegion()
                && !standsBesideWithoutWall(npc.getWorldLocation())) {
            // Search each from a tile that touches it with no wall or fence between: a crate on the far side of a wall
            // answers "I can't reach that!" however close it looks.
            WorldPoint at = npc.getTemplateWorldLocation();
            WorldPoint stand = chooseObjectApproachTile(at, Collections.singletonList(at));
            if (stand == null) {
                npcsHandled.add(npc.getIndex()); // no way to reach it: skip it
                return false;
            }
            Rs2Walker.walkTo(stand, 0);
            return false;
        }
        boolean clickFailedRecently = npcKnown && npc.getIndex() == failedClickNpcIndex
                && System.currentTimeMillis() < failedClickUntilMs;
        boolean inSight = npcKnown && npc.hasLineOfSight();
        boolean reachable = inSight && !clickFailedRecently && npcDistance > QuestNpcApproach.POINT_BLANK
                && Rs2Walker.canReach(npc.getTemplateWorldLocation());
        if (npcKnown && (QuestNpcApproach.decide(inSight, reachable, npcDistance, clickFailedRecently)
                == QuestNpcApproach.Move.INTERACT
                || closeKillTarget || (npcOnScreen && Microbot.getClient().isInInstancedRegion()))) {
            Rs2Walker.clearWalkingRoute("quest-helper:npc-step-visible-interact");

            if (killStep && npcHasAction(npc, "Attack")) {
                // Only ever the step's target: re-target it when the player is fighting something else.
                // inCombat() is true when the target hit us first, so test the interaction target instead.
                long now = System.currentTimeMillis();
                if (KillStepRule.shouldClickAttack(isFightingNpc(npc), now - lastKillAttackClickMs, KILL_ATTACK_MIN_INTERVAL_MS)) {
                    lastKillAttackClickMs = now;
                    final Rs2NpcModel attackTarget = npc;
                    boolean attacked = clickVerified(step, () -> attackTarget.click("Attack"),
                            () -> Rs2Camera.turnTo(attackTarget.getNpc()), "NPC Attack");
                    if (!attacked) {
                        // in sight across water or a fence, or taken by someone else: walk to it instead of clicking on
                        failedClickNpcIndex = attackTarget.getIndex();
                        failedClickUntilMs = System.currentTimeMillis() + NPC_CLICK_FAILURE_WALK_MS;
                    }
                }
                return true;
            }

            if (step instanceof NpcEmoteStep) {
                performEmote(((NpcEmoteStep) step).getEmote());
                return true;
            }

            if (step.getIconItemID() == -1 && hasNoActions(npc)) {
                if (npc.getTemplateWorldLocation().distanceTo(Rs2Player.getWorldLocation()) > 3) {
                    Rs2Walker.walkTo(npc.getTemplateWorldLocation(), 2);
                }
                return true;
            }

            var itemId = step.getIconItemID();
            final Rs2NpcModel clicked = npc;
            boolean worked;
            if (itemId != -1) {
                // the item is selected inside the click, so the verified retry selects it again
                worked = clickVerified(step, () -> Rs2Inventory.use(itemId) && clicked.click(""),
                        () -> Rs2Camera.turnTo(clicked.getNpc()), "NPC");
            } else {
                String option = chooseCorrectNPCOption(step, npc);
                worked = clickVerified(step, () -> clicked.click(option), () -> Rs2Camera.turnTo(clicked.getNpc()),
                        "NPC " + option);
            }
            if (!worked) {
                // two clicks did nothing: walk to this NPC for a while instead of clicking it every tick
                failedClickNpcIndex = clicked.getIndex();
                failedClickUntilMs = System.currentTimeMillis() + NPC_CLICK_FAILURE_WALK_MS;
            }
            if (itemId == -1) {
                lastTalkedNpcId = npc.getId();
                if (step.isAllowMultipleHighlights()) {
                    multiNpcTarget.commit(step, npc.getIndex(), System.currentTimeMillis());
                }
            }

            if (step.isAllowMultipleHighlights()) {
                npcsHandled.add(npc.getIndex());
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (npc != null && npc.getLocalLocation() != null && !npcOnScreen) {
            Rs2Walker.walkTo(npc.getTemplateWorldLocation(), 2);
        } else if (npc != null && (!npc.hasLineOfSight() || !Rs2Walker.canReach(npc.getTemplateWorldLocation()))) {
            Rs2Walker.walkTo(npc.getTemplateWorldLocation(), 2);
        } else {
            if (step.getDefinedPoint().getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) > 3) {
                Rs2Walker.walkTo(step.getDefinedPoint().getWorldPoint(), 2);
                return false;
            }
        }
        return true;
    }


    private static final int KILL_AREA_RADIUS = 12;
    private static final long KILL_ATTACK_MIN_INTERVAL_MS = 1_200;
    private long lastKillAttackClickMs = 0;
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
        WorldPoint at = npc.getTemplateWorldLocation();
        return at != null && home.getPlane() == at.getPlane() && home.distanceTo(at) > KILL_AREA_RADIUS;
    }

    private boolean npcHasAction(Rs2NpcModel npc, String action) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (composition == null || composition.getActions() == null) {
                return false;
            }
            for (String candidate : composition.getActions()) {
                if (action.equalsIgnoreCase(candidate)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean isFightingNpc(Rs2NpcModel npc) {
        Actor interacting = Rs2Player.getInteracting();
        return interacting instanceof NPC && ((NPC) interacting).getIndex() == npc.getIndex();
    }

    /** Opens the Emotes tab, waits for it, and performs the emote. Returns true once the emote was sent. */
    private boolean performEmote(net.runelite.client.plugins.microbot.questhelper.steps.emote.QuestEmote emote) {
        if (emote == null) {
            return false;
        }
        if (!Rs2Tab.switchTo(InterfaceTab.EMOTES)) {
            return false;
        }
        if (!sleepUntil(() -> Rs2Tab.isCurrentTab(InterfaceTab.EMOTES) && Rs2Widget.isWidgetVisible(ComponentID.EMOTES_EMOTE_CONTAINER), 3_000)) {
            return false;
        }
        Object[] found = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Rs2Widget.getWidget(ComponentID.EMOTES_EMOTE_CONTAINER);
            Widget[] children = container == null ? null : container.getDynamicChildren();
            if (children == null) {
                return null;
            }
            for (Widget emoteWidget : children) {
                if (emoteWidget != null && emoteWidget.getSpriteId() == emote.getSpriteId()) {
                    int id = emoteWidget.getOriginalX() / 42 + ((emoteWidget.getOriginalY() - 6) / 49) * 4;
                    return new Object[]{id, emoteWidget.getText()};
                }
            }
            return null;
        }).orElse(null);
        if (found == null) {
            return false;
        }
        Microbot.doInvoke(new NewMenuEntry()
                        .option("Perform")
                        .target((String) found[1])
                        .identifier(1)
                        .type(MenuAction.CC_OP)
                        .param0((Integer) found[0])
                        .param1(ComponentID.EMOTES_EMOTE_CONTAINER)
                , new Rectangle(0, 0, 1, 1));
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue(), 2_000);
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
                        .thenComparingInt(n -> n.getTemplateWorldLocation() == null ? Integer.MAX_VALUE : me.distanceTo(n.getTemplateWorldLocation())))
                .orElse(null);
    }

    private static final int FALLBACK_NPC_RADIUS = 10;
    private static final long NO_NPC_LOG_INTERVAL_MS = 30_000;
    private final Map<Integer, String> npcNameById = new HashMap<>();
    private static final int MISSING_LOG_RADIUS = 8;
    private static final long MISSING_LOG_IDLE_MS = 10_000;
    private long missingIdleSince = 0;

    /** True when a missing NPC/object is worth logging: player within 8 tiles on the same plane, or (no point) idle for 10 s. */
    private boolean shouldLogMissingEntity(WorldPoint point) {
        long now = System.currentTimeMillis();
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            missingIdleSince = 0;
            return false;
        }
        if (point != null) {
            WorldPoint me = Rs2Player.getWorldLocation();
            return me != null && me.distanceTo(point) <= MISSING_LOG_RADIUS;
        }
        if (missingIdleSince == 0) {
            missingIdleSince = now;
        }
        return now - missingIdleSince >= MISSING_LOG_IDLE_MS;
    }

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
        if (found == null && shouldLogMissingEntity(point)) {
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
    private int failedClickNpcIndex = -1;
    private long failedClickUntilMs;
    private static final long NPC_CLICK_FAILURE_WALK_MS = 10_000;

    private boolean hasNoActions(Rs2NpcModel npc) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (composition == null || composition.getActions() == null) {
                return false;
            }
            return Arrays.stream(composition.getActions()).noneMatch(x -> x != null && !x.isEmpty());
        }).orElse(false);
    }

    /** Keeps a combined step on its sub-step for a couple of ticks so a flipping condition cannot ping-pong targets. */
    private final StepCommitment<QuestStep> conditionalCommitment = new StepCommitment<>(2);

    public boolean applyObjectStep(ObjectStep step) {
        if (freeJunkForStep(step, false)) {
            return true; // a full inventory would swallow the step's pickup
        }
        // a step without a point (Cut off Sourhog's foot) has no defined point at all: it uses whatever object is visible
        final WorldPoint definedWp = step.getDefinedPoint() == null ? null : step.getDefinedPoint().getWorldPoint();
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
            // Each object once, reachable ones first, nearest first; then round again.
            WorldPoint me = Rs2Player.getWorldLocation();
            object = QuestObjectApproach.nextToSearch(stepObjects, x -> objectsHandeled.contains(x.getHash()),
                    Comparator.comparingInt((Rs2TileObjectModel x) -> x.isReachable() ? 0 : 1)
                            .thenComparingInt(x -> me.distanceTo(x.getTemplateWorldLocation())));
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

            // the tile lookup works on the object's raw tile; its results are converted to template space
            var view = Microbot.getRs2PlayerStateCache().getLocalPlayerWorldView();
            for (var rawTile : Rs2Tile.getWalkableTilesAroundTile(object.getWorldLocation(), unreachableTargetCheckDist)) {
                final WorldPoint tile = InstanceCoordinates.toTemplate(view, rawTile);
                if (tileObjects.stream().noneMatch(x -> x.getTemplateWorldLocation().equals(tile))) {
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
        if (definedWp != null && Rs2Player.getWorldLocation().distanceTo2D(definedWp) > 1
                && (object == null || !isInteractableFromReachableTile(step, object))) {
            WorldPoint stepLocation = object == null ? definedWp : object.getTemplateWorldLocation();
            WorldPoint targetTile = chooseObjectApproachTile(stepLocation, objectFootprint(object, stepLocation));
            if (targetTile == null) {
                // Nowhere to stand that the planner can reach: let the walker path as close as it can (it opens
                // closed doors and gates on the way) rather than asking it to stand on the object's own tile.
                Rs2Walker.walkTo(stepLocation, 2);
                return false;
            }

            // A tolerance of 3 tiles is only honest when the tile is on our side of any wall: through a wall it would
            // report "arrived" next to the object (Port Sarim back-room crate) and never walk round to the door.
            WalkerState walkState = Rs2Walker.walkWithState(targetTile, QuestObjectActions.arrivalTolerance(Rs2Tile.isTileReachable(targetTile)));

            var activeRoute = Rs2PathApi.getActiveRouteStatus();
            if (activeRoute.isPresent()) {
                var endpoint = activeRoute.getEndpoint();
                if (endpoint.isPresent()
                        && endpoint.get().distanceTo(definedWp) <= 1)
                    return false;
            } else if (walkState == WalkerState.ARRIVED || mainScheduledFuture.isCancelled() || !hasLineOfSightToObject(object))
                return false;
        }

        // Line of sight and the canvas say nothing about distance: a target 50 tiles away is walked to, not clicked
        if (object != null && withinClickRange(object)
                && (hasLineOfSightToObject(object) || Rs2Camera.isTileOnScreen(object.getLocalLocation())
                || object.getCanvasLocation() != null)) {
            Rs2Walker.clearWalkingRoute("quest-helper:object-step-interact");

            final Rs2TileObjectModel clickedObject = object;
            boolean clicked;
            if (itemId == -1) {
                String option = fallbackUsed ? chooseFallbackObjectOption(step, object) : chooseCorrectObjectOption(step, object);
                clicked = clickVerified(step, () -> clickedObject.click(option), () -> turnCameraUnlessRefused(clickedObject), "object " + option);
            } else {
                // the item is selected inside the click, so the verified retry selects it again
                clicked = clickVerified(step, () -> Rs2Inventory.use(itemId) && clickedObject.click(""),
                        () -> turnCameraUnlessRefused(clickedObject), "object");
            }
            // off screen or covered by another object with the camera turned every way: not clicked, so not handled.
            // Look at it from elsewhere and try again next tick.
            if (!clicked && Rs2ObjectOcclusion.recentlyRefused(clickedObject.getTileObject())) {
                walkForClearView(clickedObject);
                return false;
            }

            sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating());
            sleep(100);
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());
            // Marked whatever the verification said: an effect the check cannot see (a chat message, an object or an
            // inventory change) must not make a multi-object search step pick the same crate forever.
            objectsHandeled.add(object.getHash());
        } else if (object != null) {
            Rs2Walker.walkTo(object.getTemplateWorldLocation(), 1);
            return false;
        }

        return true;
    }

    private static final long CLEAR_VIEW_WALK_INTERVAL_MS = 15_000;
    private long lastClearViewWalkAt;

    /** The verified retry's camera turn is skipped for an object the click refused: the click already turned it every way. */
    private void turnCameraUnlessRefused(Rs2TileObjectModel object) {
        if (!Rs2ObjectOcclusion.recentlyRefused(object.getTileObject())) {
            turnCameraTo(object);
        }
    }

    /** Walks to a tile that sees a covered object from another side; at most once per interval. */
    private void walkForClearView(Rs2TileObjectModel object) {
        long now = System.currentTimeMillis();
        if (now - lastClearViewWalkAt < CLEAR_VIEW_WALK_INTERVAL_MS) {
            return;
        }
        WorldPoint spot = Rs2ObjectOcclusion.findClearViewTile(object.getTileObject());
        if (spot == null) {
            return;
        }
        lastClearViewWalkAt = now;
        Microbot.log("Quest helper: " + object.getName() + " is covered by another object, walking to " + spot + " for a clear view", Level.INFO);
        Rs2Walker.walkTo(spot, 0);
    }

    private static final int APPROACH_MAX_CANDIDATES = 8;

    /** Whether the NPC is in a fight with a player other than us. */
    private static boolean isFightingAnotherPlayer(Rs2NpcModel npc) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.Actor target = npc.getInteracting();
            return target instanceof net.runelite.api.Player && target != Microbot.getClient().getLocalPlayer();
        }).orElse(false);
    }

    /** Gertrude's Cat: the step that searches the "Mew!" crate NPCs for the kitten. */
    private static boolean isKittenSearchStep(NpcStep step) {
        return step.getNpcID() == net.runelite.api.gameval.NpcID.KITTENS_MEW;
    }

    /** Whether a kitten crate shows its "Mew!" overhead text right now (read on the client thread). */
    private static boolean showsMew(Rs2NpcModel npc) {
        String text = Microbot.getClientThread().runOnClientThreadOptional(npc::getOverheadText).orElse(null);
        return text != null && text.toLowerCase(java.util.Locale.ROOT).contains("mew");
    }

    /** Whether the player stands on a tile touching {@code target} with no wall, fence or bars on the shared edge. */
    private boolean standsBesideWithoutWall(WorldPoint target) {
        WorldPoint me = Rs2Player.getWorldLocation();
        return me != null && QuestObjectApproach.touchesWithOpenEdge(me, Collections.singletonList(target),
                collisionFlags(target.getPlane()));
    }

    /** Every tile the object stands on (a staircase covers several), or just its defined tile when unknown. */
    private static List<WorldPoint> objectFootprint(Rs2TileObjectModel object, WorldPoint fallbackTile) {
        WorldArea area = object == null ? null : object.getTemplateWorldArea();
        return area == null ? Collections.singletonList(fallbackTile) : area.toWorldPointList();
    }

    /** Rotates the camera toward an object, for the retry of a click that did nothing. */
    private static void turnCameraTo(Rs2TileObjectModel object) {
        if (object.getLocalLocation() != null) {
            Rs2Camera.turnTo(object.getLocalLocation());
        }
    }

    private static final long NO_EFFECT_LOG_INTERVAL_MS = 15_000;
    private long lastNoEffectLoggedAt = 0;

    /**
     * Sends a click and checks that it did something. A menu action that was sent is not an interaction that
     * happened: the server drops it when the target is out of reach, and the click still reports success.
     */
    private boolean clickVerified(QuestStep step, BooleanSupplier click, Runnable turnCamera, String what) {
        preClickDelay(step);
        WorldPoint before = Rs2Player.getWorldLocation();
        // a timed or repeated-click step keeps the quick mouse motion
        boolean timed = !QuestInteractionDelay.allowed(false, false, false, false,
                step == null ? null : String.join(" ", step.getText()));
        if (timed) {
            HumanMouseSpeed.enterTimeCritical();
        }
        boolean worked;
        try {
            worked = VerifiedInteraction.run(click, () -> interactionTookEffect(before), turnCamera,
                    System::currentTimeMillis, ms -> sleep((int) ms));
        } finally {
            if (timed) {
                HumanMouseSpeed.exitTimeCritical();
            }
        }
        long now = System.currentTimeMillis();
        if (!worked && now - lastNoEffectLoggedAt > NO_EFFECT_LOG_INTERVAL_MS) {
            lastNoEffectLoggedAt = now;
            Microbot.log("Quest helper: click on " + what + " had no effect after a camera turn and a retry", Level.WARN);
        }
        return worked;
    }

    private final Random delayRandom = new Random();

    /** Now and then a human pause before the click on an object or NPC; never in combat, dialogue or timed steps. */
    private void preClickDelay(QuestStep step) {
        boolean allowed = QuestInteractionDelay.allowed(Rs2Combat.inCombat(), QuestCombatDetector.isKillStep(step),
                Rs2Dialogue.isInDialogue(), Rs2Player.getHealthPercentage() < 60,
                step == null ? null : String.join(" ", step.getText()));
        long delay = QuestInteractionDelay.delayMs(humanTiming, delayRandom, allowed);
        if (delay > 0) {
            Microbot.status = "Quest helper: pausing (" + delay / 1000.0 + "s)";
            sleep((int) delay);
        }
    }

    private static boolean interactionTookEffect(WorldPoint before) {
        WorldPoint now = Rs2Player.getWorldLocation();
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue()) {
            return true;
        }
        if (before != null && now != null && now.getPlane() != before.getPlane()) {
            return true;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player me = Microbot.getClient().getLocalPlayer();
            return me != null && me.getInteracting() != null;
        }).orElse(false);
    }

    /**
     * The tile to stand on to use the object at {@code objectTile}: usable from there without a wall between, and
     * reachable by the real route planner, which opens closed doors and gates (a chain of them too). Does not need
     * the object itself to be in the scene, so a closed cupboard whose id differs from the step's still works.
     */
    private WorldPoint chooseObjectApproachTile(WorldPoint objectTile, List<WorldPoint> footprint) {
        WorldPoint me = Rs2Player.getWorldLocation();
        QuestObjectApproach.CollisionFlags flags = collisionFlags(objectTile.getPlane());
        // Only tiles that touch the object with no wall, fence, bars or gate edge between: line of sight alone
        // passes through a cage, which put the player outside it next to the crate. Every tile of the footprint
        // counts, so a staircase can be used from any of its sides.
        List<WorldPoint> candidates = footprint.stream()
                .flatMap(part -> Rs2Tile.getWalkableTilesAroundTile(part, 1).stream())
                .distinct()
                .filter(x -> QuestObjectApproach.touchesWithOpenEdge(x, footprint, flags))
                .sorted(Comparator.comparingInt((WorldPoint x) -> x.distanceTo2D(me)))
                .limit(APPROACH_MAX_CANDIDATES)
                .collect(Collectors.toList());
        return QuestObjectApproach.choose(objectTile, candidates, x -> true, x -> plannedRouteLength(me, x));
    }

    /** Collision flags of one floor of the loaded scene, by world tile; every edge reads as open when unavailable. */
    private static QuestObjectApproach.CollisionFlags collisionFlags(int plane) {
        Object[] scene = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldView view = Microbot.getClient().getTopLevelWorldView();
            if (view == null || view.getCollisionMaps() == null
                    || plane < 0 || plane >= view.getCollisionMaps().length || view.getCollisionMaps()[plane] == null) {
                return null;
            }
            return new Object[] {view.getCollisionMaps()[plane].getFlags(), view.getBaseX(), view.getBaseY()};
        }).orElse(null);
        if (scene == null) {
            return (x, y) -> 0;
        }
        int[][] flags = (int[][]) scene[0];
        int baseX = (Integer) scene[1];
        int baseY = (Integer) scene[2];
        return (x, y) -> {
            int sceneX = x - baseX;
            int sceneY = y - baseY;
            boolean inside = sceneX >= 0 && sceneY >= 0 && sceneX < flags.length && sceneY < flags[sceneX].length;
            return inside ? flags[sceneX][sceneY] : 0;
        };
    }

    private static int plannedRouteLength(WorldPoint from, WorldPoint to) {
        try {
            var route = Rs2PathApi.plan(Rs2RouteRequest.to(from, to).withRefreshTarget(to));
            var path = route.getPath();
            return !path.isEmpty() && path.get(path.size() - 1).equals(to)
                    ? path.size() : QuestObjectApproach.UNREACHABLE;
        } catch (RuntimeException ex) {
            return QuestObjectApproach.UNREACHABLE;
        }
    }

    private static final int FALLBACK_OBJECT_RADIUS = 4;
    private static final int TRACKED_OBJECT_RADIUS = 15;
    /** Furthest an object step clicks from; beyond it the step walks closer first. */
    private static final int CLICK_RANGE = 20;

    /** Scene-local distance check: the player and objects report different world coordinates inside an instance. */
    private static boolean withinClickRange(Rs2TileObjectModel object) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var me = Microbot.getClient().getLocalPlayer();
            return me != null && object.getLocalLocation() != null
                    && QuestObjectApproach.withinClickRange(me.getLocalLocation(), object.getLocalLocation(),
                    Microbot.getClient().getTopLevelWorldView().getPlane(), object.getPlane(), CLICK_RANGE);
        }).orElse(false);
    }
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
        return tracked.stream().noneMatch(o -> o.getTemplateWorldLocation() != null
                && o.getTemplateWorldLocation().getPlane() == point.getPlane()
                && o.getTemplateWorldLocation().distanceTo(point) <= TRACKED_OBJECT_RADIUS);
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
            // compared in template space, the space of the step's point and the player (an instance reports raw tiles)
            List<Rs2TileObjectModel> candidates = Microbot.getRs2TileObjectCache().query()
                    .withName(name)
                    .toListOnClientThread();
            candidates = candidates.stream()
                    .filter(o -> o.getTemplateWorldLocation() != null && o.getTemplateWorldLocation().getPlane() == point.getPlane()
                            && o.getTemplateWorldLocation().distanceTo(point) <= FALLBACK_OBJECT_RADIUS)
                    .sorted(Comparator.comparingInt((Rs2TileObjectModel o) ->
                                    wantedAction != null && o.hasAction(wantedAction) ? 0 : 1)
                            .thenComparingInt(o -> o.getTemplateWorldLocation().distanceTo(point)))
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
        if (shouldLogMissingEntity(point)) {
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
        String rightClick = QuestObjectActions.rightClickAction(step.getText());
        if (rightClick != null) {
            return rightClick;
        }
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

        // "Right-click <action>" in the step text, else an action named in the text, else the object's default.
        return QuestObjectActions.choose(step.getText(), actions);
    }

    private String chooseCorrectNPCOption(QuestStep step, Rs2NpcModel npc) {
        var npcComp = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getNpcDefinition(npc.getId()))
                .orElse(null);

        if (npcComp == null)
            return "Talk-to";

        var actions = npcComp.getActions();

        String trade = QuestDialogueRules.preferTradeAction(isShopPurchaseStep(step), actions);
        if (trade != null)
            return trade;

        // An action named in the text (or a synonym such as Steal-from for pickpocket), else Talk-to.
        return QuestNpcActions.choose(step.getText(), actions);
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
		if (InstanceCoordinates.isTopLevelInstance(Microbot.getClient().getTopLevelWorldView())) {
			// line of sight reads the scene's collision: both ends in instance space (the object already is)
			playerArea = Microbot.getClientThread().runOnClientThreadOptional(() -> WorldPoint.fromLocal(Microbot.getClient(),
					Microbot.getClient().getLocalPlayer().getLocalLocation()).toWorldArea()).orElse(playerArea);
		}

		return Microbot.getClient().getTopLevelWorldView() != null
				&& playerArea.hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), objectArea);
	}

    // ---- escort steps (an NPC follows the player; sprinting ahead makes it stop) ----
    private volatile long escortProgress;
    private volatile int lastTalkedNpcId = -1;
    private int escortActions;
    private int escortLastGap = Integer.MAX_VALUE;
    private volatile boolean escortActive;
    private Boolean escortRunBefore;
    private boolean escortAutoRunBefore;
    private WorldPoint escortFollowerLastSeen;

    private static boolean isEscortStep(DetailedQuestStep step) {
        if (step instanceof EscortStep) return true;
        if (step.getClass() != DetailedQuestStep.class || step.getDefinedPoint() == null
                || step.getIconItemID() != -1) return false;
        return EscortSupport.matchesEscortText(String.join(" ", step.getText()));
    }

    private void endEscortIfInactive(QuestStep activeStep) {
        if (escortActive && !(activeStep instanceof DetailedQuestStep && isEscortStep((DetailedQuestStep) activeStep))) {
            endEscort();
        }
    }

    private void endEscort() {
        if (!escortActive) return;
        escortActive = false;
        Microbot.enableAutoRunOn = escortAutoRunBefore;
        Boolean wasRun = escortRunBefore;
        escortRunBefore = null;
        escortFollowerLastSeen = null;
        escortLastGap = Integer.MAX_VALUE;
        if (wasRun != null && wasRun) {
            Thread restorer = new Thread(() -> Rs2Player.toggleRunEnergy(true), "quest-restore-run");
            restorer.setDaemon(true);
            restorer.start();
        }
    }

    private Rs2NpcModel findEscortFollower(DetailedQuestStep step) {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (step instanceof EscortStep && ((EscortStep) step).getFollowerIds().length > 0) {
            Rs2NpcModel byId = Microbot.getRs2NpcCache().query().withIds(((EscortStep) step).getFollowerIds())
                    .nearestOnClientThread(me, 25);
            if (byId != null) return byId;
        }
        Rs2NpcModel following = Microbot.getRs2NpcCache().query().within(me, 15)
                .where(Rs2NpcModel::isInteractingWithPlayer).nearestOnClientThread(me, 15);
        if (following != null) return following;
        if (lastTalkedNpcId != -1) {
            return Microbot.getRs2NpcCache().query().withId(lastTalkedNpcId).nearestOnClientThread(me, 25);
        }
        return null;
    }

    private boolean applyEscortStep(DetailedQuestStep step) {
        WorldPoint target = step.getDefinedPoint() == null ? null : step.getDefinedPoint().getWorldPoint();
        WorldPoint me = Rs2Player.getWorldLocation();
        if (target == null || me == null) return false;

        if (!escortActive) {
            escortActive = true;
            escortRunBefore = Rs2Player.isRunEnabled();
            escortAutoRunBefore = Microbot.enableAutoRunOn;
            escortActions = 0;
        }
        Microbot.enableAutoRunOn = false; // the walker must not turn run back on
        if (Rs2Player.isRunEnabled()) {
            Rs2Player.toggleRunEnergy(false);
        }

        Rs2NpcModel follower = findEscortFollower(step);
        WorldPoint followerAt = follower == null ? null : follower.getWorldLocation();
        if (followerAt != null) escortFollowerLastSeen = followerAt;
        boolean waitedOut = false;
        if (followerAt != null && me.distanceTo(followerAt) > EscortSupport.KEEP_CLOSE
                && me.distanceTo(followerAt) <= EscortSupport.LOST_DISTANCE) {
            final Rs2NpcModel f = follower;
            waitedOut = !sleepUntil(() -> {
                WorldPoint p = f.getWorldLocation();
                WorldPoint m = Rs2Player.getWorldLocation();
                return p != null && m != null && m.distanceTo(p) <= EscortSupport.KEEP_CLOSE;
            }, 6_000);
            followerAt = follower.getWorldLocation();
            me = Rs2Player.getWorldLocation();
            if (followerAt == null || me == null) return true;
        }
        int gap = followerAt == null ? Integer.MAX_VALUE : me.distanceTo(followerAt);
        if (gap < escortLastGap) escortActions++;
        escortLastGap = gap;

        boolean interacting = follower != null && follower.isInteractingWithPlayer();
        EscortSupport.Action action = EscortSupport.decide(me, followerAt, interacting, waitedOut, target);
        switch (action) {
            case ARRIVED:
                return true;
            case FIND_FOLLOWER:
                if (escortFollowerLastSeen != null && me.distanceTo(escortFollowerLastSeen) > 2) {
                    escortActions++;
                    Rs2Walker.walkTo(escortFollowerLastSeen, 2);
                }
                break;
            case CATCH_UP:
                escortActions++;
                Rs2Walker.walkTo(followerAt, 1);
                break;
            case TALK_TO_FOLLOWER:
                escortActions++;
                if (follower.click(chooseCorrectNPCOption(step, follower))) {
                    sleepUntil(Rs2Dialogue::isInDialogue, 5_000);
                }
                break;
            case HOP:
                escortActions++;
                Rs2Walker.walkTo(EscortSupport.nextHop(me, target, EscortSupport.MAX_HOP), 0);
                break;
            case WAIT_FOR_FOLLOWER:
            default:
                break;
        }
        escortProgress = EscortSupport.progressToken(escortActions, gap == Integer.MAX_VALUE ? 999 : gap);
        return true;
    }

    private boolean applyDetailedQuestStep(DetailedQuestStep conditionalStep) {
        if (conditionalStep instanceof NpcStep) return false;
        if (isEscortStep(conditionalStep)) return applyEscortStep(conditionalStep);
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

        if (conditionalStep instanceof EmoteStep) {
            if (definedPoint != null && definedPoint.distanceTo(Rs2Player.getWorldLocation()) > 2) {
                Rs2Walker.walkTo(definedPoint, 1);
                return true;
            }
            return performEmote(((EmoteStep) conditionalStep).getEmote());
        }

        // Item-on-item: combine strictly one pair per tick, wait for the inventory to change, then re-read the
        // highlighted items. Firing every combine in one burst consumes the items the next combine needs.
        List<Integer> highlightedUseIds = new ArrayList<>();
        // "Use the three map parts together": the step text is enough, even when the items are not marked for highlight
        boolean anyHighlighted = conditionalStep.getRequirements().stream().anyMatch(r -> r instanceof ItemRequirement
                && ((ItemRequirement) r).shouldHighlightInInventory(Microbot.getClient()));
        boolean combineText = !anyHighlighted && QuestCombineText.isCombineText(conditionalStep.getText());
        for (Requirement requirement : conditionalStep.getRequirements()) {
            if (requirement instanceof ItemRequirement
                    && (combineText && ((ItemRequirement) requirement).isConsumed()
                    || ((ItemRequirement) requirement).shouldHighlightInInventory(Microbot.getClient()))) {
                ((ItemRequirement) requirement).getAllIds().stream().filter(Rs2Inventory::contains).findFirst().ifPresent(id -> {
                    if (!highlightedUseIds.contains(id) && "use".equalsIgnoreCase(chooseCorrectItemOption(conditionalStep, id))) {
                        highlightedUseIds.add(id);
                    }
                });
            }
        }
        if (highlightedUseIds.size() >= 2) {
            int first = highlightedUseIds.get(0);
            int second = highlightedUseIds.get(1);
            int firstBefore = Rs2Inventory.itemQuantity(first);
            int secondBefore = Rs2Inventory.itemQuantity(second);
            int sizeBefore = Rs2Inventory.size();
            if (Rs2Inventory.combine(first, second)) {
                combinedOnStep = conditionalStep;
                combinedIds = new HashSet<>(highlightedUseIds);
                sleepUntil(() -> Rs2Inventory.itemQuantity(first) < firstBefore
                        || Rs2Inventory.itemQuantity(second) < secondBefore
                        || Rs2Inventory.size() != sizeBefore || Rs2Dialogue.isInDialogue()
                        || Rs2Widget.isWidgetVisible(ComponentID.DIALOG_OPTION_OPTIONS), 4_000);
            }
            return true;
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
        for (var widgetDetails : step.getWidgetDetails()) {
            Integer widgetId = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget widget = Microbot.getClient().getWidget(widgetDetails.groupID, widgetDetails.childID);
                if (widget == null || widget.isHidden()) {
                    return null;
                }
                if (widgetDetails.childChildID != -1) {
                    Widget child = widget.getChild(widgetDetails.childChildID);
                    if (child != null) {
                        widget = child;
                    }
                }
                return widget.isHidden() ? null : widget.getId();
            }).orElse(null);
            if (widgetId != null) {
                return Rs2Widget.clickWidget(widgetId);
            }
        }
        return false;
    }

    protected QuestHelperPlugin getQuestHelperPlugin() {
        return (QuestHelperPlugin) Microbot.getPluginManager().getPlugins().stream().filter(x -> x instanceof QuestHelperPlugin).findFirst().orElse(null);
    }

    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getMessage().equalsIgnoreCase("I can't reach that!"))
            unreachableTarget = true;
    }
}
