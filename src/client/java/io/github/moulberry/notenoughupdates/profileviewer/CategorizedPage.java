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
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A tab made of sub-pages picked with buttons down the left side of the window, like the Mining and Foraging tabs.
 */
public class CategorizedPage implements GuiProfileViewerPage {

	private static final int SIZE = 22;
	private static final int PITCH = 25;

	private record Category(String name, Supplier<ItemStack> iconSupplier, GuiProfileViewerPage page, ItemStack[] cachedIcon) {
		ItemStack icon() {
			if (cachedIcon[0] == null) cachedIcon[0] = iconSupplier.get();
			return cachedIcon[0];
		}
	}

	private final GuiProfileViewer instance;
	private final List<Category> categories = new ArrayList<>();
	private int selected = 0;

	public CategorizedPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	protected void add(String name, Supplier<ItemStack> icon, GuiProfileViewerPage page) {
		categories.add(new Category(name, icon, page, new ItemStack[1]));
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		categories.forEach(category -> category.page().resetCache());
	}

	private GuiProfileViewerPage current() {
		return categories.get(selected).page();
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		current().drawPage(graphics, mouseX, mouseY, partialTicks);
		int x = GuiProfileViewer.getGuiLeft() - SIZE - 3;
		for (int i = 0; i < categories.size(); i++) {
			int y = GuiProfileViewer.getGuiTop() + 6 + i * PITCH;
			boolean isSelected = i == selected;
			boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, y, SIZE, SIZE);
			graphics.fill(x, y, x + SIZE, y + SIZE, isSelected ? 0xFFAAAAAA : 0xFF555555);
			graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, isSelected ? 0xFF3A3A3A : hovered ? 0xFF2C2C2C : 0xFF1E1E1E);
			RenderUtils.drawItemStack(graphics, categories.get(i).icon(), x + 3, y + 3);
			if (hovered) instance.tooltipToDisplay = List.of((isSelected ? "§a" : "§7") + categories.get(i).name());
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int x = GuiProfileViewer.getGuiLeft() - SIZE - 3;
		for (int i = 0; i < categories.size(); i++) {
			if (Utils.isWithinRect((int) mouseX, (int) mouseY, x, GuiProfileViewer.getGuiTop() + 6 + i * PITCH, SIZE, SIZE)) {
				if (selected != i) RenderUtils.playPressSound();
				selected = i;
				return true;
			}
		}
		return current().mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		return current().mouseScrolled(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return current().keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return current().charTyped(event);
	}
}
