package shortestpath.pathfinder;

public enum AbstractNodeKind
{
	// These four abstract teleport states mirror the wilderness buckets that change
	// which teleports are legal.
	GLOBAL_TELEPORTS_OVER_30,
	GLOBAL_TELEPORTS_OVER_20,
	GLOBAL_TELEPORTS_OVER_0,
	GLOBAL_TELEPORTS_NORMAL;

	public static AbstractNodeKind fromWildernessLevel(int wildernessLevel)
	{
		if (wildernessLevel > 30)
		{
			return GLOBAL_TELEPORTS_OVER_30;
		}
		if (wildernessLevel > 20)
		{
			return GLOBAL_TELEPORTS_OVER_20;
		}
		if (wildernessLevel > 0)
		{
			return GLOBAL_TELEPORTS_OVER_0;
		}
		return GLOBAL_TELEPORTS_NORMAL;
	}

	public int maxWildernessLevel()
	{
		switch (this)
		{
			case GLOBAL_TELEPORTS_OVER_30:
				return 31;
			case GLOBAL_TELEPORTS_OVER_20:
				return 30;
			case GLOBAL_TELEPORTS_OVER_0:
				return 20;
			case GLOBAL_TELEPORTS_NORMAL:
			default:
				return 0;
		}
	}
}
