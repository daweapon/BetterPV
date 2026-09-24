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
 * Client-only rendering helpers used by the profile viewer GUI port. This is the client-side sibling of
 * {@link Utils}: it exists because {@code src/main/java} is shared with a (theoretical) dedicated-server
 * environment and can't reference client-only Minecraft classes like {@code Minecraft}/{@code Font}/
 * {@code GuiGraphicsExtractor}, while {@code src/client/java} can.
 *
 * <p>API mapping notes (Forge 1.8.9 {@code Utils} draw helpers -&gt; Fabric 26.1.2):
 * <ul>
 *   <li>All immediate-mode GL draw calls (Tessellator/GlStateManager/{@code drawTexturedModalRect}) are gone.
 *   Screens now build up a list of "render state" objects on a {@code GuiGraphicsExtractor} (passed into every
 *   {@code extractRenderState}/page-draw call), which the renderer consumes later in the frame. Every helper here
 *   takes that object as its first parameter.</li>
 *   <li>{@code FontRenderer#drawString} -&gt; {@code GuiGraphicsExtractor#text(Font, ...)}.</li>
 *   <li>{@code RenderItem#renderItemAndEffectIntoGUI}/{@code drawItemStack} -&gt; {@code GuiGraphicsExtractor#item(ItemStack, x, y)}.</li>
 *   <li>{@code GuiScreen#drawHoveringText}/tooltips -&gt; {@code GuiGraphicsExtractor#setComponentTooltipForNextFrame(...)},
 *   which defers the actual tooltip draw to the end of the frame (so it draws on top of everything else) instead
 *   of needing to be called last manually.</li>
 *   <li>Manual GL matrix scale/translate around an item draw (for the small "linear" skill icons) -&gt;
 *   {@code graphics.pose()} is a 2D affine {@code Matrix3x2fStack} with the same push/translate/scale/pop shape.</li>
 * </ul>
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

	/**
	 * The Forge original had a "Linear" variant used while a manual GL scale/translate matrix was active around
	 * the call. The new pose stack (accessible via {@code graphics.pose()}) plays the same role, so this is just
	 * an alias kept for call-site parity with the ported page classes.
	 */
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
	 * {@code GuiGraphicsExtractor#text(...)} silently no-ops (never even queues a render-state object) whenever
	 * {@code ARGB.alpha(color) == 0} - see {@code GuiGraphicsExtractor.text(Font, FormattedCharSequence, int, int,
	 * int, boolean)} in the decompiled 26.1.2 sources, which is gated by {@code if (ARGB.alpha(color) != 0)}
	 * before calling {@code guiRenderState.addText(...)}. All the colour ints ported over from the Forge 1.8.9
	 * {@code FontRenderer#drawString} call sites (e.g. {@code 0xFFFFFF}, {@code 0x3FE0D0}) are bare RGB with no
	 * alpha byte set, which the old API treated as implicitly opaque but the new one treats as invisible. Every
	 * text helper below routes its colour through this so legacy RGB-only colours become fully opaque instead of
	 * being dropped, while colours that already carry a real alpha byte are left alone.
	 */
	public static int opaque(int colour) {
		return (colour & 0xFF000000) == 0 ? (colour | 0xFF000000) : colour;
	}

	/**
	 * Port of the Forge 1.8.9 {@code Utils.playPressSound()} (old {@code gui.button.press} via
	 * {@code Minecraft#getSoundHandler()}) -&gt; {@code SoundEvents.UI_BUTTON_CLICK} via
	 * {@code Minecraft#getSoundManager()}. Lives here rather than the common {@code util.Utils} (all of whose
	 * call sites are already client-only) since playing a sound needs {@code Minecraft}'s client-only sound
	 * manager, which isn't safe to reference from {@code util.Utils}'s common/dedicated-server-safe source set.
	 */
	public static void playPressSound() {
		Minecraft.getInstance()
			.getSoundManager()
			.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	/**
	 * Drop-in replacement for a direct {@code graphics.text(font, str, x, y, colour, shadow)} call that guards
	 * against the same alpha=0 silent-no-op described on {@link #opaque(int)}. Several page classes called
	 * {@code graphics.text(...)} directly (bypassing {@link #drawStringCentered}) with the same bare-RGB legacy
	 * colours, so those call sites route through this instead.
	 */
	public static void text(GuiGraphicsExtractor graphics, Font font, String str, int x, int y, int colour, boolean shadow) {
		graphics.text(font, str, x, y, opaque(colour), shadow);
	}

	public static void drawStringCentered(GuiGraphicsExtractor graphics, String str, Font font, float x, float y, boolean shadow, int colour) {
		int width = font.width(str);
		graphics.text(font, str, Math.round(x - width / 2f), Math.round(y - font.lineHeight / 2f), opaque(colour == 0 ? 0xFFFFFF : colour), shadow);
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
	 * Draws {@code first} left-aligned and {@code second} right-aligned within a {@code length}-pixel wide span
	 * starting at (x, y), using the default client font. Mirrors the old two-part "stat name: value" rows,
	 * including their fallback: if the two don't fit side by side, they're drawn as one line, shrunk to fit.
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
