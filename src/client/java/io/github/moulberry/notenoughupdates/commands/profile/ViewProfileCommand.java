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

import io.github.moulberry.notenoughupdates.client.McCompat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** {@code /neuprofile [player]}, a plain Brigadier client command. Tab completion uses the online player names. */
public class ViewProfileCommand {

	public static final Consumer<FabricClientCommandSource> RUNNABLE = source -> openProfile(source, source.getPlayer().getGameProfile().name());

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		dispatcher.register(
			ClientCommands.literal(name)
				.executes(ctx -> {
					RUNNABLE.accept(ctx.getSource());
					return 0;
				})
				.then(
					ClientCommands.argument("player", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
							ctx.getSource().getCustomTabSuggestions(), builder
						))
						.executes(ctx -> {
							openProfile(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
							return 0;
						})
				)
		);
	}

	public static void openProfile(FabricClientCommandSource source, String playerName) {
		NotEnoughUpdates.INSTANCE.getProfileViewer().getProfileByName(playerName, profile -> {
			// This callback runs on a network thread, so anything touching the GUI has to go through Client#execute.
			source.getClient().execute(() -> {
				if (profile == null) {
					source.sendError(Component.literal(ChatFormatting.RED + "Unknown player, or the Better PV server couldn't be reached."));
				} else {
					profile.resetCache();
						GuiProfileViewer.applyOpeningTab();
					McCompat.setScreen(source.getClient(), new GuiProfileViewer(profile));
				}
			});
		});
	}
}
