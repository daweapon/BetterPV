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
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of NEU's {@code CrimsonIslePage}: Kuudra completions, Dojo results, the last Matriarch attempt and faction
 * reputation. It is the third page of the Basic tab, opened from the button under Level ({@link BasicPage}).
 */
public class CrimsonIslePage implements GuiProfileViewerPage {

	private static final Identifier CRIMSON_ISLE = Identifier.parse("betterpv:pv_crimson_isle_page.png");
	private static final int WIDTH = 431;
	private static final int HEIGHT = 202;

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("EEE d MMM yyyy");
	private static final DateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
	private static final String[] DOJO_GRADES = {"F", "D", "C", "B", "A", "S"};

	/** Kuudra's key icons, one per tier. */
	static final ItemStack[] KUUDRA_KEYS = {
		Utils.createSkull(ChatFormatting.BLUE + "Kuudra Key",
			"2a9e4728-b0c5-3f7d-9c45-7ff3fc1eb206",
			"ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1MjgzNTU0NCwKICAicHJvZmlsZUlkIiA6ICJkYmQ4MDQ2M2EwMzY0Y2FjYjI3OGNhODBhMDBkZGIxMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ4bG9nMjEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmZkM2U3MTgzOGMwZTc2Zjg5MDIxMzEyMGI0Y2U3NDQ5NTc3NzM2NjA0MzM4YThkMjhiNGM4NmRiMjU0N2U3MSIKICAgIH0KICB9Cn0"),
		Utils.createSkull(ChatFormatting.DARK_PURPLE + "Hot Kuudra Key",
			"80a91601-ac87-302b-ac6a-4c635b73c2a2",
			"ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1Mjg2NTc1MiwKICAicHJvZmlsZUlkIiA6ICJkMGI4MjE1OThmMTE0NzI1ODBmNmNiZTliOGUxYmU3MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJqYmFydHl5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2MwMjU5ZTg5NjRjM2RlYjk1YjEyMzNiYjJkYzgyYzk4NjE3N2U2M2FlMzZjMTEyNjVjYjM4NTE4MGJiOTFjYzAiCiAgICB9CiAgfQp9"),
		Utils.createSkull(ChatFormatting.DARK_PURPLE + "Burning Kuudra Key",
			"74ceda89-849d-35b4-b0fb-00083f599c02",
			"ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1Mjg4MjI5NSwKICAicHJvZmlsZUlkIiA6ICI1YjY2YzNkZWZhYTI0NWMzYTcwNjM3OTA3NTQ0Yjg3MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWFuX1JhaWNvMDgxNiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zMzBmNmY2ZTYzYjI0NWY4MzllM2NjZGNlNWE1ZjIyMDU2MjAxZDAyNzQ0MTFkZmU1ZDk0YmJlNDQ5YzRlY2UiCiAgICB9CiAgfQp9"),
		Utils.createSkull(ChatFormatting.DARK_PURPLE + "Fiery Kuudra Key",
			"75c56136-e7cc-3836-a4d5-ed516b7651d6",
			"ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1Mjg5ODM0MSwKICAicHJvZmlsZUlkIiA6ICI5ZDQyNWFiOGFmZjg0MGU1OWM3NzUzZjc5Mjg5YjMyZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJUb21wa2luNDIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ4NTQzOTNiYmY5NDQ0NTQyNTAyNTgyZDRiNWEyM2NjNzM4OTY1MDZlMmZjNzM5ZDU0NWJjMzViYzdiMWMwNiIKICAgIH0KICB9Cn0"),
		Utils.createSkull(ChatFormatting.GOLD + "Infernal Kuudra Key",
			"3877a428-ace8-3faf-9992-4644fbd87f4c",
			"ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1MjkxMzA5NiwKICAicHJvZmlsZUlkIiA6ICJjNTlkMDFlMDI4MWI0MGNhOTczNjc5ODc4NmRmN2FmNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJvWm9va3hQYXJjY2VyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzgyZWUyNTQxNGFhN2VmYjRhMmI0OTAxYzZlMzNlNWVhYTcwNWE2YWIyMTJlYmViZmQ2YTRkZTk4NDEyNWM3YTAiCiAgICB9CiAgfQp9")
	};

