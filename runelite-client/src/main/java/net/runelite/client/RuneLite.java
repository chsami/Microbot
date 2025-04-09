/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import joptsimple.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.discord.DiscordService;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.sideloading.MicrobotPluginManager;
import net.runelite.client.rs.ClientLoader;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.FatalErrorDialog;
import net.runelite.client.ui.SplashScreen;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.WidgetOverlay;
import net.runelite.client.ui.overlay.tooltip.TooltipOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.util.OSType;
import net.runelite.client.util.ReflectUtil;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;

@Singleton
@Slf4j
public class RuneLite
{
	public static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	public static final File CACHE_DIR = new File(RUNELITE_DIR, "cache");
	public static final File PLUGINS_DIR = new File(RUNELITE_DIR, "plugins");
	public static final File SCREENSHOT_DIR = new File(RUNELITE_DIR, "screenshots");
	public static final File LOGS_DIR = new File(RUNELITE_DIR, "logs");
	public static final File DEFAULT_SESSION_FILE = new File(RUNELITE_DIR, "session");
	public static final File NOTIFICATIONS_DIR = new File(RuneLite.RUNELITE_DIR, "notifications");

	private static final int MAX_OKHTTP_CACHE_SIZE = 20 * 1024 * 1024; // 20mb
	public static String USER_AGENT = "RuneLite/" + RuneLiteProperties.getVersion() + "-" + RuneLiteProperties.getCommit() + (RuneLiteProperties.isDirty() ? "+" : "");

	@Getter
	private static Injector injector;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ExternalPluginManager externalPluginManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private DiscordService discordService;

	@Inject
	private ClientSessionManager clientSessionManager;

	@Inject
	private ClientUI clientUI;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Provider<TooltipOverlay> tooltipOverlay;

	@Inject
	private Provider<WorldMapOverlay> worldMapOverlay;

	@Inject
	private Gson gson;

	@Inject
	@Nullable
	private Client client;

	@Inject
	@Nullable
	private RuntimeConfig runtimeConfig;

	@Inject
	@Nullable
	private TelemetryClient telemetryClient;

    @Inject
    private MicrobotPluginManager microbotPluginManager;

