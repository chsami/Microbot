package net.runelite.client.plugins.microbot.util.reflection;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Build-time generator for {@code menu-action-info.properties}. Reads {@code client.class}
 * from the runtime classpath (the injected-client jar ships it pre-patched with RuneLite
 * mixins, so the {@code menuAction} wrapper is already present), runs the same ASM scan
 * that {@link MenuActionAsmResolver} does at runtime, and writes the resolution as a
 * properties file at the destination path.
 *
 * Shipping the generated resource inside the runelite-client jar lets end users skip the
 * runtime ASM scan entirely on first launch — the cache in
 * {@link MenuActionInfoCache#loadBundled()} picks it up.
 *
 * Regenerate whenever the injected-client version bumps; the values (especially the
 * garbage int) are tied to the obfuscator seed of the specific build.
 *
 * Invocation: see the {@code seedMenuActionInfo} Gradle task.
 */
public final class MenuActionResourceSeeder {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: MenuActionResourceSeeder <output properties file>");
        }
        Path outputPath = Paths.get(args[0]);

        Raw raw = scanClasspathClient();
        if (raw == null) {
            throw new IllegalStateException("Could not locate the vanilla menuAction call in client.class — "
                    + "either the injected-client descriptor drifted or client.class is not on the classpath.");
        }

        Properties props = new Properties();
        props.setProperty(MenuActionInfoCache.KEY_OWNER, raw.ownerClassName);
        props.setProperty(MenuActionInfoCache.KEY_METHOD, raw.methodName);
        props.setProperty(MenuActionInfoCache.KEY_DESCRIPTOR, raw.descriptor);
        props.setProperty(MenuActionInfoCache.KEY_GARBAGE_KIND, raw.garbage.getClass().getSimpleName());
        props.setProperty(MenuActionInfoCache.KEY_GARBAGE_VALUE, raw.garbage.toString());

        Path parent = outputPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (var out = Files.newOutputStream(outputPath)) {
            props.store(out, "Build-time pre-seed for MenuActionInfoCache (do not edit by hand; regenerate via :client:seedMenuActionInfo)");
        }
        System.out.println("wrote " + outputPath + " — owner=" + raw.ownerClassName
                + " method=" + raw.methodName
                + " garbage=(" + raw.garbage.getClass().getSimpleName() + ") " + raw.garbage);
    }

    private static Raw scanClasspathClient() throws IOException {
        // The injected-client jar places the patched client class at the archive root as
        // "client.class" with no package, mirroring the Jagex applet convention.
        String resource = "client.class";
        try (InputStream in = MenuActionResourceSeeder.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return null;
            ClassReader reader = new ClassReader(in);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            reader.accept(node, ClassReader.SKIP_FRAMES);

            MethodNode wrapper = node.methods.stream()
                    .filter(m -> m.access == Opcodes.ACC_PUBLIC
                            && ((m.name.equals("menuAction") && m.desc.equals(MenuActionAsmResolver.MENU_ACTION_DESCRIPTOR_RUNELITE))
                            || (m.name.equals("openWorldHopper") && m.desc.equals("()V"))
                            || (m.name.equals("hopToWorld") && m.desc.equals("(Lnet/runelite/api/World;)V"))))
                    .findFirst()
                    .orElse(null);
            if (wrapper == null) return null;

            InsnList instructions = wrapper.instructions;
            for (AbstractInsnNode insn : instructions) {
                if (!((insn instanceof LdcInsnNode || insn instanceof IntInsnNode)
                        && insn.getNext() instanceof MethodInsnNode)) continue;

                Object garbage = null;
                if (insn instanceof LdcInsnNode) {
                    garbage = ((LdcInsnNode) insn).cst;
                } else {
                    IntInsnNode ii = (IntInsnNode) insn;
                    if (ii.getOpcode() == Opcodes.BIPUSH) garbage = (byte) ii.operand;
                    else if (ii.getOpcode() == Opcodes.SIPUSH) garbage = (short) ii.operand;
                }

                MethodInsnNode mi = (MethodInsnNode) insn.getNext();
                if (!MenuActionDescriptor.isVanilla(mi.desc)) continue;

                garbage = MenuActionDescriptor.normalizeGarbage(garbage, mi.desc);
                if (garbage == null) return null;

                String owner = mi.owner.replace('/', '.');
                return new Raw(owner, mi.name, mi.desc, garbage);
            }
            return null;
        }
    }

    private static final class Raw {
        final String ownerClassName;
        final String methodName;
        final String descriptor;
        final Object garbage;

        Raw(String ownerClassName, String methodName, String descriptor, Object garbage) {
            this.ownerClassName = ownerClassName;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.garbage = garbage;
        }
    }

    private MenuActionResourceSeeder() {
    }
}
