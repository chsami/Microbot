package net.runelite.client.plugins.microbot.util.security;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

/**
 * Saves the logged-in account's membership to the active profile, so the next login can pick a members or
 * free world before the game has sent it.
 * <p>
 * The game sends the days of membership left in varp {@code ACCOUNT_CREDIT} and a member flag in varc
 * {@code PLAYERMEMBER}. A member can have 0 credit days left, so either one marks the account as members.
 * Both read 0 until the server sends them, so the values are checked on every game tick and saved again
 * whenever they change; that also catches a bond redeemed during the session.
 */
@Slf4j
public class MembershipRecorder {
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    private final Runnable onSaved;
    private Integer savedCredit;
    private Boolean savedMember;

    /**
     * @param onSaved runs on the executor after each save
     */
    public MembershipRecorder(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    /**
     * Call on every game tick (client thread). Profile writes go to {@code executor}.
     */
    public void onGameTick(Client client, ConfigManager configManager, Executor executor) {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) {
            return;
        }
        int credit = client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT);
        boolean member = credit > 0 || client.getVarcIntValue(VarClientID.PLAYERMEMBER) == 1;
        if (Objects.equals(savedCredit, credit) && Objects.equals(savedMember, member)) {
            return;
        }
        ConfigProfile profile = configManager.getProfile();
        if (profile == null || profile.isInternal()) {
            return;
        }
        savedCredit = credit;
        savedMember = member;
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            configManager.setMember(profile, member);
            configManager.setMemberExpireDays(profile, credit);
            configManager.setMemberExpireDaysTimeStemp(profile, now);
            onSaved.run();
        });
    }

    /**
     * Call when the client returns to the login screen, so the next login saves a fresh reading.
     */
    public void reset() {
        savedCredit = null;
        savedMember = null;
    }

    /**
     * Posted after the active profile's membership is saved, so an open profiles panel can show it.
     */
    public static final class MembershipSaved {
    }

    /**
     * Days of membership left on the profile, counted down from when they were saved.
     *
     * @return -1 if the profile has never been logged in since this was added
     */
    public static long remainingDays(ConfigProfile profile, long now) {
        if (profile == null || profile.getMemberExpireDaysTimeStemp() == 0) {
            return -1;
        }
        long daysPassed = (now - profile.getMemberExpireDaysTimeStemp()) / DAY_MILLIS;
        return Math.max(0, profile.getMemberExpireDays() - daysPassed);
    }

    /**
     * Whether the profile's account is members, from what was saved at its last login. Used both for display
     * and for choosing a world while logged out, so the two always agree.
     */
    public static boolean isMember(ConfigProfile profile, long now) {
        if (profile == null) {
            return false;
        }
        long days = remainingDays(profile, now);
        if (days > 0) {
            return true;
        }
        if (days == 0 && profile.getMemberExpireDays() > 0) {
            return false; // the saved days have run out since the last login
        }
        // a member recorded with 0 credit days, or a profile not logged in since this was added
        return profile.isMember();
    }

    /**
     * Membership as saved on the profile, for display.
     */
    public static String describe(ConfigProfile profile, long now) {
        long days = remainingDays(profile, now);
        if (days < 0) {
            return "Unknown until login";
        }
        if (days > 0) {
            return "Members · " + days + (days == 1 ? " day left" : " days left");
        }
        return isMember(profile, now) ? "Members" : "Free-to-play";
    }
}
