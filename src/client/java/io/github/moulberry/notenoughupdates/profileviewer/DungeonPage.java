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

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** The Dungeoneering tab: Catacombs level and floor time calculator, boss completions and class levels. */
public class DungeonPage implements GuiProfileViewerPage {

	private static final Identifier pv_dung = Identifier.parse("betterpv:pv_dung.png");
	private static final Identifier pv_elements = Identifier.parse("betterpv:pv_elements.png");
	private static final ItemStack DEADBUSH = new ItemStack(Blocks.DEAD_BUSH);
	private static final String[] dungSkillsName = { "Healer", "Mage", "Berserk", "Archer", "Tank" };
	private static final ItemStack[] BOSS_HEADS = new ItemStack[7];
	private static final String[] bossFloorArr = { "Bonzo", "Scarf", "Professor", "Thorn", "Livid", "Sadan", "Necron" };
	private static final String[] bossFloorHeads = {
		"12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c",
		"7de7bbbdf22bfe17980d4e20687e386f11d59ee1db6f8b4762391b79a5ac532d",
		"9971cee8b833a62fc2a612f3503437fdf93cad692d216b8cf90bbb0538c47dd8",
		"8b6a72138d69fbbd2fea3fa251cabd87152e4f1c97e5f986bf685571db3cc0",
		"c1007c5b7114abec734206d4fc613da4f3a0e99f71ff949cedadc99079135a0b",
		"fa06cb0c471c1c9bc169af270cd466ea701946776056e472ecdaeb49f0f4a4dc",
		"a435164c05cea299a3f016bbbed05706ebb720dac912ce4351c2296626aecd9a",
	};

	private static ItemStack[] dungSkillsStack;
	private static LinkedHashMap<String, ItemStack> dungeonsModeIcons;

	private static ItemStack[] dungSkillsStack() {
		if (dungSkillsStack == null) {
			dungSkillsStack = new ItemStack[] {
				new ItemStack(Items.SPLASH_POTION),
				new ItemStack(Items.BLAZE_ROD),
				new ItemStack(Items.IRON_SWORD),
				new ItemStack(Items.BOW),
				new ItemStack(Items.LEATHER_CHESTPLATE),
			};
		}
		return dungSkillsStack;
	}

	private static LinkedHashMap<String, ItemStack> dungeonsModeIcons() {
		if (dungeonsModeIcons == null) {
			dungeonsModeIcons = new LinkedHashMap<>();
			dungeonsModeIcons.put("catacombs", repoIconOrFallback(
				"DUNGEON_STONE", Utils.createItemStack(Blocks.STONE_BRICKS, ChatFormatting.GRAY + "Normal Mode")
			));
			dungeonsModeIcons.put("master_catacombs", repoIconOrFallback(
				"MASTER_SKULL_TIER_7", Utils.createItemStack(Blocks.NETHERITE_BLOCK, ChatFormatting.GRAY + "Master Mode")
			));
		}
		return dungeonsModeIcons;
	}

	private static ItemStack repoIconOrFallback(String internalname, ItemStack fallback) {
		com.google.gson.JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(internalname);
		return json != null ? NotEnoughUpdates.INSTANCE.manager.jsonToStack(json) : fallback;
	}

	private final GuiProfileViewer instance;
	private final HashMap<String, HashMap<String, ProfileViewer.Level>> levelObjClasseses = new HashMap<>();
	private final HashMap<String, ProfileViewer.Level> levelObjCatas = new HashMap<>();
	private EditBox dungeonLevelTextField;
	private int floorLevelTo = -1;
	private long floorLevelToXP = -1;
	private boolean onMasterMode = false;
	private int floorTime = 7;

