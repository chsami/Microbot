package net.runelite.client.plugins.microbot.util.dialogues;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.runelite.api.widgets.Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Lets a higher layer (the quest driver) publish the dialogue options it wants chosen so lower layers that
 * trigger a conversation on their own (the walker opening a guarded door) can answer it immediately, instead of
 * waiting for the owner's slower tick. Nothing here blocks the client thread; callers run on script threads.
 */
public final class DialogueChoiceProvider {
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern LEADING_INDEX = Pattern.compile("^\\[\\d+\\]\\s*");
	private static final int POLL_MS = 100;

	private static volatile Supplier<List<String>> supplier;

	private DialogueChoiceProvider() {
	}

	/** Sets (or with null clears) the wanted options, in priority order. Evaluated on each answer attempt. */
	public static void set(Supplier<List<String>> wanted) {
		supplier = wanted;
	}

	/** Drops the wanted options. */
	public static void clear() {
		supplier = null;
	}

	/** True while a wanted-options supplier is set. */
	public static boolean isActive() {
		return supplier != null;
	}

	/** Tag-stripped, "[n]"-stripped, whitespace-collapsed, lower-case text. */
	static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String plain = TAGS.matcher(text).replaceAll("").replace((char) 160, ' ');
		plain = LEADING_INDEX.matcher(plain.trim()).replaceFirst("");
		return plain.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Index (0-based) of the option matching the first wanted choice that matches anything: exact normalized match
	 * first, then contains. -1 when nothing matches.
	 */
	public static int pickIndex(List<String> options, List<String> wanted) {
		if (options == null || wanted == null) {
			return -1;
		}
		for (String want : wanted) {
			String target = normalize(want);
			if (target.isEmpty()) {
				continue;
			}
			for (int i = 0; i < options.size(); i++) {
				if (normalize(options.get(i)).equals(target)) {
					return i;
				}
			}
			for (int i = 0; i < options.size(); i++) {
				if (normalize(options.get(i)).contains(target)) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Answers the open conversation from the published choices. Polls every ~100 ms for at most {@code maxMs}
	 * while the conversation is open. Continue prompts are clicked through only after an option has been answered
	 * (so a plain "you can't go in there" line stays readable to the caller's lock detection).
	 *
	 * @return true when at least one option was answered
	 */
	public static boolean answerOpenDialogue(long maxMs) {
		Supplier<List<String>> source = supplier;
		if (source == null || !Rs2Dialogue.isInDialogue()) {
			return false;
		}
		boolean answered = false;
		long end = System.currentTimeMillis() + maxMs;
		int stuck = 0;
		String lastMenu = null;
		while (System.currentTimeMillis() < end && !Thread.currentThread().isInterrupted() && Rs2Dialogue.isInDialogue()) {
			if (Rs2Dialogue.hasSelectAnOption()) {
				List<Widget> widgets = Rs2Dialogue.getDialogueOptions();
				List<String> texts = new java.util.ArrayList<>();
				for (Widget widget : widgets) {
					texts.add(widget.getText());
				}
				int index = pickIndex(texts, source.get());
				if (index < 0) {
					return answered;
				}
				String menu = String.join("|", texts);
				stuck = menu.equals(lastMenu) ? stuck + 1 : 0;
				if (stuck >= 3) {
					return answered;
				}
				lastMenu = menu;
				if (Rs2Dialogue.keyPressForDialogueOption(index + 1)) {
					answered = true;
				}
				sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption() || !String.join("|", texts).equals(currentMenu()), 600);
			} else if (answered && Rs2Dialogue.hasContinue()) {
				Rs2Dialogue.clickContinue();
				sleepUntil(() -> !Rs2Dialogue.hasContinue(), 600);
			} else {
				return answered;
			}
		}
		return answered;
	}

	private static String currentMenu() {
		List<String> texts = new java.util.ArrayList<>();
		for (Widget widget : Rs2Dialogue.getDialogueOptions()) {
			texts.add(widget.getText());
		}
		return String.join("|", texts);
	}
}
