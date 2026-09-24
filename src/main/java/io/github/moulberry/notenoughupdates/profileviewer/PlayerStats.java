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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.info.QuiverInfo;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.JsonUtils;
import io.github.moulberry.notenoughupdates.util.PetData;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Port of the Forge 1.8.9 {@code profileviewer.PlayerStats} onto Fabric 26.1.2 (Mojang-mapped) APIs.
 *
 * <p>The original also reached into {@code GuiProfileViewer} (a GUI class, out of scope for this pass) for pet
 * levelling data that is really pure logic: {@code GuiProfileViewer.getPetLevel}/{@code PetLevel} and the
 * {@code MINION_RARITY_TO_NUM}/{@code PET_STAT_BOOSTS}/{@code PET_STAT_BOOSTS_MULT} static maps. Those are
 * relocated into this class (as {@link #getPetLevel}/{@link PetLevel}/{@link #PET_STAT_BOOSTS}/
 * {@link #PET_STAT_BOOSTS_MULT}) since that's genuinely where they're used from and they have no GUI/rendering
 * dependency at all - only {@code Constants.PETS} and {@code PetData.Rarity}.
 *
 * <p>API mapping notes: NBT reading uses the new {@code net.minecraft.nbt.NbtIo}/{@code CompoundTag}/
 * {@code ListTag} API (see {@link ProfileViewer} javadoc for the general mapping); the old
 * {@code net.minecraft.nbt.JsonToNBT.getTagFromJson(String)} (used to parse an accessory's stored SNBT string
 * back into a compound tag) is now {@code net.minecraft.nbt.TagParser.parseCompoundFully(String)}.
 */
public class PlayerStats {

	public static final String HEALTH = "health";
	public static final String DEFENCE = "defence";
	public static final String STRENGTH = "strength";
	public static final String SPEED = "speed";
	public static final String CRIT_CHANCE = "crit_chance";
	public static final String CRIT_DAMAGE = "crit_damage";
	public static final String BONUS_ATTACK_SPEED = "bonus_attack_speed";
	public static final String INTELLIGENCE = "intelligence";
	public static final String SEA_CREATURE_CHANCE = "sea_creature_chance";
	public static final String MAGIC_FIND = "magic_find";
	public static final String PET_LUCK = "pet_luck";
	public static final String MINING_FORTUNE = "mining_fortune";
	public static final String MINING_SPEED = "mining_speed";

	public static final String[] defaultStatNames = new String[] {
		"health",
		"defence",
		"strength",
		"speed",
		"crit_chance",
		"crit_damage",
		"bonus_attack_speed",
		"intelligence",
		"sea_creature_chance",
		"magic_find",
		"pet_luck",
		"ferocity",
		"ability_damage",
		"mining_fortune",
		"mining_speed",
	};
	public static final String[] defaultStatNamesPretty = new String[] {
		ChatFormatting.RED + "❤ Health",
		ChatFormatting.GREEN + "❈ Defence",
		ChatFormatting.RED + "❁ Strength",
		ChatFormatting.WHITE + "✦ Speed",
		ChatFormatting.BLUE + "☣ Crit Chance",
		ChatFormatting.BLUE + "☠ Crit Damage",
		ChatFormatting.YELLOW + "⚔ Attack Speed",
		ChatFormatting.AQUA + "✎ Intelligence",
		ChatFormatting.DARK_AQUA + "α SC Chance",
		ChatFormatting.AQUA + "✯ Magic Find",
		ChatFormatting.LIGHT_PURPLE + "♣ Pet Luck",
		ChatFormatting.RED + "⫽ Ferocity",
		ChatFormatting.RED + "✹ Ability Damage",
		ChatFormatting.GOLD + "☘ Mining Fortune",
		ChatFormatting.GOLD + "⸕ Mining Speed",
	};
	private static final HashMap<String, Pattern> STAT_PATTERN_MAP = new HashMap<String, Pattern>() {
		{
			put(HEALTH, Pattern.compile("^Health: ((?:\\+|-)[0-9]+)"));
			put(DEFENCE, Pattern.compile("^Defense: ((?:\\+|-)[0-9]+)"));
			put(STRENGTH, Pattern.compile("^Strength: ((?:\\+|-)[0-9]+)"));
			put(SPEED, Pattern.compile("^Speed: ((?:\\+|-)[0-9]+)"));
			put(CRIT_CHANCE, Pattern.compile("^Crit Chance: ((?:\\+|-)[0-9]+)"));
			put(CRIT_DAMAGE, Pattern.compile("^Crit Damage: ((?:\\+|-)[0-9]+)"));
			put(BONUS_ATTACK_SPEED, Pattern.compile("^Bonus Attack Speed: ((?:\\+|-)[0-9]+)"));
			put(INTELLIGENCE, Pattern.compile("^Intelligence: ((?:\\+|-)[0-9]+)"));
			put(SEA_CREATURE_CHANCE, Pattern.compile("^Sea Creature Chance: ((?:\\+|-)[0-9]+)"));
			put("ferocity", Pattern.compile("^Ferocity: ((?:\\+|-)[0-9]+)"));
			put("ability_damage", Pattern.compile("^Ability Damage: ((?:\\+|-)[0-9]+)"));
		}
	};

	// Relocated from GuiProfileViewer (see class javadoc).
	public static final HashMap<String, String> MINION_RARITY_TO_NUM = new HashMap<String, String>() {
		{
			put("COMMON", "0");
			put("UNCOMMON", "1");
			put("RARE", "2");
			put("EPIC", "3");
			put("LEGENDARY", "4");
			put("MYTHIC", "5");
		}
	};
	public static final HashMap<String, HashMap<String, Float>> PET_STAT_BOOSTS =
		new HashMap<String, HashMap<String, Float>>() {
			{
				put(
					"PET_ITEM_BIG_TEETH_COMMON",
					new HashMap<String, Float>() {
						{
							put("CRIT_CHANCE", 5f);
						}
					}
				);
				put(
					"PET_ITEM_HARDENED_SCALES_UNCOMMON",
					new HashMap<String, Float>() {
						{
							put("DEFENCE", 25f);
						}
					}
				);
				put(
					"PET_ITEM_LUCKY_CLOVER",
					new HashMap<String, Float>() {
						{
							put("MAGIC_FIND", 7f);
						}
					}
				);
				put(
					"PET_ITEM_SHARPENED_CLAWS_UNCOMMON",
					new HashMap<String, Float>() {
						{
							put("CRIT_DAMAGE", 15f);
						}
					}
				);
			}
		};
	public static final HashMap<String, HashMap<String, Float>> PET_STAT_BOOSTS_MULT =
		new HashMap<String, HashMap<String, Float>>() {
			{
				put(
					"PET_ITEM_IRON_CLAWS_COMMON",
					new HashMap<String, Float>() {
						{
							put("CRIT_DAMAGE", 1.4f);
							put("CRIT_CHANCE", 1.4f);
						}
					}
				);
				put(
					"PET_ITEM_TEXTBOOK",
					new HashMap<String, Float>() {
						{
							put("INTELLIGENCE", 2f);
						}
					}
				);
			}
		};

	public static Stats getBaseStats() {
		JsonObject misc = Constants.MISC;
		if (misc == null) return null;

		Stats stats = new Stats();
		for (String statName : defaultStatNames) {
			stats.addStat(statName, Utils.getElementAsFloat(Utils.getElement(misc, "base_stats." + statName), 0));
		}
		return stats;
	}

	private static Stats getFairyBonus(int fairyExchanges) {
		Stats bonus = new Stats();

		bonus.addStat(SPEED, fairyExchanges / 10);

		for (int i = 0; i < fairyExchanges; i++) {
			bonus.addStat(STRENGTH, (i + 1) % 5 == 0 ? 2 : 1);
			bonus.addStat(DEFENCE, (i + 1) % 5 == 0 ? 2 : 1);
			bonus.addStat(HEALTH, 3 + i / 2);
		}

		return bonus;
	}

	private static Stats getSkillBonus(Map<String, ProfileViewer.Level> skyblockInfo) {
		JsonObject bonuses = Constants.BONUSES;
		if (bonuses == null) return null;

		Stats skillBonus = new Stats();

		for (Map.Entry<String, ProfileViewer.Level> entry : skyblockInfo.entrySet()) {
			JsonElement element = Utils.getElement(bonuses, "bonus_stats." + entry.getKey());
			if (element != null && element.isJsonObject()) {
				JsonObject skillStatMap = element.getAsJsonObject();

				Stats currentBonus = new Stats();
				for (int i = 1; i <= entry.getValue().level; i++) {
					if (skillStatMap.has("" + i)) {
						currentBonus = new Stats();
						for (Map.Entry<String, JsonElement> entry2 : skillStatMap.get("" + i).getAsJsonObject().entrySet()) {
							currentBonus.addStat(entry2.getKey(), entry2.getValue().getAsFloat());
						}
					}
					skillBonus.add(currentBonus);
				}
			}
		}

		return skillBonus;
	}

	private static Stats getTamingBonus(JsonObject profile) {
		JsonObject bonuses = Constants.BONUSES;
		if (bonuses == null) return null;

		JsonElement petsElement = Utils.getElement(profile, "pets");
		if (petsElement == null) return new Stats();

		JsonArray pets = petsElement.getAsJsonArray();

		HashMap<String, String> highestRarityMap = new HashMap<>();

		for (int i = 0; i < pets.size(); i++) {
			JsonObject pet = pets.get(i).getAsJsonObject();
			highestRarityMap.put(pet.get("type").getAsString(), pet.get("tier").getAsString());
		}

		int petScore = 0;
		for (String value : highestRarityMap.values()) {
			petScore += Utils.getElementAsFloat(Utils.getElement(bonuses, "pet_value." + value.toUpperCase()), 0);
		}

		JsonElement petRewardsElement = Utils.getElement(bonuses, "pet_rewards");
		if (petRewardsElement == null) return null;
		JsonObject petRewards = petRewardsElement.getAsJsonObject();

		Stats petBonus = new Stats();
		for (int i = 0; i <= petScore; i++) {
			if (petRewards.has("" + i)) {
				petBonus = new Stats();
				for (Map.Entry<String, JsonElement> entry : petRewards.get("" + i).getAsJsonObject().entrySet()) {
					petBonus.addStat(entry.getKey(), entry.getValue().getAsFloat());
				}
			}
		}
		return petBonus;
	}

	private static float harpBonus(JsonObject profile) {
		String talk_to_melody = Utils.getElementAsString(Utils.getElement(profile, "objectives.talk_to_melody.status"), "INCOMPLETE");
		if (talk_to_melody.equalsIgnoreCase("COMPLETE")) {
			return 26;
		} else {
			return 0;
		}
	}

	private static float hotmFortune(JsonObject profile, Map<String, ProfileViewer.Level> skyblockInfo) {
		int miningLevelFortune = (int) (4 * (float) Math.floor(skyblockInfo.get("mining").level));
		int miningFortuneStat = ((Utils.getElementAsInt(Utils.getElement(profile, "mining_core.nodes.mining_fortune"), 0)) * 5);
		int miningFortune2Stat = ((Utils.getElementAsInt(Utils.getElement(profile, "mining_core.nodes.mining_fortune_2"), 0)) * 5);
		return miningFortuneStat + miningFortune2Stat + miningLevelFortune;
	}

	private static float hotmSpeed(JsonObject profile) {
		int miningSpeedStat = ((Utils.getElementAsInt(Utils.getElement(profile, "mining_core.nodes.mining_speed"), 0)) * 20);
		int miningSpeed2Stat = ((Utils.getElementAsInt(Utils.getElement(profile, "mining_core.nodes.mining_speed_2"), 0)) * 40);
		return miningSpeedStat + miningSpeed2Stat;
	}

	public static Stats getPassiveBonuses(Map<String, ProfileViewer.Level> skyblockInfo, JsonObject profile) {
		Stats passiveBonuses = new Stats();

		Stats fairyBonus = getFairyBonus((int) Utils.getElementAsFloat(Utils.getElement(profile, "fairy_exchanges"), 0));
		Stats skillBonus = getSkillBonus(skyblockInfo);
		Stats petBonus = getTamingBonus(profile);

		if (skillBonus == null || petBonus == null) {
			return null;
		}

		passiveBonuses.add(fairyBonus);
		passiveBonuses.add(skillBonus);
		passiveBonuses.addStat(INTELLIGENCE, harpBonus(profile));
		passiveBonuses.add(petBonus);

		return passiveBonuses;
	}

	public static Stats getHOTMBonuses(Map<String, ProfileViewer.Level> skyblockInfo, JsonObject profile) {
		Stats hotmBonuses = new Stats();

		hotmBonuses.addStat(MINING_FORTUNE, hotmFortune(profile, skyblockInfo));
		hotmBonuses.addStat(MINING_SPEED, hotmSpeed(profile));

		return hotmBonuses;
	}

	private static String getFullset(JsonArray armor, int ignore) {
		String fullset = null;

		for (int i = 0; i < armor.size(); i++) {
			if (i == ignore) continue;

			JsonElement itemElement = armor.get(i);
			if (itemElement == null || !itemElement.isJsonObject()) {
				fullset = null;
				break;
			}
			JsonObject item = itemElement.getAsJsonObject();
			String internalname = item.get("internalname").getAsString();

			String[] split = internalname.split("_");
			split[split.length - 1] = "";
			String armorname = String.join("_", split);

			if (fullset == null) {
				fullset = armorname;
			} else if (!fullset.equalsIgnoreCase(armorname)) {
				fullset = null;
				break;
			}
		}
		return fullset;
	}

	private static Stats getSetBonuses(
		Stats stats,
		JsonObject inventoryInfo,
		JsonObject collectionInfo,
		Map<String, ProfileViewer.Level> skyblockInfo,
		JsonObject profile
	) {
		JsonArray armor = Utils.getElement(inventoryInfo, "inv_armor").getAsJsonArray();

		Stats bonuses = new Stats();

		String fullset = getFullset(armor, -1);

		if (fullset != null) {
			switch (fullset) {
				case "LAPIS_ARMOR_":
					bonuses.addStat(HEALTH, 60);
					break;
				case "EMERALD_ARMOR_":
				{
					int bonus = (int) Math.floor(Utils.getElementAsFloat(Utils.getElement(collectionInfo, "EMERALD"), 0) / 3000);
					bonuses.addStat(HEALTH, bonus);
					bonuses.addStat(DEFENCE, bonus);
				}
				break;
				case "FAIRY_":
					bonuses.addStat(HEALTH, Utils.getElementAsFloat(Utils.getElement(profile, "fairy_souls_collected"), 0));
					break;
				case "SPEEDSTER_":
					bonuses.addStat(SPEED, 20);
					break;
				case "YOUNG_DRAGON_":
					bonuses.addStat(SPEED, 70);
					break;
				case "MASTIFF_":
					bonuses.addStat(HEALTH, 50 * Math.round(stats.get(CRIT_DAMAGE)));
					break;
				case "ANGLER_":
					bonuses.addStat(HEALTH, 10 * (float) Math.floor(skyblockInfo.get("fishing").level));
					bonuses.addStat(SEA_CREATURE_CHANCE, 4);
					break;
				case "ARMOR_OF_MAGMA_":
					int bonus = (int) Math.min(
						200,
						Math.floor(Utils.getElementAsFloat(Utils.getElement(profile, "stats.kills_magma_cube"), 0) / 10)
					);
					bonuses.addStat(HEALTH, bonus);
					bonuses.addStat(INTELLIGENCE, bonus);
				case "OLD_DRAGON_":
					bonuses.addStat(HEALTH, 200);
					bonuses.addStat(DEFENCE, 40);
					break;
			}
		}

		JsonElement chestplateElement = armor.get(2);
		if (chestplateElement != null && chestplateElement.isJsonObject()) {
			JsonObject chestplate = chestplateElement.getAsJsonObject();
			if (chestplate.get("internalname").getAsString().equals("OBSIDIAN_CHESTPLATE")) {
				JsonArray inventory = Utils.getElement(inventoryInfo, "inv_contents").getAsJsonArray();
				for (int i = 0; i < inventory.size(); i++) {
					JsonElement itemElement = inventory.get(i);
					if (itemElement != null && itemElement.isJsonObject()) {
						JsonObject item = itemElement.getAsJsonObject();
						if (item.get("internalname").getAsString().equals("OBSIDIAN")) {
							int count = 1;
							if (item.has("count")) {
								count = item.get("count").getAsInt();
							}
							bonuses.addStat(SPEED, count / 20);
						}
					}
				}
			}
		}

		return bonuses;
	}

	private static Stats getStatForItem(String internalname, JsonObject item, JsonArray lore) {
		Stats stats = new Stats();
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i).getAsString();
			for (Map.Entry<String, Pattern> entry : STAT_PATTERN_MAP.entrySet()) {
				Matcher matcher = entry.getValue().matcher(Utils.cleanColour(line));
				if (matcher.find()) {
					int bonus = Integer.parseInt(matcher.group(1));
					stats.addStat(entry.getKey(), bonus);
				}
			}
		}
		if (internalname.equals("DAY_CRYSTAL") || internalname.equals("NIGHT_CRYSTAL")) {
			stats.addStat(STRENGTH, 2.5f);
			stats.addStat(DEFENCE, 2.5f);
		}
		if (internalname.equals("NEW_YEAR_CAKE_BAG") && item.has("item_contents")) {
			JsonArray bytesArr = item.get("item_contents").getAsJsonArray();
			byte[] bytes = new byte[bytesArr.size()];
			for (int i = 0; i < bytesArr.size(); i++) {
				bytes[i] = bytesArr.get(i).getAsByte();
			}
			try {
				CompoundTag contents_nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
				var items = contents_nbt.getListOrEmpty("i");
				HashSet<Integer> cakes = new HashSet<>();
				for (int j = 0; j < items.size(); j++) {
					CompoundTag itemTag = items.getCompoundOrEmpty(j);
					if (itemTag.size() > 0) {
						CompoundTag nbt = itemTag.getCompoundOrEmpty("tag");
						if (nbt.contains("ExtraAttributes")) {
							CompoundTag ea = nbt.getCompoundOrEmpty("ExtraAttributes");
							if (ea.contains("new_years_cake")) {
								cakes.add(ea.getInt("new_years_cake").orElse(0));
							}
						}
					}
				}
				stats.addStat(HEALTH, cakes.size());
			} catch (IOException e) {
				e.printStackTrace();
				return stats;
			}
		}
		return stats;
	}

	private static Stats getItemBonuses(boolean talismanOnly, JsonArray... inventories) {
		JsonObject misc = Constants.MISC;
		if (misc == null) return null;
		JsonElement talisman_upgrades_element = misc.get("talisman_upgrades");
		if (talisman_upgrades_element == null) return null;
		JsonObject talisman_upgrades = talisman_upgrades_element.getAsJsonObject();

		HashMap<String, Stats> itemBonuses = new HashMap<>();
		for (JsonArray inventory : inventories) {
			for (int i = 0; i < inventory.size(); i++) {
				JsonElement itemElement = inventory.get(i);
				if (itemElement != null && itemElement.isJsonObject()) {
					JsonObject item = itemElement.getAsJsonObject();
					String internalname = item.get("internalname").getAsString();
					if (itemBonuses.containsKey(internalname)) {
						continue;
					}
					if (!talismanOnly || Utils.checkItemType(item.get("lore").getAsJsonArray(), true, "ACCESSORY", "HATCCESSORY") >= 0) {
						Stats itemBonus = getStatForItem(internalname, item, item.get("lore").getAsJsonArray());

						itemBonuses.put(internalname, itemBonus);

						for (Map.Entry<String, JsonElement> talisman_upgrades_item : talisman_upgrades.entrySet()) {
							JsonArray upgrades = talisman_upgrades_item.getValue().getAsJsonArray();
							for (int j = 0; j < upgrades.size(); j++) {
								String upgrade = upgrades.get(j).getAsString();
								if (upgrade.equals(internalname)) {
									itemBonuses.put(talisman_upgrades_item.getKey(), new Stats());
									break;
								}
							}
						}
					}
				}
			}
		}
		Stats itemBonusesStats = new Stats();
		for (Stats stats : itemBonuses.values()) {
			itemBonusesStats.add(stats);
		}

		return itemBonusesStats;
	}

	public static Stats getPetStatBonuses(JsonObject petsInfo) {
		JsonObject petsJson = Constants.PETS;
		JsonObject petnums = Constants.PETNUMS;
		if (petsJson == null || petnums == null) return new Stats();

		if (
			petsInfo != null &&
				petsInfo.has("active_pet") &&
				petsInfo.get("active_pet") != null &&
				petsInfo.get("active_pet").isJsonObject()
		) {
			JsonObject pet = petsInfo.get("active_pet").getAsJsonObject();
			if (
				pet.has("type") &&
					pet.get("type") != null &&
					pet.has("tier") &&
					pet.get("tier") != null &&
					pet.has("exp") &&
					pet.get("exp") != null
			) {
				String petname = pet.get("type").getAsString();
				String tier = pet.get("tier").getAsString();
				String heldItem = Utils.getElementAsString(pet.get("heldItem"), null);

				if (!petnums.has(petname)) {
					return new Stats();
				}

				String tierNum = MINION_RARITY_TO_NUM.get(tier);
				float exp = pet.get("exp").getAsFloat();
				if (tierNum == null) return new Stats();

				if (
					pet.has("heldItem") &&
						!pet.get("heldItem").isJsonNull() &&
						pet.get("heldItem").getAsString().equals("PET_ITEM_TIER_BOOST")
				) {
					tierNum = "" + (Integer.parseInt(tierNum) + 1);
				}

				PetLevel levelObj = getPetLevel(petname, tier, exp);
				float level = levelObj.level;
				float currentLevelRequirement = levelObj.currentLevelRequirement;
				float maxXP = levelObj.maxXP;
				pet.addProperty("level", level);
				pet.addProperty("currentLevelRequirement", currentLevelRequirement);
				pet.addProperty("maxXP", maxXP);

				JsonObject petItem = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(petname + ";" + tierNum);
				if (petItem == null) return new Stats();

				Stats stats = new Stats();

				JsonObject petInfo = petnums.get(petname).getAsJsonObject();
				if (petInfo.has(tier)) {
					JsonObject petInfoTier = petInfo.get(tier).getAsJsonObject();
					if (petInfoTier == null || !petInfoTier.has("1") || !petInfoTier.has("100")) {
						return new Stats();
					}

					JsonObject min = petInfoTier.get("1").getAsJsonObject();
					JsonObject max = petInfoTier.get("100").getAsJsonObject();

					float minMix = (100 - level) / 99f;
					float maxMix = (level - 1) / 99f;

					for (Map.Entry<String, JsonElement> entry : max.get("statNums").getAsJsonObject().entrySet()) {
						float statMax = entry.getValue().getAsFloat();
						float statMin = min.get("statNums").getAsJsonObject().get(entry.getKey()).getAsFloat();
						float val = statMin * minMix + statMax * maxMix;

						stats.addStat(entry.getKey().toLowerCase(), (int) Math.floor(val));
					}
				}

				if (heldItem != null) {
					HashMap<String, Float> petStatBoots = PET_STAT_BOOSTS.get(heldItem);
					HashMap<String, Float> petStatBootsMult = PET_STAT_BOOSTS_MULT.get(heldItem);
					if (petStatBoots != null) {
						for (Map.Entry<String, Float> entryBoost : petStatBoots.entrySet()) {
							String key = entryBoost.getKey().toLowerCase();
							try {
								stats.addStat(key, entryBoost.getValue());
							} catch (Exception ignored) {}
						}
					}
					if (petStatBootsMult != null) {
						for (Map.Entry<String, Float> entryBoost : petStatBootsMult.entrySet()) {
							String key = entryBoost.getKey().toLowerCase();
							try {
								stats.scale(key, entryBoost.getValue());
							} catch (Exception ignored) {}
						}
					}
				}

				return stats;
			}
		}
		return new Stats();
	}

	private static float getMaxLevelXp(JsonArray levels, int offset, int maxLevel) {
		float xpTotal = 0;

		for (int i = offset; i < offset + maxLevel - 1; i++) {
			xpTotal += levels.get(i).getAsFloat();
		}

		return xpTotal;
	}

	/**
	 * Relocated from {@code GuiProfileViewer.getPetLevel} (see class javadoc) - pure pet-levelling-curve math,
	 * no GUI/rendering dependency.
	 */
	public static PetLevel getPetLevel(
		String petType,
		String rarity,
		float exp
	) {
		int offset = PetData.Rarity.valueOf(rarity).petOffset;
		int maxLevel = 100;

		JsonArray levels = new JsonArray();
		levels.addAll(Constants.PETS.get("pet_levels").getAsJsonArray());
		JsonElement customLevelingJson = Constants.PETS.get("custom_pet_leveling").getAsJsonObject().get(petType);
		if (customLevelingJson != null) {
			switch (Utils.getElementAsInt(Utils.getElement(customLevelingJson, "type"), 0)) {
				case 1:
					levels.addAll(customLevelingJson.getAsJsonObject().get("pet_levels").getAsJsonArray());
					break;
				case 2:
					levels = customLevelingJson.getAsJsonObject().get("pet_levels").getAsJsonArray();
					break;
			}
			maxLevel = Utils.getElementAsInt(Utils.getElement(customLevelingJson, "max_level"), 100);
		}

		float maxXP = getMaxLevelXp(levels, offset, maxLevel);
		boolean isMaxed = exp >= maxXP;

		int level = 1;
		float currentLevelRequirement = 0;
		float xpThisLevel = 0;
		float pct = 0;

		if (isMaxed) {
			level = maxLevel;
			currentLevelRequirement = levels.get(offset + level - 2).getAsFloat();
			xpThisLevel = currentLevelRequirement;
			pct = 1;
		} else {
			long totalExp = 0;
			for (int i = offset; i < levels.size(); i++) {
				currentLevelRequirement = levels.get(i).getAsLong();
				totalExp += currentLevelRequirement;
				if (totalExp >= exp) {
					xpThisLevel = currentLevelRequirement - (totalExp - exp);
					level = Math.min(i - offset + 1, maxLevel);
					break;
				}
			}
			pct = currentLevelRequirement != 0 ? xpThisLevel / currentLevelRequirement : 0;
			level += pct;
		}

		PetLevel levelObj = new PetLevel();
		levelObj.level = level;
		levelObj.maxLevel = maxLevel;
		levelObj.currentLevelRequirement = currentLevelRequirement;
		levelObj.maxXP = maxXP;
		levelObj.levelPercentage = pct;
		levelObj.levelXp = xpThisLevel;
		levelObj.totalXp = exp;
		return levelObj;
	}

	private static float getStatMult(JsonObject inventoryInfo) {
		float mult = 1f;

		JsonArray armor = Utils.getElement(inventoryInfo, "inv_armor").getAsJsonArray();

		String fullset = getFullset(armor, -1);

		if (fullset != null && fullset.equals("SUPERIOR_DRAGON_")) {
			mult *= 1.05f;
		}

		for (int i = 0; i < armor.size(); i++) {
			JsonElement itemElement = armor.get(i);
			if (itemElement == null || !itemElement.isJsonObject()) continue;

			JsonObject item = itemElement.getAsJsonObject();
			String internalname = item.get("internalname").getAsString();

			String reforge = Utils.getElementAsString(Utils.getElement(item, "ExtraAttributes.modifier"), "");

			if (reforge.equals("renowned")) {
				mult *= 1.01f;
			}
		}

		return mult;
	}

	private static void applyLimits(Stats stats, JsonObject inventoryInfo) {
		//>0
		JsonArray armor = Utils.getElement(inventoryInfo, "inv_armor").getAsJsonArray();

		String fullset = getFullset(armor, 3);

		if (fullset != null) {
			switch (fullset) {
				case "CHEAP_TUXEDO_":
					stats.statsJson.add(HEALTH, new JsonPrimitive(Math.min(75, stats.get(HEALTH))));
				case "FANCY_TUXEDO_":
					stats.statsJson.add(HEALTH, new JsonPrimitive(Math.min(150, stats.get(HEALTH))));
				case "ELEGANT_TUXEDO_":
					stats.statsJson.add(HEALTH, new JsonPrimitive(Math.min(250, stats.get(HEALTH))));
			}
		}

		for (Map.Entry<String, JsonElement> statEntry : stats.statsJson.entrySet()) {
			if (
				statEntry.getKey().equals(CRIT_DAMAGE) ||
					statEntry.getKey().equals(INTELLIGENCE) ||
					statEntry.getKey().equals(BONUS_ATTACK_SPEED)
			) continue;
			stats.statsJson.add(statEntry.getKey(), new JsonPrimitive(Math.max(0, statEntry.getValue().getAsFloat())));
		}
	}

	public static Stats getStats(
		Map<String, ProfileViewer.Level> skyblockInfo,
		JsonObject inventoryInfo,
		JsonObject collectionInfo,
		JsonObject petsInfo,
		JsonObject profile
	) {
		if (skyblockInfo == null || inventoryInfo == null || collectionInfo == null || profile == null) return null;

		JsonArray armor = Utils.getElement(inventoryInfo, "inv_armor").getAsJsonArray();
		JsonArray inventory = Utils.getElement(inventoryInfo, "inv_contents").getAsJsonArray();
		JsonArray talisman_bag = Utils.getElement(inventoryInfo, "talisman_bag").getAsJsonArray();

		Stats passiveBonuses = getPassiveBonuses(skyblockInfo, profile);
		Stats hotmBonuses = getHOTMBonuses(skyblockInfo, profile);
		Stats armorBonuses = getItemBonuses(false, armor);
		Stats talismanBonuses = getItemBonuses(true, inventory, talisman_bag);

		if (passiveBonuses == null || armorBonuses == null || talismanBonuses == null) {
			return null;
		}

		Stats stats = getBaseStats();
		if (stats == null) {
			return null;
		}

		Stats petBonus = getPetStatBonuses(petsInfo);
		if (petBonus == null) return null;

		stats = stats.add(passiveBonuses).add(armorBonuses).add(talismanBonuses).add(petBonus).add(hotmBonuses);

		stats.add(getSetBonuses(stats, inventoryInfo, collectionInfo, skyblockInfo, profile));

		stats.scaleAll(getStatMult(inventoryInfo));

		applyLimits(stats, inventoryInfo);

		return stats;
	}

	/**
	 * Calculates the amount of Magical Power the player has using the list of accessories
	 *
	 * @param inventoryInfo inventory info object
	 * @return the amount of Magical Power or -1
	 * @see io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer.Profile#getInventoryInfo(String)
	 */
	public static int getMagicalPower(JsonObject inventoryInfo) {
		if (inventoryInfo == null || !inventoryInfo.has("talisman_bag") || !inventoryInfo.get("talisman_bag").isJsonArray()) {
			return -1;
		}

		Map<String, Integer> accessories = JsonUtils.getJsonArrayAsStream(inventoryInfo.get("talisman_bag").getAsJsonArray())
																								.map(o -> {
				try {
					return Utils.parseLegacyNbt(o.getAsJsonObject().get("nbttag").getAsString());
				} catch (Exception ignored) {
					return null;
				}
			}).filter(Objects::nonNull).map(tag -> {
				var loreTagList = tag.getCompoundOrEmpty("display").getListOrEmpty("Lore");
				String lastElement = loreTagList.getStringOr(loreTagList.size() - 1, "");
				if (lastElement.contains(ChatFormatting.OBFUSCATED.toString())) {
					lastElement = lastElement.substring(lastElement.indexOf(' ')).trim().substring(4);
				}
				JsonArray lastElementJsonArray = new JsonArray();
				lastElementJsonArray.add(new JsonPrimitive(lastElement));
				return new AbstractMap.SimpleEntry<>(
					tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", ""),
					Utils.getRarityFromLore(lastElementJsonArray)
				);
			}).sorted(Comparator.comparingInt(e -> -e.getValue())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2)->v1, LinkedHashMap::new));

		Set<String> ignoredTalismans = new HashSet<>();
		int powderAmount = 0;
		for (Map.Entry<String, Integer> entry : accessories.entrySet()) {
			if (ignoredTalismans.contains(entry.getKey())) {
				continue;
			}

			JsonArray children = Utils.getElementOrDefault(Constants.PARENTS, entry.getKey(), new JsonArray()).getAsJsonArray();
			for (JsonElement child : children) {
				ignoredTalismans.add(child.getAsString());
			}

			if (entry.getKey().equals("HEGEMONY_ARTIFACT")) {
				switch (entry.getValue()) {
					case 4:
						powderAmount += 16;
						break;
					case 5:
						powderAmount += 22;
						break;
				}
			}
			switch (entry.getValue()) {
				case 0:
				case 6:
					powderAmount += 3;
					break;
				case 1:
				case 7:
					powderAmount += 5;
					break;
				case 2:
					powderAmount += 8;
					break;
				case 3:
					powderAmount += 12;
					break;
				case 4:
					powderAmount += 16;
					break;
				case 5:
					powderAmount += 22;
					break;
			}
		}
		return powderAmount;
	}

	/**
	 * Finds the Magical Power the player selected if applicable
	 *
	 * @param profileInfo profile information object
	 * @return selected magical power as a String or null
	 * @see io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer.Profile#getProfileInformation(String)
	 */
	public static String getSelectedMagicalPower(JsonObject profileInfo) {
		String abs = "accessory_bag_storage";

		if (
			profileInfo == null ||
				!profileInfo.has(abs) ||
				!profileInfo.get(abs).isJsonObject() ||
				!profileInfo.get(abs).getAsJsonObject().has("selected_power")
		) {
			return null;
		}
		String selectedPower = profileInfo.get(abs).getAsJsonObject().get("selected_power").getAsString();
		return selectedPower.substring(0, 1).toUpperCase() + selectedPower.substring(1);
	}

	public static QuiverInfo getQuiverInfo(JsonObject inventoryInfo, JsonObject profileInfo) {
		if (inventoryInfo == null
			|| !inventoryInfo.has("quiver")
			|| !inventoryInfo.get("quiver").isJsonArray()) {
			return null;
		}
		QuiverInfo quiverInfo = new QuiverInfo();
		quiverInfo.arrows = new HashMap<>();

		JsonArray quiver = inventoryInfo.getAsJsonArray("quiver");
		for (JsonElement quiverEntry : quiver) {
			if (quiverEntry == null || quiverEntry.isJsonNull() || !quiverEntry.isJsonObject()) {
				continue;
			}
			JsonObject stack = quiverEntry.getAsJsonObject();
			if (!stack.has("internalname") || !stack.has("count")) {
				continue;
			}
			String internalName = stack.get("internalname").getAsString();
			int count = stack.get("count").getAsInt();

			quiverInfo.arrows.computeIfPresent(internalName, (key, existing) -> existing + count);
			quiverInfo.arrows.putIfAbsent(internalName, count);
		}

		if (profileInfo.has("favorite_arrow")) {
			quiverInfo.selectedArrow = profileInfo.get("favorite_arrow").getAsString();
		}

		return quiverInfo;
	}

	public static class PetLevel {

		public float level;
		public float maxLevel;
		public float currentLevelRequirement;
		public float maxXP;
		public float levelPercentage;
		public float levelXp;
		public float totalXp;
	}

	public static class Stats {

		JsonObject statsJson = new JsonObject();

		public Stats(Stats... statses) {
			for (Stats stats : statses) {
				add(stats);
			}
		}

		public float get(String statName) {
			if (statsJson.has(statName)) {
				return statsJson.get(statName).getAsFloat();
			} else {
				return 0;
			}
		}

		public Stats add(Stats stats) {
			for (Map.Entry<String, JsonElement> statEntry : stats.statsJson.entrySet()) {
				if (statEntry.getValue().isJsonPrimitive() && ((JsonPrimitive) statEntry.getValue()).isNumber()) {
					if (!statsJson.has(statEntry.getKey())) {
						statsJson.add(statEntry.getKey(), statEntry.getValue());
					} else {
						JsonPrimitive e = statsJson.get(statEntry.getKey()).getAsJsonPrimitive();
						float statNum = e.getAsFloat() + statEntry.getValue().getAsFloat();
						statsJson.add(statEntry.getKey(), new JsonPrimitive(statNum));
					}
				}
			}
			return this;
		}

		public void scale(String statName, float scale) {
			if (statsJson.has(statName)) {
				statsJson.add(statName, new JsonPrimitive(statsJson.get(statName).getAsFloat() * scale));
			}
		}

		public void scaleAll(float scale) {
			for (Map.Entry<String, JsonElement> statEntry : statsJson.entrySet()) {
				statsJson.add(statEntry.getKey(), new JsonPrimitive(statEntry.getValue().getAsFloat() * scale));
			}
		}

		public void addStat(String statName, float amount) {
			if (!statsJson.has(statName)) {
				statsJson.add(statName, new JsonPrimitive(amount));
			} else {
				JsonPrimitive e = statsJson.get(statName).getAsJsonPrimitive();
				statsJson.add(statName, new JsonPrimitive(e.getAsFloat() + amount));
			}
		}
	}
}
