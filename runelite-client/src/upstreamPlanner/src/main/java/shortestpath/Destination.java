package shortestpath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.transport.parser.FieldParser;
import shortestpath.transport.parser.QuestParser;
import shortestpath.transport.parser.SkillRequirementParser;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.parser.VarRequirementParser;

/**
 * Utility loader for destination coordinate sets grouped by feature category.
 * <p>
 * Destination data is stored in tab-separated value (TSV) resources under
 * {@code /destinations/**}. Each file's
 * first non-empty line is treated as a header (leading comment markers
 * {@code #} or {@code # } are stripped) and
 * defines column names. Rows beginning with {@code #} or blank lines are
 * ignored. Columns named "Destination" are
 * parsed as space-delimited triples {@code (x y plane)} that are packed into
 * single {@code int} values via
 * {@link WorldPointUtil#packWorldPoint(int, int, int)}.
 */
@Slf4j
public class Destination
{
	private static final FieldParser<int[]> SKILL_PARSER = new SkillRequirementParser();
	private static final FieldParser<Set<Quest>> QUEST_PARSER = new QuestParser();
	private static final FieldParser<Set<VarRequirement>> VARBIT_PARSER = VarRequirementParser.forVarbits();
	private static final FieldParser<Set<VarRequirement>> VARPLAYER_PARSER = VarRequirementParser.forVarPlayers();
	private static final String DELIM_COLUMN = "\t";
	private static final String PREFIX_COMMENT = "#";
	private static final String FILE_EXTENSION = ".";
	private static final String DELIM_PATH = "/";
	private static final String DELIM = " ";

