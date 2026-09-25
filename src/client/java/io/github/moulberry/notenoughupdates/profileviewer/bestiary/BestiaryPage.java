/*
 * Copyright (C) 2022-2023 NotEnoughUpdates contributors
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

package io.github.moulberry.notenoughupdates.profileviewer.bestiary;

import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.bestiary.BestiaryData.Category;
import io.github.moulberry.notenoughupdates.profileviewer.bestiary.BestiaryData.FamilyData;
import io.github.moulberry.notenoughupdates.profileviewer.bestiary.BestiaryData.Mob;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Bestiary tab, ported from current NEU. Categories come from {@code bestiary.json} via {@link BestiaryData}.
 * There are more categories than in NEU, so the rows shrink to fit, and selection happens on mouse release.
 */
public class BestiaryPage implements GuiProfileViewerPage {

	private static final Identifier BESTIARY_TEXTURE = Identifier.parse("betterpv:pv_bestiary_tab.png");
	private static final NumberFormat numberFormat = GuiProfileViewer.numberFormat;

	private static final int MOB_X_COUNT = 9;
	private static final int MOB_Y_COUNT = 5;
	private static final float MOB_X_PADDING = (240 - MOB_X_COUNT * 20) / (float) (MOB_X_COUNT + 1);
	private static final float MOB_Y_PADDING = (202 - MOB_Y_COUNT * 20) / (float) (MOB_Y_COUNT + 1);

	/** Category row: first box's left edge to last box's right edge, relative to guiLeft. */
	private static final int CATEGORY_ROW_LEFT = 22;
	private static final int CATEGORY_ROW_RIGHT = 409;
	private static final int CATEGORY_ROW_Y = 10;
	/** Subcategory row, inside the right-hand info panel. */
	private static final int SUBCATEGORY_ROW_LEFT = 280;
	private static final int SUBCATEGORY_ROW_RIGHT = 398;
	private static final int SUBCATEGORY_ROW_Y = 175;

	private final GuiProfileViewer instance;
	private List<Category> categories = new ArrayList<>();
	private int bestiaryTiers = 0;
	private Object parsedFor = null;
	private String selectedCategory = "";
	private String selectedSubCategory = "";

