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
 * Port of the Forge 1.8.9 {@code GuiProfileViewerPage} abstract class (kept as an interface here, since Java's
 * lack of multiple inheritance never mattered for it and an interface with default methods reads more cleanly).
 *
 * <p>API mapping notes (Forge 1.8.9 -&gt; Fabric 26.1.2):
 * <ul>
 *   <li>{@code drawPage(int mouseX, int mouseY, float partialTicks)} -&gt; renamed to match the modern
 *   {@code Renderable#extractRenderState}/{@code Screen#extractRenderState} naming and now takes the
 *   {@code GuiGraphicsExtractor} that all drawing goes through (there's no more global GL state to draw
 *   against).</li>
 *   <li>{@code mouseClicked(int, int, int) throws IOException} -&gt; {@code boolean mouseClicked(double, double, int)}.
 *   Mouse coordinates are {@code double} in the modern input system (sub-pixel precision from high-DPI/scaled
 *   displays); IOException was never actually thrown by any implementation and modern {@code GuiEventListener}
 *   methods don't declare it.</li>
 *   <li>{@code mouseReleased(int, int, int)} -&gt; {@code double} coordinates, same as above.</li>
 *   <li>{@code keyTyped(char, int) throws IOException} -&gt; split into {@code keyPressed(KeyEvent)} (special keys:
 *   enter, backspace, arrows, etc, matching {@code GuiEventListener#keyPressed}) and {@code charTyped(CharacterEvent)}
 *   (printable characters), matching how modern {@code Screen}/text-field input works.</li>
 * </ul>
 */
public interface GuiProfileViewerPage {

	/**
	 * @return Instance of the current {@link GuiProfileViewer}
	 */
	GuiProfileViewer getInstance();

	void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks);

	/**
	 * @return Whether to consume the click (stop further handling in the caller)
	 */
	default boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		return false;
	}

	default void mouseReleased(double mouseX, double mouseY, int mouseButton) {
	}

	/**
	 * Replaces the Forge pages' polling of {@code Mouse.getDWheel()} inside {@code drawPage}.
	 *
	 * @param scrollY wheel notches, positive when scrolling up
	 * @return Whether to consume the scroll
	 */
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
