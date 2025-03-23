package net.runelite.client.plugins.microbot.github;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.github.models.FileInfo;
import net.runelite.client.plugins.microbot.github.models.GithubRepoInfo;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads files from a GitHub repository using the GitHub API.
 */
@Slf4j
public class GithubDownloader {
    // GitHub API URL format: replace {owner}, {repo}, and {path}
    private static final String GITHUB_API_URL = "https://api.github.com/repos/%s/%s/contents/%s";


    /**
     * Makes an HTTP GET request and returns the response as a String.
     */
    private static String get(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // Set headers to ensure GitHub returns JSON in the v3 format
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "Java-GitHubDownloader");
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    /**
     * Downloads a file from the given URL and saves it to the specified destination.
     */
    public static void downloadFile(String downloadUrl) throws Exception {
        URL url = new URL(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Java-GitHubDownloader");
        String filename = url.toString().substring(url.toString().lastIndexOf('/') + 1);
        InputStream in = connection.getInputStream();
        OutputStream out = new FileOutputStream(Paths.get(RuneLite.RUNELITE_DIR + "/microbot-plugins/" + filename).toString());

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        in.close();
        out.close();
    }

    /**
     * Fetches the files in a GitHub repository folder.
     *
     * @param url
     * @param folder
     * @return
     */
    public static String fetchFiles(String url, String folder, String token) {
        try {
            GithubRepoInfo repoInfo = new GithubRepoInfo(url);
            String apiUrl = String.format(GITHUB_API_URL, repoInfo.getOwner(), repoInfo.getRepo(), folder);

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            checkRateLimit(conn);
            String json = new String(conn.getInputStream().readAllBytes());

            return json;
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return "";
    }

    @Nullable
    private static String checkRateLimit(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode == 403) {
            // read the error stream and show it
            String errorMsg = readStream(conn.getErrorStream());
            JOptionPane.showConfirmDialog(null, "GitHub API error (403):\n" + errorMsg, "Error",
                    JOptionPane.DEFAULT_OPTION);
        }
        return "";
    }

    private static String readStream(InputStream stream) {
        if (stream == null) return "No error message received.";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "Failed to read error message: " + e.getMessage();
        }
    }

    /**
     * Checks if the repository is too large to download.
     *
     * @param url
     * @return
     */
    public static boolean isLargeRepo(String url, String token) {
        try {
            var githubRepoInfo = new GithubRepoInfo(url);

            String apiUrl = String.format("https://api.github.com/repos/%s/%s",
                    URLEncoder.encode(githubRepoInfo.getOwner(), "UTF-8"), URLEncoder.encode(githubRepoInfo.getRepo(), "UTF-8"));

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (conn.getResponseCode() != 200) {
                return false; // can't determine size, assume it's fine
            }

            checkRateLimit(conn);
            String json = new String(conn.getInputStream().readAllBytes());
            JSONObject obj = new JSONObject(json);
            int sizeKB = obj.getInt("size"); // GitHub gives size in kilobytes
            double sizeMB = sizeKB / 1024.0;

            if (sizeMB > 50) {
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to check repo size: " + e.getMessage());
        }
        return false;
    }

    /**
     * Fetches all files in a GitHub repository folder recursively.
     *
     * @param url
     * @param path
     * @return
     */
    public static List<FileInfo> getAllFilesRecursively(String url, String path, String token) {
        List<FileInfo> files = new ArrayList<>();
        fetchRecursive(url, path, token, files);
        return files;
    }

    /**
     * Fetches all files in a GitHub repository folder recursively.
     *
     * @param url
     * @param path
     * @param files
     */
    public static void fetchRecursive(String url, String path, String token, List<FileInfo> files) {
        try {
            GithubRepoInfo repoInfo = new GithubRepoInfo(url);
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                    URLEncoder.encode(repoInfo.getOwner(), "UTF-8"),
                    URLEncoder.encode(repoInfo.getRepo(), "UTF-8"),
                    URLEncoder.encode(path, "UTF-8"));

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (conn.getResponseCode() != 200) {
                System.err.println("Failed to fetch: " + apiUrl + " (HTTP " + conn.getResponseCode() + ")");
                return;
            }

            checkRateLimit(conn);
            String json = new String(conn.getInputStream().readAllBytes());
            JSONArray items = new JSONArray(json);

            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.getJSONObject(i);
                String type = obj.getString("type");

                if (type.equals("file")) {
                    String name = obj.getString("name");
                    String downloadUrl = obj.getString("download_url");

                    files.add(new FileInfo(name, downloadUrl));
                } else if (type.equals("dir")) {
                    String subPath = obj.getString("path"); // full subfolder path
                    fetchRecursive(url, subPath, token, files); // recurse
                }
            }
        } catch (Exception e) {
            log.error("Error in fetchRecursive: " + e.getMessage());
        }
    }

}
