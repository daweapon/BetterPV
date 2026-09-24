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

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Holds the NEU repo's "constants" JSON files (leveling curves, pet data, item weight formulas, etc.), same
 * field set as the Forge 1.8.9 {@code util.Constants}.
 *
 * TODO(fabric-port): the original loaded these by first downloading/updating a zip of
 * https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO into {@code NEUManager#repoLocation} (see
 * {@code NEUManager.loadRepository}/{@code updateRepo} in the Forge source), then reading
 * {@code <repo>/constants/<name>.json}. That network-sync machinery is out of scope for this pass (it's tied up
 * with the mod's config/update-notification system). For now, {@link #load(File, Gson)} only reads local files
 * out of {@code <repoLocation>/constants/*.json} if they happen to already exist there; if the repo hasn't been
 * synced, every field below simply stays {@code null} and callers already null-check them (see
 * {@code ProfileViewer.Profile#getSkyblockInfo}, which bails out via {@code Utils.showOutdatedRepoNotification()}
 * equivalent handling when {@code LEVELING} is null).
 */
public class Constants {
	public static JsonObject BONUSES;
	public static JsonObject DISABLE;
	public static JsonObject ENCHANTS;
	public static JsonObject LEVELING;
	public static JsonObject MISC;
	public static JsonObject PETNUMS;
	public static JsonObject PETS;
	public static JsonObject PARENTS;
	public static JsonObject ESSENCECOSTS;
	public static JsonObject FAIRYSOULS;
	public static JsonObject REFORGESTONES;
	public static JsonObject TROPHYFISH;
	public static JsonObject WEIGHT;
	public static JsonObject RNGSCORE;
	public static JsonObject BESTIARY;
	public static JsonObject HOTMLAYOUT;
	public static JsonObject ATTRIBUTE_SHARDS;
	public static JsonObject MUSEUM;

	private Constants() {
	}

	/**
	 * Best-effort local load of the repo constants. See class javadoc for what's simplified here vs. the
	 * original Forge implementation.
	 */
	public static void load(File repoLocation, Gson gson) {
		BONUSES = readConstant(repoLocation, "bonuses", gson);
		DISABLE = readConstant(repoLocation, "disabled", gson);
		ENCHANTS = readConstant(repoLocation, "enchants", gson);
		LEVELING = readConstant(repoLocation, "leveling", gson);
		MISC = readConstant(repoLocation, "misc", gson);
		PETNUMS = readConstant(repoLocation, "petnums", gson);
		PETS = readConstant(repoLocation, "pets", gson);
		PARENTS = readConstant(repoLocation, "parents", gson);
		ESSENCECOSTS = readConstant(repoLocation, "essencecosts", gson);
		FAIRYSOULS = readConstant(repoLocation, "fairysouls", gson);
		REFORGESTONES = readConstant(repoLocation, "reforgestones", gson);
		TROPHYFISH = readConstant(repoLocation, "trophyfish", gson);
		WEIGHT = readConstant(repoLocation, "weight", gson);
		RNGSCORE = readConstant(repoLocation, "rngscore", gson);
		BESTIARY = readConstant(repoLocation, "bestiary", gson);
		HOTMLAYOUT = readConstant(repoLocation, "hotmlayout", gson);
		ATTRIBUTE_SHARDS = readConstant(repoLocation, "attribute_shards", gson);
		MUSEUM = readConstant(repoLocation, "museum", gson);
	}

	private static JsonObject readConstant(File repoLocation, String constant, Gson gson) {
		if (repoLocation == null || !repoLocation.exists()) return null;
		File jsonFile = new File(repoLocation, "constants/" + constant + ".json");
		if (!jsonFile.exists()) return null;
		try (
			BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(jsonFile),
				StandardCharsets.UTF_8
			))
		) {
			return gson.fromJson(reader, JsonObject.class);
		} catch (Exception e) {
			return null;
		}
	}
}
