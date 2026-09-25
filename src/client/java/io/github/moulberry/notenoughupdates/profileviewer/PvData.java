/*
 * Copyright (C) 2022 NotEnoughUpdates contributors
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

// Portions of this code are from the SkyBlockPv mod.

package io.github.moulberry.notenoughupdates.profileviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data helpers for the garden, chocolate factory and rift tabs: the bundled JSON in
 * {@code assets/betterpv/profile_viewer/}, item lookup, SkyBlockPv's text tags, reward formulas and cost lists.
 */
public final class PvData {

	private static final Map<String, JsonObject> BUNDLED = new HashMap<>();
	private static final Map<String, ItemStack> ITEMS = new HashMap<>();
	private static final Pattern TAG = Pattern.compile("<(/?)([a-z_]+)>");
	private static final Map<String, String> TAG_CODES = Map.ofEntries(
		Map.entry("black", "§0"), Map.entry("dark_blue", "§1"), Map.entry("dark_green", "§2"),
		Map.entry("dark_aqua", "§3"), Map.entry("dark_red", "§4"), Map.entry("dark_purple", "§5"),
		Map.entry("gold", "§6"), Map.entry("gray", "§7"), Map.entry("dark_gray", "§8"), Map.entry("blue", "§9"),
		Map.entry("green", "§a"), Map.entry("aqua", "§b"), Map.entry("red", "§c"), Map.entry("light_purple", "§d"),
		Map.entry("yellow", "§e"), Map.entry("white", "§f"), Map.entry("bold", "§l"), Map.entry("b", "§l"),
		Map.entry("italic", "§o"), Map.entry("i", "§o")
	);

