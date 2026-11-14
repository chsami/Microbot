package com.microbot.hwid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class PlatformScanTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Before
	@After
	public void reset()
	{
		HwidAgent.resetPlatformHooksForTest();
		PlatformScan.resetForTesting();
	}

	@Test
	public void everyHookIsKnownBeforeAnyGameCodeRuns() throws Exception
	{
		Map<String, byte[]> classes = new LinkedHashMap<>();
		classes.put("client", plainClass("client"));
		classes.put("ab", signerClass("ab", true));
		classes.put("cd", plainClass("cd"));
		try (TransformingLoader loader = new TransformingLoader(jar(classes)))
		{
			// The process-name hook is not part of this jar; it is installed by its own class in the game.
			HwidAgent.markProcessHookInstalled();
			loader.loadClass("client");
			assertEquals("only the class the game asked for is loaded so far", List.of("client"), loader.defined);
			assertNull(System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));

			PlatformScan.run(loader, "client");

			assertEquals(List.of("client", "ab"), loader.defined);
			assertEquals("true", System.getProperty(HwidAgent.PLATFORM_SCANNED_PROPERTY));
			assertEquals("true", System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
			assertNull(System.getProperty(HwidAgent.PLATFORM_MISSED_PROPERTY));
		}
	}

	@Test
	public void aSignerCallThatCannotBeHiddenIsKnownBeforeTheFirstLogin() throws Exception
	{
		Map<String, byte[]> classes = new LinkedHashMap<>();
		classes.put("client", plainClass("client"));
		classes.put("ab", signerClass("ab", true));
		classes.put("ef", signerClass("ef", false));
		try (TransformingLoader loader = new TransformingLoader(jar(classes)))
		{
			HwidAgent.markProcessHookInstalled();
			loader.loadClass("client");
			assertNull(System.getProperty(HwidAgent.PLATFORM_MISSED_PROPERTY));

			PlatformScan.run(loader, "client");

			assertEquals("true", System.getProperty(HwidAgent.PLATFORM_SCANNED_PROPERTY));
			assertEquals("true", System.getProperty(HwidAgent.PLATFORM_MISSED_PROPERTY));
			assertNull(System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
		}
	}

	@Test
	public void scanIsNotReportedWhenTheGameClassesCannotBeFound() throws Exception
	{
		try (URLClassLoader empty = new URLClassLoader(new URL[0], null))
		{
			PlatformScan.run(empty, "client");
		}
		assertNull(System.getProperty(HwidAgent.PLATFORM_SCANNED_PROPERTY));
	}

	@Test
	public void candidatesAreFoundInAClassDirectoryToo() throws Exception
	{
		File directory = folder.newFolder("classes");
		Files.write(new File(directory, "client.class").toPath(), plainClass("client"));
		Files.write(new File(directory, "ab.class").toPath(), signerClass("ab", true));
		Files.write(new File(directory, "cd.class").toPath(), plainClass("cd"));
		URL resource = new File(directory, "client.class").toURI().toURL();
		assertEquals(List.of("ab"), PlatformScan.candidates(resource, "client"));
	}

	@Test
	public void realInjectedClientCandidatesAreExactlyTheClassesTheTransformerPatches() throws Exception
	{
		File jar = InjectedClientJar.locate();
		assertNotNull("injected-client jar missing from test classpath", jar);
		URL resource = new URL("jar:" + jar.toURI().toURL() + "!/client.class");
		List<String> candidates = PlatformScan.candidates(resource, "client");

		PlatformMetadataTransformer transformer = new PlatformMetadataTransformer();
		TreeSet<String> patched = new TreeSet<>();
		int classes = 0;
		try (ZipFile zip = new ZipFile(jar))
		{
			for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();)
			{
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || !entry.getName().endsWith(".class"))
				{
					continue;
				}
				classes++;
				String name = entry.getName().substring(0, entry.getName().length() - ".class".length());
				try (InputStream in = zip.getInputStream(entry))
				{
					if (transformer.transform(null, name, null, null, in.readAllBytes()) != null)
					{
						patched.add(name);
					}
				}
			}
		}
		assertFalse(patched.isEmpty());
		assertTrue("scan would miss " + patched, candidates.containsAll(patched));
		assertTrue("scan should load a handful of classes, not " + candidates.size() + " of " + classes, candidates.size() <= 10);
		assertEquals("true", System.getProperty(HwidAgent.PLATFORM_HOOK_PROPERTY));
	}

	private File jar(Map<String, byte[]> classes) throws IOException
	{
		File jar = folder.newFile("game.jar");
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath())))
		{
			for (Map.Entry<String, byte[]> entry : classes.entrySet())
			{
				out.putNextEntry(new JarEntry(entry.getKey() + ".class"));
				out.write(entry.getValue());
				out.closeEntry();
			}
			// A class outside the default package is never a game class.
			out.putNextEntry(new JarEntry("net/example/Other.class"));
			out.write(signerClass("net/example/Other", false));
			out.closeEntry();
		}
		return jar;
	}

	private static byte[] plainClass(String name)
	{
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		writer.visitEnd();
		return writer.toByteArray();
	}

	/** {@code Arrays.hashCode(callbacks.getClass().getSigners())}, read with GETFIELD when known, GETSTATIC when not. */
	private static byte[] signerClass(String name, boolean known)
	{
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		writer.visitField(Opcodes.ACC_PUBLIC, "callbacks", "Ljava/lang/Object;", null, null).visitEnd();
		writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "staticCallbacks", "Ljava/lang/Object;", null, null).visitEnd();
		writer.visitField(Opcodes.ACC_PUBLIC, "typed", "Lnet/runelite/api/hooks/Callbacks;", null, null).visitEnd();
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "hash", "()I", null, null);
		method.visitCode();
		if (known)
		{
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitFieldInsn(Opcodes.GETFIELD, name, "typed", "Lnet/runelite/api/hooks/Callbacks;");
		}
		else
		{
			method.visitFieldInsn(Opcodes.GETSTATIC, name, "staticCallbacks", "Ljava/lang/Object;");
		}
		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getSigners", "()[Ljava/lang/Object;", false);
		method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "hashCode", "([Ljava/lang/Object;)I", false);
		method.visitInsn(Opcodes.IRETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
		writer.visitEnd();
		return writer.toByteArray();
	}

	/** Stands in for the JVM with the agent attached: every class it defines goes through the transformer first. */
	private static final class TransformingLoader extends URLClassLoader
	{
		private final PlatformMetadataTransformer transformer = new PlatformMetadataTransformer();
		final List<String> defined = new ArrayList<>();

		TransformingLoader(File jar) throws IOException
		{
			super(new URL[]{jar.toURI().toURL()}, ClassLoader.getPlatformClassLoader());
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException
		{
			URL resource = findResource(name.replace('.', '/') + ".class");
			if (resource == null)
			{
				throw new ClassNotFoundException(name);
			}
			try (InputStream in = resource.openStream())
			{
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				in.transferTo(out);
				byte[] bytes = out.toByteArray();
				byte[] patched = transformer.transform(this, name.replace('.', '/'), null, null, bytes);
				defined.add(name);
				byte[] result = patched != null ? patched : bytes;
				return defineClass(name, result, 0, result.length);
			}
			catch (IOException e)
			{
				throw new ClassNotFoundException(name, e);
			}
		}
	}
}
