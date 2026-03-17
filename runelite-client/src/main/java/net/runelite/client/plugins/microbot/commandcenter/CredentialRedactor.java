package net.runelite.client.plugins.microbot.commandcenter;

import java.util.regex.Pattern;

/**
 * Utility to strip credentials from log messages.
 * Used by any logging path that might include profile data.
 */
public final class CredentialRedactor {
    private static final Pattern CREDENTIAL_PATTERN =
        Pattern.compile("password=\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("email=\\S+@\\S+", Pattern.CASE_INSENSITIVE);

    private CredentialRedactor() {}

    public static String redact(String msg) {
        if (msg == null) return null;
        String result = CREDENTIAL_PATTERN.matcher(msg).replaceAll("password=***");
        result = EMAIL_PATTERN.matcher(result).replaceAll("email=***");
        return result;
    }
}
