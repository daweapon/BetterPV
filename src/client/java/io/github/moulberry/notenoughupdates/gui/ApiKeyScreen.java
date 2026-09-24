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

package io.github.moulberry.notenoughupdates.gui;

import io.github.moulberry.notenoughupdates.util.ApiKeyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Standalone "put your own Hypixel API key here" GUI, opened via {@code /bpv} (see {@code BpvCommand}). The key
 * is optional - without one, lookups go through the Better PV backend ({@code BpvBackend}). There's no
 * annotation-driven {@code NEUConfig} config-GUI framework in this port (see {@link ApiKeyConfig} class javadoc),
 * so this is a small hand-built {@link Screen} instead of a generated config page - reads/writes the same
 * {@code apiKey} field in {@code config/notenoughupdates/config.json} that {@code /bpv setapi <key>} does.
 */
public class ApiKeyScreen extends Screen {
	private final Screen parent;
	private EditBox keyField;
	private Component status = Component.empty();

	public ApiKeyScreen(Screen parent) {
		super(Component.literal("Better PV - API Key"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int boxWidth = 300;
		int centerX = this.width / 2;
		int fieldY = this.height / 2 - 10;

		keyField = new EditBox(this.font, centerX - boxWidth / 2, fieldY, boxWidth, 20, Component.literal("API key"));
		keyField.setMaxLength(64);
		keyField.setValue(ApiKeyConfig.getApiKey());
		this.setInitialFocus(keyField);
		this.addRenderableWidget(keyField);

		this.addRenderableWidget(
			Button.builder(Component.literal("Save"), button -> save())
				.bounds(centerX - 154, fieldY + 28, 100, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.literal("Get a key"), button -> ConfirmLinkScreen.confirmLinkNow(
				this, "https://developer.hypixel.net/dashboard"
			))
				.bounds(centerX - 50, fieldY + 28, 100, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(centerX + 54, fieldY + 28, 100, 20)
				.build()
		);
	}

	private void save() {
		String key = keyField.getValue().trim();
		ApiKeyConfig.setApiKey(key);
		status = Component.literal(
			key.isEmpty()
				? ChatFormatting.YELLOW + "API key cleared - using the Better PV server."
				: ChatFormatting.GREEN + "API key saved."
		);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int centerX = this.width / 2;
		int fieldY = this.height / 2 - 10;
		String subtitleText = "Optional - leave blank to use the Better PV server";

		// A recreation of NEUConfigEditor's classic panel look (same border/fill colours as the original
		// options.NEUConfigEditor#drawScreen bevel: a 1px near-black top/left edge, a 1px lighter bottom/right
		// edge for a subtle inset bevel, over a translucent near-black fill) rather than the full moulconfig
		// framework, which this port doesn't carry over - see ApiKeyScreen class javadoc.
		int panelLeft = centerX - 160;
		int panelRight = centerX + 160;
		int panelTop = fieldY - 54;
		int panelBottom = fieldY + 70;
		graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xff08080e); // left
		graphics.fill(panelLeft + 1, panelTop, panelRight, panelTop + 1, 0xff08080e); // top
		graphics.fill(panelRight - 1, panelTop + 1, panelRight, panelBottom, 0xff28282e); // right
		graphics.fill(panelLeft + 1, panelBottom - 1, panelRight - 1, panelBottom, 0xff28282e); // bottom
		graphics.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 0xd008080e); // middle

		graphics.text(
			this.font, this.title, centerX - this.font.width(this.title.getVisualOrderText()) / 2, fieldY - 40, 0xffe6b213, true
		);
		graphics.text(this.font, subtitleText, centerX - this.font.width(subtitleText) / 2, fieldY - 24, 0xa0a0a0, true);

		super.extractRenderState(graphics, mouseX, mouseY, partialTicks); // draws the EditBox + buttons on top of the panel

		if (!status.getString().isEmpty()) {
			graphics.text(
				this.font, status, centerX - this.font.width(status.getVisualOrderText()) / 2, fieldY + 54, 0xffffff, true
			);
		}
	}
}
