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

import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.foraging.AttributesPage;
import io.github.moulberry.notenoughupdates.profileviewer.foraging.ForagingInfoPage;
import io.github.moulberry.notenoughupdates.profileviewer.foraging.HotfPage;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Foraging" tab, following SkyBlockPv's foraging categories: general information (tree gifts, whispers, Agatha
 * and Miria perks), the Heart of the Forest tree, and hunting attributes. The sub-pages are picked with the
 * buttons down the left side of the window, like the mining tab.
 */
public class ForagingPage implements GuiProfileViewerPage {

	private enum Category {
		MAIN("Foraging"),
		HOTF("HotF Tree"),
		ATTRIBUTES("Attributes");

		final String displayName;

		Category(String displayName) {
			this.displayName = displayName;
		}
	}

	private static final int CATEGORY_SIZE = 22;
	private static final int CATEGORY_PITCH = 25;

	private final GuiProfileViewer instance;
	private Category category = Category.MAIN;
	private final Map<Category, GuiProfileViewerPage> subPages = new EnumMap<>(Category.class);
	private final Map<Category, ItemStack> categoryIcons = new EnumMap<>(Category.class);

	public ForagingPage(GuiProfileViewer instance) {
		this.instance = instance;
		subPages.put(Category.MAIN, new ForagingInfoPage(instance));
		subPages.put(Category.HOTF, new HotfPage(instance));
		subPages.put(Category.ATTRIBUTES, new AttributesPage(instance));
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		subPages.values().forEach(GuiProfileViewerPage::resetCache);
	}

	private GuiProfileViewerPage current() {
		return subPages.get(category);
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		current().drawPage(graphics, mouseX, mouseY, partialTicks);
		drawCategoryButtons(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		for (Category each : Category.values()) {
			if (Utils.isWithinRect((int) mouseX, (int) mouseY, guiLeft - CATEGORY_SIZE - 3,
				guiTop + 6 + each.ordinal() * CATEGORY_PITCH, CATEGORY_SIZE, CATEGORY_SIZE)) {
				if (category != each) RenderUtils.playPressSound();
				category = each;
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

	private void drawCategoryButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = GuiProfileViewer.getGuiLeft() - CATEGORY_SIZE - 3;
		for (Category each : Category.values()) {
			int y = GuiProfileViewer.getGuiTop() + 6 + each.ordinal() * CATEGORY_PITCH;
			boolean selected = each == category;
			boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, y, CATEGORY_SIZE, CATEGORY_SIZE);
			graphics.fill(x, y, x + CATEGORY_SIZE, y + CATEGORY_SIZE, selected ? 0xFFAAAAAA : 0xFF555555);
			graphics.fill(x + 1, y + 1, x + CATEGORY_SIZE - 1, y + CATEGORY_SIZE - 1,
				selected ? 0xFF3A3A3A : hovered ? 0xFF2C2C2C : 0xFF1E1E1E);
			RenderUtils.drawItemStack(graphics, categoryIcon(each), x + 3, y + 3);
			if (hovered) instance.tooltipToDisplay = List.of((selected ? "§a" : "§7") + each.displayName);
		}
	}

	private ItemStack categoryIcon(Category each) {
		return categoryIcons.computeIfAbsent(each, key -> switch (key) {
			case MAIN -> new ItemStack(Items.OAK_WOOD);
			case HOTF -> SkillTreeView.hotfSkull();
			// SkyBlockPv's attributes icon is shard R43 (Ladybug).
			case ATTRIBUTES -> {
				JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get("ATTRIBUTE_SHARD_PRETTY_CLOTHES;1");
				ItemStack stack = json == null ? null : NotEnoughUpdates.INSTANCE.manager.jsonToStack(json);
				yield stack == null || stack.isEmpty() ? new ItemStack(Items.PRISMARINE_SHARD) : stack;
			}
		});
	}
}
