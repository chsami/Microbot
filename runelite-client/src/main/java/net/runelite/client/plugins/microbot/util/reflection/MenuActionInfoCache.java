package net.runelite.client.plugins.microbot.util.reflection;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/**
 * Persistent, per-install cache of the vanilla-menuAction reflection target.
 * On cache hit we resolve entirely through plain reflection, so the ASM scanning
 * path (and therefore the entire ASM library) is never class-loaded. That turns
 * the presence of {@code MenuActionAsmResolver}/{@code org.objectweb.asm.*} in
 * the runtime class list into a first-launch-only signal instead of a steady-state
 * fingerprint.
 *
 * The cache is validated on each load by re-resolving the method against the
 * current JVM — if the injected-client class layout shifts (renamed owner,
 * dropped method, descriptor change), we bust and fall back to a fresh ASM scan.
 */
@Slf4j
public final class MenuActionInfoCache {

    static final String FILE_NAME = ".menu-action-info.properties";
    static final String KEY_OWNER = "menuAction.ownerClass";
    static final String KEY_METHOD = "menuAction.methodName";
    static final String KEY_DESCRIPTOR = "menuAction.descriptor";
    static final String KEY_GARBAGE_KIND = "menuAction.garbageKind";
    static final String KEY_GARBAGE_VALUE = "menuAction.garbageValue";

    static final String BUNDLED_RESOURCE = "menu-action-info.properties";

    private MenuActionInfoCache() {
    }

    public static Path defaultPath() {
        return Paths.get(System.getProperty("user.home"), ".runelite", FILE_NAME);
    }

    public static MenuActionAsmResolver.Resolution load() {
        MenuActionAsmResolver.Resolution user = load(defaultPath());
        if (user != null) return user;
        return loadBundled();
    }

    /**
     * Classpath fallback: packagers can ship a pre-computed cache resource
     * ({@value #BUNDLED_RESOURCE}) built against a known injected-client
     * version. First-run clients on that exact injected-client resolve
     * through the bundled cache without ever touching ASM. Version drift
     * triggers the same bust logic as a stale user cache.
     */
    static MenuActionAsmResolver.Resolution loadBundled() {
        try (InputStream in = MenuActionInfoCache.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) return null;
            Properties props = new Properties();
            props.load(in);
            return resolveProps(props, "bundled");
        } catch (IOException e) {
            log.debug("bundled menu-action resource read failed: {}", e.getMessage());
            return null;
        }
    }

    static MenuActionAsmResolver.Resolution load(Path path) {
        if (!Files.exists(path)) return null;
        Properties props = new Properties();
        try (var in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            log.debug("menu-action cache read failed ({}): {}", path, e.getMessage());
            return null;
        }
        return resolveProps(props, path.toString());
    }

    static MenuActionAsmResolver.Resolution resolveProps(Properties props, String source) {
        String owner = props.getProperty(KEY_OWNER);
        String methodName = props.getProperty(KEY_METHOD);
        String descriptor = props.getProperty(KEY_DESCRIPTOR);
        String garbageKind = props.getProperty(KEY_GARBAGE_KIND);
        String garbageValueRaw = props.getProperty(KEY_GARBAGE_VALUE);

        if (owner == null || methodName == null || descriptor == null
                || garbageKind == null || garbageValueRaw == null) {
            return null;
        }

        if (!MenuActionDescriptor.isVanilla(descriptor)) {
            log.info("menu-action cache ignored ({}): stored descriptor '{}' is not a supported menu action", source, descriptor);
            return null;
        }

        Object garbage = decodeGarbage(garbageKind, garbageValueRaw);
        if (garbage == null) {
            log.info("menu-action cache ignored ({}): unrecognised garbage kind '{}'", source, garbageKind);
            return null;
        }

        garbage = MenuActionDescriptor.normalizeGarbage(garbage, descriptor);
        if (garbage == null) {
            log.info("menu-action cache ignored ({}): garbage value '{}' is invalid for descriptor '{}'",
                    source, garbageValueRaw, descriptor);
            return null;
        }

        try {
            Class<?> ownerClass = Class.forName(owner);
            Class<?>[] parameterTypes = MethodType.fromMethodDescriptorString(
                    descriptor, ownerClass.getClassLoader()).parameterArray();
            Method method = ownerClass.getDeclaredMethod(methodName, parameterTypes);
            return new MenuActionAsmResolver.Resolution(method, garbage);
        } catch (ClassNotFoundException e) {
            log.info("menu-action cache busted ({}): owner class '{}' not on classpath", source, owner);
            return null;
        } catch (IllegalArgumentException | TypeNotPresentException | NoSuchMethodException e) {
            log.info("menu-action cache busted ({}): {}#{}{} no longer resolves", source, owner, methodName, descriptor);
            return null;
        }
    }

    public static void store(MenuActionAsmResolver.Resolution resolution) {
        store(resolution, defaultPath());
    }

    static void store(MenuActionAsmResolver.Resolution resolution, Path path) {
        if (resolution == null || resolution.method == null || resolution.garbageValue == null) return;
        Properties props = new Properties();
        props.setProperty(KEY_OWNER, resolution.method.getDeclaringClass().getName());
        props.setProperty(KEY_METHOD, resolution.method.getName());
        props.setProperty(KEY_DESCRIPTOR, MethodType.methodType(
                resolution.method.getReturnType(), resolution.method.getParameterTypes()).toMethodDescriptorString());
        props.setProperty(KEY_GARBAGE_KIND, resolution.garbageValue.getClass().getSimpleName());
        props.setProperty(KEY_GARBAGE_VALUE, resolution.garbageValue.toString());

        try {
            Path dir = path.getParent();
            if (dir != null) Files.createDirectories(dir);
            try (var out = Files.newOutputStream(path)) {
                props.store(out, "menu-action reflection cache — safe to delete");
            }
            applyUserOnlyPerms(path);
        } catch (IOException e) {
            log.warn("menu-action cache write failed ({}): {}", path, e.getMessage());
        }
    }

    public static void invalidate() {
        invalidate(defaultPath());
    }

    static void invalidate(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static Object decodeGarbage(String kind, String raw) {
        try {
            switch (kind) {
                case "Integer": return Integer.valueOf(raw);
                case "Byte": return Byte.valueOf(raw);
                case "Short": return Short.valueOf(raw);
                case "Long": return Long.valueOf(raw);
                default: return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void applyUserOnlyPerms(Path path) {
        boolean posix = java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (posix) {
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (IOException ignored) {
            }
        }
    }
}
