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

package io.github.moulberry.notenoughupdates.profileviewer;

import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * SkyBlockPv's grouped screen: entries grouped by rarity in centred rows of tinted slots, with a search box and a
 * filter button (left/right click cycles). Used by the garden visitors and mutations.
 */
public abstract class GroupedGridPage<E> implements GuiProfileViewerPage {

	private static final int SLOT = PvUi.SLOT;
	private static final int GROUP_GAP = 4;
	private static final int CONTROL_WIDTH = 100;
	private static final int CONTROL_HEIGHT = 20;
	private static final int CONTROL_GAP = 5;
	private static final int CONTROLS_TOP = 8;
	private static final int GRID_TOP = CONTROLS_TOP + CONTROL_HEIGHT + 6;
	private static final int GRID_MARGIN = 8;

	protected final GuiProfileViewer instance;
	private EditBox searchField;
	protected int filter = 0;
	private int scroll = 0;
	private String lastQuery = "";

	protected GroupedGridPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	/** A message to show instead of the grid (loading, errors), or null. */
	protected abstract String status();

	protected abstract List<E> entries();

	/** The rarity index to group by; -1 groups last. */
	protected abstract int group(E entry);

	protected abstract String[] filters();

	protected abstract boolean shows(E entry, int filter);

	protected abstract boolean matches(E entry, String query);

	protected abstract ItemStack icon(E entry);

	protected abstract int tint(E entry);

	protected abstract List<String> tooltip(E entry);

