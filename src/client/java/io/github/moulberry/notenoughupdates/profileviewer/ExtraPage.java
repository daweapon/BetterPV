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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.text.WordUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Port of the Forge 1.8.9 {@code ExtraPage} ("Profile Stats" tab).
 *
 * <p>TODO(fabric-port): pronoun display (via {@code GuiProfileViewer.pronouns}/PronounDB) was not ported in the
 * data-layer pass, so it's skipped here as well (same as {@link BasicPage}).
 */
public class ExtraPage implements GuiProfileViewerPage {

	private static final Identifier pv_extra = Identifier.parse("notenoughupdates:pv_extra.png");

	private final GuiProfileViewer instance;
	private TreeMap<Integer, Set<String>> topKills = null;
	private TreeMap<Integer, Set<String>> topDeaths = null;

	public ExtraPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		topKills = null;
		topDeaths = null;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		RenderUtils.drawTexturedRect(graphics, pv_extra, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject profileInfo = profile.getProfileInformation(profileId);
		if (profileInfo == null) return;
		Map<String, ProfileViewer.Level> skyblockInfo = profile.getSkyblockInfo(profileId);

		float xStart = 22;
		float xOffset = 103;
		float yStartTop = 27;
		float yStartBottom = 105;
		float yOffset = 10;

		float bankBalance = Utils.getElementAsFloat(Utils.getElement(profileInfo, "banking.balance"), 0);
		float purseBalance = Utils.getElementAsFloat(Utils.getElement(profileInfo, "coin_purse"), 0);

		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.GOLD + "Bank Balance",
			ChatFormatting.WHITE + StringUtils.shortNumberFormat(bankBalance),
			guiLeft + xStart,
			guiTop + yStartTop,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.GOLD + "Purse",
			ChatFormatting.WHITE + StringUtils.shortNumberFormat(purseBalance),
			guiLeft + xStart,
			guiTop + yStartTop + yOffset,
			76
		);

		{
			String first_join = getTimeSinceString(profileInfo, "first_join");
			if (first_join != null) {
				RenderUtils.renderAlignedString(
					graphics,
					ChatFormatting.AQUA + "Joined",
					ChatFormatting.WHITE + first_join,
					guiLeft + xStart,
					guiTop + yStartTop + yOffset * 2,
					76
				);
			}
		}
		JsonObject guildInfo = profile.getGuildInformation(null);
		boolean shouldRenderGuild = guildInfo != null && guildInfo.has("name");
		{
			if (shouldRenderGuild) {
				RenderUtils.renderAlignedString(
					graphics,
					ChatFormatting.AQUA + "Guild",
					ChatFormatting.WHITE + guildInfo.get("name").getAsString(),
					guiLeft + xStart,
					guiTop + yStartTop + yOffset * 3,
					76
				);
			}
		}
		// TODO(fabric-port): pronoun lookup/display (PronounDB) wasn't ported in the data-layer pass; skipped.

		float fairySouls = Utils.getElementAsFloat(Utils.getElement(profileInfo, "fairy_souls_collected"), 0);

