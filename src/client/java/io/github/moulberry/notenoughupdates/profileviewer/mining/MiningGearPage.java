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
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Mining Gear sub-page: the best mining armor, equipment, pickaxes/drills and chisel from every inventory,
 * and Suspicious Scrap held. Lists and scoring follow SkyBlockPv's {@code MiningGearScreen}.
 */
public class MiningGearPage implements GuiProfileViewerPage {

	// Every list here is worst to best (Divan is the best armor); the best tier the player
	// owns wins, and the upgrade score only decides between copies of the same tier.
	private static final List<String> PICKAXES = List.of(
		"WOOD_PICKAXE", "ROOKIE_PICKAXE", "STONE_PICKAXE", "ZOMBIE_PICKAXE", "IRON_PICKAXE", "GOLD_PICKAXE",
		"PROMISING_PICKAXE", "LAPIS_PICKAXE", "DIAMOND_PICKAXE", "JUNGLE_PICKAXE", "ALPHA_PICK",
		"FRACTURED_MITHRIL_PICKAXE", "BANDAGED_MITHRIL_PICKAXE", "MITHRIL_PICKAXE", "REFINED_MITHRIL_PICKAXE",
		"RUSTY_TITANIUM_PICKAXE", "TITANIUM_PICKAXE", "REFINED_TITANIUM_PICKAXE", "PICKONIMBUS", "MITHRIL_DRILL_1",
		"MITHRIL_DRILL_2", "GEMSTONE_GAUNTLET", "TITANIUM_DRILL_1", "TITANIUM_DRILL_2", "TITANIUM_DRILL_3",
		"TITANIUM_DRILL_4", "GEMSTONE_DRILL_1", "GEMSTONE_DRILL_2", "GEMSTONE_DRILL_3", "GEMSTONE_DRILL_4", "DIVAN_DRILL"
	);
	private static final List<String> ARMOR_SETS = List.of(
		"MINER_OUTFIT", "LAPIS_ARMOR", "TANK_MINER", "HARDENED_DIAMOND", "MINERAL", "GLOSSY_MINERAL", "GOBLIN",
		"GLACITE", "HEAT", "ARMOR_OF_YOG", "FLAME_BREAKER", "SORROW", "DIVAN"
	);
	private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
	private static final List<String> NECKLACES = List.of("MITHRIL_NECKLACE", "TITANIUM_NECKLACE", "AMBER_NECKLACE", "DIVAN_PENDANT");
	private static final List<String> CLOAKS = List.of("ANCIENT_CLOAK", "MITHRIL_CLOAK", "TITANIUM_CLOAK", "SAPPHIRE_CLOAK");
	private static final List<String> BELTS = List.of("MITHRIL_BELT", "TITANIUM_BELT", "JADE_BELT");
	private static final List<String> GLOVES = List.of(
		"GLOWSTONE_GAUNTLET", "VANQUISHED_GLOWSTONE_GAUNTLET", "MITHRIL_GAUNTLET", "TITANIUM_GAUNTLET",
		"AMETHYST_GAUNTLET", "DWARVEN_HANDWARMERS"
	);
	private static final List<String> CHISELS = List.of("CHISEL", "REINFORCED_CHISEL", "GLACITE_CHISEL", "PERFECT_CHISEL");
	private static final String SCRAP = "SUSPICIOUS_SCRAP";

	private static final Identifier[] ARMOR_SLOT_SPRITES = {
		Identifier.withDefaultNamespace("container/slot/helmet"),
		Identifier.withDefaultNamespace("container/slot/chestplate"),
		Identifier.withDefaultNamespace("container/slot/leggings"),
		Identifier.withDefaultNamespace("container/slot/boots"),
	};

	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"};
	private static final String[] GEM_QUALITIES = {"ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT"};
	private static final Pattern NUMBER_ENTRY = Pattern.compile("(\\w+):(-?\\d+)");
	private static final Pattern GEM_ENTRY = Pattern.compile("(\\w+?_\\d+):(?:\"(\\w+)\"|\\{[^}]*?quality:\"(\\w+)\")");

	private final GuiProfileViewer instance;
	private final Map<JsonObject, ItemStack> stackCache = new IdentityHashMap<>();
	private JsonObject gearFor;
	private JsonObject[] armor;
	private JsonObject[] equipment;
	private JsonObject[] pickaxes;
	private JsonObject chisel;
	private int scrapCount;
	private ItemStack scrapIcon;

