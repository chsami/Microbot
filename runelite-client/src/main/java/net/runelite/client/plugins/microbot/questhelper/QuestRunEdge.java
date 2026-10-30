package net.runelite.client.plugins.microbot.questhelper;

/** Edge detection for the automation switch, so one-shot stop work does not repeat on every stopped tick. */
final class QuestRunEdge {
	private QuestRunEdge() {
	}

	/** True only on the tick where automation went from running to stopped. */
	static boolean justStopped(boolean wasRunning, boolean running) {
		return wasRunning && !running;
	}
}
