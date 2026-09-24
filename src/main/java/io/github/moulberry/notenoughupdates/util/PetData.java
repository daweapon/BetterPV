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

import net.minecraft.ChatFormatting;

/**
 * Trimmed port of the Forge 1.8.9 {@code util.PetData}: only the {@link Rarity} enum is ported, since it's the
 * only part {@code PlayerStats}/{@code ProfileViewer}'s pet-levelling logic (formerly
 * {@code GuiProfileViewer.getPetLevel}) actually needs. The rest of the original class dealt with pet tooltip
 * regexes used by GUI code, which is out of scope for this pass.
 *
 * API mapping note: old {@code net.minecraft.util.EnumChatFormatting} -> new {@code net.minecraft.ChatFormatting}
 * (same constant names).
 */
public class PetData {
	public enum Rarity {
		COMMON(0, 0, 1, ChatFormatting.WHITE),
		UNCOMMON(6, 1, 2, ChatFormatting.GREEN),
		RARE(11, 2, 3, ChatFormatting.BLUE),
		EPIC(16, 3, 4, ChatFormatting.DARK_PURPLE),
		LEGENDARY(20, 4, 5, ChatFormatting.GOLD),
		MYTHIC(20, 5, 5, ChatFormatting.LIGHT_PURPLE);

		public final int petOffset;
		public final ChatFormatting chatFormatting;
		public final int petId;
		public final int beastcreatMultiplyer;

		Rarity(int petOffset, int petId, int beastcreatMultiplyer, ChatFormatting chatFormatting) {
			this.chatFormatting = chatFormatting;
			this.petOffset = petOffset;
			this.petId = petId;
			this.beastcreatMultiplyer = beastcreatMultiplyer;
		}
	}
}
