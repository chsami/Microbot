package net.runelite.client.plugins.microbot.util.walker.transport;

import java.util.Map;
import java.util.Optional;

/** Pure decisions for an object transport whose landing is late: keep waiting while something is happening. */
public final class TransportLandingRules {
    /** Extended wait for a landing that follows a dialogue, animation or walk. */
    public static final long EXTENDED_LANDING_WAIT_MS = 15_000;

    /**
     * Objects whose transport needs a named NPC spoken to first (story gates). Data-driven: object id to NPC name.
     * 3443 is the Holy barrier in the Paterdomus basement, which sends the player to Drezel before it lets them pass.
     */
    private static final Map<Integer, String> TALK_FIRST_NPC = Map.of(3443, "Drezel");

    private TransportLandingRules() {
    }

    /** Whether the player showed any sign the interaction was taken up. */
    public static boolean showsActivity(boolean dialogueOpen, boolean animating, boolean moving) {
        return dialogueOpen || animating || moving;
    }

    /** Only choices on screen and nothing moving: a question the extended wait cannot answer, so waiting is pointless. */
    public static boolean optionsOnly(boolean dialogueOpen, boolean hasContinue, boolean hasOptions,
                                      boolean animating, boolean moving) {
        return dialogueOpen && !hasContinue && hasOptions && !animating && !moving;
    }

    /** Keep waiting for the landing while something is happening and the extended budget lasts. */
    public static boolean keepWaiting(boolean dialogueOpen, boolean animating, boolean moving, long elapsedMs) {
        return showsActivity(dialogueOpen, animating, moving) && elapsedMs < EXTENDED_LANDING_WAIT_MS;
    }

    /** The NPC to talk to when this transport object does not land the player. */
    public static Optional<String> talkFirstNpc(int objectId) {
        return Optional.ofNullable(TALK_FIRST_NPC.get(objectId));
    }
}