    public static void main(String[] args) throws Exception {
        validateJavaVersion();
        Locale.setDefault(Locale.ENGLISH);

		final OptionParser parser = new OptionParser(false);
        parser.accepts("clean-jagex-launcher", "Enable jagex launcher"); // will remove the credentials.properties file in .runelite folder
		parser.accepts("developer-mode", "Enable developer tools");
		parser.accepts("debug", "Show extra debugging output");
		parser.accepts("safe-mode", "Disables external plugins and the GPU plugin");
		parser.accepts("insecure-skip-tls-verification", "Disables TLS verification");
		parser.accepts("jav_config", "jav_config url")
			.withRequiredArg()
			.defaultsTo(RuneLiteProperties.getJavConfig());
		parser.accepts("disable-telemetry", "Disable telemetry");
        parser.accepts("disable-walker-update", "Disable updates for the static walker");
		parser.accepts("profile", "Configuration profile to use").withRequiredArg();
		parser.accepts("noupdate", "Skips the launcher update");
		parser.accepts("clean-randomdat", "Clean random dat file");


        final ArgumentAcceptingOptionSpec<String> proxyInfo = parser.accepts("proxy", "Use a proxy server for your runelite session")
                .withRequiredArg().ofType(String.class);
        parser.accepts("proxy-type", "The Type of proxy: HTTP or SOCKS").withRequiredArg().ofType(String.class);
        final ArgumentAcceptingOptionSpec<File> sessionfile = parser.accepts("sessionfile", "Use a specified session file")
                .withRequiredArg()
                .withValuesConvertedBy(new ConfigFileConverter())
                .defaultsTo(DEFAULT_SESSION_FILE);

		final OptionSpec<Void> insecureWriteCredentials = parser.accepts("insecure-write-credentials", "Dump authentication tokens from the Jagex Launcher to a text file to be used for development");

		parser.accepts("help", "Show this text").forHelp();
		OptionSet options = parser.parse(args);

        if (options.has("clean-jagex-launcher")) {
            System.out.println("clean-jagex-launcher option is enabled. This will delete your credentials.properties file to allow logging in with a username/password");
            System.out.println("You can disable this in your run configuration by removing -clean-jagex-launcher");
            File myObj = new File(System.getProperty("user.home") + "/.runelite/credentials.properties");
            if (myObj.delete()) {
                System.out.println("Succesfully Deleted the file: " + myObj.getName());
            } else {
                System.out.println("Credentials.properties file was not found.");
            }
        }

        if (options.has("help")) {
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.INFO);

        if (options.has("debug")) {
            logger.setLevel(Level.DEBUG);
        }

		if (options.has("clean-randomdat")) {
			File randomDat = new File(System.getProperty("user.home"), "random.dat");

			// If random.dat exists, remove it
			if (randomDat.exists()) {
				if (!randomDat.delete()) {
					System.err.println("Failed to delete random.dat");
				}
			}

			// Create a new random.dat file
			try {
				if (randomDat.createNewFile()) {
					// Attempt to mark the file read‐only
					boolean readOnlySuccess = randomDat.setReadOnly();
					if (!readOnlySuccess) {
						System.err.println("Failed to set random.dat to read‐only");
					}
				} else {
					System.err.println("Failed to create new random.dat file");
				}
			} catch (IOException e) {
				System.err.println("Error creating random.dat: " + e.getMessage());
				e.printStackTrace();
			}
		}

        //More information about java proxies can be found here
        //https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html
        //usage: -proxy=IP:PORT:USER:PASS -proxytype=SOCKS
        //OR
        //usage: -proxy=IP:PORT:USER:PASS -proxytype=HTTP
        if (options.has("proxy")) {
            String[] proxy = options.valueOf(proxyInfo).split(":");
            boolean httpProxy = false;
            boolean socksProxy = true; //default we take socks proxy
            if (options.has("proxy-type")) {
                socksProxy = options.valueOf("proxy-type").toString().equalsIgnoreCase("SOCKS");
                httpProxy = options.valueOf("proxy-type").toString().equalsIgnoreCase("HTTP");
            }

            ClientUI.proxyMessage = (socksProxy ? "SOCKS" : "HTTP") + " Proxy with address " + options.valueOf(proxyInfo);

            if (httpProxy && proxy.length >= 2) {
                System.setProperty("http.proxyHost", proxy[0]);
                System.setProperty("http.proxyPort", proxy[1]);
            } else if (socksProxy && proxy.length >= 2) {
                System.setProperty("socksProxyHost", proxy[0]);
                System.setProperty("socksProxyPort", proxy[1]);
            }

            if (socksProxy && proxy.length >= 4) {
                System.setProperty("java.net.socks.username", proxy[2]);
                System.setProperty("java.net.socks.password", proxy[3]);

                final String user = proxy[2];
                final char[] pass = proxy[3].toCharArray();

                Authenticator.setDefault(new Authenticator() {
                    private final PasswordAuthentication auth = new PasswordAuthentication(user, pass);

                    protected PasswordAuthentication getPasswordAuthentication() {
                        return auth;
                    }
                });
            }
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
        {
            log.error("Uncaught exception:", throwable);
            if (throwable instanceof AbstractMethodError) {
                log.error("Classes are out of date; Build with maven again.");
            }
        });

		final OkHttpClient okHttpClient = buildHttpClient(options.has("insecure-skip-tls-verification"));
		RuneLiteAPI.CLIENT = okHttpClient;

		SplashScreen.init();
		SplashScreen.stage(0, "Preparing RuneScape", "");

		try
		{
			final RuntimeConfigLoader runtimeConfigLoader = new RuntimeConfigLoader(okHttpClient);
			final ClientLoader clientLoader = new ClientLoader(okHttpClient, runtimeConfigLoader, (String) options.valueOf("jav_config"));

			new Thread(() ->
			{
				clientLoader.get();
				ClassPreloader.preload();
			}, "Preloader").start();

			final boolean developerMode = options.has("developer-mode") && RuneLiteProperties.getLauncherVersion() == null;

			if (developerMode)
			{
				boolean assertions = false;
				assert assertions = true;
				if (!assertions)
				{
					SwingUtilities.invokeLater(() ->
						new FatalErrorDialog("Developers should enable assertions; Add `-ea` to your JVM arguments`")
							.addHelpButtons()
							.addBuildingGuide()
							.open());
					return;
				}
			}

			log.info("RuneLite {} (launcher version {}) starting up, args: {}",
				RuneLiteProperties.getVersion(), MoreObjects.firstNonNull(RuneLiteProperties.getLauncherVersion(), "unknown"),
				args.length == 0 ? "none" : String.join(" ", args));

			final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
			// This includes arguments from _JAVA_OPTIONS, which are parsed after command line flags and applied to
			// the global VM args
			log.info("Java VM arguments: {}", String.join(" ", runtime.getInputArguments()));

			final long start = System.currentTimeMillis();
			injector = Guice.createInjector(new RuneLiteModule(
				okHttpClient,
				clientLoader,
				runtimeConfigLoader,
				developerMode,
				options.has("safe-mode"),
				options.has("disable-telemetry"),
                options.has("disable-walker-update"),
				options.valueOf(sessionfile),
				(String) options.valueOf("profile"),
				options.has(insecureWriteCredentials),
				options.has("noupdate")
			));

			injector.getInstance(RuneLite.class).start();

			final long end = System.currentTimeMillis();
			final long uptime = runtime.getUptime();
			log.info("Client initialization took {}ms. Uptime: {}ms", end - start, uptime);
		}
		catch (Exception e)
		{
			log.error("Failure during startup", e);
			SwingUtilities.invokeLater(() ->
				new FatalErrorDialog("RuneLite has encountered an unexpected error during startup.")
					.addHelpButtons()
					.open());
		}
		finally
		{
			SplashScreen.stop();
		}
	}

