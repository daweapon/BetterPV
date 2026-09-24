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

package io.github.moulberry.notenoughupdates.profileviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.mining.MiningUi;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing helpers for the Farming, Chocolate Factory and Rift tabs. Sections have a centred title and no panel
 * behind them, like the Loadouts and Foraging tabs.
 */
public final class PvUi {

	public static final int SLOT = 18;
	public static final int TITLE = MiningUi.PANEL_TITLE;
	public static final int ROW = 11;

	private PvUi() {
	}

	public static void title(GuiGraphicsExtractor graphics, Font font, String title, int x, int y, int width) {
		RenderUtils.drawStringCentered(graphics, title, font, x + width / 2f, y + TITLE / 2f, true, 0xFFFFFF);
	}

	/** Lines of text under a title; returns the height used. */
	public static int lines(GuiGraphicsExtractor graphics, Font font, String title, List<String> lines, int x, int y, int width) {
		title(graphics, font, title, x, y, width);
		int rowY = y + TITLE + 3;
		for (String line : lines) {
			RenderUtils.text(graphics, font, line, x + 4, rowY, 0xFFFFFF, true);
			rowY += ROW;
		}
		return rowY - y;
	}

	public static int linesWidth(Font font, String title, List<String> lines) {
		int width = font.width(title) + 8;
		for (String line : lines) width = Math.max(width, font.width(line) + 8);
		return width;
	}

	public static int linesHeight(int lines) {
		return TITLE + 3 + lines * ROW;
	}

	/** An item slot; {@code stack} may be null for an empty one. Returns whether the mouse is over it. */
	public static boolean slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
		MiningUi.drawSlotBackground(graphics, x, y);
		if (stack != null) RenderUtils.drawItemStack(graphics, stack, x + 1, y + 1);
		return Utils.isWithinRect(mouseX, mouseY, x, y, SLOT, SLOT);
	}

	/** A slot tinted with {@code rgb}, as SkyBlockPv colours its rarity groups. */
	public static boolean tintedSlot(GuiGraphicsExtractor graphics, ItemStack stack, int rgb, int x, int y, int mouseX, int mouseY) {
		MiningUi.drawSlotBackground(graphics, x, y);
		graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x50000000 | rgb);
		if (stack != null) RenderUtils.drawItemStack(graphics, stack, x + 1, y + 1);
		return Utils.isWithinRect(mouseX, mouseY, x, y, SLOT, SLOT);
	}

	/** Text in a slot's bottom-right corner, like an item count. */
	public static void count(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
		RenderUtils.text(graphics, font, text, x + 18 - font.width(text), y + 10, 0xFFFFFF, true);
	}

	/** The item JSON's name and lore as a tooltip. */
	public static List<String> itemTooltip(JsonObject item) {
		List<String> tooltip = new ArrayList<>();
		String name = Utils.getElementAsString(item.get("displayname"), "");
		tooltip.add(name.isEmpty() ? "Unknown Item" : name);
		if (item.get("lore") instanceof JsonArray lore) {
			for (JsonElement line : lore) tooltip.add(line.getAsString());
		}
		return tooltip;
	}

	public static void centred(GuiGraphicsExtractor graphics, Font font, GuiProfileViewer instance, String text) {
		RenderUtils.drawStringCentered(graphics, text, font, GuiProfileViewer.getGuiLeft() + instance.sizeX / 2f,
			GuiProfileViewer.getGuiTop() + instance.sizeY / 2f, true, 0xFFFFFF);
	}
}
