package shortestpath.pathfinder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static net.runelite.api.Constants.REGION_SIZE;

import shortestpath.ShortestPathPlugin;
import shortestpath.Util;

public class SplitFlagMap
{
	private static final int FLAG_COUNT = 2;
	private static final int BITS_PER_PLANE = REGION_SIZE * REGION_SIZE * FLAG_COUNT;
	private static final int WORDS_PER_PLANE = BITS_PER_PLANE / Long.SIZE;
	private static final int REGION_MASK = REGION_SIZE - 1;

	@Getter
	private static RegionExtent regionExtents;

	private final byte[] regionMapPlaneCounts;
	// Every region's collision bits are packed into one shared word array instead of a separate
	// FlagMap + BitSet + long[] per region. regionWordOffset gives each region's start word, or -1
	// when the region has no collision data (issue #491).
	private final long[] flags;
	private final int[] regionWordOffset;
	private final int widthInclusive;

	public SplitFlagMap(Map<Integer, byte[]> compressedRegions)
	{
		widthInclusive = regionExtents.getWidth() + 1;
		final int heightInclusive = regionExtents.getHeight() + 1;
		final int regionCount = widthInclusive * heightInclusive;
		regionMapPlaneCounts = new byte[regionCount];
		regionWordOffset = new int[regionCount];
		Arrays.fill(regionWordOffset, -1);

		// First pass: decode each region and reserve it a slice of the shared word array.
		final Map<Integer, long[]> regionWords = new HashMap<>(compressedRegions.size());
		int totalWords = 0;
		for (Map.Entry<Integer, byte[]> entry : compressedRegions.entrySet())
		{
			final int pos = entry.getKey();
			final int index = getIndex(unpackX(pos), unpackY(pos));
			final BitSet bits = BitSet.valueOf(entry.getValue());
			// Same plane-count derivation the old FlagMap used.
			final int planeCount = (bits.size() + BITS_PER_PLANE - 1) / BITS_PER_PLANE;
			regionMapPlaneCounts[index] = (byte) planeCount;
			regionWordOffset[index] = totalWords;
			regionWords.put(index, bits.toLongArray());
			totalWords += planeCount * WORDS_PER_PLANE;
		}

		// Second pass: copy each region's words into its reserved slice (trailing zero words from
		// BitSet.toLongArray are left as the zero-filled remainder of the slice).
		flags = new long[totalWords];
		for (Map.Entry<Integer, long[]> entry : regionWords.entrySet())
		{
			final long[] words = entry.getValue();
			System.arraycopy(words, 0, flags, regionWordOffset[entry.getKey()], words.length);
		}
	}

	public static int unpackX(int position)
	{
		return position & 0xFFFF;
	}

	public static int unpackY(int position)
	{
		return (position >> 16) & 0xFFFF;
	}

	public static int packPosition(int x, int y)
	{
		return (x & 0xFFFF) | ((y & 0xFFFF) << 16);
	}

	public static SplitFlagMap fromResources()
	{
		Map<Integer, byte[]> compressedRegions = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(Objects.requireNonNull(ShortestPathPlugin.class.getResourceAsStream("/collision-map.zip"))))
		{
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxX = 0;
			int maxY = 0;

			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null)
			{
				String[] n = entry.getName().split("_");
				final int x = Integer.parseInt(n[0]);
				final int y = Integer.parseInt(n[1]);
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);

				compressedRegions.put(SplitFlagMap.packPosition(x, y), Util.readAllBytes(in));
			}

			regionExtents = new RegionExtent(minX, minY, maxX, maxY);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}

		return new SplitFlagMap(compressedRegions);
	}

	public byte getRegionPlaneCounts(int index)
	{
		return regionMapPlaneCounts[index];
	}

	public boolean get(int x, int y, int z, int flag)
	{
		final int index = getIndex(x / REGION_SIZE, y / REGION_SIZE);
		if (index < 0 || index >= regionWordOffset.length)
		{
			return false;
		}

		final int wordOffset = regionWordOffset[index];
		if (wordOffset < 0 || z < 0 || z >= regionMapPlaneCounts[index])
		{
			return false;
		}

		// SplitFlagMap routes (x, y) to the region that contains it, so the in-region coordinates
		// are simply the low REGION_SIZE bits; this matches the old FlagMap.index arithmetic.
		final int localBit = (z * REGION_SIZE * REGION_SIZE
			+ (y & REGION_MASK) * REGION_SIZE
			+ (x & REGION_MASK)) * FLAG_COUNT + flag;
		return (flags[wordOffset + (localBit >> 6)] >>> (localBit & 63) & 1L) != 0L;
	}

	private int getIndex(int regionX, int regionY)
	{
		return (regionX - regionExtents.getMinX()) + (regionY - regionExtents.getMinY()) * widthInclusive;
	}

	@RequiredArgsConstructor
	@Getter
	public static class RegionExtent
	{
		public final int minX, minY, maxX, maxY;

		public int getWidth()
		{
			return maxX - minX;
		}

		public int getHeight()
		{
			return maxY - minY;
		}
	}
}
