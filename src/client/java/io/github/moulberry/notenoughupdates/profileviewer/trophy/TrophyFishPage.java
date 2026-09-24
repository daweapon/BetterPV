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

package io.github.moulberry.notenoughupdates.profileviewer.trophy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Port of the Forge 1.8.9 {@code trophy.TrophyFishPage} ("Trophy Fish" tab). {@link TrophyFish} (the data model)
 * was already ported in the prior data-layer pass; this is the rendering/layout port.
 *
 * <p>TODO(fabric-port) — intentionally simplified vs. the original:
 * <ul>
 *   <li>Per-fish item icons (bronze-trophy skull textures), the "not discovered" icon substitute, and the
 *   tier-reward helmet icons (hunter helmets) are all rendered via {@code NEUManager#jsonToStack} against their
 *   repo entries (falling back to no icon, just the rarity-tinted background square, if the repo hasn't been
 *   synced or doesn't have that entry).</li>
 *   <li>The rarity-color tint on each fish's background square (originally a {@code GlStateManager.color} tint
 *   applied to the {@code pv_elements} slot-background texture) is simplified to a flat-filled colored rect,
 *   since the modern render-state API doesn't expose an equivalent simple texture tint helper here.</li>
 * </ul>
 */
public class TrophyFishPage implements GuiProfileViewerPage {

	private static final Identifier TROPHY_FISH_TEXTURE = Identifier.parse("notenoughupdates:pv_trophy_fish_tab.png");

	private static final Map<String, ChatFormatting> internalTrophyFish = new HashMap<>();

	static {
		internalTrophyFish.put("gusher", ChatFormatting.WHITE);
		internalTrophyFish.put("flyfish", ChatFormatting.GREEN);
		internalTrophyFish.put("moldfin", ChatFormatting.DARK_PURPLE);
		internalTrophyFish.put("vanille", ChatFormatting.BLUE);
		internalTrophyFish.put("blobfish", ChatFormatting.WHITE);
		internalTrophyFish.put("mana_ray", ChatFormatting.BLUE);
		internalTrophyFish.put("slugfish", ChatFormatting.GREEN);
		internalTrophyFish.put("soul_fish", ChatFormatting.DARK_PURPLE);
		internalTrophyFish.put("lava_horse", ChatFormatting.BLUE);
		internalTrophyFish.put("golden_fish", ChatFormatting.GOLD);
		internalTrophyFish.put("karate_fish", ChatFormatting.DARK_PURPLE);
		internalTrophyFish.put("skeleton_fish", ChatFormatting.DARK_PURPLE);
		internalTrophyFish.put("sulphur_skitter", ChatFormatting.WHITE);
		internalTrophyFish.put("obfuscated_fish_1", ChatFormatting.WHITE);
		internalTrophyFish.put("obfuscated_fish_2", ChatFormatting.GREEN);
		internalTrophyFish.put("obfuscated_fish_3", ChatFormatting.BLUE);
		internalTrophyFish.put("volcanic_stonefish", ChatFormatting.BLUE);
		internalTrophyFish.put("steaming_hot_flounder", ChatFormatting.WHITE);
	}

	private static final LinkedHashMap<String, Pair<String, Integer>> armorHelmets = new LinkedHashMap<>();

	static {
		armorHelmets.put("BRONZE_HUNTER_HELMET", Pair.of(ChatFormatting.GREEN + "Novice Fisher", 1));
		armorHelmets.put("SILVER_HUNTER_HELMET", Pair.of(ChatFormatting.BLUE + "Adept Fisher", 2));
		armorHelmets.put("GOLD_HUNTER_HELMET", Pair.of(ChatFormatting.DARK_PURPLE + "Expert Fisher", 3));
		armorHelmets.put("DIAMOND_HUNTER_HELMET", Pair.of(ChatFormatting.GOLD + "Master Fisher", 4));
	}

	private static final Map<Integer, Pair<Integer, Integer>> slotLocations = new HashMap<>();

	static {
		slotLocations.put(0, Pair.of(277, 46));
		slotLocations.put(1, Pair.of(253, 58));
		slotLocations.put(2, Pair.of(301, 58));
		slotLocations.put(3, Pair.of(229, 70));
		slotLocations.put(4, Pair.of(325, 70));
		slotLocations.put(5, Pair.of(277, 70));
		slotLocations.put(6, Pair.of(253, 82));
		slotLocations.put(7, Pair.of(301, 82));
		slotLocations.put(8, Pair.of(229, 94));
		slotLocations.put(9, Pair.of(325, 94));
		slotLocations.put(10, Pair.of(253, 106));
		slotLocations.put(11, Pair.of(301, 106));
		slotLocations.put(12, Pair.of(277, 118));
		slotLocations.put(13, Pair.of(229, 118));
		slotLocations.put(14, Pair.of(325, 118));
		slotLocations.put(15, Pair.of(253, 130));
		slotLocations.put(16, Pair.of(301, 130));
		slotLocations.put(17, Pair.of(277, 142));
	}

	private static final String checkX = "§c✖";
	private static final String check = "§a✔";

	private final GuiProfileViewer instance;
	private final Map<String, Integer> total = new HashMap<>();
	private final Map<String, TrophyFish> trophyFishList = new HashMap<>();
	private long totalCount = 0;
	/**
	 * Caches icons resolved via {@code NEUManager#jsonToStack} in {@link #repoIconOrNull}, keyed by repo
	 * internalname. Without this, every fish/helmet icon would be re-resolved (a fresh {@code ItemStack}/
	 * {@code GameProfile} via {@code .copy()} on every cache hit) on every single frame - see the identical fix
	 * and rationale in {@code InventoriesPage#resolvedIconCache}.
	 */
	private final Map<String, ItemStack> iconCache = new HashMap<>();

	public TrophyFishPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		total.clear();
		trophyFishList.clear();
		totalCount = 0;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		trophyFishList.clear();

		JsonObject profileInformation = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInformation == null || !profileInformation.has("trophy_fish")) {
			RenderUtils.drawStringCentered(
				graphics,
				ChatFormatting.RED + "No data found",
				instance.getFont(),
				guiLeft + 431 / 2f,
				guiTop + 101,
				true,
				0
			);
			return;
		}
		JsonObject trophyObject = profileInformation.get("trophy_fish").getAsJsonObject();

		loadTrophyInformation(trophyObject);

		RenderUtils.drawTexturedRect(graphics, TROPHY_FISH_TEXTURE, guiLeft, guiTop, 431, 202);

		JsonObject stats = profileInformation.get("stats").getAsJsonObject();

		int thunderKills = 0;
		if (stats.has("kills_thunder")) {
			thunderKills = stats.get("kills_thunder").getAsInt();
		}
		RenderUtils.text(
			graphics,
			instance.getFont(),
			ChatFormatting.AQUA + "Thunder Kills: §f" + thunderKills,
			guiLeft + 20,
			guiTop + 112,
			0xFFFFFF,
			true
		);

		int jawbusKills = 0;
		if (stats.has("kills_lord_jawbus")) {
			jawbusKills = stats.get("kills_lord_jawbus").getAsInt();
		}
		RenderUtils.text(
			graphics,
			instance.getFont(),
			ChatFormatting.AQUA + "Lord Jawbus Kills: §f" + jawbusKills,
			guiLeft + 20,
			guiTop + 124,
			0xFFFFFF,
			true
		);

		RenderUtils.text(
			graphics,
			instance.getFont(),
			ChatFormatting.AQUA + "Total Caught: §f" + totalCount,
			guiLeft + 20,
			guiTop + 25,
			0xFFFFFF,
			true
		);

		ArrayList<TrophyFish> arrayList = new ArrayList<>(trophyFishList.values());
		arrayList.sort((c1, c2) -> Integer.compare(c2.getTotal(), c1.getTotal()));

		int x;
		int y;
		for (TrophyFish value : arrayList) {
			int index = arrayList.indexOf(value);
			Pair<Integer, Integer> slot = slotLocations.get(index);
			if (slot == null) continue;
			x = guiLeft + slot.getLeft();
			y = guiTop + slot.getRight();

			Map<TrophyFish.TrophyFishRarity, Integer> trophyFishRarityIntegerMap = value.getTrophyFishRarityIntegerMap();
			int tint = 0xFFFFFFFF;
			if (trophyFishRarityIntegerMap.containsKey(TrophyFish.TrophyFishRarity.BRONZE)) tint = 0xFFFF8200;
			if (trophyFishRarityIntegerMap.containsKey(TrophyFish.TrophyFishRarity.SILVER)) tint = 0xFFC0C0C0;
			if (trophyFishRarityIntegerMap.containsKey(TrophyFish.TrophyFishRarity.GOLD)) tint = 0xFFFFD100;
			if (trophyFishRarityIntegerMap.containsKey(TrophyFish.TrophyFishRarity.DIAMOND)) tint = 0xFF1FD8F1;
			fillCircle(graphics, x + 8, y + 8, 10, (tint & 0x00FFFFFF) | SLOT_ALPHA);
			ItemStack fishIcon = resolveFishIcon(value.getName());
			if (fishIcon != null) {
				RenderUtils.drawItemStack(graphics, fishIcon, x, y);
			}

			if (mouseX >= x - 2 && mouseX < x + 18 && mouseY >= y - 2 && mouseY <= y + 18) {
				instance.tooltipToDisplay = getTooltip(value.getName(), value.getTrophyFishRarityIntegerMap());
			}
		}

		if (arrayList.size() != internalTrophyFish.size()) {
			List<String> clonedList = new ArrayList<>(internalTrophyFish.keySet());
			clonedList.removeAll(fixStringName(new ArrayList<>(trophyFishList.keySet())));
			for (String difference : clonedList) {
				int index = clonedList.indexOf(difference) + trophyFishList.keySet().size();
				Pair<Integer, Integer> slot = slotLocations.get(index);
				if (slot == null) continue;
				x = guiLeft + slot.getLeft();
				y = guiTop + slot.getRight();

				fillCircle(graphics, x + 8, y + 8, 10, 0x555555 | SLOT_ALPHA);
				ItemStack undiscoveredIcon = resolveFishIcon(difference);
				if (undiscoveredIcon != null) {
					RenderUtils.drawItemStack(graphics, undiscoveredIcon, x, y);
				}

				if (mouseX >= x - 2 && mouseX < x + 18 && mouseY >= y - 2 && mouseY <= y + 18) {
					instance.tooltipToDisplay = getTooltip(difference, null);
				}
			}
		}

		if (!trophyObject.has("rewards")) return;

		int[] trophiesPerTier = getTrophiesPerTier(trophyObject);
		JsonArray rewards = trophyObject.get("rewards").getAsJsonArray();
		int i = 0;
		for (Entry<String, Pair<String, Integer>> entry : armorHelmets.entrySet()) {
			int integer = entry.getValue().getRight();
			x = guiLeft + 15;
			y = guiTop + 50 + i;

			ItemStack helmetIcon = repoIconOrNull(entry.getKey());
			if (helmetIcon != null) {
				RenderUtils.drawItemStack(graphics, helmetIcon, x, y);
			}
			RenderUtils.text(graphics, instance.getFont(), entry.getValue().getLeft(), x + 20, y + 4, 0xFFFFFF, true);

			int hasValue = trophiesPerTier[integer - 1];
			int neededValue = integer == 1 ? 15 : 18;
			String neededText = "§c" + hasValue + "/" + neededValue;

			boolean claimed = integer - 1 < rewards.size() && !rewards.get(integer - 1).isJsonNull();
			// Right-aligned to the panel's inner edge, so a long name ("Master Fisher") can't run into it.
			String status = claimed ? check : neededText;
			int statusRight = guiLeft + HELMET_PANEL_RIGHT;
			RenderUtils.text(
				graphics, instance.getFont(), status, statusRight - instance.getFont().width(status), y + (claimed ? 2 : 4),
				0xFFFFFF, true
			);
			i += 10;
		}
	}

	/** Fish slot background opacity (~40%). */
	private static final int SLOT_ALPHA = 0x66000000;
	/** Inner right edge of the helmet-tier panel in the page texture, relative to guiLeft. */
	private static final int HELMET_PANEL_RIGHT = 142;

	/** Filled circle with one-pixel alpha-smoothed edges. */
	private static void fillCircle(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int argb) {
		for (int dy = -radius; dy < radius; dy++) {
			float rowCenter = dy + 0.5f;
			float halfWidth = (float) Math.sqrt(radius * radius - rowCenter * rowCenter);
			float leftEdge = centerX - halfWidth;
			float rightEdge = centerX + halfWidth;
			int leftPixel = (int) Math.floor(leftEdge);
			int rightPixel = (int) Math.ceil(rightEdge) - 1;
			int y = centerY + dy;

			float leftCoverage = Math.min(1, leftPixel + 1 - leftEdge);
			graphics.fill(leftPixel, y, leftPixel + 1, y + 1, scaleAlpha(argb, leftCoverage));
			if (rightPixel > leftPixel + 1) {
				graphics.fill(leftPixel + 1, y, rightPixel, y + 1, argb);
			}
			if (rightPixel > leftPixel) {
				float rightCoverage = Math.min(1, rightEdge - rightPixel);
				graphics.fill(rightPixel, y, rightPixel + 1, y + 1, scaleAlpha(argb, rightCoverage));
			}
		}
	}

	private static int scaleAlpha(int argb, float coverage) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * coverage);
		return (argb & 0x00FFFFFF) | (alpha << 24);
	}

	private int[] getTrophiesPerTier(JsonObject trophyFish) {
		int[] trophiesPerTier = new int[] { 0, 0, 0, 0 };
		for (String fishType : internalTrophyFish.keySet()) {
			int highestTier = 0;
			if (trophyFish.has((fishType + "_bronze"))) highestTier = 1;
			if (trophyFish.has((fishType + "_silver"))) highestTier = 2;
			if (trophyFish.has((fishType + "_gold"))) highestTier = 3;
			if (trophyFish.has((fishType + "_diamond"))) highestTier = 4;

			if (highestTier >= 1) trophiesPerTier[0]++;
			if (highestTier >= 2) trophiesPerTier[1]++;
			if (highestTier >= 3) trophiesPerTier[2]++;
			if (highestTier >= 4) trophiesPerTier[3]++;
		}
		return trophiesPerTier;
	}

	private List<String> getTooltip(String name, Map<TrophyFish.TrophyFishRarity, Integer> trophyFishRarityIntegerMap) {
		List<String> tooltip = new ArrayList<>();
		ChatFormatting nameColor = internalTrophyFish.get(name.toLowerCase(Locale.US).replace(" ", "_"));
		tooltip.add((nameColor == null ? ChatFormatting.WHITE : nameColor) + WordUtils.capitalize(name.replace("_", " ")));

		List<String> lore = readLoreFromRepo(name.toUpperCase(Locale.US));
		List<String> description = readDescriptionFromLore(lore);
		tooltip.addAll(description);
		tooltip.add(" ");

		if (trophyFishRarityIntegerMap == null) {
			tooltip.add(ChatFormatting.RED + checkX + " Not Discovered");
			tooltip.add(" ");
		}

		tooltip.add(display(trophyFishRarityIntegerMap, TrophyFish.TrophyFishRarity.DIAMOND, ChatFormatting.AQUA));
		tooltip.add(display(trophyFishRarityIntegerMap, TrophyFish.TrophyFishRarity.GOLD, ChatFormatting.GOLD));
		tooltip.add(display(trophyFishRarityIntegerMap, TrophyFish.TrophyFishRarity.SILVER, ChatFormatting.GRAY));
		tooltip.add(display(trophyFishRarityIntegerMap, TrophyFish.TrophyFishRarity.BRONZE, ChatFormatting.DARK_GRAY));
		return tooltip;
	}

	private String display(
		Map<TrophyFish.TrophyFishRarity, Integer> trophyFishRarityIntegerMap,
		TrophyFish.TrophyFishRarity rarity,
		ChatFormatting color
	) {
		String name = WordUtils.capitalize(rarity.name().toLowerCase(Locale.US));
		if (trophyFishRarityIntegerMap == null) {
			return color + name + ": " + checkX;
		}

		if (trophyFishRarityIntegerMap.containsKey(rarity)) {
			return color + name + ": " + ChatFormatting.GOLD + trophyFishRarityIntegerMap.get(rarity);
		} else {
			return color + name + ": " + checkX;
		}
	}

	private void loadTrophyInformation(JsonObject trophyObject) {
		Map<String, List<Pair<TrophyFish.TrophyFishRarity, Integer>>> trophyFishRarityIntegerMap = new HashMap<>();
		totalCount = 0;
		for (Entry<String, JsonElement> stringJsonElementEntry : trophyObject.entrySet()) {
			String key = stringJsonElementEntry.getKey();
			if (key.equalsIgnoreCase("rewards") || key.equalsIgnoreCase("total_caught")) {
				if (key.equalsIgnoreCase("total_caught")) {
					totalCount = stringJsonElementEntry.getValue().getAsInt();
				}
				continue;
			}
			// Only per-fish counts are numbers; skip anything else Hypixel keeps here (e.g. "last_caught":
			// "obfuscated_fish_1/bronze").
			JsonElement countElement = stringJsonElementEntry.getValue();
			if (!countElement.isJsonPrimitive() || !countElement.getAsJsonPrimitive().isNumber()) continue;

			String[] s = key.split("_");
			String type = s[s.length - 1];
			TrophyFish.TrophyFishRarity trophyFishRarity;
			int value = stringJsonElementEntry.getValue().getAsInt();

			if (key.startsWith("golden_fish_")) {
				type = s[2];
			}
			try {
				trophyFishRarity = TrophyFish.TrophyFishRarity.valueOf(type.toUpperCase(Locale.US));
			} catch (IllegalArgumentException ignored) {
				total.put(WordUtils.capitalize(key), value);
				continue;
			}

			String replace = key.replace("_" + type, "");
			String name = WordUtils.capitalize(replace);
			List<Pair<TrophyFish.TrophyFishRarity, Integer>> pairs;

			if (trophyFishRarityIntegerMap.containsKey(name)) {
				pairs = trophyFishRarityIntegerMap.get(name);
			} else {
				pairs = new ArrayList<>();
			}
			pairs.add(Pair.of(trophyFishRarity, value));
			trophyFishRarityIntegerMap.put(name, pairs);
		}

		trophyFishRarityIntegerMap.forEach((name, pair) -> {
			if (!trophyFishList.containsKey(name)) {
				TrophyFish trophyFish = new TrophyFish(name, new HashMap<>());
				Integer fishTotal = total.get(name);
				trophyFish.addTotal(fishTotal == null ? 0 : fishTotal);
				for (Pair<TrophyFish.TrophyFishRarity, Integer> pair1 : pair) {
					trophyFish.add(pair1.getKey(), pair1.getValue());
				}
				trophyFishList.put(name, trophyFish);
			} else {
				TrophyFish trophyFish = trophyFishList.get(name);
				for (Pair<TrophyFish.TrophyFishRarity, Integer> pair1 : pair) {
					trophyFish.add(pair1.getKey(), pair1.getValue());
				}
			}
		});
	}

	private List<String> fixStringName(List<String> list) {
		List<String> fixedList = new ArrayList<>();
		for (String s : list) {
			fixedList.add(s.toLowerCase(Locale.US).replace(" ", "_"));
		}
		return fixedList;
	}

	private List<String> readDescriptionFromLore(List<String> lore) {
		List<String> description = new ArrayList<>();
		boolean found = false;

		for (String line : lore) {
			if (!found && line.startsWith("§7")) found = true;
			if (found && line.isEmpty()) break;

			if (found) {
				description.add(line);
			}
		}

		return description;
	}

	/** Resolves a trophy fish's icon via its {@code <FISH>_BRONZE} repo entry (the bronze-trophy skull texture). */
	private ItemStack resolveFishIcon(String name) {
		String repoName = name.toUpperCase(Locale.US).replace(" ", "_") + "_BRONZE";
		return repoIconOrNull(repoName);
	}

	private ItemStack repoIconOrNull(String internalname) {
		if (iconCache.containsKey(internalname)) return iconCache.get(internalname);
		JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(internalname);
		ItemStack icon = json != null ? NotEnoughUpdates.INSTANCE.manager.jsonToStack(json) : null;
		iconCache.put(internalname, icon);
		return icon;
	}

	private List<String> readLoreFromRepo(String name) {
		String repoName = name.toUpperCase(Locale.US).replace(" ", "_") + "_BRONZE";
		JsonObject jsonItem = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(repoName);

		List<String> list = new ArrayList<>();
		if (jsonItem != null && jsonItem.has("lore")) {
			for (JsonElement line : jsonItem.getAsJsonArray("lore")) {
				list.add(line.getAsString());
			}
		}

		return list;
	}
}
