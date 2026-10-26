package net.runelite.client.plugins.microbot.util.security;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import lombok.ToString;
import lombok.Value;

/**
 * The Jagex Account character saved in a {@code credentials.properties} file, as written by
 * {@code --insecure-write-credentials}.
 */
@Value
public class JagexCredentials {
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
        String sessionId = properties.getProperty("JX_SESSION_ID");
        String characterId = properties.getProperty("JX_CHARACTER_ID");
        String displayName = properties.getProperty("JX_DISPLAY_NAME");
        if (isBlank(sessionId) || isBlank(characterId) || isBlank(displayName)) {
            return null;
        }
        return new JagexCredentials(sessionId, characterId, displayName);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
