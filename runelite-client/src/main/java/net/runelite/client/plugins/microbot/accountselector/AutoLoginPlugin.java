package net.runelite.client.plugins.microbot.accountselector;

import java.awt.AWTException;
import java.awt.event.KeyEvent;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "AutoLogin", description = "Microbot autologin plugin", tags = {
        "account", "microbot", "login" }, enabledByDefault = false)
@Slf4j
public class AutoLoginPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    AutoLoginScript accountSelectorScript;

    @Inject
    AutoLoginConfig autoLoginConfig;

    private AtomicInteger directLoginAttempts = new AtomicInteger(0);

    @Provides
    AutoLoginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoLoginConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        log.info("=== AutoLogin plugin starting up ===");
        log.info("Current game state: {}", client != null ? client.getGameState() : "CLIENT_NULL");

        Microbot.pauseAllScripts.compareAndSet(true, false);
        log.info("Microbot scripts unpaused");

        accountSelectorScript.run(autoLoginConfig);
        log.info("AutoLoginScript started");

        // Check if direct login credentials are available
        boolean directLoginEnabled = autoLoginConfig.enableDirectLogin();
        log.info("Direct login enabled: {}", directLoginEnabled);

        if (directLoginEnabled) {
            String username = autoLoginConfig.username();
            String password = autoLoginConfig.password();
            log.info("Username configured: '{}' (length: {})", username, username.length());
            log.info("Password configured: {} (length: {})", !password.isEmpty() ? "YES" : "NO", password.length());

            // Log world configuration
            boolean usePreferredWorld = autoLoginConfig.usePreferredWorld();
            int preferredWorld = autoLoginConfig.world();
            String worldSelectionMode = autoLoginConfig.worldSelectionMode().name();
            boolean membersOnly = autoLoginConfig.membersOnly();

            log.info("🌍 World Selection Config:");
            log.info("  - Use Preferred World: {}", usePreferredWorld);
            log.info("  - Preferred World: {}", preferredWorld);
            log.info("  - World Selection Mode: {}", worldSelectionMode);
            log.info("  - Members Only: {}", membersOnly);
            log.info("  - Region Preference: {}", autoLoginConfig.regionPreference().name());
            log.info("  - Avoid Empty Worlds: {}", autoLoginConfig.avoidEmptyWorlds());
            log.info("  - Avoid Overcrowded: {}", autoLoginConfig.avoidOvercrowdedWorlds());

            if (!username.isEmpty() && !password.isEmpty()) {
                log.info("✓ Direct login ready for user: {}", username);
            } else {
                log.warn("✗ Direct login enabled but credentials incomplete!");
                log.warn("  - Username empty: {}", username.isEmpty());
                log.warn("  - Password empty: {}", password.isEmpty());
            }
        } else {
            log.info("Direct login disabled - using world selection logic");
        }

        log.info("=== AutoLogin plugin startup complete ===");

        // Check if we're already on login screen and trigger direct login
        if (directLoginEnabled && client != null) {
            GameState currentState = client.getGameState();
            log.info("🔄 Checking current game state after startup: {}", currentState);

            if (currentState == GameState.LOGIN_SCREEN) {
                log.info("🎯 Already on login screen at startup - triggering direct login!");
                directLoginAttempts.set(0);
                executor.schedule(this::attemptDirectLogin, 3000, TimeUnit.MILLISECONDS);
            }
        }
    }

    protected void shutDown() {
        accountSelectorScript.shutdown();
        directLoginAttempts.set(0);
        log.info("AutoLogin plugin stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        GameState newState = event.getGameState();
        log.info("🔄 Game state changed to: {}", newState);

        if (!autoLoginConfig.enableDirectLogin()) {
            log.debug("Direct login disabled, ignoring state change");
            return; // Skip direct login if not enabled
        }

        log.info("Processing state change for direct login...");

        if (newState == GameState.LOGIN_SCREEN) {
            directLoginAttempts.set(0);
            log.info("🏁 LOGIN_SCREEN detected!");
            log.info("Current direct login attempts: {}", directLoginAttempts.get());
            log.info("Current world: {}", client.getWorld());

            // Log current config state
            log.info("📊 Current AutoLogin Config at LOGIN_SCREEN:");
            log.info("  - Username: '{}'", autoLoginConfig.username());
            log.info("  - Password set: {}", !autoLoginConfig.password().isEmpty());
            log.info("  - Direct login enabled: {}", autoLoginConfig.enableDirectLogin());
            log.info("  - Use preferred world: {}", autoLoginConfig.usePreferredWorld());
            log.info("  - Preferred world: {}", autoLoginConfig.world());
            log.info("  - Members only: {}", autoLoginConfig.membersOnly());

            log.info("Scheduling direct login attempt in 2000ms...");
            executor.schedule(this::attemptDirectLogin, 2000, TimeUnit.MILLISECONDS);
        } else if (newState == GameState.LOGGED_IN) {
            log.info("🎉 SUCCESS! LOGGED_IN state detected - login successful!");
            log.info("✅ Final world: {}", client.getWorld());
            log.info("✅ Total login attempts made: {}", directLoginAttempts.get());
            directLoginAttempts.set(0);
            captureCredentialsAfterLogin();
        } else {
            log.info("Other game state: {} - no direct login action needed", newState);
        }
    }

    private void attemptDirectLogin() {
        log.info("⚡ attemptDirectLogin() called");

        GameState currentState = client.getGameState();
        log.info("Current game state in attemptDirectLogin: {}", currentState);

        if (currentState != GameState.LOGIN_SCREEN) {
            log.warn("❌ Not on login screen! Current state: {}", currentState);
            return;
        }

        // Check for login errors that should prevent retries
        int loginIndex = client.getLoginIndex();
        log.info("Current login index: {}", loginIndex);
        
        if (loginIndex == 3) { // Wrong credentials/authentication
            log.error("❌ Authentication failed (login index 3) - check credentials!");
            directLoginAttempts.set(999); // Prevent further attempts
            return;
        }
        
        if (loginIndex == 4) { // 2FA/Authenticator required OR wrong credentials
            log.warn("⚠️ Login index 4 detected - could be 2FA/Authenticator required or wrong credentials");
            log.info("💡 If your account has 2FA enabled, you may need to disable auto-login and enter the code manually");
            log.info("💡 If you don't have 2FA, check your username and password");
            
            // Allow a few more attempts in case it's 2FA and user enters code manually
            if (directLoginAttempts.get() > 5) {
                log.error("❌ Multiple attempts with authentication screen - stopping to prevent account lock");
                directLoginAttempts.set(999);
                return;
            }
        }
        
        if (loginIndex == 34) { // Not a member trying to access members world
            log.error("❌ Account is not a member but trying to access members world (login index 34)!");
            log.info("💡 Try setting preferred world to a F2P world or disable 'Members Only' option");
            directLoginAttempts.set(999); // Prevent further attempts
            return;
        }

        log.info("✓ Confirmed on login screen, proceeding...");

        String username = autoLoginConfig.username();
        String password = autoLoginConfig.password();

        log.info("Retrieved credentials - Username: '{}', Password length: {}", username, password.length());

        if (username.isEmpty() || password.isEmpty()) {
            log.error("❌ Direct login credentials not configured properly!");
            log.error("   Username empty: {}", username.isEmpty());
            log.error("   Password empty: {}", password.isEmpty());
            return;
        }

        log.info("✓ Credentials validated");

        // Check and set world first
        selectAppropriateWorld();

        int currentAttempts = directLoginAttempts.get();
        log.info("Current login attempts: {}", currentAttempts);

        if (currentAttempts >= 3) {
            log.warn("❌ Max direct login attempts reached ({})", currentAttempts);
            return;
        }

        directLoginAttempts.incrementAndGet();
        log.info("🎯 Starting direct login attempt #{} for user: {}", directLoginAttempts.get(), username);

        clientThread.invoke(() -> {
            try {
                log.info("📋 Inside clientThread.invoke() for login setup");

                // Set username if needed
                @SuppressWarnings("deprecation")
                String currentUsername = client.getUsername();
                log.info("Current username in client: '{}'", currentUsername);

                if (currentUsername == null || currentUsername.isEmpty() || !currentUsername.equals(username)) {
                    log.info("🔧 Setting username to: {}", username);
                    client.setUsername(username);
                    log.info("✓ Username set successfully");

                    log.info("⏰ Scheduling password setting in 500ms...");
                    executor.schedule(() -> clientThread.invoke(() -> setPasswordAndLogin(password)), 500,
                            TimeUnit.MILLISECONDS);
                } else {
                    log.info("✓ Username already correct, proceeding to password");
                    setPasswordAndLogin(password);
                }
            } catch (Exception e) {
                log.error("💥 Exception during direct login setup", e);
                log.error("Exception details: {}", e.getMessage());
            }
        });
    }

    private void selectAppropriateWorld() {
        try {
            log.info("🌍 selectAppropriateWorld() called");

            int currentWorld = client.getWorld();
            log.info("Current world: {}", currentWorld);

            boolean usePreferredWorld = autoLoginConfig.usePreferredWorld();
            int preferredWorld = autoLoginConfig.world();
            boolean membersOnly = autoLoginConfig.membersOnly();

            log.info("World selection logic:");
            log.info("  - Use Preferred World: {}", usePreferredWorld);
            log.info("  - Preferred World: {}", preferredWorld);
            log.info("  - Members Only: {}", membersOnly);

            int targetWorld = currentWorld;

            if (usePreferredWorld && preferredWorld > 0) {
                targetWorld = preferredWorld;
                log.info("✓ Using preferred world: {}", targetWorld);
            } else {
                log.info("Using world selection mode: {}", autoLoginConfig.worldSelectionMode().name());
                // For now, use current world if no preferred world is set
                log.info("No preferred world set, staying on current world: {}", currentWorld);
            }

            if (targetWorld != currentWorld) {
                log.info("🔄 Need to switch from world {} to world {}", currentWorld, targetWorld);
                
                if (client.getGameState() == GameState.LOGIN_SCREEN) {
                    log.info("🎯 On login screen - attempting world change...");
                    
                    // Get the world from world service
                    net.runelite.http.api.worlds.WorldResult worlds = Microbot.getWorldService().getWorlds();
                    net.runelite.http.api.worlds.World worldData = worlds != null ? worlds.findWorld(targetWorld) : null;
                    
                    if (worldData != null) {
                        log.info("✓ Found world data for world {}", targetWorld);
                        
                        // Create RuneScape world object
                        final net.runelite.api.World rsWorld = client.createWorld();
                        rsWorld.setActivity(worldData.getActivity());
                        rsWorld.setAddress(worldData.getAddress());
                        rsWorld.setId(worldData.getId());
                        rsWorld.setPlayerCount(worldData.getPlayers());
                        rsWorld.setLocation(worldData.getLocation());
                        rsWorld.setTypes(net.runelite.client.util.WorldUtil.toWorldTypes(worldData.getTypes()));
                        
                        // Change world on login screen
                        client.changeWorld(rsWorld);
                        log.info("✅ Successfully changed to world {}", targetWorld);
                        
                        // Small delay to let world change take effect
                        try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        
                        // Verify world change
                        int newCurrentWorld = client.getWorld();
                        if (newCurrentWorld == targetWorld) {
                            log.info("✅ World change confirmed: now on world {}", newCurrentWorld);
                        } else {
                            log.warn("⚠️ World change may not have taken effect. Expected: {}, Current: {}", targetWorld, newCurrentWorld);
                        }
                    } else {
                        log.error("❌ Could not find world data for world {}", targetWorld);
                        log.warn("⚠️ Continuing with current world {} - may cause login issues", currentWorld);
                    }
                } else {
                    log.warn("⚠️ Not on login screen - cannot change world directly");
                    log.warn("⚠️ Current world {} may not be suitable for this account type", currentWorld);
                }
            } else {
                log.info("✓ Already on target world: {}", targetWorld);
            }

        } catch (Exception e) {
            log.error("💥 Exception in selectAppropriateWorld", e);
            log.error("Exception details: {}", e.getMessage());
        }
    }

    private void setPasswordAndLogin(String password) {
        log.info("🔐 setPasswordAndLogin() called");

        try {
            GameState currentState = client.getGameState();
            log.info("Game state check in setPasswordAndLogin: {}", currentState);

            if (currentState != GameState.LOGIN_SCREEN) {
                log.warn("❌ Not on login screen in setPasswordAndLogin! State: {}", currentState);
                return;
            }

            log.info("✓ Still on login screen, setting password...");
            log.info("Password length: {}", password.length());

            client.setPassword(password);
            log.info("✅ Password set successfully");

            // Improved timing - longer delay to ensure password is set properly
            int delay = 1200 + (directLoginAttempts.get() * 200); // Progressive delay
            log.info("⏰ Scheduling login submission in {}ms (attempt #{})...", delay, directLoginAttempts.get());
            executor.schedule(() -> clientThread.invoke(this::submitLogin), delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("💥 Exception in setPasswordAndLogin", e);
            log.error("Exception details: {}", e.getMessage());
        }
    }

    private void submitLogin() {
        log.info("🚀 submitLogin() called");

        try {
            GameState currentState = client.getGameState();
            log.info("Game state check in submitLogin: {}", currentState);

            if (currentState == GameState.LOGIN_SCREEN) {
                log.info("✓ Still on login screen, submitting login...");

                // Press Enter to submit login
                log.info("⌨️ Pressing Enter key to submit login...");
                Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                log.info("✅ Enter key pressed successfully");

                // Backup: Try to click login button
                log.info("⏰ Scheduling backup login button click in 200ms...");
                executor.schedule(() -> {
                    try {
                        log.info("🔍 Looking for login button widget...");
                        Widget loginWidget = findLoginWidget();
                        if (loginWidget != null) {
                            log.info("✓ Found login widget: bounds={}", loginWidget.getBounds());

                            int clickX = loginWidget.getBounds().x + loginWidget.getBounds().width / 2;
                            int clickY = loginWidget.getBounds().y + loginWidget.getBounds().height / 2;
                            log.info("🖱️ Clicking login button at ({}, {})", clickX, clickY);

                            Microbot.getMouse().click(new net.runelite.api.Point(clickX, clickY));
                            log.info("✅ Login button clicked successfully");
                        } else {
                            log.warn("❌ Login widget not found for backup click");
                            // Enhanced fallback click with multiple possible locations
                            log.info("🎯 Attempting enhanced fallback clicks at possible login button locations...");
                            
                            // Multiple possible locations for login button based on different screen sizes/modes
                            int[][] fallbackLocations = {
                                {535, 565},  // Original location
                                {525, 565},  // Slightly left
                                {545, 565},  // Slightly right
                                {535, 555},  // Slightly up
                                {535, 575},  // Slightly down
                                {400, 300},  // Center screen area
                                {765, 503}   // Alternative known location
                            };
                            
                            for (int i = 0; i < fallbackLocations.length && i < 2; i++) { // Try max 2 locations
                                int[] location = fallbackLocations[i];
                                log.info("🖱️ Fallback click #{} at ({}, {})", i + 1, location[0], location[1]);
                                
                                try {
                                    Microbot.getMouse().click(new net.runelite.api.Point(location[0], location[1]));
                                    Thread.sleep(300); // Small delay between clicks
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            
                            log.info("✅ Enhanced fallback login button clicks completed");
                        }
                    } catch (Exception ex) {
                        log.error("💥 Exception during backup login button click", ex);
                    }
                }, 200, TimeUnit.MILLISECONDS);

                log.info("🎯 Direct login attempt #{} submission completed", directLoginAttempts.get());
            } else {
                log.warn("❌ Not on login screen during submitLogin! State: {}", currentState);
            }
        } catch (Exception e) {
            log.error("💥 Exception in submitLogin", e);
            log.error("Exception details: {}", e.getMessage());
            log.error("Stack trace:", e);
        }
    }

    private Widget findLoginWidget() {
        log.info("🔍 findLoginWidget() called - searching for login button...");

        // First, check the current login index to understand login screen state
        int loginIndex = client.getLoginIndex();
        log.info("Current login index during widget search: {}", loginIndex);

        // Try multiple widget root IDs for login screen with more comprehensive search
        int[] possibleRootIds = { 596, 378, 162, 129, 550, 65 }; // Extended list of login screen widget IDs

        for (int rootId : possibleRootIds) {
            log.debug("Trying root widget ID: {}", rootId);
            Widget rootWidget = client.getWidget(rootId, 0);

            if (rootWidget != null && !rootWidget.isHidden()) {
                log.debug("Found visible root widget {}: bounds={}", rootId, rootWidget.getBounds());
                Widget result = findLoginButtonRecursive(rootWidget);
                if (result != null) {
                    log.info("✅ Found login button in root {}: bounds={} text='{}'", 
                            rootId, result.getBounds(), result.getText());
                    return result;
                }
            }
        }

        // Enhanced fallback: Try more specific known widget IDs for login button
        log.info("🔍 Enhanced fallback: Trying specific widget IDs for login button...");
        int[][] specificWidgetIds = {
                { 378, 17 }, // Common login button ID
                { 378, 18 }, // Alternative login button ID  
                { 378, 19 }, // Another alternative
                { 596, 17 }, // Another possible login button ID
                { 596, 18 }, // Another alternative
                { 596, 19 }, // Yet another alternative
                { 162, 32 }, // Old-style login button
                { 162, 33 }, // Old-style alternative
                { 65, 4 },   // Possible login button
                { 65, 6 },   // Alternative
                { 550, 14 }, // Another possible location
                { 550, 15 }  // Alternative
        };

        for (int[] widgetId : specificWidgetIds) {
            Widget specificWidget = client.getWidget(widgetId[0], widgetId[1]);
            if (specificWidget != null && !specificWidget.isHidden()) {
                String text = specificWidget.getText();
                if (text != null) {
                    String lowerText = text.toLowerCase().trim();
                    if (lowerText.contains("login") || lowerText.contains("enter") || 
                        lowerText.contains("play") || lowerText.contains("existing user") ||
                        lowerText.equals("click here to play")) {
                        log.info("✅ Found login widget via specific ID search: [{}][{}] text='{}' bounds={}",
                                widgetId[0], widgetId[1], specificWidget.getText(), specificWidget.getBounds());
                        return specificWidget;
                    }
                }
                
                // Also check widgets that might be clickable buttons without text
                if (specificWidget.getBounds() != null && 
                    specificWidget.getBounds().width > 50 && specificWidget.getBounds().height > 20) {
                    log.debug("Found potential clickable widget [{}][{}] without text: bounds={}",
                            widgetId[0], widgetId[1], specificWidget.getBounds());
                }
            }
        }

        // Last resort: search all widgets on screen for login-related content
        log.info("🔍 Last resort: Searching all visible widgets for login button...");
        return searchAllWidgetsForLogin();
    }

    private Widget findLoginButtonRecursive(Widget widget) {
        // Check current widget
        if (widget != null && widget.getText() != null &&
                (widget.getText().toLowerCase().contains("login") ||
                        widget.getText().toLowerCase().contains("enter") ||
                        widget.getText().toLowerCase().contains("play"))) {
            return widget;
        }

        // Check children
        if (widget != null && widget.getChildren() != null) {
            Widget[] children = widget.getChildren();
            if (children != null) {
                for (Widget child : children) {
                    Widget result = findLoginButtonRecursive(child);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Comprehensive search for login button across all widgets
     */
    private Widget searchAllWidgetsForLogin() {
        log.debug("🔍 Searching all widgets for login button...");
        
        try {
            // Get all possible widget roots that could contain login elements
            int[] allRoots = { 596, 378, 162, 129, 550, 65, 109, 164, 165, 166 };
            
            for (int rootId : allRoots) {
                Widget root = client.getWidget(rootId, 0);
                if (root != null && !root.isHidden()) {
                    // Search this entire tree
                    Widget result = searchWidgetTreeForLogin(root, 0);
                    if (result != null) {
                        log.info("✅ Found login widget in comprehensive search: root={} text='{}' bounds={}",
                                rootId, result.getText(), result.getBounds());
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error during comprehensive widget search: {}", e.getMessage());
        }
        
        log.warn("❌ No login widget found in comprehensive search");
        return null;
    }

    /**
     * Recursively search widget tree with depth limit
     */
    private Widget searchWidgetTreeForLogin(Widget widget, int depth) {
        if (widget == null || depth > 10) { // Prevent infinite recursion
            return null;
        }
        
        try {
            // Check current widget
            if (!widget.isHidden() && widget.getText() != null) {
                String text = widget.getText().toLowerCase().trim();
                if (text.contains("login") || text.contains("enter") || text.contains("play") ||
                    text.contains("existing user") || text.equals("click here to play") ||
                    text.contains("world") && text.contains("select")) {
                    
                    // Additional validation - make sure it looks like a button
                    if (widget.getBounds() != null && widget.getBounds().width > 30 && widget.getBounds().height > 15) {
                        return widget;
                    }
                }
            }
            
            // Search children recursively
            Widget[] children = widget.getChildren();
            if (children != null) {
                for (Widget child : children) {
                    Widget result = searchWidgetTreeForLogin(child, depth + 1);
                    if (result != null) {
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error searching widget at depth {}: {}", depth, e.getMessage());
        }
        
        return null;
    }

    private void captureCredentialsAfterLogin() {
        log.info("📝 captureCredentialsAfterLogin() called");

        // Get current username from client if logged in
        String currentUsername = null;
        if (client.getLocalPlayer() != null) {
            currentUsername = client.getLocalPlayer().getName();
            log.info("Local player found, username: '{}'", currentUsername);
        } else {
            log.warn("Local player is null, cannot capture username");
        }

        if (currentUsername != null && !currentUsername.isEmpty()) {
            String savedUsername = autoLoginConfig.username();
            log.info("Comparing usernames - Current: '{}', Saved: '{}'", currentUsername, savedUsername);

            if (savedUsername.isEmpty()) {
                log.info("💾 First successful login detected! Saving username: {}", currentUsername);
                configManager.setConfiguration("AutoLoginConfig", "username", currentUsername);
                log.info("✅ Username saved successfully. Set password in plugin settings to enable full auto-login.");
            } else {
                log.info("Username already saved, no action needed");
            }
        } else {
            log.warn("❌ Cannot capture username - current username is null or empty");
        }
    }

    // Public methods for external credential management
    public void setCredentials(String username, String password) {
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            configManager.setConfiguration("AutoLoginConfig", "username", username);
            configManager.setConfiguration("AutoLoginConfig", "password", password);
            configManager.setConfiguration("AutoLoginConfig", "enableDirectLogin", "true");

            log.info("Credentials manually set for user: {}", username);
            log.info("Direct auto-login is now enabled!");
        }
    }

    public void clearCredentials() {
        configManager.unsetConfiguration("AutoLoginConfig", "username");
        configManager.unsetConfiguration("AutoLoginConfig", "password");
        configManager.unsetConfiguration("AutoLoginConfig", "enableDirectLogin");
        log.info("Credentials cleared");
    }

    public void performManualLogin(String username, String password) {
        if (username == null || password == null) {
            log.error("Username or password cannot be null");
            return;
        }

        clientThread.invoke(() -> {
            try {
                client.setUsername(username);
                client.setPassword(password);
                executor.schedule(() -> clientThread.invoke(this::submitLogin), 500, TimeUnit.MILLISECONDS);
                log.info("Manual login initiated for user: {}", username);
            } catch (Exception e) {
                log.error("Failed to perform manual login", e);
            }
        });
    }

    /**
     * Reset direct login attempts counter (useful for retrying after fixing configuration)
     */
    public void resetLoginAttempts() {
        directLoginAttempts.set(0);
        log.info("🔄 Direct login attempts counter reset to 0");
    }

    /**
     * Manually trigger a direct login attempt (useful for debugging or manual retry)
     */
    public void triggerManualLogin() {
        if (!autoLoginConfig.enableDirectLogin()) {
            log.warn("❌ Direct login is not enabled in configuration");
            return;
        }
        
        GameState currentState = client.getGameState();
        if (currentState != GameState.LOGIN_SCREEN) {
            log.warn("❌ Not on login screen! Current state: {}", currentState);
            return;
        }
        
        log.info("🎯 Manual login trigger initiated...");
        resetLoginAttempts(); // Reset counter for manual attempt
        executor.schedule(this::attemptDirectLogin, 500, TimeUnit.MILLISECONDS);
    }

}
