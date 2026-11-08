package net.runelite.client.plugins.microbot.externalplugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.util.Text;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/** Reads only this artifact, never the entire parent classpath. No plugin code is initialized here. */
final class LivePluginArtifact implements AutoCloseable {
    final Path cacheFile;
    final PluginJarClassLoader loader;
    final List<Class<? extends Plugin>> types;
    final String id;

    private LivePluginArtifact(Path cacheFile, PluginJarClassLoader loader,
            List<Class<? extends Plugin>> types, String id) {
        this.cacheFile = cacheFile;
        this.loader = loader;
        this.types = types;
        this.id = id;
    }

    static LivePluginArtifact read(Path file) throws Exception {
        Set<String> classes = new LinkedHashSet<>();
        List<String> entries = new ArrayList<>();
        String declaredClass = null;
        String declaredId = null;
        try (JarFile jar = new JarFile(file.toFile(), true)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                declaredClass = attributes.getValue("Microbot-Plugin-Class");
                declaredId = attributes.getValue("Microbot-Plugin-Id");
                if (attributes.getValue(Attributes.Name.CLASS_PATH) != null) {
                    throw new IOException("Plugin JARs must include their private dependencies; manifest Class-Path is unsupported.");
                }
            }
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) {
                    continue;
                }
                if (entry.getName().startsWith("META-INF/versions/")) {
                    throw new IOException("Multi-release plugin JARs are unsupported. Build a Java 11 plugin JAR.");
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassReader reader = readClass(entry.getName(), input.readAllBytes());
                    String className = reader.getClassName().replace('/', '.');
                    if (!entry.getName().equals(reader.getClassName() + ".class") || !classes.add(className)) {
                        throw new IOException("Duplicate or incorrectly named class: " + className);
                    }
                    if (isSharedClass(className)) {
                        throw new IOException("Plugin bundles shared client classes: " + className
                                + ". Build with the client as a compile-only dependency.");
                    }
                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (descriptor.equals("Lnet/runelite/client/plugins/PluginDescriptor;")) {
                                entries.add(className);
                            }
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        Collections.sort(entries);
        if (entries.isEmpty()) {
            throw new IOException("The JAR contains no @PluginDescriptor plugin.");
        }
        if (declaredClass != null && !entries.contains(declaredClass)) {
            throw new IOException("Microbot-Plugin-Class does not identify a plugin in this JAR.");
        }
        if (declaredClass != null) {
            entries.remove(declaredClass);
            entries.add(0, declaredClass);
        }
        PluginJarClassLoader loader = new PluginJarClassLoader(file.toFile(),
                LivePluginArtifact.class.getClassLoader(), classes);
        try {
            List<Class<? extends Plugin>> types = new ArrayList<>();
            for (String entry : entries) {
                Class<?> type = Class.forName(entry, false, loader);
                if (type.getSuperclass() != Plugin.class) {
                    throw new IOException("Plugin must directly extend Plugin: " + entry);
                }
                PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
                if (descriptor.alwaysOn() || descriptor.hidden()) {
                    throw new IOException("Hidden or always-on plugins cannot be managed live: " + entry);
                }
                if (!Rs2UiHelper.isClientVersionCompatible(descriptor.minClientVersion())) {
                    throw new BlockedPluginException(type, "requires client " + descriptor.minClientVersion()
                            + " or newer (current " + RuneLiteProperties.getMicrobotVersion() + ")");
                }
                if (descriptor.disable()) {
                    throw new BlockedPluginException(type, "turned off by its author");
                }
                types.add(type.asSubclass(Plugin.class));
            }
            String id = declaredId == null ? entries.get(0) : declaredId.trim();
            if (id.isEmpty() || id.length() > 200 || !id.matches("[A-Za-z0-9_.-]+")) {
                throw new IOException("Invalid Microbot-Plugin-Id.");
            }
            return new LivePluginArtifact(file, loader, types, id);
        } catch (Throwable failure) {
            loader.close();
            throw failure;
        }
    }

    static ClassReader readClass(String entryName, byte[] bytes) throws IOException {
        if (bytes.length < 8) {
            throw new IOException("Malformed class file in plugin JAR: " + entryName);
        }
        int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        int classJava = major - 44;
        int runtimeJava = Runtime.version().feature();
        if (classJava > runtimeJava) {
            throw new IOException("This plugin was built for Java " + classJava + ", but your client runs on Java "
                    + runtimeJava + ", so it can't be loaded. Ask the plugin author to rebuild it for Java 11"
                    + " (--release 11). (class: " + entryName + ")");
        }
        try {
            return new ClassReader(bytes);
        } catch (IllegalArgumentException ex) {
            throw new IOException("This plugin contains a class the client can't read (built for Java " + classJava
                    + "). Ask the plugin author to rebuild it for Java 11 (--release 11). (class: " + entryName + ")", ex);
        }
    }

    private static boolean isSharedClass(String name) {
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("net.runelite.api.")
                || name.startsWith("com.google.inject.") || name.startsWith("com.google.common.")
                || name.startsWith("org.slf4j.") || name.startsWith("okhttp3.")) {
            return true;
        }
        if (!name.startsWith("net.runelite.client.")) {
            return false;
        }
        // Existing core/API classes must always retain their parent identity. Migrated plugins
        // are absent from the packaged client; importing a bundled plugin in an IDE is rejected.
        return LivePluginArtifact.class.getClassLoader().getResource(name.replace('.', '/') + ".class") != null;
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }

    static final class BlockedPluginException extends IOException {
        final String internalName;
        final String displayName;
        final String version;
        final String reason;

        BlockedPluginException(Class<?> type, String reason) {
            this(type.getSimpleName(), type.getAnnotation(PluginDescriptor.class), reason);
        }

        BlockedPluginException(String internalName, PluginDescriptor descriptor, String reason) {
            super(Text.removeTags(descriptor.name()) + " was not loaded: " + reason + ".");
            this.internalName = internalName;
            this.displayName = Text.removeTags(descriptor.name());
            this.version = descriptor.version();
            this.reason = reason;
        }
    }
}
