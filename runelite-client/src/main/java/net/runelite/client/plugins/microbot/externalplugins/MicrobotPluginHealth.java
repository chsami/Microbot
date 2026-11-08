package net.runelite.client.plugins.microbot.externalplugins;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MicrobotPluginHealth {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_BROKEN = "broken";
    public static final String STATUS_UNVERIFIED = "unverified";
    public static final String ALL_VERSIONS = "*";

    public enum State {
        DISABLED("Turned off on the Microbot Hub", true),
        INCOMPATIBLE("Requires a newer client", true),
        BROKEN("Confirmed broken", true),
        UNVERIFIED("Unverified report", false),
        VERIFIED("Verified working", false),
        UNKNOWN("Health not reported", false);

        private final String label;
        private final boolean blocking;

        State(String label, boolean blocking) {
            this.label = label;
            this.blocking = blocking;
        }

        public String getLabel() {
            return label;
        }

        public boolean isBlocking() {
            return blocking;
        }
    }

    @Data
    public static class Metadata {
        private String status;
        private String reason;
        private List<String> affectedVersions;
        private String trackingUrl;
        private String lastVerifiedVersion;
        private String lastVerifiedClientVersion;
        private String updatedAt;
    }

    private final State state;
    private final String version;
    private final Metadata metadata;
    private final String requiredClientVersion;

    private MicrobotPluginHealth(State state, String version, Metadata metadata, String requiredClientVersion) {
        this.state = state;
        this.version = version;
        this.metadata = metadata;
        this.requiredClientVersion = requiredClientVersion;
    }

    public static MicrobotPluginHealth evaluate(MicrobotPluginManifest manifest, String version, boolean clientCompatible) {
        String evaluated = Strings.isNullOrEmpty(version) ? manifest.getVersion() : version.trim();
        Metadata metadata = manifest.getHealth();
        String required = manifest.getMinClientVersion();

        if (manifest.isDisable()) {
            return new MicrobotPluginHealth(State.DISABLED, evaluated, metadata, required);
        }

        if (!clientCompatible && Objects.equals(evaluated, manifest.getVersion())) {
            return new MicrobotPluginHealth(State.INCOMPATIBLE, evaluated, metadata, required);
        }

        return new MicrobotPluginHealth(stateFromMetadata(metadata, evaluated), evaluated, metadata, required);
    }

    private static State stateFromMetadata(Metadata metadata, String version) {
        if (metadata == null) {
            return State.UNKNOWN;
        }

        String status = normalize(metadata.getStatus());
        if (STATUS_BROKEN.equals(status)) {
            if (affects(metadata, version)) {
                return State.BROKEN;
            }
            if (affectedVersions(metadata).isEmpty()) {
                return State.UNVERIFIED;
            }
            return verifiedFor(metadata, version, false) ? State.VERIFIED : State.UNKNOWN;
        }
        if (STATUS_UNVERIFIED.equals(status)) {
            return State.UNVERIFIED;
        }
        if (STATUS_OK.equals(status)) {
            return verifiedFor(metadata, version, true) ? State.VERIFIED : State.UNKNOWN;
        }
        return State.UNKNOWN;
    }

    private static boolean verifiedFor(Metadata metadata, String version, boolean blankMeansCurrent) {
        String verified = trimToNull(metadata.getLastVerifiedVersion());
        if (verified == null) {
            return blankMeansCurrent;
        }
        return verified.equals(version);
    }

    private static boolean affects(Metadata metadata, String version) {
        List<String> affected = affectedVersions(metadata);
        return affected.contains(ALL_VERSIONS) || (version != null && affected.contains(version));
    }

    private static List<String> affectedVersions(Metadata metadata) {
        if (metadata == null || metadata.getAffectedVersions() == null) {
            return Collections.emptyList();
        }
        return metadata.getAffectedVersions().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toList());
    }

    private static String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public State getState() {
        return state;
    }

    public String getVersion() {
        return version;
    }

    public boolean isBlocking() {
        return state.isBlocking();
    }

    public boolean isWarning() {
        return state.isBlocking() || state == State.UNVERIFIED;
    }

    public boolean isAffected(String pluginVersion) {
        return metadata != null && STATUS_BROKEN.equals(normalize(metadata.getStatus())) && affects(metadata, pluginVersion);
    }

    public String getLabel() {
        if (state == State.INCOMPATIBLE && requiredClientVersion != null) {
            return "Requires client " + requiredClientVersion;
        }
        if (state == State.UNVERIFIED && metadata != null && STATUS_BROKEN.equals(normalize(metadata.getStatus()))) {
            return "Reported broken, versions not specified";
        }
        return state.getLabel();
    }

    public String getTrackingUrl() {
        String url = metadata == null ? null : trimToNull(metadata.getTrackingUrl());
        if (url == null) {
            return null;
        }
        return url.toLowerCase(Locale.ROOT).startsWith("https://") ? url : null;
    }

    public String getReason() {
        return metadata == null ? null : trimToNull(metadata.getReason());
    }

    public List<String> getDetails(String currentClientVersion) {
        List<String> lines = new ArrayList<>();
        lines.add("Status: " + getLabel());
        lines.add("Plugin version: " + valueOrUnknown(version));

        if (state == State.INCOMPATIBLE) {
            lines.add("Required client: " + valueOrUnknown(requiredClientVersion));
            lines.add("Current client: " + valueOrUnknown(currentClientVersion));
        }

        if (metadata == null) {
            lines.add(state == State.DISABLED
                    ? "Reason: not provided"
                    : "No health report has been published for this plugin.");
            return lines;
        }

        lines.add("Reason: " + valueOr(metadata.getReason(), "not provided"));
        List<String> affected = affectedVersions(metadata);
        if (STATUS_BROKEN.equals(normalize(metadata.getStatus())) || !affected.isEmpty()) {
            lines.add("Affected versions: " + (affected.isEmpty()
                    ? "not specified"
                    : affected.contains(ALL_VERSIONS) ? "all versions" : String.join(", ", affected)));
        }
        lines.add("Tracking: " + valueOr(getTrackingUrl(), "none"));
        lines.add("Last verified: plugin " + valueOrUnknown(metadata.getLastVerifiedVersion())
                + " on client " + valueOrUnknown(metadata.getLastVerifiedClientVersion()));
        if (trimToNull(metadata.getUpdatedAt()) != null) {
            lines.add("Updated: " + metadata.getUpdatedAt().trim());
        }
        return lines;
    }

    private static String valueOrUnknown(String value) {
        return valueOr(value, "unknown");
    }

    private static String valueOr(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    public static final class LenientAdapterFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Metadata.class) {
                return null;
            }
            TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
            TypeAdapter<Metadata> delegate = gson.getAdapter(Metadata.class);
            return (TypeAdapter<T>) new TypeAdapter<Metadata>() {
                @Override
                public void write(JsonWriter out, Metadata value) throws IOException {
                    delegate.write(out, value);
                }

                @Override
                public Metadata read(JsonReader in) throws IOException {
                    JsonElement element = elementAdapter.read(in);
                    if (element == null || !element.isJsonObject()) {
                        return null;
                    }
                    try {
                        return delegate.fromJsonTree(element);
                    } catch (JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
                        return null;
                    }
                }
            };
        }
    }
}