    private static void validateJavaVersion() {
        // Get the Java version
        String javaVersion = System.getProperty("java.version");

        // Parse the major version
        int majorVersion = getMajorJavaVersion(javaVersion);

        // Check if the version is less than 11
        if (majorVersion < 11) {
            JOptionPane.showMessageDialog(
                    null,
                    "Your Java version is " + javaVersion + ".\n"
                            + "This application requires Java 11 or higher." +
                            "The application might not work correctly!\n\n"
                            + "A webpage will now open where you can download the latest Java version.",
                    "Unsupported Java Version",
                    JOptionPane.ERROR_MESSAGE
            );
            if (Desktop.isDesktopSupported()) {
                String osName = System.getProperty("os.name").toLowerCase();
                String downloadLink;
                // Map OS to download links
                Map<String, String> downloadLinks = new HashMap<>();
                downloadLinks.put("windows", "https://www.oracle.com/java/technologies/downloads/#jdk23-windows");
                downloadLinks.put("mac", "https://www.oracle.com/java/technologies/downloads/#jdk23-mac");
                downloadLinks.put("linux", "https://www.oracle.com/java/technologies/downloads/#jdk23-linux");

                if (osName.contains("win")) {
                    downloadLink = downloadLinks.get("windows");
                } else if (osName.contains("mac")) {
                    downloadLink = downloadLinks.get("mac");
                } else if (osName.contains("nux") || osName.contains("nix")) {
                    downloadLink = downloadLinks.get("linux");
                } else {
                    downloadLink = "https://www.oracle.com/java/technologies/downloads/";
                    System.out.println("OS not recognized, defaulting to general download page.");
                }

                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(downloadLink));
                    } else {
                        System.err.println("Desktop operations are not supported on this platform.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.err.println("Desktop is not supported on this platform.");
            }
        }
    }

