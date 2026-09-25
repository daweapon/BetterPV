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

package io.github.moulberry.notenoughupdates.auction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NEUManager;
import io.github.moulberry.notenoughupdates.util.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trimmed port of the Forge 1.8.9 {@code auction.APIManager}. The original also drove the in-game Auction House
 * GUI/search (page downloading, bid notifications, craft-cost calculation, the price-graph cache, etc.); none of
 * that is needed by the data layer, so only the price-lookup caches and their Hypixel/moulberry.codes update
 * requests were ported - this is exactly the subset {@code ProfileViewer.Profile#getNetWorth} and
 * {@code ProfileViewer} networth/set-bonus calculations call
 * ({@code getBazaarInfo}/{@code getItemAvgBin}/{@code getLowestBin}/{@code getItemAuctionInfo}/
 * {@code isVanillaItem}).
 */
public class APIManager {
	private final NEUManager manager;

	private JsonObject lowestBins = null;
	private JsonObject coflLowestBins = null;
	private JsonObject auctionPricesAvgLowestBinJson = null;
	private JsonObject bazaarJson = null;
	private JsonObject auctionPricesJson = null;
	private final java.util.concurrent.atomic.AtomicInteger priceSourcesLoaded = new java.util.concurrent.atomic.AtomicInteger();

	private static final List<String> hardcodedVanillaItems = Utils.createList(
		"WOOD_AXE", "WOOD_HOE", "WOOD_PICKAXE", "WOOD_SPADE", "WOOD_SWORD",
		"GOLD_AXE", "GOLD_HOE", "GOLD_PICKAXE", "GOLD_SPADE", "GOLD_SWORD",
		"ROOKIE_HOE"
	);

	private static final Pattern BAZAAR_ENCHANTMENT_PATTERN = Pattern.compile("ENCHANTMENT_(\\D*)_(\\d+)");

	public APIManager(NEUManager manager) {
		this.manager = manager;
	}

	/** True once both Bazaar and auction prices are available for a complete profile calculation. */
	public boolean isPricingReady() {
		return bazaarJson != null && (coflLowestBins != null || lowestBins != null || auctionPricesAvgLowestBinJson != null);
	}

	/** Counts price sources that have loaded for the first time, so cached totals from partial prices can be redone. */
	public int getPriceSourcesLoaded() {
		return priceSourcesLoaded.get();
	}

	public long getLowestBin(String internalName) {
		if (coflLowestBins != null) {
			JsonElement coflPrice = coflLowestBins.get(internalName);
			if (coflPrice != null && coflPrice.isJsonPrimitive() && coflPrice.getAsJsonPrimitive().isNumber()) {
				return coflPrice.getAsBigDecimal().longValue();
			}
		}
		if (lowestBins != null) {
			String priceKey = internalName;
			int separator = internalName.lastIndexOf(';');
			if (separator > 0) {
				String[] rarities = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
				try {
					int rarity = Integer.parseInt(internalName.substring(separator + 1));
					if (rarity >= 0 && rarity < rarities.length) {
						priceKey = "PET-" + internalName.substring(0, separator) + "-" + rarities[rarity];
					}
				} catch (NumberFormatException ignored) {
				}
			}
			JsonElement e = lowestBins.get(priceKey);
			if (e == null) e = lowestBins.get(internalName);
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
				return e.getAsBigDecimal().longValue();
			}
		}
		return -1;
	}

	public long getPetLowestBin(String type, int rarity, int level) {
		if (lowestBins == null) return -1;
		String[] rarities = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
		if (rarity < 0 || rarity >= rarities.length) return -1;
		String base = "PET-" + type + "-" + rarities[rarity];
		JsonElement priced = null;
		if (level >= 200) priced = lowestBins.get(base + "-200");
		if (priced == null && level >= 100) priced = lowestBins.get(base + "-100");
		if (priced == null) priced = lowestBins.get(base);
		return priced != null && priced.isJsonPrimitive() && priced.getAsJsonPrimitive().isNumber()
			? priced.getAsBigDecimal().longValue() : -1;
	}

	public void updateLowestBin() {
		manager.apiUtils
			.request()
			.url("https://sky.coflnet.com/api/prices/neu")
			.requestJson()
			.thenAccept(jsonObject -> {
				if (jsonObject == null) return;
					if (coflLowestBins == null) priceSourcesLoaded.incrementAndGet();
					coflLowestBins = jsonObject;
			});
		manager.apiUtils
			.request()
			.url("https://lb.tricked.dev/lowestbins.json.gz")
			.gunzip()
			.requestJson()
			.thenAccept(jsonObject -> {
				if (jsonObject == null) return;
				if (lowestBins == null) {
					lowestBins = new JsonObject();
				}
				for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
					lowestBins.add(entry.getKey(), entry.getValue());
				}
			});
	}

	public Set<String> getItemAuctionInfoKeySet() {
		if (auctionPricesJson == null) return new HashSet<>();
		HashSet<String> keys = new HashSet<>();
		for (Map.Entry<String, JsonElement> entry : auctionPricesJson.entrySet()) {
			keys.add(entry.getKey());
		}
		return keys;
	}

	public JsonObject getItemAuctionInfo(String internalname) {
		if (auctionPricesJson == null) return null;
		JsonElement e = auctionPricesJson.get(internalname);
		if (e == null) {
			return null;
		}
		return e.getAsJsonObject();
	}

	public double getItemAvgBin(String internalName) {
		if (auctionPricesAvgLowestBinJson == null) return -1;
		JsonElement e = auctionPricesAvgLowestBinJson.get(internalName);
		if (e == null) {
			return -1;
		}
		return Math.round(e.getAsDouble());
	}

	public void updateAvgPrices() {
		manager.apiUtils
			.newMoulberryRequest("auction_averages/3day.json.gz")
			.gunzip().requestJson().thenAccept((jsonObject) -> auctionPricesJson = jsonObject);
		manager.apiUtils
			.newMoulberryRequest("auction_averages_lbin/1day.json.gz")
			.gunzip().requestJson()
			.thenAccept((jsonObject) -> {
					if (jsonObject != null && auctionPricesAvgLowestBinJson == null) priceSourcesLoaded.incrementAndGet();
					auctionPricesAvgLowestBinJson = jsonObject;
				});
	}

	public double getBazaarOrBin(String internalName) {
		JsonObject bazaarInfo = getBazaarInfo(internalName);
		if (bazaarInfo != null && bazaarInfo.get("curr_buy") != null) {
			return bazaarInfo.get("curr_buy").getAsFloat();
		} else {
			return getLowestBin(internalName);
		}
	}

	public JsonObject getBazaarInfo(String internalName) {
		if (bazaarJson == null) return null;
		JsonElement e = bazaarJson.get(internalName);
		if (e == null) {
			return null;
		}
		return e.getAsJsonObject();
	}

	public String transformHypixelBazaarToNEUItemId(String hypixelId) {
		Matcher matcher = BAZAAR_ENCHANTMENT_PATTERN.matcher(hypixelId);
		if (matcher.matches()) {
			return matcher.group(1) + ";" + matcher.group(2);
		}
		return hypixelId.replace(":", "-");
	}

	public void updateBazaar() {
		manager.apiUtils
			.newAnonymousHypixelApiRequest("v2/skyblock/bazaar")
			.requestJson()
			.thenAccept(jsonObject -> {
				if (jsonObject == null || !jsonObject.has("success") || !jsonObject.get("success").getAsBoolean()) return;

				JsonObject newBazaarJson = new JsonObject();
				JsonObject products = jsonObject.get("products").getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
					if (entry.getValue().isJsonObject()) {
						JsonObject productInfo = new JsonObject();

						JsonObject product = entry.getValue().getAsJsonObject();
						JsonObject quickStatus = product.get("quick_status").getAsJsonObject();
						productInfo.addProperty("avg_buy", quickStatus.get("buyPrice").getAsFloat());
						productInfo.addProperty("avg_sell", quickStatus.get("sellPrice").getAsFloat());

						for (JsonElement element : product.get("sell_summary").getAsJsonArray()) {
							if (element.isJsonObject()) {
								JsonObject sellSummaryFirst = element.getAsJsonObject();
								productInfo.addProperty("curr_sell", sellSummaryFirst.get("pricePerUnit").getAsFloat());
								break;
							}
						}

						for (JsonElement element : product.get("buy_summary").getAsJsonArray()) {
							if (element.isJsonObject()) {
								JsonObject sellSummaryFirst = element.getAsJsonObject();
								productInfo.addProperty("curr_buy", sellSummaryFirst.get("pricePerUnit").getAsFloat());
								break;
							}
						}

						newBazaarJson.add(transformHypixelBazaarToNEUItemId(entry.getKey()), productInfo);
					}
				}
				if (bazaarJson == null) priceSourcesLoaded.incrementAndGet();
					bazaarJson = newBazaarJson;
			});
	}

	public boolean isVanillaItem(String internalname) {
		if (hardcodedVanillaItems.contains(internalname)) return true;

		//Removes trailing numbers and underscores, eg. LEAVES_2-3 -> LEAVES
		String vanillaName = internalname.split("-")[0];
		if (manager.getItemInformation().containsKey(vanillaName)) {
			JsonObject json = manager.getItemInformation().get(vanillaName);
			if (json != null && json.has("vanilla") && json.get("vanilla").getAsBoolean()) return true;
		}
		return Utils.isRegisteredVanillaItem(vanillaName);
	}
}
