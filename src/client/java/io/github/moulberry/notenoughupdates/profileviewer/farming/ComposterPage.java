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
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The composter sub-page: garden plots around the barn, what the composter holds, greenhouse upgrades and
 * composter upgrades with their costs.
 */
public class ComposterPage implements GuiProfileViewerPage {

	private static final String[] UPGRADES = {"SPEED", "MULTI_DROP", "FUEL_CAP", "ORGANIC_MATTER_CAP", "COST_REDUCTION"};
	private static final String[] GREENHOUSE = {"GROWTH_SPEED", "YIELD", "PLOT_LIMIT"};
	private static final List<String> RARE_CROPS = List.of("CROPIE", "SQUASH", "FERMENTO", "CONDENSED_FERMENTO");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.US);
	private static final DecimalFormat DECIMAL = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
	private static final int GAP = 12;

	private record Line(String text, List<String> tooltip) {
	}

	private final GuiProfileViewer instance;
	private final ItemStack unlocked = new ItemStack(VanillaItems.LIME_STAINED_GLASS_PANE);
	private final ItemStack locked = new ItemStack(VanillaItems.BLACK_STAINED_GLASS_PANE);

	public ComposterPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
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

		List<Line> information = information(garden);
		int infoWidth = font.width("Information") + 8;
		for (Line line : information) infoWidth = Math.max(infoWidth, font.width(line.text()) + 8);
		int plotsWidth = 18 * 5;
		int upgradesTop = PvUi.linesHeight(information.size()) + 8;
		int height = Math.max(PvUi.TITLE + 3 + 90, upgradesTop + PvUi.TITLE + 3 + 18);
		int left = GuiProfileViewer.getGuiLeft() + (instance.sizeX - plotsWidth - GAP - infoWidth) / 2;
		int top = GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;

		drawPlots(graphics, font, garden, left, top, mouseX, mouseY);

		int x = left + plotsWidth + GAP;
		PvUi.title(graphics, font, "Information", x, top, infoWidth);
		int rowY = top + PvUi.TITLE + 3;
		for (Line line : information) {
			RenderUtils.text(graphics, font, line.text(), x + 4, rowY, 0xFFFFFF, true);
			if (line.tooltip() != null && Utils.isWithinRect(mouseX, mouseY, x + 4, rowY - 1, font.width(line.text()), PvUi.ROW)) {
				instance.tooltipToDisplay = line.tooltip();
			}
			rowY += PvUi.ROW;
		}

		int upgradesWidth = UPGRADES.length * 18;
		int upgradesX = x + (infoWidth - upgradesWidth) / 2;
		int upgradesY = top + upgradesTop;
		PvUi.title(graphics, font, "Upgrades", upgradesX, upgradesY, upgradesWidth);
		for (int i = 0; i < UPGRADES.length; i++) {
			drawUpgrade(graphics, font, garden, UPGRADES[i], upgradesX + i * 18, upgradesY + PvUi.TITLE + 3, mouseX, mouseY);
		}
	}

	// ---- plots ----

	private void drawPlots(GuiGraphicsExtractor graphics, Font font, JsonObject garden, int x, int y, int mouseX, int mouseY) {
		PvUi.title(graphics, font, "Plots", x, y, 18 * 5);
		int top = y + PvUi.TITLE + 3;
		List<String> unlockedIds = new ArrayList<>();
		if (garden.get("unlocked_plots_ids") instanceof JsonArray array) for (JsonElement id : array) unlockedIds.add(id.getAsString());
		Map<String, Integer> unlockedByType = new HashMap<>();
		for (String id : unlockedIds) unlockedByType.merge(type(id), 1, Integer::sum);

		if (Garden.repo("plots") instanceof JsonArray plots) {
			for (JsonElement element : plots) {
				if (!(element instanceof JsonObject plot) || !(plot.get("location") instanceof JsonArray location)) continue;
				String id = Utils.getElementAsString(plot.get("id"), "");
				// SkyBlockPv's plot grid: x is the column, y counts up from the bottom row.
				int slotX = x + location.get(0).getAsInt() * 18;
				int slotY = top + (4 - location.get(1).getAsInt()) * 18;
				boolean isUnlocked = unlockedIds.contains(id);
				if (!PvUi.slot(graphics, isUnlocked ? unlocked : locked, slotX, slotY, mouseX, mouseY)) continue;

				String name = "§ePlot §7- §b" + Utils.getElementAsString(plot.get("number"), "?");
				List<String> tooltip = new ArrayList<>();
				if (isUnlocked) {
					tooltip.add("§aPlot §7- §b" + Utils.getElementAsString(plot.get("number"), "?"));
				} else {
					tooltip.add(name);
					int owned = unlockedByType.getOrDefault(type(id), 0);
					if (Garden.repo("plot_cost." + type(id)) instanceof JsonArray costs && owned < costs.size()) {
						JsonElement cost = costs.get(owned);
						boolean bundle = cost instanceof JsonObject object && object.has("bundle") && object.get("bundle").getAsBoolean();
						long amount = cost instanceof JsonObject object ? PvData.asLong(object.get("amount"), 0) : PvData.asLong(cost, 0);
						tooltip.add(PvData.itemName(bundle ? "ENCHANTED_COMPOST" : "COMPOST") + " §8x" + amount);
					}
				}
				instance.tooltipToDisplay = tooltip;
			}
		}

		// The barn, with the selected skin, in the middle.
		String skin = Utils.getElementAsString(garden.get("selected_barn_skin"), null);
		ItemStack barn;
		String barnName;
		if (skin != null && Garden.repo("barn_skins." + skin) instanceof JsonObject known) {
			barn = PvData.item(Utils.getElementAsString(known.get("item"), "barrier"));
			barnName = PvData.tags(Utils.getElementAsString(known.get("displayname"), skin));
		} else if (skin != null) {
			barn = PvData.item(skin + "_BARN_SKIN");
			barnName = PvData.itemName(skin + "_BARN_SKIN");
		} else {
			barn = new ItemStack(Items.BARRIER);
			barnName = "§cUnknown";
		}
		if (PvUi.slot(graphics, barn, x + 36, top + 36, mouseX, mouseY)) instance.tooltipToDisplay = List.of(barnName);
	}

	private static String type(String plotId) {
		int underscore = plotId.indexOf('_');
		return underscore < 0 ? plotId : plotId.substring(0, underscore);
	}

	// ---- information ----

	private static List<Line> information(JsonObject garden) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line("§7Organic Matter Stored: §a" + decimal(garden, "composter_data.organic_matter"), null));
		lines.add(new Line("§7Fuel Stored: §2" + decimal(garden, "composter_data.fuel_units"), null));
		lines.add(new Line("§7Compost Stored: §c" + PvData.format(PvData.getLong(garden, "composter_data.compost_items")), null));
		long lastSave = PvData.getLong(garden, "composter_data.last_save");
		lines.add(new Line("§7Last Update: §8" + (lastSave <= 0 ? "Never"
			: DATE.format(Instant.ofEpochMilli(lastSave).atZone(ZoneId.systemDefault()))), null));
		int slots = garden.get("greenhouse_slots") instanceof JsonArray array ? array.size() : 0;
		lines.add(new Line("§7Greenhouse spaces: §f" + (slots + 12) + "§7/100", null));

		for (String key : GREENHOUSE) {
			if (!(Garden.repo("greenhouse_upgrades." + key) instanceof JsonObject data)) continue;
			int level = (int) PvData.getLong(garden, "garden_upgrades." + key);
			List<Map<String, Long>> costs = PvData.cumulativeCosts(data.get("upgrades"));
			String name = PvData.tags(Utils.getElementAsString(data.get("name"), key));
			List<String> tooltip = new ArrayList<>();
			tooltip.add(name);
			tooltip.add("");
			tooltip.addAll(reward(data, level));
			tooltip.add("");
			tooltip.addAll(PvData.costLines(level, costs));
			lines.add(new Line("§7" + Utils.cleanColour(name) + ": " + (level >= costs.size() ? "§a" : "§c") + level + "§7/" + costs.size(), tooltip));
		}
		return lines;
	}

	private static String decimal(JsonObject root, String path) {
		JsonElement element = Utils.getElement(root, path);
		return DECIMAL.format(element != null && element.isJsonPrimitive() ? element.getAsDouble() : 0);
	}

	/** The upgrade's tooltip text with this level's reward filled in. */
	private static List<String> reward(JsonObject data, int level) {
		double reward = PvData.evaluate(Utils.getElementAsString(data.get("reward_formula"), "level"), level);
		String text = Utils.getElementAsString(data.get("tooltip"), "").replace("%reward%", PvData.format(reward));
		return List.of(PvData.tags(text).split("\n"));
	}

	// ---- composter upgrades ----

	private void drawUpgrade(GuiGraphicsExtractor graphics, Font font, JsonObject garden, String key, int x, int y, int mouseX, int mouseY) {
		if (!(Garden.repo("composter_data." + key) instanceof JsonObject data)) return;
		int level = (int) PvData.getLong(garden, "composter_data.upgrades." + key.toLowerCase(Locale.ROOT));
		List<Map<String, Long>> costs = PvData.cumulativeCosts(data.get("upgrades"));
		boolean hovered = PvUi.slot(graphics, PvData.item(Utils.getElementAsString(data.get("item"), "barrier")), x, y, mouseX, mouseY);
		PvUi.count(graphics, font, (level >= costs.size() ? "§a" : "§f") + level, x, y);
		if (!hovered) return;

		List<String> tooltip = new ArrayList<>();
		tooltip.add("§a§l" + PvData.tags(Utils.getElementAsString(data.get("name"), key)));
		tooltip.addAll(reward(data, level));
		tooltip.add("");
		tooltip.add("§7Level §a" + level + "§2/§a" + costs.size());

		// The cost lines split into copper, crops and rare crops, like SkyBlockPv.
		List<Map<String, Long>> copper = new ArrayList<>();
		List<Map<String, Long>> crops = new ArrayList<>();
		List<Map<String, Long>> rare = new ArrayList<>();
		for (Map<String, Long> levelCosts : costs) {
			Map<String, Long> copperCosts = new HashMap<>();
			Map<String, Long> cropCosts = new java.util.LinkedHashMap<>();
			Map<String, Long> rareCosts = new java.util.LinkedHashMap<>();
			for (Map.Entry<String, Long> cost : levelCosts.entrySet()) {
				if (cost.getKey().equalsIgnoreCase("copper")) copperCosts.put("copper", cost.getValue());
				else if (RARE_CROPS.contains(cost.getKey())) rareCosts.put(cost.getKey(), cost.getValue());
				else cropCosts.put(cost.getKey(), cost.getValue());
			}
			copper.add(copperCosts);
			crops.add(cropCosts);
			rare.add(rareCosts);
		}
		tooltip.add("");
		tooltip.add("§7Copper needed");
		tooltip.addAll(PvData.costLines(level, copper));
		tooltip.add("");
		tooltip.add("§7Crops needed");
		tooltip.addAll(PvData.costLines(level, crops));
		tooltip.add("");
		tooltip.add("§7Rare Crops needed");
		tooltip.addAll(PvData.costLines(level, rare));
		instance.tooltipToDisplay = tooltip;
	}
}