	public void start() throws Exception
	{
		// Inject members into client
		injector.injectMembers(client);

		setupSystemProps();
		setupCompilerControl();

		// Start the applet
		copyJagexCache();

		// Client size must be set prior to init
		var applet = (Applet) client;
		applet.setSize(Constants.GAME_FIXED_SIZE);

		System.setProperty("jagex.disableBouncyCastle", "true");
		System.setProperty("jagex.userhome", RUNELITE_DIR.getAbsolutePath());

		applet.init();
		applet.start();

		SplashScreen.stage(.57, null, "Loading configuration");

		// Load the session so that the session profiles can be loaded next
		sessionManager.loadSession();

		// Load user configuration
		configManager.load();

		// Update check requires ConfigManager to be ready before it runs
		Updater updater = injector.getInstance(Updater.class);
		updater.update(); // will exit if an update is in progress

		// Load the plugins, but does not start them yet.
		// This will initialize configuration
		pluginManager.loadCorePlugins();
		pluginManager.loadSideLoadPlugins();
		externalPluginManager.loadExternalPlugins();

        microbotPluginManager.loadSideLoadPlugins();

        SplashScreen.stage(.70, null, "Finalizing configuration");

		// Plugins have provided their config, so set default config
		// to main settings
		pluginManager.loadDefaultPluginConfiguration(null);

		// Start client session
		clientSessionManager.start();
		eventBus.register(clientSessionManager);

		SplashScreen.stage(.75, null, "Starting core interface");

		// Initialize UI
		clientUI.init();

		// Initialize Discord service
		discordService.init();

		// Register event listeners
		eventBus.register(clientUI);
		eventBus.register(pluginManager);
		eventBus.register(externalPluginManager);
		eventBus.register(overlayManager);
		eventBus.register(configManager);
		eventBus.register(discordService);

		// Add core overlays
		WidgetOverlay.createOverlays(overlayManager, client).forEach(overlayManager::add);
		overlayManager.add(worldMapOverlay.get());
		overlayManager.add(tooltipOverlay.get());

		// Start plugins
		pluginManager.startPlugins();

		SplashScreen.stop();

		clientUI.show();

		if (telemetryClient != null)
		{
			telemetryClient.submitTelemetry();
			telemetryClient.submitVmErrors(LOGS_DIR);
		}

		ReflectUtil.queueInjectorAnnotationCacheInvalidation(injector);
		ReflectUtil.invalidateAnnotationCaches();
	}

	@VisibleForTesting
	public static void setInjector(Injector injector)
	{
		RuneLite.injector = injector;
	}

	private static class ConfigFileConverter implements ValueConverter<File>
	{
		@Override
		public File convert(String fileName)
		{
			final File file;

			if (Paths.get(fileName).isAbsolute()
				|| fileName.startsWith("./")
				|| fileName.startsWith(".\\"))
			{
				file = new File(fileName);
			}
			else
			{
				file = new File(RuneLite.RUNELITE_DIR, fileName);
			}

			if (file.exists() && (!file.isFile() || !file.canWrite()))
			{
				throw new ValueConversionException(String.format("File %s is not accessible", file.getAbsolutePath()));
			}

			return file;
		}

		@Override
		public Class<? extends File> valueType()
		{
			return File.class;
		}

		@Override
		public String valuePattern()
		{
			return null;
		}
	}

