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

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A page of the profile viewer. Drawing goes through a {@code GuiGraphicsExtractor}, mouse coordinates are
 * doubles, and key input is split into {@code keyPressed} and {@code charTyped}.
 */
public interface GuiProfileViewerPage {

	/** The current {@link GuiProfileViewer}. */
	GuiProfileViewer getInstance();

	void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks);

	/** Returns true to consume the click. */
	default boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		return false;
	}

	default void mouseReleased(double mouseX, double mouseY, int mouseButton) {
	}

	/** Mouse wheel scroll; {@code scrollY} is positive when scrolling up. Returns true to consume it. */
	default boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		return false;
	}

	default boolean keyPressed(KeyEvent event) {
		return false;
	}

	default boolean charTyped(CharacterEvent event) {
		return false;
	}

	default void resetCache() {
	}
}
