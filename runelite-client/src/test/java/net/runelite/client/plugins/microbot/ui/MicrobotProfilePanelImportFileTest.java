package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.microbot.util.security.JagexCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrobotProfilePanelImportFileTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void existingFileIsReadable() throws IOException {
        assertTrue(MicrobotProfilePanel.isReadableFile(folder.newFile("profile.properties")));
    }

    @Test
    public void missingFileIsNotReadable() {
        assertFalse(MicrobotProfilePanel.isReadableFile(new File(folder.getRoot(), "import")));
    }

    @Test
    public void directoryIsNotReadable() throws IOException {
        assertFalse(MicrobotProfilePanel.isReadableFile(folder.newFolder("import")));
    }

    @Test
    public void noFileIsNotReadable() {
        assertFalse(MicrobotProfilePanel.isReadableFile(null));
    }

    @Test
    public void missingFileFailsTheCredentialsProbeAndIsNotReadable() {
        File file = new File(folder.getRoot(), "import");

        assertTrue(credentialsProbeFails(file));
        assertFalse(MicrobotProfilePanel.isReadableFile(file));
    }

    @Test
    public void profileFileThatIsNotUtf8FailsTheCredentialsProbeButStaysReadable() throws IOException {
        File file = folder.newFile("profile.properties");
        Files.write(file.toPath(), new byte[]{'n', 'o', 't', 'e', '=', 'c', 'a', 'f', (byte) 0xE9, '\n'});

        assertTrue(credentialsProbeFails(file));
        assertTrue(MicrobotProfilePanel.isReadableFile(file));
    }

    @Test
    public void cleanProfileFilePassesTheCredentialsProbe() throws IOException {
        File file = folder.newFile("profile.properties");
        Files.write(file.toPath(), new byte[]{'n', 'o', 't', 'e', '=', 'a', '\n'});

        assertFalse(credentialsProbeFails(file));
    }

    private static boolean credentialsProbeFails(File file) {
        try {
            JagexCredentials.load(file);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @Test
    public void unreadableMessageNamesTheFileWithoutItsFolder() {
        File file = new File(folder.getRoot(), "import");

        assertEquals("'import' does not exist or can not be read, so no profile was imported."
                + " Click Import again and choose an existing .properties file.",
            MicrobotProfilePanel.importFileUnreadableMessage(file));
    }
}
