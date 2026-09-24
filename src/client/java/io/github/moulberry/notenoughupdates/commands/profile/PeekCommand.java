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

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.PlayerStats;
import io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Port of the Forge 1.8.9 {@code commands.profile.PeekCommand} ({@code /peek [player]}): prints a quick chat
 * summary of a player's Skyblock stats without opening the full profile viewer GUI.
 *
 * <p>TODO(fabric-port) - simplified vs. the original:
 * <ul>
 *   <li>{@code sendChatMessage} with an id + {@code printChatMessageWithOptionalDeletion} was used to show a
 *   "Getting player information..." status line and then overwrite it in place; that specific
 *   delete-and-replace-by-id chat API doesn't have a direct, stable equivalent to verify against here, so this
 *   just skips the interim status line and prints the final summary once the async profile fetch resolves.</li>
 *   <li>The original polled with a 10-second-timeout retry loop waiting for {@code getProfileInformation} to be
 *   non-null (since the underlying HTTP fetch is itself async/cached). This makes a single attempt instead; if
 *   the profile info isn't ready yet it reports that instead of retrying.</li>
 *   <li>The "special bois" chroma-name easter egg and pet-rarity colour lookup ({@code petRarityToColourMap})
 *   aren't ported (data-layer pieces out of scope here), so the pet line always renders in light purple.</li>
 * </ul>
 */
public class PeekCommand {

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
			ClientCommands.literal("peek")
				.executes(ctx -> {
					run(ctx.getSource(), ctx.getSource().getPlayer().getGameProfile().name());
					return 0;
				})
				.then(
					ClientCommands.argument("player", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
							ctx.getSource().getCustomTabSuggestions(), builder
						))
						.executes(ctx -> {
							run(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
							return 0;
						})
				)
		);
	}

	private static void run(FabricClientCommandSource source, String name) {
		source.sendFeedback(Component.literal(ChatFormatting.YELLOW + "[PEEK] Getting player information..."));
		NotEnoughUpdates.INSTANCE.getProfileViewer().getProfileByName(name, profile -> {
			// getProfileByName's callback runs on an async network-request thread, not the render thread - every
			// call into Minecraft/chat state here must be dispatched via Client#execute, or it can crash natively.
			source.getClient().execute(() -> {
				if (profile == null) {
					source.sendError(Component.literal(ChatFormatting.RED + "[PEEK] Unknown player or the api is down."));
					return;
				}
				profile.resetCache();
				printSummary(source, name, profile);
			});
		});
	}

	private static void printSummary(FabricClientCommandSource source, String name, ProfileViewer.Profile profile) {
		String g = ChatFormatting.GRAY.toString();

		JsonObject profileInfo = profile.getProfileInformation(null);
		if (profileInfo == null) {
			source.sendError(Component.literal(ChatFormatting.RED + "[PEEK] Couldn't load profile info (not ready yet or API disabled)."));
			return;
		}

		PlayerStats.Stats stats = profile.getStats(null);
		Map<String, ProfileViewer.Level> skyblockInfo = profile.getSkyblockInfo(null);

		source.sendFeedback(
			Component.literal(
				ChatFormatting.GREEN + " " + ChatFormatting.STRIKETHROUGH + "-=-" + ChatFormatting.RESET + ChatFormatting.GREEN + " " +
					Utils.getElementAsString(profile.getHypixelProfile() == null ? null : profile.getHypixelProfile().get("displayname"), name) +
					"'s Info " + ChatFormatting.STRIKETHROUGH + "-=-"
			)
		);

		if (skyblockInfo == null) {
			source.sendFeedback(Component.literal(ChatFormatting.YELLOW + "Skills API disabled!"));
		} else {
			List<String> skills = Arrays.asList("taming", "mining", "foraging", "enchanting", "farming", "combat", "fishing", "alchemy", "carpentry");
			float totalSkillLVL = 0;
			for (String skillName : skills) {
				ProfileViewer.Level level = skyblockInfo.get(skillName);
				if (level != null) totalSkillLVL += level.level;
			}
			float avgSkillLVL = totalSkillLVL / skills.size();
			int cata = skyblockInfo.containsKey("catacombs") ? (int) skyblockInfo.get("catacombs").level : 0;

			source.sendFeedback(
				Component.literal(g + "Average skill level: " + ChatFormatting.YELLOW + (int) Math.floor(avgSkillLVL) + g + " - Catacombs: " + ChatFormatting.YELLOW + cata)
			);
		}

		if (stats == null) {
			source.sendFeedback(Component.literal(ChatFormatting.YELLOW + "Skills, collection and/or inventory apis disabled!"));
		} else {
			int health = (int) stats.get("health");
			int defence = (int) stats.get("defence");
			int strength = (int) stats.get("strength");
			int intelligence = (int) stats.get("intelligence");

			source.sendFeedback(
				Component.literal(
					g + "Stats  : " + ChatFormatting.RED + health + "❤ " + ChatFormatting.GREEN + defence + "❈ " +
						ChatFormatting.RED + strength + "❁ " + ChatFormatting.AQUA + intelligence + "✎ "
				)
			);
		}

		float bankBalance = Utils.getElementAsFloat(Utils.getElement(profileInfo, "banking.balance"), -1);
		float purseBalance = Utils.getElementAsFloat(Utils.getElement(profileInfo, "coin_purse"), 0);
		long networth = profile.getNetWorth(null);
		float money = Math.max(bankBalance + purseBalance, networth);

		source.sendFeedback(
			Component.literal(
				g + "Purse: " + ChatFormatting.GOLD + io.github.moulberry.notenoughupdates.core.util.StringUtils.shortNumberFormat(purseBalance, 0) +
					g + " - Bank: " + (bankBalance == -1
					? ChatFormatting.YELLOW + "N/A"
					: ChatFormatting.GOLD + io.github.moulberry.notenoughupdates.core.util.StringUtils.shortNumberFormat(bankBalance, 0)) +
					(networth > 0 ? g + " - Net: " + ChatFormatting.GOLD + io.github.moulberry.notenoughupdates.core.util.StringUtils.shortNumberFormat(networth, 0) : "")
			)
		);

		JsonObject petsInfo = profile.getPetsInfo(null);
		String activePet = Utils.getElementAsString(Utils.getElement(petsInfo, "active_pet.type"), "None Active");
		source.sendFeedback(
			Component.literal(g + "Pet    : " + ChatFormatting.LIGHT_PURPLE + activePet.replace("_", " "))
		);
	}
}
