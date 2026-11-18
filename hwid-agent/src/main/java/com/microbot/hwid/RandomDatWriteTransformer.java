package com.microbot.hwid;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class RandomDatWriteTransformer implements ClassFileTransformer
{
	private static final Logger log = Logger.getLogger(RandomDatWriteTransformer.class.getName());

	private static final String HOOK_CLASS_INTERNAL = "com/microbot/hwid/HwidAgent";
	private static final String HOOK_METHOD = "randomDatFile";
	private static final String HOOK_DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/Object;";

	private static final Object lock = new Object();
	private static final List<String> patchedMethods = new ArrayList<>();

	public byte[] transform(ClassLoader loader, String className, Class<?> beingLoaded, ProtectionDomain pd, byte[] bytes)
	{
		try
		{
			return transformInternal(loader, className, bytes);
		}
		catch (Throwable t)
		{
			log.warning("hwid-agent random.dat write transform skipped on " + className + ": " + t);
			return null;
		}
	}

	private byte[] transformInternal(ClassLoader loader, String className, byte[] bytes)
	{
		if (className == null || bytes == null || RandomDatTransformer.looksUnlikely(className))
		{
			return null;
		}

		ClassReader reader = new ClassReader(bytes);
		ClassNode node = new ClassNode();
		reader.accept(node, ClassReader.SKIP_FRAMES);

		List<FieldInsnNode> guards = new ArrayList<>();
		List<String> methods = new ArrayList<>();
		for (MethodNode method : node.methods)
		{
			if (alreadyPatched(method) || !hasSeedLength(method))
			{
				continue;
			}
			Set<String> rewound = rewoundHandles(method);
			if (rewound.isEmpty())
			{
				continue;
			}
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				if (insn.getOpcode() == Opcodes.GETSTATIC
					&& rewound.contains(key((FieldInsnNode) insn))
					&& isNullGuard(insn))
				{
					guards.add((FieldInsnNode) insn);
					methods.add(node.name + "." + method.name + method.desc);
				}
			}
		}

		if (guards.isEmpty())
		{
			return null;
		}

		for (FieldInsnNode guard : guards)
		{
			MethodNode method = findMethod(node, guard);
			method.instructions.insert(guard, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_CLASS_INTERNAL, HOOK_METHOD, HOOK_DESCRIPTOR, false));
		}

		synchronized (lock)
		{
			patchedMethods.addAll(methods);
		}
		log.info("hwid-agent random.dat write guard installed on " + node.name + " (" + guards.size() + " sites)");

		FrameClassWriter writer = new FrameClassWriter(loader);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static String key(FieldInsnNode field)
	{
		return field.owner + "." + field.name + ":" + field.desc;
	}

	private static boolean hasSeedLength(MethodNode method)
	{
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 24)
			{
				return true;
			}
		}
		return false;
	}

	private static Set<String> rewoundHandles(MethodNode method)
	{
		Set<String> handles = new HashSet<>();
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			if (insn.getOpcode() != Opcodes.GETSTATIC)
			{
				continue;
			}
			FieldInsnNode field = (FieldInsnNode) insn;
			if (!field.desc.startsWith("L") || field.desc.startsWith("Ljava/"))
			{
				continue;
			}
			AbstractInsnNode offset = RandomDatTransformer.nextReal(insn);
			AbstractInsnNode seek = offset == null ? null : RandomDatTransformer.nextReal(offset);
			if (offset != null
				&& offset.getOpcode() == Opcodes.LCONST_0
				&& seek instanceof MethodInsnNode
				&& isSeek((MethodInsnNode) seek, field.desc))
			{
				handles.add(key(field));
			}
		}
		return handles;
	}

	private static boolean isSeek(MethodInsnNode seek, String handleDesc)
	{
		if (!handleDesc.equals("L" + seek.owner + ";"))
		{
			return false;
		}
		if (seek.getOpcode() == Opcodes.INVOKEVIRTUAL)
		{
			return seek.desc.equals("(J)V");
		}
		return seek.getOpcode() == Opcodes.INVOKESTATIC && seek.desc.equals("(" + handleDesc + "J)V");
	}

	private static boolean isNullGuard(AbstractInsnNode insn)
	{
		AbstractInsnNode next = RandomDatTransformer.nextReal(insn);
		if (next == null)
		{
			return false;
		}
		int op = next.getOpcode();
		if (op == Opcodes.IFNULL || op == Opcodes.IFNONNULL)
		{
			return true;
		}
		if (RandomDatTransformer.isReferenceCompare(op))
		{
			AbstractInsnNode previous = RandomDatTransformer.previousReal(insn);
			return previous != null && previous.getOpcode() == Opcodes.ACONST_NULL;
		}
		if (op == Opcodes.ACONST_NULL)
		{
			AbstractInsnNode compare = RandomDatTransformer.nextReal(next);
			return compare != null && RandomDatTransformer.isReferenceCompare(compare.getOpcode());
		}
		return false;
	}

	private static boolean alreadyPatched(MethodNode method)
	{
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			if (insn instanceof MethodInsnNode
				&& HOOK_CLASS_INTERNAL.equals(((MethodInsnNode) insn).owner)
				&& HOOK_METHOD.equals(((MethodInsnNode) insn).name))
			{
				return true;
			}
		}
		return false;
	}

	private static MethodNode findMethod(ClassNode node, AbstractInsnNode insn)
	{
		for (MethodNode method : node.methods)
		{
			if (method.instructions.contains(insn))
			{
				return method;
			}
		}
		throw new IllegalStateException("guard instruction without method");
	}

	static List<String> patchedMethods()
	{
		synchronized (lock)
		{
			return new ArrayList<>(patchedMethods);
		}
	}

	static void resetForTesting()
	{
		synchronized (lock)
		{
			patchedMethods.clear();
		}
	}
}
