package net.runelite.client.plugins.microbot.util.security;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class JagexCredentialsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void readsTheCharacterFromACredentialsFile() throws Exception {
        File file = folder.newFile("credentials.properties");
        Files.writeString(file.toPath(), "#Do not share this file with anyone\n"
            + "JX_CHARACTER_ID=42\nJX_SESSION_ID=session-id\nJX_DISPLAY_NAME=Some Name\n"
            + "JX_REFRESH_TOKEN=refresh\nJX_ACCESS_TOKEN=access\n");

        JagexCredentials credentials = JagexCredentials.load(file);

        assertEquals("session-id", credentials.getSessionId());
        assertEquals("42", credentials.getCharacterId());
        assertEquals("Some Name", credentials.getDisplayName());
        assertFalse(credentials.toString().contains("session-id"));
    }

    @Test public void aSettingsFileIsNotACredentialsFile() throws Exception {
        File file = folder.newFile("settings.properties");
        Files.writeString(file.toPath(), "runelite.gpuplugin=true\n");

        assertNull(JagexCredentials.load(file));
    }
}