	public DungeonPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		levelObjClasseses.clear();
		levelObjCatas.clear();
		floorLevelTo = -1;
		floorLevelToXP = -1;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		RenderUtils.drawTexturedRect(graphics, pv_dung, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject hypixelInfo = profile.getHypixelProfile();
		if (hypixelInfo == null) return;
		JsonObject profileInfo = profile.getProfileInformation(profileId);
		if (profileInfo == null) return;

		JsonObject leveling = Constants.LEVELING;
		if (leveling == null) return;

		if (dungeonLevelTextField == null) {
			dungeonLevelTextField = new EditBox(instance.getFont(), guiLeft + 45, guiTop + 54, 20, 10, Component.literal("Level"));
			dungeonLevelTextField.setBordered(false);
			dungeonLevelTextField.setMaxLength(4);
		}

		int sectionWidth = 110;

		String dungeonString = onMasterMode ? "master_catacombs" : "catacombs";

		RenderUtils.drawStringCentered(
			graphics, ChatFormatting.RED + (onMasterMode ? "Master Mode" : "Catacombs"), instance.getFont(),
			guiLeft + instance.sizeX / 2f, guiTop + 5, true, 0
		);

		ProfileViewer.Level levelObjCata = levelObjCatas.get(profileId);
		{
			if (levelObjCata == null) {
				float cataXp = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.experience"), 0);
				levelObjCata = ProfileViewer.getLevel(
					Utils.getElementOrDefault(leveling, "catacombs", new JsonArray()).getAsJsonArray(), cataXp, 99, false
				);
				levelObjCata.totalXp = cataXp;
				levelObjCatas.put(profileId, levelObjCata);
			}

			String skillName = ChatFormatting.RED + "Catacombs";
			float level = levelObjCata.level;
			int levelFloored = (int) Math.floor(level);

			if (floorLevelTo == -1 && levelFloored >= 0) {
				dungeonLevelTextField.setValue("" + (levelFloored + 1));
				calculateFloorLevelXP();
			}

			int x = guiLeft + 23;
			int y = guiTop + 25;

			// The level is computed up to 99 so the "Until Cata N" calculator can go past 50, but Catacombs itself
			// maxes at 50 (leveling_caps): from there show the rainbow bar, but keep the overflow level number.
			int cataCap = (int) Utils.getElementAsFloat(Utils.getElement(leveling, "leveling_caps.catacombs"), 50);
			ProfileViewer.Level shownCata = levelObjCata;
			if (levelObjCata.level >= cataCap) {
				shownCata = new ProfileViewer.Level();
				shownCata.level = levelObjCata.level;
				shownCata.maxLevel = cataCap;
				shownCata.totalXp = levelObjCata.totalXp;
				shownCata.maxed = true;
			}
			instance.renderXpBar(graphics, skillName, DEADBUSH, x, y, sectionWidth, shownCata, mouseX, mouseY);

			RenderUtils.renderAlignedString(
				graphics, ChatFormatting.YELLOW + "Until Cata " + floorLevelTo + ": ",
				ChatFormatting.WHITE + StringUtils.shortNumberFormat((double) floorLevelToXP), x, y + 16, sectionWidth
			);

			if (mouseX > x && mouseX < x + sectionWidth && mouseY > y + 16 && mouseY < y + 24) {
				instance.tooltipToDisplay = buildRunsTooltip(profileInfo, onMasterMode);
			}

			dungeonLevelTextField.extractWidgetRenderState(graphics, mouseX, mouseY, partialTicks);
			int calcLen = instance.getFont().width("Calculate");
			RenderUtils.drawStringCentered(
				graphics, ChatFormatting.WHITE + "Calculate", instance.getFont(), x + sectionWidth - 17 - calcLen / 2f, y + 30, true, 0
			);

			float secrets = Utils.getElementAsFloat(Utils.getElement(hypixelInfo, "achievements.skyblock_treasure_hunter"), 0);
			float totalRunsF = 0;
			float totalRunsF5 = 0;
			for (int i = 1; i <= 7; i++) {
				float runs = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.tier_completions." + i), 0);
				totalRunsF += runs;
				if (i >= 5) totalRunsF5 += runs;
			}
			float totalRunsM = 0;
			float totalRunsM5 = 0;
			for (int i = 1; i <= 7; i++) {
				float runs = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.tier_completions." + i), 0);
				totalRunsM += runs;
				if (i >= 5) totalRunsM5 += runs;
			}
			float totalRuns = totalRunsF + totalRunsM;

			float mobKillsF = 0;
			for (int i = 1; i <= 7; i++) {
				mobKillsF += Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.mobs_killed." + i), 0);
			}
			float mobKillsM = 0;
			for (int i = 1; i <= 7; i++) {
				mobKillsM += Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.mobs_killed." + i), 0);
			}
			float mobKills = mobKillsF + mobKillsM;

			int miscTopY = y + 55;

			RenderUtils.renderAlignedString(
				graphics, ChatFormatting.YELLOW + "Total Runs " + (onMasterMode ? "M" : "F"),
				ChatFormatting.WHITE.toString() + ((int) (onMasterMode ? totalRunsM : totalRunsF)), x, miscTopY, sectionWidth
			);
			RenderUtils.renderAlignedString(
				graphics, ChatFormatting.YELLOW + "Total Runs (" + (onMasterMode ? "M" : "F") + "5-7)  ",
				ChatFormatting.WHITE.toString() + ((int) (onMasterMode ? totalRunsM5 : totalRunsF5)), x, miscTopY + 10, sectionWidth
			);
			RenderUtils.renderAlignedString(
				graphics, ChatFormatting.YELLOW + "Secrets (Total)  ", ChatFormatting.WHITE + StringUtils.shortNumberFormat(secrets),
				x, miscTopY + 20, sectionWidth
			);
			RenderUtils.renderAlignedString(
				graphics, ChatFormatting.YELLOW + "Secrets (/Run)  ",
				ChatFormatting.WHITE.toString() + (Math.round(secrets / Math.max(1, totalRuns) * 100) / 100f), x, miscTopY + 30, sectionWidth
			);
			RenderUtils.renderAlignedString(
				graphics, ChatFormatting.YELLOW + "Mob Kills (Total)  ", ChatFormatting.WHITE + StringUtils.shortNumberFormat(mobKills),
				x, miscTopY + 40, sectionWidth
			);

			int y3 = y + 117;

			for (int i = 1; i <= 7; i++) {
				int w = instance.getFont().width("" + i);
				int bx = x + sectionWidth * i / 8 - w / 2;

				boolean invert = i == floorTime;
				float uMin = 20 / 256f;
				float uMax = 29 / 256f;
				float vMin = 0 / 256f;
				float vMax = 11 / 256f;

				RenderUtils.drawTexturedRect(
					graphics, pv_elements, bx - 2, y3 - 2, 9, 11,
					invert ? uMax : uMin, invert ? uMin : uMax, invert ? vMax : vMin, invert ? vMin : vMax
				);

				RenderUtils.drawStringCentered(graphics, ChatFormatting.WHITE.toString() + i, instance.getFont(), bx + w / 2f, y3 + 5, true, 0);
			}

			float timeNorm = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types." + dungeonString + ".fastest_time." + floorTime), 0);
			float timeS = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types." + dungeonString + ".fastest_time_s." + floorTime), 0);
			float timeSPLUS = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types." + dungeonString + ".fastest_time_s_plus." + floorTime), 0);
			String timeNormStr = timeNorm <= 0 ? "N/A" : prettyTime((long) timeNorm);
			String timeSStr = timeS <= 0 ? "N/A" : prettyTime((long) timeS);
			String timeSPlusStr = timeSPLUS <= 0 ? "N/A" : prettyTime((long) timeSPLUS);
			RenderUtils.renderAlignedString(graphics, ChatFormatting.YELLOW + "Floor " + floorTime + " ", ChatFormatting.WHITE + timeNormStr, x, y3 + 10, sectionWidth);
			RenderUtils.renderAlignedString(graphics, ChatFormatting.YELLOW + "Floor " + floorTime + " S", ChatFormatting.WHITE + timeSStr, x, y3 + 20, sectionWidth);
			RenderUtils.renderAlignedString(graphics, ChatFormatting.YELLOW + "Floor " + floorTime + " S+", ChatFormatting.WHITE + timeSPlusStr, x, y3 + 30, sectionWidth);
		}

		// Boss Collections
		{
			int x = guiLeft + 161;
			int y = guiTop + 27;

			RenderUtils.drawStringCentered(graphics, ChatFormatting.RED + "Boss Collections", instance.getFont(), x + sectionWidth / 2f, y, true, 0);
			for (int i = 1; i <= 7; i++) {
				float compl = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types." + dungeonString + ".tier_completions." + i), 0);

				if (BOSS_HEADS[i - 1] == null) {
					String textureLink = bossFloorHeads[i - 1];
					String b64Decoded = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + textureLink + "\"}}}";
					String b64Encoded = new String(Base64.getEncoder().encode(b64Decoded.getBytes()));
					String uuid = UUID.nameUUIDFromBytes(b64Encoded.getBytes()).toString();
					BOSS_HEADS[i - 1] = Utils.createSkull(bossFloorArr[i - 1], uuid, b64Encoded);
				}

				RenderUtils.drawItemStack(graphics, BOSS_HEADS[i - 1], x - 4, y + 10 + 20 * (i - 1));

				RenderUtils.renderAlignedString(
					graphics,
					String.format(ChatFormatting.YELLOW + "%s (" + (onMasterMode ? "M" : "F") + "%d) ", bossFloorArr[i - 1], i),
					ChatFormatting.WHITE.toString() + (int) compl,
					x + 16, y + 18 + 20 * (i - 1), sectionWidth - 15
				);
			}
		}

		// Class Levels
		{
			int x = guiLeft + 298;
			int y = guiTop + 27;

			RenderUtils.drawStringCentered(graphics, ChatFormatting.DARK_PURPLE + "Class Levels", instance.getFont(), x + sectionWidth / 2f, y, true, 0);

			JsonElement activeClassElement = Utils.getElement(profileInfo, "dungeons.selected_dungeon_class");
			String activeClass = null;
			if (activeClassElement instanceof JsonPrimitive && ((JsonPrimitive) activeClassElement).isString()) {
				activeClass = activeClassElement.getAsString();
			}

			for (int i = 0; i < dungSkillsName.length; i++) {
				String skillName = dungSkillsName[i];

				HashMap<String, ProfileViewer.Level> levelObjClasses = levelObjClasseses.computeIfAbsent(profileId, k -> new HashMap<>());
				if (!levelObjClasses.containsKey(skillName)) {
					float cataXp = Utils.getElementAsFloat(
						Utils.getElement(profileInfo, "dungeons.player_classes." + skillName.toLowerCase(java.util.Locale.US) + ".experience"), 0
					);
					ProfileViewer.Level levelObj = ProfileViewer.getLevel(
						Utils.getElementOrDefault(leveling, "catacombs", new JsonArray()).getAsJsonArray(), cataXp, 50, false
					);
					// getLevel only flags levels past the cap, so exactly 50 would still show a progress bar.
					if (levelObj.level >= 50) levelObj.maxed = true;
					levelObjClasses.put(skillName, levelObj);
				}

				String colour = ChatFormatting.WHITE.toString();
				if (skillName.toLowerCase(java.util.Locale.US).equals(activeClass)) {
					colour = ChatFormatting.GREEN.toString();
				}

				ProfileViewer.Level levelObj = levelObjClasses.get(skillName);

				instance.renderXpBar(graphics, colour + skillName, dungSkillsStack()[i], x, y + 20 + 29 * i, sectionWidth, levelObj, mouseX, mouseY);
			}
		}

		drawSideButtons(graphics, mouseX, mouseY);
	}

