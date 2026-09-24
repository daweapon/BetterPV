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

import io.github.moulberry.notenoughupdates.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Empty page for tabs that don't have content yet (Farming, Foraging, Loadouts, Museum, Chocolate Factory, Rift).
 * Each gets replaced by its own page class once it's built.
 */
public class PlaceholderPage implements GuiProfileViewerPage {

	private final GuiProfileViewer instance;
	private final String name;

	public PlaceholderPage(GuiProfileViewer instance, String name) {
		this.instance = instance;
		this.name = name;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		RenderUtils.drawStringCenteredScaledMaxWidth(
			graphics,
			"§7" + name + " - coming soon",
			Minecraft.getInstance().font,
			guiLeft + instance.sizeX / 2,
			guiTop + instance.sizeY / 2,
			true,
			instance.sizeX - 20,
			0xFFFFFF
		);
	}
}
