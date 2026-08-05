package shortestpath.transport.parser;

import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/**
 * Parses world point coordinates from TSV field values.
 *
 * <p>
 * Format: {@code x y plane} (space-separated)
 * </p>
 * <p>
 * Empty values are treated as location permutations (for fairy rings, etc.)
 * </p>
 */
public class WorldPointParser implements FieldParser<Integer>
{
	private static final String DELIM_SPACE = " ";

	@Override
	public Integer parse(String value)
	{
		if (value == null || value.isEmpty())
		{
			return Transport.LOCATION_PERMUTATION;
		}
		String[] parts = value.split(DELIM_SPACE);
		return parts.length == 3 ? WorldPointUtil.packWorldPoint(
			Integer.parseInt(parts[0]),
			Integer.parseInt(parts[1]),
			Integer.parseInt(parts[2])) : Transport.LOCATION_PERMUTATION;
	}
}
