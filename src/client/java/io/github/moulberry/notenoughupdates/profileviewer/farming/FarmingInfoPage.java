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

package io.github.moulberry.notenoughupdates.profileviewer.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import io.github.moulberry.notenoughupdates.util.PetData;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main sub-page of the farming tab, as SkyBlockPv's {@code FarmingScreen}: the player's best farming gear, Jacob's
 * contests per crop, garden chips, and general information (copper, garden level, medals, Jacob's perks).
 */
public class FarmingInfoPage implements GuiProfileViewerPage {

	private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"};
	private static final String[] MEDALS = {"bronze", "silver", "gold", "platinum", "diamond"};
	private static final String[] MEDAL_CODES = {"§c", "§7", "§6", "§3", "§b"};
	private static final String[] CHIPS = {
		"cropshot", "evergreen", "hypercharge", "mechamind", "overdrive", "quickdraw", "rarefinder", "sowledge",
		"synthesis", "vermin_vaporizer"
	};
	private static final Pattern ENCHANT = Pattern.compile("(\\w+):(\\d+)");
	private static final Identifier[] ARMOR_SLOT_SPRITES = {
		Identifier.withDefaultNamespace("container/slot/helmet"),
		Identifier.withDefaultNamespace("container/slot/chestplate"),
		Identifier.withDefaultNamespace("container/slot/leggings"),
		Identifier.withDefaultNamespace("container/slot/boots"),
	};
	private static final int GAP = 12;

	/** A line of text with an optional hover tooltip. */
	private record Line(String text, List<String> tooltip) {
		Line(String text) {
			this(text, null);
		}
	}

	private record Pet(ItemStack icon, int level, List<String> tooltip) {
	}

	private final GuiProfileViewer instance;
	private final Map<JsonObject, ItemStack> stacks = new IdentityHashMap<>();
	private JsonObject gearFor;
	private JsonObject[] armor;
	private JsonObject[] equipment;
	private JsonObject vacuum;
	private JsonObject wateringCan;
	private final List<Pet> pets = new ArrayList<>();

	public FarmingInfoPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		gearFor = null;
		stacks.clear();
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject profileInfo = profile.getProfileInformation(profileId);
		JsonObject inventoryInfo = profile.getInventoryInfo(profileId);
		if (profileInfo == null || inventoryInfo == null) return;
		if (gearFor != inventoryInfo) {
			findGear(profileInfo, inventoryInfo);
			gearFor = inventoryInfo;
		}
		Font font = instance.getFont();

		List<Line> information = information(profileInfo, Garden.garden());
		int gearWidth = 18 * 4 + 8;
		int contestsWidth = 18 * 3;
		int chipsWidth = 18 * 2;
		int infoWidth = font.width("Information") + 8;
		for (Line line : information) infoWidth = Math.max(infoWidth, font.width(line.text()) + 8);
		int height = Math.max(PvUi.TITLE + 3 + 90, PvUi.linesHeight(information.size()));

		int x = GuiProfileViewer.getGuiLeft() + (instance.sizeX - gearWidth - contestsWidth - chipsWidth - infoWidth - GAP * 3) / 2;
		int y = GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;
		drawGear(graphics, font, x, y, mouseX, mouseY);
		x += gearWidth + GAP;
		drawContests(graphics, font, profileInfo, x, y, mouseX, mouseY);
		x += contestsWidth + GAP;
		drawChips(graphics, font, profileInfo, x, y, mouseX, mouseY);
		x += chipsWidth + GAP;

		PvUi.title(graphics, font, "Information", x, y, infoWidth);
		int rowY = y + PvUi.TITLE + 3;
		for (Line line : information) {
			RenderUtils.text(graphics, font, line.text(), x + 4, rowY, 0xFFFFFF, true);
			if (line.tooltip() != null && !line.tooltip().isEmpty()
				&& Utils.isWithinRect(mouseX, mouseY, x + 4, rowY - 1, font.width(line.text()), PvUi.ROW)) {
				instance.tooltipToDisplay = line.tooltip();
			}
			rowY += PvUi.ROW;
		}
	}

	// ---- gear ----

	private void drawGear(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
		PvUi.title(graphics, font, "Gear", x, y, 18 * 4 + 8);
		int top = y + PvUi.TITLE + 3 + 9;
		for (int i = 0; i < 4; i++) {
			drawItem(graphics, armor[i], ARMOR_SLOT_SPRITES[i], x, top + i * 18, mouseX, mouseY);
			drawItem(graphics, equipment[i], null, x + 18, top + i * 18, mouseX, mouseY);
			Pet pet = i < pets.size() ? pets.get(i) : null;
			if (PvUi.slot(graphics, pet == null ? null : pet.icon(), x + 18 * 2 + 4, top + i * 18, mouseX, mouseY) && pet != null) {
				instance.tooltipToDisplay = pet.tooltip();
			}
			if (pet != null) PvUi.count(graphics, font, String.valueOf(pet.level()), x + 18 * 2 + 4, top + i * 18);
		}
		drawItem(graphics, vacuum, null, x + 18 * 3 + 8, top + 18, mouseX, mouseY);
		drawItem(graphics, wateringCan, null, x + 18 * 3 + 8, top + 36, mouseX, mouseY);
	}

	private void drawItem(GuiGraphicsExtractor graphics, JsonObject item, Identifier emptySprite, int x, int y, int mouseX, int mouseY) {
		ItemStack stack = item == null ? null : stacks.computeIfAbsent(item, json -> NotEnoughUpdates.INSTANCE.manager.jsonToStack(json, false));
		boolean hovered = PvUi.slot(graphics, stack, x, y, mouseX, mouseY);
		if (item == null && emptySprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, emptySprite, x + 1, y + 1, 16, 16);
		if (hovered && item != null) instance.tooltipToDisplay = PvUi.itemTooltip(item);
	}

	private void findGear(JsonObject profileInfo, JsonObject inventoryInfo) {
		List<JsonObject> items = new ArrayList<>();
		for (Map.Entry<String, JsonElement> inventory : inventoryInfo.entrySet()) {
			if (inventory.getKey().equals("backpack_sizes") || !(inventory.getValue() instanceof JsonArray array)) continue;
			for (JsonElement element : array) {
				if (element instanceof JsonObject item) items.add(item);
			}
		}
		// Rarity first, then SkyBlockPv's equipment score (enchantment levels, recombobulator, reforge).
		Map<JsonObject, Integer> scores = new IdentityHashMap<>();
		items.sort(Comparator.comparingInt((JsonObject item) -> scores.computeIfAbsent(item, FarmingInfoPage::score)).reversed());

		List<String> armorIds = strings("gear.armor");
		armor = new JsonObject[4];
		for (int i = 0; i < 4; i++) {
			String piece = ARMOR_PIECES[i];
			armor[i] = first(items, id -> armorIds.contains(id) && id.endsWith("_" + piece));
		}
		equipment = new JsonObject[]{
			first(items, strings("gear.necklaces")::contains), first(items, strings("gear.cloaks")::contains),
			first(items, strings("gear.belts")::contains), first(items, strings("gear.gloves")::contains)
		};
		vacuum = first(items, strings("gear.vacuum")::contains);
		wateringCan = first(items, strings("gear.watering_can")::contains);

		pets.clear();
		List<String> petTypes = strings("gear.pets");
		List<JsonObject> owned = new ArrayList<>();
		if (profileInfo.get("pets") instanceof JsonArray array) {
			for (JsonElement element : array) {
				if (element instanceof JsonObject pet && petTypes.contains(Utils.getElementAsString(pet.get("type"), ""))) owned.add(pet);
			}
		}
		owned.sort(Comparator.comparingInt((JsonObject pet) -> rarityIndex(Utils.getElementAsString(pet.get("tier"), "")))
			.thenComparingDouble(pet -> Utils.getElementAsFloat(pet.get("exp"), 0)).reversed());
		Set<String> seen = new HashSet<>();
		for (JsonObject pet : owned) {
			String type = pet.get("type").getAsString();
			if (pets.size() >= 4 || !seen.add(type)) continue;
			String tier = Utils.getElementAsString(pet.get("tier"), "COMMON");
			PetData.Rarity rarity;
			try {
				rarity = PetData.Rarity.valueOf(tier);
			} catch (IllegalArgumentException e) {
				rarity = PetData.Rarity.COMMON;
			}
			int level = (int) GuiProfileViewer.getPetLevel(type, tier, Utils.getElementAsFloat(pet.get("exp"), 0)).level;
			JsonObject repo = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(type + ";" + rarity.petId);
			ItemStack icon = repo == null ? PvData.item("BONE") : NotEnoughUpdates.INSTANCE.manager.jsonToStack(repo);
			List<String> tooltip = new ArrayList<>();
			tooltip.add("§7[Lvl " + level + "] " + rarity.chatFormatting + PvData.titleCase(type));
			String held = Utils.getElementAsString(pet.get("heldItem"), null);
			if (held != null) tooltip.add("§7Held Item: " + PvData.itemName(held));
			pets.add(new Pet(icon, level, tooltip));
		}
	}

	private static List<String> strings(String path) {
		List<String> list = new ArrayList<>();
		if (Garden.repo(path) instanceof JsonArray array) {
			for (JsonElement element : array) list.add(element.getAsString());
		}
		return list;
	}

	private static JsonObject first(List<JsonObject> items, Predicate<String> accepts) {
		for (JsonObject item : items) {
			if (accepts.test(Utils.getElementAsString(item.get("internalname"), ""))) return item;
		}
		return null;
	}

	private static int score(JsonObject item) {
		String nbt = Utils.getElementAsString(item.get("nbttag"), "");
		int score = rarity(item) * 1000;
		int start = nbt.indexOf("enchantments:{");
		if (start >= 0) {
			int end = nbt.indexOf('}', start);
			Matcher matcher = ENCHANT.matcher(nbt.substring(start + "enchantments:{".length(), end < 0 ? nbt.length() : end));
			while (matcher.find()) score += Integer.parseInt(matcher.group(2));
		}
		if (nbt.contains("rarity_upgrades:1")) score++;
		if (nbt.contains("modifier:")) score++;
		return score;
	}

	/** Index into {@link #RARITIES} from the last lore line naming one, or -1. */
	private static int rarity(JsonObject item) {
		if (!(item.get("lore") instanceof JsonArray lore)) return -1;
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = Utils.cleanColour(lore.get(i).getAsString()).trim();
			for (int r = RARITIES.length - 1; r >= 0; r--) {
				if (line.startsWith(RARITIES[r]) || line.startsWith("a " + RARITIES[r])) return r;
			}
		}
		return -1;
	}

	private static int rarityIndex(String rarity) {
		for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return i;
		return -1;
	}

	// ---- contests ----

	private void drawContests(GuiGraphicsExtractor graphics, Font font, JsonObject profileInfo, int x, int y, int mouseX, int mouseY) {
		PvUi.title(graphics, font, "Contests", x, y, 18 * 3);
		JsonObject jacob = Utils.getElement(profileInfo, "jacobs_contest") instanceof JsonObject object ? object : new JsonObject();
		int top = y + PvUi.TITLE + 3;
		for (int i = 0; i < Garden.CROPS.size(); i++) {
			Garden.Crop crop = Garden.CROPS.get(i);
			if (PvUi.slot(graphics, PvData.item(crop.item()), x + i / 5 * 18, top + i % 5 * 18, mouseX, mouseY)) {
				instance.tooltipToDisplay = contestTooltip(jacob, crop);
			}
		}
	}

	private static List<String> contestTooltip(JsonObject jacob, Garden.Crop crop) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add("§f§l" + crop.name());
		tooltip.add("");

		int contests = 0;
		int bestPosition = Integer.MAX_VALUE;
		Map<String, Integer> claimed = new LinkedHashMap<>();
		if (jacob.get("contests") instanceof JsonObject all) {
			for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
				if (!entry.getKey().endsWith(":" + crop.key()) || !(entry.getValue() instanceof JsonObject contest)) continue;
				contests++;
				if (contest.has("claimed_position")) {
					bestPosition = Math.min(bestPosition, (int) PvData.asLong(contest.get("claimed_position"), Integer.MAX_VALUE - 1) + 1);
				}
				String medal = Utils.getElementAsString(contest.get("claimed_medal"), "");
				if (!medal.isEmpty()) claimed.merge(medal.toLowerCase(Locale.ROOT), 1, Integer::sum);
			}
		}

		StringBuilder brackets = new StringBuilder("§7Brackets: ");
		for (int i = 0; i < MEDALS.length; i++) {
			boolean unlocked = Utils.getElement(jacob, "unique_brackets." + MEDALS[i]) instanceof JsonArray array
				&& array.contains(new com.google.gson.JsonPrimitive(crop.key()));
			brackets.append(MEDAL_CODES[i]).append(unlocked ? "●" : "◌");
		}
		List<String> counts = new ArrayList<>();
		for (int i = 0; i < MEDALS.length; i++) {
			Integer count = claimed.get(MEDALS[i]);
			if (count != null) counts.add(MEDAL_CODES[i] + PvData.format(count));
		}
		if (!counts.isEmpty()) brackets.append(" §7(").append(String.join("§8/", counts)).append("§7)");
		tooltip.add(brackets.toString());

		if (!(Utils.getElement(jacob, "perks.personal_bests") instanceof JsonElement unlocked && unlocked.isJsonPrimitive()
			&& unlocked.getAsBoolean())) {
			tooltip.add("§7Personal Best: §cNot Unlocked!");
		} else {
			long best = PvData.getLong(jacob, "personal_bests." + crop.key());
			long max = PvData.asLong(Garden.repo("misc.personal_bests." + crop.key()), 0);
			String line = "§7Personal Best: §e" + PvData.format(best);
			if (max >= best && max > 0) line += "§6/§e" + PvData.shorten(max) + " §7(§3" + PvData.percent(best, max) + "%§7)";
			tooltip.add(line);
		}
		tooltip.add("§7Contests participated: §e" + PvData.format(contests));
		if (bestPosition != Integer.MAX_VALUE) tooltip.add("§7Highest Position: §e#" + PvData.format(bestPosition));
		return tooltip;
	}

	// ---- chips ----

	private void drawChips(GuiGraphicsExtractor graphics, Font font, JsonObject profileInfo, int x, int y, int mouseX, int mouseY) {
		PvUi.title(graphics, font, "Chips", x, y, 18 * 2);
		List<Long> costs = PvData.cumulative(Garden.repo("chips"));
		int top = y + PvUi.TITLE + 3;
		for (int i = 0; i < CHIPS.length; i++) {
			String id = CHIPS[i].toUpperCase(Locale.ROOT) + "_GARDEN_CHIP";
			int level = (int) PvData.getLong(profileInfo, "player_data.garden_chips." + CHIPS[i]);
			String colour = level == 0 ? "§c" : level <= 10 ? "§9" : level <= 15 ? "§5" : "§6";
			int slotX = x + i / 5 * 18;
			int slotY = top + i % 5 * 18;
			boolean hovered = PvUi.slot(graphics, PvData.item(id), slotX, slotY, mouseX, mouseY);
			PvUi.count(graphics, font, colour + level, slotX, slotY);
			if (!hovered) continue;

			List<String> tooltip = new ArrayList<>();
			tooltip.add(colour + Utils.cleanColour(PvData.itemName(id)));
			tooltip.add("");
			// The chip's ability, from the "Ability:" line to the next blank line.
			List<String> lore = PvData.itemLore(id);
			int start = 0;
			while (start < lore.size() && !Utils.cleanColour(lore.get(start)).startsWith("Ability")) start++;
			int end = start;
			while (end < lore.size() && !Utils.cleanColour(lore.get(end)).isBlank()) end++;
			if (start < lore.size()) {
				tooltip.addAll(lore.subList(start, end));
				tooltip.add("");
			}
			long spent = costs.isEmpty() ? 0 : costs.get(Math.max(0, Math.min(level - 1, costs.size() - 1)));
			long max = costs.isEmpty() ? 0 : costs.get(costs.size() - 1);
			tooltip.add("§7Sowdust: " + (spent == 0 ? "§c" : spent == max ? "§2" : "§a") + PvData.format(spent) + "§7/§2" +
				PvData.shorten(max) + " §7(§3" + PvData.percent(spent, max) + "%§7)");
			instance.tooltipToDisplay = tooltip;
		}
	}

	// ---- information ----

	private static List<Line> information(JsonObject profileInfo, JsonObject garden) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line("§7Copper: §c" + PvData.format(PvData.getLong(profileInfo, "garden_player_data.copper"))));
		lines.add(gardenLevel(garden));

		JsonObject jacob = Utils.getElement(profileInfo, "jacobs_contest") instanceof JsonObject object ? object : new JsonObject();
		int contests = jacob.get("contests") instanceof JsonObject all ? all.size() : 0;
		lines.add(new Line("§7Contests Participated: §e" + PvData.format(contests)));
		lines.add(new Line("§7Medals: §c" + PvData.format(PvData.getLong(jacob, "medals_inv.bronze")) + "§8/§7" +
			PvData.format(PvData.getLong(jacob, "medals_inv.silver")) + "§8/§6" +
			PvData.format(PvData.getLong(jacob, "medals_inv.gold"))));

		long larva = PvData.getLong(profileInfo, "garden_player_data.larva_consumed");
		long maxLarva = PvData.asLong(Garden.repo("misc.max_larva_consumed"), 5);
		lines.add(new Line("§7Larva Consumed: " + (larva >= maxLarva ? "§a" : "§c") + larva + "§7/" + maxLarva));
		lines.add(perk("Farming Level Cap", (int) PvData.getLong(jacob, "perks.farming_level_cap"), "misc.farming_level_cap"));
		lines.add(perk("Double Drops", (int) PvData.getLong(jacob, "perks.double_drops"), "misc.extra_farming_fortune"));
		return lines;
	}

	private static Line gardenLevel(JsonObject garden) {
		if (Garden.status(garden) != null) {
			return new Line("§7Garden Level: " + (garden == null ? "§eLoading..." : "§c?"),
				garden == null ? null : List.of(Garden.status(garden)));
		}
		long experience = PvData.getLong(garden, "garden_experience");
		List<Long> brackets = PvData.cumulative(Garden.repo("misc.garden_level"));
		int level = Garden.level(experience);
		int maxLevel = brackets.size();
		long total = brackets.get(brackets.size() - 1);
		List<String> tooltip = new ArrayList<>();
		if (level < maxLevel) {
			long into = experience - brackets.get(level - 1);
			long needed = brackets.get(level) - brackets.get(level - 1);
			tooltip.add("§7To level " + (level + 1) + ": §2" + PvData.format(into) + "§a/§2" + PvData.format(needed) +
				" §7(§3" + PvData.percent(into, needed) + "%§7)");
			tooltip.add("§7To max: §2" + PvData.format(experience) + "§a/§2" + PvData.format(total) +
				" §7(§3" + PvData.percent(experience, total) + "%§7)");
		} else {
			tooltip.add("§7Overflow XP: §2" + PvData.format(experience - total));
		}
		return new Line("§7Garden Level: §2" + level + "§7/" + maxLevel, tooltip);
	}

	private static Line perk(String name, int level, String costsPath) {
		List<Map<String, Long>> costs = PvData.cumulativeCosts(Garden.repo(costsPath));
		String text = "§7" + name + ": " + (level >= costs.size() ? "§a" : "§c") + level + "§7/" + costs.size();
		return new Line(text, level >= costs.size() ? null : PvData.costLines(level, costs));
	}
}
