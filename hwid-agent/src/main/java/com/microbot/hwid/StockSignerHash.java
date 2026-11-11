package com.microbot.hwid;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The signer hash a stock RuneLite client sends, taken from the client jar RuneLite currently ships
 * rather than from a constant that goes stale when RuneLite signs with a new certificate.
 *
 * <p>On start a background thread reads RuneLite's public bootstrap, the same file the stock launcher
 * reads, and compares the listed client jar with the one the stored hash was computed from. Only when
 * RuneLite has released a new client is the jar read again: from the stock launcher's own copy when it
 * matches the bootstrap checksum, otherwise downloaded once. The hash is computed from the verified
 * signers of the callbacks class, exactly as {@code Class.getSigners()} reports them.
 *
 * <p>The value in use is always a stock one, never the unsigned build's 0: the live value, else the
 * last one confirmed on this machine, else {@link #BUNDLED}.
 */
final class StockSignerHash
{
	private static final Logger log = Logger.getLogger(StockSignerHash.class.getName());

	/**
	 * {@code Arrays.hashCode(Hooks.class.getSigners())} of stock client-1.13.6.jar, checked 2026-11-11:
	 * one self-signed certificate, CN=runelite, valid 2017-04-17 to 2027-04-15. Used only until the first
	 * live lookup on a machine succeeds. To recompute, load {@code net.runelite.client.callback.Hooks}
	 * from a stock signed client jar and take {@code Arrays.hashCode(getSigners())}.
	 */
	static final int BUNDLED = -991467620;

	/** {@code live}, {@code cached} or {@code bundled}: where the hash in use came from. */
	static final String SOURCE_PROPERTY = "microbot.hwid.signer.source";
	/** Set to {@code false} to skip the lookup and use the cached or bundled value. */
	static final String LOOKUP_PROPERTY = "microbot.hwid.signer.lookup";
	static final String STORE_PROPERTY = "microbot.hwid.signer.store";

	static final String SOURCE_LIVE = "live";
	static final String SOURCE_CACHED = "cached";
	static final String SOURCE_BUNDLED = "bundled";

	private static final String BOOTSTRAP_URL = "https://static.runelite.net/bootstrap.json";
	private static final String CALLBACKS_ENTRY = "net/runelite/client/callback/Hooks.class";
	private static final String STORE_ARTIFACT = "artifact.sha256";
	private static final String STORE_HASH = "signer.hash";
	private static final long MAX_JAR_BYTES = 64L * 1024 * 1024;
	private static final int MAX_BOOTSTRAP_BYTES = 4 * 1024 * 1024;

	private static final Pattern DIFFS = Pattern.compile("\"diffs\"\\s*:\\s*\\[[^\\]]*\\]");
	private static final Pattern FLAT_OBJECT = Pattern.compile("\\{[^{}]*\\}");
	private static final Pattern CLIENT_JAR = Pattern.compile("client-[0-9][0-9A-Za-z.\\-]*\\.jar");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private static volatile int current = BUNDLED;

	private StockSignerHash()
	{
	}

	/** Where the bootstrap and the client jar come from; replaced in tests. */
	interface Remote
	{
		byte[] bootstrap() throws IOException;

		void download(String url, File target, long maxBytes) throws IOException;
	}

	static int get()
	{
		return current;
	}

	/** Uses the stored value at once, then refreshes it off the calling thread. */
	static void start()
	{
		File store = storeFile();
		File repository = new File(runeliteHome(), "repository2");
		loadCached(store);
		if ("false".equalsIgnoreCase(System.getProperty(LOOKUP_PROPERTY, "true").trim()))
		{
			return;
		}
		Thread thread = new Thread(() -> refresh(new HttpRemote(), store, repository), "hwid-stock-signer");
		thread.setDaemon(true);
		thread.start();
	}

	/** Returns the source of the value in use afterwards. Never throws. */
	static String refresh(Remote remote, File store, File stockRepository)
	{
		String source = loadCached(store);
		File downloaded = null;
		try
		{
			Artifact client = clientArtifact(new String(remote.bootstrap(), StandardCharsets.UTF_8));
			if (client == null)
			{
				throw new IOException("no client jar in bootstrap");
			}
			Properties stored = readStore(store);
			if (client.sha256.equals(stored.getProperty(STORE_ARTIFACT)) && parse(stored.getProperty(STORE_HASH)) != null)
			{
				return use(parse(stored.getProperty(STORE_HASH)), SOURCE_LIVE);
			}

			File jar = new File(stockRepository, client.name);
			if (!jar.isFile() || !client.sha256.equals(sha256(jar)))
			{
				URL url = new URL(client.path);
				String host = url.getHost().toLowerCase(Locale.ROOT);
				if (!"https".equals(url.getProtocol()) || !(host.equals("runelite.net") || host.endsWith(".runelite.net")))
				{
					throw new IOException("unexpected client jar location");
				}
				File parent = store.getAbsoluteFile().getParentFile();
				if (parent != null && !parent.isDirectory() && !parent.mkdirs())
				{
					throw new IOException("could not create " + parent);
				}
				downloaded = File.createTempFile("stock-client", ".jar", parent);
				remote.download(client.path, downloaded, Math.min(MAX_JAR_BYTES, client.size > 0 ? client.size : MAX_JAR_BYTES));
				if (!client.sha256.equals(sha256(downloaded)))
				{
					throw new IOException("client jar checksum mismatch");
				}
				jar = downloaded;
			}

			Integer hash = signerHash(jar);
			if (hash == null)
			{
				throw new IOException("stock client jar has no verified signer on its callbacks class");
			}
			stored.setProperty(STORE_ARTIFACT, client.sha256);
			stored.setProperty(STORE_HASH, Integer.toString(hash));
			writeStore(store, stored);
			return use(hash, SOURCE_LIVE);
		}
		catch (Throwable t)
		{
			log.info("Stock signer lookup failed, using the " + source + " value: " + t);
			return source;
		}
		finally
		{
			if (downloaded != null && !downloaded.delete())
			{
				downloaded.deleteOnExit();
			}
		}
	}

	/**
	 * What {@code Class.getSigners()} hashes to for the callbacks class of this jar, or null when the
	 * class is missing, unsigned or fails verification.
	 */
	static Integer signerHash(File jar) throws IOException
	{
		try (JarFile file = new JarFile(jar, true))
		{
			JarEntry entry = file.getJarEntry(CALLBACKS_ENTRY);
			if (entry == null)
			{
				return null;
			}
			try (InputStream in = file.getInputStream(entry))
			{
				// Signers are only known once the whole entry has been read and its digest checked.
				in.readAllBytes();
			}
			catch (SecurityException e)
			{
				return null;
			}
			CodeSigner[] signers = entry.getCodeSigners();
			if (signers == null || signers.length == 0)
			{
				return null;
			}
			// The same conversion a class loader applies before Class.getSigners() returns them.
			return Arrays.hashCode(new CodeSource(null, signers).getCertificates());
		}
	}

	/** The top-level {@code client-<version>.jar} artifact; entries under {@code diffs} are patches, not jars. */
	static Artifact clientArtifact(String bootstrap)
	{
		Matcher objects = FLAT_OBJECT.matcher(DIFFS.matcher(bootstrap).replaceAll("\"diffs\":null"));
		while (objects.find())
		{
			String object = objects.group();
			String name = stringField(object, "name");
			String path = stringField(object, "path");
			String hash = stringField(object, "hash");
			if (name == null || path == null || hash == null || !CLIENT_JAR.matcher(name).matches())
			{
				continue;
			}
			hash = hash.toLowerCase(Locale.ROOT);
			if (!SHA256.matcher(hash).matches())
			{
				continue;
			}
			Matcher size = Pattern.compile("\"size\"\\s*:\\s*(\\d{1,12})").matcher(object);
			return new Artifact(name, path, hash, size.find() ? Long.parseLong(size.group(1)) : -1);
		}
		return null;
	}

	private static String stringField(String object, String field)
	{
		Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"\\\\]*)\"").matcher(object);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String loadCached(File store)
	{
		Integer cached = parse(readStore(store).getProperty(STORE_HASH));
		return cached != null ? use(cached, SOURCE_CACHED) : use(BUNDLED, SOURCE_BUNDLED);
	}

	private static String use(int hash, String source)
	{
		current = hash;
		System.setProperty(SOURCE_PROPERTY, source);
		return source;
	}

	private static Integer parse(String value)
	{
		try
		{
			return value == null ? null : Integer.valueOf(value.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static Properties readStore(File store)
	{
		Properties properties = new Properties();
		if (store.isFile())
		{
			try (InputStream in = Files.newInputStream(store.toPath()))
			{
				properties.load(in);
			}
			catch (IOException | IllegalArgumentException e)
			{
				log.info("Could not read stock signer store: " + e);
			}
		}
		return properties;
	}

	private static void writeStore(File store, Properties properties) throws IOException
	{
		File parent = store.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs())
		{
			throw new IOException("could not create " + parent);
		}
		File temp = File.createTempFile("stock-signer", ".tmp", parent);
		try (OutputStream out = Files.newOutputStream(temp.toPath()))
		{
			properties.store(out, "Signer hash of the stock RuneLite client, refreshed by the hwid agent");
		}
		Files.move(temp.toPath(), store.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private static String sha256(File file) throws IOException
	{
		try (InputStream in = Files.newInputStream(file.toPath()))
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[65536];
			for (int read; (read = in.read(buffer)) > 0;)
			{
				digest.update(buffer, 0, read);
			}
			StringBuilder hex = new StringBuilder();
			for (byte b : digest.digest())
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (java.security.NoSuchAlgorithmException e)
		{
			throw new IOException(e);
		}
	}

	private static File runeliteHome()
	{
		return new File(System.getProperty("runelite.home", new File(System.getProperty("user.home"), ".runelite").getPath()));
	}

	private static File storeFile()
	{
		String override = System.getProperty(STORE_PROPERTY);
		return override != null && !override.isBlank() ? new File(override) : new File(runeliteHome(), "stock-signer.properties");
	}

	static void resetForTesting()
	{
		current = BUNDLED;
		System.clearProperty(SOURCE_PROPERTY);
	}

	static final class Artifact
	{
		final String name;
		final String path;
		final String sha256;
		final long size;

		Artifact(String name, String path, String sha256, long size)
		{
			this.name = name;
			this.path = path;
			this.sha256 = sha256;
			this.size = size;
		}
	}

	private static final class HttpRemote implements Remote
	{
		@Override
		public byte[] bootstrap() throws IOException
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			copy(BOOTSTRAP_URL, out, MAX_BOOTSTRAP_BYTES);
			return out.toByteArray();
		}

		@Override
		public void download(String url, File target, long maxBytes) throws IOException
		{
			try (OutputStream out = Files.newOutputStream(target.toPath()))
			{
				copy(url, out, maxBytes);
			}
		}

		private static void copy(String url, OutputStream out, long maxBytes) throws IOException
		{
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(15000);
			connection.setInstanceFollowRedirects(false);
			try
			{
				if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
				{
					throw new IOException("HTTP " + connection.getResponseCode());
				}
				try (InputStream in = connection.getInputStream())
				{
					byte[] buffer = new byte[65536];
					long total = 0;
					for (int read; (read = in.read(buffer)) > 0;)
					{
						total += read;
						if (total > maxBytes)
						{
							throw new IOException("response larger than expected");
						}
						out.write(buffer, 0, read);
					}
				}
			}
			finally
			{
				connection.disconnect();
			}
		}
	}
}