	public MiningGearPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		gearFor = null;
		stackCache.clear();
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		Font font = instance.getFont();

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject profileInfo = profile.getProfileInformation(profileId);
		JsonObject inventoryInfo = profile.getInventoryInfo(profileId);
		if (profileInfo == null || inventoryInfo == null) return;
		if (gearFor != inventoryInfo) {
			findGear(profileInfo, inventoryInfo);
			gearFor = inventoryInfo;
		}

		// Armor + equipment (2x4), pickaxes (1x4), then the chisel and scrap column.
		int width = 8 + 36 + 6 + 18 + 6 + 18 + 8;
		int height = MiningUi.PANEL_TITLE + 6 + 72 + 8;
		int x = guiLeft + (instance.sizeX - width) / 2;
		int y = guiTop + (instance.sizeY - height) / 2;
		MiningUi.drawPanel(graphics, font, x, y, width, height, "Mining Gear");

		int top = y + MiningUi.PANEL_TITLE + 6;
		int left = x + 8;
		for (int i = 0; i < 4; i++) {
			drawSlot(graphics, armor[i], ARMOR_SLOT_SPRITES[i], left, top + i * 18, mouseX, mouseY);
			drawSlot(graphics, equipment[i], null, left + 18, top + i * 18, mouseX, mouseY);
			drawSlot(graphics, pickaxes[i], null, left + 42, top + i * 18, mouseX, mouseY);
		}
		drawSlot(graphics, chisel, null, left + 66, top + 9, mouseX, mouseY);

