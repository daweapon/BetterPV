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

/**
 * Port of the Forge 1.8.9 {@code commands.profile.PvCommand} ({@code /pv [player]}).
 *
 * <p>TODO(fabric-port): the original only intercepted {@code /pv} locally while actually inside Skyblock
 * ({@code NotEnoughUpdates.INSTANCE.isOnSkyblock()}), forwarding the chat message to the server otherwise (so
 * Hypixel's own {@code /pv} - unrelated to this mod - kept working elsewhere). {@code isOnSkyblock()} wasn't
 * ported in the data-layer pass this GUI port builds on, so for now this always opens the local profile viewer
 * regardless of location.
 */
public class PvCommand {
	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
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
