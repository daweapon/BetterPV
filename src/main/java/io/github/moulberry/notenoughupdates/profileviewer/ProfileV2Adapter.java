package io.github.moulberry.notenoughupdates.profileviewer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;

/**
 * Rewrites a {@code v2/skyblock/profiles} member object into the v1 layout the ported profile viewer pages read
 * (e.g. {@code player_data.experience.SKILL_COMBAT} -> {@code experience_skill_combat}), so the pages keep working
 * unchanged. v1 keys are only added, never overwritten, and the v2 keys are left in place.
 *
 * <p>Not mapped: the wardrobe. Hypixel replaced {@code wardrobe_contents} (one packed inventory) with
 * {@code loadout.armor}, which stores each armour piece separately; rebuilding the packed form would mean
 * re-encoding NBT, so the wardrobe stays empty for now.
 */
public final class ProfileV2Adapter {
	private ProfileV2Adapter() {}

	public static void toV1(JsonObject member) {
		// Skills. v1 called social "social2".
		JsonObject experience = object(member, "player_data", "experience");
		if (experience != null) {
			for (Map.Entry<String, JsonElement> entry : experience.entrySet()) {
				String key = entry.getKey();
				if (!key.startsWith("SKILL_") || !entry.getValue().isJsonPrimitive()) continue;
				String skill = key.substring("SKILL_".length()).toLowerCase(Locale.ROOT);
				if (skill.equals("social")) skill = "social2";
				copy(member, "experience_skill_" + skill, entry.getValue());
			}
		}

		// Inventories.
		JsonObject inventory = object(member, "inventory");
		if (inventory != null) {
			for (String key : new String[]{
				"inv_contents", "inv_armor", "ender_chest_contents", "personal_vault_contents", "wardrobe_contents",
				"backpack_contents", "backpack_icons", "sacks_counts"
			}) {
				copy(member, key, inventory.get(key));
			}
			// "equippment" is v1's own spelling.
			copy(member, "equippment_contents", inventory.get("equipment_contents"));
			JsonObject bags = object(inventory, "bag_contents");
			if (bags != null) {
				for (String key : new String[]{"talisman_bag", "potion_bag", "fishing_bag", "quiver"}) {
					copy(member, key, bags.get(key));
				}
			}
		}
		JsonObject sharedInventory = object(member, "shared_inventory");
		if (sharedInventory != null) {
			copy(member, "candy_inventory_contents", sharedInventory.get("candy_inventory_contents"));
		}

		// Assorted fields that moved into sub-objects.
		copy(member, "pets", element(member, "pets_data", "pets"));
		copy(member, "coin_purse", element(member, "currencies", "coin_purse"));
		copy(member, "first_join", element(member, "profile", "first_join"));
		copy(member, "coop_invitation", element(member, "profile", "coop_invitation"));
		copy(member, "fairy_souls_collected", element(member, "fairy_soul", "total_collected"));
		copy(member, "fairy_exchanges", element(member, "fairy_soul", "fairy_exchanges"));
		copy(member, "unlocked_coll_tiers", element(member, "player_data", "unlocked_coll_tiers"));
		copy(member, "crafted_generators", element(member, "player_data", "crafted_generators"));
		copy(member, "favorite_arrow", element(member, "item_data", "favorite_arrow"));
		copy(member, "slayer_bosses", element(member, "slayer", "slayer_bosses"));
		JsonObject jacobPerks = object(member, "jacobs_contest", "perks");
		if (jacobPerks != null && !member.has("jacob2")) {
			JsonObject jacob2 = new JsonObject();
			jacob2.add("perks", jacobPerks);
			member.add("jacob2", jacob2);
		}

		// Heart of the Mountain moved from mining_core.{experience,nodes} to skill_tree.{experience,nodes}.mining.
		// There are now up to 5 tree slots: slot 1 is nodes.mining, slot N is nodes.mining_N, and each slot has its own
		// powder_spent_<powder>[_N]. powder_<powder> became the total earned. Map the selected slot back.
		JsonObject miningCore = object(member, "mining_core");
		if (miningCore != null) {
			JsonElement slotElement = element(member, "skill_tree", "selected_skill_tree_slot", "mining");
			int slot = slotElement != null && slotElement.isJsonPrimitive() ? slotElement.getAsInt() : 1;
			String suffix = slot > 1 ? "_" + slot : "";
			copy(miningCore, "experience", element(member, "skill_tree", "experience", "mining"));
			copy(miningCore, "nodes", element(member, "skill_tree", "nodes", "mining" + suffix));
			if (element(member, "skill_tree") != null) {
				miningCore.addProperty("powder_is_total", true);
				for (String powder : new String[]{"mithril", "gemstone", "glacite"}) {
					copy(miningCore, "powder_spent_selected_" + powder, element(miningCore, "powder_spent_" + powder + suffix));
				}
			}
		}

		// Bestiary: bestiary.kills.<mob> -> bestiary.kills_<mob>, same for deaths.
		JsonObject bestiary = object(member, "bestiary");
		if (bestiary != null) {
			flattenInto(bestiary, object(bestiary, "kills"), "kills_");
			flattenInto(bestiary, object(bestiary, "deaths"), "deaths_");
		}

		// Stats: v1's flat "stats" object, rebuilt from v2's nested "player_stats".
		JsonObject playerStats = object(member, "player_stats");
		if (playerStats != null && !member.has("stats")) {
			JsonObject stats = new JsonObject();
			for (Map.Entry<String, JsonElement> entry : playerStats.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) stats.add(entry.getKey(), entry.getValue());
			}
			flattenInto(stats, object(playerStats, "kills"), "kills_");
			flattenInto(stats, object(playerStats, "deaths"), "deaths_");
			flattenInto(stats, object(playerStats, "auctions"), "auctions_");
			flattenInto(stats, object(playerStats, "pets", "milestone"), "pet_milestone_");
			JsonObject itemsFished = object(playerStats, "items_fished");
			if (itemsFished != null) {
				copy(stats, "items_fished", itemsFished.get("total"));
				copy(stats, "items_fished_treasure", itemsFished.get("treasure"));
				copy(stats, "items_fished_large_treasure", itemsFished.get("large_treasure"));
			}
			member.add("stats", stats);
		}
	}

	/** Copies each primitive in {@code source} into {@code target} as {@code prefix + key}. */
	private static void flattenInto(JsonObject target, JsonObject source, String prefix) {
		if (source == null) return;
		for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) copy(target, prefix + entry.getKey(), entry.getValue());
		}
	}

	private static void copy(JsonObject target, String key, JsonElement value) {
		if (value != null && !value.isJsonNull() && !target.has(key)) target.add(key, value);
	}

	private static JsonElement element(JsonObject root, String... path) {
		JsonElement current = root;
		for (String part : path) {
			if (current == null || !current.isJsonObject()) return null;
			current = current.getAsJsonObject().get(part);
		}
		return current;
	}

	private static JsonObject object(JsonObject root, String... path) {
		JsonElement element = element(root, path);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}
}
