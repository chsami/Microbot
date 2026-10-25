package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure helpers for the quest driver's dialogue and shop decisions (kept free of client state so they can be tested). */
public final class QuestDialogueRules {
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern LEADING_INDEX = Pattern.compile("^\\[\\d+\\]\\s*");
	private static final Pattern WORD = Pattern.compile("[a-z']{4,}");
	private static final Pattern BAD_OPTION = Pattern.compile(
		"^(?:no|nope|no,? thanks?(?: you)?|goodbye|bye|nothing|not really|never ?mind|maybe later|cancel)\\W*$");
	private static final Pattern BUY_TEXT = Pattern.compile("(?i)(?<![a-z])(?:buy|purchase)(?![a-z])");
	private static final Set<String> STOP_WORDS = new HashSet<>(List.of(
		"talk", "speak", "with", "that", "this", "them", "then", "from", "into", "your", "have", "about", "there",
		"where", "when", "what", "will", "need", "again", "after", "before", "near", "next", "back", "the", "and"));

	private QuestDialogueRules() {
	}

	/** Lower-case, tag-stripped, "[n]"-stripped, whitespace-collapsed text. */
	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String plain = TAGS.matcher(text).replaceAll("").replace((char) 160, ' ');
		plain = LEADING_INDEX.matcher(plain.trim()).replaceFirst("");
		return plain.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	/** An expected previous line matches when the last seen dialogue text contains it (tag and case insensitive). */
	public static boolean previousLineMatches(String expected, String lastLine) {
		if (expected == null) {
			return true;
		}
		if (lastLine == null) {
			return false;
		}
		return normalize(lastLine).contains(normalize(expected));
	}

	/**
	 * Picks a dialogue option when none of the step's configured choices matched. Prefers the option sharing the most
	 * words with the step text / choices; otherwise the first option that is not a refusal ("Goodbye", "No", ...).
	 *
	 * @return the chosen option's text as given, or null when there are no options
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts) {
		if (options == null || options.isEmpty()) {
			return null;
		}
		Set<String> hints = new HashSet<>();
		if (hintTexts != null) {
			for (String hint : hintTexts) {
				var matcher = WORD.matcher(normalize(hint));
				while (matcher.find()) {
					if (!STOP_WORDS.contains(matcher.group())) {
						hints.add(matcher.group());
					}
				}
			}
		}
		String best = null;
		int bestScore = 0;
		for (String option : options) {
			if (option == null || BAD_OPTION.matcher(normalize(option)).matches()) {
				continue;
			}
			String plain = normalize(option);
			int score = 0;
			for (String hint : hints) {
				if (plain.contains(hint)) {
					score++;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				best = option;
			}
		}
		if (best != null) {
			return best;
		}
		for (String option : options) {
			if (option != null && !option.isBlank() && !BAD_OPTION.matcher(normalize(option)).matches()) {
				return option;
			}
		}
		return null;
	}

	/** Step text asks to buy something. */
	public static boolean isBuyText(List<String> stepText) {
		if (stepText == null) {
			return false;
		}
		for (String line : stepText) {
			if (line != null && BUY_TEXT.matcher(TAGS.matcher(line).replaceAll("")).find()) {
				return true;
			}
		}
		return false;
	}

	/** The NPC's "Trade" style action when this is a shop-purchase step, else null. */
	public static String preferTradeAction(boolean shopStep, String[] npcActions) {
		if (!shopStep || npcActions == null) {
			return null;
		}
		for (String action : npcActions) {
			if (action != null && action.toLowerCase(Locale.ROOT).startsWith("trade")) {
				return action;
			}
		}
		return null;
	}

	/** Splits a quantity into the shop's fixed buy amounts, largest first. */
	public static List<Integer> shopBuyChunks(int quantity) {
		List<Integer> chunks = new ArrayList<>();
		int remaining = quantity;
		for (int allowed : new int[]{50, 10, 5, 1}) {
			while (remaining >= allowed) {
				chunks.add(allowed);
				remaining -= allowed;
			}
		}
		return chunks;
	}
}
