package net.runelite.client.plugins.microbot.externalplugins;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ReflectUtil;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
public class PluginJarClassLoader extends URLClassLoader implements ReflectUtil.PrivateLookupableClassLoader {

    @Getter
    @Setter
    private MethodHandles.Lookup lookup;

    private final ClassLoader parent;
    private final Set<String> ownedClasses;

    public PluginJarClassLoader(File jarFile, ClassLoader parent) throws IOException {
        this(jarFile, parent, null);
    }

    public PluginJarClassLoader(File jarFile, ClassLoader parent, Set<String> ownedClasses) throws IOException {
        super(new URL[]{jarFile.toURI().toURL()}, null);
        this.parent = parent;
        this.ownedClasses = ownedClasses == null ? null : new LinkedHashSet<>(ownedClasses);
        ReflectUtil.installLookupHelper(this);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                if (ownedClasses != null && !ownedClasses.contains(name)) {
                    result = parent.loadClass(name);
                } else {
                    try {
                        result = super.loadClass(name, false);
                    } catch (ClassNotFoundException ex) {
                        result = parent.loadClass(name);
                    }
                }
            }
            if (resolve) {
                resolveClass(result);
            }
            return result;
        }
    }

    @Override
    public URL getResource(String name) {
        URL resource = findResource(name);
        return resource != null ? resource : parent.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> resources = new LinkedHashSet<>(Collections.list(findResources(name)));
        resources.addAll(Collections.list(parent.getResources(name)));
        return Collections.enumeration(resources);
    }

    @Override
    public Class<?> defineClass0(String name, byte[] b, int off, int len) throws ClassFormatError {
        return super.defineClass(name, b, off, len);
    }
}