		int fairySoulMax = 227;
		if (Constants.FAIRYSOULS != null && Constants.FAIRYSOULS.has("Max Souls")) {
			fairySoulMax = Constants.FAIRYSOULS.get("Max Souls").getAsInt();
		}
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.LIGHT_PURPLE + "Fairy Souls",
			ChatFormatting.WHITE.toString() + (int) fairySouls + "/" + fairySoulMax,
			guiLeft + xStart,
			guiTop + yStartBottom,
			76
		);

		if (skyblockInfo != null) {
			float totalSkillLVL = 0;
			float totalTrueSkillLVL = 0;
			float totalSlayerLVL = 0;
			float totalSkillCount = 0;
			float totalSlayerCount = 0;
			float totalSlayerXP = 0;

			List<String> skills = Arrays.asList(
				"taming",
				"mining",
				"foraging",
				"enchanting",
				"farming",
				"combat",
				"fishing",
				"alchemy",
				"carpentry"
			);
			List<String> slayers = Arrays.asList("zombie", "spider", "wolf", "enderman", "blaze");

			for (Map.Entry<String, ProfileViewer.Level> entry : skyblockInfo.entrySet()) {
				if (skills.contains(entry.getKey())) {
					totalSkillLVL += entry.getValue().level;
					totalTrueSkillLVL += Math.floor(entry.getValue().level);
					totalSkillCount++;
				} else if (slayers.contains(entry.getKey())) {
					totalSlayerLVL += entry.getValue().level;
					totalSlayerCount++;
					totalSlayerXP += entry.getValue().totalXp;
				}
			}

			float avgSkillLVL = totalSkillLVL / totalSkillCount;
			float avgTrueSkillLVL = totalTrueSkillLVL / totalSkillCount;
			float avgSlayerLVL = totalSlayerLVL / totalSlayerCount;

			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.RED + "AVG Skill Level",
				ChatFormatting.WHITE.toString() + Math.floor(avgSkillLVL * 10) / 10,
				guiLeft + xStart,
				guiTop + yStartBottom + yOffset,
				76
			);

			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.RED + "True AVG Skill Level",
				ChatFormatting.WHITE.toString() + Math.floor(avgTrueSkillLVL * 10) / 10,
				guiLeft + xStart,
				guiTop + yStartBottom + yOffset * 2,
				76
			);

			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.RED + "AVG Slayer Level",
				ChatFormatting.WHITE.toString() + Math.floor(avgSlayerLVL * 10) / 10,
				guiLeft + xStart,
				guiTop + yStartBottom + yOffset * 3,
				76
			);

			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.RED + "Total Slayer XP",
				ChatFormatting.WHITE + StringUtils.shortNumberFormat(totalSlayerXP),
				guiLeft + xStart,
				guiTop + yStartBottom + yOffset * 4,
				76
			);
		}

		float auctions_bids = Utils.getElementAsFloat(Utils.getElement(profileInfo, "stats.auctions_bids"), 0);
		float auctions_highest_bid = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "stats.auctions_highest_bid"),
			0
		);
		float auctions_won = Utils.getElementAsFloat(Utils.getElement(profileInfo, "stats.auctions_won"), 0);
		float auctions_created = Utils.getElementAsFloat(Utils.getElement(profileInfo, "stats.auctions_created"), 0);
		float auctions_gold_spent = Utils.getElementAsFloat(Utils.getElement(profileInfo, "stats.auctions_gold_spent"), 0);
		float auctions_gold_earned = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "stats.auctions_gold_earned"),
			0
		);

		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_PURPLE + "Auction Bids",
			ChatFormatting.WHITE.toString() + (int) auctions_bids,
			guiLeft + xStart + xOffset,
			guiTop + yStartTop,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_PURPLE + "Highest Bid",
			ChatFormatting.WHITE + StringUtils.shortNumberFormat(auctions_highest_bid),
			guiLeft + xStart + xOffset,
			guiTop + yStartTop + yOffset,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_PURPLE + "Auctions Won",
			ChatFormatting.WHITE.toString() + (int) auctions_won,
			guiLeft + xStart + xOffset,
			guiTop + yStartTop + yOffset * 2,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_PURPLE + "Auctions Created",
			ChatFormatting.WHITE.toString() + (int) auctions_created,
			guiLeft + xStart + xOffset,
			guiTop + yStartTop + yOffset * 3,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_PURPLE + "Gold Spent",
			ChatFormatting.WHITE + StringUtils.shortNumberFormat(auctions_gold_spent),
			guiLeft + xStart + xOffset,
			guiTop + yStartTop + yOffset * 4,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_PURPLE + "Gold Earned",
			ChatFormatting.WHITE + StringUtils.shortNumberFormat(auctions_gold_earned),
			guiLeft + xStart + xOffset,
			guiTop + yStartTop + yOffset * 5,
			76
		);

		// Slayer values
		float zombie_boss_kills_tier_2 = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "slayer_bosses.zombie.boss_kills_tier_2"),
			0
		);
		float zombie_boss_kills_tier_3 = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "slayer_bosses.zombie.boss_kills_tier_3"),
			0
		);
		float zombie_boss_kills_tier_4 = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "slayer_bosses.zombie.boss_kills_tier_4"),
			0
		);
		float wolf_boss_kills_tier_2 = Utils.getElementAsFloat(Utils.getElement(
			profileInfo,
			"slayer_bosses.wolf.boss_kills_tier_2"
		), 0);
		float wolf_boss_kills_tier_3 = Utils.getElementAsFloat(Utils.getElement(
			profileInfo,
			"slayer_bosses.wolf.boss_kills_tier_3"
		), 0);
		float spider_boss_kills_tier_2 = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "slayer_bosses.spider.boss_kills_tier_2"),
			0
		);
		float spider_boss_kills_tier_3 = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "slayer_bosses.spider.boss_kills_tier_3"),
			0
		);
		float enderman_boss_kills_tier_2 = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "slayer_bosses.enderman.boss_kills_tier_2"),
			0
		);
		float enderman_boss_kills_tier_3 = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "slayer_bosses.enderman.boss_kills_tier_3"),
			0
		);
		float blaze_boss_kills_tier_2 = Utils.getElementAsFloat(Utils.getElement(
			profileInfo,
			"slayer_bosses.blaze.boss_kills_tier_2"
		), 0);
		float blaze_boss_kills_tier_3 = Utils.getElementAsFloat(Utils.getElement(
			profileInfo,
			"slayer_bosses.blaze.boss_kills_tier_3"
		), 0);

		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Revenant T3",
			ChatFormatting.WHITE.toString() + (int) zombie_boss_kills_tier_2,
			guiLeft + xStart + xOffset,
			guiTop + yStartBottom,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Revenant T4",
			ChatFormatting.WHITE.toString() + (int) zombie_boss_kills_tier_3,
			guiLeft + xStart + xOffset,
			guiTop + yStartBottom + yOffset,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Revenant T5",
			ChatFormatting.WHITE.toString() + (int) zombie_boss_kills_tier_4,
			guiLeft + xStart + xOffset,
			guiTop + yStartBottom + yOffset * 2,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Tarantula T3",
			ChatFormatting.WHITE.toString() + (int) spider_boss_kills_tier_2,
			guiLeft + xStart + xOffset,
			guiTop + yStartBottom + yOffset * 3,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Tarantula T4",
			ChatFormatting.WHITE.toString() + (int) spider_boss_kills_tier_3,
			guiLeft + xStart + xOffset,
			guiTop + yStartBottom + yOffset * 4,
			76
		);

		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Sven T3",
			ChatFormatting.WHITE.toString() + (int) wolf_boss_kills_tier_2,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartBottom + yOffset * 0,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Sven T4",
			ChatFormatting.WHITE.toString() + (int) wolf_boss_kills_tier_3,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartBottom + yOffset * 1,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Voidgloom T3",
			ChatFormatting.WHITE.toString() + (int) enderman_boss_kills_tier_2,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartBottom + yOffset * 2,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Voidgloom T4",
			ChatFormatting.WHITE.toString() + (int) enderman_boss_kills_tier_3,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartBottom + yOffset * 3,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Inferno T3",
			ChatFormatting.WHITE.toString() + (int) blaze_boss_kills_tier_2,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartBottom + yOffset * 4,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.DARK_AQUA + "Inferno T4",
			ChatFormatting.WHITE.toString() + (int) blaze_boss_kills_tier_3,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartBottom + yOffset * 5,
			76
		);

		float pet_milestone_ores_mined = Utils.getElementAsFloat(Utils.getElement(
			profileInfo,
			"stats.pet_milestone_ores_mined"
		), 0);
		float pet_milestone_sea_creatures_killed = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "stats.pet_milestone_sea_creatures_killed"),
			0
		);

		float items_fished = Utils.getElementAsFloat(Utils.getElement(profileInfo, "stats.items_fished"), 0);
		float items_fished_treasure = Utils.getElementAsFloat(
			Utils.getElement(profileInfo, "stats.items_fished_treasure"),
			0
		);
		float items_fished_large_treasure = Utils.getElementAsFloat(Utils.getElement(
			profileInfo,
			"stats.items_fished_large_treasure"
		), 0);

		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.GREEN + "Ores Mined",
			ChatFormatting.WHITE.toString() + (int) pet_milestone_ores_mined,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartTop,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.GREEN + "Sea Creatures Killed",
			ChatFormatting.WHITE.toString() + (int) pet_milestone_sea_creatures_killed,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartTop + yOffset,
			76
		);

		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.GREEN + "Items Fished",
			ChatFormatting.WHITE.toString() + (int) items_fished,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartTop + yOffset * 3,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.GREEN + "Treasures Fished",
			ChatFormatting.WHITE.toString() + (int) items_fished_treasure,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartTop + yOffset * 4,
			76
		);
		RenderUtils.renderAlignedString(
			graphics,
			ChatFormatting.GREEN + "Large Treasures",
			ChatFormatting.WHITE.toString() + (int) items_fished_large_treasure,
			guiLeft + xStart + xOffset * 2,
			guiTop + yStartTop + yOffset * 5,
			76
		);

		if (topKills == null) {
			topKills = new TreeMap<>();
			JsonObject stats = profileInfo.get("stats").getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : stats.entrySet()) {
				if (entry.getKey().startsWith("kills_")) {
					if (entry.getValue().isJsonPrimitive()) {
						JsonPrimitive prim = (JsonPrimitive) entry.getValue();
						if (prim.isNumber()) {
							String name = WordUtils.capitalizeFully(entry.getKey().substring("kills_".length()).replace("_", " "));
							Set<String> kills = topKills.computeIfAbsent(prim.getAsInt(), k -> new HashSet<>());
							kills.add(name);
						}
					}
				}
			}
		}
		if (topDeaths == null) {
			topDeaths = new TreeMap<>();
			JsonObject stats = profileInfo.get("stats").getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : stats.entrySet()) {
				if (entry.getKey().startsWith("deaths_")) {
					if (entry.getValue().isJsonPrimitive()) {
						JsonPrimitive prim = (JsonPrimitive) entry.getValue();
						if (prim.isNumber()) {
							String name = WordUtils.capitalizeFully(entry.getKey().substring("deaths_".length()).replace("_", " "));
							Set<String> deaths = topDeaths.computeIfAbsent(prim.getAsInt(), k -> new HashSet<>());
							deaths.add(name);
						}
					}
				}
			}
		}

		int index = 0;
		for (int killCount : topKills.descendingKeySet()) {
			if (index >= 6) break;
			Set<String> kills = topKills.get(killCount);
			for (String killType : kills) {
				if (index >= 6) break;
				RenderUtils.renderAlignedString(
					graphics,
					ChatFormatting.YELLOW + killType + " Kills",
					ChatFormatting.WHITE.toString() + killCount,
					guiLeft + xStart + xOffset * 3,
					guiTop + yStartTop + yOffset * index,
					76
				);
				index++;
			}
		}
		index = 0;
		for (int deathCount : topDeaths.descendingKeySet()) {
			if (index >= 6) break;
			Set<String> deaths = topDeaths.get(deathCount);
			for (String deathType : deaths) {
				if (index >= 6) break;
				RenderUtils.renderAlignedString(
					graphics,
					ChatFormatting.YELLOW + "Deaths: " + deathType,
					ChatFormatting.WHITE.toString() + deathCount,
					guiLeft + xStart + xOffset * 3,
					guiTop + yStartBottom + yOffset * index,
					76
				);
				index++;
			}
		}
	}

	private String getTimeSinceString(JsonObject profileInfo, String path) {
		JsonElement lastSaveElement = Utils.getElement(profileInfo, path);

		if (lastSaveElement != null && lastSaveElement.isJsonPrimitive()) {
			Instant lastSave = Instant.ofEpochMilli(lastSaveElement.getAsLong());
			LocalDateTime lastSaveTime = LocalDateTime.ofInstant(lastSave, TimeZone.getDefault().toZoneId());
			long timeDiff = System.currentTimeMillis() - lastSave.toEpochMilli();
			LocalDateTime sinceOnline = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeDiff), ZoneId.of("UTC"));
			String renderText;

			if (timeDiff < 60000L) {
				renderText = sinceOnline.getSecond() + " seconds ago.";
			} else if (timeDiff < 3600000L) {
				renderText = sinceOnline.getMinute() + " minutes ago.";
			} else if (timeDiff < 86400000L) {
				renderText = sinceOnline.getHour() + " hours ago.";
			} else if (timeDiff < 31556952000L) {
				renderText = sinceOnline.getDayOfYear() + " days ago.";
			} else {
				renderText = lastSaveTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
			}
			return renderText;
		}
		return null;
	}
}
