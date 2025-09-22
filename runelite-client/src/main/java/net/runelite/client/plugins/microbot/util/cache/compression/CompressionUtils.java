package net.runelite.client.plugins.microbot.util.cache.compression;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compression utilities for cache data to enable efficient cloud sync.
 * Uses GZip compression with Base64 encoding for profile storage compatibility.
 *
 * This enables optional cloud sync by compressing cache JSON data before storing
 * in RuneLite profiles, significantly reducing the profile size impact.
 */
@Slf4j
public class CompressionUtils {

    // Maximum reasonable size for compressed cache data in profiles (64KB)
    private static final int MAX_COMPRESSED_SIZE = 64 * 1024;

    /**
     * Compresses a JSON string using GZip and encodes it as Base64 for profile storage.
     *
     * @param jsonData The JSON string to compress
     * @return CompressedData object containing the compressed data and metadata
     */
    public static CompressedData compressForProfile(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            log.warn("Cannot compress null or empty JSON data");
            return new CompressedData(null, 0, 0, 0.0);
        }

        try {
            long startTime = System.nanoTime();

            // convert string to bytes
            byte[] originalBytes = jsonData.getBytes(StandardCharsets.UTF_8);
            int originalSize = originalBytes.length;

            // compress using GZip
            byte[] compressedBytes = compress(originalBytes);
            int compressedSize = compressedBytes.length;

            // encode as base64 for profile storage (actual payload size)
            String encodedData = Base64.getEncoder().encodeToString(compressedBytes);
            int encodedSize = encodedData.length();

            // enforce profile-storage cap against encoded length
            if (encodedSize > MAX_COMPRESSED_SIZE) {
                log.warn("Encoded cache data too large for profile storage: {} bytes (max: {} bytes)",
                        encodedSize, MAX_COMPRESSED_SIZE);
                return new CompressedData(null, originalSize, compressedSize,
                        (double) compressedSize / originalSize);
            }

            long endTime = System.nanoTime();
            double compressionTimeMs = (endTime - startTime) / 1_000_000.0;
            double compressionRatio = (double) compressedSize / originalSize;

            log.debug("Compressed cache data: {} bytes → {} bytes (ratio: {:.2f}, time: {:.2f}ms)",
                    originalSize, compressedSize, compressionRatio, compressionTimeMs);

            return new CompressedData(encodedData, originalSize, compressedSize, compressionRatio);

        } catch (IOException e) {
            log.error("Failed to compress cache data for profile storage", e);
            return new CompressedData(null, jsonData.getBytes(StandardCharsets.UTF_8).length, 0, 0.0);
        }
    }

    /**
     * Decompresses Base64-encoded GZip data back to a JSON string.
     *
     * @param compressedData The Base64-encoded compressed data from profile
     * @return Original JSON string or null if decompression fails
     */
    public static String decompressFromProfile(String compressedData) {
        if (compressedData == null || compressedData.trim().isEmpty()) {
            log.warn("Cannot decompress null or empty compressed data");
            return null;
        }

        try {
            long startTime = System.nanoTime();

            // decode from base64
            byte[] compressedBytes = Base64.getDecoder().decode(compressedData);

            // decompress using GZip
            byte[] originalBytes = decompress(compressedBytes, compressedBytes.length);

            // convert back to string
            String jsonData = new String(originalBytes, StandardCharsets.UTF_8);

            long endTime = System.nanoTime();
            double decompressionTimeMs = (endTime - startTime) / 1_000_000.0;

            log.debug("Decompressed cache data: {} bytes → {} bytes (time: {:.2f}ms)",
                    compressedBytes.length, originalBytes.length, decompressionTimeMs);

            return jsonData;

        } catch (Exception e) {
            log.error("Failed to decompress cache data from profile storage", e);
            return null;
        }
    }

    /**
     * Estimates the compressed size of JSON data without actually compressing it.
     * Uses a heuristic based on typical JSON compression ratios.
     *
     * @param jsonData The JSON string to estimate
     * @return Estimated compressed size in bytes
     */
    public static int estimateCompressedSize(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return 0;
        }

        int originalSize = jsonData.getBytes(StandardCharsets.UTF_8).length;

        // typical JSON compression ratio is around 0.15-0.3 for cache data
        // use conservative estimate of 0.25
        return (int) (originalSize * 0.25);
    }

    /**
     * Checks if the data would be suitable for profile storage based on size.
     *
     * @param jsonData The JSON string to check
     * @return true if the data would likely compress to a reasonable size
     */
    public static boolean isSuitableForProfileStorage(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return false;
        }

        int estimatedSize = estimateCompressedSize(jsonData);
        return estimatedSize <= MAX_COMPRESSED_SIZE;
    }

    /**
     * Gets the maximum allowed compressed size for profile storage.
     *
     * @return Maximum compressed size in bytes
     */
    public static int getMaxCompressedSize() {
        return MAX_COMPRESSED_SIZE;
    }

    /**
     * Data class to hold compression results and metadata.
     */
    public static class CompressedData {
        public final String compressedData; // Base64-encoded compressed data (null if compression failed/unsuitable)
        public final int originalSize;       // Original size in bytes
        public final int compressedSize;     // Compressed size in bytes
        public final double compressionRatio; // Compression ratio (compressedSize / originalSize)

        public CompressedData(String compressedData, int originalSize, int compressedSize, double compressionRatio) {
            this.compressedData = compressedData;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.compressionRatio = compressionRatio;
        }

        /**
         * Returns true if compression was successful and data is suitable for profile storage.
         */
        public boolean isValid() {
            return compressedData != null && compressedData.length() <= MAX_COMPRESSED_SIZE;
        }

        /**
         * Gets the space savings as a percentage.
         */
        public double getSpaceSavingsPercent() {
            if (originalSize == 0) return 0.0;
            return (1.0 - compressionRatio) * 100.0;
        }

        /**
         * Gets a human-readable description of the compression results.
         */
        public String getCompressionSummary() {
            if (compressedData == null) {
                return "Compression failed";
            }
            return String.format("%d bytes → %d bytes (%.1f%% savings)",
                    originalSize, compressedSize, getSpaceSavingsPercent());
        }
    }

    /**
     * Compresses byte array using GZip compression.
     * Implementation from RuneLite cache module.
     *
     * @param bytes The bytes to compress
     * @return Compressed byte array
     * @throws IOException if compression fails
     */
    private static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        try (InputStream is = new ByteArrayInputStream(bytes);
             OutputStream os = new GZIPOutputStream(bout)) {
            is.transferTo(os);
        }

        byte[] out = bout.toByteArray();
        out[9] = 0; // JDK-8244706: set OS to 0
        return out;
    }

    /**
     * Decompresses GZip compressed byte array.
     * Implementation from RuneLite cache module.
     *
     * @param bytes The compressed bytes
     * @param len Length of compressed data
     * @return Decompressed byte array
     * @throws IOException if decompression fails
     */
    private static byte[] decompress(byte[] bytes, int len) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(bytes, 0, len))) {
            is.transferTo(os);
        }

        return os.toByteArray();
    }
}