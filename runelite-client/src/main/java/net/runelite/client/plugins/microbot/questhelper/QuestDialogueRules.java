package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.Collections;
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
	/** Declining or leaving lines: "No. I think you've had enough.", "I'd rather not.", "Not now.", "Okay, I'll get going!" */
	private static final Pattern DECLINE = Pattern.compile(
		"^(?:no|nope)(?:[.,!?]|\\s|$).*|.*\\bhad enough\\b.*|^i'?d rather not.*|^not (?:now|today|yet).*|^leave me alone.*"
			+ "|^(?:(?:ok|okay|alright|all right),? )?i'?(?:ll|d best) (?:get going|be going|be off|go now|leave)\\W*$");
	/**
	 * Options that spend, travel, teleport, buy, sell, start something or leave: never chosen by the fallback unless the
	 * step's own words match them.
	 */
	private static final Pattern RISKY_OPTION = Pattern.compile(
		".*\\b(?:sail|travel|take me|teleport|ship|boat|charter|ferry|buy|purchase|pay|coins?|gp|sell|trade|start|quest"
			+ "|i'?d best get going|i should (?:go|get going)|goodbye|bye|farewell|see you|that'?s all|never ?mind)\\b.*");
	/** NPC lines that put a cost or a journey on the table: a bare "Yes" after one is not a safe answer. */
	private static final Pattern RISKY_CONTEXT = Pattern.compile(
		".*\\b(?:sail|travel|journey|teleport|ship|boat|charter|fare|coins?|gp|cost|price|pay|buy|sell|essence mine"
			+ "|take you|take me)\\b.*");
	/** Lines that move a conversation on: "Alright, here you go.", "Yes.", "I need your help ...". */
	private static final Pattern AFFIRM = Pattern.compile(
		"^(?:yes|yeah|yep|ok|okay|alright|all right|sure|please|accept|i need|i would|i'?d like|i want)\\b.*|.*\\bhere you go\\b.*");
	/** Lines that end a conversation politely: "That'll be all for now.", "Goodbye.", "I'll be going." */
	private static final Pattern EXIT_OPTION = Pattern.compile(
		"^(?:that'?ll be all|that will be all|that'?s all|that is all|goodbye|bye|i'?ll be going|never ?mind)\\b.*"
			+ "|[^?]*\\bfor now[.!\\s]*$");
	/** "Can I have another pair of ...", "I lost my ...": asking for a replacement item, never needed unless the step wants it. */
	private static final Pattern ITEM_REREQUEST = Pattern.compile(
		"^(?:can|could|may) i (?:have|get|borrow|take) (?:another|a new|a replacement|a spare)\\b.*|.*\\banother (?:pair|set|copy) of\\b.*"
			+ "|^i (?:lost|dropped|broke) (?:my|the)\\b.*|.*\\bi lost (?:my|the)\\b.*");
	private static final Pattern BUY_TEXT = Pattern.compile("(?i)(?<![a-z])(?:buy|purchase)(?![a-z])");
	private static final Set<String> STOP_WORDS = new HashSet<>(List.of(
		"talk", "speak", "with", "that", "this", "them", "then", "from", "into", "your", "have", "about", "there",
		"where", "when", "what", "will", "need", "again", "after", "before", "near", "next", "back", "the", "and",
		"some", "want", "help", "give", "take", "make", "here", "would", "cost", "trip", "could", "should", "which",
		"their", "these", "those", "going", "think", "right", "other", "thing", "things", "just", "like", "come",
		"much", "many", "more", "know", "tell", "ask", "get", "let", "see"));

	private QuestDialogueRules() {
	}

	/** Whether {@code word} appears in {@code text} as a whole word ("port" is not in "transport"). */
	static boolean containsWord(String text, String word) {
		return Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])").matcher(text).find();
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

	/** Whether an option leaves or declines the conversation. */
	static boolean isRefusal(String option) {
		String plain = normalize(option);
		return plain.isEmpty() || BAD_OPTION.matcher(plain).matches() || DECLINE.matcher(plain).matches();
	}

	/**
	 * Picks a dialogue option when none of the step's configured choices matched. Prefers the option sharing the most
	 * words with the step text / choices; otherwise the first option that is not a refusal ("Goodbye", "No", ...).
	 *
	 * @return the chosen option's text as given, or null when there are no options
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts) {
		return pickFallbackOption(options, hintTexts, 0);
	}

	/**
	 * As {@link #pickFallbackOption(List, List)}, for the {@code attempt}-th try of the same menu: attempt 0 is the best
	 * option, 1 the next best, and so on round the options, so a conversation that loops without progress tries
	 * something else. Refusals are never picked.
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts, int attempt) {
		return pickFallbackOption(options, hintTexts, attempt, null);
	}

	/**
	 * As above, with the NPC's last line for context. Each option is tried at most once: {@code null} once the attempts
	 * have gone round, and the caller closes the menu.
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts, int attempt, String lastLine) {
		return pickFallbackOption(options, hintTexts, Collections.emptyList(), attempt, lastLine);
	}

	/**
	 * As above; {@code weakHints} (the quest's name) only rank options and never excuse a risky one: "Rune Mysteries" must
	 * not make "Can I buy some runes?" safe.
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts, List<String> weakHints,
		int attempt, String lastLine) {
		List<String> ranked = rankOptions(options, hintTexts, weakHints, lastLine);
		return attempt < 0 || attempt >= ranked.size() ? null : ranked.get(attempt);
	}

	/**
	 * As above, rotating through the menu: the best option NOT chosen before in this conversation comes first, so a menu
	 * whose options change as they are asked (a guildmaster's list of questions) never repeats the same one while
	 * another is still unasked. Only when every safe option has been chosen does the {@code attempt}-th ranked option
	 * (or null) decide, as before.
	 *
	 * @param alreadyChosen the normalised texts chosen so far
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts, List<String> weakHints,
		int attempt, String lastLine, Set<String> alreadyChosen) {
		List<String> ranked = rankOptions(options, hintTexts, weakHints, lastLine);
		for (String option : ranked) {
			if (!alreadyChosen.contains(normalize(option))) {
				return option;
			}
		}
		return attempt < 0 || attempt >= ranked.size() ? null : ranked.get(attempt);
	}

	/**
	 * Whether an option politely ends the conversation ("That'll be all for now."). A refusal such as "No." or
	 * "Nothing" or "Cancel" is not one: it answers a question instead of leaving the NPC.
	 */
	public static boolean isExitOption(String option) {
		String plain = normalize(option);
		return !plain.isEmpty() && EXIT_OPTION.matcher(plain).matches();
	}

	/** Whether picking this option confirms something ("Yes", "Alright, here you go"): it may be chosen again. */
	public static boolean isConfirmOption(String option) {
		return AFFIRM.matcher(normalize(option)).matches();
	}

	/** The first exit option of the menu, preferring one that says it is all ("That'll be all"), or null. */
	public static String firstExitOption(List<String> options) {
		String fallback = null;
		if (options == null) {
			return null;
		}
		for (String option : options) {
			if (option == null || !isExitOption(option)) {
				continue;
			}
			if (normalize(option).startsWith("that")) {
				return option;
			}
			if (fallback == null) {
				fallback = option;
			}
		}
		return fallback;
	}

	/**
	 * The fallback pick for a menu none of the step's choices matched, with a way out. An informational option already
	 * chosen in this conversation is not chosen again (confirmations such as "Yes" may be); when none is left and the
	 * menu has a polite exit ("That'll be all"), the exit ends the conversation; with no exit the answer is the
	 * {@code attempt}-th ranked option or null, as before. {@code preferExit} takes the exit at once.
	 * Replacement-item requests ("Can I have another ...") are only candidates when {@code missingItems} names the item.
	 *
	 * @param alreadyChosen normalised texts of the informational options chosen so far in this conversation
	 */
	public static String pickOptionOrExit(List<String> options, List<String> hintTexts, List<String> weakHints,
		List<String> missingItems, int attempt, String lastLine, Set<String> alreadyChosen, boolean preferExit) {
		String exit = firstExitOption(options);
		if (preferExit && exit != null) {
			return exit;
		}
		List<String> ranked = rankOptions(options, hintTexts, weakHints, missingItems, lastLine);
		for (String option : ranked) {
			if (isConfirmOption(option) || !alreadyChosen.contains(normalize(option))) {
				return option;
			}
		}
		if (exit != null) {
			return exit;
		}
		return attempt < 0 || attempt >= ranked.size() ? null : ranked.get(attempt);
	}

	public static List<String> rankOptions(List<String> options, List<String> hintTexts) {
		return rankOptions(options, hintTexts, null);
	}

	/**
	 * The options that are safe to choose, best first: hint words weigh most, then a go-ahead wording, then the menu
	 * order. Refusals are left out; so are risky options (see {@link #RISKY_OPTION}) unless a hint matches them, and a
	 * bare go-ahead after an NPC line about a cost or a journey.
	 */
	public static List<String> rankOptions(List<String> options, List<String> hintTexts, String lastLine) {
		return rankOptions(options, hintTexts, Collections.emptyList(), lastLine);
	}

	public static List<String> rankOptions(List<String> options, List<String> hintTexts, List<String> weakHints,
		String lastLine) {
		return rankOptions(options, hintTexts, weakHints, Collections.emptyList(), lastLine);
	}

	/** As above; {@code missingItems} (names of required items the player lacks) unlock replacement-item requests only. */
	public static List<String> rankOptions(List<String> options, List<String> hintTexts, List<String> weakHints,
		List<String> missingItems, String lastLine) {
		Set<String> itemWords = new HashSet<>();
		if (missingItems != null) {
			for (String item : missingItems) {
				var matcher = WORD.matcher(normalize(item));
				while (matcher.find()) {
					itemWords.add(matcher.group());
				}
			}
		}
		boolean riskyContext = lastLine != null && RISKY_CONTEXT.matcher(normalize(lastLine)).matches();
		List<String> ranked = new ArrayList<>();
		if (options == null || options.isEmpty()) {
			return ranked;
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
		String plainLine = lastLine == null ? "" : normalize(lastLine);
		boolean lineMatchesStep = false;
		for (String hint : hints) {
			if (containsWord(plainLine, hint)) {
				lineMatchesStep = true;
				break;
			}
		}
		Set<String> weakWords = new HashSet<>();
		if (weakHints != null) {
			for (String hint : weakHints) {
				var matcher = WORD.matcher(normalize(hint));
				while (matcher.find()) {
					if (!STOP_WORDS.contains(matcher.group())) {
						weakWords.add(matcher.group());
					}
				}
			}
		}
		List<Integer> scores = new ArrayList<>();
		for (String option : options) {
			if (option == null || isRefusal(option)) {
				continue;
			}
			String plain = normalize(option);
			boolean goAhead = AFFIRM.matcher(plain).matches();
			int hintScore = 0;
			for (String hint : hints) {
				if (containsWord(plain, hint)) {
					hintScore += 3;
				}
			}
			boolean hinted = hintScore >= 3;
			// a bare go-ahead is fine after an NPC line that carries one of the step's own words (the step says
			// Karamja and the sailor talks about Karamja)
			boolean goAheadOk = goAhead && lineMatchesStep;
			if (ITEM_REREQUEST.matcher(plain).matches()) {
				// only the names of missing items unlock this, never a hint word (and never a risky option)
				boolean wanted = false;
				for (String word : itemWords) {
					wanted |= containsWord(plain, word);
				}
				if (!wanted) {
					continue;
				}
			}
			if (!hinted && (RISKY_OPTION.matcher(plain).matches() || EXIT_OPTION.matcher(plain).matches()
				|| (goAhead && riskyContext && !goAheadOk))) {
				continue;
			}
			int score = hintScore + (goAhead ? 1 : 0);
			for (String weak : weakWords) {
				if (containsWord(plain, weak)) {
					score++;
				}
			}
			int at = 0;
			while (at < ranked.size() && scores.get(at) >= score) {
				at++;
			}
			ranked.add(at, option);
			scores.add(at, score);
		}
		return ranked;
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