	/**
	 * Parses a TSV resource of destination coordinates and merges them into the provided map.
	 * The key in the destination map is derived from the directory component immediately preceding the file
	 * name (e.g., {@code /destinations/game_features/bank.tsv -> bank}).
	 *
	 * @param destinations accumulator map of category key to a set of packed world point integers.
	 * @param path         classpath resource path to a TSV file beginning with a header line.
	 * @throws RuntimeException wrapping {@link IOException} if the resource cannot be read.
	 */
	private static void addDestinations(Map<String, Set<Integer>> destinations, String path)
	{
		try
		{
			String s = new String(Util.readAllBytes(Objects.requireNonNull(ShortestPathPlugin.class.getResourceAsStream(path))),
				StandardCharsets.UTF_8);
			Scanner scanner = new Scanner(s);

			// Header line is the first line in the file and will start with either '#' or
			// '# '
			String[] headers = parse_header_line(scanner);

			String[] parts = path.replace(FILE_EXTENSION, DELIM_PATH).split(DELIM_PATH);
			String entry = parts[parts.length - 2];

			while (scanner.hasNextLine())
			{
				String line = scanner.nextLine();

				if (line.startsWith(PREFIX_COMMENT) || line.isBlank())
				{
					continue;
				}

				String[] fields = line.split(DELIM_COLUMN);
				Map<String, List<String>> fieldMap = new HashMap<>();
				for (int i = 0; i < headers.length; i++)
				{
					if (i < fields.length)
					{
						List<String> values = fieldMap.getOrDefault(headers[i], new ArrayList<>());
						values.add(fields[i]);
						fieldMap.put(headers[i], values);
					}
				}
				for (String field : fieldMap.keySet())
				{
					if ("Destination".equals(field))
					{
						for (String value : fieldMap.get(field))
						{
							try
							{
								String[] destinationArray = value.split(DELIM);
								if (destinationArray.length == 3)
								{
									Set<Integer> entryDestinations = destinations.getOrDefault(entry, new HashSet<>());
									entryDestinations.add(WorldPointUtil.packWorldPoint(
										Integer.parseInt(destinationArray[0]),
										Integer.parseInt(destinationArray[1]),
										Integer.parseInt(destinationArray[2])));
									destinations.put(entry, entryDestinations);
								}
							}
							catch (NumberFormatException e)
							{
								log.error("Invalid destination coordinate", e);
							}
						}
					}
				}
			}
			scanner.close();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static String[] parse_header_line(Scanner scanner)
	{
		String headerLine = scanner.nextLine();
		headerLine = headerLine.startsWith(PREFIX_COMMENT + " ")
			? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT)
			: headerLine;
		headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
		return headerLine.split(DELIM_COLUMN);
	}

	/**
	 * Loads a predefined subset of destination categories from bundled TSV resources.
	 *
	 * @return a map from destination category key to a set of packed world point integers.
	 */
	public static Map<String, Set<Integer>> loadAllFromResources()
	{
		Map<String, Set<Integer>> destinations = new HashMap<>(10);
		addDestinations(destinations, "/destinations/game_features/altar.tsv");
		addDestinations(destinations, "/destinations/game_features/bank.tsv");
		addDestinations(destinations, "/destinations/training/anvil.tsv");
		addDestinations(destinations, "/destinations/shopping/apothecary.tsv");
		return destinations;
	}

	/**
	 * Per-packed-point requirements for bank tiles, parsed from {@code bank.tsv} columns
	 * Skills, Quests, Varbits, and VarPlayers (same TSV format as transports).
	 * Tiles with no requirement rows behave as {@link DestinationRequirements#EMPTY}.
	 */
	public static Map<Integer, DestinationRequirements> loadBankRequirementsFromResources()
	{
		Map<Integer, DestinationRequirements> requirements = new HashMap<>();
		final String path = "/destinations/game_features/bank.tsv";
		final String DELIM_COLUMN = "\t";
		final String PREFIX_COMMENT = "#";
		final String DELIM = " ";

		try
		{
			String s = new String(Util.readAllBytes(Objects.requireNonNull(ShortestPathPlugin.class.getResourceAsStream(path))), StandardCharsets.UTF_8);
			try (Scanner scanner = new Scanner(s))
			{
				String[] headers = parse_header_line(scanner);
				for (int i = 0; i < headers.length; i++)
				{
					headers[i] = headers[i].trim();
				}

				int destCol = indexOf(headers, "Destination");
				int skillsCol = indexOf(headers, "Skills");
				int questsCol = indexOf(headers, "Quests");
				int varbitsCol = indexOf(headers, "Varbits");
				int varPlayersCol = indexOf(headers, "VarPlayers");
				if (destCol < 0)
				{
					return requirements;
				}

				while (scanner.hasNextLine())
				{
					String line = scanner.nextLine();
					if (line.startsWith(PREFIX_COMMENT) || line.isBlank())
					{
						continue;
					}
					String[] fields = line.split(DELIM_COLUMN, -1);
					if (fields.length <= destCol)
					{
						continue;
					}
					String destField = fields[destCol].trim();
					String[] destinationArray = destField.split(DELIM);
					if (destinationArray.length != 3)
					{
						continue;
					}
					int packed;
					try
					{
						packed = WorldPointUtil.packWorldPoint(
							Integer.parseInt(destinationArray[0]),
							Integer.parseInt(destinationArray[1]),
							Integer.parseInt(destinationArray[2]));
					}
					catch (NumberFormatException e)
					{
						log.error("Invalid destination coordinate in bank.tsv", e);
						continue;
					}

					String skillsStr = fieldAt(fields, skillsCol);
					String questsStr = fieldAt(fields, questsCol);
					String varbitsStr = fieldAt(fields, varbitsCol);
					String varPlayersStr = fieldAt(fields, varPlayersCol);

					int[] skillLevels = skillsCol >= 0 ? SKILL_PARSER.parse(blankToNull(skillsStr)) : new int[Skill.values().length + 3];
					Set<Quest> quests = questsCol >= 0 ? QUEST_PARSER.parse(blankToNull(questsStr)) : Set.of();
					Set<VarRequirement> varbits = varbitsCol >= 0 ? VARBIT_PARSER.parse(blankToNull(varbitsStr)) : Set.of();
					Set<VarRequirement> varPlayers = varPlayersCol >= 0 ? VARPLAYER_PARSER.parse(blankToNull(varPlayersStr)) : Set.of();

					DestinationRequirements rowReq = new DestinationRequirements(skillLevels, quests, varbits, varPlayers);
					requirements.merge(packed, rowReq, DestinationRequirements::merge);
				}
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		return requirements;
	}

	private static int indexOf(String[] headers, String name)
	{
		for (int i = 0; i < headers.length; i++)
		{
			if (name.equals(headers[i]))
			{
				return i;
			}
		}
		return -1;
	}

	private static String fieldAt(String[] fields, int col)
	{
		if (col < 0 || col >= fields.length)
		{
			return "";
		}
		return fields[col];
	}

	private static String blankToNull(String s)
	{
		return s == null || s.isBlank() ? null : s;
	}
}
