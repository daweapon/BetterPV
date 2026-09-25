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
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.mining.MiningUi;
import io.github.moulberry.notenoughupdates.util.PetData;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Loadouts tab: each saved loadout's armor, equipment, pet and HOTM/HOTF tree slots. Follows SkyBlockPv's
 * {@code LoadoutTab}; the tree layouts are in {@code skill_trees.json}.
 */
public class LoadoutsPage implements GuiProfileViewerPage {

	private static final int SLOT = 18;
	private static final int PAD = 4;
	private static final int TITLE = MiningUi.PANEL_TITLE;
	/** The game offers up to 27 loadouts (3 per row, 9 rows); the ones beyond the player's are "locked". */
	private static final int SELECTOR_SLOTS = 27;
	private static final int SELECTOR_COLUMNS = 3;
	private static final int TREE_COLUMNS = 7;
	/** Tree cells are 16px perk backgrounds 1px apart, so the 10-tier HOTM tree fits the window's height. */
	private static final int CELL = 17;
	private static final int GAP = 4;

	private static final Identifier[] ARMOR_SLOT_SPRITES = {
		Identifier.withDefaultNamespace("container/slot/helmet"),
		Identifier.withDefaultNamespace("container/slot/chestplate"),
		Identifier.withDefaultNamespace("container/slot/leggings"),
		Identifier.withDefaultNamespace("container/slot/boots"),
	};

	private record Saved(
		int id, String name, Integer armorSet, Integer equipmentSet, Integer miningSlot, Integer foragingSlot,
		String powerStone, Integer tuning, String pet
	) {
		boolean isEmpty() {
			return armorSet == null && equipmentSet == null && miningSlot == null && foragingSlot == null &&
				powerStone == null && tuning == null && pet == null && name.equals("Loadout " + id);
		}
	}

	private final GuiProfileViewer instance;
	private final Map<JsonObject, ItemStack> stackCache = new IdentityHashMap<>();
	private final Map<String, ItemStack> iconCache = new HashMap<>();
	private ItemStack hotmSkull;
	private ItemStack hotfSkull;

	private JsonObject dataFor;
	private List<Saved> saved = List.of();
	private final Map<Integer, JsonObject[]> armorSets = new HashMap<>();
	private final Map<Integer, JsonObject[]> equipmentSets = new HashMap<>();
	private int equippedArmor = -1;
	private int equippedEquipment = -1;
	private JsonObject[] wornArmor = new JsonObject[4];
	private JsonObject[] wornEquipment = new JsonObject[4];
	private int selected = -1;

