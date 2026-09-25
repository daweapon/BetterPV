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
package io.github.moulberry.notenoughupdates.profileviewer.trophy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Trophy Fish tab's second page: last catch, trophy rank, Dolphin pet progress, catch counts, festival
 * sharks and sea creature kills.
 */
final class FishingStats {

	private static final String[] TROPHY_RANKS = {
		ChatFormatting.WHITE + "Bronze Hunter", ChatFormatting.GRAY + "Silver Hunter",
		ChatFormatting.GOLD + "Gold Hunter", ChatFormatting.AQUA + "Diamond Hunter"
	};

	/** Sea creature kills needed for each Dolphin pet rarity. */
	private static final int[] DOLPHIN_KILLS = {250, 1000, 2500, 5000, 10000};
	private static final String[] DOLPHIN_RARITIES = {
		ChatFormatting.WHITE + "Common", ChatFormatting.GREEN + "Uncommon", ChatFormatting.BLUE + "Rare",
		ChatFormatting.DARK_PURPLE + "Epic", ChatFormatting.GOLD + "Legendary"
	};

	private static final int FESTIVAL_SHARKS_MAX = 5000;

	/** Sea creatures (and fishing bosses) by their {@code kills_<id>} stat; ones a profile has no kills of stay hidden. */
	private static final String[] SEA_CREATURES = {
		"pond_squid", "sea_walker", "night_squid", "sea_guardian", "sea_archer", "sea_witch", "sea_leech",
		"guardian_defender", "deep_sea_protector", "water_hydra", "sea_emperor", "catfish", "carrot_king",
		"rider_of_the_deep", "monster_of_the_deep", "thunder", "lord_jawbus", "nurse_shark", "blue_shark",
		"tiger_shark", "great_white_shark", "phantom_fisher", "grim_reaper", "yeti", "reindrake", "grinch",
		"scarecrow", "nightmare", "werewolf", "bayou_sludge", "alligator", "titanoboa", "wiki_tiki",
		"blue_ringed_octopus", "fiery_scuttler", "fireproof_witch", "lava_blaze", "lava_pigman", "flaming_worm",
		"lava_leech", "taurus", "magma_slug", "moogma", "plhlegblast", "abyssal_miner", "oasis_rabbit",
		"oasis_sheep", "zombie_miner", "trapper", "squid", "the_loch_ness_monster", "frog_man"
	};

	private FishingStats() {
	}

	static void draw(GuiProfileViewer instance, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		Font font = instance.getFont();

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		JsonObject member = profile == null ? null : profile.getProfileInformation(GuiProfileViewer.getProfileId());
		if (member == null) return;

		JsonObject trophy = member.get("trophy_fish") instanceof JsonObject object ? object : new JsonObject();
		long seaCreatures = number(member, "stats.pet_milestone_sea_creatures_killed", "pets_data.pet_milestones.sea_creatures_killed");

		panel(graphics, guiLeft + 8, guiTop + 8, 205, 96);
		panel(graphics, guiLeft + 218, guiTop + 8, 205, 96);
		panel(graphics, guiLeft + 8, guiTop + 110, 415, 84);

		// Information
		RenderUtils.drawStringCentered(graphics, ChatFormatting.AQUA + "Information", font, guiLeft + 110, guiTop + 17, true, 0);
		int x = guiLeft + 16;
		int y = guiTop + 30;
		row(graphics, "Last Catch", lastCatch(Utils.getElementAsString(trophy.get("last_caught"), "")), x, y, 189);
		row(graphics, "Trophy Rank", trophyRank(trophy), x, y + 14, 189);

		int dolphin = -1;
		for (int i = 0; i < DOLPHIN_KILLS.length; i++) {
			if (seaCreatures >= DOLPHIN_KILLS[i]) dolphin = i;
		}
		row(graphics, "Dolphin Pet", dolphin < 0 ? ChatFormatting.RED + "None" : DOLPHIN_RARITIES[dolphin], x, y + 28, 189);
		if (Utils.isWithinRect(mouseX, mouseY, x, y + 28, 189, 12)) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add(ChatFormatting.WHITE + "Sea Creatures Killed: " + ChatFormatting.AQUA + GuiProfileViewer.numberFormat.format(seaCreatures));
			tooltip.add("");
			for (int i = 0; i < DOLPHIN_KILLS.length; i++) {
				boolean got = seaCreatures >= DOLPHIN_KILLS[i];
				tooltip.add((got ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY) + "" + (got ? "" : ChatFormatting.STRIKETHROUGH) +
					ChatFormatting.stripFormatting(DOLPHIN_RARITIES[i]) + " Dolphin " + ChatFormatting.GRAY +
					"(" + GuiProfileViewer.numberFormat.format(DOLPHIN_KILLS[i]) + ")");
			}
			instance.tooltipToDisplay = tooltip;
		}
		row(graphics, "Sea Creatures Killed", ChatFormatting.WHITE + GuiProfileViewer.numberFormat.format(seaCreatures), x, y + 42, 189);
		row(graphics, "Trophy Fish Caught", ChatFormatting.WHITE + GuiProfileViewer.numberFormat.format(Utils.getElementAsLong(trophy.get("total_caught"), 0)), x, y + 56, 189);

