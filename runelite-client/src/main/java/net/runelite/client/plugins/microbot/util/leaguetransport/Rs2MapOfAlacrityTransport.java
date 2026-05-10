package net.runelite.client.plugins.microbot.util.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Objects;
import java.util.Optional;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilNotNull;

/**
 * Map of Alacrity seasonal transport (Leagues).
 *
 * Used by {@link net.runelite.client.plugins.microbot.util.walker.Rs2Walker} via
 * {@code TransportType.SEASONAL_TRANSPORT} displayInfo rows.
 *
 * State: keeps session blacklist (bad/locked rows) to stop reroute spam.
 */
@Slf4j
public final class Rs2MapOfAlacrityTransport
{
	private static final AtomicBoolean MOA_AUDIT_STUB_LOGGED = new AtomicBoolean(false);
	/**
	 * Fallback when {@link Rs2Inventory#get(String, boolean)} cannot find the relic by name (league id drift).
	 * Prefer {@link #resolveMapOfAlacrityRelic()} in {@link #tryUse}.
	 */
	private static final int MAP_OF_ALACRITY_ITEM_ID_FALLBACK = 33233;
	private static final AtomicInteger MOA_RELIC_ID_MISMATCH_LOG = new AtomicInteger(0);
	// From client widget dump (Leagues MoA interface); update if Jagex changes group/child IDs.
	private static final int MAP_OF_ALACRITY_WIDGET_GROUP = 187;
	private static final int MAP_OF_ALACRITY_LIST_CHILD = 3;
	private static final String MOA_LOCKED_MARKUP = "<str>";

	/**
	 * Session-only mutable sets: intended for walker / MoA handler coordination only.
	 * Scripts must not clear or mutate these; doing so fights MoA blacklist/lock state.
	 */
	private static final Set<Integer> blacklistedMoaDestinations = ConcurrentHashMap.newKeySet();
	private static final Set<String> lockedMoaRegions = ConcurrentHashMap.newKeySet();

	public static boolean isMoaDestinationBlacklisted(int packedDest)
	{
		return blacklistedMoaDestinations.contains(packedDest);
	}

	public static boolean isMoaRegionLocked(String region)
	{
		return region != null && lockedMoaRegions.contains(region.toLowerCase(Locale.ROOT));
	}

	/**
	 * Changes when session MoA blacklist / lock sets mutate — included in {@code PathfinderConfig} transport refresh memo key.
	 */
	public static int moaTransportCacheFingerprint()
	{
		int h = blacklistedMoaDestinations.hashCode();
		h = 31 * h + lockedMoaRegions.hashCode();
		return h;
	}

	private static void addBlacklistedMoaDestination(int packedDest)
	{
		if (packedDest == 0)
		{
			return;
		}
		if (blacklistedMoaDestinations.add(packedDest))
		{
			Rs2LeaguesTransport.persistBlacklistDestination(packedDest, null, "MoA");
		}
	}

	/**
	 * Resolves relic by exact name first, then {@value #MAP_OF_ALACRITY_ITEM_ID_FALLBACK}. Logs once per session when name id
	 * differs from fallback (update constant after league settles).
	 */
	private static Rs2ItemModel resolveMapOfAlacrityRelic()
	{
		Rs2ItemModel byName = Rs2Inventory.get("Map of Alacrity", true);
		if (byName != null)
		{
			int id = byName.getId();
			if (id != MAP_OF_ALACRITY_ITEM_ID_FALLBACK && MOA_RELIC_ID_MISMATCH_LOG.compareAndSet(0, 1))
			{
				log.warn("[MoA] Map of Alacrity resolved by name id={} != fallback={}; update fallback when ids stable.",
						id, MAP_OF_ALACRITY_ITEM_ID_FALLBACK);
			}
			return byName;
		}
		return Rs2Inventory.get(MAP_OF_ALACRITY_ITEM_ID_FALLBACK);
	}

