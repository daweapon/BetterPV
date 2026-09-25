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

import io.github.moulberry.notenoughupdates.profileviewer.VanillaItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.mining.MiningUi;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The attributes sub-page: every hunting shard by rarity, tinted by how far it's been syphoned, with search
 * and a filter. Shards come from {@code constants/attribute_shards.json}; player data is
 * {@code attributes.stacks} and {@code shards.owned}.
 */
public class AttributesPage implements GuiProfileViewerPage {

	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
	private static final int[] RARITY_COLOURS = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00};
	private static final String[] RARITY_CODES = {"§f", "§a", "§9", "§5", "§6"};

	private enum Filter {
		ALL("All"),
		UNLOCKED("Unlocked"),
		LOCKED("Locked"),
		NOT_MAXED("Not Maxed"),
		MAXED("Maxed");

		final String display;

		Filter(String display) {
			this.display = display;
		}
	}

	/**
	 * One attribute; {@code rarity} is -1 if the repo doesn't know it. {@code unlocked} means the player has had
	 * its shard or syphoned it.
	 */
	private record Shard(
		String id, String internalName, String name, String shardName, String shardId, int rarity, boolean unconsumable,
		boolean unlocked, int owned, long capturedAt, int syphoned
	) {
		int max() {
			return unconsumable || rarity < 0 ? 0 : AttributesPage.max(rarity);
		}
	}

	private static final int SLOT = 18;
	private static final int GROUP_GAP = 4;
	private static final int CONTROL_WIDTH = 100;
	private static final int CONTROL_HEIGHT = 20;
	private static final int CONTROL_GAP = 5;
	private static final int CONTROLS_TOP = 8;
	private static final int GRID_TOP = CONTROLS_TOP + CONTROL_HEIGHT + 6;
	private static final int GRID_MARGIN = 8;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US);

	private final GuiProfileViewer instance;
	private final Map<String, ItemStack> stackCache = new HashMap<>();
	private EditBox searchField;
	private Filter filter = Filter.ALL;
	private int scroll = 0;
	private String lastQuery = "";

	private JsonObject dataFor;
	private List<Shard> shards = List.of();

	public AttributesPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		dataFor = null;
		scroll = 0;
	}

	// ---- layout ----

	private int controlsLeft() {
		return GuiProfileViewer.getGuiLeft() + (instance.sizeX - CONTROL_WIDTH * 2 - CONTROL_GAP) / 2;
	}

	private int filterLeft() {
		return controlsLeft() + CONTROL_WIDTH + CONTROL_GAP;
	}

	private int gridLeft() {
		return GuiProfileViewer.getGuiLeft() + GRID_MARGIN;
	}

	private int gridTop() {
		return GuiProfileViewer.getGuiTop() + GRID_TOP;
	}

	private int gridWidth() {
		return instance.sizeX - GRID_MARGIN * 2;
	}

	private int gridHeight() {
		return instance.sizeY - GRID_TOP - 6;
	}

	private int columns() {
		return gridWidth() / SLOT;
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int guiTop = GuiProfileViewer.getGuiTop();
		if (searchField != null) {
			boolean inSearch = Utils.isWithinRect((int) mouseX, (int) mouseY, controlsLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT);
			searchField.setFocused(inSearch);
			if (inSearch) {
				if (mouseButton == 1) searchField.setValue("");
				return true;
			}
		}
		if (Utils.isWithinRect((int) mouseX, (int) mouseY, filterLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT)) {
			Filter[] filters = Filter.values();
			int step = mouseButton == 1 ? -1 : 1;
			filter = filters[(filter.ordinal() + step + filters.length) % filters.length];
			scroll = 0;
			RenderUtils.playPressSound();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!Utils.isWithinRect((int) mouseX, (int) mouseY, gridLeft(), gridTop(), gridWidth(), gridHeight())) return false;
		scroll = (int) Math.round(scroll - scrollY * SLOT);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return searchField != null && searchField.isFocused() && searchField.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return searchField != null && searchField.isFocused() && searchField.charTyped(event);
	}

	// ---- drawing ----

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInfo == null) return;
		if (dataFor != profileInfo) {
			shards = load(profileInfo);
			dataFor = profileInfo;
		}
		Font font = instance.getFont();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (searchField == null) {
			searchField = new EditBox(font, controlsLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT, Component.literal("Search"));
			searchField.setMaxLength(64);
			searchField.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
		}
		searchField.setX(controlsLeft());
		searchField.setY(guiTop + CONTROLS_TOP);
		searchField.extractWidgetRenderState(graphics, mouseX, mouseY, partialTicks);
		String query = searchField.getValue().trim().toLowerCase(Locale.ROOT);
		if (!query.equals(lastQuery)) {
			lastQuery = query;
			scroll = 0;
		}
		drawFilterButton(graphics, font, mouseX, mouseY);

		// Group the shown shards by rarity, unknown ones last, and lay them out in centred rows.
		Map<Integer, List<Shard>> groups = new LinkedHashMap<>();
		for (int rarity = 0; rarity < RARITIES.length; rarity++) groups.put(rarity, new ArrayList<>());
		groups.put(-1, new ArrayList<>());
		for (Shard shard : shards) {
			if (matches(shard, query) && shows(shard)) groups.get(shard.rarity()).add(shard);
		}

		int left = gridLeft();
		int top = gridTop();
		int columns = columns();
		int contentHeight = -GROUP_GAP;
		for (List<Shard> group : groups.values()) {
			if (!group.isEmpty()) contentHeight += (group.size() + columns - 1) / columns * SLOT + GROUP_GAP;
		}
		if (contentHeight <= 0) {
			RenderUtils.drawStringCentered(graphics, "§cNo Attribute matches the input!", font,
				left + gridWidth() / 2f, top + gridHeight() / 2f, true, 0xFFFFFF);
			return;
		}
		int maxScroll = Math.max(0, contentHeight - gridHeight());
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		boolean hoveringGrid = Utils.isWithinRect(mouseX, mouseY, left, top, gridWidth(), gridHeight());

		// Short lists sit in the middle of the grid area, like SkyBlockPv's.
		int y = top - scroll + Math.max(0, (gridHeight() - contentHeight) / 2);
		graphics.enableScissor(left, top, left + gridWidth(), top + gridHeight());
		for (List<Shard> group : groups.values()) {
			if (group.isEmpty()) continue;
			for (int start = 0; start < group.size(); start += columns) {
				int count = Math.min(columns, group.size() - start);
				int x = left + (gridWidth() - count * SLOT) / 2;
				if (y + SLOT >= top && y <= top + gridHeight()) {
					for (int i = 0; i < count; i++) {
						Shard shard = group.get(start + i);
						drawShard(graphics, shard, x + i * SLOT, y);
						if (hoveringGrid && Utils.isWithinRect(mouseX, mouseY, x + i * SLOT, y, SLOT, SLOT)) {
							instance.tooltipToDisplay = tooltip(shard);
						}
					}
				}
				y += SLOT;
			}
			y += GROUP_GAP;
		}
		graphics.disableScissor();

		if (maxScroll > 0) {
			int barX = left + gridWidth() + 3;
			int barHeight = Math.max(10, gridHeight() * gridHeight() / contentHeight);
			int barY = top + (gridHeight() - barHeight) * scroll / maxScroll;
			graphics.fill(barX, top, barX + 2, top + gridHeight(), 0x40000000);
			graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFFAAAAAA);
		}
	}

	private void drawFilterButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		int x = filterLeft();
		int y = GuiProfileViewer.getGuiTop() + CONTROLS_TOP;
		boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, y, CONTROL_WIDTH, CONTROL_HEIGHT);
		graphics.fill(x, y, x + CONTROL_WIDTH, y + CONTROL_HEIGHT, hovered ? 0xFFAAAAAA : 0xFF555555);
		graphics.fill(x + 1, y + 1, x + CONTROL_WIDTH - 1, y + CONTROL_HEIGHT - 1, hovered ? 0xFF3A3A3A : 0xFF2A2A2A);
		RenderUtils.drawStringCentered(graphics, "Filter", font, x + CONTROL_WIDTH / 2f, y + CONTROL_HEIGHT / 2f, true, 0xFFFFFF);
		if (hovered) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add("§7Click to cycle through the filters!");
			tooltip.add("§8Left Click / Right Click");
			tooltip.add("");
			for (Filter each : Filter.values()) {
				tooltip.add(each == filter ? "§7> §a" + each.display : " §c" + each.display);
			}
			instance.tooltipToDisplay = tooltip;
		}
	}

	private void drawShard(GuiGraphicsExtractor graphics, Shard shard, int x, int y) {
		MiningUi.drawSlotBackground(graphics, x, y);
		graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x50000000 | colour(shard));
		ItemStack stack;
		if (shard.max() != 0 && shard.syphoned() <= 0 && filter != Filter.LOCKED) {
			stack = stackCache.computeIfAbsent("gray_dye", key -> new ItemStack(VanillaItems.GRAY_DYE));
		} else {
			stack = shardStack(shard);
		}
		RenderUtils.drawItemStack(graphics, stack, x + 1, y + 1);
	}

	/** The rarity colour, dimmed while the attribute is locked or still being syphoned. */
	private static int colour(Shard shard) {
		int base = shard.rarity() < 0 ? 0xFFFFFF : RARITY_COLOURS[shard.rarity()];
		if (!shard.unlocked()) return scale(base, 0.65f);
		if (shard.max() <= 0 || Math.abs(shard.syphoned()) >= shard.max()) return base;
		return scale(base, 0.75f + 0.25f * shard.syphoned() / shard.max());
	}

	private static int scale(int rgb, float factor) {
		int r = (int) ((rgb >> 16 & 0xFF) * factor);
		int g = (int) ((rgb >> 8 & 0xFF) * factor);
		int b = (int) ((rgb & 0xFF) * factor);
		return r << 16 | g << 8 | b;
	}

	private ItemStack shardStack(Shard shard) {
		if (shard.internalName() == null) return stackCache.computeIfAbsent("barrier", key -> new ItemStack(Items.BARRIER));
		return stackCache.computeIfAbsent(shard.internalName(), key -> {
			JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(key);
			ItemStack stack = json == null ? null : NotEnoughUpdates.INSTANCE.manager.jsonToStack(json);
			return stack == null || stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack;
		});
	}

	private boolean shows(Shard shard) {
		return switch (filter) {
			case ALL -> true;
			case MAXED -> shard.max() <= shard.syphoned();
			case NOT_MAXED -> shard.max() > shard.syphoned();
			case UNLOCKED -> shard.unlocked();
			case LOCKED -> !shard.unlocked();
		};
	}

	private static boolean matches(Shard shard, String query) {
		if (query.isEmpty() || shard.internalName() == null) return true;
		List<String> fields = new ArrayList<>(List.of(shard.name(), shard.shardName(), shard.shardId(), shard.id(), shard.internalName()));
		if (shard.rarity() >= 0) fields.add(RARITIES[shard.rarity()]);
		fields.addAll(lore(shard));
		for (String field : fields) {
			if (Utils.cleanColour(field).toLowerCase(Locale.ROOT).contains(query)) return true;
		}
		return false;
	}

	private static List<String> lore(Shard shard) {
		List<String> lines = new ArrayList<>();
		if (shard.internalName() == null) return lines;
		JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(shard.internalName());
		if (json != null && json.get("lore") instanceof JsonArray lore) {
			for (JsonElement line : lore) lines.add(line.getAsString());
		}
		return lines;
	}

	// ---- tooltip ----

	private static List<String> tooltip(Shard shard) {
		List<String> tooltip = new ArrayList<>();
		if (shard.internalName() == null) {
			tooltip.add("§fUnknown shard!");
		} else {
			tooltip.add(RARITY_CODES[shard.rarity()] + shard.name() + " (" + shard.shardName() + ")");
			// The item's description, without its Hunting Box instructions, then its rarity line.
			List<String> lore = lore(shard);
			int end = 0;
			while (end < lore.size() && !Utils.cleanColour(lore.get(end)).startsWith("You can Syphon this shard")) end++;
			int descriptionEnd = end;
			while (descriptionEnd > 0 && Utils.cleanColour(lore.get(descriptionEnd - 1)).isBlank()) descriptionEnd--;
			tooltip.addAll(lore.subList(0, descriptionEnd));
			for (int i = end; i < lore.size(); i++) {
				if (Utils.cleanColour(lore.get(i)).contains(" SHARD")) {
					tooltip.add(lore.get(i));
					break;
				}
			}
		}
		tooltip.add("");
		tooltip.add("§7Owned: §e" + shard.owned());
		if (shard.owned() != 0) {
			tooltip.add("§7Last Captured At: " + (shard.capturedAt() == 0 ? "§cUnknown"
				: "§a" + DATE.format(Instant.ofEpochMilli(shard.capturedAt()).atZone(ZoneId.systemDefault()))));
		}

		int max = shard.max();
		int syphoned = shard.syphoned();
		if (shard.rarity() < 0) {
			tooltip.add("§7Syphoned: §e" + syphoned + "§7/§c?");
		} else if (max == 0) {
			tooltip.add("§cCan't be syphoned!");
		} else {
			int level = level(shard.rarity(), syphoned);
			tooltip.add("§7Syphoned: " + (syphoned == 0 ? "§c" : syphoned >= max ? "§a" : "§e") + syphoned + "§7/§a" + max);
			tooltip.add("§7Level: " + (level == 0 ? "§c" : level == 10 ? "§a" : "§e") + level + "§7/§a10");
			int used = Math.min(level * 4, 40);
			tooltip.add("§a§m" + " ".repeat(used) + "§7§m" + " ".repeat(40 - used));
		}
		return tooltip;
	}

	// ---- data ----

	private static JsonArray levelling(int rarity) {
		JsonElement table = Utils.getElement(Constants.ATTRIBUTE_SHARDS, "attribute_levelling." + RARITIES[rarity]);
		return table instanceof JsonArray array ? array : new JsonArray();
	}

	/** Shards syphoned to reach level 10. */
	private static int max(int rarity) {
		int total = 0;
		for (JsonElement step : levelling(rarity)) total += step.getAsInt();
		return total;
	}

	/** Levels reached with {@code syphoned} shards; the table lists the shards each level costs. */
	private static int level(int rarity, int syphoned) {
		int level = 0;
		int total = 0;
		for (JsonElement step : levelling(rarity)) {
			total += step.getAsInt();
			if (syphoned < total) break;
			level++;
		}
		return level;
	}

	/** The sum of the player's attribute levels (each shard's level out of 10) and the sum if all were maxed. */
	public static int[] totalLevels(JsonObject profileInfo) {
		int levels = 0;
		int max = 0;
		for (Shard shard : load(profileInfo)) {
			if (shard.max() <= 0) continue;
			levels += level(shard.rarity(), shard.syphoned());
			max += 10;
		}
		return new int[]{levels, max};
	}

	private static List<Shard> load(JsonObject profileInfo) {
		Map<String, Integer> stacks = new HashMap<>();
		if (Utils.getElement(profileInfo, "attributes.stacks") instanceof JsonObject object) {
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				stacks.put(entry.getKey().toLowerCase(Locale.ROOT), (int) Utils.getElementAsFloat(entry.getValue(), 0));
			}
		}
		Map<String, JsonObject> owned = new HashMap<>();
		if (Utils.getElement(profileInfo, "shards.owned") instanceof JsonArray array) {
			for (JsonElement element : array) {
				if (element instanceof JsonObject shard && shard.has("type")) {
					owned.put(shard.get("type").getAsString().toUpperCase(Locale.ROOT), shard);
				}
			}
		}

		Set<String> unconsumable = new HashSet<>();
		if (Utils.getElement(Constants.ATTRIBUTE_SHARDS, "unconsumable_attributes") instanceof JsonArray array) {
			for (JsonElement element : array) unconsumable.add(element.getAsString());
		}

		List<Shard> shards = new ArrayList<>();
		Set<String> known = new HashSet<>();
		if (Utils.getElement(Constants.ATTRIBUTE_SHARDS, "attributes") instanceof JsonArray attributes) {
			for (JsonElement element : attributes) {
				if (!(element instanceof JsonObject repo)) continue;
				String internalName = Utils.getElementAsString(repo.get("internalName"), "");
				String bazaarName = Utils.getElementAsString(repo.get("bazaarName"), "");
				String id = internalName.replaceFirst("^ATTRIBUTE_SHARD_", "").replaceFirst(";.*$", "").toLowerCase(Locale.ROOT);
				int rarity = List.of(RARITIES).indexOf(Utils.getElementAsString(repo.get("rarity"), ""));
				known.add(id);
				// Shards that can't be syphoned (the Chameleon) have no attribute to show.
				if (unconsumable.contains(bazaarName)) continue;

				JsonObject ownedShard = owned.get(bazaarName.replaceFirst("^SHARD_", ""));
				Integer syphoned = stacks.get(id);
				shards.add(new Shard(id, internalName,
					Utils.getElementAsString(repo.get("abilityName"), id),
					Utils.getElementAsString(repo.get("displayName"), id),
					Utils.getElementAsString(repo.get("shardId"), ""),
					rarity, unconsumable.contains(bazaarName),
					ownedShard != null || syphoned != null,
					ownedShard == null ? 0 : (int) Utils.getElementAsFloat(ownedShard.get("amount_owned"), 0),
					ownedShard == null || !ownedShard.has("captured") ? 0 : ownedShard.get("captured").getAsLong(),
					syphoned == null ? 0 : syphoned));
			}
		}
		// Attributes the player has that the repo doesn't list yet.
		for (Map.Entry<String, Integer> entry : stacks.entrySet()) {
			if (!known.contains(entry.getKey())) {
				shards.add(new Shard(entry.getKey(), null, entry.getKey(), entry.getKey(), "", -1, false, true, 0, 0, entry.getValue()));
			}
		}
		return shards;
	}
}
