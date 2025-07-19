package net.runelite.client.plugins.microbot;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.runelite.client.RuneLiteProperties;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Class that communicates with the microbot api
 */
public class MicrobotApi {

    private final OkHttpClient client;
    private final Gson gson;

    private final String microbotApiUrl = "https://microbot.cloud/api";
    @Inject
    MicrobotApi(OkHttpClient client, Gson gson) {
        this.client = client;
        this.gson = gson;
    }

    /**
     * Opens a new session by sending a request to the microbot API and retrieves the session UUID.
     *
     * Steps:
     * 1. Sends an HTTP GET request to the `/session` endpoint.
     * 2. Reads the response body as a stream and parses it as a `UUID` using Gson.
     * 3. If the response contains invalid JSON or the parsed UUID is invalid, an exception is thrown.
     * 4. Uses a try-with-resources block to ensure the HTTP response and input stream are properly closed.
     *
     * @return the UUID of the newly opened session.
     * @throws IOException if the HTTP request fails, the response body is invalid, or the UUID parsing fails.
     */
    public UUID microbotOpen() throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(microbotApiUrl + "/session").build()).execute()) {
            ResponseBody body = response.body();

            InputStream in = body.byteStream();
            return gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), UUID.class);
        } catch (JsonParseException | IllegalArgumentException ex) // UUID.fromString can throw IllegalArgumentException
        {
            throw new IOException(ex);
        }
    }

    /**
     * Sends a ping request to the microbot API to update the session status.
     *
     * Steps:
     * 1. Constructs an HTTP GET request with query parameters:
     *    - `sessionId`: The unique identifier of the session (UUID).
     *    - `isLoggedIn`: A boolean indicating if the user is logged in.
     *    - `version`: The version of the microbot (retrieved from `RuneLiteProperties`).
     * 2. Sends the request using the HTTP client and checks the response.
     * 3. If the response is unsuccessful, an `IOException` is thrown to indicate the failure.
     * 4. Uses a try-with-resources block to automatically close the HTTP response.
     *
     * @param uuid the unique identifier of the session to ping.
     * @param loggedIn the login status to send with the ping.
     * @throws IOException if the HTTP request fails or the response is unsuccessful.
     */
    public void microbotPing(UUID uuid, boolean loggedIn) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(microbotApiUrl + "/session?sessionId=" + uuid.toString()
                + "&isLoggedIn=" + loggedIn
                + "&version=" + RuneLiteProperties.getMicrobotVersion()).build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unsuccessful ping");
            }
        }
    }

    /**
     * Sends a DELETE request to remove a microbot session by its UUID.
     *
     * Steps:
     * 1. Constructs an HTTP DELETE request to the endpoint with the session ID as a query parameter.
     * 2. Sends the request using the HTTP client and closes the response to free resources.
     *
     * @param uuid the unique identifier of the session to be deleted.
     * @throws IOException if an I/O error occurs during the HTTP request.
     */
    public void microbotDelete(UUID uuid) throws IOException {
        Request request = new Request.Builder()
                .delete()
                .url(microbotApiUrl + "/session?sessionId=" + uuid)
                .build();

        client.newCall(request).execute().close();
    }
}