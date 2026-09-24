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

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trimmed port of the Forge 1.8.9 {@code util.ItemUtils}. Only the NBT-lore helpers used by
 * {@code ItemResolutionQuery} are ported here; the original also had recipe/enchant helpers that depend on
 * repo/config machinery out of scope for this pass.
 *
 * API mapping note: this class historically stored Hypixel's item metadata inside the *vanilla* item NBT tag
 * (old {@code net.minecraft.nbt.NBTTagCompound}/{@code NBTTagList}). In modern Minecraft, vanilla ItemStacks no
 * longer carry a free-form NBT "tag" (they use typed DataComponents instead) - but the Hypixel API still hands us
 * raw NBT blobs (via {@code net.minecraft.nbt.NbtIo}) representing the *server's* view of the item, which we
 * parse directly with {@link net.minecraft.nbt.CompoundTag}/{@link net.minecraft.nbt.ListTag}. That's why this
 * class still operates on CompoundTag rather than ItemStack components.
 */
public class ItemUtils {
	public static List<String> getLore(CompoundTag tagCompound) {
		if (tagCompound == null) {
			return Collections.emptyList();
		}
		var tagList = tagCompound.getCompoundOrEmpty("display").getListOrEmpty("Lore");
		List<String> list = new ArrayList<>();
		for (int i = 0; i < tagList.size(); i++) {
			list.add(tagList.getStringOr(i, ""));
		}
		return list;
	}

	public static String getDisplayName(CompoundTag compound) {
		if (compound == null) return null;
		String string = compound.getCompoundOrEmpty("display").getStringOr("Name", "");
		if (string == null || string.isEmpty())
			return null;
		return string;
	}
}
