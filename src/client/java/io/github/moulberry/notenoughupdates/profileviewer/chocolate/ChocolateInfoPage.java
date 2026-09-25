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

package io.github.moulberry.notenoughupdates.profileviewer.chocolate;

import io.github.moulberry.notenoughupdates.profileviewer.VanillaItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Main chocolate factory sub-page: employees, upgrades, general information and rabbits per rarity. Player
 * data is {@code events.easter}; costs are in {@code chocolate_factory.json}.
 */
public class ChocolateInfoPage implements GuiProfileViewerPage {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.US);
	private static final int GAP = 12;
	private static final int PER_ROW = 4;

	private record Line(String text, List<String> tooltip) {
	}

	private final GuiProfileViewer instance;

	public ChocolateInfoPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	/** The profile's {@code events.easter}, or null if it has none. */
	static JsonObject easter() {
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		return Utils.getElement(profileInfo, "events.easter") instanceof JsonObject easter ? easter : null;
	}

	static JsonObject repo() {
		return PvData.bundled("chocolate_factory");
	}

	static ItemStack texture(String key) {
		String texture = Utils.getElementAsString(Utils.getElement(repo(), "textures." + key), null);
		return texture == null ? new ItemStack(Items.BARRIER) : PvData.skull(texture);
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font font = instance.getFont();
		if (GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId()) == null) return;
		JsonObject cf = easter();
		if (cf == null) {
			PvUi.centred(graphics, font, instance, "§cThis profile hasn't found the Chocolate Factory yet!");
			return;
		}

		List<Line> information = information(cf);
		int infoWidth = font.width("Information") + 8;
		for (Line line : information) infoWidth = Math.max(infoWidth, font.width(line.text()) + 8);
		int columnWidth = PER_ROW * 18;
		int leftHeight = PvUi.TITLE + 3 + 36 + 8 + PvUi.TITLE + 3 + 18;
		int height = Math.max(leftHeight, PvUi.linesHeight(information.size()));
		int x = GuiProfileViewer.getGuiLeft() + (instance.sizeX - columnWidth * 2 - infoWidth - GAP * 2) / 2;
		int y = GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;

		drawEmployees(graphics, font, cf, x, y, mouseX, mouseY);
		drawUpgrades(graphics, font, cf, x, y + PvUi.TITLE + 3 + 36 + 8, mouseX, mouseY);
		x += columnWidth + GAP;

		PvUi.title(graphics, font, "Information", x, y, infoWidth);
		int rowY = y + PvUi.TITLE + 3;
		for (Line line : information) {
			RenderUtils.text(graphics, font, line.text(), x + 4, rowY, 0xFFFFFF, true);
			if (line.tooltip() != null && Utils.isWithinRect(mouseX, mouseY, x + 4, rowY - 1, font.width(line.text()), PvUi.ROW)) {
				instance.tooltipToDisplay = line.tooltip();
			}
			rowY += PvUi.ROW;
		}
		x += infoWidth + GAP;

		drawRarities(graphics, font, cf, x, y, mouseX, mouseY);
	}

	/** Slot position for the {@code index}th of {@code count} items in centred rows of four. */
	private static int rowX(int x, int index, int count) {
		int row = index / PER_ROW;
		int inRow = Math.min(PER_ROW, count - row * PER_ROW);
		return x + (PER_ROW - inRow) * 9 + index % PER_ROW * 18;
	}

	private void drawEmployees(GuiGraphicsExtractor graphics, Font font, JsonObject cf, int x, int y, int mouseX, int mouseY) {
		PvUi.title(graphics, font, "Employees", x, y, PER_ROW * 18);
		if (!(repo().get("employees") instanceof JsonArray employees)) return;
		int top = y + PvUi.TITLE + 3;
		for (int i = 0; i < employees.size(); i++) {
			JsonObject employee = employees.get(i).getAsJsonObject();
			String id = employee.get("id").getAsString();
			int level = (int) PvData.getLong(cf, "employees." + id);
			String colour = level < 10 ? "§f" : level < 75 ? "§a" : level < 125 ? "§9" : level < 175 ? "§5" :
				level < 200 ? "§6" : level < 220 ? "§d" : level < 236 ? "§b" : "§7";
			int slotX = rowX(x, i, employees.size());
			int slotY = top + i / PER_ROW * 18;
			boolean hovered = PvUi.slot(graphics, level > 0 ? texture(id) : new ItemStack(VanillaItems.GRAY_DYE), slotX, slotY, mouseX, mouseY);
			PvUi.count(graphics, font, colour + level, slotX, slotY);
			if (hovered) {
				double reward = PvData.evaluate(Utils.getElementAsString(employee.get("reward"), "level"), level);
				instance.tooltipToDisplay = List.of(
					colour + "§l" + Utils.getElementAsString(employee.get("name"), id) + " §r§7(" + colour + level + "§7)",
					"",
					"§7Produces §6+" + PvData.format(reward) + " Chocolate §7per second."
				);
			}
		}
	}

	private void drawUpgrades(GuiGraphicsExtractor graphics, Font font, JsonObject cf, int x, int y, int mouseX, int mouseY) {
		PvUi.title(graphics, font, "Upgrades", x, y, PER_ROW * 18);
		int top = y + PvUi.TITLE + 3;
		long timeTower = PvData.getLong(cf, "time_tower.level");
		Object[][] upgrades = {
			{new ItemStack(Items.COOKIE), "Click Upgrade " + (PvData.getLong(cf, "click_upgrades") + 1),
				List.of("§7Increases the amount of §6Chocolate", "§7you get per click.")},
			{new ItemStack(Items.CLOCK), "Time Tower " + timeTower,
				List.of("§7Increases your §6Chocolate Production", "§7for §a1h §7per charge.", "",
					"§7Charges: §d" + PvData.getLong(cf, "time_tower.charges") + "§7/§d3")},
			{new ItemStack(Items.RABBIT_FOOT), "Rabbit Shrine " + PvData.getLong(cf, "rabbit_rarity_upgrades"),
				List.of("§7Increases the chance of getting", "§dhigher rarity rabbits §7during §dHoppity's Hunt§7.")},
			{texture("coach_jackrabbit"), "Coach Jackrabbit " + PvData.getLong(cf, "chocolate_multiplier_upgrades"),
				List.of("§7Increases the amount of", "§6Chocolate §7you get per second.")},
		};
		for (int i = 0; i < upgrades.length; i++) {
			if (PvUi.slot(graphics, (ItemStack) upgrades[i][0], x + i * 18, top, mouseX, mouseY)) {
				List<String> tooltip = new ArrayList<>();
				tooltip.add("§d" + upgrades[i][1]);
				@SuppressWarnings("unchecked") List<String> lore = (List<String>) upgrades[i][2];
				tooltip.addAll(lore);
				instance.tooltipToDisplay = tooltip;
			}
		}
	}

	private void drawRarities(GuiGraphicsExtractor graphics, Font font, JsonObject cf, int x, int y, int mouseX, int mouseY) {
		PvUi.title(graphics, font, "Rarities", x, y, PER_ROW * 18);
		// Per rarity: rabbits in the collection, found uniques and total finds. Rarest first, as SkyBlockPv lists them.
		Map<Integer, long[]> counts = new TreeMap<>(Comparator.reverseOrder());
		for (Rabbits.Rabbit rabbit : Rabbits.all()) {
			long[] count = counts.computeIfAbsent(rabbit.rarity(), key -> new long[3]);
			long found = Rabbits.found(cf, rabbit.id());
			count[0]++;
			if (found > 0) count[1]++;
			count[2] += found;
		}
		int top = y + PvUi.TITLE + 3;
		int i = 0;
		for (Map.Entry<Integer, long[]> entry : counts.entrySet()) {
			String rarity = entry.getKey() < 0 ? "UNKNOWN" : PvData.RARITIES.get(entry.getKey());
			long[] count = entry.getValue();
			String code = PvData.rarityCode(entry.getKey());
			int slotX = rowX(x, i, counts.size());
			int slotY = top + i / PER_ROW * 18;
			boolean hovered = PvUi.slot(graphics, texture(rarity), slotX, slotY, mouseX, mouseY);
			PvUi.count(graphics, font, code + count[1], slotX, slotY);
			if (hovered) {
				instance.tooltipToDisplay = List.of(
					code + "§l" + rarity + " §r§7(" + code + count[1] + "§7)",
					"§7Uniques: " + code + count[1] + "§7/" + count[0],
					"§7Total: §6" + PvData.format(count[2])
				);
			}
			i++;
		}
	}

	private static List<Line> information(JsonObject cf) {
		List<Line> lines = new ArrayList<>();
		long sincePrestige = PvData.getLong(cf, "chocolate_since_prestige");
		lines.add(new Line("§7Chocolate: §6" + PvData.shorten(PvData.getLong(cf, "chocolate")), null));
		lines.add(new Line("§7Total Chocolate: §6" + PvData.shorten(PvData.getLong(cf, "total_chocolate")), null));
		lines.add(new Line("§7Chocolate since Prestige: §6" + PvData.shorten(sincePrestige), null));

		int prestige = (int) PvData.getLong(cf, "chocolate_level");
		JsonObject prestiges = Utils.getElement(repo(), "misc.chocolate_prestige") instanceof JsonObject object ? object : new JsonObject();
		int maxPrestige = 1;
		for (String level : prestiges.keySet()) maxPrestige = Math.max(maxPrestige, Integer.parseInt(level));
		if (prestige < maxPrestige) {
			long needed = PvData.asLong(prestiges.get(String.valueOf(prestige + 1)), 0) - sincePrestige;
			lines.add(new Line("§7Chocolate for next Prestige: " + (needed <= 0 ? "§aReady!" : "§6" + PvData.shorten(needed)), null));
		}
		lines.add(new Line("§7Prestige Level: §d" + prestige + "§7/" + maxPrestige, null));
		lines.add(new Line("§7Barn Capacity: §a" + (PvData.getLong(cf, "rabbit_barn_capacity_level") * 2 + 18), null));
		long lastViewed = PvData.getLong(cf, "last_viewed_chocolate_factory");
		lines.add(new Line("§7Last Updated: §8" + (lastViewed <= 0 ? "Never"
			: DATE.format(Instant.ofEpochMilli(lastViewed).atZone(ZoneId.systemDefault()))), null));

		long slots = PvData.getLong(cf, "rabbit_hitmen.rabbit_hitmen_slots");
		List<Long> hitmanCosts = PvData.cumulative(repo().get("hitman_cost"));
		long paid = slots >= 1 ? hitmanCosts.get((int) Math.min(slots - 1, hitmanCosts.size() - 1)) : 0;
		long totalCost = hitmanCosts.get(hitmanCosts.size() - 1);
		lines.add(new Line("§7Hitman Slots: §c" + slots + " Unlocked §7- §c" +
			PvData.getLong(cf, "rabbit_hitmen.missed_uncollected_eggs") + " Ready",
			List.of("§7Paid: §6" + PvData.format(paid) + "§7/§6" + PvData.shorten(totalCost) + " §7(§6" +
				PvData.percent(paid, totalCost) + "%§7)")));

		List<String> chocobits = new ArrayList<>();
		chocobits.add("§cCurrent §7/ §cTotal Found");
		chocobits.add("");
		List<JsonObject> owned = new ArrayList<>();
		if (Utils.getElement(cf, "chocobits.owned") instanceof JsonArray array) {
			for (JsonElement element : array) {
				if (element instanceof JsonObject bit && PvData.asLong(bit.get("id"), -1) > 0) owned.add(bit);
			}
		}
		owned.sort(Comparator.comparingLong(bit -> PvData.asLong(bit.get("expiry_year"), 0)));
		if (owned.isEmpty()) chocobits.add("§cNONE");
		for (JsonObject bit : owned) {
			chocobits.add("§7Chocobit §e" + PvData.asLong(bit.get("id"), 0) + "§7, found in year §a" +
				PvData.asLong(bit.get("owned_year"), 0) + "§7, expires in year §6" + PvData.asLong(bit.get("expiry_year"), 0));
		}
		lines.add(new Line("§7Chocobits: §6" + owned.size() + " §7/ §6" + PvData.format(PvData.getLong(cf, "chocobits.total_found")), chocobits));
		return lines;
	}
}
