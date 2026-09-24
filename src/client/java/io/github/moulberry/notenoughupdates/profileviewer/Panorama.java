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

package io.github.moulberry.notenoughupdates.profileviewer;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;

/**
 * Port of the Forge 1.8.9 {@code Panorama} background renderer used behind the player's location art in the
 * profile viewer's basic page.
 *
 * <p><b>TODO(fabric-port) — simplified, not a faithful port:</b> the original rendered a genuine rotating 3D
 * skybox cube (6 textured quads with a hand-rolled perspective projection, drawn straight into the current GL
 * viewport via {@code Tessellator}/{@code WorldRenderer} immediate-mode calls plus a manual framebuffer blit).
 * That whole rendering model is gone in 26.1.2: there is no per-widget viewport/projection override available
 * from {@code GuiGraphicsExtractor}, and the vanilla replacement ({@code net.minecraft.client.renderer.CubeMap})
 * only draws full-screen and expects a single pre-stitched cubemap texture + a hand-written GPU render pass
 * (vertex buffers, pipelines, uniforms) rather than 6 loose textures - reimplementing that whole pipeline for a
 * small inset panel was out of scope for this pass. Instead, this draws a single static face of the panorama
 * (the "front" face, index 0) as a flat textured rect covering the requested area, with the {@code angle}
 * parameter ignored. This keeps the call sites (and the "which panorama for this location" lookup/fallback logic)
 * unchanged so a real rotating panorama can be dropped in later without touching {@code BasicPage}/{@code PetsPage}.
 */
public class Panorama {

	private static final HashMap<String, Identifier[]> panoramasMap = new HashMap<>();

	/**
	 * @return {@code "day"} or {@code "night"}, for the {@code identifier} passed to {@link #getPanoramasForLocation}.
	 * TODO(fabric-port): the original derived this from the live in-game Skyblock clock (parsed off the
	 * scoreboard sidebar by {@code util.SBInfo}, which wasn't ported - see this class's own TODO on why the data
	 * layer is out of scope here). There's no reliable, verified formula for computing Skyblock's in-game time
	 * from a wall-clock timestamp alone available in this codebase to fall back to, so rather than guess at one
	 * and risk being confidently wrong forever, this uses the player's real local time of day as an approximate
	 * substitute - purely cosmetic (which of two background art variants to show), not used for anything
	 * gameplay-relevant.
	 */
	public static String currentDayNightIdentifier() {
		int hour = java.time.LocalTime.now().getHour();
		return (hour >= 6 && hour < 18) ? "day" : "night";
	}

	public static synchronized Identifier[] getPanoramasForLocation(String location, String identifier) {
		String key = location + identifier;
		if (panoramasMap.containsKey(key)) return panoramasMap.get(key);

		// Note: Minecraft.getResourceManager().getResource(Identifier) went from throwing IOException (1.8.9) to
		// returning an Optional<Resource> in modern versions, so existence is checked with isPresent() instead.
		Identifier[] specific = new Identifier[6];
		boolean specificExists = true;
		for (int i = 0; i < 6; i++) {
			specific[i] = Identifier.parse("notenoughupdates:panoramas/" + location + "_" + identifier + "/panorama_" + i + ".jpg");
			if (Minecraft.getInstance().getResourceManager().getResource(specific[i]).isEmpty()) specificExists = false;
		}
		if (specificExists) {
			panoramasMap.put(key, specific);
			return specific;
		}

		Identifier[] fallback = new Identifier[6];
		boolean fallbackExists = true;
		for (int i = 0; i < 6; i++) {
			fallback[i] = Identifier.parse("notenoughupdates:panoramas/" + location + "/panorama_" + i + ".jpg");
			if (Minecraft.getInstance().getResourceManager().getResource(fallback[i]).isEmpty()) fallbackExists = false;
		}
		if (fallbackExists) {
			panoramasMap.put(key, fallback);
			return fallback;
		}

		Identifier[] unknown = new Identifier[6];
		for (int i = 0; i < 6; i++) {
			unknown[i] = Identifier.parse("notenoughupdates:panoramas/unknown/panorama_" + i + ".jpg");
		}
		panoramasMap.put(key, unknown);
		return unknown;
	}

	public static void drawPanorama(
		GuiGraphicsExtractor graphics,
		float angle,
		int x,
		int y,
		int width,
		int height,
		float yOffset,
		float zOffset,
		Identifier[] panoramas
	) {
		// See the class TODO above: this is a static single-face placeholder, not the rotating 3D skybox the
		// legacy renderer produced. angle/yOffset/zOffset are accepted (matching the old call signature used by
		// BasicPage/PetsPage) but currently unused.
		Identifier texture = decodedTexture(panoramas[0]);
		if (texture == null) {
			graphics.fill(x, y, x + width, y + height, 0xff101014);
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, width, height);
	}

	/** Source .jpg identifier -> registered texture identifier, or null if decoding failed (so it isn't retried). */
	private static final HashMap<Identifier, Identifier> decodedTextures = new HashMap<>();

	/**
	 * The panorama art is JPEG (carried over from the original), but the texture manager only loads PNGs - it
	 * rejects anything else with "Bad PNG Signature" and draws the missing-texture checkerboard. So the JPEG is
	 * decoded once with ImageIO, copied into a NativeImage and registered as a DynamicTexture.
	 */
	private static Identifier decodedTexture(Identifier jpg) {
		if (decodedTextures.containsKey(jpg)) return decodedTextures.get(jpg);
		Identifier result = null;
		Minecraft minecraft = Minecraft.getInstance();
		try (java.io.InputStream in = minecraft.getResourceManager().open(jpg)) {
			java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(in);
			if (image != null) {
				NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
				for (int py = 0; py < image.getHeight(); py++) {
					for (int px = 0; px < image.getWidth(); px++) {
						nativeImage.setPixel(px, py, 0xff000000 | image.getRGB(px, py));
					}
				}
				result = Identifier.parse(jpg.getNamespace() + ":" + jpg.getPath().replace(".jpg", "_decoded"));
				minecraft.getTextureManager().register(result, new DynamicTexture(result::toString, nativeImage));
			}
		} catch (Throwable t) {
			// Throwable: also covers the java.desktop module (ImageIO) being absent from an unusual Java runtime.
			NotEnoughUpdates.LOGGER.warn("Couldn't load panorama {}: {}", jpg, t.toString());
			result = null;
		}
		decodedTextures.put(jpg, result);
		return result;
	}
}
