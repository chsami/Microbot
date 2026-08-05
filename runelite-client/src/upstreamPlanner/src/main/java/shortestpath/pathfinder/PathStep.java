package shortestpath.pathfinder;

import lombok.Getter;
import shortestpath.transport.Transport;

@Getter
public final class PathStep
{
	private final int packedPosition;
	private final boolean bankVisited;
	private final Transport transport;

	public PathStep(int packedPosition, boolean bankVisited)
	{
		this(packedPosition, bankVisited, null);
	}

	public PathStep(int packedPosition, boolean bankVisited, Transport transport)
	{
		this.packedPosition = packedPosition;
		this.bankVisited = bankVisited;
		this.transport = transport;
	}
}
