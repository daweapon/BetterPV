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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of the Forge 1.8.9 {@code BingoPage} ("Bingo" tab: personal/community goal grid). Unlike most of the
 * other newly-ported pages, every item icon here is a real vanilla item (paper/dye/iron-and-emerald blocks), so
 * no {@code jsonToStack} fallback is needed - this page ports essentially verbatim.
 */
public class BingoPage implements GuiProfileViewerPage {

	private static final Identifier BINGO_GUI_TEXTURE = Identifier.parse("notenoughupdates:pv_bingo_tab.png");

	private final GuiProfileViewer instance;
	private long lastResourceRequest;
	private List<JsonObject> bingoGoals = null;
	private int currentEventId;

	public BingoPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		bingoGoals = null;
		lastResourceRequest = 0;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		processBingoResources();
		JsonObject bingoInfo = GuiProfileViewer.getProfile().getBingoInformation();

		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		if (bingoInfo == null) {
			showMissingDataMessage(graphics, guiLeft, guiTop);
			return;
		}

		JsonArray events = bingoInfo.get("events").getAsJsonArray();
		if (events.size() == 0) {
			showMissingDataMessage(graphics, guiLeft, guiTop);
			return;
		}
		JsonObject lastEvent = events.get(events.size() - 1).getAsJsonObject();
		int lastParticipatedId = lastEvent.get("key").getAsInt();
		if (bingoGoals == null || currentEventId != lastParticipatedId) {
			showMissingDataMessage(graphics, guiLeft, guiTop);
			return;
		}

		List<String> completedGoals = jsonArrayToStringList(lastEvent.get("completed_goals").getAsJsonArray());
		RenderUtils.drawTexturedRect(graphics, BINGO_GUI_TEXTURE, guiLeft, guiTop, 431, 202);

		int row = 0;
		int col = 0;
		int initialY = guiTop + 46;
		int initialX = guiLeft + 231;
		int xAdjustment = 0;
		int yAdjustment = 0;
		for (JsonObject bingoGoal : bingoGoals) {
			boolean dye = false;
			boolean completed;
			boolean communityGoal;
			Item material;
			if (bingoGoal.has("tiers")) {
				material = isCommunityGoalFinished(bingoGoal) ? Blocks.EMERALD_BLOCK.asItem() : Blocks.IRON_BLOCK.asItem();
				completed = true;
				communityGoal = true;
				yAdjustment = -1;
				xAdjustment = -1;
			} else {
				communityGoal = false;
				if (completedGoals.contains(bingoGoal.get("id").getAsString())) {
					material = Items.LIME_DYE;
					xAdjustment = -1;
					dye = true;
					completed = true;
				} else {
					material = Items.PAPER;
					completed = false;
				}
			}

			ItemStack itemStack = new ItemStack(material);
			int x = col == 0 ? initialX + xAdjustment : initialX + (24 * col) + xAdjustment;
			int y = row == 0 ? initialY + yAdjustment : initialY + (24 * row) + yAdjustment;

			RenderUtils.drawItemStack(graphics, itemStack, x, y);
			int tooltipY = communityGoal ? y - 1 : y;
			if (mouseX >= x && mouseX < x + 24) {
				if (mouseY >= tooltipY && mouseY <= tooltipY + 24) {
					instance.tooltipToDisplay = getTooltip(bingoGoal, completed, communityGoal);
				}
			}
			col++;
			if (col == 5) {
				col = 0;
				row++;
			}
		}

