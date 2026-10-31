package net.runelite.client.plugins.microbot.util.security;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import net.runelite.client.RuneLite;
import org.junit.After;
import org.junit.Assume;
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

    @After public void forgetTheLaunchCredentials() throws Exception {
        System.clearProperty("runelite.credentials.path");
        resetLaunch();
    }

    @Test public void aMalformedCredentialsFileAtLaunchIsIgnoredInsteadOfThrowing() throws Exception {
        Assume.assumeTrue(System.getenv("JX_SESSION_ID") == null);
        File file = folder.newFile("credentials.properties");
        Files.writeString(file.toPath(), "JX_CHARACTER_ID=42\nJX_SESSION_ID=session-id\nJX_DISPLAY_NAME=Some Name\n"
            + "JX_NOTE=C:\\users\\me\\.runelite\n");
        Assume.assumeTrue(RuneLite.RUNELITE_DIR.isDirectory());
        String relativePath = RuneLite.RUNELITE_DIR.getAbsoluteFile().toPath().relativize(file.getAbsoluteFile().toPath()).toString();
        assertTrue(new File(RuneLite.RUNELITE_DIR, relativePath).exists());
        System.setProperty("runelite.credentials.path", relativePath);
        resetLaunch();

        try {
            JagexCredentials.load(file);
            fail("A stray backslash should make the file unreadable");
        } catch (IllegalArgumentException expected) {
        }
        assertNull(JagexCredentials.fromLaunch());
        assertNull(JagexCredentials.fromLaunch());
    }

    private static void resetLaunch() throws Exception {
        Field launch = JagexCredentials.class.getDeclaredField("launch");
        launch.setAccessible(true);
        launch.set(null, null);
        Field readFailureLogged = JagexCredentials.class.getDeclaredField("readFailureLogged");
        readFailureLogged.setAccessible(true);
        readFailureLogged.set(null, false);
    }
}
