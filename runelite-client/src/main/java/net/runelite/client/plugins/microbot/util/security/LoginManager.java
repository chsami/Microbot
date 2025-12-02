package net.runelite.client.plugins.microbot.util.security;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

/**
 * Centralised login manager used by automation scripts, break handler and utility classes.
 * Responsible for tracking login state, coordinating login attempts and exposing helper methods
 * for world selection.
 */
@Slf4j
public final class LoginManager {

    private static final int MAX_PLAYER_COUNT = 1950;
    private static final Object LOGIN_LOCK = new Object();
    private static final AtomicBoolean LOGIN_ATTEMPT_ACTIVE = new AtomicBoolean(false);
    private static final AtomicReference<Instant> LAST_LOGIN_ATTEMPT = new AtomicReference<>(null);

    @Getter
    private static Instant lastLoginTimestamp = null;


    @Setter
    public static ConfigProfile activeProfile = null;

    public static ConfigProfile getActiveProfile() {
        return Microbot.getConfigManager().getProfile();

    }

    private LoginManager() {
        // Utility class
    }

    /**
     * Returns the current RuneLite client GameState or UNKNOWN if client not available.
     */
    public static GameState getGameState() {
        Client client = Microbot.getClient();
        return client != null ? client.getGameState() : GameState.UNKNOWN;
    }

