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
import io.github.moulberry.notenoughupdates.profileviewer.GroupedGridPage;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Hoppity's Collection" sub-page of the chocolate factory tab, laid out like the foraging tab's attributes: every
 * rabbit grouped by rarity with a search box and a filter. Found rabbits show their rarity's head, missing ones
 * gray dye; the tooltip has how often it was found, where, and what it adds to the factory.
 */
public class RabbitsPage extends GroupedGridPage<RabbitsPage.Entry> {

	private static final String[] FILTERS = {"All", "Found", "Not Found", "Duplicates"};
	private static final int NOT_FOUND = 2;

	record Entry(Rabbits.Rabbit rabbit, long found, String location) {
	}

	private final ItemStack grayDye = new ItemStack(Items.GRAY_DYE);
	private JsonObject dataFor;
	private List<Entry> entries = List.of();

	public RabbitsPage(GuiProfileViewer instance) {
		super(instance);
	}

	@Override
	protected String status() {
		if (GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId()) == null) return "§eLoading...";
		JsonObject cf = ChocolateInfoPage.easter();
		if (cf == null) return "§cThis profile hasn't found the Chocolate Factory yet!";
		if (dataFor != cf) {
			load(cf);
			dataFor = cf;
		}
		return null;
	}

	private void load(JsonObject cf) {
		// collected_locations: location -> rabbits found there.
		Map<String, String> locations = new HashMap<>();
		if (Utils.getElement(cf, "rabbits.collected_locations") instanceof JsonObject byLocation) {
			for (Map.Entry<String, JsonElement> location : byLocation.entrySet()) {
				if (!(location.getValue() instanceof JsonArray names)) continue;
				for (JsonElement name : names) locations.putIfAbsent(name.getAsString().toLowerCase(Locale.ROOT), location.getKey());
			}
		}
		List<Entry> list = new ArrayList<>();
		for (Rabbits.Rabbit rabbit : Rabbits.all()) {
			list.add(new Entry(rabbit, Rabbits.found(cf, rabbit.id()), locations.get(rabbit.id().toLowerCase(Locale.ROOT))));
		}
		entries = list;
	}

	@Override
	protected List<Entry> entries() {
		return entries;
	}

	@Override
	protected int group(Entry entry) {
		return entry.rabbit().rarity();
	}

	@Override
	protected String[] filters() {
		return FILTERS;
	}

	@Override
	protected boolean shows(Entry entry, int filter) {
		return switch (filter) {
			case 1 -> entry.found() > 0;
			case NOT_FOUND -> entry.found() <= 0;
			case 3 -> entry.found() > 1;
			default -> true;
		};
	}

	@Override
	protected boolean matches(Entry entry, String query) {
		int rarity = entry.rabbit().rarity();
		return contains(query, entry.rabbit().id(), PvData.titleCase(entry.rabbit().id()),
			rarity < 0 ? null : PvData.RARITIES.get(rarity), entry.location() == null ? null : PvData.titleCase(entry.location()));
	}

	@Override
	protected ItemStack icon(Entry entry) {
		if (entry.found() <= 0 && filter != NOT_FOUND) return grayDye;
		int rarity = entry.rabbit().rarity();
		return ChocolateInfoPage.texture(rarity < 0 ? "COMMON" : PvData.RARITIES.get(rarity));
	}

	@Override
	protected int tint(Entry entry) {
		int colour = PvData.rarityColour(entry.rabbit().rarity());
		return entry.found() > 0 ? colour : (colour >> 1) & 0x7F7F7F;
	}

	@Override
	protected List<String> tooltip(Entry entry) {
		Rabbits.Rabbit rabbit = entry.rabbit();
		String code = PvData.rarityCode(rabbit.rarity());
		List<String> tooltip = new ArrayList<>();
		tooltip.add(code + PvData.titleCase(rabbit.id()));
		if (rabbit.rarity() >= 0) tooltip.add(code + "§l" + PvData.RARITIES.get(rabbit.rarity()) + " RABBIT");
		tooltip.add("");
		if (rabbit.chocolate() > 0) tooltip.add("§7Chocolate: §6+" + PvData.format(rabbit.chocolate()) + " §7per second");
		if (rabbit.multiplier() > 0) {
			tooltip.add("§7Chocolate Multiplier: §6+" + String.format(Locale.US, "%.3f", rabbit.multiplier()).replaceAll("0+$", "") + "x");
		}
		if (rabbit.chocolate() > 0 || rabbit.multiplier() > 0) tooltip.add("");
		tooltip.add("§7Found: " + (entry.found() > 0 ? "§aYes" : "§cNo"));
		if (entry.found() > 0) {
			tooltip.add("§7Times Found: §e" + PvData.format(entry.found()));
			if (entry.found() > 1) tooltip.add("§7Duplicates: §e" + PvData.format(entry.found() - 1));
			if (entry.location() != null) tooltip.add("§7Found At: §b" + PvData.titleCase(entry.location()));
		}
		return tooltip;
	}

	@Override
	protected String noMatch() {
		return "No Rabbit matches the input!";
	}
}
