package com.microbot.hwid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class PlatformMetadataTransformerTest
{
	private String previousSpoof;
	private String previousOs;

	@Before
	public void setUp()
	{
		previousSpoof = System.getProperty(HwidAgent.TOGGLE_PROPERTY);
		previousOs = System.getProperty("os.name");
	}

	@After
	public void tearDown()
	{
		restore(HwidAgent.TOGGLE_PROPERTY, previousSpoof);
		restore("os.name", previousOs);
		HwidAgent.resetPlatformHooksForTest();
		StockSignerHash.resetForTesting();
	}

	private static void restore(String key, String value)
	{
		if (value == null)
		{
			System.clearProperty(key);
		}
		else
		{
			System.setProperty(key, value);
		}
	}

	@Test
	public void signerHashLooksStockWhileSpoofing()
	{
		Object[] signers = {"unsigned build"};
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "true");
		assertEquals(StockSignerHash.BUNDLED, HwidAgent.signerHash(signers));
		assertEquals(StockSignerHash.BUNDLED, HwidAgent.signerHash(null));
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		assertEquals(Arrays.hashCode(signers), HwidAgent.signerHash(signers));
		assertEquals(0, HwidAgent.signerHash(null));
	}

	@Test
	public void processNamesLookStockOnMacWhileSpoofing()
	{
		System.setProperty("os.name", "Mac OS X");
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "true");
		assertEquals("RuneLite", HwidAgent.platformName("java", PlatformMetadataTransformer.PHASE_EXECUTABLE));
		assertEquals("launchd", HwidAgent.platformName("zsh", PlatformMetadataTransformer.PHASE_PARENT));
		assertEquals("", HwidAgent.platformName("hwid-agent.jar", PlatformMetadataTransformer.PHASE_AGENT));
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		assertEquals("java", HwidAgent.platformName("java", PlatformMetadataTransformer.PHASE_EXECUTABLE));
	}

	@Test
	public void onlyTheAgentNameIsHiddenOnOtherSystems()
	{
		System.setProperty("os.name", "Windows 11");
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "true");
		assertEquals("javaw", HwidAgent.platformName("javaw", PlatformMetadataTransformer.PHASE_EXECUTABLE));
		assertEquals("explorer", HwidAgent.platformName("explorer", PlatformMetadataTransformer.PHASE_PARENT));
		assertEquals("", HwidAgent.platformName("hwid-agent.jar", PlatformMetadataTransformer.PHASE_AGENT));
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		assertEquals("hwid-agent.jar", HwidAgent.platformName("hwid-agent.jar", PlatformMetadataTransformer.PHASE_AGENT));
	}

	@Test
	public void incompleteCollectorIsLeftAlone() throws Exception
	{
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "Collector", null, "java/lang/Object", null);
		writer.visitField(Opcodes.ACC_PUBLIC, "executable", "Ljava/lang/String;", null, null).visitEnd();
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "collect", "(LCollector;)V", null, null);
		method.visitCode();
		method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/ProcessHandle", "current", "()Ljava/lang/ProcessHandle;", true);
		method.visitInsn(Opcodes.POP);
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitLdcInsn("x");
		method.visitFieldInsn(Opcodes.PUTFIELD, "Collector", "executable", "Ljava/lang/String;");
		method.visitLdcInsn("getInputArguments");
		method.visitInsn(Opcodes.POP);
		method.visitInsn(Opcodes.RETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
		writer.visitEnd();
		assertNull(new PlatformMetadataTransformer().transform(null, "Collector", null, null, writer.toByteArray()));
	}

	@Test
	public void classWithAnUnknownSignerSiteIsLeftAloneAndNotReported()
	{
		byte[] mixed = signerClass("Mixed", true, false);
		ClassNode original = node(mixed);
		assertEquals(2, PlatformMetadataTransformer.getSignersCalls(original));
		assertEquals(1, PlatformMetadataTransformer.signerHashCalls(original).size());

		assertNull(new PlatformMetadataTransformer().transform(null, "Mixed", null, null, mixed));
		HwidAgent.markProcessHookInstalled();
		assertNull(System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
	}

	@Test
	public void classWithOnlyKnownSignerSitesIsHookedAndReported()
	{
		byte[] patched = new PlatformMetadataTransformer().transform(null, "Known", null, null, signerClass("Known", true, true));
		assertNotNull(patched);
		assertEquals(2, hookCalls(node(patched), "signerHash"));
		HwidAgent.markProcessHookInstalled();
		assertEquals("true", System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
	}

	@Test
	public void unknownSignerSiteInALaterClassWithdrawsTheReport()
	{
		PlatformMetadataTransformer transformer = new PlatformMetadataTransformer();
		HwidAgent.markProcessHookInstalled();
		assertNotNull(transformer.transform(null, "Known", null, null, signerClass("Known", true, true)));
		assertEquals("true", System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));

		assertNull(transformer.transform(null, "Unknown", null, null, signerClass("Unknown", false, false)));
		assertNull(System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
	}

	@Test
	public void unknownSignerSiteInAnEarlierClassBlocksTheReport()
	{
		PlatformMetadataTransformer transformer = new PlatformMetadataTransformer();
		assertNull(transformer.transform(null, "Unknown", null, null, signerClass("Unknown", false, false)));
		assertNotNull(transformer.transform(null, "Known", null, null, signerClass("Known", true, true)));
		HwidAgent.markProcessHookInstalled();
		assertNull(System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
	}

	@Test
	public void everyKnownClientRevisionHooksEverySignerSite() throws Exception
	{
		List<File> jars = new ArrayList<>();
		jars.add(InjectedClientJar.locate());
		for (String path : System.getProperty("hwid.historicalClients", "").split(File.pathSeparator))
		{
			if (!path.isBlank())
			{
				jars.add(new File(path));
			}
		}
		assertNotNull("injected-client jar missing from test classpath", jars.get(0));

		for (File jar : jars)
		{
			String version = InjectedClientJar.version(jar);
			HwidAgent.resetPlatformHooksForTest();
			PlatformMetadataTransformer transformer = new PlatformMetadataTransformer();
			int signerSites = 0;
			int signerHooks = 0;
			try (ZipFile zip = new ZipFile(jar))
			{
				for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();)
				{
					ZipEntry entry = entries.nextElement();
					if (entry.isDirectory() || !entry.getName().endsWith(".class"))
					{
						continue;
					}
					byte[] bytes;
					try (InputStream in = zip.getInputStream(entry))
					{
						bytes = in.readAllBytes();
					}
					String name = entry.getName().substring(0, entry.getName().length() - ".class".length());
					signerSites += PlatformMetadataTransformer.getSignersCalls(node(bytes));
					byte[] patched = transformer.transform(null, name, null, null, bytes);
					if (patched != null)
					{
						signerHooks += hookCalls(node(patched), "signerHash");
					}
				}
			}
			assertTrue(version + ": no getSigners call found", signerSites > 0);
			assertEquals(version + ": getSigners calls left unhooked", signerSites, signerHooks);
			assertEquals(version + ": platform hook not reported", "true", System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
		}
	}

	/**
	 * One {@code Arrays.hashCode(callbacks.getClass().getSigners())} per method. The first always reads
	 * {@code callbacks} with GETFIELD, the shape the game uses; the second with GETFIELD when
	 * {@code secondKnown}, otherwise with GETSTATIC, a shape the transformer does not know.
	 */
	private static byte[] signerClass(String name, boolean firstKnown, boolean secondKnown)
	{
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		writer.visitField(Opcodes.ACC_PUBLIC, "callbacks", "Lnet/runelite/api/hooks/Callbacks;", null, null).visitEnd();
		writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "staticCallbacks", "Lnet/runelite/api/hooks/Callbacks;", null, null).visitEnd();
		signerMethod(writer, name, "first", firstKnown);
		signerMethod(writer, name, "second", secondKnown);
		writer.visitEnd();
		return writer.toByteArray();
	}

	private static void signerMethod(ClassWriter writer, String owner, String methodName, boolean known)
	{
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()I", null, null);
		method.visitCode();
		if (known)
		{
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitFieldInsn(Opcodes.GETFIELD, owner, "callbacks", "Lnet/runelite/api/hooks/Callbacks;");
		}
		else
		{
			method.visitFieldInsn(Opcodes.GETSTATIC, owner, "staticCallbacks", "Lnet/runelite/api/hooks/Callbacks;");
		}
		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getSigners", "()[Ljava/lang/Object;", false);
		method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "hashCode", "([Ljava/lang/Object;)I", false);
		method.visitInsn(Opcodes.IRETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
	}

	private static ClassNode node(byte[] bytes)
	{
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	private static int hookCalls(ClassNode node, String hook)
	{
		int calls = 0;
		for (MethodNode method : node.methods)
		{
			for (AbstractInsnNode insn : method.instructions)
			{
				if (insn instanceof MethodInsnNode && "com/microbot/hwid/HwidAgent".equals(((MethodInsnNode) insn).owner)
					&& hook.equals(((MethodInsnNode) insn).name))
				{
					calls++;
				}
			}
		}
		return calls;
	}

	@Test
	public void realInjectedClientIsHooked() throws Exception
	{
		File jar = InjectedClientJar.locate();
		assertNotNull("injected-client jar missing from test classpath", jar);

		PlatformMetadataTransformer transformer = new PlatformMetadataTransformer();
		int signerHooks = 0;
		int nameHooks = 0;
		int patchedClasses = 0;
		try (ZipFile zip = new ZipFile(jar))
		{
			for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();)
			{
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || !entry.getName().endsWith(".class"))
				{
					continue;
				}
				byte[] bytes;
				try (InputStream in = zip.getInputStream(entry))
				{
					bytes = in.readAllBytes();
				}
				String name = entry.getName().substring(0, entry.getName().length() - ".class".length());
				byte[] patched = transformer.transform(null, name, null, null, bytes);
				if (patched == null)
				{
					continue;
				}
				patchedClasses++;
				ClassNode node = new ClassNode();
				new ClassReader(patched).accept(node, 0);
				assertTrue(PlatformMetadataTransformer.signerHashCalls(node).isEmpty());
				for (MethodNode method : node.methods)
				{
					for (AbstractInsnNode insn : method.instructions)
					{
						if (insn instanceof MethodInsnNode && "com/microbot/hwid/HwidAgent".equals(((MethodInsnNode) insn).owner))
						{
							String hook = ((MethodInsnNode) insn).name;
							signerHooks += "signerHash".equals(hook) ? 1 : 0;
							nameHooks += "platformName".equals(hook) ? 1 : 0;
						}
					}
				}
			}
		}
		assertTrue("no signer hash call was hooked", signerHooks > 0);
		assertTrue("the executable, parent and agent name writers were not all hooked", nameHooks >= 3);
		assertTrue(patchedClasses >= 2);
		assertEquals("true", System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
	}
}
