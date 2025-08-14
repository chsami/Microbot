package net.runelite.client.plugins.microbot.externalplugins;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ReflectUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Slf4j
public class MicrobotPluginClassLoader extends ClassLoader  implements ReflectUtil.PrivateLookupableClassLoader {

    @Getter
    @Setter
    private MethodHandles.Lookup lookup;

    private final Map<String, byte[]> classBytes = new HashMap<>();

    public MicrobotPluginClassLoader(byte[] jarBytes, ClassLoader parent) {
        super(parent);
        loadJarBytes(jarBytes);
        ReflectUtil.installLookupHelper(this);
    }

    private void loadJarBytes(byte[] jarBytes) {
        try (JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace("/", ".").replace(".class", "");
                    byte[] classData = jarInputStream.readAllBytes();
                    classBytes.put(className, classData);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load JAR bytes", e);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classData = classBytes.get(name);
        if (classData == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, classData, 0, classData.length);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (classBytes.containsKey(name)) {
            return new ByteArrayInputStream(classBytes.get(name));
        }
        return super.getResourceAsStream(name);
    }

    // **Expose class names**
    public Set<String> getLoadedClassNames() {
        return classBytes.keySet();
    }

    @Override
    public Class<?> defineClass0(String name, byte[] b, int off, int len) throws ClassFormatError {
        return super.defineClass(name, b, off, len);
    }
}
