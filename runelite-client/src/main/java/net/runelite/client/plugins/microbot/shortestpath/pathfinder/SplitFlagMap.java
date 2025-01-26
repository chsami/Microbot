package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.Util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static net.runelite.api.Constants.REGION_SIZE;

public class SplitFlagMap {
    @Getter
    private static RegionExtent regionExtents;

    @Getter
    private final byte[] regionMapPlaneCounts;
    // Size is automatically chosen based on the max extents of the collision data
    private final FlagMap[] regionMaps;
    private final int widthInclusive;

    public SplitFlagMap(Map<Integer, byte[]> compressedRegions) {
        widthInclusive = regionExtents.getWidth() + 1;
        final int heightInclusive = regionExtents.getHeight() + 1;
        regionMaps = new FlagMap[widthInclusive * heightInclusive];
        regionMapPlaneCounts = new byte[regionMaps.length];

        for (Map.Entry<Integer, byte[]> entry : compressedRegions.entrySet()) {
            final int pos = entry.getKey();
            final int x = unpackX(pos);
            final int y = unpackY(pos);
            final int index = getIndex(x, y);
            FlagMap flagMap = new FlagMap(x * REGION_SIZE, y * REGION_SIZE, entry.getValue());
            regionMaps[index] = flagMap;
            regionMapPlaneCounts[index] = flagMap.getPlaneCount();
        }
    }

    public boolean get(int x, int y, int z, int flag) {
        final int index = getIndex(x / REGION_SIZE, y / REGION_SIZE);
        if (index < 0 || index >= regionMaps.length || regionMaps[index] == null) {
            return false;
        }

   /*     try {
            if (Rs2Player.getWorldLocation().getRegionID() == 14162) { //toa puzzle room
                WorldPoint globalWorldPoint = Rs2WorldPoint.convertInstancedWorldPoint(new WorldPoint(x, y, z));
                if (globalWorldPoint == null) {
                    Microbot.log("Something went wrong.");
                } else {
                    TileObject go = Rs2GameObject.findGroundObjectByLocation(globalWorldPoint);
                    if (go != null && go.getId() == 45340) {
                        //System.out.println("found a tile " + globalWorldPoint);
                        return false;
                    }
                }
            }
        } catch(Exception ex) {
            Microbot.log("oops something went wrong");
        }*/



        return regionMaps[index].get(x, y, z, flag);
    }

    private int getIndex(int regionX, int regionY) {
        return (regionX - regionExtents.getMinX()) + (regionY - regionExtents.getMinY()) * widthInclusive;
    }

    public static int unpackX(int position) {
        return position & 0xFFFF;
    }

    public static int unpackY(int position) {
        return (position >> 16) & 0xFFFF;
    }

    public static int packPosition(int x, int y) {
        return (x & 0xFFFF) | ((y & 0xFFFF) << 16);
    }

    public static SplitFlagMap fromResources() {
        Map<Integer, byte[]> compressedRegions = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(ShortestPathPlugin.class.getResourceAsStream("collision-map.zip"))) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = 0;
            int maxY = 0;

            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new SplitFlagMap(compressedRegions);
    }

    @RequiredArgsConstructor
    @Getter
    public static class RegionExtent {
        public final int minX, minY, maxX, maxY;

        public int getWidth() {
            return maxX - minX;
        }

        public int getHeight() {
            return maxY - minY;
        }
    }
}