	@VisibleForTesting
	static OkHttpClient buildHttpClient(boolean insecureSkipTlsVerification)
	{
		OkHttpClient.Builder builder = new OkHttpClient.Builder()
			.pingInterval(30, TimeUnit.SECONDS)
			.addInterceptor(chain ->
			{
				Request request = chain.request();
				if (request.header("User-Agent") != null)
				{
					return chain.proceed(request);
				}

				Request userAgentRequest = request
					.newBuilder()
					.header("User-Agent", USER_AGENT)
					.build();
				return chain.proceed(userAgentRequest);
			})
			// Setup cache
			.cache(new Cache(new File(CACHE_DIR, "okhttp"), MAX_OKHTTP_CACHE_SIZE))
			.addNetworkInterceptor(chain ->
			{
				// This has to be a network interceptor so it gets hit before the cache tries to store stuff
				Response res = chain.proceed(chain.request());
				if (res.code() >= 400 && "GET".equals(res.request().method()))
				{
					// if the request 404'd we don't want to cache it because its probably temporary
					res = res.newBuilder()
						.header("Cache-Control", "no-store")
						.build();
				}
				return res;
			});

		try
		{
			if (insecureSkipTlsVerification || RuneLiteProperties.isInsecureSkipTlsVerification())
			{
				setupInsecureTrustManager(builder);
			}
			else
			{
				setupTrustManager(builder);
			}
		}
		catch (KeyStoreException | KeyManagementException | NoSuchAlgorithmException e)
		{
			log.warn("error setting up trust manager", e);
		}

		return builder.build();
	}

	private static void copyJagexCache()
	{
		Path from = Paths.get(System.getProperty("user.home"), "jagexcache");
		Path to = Paths.get(System.getProperty("user.home"), ".runelite", "jagexcache");
		if (Files.exists(to) || !Files.exists(from))
		{
			return;
		}

		log.info("Copying jagexcache from {} to {}", from, to);

		// Recursively copy path https://stackoverflow.com/a/50418060
		try (Stream<Path> stream = Files.walk(from))
		{
			stream.forEach(source ->
			{
				try
				{
					Files.copy(source, to.resolve(from.relativize(source)), COPY_ATTRIBUTES);
				}
				catch (IOException e)
				{
					throw new RuntimeException(e);
				}
			});
		}
		catch (Exception e)
		{
			log.warn("unable to copy jagexcache", e);
		}
	}

	private void setupSystemProps()
	{
		if (runtimeConfig == null || runtimeConfig.getSysProps() == null)
		{
			return;
		}

		for (Map.Entry<String, String> entry : runtimeConfig.getSysProps().entrySet())
		{
			String key = entry.getKey(), value = entry.getValue();
			log.debug("Setting property {}={}", key, value);
			System.setProperty(key, value);
		}
	}

	private void setupCompilerControl()
	{
		try
		{
			var file = Files.createTempFile("rl_compilercontrol", "");
			try
			{
				if (runtimeConfig != null && runtimeConfig.getCompilerControl() != null)
				{
					var json = gson.toJson(runtimeConfig.getCompilerControl());
					Files.writeString(file, json, StandardCharsets.UTF_8);
				}
				else
				{
					try (var in = RuneLite.class.getResourceAsStream("/compilercontrol.json"))
					{
						Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
					}
				}

				ManagementFactory.getPlatformMBeanServer().invoke(
					new ObjectName("com.sun.management:type=DiagnosticCommand"),
					"compilerDirectivesAdd",
					new Object[]{new String[]{file.toFile().getAbsolutePath()}},
					new String[]{String[].class.getName()});
			}
			finally
			{
				Files.delete(file);
			}
		}
		catch (Exception e)
		{
			log.info("Failed to set compiler control", e);
		}
	}

	// region trust manager
	private static TrustManager[] loadTrustManagers(String trustStoreType) throws KeyStoreException, NoSuchAlgorithmException
	{
		// javax.net.ssl.trustStoreType controls which keystore implementation the TrustStoreManager uses
		String old;
		if (trustStoreType != null)
		{
			old = System.setProperty("javax.net.ssl.trustStoreType", trustStoreType);
		}
		else
		{
			old = System.clearProperty("javax.net.ssl.trustStoreType");
		}

		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init((KeyStore) null);

		TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

		// restore old value
		if (old == null)
		{
			System.clearProperty("javax.net.ssl.trustStoreType");
		}
		else
		{
			System.setProperty("javax.net.ssl.trustStoreType", old);
		}

		return trustManagers;
	}

