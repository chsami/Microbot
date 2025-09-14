package net.runelite.client.plugins.microbot.autoworldhopper.scripts;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autoworldhopper.AutoWorldHopperConfig;
import net.runelite.client.plugins.microbot.autoworldhopper.enums.WorldMembershipFilter;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class WorldHopScript extends Script {

    private ChatMessageManager chatMessageManager;
    
    private AutoWorldHopperConfig config;
    private WorldService worldService;
    
    @Getter
    private long lastHopTime = 0;
    @Getter
    private long nextTimeHop = 0;
    @Getter
    private String lastHopReason = "None";
    @Getter
    private int playersNearby = 0;
    @Getter
    private boolean paused = false;
    @Getter
    private long scriptStartTime = 0;
    
    private boolean chatTriggered = false;
    private long chatTriggerTime = 0;
    
    /**
     * Check if the startup delay has elapsed
     */
    public boolean isStartupDelayComplete() {
        if (config.startupDelay() == 0) {
            return true; // No delay configured
        }
        
        long elapsedSeconds = (System.currentTimeMillis() - scriptStartTime) / 1000;
        return elapsedSeconds >= config.startupDelay();
    }
    
    /**
     * Get remaining startup delay in seconds
     */
    public int getRemainingStartupDelay() {
        if (config.startupDelay() == 0) {
            return 0;
        }
        
        long elapsedSeconds = (System.currentTimeMillis() - scriptStartTime) / 1000;
        return Math.max(0, config.startupDelay() - (int)elapsedSeconds);
    }
    
    public boolean run(AutoWorldHopperConfig config, WorldService worldService, ChatMessageManager chatMessageManager) {
        this.config = config;
        this.worldService = worldService;
        this.chatMessageManager = chatMessageManager;
        
        if (!config.enabled()) {
            return false;
        }
        
        resetTimers();
        paused = false;
        scriptStartTime = System.currentTimeMillis();
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
                    return;
                }
                
                if (!super.run() || paused) {
                    return;
                }
                
                // Update player count
                updatePlayersNearby();
                
                // Check if we should hop
                String hopReason = shouldHop();
                if (hopReason != null) {
                    performWorldHop(hopReason);
                }
                
            } catch (Exception ex) {
                log.error("Error in WorldHopScript", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS); // Check every second
        
        return true;
    }
    
    public void onGameTick() {
        // Update on each game tick for more precise timing
        updatePlayersNearby();
    }
    
    public void resetTimers() {
        if (config != null && config.enableTimeHopping()) {
            nextTimeHop = System.currentTimeMillis() + (config.hopIntervalMinutes() * 60 * 1000);
        } else {
            nextTimeHop = 0;
        }
        chatTriggered = false;
        chatTriggerTime = 0;
        lastHopReason = "None";
    }
    
    public void pause() {
        paused = true;
    }
    
    public void resume() {
        paused = false;
        resetTimers();
    }
    
    public void triggerChatHop(String playerName, String message) {
        if (!config.enableChatDetection()) {
            return;
        }
        
        chatTriggered = true;
        chatTriggerTime = System.currentTimeMillis();
        log.info("Chat trigger activated by player: {} - Message: {}", playerName, message);
    }
    
    private void updatePlayersNearby() {
        if (!config.enablePlayerDetection()) {
            playersNearby = 0;
            return;
        }
        
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) {
            playersNearby = 0;
            return;
        }
        
        WorldPoint localPlayerLocation = localPlayer.getWorldLocation();
        int radius = config.detectionRadius();
        int count = 0;
        
        for (Player player : Microbot.getClient().getPlayers()) {
            if (player == localPlayer) {
                continue; // Skip ourselves
            }
            
            double distance = player.getWorldLocation().distanceTo(localPlayerLocation);
            boolean withinRadius = (radius == 0) ? (distance == 0) : (distance <= radius);
            
            if (withinRadius) {
                count++;
            }
        }
        
        playersNearby = count;
    }
    
    private String shouldHop() {
        long currentTime = System.currentTimeMillis();
        
        // Check startup delay first
        if (!isStartupDelayComplete()) {
            return null; // Still in startup delay period
        }
        
        // Check cooldown
        if (currentTime - lastHopTime < (config.hopCooldown() * 1000)) {
            return null; // Still in cooldown
        }
        
        // Check chat trigger
        if (chatTriggered && config.enableChatDetection()) {
            return "Chat detected";
        }
        
        // Check player count
        if (config.enablePlayerDetection()) {
            if (config.maxPlayers() == 0) {
                // Zero tolerance mode - hop if ANY players detected
                if (playersNearby > 0) {
                    return "Zero tolerance - " + playersNearby + " player(s) detected";
                }
            } else {
                // Normal mode - hop if too many players
                if (playersNearby >= config.maxPlayers()) {
                    return "Too many players (" + playersNearby + "/" + config.maxPlayers() + ")";
                }
            }
        }
        
        // Check time-based hopping
        if (config.enableTimeHopping() && nextTimeHop > 0) {
            if (currentTime >= nextTimeHop) {
                return "Time interval reached";
            }
        }
        
        return null; // No reason to hop
    }
    
    private void performWorldHop(String reason) {
        try {
            // Add random delay if configured
            if (config.randomDelay() > 0) {
                int delay = Rs2Random.between(0, config.randomDelay() * 1000);
                Thread.sleep(delay);
            }
            
            World targetWorld = findSuitableWorld();
            if (targetWorld == null) {
                log.warn("No suitable world found for hopping");
                return;
            }
            
            log.info("Hopping to world {} - Reason: {}", targetWorld.getId(), reason);
            
            // Perform the world hop
            if (hopToWorld(targetWorld)) {
                lastHopTime = System.currentTimeMillis();
                lastHopReason = reason;
                
                // Reset triggers and timers
                chatTriggered = false;
                chatTriggerTime = 0;
                if (config.enableTimeHopping()) {
                    nextTimeHop = System.currentTimeMillis() + (config.hopIntervalMinutes() * 60 * 1000);
                }
                
                // Show notification
                if (config.showNotifications()) {
                    sendChatMessage("Hopped to world " + targetWorld.getId() + " - " + reason);
                }
            }
            
        } catch (Exception ex) {
            log.error("Error performing world hop", ex);
        }
    }
    
    private World findSuitableWorld() {
        WorldResult worldResult = worldService.getWorlds();
        if (worldResult == null) {
            return null;
        }
        
        int currentWorld = Microbot.getClient().getWorld();
        List<World> suitableWorlds = new ArrayList<>();
        
        for (World world : worldResult.getWorlds()) {
            if (world.getId() == currentWorld) {
                continue; // Skip current world
            }
            
            if (!isWorldSuitable(world)) {
                continue;
            }
            
            suitableWorlds.add(world);
        }
        
        if (suitableWorlds.isEmpty()) {
            return null;
        }
        
        // Return random suitable world
        return suitableWorlds.get(Rs2Random.between(0, suitableWorlds.size() - 1));
    }
    
    private boolean isWorldSuitable(World world) {
        EnumSet<WorldType> types = world.getTypes();
        
        // Check membership filter
        switch (config.membershipFilter()) {
            case FREE:
                if (types.contains(WorldType.MEMBERS)) {
                    return false;
                }
                break;
            case MEMBERS:
                if (!types.contains(WorldType.MEMBERS)) {
                    return false;
                }
                break;
            case BOTH:
                // Allow both
                break;
        }
        
        // Check PvP worlds
        if (config.avoidPvpWorlds()) {
            if (types.contains(WorldType.PVP) || types.contains(WorldType.HIGH_RISK) || 
                types.contains(WorldType.DEADMAN)) {
                return false;
            }
        }
        
        // Check skill total worlds
        if (config.avoidSkillTotalWorlds()) {
            if (types.contains(WorldType.SKILL_TOTAL)) {
                return false;
            }
        }
        
        // Check if world is offline
        if (world.getPlayers() < 0) {
            return false;
        }
        
        // Avoid very full worlds (near max capacity)
        if (world.getPlayers() >= 1900) {
            return false;
        }
        
        return true;
    }
    
    private boolean hopToWorld(World world) {
        try {
            // Use the existing world hopping functionality
            net.runelite.api.World rsWorld = Microbot.getClient().createWorld();
            rsWorld.setActivity(world.getActivity());
            rsWorld.setAddress(world.getAddress());
            rsWorld.setId(world.getId());
            rsWorld.setPlayerCount(world.getPlayers());
            rsWorld.setLocation(world.getLocation());
            rsWorld.setTypes(net.runelite.client.util.WorldUtil.toWorldTypes(world.getTypes()));
            
            // If on login screen, just change world
            if (Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN) {
                Microbot.getClient().changeWorld(rsWorld);
                return true;
            }
            
            // If logged in, use the world hopper
            Microbot.getClient().openWorldHopper();
            sleep(1000); // Wait for interface to open
            
            if (Microbot.getClient().getWidget(InterfaceID.Worldswitcher.BUTTONS) != null) {
                Microbot.getClient().hopToWorld(rsWorld);
                return true;
            }
            
            return false;
            
        } catch (Exception ex) {
            log.error("Error hopping to world {}", world.getId(), ex);
            return false;
        }
    }
    
    private void sendChatMessage(String message) {
        if (chatMessageManager == null) {
            return;
        }
        
        String chatMessage = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("[Auto World Hopper] ")
                .append(ChatColorType.NORMAL)
                .append(message)
                .build();
        
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(chatMessage)
                .build());
    }
    
    @Override
    public void shutdown() {
        paused = true;
        super.shutdown();
        log.info("WorldHopScript shutdown");
    }
}
