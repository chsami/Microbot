package net.runelite.client.plugins.microbot.ui;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

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
}
