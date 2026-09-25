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

package io.github.moulberry.notenoughupdates.client;

import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.util.BpvConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Right-clicking a player name in the chat opens their profile, while on SkyBlock. Right click is used because
 * left click is already taken by clickable chat text such as the friends list.
 *
 * <p>Vanilla only hit-tests chat text that carries a click event, and plain names (guild chat, most messages)
 * have none. So the chat lines are run through vanilla's own click-target finder with every name tagged with a
 * click event holding that name; the name under the cursor is then read back from the result. Names also get a
 * hover hint as messages arrive.
 */
public class ChatProfileClick {
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !handleClick(client, event));
			}
		});
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
			overlay || !BpvConfig.isChatRightClick() || !isOnSkyblock(Minecraft.getInstance()) ? message : addHoverHints(message));
	}

	private static boolean handleClick(Minecraft client, MouseButtonEvent event) {
		if (event.button() != 1 || !BpvConfig.isChatRightClick() || !isOnSkyblock(client)) return false;
		String name = nameAt(client, (int) event.x(), (int) event.y());
		if (name == null) return false;
		openProfile(client, name);
		return true;
	}

	/** SkyBlock shows a "SKYBLOCK" sidebar title in every one of its areas. */
	private static boolean isOnSkyblock(Minecraft client) {
		if (client.level == null) return false;
		StringBuilder seen = new StringBuilder();
		boolean result = false;
		for (DisplaySlot slot : DisplaySlot.values()) {
			Objective objective = client.level.getScoreboard().getDisplayObjective(slot);
			if (objective == null) continue;
			String title = ChatFormatting.stripFormatting(objective.getDisplayName().getString());
			seen.append(slot).append('=').append(objective.getName()).append('/').append(title).append(' ');
			if (title != null && title.toUpperCase().contains("SKYBLOCK")) result = true;
		}
		// Logged when it changes, to diagnose the check from latest.log.
		String state = result + " " + seen;
		if (!state.equals(lastDetection)) {
			lastDetection = state;
			NotEnoughUpdates.LOGGER.info("Chat profile click SkyBlock check: {}", state);
		}
		return result;
	}

	private static String lastDetection = "";

	private static String nameAt(Minecraft client, int mouseX, int mouseY) {
		ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(client.font, mouseX, mouseY);
		ChatComponent chat = client.gui.getChat();
		chat.captureClickableText(new NameTagger(finder), client.getWindow().getGuiScaledHeight(), client.gui.getGuiTicks(),
			ChatComponent.DisplayMode.FOREGROUND);
		Style style = finder.result();
		if (style != null && style.getClickEvent() instanceof ClickEvent.CopyToClipboard copy) return copy.value();
		return null;
	}

	private static void openProfile(Minecraft client, String name) {
		NotEnoughUpdates.INSTANCE.getProfileViewer().getProfileByName(name, profile -> client.execute(() -> {
			if (profile == null) {
				client.player.sendSystemMessage(Component.literal(ChatFormatting.RED + "Unknown player, or the Better PV server couldn't be reached."));
			} else {
				profile.resetCache();
				io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer.applyOpeningTab();
				client.setScreen(new io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer(profile));
			}
		}));
	}

	// ---- name detection ----

	private static boolean isNameChar(int c) {
		return c < 128 && (Character.isLetterOrDigit(c) || c == '_');
	}

	private static String hoverText(Style style) {
		return style.getHoverEvent() instanceof HoverEvent.ShowText show ? show.value().getString() : null;
	}

	/**
	 * Whether text[start, end) is a player name: a valid username that is either named in the hover text Hypixel
	 * put on it (friends list, public chat), or written like a sender or a join/leave/location line. Rank and level
	 * brackets such as "[MVP+]" are skipped.
	 */
	private static boolean isName(String text, int start, int end, String hover) {
		int length = end - start;
		String word = text.substring(start, end);
		if (length < 3 || length > 16 || word.chars().allMatch(Character::isDigit)) return false;
		if (start > 0 && text.charAt(start - 1) == '[') return false;
		if (hover != null && hover.contains(word)) return true;
		return end < text.length() && text.charAt(end) == ':'
			|| text.startsWith(" is in ", end) || text.startsWith(" joined", end) || text.startsWith(" left", end);
	}

	/** Start/end pairs of the names in {@code text}; {@code hovers} is the hover text of each char, or null. */
	private static List<int[]> findNames(String text, String[] hovers) {
		List<int[]> names = new ArrayList<>();
		int i = 0, n = text.length();
		while (i < n) {
			if (!isNameChar(text.charAt(i))) {
				i++;
				continue;
			}
			int start = i;
			while (i < n && isNameChar(text.charAt(i))) i++;
			if (isName(text, start, i, hovers[start])) names.add(new int[]{start, i});
		}
		return names;
	}

	// ---- hover hint ----

	/** Adds the right-click hint to the hover text of every name in an incoming message. */
	private static Component addHoverHints(Component message) {
		List<String> texts = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		message.visit((style, string) -> {
			splitLegacyCodes(string, style, texts, styles);
			return Optional.empty();
		}, Style.EMPTY);

		StringBuilder full = new StringBuilder();
		List<String> hoverList = new ArrayList<>();
		for (int s = 0; s < texts.size(); s++) {
			String hover = hoverText(styles.get(s));
			for (int k = 0; k < texts.get(s).length(); k++) hoverList.add(hover);
			full.append(texts.get(s));
		}
		List<int[]> names = findNames(full.toString(), hoverList.toArray(new String[0]));
		if (names.isEmpty()) return message;

		MutableComponent result = Component.empty();
		int offset = 0;
		for (int s = 0; s < texts.size(); s++) {
			String text = texts.get(s);
			Style style = styles.get(s);
			int segEnd = offset + text.length();
			int pos = 0;
			for (int[] name : names) {
				// Names spanning several styled parts are left alone.
				if (name[0] < offset || name[1] > segEnd) continue;
				if (name[0] - offset > pos) result.append(Component.literal(text.substring(pos, name[0] - offset)).withStyle(style));
				result.append(Component.literal(text.substring(name[0] - offset, name[1] - offset)).withStyle(withHint(style)));
				pos = name[1] - offset;
			}
			if (pos < text.length()) result.append(Component.literal(text.substring(pos)).withStyle(style));
			offset = segEnd;
		}
		return result;
	}

	/**
	 * Hypixel writes colours as legacy section-sign codes inside the text. Names are split out of that text, and a
	 * code cut off from the text it colours would print as a stray letter, so the codes become real styles first.
	 */
	private static void splitLegacyCodes(String string, Style base, List<String> texts, List<Style> styles) {
		Style current = base;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c == '§' && i + 1 < string.length()) {
				ChatFormatting format = ChatFormatting.getByCode(string.charAt(++i));
				if (run.length() > 0) {
					texts.add(run.toString());
					styles.add(current);
					run.setLength(0);
				}
				if (format == ChatFormatting.RESET) current = base;
				else if (format != null) current = current.applyLegacyFormat(format);
			} else {
				run.append(c);
			}
		}
		if (run.length() > 0) {
			texts.add(run.toString());
			styles.add(current);
		}
	}

	private static Style withHint(Style style) {
		MutableComponent hint = Component.empty();
		if (style.getHoverEvent() instanceof HoverEvent.ShowText show) hint.append(show.value()).append(Component.literal("\n"));
		hint.append(Component.literal("Right-click to open Better PV profile").withStyle(ChatFormatting.YELLOW))
			.append(Component.literal("\n"))
			.append(Component.literal("(may take a second or two to load)").withStyle(ChatFormatting.GRAY));
		return style.withHoverEvent(new HoverEvent.ShowText(hint));
	}

	// ---- click target ----

	/** Forwards to the finder, but with each name tagged with a click event carrying it. */
	private record NameTagger(ActiveTextCollector delegate) implements ActiveTextCollector {
		@Override
		public Parameters defaultParameters() {
			return delegate.defaultParameters();
		}

		@Override
		public void defaultParameters(Parameters parameters) {
			delegate.defaultParameters(parameters);
		}

		@Override
		public void acceptScrolling(Component text, int x, int y, int left, int right, int top, Parameters parameters) {
			delegate.acceptScrolling(text, x, y, left, right, top, parameters);
		}

		@Override
		public void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence text) {
			List<Style> styles = new ArrayList<>();
			List<Integer> codepoints = new ArrayList<>();
			StringBuilder chars = new StringBuilder();
			text.accept((index, style, codepoint) -> {
				styles.add(style);
				codepoints.add(codepoint);
				// One char per code point so indices line up with the lists.
				chars.append(codepoint < 0x10000 ? (char) (int) codepoint : '?');
				return true;
			});
			int n = codepoints.size();
			String[] hovers = new String[n];
			for (int k = 0; k < n; k++) hovers[k] = hoverText(styles.get(k));
			Style[] tagged = new Style[n];
			for (int[] name : findNames(chars.toString(), hovers)) {
				ClickEvent tag = new ClickEvent.CopyToClipboard(chars.substring(name[0], name[1]));
				for (int k = name[0]; k < name[1]; k++) tagged[k] = styles.get(k).withClickEvent(tag);
			}
			delegate.accept(alignment, x, y, parameters, sink -> {
				for (int k = 0; k < n; k++) {
					if (!sink.accept(k, tagged[k] != null ? tagged[k] : styles.get(k), codepoints.get(k))) return false;
				}
				return true;
			});
		}
	}
}