		String totalPointsString =
			ChatFormatting.AQUA + "Collected Points: " + ChatFormatting.WHITE + lastEvent.get("points").getAsInt();
		int totalGoals = completedGoals.size();
		String personalGoalsString;
		if (totalGoals == 20) {
			personalGoalsString = ChatFormatting.AQUA + "Personal Goals: " + ChatFormatting.GOLD + "20/20";
		} else {
			personalGoalsString =
				ChatFormatting.AQUA +
					"Personal Goals: " +
					ChatFormatting.WHITE +
					completedGoals.size() +
					ChatFormatting.GOLD +
					"/" +
					ChatFormatting.WHITE +
					20;
		}
		RenderUtils.text(graphics, instance.getFont(), totalPointsString, guiLeft + 22, guiTop + 19, 0xFFFFFF, true);
		RenderUtils.text(graphics, instance.getFont(), personalGoalsString, guiLeft + 22, guiTop + 31, 0xFFFFFF, true);
	}

	private boolean isCommunityGoalFinished(JsonObject goal) {
		JsonArray tiers = goal.get("tiers").getAsJsonArray();
		int totalTiers = tiers.size();
		long progress = goal.get("progress").getAsLong();
		int finalTier = 0;
		for (JsonElement tier : tiers) {
			long currentTier = tier.getAsLong();
			if (progress < currentTier) {
				break;
			}
			finalTier++;
		}
		return finalTier == totalTiers;
	}

	private String generateProgressIndicator(double progress, double goal) {
		int totalFields = 20;
		int filled;
		double percentage = progress / goal * 100;
		if (percentage >= 100) {
			filled = 20;
		} else {
			filled = (int) Math.round((percentage / 100) * 20);
		}
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(ChatFormatting.DARK_GREEN);
		for (int i = 0; i < totalFields; i++) {
			stringBuilder.append("-");
			if (i > filled) {
				stringBuilder.append(ChatFormatting.GRAY);
			}
		}

		return stringBuilder.toString();
	}

	private List<String> getTooltip(JsonObject goal, boolean completed, boolean communityGoal) {
		List<String> tooltip = new ArrayList<>();
		if (communityGoal) {
			JsonArray tiers = goal.get("tiers").getAsJsonArray();
			int totalTiers = tiers.size();
			double progress = goal.get("progress").getAsLong();
			int finalTier = 0;
			for (JsonElement tier : tiers) {
				double currentTier = tier.getAsLong();
				if (progress < currentTier) {
					break;
				}
				finalTier++;
			}
			double nextTier = finalTier < totalTiers ? tiers.get(totalTiers - 1).getAsLong() : tiers
				.get(finalTier - 1)
				.getAsLong();
			int progressToNextTier = (int) Math.round(progress / nextTier * 100);
			if (progressToNextTier > 100) progressToNextTier = 100;
			String progressBar = generateProgressIndicator(progress, nextTier);
			String name = goal.get("name").getAsString();
			int nextTierNum = finalTier < totalTiers ? finalTier + 1 : totalTiers;

			String nextTierString = StringUtils.shortNumberFormat(nextTier, 0);
			String progressString = StringUtils.shortNumberFormat(progress, 0);
			tooltip.add(ChatFormatting.GREEN + name + " " + finalTier);
			tooltip.add(ChatFormatting.DARK_GRAY + "Community Goal");
			tooltip.add("");
			tooltip.add(
				ChatFormatting.GRAY +
					"Progress to " +
					name +
					" " +
					nextTierNum +
					": " +
					ChatFormatting.YELLOW +
					progressToNextTier +
					ChatFormatting.GOLD +
					"%"
			);
			tooltip.add(
				progressBar +
					ChatFormatting.YELLOW +
					" " +
					progressString +
					ChatFormatting.GOLD +
					"/" +
					ChatFormatting.YELLOW +
					nextTierString
			);
			tooltip.add("");
			tooltip.add(ChatFormatting.DARK_GRAY.toString() + ChatFormatting.ITALIC + "Community Goals are");
			tooltip.add(ChatFormatting.DARK_GRAY.toString() + ChatFormatting.ITALIC + "collaborative - anyone with a");
			tooltip.add(ChatFormatting.DARK_GRAY.toString() + ChatFormatting.ITALIC + "Bingo profile can help to reach");
			tooltip.add(ChatFormatting.DARK_GRAY.toString() + ChatFormatting.ITALIC + "the goal!");
			tooltip.add("");
			tooltip.add(ChatFormatting.DARK_GRAY.toString() + ChatFormatting.ITALIC + "The more you contribute");
			tooltip.add(ChatFormatting.DARK_GRAY.toString() + ChatFormatting.ITALIC + "towards the goal, the more you");
			tooltip.add(ChatFormatting.DARK_GRAY.toString() + ChatFormatting.ITALIC + "will be rewarded");

			if (finalTier == totalTiers) {
				tooltip.add("");
				tooltip.add(ChatFormatting.GREEN + "GOAL REACHED");
			}
		} else {
			tooltip.add(ChatFormatting.GREEN + goal.get("name").getAsString());
			tooltip.add(ChatFormatting.DARK_GRAY + "Personal Goal");
			tooltip.add("");
			tooltip.add(goal.get("lore").getAsString());
			tooltip.add("");
			tooltip.add(ChatFormatting.GRAY + "Reward");
			tooltip.add(ChatFormatting.GOLD + "1 Bingo Point");
			if (completed) {
				tooltip.add("");
				tooltip.add(ChatFormatting.GREEN + "GOAL REACHED");
			} else {
				tooltip.add("");
				tooltip.add(ChatFormatting.RED + "You have not reached this goal!");
			}
		}
		return tooltip;
	}

	private void showMissingDataMessage(GuiGraphicsExtractor graphics, int guiLeft, int guiTop) {
		RenderUtils.drawStringCentered(
			graphics, ChatFormatting.RED + "No Bingo data for current event!", instance.getFont(),
			guiLeft + 431 / 2f, guiTop + 101, true, 0
		);
	}

	private List<String> jsonArrayToStringList(JsonArray completedGoals) {
		List<String> list = new ArrayList<>();
		for (JsonElement completedGoal : completedGoals) {
			list.add(completedGoal.getAsString());
		}
		return list;
	}

	private List<JsonObject> jsonArrayToJsonObjectList(JsonArray goals) {
		List<JsonObject> list = new ArrayList<>();
		for (JsonElement goal : goals) {
			list.add(goal.getAsJsonObject());
		}

		return list;
	}

	private void processBingoResources() {
		long currentTime = System.currentTimeMillis();

		// renew every 2 minutes
		if (currentTime - lastResourceRequest < 120 * 1000 && bingoGoals != null) return;
		lastResourceRequest = currentTime;

		NotEnoughUpdates.INSTANCE.manager.apiUtils
			.newAnonymousHypixelApiRequest("resources/skyblock/bingo")
			.requestJson()
			.thenAccept(jsonObject -> {
				if (jsonObject.has("success") && jsonObject.get("success").getAsBoolean()) {
					bingoGoals = jsonArrayToJsonObjectList(jsonObject.get("goals").getAsJsonArray());
					currentEventId = jsonObject.get("id").getAsInt();
				}
			});
	}
}
