package io.github.moulberry.notenoughupdates.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;

/**
 * Client for the Better PV backend (see {@code backend/} in the repo), which proxies the key-protected Hypixel
 * endpoints so players don't need their own API key.
 *
 * <p>Authentication uses the account's Mojang-certified profile key pair (the one vanilla uses for chat
 * signing): we sign a timestamped message with the private key and send it along with the public key and
 * Mojang's signature of it, which the backend can check offline. The key pair comes from the {@link KeySource}
 * the client entrypoint installs, since this source set can't see client classes. The backend's token is cached
 * here until shortly before it expires.
 */
public class BpvBackend {
	/** The deployed Worker (see {@code backend/README.md}). Can be overridden with {@code "backendUrl"} in config.json. */
	public static final String DEFAULT_BACKEND_URL = "https://betterpv-api.bradleyglazier7.workers.dev/";

	/**
	 * The logged-in account's profile key pair.
	 *
	 * @param uuid             account UUID, 32 lowercase hex digits without dashes
	 * @param keyExpiresAtMillis when Mojang's certificate for the key expires
	 * @param publicKey        X.509-encoded public key
	 * @param keySignature     Mojang's signature over the UUID, expiry and public key
	 */
	public record AccountKeys(
		String uuid,
		long keyExpiresAtMillis,
		byte[] publicKey,
		byte[] keySignature,
		PrivateKey privateKey
	) {}

	/** Supplies the account's profile key pair. Installed by the client entrypoint. */
	public interface KeySource {
		AccountKeys get() throws Exception;
	}

	private static final Gson gson = new Gson();
	private static final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private static volatile KeySource keySource;
	private static String token;
	private static long tokenExpiresAtMillis;

	public static void setKeySource(KeySource source) {
		keySource = source;
	}

	public static String baseUrl() {
		String url = ApiKeyConfig.getBackendUrl();
		if (url.isEmpty()) url = DEFAULT_BACKEND_URL;
		return url.endsWith("/") ? url : url + "/";
	}

	/**
	 * Returns a valid backend token, logging in first if needed. Blocks on network calls, so only call this off
	 * the render thread.
	 */
	public static synchronized String getToken() throws IOException {
		// Refresh a minute early so a token never expires mid-request.
		if (token != null && System.currentTimeMillis() < tokenExpiresAtMillis - 60_000) {
			return token;
		}
		KeySource source = keySource;
		if (source == null) {
			throw new IOException("Minecraft account keys are not available");
		}

		JsonObject login = new JsonObject();
		try {
			AccountKeys keys = source.get();
			long timestamp = System.currentTimeMillis();
			Signature signer = Signature.getInstance("SHA256withRSA");
			signer.initSign(keys.privateKey());
			signer.update(("betterpv-auth:" + keys.uuid() + ":" + timestamp).getBytes(StandardCharsets.UTF_8));

			Base64.Encoder base64 = Base64.getEncoder();
			login.addProperty("uuid", keys.uuid());
			login.addProperty("keyExpiresAt", keys.keyExpiresAtMillis());
			login.addProperty("publicKey", base64.encodeToString(keys.publicKey()));
			login.addProperty("keySignature", base64.encodeToString(keys.keySignature()));
			login.addProperty("timestamp", timestamp);
			login.addProperty("signature", base64.encodeToString(signer.sign()));
		} catch (Exception e) {
			throw new IOException("Could not sign in with your Minecraft account: " + e.getMessage(), e);
		}

		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "auth"))
			.header("User-Agent", "BetterPV/" + NotEnoughUpdates.VERSION)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(login.toString()))
			.timeout(Duration.ofSeconds(10))
			.build();
		HttpResponse<String> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}

		JsonObject json;
		try {
			json = gson.fromJson(response.body(), JsonObject.class);
		} catch (com.google.gson.JsonParseException e) {
			json = null;
		}
		if (response.statusCode() != 200 || json == null || !json.has("token") || !json.has("expiresAt")) {
			String cause = json != null && json.has("cause") ? json.get("cause").getAsString() : "HTTP " + response.statusCode();
			throw new IOException("Better PV server rejected login: " + cause);
		}
		token = json.get("token").getAsString();
		tokenExpiresAtMillis = json.get("expiresAt").getAsLong() * 1000L;
		return token;
	}

	/** Drops the cached token, e.g. after the backend answered 401. */
	public static synchronized void invalidateToken() {
		token = null;
	}
}