    /**
     * Returns true if the client is currently considered logged in.
     */
    public static boolean isLoggedIn() {
        Client client = Microbot.getClient();
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    /**
     * Returns true if a login attempt is currently being processed.
     */
    public static boolean isLoginAttemptActive() {
        return LOGIN_ATTEMPT_ACTIVE.get();
    }

    /**
     * Marks the client as logged in by updating timestamps. This should be called when GameState transitions.
     */
    public static void markLoggedIn() {
        // Only set timestamp if client reports logged in.
        if (isLoggedIn()) {
            LOGIN_ATTEMPT_ACTIVE.set(false);
            lastLoginTimestamp = Instant.now();
        }
    }

    /**
     * Marks the client as logged out. Should be triggered whenever the game enters the login screen.
     */
    public static void markLoggedOut() {
        LOGIN_ATTEMPT_ACTIVE.set(false);
    }

    /**
     * Returns the duration the account has been logged in for. Equivalent to Microbot.getLoginTime().
     */
    public static Duration getLoginDuration() {
        if (lastLoginTimestamp == null || !isLoggedIn()) {
            return Duration.of(0, ChronoUnit.MILLIS);
        }
        return Duration.between(lastLoginTimestamp, Instant.now());
    }

    /**
     * Attempts a login using the active profile and an intelligent world selection.
     */
    public static boolean login() {
        if (getActiveProfile() == null) {
            log.warn("No active profile available for login");
            return false;
        }
        System.out.println(getActiveProfile());
        Client client = Microbot.getClient();
        if (client == null) {
            log.warn("Cannot login - client is not initialised");
            return false;
        }

        // Get the selected world from profile
        Integer selectedWorld = getActiveProfile().getSelectedWorld();
        int targetWorld;

        if (selectedWorld != null) {
            if (selectedWorld == -1) {
                // Random Members World
                targetWorld = getRandomWorld(true);
                log.info("Using random members world for login: {}", targetWorld);
            } else if (selectedWorld == -2) {
                // Random F2P World
                targetWorld = getRandomWorld(false);
                log.info("Using random F2P world for login: {}", targetWorld);
            } else {
                // Specific world selected
                targetWorld = selectedWorld;
                log.info("Using profile-selected world for login: {}", targetWorld);
            }
        } else {
            // Fallback to old behavior if no world is selected
            if (getActiveProfile().isMember() && !isCurrentWorldMembers() ||
                    !getActiveProfile().isMember() && isCurrentWorldMembers()) {
                targetWorld = getRandomWorld(getActiveProfile().isMember());
            } else {
                targetWorld = Microbot.getClient().getWorld();
            }
            log.info("Using fallback world selection for login: {}", targetWorld);
        }

        return login(getActiveProfile().getName(), getActiveProfile().getPassword(), targetWorld);
    }

    /**
     * Attempts a login using the active profile into a specific world.
     */
    public static boolean login(int worldId) {
        if (getActiveProfile() == null) {
            log.warn("No active profile available for world specific login");
            return false;
        }
        return login(getActiveProfile().getName(), getActiveProfile().getPassword(), worldId);
    }

    /**
     * Attempts a login with explicit credentials and world target.
     */
    public static boolean login(String username, String encryptedPassword, int worldId) {
        if (username == null || username.isBlank()) {
            log.warn("Cannot login without username");
            return false;
        }
        if (isLoggedIn()) {
            return true;
        }
        if (LOGIN_ATTEMPT_ACTIVE.get()) {
            log.debug("Login attempt already active - skipping duplicate request");
            return false;
        }
        final Client client = Microbot.getClient();
        if (client == null) {
            log.warn("Cannot login - client is not initialised");
            return false;
        }
        synchronized (LOGIN_LOCK) {
            Instant lastAttempt = LAST_LOGIN_ATTEMPT.get();
            Instant now = Instant.now();
            if (lastAttempt != null && Duration.between(lastAttempt, now).toMillis() < 1500) {
                log.debug("Login throttled - last attempt {}ms ago", Duration.between(lastAttempt, now).toMillis());
                return false;
            }
            LAST_LOGIN_ATTEMPT.set(now);
            LOGIN_ATTEMPT_ACTIVE.set(true);
        }

        try {
            handleDisconnectDialogs(client);
            triggerLoginScreen();
            trySetWorld(worldId);
            setCredentials(client, username, encryptedPassword);
            submitLogin();
            handleBlockingDialogs(client);
            return true;
        } catch (Exception ex) {
            log.error("Error during login attempt", ex);
            return false;
        } finally {
            // Keep attempt active until either logged in state observed or logout recorded externally
            LOGIN_ATTEMPT_ACTIVE.set(false);
        }
    }

    private static void handleDisconnectDialogs(Client client) {
        if (client == null) {
            return;
        }
        int loginIndex = client.getLoginIndex();
        if (loginIndex == 3 || loginIndex == 24) {
            int loginScreenWidth = 804;
            int startingWidth = (client.getCanvasWidth() / 2) - (loginScreenWidth / 2);
            Microbot.getMouse().click(365 + startingWidth, 308);
            sleep(600);
        }
    }

    private static void triggerLoginScreen() {
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleep(600);
    }

    private static void trySetWorld(int worldId) {
        if (worldId <= 0) {
            return;
        }
        try {
            setWorld(worldId);
        } catch (Exception e) {
            log.warn("Changing world failed for {}", worldId, e);
        }
    }

    private static void setCredentials(Client client, String username, String encryptedPassword) {
        client.setUsername(username);
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return;
        }
        try {
            client.setPassword(Encryption.decrypt(encryptedPassword));
        } catch (Exception e) {
            log.warn("Unable to decrypt stored password", e);
        }
        sleep(300);
    }

    private static void submitLogin() {
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleep(300);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
    }

    private static void handleBlockingDialogs(Client client) {
        if (client == null) {
            return;
        }
        int loginIndex = client.getLoginIndex();
        int loginScreenWidth = 804;
        int startingWidth = (client.getCanvasWidth() / 2) - (loginScreenWidth / 2);

        if (loginIndex == 10) {
            Microbot.getMouse().click(365 + startingWidth, 250);
        } else if (loginIndex == 9) {
            Microbot.getMouse().click(365 + startingWidth, 300);
        }
    }

