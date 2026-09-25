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

package io.github.moulberry.notenoughupdates.profileviewer.chocolate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every rabbit in Hoppity's collection with its rarity and factory bonus, from {@code constants/hoppity.json}.
 * Falls back to the bundled SkyBlockPv list (no bonuses) if the repo lacks it.
 */
final class Rabbits {

	record Rabbit(String id, int rarity, double chocolate, double multiplier) {
	}

	private static JsonObject builtFrom;
	private static List<Rabbit> rabbits = List.of();

	private Rabbits() {
	}

	static List<Rabbit> all() {
		JsonObject source = Constants.HOPPITY;
		if (rabbits.isEmpty() || builtFrom != source) {
			rabbits = source != null ? fromNeu(source) : fromBundled();
			builtFrom = source;
		}
		return rabbits;
	}

	private static List<Rabbit> fromNeu(JsonObject hoppity) {
		List<Rabbit> list = new ArrayList<>();
		JsonObject special = Utils.getElement(hoppity, "hoppity.special") instanceof JsonObject object ? object : new JsonObject();
		if (Utils.getElement(hoppity, "hoppity.rarities") instanceof JsonObject rarities) {
			for (Map.Entry<String, JsonElement> rarity : rarities.entrySet()) {
				if (!(rarity.getValue() instanceof JsonObject data) || !(data.get("rabbits") instanceof JsonArray names)) continue;
				int index = PvData.rarityIndex(rarity.getKey());
				double chocolate = Utils.getElementAsFloat(data.get("chocolate"), 0);
				double multiplier = Utils.getElementAsFloat(data.get("multiplier"), 0);
				for (JsonElement name : names) {
					String id = name.getAsString();
					JsonElement override = special.get(id);
					list.add(new Rabbit(id, index,
						override == null ? chocolate : Utils.getElementAsFloat(Utils.getElement(override, "chocolate"), 0),
						override == null ? multiplier : Utils.getElementAsFloat(Utils.getElement(override, "multiplier"), 0)));
				}
			}
		}
		return list.isEmpty() ? fromBundled() : list;
	}

	private static List<Rabbit> fromBundled() {
		List<Rabbit> list = new ArrayList<>();
		if (ChocolateInfoPage.repo().get("rabbits") instanceof JsonObject rarities) {
			for (Map.Entry<String, JsonElement> rarity : rarities.entrySet()) {
				int index = PvData.rarityIndex(rarity.getKey());
				for (JsonElement name : rarity.getValue().getAsJsonArray()) list.add(new Rabbit(name.getAsString(), index, 0, 0));
			}
		}
		return list;
	}

	/** How many times the player has found this rabbit ({@code events.easter.rabbits}), 0 if never. */
	static long found(JsonObject cf, String id) {
		JsonElement count = Utils.getElement(cf, "rabbits." + id.toLowerCase(Locale.ROOT));
		return count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber() ? count.getAsLong() : 0;
	}
}