	private static final String[] KUUDRA_TIER_NAMES = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
	/** The tier names as the API spells them. */
	private static final String[] KUUDRA_TIERS = {"none", "hot", "burning", "fiery", "infernal"};

	private static final Map<String, String> DOJO_TESTS = new LinkedHashMap<>();
	private static final LinkedHashMap<Integer, String> DOJO_RANKS = new LinkedHashMap<>();
	private static final Map<String, String> FACTIONS = Map.of(
		"mages", ChatFormatting.DARK_PURPLE + "Mages",
		"barbarians", ChatFormatting.RED + "Barbarians",
		"N/A", ChatFormatting.GRAY + "N/A"
	);
	private static final LinkedHashMap<Integer, String> FACTION_TITLES = new LinkedHashMap<>();

	static {
		DOJO_TESTS.put("mob_kb", ChatFormatting.GOLD + "Test of Force");
		DOJO_TESTS.put("wall_jump", ChatFormatting.LIGHT_PURPLE + "Test of Stamina");
		DOJO_TESTS.put("archer", ChatFormatting.YELLOW + "Test of Mastery");
		DOJO_TESTS.put("sword_swap", ChatFormatting.RED + "Test of Discipline");
		DOJO_TESTS.put("snake", ChatFormatting.GREEN + "Test of Swiftness");
		DOJO_TESTS.put("lock_head", ChatFormatting.BLUE + "Test of Control");
		DOJO_TESTS.put("fireball", ChatFormatting.GOLD + "Test of Tenacity");

		DOJO_RANKS.put(0, ChatFormatting.GRAY + "None");
		DOJO_RANKS.put(1000, ChatFormatting.YELLOW + "Yellow");
		DOJO_RANKS.put(2000, ChatFormatting.GREEN + "Green");
		DOJO_RANKS.put(4000, ChatFormatting.BLUE + "Blue");
		DOJO_RANKS.put(6000, ChatFormatting.GOLD + "Brown");
		DOJO_RANKS.put(7000, ChatFormatting.DARK_GRAY + "Black");

		FACTION_TITLES.put(-3000, "Hostile");
		FACTION_TITLES.put(-1000, "Unfriendly");
		FACTION_TITLES.put(0, "Neutral");
		FACTION_TITLES.put(1000, "Friendly");
		FACTION_TITLES.put(3000, "Trusted");
		FACTION_TITLES.put(6000, "Honored");
		FACTION_TITLES.put(12000, "Hero");
	}

	private final GuiProfileViewer instance;

	public CrimsonIslePage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		return BasicPage.clickedSideButtons(mouseX, mouseY, mouseButton);
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		Font font = instance.getFont();
		BasicPage.drawSideButtons(graphics, instance, mouseX, mouseY);

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		JsonObject member = profile == null ? null : profile.getProfileInformation(GuiProfileViewer.getProfileId());
		if (member == null) return;

		if (!(member.get("nether_island_player_data") instanceof JsonObject data)) {
			RenderUtils.drawStringCentered(graphics, ChatFormatting.RED + "No data found for the Crimson Isle", font,
				guiLeft + WIDTH / 2f, guiTop + 101, true, 0);
			return;
		}

