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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Heart of the Mountain / Heart of the Forest perk trees as SkyBlockPv draws them: the node layouts bundled in
 * {@code skill_trees.json} (from its repo) and its vanilla items for each node state. Used by the Loadouts tab and
 * the Foraging tab's HotF sub-page.
 */
public final class SkillTreeView {

	private static final Identifier TREES = Identifier.parse("notenoughupdates:profile_viewer/skill_trees.json");
	private static final Identifier PERK_BACKGROUND = Identifier.parse("notenoughupdates:profile_viewer/mining/perk_background.png");

	/** One perk tree's node layout and the vanilla items its node states are drawn with. */
	public record Tree(String title, String skill, String core, List<Node> nodes, int rows, TreeItems items) {
	}

	public record Node(String id, String type, String name, int x, int y, int max) {
	}

	private record TreeItems(
		Item coreMax, Item coreUnlocked, Item coreLeveling, Item coreLocked,
		Item abilitySelected, Item abilityUnlocked, Item abilityLocked,
		Item disabled, Item max, Item unlocked, Item locked
	) {
	}

	private static final TreeItems MINING_ITEMS = new TreeItems(
		Items.DIAMOND_BLOCK, Items.COPPER_BLOCK, Items.REDSTONE_BLOCK, Items.BEDROCK,
		Items.EMERALD_BLOCK, Items.REDSTONE_BLOCK, Items.COAL_BLOCK,
		Items.REDSTONE, Items.DIAMOND, Items.EMERALD, Items.COAL
	);
	private static final TreeItems FORAGING_ITEMS = new TreeItems(
		Items.OAK_WOOD, Items.STRIPPED_BIRCH_WOOD, Items.STRIPPED_OAK_WOOD, Items.STRIPPED_PALE_OAK_WOOD,
		Items.OAK_SAPLING, Items.CHERRY_SAPLING, Items.PALE_OAK_SAPLING,
		Items.STRIPPED_MANGROVE_LOG, Items.OAK_LOG, Items.STRIPPED_OAK_LOG, Items.PALE_OAK_BUTTON
	);

	public static final String HOTM_SKULL =
		"ewogICJ0aW1lc3RhbXAiIDogMTYxOTAxNDUyMjgzOCwKICAicHJvZmlsZUlkIiA6ICIyMzYxYmNlZjZkMWM0ZWI1OGNhMDUzNDFjNGU4MGM0YyIsCiAgInByb2ZpbGVOYW1lIiA6ICJIaXJvQ2FwdWNjaW5vODciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODZmMDZlYWEzMDA0YWVlZDA5YjNkNWI0NWQ5NzZkZTU4NGU2OTFjMGU5Y2FkZTEzMzYzNWRlOTNkMjNiOWVkYiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
	public static final String HOTF_SKULL =
		"ewogICJ0aW1lc3RhbXAiIDogMTcxNzAyMTQ2Njk1NSwKICAicHJvZmlsZUlkIiA6ICIzZGE2ZDgxOTI5MTY0MTNlODhlNzg2MjQ3NzA4YjkzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJGZXJTdGlsZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81ZWY1MzliMTY1MTI1Y2ZhNDZiMDZmZmI5NjU5ZTdjZjg5MDg0YmJkM2VkZTFiMzE0ZWRjOGY0NDMzNDNkNjFjIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";

	private static Tree miningTree;
	private static Tree foragingTree;
	private static boolean treesLoaded;
	private static final Map<Item, ItemStack> ITEM_CACHE = new HashMap<>();

	private SkillTreeView() {
	}

	public static Tree mining() {
		loadTrees();
		return miningTree;
	}

	public static Tree foraging() {
		loadTrees();
		return foragingTree;
	}

	public static ItemStack hotmSkull() {
		return Utils.createSkull("", "2361bcef6d1c4eb58ca05341c4e80c4c", HOTM_SKULL);
	}

	public static ItemStack hotfSkull() {
		return Utils.createSkull("", "3da6d8192916413e88e786247708b93e", HOTF_SKULL);
	}

	/** The tree slot (1-5) the player has selected for {@code skill} ("mining" or "foraging"). */
	public static int selectedSlot(JsonObject profileInfo, String skill) {
		JsonElement slot = Utils.getElement(profileInfo, "skill_tree.selected_skill_tree_slot." + skill);
		return slot != null && slot.isJsonPrimitive() ? Math.max(1, slot.getAsInt()) : 1;
	}

