package net.runelite.client.proxy;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSet;
import net.runelite.client.plugins.microbot.Microbot;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Configures the JVM to use a SOCKS5 proxy if the appropriate command line argument is provided.
 */
public class ProxyConfiguration {

    private static volatile String username;

    public static String getUsername() {
        return username;
    }

    /**
     * Sets up the proxy configuration based on the provided options.
     * @param options
     * @param proxyInfo
     */
    public static void setupProxy(OptionSet options, ArgumentAcceptingOptionSpec<String> proxyInfo) {
        if (!options.has(proxyInfo)) {
            return;
        }

        URI uri = URI.create(options.valueOf(proxyInfo));

        if (options.has("proxy-type")) {
            Microbot.showMessage("Proxy type is no longer supported, please use the format -proxy=socks://user:pass@host:port or http://user:pass@host:port");
            System.exit(1);
        }

        String host = uri.getHost();
        String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);

        validateProxyScheme(scheme);

        int port = validatePort(uri.getPort());

        String[] credentials = extractCredentials(uri);
        String user = credentials[0];
        String pass = credentials[1];

        configureProxy(host, port);
        username = user;

        if (user != null) {
            setupAuthenticator(host, port, user, pass);
        }
    }

    /**
     * Validates the proxy scheme to ensure it is SOCKS5.
     * @param scheme
     */
    private static void validateProxyScheme(String scheme) {
        boolean isHttpProxy = scheme.equals("http") || scheme.equals("https");
        if (isHttpProxy) {
            Microbot.showMessage("HTTP(S) proxies are not supported, please use a SOCKS5 proxy. \n\n This is to make sure that osrs traffic is also routed through the proxy.");
            System.exit(1);
        }

        boolean isSocksProxy = scheme.equals("socks") || scheme.equals("socks5");
        if (!isSocksProxy) {
            Microbot.showMessage("Proxy scheme must be socks(5).");
            System.exit(1);
        }
    }

    /**
     * Validates the proxy port to ensure it is a positive integer.
     * @param port
     * @return
     */
    private static int validatePort(int port) {
        if (port <= 0) {
            Microbot.showMessage("Invalid proxy port");
            System.exit(1);
        }
        return port;
    }

    /**
     * Extracts the username and password from the URI's user info.
     * @param uri
     * @return
     */
    private static String[] extractCredentials(URI uri) {
        String user = null;
        String pass = null;
        if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            user = userInfo[0];
            pass = userInfo[1];
        }
        return new String[]{user, pass};
    }

    /**
     * Configures the JVM to use the specified SOCKS5 proxy.
     * @param host
     * @param port
     */
    private static void configureProxy(String host, int port) {
        System.setProperty("socksProxyHost", host);
        System.setProperty("socksProxyPort", String.valueOf(port > 0 ? port : 1080));
    }

    /**
     * Sets up the default authenticator for proxy authentication.
     * @param user
     * @param pass
     */
    static void setupAuthenticator(String host, int port, String user, String pass) {
        Authenticator.setDefault(proxyAuthenticator(host, port, user, pass));
    }

    static Authenticator proxyAuthenticator(String host, int port, String user, String pass) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (!isProxyRequest(getRequestingProtocol(), getRequestingHost(), getRequestingPort(), host, port)) {
                    return null;
                }
                return new PasswordAuthentication(user, pass != null ? pass.toCharArray() : new char[0]);
            }
        };
    }

    static boolean isProxyRequest(String protocol, String requestingHost, int requestingPort, String proxyHost, int proxyPort) {
        return protocol != null
                && protocol.toUpperCase(Locale.ROOT).startsWith("SOCKS")
                && requestingHost != null
                && requestingHost.equalsIgnoreCase(proxyHost)
                && requestingPort == proxyPort;
    }
}