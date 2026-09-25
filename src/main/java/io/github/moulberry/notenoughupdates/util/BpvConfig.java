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
import java.util.ArrayList;
import java.util.List;

/**
 * Better PV's small config file, {@code <config-dir>/betterpv/config.json}, read with Gson. It holds
 * {@code backendUrl}, an optional override for the Better PV backend (see {@link BpvBackend}; leave it empty to
 * use the default), the recent-player history, and the settings screen's options. The mod has no API key setting: Hypixel keys stay on the backend (see
 * {@link ApiUtil#newHypixelApiRequest}). Older files may still have an {@code apiKey} field, which is ignored.
 */
public class BpvConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data cached;

	private static class Data {
		String backendUrl = "";
		List<ProfileHistoryEntry> profileHistory = new ArrayList<>();
		/** {@code ProfileViewerPage} names of the tabs switched off in settings. */
		List<String> hiddenTabs = new ArrayList<>();
		/** A {@code ProfileViewerPage} name, or {@code "LAST"} to reopen on the tab used last. */
		String openingTab = "LAST";
		boolean shortNumbers = false;
		boolean hideNetWorth = false;
		boolean chatRightClick = true;
	}

	public static class ProfileHistoryEntry {
		public String uuid;
		public String name;

		private ProfileHistoryEntry(String uuid, String name) {
			this.uuid = uuid;
			this.name = name;
		}
	}

	/** @return an override for the Better PV backend URL, or {@code ""} to use {@link BpvBackend#DEFAULT_BACKEND_URL}. */
	public static synchronized String getBackendUrl() {
		if (cached == null) load();
		return cached.backendUrl == null ? "" : cached.backendUrl.trim();
	}

	public static synchronized boolean isTabHidden(String tab) {
		if (cached == null) load();
		return cached.hiddenTabs != null && cached.hiddenTabs.contains(tab);
	}

	public static synchronized void setTabHidden(String tab, boolean hidden) {
		if (cached == null) load();
		if (cached.hiddenTabs == null) cached.hiddenTabs = new ArrayList<>();
		cached.hiddenTabs.remove(tab);
		if (hidden) cached.hiddenTabs.add(tab);
		save();
	}

	/** @return a {@code ProfileViewerPage} name, or {@code "LAST"}. */
	public static synchronized String getOpeningTab() {
		if (cached == null) load();
		return cached.openingTab == null ? "LAST" : cached.openingTab;
	}

	public static synchronized void setOpeningTab(String tab) {
		if (cached == null) load();
		cached.openingTab = tab;
		save();
	}

	public static synchronized boolean isShortNumbers() {
		if (cached == null) load();
		return cached.shortNumbers;
	}

	public static synchronized void setShortNumbers(boolean value) {
		if (cached == null) load();
		cached.shortNumbers = value;
		save();
	}

	public static synchronized boolean isHideNetWorth() {
		if (cached == null) load();
		return cached.hideNetWorth;
	}

	public static synchronized void setHideNetWorth(boolean value) {
		if (cached == null) load();
		cached.hideNetWorth = value;
		save();
	}

	public static synchronized boolean isChatRightClick() {
		if (cached == null) load();
		return cached.chatRightClick;
	}

	public static synchronized void setChatRightClick(boolean value) {
		if (cached == null) load();
		cached.chatRightClick = value;
		save();
	}

	public static synchronized List<ProfileHistoryEntry> getProfileHistory() {
		if (cached == null) load();
		if (cached.profileHistory == null) cached.profileHistory = new ArrayList<>();
		return List.copyOf(cached.profileHistory);
	}

	public static synchronized void addProfileHistory(String uuid, String name) {
		if (uuid == null || uuid.isBlank() || name == null || name.isBlank()) return;
		if (cached == null) load();
		if (cached.profileHistory == null) cached.profileHistory = new ArrayList<>();
		for (ProfileHistoryEntry entry : cached.profileHistory) {
			if (entry != null && (uuid.equalsIgnoreCase(entry.uuid) || name.equalsIgnoreCase(entry.name))) {
				entry.uuid = uuid;
				entry.name = name;
				save();
				return;
			}
		}
		cached.profileHistory.add(0, new ProfileHistoryEntry(uuid, name));
		if (cached.profileHistory.size() > 7) cached.profileHistory.subList(7, cached.profileHistory.size()).clear();
		save();
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

	private static void save() {
		File file = configFile();
		try {
			file.getParentFile().mkdirs();
			Files.writeString(file.toPath(), GSON.toJson(cached), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NotEnoughUpdates.LOGGER.warn("Failed to write {}: {}", file, e.toString());
		}
	}
}
