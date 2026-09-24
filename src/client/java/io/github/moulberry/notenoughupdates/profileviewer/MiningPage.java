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
 *
 * Portions of this code are from the SkyBlockPv mod (https://github.com/meowdding/skyblock-pv, MIT with
 * attribution): the skill-tree powder maths (total earned minus spent in the selected tree), the Rock pet brackets,
 * and the crystal / Glacite tooltips.
 */

package io.github.moulberry.notenoughupdates.profileviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.profileviewer.hotm.HotmTree;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Heart of the Mountain" tab, following current NEU's layout: HOTM level, Mithril/Gemstone/Glacite powder, all
 * twelve crystals, and the perk tree drawn from the repo's {@code constants/hotmlayout.json} (see {@link HotmTree}),
 * which scrolls because the 10-tier tree is taller than its panel. Icons next to the HOTM bar show the Forge, the
 * Rock pet milestone, and Glacite Tunnels stats on hover.
 */
public class MiningPage implements GuiProfileViewerPage {

	private static final Identifier BACKGROUND = Identifier.parse("notenoughupdates:profile_viewer/mining/background.png");
	private static final Identifier PERK_BACKGROUND = Identifier.parse("notenoughupdates:profile_viewer/mining/perk_background.png");
	private static final Identifier PERK_CONNECTION_X = Identifier.parse("notenoughupdates:profile_viewer/mining/perk_connection_x.png");
	private static final Identifier PERK_CONNECTION_Y = Identifier.parse("notenoughupdates:profile_viewer/mining/perk_connection_y.png");

	private static final Map<String, ChatFormatting> CRYSTAL_COLOURS = new LinkedHashMap<>();
	static {
		CRYSTAL_COLOURS.put("jade", ChatFormatting.GREEN);
		CRYSTAL_COLOURS.put("amethyst", ChatFormatting.DARK_PURPLE);
		CRYSTAL_COLOURS.put("amber", ChatFormatting.GOLD);
		CRYSTAL_COLOURS.put("sapphire", ChatFormatting.AQUA);
		CRYSTAL_COLOURS.put("topaz", ChatFormatting.YELLOW);
		CRYSTAL_COLOURS.put("jasper", ChatFormatting.LIGHT_PURPLE);
		CRYSTAL_COLOURS.put("ruby", ChatFormatting.RED);
		CRYSTAL_COLOURS.put("opal", ChatFormatting.WHITE);
		CRYSTAL_COLOURS.put("aquamarine", ChatFormatting.BLUE);
		CRYSTAL_COLOURS.put("peridot", ChatFormatting.DARK_GREEN);
		CRYSTAL_COLOURS.put("onyx", ChatFormatting.DARK_GRAY);
		CRYSTAL_COLOURS.put("citrine", ChatFormatting.DARK_RED);
	}
	/** Crystals placed once per Crystal Nucleus run. */
	private static final String[] NUCLEUS_CRYSTALS = {"amber", "amethyst", "jade", "sapphire", "topaz"};

	private static final String[] POWDERS = {"mithril", "gemstone", "glacite"};
	private static final String[] POWDER_NAMES = {"§2Mithril", "§dGemstone", "§bGlacite"};

	/** Ores mined needed for each Rock pet rarity (the Rock is given by the ores-mined pet milestone). */
	private static final int[] ROCK_ORES = {2500, 7500, 20000, 100000, 250000};
	private static final String[] RARITIES = {"§fCommon", "§aUncommon", "§9Rare", "§5Epic", "§6Legendary"};

	private static final String[] FOSSILS = {"CLAW", "SPINE", "CLUBBED", "UGLY", "HELIX", "FOOTPRINT", "WEBBED", "TUSK"};
	private static final String[] CORPSES = {"lapis", "tungsten", "umber", "vanguard"};
	private static final String[] CORPSE_COLOURS = {"§9", "§7", "§6", "§b"};

	// Perk tree panel of the background texture, and the grid pitch (16px perk + 4px connection).
	private static final int TREE_LEFT = 249;
	private static final int TREE_TOP = 16;
	private static final int TREE_RIGHT = 412;
	private static final int TREE_BOTTOM = 185;
	private static final int GRID = 20;
	private static final int SPACING = 4;
	private static final int TREE_MARGIN = 3;

