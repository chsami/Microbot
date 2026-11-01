package net.runelite.client.plugins.microbot.util.security;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * The Jagex Account character saved in a {@code credentials.properties} file, as written by
 * {@code --insecure-write-credentials}.
 */
@Slf4j
@Value
public class JagexCredentials {
    private static volatile JagexCredentials launch;
    private static volatile boolean readFailureLogged;

    @ToString.Exclude
    String sessionId;
    String characterId;
    String displayName;

    /**
     * @return the character in {@code file}, or null if the file is not a Jagex credentials file
     */
    public static JagexCredentials load(File file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return of(properties.getProperty("JX_SESSION_ID"), properties.getProperty("JX_CHARACTER_ID"),
            properties.getProperty("JX_DISPLAY_NAME"));
    }

    /**
     * The character the client was launched with. Like the client, this reads the {@code JX_*} environment
     * variables first and then the credentials file in the RuneLite directory. It is read once, so a file
     * rewritten by a later launch does not change this client's character.
     *
     * @return null if the client was not launched with a Jagex Account
     */
    public static JagexCredentials fromLaunch() {
        JagexCredentials credentials = launch;
        if (credentials == null) {
            credentials = readLaunch();
            launch = credentials;
        }
        return credentials;
    }

    private static JagexCredentials readLaunch() {
        try {
            JagexCredentials fromEnvironment = of(System.getenv("JX_SESSION_ID"), System.getenv("JX_CHARACTER_ID"),
                System.getenv("JX_DISPLAY_NAME"));
            if (fromEnvironment != null) {
                return fromEnvironment;
            }
            return readLaunch(new File(RuneLite.RUNELITE_DIR, System.getProperty("runelite.credentials.path", "credentials.properties")));
        } catch (RuntimeException e) {
            logReadFailure(e);
            return null;
        }
    }

    static JagexCredentials readLaunch(File file) {
        try {
            return file.exists() ? load(file) : null;
        } catch (IOException | RuntimeException e) {
            logReadFailure(e);
            return null;
        }
    }

    private static void logReadFailure(Exception e) {
        if (!readFailureLogged) {
            readFailureLogged = true;
            log.warn("Unable to read the Jagex Account credentials file ({}), so it is ignored. A backslash in a value, such as a Windows path, breaks the file.",
                e.getClass().getSimpleName());
        }
    }

    private static JagexCredentials of(String sessionId, String characterId, String displayName) {
        if (isBlank(sessionId) || isBlank(characterId) || isBlank(displayName)) {
            return null;
        }
        return new JagexCredentials(sessionId, characterId, displayName);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