    public static int getRandomWorld(boolean isMembers, WorldRegion region) {
        WorldResult worldResult = Microbot.getWorldService().getWorlds();
        if (worldResult == null) {
            return isMembers ? 360 : 383;
        }
        List<World> worlds = worldResult.getWorlds();
        boolean isInSeasonalWorld;
        if (Microbot.getClient() != null && Microbot.getClient().getWorldType() != null) {
            isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
        } else {
            isInSeasonalWorld = false;
        }

        List<World> filteredWorlds = worlds.stream()
            .filter(x -> !x.getTypes().contains(WorldType.PVP) &&
                !x.getTypes().contains(WorldType.HIGH_RISK) &&
                !x.getTypes().contains(WorldType.BOUNTY) &&
                !x.getTypes().contains(WorldType.SKILL_TOTAL) &&
                !x.getTypes().contains(WorldType.LAST_MAN_STANDING) &&
                !x.getTypes().contains(WorldType.QUEST_SPEEDRUNNING) &&
                !x.getTypes().contains(WorldType.BETA_WORLD) &&
                !x.getTypes().contains(WorldType.DEADMAN) &&
                !x.getTypes().contains(WorldType.PVP_ARENA) &&
                !x.getTypes().contains(WorldType.TOURNAMENT) &&
                !x.getTypes().contains(WorldType.NOSAVE_MODE) &&
                !x.getTypes().contains(WorldType.LEGACY_ONLY) &&
                !x.getTypes().contains(WorldType.EOC_ONLY) &&
                !x.getTypes().contains(WorldType.FRESH_START_WORLD) &&
                x.getPlayers() < MAX_PLAYER_COUNT &&
                x.getPlayers() >= 0)
            .filter(x -> isInSeasonalWorld == x.getTypes().contains(WorldType.SEASONAL))
            .collect(Collectors.toList());

        filteredWorlds = isMembers
            ? filteredWorlds.stream().filter(x -> x.getTypes().contains(WorldType.MEMBERS)).collect(Collectors.toList())
            : filteredWorlds.stream().filter(x -> !x.getTypes().contains(WorldType.MEMBERS)).collect(Collectors.toList());

        if (region != null) {
            filteredWorlds = filteredWorlds.stream()
                .filter(x -> x.getRegion() == region)
                .collect(Collectors.toList());
        }

        if (filteredWorlds.isEmpty()) {
            return isMembers ? 360 : 383;
        }

        Random random = new Random();
        World world = filteredWorlds.get(random.nextInt(filteredWorlds.size()));

        return (world != null) ? world.getId() : (isMembers ? 360 : 383);
    }

    public static int getRandomWorld(boolean isMembers) {
        return getRandomWorld(isMembers, null);
    }

    public static int getNextWorld(boolean isMembers) {
        return getNextWorld(isMembers, null);
    }

