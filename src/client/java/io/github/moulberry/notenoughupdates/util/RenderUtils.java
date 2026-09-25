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

package io.github.moulberry.notenoughupdates.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only rendering helpers for the profile viewer, the client-side sibling of {@link Utils} (which lives
 * in {@code src/main} and can't touch client classes). Every helper takes the {@code GuiGraphicsExtractor}
 * first; the old immediate-mode GL calls are gone.
 */
public class RenderUtils {

	public static void drawTexturedRect(GuiGraphicsExtractor graphics, Identifier texture, float x, float y, float width, float height) {
		drawTexturedRect(graphics, texture, x, y, width, height, 0, 1, 0, 1);
	}

	public static void drawTexturedRect(
		GuiGraphicsExtractor graphics,
		Identifier texture,
		float x,
		float y,
		float width,
		float height,
		float uMin,
		float uMax,
		float vMin,
		float vMax
	) {
		graphics.blit(texture, (int) x, (int) y, (int) (x + width), (int) (y + height), uMin, uMax, vMin, vMax);
	}

	public static void drawItemStack(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		if (stack == null || stack.isEmpty()) return;
		graphics.item(stack, x, y);
	}

	/** Same as the non-linear draw; kept so ported call sites read the same. */
	/** Like {@link #drawItemStack} but also draws the stack count (and durability bar) over the icon. */
	public static void drawItemStackWithCount(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		if (stack == null || stack.isEmpty()) return;
		graphics.item(stack, x, y);
		graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y);
	}

	public static void drawItemStackLinear(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		drawItemStack(graphics, stack, x, y);
	}

	/** Draws a skill-row icon at 70% size with its top-left at (x, y), as the original skill rows did. */
	public static void drawSkillIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(0.7f, 0.7f);
		drawItemStack(graphics, stack, 0, 0);
		graphics.pose().popMatrix();
	}

	/**
	 * {@code GuiGraphicsExtractor#text} does nothing when the colour's alpha is 0, and the old bare-RGB colours
	 * (0xFFFFFF, 0x3FE0D0) have no alpha byte. Text helpers route colours through this to make them opaque;
	 * colours that already have an alpha are left alone.
	 */
	public static int opaque(int colour) {
		return (colour & 0xFF000000) == 0 ? (colour | 0xFF000000) : colour;
	}

	/** Plays the button click sound. Here rather than in {@code Utils} because the sound manager is client-only. */
	public static void playPressSound() {
		Minecraft.getInstance()
			.getSoundManager()
			.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	/** Like {@code graphics.text(font, str, x, y, colour, shadow)}, but with the alpha fix from {@link #opaque(int)}. */
	public static void text(GuiGraphicsExtractor graphics, Font font, String str, int x, int y, int colour, boolean shadow) {
		graphics.text(font, str, x, y, opaque(colour), shadow);
	}

	public static void drawStringCentered(GuiGraphicsExtractor graphics, String str, Font font, float x, float y, boolean shadow, int colour) {
		int width = font.width(str);
		graphics.text(font, str, Math.round(x - width / 2f), Math.round(y - font.lineHeight / 2f), opaque(colour == 0 ? 0xFFFFFF : colour), shadow);
	}

	/** The text as a Component with a rainbow that scrolls along it over time, one hue per character. */
	public static Component rainbow(String plain, long timeMillis) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		float offset = (timeMillis % 3000) / 3000f;
		for (int i = 0; i < plain.length(); i++) {
			int rgb = java.awt.Color.HSBtoRGB((offset + i * 0.09f) % 1f, 0.7f, 1f) & 0xFFFFFF;
			out.append(Component.literal(String.valueOf(plain.charAt(i)))
				.withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb))));
		}
		return out;
	}

	/** {@link #drawStringCentered} for a rainbow-coloured, plain (no § codes) string. */
	public static void drawRainbowCentered(GuiGraphicsExtractor graphics, String plain, Font font, float x, float y, boolean shadow, long timeMillis) {
		Component text = rainbow(plain, timeMillis);
		graphics.text(font, text, Math.round(x - font.width(text) / 2f), Math.round(y - font.lineHeight / 2f), opaque(0xFFFFFF), shadow);
	}

	public static void drawStringCenteredScaledMaxWidth(
		GuiGraphicsExtractor graphics,
		String str,
		Font font,
		float x,
		float y,
		boolean shadow,
		int maxWidth,
		int colour
	) {
		int width = font.width(str);
		float scale = width > maxWidth ? maxWidth / (float) width : 1f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, str, Math.round(-width / 2f), Math.round(-font.lineHeight / 2f), opaque(colour), shadow);
		graphics.pose().popMatrix();
	}

	/**
	 * Draws {@code first} left-aligned and {@code second} right-aligned in a {@code length}-pixel span at (x, y).
	 * If they don't fit side by side they're drawn as one line, shrunk to fit.
	 */
	public static void renderAlignedString(GuiGraphicsExtractor graphics, String first, String second, float x, float y, int length) {
		Font font = Minecraft.getInstance().font;
		if (font.width(first + " " + second) >= length) {
			drawStringCenteredScaledMaxWidth(graphics, first + " " + second, font, x + length / 2f, y + font.lineHeight / 2f, true, length, 0xFFFFFF);
			return;
		}
		graphics.text(font, first, Math.round(x), Math.round(y), opaque(0xFFFFFF), true);
		int secondWidth = font.width(second);
		graphics.text(font, second, Math.round(x + length - secondWidth), Math.round(y), opaque(0xFFFFFF), true);
	}

	/** Queues a plain-string tooltip for this frame, positioned near the mouse, using the default client font. */
	public static void drawHoveringText(GuiGraphicsExtractor graphics, List<String> textLines, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		List<Component> components = new ArrayList<>(textLines.size());
		for (String line : textLines) {
			components.add(Component.literal(line));
		}
		graphics.setComponentTooltipForNextFrame(font, components, mouseX, mouseY);
	}
}
