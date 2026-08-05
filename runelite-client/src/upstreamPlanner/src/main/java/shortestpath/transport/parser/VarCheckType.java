package shortestpath.transport.parser;

import lombok.Getter;

/**
 * The type of comparison to perform when checking a variable requirement.
 */
@Getter
public enum VarCheckType
{
	BIT_SET("&"),
	COOLDOWN_MINUTES("@"),
	EQUAL("="),
	GREATER(">"),
	SMALLER("<");

	private final String code;

	VarCheckType(String code)
	{
		this.code = code;
	}
}