		RenderUtils.drawTexturedRect(graphics, CRIMSON_ISLE, guiLeft, guiTop, WIDTH, HEIGHT);
		drawDojo(graphics, font, data, guiLeft, guiTop);
		drawKuudra(graphics, font, data, guiLeft, guiTop, mouseX, mouseY);
		drawMatriarch(graphics, font, data, guiLeft, guiTop);
		drawFactions(graphics, font, data, guiLeft, guiTop);
	}

	private void drawKuudra(GuiGraphicsExtractor graphics, Font font, JsonObject data, int guiLeft, int guiTop, int mouseX, int mouseY) {
		RenderUtils.drawStringCentered(graphics, ChatFormatting.RED + "Kuudra Stats", font, guiLeft + WIDTH * 0.18f, guiTop + 14, true, 0);

		if (!(data.get("kuudra_completed_tiers") instanceof JsonObject tiers)) {
			RenderUtils.renderAlignedString(graphics, ChatFormatting.RED + "No kuudra stats found!", " ", guiLeft + 15, guiTop + 101, 130);
			return;
		}

		int totalRuns = 0;
		for (Map.Entry<String, JsonElement> run : tiers.entrySet()) {
			if (!run.getKey().startsWith("highest_wave")) totalRuns += Utils.getElementAsInt(run.getValue(), 0);
		}

		for (int i = 0; i < KUUDRA_TIERS.length; i++) {
			int completions = Utils.getElementAsInt(tiers.get(KUUDRA_TIERS[i]), 0);
			// Only runs since Infernal was released record a highest wave.
			int highestWave = Utils.getElementAsInt(tiers.get("highest_wave_" + KUUDRA_TIERS[i]), 0);
			int y = guiTop + 30 + i * 30;

			RenderUtils.drawItemStack(graphics, KUUDRA_KEYS[i], guiLeft + 8, y);
			RenderUtils.renderAlignedString(graphics, ChatFormatting.RED + KUUDRA_TIER_NAMES[i] + ": ",
				ChatFormatting.WHITE + String.valueOf(completions), guiLeft + 23, y, 110);
			RenderUtils.renderAlignedString(graphics, ChatFormatting.RED + "Highest Wave: ",
				ChatFormatting.WHITE + (highestWave != 0 ? String.valueOf(highestWave) : "N/A"), guiLeft + 23, y + 12, 110);

			if (highestWave == 0 && Utils.isWithinRect(mouseX, mouseY, guiLeft + 23, y + 12, 110, 9)) {
				instance.tooltipToDisplay = List.of(
					ChatFormatting.RED + "N/A will only show for highest wave",
					ChatFormatting.RED + "if you have not completed a run for",
					ChatFormatting.RED + "this tier since Infernal tier was released."
				);
			}
		}

		RenderUtils.renderAlignedString(graphics, ChatFormatting.RED + "Total runs: ",
			ChatFormatting.WHITE + String.valueOf(totalRuns), guiLeft + 23, guiTop + 30 + KUUDRA_TIERS.length * 30, 110);
	}

	private void drawDojo(GuiGraphicsExtractor graphics, Font font, JsonObject data, int guiLeft, int guiTop) {
		RenderUtils.drawStringCentered(graphics, ChatFormatting.YELLOW + "Dojo Stats", font, guiLeft + WIDTH * 0.49f, guiTop + 14, true, 0);

		float x = guiLeft + WIDTH * 0.49f - 65;
		int totalPoints = 0;
		int row = 0;
		for (Map.Entry<String, String> test : DOJO_TESTS.entrySet()) {
			int points = Utils.getElementAsInt(Utils.getElement(data, "dojo.dojo_points_" + test.getKey()), 0);
			totalPoints += points;
			RenderUtils.renderAlignedString(graphics, test.getValue() + ": ",
				ChatFormatting.WHITE + "" + points + " (" + DOJO_GRADES[Math.min(points / 200, 5)] + ")",
				x, guiTop + 30 + row * 12, 130);
			row++;
		}

		int below = guiTop + DOJO_TESTS.size() * 12;
		int toNext = pointsToNextRank(totalPoints);
		RenderUtils.renderAlignedString(graphics, ChatFormatting.GRAY + "Points: ", ChatFormatting.GOLD + String.valueOf(totalPoints), x, below + 40, 130);
		RenderUtils.renderAlignedString(graphics, ChatFormatting.GRAY + "Rank: ", rank(totalPoints), x, below + 52, 130);
		RenderUtils.renderAlignedString(graphics, ChatFormatting.GRAY + "Points to next: ",
			toNext == 0 ? ChatFormatting.GOLD + "MAXED!" : String.valueOf(toNext), x, below + 64, 130);
	}

	private static String rank(int points) {
		String rank = DOJO_RANKS.get(0);
		for (Map.Entry<Integer, String> each : DOJO_RANKS.entrySet()) {
			if (points >= each.getKey()) rank = each.getValue();
		}
		return rank;
	}

	private static int pointsToNextRank(int points) {
		for (int threshold : DOJO_RANKS.keySet()) {
			if (threshold > points) return threshold - points;
		}
		return 0;
	}

	private void drawMatriarch(GuiGraphicsExtractor graphics, Font font, JsonObject data, int guiLeft, int guiTop) {
		RenderUtils.drawStringCentered(graphics, ChatFormatting.GOLD + "Last Matriarch Attempt", font, guiLeft + WIDTH * 0.82f, guiTop + 104, true, 0);

		if (data.get("matriarch") instanceof JsonObject attempt && !attempt.isEmpty()) {
			Date when = new Date(Utils.getElementAsLong(attempt.get("last_attempt"), 0));
			RenderUtils.renderAlignedString(graphics, ChatFormatting.GOLD + "Heavy Pearls Acquired: ",
				ChatFormatting.WHITE + String.valueOf(Utils.getElementAsInt(attempt.get("pearls_collected"), 0)), guiLeft + 290, guiTop + 119, 130);
			RenderUtils.renderAlignedString(graphics, ChatFormatting.GOLD + "Last Attempt: ",
				ChatFormatting.WHITE + DATE_FORMAT.format(when), guiLeft + 290, guiTop + 131, 130);
			RenderUtils.renderAlignedString(graphics, ChatFormatting.GOLD + " ",
				ChatFormatting.WHITE + TIME_FORMAT.format(when), guiLeft + 290, guiTop + 143, 130);
			return;
		}
		RenderUtils.renderAlignedString(graphics, ChatFormatting.RED + "No attempts found!", " ", guiLeft + 290, guiTop + 119, 130);
	}

	private void drawFactions(GuiGraphicsExtractor graphics, Font font, JsonObject data, int guiLeft, int guiTop) {
		RenderUtils.drawStringCentered(graphics, ChatFormatting.DARK_PURPLE + "Faction Reputation", font, guiLeft + WIDTH * 0.82f, guiTop + 14, true, 0);

		String selected = Utils.getElementAsString(data.get("selected_faction"), "N/A");
		RenderUtils.renderAlignedString(graphics, ChatFormatting.GREEN + "Faction: ",
			FACTIONS.getOrDefault(selected, ChatFormatting.GRAY + selected), guiLeft + 290, guiTop + 30, 130);

		// The chosen faction goes first; with none chosen the mages are first.
		boolean magesFirst = !selected.equals("barbarians");
		drawFaction(graphics, ChatFormatting.DARK_PURPLE, "Mage", Utils.getElementAsInt(data.get("mages_reputation"), 0),
			guiLeft + 290, guiTop + 42 + (magesFirst ? 0 : 24));
		drawFaction(graphics, ChatFormatting.RED, "Barbarian", Utils.getElementAsInt(data.get("barbarians_reputation"), 0),
			guiLeft + 290, guiTop + 42 + (magesFirst ? 24 : 0));
	}

	private static void drawFaction(GuiGraphicsExtractor graphics, ChatFormatting colour, String name, int reputation, int x, int y) {
		String title = FACTION_TITLES.get(-3000);
		for (Map.Entry<Integer, String> each : FACTION_TITLES.entrySet()) {
			if (reputation >= each.getKey()) title = each.getValue();
		}
		RenderUtils.renderAlignedString(graphics, colour + name + " Reputation: ", ChatFormatting.WHITE + String.valueOf(reputation), x, y, 130);
		RenderUtils.renderAlignedString(graphics, colour + "Title: ", ChatFormatting.WHITE + title, x, y + 12, 130);
	}
}
