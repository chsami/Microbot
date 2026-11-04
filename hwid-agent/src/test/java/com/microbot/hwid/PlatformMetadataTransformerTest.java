package com.microbot.hwid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
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
		assertEquals(-991467620, HwidAgent.signerHash(signers));
		assertEquals(-991467620, HwidAgent.signerHash(null));
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
	public void processNamesAreLeftAloneOnOtherSystems()
	{
		System.setProperty("os.name", "Windows 11");
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "true");
		assertEquals("javaw", HwidAgent.platformName("javaw", PlatformMetadataTransformer.PHASE_EXECUTABLE));
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
