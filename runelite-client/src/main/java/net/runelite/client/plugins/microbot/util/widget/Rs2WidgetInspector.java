package net.runelite.client.plugins.microbot.util.widget;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class Rs2WidgetInspector {

	private static final int DEFAULT_MAX_DEPTH = 5;

	public static List<WidgetDescription> getVisibleInterfaces() {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			List<WidgetDescription> results = new ArrayList<>();
			Widget[] roots = Rs2Widget.widgetRoots();
			if (roots == null) return results;

			for (Widget root : roots) {
				if (root == null || root.isHidden()) continue;
				WidgetDescription desc = describeWidgetShallow(root, "");
				if (desc != null) {
					results.add(desc);
				}
			}
			return results;
		}).orElse(Collections.emptyList());
	}

	public static List<WidgetDescription> describeWidget(int groupId, int childId, int maxDepth) {
		if (groupId < 0 || childId < 0) return Collections.emptyList();
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget widget;
			try {
				widget = Microbot.getClient().getWidget(groupId, childId);
			} catch (IndexOutOfBoundsException e) {
				return Collections.<WidgetDescription>emptyList();
			}
			if (widget == null) return Collections.<WidgetDescription>emptyList();
			List<WidgetDescription> results = new ArrayList<>();
			walkTree(widget, "", 0, maxDepth, results);
			return results;
		}).orElse(Collections.emptyList());
	}

	public static List<WidgetDescription> describeWidget(int groupId, int childId) {
		return describeWidget(groupId, childId, DEFAULT_MAX_DEPTH);
	}

	public static List<WidgetDescription> search(String... keywords) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			List<WidgetDescription> results = new ArrayList<>();
			Widget[] roots = Rs2Widget.widgetRoots();
			if (roots == null) return results;

			String[] lowerKeywords = Arrays.stream(keywords)
					.map(String::toLowerCase)
					.toArray(String[]::new);

			for (Widget root : roots) {
				if (root == null || root.isHidden()) continue;
				searchTree(root, "", lowerKeywords, results, 0, 10);
			}

			results.sort((a, b) -> Integer.compare(b.relevanceScore, a.relevanceScore));
			return results;
		}).orElse(Collections.emptyList());
	}

	public static String getWidgetPath(Widget widget) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			List<String> parts = new ArrayList<>();
			Widget current = widget;
			while (current != null) {
				String label = getLabel(current);
				if (!label.isEmpty()) {
					parts.add(0, label);
				} else {
					int id = current.getId();
					int group = WidgetUtil.componentToInterface(id);
					int child = WidgetUtil.componentToId(id);
					parts.add(0, group + ":" + child);
				}
				current = current.getParent();
			}
			return String.join(" > ", parts);
		}).orElse("");
	}

	private static void walkTree(Widget widget, String pathPrefix, int depth, int maxDepth, List<WidgetDescription> results) {
		if (widget == null || depth > maxDepth) return;

		String label = getLabel(widget);
		String currentPath = pathPrefix.isEmpty() ? label : pathPrefix + " > " + label;

		WidgetDescription desc = toDescription(widget, currentPath);
		results.add(desc);

		List<Widget> children = getAllChildren(widget);
		for (Widget child : children) {
			if (child == null || child.isHidden()) continue;
			walkTree(child, currentPath, depth + 1, maxDepth, results);
		}
	}

	private static void searchTree(Widget widget, String pathPrefix, String[] keywords, List<WidgetDescription> results, int depth, int maxDepth) {
		if (widget == null || depth > maxDepth) return;

		String label = getLabel(widget);
		String currentPath = pathPrefix.isEmpty() ? label : pathPrefix + " > " + label;

		int score = scoreWidget(widget, keywords);
		if (score > 0) {
			WidgetDescription desc = toDescription(widget, currentPath);
			desc.relevanceScore = score;
			results.add(desc);
		}

		List<Widget> children = getAllChildren(widget);
		for (Widget child : children) {
			if (child == null || child.isHidden()) continue;
			searchTree(child, currentPath, keywords, results, depth + 1, maxDepth);
		}
	}

	private static int scoreWidget(Widget widget, String[] keywords) {
		String text = cleanText(widget.getText());
		String name = cleanText(widget.getName());
		String[] actions = widget.getActions();
		String allText = (text + " " + name).toLowerCase();
		if (actions != null) {
			allText += " " + Arrays.stream(actions)
					.filter(Objects::nonNull)
					.map(Rs2UiHelper::stripColTags)
					.collect(Collectors.joining(" ")).toLowerCase();
		}

		int score = 0;
		for (String keyword : keywords) {
			if (allText.contains(keyword)) {
				score++;
				if (text.toLowerCase().contains(keyword)) score++;
				if (name.toLowerCase().contains(keyword)) score++;
			}
		}
		return score;
	}

	private static WidgetDescription describeWidgetShallow(Widget widget, String path) {
		String label = getLabel(widget);
		if (label.isEmpty() && !hasVisibleChildren(widget)) return null;
		return toDescription(widget, path.isEmpty() ? label : path + " > " + label);
	}

	private static WidgetDescription toDescription(Widget widget, String path) {
		int id = widget.getId();
		int groupId = WidgetUtil.componentToInterface(id);
		int childId = WidgetUtil.componentToId(id);

		WidgetDescription desc = new WidgetDescription();
		desc.groupId = groupId;
		desc.childId = childId;
		desc.index = widget.getIndex();
		desc.text = cleanText(widget.getText());
		desc.name = cleanText(widget.getName());
		desc.type = getTypeName(widget.getType());
		desc.path = path;
		desc.spriteId = widget.getSpriteId();
		desc.itemId = widget.getItemId();
		desc.hidden = widget.isSelfHidden();

		String[] rawActions = widget.getActions();
		if (rawActions != null) {
			desc.actions = Arrays.stream(rawActions)
					.filter(a -> a != null && !a.isEmpty())
					.map(Rs2UiHelper::stripColTags)
					.collect(Collectors.toList());
		} else {
			desc.actions = Collections.emptyList();
		}

		desc.interactable = !desc.actions.isEmpty();
		desc.childCount = countVisibleChildren(widget);

		return desc;
	}

	private static List<Widget> getAllChildren(Widget widget) {
		List<Widget> all = new ArrayList<>();
		addNonNull(all, widget.getStaticChildren());
		addNonNull(all, widget.getDynamicChildren());
		addNonNull(all, widget.getNestedChildren());
		return all;
	}

	private static void addNonNull(List<Widget> list, Widget[] arr) {
		if (arr != null) {
			for (Widget w : arr) {
				if (w != null) list.add(w);
			}
		}
	}

	private static boolean hasVisibleChildren(Widget widget) {
		return countVisibleChildren(widget) > 0;
	}

	private static int countVisibleChildren(Widget widget) {
		int count = 0;
		Widget[] children = widget.getStaticChildren();
		if (children != null) count += countVisible(children);
		children = widget.getDynamicChildren();
		if (children != null) count += countVisible(children);
		children = widget.getNestedChildren();
		if (children != null) count += countVisible(children);
		return count;
	}

	private static int countVisible(Widget[] widgets) {
		int c = 0;
		for (Widget w : widgets) {
			if (w != null && !w.isHidden()) c++;
		}
		return c;
	}

	private static String getLabel(Widget widget) {
		String text = cleanText(widget.getText());
		if (!text.isEmpty()) return text;
		String name = cleanText(widget.getName());
		if (!name.isEmpty()) return name;
		return "";
	}

	private static String cleanText(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		return Rs2UiHelper.stripColTags(raw).trim();
	}

	private static String getTypeName(int type) {
		switch (type) {
			case WidgetType.LAYER: return "LAYER";
			case WidgetType.RECTANGLE: return "RECTANGLE";
			case WidgetType.TEXT: return "TEXT";
			case WidgetType.GRAPHIC: return "GRAPHIC";
			case WidgetType.MODEL: return "MODEL";
			case WidgetType.TEXT_INVENTORY: return "TEXT_INVENTORY";
			case WidgetType.IF1_TOOLTIP: return "IF1_TOOLTIP";
			case WidgetType.LINE: return "LINE";
			default: return "TYPE_" + type;
		}
	}

	public static class WidgetDescription {
		public int groupId;
		public int childId;
		public int index;
		public String text;
		public String name;
		public String type;
		public String path;
		public List<String> actions;
		public boolean interactable;
		public boolean hidden;
		public int spriteId;
		public int itemId;
		public int childCount;
		public int relevanceScore;
	}
}
