package shortestpath;

import shortestpath.transport.TransportType;

/**
 * Resource/config compatibility anchor for the vendored planner core.
 *
 * <p>This is intentionally not a RuneLite plugin. Microbot owns the only plugin lifecycle and projects
 * resolved state into the upstream engine through its adapter.</p>
 */
public final class ShortestPathPlugin
{
	public static final String CONFIG_GROUP = "shortestpath";
	private static final int POH_MIN_X = 1856;
	private static final int POH_MAX_X = 2047;
	private static final int POH_MIN_Y = 5696;
	private static final int POH_MAX_Y = 5767;

	private ShortestPathPlugin()
	{
	}

	public static boolean isInsidePoh(int x, int y)
	{
		return x >= POH_MIN_X && x <= POH_MAX_X && y >= POH_MIN_Y && y <= POH_MAX_Y;
	}

	public static boolean override(String key, boolean value)
	{
		return value;
	}

	public static int override(String key, int value)
	{
		return value;
	}

	public static boolean override(TransportType type, boolean value)
	{
		return value;
	}

	public static int override(TransportType type, int value)
	{
		return value;
	}

	public static TeleportationItem override(String key, TeleportationItem value)
	{
		return value;
	}

	public static JewelleryBoxTier override(String key, JewelleryBoxTier value)
	{
		return value;
	}
}
