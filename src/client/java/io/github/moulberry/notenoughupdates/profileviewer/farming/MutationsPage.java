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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GroupedGridPage;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Mutations" sub-page of the farming tab, as SkyBlockPv's {@code MutationScreen}: every greenhouse mutation grouped
 * by rarity, and whether the player has discovered and analyzed it ({@code garden_player_data}).
 */
public class MutationsPage extends GroupedGridPage<MutationsPage.Mutation> {

	private static final String[] FILTERS = {"All", "Undiscovered", "Discovered", "Discovered (not analyzed)", "Analyzed"};
	private static final int UNDISCOVERED = 1;

	/** {@code known} is false for a mutation the repo doesn't list; {@code analyzable} is false for ones that can't be. */
	record Mutation(String id, String name, int rarity, boolean known, boolean analyzable, boolean discovered, boolean analyzed) {
	}

	private final ItemStack grayDye = new ItemStack(Items.GRAY_DYE);
	private final ItemStack unknown = new ItemStack(Items.BARRIER);
	private JsonObject dataFor;
	private List<Mutation> mutations = List.of();

	public MutationsPage(GuiProfileViewer instance) {
		super(instance);
	}

	@Override
	protected String status() {
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInfo == null) return "§eLoading...";
		if (dataFor != profileInfo) {
			load(profileInfo);
			dataFor = profileInfo;
		}
		return null;
	}

	private void load(JsonObject profileInfo) {
		Set<String> analyzed = ids(Utils.getElement(profileInfo, "garden_player_data.analyzed_greenhouse_crops"));
		Set<String> discovered = ids(Utils.getElement(profileInfo, "garden_player_data.discovered_greenhouse_crops"));
		List<Mutation> list = new ArrayList<>();
		Set<String> known = new HashSet<>();
		if (Garden.repo("mutations") instanceof JsonArray repo) {
			for (JsonElement element : repo) {
				if (!(element instanceof JsonObject mutation)) continue;
				String id = Utils.getElementAsString(mutation.get("id"), "").toUpperCase(Locale.ROOT);
				known.add(id);
				boolean analyzable = !(mutation.get("analyzable") instanceof JsonElement flag && flag.isJsonPrimitive() && !flag.getAsBoolean());
				list.add(new Mutation(id, Utils.getElementAsString(mutation.get("name"), id),
					PvData.rarityIndex(Utils.getElementAsString(mutation.get("rarity"), "")), true, analyzable,
					discovered.contains(id), analyzed.contains(id) || !analyzable));
			}
		}
		Set<String> extra = new LinkedHashSet<>(discovered);
		extra.addAll(analyzed);
		for (String id : extra) {
			if (!known.contains(id)) {
				list.add(new Mutation(id, PvData.titleCase(id), -1, false, true, discovered.contains(id), analyzed.contains(id)));
			}
		}
		mutations = list;
	}

	private static Set<String> ids(JsonElement element) {
		Set<String> ids = new HashSet<>();
		if (element instanceof JsonArray array) {
			for (JsonElement id : array) ids.add(id.getAsString().toUpperCase(Locale.ROOT));
		}
		return ids;
	}

	@Override
	protected List<Mutation> entries() {
		return mutations;
	}

	@Override
	protected int group(Mutation entry) {
		return entry.rarity();
	}

	@Override
	protected String[] filters() {
		return FILTERS;
	}

	@Override
	protected boolean shows(Mutation entry, int filter) {
		return switch (filter) {
			case UNDISCOVERED -> !entry.discovered();
			case 2 -> entry.discovered();
			case 3 -> entry.discovered() && !entry.analyzed();
			case 4 -> entry.analyzed();
			default -> true;
		};
	}

	@Override
	protected boolean matches(Mutation entry, String query) {
		return contains(query, entry.name(), entry.id(), entry.rarity() < 0 ? null : PvData.RARITIES.get(entry.rarity()));
	}

	@Override
	protected ItemStack icon(Mutation entry) {
		if (entry.discovered() && filter != UNDISCOVERED) return PvData.item(entry.id());
		return entry.known() ? grayDye : unknown;
	}

	@Override
	protected int tint(Mutation entry) {
		int colour = PvData.rarityColour(entry.rarity());
		return entry.discovered() ? colour : (colour >> 1) & 0x7F7F7F;
	}

	@Override
	protected List<String> tooltip(Mutation entry) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add(PvData.repoItem(entry.id()) != null ? PvData.itemName(entry.id()) : PvData.rarityCode(entry.rarity()) + entry.name());
		tooltip.add("");
		tooltip.add("§7Discovered: " + (entry.discovered() ? "§aYes" : "§cNo"));
		tooltip.add("§7Analyzed: " + (!entry.analyzable() ? "§6N/A" : entry.analyzed() ? "§aYes" : "§cNo"));
		return tooltip;
	}

	@Override
	protected String noMatch() {
		return "No mutation matches the input!";
	}
}