		int scrapY = top + 45;
		MiningUi.drawSlotBackground(graphics, left + 66, scrapY);
		RenderUtils.drawItemStack(graphics, scrapIcon, left + 67, scrapY + 1);
		// Only about 3 characters fit in the slot, so large counts are shortened (1.2k).
		String count = scrapCount > 999 ? StringUtils.shortNumberFormat(scrapCount) : String.valueOf(scrapCount);
		RenderUtils.text(graphics, font, count, left + 67 + 17 - font.width(count), scrapY + 10, 0xFFFFFF, true);
		if (Utils.isWithinRect(mouseX, mouseY, left + 66, scrapY, 18, 18)) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add(scrapIcon.getHoverName().getString());
			tooltip.add("§7Held in inventories and sacks: §f" + String.format(Locale.US, "%,d", scrapCount));
			instance.tooltipToDisplay = tooltip;
		}
	}

	private void drawSlot(GuiGraphicsExtractor graphics, JsonObject item, Identifier emptySprite, int x, int y, int mouseX, int mouseY) {
		MiningUi.drawSlotBackground(graphics, x, y);
		if (item == null) {
			if (emptySprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, emptySprite, x + 1, y + 1, 16, 16);
			return;
		}
		RenderUtils.drawItemStack(graphics, stackCache.computeIfAbsent(item,
			json -> NotEnoughUpdates.INSTANCE.manager.jsonToStack(json, false)), x + 1, y + 1);
		if (Utils.isWithinRect(mouseX, mouseY, x, y, 18, 18)) {
			List<String> tooltip = new ArrayList<>();
			String name = Utils.getElementAsString(item.get("displayname"), "");
			tooltip.add(name.isEmpty() ? "Unknown Item" : name);
			if (item.get("lore") instanceof JsonArray lore) {
				for (JsonElement line : lore) tooltip.add(line.getAsString());
			}
			instance.tooltipToDisplay = tooltip;
		}
	}

	private void findGear(JsonObject profileInfo, JsonObject inventoryInfo) {
		List<JsonObject> items = new ArrayList<>();
		scrapCount = 0;
		for (Map.Entry<String, JsonElement> inventory : inventoryInfo.entrySet()) {
			if (inventory.getKey().equals("backpack_sizes") || !(inventory.getValue() instanceof JsonArray array)) continue;
			for (JsonElement element : array) {
				if (!(element instanceof JsonObject item)) continue;
				String id = Utils.getElementAsString(item.get("internalname"), "");
				if (id.equals(SCRAP)) scrapCount += (int) Utils.getElementAsFloat(item.get("count"), 1);
				else items.add(item);
			}
		}
		scrapCount += (int) Utils.getElementAsFloat(Utils.getElement(profileInfo, "sacks_counts." + SCRAP),
			Utils.getElementAsFloat(Utils.getElement(profileInfo, "inventory.sacks_counts." + SCRAP), 0));

		Map<JsonObject, Integer> scores = new IdentityHashMap<>();
		items.sort(Comparator.comparingInt((JsonObject item) -> scores.computeIfAbsent(item, MiningGearPage::score)).reversed());

		armor = new JsonObject[4];
		for (int i = 0; i < 4; i++) {
			String piece = ARMOR_PIECES[i];
			armor[i] = best(items, id -> id.endsWith("_" + piece) ? ARMOR_SETS.indexOf(id.substring(0, id.length() - piece.length() - 1)) : -1);
		}
		equipment = new JsonObject[]{
			best(items, NECKLACES::indexOf), best(items, CLOAKS::indexOf), best(items, BELTS::indexOf), best(items, GLOVES::indexOf)
		};
		// Top four pickaxes by tier; the stable sort keeps the score order within a tier.
		List<JsonObject> owned = new ArrayList<>();
		for (JsonObject item : items) {
			if (PICKAXES.contains(Utils.getElementAsString(item.get("internalname"), ""))) owned.add(item);
		}
		owned.sort(Comparator.comparingInt((JsonObject item) ->
			PICKAXES.indexOf(Utils.getElementAsString(item.get("internalname"), ""))).reversed());
		pickaxes = new JsonObject[4];
		for (int i = 0; i < Math.min(4, owned.size()); i++) pickaxes[i] = owned.get(i);
		chisel = best(items, CHISELS::indexOf);

		JsonObject scrapJson = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(SCRAP);
		scrapIcon = scrapJson == null ? new ItemStack(Items.PRISMARINE_SHARD)
			: NotEnoughUpdates.INSTANCE.manager.jsonToStack(scrapJson);
	}

	/**
	 * The item with the highest tier ({@code tierOf}, -1 if not in the category). {@code items} is sorted by
	 * score, so ties keep the highest-scoring copy.
	 */
	private static JsonObject best(List<JsonObject> items, ToIntFunction<String> tierOf) {
		JsonObject best = null;
		int bestTier = -1;
		for (JsonObject item : items) {
			int tier = tierOf.applyAsInt(Utils.getElementAsString(item.get("internalname"), ""));
			if (tier > bestTier) {
				best = item;
				bestTier = tier;
			}
		}
		return best;
	}

	/**
	 * SkyBlockPv's mining score: a point each for recombobulator, reforge, fuel tank, engine and upgrade module,
	 * the ultimate enchant's level, levels above V on other enchants, flawless/perfect gems (Jasper from fine),
	 * Divan powder coating, and rarity above Rare (up to 3).
	 */
	private static int score(JsonObject item) {
		String nbt = Utils.getElementAsString(item.get("nbttag"), "");
		int score = 0;

		if (nbt.contains("rarity_upgrades:1")) score++;

		String enchants = block(nbt, "enchantments:{");
		if (enchants != null) {
			Matcher matcher = NUMBER_ENTRY.matcher(enchants);
			boolean ultimateCounted = false;
			while (matcher.find()) {
				int level = Integer.parseInt(matcher.group(2));
				if (!ultimateCounted && matcher.group(1).startsWith("ultimate")) {
					score += level;
					ultimateCounted = true;
				}
				if (level > 4) score += level - 4;
			}
		}

		String gems = block(nbt, "gems:{");
		if (gems != null) {
			Matcher matcher = GEM_ENTRY.matcher(gems);
			while (matcher.find()) {
				String slot = matcher.group(1);
				String quality = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
				int ordinal = indexOf(GEM_QUALITIES, quality);
				if (ordinal < 0) continue;
				Matcher type = Pattern.compile(Pattern.quote(slot) + "_gem:\"(\\w+)\"").matcher(gems);
				String gemstone = type.find() ? type.group(1) : slot.substring(0, slot.lastIndexOf('_'));
				int points = gemstone.equals("JASPER") ? ordinal - 2 : ordinal - 3;
				if (points >= 1) score += points;
			}
		}

		Matcher coating = Pattern.compile("divan_powder_coating:(\\d+)").matcher(nbt);
		if (coating.find()) score += Integer.parseInt(coating.group(1));
		if (nbt.contains("modifier:")) score++;
		if (nbt.contains("drill_part_fuel_tank:")) score++;
		if (nbt.contains("drill_part_engine:")) score++;
		if (nbt.contains("drill_part_upgrade_module:")) score++;

		score += Math.max(0, Math.min(3, rarity(item) - 2));
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

	/** The {@code {...}} block that starts with {@code opening} in an SNBT string, braces balanced. */
	private static String block(String nbt, String opening) {
		int start = nbt.indexOf(opening);
		if (start < 0) return null;
		int depth = 0;
		for (int i = start + opening.length() - 1; i < nbt.length(); i++) {
			char c = nbt.charAt(i);
			if (c == '{') depth++;
			else if (c == '}' && --depth == 0) return nbt.substring(start + opening.length(), i);
		}
		return null;
	}

	private static int indexOf(String[] values, String value) {
		for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
		return -1;
	}
}
