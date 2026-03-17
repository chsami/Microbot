package net.runelite.client.proxy;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class ProxyChecker {
    private static final Pattern IPV4 =
            Pattern.compile("^(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");

    /**
     * Detects the external IP address by querying a list of endpoints.
     * Uses public, neutral IP-echo services — no upstream Microbot cloud calls.
     * @param okHttpClient
     * @return
     */
    public static String getDetectedIp(OkHttpClient okHttpClient) {
        List<String> endpoints = List.of(
                "https://api4.my-ip.io/v2/ip.txt",
                "https://ipv4.icanhazip.com"
        );

        for (String url : endpoints) {
            String ip = fetchIp(okHttpClient, url);
            if (ip != null) {
                return ip;
            }
        }
        return "";
    }

    /**
     * Fetches the IP address from a given URL using the provided OkHttpClient.
     * @param client
     * @param url
     * @return
     */
    private static String fetchIp(OkHttpClient client, String url) {
        Request req = new Request.Builder().url(url).build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                log.warn("IP endpoint failed: {} (code {})", url, res.code());
                return null;
            }
            String ip = res.body().string().trim();
            if (!IPV4.matcher(ip).matches()) {
                log.warn("Invalid IP payload from {}: '{}'", url, ip);
                return null;
            }
            return ip;
        } catch (IOException e) {
            log.error("Error calling {}", url, e);
            return null;
        }
    }

}