	private final GuiProfileViewer instance;
	private final HashMap<String, ProfileViewer.Level> hotmLevels = new HashMap<>();
	private final Map<String, ItemStack> iconCache = new HashMap<>();
	private final Map<HotmTree.Perk, HotmTree.PerkState> perkStates = new IdentityHashMap<>();
	private JsonObject perkStatesFor;
	private HotmTree perkStatesTree;
	/** Pixels scrolled up from the bottom of the tree, where it starts. */
	private int scroll = 0;

	public MiningPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		hotmLevels.clear();
		scroll = 0;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		if (!Utils.isWithinRect((int) mouseX, (int) mouseY, guiLeft + TREE_LEFT, guiTop + TREE_TOP,
			TREE_RIGHT - TREE_LEFT, TREE_BOTTOM - TREE_TOP)) return false;
		scroll = Math.max(0, Math.min(maxScroll(), scroll + (int) Math.round(scrollY * GRID)));
		return true;
	}

	private int maxScroll() {
		HotmTree tree = HotmTree.get();
		if (tree == null) return 0;
		return Math.max(0, tree.rows() * GRID + TREE_MARGIN * 2 - (TREE_BOTTOM - TREE_TOP));
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		Font font = instance.getFont();

		RenderUtils.drawTexturedRect(graphics, BACKGROUND, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject profileInfo = profile.getProfileInformation(profileId);
		if (profileInfo == null) return;
		JsonObject miningCore = Utils.getElement(profileInfo, "mining_core") instanceof JsonObject object ? object : new JsonObject();

		ProfileViewer.Level hotmLevel = hotmLevels.computeIfAbsent(profileId, id -> {
			JsonObject leveling = Constants.LEVELING;
			JsonArray table = Utils.getElementOrDefault(leveling, "HOTM", new JsonArray()).getAsJsonArray();
			int cap = (int) Utils.getElementAsFloat(Utils.getElement(leveling, "leveling_caps.HOTM"), 10);
			return ProfileViewer.getLevel(table, Utils.getElementAsFloat(miningCore.get("experience"), 0), cap, false);
		});

		instance.renderXpBar(graphics, ChatFormatting.RED + "HOTM", new ItemStack(Items.IRON_PICKAXE),
			guiLeft + 23, guiTop + 25, 110, hotmLevel, mouseX, mouseY);

		drawPowder(graphics, font, miningCore, guiLeft, guiTop);
		drawCrystals(graphics, miningCore, guiLeft, guiTop, mouseX, mouseY);
		drawInfoIcons(graphics, profileInfo, miningCore, guiLeft, guiTop, mouseX, mouseY);
		drawTree(graphics, miningCore, (int) Math.floor(hotmLevel.level), guiLeft, guiTop, mouseX, mouseY);
	}

	private void drawPowder(GuiGraphicsExtractor graphics, Font font, JsonObject miningCore, int guiLeft, int guiTop) {
		// Since the skill tree rework powder_<type> is the total ever earned, and what's spent is tracked per tree
		// slot. Older data had powder_<type> as the current amount and powder_spent_<type> as everything spent.
		boolean isTotal = miningCore.has("powder_is_total");
		int[] columns = {100, 150, 200};
		int labelX = guiLeft + 22;
		RenderUtils.text(graphics, font, "§7Current", labelX, guiTop + 65, 0xFFFFFF, true);
		RenderUtils.text(graphics, font, "§7Total", labelX, guiTop + 77, 0xFFFFFF, true);
		for (int i = 0; i < POWDERS.length; i++) {
			float stored = Utils.getElementAsFloat(miningCore.get("powder_" + POWDERS[i]), 0);
			float current;
			float total;
			if (isTotal) {
				total = stored;
				current = stored - Utils.getElementAsFloat(miningCore.get("powder_spent_selected_" + POWDERS[i]), 0);
			} else {
				current = stored;
				total = stored + Utils.getElementAsFloat(miningCore.get("powder_spent_" + POWDERS[i]), 0);
			}
			String colour = POWDER_NAMES[i].substring(0, 2);
			float x = guiLeft + columns[i];
			RenderUtils.drawStringCentered(graphics, POWDER_NAMES[i], font, x, guiTop + 57, true, 0);
			RenderUtils.drawStringCentered(graphics, colour + StringUtils.shortNumberFormat(Math.max(0, current)), font, x, guiTop + 69, true, 0);
			RenderUtils.drawStringCentered(graphics, colour + StringUtils.shortNumberFormat(total), font, x, guiTop + 81, true, 0);
		}
	}

	private void drawCrystals(GuiGraphicsExtractor graphics, JsonObject miningCore, int guiLeft, int guiTop, int mouseX, int mouseY) {
		int startX = guiLeft + 20;
		int startY = guiTop + 101;
		int rows = (CRYSTAL_COLOURS.size() + 1) / 2;
		int columnWidth = 101;
		int index = 0;
		for (Map.Entry<String, ChatFormatting> crystal : CRYSTAL_COLOURS.entrySet()) {
			int x = startX + (index / rows) * (columnWidth + 4);
			int y = startY + (index % rows) * 10;
			JsonElement data = Utils.getElement(miningCore, "crystals." + crystal.getKey() + "_crystal");
			String state = Utils.getElementAsString(Utils.getElement(data, "state"), "NOT_FOUND");
			boolean found = state.equals("FOUND") || state.equals("PLACED");
			String name = crystal.getKey().substring(0, 1).toUpperCase(Locale.ROOT) + crystal.getKey().substring(1);
			RenderUtils.renderAlignedString(graphics, crystal.getValue() + name + " Crystal:", found ? "§a✔" : "§c✖", x, y, columnWidth);

			if (Utils.isWithinRect(mouseX, mouseY, x, y - 1, columnWidth, 10)) {
				List<String> tooltip = new ArrayList<>();
				tooltip.add(crystal.getValue() + name + " Crystal");
				tooltip.add("§7State: " + (found ? "§a" : "§c") + titleCase(state));
				tooltip.add("§7Found: §f" + formatNumber(Utils.getElementAsFloat(Utils.getElement(data, "total_found"), 0)));
				float placed = Utils.getElementAsFloat(Utils.getElement(data, "total_placed"), 0);
				if (placed > 0) tooltip.add("§7Placed: §f" + formatNumber(placed));
				instance.tooltipToDisplay = tooltip;
			}
			index++;
		}

		double runs = Double.MAX_VALUE;
		for (String crystal : NUCLEUS_CRYSTALS) {
			runs = Math.min(runs, Utils.getElementAsFloat(Utils.getElement(miningCore, "crystals." + crystal + "_crystal.total_placed"), 0));
		}
		RenderUtils.renderAlignedString(graphics, "§9Nucleus Runs Completed:", "§f" + formatNumber(runs),
			startX, guiTop + 172, columnWidth * 2 + 4);
	}

	private void drawInfoIcons(
		GuiGraphicsExtractor graphics, JsonObject profileInfo, JsonObject miningCore, int guiLeft, int guiTop, int mouseX, int mouseY
	) {
		int y = guiTop + 20;

		// Forge
		int forgeX = guiLeft + 149;
		drawIcon(graphics, new ItemStack(Items.ANVIL), forgeX, y);
		if (Utils.isWithinRect(mouseX, mouseY, forgeX, y, 16, 16)) instance.tooltipToDisplay = forgeTooltip(profileInfo, miningCore);

		// Rock pet
		float oresMined = Utils.getElementAsFloat(Utils.getElement(profileInfo, "player_stats.pets.milestone.ores_mined"),
			Utils.getElementAsFloat(Utils.getElement(profileInfo, "stats.pet_milestone_ores_mined"), 0));
		int rarity = -1;
		for (int i = 0; i < ROCK_ORES.length; i++) if (oresMined >= ROCK_ORES[i]) rarity = i;
		int rockX = guiLeft + 171;
		drawIcon(graphics, repoIcon("ROCK;" + Math.max(rarity, 0), Items.COBBLESTONE), rockX, y);
		if (Utils.isWithinRect(mouseX, mouseY, rockX, y, 16, 16)) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add("§7Rock Pet: " + (rarity < 0 ? "§cNone" : RARITIES[rarity]));
			tooltip.add("§7Ores Mined: §b" + formatNumber(oresMined));
			tooltip.add("");
			for (int i = 0; i < ROCK_ORES.length; i++) {
				tooltip.add((oresMined >= ROCK_ORES[i] ? RARITIES[i] + " Rock" : "§8§m" + RARITIES[i].substring(2) + " Rock") +
					" §8(" + StringUtils.shortNumberFormat(ROCK_ORES[i]) + ")");
			}
			instance.tooltipToDisplay = tooltip;
		}

		// Glacite Tunnels
		JsonElement glacite = Utils.getElement(profileInfo, "glacite_player_data");
		int glaciteX = guiLeft + 193;
		drawIcon(graphics, new ItemStack(Items.PACKED_ICE), glaciteX, y);
		if (Utils.isWithinRect(mouseX, mouseY, glaciteX, y, 16, 16)) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add("§bGlacite Tunnels");
			tooltip.add("§7Mineshafts Entered: §f" + formatNumber(Utils.getElementAsFloat(Utils.getElement(glacite, "mineshafts_entered"), 0)));
			tooltip.add("§7Fossil Dust: §f" + formatNumber(Utils.getElementAsFloat(Utils.getElement(glacite, "fossil_dust"), 0)));
			tooltip.add("");
			tooltip.add("§7Corpses Looted:");
			for (int i = 0; i < CORPSES.length; i++) {
				tooltip.add("§8 • " + CORPSE_COLOURS[i] + titleCase(CORPSES[i]) + "§7: §f" +
					formatNumber(Utils.getElementAsFloat(Utils.getElement(glacite, "corpses_looted." + CORPSES[i]), 0)));
			}
			List<String> donated = new ArrayList<>();
			if (Utils.getElement(glacite, "fossils_donated") instanceof JsonArray array) {
				for (JsonElement fossil : array) donated.add(fossil.getAsString());
			}
			tooltip.add("");
			tooltip.add("§7Fossils Donated: §f" + donated.size() + "§7/" + FOSSILS.length);
			for (String fossil : FOSSILS) {
				tooltip.add("§8 • " + (donated.contains(fossil) ? "§a" : "§c") + titleCase(fossil) + " Fossil");
			}
			instance.tooltipToDisplay = tooltip;
		}
	}

	private List<String> forgeTooltip(JsonObject profileInfo, JsonObject miningCore) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add("§6Forge");
		tooltip.add("");
		JsonElement processes = Utils.getElement(profileInfo, "forge.forge_processes.forge_1");
		TreeMap<Integer, JsonObject> slots = new TreeMap<>();
		if (processes instanceof JsonObject object) {
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (entry.getValue() instanceof JsonObject slot) {
					slots.put((int) Utils.getElementAsFloat(slot.get("slot"), Integer.parseInt(entry.getKey())), slot);
				}
			}
		}
		if (slots.isEmpty()) {
			tooltip.add("§7Nothing is being forged.");
			return tooltip;
		}

		// Quick Forge, as in hotmlayout.json: 10% + 0.5% per level, 30% at level 20.
		int quickForge = (int) Utils.getElementAsFloat(Utils.getElement(miningCore, "nodes.quick_forge"),
			Utils.getElementAsFloat(Utils.getElement(miningCore, "nodes.forge_time"), 0));
		double multiplier = quickForge <= 0 ? 1 : quickForge >= 20 ? 0.7 : 1 - (10 + quickForge * 0.5) / 100;

		Map<String, JsonObject> items = NotEnoughUpdates.INSTANCE.manager.getItemInformation();
		for (Map.Entry<Integer, JsonObject> entry : slots.entrySet()) {
			JsonObject slot = entry.getValue();
			String id = Utils.getElementAsString(slot.get("id"), "?");
			JsonObject item = items.get(id);
			if (item == null && "PETS".equals(Utils.getElementAsString(slot.get("type"), ""))) item = items.get(id + ";4");
			String name = item != null && item.has("displayname")
				? item.get("displayname").getAsString().replace("[Lvl {LVL}] ", "")
				: "§f" + titleCase(id);

			long duration = forgeDuration(item);
			String status;
			if (duration <= 0) {
				status = "§7Time left: §cUnknown";
			} else {
				long start = (long) Utils.getElementAsFloat(slot.get("startTime"), 0);
				long left = start + (long) (duration * 1000 * multiplier) - System.currentTimeMillis();
				status = left <= 0 ? "§aReady!" : "§7Time left: §a" + formatDuration(left);
			}
			tooltip.add("§7" + entry.getKey() + ": " + name + " " + status);
		}
		return tooltip;
	}

	/** Forge time in seconds from the repo item's forge recipe, or 0 if it has none. */
	private static long forgeDuration(JsonObject item) {
		if (item == null || !(item.get("recipes") instanceof JsonArray recipes)) return 0;
		for (JsonElement recipe : recipes) {
			if (recipe instanceof JsonObject object && "forge".equals(Utils.getElementAsString(object.get("type"), ""))) {
				return (long) Utils.getElementAsFloat(object.get("duration"), 0);
			}
		}
		return 0;
	}

	private static String formatDuration(long millis) {
		long seconds = millis / 1000;
		long days = seconds / 86400;
		long hours = seconds / 3600 % 24;
		long minutes = seconds / 60 % 60;
		if (days > 0) return days + "d " + hours + "h";
		if (hours > 0) return hours + "h " + minutes + "m";
		if (minutes > 0) return minutes + "m " + seconds % 60 + "s";
		return seconds + "s";
	}

	private void drawTree(
		GuiGraphicsExtractor graphics, JsonObject miningCore, int hotmTier, int guiLeft, int guiTop, int mouseX, int mouseY
	) {
		int left = guiLeft + TREE_LEFT;
		int top = guiTop + TREE_TOP;
		int right = guiLeft + TREE_RIGHT;
		int bottom = guiTop + TREE_BOTTOM;

		HotmTree tree = HotmTree.get();
		if (tree == null) {
			RenderUtils.drawStringCenteredScaledMaxWidth(graphics, "§cRepo is missing hotmlayout.json", instance.getFont(),
				(left + right) / 2f, (top + bottom) / 2f, true, right - left - 8, 0xFFFFFF);
			return;
		}

		scroll = Math.min(scroll, maxScroll());
		// Evaluating a perk runs its hotmlayout.json formulas, so keep the results while the profile and tree stay the same.
		if (perkStatesFor != miningCore || perkStatesTree != tree) {
			perkStates.clear();
			perkStatesFor = miningCore;
			perkStatesTree = tree;
		}
		int originX = left + (right - left - tree.columns() * GRID) / 2;
		int originY = top + TREE_MARGIN - maxScroll() + scroll;
		JsonObject nodes = miningCore.get("nodes") instanceof JsonObject object ? object : new JsonObject();
		boolean hoveringPanel = Utils.isWithinRect(mouseX, mouseY, left, top, right - left, bottom - top);
		int half = SPACING / 2;

		graphics.enableScissor(left, top, right, bottom);
		for (HotmTree.Perk perk : tree.perks()) {
			int cellX = originX + perk.x() * GRID;
			int cellY = originY + perk.y() * GRID;
			if (cellY > bottom || cellY + GRID < top) continue;

			if (tree.hasPerkAt(perk.x() - 1, perk.y())) {
				RenderUtils.drawTexturedRect(graphics, PERK_CONNECTION_X, cellX - half, cellY, SPACING, GRID);
			}
			if (tree.hasPerkAt(perk.x(), perk.y() - 1)) {
				RenderUtils.drawTexturedRect(graphics, PERK_CONNECTION_Y, cellX, cellY - half, GRID, SPACING);
			}
			RenderUtils.drawTexturedRect(graphics, PERK_BACKGROUND, cellX + half, cellY + half, GRID - SPACING, GRID - SPACING);

			HotmTree.PerkState state = perkStates.computeIfAbsent(perk, p -> tree.evaluate(p, nodes, hotmTier));
			RenderUtils.drawItemStack(graphics, perkIcon(state.itemId()), cellX + half, cellY + half);

			if (hoveringPanel && Utils.isWithinRect(mouseX, mouseY, cellX + half, cellY + half, GRID - SPACING, GRID - SPACING)) {
				instance.tooltipToDisplay = state.tooltip();
			}
		}
		graphics.disableScissor();
	}

	private void drawIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		RenderUtils.drawTexturedRect(graphics, PERK_BACKGROUND, x, y, 16, 16);
		RenderUtils.drawItemStack(graphics, stack, x, y);
	}

	/** Perk icons are vanilla ids (COAL, EMERALD_BLOCK, ...); anything else is looked up in the item repo. */
	private ItemStack perkIcon(String id) {
		if (id == null) return new ItemStack(Items.PAINTING);
		return iconCache.computeIfAbsent(id, key -> {
			Identifier vanillaId = Identifier.tryParse(key.toLowerCase(Locale.ROOT));
			Item item = vanillaId == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(vanillaId);
			if (item != Items.AIR) return new ItemStack(item);
			return repoIcon(key, Items.PAINTING);
		});
	}

	private ItemStack repoIcon(String id, Item fallback) {
		return iconCache.computeIfAbsent("repo:" + id, key -> {
			JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(id);
			ItemStack stack = json == null ? null : NotEnoughUpdates.INSTANCE.manager.jsonToStack(json);
			return stack == null || stack.isEmpty() ? new ItemStack(fallback) : stack;
		});
	}

	private static String formatNumber(double number) {
		return NumberFormat.getIntegerInstance(Locale.US).format(number);
	}

	private static String titleCase(String text) {
		StringBuilder builder = new StringBuilder();
		for (String word : text.toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty()) continue;
			if (!builder.isEmpty()) builder.append(' ');
			builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return builder.toString();
	}
}
