package net.runelite.client.plugins.microbot.util.reflection;

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Isolates all ASM usage behind a single class so the rest of the microbot codebase
 * (notably {@link Rs2Reflection}) does not carry {@code org.objectweb.asm.*} strings
 * in its constant pool. A forensic scan of {@code Rs2Reflection.class} will not show
 * ASM imports anymore; only this file does — and it is loaded lazily only when the
 * vanilla menuAction descriptor actually needs resolving.
 *
 * This is P7-b per docs/DETECTION_HARDENING.md — "keep ASM runtime use localised"
 * — and prepares the ground for a fully build-time solution if we ever want to
 * ship the resolved values as a resource and remove the runtime dependency entirely.
 */
@Slf4j
public final class MenuActionAsmResolver {

    static final String MENU_ACTION_DESCRIPTOR_RUNELITE =
            "(IILnet/runelite/api/MenuAction;IILjava/lang/String;Ljava/lang/String;)V";
    public static final class Resolution {
        public final Method method;
        public final Object garbageValue;

        public Resolution(Method method, Object garbageValue) {
            this.method = method;
            this.garbageValue = garbageValue;
        }
    }

    private MenuActionAsmResolver() {
    }

    public static Resolution resolve(Class<?> clientClazz) throws Exception {
        ClassReader classReader = new ClassReader(clientClazz.getName());
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        classReader.accept(classNode, ClassReader.SKIP_FRAMES);

        MethodNode target = classNode.methods.stream()
                .filter(m -> m.access == Opcodes.ACC_PUBLIC
                        && ((m.name.equals("menuAction") && m.desc.equals(MENU_ACTION_DESCRIPTOR_RUNELITE))
                        || (m.name.equals("openWorldHopper") && m.desc.equals("()V"))
                        || (m.name.equals("hopToWorld") && m.desc.equals("(Lnet/runelite/api/World;)V"))))
                .findFirst()
                .orElse(null);

        if (target == null) return null;

        InsnList instructions = target.instructions;
        for (AbstractInsnNode insnNode : instructions) {
            if (!((insnNode instanceof LdcInsnNode || insnNode instanceof IntInsnNode)
                    && insnNode.getNext() instanceof MethodInsnNode)) continue;

            Object garbage = null;
            if (insnNode instanceof LdcInsnNode) {
                garbage = ((LdcInsnNode) insnNode).cst;
            } else {
                IntInsnNode ii = (IntInsnNode) insnNode;
                if (ii.getOpcode() == Opcodes.BIPUSH) garbage = (byte) ii.operand;
                else if (ii.getOpcode() == Opcodes.SIPUSH) garbage = (short) ii.operand;
            }

            MethodInsnNode methodInsn = (MethodInsnNode) insnNode.getNext();
            if (!MenuActionDescriptor.isVanilla(methodInsn.desc)) continue;

            garbage = MenuActionDescriptor.normalizeGarbage(garbage, methodInsn.desc);
            if (garbage == null) return null;

            Method method = Arrays.stream(Class.forName(methodInsn.owner).getDeclaredMethods())
                    .filter(m -> m.getName().equals(methodInsn.name)
                            && org.objectweb.asm.Type.getMethodDescriptor(m).equals(methodInsn.desc))
                    .findFirst()
                    .orElse(null);

            if (method == null) return null;
            return new Resolution(method, garbage);
        }

        return null;
    }

}
