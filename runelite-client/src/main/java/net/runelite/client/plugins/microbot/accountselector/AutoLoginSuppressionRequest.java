package net.runelite.client.plugins.microbot.accountselector;

/**
 * Scripts post this event before a terminal logout to prevent automatic re-login.
 * AutoLogin resumes when another script starts, after a manual login, or on AutoLogin restart.
 * Ordinary logouts must not post this event.
 */
public final class AutoLoginSuppressionRequest {
}
