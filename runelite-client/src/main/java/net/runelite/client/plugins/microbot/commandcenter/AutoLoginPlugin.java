package net.runelite.client.plugins.microbot.commandcenter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

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
    private boolean loginAttempted;

    @Override
    protected void startUp() {
        loginAttempted = false;
        String profileDir = System.getProperty("cc-profile-dir");
        if (profileDir == null || profileDir.isEmpty()) {
            log.warn("No --cc-profile-dir set, AutoLogin disabled");
            return;
        }

        Path profilePath = Paths.get(profileDir);

        // Read credentials
        try {
            Properties creds = new Properties();
            creds.load(new FileInputStream(profilePath.resolve("credentials.properties").toFile()));
            email = creds.getProperty("email");
            password = creds.getProperty("password");
        } catch (Exception e) {
            log.error("Failed to read credentials.properties: {}", e.getMessage());
        }

        // Read world from commandcenter.properties
        try {
            Properties config = new Properties();
            config.load(new FileInputStream(profilePath.resolve("commandcenter.properties").toFile()));
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

        // Set world if configured
        if (world != null && !world.isEmpty() && !"auto".equals(world)) {
            try {
                int worldNum = Integer.parseInt(world);
                client.changeWorld(client.createWorld().withId(worldNum));
            } catch (NumberFormatException e) {
                log.warn("Invalid world number: {}", world);
            }
        }

        // Inject credentials
        client.setUsername(email);
        client.setPassword(password);

        // RuneLite fires LOGIN_SCREEN when ready — we can set credentials directly
        // The actual login is triggered by the client's login field being populated
        log.info("AutoLogin: credentials injected for {}", email.substring(0, 3) + "***");
    }

    @Override
    protected void shutDown() {
        email = null;
        password = null;
        world = null;
    }
}
