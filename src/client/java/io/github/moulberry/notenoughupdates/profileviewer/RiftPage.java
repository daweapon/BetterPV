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

import io.github.moulberry.notenoughupdates.profileviewer.rift.RiftEnderChestPage;
import io.github.moulberry.notenoughupdates.profileviewer.rift.RiftInventoryPage;
import io.github.moulberry.notenoughupdates.profileviewer.rift.RiftMainPage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Rift" tab, following SkyBlockPv's rift categories: general rift progress and timecharms, the rift inventory,
 * and the rift ender chest.
 */
public class RiftPage extends CategorizedPage {

	public RiftPage(GuiProfileViewer instance) {
		super(instance);
		add("Rift", () -> new ItemStack(Items.LILAC), new RiftMainPage(instance));
		add("Inventory", () -> new ItemStack(Items.CHEST), new RiftInventoryPage(instance));
		add("Ender Chest", () -> new ItemStack(Items.ENDER_CHEST), new RiftEnderChestPage(instance));
	}
}
