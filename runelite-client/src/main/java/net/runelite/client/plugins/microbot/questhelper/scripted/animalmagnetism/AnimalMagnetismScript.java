package net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.scripted.AttemptBudget;
import net.runelite.client.plugins.microbot.questhelper.scripted.ScriptedQuest;
import net.runelite.client.plugins.microbot.questhelper.scripted.ScriptedQuestHost;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Action;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Snapshot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.event.Level;

/**
 * Animal Magnetism, played from the quest varbit and the inventory alone, so a restart resumes at the right place. Each
 * tick: answer a dialogue, ask the {@link AnimalMagnetismPlan} for the next action, run it. An action that keeps
 * repeating without the quest moving on is cut off and the tick goes to the generic Quest Helper steps instead.
 */
public final class AnimalMagnetismScript implements ScriptedQuest
{
	private static final int DEFAULT_ATTEMPTS = 12;
	private static final int CHOP_ATTEMPTS = 80;
	private static final int TOKEN_ATTEMPTS = 700;
	private static final int RESUPPLY_ATTEMPTS = 4;

	private final AttemptBudget budget = new AttemptBudget();
	private final AnimalMagnetismActions actions = new AnimalMagnetismActions();
	private final Set<String> warned = new HashSet<>();
	private Action lastLogged;

	@Override
	public QuestHelperQuest quest()
	{
		return QuestHelperQuest.ANIMAL_MAGNETISM;
	}

	@Override
	public void reset()
	{
		budget.reset();
		warned.clear();
		lastLogged = null;
		actions.reset();
	}

	@Override
	public boolean deferGates()
	{
		return AnimalMagnetismActions.inMorytania(Rs2Player.getWorldLocation());
	}

	@Override
	public List<QuestPreflight.Need> preflightNeeds()
	{
		int varbit = Microbot.getVarbitValue(AnimalMagnetismPlan.VARBIT);
		return AnimalMagnetismPlan.shoppingList(varbit, actions.snapshot()).stream()
			.map(AnimalMagnetismPlan.Carry::toNeed).collect(Collectors.toList());
	}

	@Override
	public boolean tick(ScriptedQuestHost host)
	{
		int varbit = Microbot.getVarbitValue(AnimalMagnetismPlan.VARBIT);
		Snapshot snapshot = actions.snapshot();
		if (Rs2Dialogue.isInDialogue())
		{
			return actions.answerDialogue(dialogueOptions(varbit, snapshot));
		}
		if (AnimalMagnetismPlan.missing(varbit, snapshot).isEmpty())
		{
			actions.resupplyDone();
		}
		Action action = AnimalMagnetismPlan.next(varbit, snapshot);
		if (!budget.tryUse(action.name(), snapshot.progress(varbit), attemptsFor(action)))
		{
			if (warned.add(action + "@" + varbit))
			{
				Microbot.log("AnimalMagnetism: " + action + " is not working at stage " + varbit
					+ ", using the generic steps", Level.WARN);
			}
			return false;
		}
		if (action != lastLogged)
		{
			lastLogged = action;
			Microbot.log("AnimalMagnetism: stage " + varbit + " -> " + action, Level.INFO);
			if (action == Action.GET_TOKENS)
			{
				Microbot.log("AnimalMagnetism: earning Ecto-tokens, holding "
					+ AnimalMagnetismPlan.tokensToHold(snapshot.ghostsAhoyDone()) + " counting the entry toll", Level.INFO);
			}
		}
		if (!run(action, varbit, snapshot, host))
		{
			sleepGaussian(600, 150);
		}
		return true;
	}

	private static List<String> dialogueOptions(int varbit, Snapshot snapshot)
	{
		List<String> options = new ArrayList<>(AnimalMagnetismPlan.dialogueOptions(varbit, snapshot.count(AnimalMagnetismPlan.CHICKEN)));
		options.add("Burthorpe."); // the Games necklace menu, for the Turael trip and the way out of the manor
		return options;
	}

	private static int attemptsFor(Action action)
	{
		switch (action)
		{
			case GET_TOKENS:
				return TOKEN_ATTEMPTS;
			case CHOP_FOR_TWIGS:
				return CHOP_ATTEMPTS;
			case RESUPPLY:
				return RESUPPLY_ATTEMPTS;
			default:
				return DEFAULT_ATTEMPTS;
		}
	}

	private boolean run(Action action, int varbit, Snapshot s, ScriptedQuestHost host)
	{
		switch (action)
		{
			case FINISH:
				host.finish("Animal Magnetism is complete");
				return true;
			case WEAR_GHOSTSPEAK:
				return actions.wearGhostspeak();
			case EAT:
				return actions.eat();
			case RESUPPLY:
				return actions.resupply(host, varbit, s);
			case GET_TOKENS:
				return actions.earnTokens(host, s.count(AnimalMagnetismPlan.CHICKEN));
			case BUY_CHICKENS:
				return actions.talkTo(AnimalMagnetismPlan.MALCOLM, AnimalMagnetismPlan.MALCOLM_NPCS);
			case TALK_MALCOLM:
				return actions.talkTo(AnimalMagnetismPlan.MALCOLM, AnimalMagnetismPlan.MALCOLM_NPCS);
			case TALK_ALICE:
				return actions.talkTo(AnimalMagnetismPlan.ALICE, AnimalMagnetismPlan.ALICE_NPC);
			case TALK_CRONE:
				return actions.talkTo(AnimalMagnetismPlan.CRONE, AnimalMagnetismPlan.CRONE_NPC);
			case TALK_WITCH:
				return actions.talkTo(AnimalMagnetismPlan.WITCH, AnimalMagnetismPlan.WITCH_NPC);
			case TALK_TUREAL:
				return actions.talkTo(AnimalMagnetismPlan.TUREAL, AnimalMagnetismPlan.TUREAL_NPCS);
			case TALK_AVA:
				return actions.talkToAva();
			default:
				return runTravelOrCraft(action, varbit);
		}
	}

	private boolean runTravelOrCraft(Action action, int varbit)
	{
		switch (action)
		{
			case MAKE_MAGNET:
				return actions.makeMagnet();
			case LEAVE_MANOR:
				return actions.breakDraynorTab(true);
			case GO_TO_TREES:
				return actions.goToTrees();
			case CHOP_ONCE:
				return actions.chopOnce(varbit);
			case LEAVE_TREES:
				return actions.leaveTrees();
			case TELEPORT_BURTHORPE:
				return actions.rubNecklace();
			case UNEQUIP_FOR_TURAEL:
				return actions.unequipForTureal();
			case CHOP_FOR_TWIGS:
				return actions.chopForTwigs();
			case TRANSLATE_NOTES:
				return actions.translateNotes();
			case CLICK_TRANSLATION:
				return actions.clickTranslation();
			case COMBINE_LEATHER:
				return actions.combineLeather();
			case COMBINE_BUTTONS:
				return actions.combineButtons();
			default:
				return false;
		}
	}
}
