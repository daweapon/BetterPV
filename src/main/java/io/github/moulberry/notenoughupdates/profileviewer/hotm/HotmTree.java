/*
 * Copyright (C) 2024 NotEnoughUpdates contributors
 *
 * This file is part of NotEnoughUpdates.
 *
 * NotEnoughUpdates is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * NotEnoughUpdates is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with NotEnoughUpdates. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.moulberry.notenoughupdates.profileviewer.hotm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of NEU's {@code HotmTreeLayout}/{@code HotmTreeRenderer} without the drawing: loads
 * {@code constants/hotmlayout.json} and evaluates a perk for a player's node levels. New perks appear once
 * the repo is updated.
 */
public final class HotmTree {
	private static final Logger LOGGER = LoggerFactory.getLogger("BetterPV");
	private static final Set<String> PERK_FIELDS = Set.of("name", "x", "y", "maxLevel", "powder", "cost", "lore");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z\\-A-Z_0-9]+)}");

	public record LoreLine(String text, HotmLisp.Program condition) {}

	public record Perk(
		String id, String name, int x, int y, int maxLevel,
		HotmLisp.Program powder, List<LoreLine> lore, Map<String, HotmLisp.Program> functions
	) {}

	/** A perk evaluated for one player: its level, full tooltip, and the repo/vanilla item id for its icon. */
	public record PerkState(int level, List<String> tooltip, String itemId) {}

	private static JsonObject loadedFrom;
	private static HotmTree loaded;

	private final Map<String, Perk> perks = new LinkedHashMap<>();
	private final Map<String, String> powderCostLines = new LinkedHashMap<>();
	private final Set<Long> occupied = new HashSet<>();
	private final HotmLisp.Env root = HotmLisp.rootEnv();
	private int maxX;
	private int maxY;

	/** The tree for the current repo, or null if the repo has no (valid) hotmlayout.json. */
	public static synchronized HotmTree get() {
		JsonObject layout = Constants.HOTMLAYOUT;
		if (layout != loadedFrom) {
			loadedFrom = layout;
			loaded = null;
			if (layout != null) {
				try {
					loaded = new HotmTree(layout);
				} catch (RuntimeException e) {
					LOGGER.warn("Couldn't load constants/hotmlayout.json", e);
				}
			}
		}
		return loaded;
	}

	private HotmTree(JsonObject file) {
		if (file.has("prelude")) {
			for (JsonElement line : file.getAsJsonArray("prelude")) HotmLisp.run(HotmLisp.parse(line.getAsString()), root);
		}
		JsonObject hotm = file.getAsJsonObject("hotm");
		for (Map.Entry<String, JsonElement> entry : hotm.getAsJsonObject("powders").entrySet()) {
			powderCostLines.put(entry.getKey(), entry.getValue().getAsJsonObject().get("costLine").getAsString());
		}
		for (Map.Entry<String, JsonElement> entry : hotm.getAsJsonObject("perks").entrySet()) {
			JsonObject data = entry.getValue().getAsJsonObject();
			List<LoreLine> lore = new ArrayList<>();
			for (JsonElement line : data.getAsJsonArray("lore")) {
				if (line.isJsonObject()) {
					JsonObject object = line.getAsJsonObject();
					lore.add(new LoreLine(object.get("text").getAsString(), HotmLisp.parse(object.get("onlyIf").getAsString())));
				} else {
					lore.add(new LoreLine(line.getAsString(), null));
				}
			}
			// Every extra field ("item", "stat", "statBoost", ...) is a formula, plus "cost".
			Map<String, HotmLisp.Program> functions = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> field : data.entrySet()) {
				if (!PERK_FIELDS.contains(field.getKey())) functions.put(field.getKey(), HotmLisp.parse(field.getValue().getAsString()));
			}
			functions.put("cost", HotmLisp.parse(data.get("cost").getAsString()));

			Perk perk = new Perk(
				entry.getKey(), data.get("name").getAsString(), data.get("x").getAsInt(), data.get("y").getAsInt(),
				data.get("maxLevel").getAsInt(), HotmLisp.parse(data.get("powder").getAsString()), lore, functions
			);
			perks.put(perk.id(), perk);
			occupied.add(key(perk.x(), perk.y()));
			maxX = Math.max(maxX, perk.x());
			maxY = Math.max(maxY, perk.y());
		}
	}

	private static long key(int x, int y) {
		return ((long) x << 32) | (y & 0xFFFFFFFFL);
	}

	public Iterable<Perk> perks() {
		return perks.values();
	}

	public boolean hasPerkAt(int x, int y) {
		return occupied.contains(key(x, y));
	}

	public int columns() {
		return maxX + 1;
	}

	public int rows() {
		return maxY + 1;
	}

	/**
	 * {@code nodes} is the player's perk levels ({@code mining_core.nodes}); {@code toggle_<perk>: false} marks a
	 * disabled perk.
	 */
	public PerkState evaluate(Perk perk, JsonObject nodes, int hotmLevel) {
		Map<String, Integer> levels = levels(nodes);
		int level = levels.getOrDefault(perk.id(), 0);

		HotmLisp.Env bindings = root.fork();
		// Peak of the Mountain was renamed Core of the Mountain; the old API called it special_0.
		bindings.set("potm", (double) levels.getOrDefault("core_of_the_mountain", levels.getOrDefault("special_0", 0)));
		bindings.set("hotm", (double) hotmLevel);
		bindings.set("other-perk-level", (HotmLisp.Fn) args -> {
			if (args.size() != 1 || !(args.get(0) instanceof String name)) throw new HotmLisp.LispError("Need exactly one perk name");
			return (double) levels.getOrDefault(name, 0);
		});
		bindings.set("level", (double) (level == 0 ? 1 : level));
		bindings.set("maxLevel", (double) perk.maxLevel());
		bindings.set("level0", (double) level);

		Map<String, Object> values = new HashMap<>();
		for (Map.Entry<String, HotmLisp.Program> function : perk.functions().entrySet()) {
			values.put(function.getKey(), runSafely(function.getValue(), bindings.fork(), perk, function.getKey()));
		}

		List<String> lines = new ArrayList<>();
		String title = level == perk.maxLevel() ? "§a" : level == 0 ? "§c" : "§e";
		lines.add(title + perk.name());
		if (perk.maxLevel() != 1) {
			lines.add(level != perk.maxLevel() ? "§7Level " + level + "§8/" + perk.maxLevel() : "§7Level " + level);
			lines.add("");
		}
		for (LoreLine line : perk.lore()) {
			if (line.condition() == null || HotmLisp.isTruthy(runSafely(line.condition(), bindings.fork(), perk, "lore"))) {
				lines.add(line.text());
			}
		}
		if (level != 0 && level != perk.maxLevel()) {
			HotmLisp.Env powderBindings = bindings.fork();
			for (String powder : powderCostLines.keySet()) powderBindings.set(powder, powder);
			Object powder = runSafely(perk.powder(), powderBindings, perk, "powder");
			lines.add("");
			lines.add(powder instanceof String name && powderCostLines.containsKey(name) ? powderCostLines.get(name) : "<lisp-error>");
		}
		if (nodes != null && nodes.has("toggle_" + perk.id()) && !nodes.get("toggle_" + perk.id()).getAsBoolean()) {
			lines.add("");
			lines.add("§cDisabled");
		}

		List<String> tooltip = new ArrayList<>(lines.size());
		for (String line : lines) tooltip.add(fillPlaceholders(line, values));

		Object item = values.get("item");
		String itemId = item instanceof HotmLisp.Atom atom ? atom.label() : item instanceof String string ? string : null;
		return new PerkState(level, tooltip, itemId);
	}

	private static Map<String, Integer> levels(JsonObject nodes) {
		Map<String, Integer> levels = new HashMap<>();
		if (nodes == null) return levels;
		for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
			JsonElement value = entry.getValue();
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) levels.put(entry.getKey(), value.getAsInt());
		}
		return levels;
	}

	private static Object runSafely(HotmLisp.Program program, HotmLisp.Env env, Perk perk, String what) {
		try {
			return HotmLisp.run(program, env);
		} catch (RuntimeException e) {
			LOGGER.debug("hotmlayout.json perk {} {}: {}", perk.id(), what, e.getMessage());
			return null;
		}
	}

	private static String fillPlaceholders(String line, Map<String, Object> values) {
		Matcher matcher = PLACEHOLDER.matcher(line);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			Object value = values.get(name);
			String replacement;
			if (value instanceof String string) {
				replacement = string;
			} else if (value instanceof Double number) {
				replacement = NumberFormat.getInstance(Locale.US).format(name.equals("cost") ? Math.floor(number) : number);
			} else {
				replacement = "<lisp-error>";
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
