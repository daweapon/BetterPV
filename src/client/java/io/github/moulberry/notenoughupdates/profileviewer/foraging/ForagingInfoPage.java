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
import io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.SkillTreeView;
import io.github.moulberry.notenoughupdates.profileviewer.mining.MiningUi;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Main foraging sub-page: HotF tier, tree gifts, whispers and the Agatha / Miria perks. Caps come from
 * SkyBlockPv's repo.
 */
public class ForagingInfoPage implements GuiProfileViewerPage {

	/** Tree gift milestones per tree (pv/foraging tree_gifts). */
	private static final int GIFT_MILESTONES = 7;
	private static final int PERSONAL_BEST_MAX = 100_000;
	private static final int FORTUNE_MAX = 50;

	private record TreeType(String name, String key, String npc) {
	}

	private static final TreeType[] GIFTS = {
		new TreeType("Mangrove", "MANGROVE", "agatha"),
		new TreeType("Fig", "FIG", "agatha"),
		new TreeType("Helix", "HELIX", "miria"),
	};
	/** The personal best panels, in SkyBlockPv's order. */
	private static final TreeType[] BESTS = {GIFTS[1], GIFTS[0], GIFTS[2]};

	private static final int ROW = 11;
	private static final int PAD = 6;
	private static final int GAP = 5;

	private final GuiProfileViewer instance;

	public ForagingInfoPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		JsonObject profileInfo = profile.getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInfo == null) return;
		Font font = instance.getFont();

		List<String> information = information(profileInfo);
		List<List<String>> bests = new ArrayList<>();
		for (TreeType tree : BESTS) bests.add(personalBest(profileInfo, tree));

		int infoWidth = width(font, information, "Information");
		int infoHeight = height(information.size());
		int bestWidth = 0;
		int bestHeight = -GAP;
		for (int i = 0; i < bests.size(); i++) {
			bestWidth = Math.max(bestWidth, width(font, bests.get(i), BESTS[i].name()));
			bestHeight += height(bests.get(i).size()) + GAP;
		}

		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		int left = guiLeft + (instance.sizeX - infoWidth - GAP * 2 - bestWidth) / 2;
		// Both columns start at the same height so their titles line up.
		int top = guiTop + (instance.sizeY - Math.max(infoHeight, bestHeight)) / 2;
		drawLines(graphics, font, "Information", information, left, top, infoWidth);

		int x = left + infoWidth + GAP * 2;
		int y = top;
		for (int i = 0; i < bests.size(); i++) {
			drawLines(graphics, font, BESTS[i].name(), bests.get(i), x, y, bestWidth);
			y += height(bests.get(i).size()) + GAP;
		}
	}

	private List<String> information(JsonObject profileInfo) {
		List<String> lines = new ArrayList<>();
		lines.add("§7HotF Tier: §e" + HotfPage.tier(profileInfo) + "§7/§a" + HotfPage.MAX_TIER);
		for (TreeType tree : GIFTS) {
			int claimed = (int) Utils.getElementAsFloat(Utils.getElement(profileInfo, "foraging.tree_gifts.milestone_tier_claimed." + tree.key()), 0);
			float total = Utils.getElementAsFloat(Utils.getElement(profileInfo, "foraging.tree_gifts." + tree.key()), 0);
			lines.add("§7" + tree.name() + " Gifts: " + (claimed >= GIFT_MILESTONES ? "§a" : "§c") + claimed + "§7/§a" + GIFT_MILESTONES);
			lines.add("§7Total " + tree.name() + " Gifts: §f" + MiningUi.formatNumber(total));
		}
		lines.add("");
		lines.add("§7Whispers (Current/Total)");
		int slot = SkillTreeView.selectedSlot(profileInfo, "foraging");
		lines.add(whispers(profileInfo, "Forest", "forest", "§3", slot));
		lines.add(whispers(profileInfo, "Desert", "desert", "§6", slot));
		return lines;
	}

	/** Whispers earned in total, minus what the selected HotF slot has spent. */
	private static String whispers(JsonObject profileInfo, String name, String key, String colour, int slot) {
		float total = Utils.getElementAsFloat(Utils.getElement(profileInfo, "foraging_core.whispers." + key + ".total"), 0);
		float spent = Utils.getElementAsFloat(Utils.getElement(profileInfo, "foraging_core.whispers." + key + "." + slot + ".spent"), 0);
		return colour + name + "§8: " + colour + MiningUi.formatNumber(Math.max(0, total - spent)) + "§8/" + colour +
			MiningUi.formatNumber(total);
	}

	private static List<String> personalBest(JsonObject profileInfo, TreeType tree) {
		String prefix = "player_data.perks." + tree.npc() + "_" + tree.key().toLowerCase() + "_";
		boolean unlocked = Utils.getElement(profileInfo, prefix + "personal_best") != null;
		int fortune = (int) Utils.getElementAsFloat(Utils.getElement(profileInfo, prefix + "fortune"), 0);

		List<String> lines = new ArrayList<>();
		lines.add("§7" + tree.name() + " Personal Bests: " + (unlocked ? "§aYes" : "§cNo"));
		if (unlocked) {
			float best = Utils.getElementAsFloat(Utils.getElement(profileInfo, "foraging.starlyn.personal_bests." + tree.key() + "_LOG"), 0);
			lines.add("§7" + tree.name() + " Best: " + (best >= PERSONAL_BEST_MAX ? "§a" : "§c") +
				MiningUi.formatNumber(Math.min(best, PERSONAL_BEST_MAX)) + "§7/§a" + MiningUi.formatNumber(PERSONAL_BEST_MAX));
		}
		lines.add("§7" + tree.name() + " Fortune Level: " + (fortune >= FORTUNE_MAX ? "§a" : "§c") + fortune + "§7/§a" + FORTUNE_MAX);
		return lines;
	}

	private static int width(Font font, List<String> lines, String title) {
		int width = font.width(title) + 16;
		for (String line : lines) width = Math.max(width, font.width(line) + PAD * 2);
		return width;
	}

	private static int height(int lines) {
		return MiningUi.PANEL_TITLE + PAD + lines * ROW + PAD - 3;
	}

	private static void drawLines(GuiGraphicsExtractor graphics, Font font, String title, List<String> lines, int x, int y, int width) {
		RenderUtils.drawStringCentered(graphics, title, font, x + width / 2f, y + MiningUi.PANEL_TITLE / 2f, true, 0xFFFFFF);
		int rowY = y + MiningUi.PANEL_TITLE + PAD;
		for (String line : lines) {
			RenderUtils.text(graphics, font, line, x + PAD, rowY, 0xFFFFFF, true);
			rowY += ROW;
		}
	}
}