	private static int moaListPageSignature(Widget listRoot)
	{
		if (listRoot == null)
		{
			return 0;
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget[] d = listRoot.getDynamicChildren();
			int n = d == null ? 0 : d.length;
			int h = n * 31;
			if (d != null)
			{
				int cap = Math.min(4, d.length);
				for (int i = 0; i < cap; i++)
				{
					Widget w = d[i];
					String t = w != null ? w.getText() : "";
					h = h * 31 + (t != null ? t.hashCode() : 0);
				}
			}
			return h;
		}).orElse(0);
	}

	private static void addLockedMoaRegion(String region)
	{
		if (region != null && !region.isEmpty())
		{
			lockedMoaRegions.add(region.toLowerCase(Locale.ROOT));
		}
	}

	// Matches the OSRS menu-row hotkey prefix, e.g. "[1] ..." or "1: ..." or "A. ...".
	private static final Pattern MOA_HOTKEY_PATTERN =
			Pattern.compile("^\\s*(?:\\[([0-9A-Za-z])\\]|([0-9A-Za-z])\\s*[:.])");
	private static final Pattern MOA_MARKUP_PATTERN = Pattern.compile("<[^>]*>");
	private static final Pattern MOA_PUNCT_PATTERN = Pattern.compile("[^a-zA-Z0-9 ]");
	private static final Pattern MOA_WHITESPACE_PATTERN = Pattern.compile("\\s+");
	/** When {@code " - "} absent after {@link #normalizeMoaRegionShortcutSeparator}, split on hyphen/en-dash/em-dash/minus (allows hyphen-glued titles). */
	private static final Pattern MOA_REGION_SHORTCUT_FALLBACK_SPLIT = Pattern.compile("\\s*[-\\u2013\\u2014\\u2212]\\s*");
	private static final List<String> MOA_RELIC_ACTION_FALLBACK = List.of("Read", "Open", "Teleport", "Invoke");

	private Rs2MapOfAlacrityTransport()
	{
	}

	/** Fullwidth / compatibility colon forms → ASCII {@code ':'} for title parsing. */
	private static String normalizeMoaTitleColons(String displayInfo)
	{
		if (displayInfo == null)
		{
			return "";
		}
		return Rs2TextSanitizer.normalizeAsciiColons(displayInfo);
	}

	/**
	 * Normalizes dashes so {@link #tryUse} can find {@code " - "} or ASCII hyphens: space-en-dash / space-em-dash → {@code " - "};
	 * remaining en/em/minus signs → ASCII {@code '-'} (so {@code lastIndexOf(" - ")} and the fallback hyphen regex both see a delimiter).
	 */
	private static String normalizeMoaRegionShortcutSeparator(String rest)
	{
		if (rest == null || rest.isEmpty())
		{
			return "";
		}
		String s = rest.replace(" \u2013 ", " - ").replace(" \u2014 ", " - ");
		s = s.replace('\u2013', '-').replace('\u2014', '-').replace('\u2212', '-');
		return s;
	}

	// TSV / menu: {@code Map of Alacrity: <Region> - <Shortcut>} (ASCII {@code :} after {@link #normalizeMoaTitleColons}); {@link #tryUse} splits on first {@code :}.
	private static final String MOA_DISPLAY_TITLE_PREFIX = "Map of Alacrity";
	/**
	 * Strips Unicode format characters ({@code \p{Cf}}) only in the substring before the first {@code ':'} (title part) so ZWJ
	 * / variation selectors do not break the {@value #MOA_DISPLAY_TITLE_PREFIX} prefix check. Region/shortcut text after
	 * {@code ':'} is left unchanged.
	 */
	private static final Pattern MOA_FORMAT_CHARS = Pattern.compile("\\p{Cf}");

	/** Same pipeline as {@link #isMapOfAlacrityTransport} prefix check: colons + strip format chars + trim. */
	private static String normalizeMoaDisplayInfoForParsing(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		String colons = normalizeMoaTitleColons(raw);
		int colon = colons.indexOf(':');
		if (colon < 0)
		{
			return MOA_FORMAT_CHARS.matcher(colons).replaceAll("").trim();
		}
		String title = colons.substring(0, colon);
		String restText = colons.substring(colon + 1);
		String cleanTitle = MOA_FORMAT_CHARS.matcher(title).replaceAll("").trim();
		String cleanRest = restText.trim();
		return cleanRest.isEmpty() ? (cleanTitle + ":") : (cleanTitle + ": " + cleanRest);
	}

	/**
	 * True when {@code displayInfo} begins with the Map of Alacrity title (trimmed, case-insensitive).
	 * Callers that gate pathfinding should also require {@link net.runelite.client.plugins.microbot.shortestpath.TransportType#SEASONAL_TRANSPORT};
	 * this method intentionally ignores type so tests/helpers can pass partial mocks.
	 */
	public static boolean isMapOfAlacrityTransport(Transport transport)
	{
		if (transport == null || transport.getDisplayInfo() == null)
		{
			return false;
		}
		String t = normalizeMoaDisplayInfoForParsing(transport.getDisplayInfo());
		return t.regionMatches(true, 0, MOA_DISPLAY_TITLE_PREFIX, 0, MOA_DISPLAY_TITLE_PREFIX.length());
	}

	public static boolean tryUse(Transport transport)
	{
		if (transport == null || transport.getDisplayInfo() == null || transport.getDestination() == null)
		{
			return false;
		}

		if (!isMapOfAlacrityTransport(transport))
		{
			return false;
		}

		final String displayInfo = normalizeMoaDisplayInfoForParsing(transport.getDisplayInfo());
		if (log.isDebugEnabled())
		{
			int first = displayInfo.indexOf(':');
			int second = first < 0 ? -1 : displayInfo.indexOf(':', first + 1);
			if (second >= 0)
			{
				String sample = displayInfo.length() > 120 ? displayInfo.substring(0, 120) + "…" : displayInfo;
				log.debug("[MoA] multiple ':' in normalized displayInfo — split uses first only; sample='{}'", sample);
			}
		}

		int packedDest = WorldPointUtil.packWorldPoint(transport.getDestination());
		if (isMoaDestinationBlacklisted(packedDest))
		{
			return false;
		}

		Rs2ItemModel relic = resolveMapOfAlacrityRelic();
		if (relic == null)
		{
			return false;
		}

		Optional<MoaParsedRow> parsedOpt = parseMoaDisplayInfo(displayInfo);
		if (!parsedOpt.isPresent())
		{
			return false;
		}
		MoaParsedRow parsed = parsedOpt.get();
		String region = parsed.region;
		String shortName = parsed.shortcutName;

		if (isMoaRegionLocked(region))
		{
			addBlacklistedMoaDestination(packedDest);
			return false;
		}

		String action = relic.getAction("Read");
		if (action == null) action = relic.getActionFromList(MOA_RELIC_ACTION_FALLBACK);
		if (action == null)
		{
			return false;
		}
		if (!Rs2Inventory.interact(relic, action))
		{
			return false;
		}
		Rs2LeaguesTransport.recordTransportAttempt(transport, "MoA");

		if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(MAP_OF_ALACRITY_WIDGET_GROUP, MAP_OF_ALACRITY_LIST_CHILD), 3000))
		{
			return false;
		}

		Widget regionRoot = Rs2Widget.getWidget(MAP_OF_ALACRITY_WIDGET_GROUP, MAP_OF_ALACRITY_LIST_CHILD);
		if (regionRoot == null)
		{
			return false;
		}

		Widget regionMatch = findMoaWidget(regionRoot, region);
		if (regionMatch == null)
		{
			return false;
		}

		String regionText = Microbot.getClientThread().runOnClientThreadOptional(regionMatch::getText).orElse("");
		if (regionText != null && regionText.contains(MOA_LOCKED_MARKUP))
		{
			addLockedMoaRegion(region);
			addBlacklistedMoaDestination(packedDest);
			return false;
		}

		Character regionHotkey = extractMoaHotkey(regionText);
		if (regionHotkey == null) regionHotkey = computeMoaHotkeyByIndex(regionRoot, regionMatch);
		final int sigBefore = moaListPageSignature(regionRoot);
		if (regionHotkey != null)
		{
			Rs2Keyboard.keyPress(regionHotkey);
			if (sigBefore != 0)
			{
				sleepUntil(() ->
				{
					Widget r = Rs2Widget.getWidget(MAP_OF_ALACRITY_WIDGET_GROUP, MAP_OF_ALACRITY_LIST_CHILD);
					return r != null && moaListPageSignature(r) != sigBefore;
				}, 3000);
			}
		}
		else
		{
			if (!Rs2Widget.clickWidget(regionMatch))
			{
				return false;
			}
		}

		Widget destMatch = sleepUntilNotNull(() ->
		{
			Widget root = Rs2Widget.getWidget(MAP_OF_ALACRITY_WIDGET_GROUP, MAP_OF_ALACRITY_LIST_CHILD);
			if (root == null) return null;
			return findMoaWidget(root, shortName);
		}, 3000);

		if (destMatch == null)
		{
			addBlacklistedMoaDestination(packedDest);
			return false;
		}

		String destText = Microbot.getClientThread().runOnClientThreadOptional(destMatch::getText).orElse("");
		if (destText != null && destText.contains(MOA_LOCKED_MARKUP))
		{
			addBlacklistedMoaDestination(packedDest);
			return false;
		}

		Character destHotkey = extractMoaHotkey(destText);
		if (destHotkey == null)
		{
			Widget destRoot = Rs2Widget.getWidget(MAP_OF_ALACRITY_WIDGET_GROUP, MAP_OF_ALACRITY_LIST_CHILD);
			destHotkey = computeMoaHotkeyByIndex(destRoot, destMatch);
		}
		if (destHotkey != null)
		{
			Rs2Keyboard.keyPress(destHotkey);
		}
		else
		{
			if (!Rs2Widget.clickWidget(destMatch))
			{
				return false;
			}
		}

		return true;
	}

	static final class MoaParsedRow
	{
		private final String region;
		private final String shortcutName;

		private MoaParsedRow(String region, String shortcutName)
		{
			this.region = Objects.requireNonNull(region, "region");
			this.shortcutName = Objects.requireNonNull(shortcutName, "shortcutName");
		}

		String getRegion()
		{
			return region;
		}

		String getShortcutName()
		{
			return shortcutName;
		}
	}

	/**
	 * Parse "Map of Alacrity: &lt;Region&gt; - &lt;Shortcut&gt;" (after {@link #normalizeMoaDisplayInfoForParsing}).
	 * Returns empty if format is unexpected.
	 */
	static Optional<MoaParsedRow> parseMoaDisplayInfo(String normalizedDisplayInfo)
	{
		if (normalizedDisplayInfo == null || normalizedDisplayInfo.isEmpty())
		{
			return Optional.empty();
		}

		int colon = normalizedDisplayInfo.indexOf(':');
		if (colon >= 0 && colon < MOA_DISPLAY_TITLE_PREFIX.length())
		{
			if (log.isDebugEnabled())
			{
				String sample = normalizedDisplayInfo.length() > 80 ? normalizedDisplayInfo.substring(0, 80) + "…" : normalizedDisplayInfo;
				log.debug("[MoA] ':' appears before end of title prefix (minIndex={}); sample='{}'",
						MOA_DISPLAY_TITLE_PREFIX.length(), sample);
			}
			return Optional.empty();
		}

		String rest = colon >= 0 ? normalizedDisplayInfo.substring(colon + 1).trim() : normalizedDisplayInfo.trim();
		rest = normalizeMoaRegionShortcutSeparator(rest);
		String region;
		String shortName;
		// Last " - " so region may contain that substring; shortcut may contain " - " (e.g. "Place - Wing").
		int spacedDash = rest.lastIndexOf(" - ");
		if (spacedDash >= 0)
		{
			region = rest.substring(0, spacedDash).trim();
			shortName = rest.substring(spacedDash + 3).trim();
		}
		else
		{
			// Last dash match: shortcut may be unhyphenated while region contains several (game-dependent).
			Matcher dashSplit = MOA_REGION_SHORTCUT_FALLBACK_SPLIT.matcher(rest);
			int lastStart = -1;
			int lastEnd = -1;
			while (dashSplit.find())
			{
				lastStart = dashSplit.start();
				lastEnd = dashSplit.end();
			}
			if (lastStart < 0)
			{
				return Optional.empty();
			}
			region = rest.substring(0, lastStart).trim();
			shortName = rest.substring(lastEnd).trim();
		}
		if (region.isEmpty() || shortName.isEmpty())
		{
			return Optional.empty();
		}
		return Optional.of(new MoaParsedRow(region, shortName));
	}

	/**
	 * Placeholder for MoA audit automation; see {@link net.runelite.client.plugins.microbot.moaaudit.MoaAuditPlugin}.
	 * Logs at debug once per JVM if invoked without a real implementation.
	 */
	public static void runMoaAudit()
	{
		if (Thread.currentThread().isInterrupted())
		{
			return;
		}
		if (!Rs2LogRateLimit.once(MOA_AUDIT_STUB_LOGGED))
		{
			return;
		}
		log.debug("[MoA-AUDIT] stub: implement before relying on audit output");
	}

	/** For tests: allow a second {@link #runMoaAudit} log in the same JVM. */
	public static void resetMoaAuditStubLogForTests()
	{
		MOA_AUDIT_STUB_LOGGED.set(false);
	}

	private static Widget findMoaWidget(Widget root, String needle)
	{
		String normalised = normaliseMoaText(needle);
		if (normalised.isEmpty()) return null;
		String[] tokens = normalised.split(" ");
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget exact = null;
			Widget tokenMatch = null;
			int bestTokenHayLen = Integer.MAX_VALUE;
			String bestTokenHay = null;
			for (Widget w : collectMoaChildren(root))
			{
				String hay = normaliseMoaText(w.getText());
				if (hay.isEmpty())
				{
					continue;
				}
				if (hay.equals(normalised))
				{
					exact = w;
					break;
				}
				boolean all = true;
				for (String t : tokens)
				{
					if (t.isEmpty())
					{
						continue;
					}
					if (!hay.contains(t))
					{
						all = false;
						break;
					}
				}
				if (all)
				{
					// Tie on length: lexical order — deterministic, not semantic “best” if two rows normalize the same.
					int lenCmp = Integer.compare(hay.length(), bestTokenHayLen);
					boolean better = lenCmp < 0
							|| (lenCmp == 0 && (bestTokenHay == null || hay.compareTo(bestTokenHay) < 0));
					if (better)
					{
						tokenMatch = w;
						bestTokenHayLen = hay.length();
						bestTokenHay = hay;
					}
				}
			}
			return exact != null ? exact : tokenMatch;
		}).orElse(null);
	}

	private static List<Widget> collectMoaChildren(Widget root)
	{
		List<Widget> out = new ArrayList<>();
		if (root == null) return out;
		Widget[] dyn = root.getDynamicChildren();
		Widget[] kids = dyn != null && dyn.length > 0 ? dyn : root.getChildren();
		if (kids == null) return out;
		for (Widget w : kids)
		{
			if (w == null) continue;
			String txt = w.getText();
			if (txt == null || txt.isEmpty()) continue;
			out.add(w);
		}
		return out;
	}

	private static String normaliseMoaText(String raw)
	{
		if (raw == null) return "";
		String s = raw.toLowerCase(Locale.ROOT);
		s = Rs2TextSanitizer.stripTagsToSpace(s);
		s = s.replace('’', '\'');
		s = MOA_PUNCT_PATTERN.matcher(s).replaceAll(" ");
		s = MOA_WHITESPACE_PATTERN.matcher(s).replaceAll(" ").trim();
		return s;
	}

	private static Character extractMoaHotkey(String rawText)
	{
		if (rawText == null) return null;
		String plain = MOA_MARKUP_PATTERN.matcher(rawText).replaceAll("");
		java.util.regex.Matcher m = MOA_HOTKEY_PATTERN.matcher(plain);
		if (!m.find()) return null;
		String c = m.group(1) != null ? m.group(1) : m.group(2);
		if (c == null || c.isEmpty()) return null;
		return c.charAt(0);
	}

	/**
	 * Fallback hotkey when markup parsing fails. Assumes {@code root}'s child array order matches
	 * on-screen row order (1–9, then A–Z); padding or hidden rows can skew keys — {@link Rs2Widget#clickWidget}
	 * remains the fallback when this returns null.
	 */
	private static Character computeMoaHotkeyByIndex(Widget root, Widget match)
	{
		if (root == null || match == null)
		{
			return null;
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget[] dyn = root.getDynamicChildren();
			Widget[] kids = dyn != null && dyn.length > 0 ? dyn : root.getChildren();
			if (kids == null) return null;
			for (int i = 0; i < kids.length; i++)
			{
				if (kids[i] == match)
				{
					int idx = i + 1;
					if (idx >= 1 && idx <= 9) return (char) ('0' + idx);
					int alpha = idx - 10;
					if (alpha >= 0 && alpha < 26) return (char) ('A' + alpha);
					return null;
				}
			}
			return null;
		}).orElse(null);
	}
}

