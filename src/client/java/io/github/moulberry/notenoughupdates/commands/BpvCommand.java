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

package io.github.moulberry.notenoughupdates.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.moulberry.notenoughupdates.gui.ApiKeyScreen;
import io.github.moulberry.notenoughupdates.util.ApiKeyConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /bpv} - opens the {@link ApiKeyScreen} GUI for setting an optional personal Hypixel developer API key
 * (without one, lookups go through the Better PV backend). {@code /bpv setapi <key>} sets it directly from
 * chat/a command block without opening the GUI.
 */
public class BpvCommand {
	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
			ClientCommands.literal("bpv")
				.executes(ctx -> {
					// Deferred a tick via Client#execute: vanilla's ChatScreen closes itself (setScreen(null)) right
					// after a typed command finishes executing, which would immediately clobber a setScreen() call
					// made synchronously from within this handler.
					var client = ctx.getSource().getClient();
					client.execute(() -> client.setScreen(new ApiKeyScreen(client.screen)));
					return 0;
				})
				.then(
					ClientCommands.literal("setapi")
						.then(
							ClientCommands.argument("key", StringArgumentType.greedyString())
								.executes(ctx -> {
									String key = StringArgumentType.getString(ctx, "key").trim();
									ApiKeyConfig.setApiKey(key);
									ctx.getSource().sendFeedback(Component.literal(
										key.isEmpty()
											? ChatFormatting.YELLOW + "Hypixel API key cleared - using the Better PV server."
											: ChatFormatting.GREEN + "Hypixel API key saved."
									));
									return 0;
								})
						)
				)
		);
	}
}
