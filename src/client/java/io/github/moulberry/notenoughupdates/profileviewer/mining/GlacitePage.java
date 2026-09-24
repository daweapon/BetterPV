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

package io.github.moulberry.notenoughupdates.profileviewer.mining;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Glacite Tunnels" sub-page of the mining tab: mineshafts and fossil dust, the eight fossils (donated, and whether
 * their pet is in the pet menu), and corpses looted with the corpse milestone. Fossil pets and the corpse
 * milestone table come from SkyBlockPv's repo ({@code pv/fossils}, {@code pv/corpse_milestones}).
 */
public class GlacitePage implements GuiProfileViewerPage {

	private record Fossil(String id, String name, String pet) {
	}

	private static final Fossil[] FOSSILS = {
		new Fossil("HELIX", "Helix Fossil", "AMMONITE"),
		new Fossil("CLAW_FOSSIL", "Claw Fossil", "MOLE"),
		new Fossil("WEBBED_FOSSIL", "Webbed Fossil", "PENGUIN"),
		new Fossil("UGLY_FOSSIL", "Ugly Fossil", "GOBLIN"),
		new Fossil("TUSK_FOSSIL", "Tusk Fossil", "MAMMOTH"),
		new Fossil("SPINE_FOSSIL", "Spine Fossil", "SPINOSAURUS"),
		new Fossil("FOOTPRINT_FOSSIL", "Footprint Fossil", "TYRANNOSAURUS"),
		new Fossil("CLUBBED_FOSSIL", "Clubbed Fossil", "ANKYLOSAURUS"),
	};

	private static final String[] CORPSES = {"lapis", "tungsten", "umber", "vanguard"};
	private static final String[] CORPSE_NAMES = {"§9Lapis", "§7Tungsten", "§6Umber", "§bVanguard"};
	/** Corpses of each type needed for each milestone, in {@link #CORPSES} order. */
	private static final int[][] CORPSE_MILESTONES = {
		{10, 0, 0, 0},
		{25, 1, 1, 0},
		{50, 5, 5, 0},
		{100, 10, 10, 1},
		{250, 25, 25, 5},
		{500, 50, 50, 10},
		{1000, 100, 100, 20},
	};

	private static final int ROW = 11;
	private static final int PANEL_WIDTH = 150;
	private static final int CORPSE_WIDTH = 180;
	private static final int GAP = 6;

	private final GuiProfileViewer instance;
	private final Map<String, ItemStack> icons = new HashMap<>();
	private static final ItemStack NOT_DONATED = new ItemStack(Items.GRAY_DYE);

