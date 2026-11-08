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

    @Nullable
    static MicrobotPluginRowStatus of(@Nullable String failure, @Nullable String blockedReason, @Nullable MicrobotPluginHealth health,
        @Nullable String installedVersion, @Nullable String latestVersion, boolean updating)
    {
        if (failure != null)
        {
            return new MicrobotPluginRowStatus(Kind.FAILED, "Failed to start", true);
        }
        if (blockedReason != null)
        {
            return new MicrobotPluginRowStatus(Kind.BLOCKED, "Not loaded: " + StringUtils.abbreviate(blockedReason, REASON_LENGTH + 45), true);
        }
        if (health != null && health.isBlocking())
        {
            String reason = health.getReason();
            String label = health.getLabel();
            return new MicrobotPluginRowStatus(Kind.HEALTH, reason == null || reason.trim().isEmpty() ? label
                : label + ": " + StringUtils.abbreviate(reason.trim(), REASON_LENGTH), true);
        }
        if (health != null && health.isWarning())
        {
            return new MicrobotPluginRowStatus(Kind.HEALTH, "Reported issues", false);
        }
        if (installedVersion != null && latestVersion != null)
        {
            return new MicrobotPluginRowStatus(Kind.UPDATE, updating ? "Updating..." : "Update " + installedVersion + " to " + latestVersion, false);
        }
        return null;
    }
}
