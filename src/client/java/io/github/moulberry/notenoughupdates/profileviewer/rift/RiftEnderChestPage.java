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

package io.github.moulberry.notenoughupdates.profileviewer.rift;

import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * "Ender Chest" sub-page of the rift tab, as SkyBlockPv's {@code RiftEnderChestScreen}: the rift ender chest, one
 * 45-slot page at a time, picked with the numbered buttons above it.
 */
public class RiftEnderChestPage implements GuiProfileViewerPage {

	private static final int PAGE_SIZE = 45;
	private static final int BUTTON = 20;

	private final GuiProfileViewer instance;
	private final ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
	private int page = 0;

	public RiftEnderChestPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		page = 0;
	}

	private static int pages() {
		return Math.max(1, (RiftItems.enderChest().size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private int buttonsLeft() {
		return GuiProfileViewer.getGuiLeft() + (instance.sizeX - pages() * (BUTTON + 2) + 2) / 2;
	}

	private int top() {
		return GuiProfileViewer.getGuiTop() + (instance.sizeY - BUTTON - 8 - 5 * 18) / 2;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		if (!RiftItems.load()) return false;
		for (int i = 0; i < pages(); i++) {
			if (Utils.isWithinRect((int) mouseX, (int) mouseY, buttonsLeft() + i * (BUTTON + 2), top(), BUTTON, BUTTON)) {
				if (page != i) RenderUtils.playPressSound();
				page = i;
				return true;
			}
		}
		return false;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font font = instance.getFont();
		if (GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId()) == null) return;
		if (!RiftItems.load() || RiftItems.enderChest().isEmpty()) {
			PvUi.centred(graphics, font, instance, "§cNo Rift ender chest data!");
			return;
		}
		int pages = pages();
		page = Math.min(page, pages - 1);
		int top = top();
		for (int i = 0; i < pages; i++) {
			int x = buttonsLeft() + i * (BUTTON + 2);
			boolean selected = i == page;
			boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, top, BUTTON, BUTTON);
			graphics.fill(x, top, x + BUTTON, top + BUTTON, selected ? 0xFF55AA55 : 0xFF555555);
			graphics.fill(x + 1, top + 1, x + BUTTON - 1, top + BUTTON - 1, selected ? 0xFF2E4A2E : hovered ? 0xFF2C2C2C : 0xFF1E1E1E);
			RenderUtils.drawItemStack(graphics, enderChest, x + 2, top + 2);
			PvUi.count(graphics, font, String.valueOf(i + 1), x + 1, top + 1);
			if (hovered) instance.tooltipToDisplay = List.of((selected ? "§a" : "§7") + "Ender Chest Page " + (i + 1));
		}

		List<JsonObject> items = RiftItems.enderChest();
		int x = GuiProfileViewer.getGuiLeft() + (instance.sizeX - 9 * 18) / 2;
		int y = top + BUTTON + 8;
		for (int slot = 0; slot < PAGE_SIZE; slot++) {
			RiftItems.slot(instance, graphics, RiftItems.get(items, page * PAGE_SIZE + slot), x + slot % 9 * 18, y + slot / 9 * 18, mouseX, mouseY);
		}
	}
}
