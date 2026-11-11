package com.microbot.hwid;

import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Routes the platform details the game puts in its login packet through {@link HwidAgent} so they
 * can look like a stock RuneLite launch: the hash of the callbacks class signers, and the executable,
 * parent process and java agent names. The classes are found by what they call, never by their
 * obfuscated names. A shape that is not an exact match is left alone. The signer hook only counts as
 * installed when every {@code Class.getSigners()} call in every class that has one was rewritten.
 */
public final class PlatformMetadataTransformer implements ClassFileTransformer
{
	private static final Logger log = Logger.getLogger(PlatformMetadataTransformer.class.getName());

	private static final String HOOK_CLASS_INTERNAL = "com/microbot/hwid/HwidAgent";
	private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";
	private static final String CALLBACKS_DESCRIPTOR = "Lnet/runelite/api/hooks/Callbacks;";

	static final int PHASE_EXECUTABLE = 0;
	static final int PHASE_PARENT = 1;
	static final int PHASE_AGENT = 2;

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
		ProtectionDomain protectionDomain, byte[] classfileBuffer)
	{
		// The obfuscated game classes live in the default package.
		if (classBeingRedefined != null || classfileBuffer == null || className == null || className.indexOf('/') >= 0)
		{
			return null;
		}

		boolean signerCandidate = containsBytes(classfileBuffer, "getSigners");
		boolean processCandidate = containsBytes(classfileBuffer, "ProcessHandle")
			&& containsBytes(classfileBuffer, "getInputArguments");
		if (!signerCandidate && !processCandidate)
		{
			return null;
		}

		try
		{
			ClassNode node = new ClassNode();
			new ClassReader(classfileBuffer).accept(node, ClassReader.SKIP_FRAMES);

			List<MethodInsnNode> signerHashes = signerCandidate ? signerHashCalls(node) : new ArrayList<>();
			int signerSites = signerCandidate ? getSignersCalls(node) : 0;
			if (signerHashes.size() != signerSites)
			{
				// A getSigners call in a shape we do not know would still send the real hash. Leave every
				// signer site in this class alone, so the class stays self-consistent, and keep the platform
				// hook unreported for the whole session, so the login check warns.
				HwidAgent.markSignerSiteMissed();
				log.warning("Platform metadata: " + (signerSites - signerHashes.size()) + " of " + signerSites
					+ " signer calls in " + className + " have an unknown shape; signer hash left unchanged there");
				signerHashes = new ArrayList<>();
			}
			List<StringWrite> processWrites = processCandidate ? processWrites(node) : new ArrayList<>();
			if (signerHashes.isEmpty() && processWrites.isEmpty())
			{
				return null;
			}

			for (MethodInsnNode call : signerHashes)
			{
				call.owner = HOOK_CLASS_INTERNAL;
				call.name = "signerHash";
			}
			for (StringWrite write : processWrites)
			{
				InsnList hook = new InsnList();
				hook.add(new IntInsnNode(Opcodes.BIPUSH, write.phase));
				hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_CLASS_INTERNAL, "platformName",
					"(" + STRING_DESCRIPTOR + "I)" + STRING_DESCRIPTOR, false));
				write.method.instructions.insertBefore(write.putField, hook);
			}

			FrameClassWriter writer = new FrameClassWriter(loader);
			node.accept(writer);
			byte[] patched = writer.toByteArray();
			if (!signerHashes.isEmpty())
			{
				HwidAgent.markSignerHookInstalled();
			}
			if (!processWrites.isEmpty())
			{
				HwidAgent.markProcessHookInstalled();
			}
			log.info("Platform metadata hooks installed on " + className);
			return patched;
		}
		catch (Throwable t)
		{
			if (signerCandidate)
			{
				// The class may hold signer calls that now go out unchanged; never let another class report them as hooked.
				HwidAgent.markSignerSiteMissed();
			}
			log.warning("Platform metadata patch failed for " + className + ": " + t);
			return null;
		}
	}

	/** Every {@code Class.getSigners()} call in the class, whatever surrounds it. */
	static int getSignersCalls(ClassNode node)
	{
		int calls = 0;
		for (MethodNode method : node.methods)
		{
			for (AbstractInsnNode insn : method.instructions)
			{
				if (isCall(insn, Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getSigners", "()[Ljava/lang/Object;"))
				{
					calls++;
				}
			}
		}
		return calls;
	}

	/** Every {@code Arrays.hashCode(callbacks.getClass().getSigners())}; the obfuscator duplicates the writer. */
	static List<MethodInsnNode> signerHashCalls(ClassNode node)
	{
		List<MethodInsnNode> calls = new ArrayList<>();
		for (MethodNode method : node.methods)
		{
			for (AbstractInsnNode insn : method.instructions)
			{
				if (!isCall(insn, Opcodes.INVOKESTATIC, "java/util/Arrays", "hashCode", "([Ljava/lang/Object;)I"))
				{
					continue;
				}
				AbstractInsnNode getSigners = previousInstruction(insn);
				AbstractInsnNode getClass = previousInstruction(getSigners);
				AbstractInsnNode callbacks = previousInstruction(getClass);
				if (isCall(getSigners, Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getSigners", "()[Ljava/lang/Object;")
					&& isCall(getClass, Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;")
					&& callbacks != null && callbacks.getOpcode() == Opcodes.GETFIELD
					&& CALLBACKS_DESCRIPTOR.equals(((FieldInsnNode) callbacks).desc))
				{
					calls.add((MethodInsnNode) insn);
				}
			}
		}
		return calls;
	}

	/**
	 * The one static method that calls {@code ProcessHandle.current()}, then {@code .parent()}, then
	 * {@code RuntimeMXBean.getInputArguments()}, storing each result in its own String field of one class.
	 * Returns the stores after each call, or nothing unless all three phases are present and unambiguous.
	 */
	static List<StringWrite> processWrites(ClassNode node)
	{
		List<StringWrite> found = new ArrayList<>();
		int collectors = 0;
		for (MethodNode method : node.methods)
		{
			if ((method.access & Opcodes.ACC_STATIC) == 0)
			{
				continue;
			}
			int phase = -1;
			int phasesSeen = 0;
			String owner = null;
			String[] fields = new String[3];
			List<StringWrite> writes = new ArrayList<>();
			boolean valid = true;
			for (AbstractInsnNode insn : method.instructions)
			{
				int next = phaseStartedBy(insn);
				if (next >= 0)
				{
					valid &= next == phase + 1;
					phase = next;
					phasesSeen++;
				}
				else if (insn.getOpcode() == Opcodes.PUTFIELD && STRING_DESCRIPTOR.equals(((FieldInsnNode) insn).desc))
				{
					FieldInsnNode put = (FieldInsnNode) insn;
					if (phase < 0 || owner != null && !owner.equals(put.owner)
						|| fields[phase] != null && !fields[phase].equals(put.name))
					{
						valid = false;
						break;
					}
					owner = put.owner;
					fields[phase] = put.name;
					writes.add(new StringWrite(method, put, phase));
				}
			}
			if (phasesSeen == 0)
			{
				continue;
			}
			collectors++;
			boolean complete = valid && phasesSeen == 3 && fields[0] != null && fields[1] != null && fields[2] != null
				&& !fields[0].equals(fields[1]) && !fields[1].equals(fields[2]) && !fields[0].equals(fields[2]);
			if (complete)
			{
				found = writes;
			}
		}
		return collectors == 1 ? found : new ArrayList<>();
	}

	private static int phaseStartedBy(AbstractInsnNode insn)
	{
		if (isCall(insn, Opcodes.INVOKESTATIC, "java/lang/ProcessHandle", "current", "()Ljava/lang/ProcessHandle;"))
		{
			return PHASE_EXECUTABLE;
		}
		if (isCall(insn, Opcodes.INVOKEINTERFACE, "java/lang/ProcessHandle", "parent", "()Ljava/util/Optional;"))
		{
			return PHASE_PARENT;
		}
		if (isCall(insn, Opcodes.INVOKEINTERFACE, "java/lang/management/RuntimeMXBean", "getInputArguments", "()Ljava/util/List;"))
		{
			return PHASE_AGENT;
		}
		return -1;
	}

	private static boolean isCall(AbstractInsnNode insn, int opcode, String owner, String name, String desc)
	{
		if (insn == null || insn.getOpcode() != opcode || !(insn instanceof MethodInsnNode))
		{
			return false;
		}
		MethodInsnNode call = (MethodInsnNode) insn;
		return owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc);
	}

	private static AbstractInsnNode previousInstruction(AbstractInsnNode insn)
	{
		AbstractInsnNode current = insn == null ? null : insn.getPrevious();
		while (current != null && current.getOpcode() < 0)
		{
			current = current.getPrevious();
		}
		return current;
	}

	private static boolean containsBytes(byte[] bytes, String marker)
	{
		byte[] pattern = marker.getBytes(StandardCharsets.US_ASCII);
		outer:
		for (int i = 0; i <= bytes.length - pattern.length; i++)
		{
			for (int j = 0; j < pattern.length; j++)
			{
				if (bytes[i + j] != pattern[j])
				{
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	static final class StringWrite
	{
		final MethodNode method;
		final FieldInsnNode putField;
		final int phase;

		StringWrite(MethodNode method, FieldInsnNode putField, int phase)
		{
			this.method = method;
			this.putField = putField;
			this.phase = phase;
		}
	}
}
