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

package io.github.moulberry.notenoughupdates.profileviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
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
import org.apache.commons.lang3.text.WordUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Museum" tab, following SkyBlockPv's museum screens: one category per museum section, picked with the buttons down
 * the left side of the window, each a grid of its items with a search box and a filter. Donated items show as the
 * player donated them (an armor set shows its first piece; click it to see the whole set), missing ones as gray dye,
 * or lime dye when a better version (a "parent") was donated instead. The Special category shows the player's
 * special donations.
 *
 * <p>The item lists, armor sets and parents come from the NEU repo ({@code constants/museum.json}), which SkyBlockPv
 * builds from Hypixel's item list; the player's museum is {@code v2/skyblock/museum} (see
 * {@link ProfileViewer.Profile#getMuseumInfo}).
 */
public class MuseumPage implements GuiProfileViewerPage {

	private enum Category {
		COMBAT("combat", "Combat"),
		FARMING("farming", "Farming"),
		MINING("mining", "Mining"),
		FISHING("fishing", "Fishing"),
		FORAGING("foraging", "Foraging"),
		HUNTING("hunting", "Hunting"),
		DUNGEONEERING("dungeoneering", "Dungeoneering"),
		SPECIAL("special", "Special");

		final String key;
		final String displayName;

		Category(String key, String displayName) {
			this.key = key;
			this.displayName = displayName;
		}
	}

	private enum Filter {
		ALL("All"),
		DONATED("Donated"),
		DONATED_THROUGH_PARENT("Donated (Through Parent)"),
		MISSING("Missing");

		final String display;

		Filter(String display) {
			this.display = display;
		}
	}

	/**
	 * One museum slot. {@code pieces} lists an armor set's repo items (empty for other items); {@code donated} is
	 * what the player donated (null when missing); {@code parent} is the donated better version, if any.
	 */
	private record Entry(
		String id, List<String> pieces, List<JsonObject> donated, boolean borrowing, long donatedAt, String parent,
		String searchText
	) {
		boolean armor() {
			return !pieces.isEmpty();
		}
	}

	private static final Category[] CATEGORIES = Category.values();
	private static final int CATEGORY_SIZE = 22;
	private static final int CATEGORY_PITCH = 24;
	private static final int SLOT = 18;
	private static final int CONTROL_WIDTH = 100;
	private static final int CONTROL_HEIGHT = 20;
	private static final int CONTROL_GAP = 5;
	private static final int CONTROLS_TOP = 8;
	private static final int GRID_TOP = CONTROLS_TOP + CONTROL_HEIGHT + 6;
	private static final int GRID_MARGIN = 8;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
	private static final String MORT_TEXTURE =
		"eyJ0aW1lc3RhbXAiOjE1Nzg0MDk0MTMxNjksInByb2ZpbGVJZCI6IjQxZDNhYmMyZDc0OTQwMGM5MDkwZDU0MzRkMDM4MzFiIiwicHJvZmlsZU5hbWUiOiJNZWdha2xvb24iLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzliNTY4OTViOTY1OTg5NmFkNjQ3ZjU4NTk5MjM4YWY1MzJkNDZkYjljMWIwMzg5YjhiYmViNzA5OTlkYWIzM2QiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==";

	private final GuiProfileViewer instance;
	private Category category = Category.COMBAT;
	private Filter filter = Filter.ALL;
	private EditBox searchField;
	private String lastQuery = "";
	private int scroll = 0;
	/** The donated armor set whose pieces are open, and where its slot was drawn. */
	private Entry openSet;
	private int openSetX;
	private int openSetY;

	private JsonObject dataFor;
	private final Map<Category, List<Entry>> entries = new HashMap<>();
	private final Map<Category, ItemStack> categoryIcons = new HashMap<>();
	/** Item stacks for the player's donated item JSON, made once each (see PetsPage#sortedPetIcons for why). */
	private final Map<JsonObject, ItemStack> donatedStacks = new IdentityHashMap<>();
	private final Map<String, ItemStack> stacks = new HashMap<>();

	public MuseumPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		dataFor = null;
		entries.clear();
		donatedStacks.clear();
		openSet = null;
		scroll = 0;
	}

	// ---- layout ----

	private boolean showsFilter() {
		return category != Category.SPECIAL;
	}

	private int controlsLeft() {
		int width = showsFilter() ? CONTROL_WIDTH * 2 + CONTROL_GAP : CONTROL_WIDTH;
		return GuiProfileViewer.getGuiLeft() + (instance.sizeX - width) / 2;
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
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (openSet != null) {
			// Any click closes the open set, unless it lands on one of its pieces.
			boolean inside = Utils.isWithinRect(mx, my, setLeft(), setTop(), setWidth(), setHeight());
			openSet = null;
			if (inside) return true;
		}

		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		for (Category each : CATEGORIES) {
			if (Utils.isWithinRect(mx, my, guiLeft - CATEGORY_SIZE - 3, guiTop + 6 + each.ordinal() * CATEGORY_PITCH,
				CATEGORY_SIZE, CATEGORY_SIZE)) {
				if (category != each) {
					RenderUtils.playPressSound();
					category = each;
					scroll = 0;
				}
				return true;
			}
		}

		if (searchField != null) {
			boolean inSearch = Utils.isWithinRect(mx, my, controlsLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT);
			searchField.setFocused(inSearch);
			if (inSearch) {
				if (mouseButton == 1) searchField.setValue("");
				return true;
			}
		}
		if (showsFilter() && Utils.isWithinRect(mx, my, filterLeft(), guiTop + CONTROLS_TOP, CONTROL_WIDTH, CONTROL_HEIGHT)) {
			Filter[] filters = Filter.values();
			int step = mouseButton == 1 ? -1 : 1;
			filter = filters[(filter.ordinal() + step + filters.length) % filters.length];
			scroll = 0;
			RenderUtils.playPressSound();
			return true;
		}

		// Clicking a donated armor set opens its pieces.
		if (mouseButton == 0 && Utils.isWithinRect(mx, my, gridLeft(), gridTop(), gridWidth(), gridHeight())) {
			SlotAt slot = slotAt(mx, my);
			if (slot != null && slot.entry.armor() && slot.entry.donated() != null) {
				openSet = slot.entry;
				openSetX = slot.x;
				openSetY = slot.y;
				RenderUtils.playPressSound();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!Utils.isWithinRect((int) mouseX, (int) mouseY, gridLeft(), gridTop(), gridWidth(), gridHeight())) return false;
		openSet = null;
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
		drawCategoryButtons(graphics, mouseX, mouseY);
		Font font = instance.getFont();
		int centreX = GuiProfileViewer.getGuiLeft() + instance.sizeX / 2;
		int centreY = GuiProfileViewer.getGuiTop() + instance.sizeY / 2;

		JsonObject museum = GuiProfileViewer.getProfile().getMuseumInfo(GuiProfileViewer.getProfileId());
		if (museum == null) {
			RenderUtils.drawStringCentered(graphics, "§eLoading museum...", font, centreX, centreY, true, 0xFFFFFF);
			return;
		}
		if (museum.has("__error")) {
			RenderUtils.drawStringCentered(graphics, "§cCouldn't load the museum!", font, centreX, centreY, true, 0xFFFFFF);
			return;
		}
		if (museum.isEmpty()) {
			RenderUtils.drawStringCentered(graphics, "§cNo museum data!", font, centreX, centreY - 5, true, 0xFFFFFF);
			RenderUtils.drawStringCentered(graphics, "§7The player may have their Museum API disabled.", font,
				centreX, centreY + 5, true, 0xFFFFFF);
			return;
		}
		if (Constants.MUSEUM == null) {
			RenderUtils.drawStringCentered(graphics, "§cThe NEU repo has no museum data!", font, centreX, centreY, true, 0xFFFFFF);
			return;
		}
		if (dataFor != museum) {
			resetCache();
			load(museum);
			dataFor = museum;
		}

		drawControls(graphics, font, mouseX, mouseY, partialTicks);
		List<Entry> shown = shownEntries();
		int left = gridLeft();
		int top = gridTop();
		if (shown.isEmpty()) {
			RenderUtils.drawStringCentered(graphics, category == Category.SPECIAL && lastQuery.isEmpty()
					? "§cNo special items donated!" : "§cNo Item matches the input!", font,
				left + gridWidth() / 2f, top + gridHeight() / 2f, true, 0xFFFFFF);
			return;
		}

		int columns = columns();
		int rows = (shown.size() + columns - 1) / columns;
		int contentHeight = rows * SLOT;
		int maxScroll = Math.max(0, contentHeight - gridHeight());
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		boolean hoveringGrid = openSet == null && Utils.isWithinRect(mouseX, mouseY, left, top, gridWidth(), gridHeight());

		graphics.enableScissor(left, top, left + gridWidth(), top + gridHeight());
		int y = top - scroll + Math.max(0, (gridHeight() - contentHeight) / 2);
		for (int start = 0; start < shown.size(); start += columns, y += SLOT) {
			if (y + SLOT < top || y > top + gridHeight()) continue;
			int count = Math.min(columns, shown.size() - start);
			int x = left + (gridWidth() - count * SLOT) / 2;
			for (int i = 0; i < count; i++) {
				Entry entry = shown.get(start + i);
				MiningUi.drawSlotBackground(graphics, x + i * SLOT, y);
				RenderUtils.drawItemStack(graphics, icon(entry), x + i * SLOT + 1, y + 1);
				if (hoveringGrid && Utils.isWithinRect(mouseX, mouseY, x + i * SLOT, y, SLOT, SLOT)) {
					instance.tooltipToDisplay = tooltip(entry);
				}
			}
		}
		graphics.disableScissor();

		if (maxScroll > 0) {
			int barX = left + gridWidth() + 3;
			int barHeight = Math.max(10, gridHeight() * gridHeight() / contentHeight);
			int barY = top + (gridHeight() - barHeight) * scroll / maxScroll;
			graphics.fill(barX, top, barX + 2, top + gridHeight(), 0x40000000);
			graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFFAAAAAA);
		}

		if (openSet != null) drawOpenSet(graphics, mouseX, mouseY);
	}

	private void drawControls(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float partialTicks) {
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
			openSet = null;
		}
		if (!showsFilter()) return;

		int x = filterLeft();
		int y = guiTop + CONTROLS_TOP;
		boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, y, CONTROL_WIDTH, CONTROL_HEIGHT);
		graphics.fill(x, y, x + CONTROL_WIDTH, y + CONTROL_HEIGHT, hovered ? 0xFFAAAAAA : 0xFF555555);
		graphics.fill(x + 1, y + 1, x + CONTROL_WIDTH - 1, y + CONTROL_HEIGHT - 1, hovered ? 0xFF3A3A3A : 0xFF2A2A2A);
		RenderUtils.drawStringCentered(graphics, "Filter", font, x + CONTROL_WIDTH / 2f, y + CONTROL_HEIGHT / 2f, true, 0xFFFFFF);
		if (hovered && openSet == null) {
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

	private void drawCategoryButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = GuiProfileViewer.getGuiLeft() - CATEGORY_SIZE - 3;
		for (Category each : CATEGORIES) {
			int y = GuiProfileViewer.getGuiTop() + 6 + each.ordinal() * CATEGORY_PITCH;
			boolean selected = each == category;
			boolean hovered = Utils.isWithinRect(mouseX, mouseY, x, y, CATEGORY_SIZE, CATEGORY_SIZE);
			graphics.fill(x, y, x + CATEGORY_SIZE, y + CATEGORY_SIZE, selected ? 0xFFAAAAAA : 0xFF555555);
			graphics.fill(x + 1, y + 1, x + CATEGORY_SIZE - 1, y + CATEGORY_SIZE - 1,
				selected ? 0xFF3A3A3A : hovered ? 0xFF2C2C2C : 0xFF1E1E1E);
			RenderUtils.drawItemStack(graphics, categoryIcon(each), x + 3, y + 3);
			if (hovered) instance.tooltipToDisplay = categoryTooltip(each, selected);
		}
	}

	private List<String> categoryTooltip(Category each, boolean selected) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add((selected ? "§a" : "§7") + each.displayName);
		List<Entry> list = entries.get(each);
		if (list == null) return tooltip;
		if (each == Category.SPECIAL) {
			tooltip.add("§7Donated: §e" + list.size());
			return tooltip;
		}
		int donated = 0;
		int throughParent = 0;
		for (Entry entry : list) {
			if (entry.donated() != null) donated++;
			else if (entry.parent() != null) throughParent++;
		}
		tooltip.add("§7Donated: " + (donated >= list.size() ? "§a" : "§e") + donated + "§7/§a" + list.size());
		if (throughParent > 0) tooltip.add("§7Donated through parent: §a" + throughParent);
		return tooltip;
	}

	private ItemStack categoryIcon(Category each) {
		return categoryIcons.computeIfAbsent(each, key -> switch (key) {
			case COMBAT -> new ItemStack(Items.STONE_SWORD);
			case FARMING -> new ItemStack(Items.GOLDEN_HOE);
			case MINING -> new ItemStack(Items.STONE_PICKAXE);
			case FISHING -> new ItemStack(Items.FISHING_ROD);
			case FORAGING -> new ItemStack(Items.JUNGLE_SAPLING);
			case HUNTING -> new ItemStack(Items.LEAD);
			// SkyBlockPv's dungeoneering icon is Mort's head.
			case DUNGEONEERING -> Utils.createSkull("", "41d3abc2d749400c9090d5434d03831b", MORT_TEXTURE);
			case SPECIAL -> new ItemStack(Items.CAKE);
		});
	}

	// ---- open armor set ----

	/** The open set's pieces in columns of four, like SkyBlockPv's dropdown, over the slot that opened it. */
	private int setColumns() {
		return (openSet.donated().size() + 3) / 4;
	}

	private int setRows() {
		return Math.min(4, openSet.donated().size());
	}

	private int setWidth() {
		return setColumns() * SLOT + 8;
	}

	private int setHeight() {
		return setRows() * SLOT + 8;
	}

	private int setLeft() {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		return Math.max(guiLeft + 4, Math.min(openSetX - 4, guiLeft + instance.sizeX - 4 - setWidth()));
	}

	private int setTop() {
		int guiTop = GuiProfileViewer.getGuiTop();
		return Math.max(guiTop + 4, Math.min(openSetY - 4, guiTop + instance.sizeY - 4 - setHeight()));
	}

	private void drawOpenSet(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(gridLeft(), gridTop(), gridLeft() + gridWidth(), gridTop() + gridHeight(), 0x7F000000);
		int left = setLeft();
		int top = setTop();
		graphics.fill(left, top, left + setWidth(), top + setHeight(), 0xFF555555);
		graphics.fill(left + 1, top + 1, left + setWidth() - 1, top + setHeight() - 1, 0xFFC6C6C6);
		List<JsonObject> pieces = openSet.donated();
		for (int i = 0; i < pieces.size(); i++) {
			int x = left + 4 + i / 4 * SLOT;
			int y = top + 4 + i % 4 * SLOT;
			MiningUi.drawSlotBackground(graphics, x, y);
			JsonObject piece = pieces.get(i);
			if (piece == null) continue;
			RenderUtils.drawItemStack(graphics, donatedStack(piece), x + 1, y + 1);
			if (Utils.isWithinRect(mouseX, mouseY, x, y, SLOT, SLOT)) {
				instance.tooltipToDisplay = donatedTooltip(openSet, piece);
			}
		}
	}

	// ---- entries ----

	private record SlotAt(Entry entry, int x, int y) {}

	/** The shown entry under the mouse, laid out the same way drawPage lays them out. */
	private SlotAt slotAt(int mouseX, int mouseY) {
		List<Entry> shown = shownEntries();
		int columns = columns();
		int contentHeight = (shown.size() + columns - 1) / columns * SLOT;
		int y = gridTop() - scroll + Math.max(0, (gridHeight() - contentHeight) / 2);
		for (int start = 0; start < shown.size(); start += columns, y += SLOT) {
			int count = Math.min(columns, shown.size() - start);
			int x = gridLeft() + (gridWidth() - count * SLOT) / 2;
			for (int i = 0; i < count; i++) {
				if (Utils.isWithinRect(mouseX, mouseY, x + i * SLOT, y, SLOT, SLOT)) {
					return new SlotAt(shown.get(start + i), x + i * SLOT, y);
				}
			}
		}
		return null;
	}

	private List<Entry> shownEntries() {
		List<Entry> shown = new ArrayList<>();
		for (Entry entry : entries.getOrDefault(category, List.of())) {
			if (!lastQuery.isEmpty() && !entry.searchText().contains(lastQuery)) continue;
			boolean donated = entry.donated() != null;
			boolean show = category == Category.SPECIAL || switch (filter) {
				case ALL -> true;
				case DONATED -> donated;
				case DONATED_THROUGH_PARENT -> !donated && entry.parent() != null;
				case MISSING -> !donated && entry.parent() == null;
			};
			if (show) shown.add(entry);
		}
		return shown;
	}

	private ItemStack icon(Entry entry) {
		if (entry.donated() != null) {
			for (JsonObject item : entry.donated()) {
				if (item != null) return donatedStack(item);
			}
		}
		return stack(entry.parent() != null ? "lime_dye" : "gray_dye");
	}

	private ItemStack donatedStack(JsonObject item) {
		return donatedStacks.computeIfAbsent(item, json -> NotEnoughUpdates.INSTANCE.manager.jsonToStack(json, false));
	}

	private ItemStack stack(String key) {
		return stacks.computeIfAbsent(key, k -> switch (k) {
			case "lime_dye" -> new ItemStack(Items.LIME_DYE);
			default -> new ItemStack(Items.GRAY_DYE);
		});
	}

	// ---- tooltips ----

	private List<String> tooltip(Entry entry) {
		if (entry.donated() != null) {
			JsonObject first = null;
			for (JsonObject item : entry.donated()) {
				if (item != null) {
					first = item;
					break;
				}
			}
			if (first == null) return List.of("§7" + name(entry.id()));
			if (!entry.armor()) return donatedTooltip(entry, first);
			List<String> tooltip = new ArrayList<>();
			tooltip.add("§a" + name(entry.id()));
			for (JsonObject piece : entry.donated()) {
				if (piece != null) tooltip.add(" " + Utils.getElementAsString(piece.get("displayname"), "§7Unknown Item"));
			}
			addDonationInfo(tooltip, entry);
			tooltip.add("");
			tooltip.add("§eClick to view the set!");
			return tooltip;
		}

		List<String> tooltip = new ArrayList<>();
		tooltip.add(entry.armor() ? "§c" + name(entry.id()) : repoDisplayName(entry.id()));
		if (entry.parent() != null) {
			tooltip.add("§7Parent donated: " + (isArmorSet(entry.parent()) ? "§a" + name(entry.parent()) : repoDisplayName(entry.parent())));
		} else {
			tooltip.add(entry.armor() ? "§cMissing Armor" : "§7This item has not been donated!");
		}
		for (String piece : entry.pieces()) tooltip.add(" " + repoDisplayName(piece));
		return tooltip;
	}

	private List<String> donatedTooltip(Entry entry, JsonObject item) {
		List<String> tooltip = new ArrayList<>();
		String displayName = Utils.getElementAsString(item.get("displayname"), null);
		tooltip.add(displayName != null && !displayName.isEmpty() ? displayName : "Unknown Item");
		if (item.get("lore") instanceof JsonArray lore) {
			for (JsonElement line : lore) tooltip.add(line.getAsString());
		}
		if (entry.id() != null) addDonationInfo(tooltip, entry);
		return tooltip;
	}

	private static void addDonationInfo(List<String> tooltip, Entry entry) {
		if (entry.donatedAt() <= 0 && !entry.borrowing()) return;
		tooltip.add("");
		if (entry.donatedAt() > 0) {
			tooltip.add("§7Donated: §a" + DATE.format(Instant.ofEpochMilli(entry.donatedAt()).atZone(ZoneId.systemDefault())));
		}
		if (entry.borrowing()) tooltip.add("§eCurrently borrowed from the museum!");
	}

	// ---- data ----

	private static boolean isArmorSet(String id) {
		return Utils.getElement(Constants.MUSEUM, "sets_to_items." + id) instanceof JsonArray;
	}

	private static String repoDisplayName(String id) {
		JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(id);
		String name = json == null ? null : Utils.getElementAsString(json.get("displayname"), null);
		return name != null ? name : "§f" + prettify(id);
	}

	/** An armor set's name, using the repo's set name where the id differs from it (POWER_WITHER is Necron's). */
	private static String name(String id) {
		if (Utils.getElement(Constants.MUSEUM, "set_exceptions") instanceof JsonObject exceptions) {
			for (Map.Entry<String, JsonElement> exception : exceptions.entrySet()) {
				if (id.equals(exception.getValue().getAsString())) return prettify(exception.getKey());
			}
		}
		return isArmorSet(id) ? prettify(id) : Utils.cleanColour(repoDisplayName(id));
	}

	private static String prettify(String id) {
		return WordUtils.capitalizeFully(id.replace('_', ' '));
	}

	private void load(JsonObject museum) {
		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		JsonObject donatedItems = museum.get("items") instanceof JsonObject object ? object : new JsonObject();

		// museum.json maps each item to the lower item it also counts as ("children"); SkyBlockPv calls the
		// better item the lower one's parent.
		Map<String, String> parents = new HashMap<>();
		if (Utils.getElement(Constants.MUSEUM, "children") instanceof JsonObject children) {
			for (Map.Entry<String, JsonElement> child : children.entrySet()) {
				parents.put(child.getValue().getAsString(), child.getKey());
			}
		}

		for (Category each : CATEGORIES) {
			if (each == Category.SPECIAL) continue;
			List<Entry> list = new ArrayList<>();
			if (Utils.getElement(Constants.MUSEUM, "items." + each.key) instanceof JsonArray ids) {
				for (JsonElement idElement : ids) {
					String id = idElement.getAsString();
					List<String> pieces = new ArrayList<>();
					if (Utils.getElement(Constants.MUSEUM, "sets_to_items." + id) instanceof JsonArray set) {
						for (JsonElement piece : set) pieces.add(piece.getAsString());
						pieces.sort(Comparator.comparingInt(MuseumPage::pieceOrder));
					}
					JsonObject donation = donatedItems.get(id) instanceof JsonObject object ? object : null;
					List<JsonObject> donated = donation == null ? null : profile.decodeItems(donation.get("items"));
					String parent = donated == null ? donatedParent(id, parents, donatedItems) : null;

					StringBuilder search = new StringBuilder(id).append('\n').append(name(id));
					for (String piece : pieces.isEmpty() ? List.of(id) : pieces) appendRepoSearch(search, piece);
					list.add(new Entry(id, pieces, donated,
						donation != null && donation.get("borrowing") instanceof JsonPrimitive borrowing && borrowing.isBoolean() && borrowing.getAsBoolean(),
						donation == null ? 0 : time(donation),
						parent, search.toString().toLowerCase(Locale.ROOT)));
				}
			}
			entries.put(each, list);
		}

		// Special items: every item of every special donation, like SkyBlockPv's misc screen.
		List<Entry> special = new ArrayList<>();
		if (museum.get("special") instanceof JsonArray donations) {
			for (JsonElement element : donations) {
				if (!(element instanceof JsonObject donation)) continue;
				long donatedAt = time(donation);
				for (JsonObject item : profile.decodeItems(donation.get("items"))) {
					if (item == null) continue;
					String id = Utils.getElementAsString(item.get("internalname"), "");
					StringBuilder search = new StringBuilder(id).append('\n')
						.append(Utils.cleanColour(Utils.getElementAsString(item.get("displayname"), "")));
					if (item.get("lore") instanceof JsonArray lore) {
						for (JsonElement line : lore) search.append('\n').append(Utils.cleanColour(line.getAsString()));
					}
					special.add(new Entry(id, List.of(), List.of(item), false, donatedAt, null, search.toString().toLowerCase(Locale.ROOT)));
				}
			}
		}
		entries.put(Category.SPECIAL, special);
	}

	private static long time(JsonObject donation) {
		return donation.get("donated_time") instanceof JsonPrimitive time && time.isNumber() ? time.getAsLong() : 0;
	}

	/** The nearest donated item up the chain of better versions of {@code id}, or null. */
	private static String donatedParent(String id, Map<String, String> parents, JsonObject donatedItems) {
		Set<String> seen = new HashSet<>();
		String parent = parents.get(id);
		while (parent != null && seen.add(parent)) {
			if (donatedItems.has(parent)) return parent;
			parent = parents.get(parent);
		}
		return null;
	}

	private static void appendRepoSearch(StringBuilder search, String id) {
		search.append('\n').append(id);
		JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(id);
		if (json == null) return;
		search.append('\n').append(Utils.cleanColour(Utils.getElementAsString(json.get("displayname"), "")));
		if (json.get("lore") instanceof JsonArray lore) {
			for (JsonElement line : lore) search.append('\n').append(Utils.cleanColour(line.getAsString()));
		}
	}

	/** Head pieces first, then chest, legs, feet and equipment, as SkyBlockPv sorts a set's pieces. */
	private static int pieceOrder(String id) {
		String[][] slots = {
			{"HELMET", "HAT", "MASK", "HOOD", "HEAD", "CROWN"}, {"CHESTPLATE", "TUNIC", "JACKET", "SHIRT"},
			{"LEGGINGS", "PANTS", "TROUSERS"}, {"BOOTS", "SHOES", "SLIPPERS"}, {"NECKLACE"}, {"CLOAK"}, {"BELT"},
			{"BRACELET", "GLOVES", "GAUNTLET"}
		};
		for (int slot = 0; slot < slots.length; slot++) {
			for (String word : slots[slot]) {
				if (id.contains(word)) return slot;
			}
		}
		return slots.length;
	}
}
