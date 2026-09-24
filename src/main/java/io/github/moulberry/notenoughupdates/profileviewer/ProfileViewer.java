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

// Portions of this code are from the SkyBlockPv mod.

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.moulberry.notenoughupdates.NEUManager;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.util.ApiBackoff;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of the Forge 1.8.9 {@code profileviewer.ProfileViewer} data-fetching/model class onto the Fabric 26.1.2
 * (Mojang-mapped) API. This is the data layer only - {@code GuiProfileViewer} and the {@code *Page} GUI classes
 * are a separate, later porting pass.
 *
 * <p>API mapping notes (see also {@link Utils} for the general ones):
 * <ul>
 *   <li>{@code net.minecraft.nbt.NBTTagCompound}/{@code NBTTagList} -&gt; {@code net.minecraft.nbt.CompoundTag}/
 *   {@code ListTag}.</li>
 *   <li>{@code net.minecraft.nbt.CompressedStreamTools.readCompressed(InputStream)} -&gt;
 *   {@code net.minecraft.nbt.NbtIo.readCompressed(InputStream, NbtAccounter)} (the accounter is a size-limit
 *   guard; {@code NbtAccounter.unlimitedHeap()} matches the old unlimited behaviour).</li>
 *   <li>{@code NBTTagCompound#getTagList(name, id)}/{@code getCompoundTagAt(i)}/{@code getKeySet()} ->
 *   {@code CompoundTag#getListOrEmpty(name)}/{@code ListTag#getCompoundOrEmpty(i)}/{@code CompoundTag#keySet()};
 *   most getters now return {@code Optional<T>} (or have an {@code xOr(name, default)} convenience form) rather
 *   than silently defaulting.</li>
 *   <li>Old {@code net.minecraft.init.Items}/{@code Blocks} + {@code Item.getItemFromBlock(Block)} -&gt;
 *   {@code net.minecraft.world.item.Items}/{@code net.minecraft.world.level.block.Blocks} +
 *   {@code Block#asItem()}.</li>
 *   <li>Former "damage"-based item subtypes (e.g. dye colour) no longer exist; each former subtype is now its
 *   own {@code Items} constant (see the collection-icon map below).</li>
 * </ul>
 *
 * <p>Behavioural note on {@code NotEnoughUpdates.INSTANCE.manager}: the original code used the static Forge
 * singleton in {@code getResourceCollectionInformation}/{@code Profile#getBingoInformation} instead of the
 * {@code manager} field/closure that was available in both places. That was harmless there (both referred to the
 * same manager in practice) but was an inconsistency left over from copy-pasting. This port uses {@code manager}
 * (the instance actually injected into this {@code ProfileViewer}) consistently instead, since
 * {@code getResourceCollectionInformation} no longer needs to be {@code static} to do that.
 */
public class ProfileViewer {

	private static final HashMap<String, String> petRarityToNumMap = new HashMap<String, String>() {
		{
			put("COMMON", "0");
			put("UNCOMMON", "1");
			put("RARE", "2");
			put("EPIC", "3");
			put("LEGENDARY", "4");
			put("MYTHIC", "5");
		}
	};
	private static final LinkedHashMap<String, ItemStack> skillToSkillDisplayMap =
		new LinkedHashMap<String, ItemStack>() {
			{
				put("taming", Utils.createItemStack(Items.PIG_SPAWN_EGG, ChatFormatting.LIGHT_PURPLE + "Taming"));
				put("mining", Utils.createItemStack(Items.STONE_PICKAXE, ChatFormatting.GRAY + "Mining"));
				put(
					"foraging",
					Utils.createItemStack(Blocks.OAK_SAPLING, ChatFormatting.DARK_GREEN + "Foraging")
				);
				put(
					"enchanting",
					Utils.createItemStack(Blocks.ENCHANTING_TABLE, ChatFormatting.GREEN + "Enchanting")
				);
				put(
					"carpentry",
					Utils.createItemStack(Blocks.CRAFTING_TABLE, ChatFormatting.DARK_RED + "Carpentry")
				);
				put("farming", Utils.createItemStack(Items.GOLDEN_HOE, ChatFormatting.YELLOW + "Farming"));
				put("combat", Utils.createItemStack(Items.STONE_SWORD, ChatFormatting.RED + "Combat"));
				put("fishing", Utils.createItemStack(Items.FISHING_ROD, ChatFormatting.AQUA + "Fishing"));
				// Added by Hypixel in 2025 (Galatea), after NEU's last update.
				put("hunting", Utils.createItemStack(Items.LEAD, ChatFormatting.DARK_AQUA + "Hunting"));
				put("alchemy", Utils.createItemStack(Items.BREWING_STAND, ChatFormatting.BLUE + "Alchemy"));
				put("runecrafting", Utils.createItemStack(Items.MAGMA_CREAM, ChatFormatting.DARK_PURPLE + "Runecrafting"));
				// Social isn't in the grid: it sits under the SkyBlock level on the basic page (see BasicPage).
				put("zombie", Utils.createItemStack(Items.ROTTEN_FLESH, ChatFormatting.GOLD + "Rev Slayer"));
				put("spider", Utils.createItemStack(Items.SPIDER_EYE, ChatFormatting.GOLD + "Tara Slayer"));
				put("wolf", Utils.createItemStack(Items.BONE, ChatFormatting.GOLD + "Sven Slayer"));
				put("enderman", Utils.createItemStack(Items.ENDER_PEARL, ChatFormatting.GOLD + "Ender Slayer"));
				put("blaze", Utils.createItemStack(Items.BLAZE_ROD, ChatFormatting.GOLD + "Blaze Slayer"));
				put("vampire", Utils.createItemStack(Items.REDSTONE, ChatFormatting.GOLD + "Vampire Slayer"));
			}
		};
	private static final ItemStack CAT_FARMING = Utils.createItemStack(
		Items.GOLDEN_HOE,
		ChatFormatting.YELLOW + "Farming"
	);
	private static final ItemStack CAT_MINING = Utils.createItemStack(
		Items.STONE_PICKAXE,
		ChatFormatting.GRAY + "Mining"
	);
	private static final ItemStack CAT_COMBAT = Utils.createItemStack(
		Items.STONE_SWORD,
		ChatFormatting.RED + "Combat"
	);
	private static final ItemStack CAT_FORAGING = Utils.createItemStack(
		Blocks.OAK_SAPLING,
		ChatFormatting.DARK_GREEN + "Foraging"
	);
	private static final ItemStack CAT_FISHING = Utils.createItemStack(
		Items.FISHING_ROD,
		ChatFormatting.AQUA + "Fishing"
	);
	private static final LinkedHashMap<ItemStack, List<String>> collectionCatToCollectionMap =
		new LinkedHashMap<ItemStack, List<String>>() {
			{
				put(
					CAT_FARMING,
					Utils.createList(
						"WHEAT",
						"CARROT_ITEM",
						"POTATO_ITEM",
						"PUMPKIN",
						"MELON",
						"SEEDS",
						"MUSHROOM_COLLECTION",
						"INK_SACK:3",
						"CACTUS",
						"SUGAR_CANE",
						"FEATHER",
						"LEATHER",
						"PORK",
						"RAW_CHICKEN",
						"MUTTON",
						"RABBIT",
						"NETHER_STALK"
					)
				);
				put(
					CAT_MINING,
					Utils.createList(
						"COBBLESTONE",
						"COAL",
						"IRON_INGOT",
						"GOLD_INGOT",
						"DIAMOND",
						"INK_SACK:4",
						"EMERALD",
						"REDSTONE",
						"QUARTZ",
						"OBSIDIAN",
						"GLOWSTONE_DUST",
						"GRAVEL",
						"ICE",
						"NETHERRACK",
						"SAND",
						"ENDER_STONE",
						null,
						"MITHRIL_ORE",
						"HARD_STONE",
						"GEMSTONE_COLLECTION",
						"MYCEL",
						"SAND:1",
						"SULPHUR_ORE"
					)
				);
				put(
					CAT_COMBAT,
					Utils.createList(
						"ROTTEN_FLESH",
						"BONE",
						"STRING",
						"SPIDER_EYE",
						"SULPHUR",
						"ENDER_PEARL",
						"GHAST_TEAR",
						"SLIME_BALL",
						"BLAZE_ROD",
						"MAGMA_CREAM",
						null,
						null,
						null,
						null,
						"CHILI_PEPPER"
					)
				);
				put(CAT_FORAGING, Utils.createList("LOG", "LOG:1", "LOG:2", "LOG_2:1", "LOG_2", "LOG:3", null));
				put(
					CAT_FISHING,
					Utils.createList(
						"RAW_FISH",
						"RAW_FISH:1",
						"RAW_FISH:2",
						"RAW_FISH:3",
						"PRISMARINE_SHARD",
						"PRISMARINE_CRYSTALS",
						"CLAY_BALL",
						"WATER_LILY",
						"INK_SACK",
						"SPONGE",
						"MAGMA_FISH"
					)
				);
			}
		};
	private static final LinkedHashMap<ItemStack, List<String>> collectionCatToMinionMap =
		new LinkedHashMap<ItemStack, List<String>>() {
			{
				put(
					CAT_FARMING,
					Utils.createList(
						"WHEAT",
						"CARROT",
						"POTATO",
						"PUMPKIN",
						"MELON",
						null,
						"MUSHROOM",
						"COCOA",
						"CACTUS",
						"SUGAR_CANE",
						"CHICKEN",
						"COW",
						"PIG",
						null,
						"SHEEP",
						"RABBIT",
						"NETHER_WARTS",
						"SUNFLOWER"
					)
				);
				put(
					CAT_MINING,
					Utils.createList(
						"COBBLESTONE",
						"COAL",
						"IRON",
						"GOLD",
						"DIAMOND",
						"LAPIS",
						"EMERALD",
						"REDSTONE",
						"QUARTZ",
						"OBSIDIAN",
						"GLOWSTONE",
						"GRAVEL",
						"ICE",
						null,
						"SAND",
						"ENDER_STONE",
						"SNOW",
						"MITHRIL",
						"HARD_STONE",
						null,
						"MYCELIUM",
						"RED_SAND",
						null
					)
				);
				put(
					CAT_COMBAT,
					Utils.createList(
						"ZOMBIE",
						"SKELETON",
						"SPIDER",
						"CAVESPIDER",
						"CREEPER",
						"ENDERMAN",
						"GHAST",
						"SLIME",
						"BLAZE",
						"MAGMA_CUBE",
						"REVENANT",
						"TARANTULA",
						"VOIDLING",
						"INFERNO",
						"VAMPIRE"
					)
				);
				put(CAT_FORAGING, Utils.createList("OAK", "SPRUCE", "BIRCH", "DARK_OAK", "ACACIA", "JUNGLE", "FLOWER"));
				put(CAT_FISHING, Utils.createList("FISHING", null, null, null, null, null, "CLAY", "LILY_PAD", null, null));
			}
		};
	private static final LinkedHashMap<String, ItemStack> collectionToCollectionDisplayMap =
		new LinkedHashMap<String, ItemStack>() {
			{
				/* FARMING COLLECTIONS */
				put("WHEAT", Utils.createItemStack(Items.WHEAT, ChatFormatting.YELLOW + "Wheat"));
				put("CARROT_ITEM", Utils.createItemStack(Items.CARROT, ChatFormatting.YELLOW + "Carrot"));
				put("POTATO_ITEM", Utils.createItemStack(Items.POTATO, ChatFormatting.YELLOW + "Potato"));
				put(
					"PUMPKIN",
					Utils.createItemStack(Blocks.PUMPKIN, ChatFormatting.YELLOW + "Pumpkin")
				);
				put("MELON", Utils.createItemStack(Items.MELON_SLICE, ChatFormatting.YELLOW + "Melon"));
				put("SEEDS", Utils.createItemStack(Items.WHEAT_SEEDS, ChatFormatting.YELLOW + "Seeds"));
				put(
					"MUSHROOM_COLLECTION",
					Utils.createItemStack(Blocks.RED_MUSHROOM, ChatFormatting.YELLOW + "Mushroom")
				);
				put("INK_SACK:3", Utils.createItemStack(Items.COCOA_BEANS, ChatFormatting.YELLOW + "Cocoa Beans"));
				put(
					"CACTUS",
					Utils.createItemStack(Blocks.CACTUS, ChatFormatting.YELLOW + "Cactus")
				);
				put("SUGAR_CANE", Utils.createItemStack(Items.SUGAR_CANE, ChatFormatting.YELLOW + "Sugar Cane"));
				put("FEATHER", Utils.createItemStack(Items.FEATHER, ChatFormatting.YELLOW + "Feather"));
				put("LEATHER", Utils.createItemStack(Items.LEATHER, ChatFormatting.YELLOW + "Leather"));
				put("PORK", Utils.createItemStack(Items.PORKCHOP, ChatFormatting.YELLOW + "Raw Porkchop"));
				put("RAW_CHICKEN", Utils.createItemStack(Items.CHICKEN, ChatFormatting.YELLOW + "Raw Chicken"));
				put("MUTTON", Utils.createItemStack(Items.MUTTON, ChatFormatting.YELLOW + "Mutton"));
				put("RABBIT", Utils.createItemStack(Items.RABBIT, ChatFormatting.YELLOW + "Raw Rabbit"));
				put("NETHER_STALK", Utils.createItemStack(Items.NETHER_WART, ChatFormatting.YELLOW + "Nether Wart"));

				/* MINING COLLECTIONS */
				put(
					"COBBLESTONE",
					Utils.createItemStack(Blocks.COBBLESTONE, ChatFormatting.GRAY + "Cobblestone")
				);
				put("COAL", Utils.createItemStack(Items.COAL, ChatFormatting.GRAY + "Coal"));
				put("IRON_INGOT", Utils.createItemStack(Items.IRON_INGOT, ChatFormatting.GRAY + "Iron Ingot"));
				put("GOLD_INGOT", Utils.createItemStack(Items.GOLD_INGOT, ChatFormatting.GRAY + "Gold Ingot"));
				put("DIAMOND", Utils.createItemStack(Items.DIAMOND, ChatFormatting.GRAY + "Diamond"));
				put("INK_SACK:4", Utils.createItemStack(Items.LAPIS_LAZULI, ChatFormatting.GRAY + "Lapis Lazuli"));
				put("EMERALD", Utils.createItemStack(Items.EMERALD, ChatFormatting.GRAY + "Emerald"));
				put("REDSTONE", Utils.createItemStack(Items.REDSTONE, ChatFormatting.GRAY + "Redstone"));
				put("QUARTZ", Utils.createItemStack(Items.QUARTZ, ChatFormatting.GRAY + "Nether Quartz"));
				put(
					"OBSIDIAN",
					Utils.createItemStack(Blocks.OBSIDIAN, ChatFormatting.GRAY + "Obsidian")
				);
				put("GLOWSTONE_DUST", Utils.createItemStack(Items.GLOWSTONE_DUST, ChatFormatting.GRAY + "Glowstone Dust"));
				put("GRAVEL", Utils.createItemStack(Blocks.GRAVEL, ChatFormatting.GRAY + "Gravel"));
				put("ICE", Utils.createItemStack(Blocks.ICE, ChatFormatting.GRAY + "Ice"));
				put(
					"NETHERRACK",
					Utils.createItemStack(Blocks.NETHERRACK, ChatFormatting.GRAY + "Netherrack")
				);
				put("SAND", Utils.createItemStack(Blocks.SAND, ChatFormatting.GRAY + "Sand"));
				put(
					"ENDER_STONE",
					Utils.createItemStack(Blocks.END_STONE, ChatFormatting.GRAY + "End Stone")
				);
				put("MITHRIL_ORE", Utils.createItemStack(Items.PRISMARINE_CRYSTALS, ChatFormatting.GRAY + "Mithril"));
				put(
					"HARD_STONE",
					Utils.createItemStack(Blocks.STONE, ChatFormatting.GRAY + "Hard Stone")
				);
				put(
					"GEMSTONE_COLLECTION",
					Utils.createSkull(
						ChatFormatting.GRAY + "Gemstone",
						"e942eb66-a350-38e5-aafa-0dfc3e17b4ac",
						"ewogICJ0aW1lc3RhbXAiIDogMTYxODA4Mzg4ODc3MSwKICAicHJvZmlsZUlkIiA6ICJjNTBhZmE4YWJlYjk0ZTQ1OTRiZjFiNDI1YTk4MGYwMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUd29FQmFlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FhYzE1ZjZmY2YyY2U5NjNlZjRjYTcxZjFhODY4NWFkYjk3ZWI3NjllMWQxMTE5NGNiYmQyZTk2NGE4ODk3OGMiCiAgICB9CiAgfQp9"
					)
				);
				put(
					"MYCEL",
					Utils.createItemStack(Blocks.MYCELIUM, ChatFormatting.GRAY + "Mycelium")
				);
				put(
					"SAND:1",
					Utils.createItemStack(Blocks.RED_SAND, ChatFormatting.GRAY + "Red Sand")
				);
				put("SULPHUR_ORE", Utils.createItemStack(Items.GLOWSTONE_DUST, ChatFormatting.GRAY + "Sulphur"));

				/* COMBAT COLLECTIONS */
				put("ROTTEN_FLESH", Utils.createItemStack(Items.ROTTEN_FLESH, ChatFormatting.RED + "Rotten Flesh"));
				put("BONE", Utils.createItemStack(Items.BONE, ChatFormatting.RED + "Bone"));
				put("STRING", Utils.createItemStack(Items.STRING, ChatFormatting.RED + "String"));
				put("SPIDER_EYE", Utils.createItemStack(Items.SPIDER_EYE, ChatFormatting.RED + "Spider Eye"));
				put("SULPHUR", Utils.createItemStack(Items.GUNPOWDER, ChatFormatting.RED + "Gunpowder"));
				put("ENDER_PEARL", Utils.createItemStack(Items.ENDER_PEARL, ChatFormatting.RED + "Ender Pearl"));
				put("GHAST_TEAR", Utils.createItemStack(Items.GHAST_TEAR, ChatFormatting.RED + "Ghast Tear"));
				put("SLIME_BALL", Utils.createItemStack(Items.SLIME_BALL, ChatFormatting.RED + "Slimeball"));
				put("BLAZE_ROD", Utils.createItemStack(Items.BLAZE_ROD, ChatFormatting.RED + "Blaze Rod"));
				put("MAGMA_CREAM", Utils.createItemStack(Items.MAGMA_CREAM, ChatFormatting.RED + "Magma Cream"));
				put(
					"CHILI_PEPPER",
					Utils.createSkull(
						ChatFormatting.RED + "Chili Pepper",
						"3d47abaa-b40b-3826-b20c-d83a7f053bd9",
						"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg1OWM4ZGYxMTA5YzA4YTc1NjI3NWYxZDI4ODdjMjc0ODA0OWZlMzM4Nzc3NjlhN2I0MTVkNTZlZGE0NjlkOCJ9fX0"
					)
				);

				/* FORAGING COLLECTIONS */
				put(
					"LOG",
					Utils.createItemStack(Blocks.OAK_LOG, ChatFormatting.DARK_GREEN + "Oak Wood")
				);
				put(
					"LOG:1",
					Utils.createItemStack(Blocks.SPRUCE_LOG, ChatFormatting.DARK_GREEN + "Spruce Wood")
				);
				put(
					"LOG:2",
					Utils.createItemStack(Blocks.BIRCH_LOG, ChatFormatting.DARK_GREEN + "Birch Wood")
				);
				put(
					"LOG_2:1",
					Utils.createItemStack(Blocks.DARK_OAK_LOG, ChatFormatting.DARK_GREEN + "Dark Oak Wood")
				);
				put(
					"LOG_2",
					Utils.createItemStack(Blocks.ACACIA_LOG, ChatFormatting.DARK_GREEN + "Acacia Wood")
				);
				put(
					"LOG:3",
					Utils.createItemStack(Blocks.JUNGLE_LOG, ChatFormatting.DARK_GREEN + "Jungle Wood")
				);

				/* FISHING COLLECTIONS */
				put("RAW_FISH", Utils.createItemStack(Items.COD, ChatFormatting.AQUA + "Raw Fish"));
				put("RAW_FISH:1", Utils.createItemStack(Items.SALMON, ChatFormatting.AQUA + "Raw Salmon"));
				put("RAW_FISH:2", Utils.createItemStack(Items.TROPICAL_FISH, ChatFormatting.AQUA + "Clownfish"));
				put("RAW_FISH:3", Utils.createItemStack(Items.PUFFERFISH, ChatFormatting.AQUA + "Pufferfish"));
				put(
					"PRISMARINE_SHARD",
					Utils.createItemStack(Items.PRISMARINE_SHARD, ChatFormatting.AQUA + "Prismarine Shard")
				);
				put(
					"PRISMARINE_CRYSTALS",
					Utils.createItemStack(Items.PRISMARINE_CRYSTALS, ChatFormatting.AQUA + "Prismarine Crystals")
				);
				put("CLAY_BALL", Utils.createItemStack(Items.CLAY_BALL, ChatFormatting.AQUA + "Clay"));
				put(
					"WATER_LILY",
					Utils.createItemStack(Blocks.LILY_PAD, ChatFormatting.AQUA + "Lily Pad")
				);
				put("INK_SACK", Utils.createItemStack(Items.INK_SAC, ChatFormatting.AQUA + "Ink Sac"));
				put("SPONGE", Utils.createItemStack(Blocks.SPONGE, ChatFormatting.AQUA + "Sponge"));
				put(
					"MAGMA_FISH",
					Utils.createSkull(
						ChatFormatting.AQUA + "Magmafish",
						"5c53195c-5b98-3476-9731-c32647b22723",
						"ewogICJ0aW1lc3RhbXAiIDogMTY0MjQ4ODA3MDY2NiwKICAicHJvZmlsZUlkIiA6ICIzNDkxZjJiOTdjMDE0MWE2OTM2YjFjMjJhMmEwMGZiNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJKZXNzc3N1aGgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU2YjU5NTViMjk1NTIyYzk2ODk0ODE5NjBjMDFhOTkyY2ExYzc3NTRjZjRlZTMxM2M4ZGQwYzM1NmQzMzVmIgogICAgfQogIH0KfQ"
					)
				);
			}
		};
	private final AtomicBoolean updatingResourceCollection = new AtomicBoolean(false);
	private JsonObject resourceCollection = null;
	private final NEUManager manager;
	private final HashMap<String, JsonObject> uuidToHypixelProfile = new HashMap<>();
	private final HashMap<String, Profile> uuidToProfileMap = new HashMap<>();
	private final HashMap<String, String> nameToUuid = new HashMap<>();

	public ProfileViewer(NEUManager manager) {
		this.manager = manager;
	}

	public static LinkedHashMap<ItemStack, List<String>> getCollectionCatToMinionMap() {
		return collectionCatToMinionMap;
	}

	public static LinkedHashMap<String, ItemStack> getCollectionToCollectionDisplayMap() {
		return collectionToCollectionDisplayMap;
	}

	public static LinkedHashMap<ItemStack, List<String>> getCollectionCatToCollectionMap() {
		return collectionCatToCollectionMap;
	}

	public static Map<String, ItemStack> getSkillToSkillDisplayMap() {
		return Collections.unmodifiableMap(skillToSkillDisplayMap);
	}

	public static Level getLevel(JsonArray levelingArray, float xp, int levelCap, boolean cumulative) {
		Level levelObj = new Level();
		levelObj.totalXp = xp;
		levelObj.maxLevel = levelCap;

		for (int level = 0; level < levelingArray.size(); level++) {
			float levelXp = levelingArray.get(level).getAsFloat();

			if (levelXp > xp) {
				if (cumulative) {
					float previous = level > 0 ? levelingArray.get(level - 1).getAsFloat() : 0;
					levelObj.maxXpForLevel = (levelXp - previous);
					levelObj.level = 1 + level + (xp - levelXp) / levelObj.maxXpForLevel;
				} else {
					levelObj.maxXpForLevel = levelXp;
					levelObj.level = level + xp / levelXp;
				}

				if (levelObj.level > levelCap) {
					levelObj.level = levelCap;
					levelObj.maxed = true;
				}

				return levelObj;
			} else {
				if (!cumulative) {
					xp -= levelXp;
				}
			}
		}

		levelObj.level = Math.min(levelingArray.size(), levelCap);
		levelObj.maxed = true;
		return levelObj;
	}

	public JsonObject getResourceCollectionInformation() {
		if (resourceCollection != null) return resourceCollection;
		if (updatingResourceCollection.get()) return null;

		updatingResourceCollection.set(true);

		manager.apiUtils
			.newAnonymousHypixelApiRequest("resources/skyblock/collections")
			.requestJson()
			.thenAccept(jsonObject -> {
				updatingResourceCollection.set(false);
				if (jsonObject != null && jsonObject.has("success") && jsonObject.get("success").getAsBoolean()) {
					resourceCollection = jsonObject.get("collections").getAsJsonObject();
				}
			});
		return null;
	}

	public void getHypixelProfile(String name, Consumer<JsonObject> callback) {
		// Looked up by UUID: Hypixel dropped the old ?name= form, and the Better PV backend only accepts UUIDs.
		getPlayerUUID(name, uuid -> {
			if (uuid == null) {
				if (callback != null) callback.accept(null);
				return;
			}
			manager.apiUtils
				.newHypixelApiRequest("player")
				.queryArgument("uuid", uuid)
				.requestJson()
				.thenAccept(jsonObject -> {
						if (
							jsonObject != null &&
								jsonObject.has("success") &&
								jsonObject.get("success").getAsBoolean() &&
								jsonObject.get("player").isJsonObject()
						) {
							uuidToHypixelProfile.put(uuid, jsonObject.get("player").getAsJsonObject());
							if (callback != null) callback.accept(jsonObject);
						} else {
							if (callback != null) callback.accept(null);
						}
					}
				);
		});
	}

	public void putNameUuid(String name, String uuid) {
		nameToUuid.put(name, uuid);
	}

	public void getPlayerUUID(String name, Consumer<String> uuidCallback) {
		String nameF = name.toLowerCase();
		if (nameToUuid.containsKey(nameF)) {
			uuidCallback.accept(nameToUuid.get(nameF));
			return;
		}

		manager.apiUtils
			.request()
			.url("https://api.mojang.com/users/profiles/minecraft/" + nameF)
			.requestJson()
			.thenAccept(jsonObject -> {
				if (jsonObject != null && jsonObject.has("id") && jsonObject.get("id").isJsonPrimitive() &&
					((JsonPrimitive) jsonObject.get("id")).isString()) {
					String uuid = jsonObject.get("id").getAsString();
					nameToUuid.put(nameF, uuid);
					uuidCallback.accept(uuid);
					return;
				}
				uuidCallback.accept(null);
			});
	}

	public void getProfileByName(String name, Consumer<Profile> callback) {
		// The player is opening a profile: let key-protected requests try again if an API key error stopped them.
		ApiBackoff.reset();
		String nameF = name.toLowerCase();

		if (nameToUuid.containsKey(nameF) && nameToUuid.get(nameF) == null) {
			callback.accept(null);
			return;
		}

		getPlayerUUID(
			nameF,
			uuid -> {
				if (uuid == null) {
					callback.accept(null);
					nameToUuid.put(nameF, null);
				} else {
					if (!uuidToHypixelProfile.containsKey(uuid)) {
						getHypixelProfile(nameF, jsonObject -> {});
					}
					callback.accept(getProfileReset(uuid, ignored -> {}));
				}
			}
		);
	}

	public Profile getProfile(String uuid, Consumer<Profile> callback) {
		Profile profile = uuidToProfileMap.computeIfAbsent(uuid, k -> new Profile(uuid));
		if (profile.skyblockProfiles != null) {
			callback.accept(profile);
		} else {
			profile.getSkyblockProfiles(() -> callback.accept(profile));
		}
		return profile;
	}

	public Profile getProfileReset(String uuid, Consumer<Profile> callback) {
		if (uuidToProfileMap.containsKey(uuid)) uuidToProfileMap.get(uuid).resetCache();
		return getProfile(uuid, callback);
	}

	public static class Level {

		public float level = 0;
		public float maxXpForLevel = 0;
		public boolean maxed = false;
		public int maxLevel;
		public float totalXp;
	}

	public class Profile {

		private final String uuid;
		private final HashMap<String, JsonObject> profileMap = new HashMap<>();
		private final HashMap<String, JsonObject> petsInfoMap = new HashMap<>();
		private final HashMap<String, List<JsonObject>> coopProfileMap = new HashMap<>();
		private final HashMap<String, Map<String, Level>> skyblockInfoCache = new HashMap<>();
		private final HashMap<String, JsonObject> inventoryCacheMap = new HashMap<>();
		private final HashMap<String, JsonObject> collectionInfoMap = new HashMap<>();
		private final List<String> profileNames = new ArrayList<>();
		private final HashMap<String, PlayerStats.Stats> stats = new HashMap<>();
		private final HashMap<String, PlayerStats.Stats> passiveStats = new HashMap<>();
		private final HashMap<String, Long> networth = new HashMap<>();
		private final AtomicBoolean updatingSkyblockProfilesState = new AtomicBoolean(false);
		private final AtomicBoolean updatingGuildInfoState = new AtomicBoolean(false);
		private final AtomicBoolean updatingPlayerStatusState = new AtomicBoolean(false);
		private final AtomicBoolean updatingBingoInfo = new AtomicBoolean(false);
		/** Museum member data by profile name; an empty object once it's known there is none (see getMuseumInfo). */
		private final Map<String, JsonObject> museumInfoMap = new ConcurrentHashMap<>();
		private final Set<String> museumRequested = ConcurrentHashMap.newKeySet();
		/** Garden data by profile name (see getGardenInfo). */
		private final Map<String, JsonObject> gardenInfoMap = new ConcurrentHashMap<>();
		private final Set<String> gardenRequested = ConcurrentHashMap.newKeySet();
		private final Pattern COLL_TIER_PATTERN = Pattern.compile("_(-?\\d+)");
		private String latestProfile = null;
		private JsonArray skyblockProfiles = null;
		private volatile String skyblockProfilesError = null;
		private JsonObject guildInformation = null;
		private JsonObject playerStatus = null;
		private JsonObject bingoInformation = null;
		private long lastPlayerInfoState = 0;
		private long lastStatusInfoState = 0;
		private long lastGuildInfoState = 0;
		private long lastBingoInfoState = 0;

		public Profile(String uuid) {
			this.uuid = uuid;
		}

		public JsonObject getPlayerStatus() {
			if (playerStatus != null) return playerStatus;
			if (updatingPlayerStatusState.get()) return null;

			long currentTime = System.currentTimeMillis();
			if (currentTime - lastStatusInfoState < 15 * 1000) return null;
			lastStatusInfoState = currentTime;
			updatingPlayerStatusState.set(true);

			manager.apiUtils
				.newHypixelApiRequest("status")
				.queryArgument("uuid", "" + uuid)
				.requestJson()
				.handle((jsonObject, ex) -> {
					updatingPlayerStatusState.set(false);

					if (jsonObject != null && jsonObject.has("success") && jsonObject.get("success").getAsBoolean()) {
						playerStatus = jsonObject.get("session").getAsJsonObject();
					}
					return null;
				});
			return null;
		}

		public JsonObject getBingoInformation() {
			long currentTime = System.currentTimeMillis();
			if (bingoInformation != null && currentTime - lastBingoInfoState < 15 * 1000) return bingoInformation;
			if (updatingBingoInfo.get()) return bingoInformation;
			// See the matching comment in getSkyblockProfiles: rate-limit regardless of in-flight state, or a
			// fast-failing request (e.g. no API key) gets re-fired every rendered frame.
			if (currentTime - lastBingoInfoState < 15 * 1000) return null;

			lastBingoInfoState = currentTime;
			updatingBingoInfo.set(true);

			manager.apiUtils
				.newHypixelApiRequest("skyblock/bingo")
				.queryArgument("uuid", "" + uuid)
				.requestJson()
				.handle(((jsonObject, throwable) -> {
					updatingBingoInfo.set(false);

					if (jsonObject != null && jsonObject.has("success") && jsonObject.get("success").getAsBoolean()) {
						bingoInformation = jsonObject;
					} else {
						bingoInformation = null;
					}
					return null;
				}));
			return bingoInformation != null ? bingoInformation : null;
		}

		/**
		 * This player's museum ({@code members.<uuid>} of {@code v2/skyblock/museum}) for a profile: its donated
		 * {@code items} (by museum id) and {@code special} items, still NBT-encoded. Null while loading; an empty
		 * object if the player has no museum data (e.g. the museum API setting is off), or just {@code __error} if
		 * the request failed.
		 * Requested once per profile.
		 */
		public JsonObject getMuseumInfo(String profileName) {
			if (profileName == null) profileName = latestProfile;
			if (profileName == null) return null;
			JsonObject cached = museumInfoMap.get(profileName);
			if (cached != null) return cached;
			String profileId = getProfileIdFor(profileName);
			if (profileId == null || !museumRequested.add(profileName)) return null;

			String name = profileName;
			manager.apiUtils
				.newHypixelApiRequest("v2/skyblock/museum")
				.queryArgument("profile", profileId)
				.requestJson()
				.handle((jsonObject, ex) -> {
					boolean success = jsonObject != null && jsonObject.has("success") && jsonObject.get("success").getAsBoolean();
					JsonElement member = success ? Utils.getElement(jsonObject, "members." + uuid) : null;
					JsonObject info = member instanceof JsonObject object ? object : new JsonObject();
					if (!success) info.addProperty("__error", true);
					museumInfoMap.put(name, info);
					return null;
				});
			return null;
		}

		/**
		 * The profile's garden ({@code garden} of {@code v2/skyblock/garden}): plots, visitors, crop milestones and
		 * upgrades, composter. Null while loading; an empty object if the profile has no garden, or {@code __error}
		 * (with Hypixel's {@code __cause}, if any) if the request failed. Requested once per profile.
		 */
		public JsonObject getGardenInfo(String profileName) {
			if (profileName == null) profileName = latestProfile;
			if (profileName == null) return null;
			JsonObject cached = gardenInfoMap.get(profileName);
			if (cached != null) return cached;
			String profileId = getProfileIdFor(profileName);
			if (profileId == null || !gardenRequested.add(profileName)) return null;

			String name = profileName;
			manager.apiUtils
				.newHypixelApiRequest("v2/skyblock/garden")
				.queryArgument("profile", profileId)
				.requestJson()
				.handle((jsonObject, ex) -> {
					boolean success = jsonObject != null && jsonObject.has("success") && jsonObject.get("success").getAsBoolean();
					JsonObject info = success && jsonObject.get("garden") instanceof JsonObject garden ? garden : new JsonObject();
					// Hypixel answers "No garden data" with success false for a profile that never unlocked it.
					String cause = jsonObject == null ? null : Utils.getElementAsString(jsonObject.get("cause"), null);
					if (!success && (cause == null || !cause.toLowerCase(Locale.ROOT).contains("no garden"))) {
						info.addProperty("__error", true);
						if (cause != null) info.addProperty("__cause", cause);
					}
					gardenInfoMap.put(name, info);
					return null;
				});
			return null;
		}

		/** The Hypixel profile id ({@code profile_id}) of the profile with this name, or null if unknown. */
		private String getProfileIdFor(String profileName) {
			if (skyblockProfiles == null) return null;
			for (JsonElement element : skyblockProfiles) {
				if (element instanceof JsonObject profile && profile.has("cute_name") && profile.has("profile_id")
					&& profile.get("cute_name").getAsString().equalsIgnoreCase(profileName)) {
					return profile.get("profile_id").getAsString();
				}
			}
			return null;
		}

		/** Decodes one NBT-encoded item list ({@code {"type":0,"data":"<base64>"}}) into item JSON; null for empty slots. */
		public List<JsonObject> decodeItems(JsonElement encoded) {
			List<JsonObject> items = new ArrayList<>();
			String data = Utils.getElementAsString(Utils.getElement(encoded, "data"), null);
			if (data == null) return items;
			try {
				CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(data)), NbtAccounter.unlimitedHeap());
				ListTag list = nbt.getListOrEmpty("i");
				for (int i = 0; i < list.size(); i++) items.add(manager.getJsonFromNBTEntry(list.getCompoundOrEmpty(i)));
			} catch (IOException | IllegalArgumentException e) {
				// Unreadable: no items.
			}
			return items;
		}

		public long getNetWorth(String profileName) {
			if (profileName == null) profileName = latestProfile;
			if (networth.get(profileName) != null) return networth.get(profileName);
			if (!manager.auctionManager.isPricingReady()) return -1;
			if (getProfileInformation(profileName) == null) return -1;
			if (getInventoryInfo(profileName) == null) return -1;
			JsonObject museumInfo = getMuseumInfo(profileName);
			if (museumInfo == null) return -1;

			JsonObject inventoryInfo = getInventoryInfo(profileName);
			JsonObject profileInfo = getProfileInformation(profileName);

			HashMap<String, Long> mostExpensiveInternal = new HashMap<>();

			long networth = 0;
			for (Map.Entry<String, JsonElement> entry : inventoryInfo.entrySet()) {
				if (entry.getValue().isJsonArray()) {
					for (JsonElement element : entry.getValue().getAsJsonArray()) {
						if (element != null && element.isJsonObject()) {
							JsonObject item = element.getAsJsonObject();
							String internalname = item.get("internalname").getAsString();

							if (manager.auctionManager.isVanillaItem(internalname)) continue;

							JsonObject bzInfo = manager.auctionManager.getBazaarInfo(internalname);

							long auctionPrice;
							if (bzInfo != null && bzInfo.has("curr_sell")) {
								auctionPrice = (int) bzInfo.get("curr_sell").getAsFloat();
							} else {
								auctionPrice = (long) manager.auctionManager.getItemAvgBin(internalname);
								if (auctionPrice <= 0) {
									auctionPrice = manager.auctionManager.getLowestBin(internalname);
								}
							}

							try {
								if (item.has("item_contents")) {
									JsonArray bytesArr = item.get("item_contents").getAsJsonArray();
									byte[] bytes = new byte[bytesArr.size()];
									for (int bytesArrI = 0; bytesArrI < bytesArr.size(); bytesArrI++) {
										bytes[bytesArrI] = bytesArr.get(bytesArrI).getAsByte();
									}
									CompoundTag contents_nbt = NbtIo.readCompressed(
										new ByteArrayInputStream(bytes),
										NbtAccounter.unlimitedHeap()
									);
									ListTag items = contents_nbt.getListOrEmpty("i");
									for (int j = 0; j < items.size(); j++) {
										CompoundTag itemTag = items.getCompoundOrEmpty(j);
										if (itemTag.size() > 0) {
											CompoundTag nbt = itemTag.getCompoundOrEmpty("tag");
											String internalname2 = manager.getInternalnameFromNBT(nbt);
											if (internalname2 != null) {
												if (manager.auctionManager.isVanillaItem(internalname2)) continue;

												JsonObject bzInfo2 = manager.auctionManager.getBazaarInfo(internalname2);

												long auctionPrice2;
												if (bzInfo2 != null && bzInfo2.has("curr_sell")) {
													auctionPrice2 = (int) bzInfo2.get("curr_sell").getAsFloat();
												} else {
													auctionPrice2 = (long) manager.auctionManager.getItemAvgBin(internalname2);
													if (auctionPrice2 <= 0) {
														auctionPrice2 = manager.auctionManager.getLowestBin(internalname2);
													}
												}

												int count2 = itemTag.getByteOr("Count", (byte) 0);

												mostExpensiveInternal.put(
													internalname2,
													auctionPrice2 * count2 + mostExpensiveInternal.getOrDefault(internalname2, 0L)
												);
												networth += auctionPrice2 * count2;
											}
										}
									}
								}
							} catch (IOException ignored) {
							}

							int count = 1;
							if (element.getAsJsonObject().has("count")) {
								count = element.getAsJsonObject().get("count").getAsInt();
							}
							mostExpensiveInternal.put(
								internalname,
								auctionPrice * count + mostExpensiveInternal.getOrDefault(internalname, 0L)
							);
							networth += auctionPrice * count;
							networth += upgradeValue(item) * count;
						}
					}
				}
			}
			JsonObject petsInfo = getPetsInfo(profileName);
			if (petsInfo != null && petsInfo.has("pets")) {
				if (petsInfo.get("pets").isJsonArray()) {
					JsonArray pets = petsInfo.get("pets").getAsJsonArray();
					for (JsonElement element : pets) {
						if (element.isJsonObject()) {
							JsonObject pet = element.getAsJsonObject();

							String petname = pet.get("type").getAsString();
							String tier = pet.get("tier").getAsString();
							String tierNum = petRarityToNumMap.get(tier);
							if (tierNum != null) {
								int level = (int) Math.floor(PlayerStats.getPetLevel(
									petname, tier, Utils.getElementAsFloat(pet.get("exp"), 0)).level);
								networth += Math.max(0, manager.auctionManager.getPetLowestBin(
									petname, Integer.parseInt(tierNum), level));
							}
							String heldItem = Utils.getElementAsString(pet.get("heldItem"), null);
							if (heldItem != null) networth += getPrice(heldItem);
							String skin = Utils.getElementAsString(pet.get("skin"), null);
							if (skin != null) networth += getPrice(skin.startsWith("PET_SKIN_") ? skin : "PET_SKIN_" + skin);
						}
					}
				}
			}

			JsonElement sacksElement = Utils.getElement(profileInfo, "sacks_counts");
			if (sacksElement instanceof JsonObject sacks) {
				for (Map.Entry<String, JsonElement> entry : sacks.entrySet()) {
					long amount = Utils.getElementAsLong(entry.getValue(), 0);
					long price = getPrice(entry.getKey());
					if (amount > 0 && price > 0) networth += price * amount;
				}
			}

			// Match SkyBlockPV's museum category: donated and special items count, borrowed items do not.
			if (!museumInfo.has("__error")) {
				JsonElement donatedElement = museumInfo.get("items");
				if (donatedElement instanceof JsonObject donated) {
					for (JsonElement value : donated.asMap().values()) {
						if (!(value instanceof JsonObject entry)
							|| Utils.getElementAsBoolean(entry.get("borrowing"), false)) continue;
						for (JsonObject item : decodeItems(entry.get("items"))) networth += itemValue(item);
					}
				}
				JsonElement specialElement = museumInfo.get("special");
				if (specialElement instanceof JsonArray special) {
					for (JsonElement value : special) {
						for (JsonObject item : decodeItems(value)) networth += itemValue(item);
					}
				}
			}

			long bankBalance = (long) Utils.getElementAsDouble(Utils.getElement(profileInfo, "banking.balance"), 0);
			long purseBalance = (long) Utils.getElementAsDouble(Utils.getElement(profileInfo, "coin_purse"), 0);

			networth += bankBalance + purseBalance;
			if (networth == 0) return -1;

			this.networth.put(profileName, networth);
			return networth;
		}

		private long getPrice(String internalName) {
			JsonObject bazaar = manager.auctionManager.getBazaarInfo(internalName);
			if (bazaar != null && bazaar.has("curr_sell")) return (long) bazaar.get("curr_sell").getAsDouble();
			long price = (long) manager.auctionManager.getItemAvgBin(internalName);
			return price > 0 ? price : manager.auctionManager.getLowestBin(internalName);
		}

		private long itemValue(JsonObject item) {
			if (item == null || !item.has("internalname")) return 0;
			String internalName = item.get("internalname").getAsString();
			if (manager.auctionManager.isVanillaItem(internalName)) return 0;
			long price = getPrice(internalName);
			int count = Utils.getElementAsInt(item.get("count"), 1);
			return (price > 0 ? price : 0) * count + upgradeValue(item) * count;
		}

		/** Modern SkyBlock item value beyond the base ID, following SkyBlockAPI's calculator sources. */
		private long upgradeValue(JsonObject item) {
			if (!item.has("nbttag")) return 0;
			try {
				CompoundTag tag = Utils.parseLegacyNbt(item.get("nbttag").getAsString());
				CompoundTag attributes = tag.getCompoundOrEmpty("ExtraAttributes");
				long value = 0;
				if (attributes.getInt("rarity_upgrades").orElse(0) > 0) value += positivePrice("RECOMBOBULATOR_3000");

				CompoundTag enchants = attributes.getCompoundOrEmpty("enchantments");
				for (String enchant : enchants.keySet()) {
					int level = enchants.getInt(enchant).orElse(0);
					if (level > 0) value += positivePrice("ENCHANTMENT_" + enchant.toUpperCase(Locale.ROOT) + "_" + level);
				}

				int potatoBooks = attributes.getInt("hot_potato_count").orElse(0);
				value += positivePrice("HOT_POTATO_BOOK") * Math.min(10, potatoBooks);
				value += positivePrice("FUMING_POTATO_BOOK") * Math.max(0, potatoBooks - 10);
				if (attributes.getInt("art_of_war_count").orElse(0) > 0) value += positivePrice("THE_ART_OF_WAR");
				if (attributes.getInt("art_of_peace").orElse(attributes.getInt("artOfPeaceApplied").orElse(0)) > 0) value += positivePrice("THE_ART_OF_PEACE");
				if (attributes.getInt("book_of_stats").orElse(attributes.getInt("stats_book").orElse(0)) > 0) value += positivePrice("BOOK_OF_STATS");
				if (attributes.getInt("jalapeno_count").orElse(0) > 0) value += positivePrice("JALAPENO_BOOK");

				ListTag scrolls = attributes.getListOrEmpty("ability_scroll");
				for (int i = 0; i < scrolls.size(); i++) value += positivePrice(scrolls.getStringOr(i, ""));
				String[] masterStars = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};
				int stars = attributes.getInt("upgrade_level").orElse(attributes.getInt("dungeon_item_level").orElse(0));
				for (int i = 0; i < Math.min(masterStars.length, Math.max(0, stars - 5)); i++) value += positivePrice(masterStars[i]);

				for (String key : new String[]{"drill_part_fuel_tank", "drill_part_engine", "drill_part_upgrade_module",
					"power_ability_scroll", "dye_item", "applied_dye", "skin"}) {
					String id = attributes.getStringOr(key, "");
					if (!id.isEmpty()) value += positivePrice(id);
				}
				String enrichment = attributes.getStringOr("talisman_enrichment", "");
				if (!enrichment.isEmpty()) value += positivePrice("TALISMAN_ENRICHMENT_" + enrichment.toUpperCase(Locale.ROOT));

				CompoundTag gems = attributes.getCompoundOrEmpty("gems");
				for (String slot : gems.keySet()) {
					String quality = gems.getStringOr(slot, "").toUpperCase(Locale.ROOT);
					if (!quality.matches("ROUGH|FLAWED|FINE|FLAWLESS|PERFECT")) continue;
					String type = slot.replaceFirst("_[0-9]+$", "").replaceFirst("_gem$", "").toUpperCase(Locale.ROOT);
					value += positivePrice(quality + "_" + type + "_GEM");
				}
				return value;
			} catch (Exception ignored) {
				return 0;
			}
		}

		private long positivePrice(String internalName) {
			if (internalName == null || internalName.isEmpty()) return 0;
			return Math.max(0, getPrice(internalName));
		}

		public String getLatestProfile() {
			return latestProfile;
		}

		public JsonArray getSkyblockProfiles(Runnable runnable) {
			if (skyblockProfiles != null) return skyblockProfiles;

			long currentTime = System.currentTimeMillis();

			// Rate-limit retries regardless of in-flight state - a request that fails fast (e.g. no API key
			// configured, HTTP error) clears updatingSkyblockProfilesState almost instantly, and this method is
			// called every rendered frame from GuiProfileViewer, so gating only on "currently in flight" let a
			// failing request get re-fired 60+ times a second, flooding the HTTP thread pool until the game froze.
			if (updatingSkyblockProfilesState.get()) return null;
			if (currentTime - lastPlayerInfoState < 15 * 1000) return null;
			lastPlayerInfoState = currentTime;
			updatingSkyblockProfilesState.set(true);

			manager.apiUtils
				.newHypixelApiRequest("v2/skyblock/profiles")
				.queryArgument("uuid", "" + uuid)
				.requestJson()
				.handle((jsonObject, throwable) -> {
					updatingSkyblockProfilesState.set(false);

					if (jsonObject != null && jsonObject.has("success") && jsonObject.get("success").getAsBoolean()) {
						skyblockProfilesError = null;
						if (!jsonObject.has("profiles")) return null;
						skyblockProfiles = jsonObject.get("profiles").getAsJsonArray();
						// The pages read the v1 member layout; translate every member once here.
						for (JsonElement profileEle : skyblockProfiles) {
							JsonElement membersEle = profileEle.isJsonObject() ? profileEle.getAsJsonObject().get("members") : null;
							if (membersEle == null || !membersEle.isJsonObject()) continue;
							for (Map.Entry<String, JsonElement> memberEntry : membersEle.getAsJsonObject().entrySet()) {
								if (memberEntry.getValue().isJsonObject()) {
									ProfileV2Adapter.toV1(memberEntry.getValue().getAsJsonObject());
								}
							}
						}

						String lastCuteName = null;
						long lastLastSave = 0;

						profileNames.clear();

						for (JsonElement profileEle : skyblockProfiles) {
							JsonObject profile = profileEle.getAsJsonObject();

							if (!profile.has("members")) continue;
							JsonObject members = profile.get("members").getAsJsonObject();

							if (members.has(uuid)) {
								JsonObject member = members.get(uuid).getAsJsonObject();

								if (member.has("coop_invitation")) {
									if (!member.get("coop_invitation").getAsJsonObject().get("confirmed").getAsBoolean()) {
										continue;
									}
								}

								String cuteName = profile.get("cute_name").getAsString();
								profileNames.add(cuteName);
								if (profile.has("selected") && profile.get("selected").getAsBoolean()) {
									lastCuteName = cuteName;
									break;
								}
								if (lastCuteName == null) lastCuteName = cuteName;
								if (member.has("last_save")) {
									long lastSave = member.get("last_save").getAsLong();
									if (lastSave > lastLastSave) {
										lastLastSave = lastSave;
										lastCuteName = cuteName;
									}
								}
							}
						}
						latestProfile = lastCuteName;

						if (runnable != null) runnable.run();
					} else {
						String cause = jsonObject != null && jsonObject.has("cause")
							? jsonObject.get("cause").getAsString()
							: (throwable != null ? throwable.toString() : "unknown error");
						skyblockProfilesError = cause;
						NotEnoughUpdates.LOGGER.warn("skyblock/profiles request failed: {}", cause);
					}
					return null;
				});
			return null;
		}

		/** Non-null once a {@link #getSkyblockProfiles} request has come back with a definite failure (bad API
		 *  key, Hypixel API error, network error, etc.), so the GUI can stop showing "Loading..." forever. */
		public String getSkyblockProfilesError() {
			return skyblockProfilesError;
		}

		public JsonObject getGuildInformation(Runnable runnable) {
			if (guildInformation != null) return guildInformation;

			long currentTime = System.currentTimeMillis();

			// See the matching comment in getSkyblockProfiles: rate-limit regardless of in-flight state.
			if (updatingGuildInfoState.get()) return null;
			if (currentTime - lastGuildInfoState < 15 * 1000) return null;
			lastGuildInfoState = currentTime;
			updatingGuildInfoState.set(true);

			manager.apiUtils
				.newHypixelApiRequest("guild")
				.queryArgument("player", "" + uuid)
				.requestJson()
				.handle((jsonObject, ex) -> {
					updatingGuildInfoState.set(false);

					if (jsonObject != null && jsonObject.has("success") && jsonObject.get("success").getAsBoolean()) {
						if (!jsonObject.has("guild")) return null;

						guildInformation = jsonObject.get("guild").getAsJsonObject();

						if (runnable != null) runnable.run();
					}
					return null;
				});
			return null;
		}

		public List<String> getProfileNames() {
			return profileNames;
		}

		public JsonObject getProfileInformation(String profileName) {
			JsonArray playerInfo = getSkyblockProfiles(() -> {});
			if (playerInfo == null) return null;
			if (profileName == null) profileName = latestProfile;
			if (profileMap.containsKey(profileName)) return profileMap.get(profileName);

			for (int i = 0; i < skyblockProfiles.size(); i++) {
				if (!skyblockProfiles.get(i).isJsonObject()) {
					skyblockProfiles = null;
					return null;
				}
				JsonObject profile = skyblockProfiles.get(i).getAsJsonObject();
				if (profile.get("cute_name").getAsString().equalsIgnoreCase(profileName)) {
					if (!profile.has("members")) return null;
					JsonObject members = profile.get("members").getAsJsonObject();
					if (!members.has(uuid)) continue;
					JsonObject profileInfo = members.get(uuid).getAsJsonObject();
					if (profile.has("banking")) {
						profileInfo.add("banking", profile.get("banking").getAsJsonObject());
					}
					if (profile.has("game_mode")) {
						profileInfo.add("game_mode", profile.get("game_mode"));
					}
					profileMap.put(profileName, profileInfo);
					return profileInfo;
				}
			}

			return null;
		}

		public List<JsonObject> getCoopProfileInformation(String profileName) {
			JsonArray playerInfo = getSkyblockProfiles(() -> {});
			if (playerInfo == null) return null;
			if (profileName == null) profileName = latestProfile;
			if (coopProfileMap.containsKey(profileName)) return coopProfileMap.get(profileName);

			for (int i = 0; i < skyblockProfiles.size(); i++) {
				if (!skyblockProfiles.get(i).isJsonObject()) {
					skyblockProfiles = null;
					return null;
				}
				JsonObject profile = skyblockProfiles.get(i).getAsJsonObject();
				if (profile.get("cute_name").getAsString().equalsIgnoreCase(profileName)) {
					if (!profile.has("members")) return null;
					JsonObject members = profile.get("members").getAsJsonObject();
					if (!members.has(uuid)) return null;
					List<JsonObject> coopList = new ArrayList<>();
					for (Map.Entry<String, JsonElement> islandMember : members.entrySet()) {
						if (!islandMember.getKey().equals(uuid)) {
							JsonObject coopProfileInfo = islandMember.getValue().getAsJsonObject();
							coopList.add(coopProfileInfo);
						}
					}
					coopProfileMap.put(profileName, coopList);
					return coopList;
				}
			}

			return null;
		}

		public void resetCache() {
			skyblockProfiles = null;
			guildInformation = null;
			playerStatus = null;
			stats.clear();
			passiveStats.clear();
			profileNames.clear();
			profileMap.clear();
			coopProfileMap.clear();
			petsInfoMap.clear();
			skyblockInfoCache.clear();
			inventoryCacheMap.clear();
			collectionInfoMap.clear();
			networth.clear();
			// Museum and garden are kept once loaded, but a failed load is asked for again.
			museumInfoMap.entrySet().removeIf(entry -> {
				boolean failed = entry.getValue().has("__error");
				if (failed) museumRequested.remove(entry.getKey());
				return failed;
			});
			gardenInfoMap.entrySet().removeIf(entry -> {
				boolean failed = entry.getValue().has("__error");
				if (failed) gardenRequested.remove(entry.getKey());
				return failed;
			});
		}

		public int getCap(JsonObject leveling, String skillName) {
			JsonElement capsElement = Utils.getElement(leveling, "leveling_caps");
			return capsElement != null && capsElement.isJsonObject() && capsElement.getAsJsonObject().has(skillName)
				? capsElement.getAsJsonObject().get(skillName).getAsInt()
				: 50;
		}

		public Map<String, Level> getSkyblockInfo(String profileName) {
			JsonObject profileInfo = getProfileInformation(profileName);

			if (profileInfo == null) return null;
			if (profileName == null) profileName = latestProfile;
			if (skyblockInfoCache.containsKey(profileName)) return skyblockInfoCache.get(profileName);

			JsonObject leveling = Constants.LEVELING;
			if (leveling == null || !leveling.has("social")) {
				// TODO(fabric-port): original called Utils.showOutdatedRepoNotification() here, a GUI toast; the
				// repo constants simply aren't synced yet in this pass (see Constants javadoc).
				return null;
			}

			Map<String, Level> out = new HashMap<>();

			List<String> skills = Arrays.asList(
				"taming",
				"mining",
				"foraging",
				"enchanting",
				"carpentry",
				"farming",
				"combat",
				"fishing",
				"hunting",
				"alchemy",
				"runecrafting",
				"social"
			);
			float totalSkillXP = 0;
			for (String skillName : skills) {
				float skillExperience = Utils.getElementAsFloat(
					Utils.getElement(profileInfo, "experience_skill_" + (skillName.equals("social") ? "social2" : skillName)),
					0
				);
				totalSkillXP += skillExperience;

				JsonArray levelingArray = Utils.getElement(leveling, "leveling_xp").getAsJsonArray();
				if (skillName.equals("runecrafting")) {
					levelingArray = Utils.getElement(leveling, "runecrafting_xp").getAsJsonArray();
				} else if (skillName.equals("social")) {
					levelingArray = Utils.getElement(leveling, "social").getAsJsonArray();
				}

				int maxLevel = getCap(leveling, skillName);
				if (skillName.equals("farming")) {
					maxLevel += Utils.getElementAsInt(Utils.getElement(profileInfo, "jacob2.perks.farming_level_cap"), 0);
				} else if (skillName.equals("foraging")) {
					// Foraging's cap can be raised past 50 (player_data.experience.SKILL_FORAGING_extra_level_cap).
					maxLevel += Utils.getElementAsInt(Utils.getElement(profileInfo, "experience_skill_foraging_extra_level_cap"), 0);
				}
				out.put(skillName, getLevel(levelingArray, skillExperience, maxLevel, false));
			}

			// Skills API disabled?
			if (totalSkillXP <= 0) {
				return null;
			}

			out.put(
				"hotm",
				getLevel(
					Utils.getElement(leveling, "leveling_xp").getAsJsonArray(),
					Utils.getElementAsFloat(Utils.getElement(profileInfo, "mining_core.experience"), 0),
					getCap(leveling, "HOTM"),
					false
				)
			);

			out.put(
				"catacombs",
				getLevel(
					Utils.getElement(leveling, "catacombs").getAsJsonArray(),
					Utils.getElementAsFloat(Utils.getElement(profileInfo, "dungeons.dungeon_types.catacombs.experience"), 0),
					getCap(leveling, "catacombs"),
					false
				)
			);

			List<String> dungeonClasses = Arrays.asList("healer", "tank", "mage", "archer", "berserk");
			for (String className : dungeonClasses) {
				float classExperience = Utils.getElementAsFloat(
					Utils.getElement(profileInfo, "dungeons.player_classes." + className + ".experience"),
					0
				);
				out.put(
					className,
					getLevel(
						Utils.getElement(leveling, "catacombs").getAsJsonArray(),
						classExperience,
						getCap(leveling, "catacombs"),
						false
					)
				);
			}

			List<String> slayers = Arrays.asList("zombie", "spider", "wolf", "enderman", "blaze", "vampire");
			for (String slayerName : slayers) {
				JsonElement slayerXpTable = Utils.getElement(leveling, "slayer_xp." + slayerName);
				if (slayerXpTable == null || !slayerXpTable.isJsonArray()) continue;
				float slayerExperience = Utils.getElementAsFloat(Utils.getElement(
					profileInfo,
					"slayer_bosses." + slayerName + ".xp"
				), 0);
				// The table's length is the cap: 9 for most slayers, 5 for Vampire.
				JsonArray table = slayerXpTable.getAsJsonArray();
				out.put(slayerName, getLevel(table, slayerExperience, table.size(), true));
			}

			skyblockInfoCache.put(profileName, out);

			return out;
		}

		public JsonObject getInventoryInfo(String profileName) {
			JsonObject profileInfo = getProfileInformation(profileName);
			if (profileInfo == null) return null;
			if (profileName == null) profileName = latestProfile;
			if (inventoryCacheMap.containsKey(profileName)) return inventoryCacheMap.get(profileName);

			String inv_armor_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "inv_armor.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String fishing_bag_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "fishing_bag.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String quiver_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "quiver.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String ender_chest_contents_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "ender_chest_contents.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			//Todo clean this up
			//Fake string is so for I loop works the same
			String backpack_contents_json_fake = "fake should fix later";
			JsonObject backpack_contents_json = (JsonObject) Utils.getElement(profileInfo, "backpack_contents");
			JsonObject backpack_icons = (JsonObject) Utils.getElement(profileInfo, "backpack_icons");
			String personal_vault_contents_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "personal_vault_contents.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String wardrobe_contents_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "wardrobe_contents.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String potion_bag_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "potion_bag.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String inv_contents_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "inv_contents.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String talisman_bag_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "talisman_bag.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String candy_inventory_contents_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "candy_inventory_contents.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);
			String equipment_contents_bytes = Utils.getElementAsString(
				Utils.getElement(profileInfo, "equippment_contents.data"),
				"Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA="
			);

			JsonObject inventoryInfo = new JsonObject();

			String[] inv_names = new String[]{
				"inv_armor",
				"fishing_bag",
				"quiver",
				"ender_chest_contents",
				"backpack_contents",
				"personal_vault_contents",
				"wardrobe_contents",
				"potion_bag",
				"inv_contents",
				"talisman_bag",
				"candy_inventory_contents",
				"equippment_contents",
			};
			String[] inv_bytes = new String[]{
				inv_armor_bytes,
				fishing_bag_bytes,
				quiver_bytes,
				ender_chest_contents_bytes,
				backpack_contents_json_fake,
				personal_vault_contents_bytes,
				wardrobe_contents_bytes,
				potion_bag_bytes,
				inv_contents_bytes,
				talisman_bag_bytes,
				candy_inventory_contents_bytes,
				equipment_contents_bytes,
			};
			for (int i = 0; i < inv_bytes.length; i++) {
				try {
					String bytes = inv_bytes[i];

					JsonArray contents = new JsonArray();

					if (inv_names[i].equals("backpack_contents")) {
						JsonObject temp = getBackpackData(backpack_contents_json, backpack_icons);
						contents = (JsonArray) temp.get("contents");
						inventoryInfo.add("backpack_sizes", temp.get("backpack_sizes"));
					} else {
						CompoundTag inv_contents_nbt = NbtIo.readCompressed(
							new ByteArrayInputStream(Base64.getDecoder().decode(bytes)),
							NbtAccounter.unlimitedHeap()
						);
						ListTag items = inv_contents_nbt.getListOrEmpty("i");
						for (int j = 0; j < items.size(); j++) {
							JsonObject item = manager.getJsonFromNBTEntry(items.getCompoundOrEmpty(j));
							contents.add(item);
						}
					}
					inventoryInfo.add(inv_names[i], contents);
				} catch (IOException e) {
					inventoryInfo.add(inv_names[i], new JsonArray());
				}
			}

			// The wardrobe moved to loadout.armor / loadout.equipment: numbered sets, each slot its own NBT blob.
			// Kept in set order with a null for each empty slot, four per set; loadout_<kind>_ids holds each set's id
			// in the same order (saved loadouts refer to sets by id).
			addLoadoutSets(inventoryInfo, "armor", Utils.getElement(profileInfo, "loadout.armor"),
				"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
			addLoadoutSets(inventoryInfo, "equipment", Utils.getElement(profileInfo, "loadout.equipment"),
				"EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4");

			// Rebuild the old packed wardrobe layout the Storage tab reads: 36 slots per page, a row per armour slot
			// and a column per set (9 sets a page).
			if (inventoryInfo.getAsJsonArray("wardrobe_contents").isEmpty()) {
				JsonArray armor = inventoryInfo.getAsJsonArray("loadout_armor");
				JsonArray armorIds = inventoryInfo.getAsJsonArray("loadout_armor_ids");
				JsonArray wardrobe = new JsonArray();
				for (int set = 0; set < armorIds.size(); set++) {
					int slot = armorIds.get(set).getAsInt() - 1;
					if (slot < 0) continue;
					int page = slot / 9;
					int column = slot % 9;
					for (int row = 0; row < 4; row++) {
						JsonElement piece = armor.get(set * 4 + row);
						if (piece.isJsonNull()) continue;
						int index = page * 36 + row * 9 + column;
						while (wardrobe.size() <= index) wardrobe.add(JsonNull.INSTANCE);
						wardrobe.set(index, piece);
					}
				}
				inventoryInfo.add("wardrobe_contents", wardrobe);
			}

			inventoryCacheMap.put(profileName, inventoryInfo);

			return inventoryInfo;
		}

		private void addLoadoutSets(JsonObject inventoryInfo, String kind, JsonElement sets, String... slots) {
			JsonArray contents = new JsonArray();
			JsonArray ids = new JsonArray();
			inventoryInfo.add("loadout_" + kind, contents);
			inventoryInfo.add("loadout_" + kind + "_ids", ids);
			if (!(sets instanceof JsonObject setsObject)) return;
			TreeMap<Integer, JsonObject> ordered = new TreeMap<>();
			for (Map.Entry<String, JsonElement> entry : setsObject.entrySet()) {
				if (!(entry.getValue() instanceof JsonObject set)) continue;
				try {
					ordered.put(Integer.parseInt(entry.getKey()), set);
				} catch (NumberFormatException ignored) {
					// equipped_set
				}
			}
			for (Map.Entry<Integer, JsonObject> entry : ordered.entrySet()) {
				JsonObject set = entry.getValue();
				ids.add((int) Utils.getElementAsFloat(set.get("id"), entry.getKey()));
				for (String slot : slots) {
					JsonObject item = null;
					String data = Utils.getElementAsString(Utils.getElement(set, slot + ".data"), "");
					if (!data.isEmpty()) {
						try {
							ListTag items = NbtIo.readCompressed(
								new ByteArrayInputStream(Base64.getDecoder().decode(data)),
								NbtAccounter.unlimitedHeap()
							).getListOrEmpty("i");
							if (!items.isEmpty()) item = manager.getJsonFromNBTEntry(items.getCompoundOrEmpty(0));
						} catch (IOException | IllegalArgumentException ignored) {
						}
					}
					contents.add(item == null ? JsonNull.INSTANCE : item);
				}
			}
		}

		public JsonObject getBackpackData(JsonObject backpackContentsJson, JsonObject backpackIcons) {
			if (backpackContentsJson == null || backpackIcons == null) {
				JsonObject bundledReturn = new JsonObject();
				bundledReturn.add("contents", new JsonArray());
				bundledReturn.add("backpack_sizes", new JsonArray());

				return bundledReturn;
			}

			String[] backpackArray = new String[0];

			//Create backpack array which sizes up
			for (Map.Entry<String, JsonElement> backpackIcon : backpackIcons.entrySet()) {
				if (backpackIcon.getValue() instanceof JsonObject) {
					JsonObject backpackData = (JsonObject) backpackContentsJson.get(backpackIcon.getKey());
					String bytes = Utils.getElementAsString(backpackData.get("data"), "Hz8IAAAAAAAAAD9iYD9kYD9kAAMAPwI/Gw0AAAA=");
					backpackArray = growArray(bytes, Integer.parseInt(backpackIcon.getKey()), backpackArray);
				}
			}

			//reduce backpack array to filter out not existent backpacks
			{
				String[] tempBackpackArray = new String[0];
				for (String s : backpackArray) {
					if (s != null) {
						String[] veryTempBackpackArray = new String[tempBackpackArray.length + 1];
						System.arraycopy(tempBackpackArray, 0, veryTempBackpackArray, 0, tempBackpackArray.length);

						veryTempBackpackArray[veryTempBackpackArray.length - 1] = s;
						tempBackpackArray = veryTempBackpackArray;
					}
				}
				backpackArray = tempBackpackArray;
			}

			JsonArray backpackSizes = new JsonArray();
			JsonArray contents = new JsonArray();

			for (String backpack : backpackArray) {
				try {
					CompoundTag inv_contents_nbt = NbtIo.readCompressed(
						new ByteArrayInputStream(Base64.getDecoder().decode(backpack)),
						NbtAccounter.unlimitedHeap()
					);
					ListTag items = inv_contents_nbt.getListOrEmpty("i");

					backpackSizes.add(new JsonPrimitive(items.size()));
					for (int j = 0; j < items.size(); j++) {
						JsonObject item = manager.getJsonFromNBTEntry(items.getCompoundOrEmpty(j));
						contents.add(item);
					}
				} catch (IOException ignored) {
				}
			}

			JsonObject bundledReturn = new JsonObject();
			bundledReturn.add("contents", contents);
			bundledReturn.add("backpack_sizes", backpackSizes);

			return bundledReturn;
		}

		public String[] growArray(String bytes, int index, String[] oldArray) {
			int newSize = Math.max(index + 1, oldArray.length);

			String[] newArray = new String[newSize];
			System.arraycopy(oldArray, 0, newArray, 0, oldArray.length);
			newArray[index] = bytes;

			return newArray;
		}

		public JsonObject getPetsInfo(String profileName) {
			JsonObject profileInfo = getProfileInformation(profileName);
			if (profileInfo == null) return null;
			if (petsInfoMap.containsKey(profileName)) return petsInfoMap.get(profileName);

			JsonObject petsInfo = new JsonObject();
			JsonElement petsElement = profileInfo.get("pets");
			if (petsElement != null && petsElement.isJsonArray()) {
				JsonObject activePet = null;
				JsonArray pets = petsElement.getAsJsonArray();
				for (int i = 0; i < pets.size(); i++) {
					JsonObject pet = pets.get(i).getAsJsonObject();
					if (pet.has("active") && pet.get("active").getAsBoolean()) {
						activePet = pet;
						break;
					}
				}
				petsInfo.add("active_pet", activePet);
				petsInfo.add("pets", pets);
				petsInfoMap.put(profileName, petsInfo);
				return petsInfo;
			}
			return null;
		}

		public JsonObject getCollectionInfo(String profileName) {
			JsonObject profileInfo = getProfileInformation(profileName);
			if (profileInfo == null) return null;
			JsonObject resourceCollectionInfo = getResourceCollectionInformation();
			if (resourceCollectionInfo == null) return null;
			if (profileName == null) profileName = latestProfile;
			if (collectionInfoMap.containsKey(profileName)) return collectionInfoMap.get(profileName);

			List<JsonObject> coopMembers = getCoopProfileInformation(profileName);
			JsonElement unlocked_coll_tiers_element = Utils.getElement(profileInfo, "unlocked_coll_tiers");
			JsonElement crafted_generators_element = Utils.getElement(profileInfo, "crafted_generators");
			JsonObject fakeMember = new JsonObject();
			fakeMember.add("crafted_generators", crafted_generators_element);
			coopMembers.add(coopMembers.size(), fakeMember);
			JsonElement collectionInfoElement = Utils.getElement(profileInfo, "collection");

			if (unlocked_coll_tiers_element == null || collectionInfoElement == null) {
				return null;
			}

			JsonObject collectionInfo = new JsonObject();
			JsonObject collectionTiers = new JsonObject();
			JsonObject minionTiers = new JsonObject();
			JsonObject personalAmounts = new JsonObject();
			JsonObject totalAmounts = new JsonObject();

			if (collectionInfoElement.isJsonObject()) {
				personalAmounts = collectionInfoElement.getAsJsonObject();
			}

			for (Map.Entry<String, JsonElement> entry : personalAmounts.entrySet()) {
				totalAmounts.addProperty(entry.getKey(), entry.getValue().getAsLong());
			}

			List<JsonObject> coopProfiles = getCoopProfileInformation(profileName);
			if (coopProfiles != null) {
				for (JsonObject coopProfile : coopProfiles) {
					JsonElement coopCollectionInfoElement = Utils.getElement(coopProfile, "collection");
					if (coopCollectionInfoElement != null && coopCollectionInfoElement.isJsonObject()) {
						for (Map.Entry<String, JsonElement> entry : coopCollectionInfoElement.getAsJsonObject().entrySet()) {
							float existing = Utils.getElementAsFloat(totalAmounts.get(entry.getKey()), 0);
							totalAmounts.addProperty(entry.getKey(), existing + entry.getValue().getAsLong());
						}
					}
				}
			}

			if (unlocked_coll_tiers_element.isJsonArray()) {
				JsonArray unlocked_coll_tiers = unlocked_coll_tiers_element.getAsJsonArray();
				for (int i = 0; i < unlocked_coll_tiers.size(); i++) {
					String unlocked = unlocked_coll_tiers.get(i).getAsString();

					Matcher matcher = COLL_TIER_PATTERN.matcher(unlocked);

					if (matcher.find()) {
						String tier_str = matcher.group(1);
						int tier = Integer.parseInt(tier_str);
						String coll = unlocked.substring(0, unlocked.length() - (matcher.group().length()));
						if (!collectionTiers.has(coll) || collectionTiers.get(coll).getAsInt() < tier) {
							collectionTiers.addProperty(coll, tier);
						}
					}
				}
			}
			for (JsonObject current_member_info : coopMembers) {
				if (
					!current_member_info.has("crafted_generators") || !current_member_info.get("crafted_generators").isJsonArray()
				) continue;
				JsonArray crafted_generators = Utils.getElement(current_member_info, "crafted_generators").getAsJsonArray();
				for (int j = 0; j < crafted_generators.size(); j++) {
					String unlocked = crafted_generators.get(j).getAsString();
					Matcher matcher = COLL_TIER_PATTERN.matcher(unlocked);
					if (matcher.find()) {
						String tierString = matcher.group(1);
						int tier = Integer.parseInt(tierString);
						String coll = unlocked.substring(0, unlocked.length() - (matcher.group().length()));
						if (!minionTiers.has(coll) || minionTiers.get(coll).getAsInt() < tier) {
							minionTiers.addProperty(coll, tier);
						}
					}
				}
			}

			JsonObject maxAmount = new JsonObject();
			JsonObject updatedCollectionTiers = new JsonObject();
			for (Map.Entry<String, JsonElement> totalAmountsEntry : totalAmounts.entrySet()) {
				String collName = totalAmountsEntry.getKey();
				int collTier = (int) Utils.getElementAsFloat(collectionTiers.get(collName), 0);

				int currentAmount = (int) Utils.getElementAsFloat(totalAmounts.get(collName), 0);
				if (currentAmount > 0) {
					for (Map.Entry<String, JsonElement> resourceEntry : resourceCollectionInfo.entrySet()) {
						JsonElement tiersElement = Utils.getElement(resourceEntry.getValue(), "items." + collName + ".tiers");
						if (tiersElement != null && tiersElement.isJsonArray()) {
							JsonArray tiers = tiersElement.getAsJsonArray();
							int maxTierAcquired = -1;
							int maxAmountRequired = -1;
							for (int i = 0; i < tiers.size(); i++) {
								JsonObject tierInfo = tiers.get(i).getAsJsonObject();
								int tier = tierInfo.get("tier").getAsInt();
								int amountRequired = tierInfo.get("amountRequired").getAsInt();
								if (currentAmount >= amountRequired) {
									maxTierAcquired = tier;
								}
								maxAmountRequired = amountRequired;
							}
							if (maxTierAcquired >= 0 && maxTierAcquired > collTier) {
								updatedCollectionTiers.addProperty(collName, maxTierAcquired);
							}
							maxAmount.addProperty(collName, maxAmountRequired);
						}
					}
				}
			}

			for (Map.Entry<String, JsonElement> collectionTiersEntry : updatedCollectionTiers.entrySet()) {
				collectionTiers.add(collectionTiersEntry.getKey(), collectionTiersEntry.getValue());
			}

			collectionInfo.add("minion_tiers", minionTiers);
			collectionInfo.add("max_amounts", maxAmount);
			collectionInfo.add("personal_amounts", personalAmounts);
			collectionInfo.add("total_amounts", totalAmounts);
			collectionInfo.add("collection_tiers", collectionTiers);

			collectionInfoMap.put(profileName, collectionInfo);

			return collectionInfo;
		}

		public PlayerStats.Stats getPassiveStats(String profileName) {
			if (passiveStats.get(profileName) != null) return passiveStats.get(profileName);
			JsonObject profileInfo = getProfileInformation(profileName);
			if (profileInfo == null) return null;

			PlayerStats.Stats passiveStats = PlayerStats.getPassiveBonuses(getSkyblockInfo(profileName), profileInfo);

			if (passiveStats != null) {
				passiveStats.add(PlayerStats.getBaseStats());
			}

			this.passiveStats.put(profileName, passiveStats);

			return passiveStats;
		}

		public PlayerStats.Stats getStats(String profileName) {
			if (stats.get(profileName) != null) return stats.get(profileName);
			JsonObject profileInfo = getProfileInformation(profileName);
			if (profileInfo == null) {
				return null;
			}

			PlayerStats.Stats stats = PlayerStats.getStats(
				getSkyblockInfo(profileName),
				getInventoryInfo(profileName),
				getCollectionInfo(profileName),
				getPetsInfo(profileName),
				profileInfo
			);
			if (stats == null) return null;
			this.stats.put(profileName, stats);
			return stats;
		}

		public String getUuid() {
			return uuid;
		}

		public JsonObject getHypixelProfile() {
			return uuidToHypixelProfile.getOrDefault(uuid, null);
		}
	}
}
