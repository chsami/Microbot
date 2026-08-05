package shortestpath.transport.parser;

/**
 * Interface for parsing string field values into typed objects.
 * Used by TransportRecord to parse TSV field values.
 *
 * @param <T> The type of object produced by this parser
 */
public interface FieldParser<T>
{
	/**
	 * Parses a string value into the target type.
	 *
	 * @param value The string value to parse, may be null or empty
	 * @return The parsed value
	 */
	T parse(String value);
}
