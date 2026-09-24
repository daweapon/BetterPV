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

import io.github.moulberry.notenoughupdates.profileviewer.farming.ComposterPage;
import io.github.moulberry.notenoughupdates.profileviewer.farming.CropsPage;
import io.github.moulberry.notenoughupdates.profileviewer.farming.FarmingInfoPage;
import io.github.moulberry.notenoughupdates.profileviewer.farming.MutationsPage;
import io.github.moulberry.notenoughupdates.profileviewer.farming.VisitorsPage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Farming" tab, following SkyBlockPv's farming categories: gear, contests, chips and information; garden visitors;
 * crop tools, upgrades and milestones; greenhouse mutations; and the composter. The garden itself comes from
 * {@code v2/skyblock/garden} (see {@link ProfileViewer.Profile#getGardenInfo}).
 */
public class FarmingPage extends CategorizedPage {

	public FarmingPage(GuiProfileViewer instance) {
		super(instance);
		add("Farming", () -> new ItemStack(Items.WHEAT), new FarmingInfoPage(instance));
		add("Visitors", () -> new ItemStack(Items.VILLAGER_SPAWN_EGG), new VisitorsPage(instance));
		add("Crops", () -> new ItemStack(Items.CARROT), new CropsPage(instance));
		add("Mutations", () -> PvData.item("LONELILY"), new MutationsPage(instance));
		add("Composter", () -> PvData.item("COMPOST"), new ComposterPage(instance));
	}
}
