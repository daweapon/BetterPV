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

import io.github.moulberry.notenoughupdates.profileviewer.chocolate.ChocolateInfoPage;
import io.github.moulberry.notenoughupdates.profileviewer.chocolate.FactionsPage;
import io.github.moulberry.notenoughupdates.profileviewer.chocolate.RabbitsPage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Chocolate Factory" tab, following SkyBlockPv's categories: the factory itself (employees, upgrades, information,
 * rabbits per rarity), Hoppity's collection (every rabbit, like the attributes page) and the rabbit factions.
 */
public class ChocolateFactoryPage extends CategorizedPage {

	public ChocolateFactoryPage(GuiProfileViewer instance) {
		super(instance);
		add("Chocolate Factory", () -> new ItemStack(Items.COOKIE), new ChocolateInfoPage(instance));
		add("Hoppity's Collection", () -> PvData.item("HOPPITY_NPC"), new RabbitsPage(instance));
		add("Factions", ChocolateFactoryPage::ominousBanner, new FactionsPage(instance));
	}

	private static ItemStack ominousBanner() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) return new ItemStack(Items.WHITE_BANNER);
		return Raid.getOminousBannerInstance(minecraft.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
	}
}