	public BestiaryPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		parsedFor = null;
	}

	/** A row of equally spaced boxes between two x positions, shrunk (down to 14px) if they'd overlap. */
	private record Row(float left, float step, int size, int y) {
		static Row fit(int count, int left, int right, int y, int maxStep) {
			int size = 20;
			float step = count > 1 ? (right - left - size) / (float) (count - 1) : 0;
			while (count > 1 && size > 14 && step < size + 1) {
				size--;
				step = (right - left - size) / (float) (count - 1);
			}
			return new Row(left, Math.min(step, maxStep), size, y);
		}

		float x(int index) {
			return left + step * index;
		}

		boolean contains(int index, double mouseX, double mouseY) {
			return mouseX >= x(index) && mouseX < x(index) + size && mouseY >= y && mouseY < y + size;
		}
	}

	private Row categoryRow(int guiLeft, int guiTop) {
		return Row.fit(
			categories.size(), guiLeft + CATEGORY_ROW_LEFT, guiLeft + CATEGORY_ROW_RIGHT, guiTop + CATEGORY_ROW_Y, 1000
		);
	}

	private Row subCategoryRow(Category category, int guiLeft, int guiTop) {
		return Row.fit(
			category.subCategories().size(), guiLeft + SUBCATEGORY_ROW_LEFT, guiLeft + SUBCATEGORY_ROW_RIGHT,
			guiTop + SUBCATEGORY_ROW_Y, 24
		);
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		JsonObject profileInfo = profile.getProfileInformation(GuiProfileViewer.getProfileId());

		if (profileInfo == null || Constants.BESTIARY == null || !BestiaryData.hasMigrated(profileInfo)) {
			drawNoData(graphics, guiLeft, guiTop);
			return;
		}
		// Parse once per profile (and again after the profile or selected SkyBlock profile changes).
		if (parsedFor != profileInfo) {
			categories = BestiaryData.parseBestiaryData(profileInfo);
			bestiaryTiers = BestiaryData.calculateTotalBestiaryTiers(categories);
			parsedFor = profileInfo;
		}
		if (categories.isEmpty()) {
			drawNoData(graphics, guiLeft, guiTop);
			return;
		}
		Category category = selectedCategory();

		List<String> tooltip = null;

		// Category row.
		Row row = categoryRow(guiLeft, guiTop);
		for (int i = 0; i < categories.size(); i++) {
			Category c = categories.get(i);
			drawSlot(graphics, row, i, c.icon(), c == category);
			if (row.contains(i, mouseX, mouseY)) tooltip = List.of(ChatFormatting.GRAY + c.name());
		}

		RenderUtils.drawTexturedRect(graphics, BESTIARY_TEXTURE, guiLeft, guiTop, 431, 202);

		RenderUtils.renderAlignedString(
			graphics, ChatFormatting.RED + "Milestone: ", ChatFormatting.GRAY + String.valueOf(bestiaryTiers / 10.0),
			guiLeft + 280, guiTop + 50, 110
		);

		// Subcategories, bottom right.
		Category subCategory = null;
		if (!category.subCategories().isEmpty()) {
			subCategory = category.subCategories().stream()
				.filter(s -> s.id().equals(selectedSubCategory)).findFirst()
				.orElse(category.subCategories().get(0));
			selectedSubCategory = subCategory.id();

			RenderUtils.drawStringCentered(
				graphics, ChatFormatting.RED + "Subcategories", instance.getFont(), guiLeft + 339, guiTop + 167, true, 0
			);
			Row subRow = subCategoryRow(category, guiLeft, guiTop);
			for (int i = 0; i < category.subCategories().size(); i++) {
				Category s = category.subCategories().get(i);
				drawSlot(graphics, subRow, i, s.icon(), s == subCategory);
				if (subRow.contains(i, mouseX, mouseY)) tooltip = List.of(ChatFormatting.GRAY + s.name());
			}
		} else {
			selectedSubCategory = "";
		}

		drawFamilyData(graphics, category.familyData(), guiLeft, guiTop + 70);
		if (subCategory != null) drawFamilyData(graphics, subCategory.familyData(), guiLeft, guiTop + 120);

		// Mob grid.
		List<Mob> mobs = subCategory != null ? subCategory.mobs() : category.mobs();
		for (int i = 0; i < mobs.size() && i < MOB_X_COUNT * MOB_Y_COUNT; i++) {
			Mob mob = mobs.get(i);
			float x = guiLeft + 23 + MOB_X_PADDING + (MOB_X_PADDING + 20) * (i % MOB_X_COUNT);
			float y = guiTop + 30 + MOB_Y_PADDING + (MOB_Y_PADDING + 20) * (i / MOB_X_COUNT);

			RenderUtils.drawTexturedRect(graphics, GuiProfileViewer.pv_elements, x, y, 20, 20, 0, 20 / 256f, 0, 20 / 256f);
			RenderUtils.drawItemStack(graphics, mob.icon(), (int) x + 2, (int) y + 2);
			RenderUtils.drawStringCentered(
				graphics,
				(mob.levelData().maxLevel() ? ChatFormatting.GOLD.toString() : "") + mob.levelData().level(),
				instance.getFont(), x + 10, y + 26, true, 0x808080
			);
			if (mouseX > x + 2 && mouseX < x + 18 && mouseY > y + 2 && mouseY < y + 18) {
				tooltip = mobTooltip(mob);
			}
		}

		if (tooltip != null) {
			List<String> gray = new ArrayList<>(tooltip.size());
			for (String line : tooltip) gray.add(ChatFormatting.GRAY + line);
			instance.tooltipToDisplay = gray;
		}
	}

	private void drawNoData(GuiGraphicsExtractor graphics, int guiLeft, int guiTop) {
		RenderUtils.drawStringCentered(
			graphics, ChatFormatting.RED + "No valid bestiary data!", instance.getFont(), guiLeft + 431 / 2f, guiTop + 101,
			true, 0
		);
	}

	private Category selectedCategory() {
		for (Category c : categories) {
			if (c.id().equals(selectedCategory)) return c;
		}
		selectedCategory = categories.get(0).id();
		return categories.get(0);
	}

	/** A slot box (mirrored when selected, as in NEU) with the icon scaled to the box size. */
	private void drawSlot(GuiGraphicsExtractor graphics, Row row, int index, ItemStack icon, boolean selected) {
		float x = row.x(index);
		if (selected) {
			RenderUtils.drawTexturedRect(graphics, GuiProfileViewer.pv_elements, x, row.y(), row.size(), row.size(), 20 / 256f, 0, 20 / 256f, 0);
		} else {
			RenderUtils.drawTexturedRect(graphics, GuiProfileViewer.pv_elements, x, row.y(), row.size(), row.size(), 0, 20 / 256f, 0, 20 / 256f);
		}
		float scale = row.size() / 20f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 2 * scale, row.y() + 2 * scale);
		graphics.pose().scale(scale, scale);
		RenderUtils.drawItemStack(graphics, icon, 0, 0);
		graphics.pose().popMatrix();
	}

	private void drawFamilyData(GuiGraphicsExtractor graphics, FamilyData data, int guiLeft, int top) {
		drawFamilyRow(graphics, "Families Found:", data.found(), data.total(), guiLeft, top);
		drawFamilyRow(graphics, "Families Completed:", data.completed(), data.total(), guiLeft, top + 20);
	}

	private void drawFamilyRow(GuiGraphicsExtractor graphics, String label, int value, int total, int guiLeft, int y) {
		boolean full = total > 0 && value == total;
		RenderUtils.renderAlignedString(
			graphics, ChatFormatting.RED + label, (full ? "§6" : "§7") + value + "/" + total, guiLeft + 280, y, 110
		);
		if (full) {
			instance.renderGoldBar(graphics, guiLeft + 280, y + 10, 112);
		} else {
			instance.renderBar(graphics, guiLeft + 280, y + 10, 112, total == 0 ? 0 : value / (float) total);
		}
	}

	private List<String> mobTooltip(Mob mob) {
		BestiaryData.MobLevelData level = mob.levelData();
		List<String> lines = new ArrayList<>();
		lines.add(mob.name() + " " + level.level());
		lines.add(ChatFormatting.GRAY + "Kills: " + ChatFormatting.GREEN + numberFormat.format(mob.kills()));
		lines.add(ChatFormatting.GRAY + "Deaths: " + ChatFormatting.GREEN + numberFormat.format(mob.deaths()));
		lines.add("");
		if (!level.maxLevel()) {
			lines.add(ChatFormatting.GRAY + "Progress to Tier " + (level.level() + 1) + ": " + ChatFormatting.AQUA + level.progress() + "%");
			lines.add(progressBar(level.progress()) + "§r§b " + numberFormat.format(level.killData().tierKills()) + "/" +
				numberFormat.format(level.killData().tierReq()));
			lines.add("");
		}
		lines.add(ChatFormatting.GRAY + "Overall Progress: " + ChatFormatting.AQUA + level.totalProgress() + "%" +
			(level.maxLevel() ? " §7(§c§lMAX!§r§7)" : ""));
		lines.add(progressBar(level.totalProgress()) + "§r§b " + numberFormat.format(level.killData().cappedKills()) + "/" +
			numberFormat.format(level.killData().cap()));
		return lines;
	}

	/** NEU's 14-segment tooltip progress bar, made of struck-through spaces. */
	private static String progressBar(double percent) {
		StringBuilder bar = new StringBuilder("§3§l§m");
		for (int j = 1; j <= 14; j++) {
			if (percent < j * (100 / 14)) bar.append("§f§l§m");
			bar.append(' ');
		}
		return bar.toString();
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
		if (categories.isEmpty()) return;
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		Row row = categoryRow(guiLeft, guiTop);
		for (int i = 0; i < categories.size(); i++) {
			if (row.contains(i, mouseX, mouseY) && !categories.get(i).id().equals(selectedCategory)) {
				selectedCategory = categories.get(i).id();
				selectedSubCategory = "";
				RenderUtils.playPressSound();
				return;
			}
		}

		Category category = selectedCategory();
		if (category.subCategories().isEmpty()) return;
		Row subRow = subCategoryRow(category, guiLeft, guiTop);
		for (int i = 0; i < category.subCategories().size(); i++) {
			Category s = category.subCategories().get(i);
			if (subRow.contains(i, mouseX, mouseY) && !s.id().equals(selectedSubCategory)) {
				selectedSubCategory = s.id();
				RenderUtils.playPressSound();
				return;
			}
		}
	}
}
