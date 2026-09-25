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

package io.github.moulberry.notenoughupdates.profileviewer.chocolate;

import io.github.moulberry.notenoughupdates.profileviewer.VanillaItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Factions" sub-page of the chocolate factory tab, as SkyBlockPv's {@code FactionCfScreen}: the rabbits of each of
 * the four factions by rarity, the rarity's rabbit head when found and gray dye when not. The player's faction is shown in green with its
 * level.
 */
public class FactionsPage implements GuiProfileViewerPage {

	private static final String[] FACTIONS = {"city", "mountain", "country", "beach"};
	private static final int GAP = 12;
	private static final int WIDTH = 5 * 18;

	private final GuiProfileViewer instance;
	private final Map<Integer, ItemStack> heads = new HashMap<>();
	private final ItemStack missing = new ItemStack(VanillaItems.GRAY_DYE);

	public FactionsPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	private static ItemStack head(int rarity) {
		return ChocolateInfoPage.texture(rarity < 0 ? "COMMON" : PvData.RARITIES.get(rarity));
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font font = instance.getFont();
		if (GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId()) == null) return;
		JsonObject cf = ChocolateInfoPage.easter();
		if (cf == null) {
			PvUi.centred(graphics, font, instance, "§cThis profile hasn't found the Chocolate Factory yet!");
			return;
		}
		JsonObject rabbits = cf.get("rabbits") instanceof JsonObject object ? object : new JsonObject();
		String selected = Utils.getElementAsString(rabbits.get("selected_faction"), "");
		long factionLevel = PvData.getLong(rabbits, "faction_level");

		int height = PvUi.TITLE + 3 + 5 * 18;
		int x = GuiProfileViewer.getGuiLeft() + (instance.sizeX - FACTIONS.length * WIDTH - (FACTIONS.length - 1) * GAP) / 2;
		int y = GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;
		for (String faction : FACTIONS) {
			boolean isSelected = faction.equalsIgnoreCase(selected);
			PvUi.title(graphics, font, isSelected ? "§2" + PvData.titleCase(faction) + " (" + factionLevel + ")"
				: PvData.titleCase(faction), x, y, WIDTH);
			if (Utils.getElement(ChocolateInfoPage.repo(), "factions." + faction) instanceof JsonObject tiers) {
				List<Map.Entry<String, JsonElement>> rarities = new ArrayList<>(tiers.entrySet());
				rarities.sort(Comparator.comparingInt((Map.Entry<String, JsonElement> entry) -> PvData.rarityIndex(entry.getKey())).reversed());
				int rowY = y + PvUi.TITLE + 3;
				for (Map.Entry<String, JsonElement> rarity : rarities) {
					List<String> names = new ArrayList<>();
					if (rarity.getValue() instanceof JsonArray array) for (JsonElement name : array) names.add(name.getAsString());
					else names.add(rarity.getValue().getAsString());
					int rarityIndex = PvData.rarityIndex(rarity.getKey());
					int rowX = x + (WIDTH - names.size() * 18) / 2;
					for (int i = 0; i < names.size(); i++) {
						String name = names.get(i);
						JsonElement count = rabbits.get(name.toLowerCase(Locale.ROOT));
						boolean isFound = count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber();
						if (PvUi.tintedSlot(graphics, isFound ? heads.computeIfAbsent(rarityIndex, FactionsPage::head) : missing, PvData.rarityColour(rarityIndex),
							rowX + i * 18, rowY, mouseX, mouseY)) {
							List<String> tooltip = new ArrayList<>();
							tooltip.add(PvData.rarityCode(rarityIndex) + PvData.titleCase(name));
							tooltip.add("§7Found: " + (isFound ? "§aYes" : "§cNo"));
							if (isFound) tooltip.add("§7Total Found: §e" + PvData.format(count.getAsLong()));
							instance.tooltipToDisplay = tooltip;
						}
					}
					rowY += 18;
				}
			}
			x += WIDTH + GAP;
		}
	}
}
