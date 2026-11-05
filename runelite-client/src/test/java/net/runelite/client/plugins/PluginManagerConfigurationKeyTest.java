package net.runelite.client.plugins;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.plugins.microbot.externalplugins.PluginJarClassLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PluginManagerConfigurationKeyTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private final List<PluginJarClassLoader> loaders = new ArrayList<>();
    private final Map<String, String> values = new HashMap<>();
    private PluginManager manager;

    @Before
    public void setUp() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getConfiguration(anyString(), anyString())).thenAnswer(call -> values.get(call.getArgument(1)));
        doAnswer(call -> {
            assertEquals(RuneLiteConfig.GROUP_NAME, call.getArgument(0));
            values.put(call.getArgument(1), call.getArgument(2));
            return null;
        }).when(config).setConfiguration(anyString(), anyString(), anyString());
        manager = new PluginManager(false, null, null, config, null, new PluginModuleFactory());
    }

    @After
    public void closeLoaders() throws Exception {
        for (PluginJarClassLoader loader : loaders) {
            loader.close();
        }
    }

    @Test
    public void coreAndExternalPluginsWithSameSimpleNameHaveIndependentSwitches() throws Exception {
        Plugin core = new BuiltInOverlayPlugin();
        Plugin external = external("fixture.automation.BuiltInOverlayPlugin", "", false);
        manager.add(core);
        manager.add(external);
        values.put("builtinoverlayplugin", "true");

        assertEquals("builtinoverlayplugin", PluginManager.getPluginConfigurationKey(core.getClass()));
        String externalKey = PluginManager.getPluginConfigurationKey(external.getClass());
        assertEquals("external.fixture.automation.builtinoverlayplugin", externalKey);
        assertTrue(manager.isPluginEnabled(core));
        assertFalse("The native overlay's setting must not autostart automation", manager.isPluginEnabled(external));
        assertFalse("An ambiguous old key must not be migrated", values.containsKey(externalKey));

        manager.setPluginEnabled(external, true);
        assertTrue(manager.isPluginEnabled(external));
        manager.setPluginEnabled(core, false);
        assertFalse(manager.isPluginEnabled(core));
        assertTrue(manager.isPluginEnabled(external));
        manager.setPluginEnabled(external, false);
        assertFalse(manager.isPluginEnabled(external));
    }

    @Test
    public void uniqueLegacyDisabledSettingMigratesAndSurvivesFreshClassloader() throws Exception {
        Plugin first = external("fixture.automation.UniquePlugin", "", true);
        manager.add(first);
        values.put("uniqueplugin", "false");

        assertFalse(manager.isPluginEnabled(first));
        String key = PluginManager.getPluginConfigurationKey(first.getClass());
        assertEquals("false", values.get(key));
        manager.remove(first);
        Plugin replacement = external("fixture.automation.UniquePlugin", "", true);
        manager.add(replacement);
        values.put("uniqueplugin", "true");
        assertEquals(key, PluginManager.getPluginConfigurationKey(replacement.getClass()));
        assertFalse("A migrated value must no longer follow the legacy key", manager.isPluginEnabled(replacement));
    }

    @Test
    public void twoExternalClassesWithSameSimpleNameDoNotShareLegacySetting() throws Exception {
        Plugin first = external("fixture.first.SharedPlugin", "", false);
        Plugin second = external("fixture.second.SharedPlugin", "", false);
        manager.add(first);
        manager.add(second);
        values.put("sharedplugin", "true");

        assertFalse(manager.isPluginEnabled(first));
        assertFalse(manager.isPluginEnabled(second));
        assertNotEquals(PluginManager.getPluginConfigurationKey(first.getClass()), PluginManager.getPluginConfigurationKey(second.getClass()));
        manager.setPluginEnabled(first, true);
        assertTrue(manager.isPluginEnabled(first));
        assertFalse(manager.isPluginEnabled(second));
    }

    @Test
    public void explicitConfigurationNamesRetainTheirExistingIdentity() throws Exception {
        Plugin external = external("fixture.automation.NamedPlugin", "CustomSwitch", true);
        manager.add(external);
        assertEquals("customswitch", PluginManager.getPluginConfigurationKey(external.getClass()));
        values.put("customswitch", "false");
        assertFalse(manager.isPluginEnabled(external));
        manager.setPluginEnabled(external, true);
        assertEquals("true", values.get("customswitch"));
    }

    private Plugin external(String className, String configName, boolean enabledByDefault) throws Exception {
        String type = className.replace('.', '/');
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, type, null, "net/runelite/client/plugins/Plugin", null);
        AnnotationVisitor descriptor = writer.visitAnnotation("Lnet/runelite/client/plugins/PluginDescriptor;", true);
        descriptor.visit("name", className);
        descriptor.visit("configName", configName);
        descriptor.visit("enabledByDefault", enabledByDefault);
        descriptor.visit("isExternal", true);
        descriptor.visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/runelite/client/plugins/Plugin", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        Path jar = temporary.newFile("plugin-" + loaders.size() + ".jar").toPath();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(type + ".class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
        PluginJarClassLoader loader = new PluginJarClassLoader(jar.toFile(), Plugin.class.getClassLoader(), Collections.singleton(className));
        loaders.add(loader);
        return loader.loadClass(className).asSubclass(Plugin.class).getDeclaredConstructor().newInstance();
    }

    @PluginDescriptor(name = "Built-in overlay", enabledByDefault = true)
    public static class BuiltInOverlayPlugin extends Plugin { }
}
