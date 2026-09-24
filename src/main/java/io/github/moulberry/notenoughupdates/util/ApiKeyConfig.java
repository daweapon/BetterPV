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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Minimal standalone replacement for the API-key portion of the Forge 1.8.9 {@code NEUConfig} (the full
 * annotation-driven config-GUI framework is explicitly out of scope for this port - see {@link ApiUtil} class
 * javadoc). Reads/writes a single {@code apiKey} field from {@code <config-dir>/notenoughupdates/config.json}
 * via Gson, consistent with how {@link Constants}/the repo files already live under the mod's config dir.
 *
 * <p>API mapping/behaviour note: as of the current Hypixel API (see {@link ApiUtil#newHypixelApiRequest}), this
 * is a <b>developer-registered</b> key from https://developer.hypixel.net/dashboard (a "Personal API Key"), sent
 * as an {@code Api-Key} request header - not the old per-player key obtained in-game via {@code /api new}, which
 * Hypixel removed years before this port. Most of the endpoints this mod actually calls
 * ({@code player}/{@code status}/{@code guild}/{@code skyblock/profiles}/{@code skyblock/bingo}) require a key;
 * when the player hasn't set one, those go through the Better PV backend instead (see {@link BpvBackend}).
 * {@code resources/*} and {@code skyblock/bazaar} are public and work fine with no key at all.
 *
 * <p>No in-game config GUI is provided (out of scope, same as full {@code NEUConfig}) - the user edits
 * {@code config.json} directly. A commented-out placeholder file is written on first run so the location is
 * discoverable.
 */
public class ApiKeyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data cached;

	private static class Data {
		String apiKey = "";
		String backendUrl = "";
	}

	/**
	 * @return the player's own Hypixel developer API key, or {@code ""} if none is set. Optional: without one,
	 * requests go through the Better PV backend (see {@link BpvBackend}).
	 */
	public static synchronized String getApiKey() {
		if (cached == null) load();
		return cached.apiKey == null ? "" : cached.apiKey;
	}

	/** @return an override for the Better PV backend URL, or {@code ""} to use {@link BpvBackend#DEFAULT_BACKEND_URL}. */
	public static synchronized String getBackendUrl() {
		if (cached == null) load();
		return cached.backendUrl == null ? "" : cached.backendUrl.trim();
	}

	/** Sets and persists the Hypixel developer API key (used by {@code /bpv setapi} and the {@code ApiKeyScreen} GUI). */
	public static synchronized void setApiKey(String apiKey) {
		if (cached == null) load();
		cached.apiKey = apiKey;
		File file = configFile();
		try {
			file.getParentFile().mkdirs();
			Files.writeString(file.toPath(), GSON.toJson(cached), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NotEnoughUpdates.LOGGER.warn("Failed to write {}: {}", file, e.toString());
		}
	}

	private static File configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(NotEnoughUpdates.MOD_ID).resolve("config.json").toFile();
	}

	private static void load() {
		cached = new Data();
		File file = configFile();
		try {
			if (file.isFile()) {
				try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
					Data parsed = GSON.fromJson(reader, Data.class);
					if (parsed != null) cached = parsed;
				}
			} else {
				file.getParentFile().mkdirs();
				Files.writeString(file.toPath(), GSON.toJson(cached), StandardCharsets.UTF_8);
			}
		} catch (IOException | com.google.gson.JsonParseException e) {
			NotEnoughUpdates.LOGGER.warn("Failed to read {}: {}", file, e.toString());
		}
	}
}
