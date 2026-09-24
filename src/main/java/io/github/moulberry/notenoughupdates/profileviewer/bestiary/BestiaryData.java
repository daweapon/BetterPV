/*
 * Copyright (C) 2022-2024 NotEnoughUpdates contributors
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

package io.github.moulberry.notenoughupdates.profileviewer.bestiary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port of current NEU's Kotlin {@code BestiaryData}, which replaced the Forge 1.8.9 hardcoded mob tables with the
 * repo's {@code constants/bestiary.json}. One difference: NEU parses a fixed list of category ids, while this reads
 * every category in the file (in file order), so islands the repo adds later (Galatea's Moonglade Marsh and
 * Torrhus Canyon, Lotus Atoll, Critter Safari, ...) show up without a code change. Mobs may also name a
 * {@code bracketType} (e.g. {@code "CRITTERS"}), whose kill thresholds live under {@code bracketSets}.
 */
public final class BestiaryData {
	private BestiaryData() {}

	/** Top-level keys of bestiary.json that aren't categories. */
	private static final Set<String> NON_CATEGORY_KEYS = Set.of("brackets", "bracketSets");
	/** Keys inside a category with subcategories that aren't subcategories. */
	private static final Set<String> RESERVED_CATEGORY_KEYS = Set.of("name", "icon", "hasSubcategories");

	/** Corrected cap/bracket for a mob the repo has wrong, keyed by its first API id. */
	private record Correction(double cap, String bracketType, int bracket) {}

	/**
	 * Local fixes for bestiary.json entries that don't match the game. Each only applies while the repo's cap is
	 * still below the corrected one, so a repo fix takes over by itself; remove entries once the repo catches up.
	 */
	private static final Map<String, Correction> CORRECTIONS = Map.of(
		// Repo: cap 25 on CRITTERS bracket 5. In game (checked 2026-09-23) the max is 50 kills, i.e. bracket 4.
		"beeheemoth_158", new Correction(50, "CRITTERS", 4)
	);

	public record Mob(String name, ItemStack icon, double kills, double deaths, MobLevelData levelData) {}

	public record Category(
		String id, String name, ItemStack icon, List<Mob> mobs, List<Category> subCategories, FamilyData familyData
	) {}

	public record MobLevelData(int level, boolean maxLevel, double progress, double totalProgress, MobKillData killData) {}

	public record MobKillData(double tierKills, double tierReq, double cappedKills, double cap) {}

	public record FamilyData(int found, int completed, int total) {}

	/** Profiles from before Hypixel's 2023 bestiary rework have "migration": false and use the old format. */
	public static boolean hasMigrated(JsonObject profileInfo) {
		JsonElement bestiary = profileInfo.get("bestiary");
		if (bestiary == null || !bestiary.isJsonObject()) return false;
		JsonElement migration = bestiary.getAsJsonObject().get("migration");
		// No flag means a newer profile, which is already in the new format.
		return migration == null || migration.getAsBoolean();
	}

	/** Sum of every mob's tier; the bestiary milestone is this / 10. */
	public static int calculateTotalBestiaryTiers(List<Category> categories) {
		int tiers = 0;
		for (Category category : categories) tiers += countTotalLevels(category);
		return tiers;
	}

	private static int countTotalLevels(Category category) {
		int levels = 0;
		for (Mob mob : category.mobs()) levels += mob.levelData().level();
		for (Category sub : category.subCategories()) levels += countTotalLevels(sub);
		return levels;
	}

	public static List<Category> parseBestiaryData(JsonObject profileInfo) {
		List<Category> parsed = new ArrayList<>();
		JsonObject bestiary = Constants.BESTIARY;
		if (bestiary == null || !hasMigrated(profileInfo)) return parsed;

		JsonObject playerBestiary = profileInfo.getAsJsonObject("bestiary");
		Map<String, Double> kills = readCounts(playerBestiary.get("kills"));
		Map<String, Double> deaths = readCounts(playerBestiary.get("deaths"));

		for (Map.Entry<String, JsonElement> entry : bestiary.entrySet()) {
			if (NON_CATEGORY_KEYS.contains(entry.getKey()) || !entry.getValue().isJsonObject()) continue;
			parsed.add(parseCategory(entry.getValue().getAsJsonObject(), entry.getKey(), kills, deaths));
		}
		return parsed;
	}

