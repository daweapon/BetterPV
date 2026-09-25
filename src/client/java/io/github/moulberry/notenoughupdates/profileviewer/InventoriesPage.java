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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.profileviewer.info.QuiverInfo;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of the Forge 1.8.9 {@code InventoriesPage} ("Storage" tab: category sidebar, armor/equipment/best-item
 * panels, and the paged inventory grid).
 *
 * <p>TODO(fabric-port) — intentionally simplified vs. the original:
 * <ul>
 *   <li>Item icons for armor, equipment, "best weapon/rod", every inventory grid slot, backpack contents, and
 *   the Green/Purple Candy icons are now rendered via {@code NEUManager#jsonToStack} (ported in a later pass).
 *   They fall back to a plain vanilla item/colored square only when the repo hasn't been synced and the item
 *   JSON isn't available.</li>
 *   <li>The nested backpack/cake-bag NBT byte-array patch-up (previously needed only to make a jsonToStack'd
 *   backpack preview show its own contents) is dropped, since none of the ported pages render nested backpack
 *   previews.</li>
 *   <li>The inventory search box is its own page-owned {@link EditBox} (driven directly by this page's
 *   {@code mouseClicked}/{@code keyPressed}/{@code charTyped}), rather than a screen-level widget shared via
 *   {@code GuiProfileViewer}, to avoid extending the already-ported shell class for a single page's use.</li>
 *   <li>The page-left/page-right arrow icons (vanilla resource-pack-selector texture in the original) are
 *   simplified to plain "&lt;"/"&gt;" text in the same click regions.</li>
 * </ul>
 */
public class InventoriesPage implements GuiProfileViewerPage {

	private static final Identifier pv_invs = Identifier.parse("betterpv:pv_invs.png");
	private static final Identifier pv_elements = Identifier.parse("betterpv:pv_elements.png");
	private static final Identifier CHEST_GUI_TEXTURE = Identifier.parse("textures/gui/container/generic_54.png");
	private static final Pattern FISHING_SPEED_PATTERN = Pattern.compile("^Fishing Speed: \\+(\\d+)");

	private static final LinkedHashMap<String, ItemStack> invNameToDisplayMap = new LinkedHashMap<>();

	static {
		invNameToDisplayMap.put("inv_contents", Utils.createItemStack(Blocks.CHEST, ChatFormatting.GRAY + "Inventory"));
		invNameToDisplayMap.put("ender_chest_contents", Utils.createItemStack(Blocks.ENDER_CHEST, ChatFormatting.GRAY + "Ender Chest"));
		// Prefer the repo's own JUMBO_BACKPACK icon (a textured skull) if the repo has been synced; otherwise fall
		// back to a plain vanilla icon.
		JsonObject jumboBackpackJson = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get("JUMBO_BACKPACK");
		invNameToDisplayMap.put(
			"backpack_contents",
			jumboBackpackJson != null
				? NotEnoughUpdates.INSTANCE.manager.jsonToStack(jumboBackpackJson)
				: Utils.createItemStack(Blocks.BARREL, ChatFormatting.GRAY + "Backpacks")
		);
		invNameToDisplayMap.put("personal_vault_contents", Utils.createItemStack(Blocks.IRON_BARS, ChatFormatting.GRAY + "Personal Vault"));
		invNameToDisplayMap.put("talisman_bag", Utils.createItemStack(Items.GOLDEN_APPLE, ChatFormatting.GRAY + "Accessory Bag"));
		invNameToDisplayMap.put("wardrobe_contents", Utils.createItemStack(Items.LEATHER_CHESTPLATE, ChatFormatting.GRAY + "Wardrobe"));
		invNameToDisplayMap.put("fishing_bag", Utils.createItemStack(Items.COD, ChatFormatting.GRAY + "Fishing Bag"));
		invNameToDisplayMap.put("potion_bag", Utils.createItemStack(Items.POTION, ChatFormatting.GRAY + "Potion Bag"));
		invNameToDisplayMap.put("sacks", Utils.createItemStack(Items.BUNDLE, ChatFormatting.GRAY + "Sacks"));
	}

	private final GuiProfileViewer instance;
	private final HashMap<String, JsonObject[][][]> inventoryItems = new HashMap<>();
	/**
	 * Caches the {@code ItemStack} resolved (via {@code NEUManager#jsonToStack}) for each repo item-JSON object
	 * shown in this page's grids, keyed by object identity of the (stable, only rebuilt on {@link #resetCache})
	 * {@code JsonObject} it came from. Without this, {@link #renderJsonItemSlotNoTooltip} would call
	 * {@code jsonToStack} - which allocates a fresh {@code ItemStack} (and, for skull icons, a fresh
	 * {@code GameProfile}/{@code ResolvableProfile}) on every cache hit via {@code .copy()} - for every one of the
	 * up to 54 grid slots, every single frame. Each of those fresh objects looks like a brand-new item to the
	 * GPU item-icon atlas (keyed off model identity), forcing a full re-bake (a real 3D render pass with its own
	 * lighting/projection setup) of every visible skull icon every frame instead of once. That is the render-loop
	 * regression that caused the freeze/native-crash this page's icons were ported to fix: the Forge 1.8.9
	 * original resolved pet/item icons once into a cached list (see {@code PetsPage#sortedPetsStack}) rather than
	 * re-resolving them from JSON on every draw call.
	 */
	private final Map<JsonObject, ItemStack> resolvedIconCache = new java.util.IdentityHashMap<>();

	private EditBox inventorySearchField;
	private JsonObject[] bestWeapons = null;
	private JsonObject[] bestRods = null;
	private JsonObject[] armorItems = null;
	private JsonObject[] equipmentItems = null;
	private String selectedInventory = "inv_contents";
	/** The sack open in the Sacks view (its name in the repo's sacks.json), or null for the menu of sacks. */
	private String openSack = null;
	private int currentInventoryIndex = 0;
	private int arrowCount = -1;
	private int greenCandyCount = -1;
	private int purpleCandyCount = -1;

	public InventoriesPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		inventoryItems.clear();
		resolvedIconCache.clear();
		openSack = null;
		bestWeapons = null;
		bestRods = null;
		armorItems = null;
		equipmentItems = null;
		currentInventoryIndex = 0;
		arrowCount = -1;
		greenCandyCount = -1;
		purpleCandyCount = -1;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		RenderUtils.drawTexturedRect(graphics, pv_invs, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject inventoryInfo = profile.getInventoryInfo(profileId);
		if (inventoryInfo == null) return;

		if (inventorySearchField == null) {
			inventorySearchField = new EditBox(
				instance.getFont(), guiLeft + 19, guiTop + instance.sizeY - 26 - 20, 88, 20, Component.literal("Search")
			);
			inventorySearchField.setMaxLength(64);
		}

		int invNameIndex = 0;
		for (Map.Entry<String, ItemStack> entry : invNameToDisplayMap.entrySet()) {
			int xIndex = invNameIndex % 3;
			int yIndex = invNameIndex / 3;

			int x = 19 + 34 * xIndex;
			int y = 26 + 34 * yIndex;

			if (entry.getKey().equals(selectedInventory)) {
				RenderUtils.drawTexturedRect(graphics, pv_elements, guiLeft + x - 2, guiTop + y - 2, 20, 20, 20 / 256f, 0, 20 / 256f, 0);
				x++;
				y++;
			} else {
				RenderUtils.drawTexturedRect(graphics, pv_elements, guiLeft + x - 2, guiTop + y - 2, 20, 20, 0, 20 / 256f, 0, 20 / 256f);
			}

			RenderUtils.drawItemStack(graphics, entry.getValue(), guiLeft + x, guiTop + y);
			RenderUtils.text(graphics, instance.getFont(), "" + (invNameIndex + 1), guiLeft + x + 10, guiTop + y + 9, 0xFFFFFF, true);

			if (mouseX >= guiLeft + x && mouseX <= guiLeft + x + 16) {
				if (mouseY >= guiTop + y && mouseY <= guiTop + y + 16) {
					List<String> tooltip = new ArrayList<>();
					tooltip.add(entry.getValue().getHoverName().getString());
					if ("talisman_bag".equals(entry.getKey())) {
						JsonObject profileInfo = profile.getProfileInformation(profileId);
						int magicalPower = Utils.getElementAsInt(
							Utils.getElement(profileInfo, "accessory_bag_storage.highest_magical_power"), -1);
						if (magicalPower < 0) magicalPower = PlayerStats.getMagicalPower(inventoryInfo, profileInfo);
						tooltip.add(
							ChatFormatting.DARK_GRAY + "Magical Power: " +
								(magicalPower == -1
									? ChatFormatting.RED + "Error while calculating!"
									: ChatFormatting.GOLD.toString() + GuiProfileViewer.numberFormat.format(magicalPower))
						);
						String selectedPower = PlayerStats.getSelectedMagicalPower(profile.getProfileInformation(profileId));
						tooltip.add(
							ChatFormatting.DARK_GRAY + "Selected Power: " +
								(selectedPower == null ? ChatFormatting.RED + "None!" : ChatFormatting.GREEN + selectedPower)
						);
					}
					instance.tooltipToDisplay = tooltip;
				}
			}

			invNameIndex++;
		}

		inventorySearchField.extractWidgetRenderState(graphics, mouseX, mouseY, partialTicks);

		if (armorItems == null) {
			armorItems = extractJsonItems(Utils.getElement(inventoryInfo, "inv_armor"));
		}
		for (int i = 0; i < armorItems.length; i++) {
			renderJsonItemSlot(graphics, armorItems[i], guiLeft + 173, guiTop + 67 - 18 * i, mouseX, mouseY);
		}

		if (equipmentItems == null) {
			equipmentItems = extractJsonItems(Utils.getElement(inventoryInfo, "equippment_contents"));
		}
		for (int i = 0; i < equipmentItems.length; i++) {
			renderJsonItemSlot(graphics, equipmentItems[i], guiLeft + 192, guiTop + 13 + 18 * i, mouseX, mouseY);
		}

		JsonObject[][][] inventories = getItemsForInventory(inventoryInfo, selectedInventory);
		if (currentInventoryIndex >= inventories.length) currentInventoryIndex = inventories.length - 1;
		if (currentInventoryIndex < 0) currentInventoryIndex = 0;

		JsonObject[][] inventory = inventories[currentInventoryIndex];

		if (bestWeapons == null) {
			bestWeapons = findBestItems(
				inventoryInfo, 6, new String[] { "inv_contents", "ender_chest_contents" }, new String[] { "SWORD", "BOW" }
			);
		}
		if (bestRods == null) {
			bestRods = findBestItems(
				inventoryInfo, 3, new String[] { "inv_contents", "ender_chest_contents" },
				new String[] { "FISHING ROD", "FISHING WEAPON" }, FISHING_SPEED_PATTERN
			);
		}

		for (int i = 0; i < bestWeapons.length; i++) {
			renderJsonItemSlot(graphics, bestWeapons[i], guiLeft + 143, guiTop + 13 + 18 * i, mouseX, mouseY);
		}
		for (int i = 0; i < bestRods.length; i++) {
			renderJsonItemSlot(graphics, bestRods[i], guiLeft + 143, guiTop + 137 + 18 * i, mouseX, mouseY);
		}

		if (arrowCount == -1) {
			arrowCount = countItemsInInventory("ARROW", inventoryInfo, false, "quiver");
		}
		if (greenCandyCount == -1) {
			greenCandyCount = countItemsInInventory("GREEN_CANDY", inventoryInfo, true, "candy_inventory_contents");
		}
		if (purpleCandyCount == -1) {
			purpleCandyCount = countItemsInInventory("PURPLE_CANDY", inventoryInfo, true, "candy_inventory_contents");
		}

		RenderUtils.drawItemStack(graphics, new ItemStack(Items.ARROW), guiLeft + 173, guiTop + 101);
		RenderUtils.text(
			graphics, instance.getFont(), "" + (arrowCount > 999 ? StringUtils.shortNumberFormat(arrowCount) : "" + arrowCount),
			guiLeft + 190, guiTop + 109, 0xFFFFFF, true
		);
		renderRepoItemIconOrFallback(graphics, "GREEN_CANDY", guiLeft + 173, guiTop + 119, 0xFF55FF55);
		RenderUtils.text(graphics, instance.getFont(), "" + greenCandyCount, guiLeft + 190, guiTop + 127, 0xFFFFFF, true);
		renderRepoItemIconOrFallback(graphics, "PURPLE_CANDY", guiLeft + 173, guiTop + 137, 0xFFAA00AA);
		RenderUtils.text(graphics, instance.getFont(), "" + purpleCandyCount, guiLeft + 190, guiTop + 145, 0xFFFFFF, true);

		if (mouseX > guiLeft + 173 && mouseX < guiLeft + 173 + 16) {
			if (mouseY > guiTop + 101 && mouseY < guiTop + 137 + 16) {
				if (mouseY < guiTop + 101 + 17) {
					QuiverInfo quiverInfo = PlayerStats.getQuiverInfo(inventoryInfo, profile.getProfileInformation(profileId));
					if (quiverInfo == null) {
						instance.tooltipToDisplay = Utils.createList(ChatFormatting.RED + "Error checking Quiver");
					} else {
						instance.tooltipToDisplay = quiverInfo.generateProfileViewerTooltip();
					}
				} else if (mouseY < guiTop + 119 + 17) {
					instance.tooltipToDisplay = Utils.createList(ChatFormatting.GREEN + "Green Candy " + ChatFormatting.GRAY + "x" + greenCandyCount);
				} else {
					instance.tooltipToDisplay = Utils.createList(ChatFormatting.DARK_PURPLE + "Purple Candy " + ChatFormatting.GRAY + "x" + purpleCandyCount);
				}
			}
		}

		if (inventory == null) {
			String strToRender = "Inventory API not enabled!";
			if (selectedInventory.equalsIgnoreCase("personal_vault_contents")) {
				strToRender = "Personal Vault API not enabled!";
			} else if (selectedInventory.equalsIgnoreCase("sacks")) {
				strToRender = "No sacks data!";
			} else if (selectedInventory.equalsIgnoreCase("backpack_contents")) {
				strToRender = "Inventory API not enabled";
				RenderUtils.drawStringCentered(
					graphics, ChatFormatting.RED + "Or has no backpacks!", instance.getFont(), guiLeft + 317, guiTop + 112, true, 0
				);
			}
			RenderUtils.drawStringCentered(graphics, ChatFormatting.RED + strToRender, instance.getFont(), guiLeft + 317, guiTop + 101, true, 0);
			return;
		}

		int inventoryRows = inventory.length;

		int invSizeY = inventoryRows * 18 + 17 + 7;

		int x = guiLeft + 320 - 176 / 2;
		int y = guiTop + 101 - invSizeY / 2;
		int staticSelectorHeight = guiTop + 177;

		RenderUtils.drawTexturedRect(graphics, CHEST_GUI_TEXTURE, x, y, 176, inventoryRows * 18 + 17, 0, 176 / 256f, 0, (inventoryRows * 18 + 17) / 256f);
		RenderUtils.drawTexturedRect(graphics, CHEST_GUI_TEXTURE, x, y + inventoryRows * 18 + 17, 176, 7, 0, 176 / 256f, 215 / 256f, 222 / 256f);

		if (currentInventoryIndex > 0) {
			RenderUtils.text(graphics, instance.getFont(), "<", guiLeft + 320 - 12, staticSelectorHeight, 0xFFFFFF, true);
		}
		if (currentInventoryIndex < inventories.length - 1) {
			RenderUtils.text(graphics, instance.getFont(), ">", guiLeft + 320 + 4, staticSelectorHeight, 0xFFFFFF, true);
		}

		RenderUtils.text(graphics, instance.getFont(), inventoryTitle(), x + 8, y + 6, 4210752, false);
		if (selectedInventory.equals("sacks") && openSack != null) {
			int backWidth = instance.getFont().width(BACK_TEXT);
			boolean overBack = Utils.isWithinRect(mouseX, mouseY, x + 176 - 8 - backWidth, y + 4, backWidth, 11);
			RenderUtils.text(graphics, instance.getFont(), BACK_TEXT, x + 176 - 8 - backWidth, y + 6, overBack ? 0x0000AA : 4210752, false);
		}

		JsonObject stackToRender = null;
		int overlay = 0x64000000;
		String search = inventorySearchField.getValue();
		for (int yIndex = 0; yIndex < inventory.length; yIndex++) {
			if (inventory[yIndex] == null) continue;

			for (int xIndex = 0; xIndex < inventory[yIndex].length; xIndex++) {
				JsonObject item = inventory[yIndex][xIndex];

				renderJsonItemSlotNoTooltip(graphics, item, x + 8 + xIndex * 18, y + 18 + yIndex * 18);
				if (item != null && item.has("sack_count")) {
					drawSackCount(graphics, item.get("sack_count").getAsLong(), x + 8 + xIndex * 18, y + 18 + yIndex * 18);
				}

				if (
					search != null && !search.isEmpty() &&
						(item == null || !doesItemMatchSearch(item, search))
				) {
					graphics.fill(
						x + 8 + xIndex * 18, y + 18 + yIndex * 18, x + 8 + xIndex * 18 + 16, y + 18 + yIndex * 18 + 16, overlay
					);
				}

				if (item == null) continue;

				if (mouseX >= x + 8 + xIndex * 18 && mouseX <= x + 8 + xIndex * 18 + 16) {
					if (mouseY >= y + 18 + yIndex * 18 && mouseY <= y + 18 + yIndex * 18 + 16) {
						stackToRender = item;
					}
				}
			}
		}
		if (stackToRender != null) {
			instance.tooltipToDisplay = buildItemTooltip(stackToRender);
		}
	}

	/** A sack's stored amount in the slot's bottom-right corner, shrunk so "1.2M" fits. */
	private void drawSackCount(GuiGraphicsExtractor graphics, long count, int x, int y) {
		String text = count >= 10_000 ? StringUtils.shortNumberFormat(count) : String.valueOf(count);
		float scale = 0.6f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 17 - instance.getFont().width(text) * scale, y + 17 - instance.getFont().lineHeight * scale);
		graphics.pose().scale(scale, scale);
		RenderUtils.text(graphics, instance.getFont(), text, 0, 0, 0xFFFFFF, true);
		graphics.pose().popMatrix();
	}

	private static final String OTHER_SACK = "Other";
	/** Items newer than the repo's sacks.json, by the sack they go in. */
	private static final Map<String, String> NEWER_SACK_ITEMS = Map.of(
		"CRUNCHY_BUG", "Witch",
		"ALL_IN_ALOE_FRAGMENT", "Mutations"
	);
	private static final String BACK_TEXT = "< Back";

	/** Every item stored in a sack, with its amount under "sack_count" (not "count", which would draw the whole number over the icon). */
	private List<JsonObject> storedSackItems() {
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		List<JsonObject> items = new ArrayList<>();
		List<String> unresolved = new ArrayList<>();
		if (Utils.getElement(profileInfo, "sacks_counts") instanceof JsonObject sacks) {
			for (Map.Entry<String, JsonElement> entry : sacks.entrySet()) {
				long amount = Utils.getElementAsLong(entry.getValue(), 0);
				if (amount <= 0) continue;
				// The API writes dye as INK_SACK:2, the repo as INK_SACK-2.
				String id = sackKey(entry.getKey());
				JsonObject repo = repoItemForSackKey(id);
				if (repo == null) unresolved.add(entry.getKey());
				JsonObject item = repo != null ? repo.deepCopy() : new JsonObject();
				if (repo == null) {
					item.addProperty("internalname", id);
					item.addProperty("itemid", "minecraft:paper");
					item.addProperty("displayname", "§f" + org.apache.commons.lang3.text.WordUtils.capitalizeFully(id.replace('_', ' ')));
				}
				item.remove("count");
				item.addProperty("sack_count", amount);
				items.add(item);
			}
		}
		if (!unresolved.isEmpty()) {
			NotEnoughUpdates.LOGGER.info("Sacks: {} stored items have no repo item: {}", unresolved.size(), unresolved);
		}
		items.sort(java.util.Comparator.comparing(
			item -> Utils.cleanColour(Utils.getElementAsString(item.get("displayname"), "")).toLowerCase(Locale.ROOT)));
		return items;
	}

	/**
	 * The repo item for a sack key. Runes are stored under keys that differ from the repo's "NAME_RUNE;tier", so a few
	 * spellings are tried.
	 */
	private static JsonObject repoItemForSackKey(String id) {
		Map<String, JsonObject> repo = NotEnoughUpdates.INSTANCE.manager.getItemInformation();
		JsonObject exact = repo.get(id);
		if (exact != null) return exact;
		java.util.regex.Matcher tiered = java.util.regex.Pattern.compile("^(.+?)_(\\d+)$").matcher(id);
		String base = id;
		String tier = "1";
		if (tiered.matches()) {
			base = tiered.group(1);
			tier = tiered.group(2);
		}
		List<String> candidates = new ArrayList<>();
		candidates.add(id.replaceFirst("_(\\d+)$", ";$1"));
		if (base.startsWith("RUNE_")) candidates.add(base.substring("RUNE_".length()) + "_RUNE;" + tier);
		if (base.endsWith("_RUNE")) candidates.add(base + ";" + tier);
		if (!base.contains("RUNE")) candidates.add(base + "_RUNE;" + tier);
		for (String candidate : candidates) {
			JsonObject found = repo.get(candidate);
			if (found != null) return found;
		}
		return null;
	}

	private static String sackKey(String apiKey) {
		return apiKey.replace(':', '-');
	}

	/** The stored items by sack, in the repo's sack order, then whatever no sack lists under "Other". Empty sacks are left out. */
	private LinkedHashMap<String, List<JsonObject>> groupedSacks() {
		Map<String, String> sackOfItem = new HashMap<>();
		List<String> order = new ArrayList<>();
		if (Constants.SACKS != null && Constants.SACKS.get("sacks") instanceof JsonObject sacks) {
			for (Map.Entry<String, JsonElement> entry : sacks.entrySet()) {
				order.add(entry.getKey());
				if (entry.getValue() instanceof JsonObject sack && sack.get("contents") instanceof JsonArray contents) {
					for (JsonElement id : contents) sackOfItem.putIfAbsent(sackKey(id.getAsString()), entry.getKey());
				}
			}
		}
		order.add(OTHER_SACK);
		Map<String, List<JsonObject>> byName = new HashMap<>();
		for (JsonObject item : storedSackItems()) {
			String itemId = Utils.getElementAsString(item.get("internalname"), "");
			String sack = sackOfItem.get(itemId);
			if (sack == null) sack = NEWER_SACK_ITEMS.get(itemId);
			// A mutation's fragment (ALL_IN_ALOE_FRAGMENT) goes with the mutation.
			if (sack == null && itemId.endsWith("_FRAGMENT")) {
				sack = "Mutations".equals(sackOfItem.get(itemId.substring(0, itemId.length() - "_FRAGMENT".length()))) ? "Mutations" : null;
			}
			// The repo lists no contents for the rune sack, so anything rune-like goes there.
			if (sack == null) sack = itemId.contains("RUNE") ? "Rune" : OTHER_SACK;
			byName.computeIfAbsent(sack, k -> new ArrayList<>()).add(item);
		}
		LinkedHashMap<String, List<JsonObject>> grouped = new LinkedHashMap<>();
		for (String sack : order) {
			if (byName.containsKey(sack)) grouped.put(sack, byName.get(sack));
		}
		return grouped;
	}

	/** The sack's own item (from the repo) as a menu slot; it carries "sack_name" and how much is inside. */
	private JsonObject sackMenuItem(String name, List<JsonObject> contents) {
		JsonObject item = null;
		if (Utils.getElement(Constants.SACKS, "sacks." + name + ".item") instanceof JsonElement id && id.isJsonPrimitive()) {
			JsonObject repo = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(id.getAsString());
			if (repo != null) item = repo.deepCopy();
		}
		if (item == null) {
			item = new JsonObject();
			item.addProperty("internalname", "SACK_" + name.toUpperCase(Locale.ROOT).replace(' ', '_'));
			item.addProperty("itemid", "minecraft:bundle");
			item.addProperty("displayname", "§a" + name + " Sack");
		}
		item.remove("count");
		item.addProperty("sack_name", name);
		item.addProperty("sack_types", contents.size());
		long total = 0;
		for (JsonObject each : contents) total += each.get("sack_count").getAsLong();
		item.addProperty("sack_total", total);
		return item;
	}

	/** Chest pages of 54 slots for a list of items. */
	private static JsonObject[][][] chestPages(List<JsonObject> items) {
		if (items.isEmpty()) return new JsonObject[1][][];
		int pageSize = 54;
		JsonObject[][][] pages = new JsonObject[(items.size() - 1) / pageSize + 1][][];
		for (int page = 0; page < pages.length; page++) {
			int count = Math.min(pageSize, items.size() - page * pageSize);
			JsonObject[][] rows = new JsonObject[(count + 8) / 9][9];
			for (int i = 0; i < count; i++) rows[i / 9][i % 9] = items.get(page * pageSize + i);
			pages[page] = rows;
		}
		return pages;
	}

	/** The menu of sacks, or the open sack's items. */
	private JsonObject[][][] sackPages() {
		LinkedHashMap<String, List<JsonObject>> grouped = groupedSacks();
		if (openSack != null) {
			List<JsonObject> contents = grouped.get(openSack);
			if (contents != null) return chestPages(contents);
			openSack = null;
		}
		List<JsonObject> menu = new ArrayList<>();
		for (Map.Entry<String, List<JsonObject>> sack : grouped.entrySet()) menu.add(sackMenuItem(sack.getKey(), sack.getValue()));
		return chestPages(menu);
	}

	/** The title of the chest being shown: the open sack, "Sacks" for the menu, else the inventory's own name. */
	private String inventoryTitle() {
		if (selectedInventory.equals("sacks") && openSack != null) return openSack + " Sack";
		return invNameToDisplayMap.get(selectedInventory).getHoverName().getString();
	}

	/** Renders a slot's background + filler icon (if present) and, on hover, queues a full JSON-derived tooltip. */
	private void renderJsonItemSlot(GuiGraphicsExtractor graphics, JsonObject item, int x, int y, int mouseX, int mouseY) {
		if (item == null) return;
		renderJsonItemSlotNoTooltip(graphics, item, x, y);
		if (mouseX >= x - 1 && mouseX <= x + 16 + 1 && mouseY >= y - 1 && mouseY <= y + 16 + 1) {
			instance.tooltipToDisplay = buildItemTooltip(item);
		}
	}

	private static final ItemStack FILLER_STACK = new ItemStack(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);

	private void renderJsonItemSlotNoTooltip(GuiGraphicsExtractor graphics, JsonObject item, int x, int y) {
		if (item == null) return;
		if (isFiller(item)) {
			RenderUtils.drawItemStack(graphics, FILLER_STACK, x, y);
			return;
		}
		RenderUtils.drawItemStackWithCount(graphics, resolveIconCached(item), x, y);
	}

	/**
	 * See {@link #resolvedIconCache} javadoc: resolves and caches the icon {@code ItemStack} for a repo item-JSON
	 * object once instead of re-running {@code jsonToStack} (and re-allocating a fresh skull {@code GameProfile})
	 * on every frame this slot is drawn.
	 */
	private ItemStack resolveIconCached(JsonObject item) {
		// useCache=false: these are the player's real items, so two with the same internal name can still differ
		// (count, skin, glint), which the manager's per-internal-name cache would flatten into one.
		return resolvedIconCache.computeIfAbsent(item, json -> NotEnoughUpdates.INSTANCE.manager.jsonToStack(json, false));
	}

	private boolean isFiller(JsonObject item) {
		return item.has("__filler");
	}

	/**
	 * Renders the repo item icon for {@code internalname} via {@code NEUManager#jsonToStack}, falling back to a
	 * flat colored square (the pre-jsonToStack behaviour) if the repo hasn't been synced/doesn't have the item -
	 * used for GREEN_CANDY/PURPLE_CANDY, which have no sensible vanilla item to fall back on otherwise.
	 */
	private void renderRepoItemIconOrFallback(GuiGraphicsExtractor graphics, String internalname, int x, int y, int fallbackColor) {
		JsonObject itemJson = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(internalname);
		if (itemJson != null) {
			RenderUtils.drawItemStack(graphics, resolveIconCached(itemJson), x, y);
		} else {
			graphics.fill(x, y, x + 16, y + 16, fallbackColor);
		}
	}

	private List<String> buildItemTooltip(JsonObject item) {
		List<String> tooltip = new ArrayList<>();
		String displayName = Utils.getElementAsString(item.get("displayname"), null);
		tooltip.add(displayName != null && !displayName.isEmpty() ? displayName : "Unknown Item");
		JsonElement loreElement = item.get("lore");
		if (loreElement != null && loreElement.isJsonArray()) {
			for (JsonElement line : loreElement.getAsJsonArray()) {
				tooltip.add(line.getAsString());
			}
		}
		if (item.has("sack_name")) {
			tooltip.add("");
			tooltip.add(ChatFormatting.GRAY + "Item types: " + ChatFormatting.YELLOW + item.get("sack_types").getAsInt());
			tooltip.add(ChatFormatting.GRAY + "Items stored: " + ChatFormatting.YELLOW +
				GuiProfileViewer.numberFormat.format(item.get("sack_total").getAsLong()));
			tooltip.add("");
			tooltip.add(ChatFormatting.YELLOW + "Click to open!");
		}
		if (item.has("sack_count")) {
			tooltip.add("");
			tooltip.add(ChatFormatting.GRAY + "Stored: " + ChatFormatting.YELLOW +
				GuiProfileViewer.numberFormat.format(item.get("sack_count").getAsLong()));
		}
		return tooltip;
	}

	private boolean doesItemMatchSearch(JsonObject item, String search) {
		String needle = search.toLowerCase(Locale.US);
		String displayName = Utils.getElementAsString(item.get("displayname"), "");
		if (Utils.cleanColour(displayName).toLowerCase(Locale.US).contains(needle)) return true;
		String internalName = Utils.getElementAsString(item.get("internalname"), "");
		return internalName.toLowerCase(Locale.US).contains(needle);
	}

	private JsonObject[] extractJsonItems(JsonElement arrayElement) {
		if (arrayElement == null || !arrayElement.isJsonArray()) return new JsonObject[0];
		JsonArray array = arrayElement.getAsJsonArray();
		JsonObject[] items = new JsonObject[array.size()];
		for (int i = 0; i < array.size(); i++) {
			if (array.get(i) != null && array.get(i).isJsonObject()) {
				items[i] = array.get(i).getAsJsonObject();
			}
		}
		return items;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (inventorySearchField != null) {
			if (mouseX > guiLeft + 19 && mouseX < guiLeft + 19 + 88) {
				if (mouseY > guiTop + instance.sizeY - 26 - 20 && mouseY < guiTop + instance.sizeY - 26) {
					inventorySearchField.setFocused(true);
					return true;
				}
			}
			inventorySearchField.setFocused(false);
		}
		return false;
	}

	/** Opens the clicked sack, or goes back from an open sack; true when the click was used. */
	private boolean clickedSackSlot(JsonObject[][] inventory, double mouseX, double mouseY) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		int x = guiLeft + 320 - 176 / 2;
		int y = guiTop + 101 - (inventory.length * 18 + 17 + 7) / 2;
		if (openSack != null) {
			int backWidth = instance.getFont().width(BACK_TEXT);
			if (Utils.isWithinRect((int) mouseX, (int) mouseY, x + 176 - 8 - backWidth, y + 4, backWidth, 11)) {
				RenderUtils.playPressSound();
				openSack = null;
				currentInventoryIndex = 0;
				return true;
			}
			return false;
		}
		for (int row = 0; row < inventory.length; row++) {
			if (inventory[row] == null) continue;
			for (int column = 0; column < inventory[row].length; column++) {
				JsonObject item = inventory[row][column];
				if (item == null || !item.has("sack_name")) continue;
				if (Utils.isWithinRect((int) mouseX, (int) mouseY, x + 8 + column * 18, y + 18 + row * 18, 16, 16)) {
					RenderUtils.playPressSound();
					openSack = item.get("sack_name").getAsString();
					currentInventoryIndex = 0;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (mouseButton == 0) {
			int i = 0;
			for (Map.Entry<String, ItemStack> entry : invNameToDisplayMap.entrySet()) {
				int xIndex = i % 3;
				int yIndex = i / 3;

				int x = guiLeft + 19 + 34 * xIndex;
				int y = guiTop + 26 + 34 * yIndex;

				if (mouseX >= x && mouseX <= x + 16) {
					if (mouseY >= y && mouseY <= y + 16) {
						if (!selectedInventory.equals(entry.getKey())) RenderUtils.playPressSound();
						// Clicking Sacks again goes back to the menu of sacks.
						if (entry.getKey().equals("sacks") && selectedInventory.equals("sacks")) openSack = null;
						selectedInventory = entry.getKey();
						currentInventoryIndex = 0;
						return;
					}
				}

				i++;
			}

			JsonObject inventoryInfo = GuiProfileViewer.getProfile().getInventoryInfo(GuiProfileViewer.getProfileId());
			if (inventoryInfo == null) return;

			JsonObject[][][] inventories = getItemsForInventory(inventoryInfo, selectedInventory);
			if (currentInventoryIndex >= inventories.length) currentInventoryIndex = inventories.length - 1;
			if (currentInventoryIndex < 0) currentInventoryIndex = 0;

			JsonObject[][] inventory = inventories[currentInventoryIndex];
			if (inventory == null) return;

			if (selectedInventory.equals("sacks") && clickedSackSlot(inventory, mouseX, mouseY)) return;

			int staticSelectorHeight = guiTop + 177;

			if (mouseY > staticSelectorHeight && mouseY < staticSelectorHeight + 16) {
				if (mouseX > guiLeft + 320 - 12 && mouseX < guiLeft + 320 + 12) {
					if (mouseX < guiLeft + 320) {
						currentInventoryIndex--;
					} else {
						currentInventoryIndex++;
					}
				}
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (inventorySearchField != null && inventorySearchField.isFocused()) {
			return inventorySearchField.keyPressed(event);
		}
		int key = event.key();
		String newSelection = switch (key) {
			case org.lwjgl.glfw.GLFW.GLFW_KEY_1, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1 -> "inv_contents";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_2, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2 -> "ender_chest_contents";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_3, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3 -> "backpack_contents";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_4, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4 -> "personal_vault_contents";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_5, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_5 -> "talisman_bag";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_6, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_6 -> "wardrobe_contents";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_7, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_7 -> "fishing_bag";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_8, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_8 -> "potion_bag";
			case org.lwjgl.glfw.GLFW.GLFW_KEY_9, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_9 -> "sacks";
			default -> null;
		};
		if (newSelection != null) {
			selectedInventory = newSelection;
			RenderUtils.playPressSound();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (inventorySearchField != null && inventorySearchField.isFocused()) {
			return inventorySearchField.charTyped(event);
		}
		return false;
	}

	private int countItemsInInventory(
		String internalname,
		JsonObject inventoryInfo,
		boolean specific,
		String... invsToSearch
	) {
		int count = 0;
		for (String inv : invsToSearch) {
			JsonArray invItems = inventoryInfo.get(inv).getAsJsonArray();
			for (int i = 0; i < invItems.size(); i++) {
				if (invItems.get(i) == null || !invItems.get(i).isJsonObject()) continue;
				JsonObject item = invItems.get(i).getAsJsonObject();
				if (
					(specific && item.get("internalname").getAsString().equals(internalname)) ||
						(!specific && item.get("internalname").getAsString().contains(internalname))
				) {
					if (item.has("count")) {
						count += item.get("count").getAsInt();
					} else {
						count += 1;
					}
				}
			}
		}
		return count;
	}

	private JsonObject[] findBestItems(
		JsonObject inventoryInfo,
		int numItems,
		String[] invsToSearch,
		String[] typeMatches,
		Pattern... importantPatterns
	) {
		JsonObject[] bestItems = new JsonObject[numItems];
		TreeMap<Long, Set<JsonObject>> map = new TreeMap<>();
		for (String inv : invsToSearch) {
			JsonArray invItems = inventoryInfo.get(inv).getAsJsonArray();
			for (int i = 0; i < invItems.size(); i++) {
				if (invItems.get(i) == null || !invItems.get(i).isJsonObject()) continue;
				JsonObject item = invItems.get(i).getAsJsonObject();
				if (!item.has("lore")) continue;
				JsonArray lore = item.get("lore").getAsJsonArray();
				if (Utils.checkItemType(lore, true, typeMatches) >= 0) {
					long importance = 0;
					int id = 0;
					if (importantPatterns.length == 0) {
						String internalName = item.get("internalname").getAsString();
						importance += NotEnoughUpdates.INSTANCE.manager.auctionManager.getLowestBin(internalName);
						importance += ++id;
					} else {
						for (int j = 0; j < lore.size(); j++) {
							String line = lore.get(j).getAsString();
							for (Pattern pattern : importantPatterns) {
								Matcher matcher = pattern.matcher(Utils.cleanColour(line));
								if (matcher.find()) {
									importance += Integer.parseInt(matcher.group(1));
								}
							}
						}
					}
					map.computeIfAbsent(importance, k -> new HashSet<>()).add(item);
				}
			}
		}
		int i = 0;
		outer:
		for (long key : map.descendingKeySet()) {
			Set<JsonObject> items = map.get(key);
			for (JsonObject item : items) {
				bestItems[i] = item;
				if (++i >= bestItems.length) break outer;
			}
		}

		return bestItems;
	}

	private JsonObject[][][] getItemsForInventory(JsonObject inventoryInfo, String invName) {
		if (invName.equals("sacks")) {
			String key = openSack == null ? "sacks" : "sacks:" + openSack;
			JsonObject[][][] cached = inventoryItems.get(key);
			if (cached == null) {
				cached = sackPages();
				// sackPages may drop an open sack that no longer exists, so key by what it ended up showing.
				inventoryItems.put(openSack == null ? "sacks" : "sacks:" + openSack, cached);
			}
			return cached;
		}
		if (inventoryItems.containsKey(invName)) return inventoryItems.get(invName);

		JsonArray jsonInv = Utils.getElement(inventoryInfo, invName).getAsJsonArray();

		if (jsonInv.size() == 0) return new JsonObject[1][][];

		int jsonInvSize;
		if (useActualMax(invName)) {
			jsonInvSize = (int) Math.ceil(jsonInv.size() / 9f) * 9;
		} else {
			jsonInvSize = 9 * 4;
			float divideBy = 9f;
			if (invName.equals("wardrobe_contents")) {
				divideBy = 36f;
			}
			for (int i = 9 * 4; i < jsonInv.size(); i++) {
				JsonElement item = jsonInv.get(i);
				if (item != null && item.isJsonObject()) {
					jsonInvSize = (int) (Math.ceil((i + 1) / divideBy) * (int) divideBy);
				}
			}
		}

		int rowSize = 9;
		int rows = jsonInvSize / rowSize;
		int maxRowsPerPage = getRowsForInventory(invName);
		int maxInvSize = rowSize * maxRowsPerPage;

		int numInventories = (jsonInvSize - 1) / maxInvSize + 1;
		JsonArray backPackSizes = inventoryInfo.has("backpack_sizes") ? inventoryInfo.get("backpack_sizes").getAsJsonArray() : null;
		if (invName.equals("backpack_contents") && backPackSizes != null) {
			numInventories = backPackSizes.size();
		}

		JsonObject[][][] inventories = new JsonObject[numInventories][][];

		int startNumberJ = 0;

		for (int i = 0; i < numInventories; i++) {
			int thisRows = Math.min(maxRowsPerPage, rows - maxRowsPerPage * i);
			int invSize;

			if (invName.equals("backpack_contents") && backPackSizes != null) {
				thisRows = backPackSizes.get(i).getAsInt() / 9;
				invSize = startNumberJ + (thisRows * 9);
				maxInvSize = thisRows * 9;
			} else {
				startNumberJ = maxInvSize * i;
				invSize = Math.min(jsonInvSize, maxInvSize + maxInvSize * i);
			}
			if (thisRows <= 0) break;

			JsonObject[][] items = new JsonObject[thisRows][rowSize];

			for (int j = startNumberJ; j < invSize; j++) {
				int xIndex = (j % maxInvSize) % rowSize;
				int yIndex = (j % maxInvSize) / rowSize;
				if (invName.equals("inv_contents")) {
					yIndex--;
					if (yIndex < 0) yIndex = rows - 1;
				}
				if (yIndex >= thisRows) {
					break;
				}

				if (j >= jsonInv.size()) {
					JsonObject filler = new JsonObject();
					filler.addProperty("__filler", true);
					items[yIndex][xIndex] = filler;
					continue;
				}
				if (jsonInv.get(j) == null || !jsonInv.get(j).isJsonObject()) {
					continue;
				}

				items[yIndex][xIndex] = jsonInv.get(j).getAsJsonObject();
			}
			inventories[i] = items;
			if (invName.equals("backpack_contents") && backPackSizes != null) {
				startNumberJ = startNumberJ + backPackSizes.get(i).getAsInt();
			}
		}

		inventoryItems.put(invName, inventories);
		return inventories;
	}

	private boolean useActualMax(String invName) {
		switch (invName) {
			case "talisman_bag":
			case "fishing_bag":
			case "potion_bag":
			case "personal_vault_contents":
				return true;
		}
		return false;
	}

	private int getRowsForInventory(String invName) {
		switch (invName) {
			case "wardrobe_contents":
				return 4;
			case "backpack_contents":
			case "ender_chest_contents":
				return 5;
			default:
				return 6;
		}
	}
}