	private List<String> buildRunsTooltip(JsonObject profileInfo, boolean master) {
		if (master) return buildMasterRunsTooltip(profileInfo);
		return buildNormalRunsTooltip(profileInfo);
	}

	private List<String> buildNormalRunsTooltip(JsonObject profileInfo) {
		float F5 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.tier_completions.5"), 0);
		float F6 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.tier_completions.6"), 0);
		float F7 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.tier_completions.7"), 0);
		if (F5 > 150) F5 = 150;
		if (F6 > 100) F6 = 100;
		if (F7 > 50) F7 = 50;
		float xpF5 = 2400 * (F5 / 100 + 1);
		float xpF6 = 4880 * (F6 / 100 + 1);
		float xpF7 = 28000 * (F7 / 100 + 1);
		boolean shift = isShiftDown();
		if (!shift) {
			xpF5 *= 1.1;
			xpF6 *= 1.1;
			xpF7 *= 1.1;
		}

		long runsF5 = (int) Math.ceil(floorLevelToXP / xpF5);
		long runsF6 = (int) Math.ceil(floorLevelToXP / xpF6);
		long runsF7 = (int) Math.ceil(floorLevelToXP / xpF7);

		float timeF5 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.fastest_time_s_plus.5"), 0);
		float timeF6 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.fastest_time_s_plus.6"), 0);
		float timeF7 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.fastest_time_s_plus.7"), 0);

		List<String> tooltip = Lists.newArrayList(
			String.format("# F5 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpF5), runsF5),
			String.format("# F6 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpF6), runsF6),
			String.format("# F7 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpF7), runsF7),
			""
		);
		boolean hasTime = false;
		if (timeF5 > 1000) {
			tooltip.add(String.format("Expected Time (F5) : %s", prettyTime(runsF5 * (long) (timeF5 * 1.2))));
			hasTime = true;
		}
		if (timeF6 > 1000) {
			tooltip.add(String.format("Expected Time (F6) : %s", prettyTime(runsF6 * (long) (timeF6 * 1.2))));
			hasTime = true;
		}
		if (timeF7 > 1000) {
			tooltip.add(String.format("Expected Time (F7) : %s", prettyTime(runsF7 * (long) (timeF7 * 1.2))));
			hasTime = true;
		}
		if (hasTime) tooltip.add("");
		appendExplanationLines(tooltip, shift);
		return tooltip;
	}

	private List<String> buildMasterRunsTooltip(JsonObject profileInfo) {
		float M3 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.tier_completions.3"), 0);
		float M4 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.tier_completions.4"), 0);
		float M5 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.tier_completions.5"), 0);
		float M6 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.tier_completions.6"), 0);
		float M7 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.tier_completions.7"), 0);
		if (M3 > 50) M3 = 50;
		if (M4 > 50) M4 = 50;
		if (M5 > 50) M5 = 50;
		if (M6 > 50) M6 = 50;
		if (M7 > 50) M7 = 50;
		float xpM3 = 35000 * (M3 / 100 + 1);
		float xpM4 = 55000 * (M4 / 100 + 1);
		float xpM5 = 70000 * (M5 / 100 + 1);
		float xpM6 = 100000 * (M6 / 100 + 1);
		float xpM7 = 300000 * (M7 / 100 + 1);
		boolean shift = isShiftDown();
		if (!shift) {
			xpM3 *= 1.1;
			xpM4 *= 1.1;
			xpM5 *= 1.1;
			xpM6 *= 1.1;
			xpM7 *= 1.1;
		}

		long runsM3 = (int) Math.ceil(floorLevelToXP / xpM3);
		long runsM4 = (int) Math.ceil(floorLevelToXP / xpM4);
		long runsM5 = (int) Math.ceil(floorLevelToXP / xpM5);
		long runsM6 = (int) Math.ceil(floorLevelToXP / xpM6);
		long runsM7 = (int) Math.ceil(floorLevelToXP / xpM7);

		float timeM3 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.fastest_time_s_plus.3"), 0);
		float timeM4 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.fastest_time_s_plus.4"), 0);
		float timeM5 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.fastest_time_s_plus.5"), 0);
		float timeM6 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.fastest_time_s_plus.6"), 0);
		float timeM7 = Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.master_catacombs.fastest_time_s_plus.7"), 0);

		List<String> tooltip = Lists.newArrayList(
			String.format("# M3 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpM3), runsM3),
			String.format("# M4 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpM4), runsM4),
			String.format("# M5 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpM5), runsM5),
			String.format("# M6 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpM6), runsM6),
			String.format("# M7 Runs (%s xp) : %d", StringUtils.shortNumberFormat(xpM7), runsM7),
			""
		);
		boolean hasTime = false;
		if (timeM3 > 1000) {
			tooltip.add(String.format("Expected Time (M3) : %s", prettyTime(runsM3 * (long) (timeM3 * 1.2))));
			hasTime = true;
		}
		if (timeM4 > 1000) {
			tooltip.add(String.format("Expected Time (M4) : %s", prettyTime(runsM4 * (long) (timeM4 * 1.2))));
			hasTime = true;
		}
		if (timeM5 > 1000) {
			tooltip.add(String.format("Expected Time (M5) : %s", prettyTime(runsM5 * (long) (timeM5 * 1.2))));
			hasTime = true;
		}
		if (timeM6 > 1000) {
			tooltip.add(String.format("Expected Time (M6) : %s", prettyTime(runsM6 * (long) (timeM6 * 1.2))));
			hasTime = true;
		}
		if (timeM7 > 1000) {
			tooltip.add(String.format("Expected Time (M7) : %s", prettyTime(runsM7 * (long) (timeM7 * 1.2))));
			hasTime = true;
		}
		if (hasTime) tooltip.add("");
		appendExplanationLines(tooltip, shift);
		return tooltip;
	}

	private void appendExplanationLines(List<String> tooltip, boolean shift) {
		if (!shift) {
			tooltip.add("[Hold " + ChatFormatting.YELLOW + "SHIFT" + ChatFormatting.GRAY + " to show without Expert Ring]");
		}
		if (isCtrlDown()) {
			if (!shift) tooltip.add("");
			tooltip.add("Number of runs is calculated as [Remaining XP]/[XP per Run].");
			tooltip.add("The [XP per Run] is the average xp gained from an S+ run");
			tooltip.add(
				"The " + ChatFormatting.DARK_PURPLE + "Catacombs Expert Ring" + ChatFormatting.GRAY +
					" is assumed to be used, unless " + ChatFormatting.YELLOW + "SHIFT" + ChatFormatting.GRAY + " is held."
			);
			tooltip.add("[Time per run] is calculated using Fastest S+ x 120%");
		} else {
			tooltip.add("[Hold " + ChatFormatting.YELLOW + "CTRL" + ChatFormatting.GRAY + " to see details]");
		}
	}

	private static boolean isShiftDown() {
		long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
		return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
			org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	private static boolean isCtrlDown() {
		long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
		return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
			org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	private static String prettyTime(long millis) {
		long seconds = millis / 1000 % 60;
		long minutes = (millis / 1000 / 60) % 60;
		long hours = (millis / 1000 / 60 / 60) % 24;
		long days = (millis / 1000 / 60 / 60 / 24);

		if (millis < 0) {
			return "Ended!";
		} else if (minutes == 0 && hours == 0 && days == 0) {
			return seconds + "s";
		} else if (hours == 0 && days == 0) {
			return minutes + "m" + seconds + "s";
		} else if (days == 0) {
			if (hours <= 6) {
				return hours + "h" + minutes + "m" + seconds + "s";
			} else {
				return hours + "h";
			}
		} else {
			return days + "d" + hours + "h";
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (dungeonLevelTextField != null) {
			if (mouseX >= guiLeft + 45 && mouseX <= guiLeft + 65 && mouseY >= guiTop + 54 && mouseY <= guiTop + 64) {
				dungeonLevelTextField.setFocused(true);
			} else {
				dungeonLevelTextField.setFocused(false);
			}
		}

		int cW = instance.getFont().width("Calculate");
		if (mouseX >= guiLeft + 23 + 110 - 17 - cW && mouseX <= guiLeft + 23 + 110 - 17 && mouseY >= guiTop + 55 && mouseY <= guiTop + 65) {
			calculateFloorLevelXP();
		}

		int y = guiTop + 142;

		if (mouseY >= y - 2 && mouseY <= y + 9) {
			for (int i = 1; i <= 7; i++) {
				int w = instance.getFont().width("" + i);
				int x = guiLeft + 23 + 110 * i / 8 - w / 2;

				if (mouseX >= x - 2 && mouseX <= x + 7) {
					floorTime = i;
					return false;
				}
			}
		}
		if (mouseX >= guiLeft - 29 && mouseX <= guiLeft) {
			if (mouseY >= guiTop && mouseY <= guiTop + 28) {
				onMasterMode = false;
			} else if (mouseY + 28 >= guiTop && mouseY <= guiTop + 28 * 2) {
				onMasterMode = true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (dungeonLevelTextField != null && dungeonLevelTextField.isFocused()) {
			return dungeonLevelTextField.keyPressed(event);
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (dungeonLevelTextField != null && dungeonLevelTextField.isFocused()) {
			return dungeonLevelTextField.charTyped(event);
		}
		return false;
	}

	private void drawSideButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (onMasterMode) {
			drawSideButton(graphics, 1, dungeonsModeIcons().get("master_catacombs"), true);
			drawSideButton(graphics, 0, dungeonsModeIcons().get("catacombs"), false);
		} else {
			drawSideButton(graphics, 0, dungeonsModeIcons().get("catacombs"), true);
			drawSideButton(graphics, 1, dungeonsModeIcons().get("master_catacombs"), false);
		}
	}

	private void drawSideButton(GuiGraphicsExtractor graphics, int yIndex, ItemStack itemStack, boolean pressed) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		int x = guiLeft - 28;
		int y = guiTop + yIndex * 28;

		float uMin = 193 / 256f;
		float uMax = 223 / 256f;
		float vMin = 200 / 256f;
		float vMax = 228 / 256f;
		if (pressed) {
			uMin = 224 / 256f;
			uMax = 1f;

			if (yIndex != 0) {
				vMin = 228 / 256f;
				vMax = 1f;
			}

			// A small button backdrop, not a full panel - GuiProfileViewer already blurs the whole screen once per
			// frame (see its extractRenderState), so this flat darkening fill is sufficient here.
			graphics.fill(x + 2, y + 2, x + 32 - 2, y + 30 - 4, 0x80000000);
		} else {
			graphics.fill(x + 2, y + 2, x + 28 - 2, y + 30 - 4, 0x80000000);
		}

		RenderUtils.drawTexturedRect(graphics, pv_elements, x, y, pressed ? 32 : 28, 28, uMin, uMax, vMin, vMax);
		RenderUtils.drawItemStack(graphics, itemStack, x + 8, y + 7);
	}

	private void calculateFloorLevelXP() {
		JsonObject leveling = Constants.LEVELING;
		if (leveling == null) return;
		ProfileViewer.Level levelObjCata = levelObjCatas.get(GuiProfileViewer.getProfileId());
		if (levelObjCata == null) return;

		try {
			floorLevelTo = Integer.parseInt(dungeonLevelTextField.getValue());

			JsonArray levelingArray = Utils.getElementOrDefault(leveling, "catacombs", new JsonArray()).getAsJsonArray();

			float remaining = -((levelObjCata.level % 1) * levelObjCata.maxXpForLevel);

			for (int level = 0; level < Math.min(floorLevelTo, levelingArray.size()); level++) {
				if (level < Math.floor(levelObjCata.level)) {
					continue;
				}
				remaining += levelingArray.get(level).getAsFloat();
			}

			if (remaining < 0) {
				remaining = 0;
			}
			floorLevelToXP = (long) remaining;
		} catch (Exception ignored) {
		}
	}
}
