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

package io.github.moulberry.notenoughupdates.profileviewer.info;

import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Direct port of the Forge 1.8.9 {@code profileviewer.info.QuiverInfo}; only the chat-formatting import changed
 * (old {@code net.minecraft.util.EnumChatFormatting} -&gt; new {@code net.minecraft.ChatFormatting}, identical
 * constant names).
 */
public class QuiverInfo {
	public String selectedArrow;
	public Map<String, Integer> arrows;

	public List<String> generateProfileViewerTooltip() {
		List<String> list = new ArrayList<>();
		int totalCount = 0;
		list.add(ChatFormatting.AQUA + "Quiver:");
		for (Map.Entry<String, Integer> arrow : arrows.entrySet()) {
			JsonObject repoInfo = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(arrow.getKey());
			if (repoInfo == null || repoInfo.isJsonNull()) {
				continue;
			}
			list.add(
				"  " + repoInfo.get("displayname").getAsString() + ChatFormatting.RESET + ": " + ChatFormatting.GREEN +
					ChatFormatting.BOLD + arrow.getValue());
			totalCount += arrow.getValue();
		}

		list.add("");
		list.add(ChatFormatting.AQUA + "Total: " + ChatFormatting.GREEN + ChatFormatting.BOLD + totalCount);
		if (selectedArrow != null) {
			JsonObject repoInfo = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(selectedArrow);
			if (repoInfo == null) {
				list.add(ChatFormatting.AQUA + "Selected Arrow: " + ChatFormatting.RED + "ERROR");
			} else {
				list.add(ChatFormatting.AQUA + "Selected Arrow: " + repoInfo.get("displayname").getAsString());
			}
		}

		return list;
	}
}