	/** SkyBlock rarities in order, with their colour codes and slot tints. */
	public static final List<String> RARITIES = List.of(
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL");
	private static final String[] RARITY_CODES = {"§f", "§a", "§9", "§5", "§6", "§d", "§b", "§c", "§c"};
	private static final int[] RARITY_COLOURS = {
		0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF, 0x55FFFF, 0xFF5555, 0xFF5555
	};

	private PvData() {
	}

	/** {@code profile_viewer/<name>.json}, read once; an empty object if it's missing. */
	public static JsonObject bundled(String name) {
		return BUNDLED.computeIfAbsent(name, key -> {
			Identifier id = Identifier.parse("betterpv:profile_viewer/" + key + ".json");
			try (Reader reader = new java.io.InputStreamReader(
				Minecraft.getInstance().getResourceManager().open(id), StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			} catch (Exception e) {
				NotEnoughUpdates.LOGGER.warn("Couldn't read {}", id, e);
				return new JsonObject();
			}
		});
	}

	public static int rarityIndex(String rarity) {
		return rarity == null ? -1 : RARITIES.indexOf(rarity.toUpperCase(Locale.ROOT));
	}

	public static String rarityCode(int rarity) {
		return rarity < 0 ? "§7" : RARITY_CODES[rarity];
	}

	public static int rarityColour(int rarity) {
		return rarity < 0 ? 0xAAAAAA : RARITY_COLOURS[rarity];
	}

	/**
	 * An item by SkyBlock id from the NEU repo ({@code INK_SACK:3} is {@code INK_SACK-3} there), else a vanilla
	 * item id, else a barrier.
	 */
	public static ItemStack item(String id) {
		if (id == null) return new ItemStack(Items.BARRIER);
		return ITEMS.computeIfAbsent(id, key -> {
			JsonObject json = repoItem(key);
			if (json != null) {
				ItemStack stack = NotEnoughUpdates.INSTANCE.manager.jsonToStack(json);
				if (stack != null && !stack.isEmpty()) return stack;
			}
			Identifier vanilla = Identifier.tryParse(key.toLowerCase(Locale.ROOT));
			if (vanilla != null && BuiltInRegistries.ITEM.containsKey(vanilla)) {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(vanilla));
			}
			return new ItemStack(Items.BARRIER);
		});
	}

	public static JsonObject repoItem(String id) {
		return id == null ? null : NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(id.replace(':', '-'));
	}

	/** The repo item's display name, or the id made readable. */
	public static String itemName(String id) {
		JsonObject json = repoItem(id);
		String name = json == null ? null : Utils.getElementAsString(json.get("displayname"), null);
		return name != null ? name : "§f" + titleCase(id);
	}

	public static List<String> itemLore(String id) {
		List<String> lines = new ArrayList<>();
		JsonObject json = repoItem(id);
		if (json != null && json.get("lore") instanceof JsonArray lore) {
			for (JsonElement line : lore) lines.add(line.getAsString());
		}
		return lines;
	}

	/** A player head with this base64 texture, cached by texture. */
	public static ItemStack skull(String texture) {
		return ITEMS.computeIfAbsent("skull:" + texture, key ->
			Utils.createSkull("", UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)).toString(), texture));
	}

	public static String titleCase(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.replace('-', '_').split("_")) {
			if (word.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1).toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}

	/** SkyBlockPv's text tags ({@code <gray>}, {@code <bold>}) as colour codes; closing tags are dropped. */
	public static String tags(String text) {
		Matcher matcher = TAG.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String code = matcher.group(1).isEmpty() ? TAG_CODES.get(matcher.group(2)) : null;
			matcher.appendReplacement(out, Matcher.quoteReplacement(code == null ? "" : code));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	// ---- numbers ----

	public static String format(double number) {
		return String.format(Locale.US, "%,d", (long) number);
	}

	/** 1.2k, 3.4M, 5B. */
	public static String shorten(double number) {
		String[] suffixes = {"", "k", "M", "B", "T"};
		int index = 0;
		while (Math.abs(number) >= 1000 && index < suffixes.length - 1) {
			number /= 1000;
			index++;
		}
		String text = index == 0 ? String.valueOf((long) number) : String.format(Locale.US, "%.1f", number);
		if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
		return text + suffixes[index];
	}

	public static String percent(double part, double whole) {
		return whole == 0 ? "0" : String.format(Locale.US, "%.1f", part / whole * 100).replace(".0", "");
	}

	public static long asLong(JsonElement element, long fallback) {
		try {
			return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static long getLong(JsonObject root, String path) {
		return asLong(Utils.getElement(root, path), 0);
	}

	/** Running totals with a leading 0 and repeats dropped, like SkyBlockPv's {@code cum_int_list}. */
	public static List<Long> cumulative(JsonElement increments) {
		List<Long> totals = new ArrayList<>();
		totals.add(0L);
		long total = 0;
		if (increments instanceof JsonArray array) {
			for (JsonElement element : array) {
				total += asLong(element, 0);
				if (total != totals.get(totals.size() - 1)) totals.add(total);
			}
		}
		return totals;
	}

	/** Per-level running totals of each cost (level 1 is index 0), like SkyBlockPv's {@code cum_string_int_map}. */
	public static List<Map<String, Long>> cumulativeCosts(JsonElement levels) {
		List<Map<String, Long>> totals = new ArrayList<>();
		Map<String, Long> running = new LinkedHashMap<>();
		if (levels instanceof JsonArray array) {
			for (JsonElement level : array) {
				if (level instanceof JsonObject costs) {
					for (Map.Entry<String, JsonElement> cost : costs.entrySet()) {
						running.merge(cost.getKey(), asLong(cost.getValue(), 0), Long::sum);
					}
				}
				totals.add(new LinkedHashMap<>(running));
			}
		}
		return totals;
	}

	/**
	 * Tooltip lines for an upgrade's costs: what {@code level} levels cost out of the total. {@code copper} and
	 * {@code gold_medal} are named; anything else is a repo item.
	 */
	public static List<String> costLines(int level, List<Map<String, Long>> costs) {
		List<String> lines = new ArrayList<>();
		if (costs.isEmpty()) return lines;
		Map<String, Long> paid = level > 0 ? costs.get(Math.min(level, costs.size()) - 1) : Map.of();
		for (Map.Entry<String, Long> max : costs.get(costs.size() - 1).entrySet()) {
			long used = paid.getOrDefault(max.getKey(), 0L);
			String name = switch (max.getKey()) {
				case "copper" -> "§cCopper";
				case "gold_medal" -> "§6Gold Medal";
				default -> itemName(max.getKey());
			};
			lines.add(name + " §e" + format(used) + "§6/§e" + format(max.getValue()) +
				" §7(§3" + percent(used, max.getValue()) + "%§7)");
		}
		return lines;
	}

	// ---- formulas ----

	/** Evaluates a SkyBlockPv reward formula: numbers, {@code level}, + - * /, brackets and clamp/min/max. */
	public static double evaluate(String formula, double level) {
		try {
			return new Formula(formula.replace(" ", ""), level).parse();
		} catch (RuntimeException e) {
			return level;
		}
	}

	private static final class Formula {
		private final String text;
		private final double level;
		private int pos;

		Formula(String text, double level) {
			this.text = text;
			this.level = level;
		}

		double parse() {
			double value = sum();
			if (pos != text.length()) throw new IllegalArgumentException(text);
			return value;
		}

		private double sum() {
			double value = product();
			while (pos < text.length() && (peek() == '+' || peek() == '-')) {
				char op = text.charAt(pos++);
				double right = product();
				value = op == '+' ? value + right : value - right;
			}
			return value;
		}

		private double product() {
			double value = unary();
			while (pos < text.length() && (peek() == '*' || peek() == '/')) {
				char op = text.charAt(pos++);
				double right = unary();
				value = op == '*' ? value * right : value / right;
			}
			return value;
		}

		private double unary() {
			if (peek() == '-') {
				pos++;
				return -unary();
			}
			if (peek() == '(') {
				pos++;
				double value = sum();
				pos++;
				return value;
			}
			int start = pos;
			if (Character.isDigit(peek()) || peek() == '.') {
				while (pos < text.length() && (Character.isDigit(peek()) || peek() == '.')) pos++;
				return Double.parseDouble(text.substring(start, pos));
			}
			while (pos < text.length() && Character.isLetter(peek())) pos++;
			String name = text.substring(start, pos);
			if (name.equals("level")) return level;
			pos++;
			List<Double> args = new ArrayList<>();
			args.add(sum());
			while (peek() == ',') {
				pos++;
				args.add(sum());
			}
			pos++;
			return switch (name) {
				case "clamp" -> Math.max(args.get(1), Math.min(args.get(2), args.get(0)));
				case "min" -> Math.min(args.get(0), args.get(1));
				case "max" -> Math.max(args.get(0), args.get(1));
				default -> throw new IllegalArgumentException(name);
			};
		}

		private char peek() {
			return pos < text.length() ? text.charAt(pos) : '\0';
		}
	}
}
