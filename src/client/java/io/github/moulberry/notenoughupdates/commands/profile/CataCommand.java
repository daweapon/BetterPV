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

package io.github.moulberry.notenoughupdates.commands.profile;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Port of the Forge 1.8.9 {@code commands.profile.CataCommand} ({@code /cata [player]}), a shortcut that opens
 * the profile viewer straight to the Dungeoneering tab.
 */
public class CataCommand {
	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
			ClientCommands.literal("cata")
				.executes(ctx -> {
					GuiProfileViewer.currentPage = GuiProfileViewer.ProfileViewerPage.DUNGEON;
					ViewProfileCommand.RUNNABLE.accept(ctx.getSource());
					return 0;
				})
				.then(
					ClientCommands.argument("player", StringArgumentType.word())
						.executes(ctx -> {
							GuiProfileViewer.currentPage = GuiProfileViewer.ProfileViewerPage.DUNGEON;
							ViewProfileCommand.openProfile(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
							return 0;
						})
				)
		);
	}
}
