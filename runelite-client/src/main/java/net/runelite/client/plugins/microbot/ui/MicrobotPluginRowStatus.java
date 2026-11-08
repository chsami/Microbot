package net.runelite.client.plugins.microbot.ui;

import lombok.Value;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

/** The one thing a row needs from the user, if anything. */
@Value
class MicrobotPluginRowStatus
{
    enum Kind
    {
        FAILED, BLOCKED, HEALTH, UPDATE
    }

    private static final int REASON_LENGTH = 35;
    // A load block's reason is the only clue to what broke, so it gets more room than a health reason.
    private static final int BLOCKED_REASON_LENGTH = REASON_LENGTH + 45;

    Kind kind;
    String text;
    // Red when true; a health warning or an update is not.
    boolean severe;
    // The whole line when the shown text cuts a long reason short, for the tooltip; otherwise null.
    @Nullable
    String fullText;

    @Nullable
    static MicrobotPluginRowStatus of(@Nullable String failure, @Nullable String blockedReason, @Nullable MicrobotPluginHealth health,
        @Nullable String installedVersion, @Nullable String latestVersion, boolean updating)
    {
        if (failure != null)
        {
            return new MicrobotPluginRowStatus(Kind.FAILED, "Failed to start", true, null);
        }
        if (blockedReason != null)
        {
            return withReason(Kind.BLOCKED, "Not loaded", blockedReason, BLOCKED_REASON_LENGTH);
        }
        if (health != null && health.isBlocking())
        {
            // The state's own words: turned off on the Hub or a too-old client is not "broken".
            return withReason(Kind.HEALTH, health.getLabel(), health.getReason(), REASON_LENGTH);
        }
        if (health != null && health.isWarning())
        {
            return new MicrobotPluginRowStatus(Kind.HEALTH, "Reported issues", false, null);
        }
        if (installedVersion != null && latestVersion != null)
        {
            return new MicrobotPluginRowStatus(Kind.UPDATE, updating ? "Updating..." : "Update " + installedVersion + " to " + latestVersion, false, null);
        }
        return null;
    }

    // Both callers are blocking, so severe.
    private static MicrobotPluginRowStatus withReason(Kind kind, String label, @Nullable String reason, int maxLength)
    {
        if (reason == null || reason.trim().isEmpty())
        {
            return new MicrobotPluginRowStatus(kind, label, true, null);
        }
        String trimmed = reason.trim();
        String shown = StringUtils.abbreviate(trimmed, maxLength);
        return new MicrobotPluginRowStatus(kind, label + ": " + shown, true, shown.equals(trimmed) ? null : label + ": " + trimmed);
    }
}
