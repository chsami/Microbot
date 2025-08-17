package net.runelite.client.plugins.microbot.util.security;

import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public class Login {

    public static ConfigProfile activeProfile = null;
    private static final int MAX_PLAYER_COUNT = 1950;

    public Login() {
        this(Microbot.getClient().getWorld() > 300 ? Microbot.getClient().getWorld() : getRandomWorld(activeProfile.isMember()));
    }

    public Login(int world) {
        this(activeProfile.getName(), activeProfile.getPassword(), world);
    }

    public Login(String username, String password) {
        this(username, password, 360);
    }

    public Login(String username, String password, int world) {
        if(Microbot.isLoggedIn())
            return;
        if (Microbot.getClient().getLoginIndex() == 3 || Microbot.getClient().getLoginIndex() == 24) { // you were disconnected from the server.
            int loginScreenWidth = 804;
            int startingWidth = (Microbot.getClient().getCanvasWidth() / 2) - (loginScreenWidth / 2);
            Microbot.getMouse().click(365 + startingWidth, 308); //clicks a button "OK" when you've been disconnected
            sleep(600);
        }
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleep(600);
        try {
            setWorld(world);
        } catch (Exception e) {
            System.out.println("Changing world failed");
        }
        Microbot.getClient().setUsername(username);
        try {
            Microbot.getClient().setPassword(Encryption.decrypt(password));
        } catch (Exception e) {
            System.out.println("no password has been set in the profile");
        }
        sleep(300);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleep(300);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        if (Microbot.getClient().getLoginIndex() == 10) {
            int loginScreenWidth = 804;
            int startingWidth = (Microbot.getClient().getCanvasWidth() / 2) - (loginScreenWidth / 2);
            Microbot.getMouse().click(365 + startingWidth, 250); //clicks a button "OK" when you've been disconnected
        } else if (Microbot.getClient().getLoginIndex() == 9) {
            int loginScreenWidth = 804;
            int startingWidth = (Microbot.getClient().getCanvasWidth() / 2) - (loginScreenWidth / 2);
            Microbot.getMouse().click(365 + startingWidth, 300); //clicks a button "OK" when you've been disconnected
        }
    }

    public static int getRandomWorld(boolean isMembers, WorldRegion region) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                int world = getRandomWorldInternal(isMembers, region);
                if (world != -1) {
                    return world;
                }
            } catch (Exception e) {
                System.out.println("World selection attempt " + (attempt + 1) + " failed: " + e.getMessage());
            }
            
            if (attempt < 4) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return isMembers ? 360 : 383;
    }

    private static int getRandomWorldInternal(boolean isMembers, WorldRegion region) {
        WorldResult worldResult = Microbot.getWorldService().getWorlds();

        if (worldResult == null) {
            return -1;
        }

        List<World> worlds = worldResult.getWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return -1;
        }

        boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(WorldType.SEASONAL);

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
                .filter(x -> isInSeasonalWorld == x.getTypes().contains(WorldType.SEASONAL)) // seasonal filter
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
            return -1;
        }

        Random random = new Random();
        World world = filteredWorlds.stream()
                .skip(random.nextInt(filteredWorlds.size()))
                .findFirst()
                .orElse(null);

        return (world != null) ? world.getId() : -1;
    }

    public static int getRandomWorld(boolean isMembers) {
        return getRandomWorld(isMembers, null);
    }

    public static int getNextWorld(boolean isMembers) {
        return getNextWorld(isMembers, null);
    }

    public static int getNextWorld(boolean isMembers, WorldRegion region) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                int world = getNextWorldInternal(isMembers, region);
                if (world != -1) {
                    return world;
                }
            } catch (Exception e) {
                System.out.println("World selection attempt " + (attempt + 1) + " failed: " + e.getMessage());
            }
            
            if (attempt < 4) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return isMembers ? 360 : 383;
    }

    private static int getNextWorldInternal(boolean isMembers, WorldRegion region) {
        WorldResult worldResult = Microbot.getWorldService().getWorlds();

        if (worldResult == null) {
            return -1;
        }

        List<World> worlds = worldResult.getWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return -1;
        }

        boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(WorldType.SEASONAL);

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
                .filter(x -> isInSeasonalWorld == x.getTypes().contains(WorldType.SEASONAL)) // Strict seasonal filter
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
            return -1;
        }

        int currentWorldId = Microbot.getClient().getWorld();
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

        return -1;
    }

    /**
     * Refreshes world data from the world service
     */
    public static void refreshWorldData() {
        try {
            if (Microbot.getWorldService() != null) {
                Microbot.getWorldService().refresh();
                System.out.println("World data refreshed");
            }
        } catch (Exception e) {
            System.out.println("Failed to refresh world data: " + e.getMessage());
        }
    }

    public void setWorld(int worldNumber) {
        int maxWaitAttempts = 30; // Wait up to 30 attempts (30 seconds)
        for (int waitAttempt = 0; waitAttempt < maxWaitAttempts; waitAttempt++) {
            try {
                net.runelite.http.api.worlds.World world = Microbot.getWorldService().getWorlds().findWorld(worldNumber);
                
                if (world == null) {
                    System.out.println("World " + worldNumber + " not found, waiting... attempt " + (waitAttempt + 1));
                    if (waitAttempt < maxWaitAttempts - 1) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    continue;
                }
                
                if (world.getPlayers() >= 2000) {
                    System.out.println("World " + worldNumber + " is full (" + world.getPlayers() + " players), waiting for space... attempt " + (waitAttempt + 1));
                    if (waitAttempt < maxWaitAttempts - 1) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    continue;
                }
                
                final net.runelite.api.World rsWorld = Microbot.getClient().createWorld();
                rsWorld.setActivity(world.getActivity());
                rsWorld.setAddress(world.getAddress());
                rsWorld.setId(world.getId());
                rsWorld.setPlayerCount(world.getPlayers());
                rsWorld.setLocation(world.getLocation());
                rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));
                
                Microbot.getClient().changeWorld(rsWorld);
                System.out.println("Successfully initiated hop to world " + worldNumber + " (waited " + (waitAttempt + 1) + " attempts)");
                return;
                
            } catch (Exception ex) {
                System.out.println("World hop attempt " + (waitAttempt + 1) + " failed: " + ex.getMessage());
                if (waitAttempt < maxWaitAttempts - 1) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // If all wait attempts failed
        System.out.println("Failed to hop to world " + worldNumber + " after waiting " + maxWaitAttempts + " attempts");
    }

    /**
     * Hops to a world with a specific activity
     * @param activity The activity to search for (e.g., "Fishing Trawler", "Pest Control")
     * @param isMembers Whether to search members worlds only
     * @param region Optional region restriction
     * @return The world ID if found, -1 if no suitable world found
     */
    public static int hopByActivity(String activity, boolean isMembers, WorldRegion region) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                int world = hopByActivityInternal(activity, isMembers, region);
                if (world != -1) {
                    return world;
                }
            } catch (Exception e) {
                System.out.println("Activity world search attempt " + (attempt + 1) + " failed: " + e.getMessage());
            }
            
            if (attempt < 4) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return -1;
    }

    /**
     * Hops to a world with a specific activity (no region restriction)
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @return The world ID if found, -1 if no suitable world found
     */
    public static int hopByActivity(String activity, boolean isMembers) {
        return hopByActivity(activity, isMembers, null);
    }

    private static int hopByActivityInternal(String activity, boolean isMembers, WorldRegion region) {
        WorldResult worldResult = Microbot.getWorldService().getWorlds();

        if (worldResult == null) {
            return -1;
        }

        List<World> worlds = worldResult.getWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return -1;
        }

        boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(WorldType.SEASONAL);

        List<World> filteredWorlds = worlds.stream()
                .filter(x -> x.getActivity() != null && x.getActivity().contains(activity))
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
            return -1;
        }

        filteredWorlds.sort((w1, w2) -> Integer.compare(w1.getPlayers(), w2.getPlayers()));

        return filteredWorlds.get(0).getId();
    }

    /**
     * Hops to a world with a specific activity and automatically initiates the hop
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @return true if hop was initiated, false otherwise
     */
    public static boolean hopToActivity(String activity, boolean isMembers, WorldRegion region) {
        int worldId = hopByActivity(activity, isMembers, region);
        if (worldId != -1) {
            // Use the existing hopToWorld method from Microbot class
            return Microbot.hopToWorld(worldId);
        }
        return false;
    }

    /**
     * Hops to a world with a specific activity (no region restriction)
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @return true if hop was initiated, false otherwise
     */
    public static boolean hopToActivity(String activity, boolean isMembers) {
        return hopToActivity(activity, isMembers, null);
    }

    /**
     * Hops to the next world with a specific activity (cycling through available worlds)
     * @param activity The activity to search for (e.g., "Fishing Trawler", "Pest Control")
     * @param isMembers Whether to search members worlds only
     * @param region Optional region restriction
     * @return The world ID if found, -1 if no suitable world found
     */
    public static int hopToNextByActivity(String activity, boolean isMembers, WorldRegion region) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                int world = hopToNextByActivityInternal(activity, isMembers, region);
                if (world != -1) {
                    return world;
                }
            } catch (Exception e) {
                System.out.println("Next activity world search attempt " + (attempt + 1) + " failed: " + e.getMessage());
            }
            
            if (attempt < 4) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return -1;
    }

    /**
     * Hops to the next world with a specific activity (no region restriction)
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @return The world ID if found, -1 if no suitable world found
     */
    public static int hopToNextByActivity(String activity, boolean isMembers) {
        return hopToNextByActivity(activity, isMembers, null);
    }

    private static int hopToNextByActivityInternal(String activity, boolean isMembers, WorldRegion region) {
        WorldResult worldResult = Microbot.getWorldService().getWorlds();

        if (worldResult == null) {
            return -1;
        }

        List<World> worlds = worldResult.getWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return -1;
        }

        boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(WorldType.SEASONAL);

        List<World> filteredWorlds = worlds.stream()
                .filter(x -> x.getActivity() != null && x.getActivity().contains(activity))
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
            return -1;
        }

        filteredWorlds.sort((w1, w2) -> Integer.compare(w1.getPlayers(), w2.getPlayers()));

        int currentWorldId = Microbot.getClient().getWorld();
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
        } else {
            return filteredWorlds.get(0).getId();
        }
    }

    /**
     * Hops to the next world with a specific activity and automatically initiates the hop
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @param region Optional region restriction
     * @return true if hop was initiated, false otherwise
     */
    public static boolean hopToNextActivity(String activity, boolean isMembers, WorldRegion region) {
        int worldId = hopToNextByActivity(activity, isMembers, region);
        if (worldId != -1) {
            // Use the existing hopToWorld method from Microbot class
            return Microbot.hopToWorld(worldId);
        }
        return false;
    }

    /**
     * Hops to the next world with a specific activity and automatically initiates the hop (no region restriction)
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @return true if hop was initiated, false otherwise
     */
    public static boolean hopToNextActivity(String activity, boolean isMembers) {
        return hopToNextActivity(activity, isMembers, null);
    }

    /**
     * Waits for any world with a specific activity to become available
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @param region Optional region restriction
     * @param maxWaitTime Maximum time to wait in seconds
     * @return The world ID if found, -1 if none available within time limit
     */
    public static int waitForActivityWorld(String activity, boolean isMembers, WorldRegion region, int maxWaitTime) {
        System.out.println("Waiting for " + activity + " world to become available...");
        
        int maxWaitAttempts = maxWaitTime; // Check every second
        for (int waitAttempt = 0; waitAttempt < maxWaitAttempts; waitAttempt++) {
            try {
                int world = hopByActivityInternal(activity, isMembers, region);
                if (world != -1) {
                    System.out.println("Found available " + activity + " world: " + world + " (waited " + (waitAttempt + 1) + " seconds)");
                    return world;
                }
                
                if (waitAttempt < maxWaitAttempts - 1) {
                    System.out.println("All " + activity + " worlds are full, waiting... (" + (waitAttempt + 1) + "/" + maxWaitTime + " seconds)");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error checking " + activity + " worlds: " + e.getMessage());
                if (waitAttempt < maxWaitAttempts - 1) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        System.out.println("No " + activity + " worlds became available within " + maxWaitTime + " seconds");
        return -1;
    }

    /**
     * Waits for any world with a specific activity to become available (no region restriction)
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @param maxWaitTime Maximum time to wait in seconds
     * @return The world ID if found, -1 if none available within time limit
     */
    public static int waitForActivityWorld(String activity, boolean isMembers, int maxWaitTime) {
        return waitForActivityWorld(activity, isMembers, null, maxWaitTime);
    }

    /**
     * Waits for any world with a specific activity to become available and then hops to it
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @param region Optional region restriction
     * @param maxWaitTime Maximum time to wait in seconds
     * @return true if hop was initiated, false if no world available within time limit
     */
    public static boolean waitAndHopToActivity(String activity, boolean isMembers, WorldRegion region, int maxWaitTime) {
        int worldId = waitForActivityWorld(activity, isMembers, region, maxWaitTime);
        if (worldId != -1) {
            return Microbot.hopToWorld(worldId);
        }
        return false;
    }

    /**
     * Waits for any world with a specific activity to become available and then hops to it (no region restriction)
     * @param activity The activity to search for
     * @param isMembers Whether to search members worlds only
     * @param maxWaitTime Maximum time to wait in seconds
     * @return true if hop was initiated, false if no world available within time limit
     */
    public static boolean waitAndHopToActivity(String activity, boolean isMembers, int maxWaitTime) {
        return waitAndHopToActivity(activity, isMembers, null, maxWaitTime);
    }
}