	public LoadoutsPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		dataFor = null;
		selected = -1;
		stackCache.clear();
	}

	// ---- layout ----

	/** A section's title, centred over it. */
	private static void drawTitle(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, String title) {
		RenderUtils.drawStringCentered(graphics, title, font, x + width / 2f, y + TITLE / 2f, true, 0xFFFFFF);
	}


	private static int selectorWidth() {
		return SELECTOR_COLUMNS * SLOT + PAD * 2;
	}

	private static int treeWidth() {
		return TREE_COLUMNS * CELL - 1 + PAD * 2;
	}

	private static int equipmentWidth() {
		// Wide enough for its "Equipment" title.
		return 2 * SLOT + 22;
	}

	/** Panels are centred vertically in the window. */
	private int centredTop(int height) {
		return GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;
	}

	private static int selectorHeight() {
		return TITLE + PAD * 2 + SELECTOR_SLOTS / SELECTOR_COLUMNS * SLOT;
	}

	private static int equipmentHeight() {
		return TITLE + PAD + 4 * SLOT + PAD + SLOT + PAD;
	}

	private static int treeHeight(SkillTreeView.Tree tree) {
		return TITLE + PAD * 2 + tree.rows() * CELL - 1;
	}

	private int left() {
		int total = selectorWidth() + treeWidth() * 2 + equipmentWidth() + GAP * 3;
		return GuiProfileViewer.getGuiLeft() + (instance.sizeX - total) / 2;
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int index = selectorIndexAt((int) mouseX, (int) mouseY);
		if (index < 0 || index >= saved.size()) return false;
		if (selected != saved.get(index).id()) RenderUtils.playPressSound();
		selected = saved.get(index).id();
		return true;
	}

	private int selectorIndexAt(int mouseX, int mouseY) {
		int x = left() + PAD;
		int y = centredTop(selectorHeight()) + TITLE + PAD;
		if (!Utils.isWithinRect(mouseX, mouseY, x, y, SELECTOR_COLUMNS * SLOT, SELECTOR_SLOTS / SELECTOR_COLUMNS * SLOT)) return -1;
		return (mouseY - y) / SLOT * SELECTOR_COLUMNS + (mouseX - x) / SLOT;
	}

	// ---- drawing ----

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject profileInfo = profile.getProfileInformation(profileId);
		JsonObject inventoryInfo = profile.getInventoryInfo(profileId);
		if (profileInfo == null || inventoryInfo == null) return;
		if (dataFor != inventoryInfo) {
			load(profileInfo, inventoryInfo);
			dataFor = inventoryInfo;
		}
		SkillTreeView.Tree miningTree = SkillTreeView.mining();
		SkillTreeView.Tree foragingTree = SkillTreeView.foraging();

		Font font = instance.getFont();
		int x = left();

		drawSelector(graphics, font, x, centredTop(selectorHeight()), mouseX, mouseY);
		x += selectorWidth() + GAP;

		Saved current = null;
		for (Saved each : saved) if (each.id() == selected) current = each;
		if (current == null) {
			RenderUtils.drawStringCentered(graphics, "§7This profile has no loadouts.", font,
				GuiProfileViewer.getGuiLeft() + instance.sizeX / 2f + selectorWidth() / 2f, GuiProfileViewer.getGuiTop() + instance.sizeY / 2f,
				true, 0xFFFFFF);
			return;
		}

		int miningSlot = current.miningSlot() != null ? current.miningSlot() : SkillTreeView.selectedSlot(profileInfo, "mining");
		int foragingSlot = current.foragingSlot() != null ? current.foragingSlot() : SkillTreeView.selectedSlot(profileInfo, "foraging");
		if (miningTree != null) {
			drawTree(graphics, font, miningTree, profileInfo, miningSlot, x, centredTop(treeHeight(miningTree)), mouseX, mouseY);
		}
		x += treeWidth() + GAP;
		drawEquipment(graphics, font, profileInfo, current, x, centredTop(equipmentHeight()), mouseX, mouseY);
		x += equipmentWidth() + GAP;
		if (foragingTree != null) {
			drawTree(graphics, font, foragingTree, profileInfo, foragingSlot, x, centredTop(treeHeight(foragingTree)), mouseX, mouseY);
		}
	}

	private void drawSelector(GuiGraphicsExtractor graphics, Font font, int x, int top, int mouseX, int mouseY) {
		drawTitle(graphics, font, x, top, selectorWidth(), "Loadouts");
		int gridX = x + PAD;
		int gridY = top + TITLE + PAD;
		for (int i = 0; i < SELECTOR_SLOTS; i++) {
			int slotX = gridX + i % SELECTOR_COLUMNS * SLOT;
			int slotY = gridY + i / SELECTOR_COLUMNS * SLOT;
			Saved entry = i < saved.size() ? saved.get(i) : null;
			MiningUi.drawSlotBackground(graphics, slotX, slotY);
			RenderUtils.drawItemStack(graphics, selectorIcon(entry), slotX + 1, slotY + 1);
			if (entry != null && entry.id() == selected) {
				int colour = 0xFFFFAA00;
				graphics.fill(slotX, slotY, slotX + SLOT, slotY + 1, colour);
				graphics.fill(slotX, slotY + SLOT - 1, slotX + SLOT, slotY + SLOT, colour);
				graphics.fill(slotX, slotY, slotX + 1, slotY + SLOT, colour);
				graphics.fill(slotX + SLOT - 1, slotY, slotX + SLOT, slotY + SLOT, colour);
			}
			if (Utils.isWithinRect(mouseX, mouseY, slotX, slotY, SLOT, SLOT)) {
				instance.tooltipToDisplay = selectorTooltip(entry, i);
			}
		}
	}

	private List<String> selectorTooltip(Saved entry, int index) {
		List<String> tooltip = new ArrayList<>();
		if (entry == null) {
			tooltip.add("§fTemplate " + (index + 1) + " §c(Locked)");
			return tooltip;
		}
		if (entry.isEmpty()) tooltip.add("§fTemplate " + entry.id() + " §c(Empty)");
		else tooltip.add("§f" + entry.name());
		tooltip.add("§fId - §b" + entry.id());
		addSetLines(tooltip, "Armor Set", entry.armorSet(), setItems(entry.armorSet(), armorSets, equippedArmor, wornArmor));
		addSetLines(tooltip, "Equipment Set", entry.equipmentSet(),
			setItems(entry.equipmentSet(), equipmentSets, equippedEquipment, wornEquipment));
		tooltip.add("§fHotm Preset - " + (entry.miningSlot() != null ? "§b" + entry.miningSlot() : "§cNone"));
		tooltip.add("§fHotf Preset - " + (entry.foragingSlot() != null ? "§b" + entry.foragingSlot() : "§cNone"));
		tooltip.add("§fPower Stone - " + (entry.powerStone() != null ? "§b" + titleCase(entry.powerStone()) : "§cNone"));
		return tooltip;
	}

	private void addSetLines(List<String> tooltip, String label, Integer id, JsonObject[] items) {
		tooltip.add(label + " - " + (id != null ? "§b" + id : "§cNone"));
		if (id == null || items == null) return;
		for (JsonObject item : items) {
			tooltip.add("§f - " + (item == null ? "§cNone" : Utils.getElementAsString(item.get("displayname"), "Unknown Item")));
		}
		tooltip.add("");
	}

	private ItemStack selectorIcon(Saved entry) {
		if (entry == null) return new ItemStack(VanillaItems.RED_DYE);
		ItemStack armor = firstItem(setItems(entry.armorSet(), armorSets, equippedArmor, wornArmor));
		if (armor != null) return armor;
		ItemStack equipment = firstItem(setItems(entry.equipmentSet(), equipmentSets, equippedEquipment, wornEquipment));
		if (equipment != null) return equipment;
		if (entry.miningSlot() != null) return hotmSkull();
		if (entry.foragingSlot() != null) return hotfSkull();
		JsonObject pet = findPet(entry.pet());
		if (pet != null) {
			ItemStack stack = petIcon(pet);
			if (stack != null) return stack;
		}
		return new ItemStack(entry.isEmpty() ? VanillaItems.GRAY_DYE : VanillaItems.GREEN_DYE);
	}

	private ItemStack firstItem(JsonObject[] items) {
		if (items == null) return null;
		for (JsonObject item : items) if (item != null) return stack(item);
		return null;
	}

	private ItemStack hotmSkull() {
		if (hotmSkull == null) hotmSkull = SkillTreeView.hotmSkull();
		return hotmSkull;
	}

	private ItemStack hotfSkull() {
		if (hotfSkull == null) hotfSkull = SkillTreeView.hotfSkull();
		return hotfSkull;
	}

	private ItemStack stack(JsonObject item) {
		return stackCache.computeIfAbsent(item, json -> NotEnoughUpdates.INSTANCE.manager.jsonToStack(json, false));
	}

	/** The four pieces of a saved set; the equipped set is what the player is wearing now. */
	private static JsonObject[] setItems(Integer id, Map<Integer, JsonObject[]> sets, int equipped, JsonObject[] worn) {
		if (id == null) return null;
		if (id == equipped) return worn;
		return sets.get(id);
	}

	// ---- equipment panel ----

	private void drawEquipment(
		GuiGraphicsExtractor graphics, Font font, JsonObject profileInfo, Saved current, int x, int top, int mouseX, int mouseY
	) {
		drawTitle(graphics, font, x, top, equipmentWidth(), "Equipment");
		JsonObject[] armor = setItems(current.armorSet(), armorSets, equippedArmor, wornArmor);
		JsonObject[] equipment = setItems(current.equipmentSet(), equipmentSets, equippedEquipment, wornEquipment);
		int gridX = x + (equipmentWidth() - 2 * SLOT) / 2;
		int gridY = top + TITLE + PAD;
		for (int i = 0; i < 4; i++) {
			drawItemSlot(graphics, font, armor == null ? null : armor[i], ARMOR_SLOT_SPRITES[i], gridX, gridY + i * SLOT, mouseX, mouseY);
			drawItemSlot(graphics, font, equipment == null ? null : equipment[i], null, gridX + SLOT, gridY + i * SLOT, mouseX, mouseY);
		}

		int petY = gridY + 4 * SLOT + PAD;
		int petX = x + (equipmentWidth() - SLOT) / 2;
		MiningUi.drawSlotBackground(graphics, petX, petY);
		JsonObject pet = findPet(current.pet());
		if (pet == null) return;
		ItemStack icon = petIcon(pet);
		if (icon == null) return;
		RenderUtils.drawItemStack(graphics, icon, petX + 1, petY + 1);
		int level = petLevel(pet);
		String text = String.valueOf(level);
		RenderUtils.text(graphics, font, text, petX + 17 - font.width(text), petY + 10, 0xFFFFFF, true);
		if (Utils.isWithinRect(mouseX, mouseY, petX, petY, SLOT, SLOT)) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add(icon.getHoverName().getString().replace("[Lvl {LVL}] ", ""));
			tooltip.add("§7Level: §f" + level);
			String held = Utils.getElementAsString(pet.get("heldItem"), "");
			if (!held.isEmpty()) tooltip.add("§7Held item: §f" + titleCase(held));
			instance.tooltipToDisplay = tooltip;
		}
	}

	private void drawItemSlot(
		GuiGraphicsExtractor graphics, Font font, JsonObject item, Identifier emptySprite, int x, int y, int mouseX, int mouseY
	) {
		MiningUi.drawSlotBackground(graphics, x, y);
		if (item == null) {
			if (emptySprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, emptySprite, x + 1, y + 1, 16, 16);
			return;
		}
		RenderUtils.drawItemStack(graphics, stack(item), x + 1, y + 1);
		if (Utils.isWithinRect(mouseX, mouseY, x, y, SLOT, SLOT)) {
			List<String> tooltip = new ArrayList<>();
			String name = Utils.getElementAsString(item.get("displayname"), "");
			tooltip.add(name.isEmpty() ? "Unknown Item" : name);
			if (item.get("lore") instanceof JsonArray lore) {
				for (JsonElement line : lore) tooltip.add(line.getAsString());
			}
			instance.tooltipToDisplay = tooltip;
		}
	}

	private JsonObject findPet(String uuid) {
		if (uuid == null) return null;
		JsonObject petsInfo = GuiProfileViewer.getProfile().getPetsInfo(GuiProfileViewer.getProfileId());
		if (petsInfo == null || !(petsInfo.get("pets") instanceof JsonArray pets)) return null;
		for (JsonElement element : pets) {
			if (element instanceof JsonObject pet && uuid.equals(Utils.getElementAsString(pet.get("uniqueId"), null))) return pet;
		}
		return null;
	}

	private ItemStack petIcon(JsonObject pet) {
		String type = Utils.getElementAsString(pet.get("type"), "");
		String tier = Utils.getElementAsString(pet.get("tier"), "COMMON");
		String id;
		try {
			id = type.toUpperCase(Locale.ROOT) + ";" + PetData.Rarity.valueOf(tier).petId;
		} catch (IllegalArgumentException e) {
			return null;
		}
		ItemStack stack = iconCache.computeIfAbsent(id, key -> {
			JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(key);
			return json == null ? ItemStack.EMPTY : NotEnoughUpdates.INSTANCE.manager.jsonToStack(json);
		});
		return stack.isEmpty() ? null : stack;
	}

	private static int petLevel(JsonObject pet) {
		try {
			return (int) GuiProfileViewer.getPetLevel(Utils.getElementAsString(pet.get("type"), ""),
				Utils.getElementAsString(pet.get("tier"), "COMMON"), Utils.getElementAsFloat(pet.get("exp"), 0)).level;
		} catch (RuntimeException e) {
			return 1;
		}
	}

	// ---- skill trees ----

	private void drawTree(
		GuiGraphicsExtractor graphics, Font font, SkillTreeView.Tree tree, JsonObject profileInfo, int slot, int x, int top, int mouseX, int mouseY
	) {
		drawTitle(graphics, font, x, top, treeWidth(), tree.title());
		List<String> tooltip = SkillTreeView.drawNodes(graphics, font, tree, profileInfo, slot, x + PAD, top + TITLE + PAD, CELL, mouseX, mouseY);
		if (tooltip != null) instance.tooltipToDisplay = tooltip;
	}

	// ---- data ----

	private void load(JsonObject profileInfo, JsonObject inventoryInfo) {
		saved = new ArrayList<>();
		if (Utils.getElement(profileInfo, "loadout.loadouts") instanceof JsonObject loadouts) {
			for (JsonElement element : loadouts.asMap().values()) {
				if (!(element instanceof JsonObject json) || !json.has("id")) continue;
				int id = json.get("id").getAsInt();
				saved.add(new Saved(id,
					Utils.getElementAsString(json.get("name"), "Loadout " + id),
					intOrNull(json, "armor_set_id"), intOrNull(json, "equipment_set_id"),
					intOrNull(json, "mining_core_selected_slot"), intOrNull(json, "foraging_core_selected_slot"),
					Utils.getElementAsString(json.get("power_stone"), null), intOrNull(json, "tuning_points_slot"),
					Utils.getElementAsString(json.get("pet"), null)));
			}
		}
		saved.sort(Comparator.comparingInt(Saved::id));
		if (saved.stream().noneMatch(each -> each.id() == selected)) selected = saved.isEmpty() ? -1 : saved.get(0).id();

		readSets(armorSets, inventoryInfo.get("loadout_armor"), inventoryInfo.get("loadout_armor_ids"));
		readSets(equipmentSets, inventoryInfo.get("loadout_equipment"), inventoryInfo.get("loadout_equipment_ids"));
		equippedArmor = (int) Utils.getElementAsFloat(Utils.getElement(profileInfo, "loadout.armor.equipped_set"), -1);
		equippedEquipment = (int) Utils.getElementAsFloat(Utils.getElement(profileInfo, "loadout.equipment.equipped_set"), -1);

		// inv_armor lists boots first; the sets list the helmet first.
		JsonObject[] armor = flat(inventoryInfo.get("inv_armor"));
		wornArmor = new JsonObject[4];
		for (int i = 0; i < 4; i++) wornArmor[i] = armor[3 - i];
		wornEquipment = flat(inventoryInfo.get("equippment_contents"));
	}

	private static Integer intOrNull(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsInt() : null;
	}

	/** Four items per set, in the order of {@code ids}. */
	private static void readSets(Map<Integer, JsonObject[]> into, JsonElement items, JsonElement ids) {
		into.clear();
		if (!(items instanceof JsonArray flat) || !(ids instanceof JsonArray idArray)) return;
		for (int set = 0; set < idArray.size(); set++) {
			JsonObject[] slots = new JsonObject[4];
			for (int i = 0; i < 4 && set * 4 + i < flat.size(); i++) {
				slots[i] = flat.get(set * 4 + i) instanceof JsonObject item ? item : null;
			}
			into.put(idArray.get(set).getAsInt(), slots);
		}
	}

	private static JsonObject[] flat(JsonElement element) {
		JsonObject[] slots = new JsonObject[4];
		if (!(element instanceof JsonArray array)) return slots;
		for (int i = 0; i < 4 && i < array.size(); i++) slots[i] = array.get(i) instanceof JsonObject item ? item : null;
		return slots;
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
