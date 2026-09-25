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

package io.github.moulberry.notenoughupdates.profileviewer.foraging;

import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.SkillTreeView;
import io.github.moulberry.notenoughupdates.profileviewer.mining.MiningUi;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * "HotF Tree" sub-page of the foraging tab, as SkyBlockPv's {@code ForagingSkillTreeScreen}: buttons for the five
 * tree slots, the tier column, and the Heart of the Forest tree of the chosen slot.
 */
public class HotfPage implements GuiProfileViewerPage {

	public static final int MAX_TIER = 8;
	/** HotF experience needed for each tier (SkyBlockPv's {@code SkillTreeType.FORAGING}). */
	private static final long[] TIER_EXP = {0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 547_000};

	private static final int SLOTS = 5;
	private static final int BUTTON = 20;
	private static final int BUTTON_GAP = 2;
	private static final int CELL = 19;
	/** Space between the tier column and the tree. */
	private static final int TIER_GAP = 6;
	private static final int TREE_COLUMNS = 7;

	private static final Identifier PERK_BACKGROUND = Identifier.parse("betterpv:profile_viewer/mining/perk_background.png");

	private final GuiProfileViewer instance;
	private ItemStack skull;
	/** The tree slot being shown, or 0 for the player's selected one. */
	private int slot = 0;

	public HotfPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		slot = 0;
	}

	public static long experience(JsonObject profileInfo) {
		return (long) Utils.getElementAsFloat(Utils.getElement(profileInfo, "skill_tree.experience.foraging"), 0);
	}

	public static int tier(JsonObject profileInfo) {
		long experience = experience(profileInfo);
		int tier = 0;
		for (long needed : TIER_EXP) if (experience >= needed) tier++;
		return tier;
	}

	private int buttonsLeft() {
		return GuiProfileViewer.getGuiLeft() + (instance.sizeX - (SLOTS * BUTTON + (SLOTS - 1) * BUTTON_GAP)) / 2;
	}

	private int top() {
		int height = BUTTON + 8 + MAX_TIER * CELL;
		return GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int left = buttonsLeft();
		for (int i = 0; i < SLOTS; i++) {
			if (Utils.isWithinRect((int) mouseX, (int) mouseY, left + i * (BUTTON + BUTTON_GAP), top(), BUTTON, BUTTON)) {
				if (slot != i + 1) RenderUtils.playPressSound();
				slot = i + 1;
				return true;
			}
		}
		return false;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInfo == null) return;
		Font font = instance.getFont();
		int shown = slot > 0 ? slot : SkillTreeView.selectedSlot(profileInfo, "foraging");
		int top = top();

		int left = buttonsLeft();
		if (skull == null) skull = SkillTreeView.hotfSkull();
		for (int i = 0; i < SLOTS; i++) {
			int x = left + i * (BUTTON + BUTTON_GAP);
			boolean selected = i + 1 == shown;
			boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, top, BUTTON, BUTTON);
			graphics.fill(x, top, x + BUTTON, top + BUTTON, selected ? 0xFF55AA55 : 0xFF555555);
			graphics.fill(x + 1, top + 1, x + BUTTON - 1, top + BUTTON - 1,
				selected ? 0xFF2F5E2F : hovered ? 0xFF2C2C2C : 0xFF1E1E1E);
			RenderUtils.drawItemStack(graphics, skull, x + 2, top + 2);
			if (hovered) instance.tooltipToDisplay = List.of("§7Slot " + (i + 1));
		}

		SkillTreeView.Tree tree = SkillTreeView.foraging();
		if (tree == null) return;
		int treeWidth = TREE_COLUMNS * CELL;
		int gridX = GuiProfileViewer.getGuiLeft() + (instance.sizeX - treeWidth + CELL + TIER_GAP) / 2;
		int gridY = top + BUTTON + 8;
		drawTiers(graphics, profileInfo, gridX - CELL - TIER_GAP, gridY, mouseX, mouseY);
		// The tree layout has fewer rows than tiers when the top tiers add no nodes; line it up from the bottom.
		int treeY = gridY + (MAX_TIER - tree.rows()) * CELL;
		List<String> tooltip = SkillTreeView.drawNodes(graphics, font, tree, profileInfo, shown, gridX, treeY, CELL, mouseX, mouseY);
		if (tooltip != null) instance.tooltipToDisplay = tooltip;
	}

	private void drawTiers(GuiGraphicsExtractor graphics, JsonObject profileInfo, int x, int top, int mouseX, int mouseY) {
		int tier = tier(profileInfo);
		for (int i = 1; i <= MAX_TIER; i++) {
			int y = top + (MAX_TIER - i) * CELL;
			ItemStack pane = new ItemStack(i <= tier ? Items.GREEN_STAINED_GLASS_PANE
				: i == tier + 1 ? Items.YELLOW_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE);
			RenderUtils.drawTexturedRect(graphics, PERK_BACKGROUND, x, y, CELL - 1, CELL - 1);
			RenderUtils.drawItemStack(graphics, pane, x + 1, y + 1);
			if (Utils.isWithinRect(mouseX, mouseY, x, y, CELL - 1, CELL - 1)) {
				instance.tooltipToDisplay = tierTooltip(i, tier, experience(profileInfo));
			}
		}
	}

	private static List<String> tierTooltip(int tier, int current, long experience) {
		List<String> tooltip = new ArrayList<>();
		boolean unlocked = tier <= current;
		boolean unlocking = tier == current + 1;
		tooltip.add((unlocked ? "§a" : unlocking ? "§e" : "§c") + "Tier " + tier);
		if (unlocking) {
			long into = experience - TIER_EXP[tier - 2];
			long needed = TIER_EXP[tier - 1] - TIER_EXP[tier - 2];
			float progress = Math.min(1, into / (float) needed);
			int green = Math.round(25 * progress);
			tooltip.add("");
			tooltip.add("§7Progress: §e" + Math.round(progress * 100) + "%");
			tooltip.add("§2§m" + " ".repeat(green) + "§f§m" + " ".repeat(25 - green) + "§r §e" + MiningUi.formatNumber(into) +
				"§6/§e" + MiningUi.formatNumber(needed));
		}
		tooltip.add("");
		if (unlocked) tooltip.add("§a§lUNLOCKED");
		else if (unlocking) tooltip.add("§c§lLOCKED");
		else tooltip.add("§cRequires Tier " + (tier - 1));
		return tooltip;
	}
}
