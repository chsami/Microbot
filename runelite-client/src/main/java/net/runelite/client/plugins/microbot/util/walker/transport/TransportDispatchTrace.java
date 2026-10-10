package net.runelite.client.plugins.microbot.util.walker.transport;

import net.runelite.api.coords.WorldPoint;

public final class TransportDispatchTrace {

    public enum Verdict {
        HANDLED,
        REFUSED_AFTER_INTERACTION,
        INTERACTION_LEFT_ORIGIN,
        DELIBERATE_SKIP,
        REFUSED_WITHOUT_INTERACTION,
        NOT_ATTEMPTED
    }

    public enum DeliberateSkip {
        STATIONARY_DOOR_SETTLING,
        ALREADY_AT_DESTINATION,
        SPIRIT_TREE_TRAVEL_DISABLED,
        PLANE_MISMATCH,
        BARROWS_APPROACH,
        TERMINAL_ALREADY_ATTEMPTED,
        TERMINAL_APPROACH
    }

    public enum Refusal {
        ORIGIN_UNREACHABLE,
        ACTION_MISSING,
        OBJECT_MISSING,
        INTERACT_FAILED,
        HANDLER_FAILED,
        UNSUPPORTED
    }

    private boolean interacted;
    private DeliberateSkip deliberateSkip;
    private Refusal refusal;

    public void interacted() {
        interacted = true;
    }

    public void skip(DeliberateSkip reason) {
        if (deliberateSkip == null) {
            deliberateSkip = reason;
        }
    }

    public void refuse(Refusal reason) {
        if (refusal == null) {
            refusal = reason;
        }
    }

    public boolean hasInteracted() {
        return interacted;
    }

    public DeliberateSkip deliberateSkip() {
        return deliberateSkip;
    }

    public Refusal refusal() {
        return refusal;
    }

    public Verdict verdict(boolean handled, WorldPoint player, WorldPoint origin, WorldPoint destination) {
        return classify(handled, interacted, deliberateSkip, refusal,
                TransportRefusalLedger.isStillAtOrigin(player, origin, destination));
    }

    public static Verdict classify(boolean handled, boolean interacted, DeliberateSkip deliberateSkip,
                                   Refusal refusal, boolean stillAtOrigin) {
        if (handled) {
            return Verdict.HANDLED;
        }
        if (interacted) {
            return stillAtOrigin ? Verdict.REFUSED_AFTER_INTERACTION : Verdict.INTERACTION_LEFT_ORIGIN;
        }
        if (deliberateSkip != null) {
            return Verdict.DELIBERATE_SKIP;
        }
        if (refusal != null) {
            return Verdict.REFUSED_WITHOUT_INTERACTION;
        }
        return Verdict.NOT_ATTEMPTED;
    }

    public String reason() {
        if (deliberateSkip != null) {
            return deliberateSkip.name();
        }
        return refusal != null ? refusal.name() : "";
    }
}
