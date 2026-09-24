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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Port of the Forge 1.8.9 {@code util.ApiUtil}.
 *
 * <p>Simplifications from the original:
 * <ul>
 *   <li>Rewritten on top of {@code java.net.http.HttpClient} (JDK 11+) instead of Apache HttpComponents
 *   ({@code org.apache.http.*}) and Apache Commons IO, since those aren't declared as dependencies of this
 *   Fabric project and pulling them in wasn't necessary - the JDK HTTP client covers everything this class
 *   needs.</li>
 *   <li>The original pinned Hypixel's TLS certificate via a bundled {@code neukeystore.jks} keystore resource.
 *   That resource doesn't exist in this project and cert pinning is an orthogonal hardening concern, so this
 *   port uses the platform default trust store. TODO(fabric-port): re-add certificate pinning here if desired.</li>
 *   <li>The API key: the full {@code NEUConfig} config-GUI system is out of scope for this pass (see class
 *   javadoc elsewhere), so the key is read from a small standalone {@link ApiKeyConfig} JSON file instead. Also
 *   note the key itself changed meaning - Hypixel removed the old per-player {@code /api new} key years before
 *   this port in favor of developer-registered keys from https://developer.hypixel.net, sent as an
 *   {@code Api-Key} header (not the old {@code ?key=} query parameter). A personal key is now optional: by
 *   default key-protected requests go through the Better PV backend. See {@link #newHypixelApiRequest}.</li>
 *   <li>{@code moulberryCodesApi} (the moulberry.codes host override) came from the config system too and is
 *   still hardcoded - see {@link #getMyApiURL()}.</li>
 * </ul>
 */
public class ApiUtil {
	private static final Gson gson = new Gson();
	private static final ExecutorService executorService = Executors.newFixedThreadPool(3);
	private static final String USER_AGENT = "NotEnoughUpdates/" + NotEnoughUpdates.VERSION;
	private static final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(java.time.Duration.ofSeconds(10))
		.build();

	public static class Request {

		private final List<String[]> queryArguments = new ArrayList<>();
		private final List<String[]> headers = new ArrayList<>();
		private String baseUrl = null;
		private boolean shouldGunzip = false;
		private boolean backendAuth = false;
		private String method = "GET";

		public Request header(String key, String value) {
			headers.add(new String[]{key, value});
			return this;
		}

		public Request method(String method) {
			this.method = method;
			return this;
		}

		public Request url(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Request queryArgument(String key, String value) {
			queryArguments.add(new String[]{key, value});
			return this;
		}

		public Request gunzip() {
			shouldGunzip = true;
			return this;
		}

		/** Attach a Better PV backend token (see {@link BpvBackend}), re-authenticating once if it's rejected. */
		public Request backendAuth() {
			backendAuth = true;
			return this;
		}

		private URI buildUri() {
			StringBuilder sb = new StringBuilder(baseUrl);
			for (int i = 0; i < queryArguments.size(); i++) {
				sb.append(i == 0 && !baseUrl.contains("?") ? '?' : '&');
				String[] kv = queryArguments.get(i);
				sb
					.append(URLEncoder.encode(kv[0], StandardCharsets.UTF_8))
					.append('=')
					.append(URLEncoder.encode(kv[1], StandardCharsets.UTF_8));
			}
			return URI.create(sb.toString());
		}

		public CompletableFuture<String> requestString() {
			return CompletableFuture.supplyAsync(() -> {
				try {
					HttpResponse<byte[]> response = send();
					if (backendAuth && response.statusCode() == 401) {
						// Token expired or the backend's signing secret changed - log in again and retry once.
						BpvBackend.invalidateToken();
						response = send();
					}
					byte[] body = response.body();
					if (shouldGunzip) {
						try (GZIPInputStream gzipIn = new GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
							body = gzipIn.readAllBytes();
						}
					}
					return new String(body, StandardCharsets.UTF_8);
				} catch (BackendAuthException e) {
					// Answer with a Hypixel-style error body rather than failing the future, so callers take their
					// normal "success: false" path instead of never hearing back.
					NotEnoughUpdates.LOGGER.warn("Better PV backend login failed: {}", e.getMessage());
					JsonObject error = new JsonObject();
					error.addProperty("success", false);
					error.addProperty("cause", e.getMessage());
					return error.toString();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			}, executorService);
		}

		private HttpResponse<byte[]> send() throws IOException, InterruptedException {
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(buildUri())
				.method(method, HttpRequest.BodyPublishers.noBody())
				.header("User-Agent", USER_AGENT)
				.timeout(java.time.Duration.ofSeconds(10));
			for (String[] header : headers) {
				requestBuilder.header(header[0], header[1]);
			}
			if (backendAuth) {
				String token;
				try {
					token = BpvBackend.getToken();
				} catch (IOException e) {
					throw new BackendAuthException(e.getMessage());
				}
				requestBuilder.header("Authorization", "Bearer " + token);
			}
			return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
		}

		public CompletableFuture<JsonObject> requestJson() {
			return requestJson(JsonObject.class);
		}

		public <T> CompletableFuture<T> requestJson(Class<? extends T> clazz) {
			return requestString().thenApply(str -> gson.fromJson(str, clazz));
		}

	}

	public Request request() {
		return new Request();
	}

	private static class BackendAuthException extends IOException {
		BackendAuthException(String message) {
			super(message);
		}
	}

	/**
	 * A Hypixel API request for an endpoint that needs authentication (e.g. {@code player}, {@code status},
	 * {@code guild}, {@code skyblock/profiles}, {@code skyblock/bingo}). If the player configured their own
	 * developer key (see {@link ApiKeyConfig}), it goes straight to Hypixel with that key as an {@code Api-Key}
	 * header. Otherwise it goes through the Better PV backend (see {@link BpvBackend}), which holds the key
	 * server-side - Hypixel doesn't allow keys to be shipped inside a client mod.
	 */
	public Request newHypixelApiRequest(String apiPath) {
		String key = apiKey();
		if (!key.isEmpty()) {
			return newAnonymousHypixelApiRequest(apiPath).header("Api-Key", key);
		}
		return new Request()
			.url(BpvBackend.baseUrl() + "hypixel/" + apiPath)
			.backendAuth();
	}

	/**
	 * A Hypixel API request for one of the public, keyless endpoints (e.g. {@code resources/*},
	 * {@code skyblock/bazaar}).
	 */
	public Request newAnonymousHypixelApiRequest(String apiPath) {
		return new Request()
			.url("https://api.hypixel.net/" + apiPath);
	}

	public Request newMoulberryRequest(String path) {
		return new Request()
			.url(getMyApiURL() + path);
	}

	private String apiKey() {
		return ApiKeyConfig.getApiKey();
	}

	// TODO(fabric-port): likewise, the moulberry.codes-compatible API host was config-driven.
	private String getMyApiURL() {
		return "https://moulberry.codes/";
	}
}
