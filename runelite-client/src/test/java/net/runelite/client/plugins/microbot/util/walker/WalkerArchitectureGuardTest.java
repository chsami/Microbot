package net.runelite.client.plugins.microbot.util.walker;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Source-level boundaries for the incremental walker rewrite.
 *
 * <p>These checks intentionally protect ownership rather than package layout. Moving a legacy
 * method into a new class is not decomposition when that class can still replace the active target,
 * restart pathfinding, or publish mutable plugin state. The limits may only move downward as legacy
 * orchestration is deleted.</p>
 */
public class WalkerArchitectureGuardTest
{
	private static final String WALKER_RELATIVE =
		"util/walker/Rs2Walker.java";
	private static final String PATH_API_RELATIVE =
		"util/walker/Rs2PathApi.java";

	/**
	 * Current legacy ceiling. New behaviour belongs in NavigationEngine, not processWalk.
	 *
	 * <p>Rebased from 1628 to 1647 when walker-fix merged in on 2026-08-07. This guard is
	 * PluginTesting's, and the walker it now guards is walker-fix's, whose door work grew processWalk
	 * past the old ceiling. Nineteen lines is the honest cost of that merge, not a licence to keep
	 * growing: the point of the guard is that the next branch added here has to justify itself, and it
	 * still does.
	 *
	 * <p>1647 → 1646 on 2026-08-10 with the {@code String exitReason} → {@link
	 * net.runelite.client.plugins.microbot.util.walker.state.WalkExit} refactor, then 1646 → 1641
	 * when the epilogue's partial-retry accounting moved into
	 * {@link net.runelite.client.plugins.microbot.util.walker.recovery.TailDecision}, then 1641 → 1630
	 * when the per-segment gating moved into
	 * {@link net.runelite.client.plugins.microbot.util.walker.segment.SegmentGate}, then 1630 → 1623
	 * when the blanket post-click stall grace was deleted as redundant. This number may only ever move
	 * DOWN. It was raised once, to absorb a merge; doing that again would turn the guard into a rubber
	 * stamp. A change that needs more lines here needs the behaviour moved out instead — which is what
	 * each of those commits did, and why the number keeps falling.
	 */
	private static final int MAX_LEGACY_PROCESS_WALK_LINES = 1598;

	private static final Pattern NON_CODE = Pattern.compile(
		"(?s)/\\*.*?\\*/|(?m)//[^\\r\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");

	private static final List<String> SHORTEST_PATH_MUTABLE_ACCESS = Arrays.asList(
		"ShortestPathPlugin.getPathfinder(",
		"ShortestPathPlugin.setPathfinder(",
		"ShortestPathPlugin.getPathfinderFuture(",
		"ShortestPathPlugin.setPathfinderFuture(",
		"ShortestPathPlugin.getPathfindingExecutor(",
		"ShortestPathPlugin.setPathfindingExecutor(",
		"ShortestPathPlugin.getPathfinderMutex(",
		"ShortestPathPlugin.getPathfinderConfig(",
		"ShortestPathPlugin.setReachedDistance(",
		"ShortestPathPlugin.setStartPointSet(",
		"ShortestPathPlugin.setLastLocation(",
		"ShortestPathPlugin.getMarker(",
		"ShortestPathPlugin.setMarker(",
		"ShortestPathPlugin.getTransports(",
		"ShortestPathPlugin.exit(",
		"ShortestPathPlugin.pathfinder"
	);

	private static final List<String> FORBIDDEN_HANDLER_LIFECYCLE_ACCESS = Arrays.asList(
		"Rs2Walker.setTarget(",
		"Rs2Walker.clearWalkingRoute(",
		"Rs2Walker.recalculatePath(",
		"Rs2Walker.restartPathfinding(",
		"Rs2WalkerLifecycleRuntime.",
		"Rs2PathApi.setPathfinder(",
		"Rs2PathApi.setPathfinderFuture(",
		"Rs2PathApi.setPathfindingExecutor(",
		"Rs2PathApi.exit("
	);

	@Test
	public void mutableShortestPathPluginStateStaysBehindFacade() throws IOException
	{
		Path microbotRoot = microbotSourceRoot();
		List<String> violations = new ArrayList<>();
		for (Path source : javaSources(microbotRoot))
		{
			String relative = relative(microbotRoot, source);
			if (relative.startsWith("shortestpath/") || PATH_API_RELATIVE.equals(relative))
			{
				continue;
			}
			String code = codeOnly(source);
			collectMatches(relative, code, SHORTEST_PATH_MUTABLE_ACCESS, violations);
		}
		assertNoViolations("Mutable ShortestPathPlugin state must stay behind Rs2PathApi", violations);
	}

