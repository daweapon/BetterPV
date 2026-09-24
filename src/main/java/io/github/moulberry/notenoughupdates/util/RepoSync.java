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
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Port of the repo download/extract half of the Forge 1.8.9 {@code NEUManager#fetchRepository}/
 * {@code unzipIgnoreFirstFolder} pipeline (the GitHub-zip-archive sync for
 * https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO), rewritten on {@code java.net.http.HttpClient} to
 * match {@link ApiUtil}'s established HTTP approach.
 *
 * <p>TODO(fabric-port) — simplifications vs. the original:
 * <ul>
 *   <li>The original checked the GitHub "latest commit" API on every launch and diffed it against a saved
 *   {@code currentCommit.json} to decide whether to redownload, so it would pick up upstream repo updates on
 *   every launch (when {@code autoupdate} was enabled - a config-system option out of scope here anyway). This
 *   port instead does a much simpler "sync if missing or stale" check: it skips the download entirely if
 *   {@code <repoLocation>/items} already exists and a {@code .last-synced} marker file is younger than {@link
 *   #MAX_AGE}. This means an already-synced repo will only refresh itself roughly once a day at most, not on
 *   every commit to the upstream repo - acceptable for this pass since the alternative (hitting the GitHub API
 *   on every launch) has more failure modes for no benefit to a first-run/"populate an empty config dir" use
 *   case.</li>
 *   <li>No progress reporting/chat message on completion ({@code userFacingRepositoryReload}'s
 *   "§aRepository reloaded." messages) - this is a headless best-effort background sync, not a user-triggered
 *   command yet. A {@code /neu reloadrepo}-equivalent command wasn't ported either.</li>
 *   <li>Corrupt-zip / zip-slip path traversal validation is kept (same check the original had) since it's cheap
 *   and a real security property, not a nuance being simplified away.</li>
 * </ul>
 */
public class RepoSync {
	private static final String REPO_USER = "NotEnoughUpdates";
	private static final String REPO_NAME = "NotEnoughUpdates-REPO";
	private static final String REPO_BRANCH = "master";
	private static final String DOWNLOAD_URL =
		"https://github.com/" + REPO_USER + "/" + REPO_NAME + "/archive/refs/heads/" + REPO_BRANCH + ".zip";
	private static final Duration MAX_AGE = Duration.ofDays(1);

	private static final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final java.util.concurrent.ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "NEU-RepoSync");
		t.setDaemon(true);
		return t;
	});

	private RepoSync() {
	}

	/**
	 * Downloads and extracts the repo zip into {@code repoLocation} if it's missing or stale, on a background
	 * thread. Completes with {@code true} if a (re)download happened, {@code false} if the existing local repo
	 * was left alone (already fresh, or the download failed - failures are logged but not thrown, matching
	 * {@code Constants}' existing "best effort, null-check the fields" contract).
	 */
	public static CompletableFuture<Boolean> syncIfNeeded(File repoLocation) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				File marker = new File(repoLocation, ".last-synced");
				File itemsDir = new File(repoLocation, "items");
				if (itemsDir.isDirectory() && marker.isFile()) {
					long lastSynced = Long.parseLong(Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim());
					if (Duration.between(Instant.ofEpochMilli(lastSynced), Instant.now()).compareTo(MAX_AGE) < 0) {
						return false;
					}
				}

				System.out.println("[NotEnoughUpdates] Downloading repo data from " + DOWNLOAD_URL + "...");
				HttpRequest request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL))
					.header("User-Agent", "NotEnoughUpdates/" + NotEnoughUpdates.VERSION)
					.timeout(Duration.ofSeconds(60))
					.GET()
					.build();
				HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() != 200) {
					System.err.println(
						"[NotEnoughUpdates] Failed to download repo data: HTTP " + response.statusCode() +
							(itemsDir.isDirectory() ? " (keeping existing local repo)" : "")
					);
					return false;
				}

				File extractTarget = itemsDir.isDirectory() ? new File(repoLocation, ".sync-tmp") : repoLocation;
				if (extractTarget.exists()) {
					Utils.recursiveDelete(extractTarget);
				}
				extractTarget.mkdirs();
				unzipIgnoreFirstFolder(response.body(), extractTarget);

				if (extractTarget != repoLocation) {
					// Swap the freshly extracted tree in for the old one, so a failed/partial download never
					// clobbers a previously-working local repo.
					File[] existingFiles = repoLocation.listFiles((dir, name) -> !name.equals(".sync-tmp") && !name.equals(".last-synced"));
					if (existingFiles != null) {
						for (File existing : existingFiles) {
							Utils.recursiveDelete(existing);
						}
					}
					File[] extractedFiles = extractTarget.listFiles();
					if (extractedFiles != null) {
						for (File extracted : extractedFiles) {
							Files.move(extracted.toPath(), new File(repoLocation, extracted.getName()).toPath());
						}
					}
					Utils.recursiveDelete(extractTarget);
				}

				Files.writeString(marker.toPath(), Long.toString(Instant.now().toEpochMilli()), StandardCharsets.UTF_8);
				System.out.println("[NotEnoughUpdates] Repo data synced to " + repoLocation.getAbsolutePath());
				return true;
			} catch (Exception e) {
				System.err.println("[NotEnoughUpdates] Repo sync failed: " + e);
				e.printStackTrace();
				return false;
			}
		}, executorService);
	}

	/**
	 * Port of the original's {@code unzipIgnoreFirstFolder}: GitHub's archive zips wrap everything in a single
	 * {@code <repo>-<branch>/} top-level folder, which is stripped here so the repo's real top-level dirs
	 * ({@code items/}, {@code constants/}, ...) land directly under {@code destDir}.
	 */
	private static void unzipIgnoreFirstFolder(byte[] zipBytes, File destDir) throws IOException {
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				int firstSlash = name.indexOf('/');
				if (firstSlash < 0) continue; // the top-level folder entry itself
				name = name.substring(firstSlash + 1);
				if (name.isEmpty()) continue;

				File outFile = new File(destDir, name);
				// Zip-slip guard (same check the original had for "invalid zip file").
				if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
					throw new IOException("Repo zip entry escapes destination directory: " + entry.getName());
				}

				if (entry.isDirectory()) {
					outFile.mkdirs();
					continue;
				}
				outFile.getParentFile().mkdirs();
				try (FileOutputStream fos = new FileOutputStream(outFile)) {
					zis.transferTo(fos);
				}
			}
		}
	}
}