	/** mob id -> count, skipping non-numeric entries such as "last_killed_mob". */
	private static Map<String, Double> readCounts(JsonElement element) {
		Map<String, Double> counts = new HashMap<>();
		if (element == null || !element.isJsonObject()) return counts;
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			JsonElement value = entry.getValue();
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				counts.put(entry.getKey(), value.getAsDouble());
			}
		}
		return counts;
	}

	private static Category parseCategory(
		JsonObject categoryData, String categoryId, Map<String, Double> kills, Map<String, Double> deaths
	) {
		String categoryName = categoryData.get("name").getAsString();
		ItemStack categoryIcon = icon(categoryData.getAsJsonObject("icon"), categoryName);

		if (categoryData.has("hasSubcategories")) {
			List<Category> subCategories = new ArrayList<>();
			for (Map.Entry<String, JsonElement> entry : categoryData.entrySet()) {
				if (RESERVED_CATEGORY_KEYS.contains(entry.getKey()) || !entry.getValue().isJsonObject()) continue;
				subCategories.add(parseCategory(
					entry.getValue().getAsJsonObject(), categoryId + "_" + entry.getKey(), kills, deaths
				));
			}
			return new Category(
				categoryId, categoryName, categoryIcon, List.of(), subCategories, familyDataOfSubcategories(subCategories)
			);
		}

		List<Mob> mobs = new ArrayList<>();
		for (JsonElement mobElement : categoryData.getAsJsonArray("mobs")) {
			JsonObject mobData = mobElement.getAsJsonObject();
			String mobName = mobData.get("name").getAsString();
			double mobKills = 0;
			double mobDeaths = 0;
			// "mobs" lists the API ids (one per mob level) that together make up this family.
			for (JsonElement apiId : mobData.getAsJsonArray("mobs")) {
				mobKills += kills.getOrDefault(apiId.getAsString(), 0d);
				mobDeaths += deaths.getOrDefault(apiId.getAsString(), 0d);
			}
			String bracketType = mobData.has("bracketType") ? mobData.get("bracketType").getAsString() : null;
			int bracket = mobData.get("bracket").getAsInt();
			double cap = mobData.get("cap").getAsDouble();
			JsonArray apiIds = mobData.getAsJsonArray("mobs");
			Correction correction = apiIds.isEmpty() ? null : CORRECTIONS.get(apiIds.get(0).getAsString());
			if (correction != null && cap < correction.cap()) {
				cap = correction.cap();
				bracketType = correction.bracketType();
				bracket = correction.bracket();
			}
			MobLevelData levelData = calculateLevel(bracketType, bracket, mobKills, cap);
			mobs.add(new Mob(mobName, icon(mobData, mobName), mobKills, mobDeaths, levelData));
		}
		return new Category(categoryId, categoryName, categoryIcon, mobs, List.of(), familyData(mobs));
	}

	private static JsonArray bracketThresholds(String bracketType, int bracket) {
		JsonObject brackets = bracketType == null
			? Constants.BESTIARY.getAsJsonObject("brackets")
			: Constants.BESTIARY.getAsJsonObject("bracketSets").getAsJsonObject(bracketType);
		return brackets.getAsJsonArray(String.valueOf(bracket));
	}

	private static MobLevelData calculateLevel(String bracketType, int bracket, double kills, double cap) {
		JsonArray thresholds = bracketThresholds(bracketType, bracket);
		boolean maxLevel = kills >= cap;
		double effectiveKills = Math.min(kills, cap);
		double totalProgress = round1(effectiveKills / cap * 100);

		int level = 0;
		double progress = 0;
		double tierKills = 0;
		double tierReq = 0;
		for (JsonElement thresholdElement : thresholds) {
			double requiredKills = thresholdElement.getAsDouble();
			if (effectiveKills >= requiredKills) {
				level++;
			} else {
				double previousTierKills = level != 0 ? thresholds.get(level - 1).getAsDouble() : 0;
				tierKills = kills - previousTierKills;
				tierReq = requiredKills - previousTierKills;
				progress = round1(tierKills / tierReq * 100);
				break;
			}
		}
		return new MobLevelData(level, maxLevel, progress, totalProgress, new MobKillData(tierKills, tierReq, effectiveKills, cap));
	}

	private static double round1(double value) {
		return Math.round(value * 10) / 10.0;
	}

	private static FamilyData familyData(List<Mob> mobs) {
		int found = 0;
		int completed = 0;
		for (Mob mob : mobs) {
			if (mob.kills() > 0) found++;
			if (mob.levelData().maxLevel()) completed++;
		}
		return new FamilyData(found, completed, mobs.size());
	}

	private static FamilyData familyDataOfSubcategories(List<Category> subCategories) {
		int found = 0;
		int completed = 0;
		int total = 0;
		for (Category category : subCategories) {
			found += category.familyData().found();
			completed += category.familyData().completed();
			total += category.familyData().total();
		}
		return new FamilyData(found, completed, total);
	}

	/** Icon from a {@code {skullOwner, texture}} or {@code {item}} object. */
	private static ItemStack icon(JsonObject data, String name) {
		if (data.has("texture")) {
			String owner = data.has("skullOwner") ? data.get("skullOwner").getAsString() : "";
			try {
				return Utils.createSkull(name, owner, data.get("texture").getAsString());
			} catch (IllegalArgumentException e) {
				// A few repo entries have a malformed skullOwner UUID; any stable UUID works for the icon.
				String uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
				return Utils.createSkull(name, uuid, data.get("texture").getAsString());
			}
		}
		if (data.has("item")) {
			Identifier id = Identifier.tryParse(data.get("item").getAsString());
			Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			if (item != Items.AIR) return Utils.createItemStack(item, name);
		}
		return Utils.createItemStack(Items.BARRIER, name);
	}
}
