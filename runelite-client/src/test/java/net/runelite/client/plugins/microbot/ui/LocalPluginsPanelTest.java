package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager.OperationResult;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LocalPluginsPanelTest {
    @Test(timeout = 10000)
    public void rescanKeepsEdtResponsiveAndLongFailureStaysInScrollableFooter() throws Exception {
        LivePluginManager manager = mock(LivePluginManager.class);
        when(manager.getPluginDirectory()).thenReturn(Paths.get("plugins"));
        when(manager.getInstalledPlugins()).thenReturn(Collections.emptyList());
        CompletableFuture<OperationResult> result = new CompletableFuture<>();
        when(manager.rescan()).thenReturn(result);
        LocalPluginsPanel panel = onEdt(() -> new LocalPluginsPanel(manager, mock(EventBus.class)));

        onEdt(() -> {
            JButton rescan = (JButton) find(panel,
                    component -> component instanceof JButton && "Rescan folder".equals(((JButton) component).getText()));
            assertNotNull(rescan);
            rescan.doClick(0);
            assertFalse(rescan.isEnabled());
            return null;
        });
        verify(manager).rescan();
        // This EDT round trip must finish while the rescan future is still incomplete.
        assertEquals("responsive", onEdt(() -> "responsive"));

        String diagnostic = "Unable to create injector:\n" + "Missing plugin dependency and binding\n".repeat(500);
        result.complete(OperationResult.failure(diagnostic));
        onEdt(() -> {
            JButton rescan = (JButton) find(panel,
                    component -> component instanceof JButton && "Rescan folder".equals(((JButton) component).getText()));
            JScrollPane statusScroll = (JScrollPane) find(panel,
                    component -> component instanceof JScrollPane
                            && ((JScrollPane) component).getViewport().getView() instanceof JTextArea);
            assertTrue(rescan.isEnabled());
            assertNotNull(statusScroll);
            JTextArea status = (JTextArea) statusScroll.getViewport().getView();
            assertEquals("Unable to complete: " + diagnostic, status.getText());
            assertEquals(0, status.getCaretPosition());
            assertEquals(96, statusScroll.getPreferredSize().height);
            assertTrue("Long diagnostics must not displace the table", statusScroll.getParent().getPreferredSize().height < 200);
            assertFalse(status.isEditable());
            return null;
        });
    }

    @Test
    public void selectedPathIsNullWhenChooserApprovesWithoutFile() {
        JFileChooser chooser = mock(JFileChooser.class);
        when(chooser.getSelectedFile()).thenReturn(null);
        assertNull(LocalPluginsPanel.selectedPath(chooser));
    }

    @Test
    public void selectedPathReturnsChosenFile() {
        JFileChooser chooser = mock(JFileChooser.class);
        File jar = new File("plugins", "example.jar");
        when(chooser.getSelectedFile()).thenReturn(jar);
        assertEquals(jar.toPath(), LocalPluginsPanel.selectedPath(chooser));
    }

    @Test
    public void selectedPathIsNullWhenTypedNameIsInvalid() {
        JFileChooser chooser = mock(JFileChooser.class);
        when(chooser.getSelectedFile()).thenReturn(new InvalidFile(new File("plugins", "example?.jar").getPath()));
        assertNull(LocalPluginsPanel.selectedPath(chooser));
    }

    @Test
    public void selectedPathUsesQuotedAbsolutePathPastedIntoChooser() {
        JFileChooser chooser = mock(JFileChooser.class);
        File folder = new File("plugins").getAbsoluteFile();
        File jar = new File(new File("downloads").getAbsoluteFile(), "example.jar");
        when(chooser.getSelectedFile()).thenReturn(new InvalidFile(folder.getPath() + File.separator + "\"" + jar.getPath() + "\""));
        assertEquals(jar.toPath(), LocalPluginsPanel.selectedPath(chooser));
    }

    @Test
    public void selectedPathIsNullWhenQuotedPathIsRelative() {
        JFileChooser chooser = mock(JFileChooser.class);
        File folder = new File("plugins").getAbsoluteFile();
        when(chooser.getSelectedFile()).thenReturn(new InvalidFile(folder.getPath() + File.separator + "\"example.jar\""));
        assertNull(LocalPluginsPanel.selectedPath(chooser));
    }

    private static final class InvalidFile extends File {
        InvalidFile(String path) {
            super(path);
        }

        @Override
        public Path toPath() {
            throw new InvalidPathException(getPath(), "Illegal char");
        }
    }

    private static Component find(Component component, Predicate<Component> match) {
        if (match.test(component)) {
            return component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component found = find(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T onEdt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
