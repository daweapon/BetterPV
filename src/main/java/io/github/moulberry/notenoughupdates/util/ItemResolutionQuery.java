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

package io.github.moulberry.notenoughupdates.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.moulberry.notenoughupdates.NEUManager;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Trimmed port of the Forge 1.8.9 {@code util.ItemResolutionQuery}. This is the piece
 * {@code NEUManager#getInternalnameFromNBT} (used heavily by {@code ProfileViewer} to resolve Hypixel item NBT
 * blobs to NEU "internal names") actually delegates to.
 *
 * <p>Dropped from the original: {@code withItemStack}/{@code withGuiContext}/{@code withCurrentGuiContext} and
 * the whole {@code resolveContextualName} path (bazaar/superpairs/catacombs-RNG-meter name resolution driven by
 * which vanilla {@code GuiChest} is currently open) - that's inherently GUI-layer functionality and none of the
 * ported data-layer callers ever set a GUI context anyway (same effective behaviour as before, since in the
 * original {@code guiContext} was only ever set from GUI code paths that are out of scope here).
 */
public class ItemResolutionQuery {

	private static final String EXTRA_ATTRIBUTES = "ExtraAttributes";
	private static final List<String> PET_RARITIES = Arrays.asList(
		"COMMON",
		"UNCOMMON",
		"RARE",
		"EPIC",
		"LEGENDARY",
		"MYTHIC"
	);
	private final NEUManager manager;
	private CompoundTag compound;
	private String knownInternalName;

	public ItemResolutionQuery(NEUManager manager) {
		this.manager = manager;
	}

	public ItemResolutionQuery withItemNBT(CompoundTag compound) {
		this.compound = compound;
		return this;
	}

	public ItemResolutionQuery withKnownInternalName(String knownInternalName) {
		this.knownInternalName = knownInternalName;
		return this;
	}

	public String resolveInternalName() {
		if (knownInternalName != null) {
			return knownInternalName;
		}
		String resolvedName = resolveFromSkyblock();
		if (resolvedName != null) {
			switch (resolvedName.intern()) {
				case "PET":
					resolvedName = resolvePetName();
					break;
				case "RUNE":
					resolvedName = resolveRuneName();
					break;
				case "ENCHANTED_BOOK":
					resolvedName = resolveEnchantedBookNameFromNBT();
					break;
				case "PARTY_HAT_CRAB":
				case "PARTY_HAT_CRAB_ANIMATED":
					resolvedName = resolveCrabHatName();
					break;
			}
		}

		return resolvedName;
	}

	public JsonObject resolveToItemListJson() {
		String internalName = resolveInternalName();
		if (internalName == null) {
			return null;
		}
		return manager.getItemInformation().get(internalName);
	}

	private String resolveCrabHatName() {
		int crabHatYear = getExtraAttributes().getInt("party_hat_year").orElse(0);
		String color = getExtraAttributes().getStringOr("party_hat_color", "");
		return "PARTY_HAT_CRAB_" + color.toUpperCase(Locale.ROOT) + (crabHatYear == 2022 ? "_ANIMATED" : "");
	}

	private String resolveEnchantedBookNameFromNBT() {
		CompoundTag enchantments = getExtraAttributes().getCompoundOrEmpty("enchantments");
		String enchantName = onlyElement(enchantments.keySet());
		if (enchantName == null || enchantName.isEmpty()) return null;
		return enchantName.toUpperCase(Locale.ROOT) + ";" + enchantments.getInt(enchantName).orElse(0);
	}

	private String resolveRuneName() {
		CompoundTag runes = getExtraAttributes().getCompoundOrEmpty("runes");
		String runeName = onlyElement(runes.keySet());
		if (runeName == null || runeName.isEmpty()) return null;
		return runeName.toUpperCase(Locale.ROOT) + "_RUNE;" + runes.getInt(runeName).orElse(0);
	}

	private String resolvePetName() {
		String petInfo = getExtraAttributes().getStringOr("petInfo", "");
		if (petInfo == null || petInfo.isEmpty()) return null;
		try {
			Gson gson = new Gson();
			JsonObject petInfoObject = gson.fromJson(petInfo, JsonObject.class);
			String petId = petInfoObject.get("type").getAsString();
			String petTier = petInfoObject.get("tier").getAsString();
			int rarityIndex = PET_RARITIES.indexOf(petTier);
			return petId.toUpperCase(Locale.ROOT) + ";" + rarityIndex;
		} catch (JsonParseException | ClassCastException ex) {
			/* This happens if Hypixel changed the pet json format;
				 I still log this exception, since this case *is* exceptional and cannot easily be recovered from */
			ex.printStackTrace();
			return null;
		}
	}

	private CompoundTag getExtraAttributes() {
		if (compound == null) return new CompoundTag();
		return compound.getCompoundOrEmpty(EXTRA_ATTRIBUTES);
	}

	private String resolveFromSkyblock() {
		String internalName = getExtraAttributes().getStringOr("id", "");
		if (internalName == null || internalName.isEmpty()) return null;
		return internalName.toUpperCase(Locale.ROOT).replace(':', '-');
	}

	private static String onlyElement(java.util.Set<String> set) {
		if (set.size() != 1) return null;
		return set.iterator().next();
	}
}