	private static void setupTrustManager(OkHttpClient.Builder okHttpClientBuilder) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException
	{
		if (OSType.getOSType() != OSType.Windows)
		{
			return;
		}

		// Use the Windows Trusted Root Certificate Authorities in addition to the bundled cacerts.
		// Corporations, schools, antivirus, and malware commonly install root certificates onto
		// machines for security or other reasons that are not present in the JRE certificate store.
		TrustManager[] jreTms = loadTrustManagers(null);
		TrustManager[] windowsTms = loadTrustManagers("Windows-ROOT");

		TrustManager[] trustManagers = new TrustManager[jreTms.length + windowsTms.length];
		System.arraycopy(jreTms, 0, trustManagers, 0, jreTms.length);
		System.arraycopy(windowsTms, 0, trustManagers, jreTms.length, windowsTms.length);

		// Even though SSLContext.init() accepts TrustManager[], Sun's SSLContextImpl only picks the first
		// X509TrustManager and uses that.
		X509TrustManager combiningTrustManager = new X509TrustManager()
		{
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
			{
				CertificateException exception = null;
				for (TrustManager trustManager : trustManagers)
				{
					if (trustManager instanceof X509TrustManager)
					{
						try
						{
							((X509TrustManager) trustManager).checkClientTrusted(chain, authType);
							// accept if any of the trust managers accept the certificate
							return;
						}
						catch (CertificateException ex)
						{
							exception = ex;
						}
					}
				}

				if (exception != null)
				{
					throw exception;
				}

				throw new CertificateException("no X509TrustManagers present");
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
			{
				CertificateException exception = null;
				for (TrustManager trustManager : trustManagers)
				{
					if (trustManager instanceof X509TrustManager)
					{
						try
						{
							((X509TrustManager) trustManager).checkServerTrusted(chain, authType);
							// accept if any of the trust managers accept the certificate
							return;
						}
						catch (CertificateException ex)
						{
							exception = ex;
						}
					}
				}

				if (exception != null)
				{
					throw exception;
				}

				throw new CertificateException("no X509TrustManagers present");
			}

			@Override
			public X509Certificate[] getAcceptedIssuers()
			{
				List<X509Certificate> certificates = new ArrayList<>();
				for (TrustManager trustManager : trustManagers)
				{
					if (trustManager instanceof X509TrustManager)
					{
						certificates.addAll(Arrays.asList(((X509TrustManager) trustManager).getAcceptedIssuers()));
					}
				}
				return certificates.toArray(new X509Certificate[0]);
			}
		};

		SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(null, new TrustManager[]{combiningTrustManager}, new SecureRandom());
		okHttpClientBuilder.sslSocketFactory(sc.getSocketFactory(), combiningTrustManager);
	}

	private static void setupInsecureTrustManager(OkHttpClient.Builder okHttpClientBuilder) throws NoSuchAlgorithmException, KeyManagementException
	{
		// the insecure trust manager trusts everything
		X509TrustManager trustManager = new X509TrustManager()
		{
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType)
			{
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType)
			{
			}

			@Override
			public X509Certificate[] getAcceptedIssuers()
			{
				return new X509Certificate[0];
			}
		};

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{trustManager}, new SecureRandom());
        okHttpClientBuilder.sslSocketFactory(sc.getSocketFactory(), trustManager);
    }
    // endregion

    /**
     * Extracts the major Java version from the full version string.
     * @param version the full Java version string
     * @return the major version as an integer
     */
    private static int getMajorJavaVersion(String version) {
        try {
            // Java 9 and above have version strings like "11.0.2"
            if (version.startsWith("1.")) {
                // Java 8 or earlier versions have strings like "1.8.0_202"
                return Integer.parseInt(version.split("\\.")[1]);
            } else {
                // For Java 9 and above
                return Integer.parseInt(version.split("\\.")[0]);
            }
        } catch (NumberFormatException e) {
            return -1; // Invalid version
        }
    }
}
