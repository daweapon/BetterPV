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

package io.github.moulberry.notenoughupdates.profileviewer.mining;

import io.github.moulberry.notenoughupdates.util.RenderUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.text.NumberFormat;
import java.util.Locale;

/** Drawing helpers shared by the mining tab's sub-pages. */
public final class MiningUi {

	/** Height of a panel's title strip. */
	public static final int PANEL_TITLE = 14;

	private static final Identifier SLOT = Identifier.parse("notenoughupdates:profile_viewer/mining/perk_background.png");

	private MiningUi() {
	}

	/** A bordered box with its title centred in a strip across the top. */
	public static void drawPanel(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, String title) {
		graphics.fill(x, y, x + width, y + height, 0x60000000);
		graphics.fill(x, y, x + width, y + PANEL_TITLE, 0x50000000);
		graphics.fill(x, y, x + width, y + 1, 0xFF555555);
		graphics.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
		graphics.fill(x, y, x + 1, y + height, 0xFF555555);
		graphics.fill(x + width - 1, y, x + width, y + height, 0xFF555555);
		graphics.fill(x + 1, y + PANEL_TITLE - 1, x + width - 1, y + PANEL_TITLE, 0xFF555555);
		RenderUtils.drawStringCentered(graphics, title, font, x + width / 2f, y + PANEL_TITLE / 2f, true, 0xFFFFFF);
	}

	/** An 18x18 item slot; the item goes at (x + 1, y + 1). */
	public static void drawSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
		RenderUtils.drawTexturedRect(graphics, SLOT, x, y, 18, 18);
	}

	public static String formatNumber(double number) {
		return NumberFormat.getIntegerInstance(Locale.US).format(number);
	}
}
