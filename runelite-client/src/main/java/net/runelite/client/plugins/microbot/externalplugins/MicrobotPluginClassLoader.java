package net.runelite.client.plugins.microbot.externalplugins;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

@Slf4j
public class MicrobotPluginClassLoader extends URLClassLoader {
    private final ClassLoader parent;

    public MicrobotPluginClassLoader(File plugin, ClassLoader parent) throws MalformedURLException {
        // null parent classloader, or else class path scanning includes everything from the main class loader
        super(new URL[]{plugin.toURI().toURL()}, null);

        this.parent = parent;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {

        if (name.startsWith("net.runelite.client.plugins.microbot.pluginscheduler")) {
            return parent.loadClass(name);
        }

        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException ex) {
            // fall back to main class loader
            return parent.loadClass(name);
        }
    }
}
