package net.runelite.client.plugins.microbot.commandcenter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.security.LoginManager;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
    name = "CC Auto Login",
    description = "Auto-login from Command Center profile credentials",
    enabledByDefault = false
)
@Slf4j
public class AutoLoginPlugin extends Plugin {
    @Inject
    private Client client;

    private String email;
    private String password;
    private String world;
    private volatile boolean loginAttempted;
    private ScheduledExecutorService executor;

    @Override
    protected void startUp() {
        loginAttempted = false;
        executor = Executors.newSingleThreadScheduledExecutor();

        String profileDir = System.getProperty("cc-profile-dir");
        if (profileDir == null || profileDir.isEmpty()) {
            log.warn("No --cc-profile-dir set, AutoLogin disabled");
            return;
        }

        Path profilePath = Paths.get(profileDir);

        try (var fis = new FileInputStream(profilePath.resolve("credentials.properties").toFile())) {
            Properties creds = new Properties();
            creds.load(fis);
            email = creds.getProperty("email");
            password = creds.getProperty("password");
        } catch (Exception e) {
            log.error("Failed to read credentials.properties: {}", e.getMessage());
        }

        try (var fis = new FileInputStream(profilePath.resolve("commandcenter.properties").toFile())) {
            Properties config = new Properties();
            config.load(fis);
            world = config.getProperty("world");
        } catch (Exception e) {
            log.debug("No commandcenter.properties or no world set");
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGIN_SCREEN) return;
        if (loginAttempted) return;
        if (email == null || password == null) return;

        loginAttempted = true;

        if (world != null && !world.isEmpty() && !"auto".equals(world)) {
            try {
                int worldNum = Integer.parseInt(world);
                LoginManager.setWorld(worldNum);
            } catch (NumberFormatException e) {
                log.warn("Invalid world number: {}", world);
            }
        }

        client.setUsername(email);
        client.setPassword(password);

        String prefix = email.length() >= 3 ? email.substring(0, 3) : email;
        log.info("AutoLogin: credentials injected for {}***, submitting in 600ms", prefix);

        executor.schedule(() -> {
            SwingUtilities.invokeLater(() -> {
                Rs2Keyboard.enter();
                log.info("AutoLogin: Enter pressed");
            });
        }, 600, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutDown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        email = null;
        password = null;
        world = null;
    }
}
