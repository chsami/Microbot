package shortestpath.transport.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Parses TSV file contents into TransportRecord objects.
 *
 * <p>
 * TSV files should have a header line (optionally starting with #)
 * followed by data lines. Empty lines and lines starting with # are ignored.
 * </p>
 */
public class TsvParser
{
	private static final String DELIM_COLUMN = "\t";
	private static final String PREFIX_COMMENT = "#";

	/**
	 * Parses TSV content into a list of TransportRecords.
	 * The first line must be a header line (optionally starting with #).
	 */
	public List<TransportRecord> parse(String contents)
	{
		List<TransportRecord> records = new ArrayList<>();
		Scanner scanner = new Scanner(contents);

		if (!scanner.hasNextLine())
		{
			scanner.close();
			return records;
		}

		// Parse header line
		String[] headers = parseHeaderLine(scanner.nextLine());

		// Parse data lines
		while (scanner.hasNextLine())
		{
			String line = scanner.nextLine();

			if (line.startsWith(PREFIX_COMMENT) || line.isBlank())
			{
				continue;
			}

			TransportRecord record = parseLine(line, headers);
			records.add(record);
		}

		scanner.close();
		return records;
	}

	/**
	 * Parses the header line, stripping the comment prefix if present.
	 */
	private String[] parseHeaderLine(String headerLine)
	{
		String normalized = headerLine;
		if (normalized.startsWith(PREFIX_COMMENT + " "))
		{
			normalized = normalized.substring(2);
		}
		else if (normalized.startsWith(PREFIX_COMMENT))
		{
			normalized = normalized.substring(1);
		}
		return normalized.split(DELIM_COLUMN);
	}

	/**
	 * Parses a single data line into a TransportRecord.
	 */
	private TransportRecord parseLine(String line, String[] headers)
	{
		// Use -1 limit to preserve trailing empty strings
		String[] fields = line.split(DELIM_COLUMN, -1);
		Map<String, String> fieldMap = new HashMap<>();

		for (int i = 0; i < headers.length; i++)
		{
			if (i < fields.length)
			{
				fieldMap.put(headers[i], fields[i]);
			}
		}

		return new TransportRecord(fieldMap);
	}
}
