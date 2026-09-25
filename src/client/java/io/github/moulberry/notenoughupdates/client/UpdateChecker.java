package io.github.moulberry.notenoughupdates.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.util.BpvConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tells the player in chat, once per launch, when GitHub has a newer Better PV release than the one running.
 * Asks GitHub's public releases API once after the first world join; failures are ignored.
 */
public final class UpdateChecker {
	private static final String LATEST_URL = "https://api.github.com/repos/daweapon/BetterPV/releases/latest";
	private static final AtomicBoolean started = new AtomicBoolean();

	private UpdateChecker() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!BpvConfig.isUpdateCheck() || !started.compareAndSet(false, true)) return;
			CompletableFuture.runAsync(() -> check(client));
		});
	}

	private static void check(Minecraft client) {
		try {
			String current = FabricLoader.getInstance().getModContainer("betterpv")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
			if (current == null) return;
			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
			HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(URI.create(LATEST_URL))
					.header("Accept", "application/vnd.github+json")
					.timeout(Duration.ofSeconds(10))
					.build(),
				HttpResponse.BodyHandlers.ofString()
			);
			if (response.statusCode() != 200) return;
			JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
			String latest = release.get("tag_name").getAsString();
			String url = release.get("html_url").getAsString();
			if (!isNewer(latest, current) || !url.startsWith("https://github.com/daweapon/BetterPV/")) return;
			String shown = latest.startsWith("v") ? latest.substring(1) : latest;
			Component message = Component.literal(ChatFormatting.GOLD + "[Better PV] " + ChatFormatting.YELLOW
				+ "Version " + shown + " is out (you have " + current + "). ")
				.append(Component.literal(ChatFormatting.AQUA + "" + ChatFormatting.UNDERLINE + "Click to download").withStyle(
					Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
			client.execute(() -> {
				if (client.player != null) client.player.sendSystemMessage(message);
			});
		} catch (Exception e) {
			NotEnoughUpdates.LOGGER.debug("Update check failed", e);
		}
	}

	/** True when {@code latest} (e.g. "v1.2.0") has a higher dotted number than {@code current} (e.g. "1.1.0"). */
	static boolean isNewer(String latest, String current) {
		int[] a = parts(latest);
		int[] b = parts(current);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0;
			int y = i < b.length ? b[i] : 0;
			if (x != y) return x > y;
		}
		return false;
	}

	private static int[] parts(String version) {
		String core = version.replaceFirst("^[vV]", "").split("[-+ ]")[0];
		String[] split = core.split("\\.");
		int[] out = new int[split.length];
		for (int i = 0; i < split.length; i++) {
			try {
				out[i] = Integer.parseInt(split[i]);
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}
}
