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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * "Inventory" sub-page of the rift tab, as SkyBlockPv's {@code RiftInventoryScreen}: the rift armor and equipment,
 * then the rift inventory with the hotbar as its bottom row.
 */
public class RiftInventoryPage implements GuiProfileViewerPage {

	private static final Identifier[] ARMOR_SLOT_SPRITES = {
		Identifier.withDefaultNamespace("container/slot/helmet"),
		Identifier.withDefaultNamespace("container/slot/chestplate"),
		Identifier.withDefaultNamespace("container/slot/leggings"),
		Identifier.withDefaultNamespace("container/slot/boots"),
	};
	private static final int GAP = 10;

	private final GuiProfileViewer instance;

	public RiftInventoryPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font font = instance.getFont();
		if (GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId()) == null) return;
		if (!RiftItems.load()) {
			PvUi.centred(graphics, font, instance, "§cNo Rift inventory data!");
			return;
		}
		int width = 36 + GAP + 9 * 18;
		int height = PvUi.TITLE + 3 + 4 * 18;
		int x = GuiProfileViewer.getGuiLeft() + (instance.sizeX - width) / 2;
		int y = GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;
		PvUi.title(graphics, font, "Equipment", x, y, 36);
		PvUi.title(graphics, font, "Inventory", x + 36 + GAP, y, 9 * 18);
		int top = y + PvUi.TITLE + 3;

		// inv_armor is boots first.
		List<JsonObject> armor = RiftItems.armor();
		for (int i = 0; i < 4; i++) {
			JsonObject piece = RiftItems.get(armor, 3 - i);
			RiftItems.slot(instance, graphics, piece, x, top + i * 18, mouseX, mouseY);
			if (piece == null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_SLOT_SPRITES[i], x + 1, top + i * 18 + 1, 16, 16);
			RiftItems.slot(instance, graphics, RiftItems.get(RiftItems.equipment(), i), x + 18, top + i * 18, mouseX, mouseY);
		}

		// Slots 0-8 are the hotbar, drawn under the other three rows.
		List<JsonObject> inventory = RiftItems.inventory();
		int invX = x + 36 + GAP;
		for (int slot = 0; slot < 36; slot++) {
			int row = slot < 9 ? 3 : slot / 9 - 1;
			RiftItems.slot(instance, graphics, RiftItems.get(inventory, slot), invX + slot % 9 * 18, top + row * 18, mouseX, mouseY);
		}
	}
}
