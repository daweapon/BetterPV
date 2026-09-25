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

package io.github.moulberry.notenoughupdates.profileviewer.rift;

import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The rift's inventories ({@code rift.inventory}), decoded once per profile load, and slots drawn from them. */
final class RiftItems {

	private static JsonObject decodedFor;
	private static List<JsonObject> inventory = List.of();
	private static List<JsonObject> armor = List.of();
	private static List<JsonObject> equipment = List.of();
	private static List<JsonObject> enderChest = List.of();
	private static final Map<JsonObject, ItemStack> STACKS = new IdentityHashMap<>();

	private RiftItems() {
	}

	/** Decodes the rift inventories for the shown profile; false if it has none. */
	static boolean load() {
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInfo == null || !(Utils.getElement(profileInfo, "rift.inventory") instanceof JsonObject rift)) return false;
		if (decodedFor != profileInfo) {
			var profile = GuiProfileViewer.getProfile();
			inventory = profile.decodeItems(rift.get("inv_contents"));
			armor = profile.decodeItems(rift.get("inv_armor"));
			equipment = profile.decodeItems(rift.get("equipment_contents"));
			enderChest = profile.decodeItems(rift.get("ender_chest_contents"));
			STACKS.clear();
			decodedFor = profileInfo;
		}
		return true;
	}

	static List<JsonObject> inventory() {
		return inventory;
	}

	static List<JsonObject> armor() {
		return armor;
	}

	static List<JsonObject> equipment() {
		return equipment;
	}

	static List<JsonObject> enderChest() {
		return enderChest;
	}

	static JsonObject get(List<JsonObject> items, int index) {
		return index >= 0 && index < items.size() ? items.get(index) : null;
	}

	/** An item slot; sets the tooltip when hovered. */
	static void slot(GuiProfileViewer instance, GuiGraphicsExtractor graphics, JsonObject item, int x, int y, int mouseX, int mouseY) {
		ItemStack stack = item == null ? null : STACKS.computeIfAbsent(item, json -> NotEnoughUpdates.INSTANCE.manager.jsonToStack(json, false));
		if (PvUi.slot(graphics, stack, x, y, mouseX, mouseY) && item != null) instance.tooltipToDisplay = PvUi.itemTooltip(item);
	}
}
