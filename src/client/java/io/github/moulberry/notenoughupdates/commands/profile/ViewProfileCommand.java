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
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Port of the Forge 1.8.9 {@code commands.profile.ViewProfileCommand} ({@code /neuprofile [player]}).
 *
 * <p>API mapping notes: client-side commands in Forge 1.8.9 subclassed {@code ClientCommandBase}
 * ({@code net.minecraft.command.CommandBase}) and were registered on {@code ClientCommandHandler.instance}.
 * Modern Fabric has no equivalent base class at all - client commands are plain Brigadier command trees
 * registered through Fabric API's {@code ClientCommandRegistrationCallback.EVENT}, using
 * {@code ClientCommands.literal(...)}/{@code ClientCommands.argument(...)} (drop-in replacements for
 * {@code LiteralArgumentBuilder.literal}/{@code RequiredArgumentBuilder.argument} that use
 * {@link FabricClientCommandSource} as the source type) and registered against the
 * {@code CommandDispatcher<FabricClientCommandSource>} passed into the callback. There is no tab-completion
 * helper method to override; Brigadier's own {@code suggests(...)} takes over that role, fed from
 * {@link FabricClientCommandSource#getCustomTabSuggestions()} (the same online-player-name list vanilla
 * client commands like {@code /msg} tab-complete against).
 */
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
			// The getProfileByName callback runs on an async network-request thread, not the render thread -
			// every call into Minecraft/GUI state here must be dispatched via Client#execute, or it can crash
			// natively (this previously crashed the game when a lookup failed off-thread).
			source.getClient().execute(() -> {
				if (profile == null) {
					source.sendError(Component.literal(ChatFormatting.RED + "Unknown player, or the Better PV server couldn't be reached."));
				} else {
					profile.resetCache();
					source.getClient().setScreen(new GuiProfileViewer(profile));
				}
			});
		});
	}
}