		// Stats
		RenderUtils.drawStringCentered(graphics, ChatFormatting.AQUA + "Stats", font, guiLeft + 320, guiTop + 17, true, 0);
		x = guiLeft + 226;
		long total = number(member, "stats.items_fished.total", "stats.items_fished");
		long normal = number(member, "stats.items_fished.normal");
		long treasure = number(member, "stats.items_fished.treasure", "stats.items_fished_treasure");
		long largeTreasure = number(member, "stats.items_fished.large_treasure", "stats.items_fished_large_treasure");
		long sharks = number(member, "leveling.fishing_festival_sharks_killed");

		row(graphics, "Total Catches", ChatFormatting.WHITE + GuiProfileViewer.numberFormat.format(total), x, y, 189);
		if (normal > 0) {
			row(graphics, "Normal Catches", ChatFormatting.WHITE + GuiProfileViewer.numberFormat.format(normal), x, y + 14, 189);
		}
		row(graphics, "Treasures Found", ChatFormatting.WHITE + GuiProfileViewer.numberFormat.format(treasure + largeTreasure), x, y + 28, 189);
		row(graphics, "Large Treasures", ChatFormatting.WHITE + GuiProfileViewer.numberFormat.format(largeTreasure), x, y + 42, 189);
		ChatFormatting sharkColour = sharks >= FESTIVAL_SHARKS_MAX ? ChatFormatting.GREEN : sharks >= 2500 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		row(graphics, "Festival Sharks", sharkColour + GuiProfileViewer.numberFormat.format(Math.min(sharks, FESTIVAL_SHARKS_MAX)) +
			ChatFormatting.GRAY + "/" + GuiProfileViewer.numberFormat.format(FESTIVAL_SHARKS_MAX), x, y + 56, 189);
		if (Utils.isWithinRect(mouseX, mouseY, x, y + 56, 189, 12)) {
			instance.tooltipToDisplay = List.of(
				ChatFormatting.AQUA + "+1 SkyBlock XP" + ChatFormatting.WHITE + " per 50 sharks killed!",
				"",
				ChatFormatting.WHITE + "Total sharks killed: " + GuiProfileViewer.numberFormat.format(sharks)
			);
		}

		// Sea creature kills
		RenderUtils.drawStringCentered(graphics, ChatFormatting.AQUA + "Sea Creature Kills", font, guiLeft + 215, guiTop + 119, true, 0);
		JsonObject stats = member.get("stats") instanceof JsonObject object ? object : new JsonObject();
		List<Map.Entry<String, Long>> kills = new ArrayList<>();
		for (String id : SEA_CREATURES) {
			long count = Utils.getElementAsLong(stats.get("kills_" + id), 0);
			if (count > 0) kills.add(Map.entry(id, count));
		}
		kills.sort(Map.Entry.<String, Long>comparingByValue().reversed());
		if (kills.isEmpty()) {
			RenderUtils.drawStringCentered(graphics, ChatFormatting.RED + "No sea creature kills recorded", font, guiLeft + 215, guiTop + 152, true, 0);
		}
		for (int i = 0; i < Math.min(kills.size(), 18); i++) {
			Map.Entry<String, Long> kill = kills.get(i);
			int column = i / 6;
			row(graphics, WordUtils.capitalizeFully(kill.getKey().replace('_', ' ')),
				ChatFormatting.WHITE + GuiProfileViewer.numberFormat.format(kill.getValue()),
				guiLeft + 16 + column * 138, guiTop + 128 + (i % 6) * 10, 128);
		}
	}

	private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, 0x66000000);
		graphics.fill(x, y, x + width, y + 1, 0x88FFFFFF);
		graphics.fill(x, y + height - 1, x + width, y + height, 0x88FFFFFF);
		graphics.fill(x, y, x + 1, y + height, 0x88FFFFFF);
		graphics.fill(x + width - 1, y, x + width, y + height, 0x88FFFFFF);
	}

	private static void row(GuiGraphicsExtractor graphics, String name, String value, int x, int y, int width) {
		RenderUtils.renderAlignedString(graphics, ChatFormatting.GRAY + name + ": ", value, x, y, width);
	}

	/** "sulphur_skitter/bronze" as "Sulphur Skitter (Bronze)". */
	private static String lastCatch(String raw) {
		if (raw.isEmpty()) return ChatFormatting.RED + "None";
		String[] parts = raw.split("/");
		String name = WordUtils.capitalizeFully(parts[0].replace('_', ' '));
		return ChatFormatting.WHITE + name + (parts.length > 1 ? " (" + WordUtils.capitalizeFully(parts[1]) + ")" : "");
	}

	private static String trophyRank(JsonObject trophy) {
		int best = 0;
		if (trophy.get("rewards") instanceof com.google.gson.JsonArray rewards) {
			for (JsonElement reward : rewards) {
				int tier = Utils.getElementAsInt(reward, 0);
				if (tier <= TROPHY_RANKS.length) best = Math.max(best, tier);
			}
		}
		return best == 0 ? ChatFormatting.RED + "None" : TROPHY_RANKS[best - 1];
	}

	/** The first of these paths that holds a number (0 when none does). */
	private static long number(JsonObject member, String... paths) {
		for (String path : paths) {
			JsonElement value = Utils.getElement(member, path);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsLong();
		}
		return 0;
	}
}