	public GlacitePage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInfo == null) return;
		JsonElement glacite = Utils.getElement(profileInfo, "glacite_player_data");
		Font font = instance.getFont();

		int infoHeight = MiningUi.PANEL_TITLE + 6 + 2 * ROW + 3;
		int fossilHeight = MiningUi.PANEL_TITLE + 6 + 36 + 6;
		int corpseHeight = MiningUi.PANEL_TITLE + 6 + (CORPSES.length + 1) * ROW + 3;
		int leftHeight = infoHeight + GAP + fossilHeight;
		int left = GuiProfileViewer.getGuiLeft() + (instance.sizeX - PANEL_WIDTH - 10 - CORPSE_WIDTH) / 2;
		int top = GuiProfileViewer.getGuiTop() + (instance.sizeY - leftHeight) / 2;
		int right = left + PANEL_WIDTH + 10;

		// Info
		MiningUi.drawPanel(graphics, font, left, top, PANEL_WIDTH, infoHeight, "Info");
		int rowY = top + MiningUi.PANEL_TITLE + 6;
		RenderUtils.renderAlignedString(graphics, "§7Mineshafts Entered:",
			"§f" + MiningUi.formatNumber(Utils.getElementAsFloat(Utils.getElement(glacite, "mineshafts_entered"), 0)),
			left + 6, rowY, PANEL_WIDTH - 12);
		RenderUtils.renderAlignedString(graphics, "§7Fossil Dust:",
			"§f" + MiningUi.formatNumber(Utils.getElementAsFloat(Utils.getElement(glacite, "fossil_dust"), 0)),
			left + 6, rowY + ROW, PANEL_WIDTH - 12);

		// Fossils
		int fossilTop = top + infoHeight + GAP;
		MiningUi.drawPanel(graphics, font, left, fossilTop, PANEL_WIDTH, fossilHeight, "Fossils");
		drawFossils(graphics, profileInfo, glacite, left + (PANEL_WIDTH - 72) / 2, fossilTop + MiningUi.PANEL_TITLE + 6, mouseX, mouseY);

		// Corpses
		MiningUi.drawPanel(graphics, font, right, top, CORPSE_WIDTH, corpseHeight, "Corpses Looted");
		rowY = top + MiningUi.PANEL_TITLE + 6;
		int[] looted = new int[CORPSES.length];
		for (int i = 0; i < CORPSES.length; i++) {
			looted[i] = (int) Utils.getElementAsFloat(Utils.getElement(glacite, "corpses_looted." + CORPSES[i]), 0);
			// Out of what the final milestone needs.
			int needed = CORPSE_MILESTONES[CORPSE_MILESTONES.length - 1][i];
			RenderUtils.renderAlignedString(graphics, CORPSE_NAMES[i] + " Corpses:",
				(looted[i] >= needed ? "§a" : "§f") + MiningUi.formatNumber(looted[i]) + "§7/" + MiningUi.formatNumber(needed),
				right + 6, rowY, CORPSE_WIDTH - 12);
			rowY += ROW;
		}
		int milestone = corpseMilestone(looted);
		int max = CORPSE_MILESTONES.length;
		RenderUtils.renderAlignedString(graphics, "§7Corpse Milestone:", (milestone >= max ? "§a" : "§c") + milestone + "§7/" + max,
			right + 6, rowY, CORPSE_WIDTH - 12);
		if (Utils.isWithinRect(mouseX, mouseY, right + 4, rowY - 1, CORPSE_WIDTH - 8, ROW)) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add("§bCorpse Milestone " + milestone + "§7/" + max);
			if (milestone < max) {
				tooltip.add("");
				tooltip.add("§7Next milestone needs:");
				int[] next = CORPSE_MILESTONES[milestone];
				for (int i = 0; i < CORPSES.length; i++) {
					if (next[i] == 0) continue;
					tooltip.add("§8 • " + CORPSE_NAMES[i] + "§7: " + (looted[i] >= next[i] ? "§a" : "§c") +
						MiningUi.formatNumber(looted[i]) + "§7/" + MiningUi.formatNumber(next[i]));
				}
			}
			instance.tooltipToDisplay = tooltip;
		}
	}

	private void drawFossils(GuiGraphicsExtractor graphics, JsonObject profileInfo, JsonElement glacite, int x, int y, int mouseX, int mouseY) {
		List<String> donated = new ArrayList<>();
		if (Utils.getElement(glacite, "fossils_donated") instanceof JsonArray array) {
			for (JsonElement fossil : array) donated.add(fossil.getAsString());
		}
		List<String> pets = new ArrayList<>();
		if (profileInfo.get("pets") instanceof JsonArray array) {
			for (JsonElement pet : array) {
				if (pet instanceof JsonObject object) pets.add(Utils.getElementAsString(object.get("type"), ""));
			}
		}

		for (int i = 0; i < FOSSILS.length; i++) {
			Fossil fossil = FOSSILS[i];
			int slotX = x + (i % 4) * 18;
			int slotY = y + (i / 4) * 18;
			// fossils_donated uses the first word of the item id (CLAW, HELIX, ...).
			boolean isDonated = donated.contains(fossil.id().split("_")[0]);
			boolean inPetMenu = pets.contains(fossil.pet());

			MiningUi.drawSlotBackground(graphics, slotX, slotY);
			RenderUtils.drawItemStack(graphics, isDonated ? icon(fossil.id()) : NOT_DONATED, slotX + 1, slotY + 1);
			if (Utils.isWithinRect(mouseX, mouseY, slotX, slotY, 18, 18)) {
				List<String> tooltip = new ArrayList<>();
				tooltip.add("§f§l" + fossil.name());
				tooltip.add("§7Donated: " + (isDonated ? "§aYes" : "§cNo"));
				tooltip.add("§7In Pet Menu: " + (inPetMenu ? "§aYes" : "§cNo §7(§a" + titleCase(fossil.pet()) + "§7)"));
				instance.tooltipToDisplay = tooltip;
			}
		}
	}

	private ItemStack icon(String id) {
		return icons.computeIfAbsent(id, key -> {
			JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(key);
			ItemStack stack = json == null ? null : NotEnoughUpdates.INSTANCE.manager.jsonToStack(json);
			return stack == null || stack.isEmpty() ? new ItemStack(Items.BONE) : stack;
		});
	}

	/** The highest milestone whose requirements are all met, counting from 1 (0 if none). */
	private static int corpseMilestone(int[] looted) {
		for (int m = CORPSE_MILESTONES.length - 1; m >= 0; m--) {
			boolean met = true;
			for (int i = 0; i < looted.length; i++) {
				if (looted[i] < CORPSE_MILESTONES[m][i]) met = false;
			}
			if (met) return m + 1;
		}
		return 0;
	}

	private static String titleCase(String text) {
		return text.charAt(0) + text.substring(1).toLowerCase(java.util.Locale.ROOT);
	}
}
