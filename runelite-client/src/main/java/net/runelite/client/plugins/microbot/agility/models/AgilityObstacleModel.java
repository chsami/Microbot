package net.runelite.client.plugins.microbot.agility.models;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class AgilityObstacleModel
{
	@Getter
	int objectID;

	@Getter
	int requiredX = -1;
	@Getter
	int requiredY = -1;
	@Getter
	Operation operationX = Operation.GREATER;
	@Getter
	Operation operationY = Operation.GREATER;

	public AgilityObstacleModel(int objectID)
	{
		this.objectID = objectID;
	}

	public AgilityObstacleModel(int objectID, int x, int y, Operation operationX, Operation operationY)
	{
		this.objectID = objectID;
		this.requiredX = x;
		this.requiredY = y;
		this.operationX = operationX;
		this.operationY = operationY;
	}
}