	/**
	 * Draws the nodes of {@code tree} for tree slot {@code slot}, the bottom row at the bottom. Each node is a
	 * {@code cell - 1} square background {@code cell} pixels apart.
	 *
	 * @return the hovered node's tooltip, or null
	 */
	public static List<String> drawNodes(
		GuiGraphicsExtractor graphics, Font font, Tree tree, JsonObject profileInfo, int slot, int gridX, int gridY, int cell,
		int mouseX, int mouseY
	) {
		String suffix = slot > 1 ? "_" + slot : "";
		JsonElement nodesElement = Utils.getElement(profileInfo, "skill_tree.nodes." + tree.skill() + suffix);
		JsonObject nodes = nodesElement instanceof JsonObject object ? object : new JsonObject();
		JsonElement ability = Utils.getElement(profileInfo, "skill_tree.selected_ability." + tree.skill() + suffix);
		String selectedAbility = ability != null && ability.isJsonPrimitive() ? ability.getAsString() : null;
		int coreLevel = level(nodes, tree.core());

		int size = cell - 1;
		int offset = (size - 16) / 2;
		List<String> tooltip = null;
		for (Node node : tree.nodes()) {
			int level = level(nodes, node.id());
			int slotX = gridX + node.x() * cell;
			int slotY = gridY + (tree.rows() - 1 - node.y()) * cell;
			JsonElement toggle = nodes.get("toggle_" + node.id());
			boolean disabled = !node.type().equals("ABILITY") && toggle != null && toggle.isJsonPrimitive() && !toggle.getAsBoolean();

			Item item = nodeItem(tree.items(), node, level, disabled, node.id().equals(selectedAbility));
			RenderUtils.drawTexturedRect(graphics, PERK_BACKGROUND, slotX, slotY, size, size);
			RenderUtils.drawItemStack(graphics, ITEM_CACHE.computeIfAbsent(item, ItemStack::new), slotX + offset, slotY + offset);

			// Levelled nodes show their level; an unlocked ability shows the axe/pick ability level the core gives.
			int shown = node.type().equals("ABILITY") ? (level > 0 ? (coreLevel < 1 ? 1 : 2) : -1) : level;
			if (shown > 1) {
				String text = String.valueOf(shown);
				RenderUtils.text(graphics, font, text, slotX + offset + 17 - font.width(text), slotY + offset + 9, 0xFFFFFF, true);
			}
			if (Utils.isWithinRect(mouseX, mouseY, slotX, slotY, size, size)) {
				tooltip = nodeTooltip(node, level, disabled, node.id().equals(selectedAbility));
			}
		}
		return tooltip;
	}

	private static int level(JsonObject nodes, String id) {
		JsonElement level = nodes.get(id);
		return level != null && level.isJsonPrimitive() && level.getAsJsonPrimitive().isNumber() ? level.getAsInt() : -1;
	}

	private static Item nodeItem(TreeItems items, Node node, int level, boolean disabled, boolean selectedAbility) {
		switch (node.type()) {
			case "CORE":
				if (level >= node.max()) return items.coreMax();
				if (level <= 0) return items.coreLocked();
				return level == 1 ? items.coreUnlocked() : items.coreLeveling();
			case "ABILITY":
				if (selectedAbility) return items.abilitySelected();
				return level > 0 ? items.abilityUnlocked() : items.abilityLocked();
			default:
				if (disabled) return items.disabled();
				if (level < 0) return items.locked();
				return level >= node.max() ? items.max() : items.unlocked();
		}
	}

	private static List<String> nodeTooltip(Node node, int level, boolean disabled, boolean selectedAbility) {
		List<String> tooltip = new ArrayList<>();
		boolean unlocked = level > 0;
		String colour = unlocked ? (level >= node.max() ? "§b" : "§a") : "§c";
		tooltip.add(colour + node.name());
		switch (node.type()) {
			case "ABILITY" -> tooltip.add(selectedAbility ? "§aSelected ability" : unlocked ? "§7Unlocked" : "§cLocked");
			case "UNLEVELABLE" -> tooltip.add(unlocked ? "§7Unlocked" : "§cLocked");
			default -> tooltip.add(level < 0 ? "§cLocked" : "§7Level: §f" + level + "§7/§f" + node.max());
		}
		if (disabled) tooltip.add("§cDisabled");
		return tooltip;
	}

	private static void loadTrees() {
		if (treesLoaded) return;
		treesLoaded = true;
		try (InputStreamReader reader = new InputStreamReader(
			Minecraft.getInstance().getResourceManager().open(TREES), StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			miningTree = parseTree(json, "mining", "HOTM Loadout", "core_of_the_mountain", MINING_ITEMS);
			foragingTree = parseTree(json, "foraging", "HOTF Loadout", "center_of_the_forest", FORAGING_ITEMS);
		} catch (Exception e) {
			System.err.println("[Better PV] Could not read skill_trees.json: " + e);
		}
	}

	private static Tree parseTree(JsonObject json, String key, String title, String core, TreeItems items) {
		List<Node> nodes = new ArrayList<>();
		int rows = 0;
		for (JsonElement element : json.getAsJsonArray(key)) {
			JsonObject node = element.getAsJsonObject();
			int max = node.has("max") ? node.get("max").getAsInt() : 1;
			int y = node.get("y").getAsInt();
			nodes.add(new Node(node.get("id").getAsString(), node.get("type").getAsString(), node.get("name").getAsString(),
				node.get("x").getAsInt(), y, max));
			rows = Math.max(rows, y + 1);
		}
		nodes.sort(Comparator.comparingInt(Node::y));
		return new Tree(title, key, core, nodes, rows, items);
	}
}
