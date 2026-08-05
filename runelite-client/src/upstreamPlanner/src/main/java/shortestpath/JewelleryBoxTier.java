package shortestpath;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JewelleryBoxTier
{
	NONE("None"),
	BASIC("Basic"),
	FANCY("Fancy"),
	ORNATE("Ornate"),
	;

	private final String type;

	public static JewelleryBoxTier fromType(String type)
	{
		for (JewelleryBoxTier tier : values())
		{
			if (tier.type.equals(type))
			{
				return tier;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return type;
	}
}
