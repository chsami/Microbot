package shortestpath.transport.parser;

import java.util.Map;

import lombok.Getter;

/**
 * Represents a variable-based requirement for a transport.
 * This can be either a varbit or a varplayer requirement.
 *
 * <p>
 * Consolidates the previously separate TransportVarbit and TransportVarPlayer
 * classes which had identical logic.
 * </p>
 */
@Getter
public class VarRequirement
{

	private final VarType varType;
	private final int id;
	private final int value;
	private final VarCheckType checkType;

	public VarRequirement(VarType varType, int id, int value, VarCheckType checkType)
	{
		this.varType = varType;
		this.id = id;
		this.value = value;
		this.checkType = checkType;
	}

	/**
	 * Creates a varbit requirement.
	 */
	public static VarRequirement varbit(int id, int value, VarCheckType checkType)
	{
		return new VarRequirement(VarType.VARBIT, id, value, checkType);
	}

	/**
	 * Creates a varplayer requirement.
	 */
	public static VarRequirement varPlayer(int id, int value, VarCheckType checkType)
	{
		return new VarRequirement(VarType.VARPLAYER, id, value, checkType);
	}

	/**
	 * Checks if this requirement is satisfied given the current variable values.
	 *
	 * @param values A map of variable IDs to their current values
	 * @return true if the requirement is satisfied
	 */
	public boolean check(Map<Integer, Integer> values)
	{
		Integer currentValue = values.get(id);
		if (currentValue == null)
		{
			return false;
		}
		return checkValue(currentValue);
	}

	/**
	 * Same logic as {@link #check(Map)} but with the variable value already resolved (e.g. from the client).
	 */
	public boolean checkValue(int currentValue)
	{
		switch (checkType)
		{
			case EQUAL:
				return currentValue == value;
			case GREATER:
				return currentValue > value;
			case SMALLER:
				return currentValue < value;
			case BIT_SET:
				return (currentValue & value) > 0;
			case COOLDOWN_MINUTES:
				return ((System.currentTimeMillis() / 60000) - currentValue) > value;
			default:
				return false;
		}
	}

	public boolean isVarbit()
	{
		return varType == VarType.VARBIT;
	}

	public boolean isVarPlayer()
	{
		return varType == VarType.VARPLAYER;
	}

	@Override
	public int hashCode()
	{
		int result = varType.hashCode();
		result = 31 * result + id;
		result = 31 * result + value;
		result = 31 * result + checkType.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}
		VarRequirement that = (VarRequirement) o;
		return id == that.id && value == that.value && varType == that.varType && checkType == that.checkType;
	}

	@Override
	public String toString()
	{
		return varType + "[" + id + " " + checkType.getCode() + " " + value + "]";
	}

	/**
	 * The type of variable this requirement checks.
	 */
	public enum VarType
	{
		VARBIT,
		VARPLAYER
	}
}
