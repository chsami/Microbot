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
            return withReason(Kind.BLOCKED, "Not loaded", blockedReason, true);
        }
        if (health != null && health.isBlocking())
        {
            // The state's own words: disabled upstream or a too-old client is not "broken".
            return withReason(Kind.HEALTH, health.getLabel(), health.getReason(), true);
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

    private static MicrobotPluginRowStatus withReason(Kind kind, String label, @Nullable String reason, boolean severe)
    {
        if (reason == null || reason.trim().isEmpty())
        {
            return new MicrobotPluginRowStatus(kind, label, severe, null);
        }
        String trimmed = reason.trim();
        String shown = StringUtils.abbreviate(trimmed, REASON_LENGTH);
        return new MicrobotPluginRowStatus(kind, label + ": " + shown, severe, shown.equals(trimmed) ? null : label + ": " + trimmed);
    }
}
