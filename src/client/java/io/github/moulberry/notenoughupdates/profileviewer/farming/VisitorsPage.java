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

import io.github.moulberry.notenoughupdates.profileviewer.VanillaItems;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The visitors sub-page: every garden visitor by rarity with visit and accepted-offer counts (from
 * {@code garden.json} and the garden's {@code commission_data}).
 */
public class VisitorsPage extends GroupedGridPage<VisitorsPage.Visitor> {

	private static final String[] FILTERS = {"All", "Never Visited", "Visited", "Visited (None Completed)", "Offer Completed"};
	private static final int NEVER_VISITED = 1;

	/** A visitor; {@code repo} is null for one the repo doesn't know yet. */
	record Visitor(String id, String name, int rarity, JsonObject repo, int visits, int accepted) {
	}

	private final ItemStack grayDye = new ItemStack(VanillaItems.GRAY_DYE);
	private final ItemStack unknown = new ItemStack(Items.BARRIER);
	private JsonObject dataFor;
	private List<Visitor> visitors = List.of();
	private String summary;

	public VisitorsPage(GuiProfileViewer instance) {
		super(instance);
	}

	@Override
	protected String status() {
		JsonObject garden = Garden.garden();
		String status = Garden.status(garden);
		if (status == null && dataFor != garden) {
			load(garden);
			dataFor = garden;
		}
		return status;
	}

	private void load(JsonObject garden) {
		JsonObject visits = Utils.getElement(garden, "commission_data.visits") instanceof JsonObject object ? object : new JsonObject();
		JsonObject completed = Utils.getElement(garden, "commission_data.completed") instanceof JsonObject object ? object : new JsonObject();
		List<Visitor> list = new ArrayList<>();
		Set<String> known = new HashSet<>();
		if (Garden.repo("visitors") instanceof JsonArray repo) {
			for (JsonElement element : repo) {
				if (!(element instanceof JsonObject visitor)) continue;
				String id = Utils.getElementAsString(visitor.get("id"), "");
				known.add(id);
				list.add(new Visitor(id, Utils.getElementAsString(visitor.get("name"), id),
					PvData.rarityIndex(Utils.getElementAsString(visitor.get("rarity"), "")), visitor,
					(int) PvData.asLong(visits.get(id), 0), (int) PvData.asLong(completed.get(id), 0)));
			}
		}
		for (Map.Entry<String, JsonElement> visit : visits.entrySet()) {
			if (known.contains(visit.getKey())) continue;
			list.add(new Visitor(visit.getKey(), PvData.titleCase(visit.getKey()), -1, null,
				(int) PvData.asLong(visit.getValue(), 0), (int) PvData.asLong(completed.get(visit.getKey()), 0)));
		}
		visitors = list;
		summary = "§7Offers Accepted: §a" + PvData.format(PvData.getLong(garden, "commission_data.total_completed")) +
			" §7- Unique Visitors Served: §a" + PvData.format(PvData.getLong(garden, "commission_data.unique_npcs_served"));
	}

	@Override
	protected List<Visitor> entries() {
		return visitors;
	}

	@Override
	protected int group(Visitor entry) {
		return entry.rarity();
	}

	@Override
	protected String[] filters() {
		return FILTERS;
	}

	@Override
	protected boolean shows(Visitor entry, int filter) {
		return switch (filter) {
			case NEVER_VISITED -> entry.visits() <= 0;
			case 2 -> entry.visits() >= 1;
			case 3 -> entry.visits() >= 1 && entry.accepted() <= 0;
			case 4 -> entry.accepted() >= 1;
			default -> true;
		};
	}

	@Override
	protected boolean matches(Visitor entry, String query) {
		return contains(query, entry.name(), entry.id(), entry.rarity() < 0 ? null : PvData.RARITIES.get(entry.rarity()));
	}

	@Override
	protected ItemStack icon(Visitor entry) {
		if (entry.repo() == null) return unknown;
		if (entry.visits() <= 0 && filter != NEVER_VISITED) return grayDye;
		String skin = Utils.getElementAsString(entry.repo().get("skin"), null);
		return skin != null ? PvData.skull(skin) : PvData.item(Utils.getElementAsString(entry.repo().get("item"), "player_head"));
	}

	@Override
	protected int tint(Visitor entry) {
		int colour = PvData.rarityColour(entry.rarity());
		// Never-visited visitors get a darker tint, as in SkyBlockPv.
		return entry.visits() > 0 ? colour : (colour >> 1) & 0x7F7F7F;
	}

	@Override
	protected List<String> tooltip(Visitor entry) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add(PvData.rarityCode(entry.rarity()) + entry.name());
		tooltip.add("");
		if (entry.visits() <= 0) {
			tooltip.add("§cNever visited!");
		} else {
			tooltip.add("§7Visits: §a" + PvData.format(entry.visits()));
			tooltip.add("§7Accepted: §a" + PvData.format(entry.accepted()));
			tooltip.add("§7Rejected: §a" + PvData.format(Math.max(0, entry.visits() - entry.accepted())));
		}
		if (summary != null) {
			tooltip.add("");
			tooltip.add(summary);
		}
		return tooltip;
	}

	@Override
	protected String noMatch() {
		return "No Visitor matches the input!";
	}
}
