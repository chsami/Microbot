package com.microbot.hwid;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class HwidAgentTest
{
	private static final String MARKER_COMMAND = "wmic csproduct get UUID";
	private static final String MARKER_OTHER = "/etc/machine-id";
	private static final String FALLBACK_DEVICE_ID = "12345678-0000-0000-0000-123456789012";

	private File storeFile;
	private File randomDatStoreFile;
	private File credentialsFile;

	@Before
	public void setUp() throws Exception
	{
		storeFile = File.createTempFile("hwid-test", ".properties");
		DeviceIdStore.setStoreFileForTesting(storeFile);
		DeviceIdStore.reset();
		System.setProperty("microbot.hwid.store", storeFile.getAbsolutePath());
		randomDatStoreFile = File.createTempFile("hwid-rd-test", ".properties");
		RandomDatStore.setStoreFileForTesting(randomDatStoreFile);
		RandomDatStore.reset();
		System.setProperty("microbot.hwid.randomdat.store", randomDatStoreFile.getAbsolutePath());
		credentialsFile = new File(randomDatStoreFile.getParentFile(), randomDatStoreFile.getName() + ".credentials");
		HwidAgent.setCredentialsFileForTesting(credentialsFile);
		HwidAgent.resetRandomDatForTesting();
	}

	@After
	public void tearDown()
	{
		System.clearProperty("microbot.hwid.store");
		System.clearProperty("microbot.hwid.randomdat.store");
		DeviceIdStore.reset();
		RandomDatStore.reset();
		RandomDatTransformer.resetForTesting();
		RandomDatWriteTransformer.resetForTesting();
		storeFile.delete();
		randomDatStoreFile.delete();
		credentialsFile.delete();
		HwidAgent.setCredentialsFileForTesting(null);
		HwidAgent.resetRandomDatForTesting();
	}

	private void launchedForCharacter(String characterId) throws Exception
	{
		java.nio.file.Files.write(credentialsFile.toPath(), ("JX_CHARACTER_ID=" + characterId + "\nJX_SESSION_ID=session\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		HwidAgent.setCredentialsFileForTesting(credentialsFile);
	}

	private static byte[] buildFakePlatformClass()
	{
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "FakePlatform", null, "java/lang/Object", null);

		MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		ctor.visitCode();
		ctor.visitVarInsn(Opcodes.ALOAD, 0);
		ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(1, 1);
		ctor.visitEnd();

		MethodVisitor ac = cw.visitMethod(Opcodes.ACC_PUBLIC, "ac", "(I)Ljava/lang/String;", null, null);
		ac.visitCode();
		ac.visitLdcInsn(MARKER_COMMAND);
		ac.visitLdcInsn(MARKER_OTHER);
		ac.visitInsn(Opcodes.POP);
		ac.visitInsn(Opcodes.ARETURN);
		ac.visitMaxs(2, 2);
		ac.visitEnd();

		MethodVisitor re = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "re", "(LFakePlatform;I)Ljava/lang/String;", null, null);
		re.visitCode();
		re.visitLdcInsn(MARKER_OTHER);
		re.visitInsn(Opcodes.ARETURN);
		re.visitMaxs(1, 2);
		re.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static final class PatchedClassLoader extends ClassLoader
	{
		PatchedClassLoader(byte[] bytes)
		{
			super(PatchedClassLoader.class.getClassLoader());
			defineClass("FakePlatform", bytes, 0, bytes.length);
		}

		Class<?> defined()
		{
			try
			{
				return loadClass("FakePlatform");
			}
			catch (ClassNotFoundException e)
			{
				throw new IllegalStateException(e);
			}
		}
	}

	@Test
	public void patchedMethodsReturnSpoofedUuids() throws Exception
	{
		PlatformInfoTransformer transformer = new PlatformInfoTransformer();
		byte[] patched = transformer.transform(null, "FakePlatform", null, null, buildFakePlatformClass());
		assertNotNull(patched);

		Class<?> fake = new PatchedClassLoader(patched).defined();
		Object instance = fake.getDeclaredConstructor().newInstance();

		Method ac = fake.getMethod("ac", int.class);
		String spoofed = (String) ac.invoke(instance, 1);
		assertNotNull(UUID.fromString(spoofed));
		assertEquals(spoofed, ac.invoke(instance, 2));

		Method re = fake.getMethod("re", fake, int.class);
		assertNotNull(UUID.fromString((String) re.invoke(null, instance, 2)));

		assertEquals(spoofed, HwidAgent.deviceId(3));
	}

	@Test
	public void transformerSkipsUnrelatedClasses() throws Exception
	{
		PlatformInfoTransformer transformer = new PlatformInfoTransformer();
		try (InputStream in = PlatformInfoTransformer.class.getResourceAsStream("DeviceIdStore.class"))
		{
			assertNotNull(in);
			assertNull(transformer.transform(null, "com/microbot/hwid/DeviceIdStore", null, null, in.readAllBytes()));
		}
	}

	@Test
	public void realInjectedClientClassIsPatched() throws Exception
	{
		File jar = InjectedClientJar.locate();
		assertNotNull("injected-client jar missing from test classpath", jar);

		PlatformInfoTransformer transformer = new PlatformInfoTransformer();
		int patchedCount = 0;
		try (ZipFile zip = new ZipFile(jar))
		{
			for (java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();)
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
				byte[] result = transformer.transform(null, zipNameToClassName(entry.getName()), null, null, bytes);
				if (result != null)
				{
					patchedCount++;
					assertTrue("class " + entry.getName() + " still contains fingerprint commands",
						!stillReferencesFingerprintCommands(result));
				}
			}
		}
		assertEquals(1, patchedCount);
	}

	private static String zipNameToClassName(String entryName)
	{
		return entryName.substring(0, entryName.length() - ".class".length());
	}

	private static boolean stillReferencesFingerprintCommands(byte[] classBytes) throws Exception
	{
		org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(classBytes);
		org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
		reader.accept(node, 0);
		for (MethodNode method : node.methods)
		{
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String)
				{
					String value = (String) ((LdcInsnNode) insn).cst;
					if (value.contains("csproduct") || value.contains("/etc/machine-id"))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	@Test
	public void spoofCanBeDisabled()
	{
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		try
		{
			assertEquals(FALLBACK_DEVICE_ID, HwidAgent.deviceId(2));
			assertEquals(VanillaDeviceId.getDeviceId(2), HwidAgent.deviceId(2));
		}
		finally
		{
			System.clearProperty(HwidAgent.TOGGLE_PROPERTY);
		}
	}

	@Test
	public void identifierFallsBackToDefaultWhenClientAbsent()
	{
		assertTrue(HwidAgent.spoofEnabled());
		assertEquals("default", HwidAgent.currentIdentifier());
	}

	@Test
	public void identifierFallsBackToLauncherCredentials() throws Exception
	{
		launchedForCharacter("123456");
		assertEquals("jx/123456", HwidAgent.currentIdentifier());
	}

	@Test
	public void serverPushedRandomDatStaysWithTheAccountThatReceivedIt() throws Exception
	{
		byte[] patched = new RandomDatTransformer().transform(null, "client", null, null, buildFakeRandomDatClass());
		Class<?> stub = new StubLoader("client", patched).defined();

		byte[] pushed = new byte[24];
		java.util.Arrays.fill(pushed, (byte) 7);

		launchedForCharacter("first");
		byte[] injected = (byte[]) stub.getMethod("sendIfNull").invoke(null);
		System.arraycopy(pushed, 0, injected, 0, pushed.length);

		launchedForCharacter("second");
		byte[] second = (byte[]) stub.getMethod("sendIfNull").invoke(null);
		assertArrayEquals(RandomDatStore.getOrCreate("jx/second"), second);
		assertFalse(java.util.Arrays.equals(pushed, second));
		assertArrayEquals(pushed, RandomDatStore.getOrCreate("jx/first"));

		launchedForCharacter("first");
		assertArrayEquals(pushed, (byte[]) stub.getMethod("sendIfNull").invoke(null));
	}

	@Test
	public void allGuardShapesAreHookedAndSpoofed() throws Exception
	{
		byte[] patched = new RandomDatTransformer().transform(null, "client", null, null, buildFakeRandomDatClass());
		assertNotNull(patched);
		assertTrue(RandomDatTransformer.isRandomHookInstalled());
		assertEquals(3, RandomDatTransformer.patchedSiteCount());
		assertEquals("client", RandomDatTransformer.fieldOwner());
		assertEquals("fix", RandomDatTransformer.fieldName());

		Class<?> stub = new StubLoader("client", patched).defined();
		System.clearProperty(HwidAgent.RANDOM_DAT_CALL_PROPERTY);
		byte[] expected = RandomDatStore.getOrCreate(HwidAgent.currentIdentifier());
		byte[] viaAcmp = (byte[]) stub.getMethod("sendAcmp").invoke(null);
		byte[] viaIfNull = (byte[]) stub.getMethod("sendIfNull").invoke(null);
		assertArrayEquals(expected, viaAcmp);
		assertTrue(viaAcmp == viaIfNull);
		assertTrue(viaAcmp == stub.getMethod("sendViaProvider").invoke(null));
		assertTrue(viaAcmp == stub.getDeclaredField("fix").get(null));
		assertNotNull(System.getProperty(HwidAgent.RANDOM_DAT_CALL_PROPERTY));
	}

	@Test
	public void randomDatFileIsHiddenWhileSpoofing() throws Exception
	{
		byte[] patched = new RandomDatWriteTransformer().transform(null, "StubWriter", null, null, buildFakeRandomDatWriter());
		assertNotNull(patched);
		assertEquals(java.util.List.of("StubWriter.persist()V"), RandomDatWriteTransformer.patchedMethods());

		Class<?> writer = new StubLoader("StubWriter", patched).defined();
		writer.getDeclaredField("handle").set(null, new StubFile());
		StubFile.seeks = 0;
		writer.getMethod("persist").invoke(null);
		assertEquals(0, StubFile.seeks);

		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		try
		{
			writer.getMethod("persist").invoke(null);
			assertEquals(1, StubFile.seeks);
		}
		finally
		{
			System.clearProperty(HwidAgent.TOGGLE_PROPERTY);
		}
		assertNull(new RandomDatWriteTransformer().transform(null, "StubWriter", null, null, patched));
	}

	public static final class StubFile
	{
		static int seeks;

		public void seek(long position)
		{
			seeks++;
		}
	}

	private static byte[] buildFakeRandomDatWriter()
	{
		String handleType = "L" + StubFile.class.getName().replace('.', '/') + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "StubWriter", null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle", handleType, null, null).visitEnd();
		MethodVisitor persist = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "persist", "()V", null, null);
		persist.visitCode();
		persist.visitInsn(Opcodes.ACONST_NULL);
		persist.visitFieldInsn(Opcodes.GETSTATIC, "StubWriter", "handle", handleType);
		org.objectweb.asm.Label done = new org.objectweb.asm.Label();
		persist.visitJumpInsn(Opcodes.IF_ACMPEQ, done);
		persist.visitFieldInsn(Opcodes.GETSTATIC, "StubWriter", "handle", handleType);
		persist.visitInsn(Opcodes.LCONST_0);
		persist.visitMethodInsn(Opcodes.INVOKEVIRTUAL, handleType.substring(1, handleType.length() - 1), "seek", "(J)V", false);
		persist.visitIntInsn(Opcodes.BIPUSH, 24);
		persist.visitInsn(Opcodes.POP);
		persist.visitLabel(done);
		persist.visitInsn(Opcodes.RETURN);
		persist.visitMaxs(0, 0);
		persist.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	public void nonClientClassIsIgnored()
	{
		assertNull(new RandomDatTransformer().transform(null, "zz", null, null, buildFakeRandomDatClass("zz")));
		assertNull(RandomDatTransformer.fieldOwner());
	}

	@Test
	public void serverPushedRandomDatIsPassedThrough() throws Exception
	{
		byte[] patched = new RandomDatTransformer().transform(null, "client", null, null, buildFakeRandomDatClass());
		Class<?> stub = new StubLoader("client", patched).defined();
		byte[] pushed = new byte[24];
		java.util.Arrays.fill(pushed, (byte) 7);
		stub.getDeclaredField("fix").set(null, pushed);
		assertTrue(pushed == stub.getMethod("sendAcmp").invoke(null));

		stub.getDeclaredField("fix").set(null, null);
		byte[] injected = (byte[]) stub.getMethod("sendAcmp").invoke(null);
		java.util.Arrays.fill(injected, (byte) 9);
		assertTrue(injected == stub.getMethod("sendIfNull").invoke(null));
		assertEquals(9, injected[0]);
	}

	@Test
	public void spoofDisabledFallsBackToVanillaRead() throws Exception
	{
		byte[] patched = new RandomDatTransformer().transform(null, "client", null, null, buildFakeRandomDatClass());
		Class<?> stub = new StubLoader("client", patched).defined();
		byte[] injected = (byte[]) stub.getMethod("sendAcmp").invoke(null);
		assertNotNull(injected);

		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		try
		{
			byte[] vanilla = (byte[]) stub.getMethod("sendIfNull").invoke(null);
			assertArrayEquals(new byte[24], vanilla);
			assertNull(stub.getDeclaredField("fix").get(null));
		}
		finally
		{
			System.clearProperty(HwidAgent.TOGGLE_PROPERTY);
		}
	}

	@Test
	public void copyHelperIsNotHooked()
	{
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "client", null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fix", "[B", null, null).visitEnd();
		MethodVisitor copy = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "copy", "()V", null, null);
		copy.visitCode();
		copy.visitFieldInsn(Opcodes.GETSTATIC, "client", "fix", "[B");
		org.objectweb.asm.Label done = new org.objectweb.asm.Label();
		copy.visitJumpInsn(Opcodes.IFNONNULL, done);
		copy.visitIntInsn(Opcodes.BIPUSH, 24);
		copy.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
		copy.visitFieldInsn(Opcodes.PUTSTATIC, "client", "fix", "[B");
		copy.visitLabel(done);
		copy.visitInsn(Opcodes.RETURN);
		copy.visitMaxs(0, 0);
		copy.visitEnd();
		cw.visitEnd();

		assertNull(new RandomDatTransformer().transform(null, "client", null, null, cw.toByteArray()));
		assertNull(RandomDatTransformer.fieldOwner());
	}

	@Test
	public void randomDatTransformIsIdempotent()
	{
		byte[] patched = new RandomDatTransformer().transform(null, "client", null, null, buildFakeRandomDatClass());
		assertNotNull(patched);
		assertNull(new RandomDatTransformer().transform(null, "client", null, null, patched));
	}

	@Test
	public void everyKnownClientRevisionIsHooked() throws Exception
	{
		java.util.List<File> jars = new java.util.ArrayList<>();
		jars.add(InjectedClientJar.locate());
		String historical = System.getProperty("hwid.historicalClients", "");
		for (String path : historical.split(File.pathSeparator))
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
			RandomDatTransformer.resetForTesting();
			RandomDatWriteTransformer.resetForTesting();
			RandomDatTransformer randomDat = new RandomDatTransformer();
			RandomDatWriteTransformer randomDatWrite = new RandomDatWriteTransformer();
			PlatformInfoTransformer platform = new PlatformInfoTransformer();
			java.util.Map<String, byte[]> patched = new java.util.HashMap<>();
			int deviceClasses = 0;
			try (ZipFile zip = new ZipFile(jar))
			{
				java.util.List<ZipEntry> ordered = new java.util.ArrayList<>();
				ordered.add(zip.getEntry("client.class"));
				for (ZipEntry entry : java.util.Collections.list(zip.entries()))
				{
					if (!entry.isDirectory() && entry.getName().endsWith(".class") && !entry.getName().equals("client.class"))
					{
						ordered.add(entry);
					}
				}
				for (ZipEntry entry : ordered)
				{
					byte[] bytes;
					try (InputStream in = zip.getInputStream(entry))
					{
						bytes = in.readAllBytes();
					}
					String className = zipNameToClassName(entry.getName());
					byte[] result = randomDat.transform(null, className, null, null, bytes);
					patched.put(className, result != null ? result : bytes);
					randomDatWrite.transform(null, className, null, null, bytes);
					if (platform.transform(null, className, null, null, bytes) != null)
					{
						deviceClasses++;
					}
				}
			}

			assertEquals(version + ": device id class", 1, deviceClasses);
			assertEquals(version + ": random.dat field owner", "client", RandomDatTransformer.fieldOwner());
			assertTrue(version + ": no random.dat send site hooked", RandomDatTransformer.patchedSiteCount() > 0);
			assertTrue(version + ": no random.dat file write guarded", !RandomDatWriteTransformer.patchedMethods().isEmpty());
			java.util.List<String> unhooked = unhookedReaders(patched, RandomDatTransformer.fieldOwner(), RandomDatTransformer.fieldName());
			assertTrue(version + ": random.dat readers left unhooked " + unhooked, unhooked.isEmpty());
		}
	}

	private static java.util.List<String> unhookedReaders(java.util.Map<String, byte[]> classes, String owner, String name)
	{
		java.util.List<String> unhooked = new java.util.ArrayList<>();
		for (java.util.Map.Entry<String, byte[]> entry : classes.entrySet())
		{
			org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
			new org.objectweb.asm.ClassReader(entry.getValue()).accept(node, org.objectweb.asm.ClassReader.SKIP_FRAMES);
			for (MethodNode method : node.methods)
			{
				boolean reads = false;
				boolean writes = false;
				boolean hooked = false;
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
				{
					if (insn instanceof org.objectweb.asm.tree.FieldInsnNode
						&& owner.equals(((org.objectweb.asm.tree.FieldInsnNode) insn).owner)
						&& name.equals(((org.objectweb.asm.tree.FieldInsnNode) insn).name))
					{
						reads |= insn.getOpcode() == Opcodes.GETSTATIC;
						writes |= insn.getOpcode() == Opcodes.PUTSTATIC && !isHookStore(insn);
					}
					hooked |= insn instanceof org.objectweb.asm.tree.MethodInsnNode
						&& "com/microbot/hwid/HwidAgent".equals(((org.objectweb.asm.tree.MethodInsnNode) insn).owner);
				}
				if (reads && !writes && !hooked)
				{
					unhooked.add(node.name + "." + method.name + method.desc);
				}
			}
		}
		return unhooked;
	}

	private static boolean isHookStore(AbstractInsnNode putstatic)
	{
		AbstractInsnNode previous = putstatic.getPrevious();
		return previous instanceof org.objectweb.asm.tree.MethodInsnNode
			&& "com/microbot/hwid/HwidAgent".equals(((org.objectweb.asm.tree.MethodInsnNode) previous).owner);
	}

	private static final class StubLoader extends ClassLoader
	{
		private final String name;

		StubLoader(String name, byte[] bytes)
		{
			super(StubLoader.class.getClassLoader());
			this.name = name;
			defineClass(name, bytes, 0, bytes.length);
		}

		Class<?> defined() throws ClassNotFoundException
		{
			return loadClass(name);
		}
	}

	private static byte[] buildFakeRandomDatClass()
	{
		return buildFakeRandomDatClass("client");
	}

	private static byte[] buildFakeRandomDatClass(String owner)
	{
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fix", "[B", null, null).visitEnd();

		MethodVisitor acmp = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sendAcmp", "()[B", null, null);
		acmp.visitCode();
		acmp.visitInsn(Opcodes.ACONST_NULL);
		acmp.visitFieldInsn(Opcodes.GETSTATIC, owner, "fix", "[B");
		org.objectweb.asm.Label acmpFresh = new org.objectweb.asm.Label();
		acmp.visitJumpInsn(Opcodes.IF_ACMPEQ, acmpFresh);
		acmp.visitFieldInsn(Opcodes.GETSTATIC, owner, "fix", "[B");
		acmp.visitInsn(Opcodes.ARETURN);
		acmp.visitLabel(acmpFresh);
		acmp.visitIntInsn(Opcodes.BIPUSH, 24);
		acmp.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
		acmp.visitVarInsn(Opcodes.ASTORE, 0);
		acmp.visitVarInsn(Opcodes.ALOAD, 0);
		acmp.visitInsn(Opcodes.ARETURN);
		acmp.visitMaxs(0, 0);
		acmp.visitEnd();

		MethodVisitor ifNull = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sendIfNull", "()[B", null, null);
		ifNull.visitCode();
		ifNull.visitFieldInsn(Opcodes.GETSTATIC, owner, "fix", "[B");
		org.objectweb.asm.Label ifNullFresh = new org.objectweb.asm.Label();
		ifNull.visitJumpInsn(Opcodes.IFNULL, ifNullFresh);
		ifNull.visitFieldInsn(Opcodes.GETSTATIC, owner, "fix", "[B");
		ifNull.visitInsn(Opcodes.ARETURN);
		ifNull.visitLabel(ifNullFresh);
		ifNull.visitIntInsn(Opcodes.BIPUSH, 24);
		ifNull.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
		ifNull.visitVarInsn(Opcodes.ASTORE, 0);
		ifNull.visitVarInsn(Opcodes.ALOAD, 0);
		ifNull.visitInsn(Opcodes.ARETURN);
		ifNull.visitMaxs(0, 0);
		ifNull.visitEnd();

		MethodVisitor provider = cw.visitMethod(Opcodes.ACC_STATIC, "provider", "(B)[B", null, null);
		provider.visitCode();
		provider.visitIntInsn(Opcodes.BIPUSH, 24);
		provider.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
		provider.visitInsn(Opcodes.ARETURN);
		provider.visitMaxs(0, 0);
		provider.visitEnd();

		MethodVisitor viaProvider = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sendViaProvider", "()[B", null, null);
		viaProvider.visitCode();
		viaProvider.visitFieldInsn(Opcodes.GETSTATIC, owner, "fix", "[B");
		org.objectweb.asm.Label providerFresh = new org.objectweb.asm.Label();
		viaProvider.visitJumpInsn(Opcodes.IFNULL, providerFresh);
		viaProvider.visitFieldInsn(Opcodes.GETSTATIC, owner, "fix", "[B");
		viaProvider.visitInsn(Opcodes.ARETURN);
		viaProvider.visitLabel(providerFresh);
		viaProvider.visitInsn(Opcodes.ICONST_0);
		viaProvider.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "provider", "(B)[B", false);
		viaProvider.visitInsn(Opcodes.ARETURN);
		viaProvider.visitMaxs(0, 0);
		viaProvider.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	public void transformedClientClassesPassJvmVerification() throws Exception
	{
		java.util.List<File> jars = new java.util.ArrayList<>();
		jars.add(InjectedClientJar.locate());
		String historical = System.getProperty("hwid.historicalClients", "");
		for (String path : historical.split(File.pathSeparator))
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
			RandomDatTransformer.resetForTesting();
			RandomDatWriteTransformer.resetForTesting();
			ClassFileTransformer[] transformers = {
				new PlatformInfoTransformer(),
				new RandomDatTransformer(),
				new RandomDatWriteTransformer()
			};

			Map<String, byte[]> classes = new LinkedHashMap<>();
			try (ZipFile zip = new ZipFile(jar))
			{
				for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();)
				{
					ZipEntry entry = entries.nextElement();
					if (entry.isDirectory() || !entry.getName().endsWith(".class"))
					{
						continue;
					}
					try (InputStream in = zip.getInputStream(entry))
					{
						classes.put(zipNameToClassName(entry.getName()), in.readAllBytes());
					}
				}
			}

			VerifyingClassLoader loader = new VerifyingClassLoader(classes);
			Set<String> changed = new LinkedHashSet<>();
			for (String name : new LinkedHashSet<>(classes.keySet()))
			{
				byte[] bytes = classes.get(name);
				boolean patched = false;
				for (ClassFileTransformer transformer : transformers)
				{
					byte[] result = transformer.transform(loader, name, null, null, bytes);
					if (result != null)
					{
						bytes = result;
						patched = true;
					}
				}
				if (patched)
				{
					classes.put(name, bytes);
					changed.add(name);
				}
			}

			assertFalse(version + ": no classes patched", changed.isEmpty());

			for (String name : changed)
			{
				VerifyError error = jvmVerify(loader, name.replace('/', '.'));
				if (error != null)
				{
					throw new AssertionError(version + ": " + name + " failed JVM verification: " + error.getMessage(), error);
				}
			}
		}
	}

	private static VerifyError jvmVerify(ClassLoader loader, String binaryName) throws InterruptedException
	{
		VerifyError[] result = new VerifyError[1];
		Thread thread = new Thread(() ->
		{
			try
			{
				Class.forName(binaryName, true, loader);
			}
			catch (VerifyError e)
			{
				result[0] = e;
			}
			catch (Throwable ignored)
			{
			}
		}, "hwid-verify-" + binaryName);
		thread.setDaemon(true);
		thread.start();
		thread.join(30_000);
		return result[0];
	}

	private static final class VerifyingClassLoader extends ClassLoader
	{
		private final Map<String, byte[]> classes;

		VerifyingClassLoader(Map<String, byte[]> classes)
		{
			super(VerifyingClassLoader.class.getClassLoader());
			this.classes = classes;
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException
		{
			byte[] bytes = classes.get(name.replace('.', '/'));
			if (bytes == null)
			{
				throw new ClassNotFoundException(name);
			}
			return defineClass(name, bytes, 0, bytes.length);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
		{
			synchronized (getClassLoadingLock(name))
			{
				Class<?> loaded = findLoadedClass(name);
				if (loaded == null)
				{
					loaded = classes.containsKey(name.replace('.', '/')) ? findClass(name) : super.loadClass(name, resolve);
				}
				if (resolve)
				{
					resolveClass(loaded);
				}
				return loaded;
			}
		}

		@Override
		public InputStream getResourceAsStream(String name)
		{
			if (name.endsWith(".class"))
			{
				byte[] bytes = classes.get(name.substring(0, name.length() - ".class".length()));
				if (bytes != null)
				{
					return new java.io.ByteArrayInputStream(bytes);
				}
			}
			return super.getResourceAsStream(name);
		}
	}
}
