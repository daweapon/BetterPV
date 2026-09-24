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

package io.github.moulberry.notenoughupdates.util;

import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Trimmed port of the Forge 1.8.9 {@code util.Utils} "god class". The original mixed pure data-layer helpers
 * (JSON path traversal, string colour-code handling, rarity lookups) with a very large amount of rendering code
 * (GuiScale/GL state, item-stack drawing, string layout, sound playback, etc.) that all belongs to the GUI layer
 * which is explicitly out of scope for this pass. Only the members actually referenced by
 * {@code ProfileViewer}/{@code PlayerStats}/the bestiary+trophy data classes/the weight calculators/
 * {@code ItemResolutionQuery} were ported.
 *
 * <p>API mapping notes (Forge 1.8.9 -&gt; Fabric 26.1.2, official Mojang mappings):
 * <ul>
 *   <li>{@code net.minecraft.util.EnumChatFormatting} -&gt; {@code net.minecraft.ChatFormatting} (identical
 *   constant names, e.g. {@code ChatFormatting.YELLOW}).</li>
 *   <li>{@code net.minecraft.item.ItemStack}/{@code Item} -&gt; {@code net.minecraft.world.item.ItemStack}/
 *   {@code Item}. Items are looked up as static fields on {@code net.minecraft.world.item.Items} (same idea as
 *   before), or via the registry {@code net.minecraft.core.registries.BuiltInRegistries.ITEM}.</li>
 *   <li>{@code Item.getItemFromBlock(Block)} -&gt; {@code Block#asItem()}.</li>
 *   <li>Item metadata/"damage" subtypes (e.g. {@code new ItemStack(Items.dye, 1, 4)} for lapis) no longer exist;
 *   modern Minecraft gives each former subtype its own top-level Item constant (e.g.
 *   {@code Items.LAPIS_LAZULI}), so call sites were updated to reference the specific item directly instead of
 *   passing a damage value.</li>
 *   <li>ItemStacks no longer carry a free-form NBT "tag" for display name/lore; that metadata is now typed
 *   "data components" set via {@code ItemStack#set(DataComponentType, T)}
 *   ({@code DataComponents.CUSTOM_NAME} takes a {@code Component}, {@code DataComponents.LORE} takes an
 *   {@code ItemLore}, and player-head skin data is {@code DataComponents.PROFILE} taking a
 *   {@code ResolvableProfile}).</li>
 *   <li>{@code net.minecraft.util.ResourceLocation} -&gt; {@code net.minecraft.resources.Identifier} (this
 *   Minecraft version renamed it), constructed via {@code Identifier.parse(String)}/
 *   {@code Identifier.fromNamespaceAndPath(namespace, path)} rather than a public constructor.</li>
 * </ul>
 */
public class Utils {
	private static final ChatFormatting[] rainbow = new ChatFormatting[]{
		ChatFormatting.RED,
		ChatFormatting.GOLD,
		ChatFormatting.YELLOW,
		ChatFormatting.GREEN,
		ChatFormatting.AQUA,
		ChatFormatting.LIGHT_PURPLE,
		ChatFormatting.DARK_PURPLE
	};
	public static String[] rarityArr = new String[]{
		"COMMON",
		"UNCOMMON",
		"RARE",
		"EPIC",
		"LEGENDARY",
		"MYTHIC",
		"SPECIAL",
		"VERY SPECIAL",
		"SUPREME",
		"^^ THAT ONE IS DIVINE ^^"
	};
	public static String[] rarityArrC = new String[]{
		ChatFormatting.WHITE + ChatFormatting.BOLD.toString() + "COMMON",
		ChatFormatting.GREEN + ChatFormatting.BOLD.toString() + "UNCOMMON",
		ChatFormatting.BLUE + ChatFormatting.BOLD.toString() + "RARE",
		ChatFormatting.DARK_PURPLE + ChatFormatting.BOLD.toString() + "EPIC",
		ChatFormatting.GOLD + ChatFormatting.BOLD.toString() + "LEGENDARY",
		ChatFormatting.LIGHT_PURPLE + ChatFormatting.BOLD.toString() + "MYTHIC",
		ChatFormatting.RED + ChatFormatting.BOLD.toString() + "SPECIAL",
		ChatFormatting.RED + ChatFormatting.BOLD.toString() + "VERY SPECIAL",
		ChatFormatting.AQUA + ChatFormatting.BOLD.toString() + "DIVINE",
		ChatFormatting.AQUA + ChatFormatting.BOLD.toString() + "DIVINE",
	};
	public static final HashMap<String, String> rarityArrMap = new HashMap<String, String>() {{
		put("COMMON", rarityArrC[0]);
		put("UNCOMMON", rarityArrC[1]);
		put("RARE", rarityArrC[2]);
		put("EPIC", rarityArrC[3]);
		put("LEGENDARY", rarityArrC[4]);
		put("MYTHIC", rarityArrC[5]);
		put("SPECIAL", rarityArrC[6]);
		put("VERY SPECIAL", rarityArrC[7]);
		put("DIVINE", rarityArrC[8]);
	}};
	public static Splitter PATH_SPLITTER = Splitter.on(".").omitEmptyStrings().limit(2);

	private Utils() {
	}

	/**
	 * Parses an SNBT string that may use 1.8's list syntax. 1.8 wrote list elements with their index
	 * ({@code textures:[0:{...}]}), which today's {@link net.minecraft.nbt.TagParser} rejects outright - the whole
	 * compound fails to parse. The repo's item {@code nbttag}s are in that format.
	 */
	public static net.minecraft.nbt.CompoundTag parseLegacyNbt(String snbt)
		throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return net.minecraft.nbt.TagParser.parseCompoundFully(stripLegacyListIndices(snbt));
	}

	/** Removes "N:" element indices directly inside [...] lists, leaving quoted strings and compound keys alone. */
	static String stripLegacyListIndices(String snbt) {
		StringBuilder out = new StringBuilder(snbt.length());
		java.util.ArrayDeque<Character> containers = new java.util.ArrayDeque<>();
		char quote = 0;
		boolean atElementStart = false;
		for (int i = 0; i < snbt.length(); i++) {
			char c = snbt.charAt(i);
			if (quote != 0) {
				out.append(c);
				if (c == '\\' && i + 1 < snbt.length()) {
					out.append(snbt.charAt(++i));
				} else if (c == quote) {
					quote = 0;
				}
				continue;
			}
			if (atElementStart && containers.peek() != null && containers.peek() == '[') {
				// Skip "<digits>:" at the start of a list element.
				int j = i;
				while (j < snbt.length() && Character.isWhitespace(snbt.charAt(j))) j++;
				int digitsStart = j;
				while (j < snbt.length() && Character.isDigit(snbt.charAt(j))) j++;
				if (j > digitsStart && j < snbt.length() && snbt.charAt(j) == ':') {
					i = j;
					atElementStart = false;
					continue;
				}
			}
			atElementStart = false;
			switch (c) {
				case '"', '\'' -> quote = c;
				case '{', '[' -> {
					containers.push(c);
					atElementStart = c == '[';
				}
				case '}', ']' -> containers.poll();
				case ',' -> atElementStart = true;
				default -> {
				}
			}
			out.append(c);
		}
		return out.toString();
	}

	public static <T> ArrayList<T> createList(T... values) {
		ArrayList<T> list = new ArrayList<>();
		Collections.addAll(list, values);
		return list;
	}

	public static String cleanColour(String in) {
		return in.replaceAll("(?i)\\u00A7.", "");
	}

	public static String cleanColourNotModifiers(String in) {
		return in.replaceAll("(?i)\\u00A7[0-9a-f]", "");
	}

	public static String prettyCase(String str) {
		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
	}

	public static String getRarityFromInt(int rarity) {
		if (rarity < 0 || rarity >= rarityArr.length) {
			return rarityArr[0];
		}
		return rarityArr[rarity];
	}

	public static int getRarityFromLore(JsonArray lore) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i).getAsString();

			for (int j = 0; j < rarityArrC.length; j++) {
				if (line.startsWith(rarityArrC[j])) {
					return j;
				}
			}
		}
		return -1;
	}

	public static int checkItemType(JsonArray lore, boolean contains, String... typeMatches) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i).getAsString();

			int returnType = checkItemType(line, contains, typeMatches);
			if (returnType != -1) {
				return returnType;
			}
		}
		return -1;
	}

	private static int checkItemType(String line, boolean contains, String... typeMatches) {
		for (String rarity : rarityArr) {
			for (int j = 0; j < typeMatches.length; j++) {
				if (contains) {
					if (line.trim().contains(rarity + " " + typeMatches[j])) {
						return j;
					} else if (line.trim().contains(rarity + " DUNGEON " + typeMatches[j])) {
						return j;
					}
				} else {
					if (line.trim().endsWith(rarity + " " + typeMatches[j])) {
						return j;
					} else if (line.trim().endsWith(rarity + " DUNGEON " + typeMatches[j])) {
						return j;
					}
				}
			}
		}
		return -1;
	}

	// Parses Roman numerals, allowing for single character irregular subtractive notation (e.g. IL is 49, IIL is invalid)
	public static int parseRomanNumeral(String input) {
		int prevVal = 0;
		int total = 0;
		for (int i = input.length() - 1; i >= 0; i--) {
			int val;
			char ch = input.charAt(i);
			switch (ch) {
				case 'I':
					val = 1;
					break;
				case 'V':
					val = 5;
					break;
				case 'X':
					val = 10;
					break;
				case 'L':
					val = 50;
					break;
				case 'C':
					val = 100;
					break;
				case 'D':
					val = 500;
					break;
				case 'M':
					val = 1000;
					break;
				default:
					throw new IllegalArgumentException("Invalid Roman Numeral Character: " + ch);
			}
			if (val < prevVal) val = -val;
			total += val;
			prevVal = val;
		}

		return total;
	}

	public static UUID parseDashlessUUID(String dashlessUuid) {
		// From: https://stackoverflow.com/a/30760478/
		java.math.BigInteger most = new java.math.BigInteger(dashlessUuid.substring(0, 16), 16);
		java.math.BigInteger least = new java.math.BigInteger(dashlessUuid.substring(16, 32), 16);
		return new UUID(most.longValue(), least.longValue());
	}

	public static ItemStack createItemStack(Item item, String displayName, String... lore) {
		ItemStack stack = new ItemStack(item, 1);
		applyDisplay(stack, displayName, lore);
		return stack;
	}

	public static ItemStack createItemStack(Block block, String displayName, String... lore) {
		return createItemStack(block.asItem(), displayName, lore);
	}

	private static void applyDisplay(ItemStack stack, String displayName, String... lore) {
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
		List<Component> loreLines = new ArrayList<>();
		for (String line : lore) {
			loreLines.add(Component.literal(line));
		}
		stack.set(DataComponents.LORE, new ItemLore(loreLines));
	}

	public static ItemStack createSkull(String displayName, String uuid, String value) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD, 1);
		applyDisplay(stack, displayName);

		HashMultimap<String, Property> propertiesBacking = HashMultimap.create();
		propertiesBacking.put("textures", new Property("textures", value));
		GameProfile profile = new GameProfile(
			UUID.fromString(insertUuidDashes(uuid)),
			"",
			new PropertyMap(propertiesBacking)
		);
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));

		return stack;
	}

	private static String insertUuidDashes(String uuid) {
		if (uuid.contains("-")) return uuid;
		return uuid.replaceFirst(
			"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
			"$1-$2-$3-$4-$5"
		);
	}

	/**
	 * Looks up a vanilla item by its (namespaced or bare, assumed minecraft:) registry id.
	 * Used by {@code APIManager#isVanillaItem}.
	 */
	public static boolean isRegisteredVanillaItem(String name) {
		Identifier id = Identifier.tryParse(name.toLowerCase(java.util.Locale.ROOT));
		if (id == null) return false;
		return BuiltInRegistries.ITEM.containsKey(id);
	}

	/** Port of the Forge 1.8.9 {@code Utils#recursiveDelete}. Used by {@link RepoSync}. */
	public static void recursiveDelete(java.io.File file) {
		if (file.isDirectory() && !java.nio.file.Files.isSymbolicLink(file.toPath())) {
			java.io.File[] children = file.listFiles();
			if (children != null) {
				for (java.io.File child : children) {
					recursiveDelete(child);
				}
			}
		}
		file.delete();
	}

	public static float getElementAsFloat(JsonElement element, float def) {
		if (element == null) return def;
		if (!element.isJsonPrimitive()) return def;
		JsonPrimitive prim = element.getAsJsonPrimitive();
		if (!prim.isNumber()) return def;
		return prim.getAsFloat();
	}

	public static int getElementAsInt(JsonElement element, int def) {
		if (element == null) return def;
		if (!element.isJsonPrimitive()) return def;
		JsonPrimitive prim = element.getAsJsonPrimitive();
		if (!prim.isNumber()) return def;
		return prim.getAsInt();
	}

	public static String getElementAsString(JsonElement element, String def) {
		if (element == null) return def;
		if (!element.isJsonPrimitive()) return def;
		JsonPrimitive prim = element.getAsJsonPrimitive();
		if (!prim.isString()) return def;
		return prim.getAsString();
	}

	public static JsonElement getElement(JsonElement element, String path) {
		List<String> path_split = PATH_SPLITTER.splitToList(path);
		if (element instanceof JsonObject) {
			JsonElement e = element.getAsJsonObject().get(path_split.get(0));
			if (path_split.size() > 1) {
				return getElement(e, path_split.get(1));
			} else {
				return e;
			}
		} else {
			return element;
		}
	}

	public static JsonElement getElementOrDefault(JsonElement element, String path, JsonElement def) {
		JsonElement result = getElement(element, path);
		return result != null ? result : def;
	}

	// ------------------------------------------------------------------------------------------------------------
	// GUI-layer helpers that don't require any client-only rendering classes. The actual drawing helpers
	// (drawTexturedRect/drawItemStack/drawStringCentered/etc, which need GuiGraphicsExtractor/Font/Minecraft) live
	// in the client-only companion class {@code io.github.moulberry.notenoughupdates.util.RenderUtils} under
	// src/client/java, since this class is compiled into the common (dedicated-server-safe) source set and can't
	// reference client-only Minecraft classes.
	// ------------------------------------------------------------------------------------------------------------

	public static boolean isWithinRect(int x, int y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	public static int roundToNearestInt(double value) {
		return (int) Math.round(value);
	}

	public static String chromaString(String str) {
		// TODO(fabric-port): the original animated the hue of each character over real time using GL colour
		// state per glyph, which has no direct equivalent against the deferred text renderer. For now this just
		// cycles through the classic Minecraft "rainbow" colour codes across the string, which is stable to
		// render but is not time-animated.
		ChatFormatting[] rainbow = {
			ChatFormatting.RED,
			ChatFormatting.GOLD,
			ChatFormatting.YELLOW,
			ChatFormatting.GREEN,
			ChatFormatting.AQUA,
			ChatFormatting.BLUE,
			ChatFormatting.LIGHT_PURPLE
		};
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			out.append(rainbow[i % rainbow.length]).append(str.charAt(i));
		}
		return out.toString();
	}
}
