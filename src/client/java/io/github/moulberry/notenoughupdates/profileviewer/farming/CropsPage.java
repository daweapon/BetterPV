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
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The crops sub-page: a column per crop with its best upgrade level (paid in copper) and garden milestone.
 */
public class CropsPage implements GuiProfileViewerPage {

	private static final int GAP = 4;

	private final GuiProfileViewer instance;
	private final ItemStack upgradeIcon = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
	private final ItemStack maxedUpgradeIcon = new ItemStack(Items.NETHERRACK);

	public CropsPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font font = instance.getFont();
		JsonObject garden = Garden.garden();
		String status = Garden.status(garden);
		if (status != null) {
			PvUi.centred(graphics, font, instance, status);
			return;
		}
		int count = Garden.CROPS.size();
		int width = count * 18 + (count - 1) * GAP;
		int height = PvUi.TITLE + 3 + 18 * 2;
		int left = GuiProfileViewer.getGuiLeft() + (instance.sizeX - width) / 2;
		int top = GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;
		PvUi.title(graphics, font, "Crops", left, top, width);
		top += PvUi.TITLE + 3;
		for (int i = 0; i < count; i++) {
			Garden.Crop crop = Garden.CROPS.get(i);
			int x = left + i * (18 + GAP);
			drawUpgrade(graphics, font, crop, garden, x, top, mouseX, mouseY);
			drawMilestone(graphics, font, crop, garden, x, top + 18, mouseX, mouseY);
		}
	}

	private void drawUpgrade(GuiGraphicsExtractor graphics, Font font, Garden.Crop crop, JsonObject garden, int x, int y, int mouseX, int mouseY) {
		int level = (int) PvData.getLong(garden, "crop_upgrade_levels." + crop.key());
		List<Long> costs = new ArrayList<>();
		if (Garden.repo("misc.crop_upgrade_cost") instanceof JsonArray array) for (JsonElement cost : array) costs.add(cost.getAsLong());
		int max = costs.size();
		String colour = level >= max ? "§a" : "§c";
		boolean hovered = PvUi.slot(graphics, level >= max ? maxedUpgradeIcon : upgradeIcon, x, y, mouseX, mouseY);
		PvUi.count(graphics, font, colour + level, x, y);
		if (!hovered) return;
		long paid = 0;
		long total = 0;
		for (int i = 0; i < costs.size(); i++) {
			total += costs.get(i);
			if (i < level) paid += costs.get(i);
		}
		List<String> tooltip = new ArrayList<>();
		tooltip.add("§f§lCrop Upgrade");
		tooltip.add("§7Upgrades: " + colour + level + "§7/" + max);
		String copper = "§7Copper paid: §c" + PvData.format(paid) + "§6/§c" + PvData.format(total);
		if (paid != total) copper += " §7(§e" + PvData.percent(paid, total) + "%§7)";
		tooltip.add(copper);
		instance.tooltipToDisplay = tooltip;
	}

	private void drawMilestone(GuiGraphicsExtractor graphics, Font font, Garden.Crop crop, JsonObject garden, int x, int y, int mouseX, int mouseY) {
		long collected = PvData.getLong(garden, "resources_collected." + crop.key());
		List<Long> brackets = PvData.cumulative(Garden.repo("crop_milestones." + crop.key()));
		int milestone = 0;
		for (int i = 0; i < brackets.size(); i++) if (brackets.get(i) <= collected) milestone = i;
		int max = brackets.size() - 1;
		String colour = milestone >= max ? "§a" : "§c";
		boolean hovered = PvUi.slot(graphics, PvData.item(crop.item()), x, y, mouseX, mouseY);
		PvUi.count(graphics, font, colour + milestone, x, y);
		if (!hovered) return;

		List<String> tooltip = new ArrayList<>();
		tooltip.add("§f§l" + crop.name() + " Milestone");
		tooltip.add("§7Progress: " + colour + milestone + "§7/" + max);
		if (milestone < max) {
			long into = collected - brackets.get(milestone);
			long needed = brackets.get(milestone + 1) - brackets.get(milestone);
			tooltip.add("§7Progress to " + (milestone + 1) + ": §e" + PvData.format(into) + "§6/§e" + PvData.shorten(needed) +
				" §7(§3" + PvData.percent(into, needed) + "%§7)");
			long last = brackets.get(max);
			tooltip.add("§7Total Progress: §e" + PvData.format(collected) + "§6/§e" + PvData.shorten(last) +
				" §7(§3" + PvData.percent(collected, last) + "%§7)");
		} else {
			tooltip.add("§7Total: §e" + PvData.format(collected));
		}
		instance.tooltipToDisplay = tooltip;
	}
}
