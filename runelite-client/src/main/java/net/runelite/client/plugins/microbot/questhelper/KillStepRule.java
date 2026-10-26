package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether quest step text is an imperative to fight ("Kill the ghast.", "Use the cannon to defeat X") as
 * opposed to a talk step that merely mentions a fight ("Talk to Ivan about the kill"). Shared by the driver and,
 * if wanted, the combat detector so both agree on what a kill step is.
 */
public final class KillStepRule {
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	/** The fight verb in imperative position: line start, after sentence punctuation, or after and/then/to. */
	private static final Pattern KILL = Pattern.compile(
		"(?i)(?:^|[.!?;:,(]\\s*|\\b(?:and|then|to)\\s+)(?:kill|defeat|slay|fight)(?![a-z])");
	/** Lines that open with a non-combat verb are never kill steps. */
	private static final Pattern NON_COMBAT_LEAD = Pattern.compile(
		"(?i)^\\s*(?:talk|speak|ask|tell|chat|listen|return|bring|give|hand|show)(?![a-z])");

	private KillStepRule() {
	}

	/** True when any line of the step text is a kill/defeat/fight/slay imperative. */
	public static boolean isKillText(List<String> text) {
		if (text == null) {
			return false;
		}
		for (String line : text) {
			if (line == null) {
				continue;
			}
			String plain = TAGS.matcher(line).replaceAll("");
			if (!NON_COMBAT_LEAD.matcher(plain).find() && KILL.matcher(plain).find()) {
				return true;
			}
		}
		return false;
	}

	/** A kill step also needs a target that can actually be attacked. */
	public static boolean isKillStep(List<String> text, boolean targetHasAttackAction) {
		return targetHasAttackAction && isKillText(text);
	}

	/** Click Attack unless we are already fighting the target; rate-limited so a slow server tick is not spammed. */
	public static boolean shouldClickAttack(boolean interactingWithTarget, long msSinceLastClick, long minIntervalMs) {
		return !interactingWithTarget && msSinceLastClick >= minIntervalMs;
	}
}
