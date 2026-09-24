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

package io.github.moulberry.notenoughupdates.profileviewer.farming;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.util.Utils;

import java.util.List;

/**
 * Garden data shared by the farming tab's sub-pages: the crops, SkyBlockPv's garden repo data (bundled as
 * {@code profile_viewer/garden.json}) and the profile's garden from {@code v2/skyblock/garden}.
 */
public final class Garden {

	/** A garden crop: its API key (collections, contests, milestones), the item to show, and its name. */
	public record Crop(String key, String item, String name) {
	}

	public static final List<Crop> CROPS = List.of(
		new Crop("WHEAT", "WHEAT", "Wheat"),
		new Crop("PUMPKIN", "PUMPKIN", "Pumpkin"),
		new Crop("POTATO_ITEM", "POTATO_ITEM", "Potato"),
		new Crop("SUGAR_CANE", "SUGAR_CANE", "Sugar Cane"),
		new Crop("MELON", "MELON", "Melon"),
		new Crop("CARROT_ITEM", "CARROT_ITEM", "Carrot"),
		new Crop("INK_SACK:3", "INK_SACK:3", "Cocoa Beans"),
		new Crop("NETHER_STALK", "NETHER_STALK", "Nether Wart"),
		new Crop("CACTUS", "CACTUS", "Cactus"),
		new Crop("MUSHROOM_COLLECTION", "RED_MUSHROOM", "Mushroom"),
		new Crop("MOONFLOWER", "MOONFLOWER", "Moonflower"),
		new Crop("DOUBLE_PLANT", "DOUBLE_PLANT", "Sunflower"),
		new Crop("WILD_ROSE", "WILD_ROSE", "Wild Rose")
	);

	private Garden() {
	}

	public static JsonObject repo() {
		return PvData.bundled("garden");
	}

	public static JsonElement repo(String path) {
		return Utils.getElement(repo(), path);
	}

	/** The profile's garden: null while loading (see ProfileViewer.Profile#getGardenInfo). */
	public static JsonObject garden() {
		return GuiProfileViewer.getProfile().getGardenInfo(GuiProfileViewer.getProfileId());
	}

	/** Why the garden can't be shown yet, or null once it's loaded. */
	public static String status(JsonObject garden) {
		if (garden == null) return "§eLoading garden...";
		if (garden.has("__error")) {
			String cause = Utils.getElementAsString(garden.get("__cause"), null);
			return "§cCouldn't load the garden!" + (cause == null ? "" : " §7(" + cause + ")");
		}
		if (garden.isEmpty()) return "§cThis profile has no garden!";
		return null;
	}

	/** Garden level from garden experience; level 1 at 0 experience. */
	public static int level(long experience) {
		List<Long> brackets = PvData.cumulative(repo("misc.garden_level"));
		int level = 0;
		for (Long bracket : brackets) {
			if (bracket <= experience) level++;
		}
		return Math.max(1, level);
	}
}
