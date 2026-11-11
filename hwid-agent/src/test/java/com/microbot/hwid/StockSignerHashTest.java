package com.microbot.hwid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class StockSignerHashTest
{
	private static final String HOOKS = "net/runelite/client/callback/Hooks";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private File store;
	private File repository;
	private String previousSpoof;

	@Before
	public void setUp() throws Exception
	{
		store = new File(folder.getRoot(), "stock-signer.properties");
		repository = folder.newFolder("repository2");
		previousSpoof = System.getProperty(HwidAgent.TOGGLE_PROPERTY);
		StockSignerHash.resetForTesting();
	}

	@After
	public void tearDown()
	{
		if (previousSpoof == null)
		{
			System.clearProperty(HwidAgent.TOGGLE_PROPERTY);
		}
		else
		{
			System.setProperty(HwidAgent.TOGGLE_PROPERTY, previousSpoof);
		}
		StockSignerHash.resetForTesting();
	}

	@Test
	public void computedHashIsWhatTheLoadedClassReports() throws Exception
	{
		File jar = signedJar("client-9.9.1.jar", "first");
		Integer computed = StockSignerHash.signerHash(jar);
		assertNotNull(computed);
		try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, null))
		{
			Object[] signers = Class.forName(HOOKS.replace('/', '.'), false, loader).getSigners();
			assertNotNull(signers);
			assertEquals(Arrays.hashCode(signers), computed.intValue());
		}
	}

	@Test
	public void unsignedJarGivesNoHash() throws Exception
	{
		assertNull(StockSignerHash.signerHash(unsignedJar("client-9.9.1.jar")));
	}

	@Test
	public void bundledValueIsUsedBeforeAnyLookup()
	{
		FakeRemote remote = new FakeRemote(null, null);
		assertEquals(StockSignerHash.SOURCE_BUNDLED, StockSignerHash.refresh(remote, store, repository));
		assertEquals(StockSignerHash.BUNDLED, StockSignerHash.get());
		assertEquals(StockSignerHash.SOURCE_BUNDLED, System.getProperty(StockSignerHash.SOURCE_PROPERTY));
	}

	@Test
	public void newStockReleaseIsDownloadedOnceThenConfirmedFromTheStore() throws Exception
	{
		File jar = signedJar("client-9.9.1.jar", "first");
		int expected = StockSignerHash.signerHash(jar);
		assertNotEquals(StockSignerHash.BUNDLED, expected);
		FakeRemote remote = new FakeRemote(bootstrap(jar), jar);

		assertEquals(StockSignerHash.SOURCE_LIVE, StockSignerHash.refresh(remote, store, repository));
		assertEquals(expected, StockSignerHash.get());
		assertEquals(1, remote.downloads);
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "true");
		assertEquals(expected, HwidAgent.signerHash(null));

		StockSignerHash.resetForTesting();
		assertEquals(StockSignerHash.SOURCE_LIVE, StockSignerHash.refresh(remote, store, repository));
		assertEquals(expected, StockSignerHash.get());
		assertEquals("same release must not be downloaded again", 1, remote.downloads);
		assertEquals(0, leftoverJars());
	}

	@Test
	public void stockLaunchersOwnCopyIsUsedInsteadOfADownload() throws Exception
	{
		File jar = signedJar("client-9.9.1.jar", "first");
		Files.copy(jar.toPath(), new File(repository, jar.getName()).toPath());
		FakeRemote remote = new FakeRemote(bootstrap(jar), jar);

		assertEquals(StockSignerHash.SOURCE_LIVE, StockSignerHash.refresh(remote, store, repository));
		assertEquals(StockSignerHash.signerHash(jar).intValue(), StockSignerHash.get());
		assertEquals(0, remote.downloads);
	}

	@Test
	public void reSignedReleaseReplacesTheStoredHash() throws Exception
	{
		File first = signedJar("client-9.9.1.jar", "first");
		StockSignerHash.refresh(new FakeRemote(bootstrap(first), first), store, repository);
		int before = StockSignerHash.get();

		File second = signedJar("client-9.9.2.jar", "second");
		assertEquals(StockSignerHash.SOURCE_LIVE,
			StockSignerHash.refresh(new FakeRemote(bootstrap(second), second), store, repository));
		assertNotEquals(before, StockSignerHash.get());
		assertEquals(StockSignerHash.signerHash(second).intValue(), StockSignerHash.get());
	}

	@Test
	public void lastConfirmedHashIsKeptWhenRuneLiteCannotBeReached() throws Exception
	{
		File jar = signedJar("client-9.9.1.jar", "first");
		StockSignerHash.refresh(new FakeRemote(bootstrap(jar), jar), store, repository);
		int confirmed = StockSignerHash.get();

		StockSignerHash.resetForTesting();
		assertEquals(StockSignerHash.SOURCE_CACHED, StockSignerHash.refresh(new FakeRemote(null, null), store, repository));
		assertEquals(confirmed, StockSignerHash.get());
	}

	@Test
	public void tamperedOrUnsignedDownloadsNeverChangeTheHash() throws Exception
	{
		File jar = signedJar("client-9.9.1.jar", "first");
		File other = signedJar("client-9.9.2.jar", "second");
		// Checksum in the bootstrap is for a different file than the one served.
		assertEquals(StockSignerHash.SOURCE_BUNDLED,
			StockSignerHash.refresh(new FakeRemote(bootstrap(jar), other), store, repository));
		assertEquals(StockSignerHash.BUNDLED, StockSignerHash.get());

		File unsigned = unsignedJar("client-9.9.3.jar");
		assertEquals(StockSignerHash.SOURCE_BUNDLED,
			StockSignerHash.refresh(new FakeRemote(bootstrap(unsigned), unsigned), store, repository));
		assertEquals(StockSignerHash.BUNDLED, StockSignerHash.get());

		String elsewhere = bootstrap(jar).replace("https://repo.runelite.net/", "https://example.com/");
		FakeRemote remote = new FakeRemote(elsewhere, jar);
		assertEquals(StockSignerHash.SOURCE_BUNDLED, StockSignerHash.refresh(remote, store, repository));
		assertEquals(0, remote.downloads);
		assertFalse(store.exists());
		assertEquals(0, leftoverJars());
	}

	@Test
	public void clientJarIsPickedFromTheBootstrapNotItsDiffs()
	{
		String hash = "a".repeat(64);
		String diffHash = "b".repeat(64);
		String json = "{\"artifacts\":[{\"hash\":\"" + "c".repeat(64) + "\",\"name\":\"runelite-api-1.13.6.jar\",\"path\":\"https://repo.runelite.net/api.jar\",\"size\":5},"
			+ "{\"diffs\":[{\"from\":\"client-1.13.5.jar\",\"hash\":\"" + diffHash + "\",\"name\":\"client-1.13.6.jar\",\"path\":\"https://repo.runelite.net/diffs/x.jar\",\"size\":17056}],"
			+ "\"hash\":\"" + hash + "\",\"name\":\"client-1.13.6.jar\",\"path\":\"https://repo.runelite.net/net/runelite/client/1.13.6/client-1.13.6.jar\",\"size\":5336403}],\"version\":\"1.13.6\"}";
		StockSignerHash.Artifact client = StockSignerHash.clientArtifact(json);
		assertNotNull(client);
		assertEquals("client-1.13.6.jar", client.name);
		assertEquals(hash, client.sha256);
		assertEquals(5336403L, client.size);
		assertTrue(client.path.endsWith("/client-1.13.6.jar"));
		assertNull(StockSignerHash.clientArtifact("{\"artifacts\":[]}"));
	}

	private int leftoverJars()
	{
		File[] files = folder.getRoot().listFiles((dir, name) -> name.startsWith("stock-client"));
		return files == null ? 0 : files.length;
	}

	private static String bootstrap(File jar) throws Exception
	{
		return "{\"artifacts\":[{\"hash\":\"" + sha256(jar) + "\",\"name\":\"" + jar.getName()
			+ "\",\"path\":\"https://repo.runelite.net/net/runelite/client/" + jar.getName() + "\",\"size\":" + jar.length() + "}]}";
	}

	private File unsignedJar(String name) throws Exception
	{
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, HOOKS, null, "java/lang/Object", null);
		writer.visitEnd();
		File jar = new File(folder.newFolder(), name);
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath())))
		{
			out.putNextEntry(new JarEntry(HOOKS + ".class"));
			out.write(writer.toByteArray());
			out.closeEntry();
		}
		return jar;
	}

	/** Signs with a fresh self-signed key per alias, so two aliases give two different certificates. */
	private File signedJar(String name, String alias) throws Exception
	{
		File bin = new File(System.getProperty("java.home"), "bin");
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
		File keytool = new File(bin, windows ? "keytool.exe" : "keytool");
		File jarsigner = new File(bin, windows ? "jarsigner.exe" : "jarsigner");
		assumeTrue("needs the JDK's keytool and jarsigner", keytool.isFile() && jarsigner.isFile());

		File jar = unsignedJar(name);
		File keystore = new File(jar.getParentFile(), alias + ".p12");
		run(keytool.getPath(), "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "30",
			"-dname", "CN=" + alias, "-keystore", keystore.getPath(), "-storetype", "PKCS12",
			"-storepass", "changeit", "-keypass", "changeit");
		run(jarsigner.getPath(), "-keystore", keystore.getPath(), "-storepass", "changeit", jar.getPath(), alias);
		return jar;
	}

	private static void run(String... command) throws Exception
	{
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue("timed out: " + command[0], process.waitFor(60, TimeUnit.SECONDS));
		assertEquals(output, 0, process.exitValue());
	}

	private static String sha256(File file) throws Exception
	{
		StringBuilder hex = new StringBuilder();
		for (byte b : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())))
		{
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	private static final class FakeRemote implements StockSignerHash.Remote
	{
		private final String bootstrap;
		private final File jar;
		int downloads;

		FakeRemote(String bootstrap, File jar)
		{
			this.bootstrap = bootstrap;
			this.jar = jar;
		}

		@Override
		public byte[] bootstrap() throws IOException
		{
			if (bootstrap == null)
			{
				throw new IOException("unreachable");
			}
			return bootstrap.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public void download(String url, File target, long maxBytes) throws IOException
		{
			downloads++;
			try (OutputStream out = Files.newOutputStream(target.toPath()))
			{
				Files.copy(jar.toPath(), out);
			}
		}
	}
}
