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
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** {@code /pv [player]}: opens the profile viewer. */
public class PvCommand {
	/**
	 * Registers {@code /pv}, replacing one another mod registered first. Brigadier merges same-named commands, so
	 * the old node is removed. Runs in a late registration phase (see NotEnoughUpdatesClient).
	 */
	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("pv"));
		dispatcher.register(
			ClientCommands.literal("pv")
				.executes(ctx -> {
					ViewProfileCommand.RUNNABLE.accept(ctx.getSource());
					return 0;
				})
				.then(
					ClientCommands.argument("player", StringArgumentType.word())
						.executes(ctx -> {
							ViewProfileCommand.openProfile(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
							return 0;
						})
				)
		);
	}
}