	@Test
	public void interactionHandlersCannotOwnRouteLifecycle() throws IOException
	{
		Path microbotRoot = microbotSourceRoot();
		Path walkerRoot = microbotRoot.resolve("util/walker");
		List<String> violations = new ArrayList<>();
		for (String child : Arrays.asList("door", "obstacle", "transport", "interaction"))
		{
			Path root = walkerRoot.resolve(child);
			if (!Files.isDirectory(root))
			{
				continue;
			}
			for (Path source : javaSources(root))
			{
				String relative = relative(microbotRoot, source);
				collectMatches(relative, codeOnly(source), FORBIDDEN_HANDLER_LIFECYCLE_ACCESS, violations);
			}
		}
		assertNoViolations("Interaction handlers report outcomes; NavigationEngine owns lifecycle", violations);
	}

	@Test
	public void legacyProcessWalkCannotGrow() throws IOException
	{
		Path walker = microbotSourceRoot().resolve(WALKER_RELATIVE);
		String source = new String(Files.readAllBytes(walker), StandardCharsets.UTF_8);
		int lines = methodLineCount(source,
			"private static WalkerState processWalk(WorldPoint target, int distance, int partialRetries)");
		assertTrue("Legacy processWalk grew to " + lines + " lines; migrate behaviour into the new engine "
				+ "or document a temporary exception instead of adding another branch",
			lines <= MAX_LEGACY_PROCESS_WALK_LINES);
	}

	private static void collectMatches(String relative, String code, List<String> forbidden,
		List<String> violations)
	{
		for (String token : forbidden)
		{
			if (code.contains(token))
			{
				violations.add(relative + " -> " + token);
			}
		}
	}

	private static void assertNoViolations(String message, List<String> violations)
	{
		if (!violations.isEmpty())
		{
			fail(message + ":\n  " + String.join("\n  ", violations));
		}
	}

	private static int methodLineCount(String source, String signature)
	{
		String code = blankNonCode(source);
		int signatureStart = code.indexOf(signature);
		if (signatureStart < 0)
		{
			throw new AssertionError("Method signature not found: " + signature);
		}
		int bodyStart = code.indexOf('{', signatureStart + signature.length());
		if (bodyStart < 0)
		{
			throw new AssertionError("Method body not found: " + signature);
		}
		int depth = 0;
		for (int i = bodyStart; i < code.length(); i++)
		{
			char c = code.charAt(i);
			if (c == '{')
			{
				depth++;
			}
			else if (c == '}' && --depth == 0)
			{
				return (int) source.substring(signatureStart, i + 1).chars().filter(ch -> ch == '\n').count() + 1;
			}
		}
		throw new AssertionError("Unclosed method body: " + signature);
	}

	private static String codeOnly(Path source) throws IOException
	{
		return blankNonCode(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
	}

	/** Replaces comments and literals with spaces while preserving indices and line numbers. */
	private static String blankNonCode(String source)
	{
		Matcher matcher = NON_CODE.matcher(source);
		StringBuffer result = new StringBuffer(source.length());
		while (matcher.find())
		{
			StringBuilder blank = new StringBuilder(matcher.group().length());
			for (int i = 0; i < matcher.group().length(); i++)
			{
				char c = matcher.group().charAt(i);
				blank.append(c == '\n' || c == '\r' ? c : ' ');
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(blank.toString()));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static List<Path> javaSources(Path root) throws IOException
	{
		try (Stream<Path> stream = Files.walk(root))
		{
			return stream.filter(path -> path.toString().endsWith(".java"))
				.collect(Collectors.toList());
		}
	}

	private static String relative(Path root, Path path)
	{
		return root.relativize(path).toString().replace('\\', '/');
	}

	private static Path microbotSourceRoot()
	{
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null)
		{
			Path candidate = current.resolve(
				"runelite-client/src/main/java/net/runelite/client/plugins/microbot");
			if (Files.isDirectory(candidate))
			{
				return candidate;
			}
			current = current.getParent();
		}
		throw new AssertionError("Unable to locate Microbot source root from " + System.getProperty("user.dir"));
	}
}
