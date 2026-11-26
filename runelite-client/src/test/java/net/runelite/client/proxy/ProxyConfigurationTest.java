package net.runelite.client.proxy;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProxyConfigurationTest
{
	private static final String PROXY_HOST = "proxy.example.net";
	private static final int PROXY_PORT = 1080;

	private final Authenticator authenticator = ProxyConfiguration.proxyAuthenticator(PROXY_HOST, PROXY_PORT, "user", "secret");

	@After
	public void resetDefaultAuthenticator()
	{
		Authenticator.setDefault(null);
		System.clearProperty("socksProxyHost");
		System.clearProperty("socksProxyPort");
	}

	@Test
	public void socksLoginToTheProxyGetsCredentials()
	{
		PasswordAuthentication auth = request(PROXY_HOST, PROXY_PORT, "SOCKS5", null, null, Authenticator.RequestorType.SERVER);

		assertNotNull(auth);
		assertEquals("user", auth.getUserName());
		assertEquals("secret", new String(auth.getPassword()));
	}

	@Test
	public void httpLoginChallengeFromAWebsiteGetsNothing() throws Exception
	{
		assertNull(request("evil.example.com", 443, "https", "Basic", new URL("https://evil.example.com/"), Authenticator.RequestorType.SERVER));
	}

	@Test
	public void httpLoginChallengeOnTheProxyHostGetsNothing() throws Exception
	{
		assertNull(request(PROXY_HOST, PROXY_PORT, "http", "Basic", new URL("http://" + PROXY_HOST + ":" + PROXY_PORT + "/"), Authenticator.RequestorType.SERVER));
	}

	@Test
	public void socksLoginToAnotherHostGetsNothing()
	{
		assertNull(request("other-proxy.example.net", PROXY_PORT, "SOCKS5", null, null, Authenticator.RequestorType.SERVER));
	}

	@Test
	public void socksLoginToAnotherPortGetsNothing()
	{
		assertNull(request(PROXY_HOST, 1081, "SOCKS5", null, null, Authenticator.RequestorType.SERVER));
	}

	@Test
	public void proxyHostMatchIgnoresCase()
	{
		assertNotNull(request("PROXY.Example.NET", PROXY_PORT, "SOCKS5", null, null, Authenticator.RequestorType.SERVER));
	}

	@Test(timeout = 10_000)
	public void jdkSocksHandshakeStillAuthenticates() throws Exception
	{
		try (ServerSocket proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()))
		{
			CompletableFuture<String> seenLogin = CompletableFuture.supplyAsync(() -> acceptSocks5Login(proxy));
			ProxyConfiguration.setupAuthenticator("127.0.0.1", proxy.getLocalPort(), "user", "secret");

			Proxy socks = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", proxy.getLocalPort()));
			try (Socket socket = new Socket(socks))
			{
				socket.connect(InetSocketAddress.createUnresolved("oldschool1.runescape.com", 43594), 5_000);
			}

			assertEquals("user:secret", seenLogin.get(5, TimeUnit.SECONDS));
		}
	}

	@Test(timeout = 10_000)
	public void jvmWideSocksProxyStillAuthenticates() throws Exception
	{
		try (ServerSocket proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()))
		{
			CompletableFuture<String> seenLogin = CompletableFuture.supplyAsync(() -> acceptSocks5Login(proxy));
			System.setProperty("socksProxyHost", "127.0.0.1");
			System.setProperty("socksProxyPort", String.valueOf(proxy.getLocalPort()));
			ProxyConfiguration.setupAuthenticator("127.0.0.1", proxy.getLocalPort(), "user", "secret");

			try (Socket socket = new Socket())
			{
				socket.connect(InetSocketAddress.createUnresolved("oldschool1.runescape.com", 43594), 5_000);
			}

			assertEquals("user:secret", seenLogin.get(5, TimeUnit.SECONDS));
		}
	}

	@Test
	public void ipv6LiteralProxyHostGetsCredentials()
	{
		Authenticator ipv6 = ProxyConfiguration.proxyAuthenticator("[::1]", PROXY_PORT, "user", "secret");

		assertNotNull(Authenticator.requestPasswordAuthentication(ipv6, "[::1]", null, PROXY_PORT, "SOCKS5", "prompt", null, null, Authenticator.RequestorType.SERVER));
	}

	private PasswordAuthentication request(String host, int port, String protocol, String scheme, URL url, Authenticator.RequestorType type)
	{
		return Authenticator.requestPasswordAuthentication(authenticator, host, null, port, protocol, "prompt", scheme, url, type);
	}

	private static String acceptSocks5Login(ServerSocket proxy)
	{
		try (Socket client = proxy.accept())
		{
			DataInputStream in = new DataInputStream(client.getInputStream());
			OutputStream out = client.getOutputStream();

			in.readUnsignedByte();
			in.readFully(new byte[in.readUnsignedByte()]);
			out.write(new byte[]{5, 2});
			out.flush();

			in.readUnsignedByte();
			String user = readString(in);
			String pass = readString(in);
			out.write(new byte[]{1, 0});
			out.flush();

			in.readFully(new byte[3]);
			int addressType = in.readUnsignedByte();
			skipAddress(in, addressType);
			in.readFully(new byte[2]);
			out.write(new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
			out.flush();
			return user + ":" + pass;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static String readString(DataInputStream in) throws IOException
	{
		byte[] value = new byte[in.readUnsignedByte()];
		in.readFully(value);
		return new String(value, StandardCharsets.UTF_8);
	}

	private static void skipAddress(InputStream in, int addressType) throws IOException
	{
		DataInputStream data = new DataInputStream(in);
		if (addressType == 1)
		{
			data.readFully(new byte[4]);
		}
		else if (addressType == 4)
		{
			data.readFully(new byte[16]);
		}
		else
		{
			data.readFully(new byte[data.readUnsignedByte()]);
		}
	}
}
