package net.runelite.client.plugins.microbot.externalplugins;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ReflectUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

@Slf4j
public class MicrobotPluginClassLoader extends ClassLoader implements ReflectUtil.PrivateLookupableClassLoader {

    @Getter
    @Setter
    private MethodHandles.Lookup lookup;

    private final Map<String, byte[]> classBytes = new HashMap<>();
    private final Map<String, byte[]> resourceBytes = new HashMap<>();
    @Getter
    private final Manifest manifest;
    private final URL jarUrl;
    @Getter
    private final String jarFileName;

    public MicrobotPluginClassLoader(ClassLoader parent, String jarFileName, byte[] jarBytes) {
        super(parent);
        this.jarFileName = jarFileName;
        this.jarUrl = createJarUrl(jarFileName);
        this.manifest = loadJarBytes(jarBytes);
        ReflectUtil.installLookupHelper(this);
    }

    private URL createJarUrl(String fileName) {
        try {
            return new URL("jar:file:///" + fileName + "!/");
        } catch (Exception e) {
            log.warn("Failed to create jar URL for {}", fileName, e);
            return null;
        }
    }

    private Manifest loadJarBytes(byte[] jarBytes) {
        Manifest jarManifest = null;
        try (JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            jarManifest = jarInputStream.getManifest();

            JarEntry entry;
            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] entryData = jarInputStream.readAllBytes();

                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName().replace("/", ".").replace(".class", "");
                        classBytes.put(className, entryData);
                        log.debug("Loaded class: {}", className);
                    } else {
                        resourceBytes.put(entry.getName(), entryData);
                        log.debug("Loaded resource: {}", entry.getName());
                    }
                }
            }

            log.info("Loaded {} classes and {} resources from plugin JAR: {}", classBytes.size(), resourceBytes.size(), jarFileName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load JAR bytes", e);
        }
        return jarManifest;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classData = classBytes.get(name);
        if (classData == null) {
            throw new ClassNotFoundException(name);
        }

        ProtectionDomain protectionDomain = null;
        if (jarUrl != null) {
            CodeSource codeSource = new CodeSource(jarUrl, (java.security.cert.Certificate[]) null);
            protectionDomain = new ProtectionDomain(codeSource, null);
        }

        return defineClass(name, classData, 0, classData.length, protectionDomain);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] resourceData = resourceBytes.get(name);
        if (resourceData != null) {
            log.debug("Found resource in JAR: {} from {}", name, jarFileName);
            return new ByteArrayInputStream(resourceData);
        }

        if (name.startsWith("/")) {
            resourceData = resourceBytes.get(name.substring(1));
            if (resourceData != null) {
                log.debug("Found resource in JAR (without leading slash): {} from {}", name, jarFileName);
                return new ByteArrayInputStream(resourceData);
            }
        }

        return super.getResourceAsStream(name);
    }

    @Override
    public URL getResource(String name) {
        if (resourceBytes.containsKey(name) ||
            (name.startsWith("/") && resourceBytes.containsKey(name.substring(1)))) {
            try {
                return new URL("jar:file:///" + jarFileName + "!/" + (name.startsWith("/") ? name.substring(1) : name));
            } catch (Exception e) {
                log.warn("Failed to create resource URL for: {} in {}", name, jarFileName, e);
            }
        }

        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>();

        URL ourResource = getResource(name);
        if (ourResource != null) {
            urls.add(ourResource);
        }

        Enumeration<URL> parentResources = super.getResources(name);
        while (parentResources.hasMoreElements()) {
            urls.add(parentResources.nextElement());
        }

        return Collections.enumeration(urls);
    }

    /**
     * Get all loaded resource names
     */
    public Set<String> getLoadedResourceNames() {
        return new HashSet<>(resourceBytes.keySet());
    }

    /**
     * Check if a specific resource exists in the loaded JAR
     */
    public boolean hasResource(String name) {
        return resourceBytes.containsKey(name) ||
               (name.startsWith("/") && resourceBytes.containsKey(name.substring(1)));
    }

    /**
     * Expose class names
     */
    public Set<String> getLoadedClassNames() {
        return new HashSet<>(classBytes.keySet());
    }

    @Override
    public Class<?> defineClass0(String name, byte[] b, int off, int len) throws ClassFormatError {
        return super.defineClass(name, b, off, len);
    }
}