    public static int getNextWorld(boolean isMembers, WorldRegion region) {
        WorldResult worldResult = Microbot.getWorldService().getWorlds();
        if (worldResult == null) {
            return isMembers ? 360 : 383;
        }

        List<World> worlds = worldResult.getWorlds();
        boolean isInSeasonalWorld;
        if (Microbot.getClient() != null && Microbot.getClient().getWorldType() != null) {
            isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
        } else {
            isInSeasonalWorld = false;
        }

        List<World> filteredWorlds = worlds.stream()
            .filter(x -> !x.getTypes().contains(WorldType.PVP) &&
                !x.getTypes().contains(WorldType.HIGH_RISK) &&
                !x.getTypes().contains(WorldType.BOUNTY) &&
                !x.getTypes().contains(WorldType.SKILL_TOTAL) &&
                !x.getTypes().contains(WorldType.LAST_MAN_STANDING) &&
                !x.getTypes().contains(WorldType.QUEST_SPEEDRUNNING) &&
                !x.getTypes().contains(WorldType.BETA_WORLD) &&
                !x.getTypes().contains(WorldType.DEADMAN) &&
                !x.getTypes().contains(WorldType.PVP_ARENA) &&
                !x.getTypes().contains(WorldType.TOURNAMENT) &&
                !x.getTypes().contains(WorldType.NOSAVE_MODE) &&
                !x.getTypes().contains(WorldType.LEGACY_ONLY) &&
                !x.getTypes().contains(WorldType.EOC_ONLY) &&
                !x.getTypes().contains(WorldType.FRESH_START_WORLD) &&
                x.getPlayers() < MAX_PLAYER_COUNT &&
                x.getPlayers() >= 0)
            .filter(x -> isInSeasonalWorld == x.getTypes().contains(WorldType.SEASONAL))
            .collect(Collectors.toList());

        filteredWorlds = isMembers
            ? filteredWorlds.stream().filter(x -> x.getTypes().contains(WorldType.MEMBERS)).collect(Collectors.toList())
            : filteredWorlds.stream().filter(x -> !x.getTypes().contains(WorldType.MEMBERS)).collect(Collectors.toList());

        if (region != null) {
            filteredWorlds = filteredWorlds.stream()
                .filter(x -> x.getRegion() == region)
                .collect(Collectors.toList());
        }

        int currentWorldId = Microbot.getClient() != null ? Microbot.getClient().getWorld() : -1;
        int currentIndex = -1;

        for (int i = 0; i < filteredWorlds.size(); i++) {
            if (filteredWorlds.get(i).getId() == currentWorldId) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex != -1) {
            int nextIndex = (currentIndex + 1) % filteredWorlds.size();
            return filteredWorlds.get(nextIndex).getId();
        } else if (!filteredWorlds.isEmpty()) {
            return filteredWorlds.get(0).getId();
        }

        return isMembers ? 360 : 383;
    }

    /**
     * Determine if the provided world id corresponds to a members world.
     *
     * @param worldId target world id (e.g. 301, 302, etc.)
     * @return true if world exists and has the MEMBERS type; false otherwise or if data unavailable
     */
    public static boolean isMemberWorld(int worldId) {
        if (worldId <= 0) {
            return false;
        }
        if (Microbot.getWorldService() == null) {
            return false;
        }
        try {
            WorldResult result = Microbot.getWorldService().getWorlds();
            if (result == null) {
                return false;
            }
            World world = result.findWorld(worldId);
            if (world == null || world.getTypes() == null) {
                return false;
            }
            return world.getTypes().contains(WorldType.MEMBERS);
        } catch (Exception e) {
            log.debug("Failed to determine membership for world {}", worldId, e);
            return false;
        }
    }

    /**
     * Convenience method to check if the current client world is a members world.
     * @return true if client available and current world is members, false otherwise.
     */
    public static boolean isCurrentWorldMembers() {
        Client client = Microbot.getClient();
        if (client == null) {
            return false;
        }
        return isMemberWorld(client.getWorld());
    }

    public static void setWorld(int worldNumber) {
        try {
            if (Microbot.getWorldService() == null || Microbot.getClient() == null) {
                log.warn("Cannot change world - client or world service unavailable");
                return;
            }
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Cannot change world - world service returned no data");
                return;
            }
            net.runelite.http.api.worlds.World world = worldResult.findWorld(worldNumber);
            if (world == null) {
                log.warn("Failed to find world {}", worldNumber);
                return;
            }
            final net.runelite.api.World rsWorld = Microbot.getClient().createWorld();
            if (rsWorld == null) {
                log.warn("Failed to create world instance for {}", worldNumber);
                return;
            }
            rsWorld.setActivity(world.getActivity());
            rsWorld.setAddress(world.getAddress());
            rsWorld.setId(world.getId());
            rsWorld.setPlayerCount(world.getPlayers());
            rsWorld.setLocation(world.getLocation());
            rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));
            Microbot.getClient().changeWorld(rsWorld);
        } catch (Exception ex) {
            log.warn("Failed to set target world {}", worldNumber, ex);
        }
    }
}