	protected abstract String noMatch();

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		scroll = 0;
	}

	protected static boolean contains(String query, String... fields) {
		for (String field : fields) {
			if (field != null && Utils.cleanColour(field).toLowerCase(Locale.ROOT).contains(query)) return true;
		}
		return false;
	}

	// ---- layout ----

	private int controlsLeft() {
		return GuiProfileViewer.getGuiLeft() + (instance.sizeX - CONTROL_WIDTH * 2 - CONTROL_GAP) / 2;
	}

	private int filterLeft() {
		return controlsLeft() + CONTROL_WIDTH + CONTROL_GAP;
	}

	private int gridLeft() {
		return GuiProfileViewer.getGuiLeft() + GRID_MARGIN;
	}

	private int gridTop() {
		return GuiProfileViewer.getGuiTop() + GRID_TOP;
	}

	private int gridWidth() {
		return instance.sizeX - GRID_MARGIN * 2;
	}

	private int gridHeight() {
		return instance.sizeY - GRID_TOP - 6;
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int guiTop = GuiProfileViewer.getGuiTop();
		if (searchField != null) {
			boolean inSearch = Utils.isWithinRect((int) mouseX, (int) mouseY, controlsLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT);
			searchField.setFocused(inSearch);
			if (inSearch) {
				if (mouseButton == 1) searchField.setValue("");
				return true;
			}
		}
		if (Utils.isWithinRect((int) mouseX, (int) mouseY, filterLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT)) {
			int count = filters().length;
			filter = (filter + (mouseButton == 1 ? -1 : 1) + count) % count;
			scroll = 0;
			RenderUtils.playPressSound();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!Utils.isWithinRect((int) mouseX, (int) mouseY, gridLeft(), gridTop(), gridWidth(), gridHeight())) return false;
		scroll = (int) Math.round(scroll - scrollY * SLOT);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return searchField != null && searchField.isFocused() && searchField.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return searchField != null && searchField.isFocused() && searchField.charTyped(event);
	}

	// ---- drawing ----

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font font = instance.getFont();
		String status = status();
		if (status != null) {
			PvUi.centred(graphics, font, instance, status);
			return;
		}
		int guiTop = GuiProfileViewer.getGuiTop();
		if (searchField == null) {
			searchField = new EditBox(font, controlsLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT, Component.literal("Search"));
			searchField.setMaxLength(64);
			searchField.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
		}
		searchField.setX(controlsLeft());
		searchField.setY(guiTop + CONTROLS_TOP);
		searchField.extractWidgetRenderState(graphics, mouseX, mouseY, partialTicks);
		String query = searchField.getValue().trim().toLowerCase(Locale.ROOT);
		if (!query.equals(lastQuery)) {
			lastQuery = query;
			scroll = 0;
		}
		drawFilterButton(graphics, font, mouseX, mouseY);

		// Rarest groups last, unknown ones after them.
		Map<Integer, List<E>> groups = new TreeMap<>((a, b) -> (a < 0 ? 100 : a) - (b < 0 ? 100 : b));
		for (E entry : entries()) {
			if ((query.isEmpty() || matches(entry, query)) && shows(entry, filter)) {
				groups.computeIfAbsent(group(entry), key -> new ArrayList<>()).add(entry);
			}
		}

		int left = gridLeft();
		int top = gridTop();
		int columns = gridWidth() / SLOT;
		int contentHeight = -GROUP_GAP;
		for (List<E> group : groups.values()) contentHeight += (group.size() + columns - 1) / columns * SLOT + GROUP_GAP;
		if (groups.isEmpty()) {
			RenderUtils.drawStringCentered(graphics, "§c" + noMatch(), font, left + gridWidth() / 2f, top + gridHeight() / 2f, true, 0xFFFFFF);
			return;
		}
		int maxScroll = Math.max(0, contentHeight - gridHeight());
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		boolean hoveringGrid = Utils.isWithinRect(mouseX, mouseY, left, top, gridWidth(), gridHeight());

		int y = top - scroll + Math.max(0, (gridHeight() - contentHeight) / 2);
		graphics.enableScissor(left, top, left + gridWidth(), top + gridHeight());
		for (List<E> group : groups.values()) {
			for (int start = 0; start < group.size(); start += columns) {
				int count = Math.min(columns, group.size() - start);
				int x = left + (gridWidth() - count * SLOT) / 2;
				if (y + SLOT >= top && y <= top + gridHeight()) {
					for (int i = 0; i < count; i++) {
						E entry = group.get(start + i);
						if (PvUi.tintedSlot(graphics, icon(entry), tint(entry), x + i * SLOT, y, mouseX, mouseY) && hoveringGrid) {
							instance.tooltipToDisplay = tooltip(entry);
						}
					}
				}
				y += SLOT;
			}
			y += GROUP_GAP;
		}
		graphics.disableScissor();

		if (maxScroll > 0) {
			int barX = left + gridWidth() + 3;
			int barHeight = Math.max(10, gridHeight() * gridHeight() / contentHeight);
			int barY = top + (gridHeight() - barHeight) * scroll / maxScroll;
			graphics.fill(barX, top, barX + 2, top + gridHeight(), 0x40000000);
			graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFFAAAAAA);
		}
	}

	private void drawFilterButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		int x = filterLeft();
		int y = GuiProfileViewer.getGuiTop() + CONTROLS_TOP;
		boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, y, CONTROL_WIDTH, CONTROL_HEIGHT);
		graphics.fill(x, y, x + CONTROL_WIDTH, y + CONTROL_HEIGHT, hovered ? 0xFFAAAAAA : 0xFF555555);
		graphics.fill(x + 1, y + 1, x + CONTROL_WIDTH - 1, y + CONTROL_HEIGHT - 1, hovered ? 0xFF3A3A3A : 0xFF2A2A2A);
		RenderUtils.drawStringCentered(graphics, "Filter", font, x + CONTROL_WIDTH / 2f, y + CONTROL_HEIGHT / 2f, true, 0xFFFFFF);
		if (hovered) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add("§7Click to cycle through the filters!");
			tooltip.add("§8Left Click / Right Click");
			tooltip.add("");
			String[] filters = filters();
			for (int i = 0; i < filters.length; i++) {
				tooltip.add(i == filter ? "§7> §a" + filters[i] : " §c" + filters[i]);
			}
			instance.tooltipToDisplay = tooltip;
		}
	}
}
