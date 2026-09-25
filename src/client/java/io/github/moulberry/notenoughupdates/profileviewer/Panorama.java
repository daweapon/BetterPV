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
 * The location panorama behind the basic and pets pages. The 1.8.9 spinning skybox cube can't be drawn in a
 * small inset any more, so this shows one static face.
 */
public class Panorama {

	private static final HashMap<String, Identifier[]> panoramasMap = new HashMap<>();

	/**
	 * "day" or "night" for {@link #getPanoramasForLocation}. The real SkyBlock clock isn't available, so this
	 * follows the player's local time of day. It only picks between two backgrounds.
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
			specific[i] = Identifier.parse("betterpv:panoramas/" + location + "_" + identifier + "/panorama_" + i + ".jpg");
			if (Minecraft.getInstance().getResourceManager().getResource(specific[i]).isEmpty()) specificExists = false;
		}
		if (specificExists) {
			panoramasMap.put(key, specific);
			return specific;
		}

		Identifier[] fallback = new Identifier[6];
		boolean fallbackExists = true;
		for (int i = 0; i < 6; i++) {
			fallback[i] = Identifier.parse("betterpv:panoramas/" + location + "/panorama_" + i + ".jpg");
			if (Minecraft.getInstance().getResourceManager().getResource(fallback[i]).isEmpty()) fallbackExists = false;
		}
		if (fallbackExists) {
			panoramasMap.put(key, fallback);
			return fallback;
		}

		Identifier[] unknown = new Identifier[6];
		for (int i = 0; i < 6; i++) {
			unknown[i] = Identifier.parse("betterpv:panoramas/unknown/panorama_" + i + ".jpg");
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
		// Static single face, not the old rotating skybox. angle, yOffset and zOffset are unused.
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
	 * The art is JPEG but the texture manager only loads PNG (it draws the missing-texture checkerboard), so
	 * it's decoded with ImageIO and registered as a DynamicTexture.
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
