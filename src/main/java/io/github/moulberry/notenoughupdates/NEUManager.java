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

package io.github.moulberry.notenoughupdates;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.moulberry.notenoughupdates.auction.APIManager;
import io.github.moulberry.notenoughupdates.util.ApiUtil;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.ItemResolutionQuery;
import io.github.moulberry.notenoughupdates.util.ItemUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.util.datafix.fixes.ItemStackTheFlatteningFix;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Heavily trimmed port of the Forge 1.8.9 {@code NEUManager} "god class". The original owned the entire repo
 * sync/update pipeline, recipe graph, config, crafting overlay, chat/command hooks, etc. Only the pieces that
 * {@code ProfileViewer}/{@code PlayerStats}/{@code QuiverInfo} actually call were ported: the API request
 * helper, the auction/bazaar manager, and NBT item-JSON resolution.
 *
 * <p>Repo syncing (downloading/extracting the NotEnoughUpdates-REPO zip into {@code repoLocation}) is handled by
 * {@link io.github.moulberry.notenoughupdates.util.RepoSync}, kicked off in the background by the constructor -
 * see that class's javadoc for how it's simplified vs. the original. {@link #getItemInformation()}/
 * {@link Constants} will be empty on a fresh config dir until that background sync completes.
 */
public class NEUManager {
	public final NotEnoughUpdates neu;
	public final APIManager auctionManager;
	public final ApiUtil apiUtils = new ApiUtil();
	public final Gson gson = new Gson();
	public File repoLocation;

	private final Map<String, JsonObject> itemInformationCache = new java.util.concurrent.ConcurrentHashMap<>();
	private static final JsonObject ITEM_INFORMATION_MISSING = new JsonObject();
	private final Map<String, JsonObject> itemInformationView = new java.util.AbstractMap<>() {
		@Override
		public JsonObject get(Object key) {
			return key instanceof String s ? lookupItemInformation(s) : null;
		}

		@Override
		public boolean containsKey(Object key) {
			return get(key) != null;
		}

		@Override
		public java.util.Set<Entry<String, JsonObject>> entrySet() {
			// Iterating the whole repo item list was never ported (nothing calls it) - only point lookups via
			// get()/containsKey() are supported, which is what every call site in this codebase actually does.
			throw new UnsupportedOperationException("getItemInformation() no longer supports iteration - use get(internalname)");
		}
	};
	private final Map<String, ItemStack> itemStackCache = new HashMap<>();

	/** Item renames after the 1.13 flattening, which {@code ItemStackTheFlatteningFix} doesn't cover. */
	private static final Map<String, String> POST_FLATTENING_RENAMES = Map.of(
		"minecraft:sign", "minecraft:oak_sign",
		"minecraft:melon_block", "minecraft:melon",
		"minecraft:rose_red", "minecraft:red_dye",
		"minecraft:dandelion_yellow", "minecraft:yellow_dye",
		"minecraft:cactus_green", "minecraft:green_dye",
		"minecraft:grass", "minecraft:short_grass",
		"minecraft:scute", "minecraft:turtle_scute",
		"minecraft:zombie_pigman_spawn_egg", "minecraft:zombified_piglin_spawn_egg"
	);

	public NEUManager(NotEnoughUpdates neu, File configLocation) {
		this.neu = neu;
		this.auctionManager = new APIManager(this);
		this.auctionManager.updateLowestBin();
		this.auctionManager.updateBazaar();

		if (!configLocation.exists()) {
			configLocation.mkdirs();
		}
		this.repoLocation = new File(configLocation, "repo");
		repoLocation.mkdir();

		Constants.load(repoLocation, gson);

		// Kick off a background download/extract of the repo if it's missing or stale (see RepoSync javadoc for
		// the simplified staleness check vs. the original's per-launch GitHub commit diff), then reload
		// Constants/getItemInformation() from whatever ends up on disk.
		io.github.moulberry.notenoughupdates.util.RepoSync.syncIfNeeded(repoLocation).thenRun(() -> {
			Constants.load(repoLocation, gson);
			itemInformationCache.clear();
			itemStackCache.clear();
		});
	}

	/**
	 * @see io.github.moulberry.notenoughupdates.util.ItemResolutionQuery
	 */
	public String getInternalnameFromNBT(CompoundTag tag) {
		return new ItemResolutionQuery(this)
			.withItemNBT(tag)
			.resolveInternalName();
	}

	public JsonObject getJsonFromNBTEntry(CompoundTag tag) {
		if (tag.size() == 0) return null;

		int id = tag.getShortOr("id", (short) 0);
		int damage = tag.getShortOr("Damage", (short) 0);
		int count = tag.getShortOr("Count", (short) tag.getIntOr("count", 0));
		// Hypixel now also sends modern components alongside the legacy NBT, notably the item model from its
		// SkyBlock resource pack (many items, e.g. baits, are plain paper without it).
		String itemModel = tag.getCompoundOrEmpty("components").getStringOr("minecraft:item_model", "");
		tag = tag.getCompoundOrEmpty("tag");

		if (id == 141) id = 391; //for some reason hypixel thinks carrots have id 141

		String internalname = getInternalnameFromNBT(tag);
		if (internalname == null) return null;

		CompoundTag display = tag.getCompoundOrEmpty("display");
		String[] lore = ItemUtils.getLore(tag).toArray(new String[0]);

		// Hypixel still sends the pre-1.13 numeric item id. Vanilla's own world-upgrade data fixer table turns it
		// into the old registry name (e.g. 261 -> minecraft:bow); resolveBaseItemStack then flattens name + damage
		// into today's item.
		String itemid = ItemIdFix.getItem(id);
		String displayName = display.getStringOr("Name", "");

		JsonObject item = new JsonObject();
		item.addProperty("internalname", internalname);
		item.addProperty("itemid", itemid);
		item.addProperty("displayname", displayName);

		if (tag.contains("ExtraAttributes")) {
			CompoundTag ea = tag.getCompoundOrEmpty("ExtraAttributes");

			byte[] bytes = null;
			for (String key : ea.keySet()) {
				if (key.endsWith("backpack_data") || key.equals("new_year_cake_bag_data")) {
					bytes = ea.getByteArray(key).orElse(null);
					break;
				}
			}
			if (bytes != null) {
				JsonArray bytesArr = new JsonArray();
				for (byte b : bytes) {
					bytesArr.add(new JsonPrimitive(b));
				}
				item.add("item_contents", bytesArr);
			}
			if (ea.contains("dungeon_item_level")) {
				item.addProperty("dungeon_item_level", ea.getInt("dungeon_item_level").orElse(0));
			}
		}

		if (lore.length > 0) {
			JsonArray jsonLore = new JsonArray();
			for (String line : lore) {
				jsonLore.add(new JsonPrimitive(line));
			}
			item.add("lore", jsonLore);
		}

		item.addProperty("damage", damage);
		if (count > 1) item.addProperty("count", count);
		if (!itemModel.isEmpty()) item.addProperty("item_model", itemModel);
		item.addProperty("nbttag", tag.toString());

		return item;
	}

	/**
	 * Repo item list (internal name -&gt; item JSON), backed by {@code <repoLocation>/items/<internalname>.json}.
	 * TODO(fabric-port): see class javadoc - lookups will come back empty unless the repo has been manually
	 * synced, since the network sync pipeline wasn't ported in this pass.
	 *
	 * <p>Every call site only ever does a point lookup ({@code .get(name)}/{@code .containsKey(name)}), so this
	 * resolves and caches items one at a time as they're requested instead of eagerly parsing the entire repo
	 * (~8800 files) up front - the repo item filenames are exactly their internalname, e.g. {@code
	 * items/JUMBO_BACKPACK.json}. The eager version used to run synchronously on the render thread the first
	 * time a profile viewer page's static initializer looked up a single item, freezing (and sometimes crashing)
	 * the game for the time it took to read and parse every item in the repo. Iterating the returned map is not
	 * supported (see {@link #itemInformationView}).
	 */
	public Map<String, JsonObject> getItemInformation() {
		return itemInformationView;
	}

	private JsonObject lookupItemInformation(String internalName) {
		JsonObject cached = itemInformationCache.get(internalName);
		if (cached != null) return cached == ITEM_INFORMATION_MISSING ? null : cached;

		JsonObject result = null;
		File file = new File(repoLocation, "items" + File.separator + internalName + ".json");
		if (file.isFile()) {
			try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
				result = gson.fromJson(reader, JsonObject.class);
			} catch (IOException | com.google.gson.JsonParseException ignored) {
			}
		}
		itemInformationCache.put(internalName, result == null ? ITEM_INFORMATION_MISSING : result);
		return result;
	}

	/**
	 * Port of the Forge 1.8.9 {@code NEUManager#jsonToStack(JsonObject, boolean, boolean, boolean)}, trimmed to
	 * the two params that were actually driven by call sites in this codebase (the {@code useReplacements}/
	 * {@code copyStack} flags always ended up {@code true} at every ported call site, so they were dropped).
	 *
	 * <p>Builds a real {@link ItemStack} from a repo item-JSON definition (as returned by
	 * {@link #getItemInformation()} or {@link #getJsonFromNBTEntry}): resolves {@code itemid} to a vanilla
	 * {@link Item}, applies {@code nbttag} (parsed via {@link TagParser}, same as {@link
	 * io.github.moulberry.notenoughupdates.profileviewer.PlayerStats}), and overlays the repo's own
	 * {@code displayname}/{@code lore} as data components.
	 *
	 * <p>TODO(fabric-port) — simplifications vs. the original:
	 * <ul>
	 *   <li>Legacy numeric "damage" subtype remapping (e.g. {@code minecraft:stained_glass} damage 5 -&gt;
	 *   pink stained glass) was NOT ported as a general table - modern Minecraft has no data-driven way to go
	 *   from a pre-flattening (id, damage) pair to today's per-variant Item short of a large hardcoded map. The
	 *   one case that matters for the vast majority of repo icons - {@code minecraft:skull} damage 3 (a player
	 *   head with a custom {@code SkullOwner} texture) - IS special-cased below, since that's how essentially
	 *   every non-vanilla SkyBlock item icon in the repo is represented. Any other legacy id/damage pair that
	 *   doesn't resolve 1:1 by registry name falls back to {@link Items#BARRIER} (the "broken texture" stand-in,
	 *   replacing the original's purple-and-black quads).</li>
	 *   <li>{@code getPetLoreReplacements} (substituting {@code {LVL}}/{@code {NAME}} etc. placeholders in pet
	 *   display names) wasn't ported in the data-layer pass, so {@code {placeholder}} tokens in
	 *   {@code displayname} are left as-is if present (repo pet items are the main user of this and are rare
	 *   among the pages that call this method).</li>
	 * </ul>
	 */
	public ItemStack jsonToStack(JsonObject json) {
		return jsonToStack(json, true);
	}

	public ItemStack jsonToStack(JsonObject json, boolean useCache) {
		if (json == null || !json.has("internalname")) {
			return new ItemStack(Items.BARRIER);
		}
		String internalname = json.get("internalname").getAsString();

		if (useCache) {
			ItemStack cached = itemStackCache.get(internalname);
			if (cached != null) return cached.copy();
		}

		ItemStack stack = resolveBaseItemStack(json);

		if (json.has("count")) {
			stack.setCount(json.get("count").getAsInt());
		}

		String itemModel = json.has("item_model") ? json.get("item_model").getAsString() : null;
		if (json.has("nbttag")) {
			try {
				CompoundTag tag = io.github.moulberry.notenoughupdates.util.Utils.parseLegacyNbt(json.get("nbttag").getAsString());
				applySkullTexture(stack, tag);
				// Repo items carry the same model reference as an "ItemModel" tag.
				if (itemModel == null) itemModel = tag.getStringOr("ItemModel", null);
				// 1.8 showed the enchantment glint whenever an "ench" list was present (Hypixel uses an empty one
				// for glint-only items).
				if (tag.contains("ench")) {
					stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
				}
				applyPotionEffects(stack, tag);
				// Dyed leather armor keeps its colour in 1.8's display.color.
				int dyeColour = tag.getCompoundOrEmpty("display").getIntOr("color", -1);
				if (dyeColour >= 0) {
					stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColour));
				}
			} catch (CommandSyntaxException ignored) {
			}
		}
		applyItemModel(stack, itemModel);

		if (json.has("displayname")) {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(json.get("displayname").getAsString()));
		}

		if (json.has("lore")) {
			List<Component> loreLines = new ArrayList<>();
			for (JsonElement line : json.get("lore").getAsJsonArray()) {
				loreLines.add(Component.literal(line.getAsString()));
			}
			stack.set(DataComponents.LORE, new ItemLore(loreLines));
		}

		if (useCache) itemStackCache.put(internalname, stack.copy());
		return stack;
	}

	private static final String[] LEGACY_EFFECT_NAMES = {
		null, "speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
		"jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility",
		"blindness", "night_vision", "hunger", "weakness", "poison", "wither", "health_boost", "absorption",
		"saturation", "glowing", "levitation", "luck", "unluck"
	};

	/**
	 * 1.8 potions took their colour from {@code CustomPotionEffects} (numeric effect ids); modern potions colour from
	 * the {@code potion_contents} component, so rebuild that from the legacy list.
	 */
	private static void applyPotionEffects(ItemStack stack, CompoundTag tag) {
		var legacy = tag.getListOrEmpty("CustomPotionEffects");
		if (legacy.isEmpty()) return;
		List<MobEffectInstance> effects = new ArrayList<>();
		for (int i = 0; i < legacy.size(); i++) {
			CompoundTag entry = legacy.getCompoundOrEmpty(i);
			int id = entry.getIntOr("Id", 0);
			if (id <= 0 || id >= LEGACY_EFFECT_NAMES.length) continue;
			var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(LEGACY_EFFECT_NAMES[id]));
			if (effect.isEmpty()) continue;
			effects.add(new MobEffectInstance(effect.get(), Math.max(1, entry.getIntOr("Duration", 1)), entry.getIntOr("Amplifier", 0)));
		}
		if (!effects.isEmpty()) {
			stack.set(DataComponents.POTION_CONTENTS, new PotionContents(java.util.Optional.empty(), java.util.Optional.empty(), effects, java.util.Optional.empty()));
		}
	}

	private ItemStack resolveBaseItemStack(JsonObject json) {
		String itemid = json.has("itemid") ? json.get("itemid").getAsString() : "";
		int damage = json.has("damage") ? json.get("damage").getAsInt() : 0;

		// See class javadoc on jsonToStack: skulls (damage 3) cover the overwhelming majority of non-vanilla
		// repo item icons, since they carry a custom texture via the SkullOwner NBT tag applied afterwards.
		if (itemid.endsWith(":skull") || itemid.endsWith(":mob_head") || itemid.endsWith(":player_head")) {
			return new ItemStack(damage == 3 || itemid.endsWith(":player_head") ? Items.PLAYER_HEAD : Items.BARRIER);
		}

		// Repo and Hypixel item ids are 1.8 names with a damage subtype (e.g. minecraft:stained_glass + 5). Vanilla's
		// 1.13 "flattening" data fixer maps those to today's items; it returns null for names that didn't change.
		String flattened = ItemStackTheFlatteningFix.updateItem(itemid, damage);
		if (flattened != null) itemid = flattened;
		itemid = POST_FLATTENING_RENAMES.getOrDefault(itemid, itemid);

		Item item = itemById(itemid);
		if (item == null) {
			// 1.8 block items renamed in 1.13 without a damage variant (e.g. minecraft:slime -> slime_block) aren't
			// in the item flattening table; vanilla's block-state upgrade table covers them by numeric block id.
			Integer legacyId = legacyNumericIds().get(itemid);
			if (legacyId != null && legacyId < 256) {
				item = itemById(BlockStateData.upgradeBlock((legacyId << 4) | (damage & 15)));
			}
		}
		if (item == null) item = itemFromVanillaModel(json);
		return new ItemStack(item != null ? item : Items.BARRIER);
	}

	/** Last resort for legacy ids that don't resolve: a vanilla {@code minecraft:<item>} model names the item. */
	private static Item itemFromVanillaModel(JsonObject json) {
		String model = json.has("item_model") ? json.get("item_model").getAsString() : null;
		if (model == null && json.has("nbttag")) {
			try {
				model = io.github.moulberry.notenoughupdates.util.Utils.parseLegacyNbt(json.get("nbttag").getAsString())
					.getStringOr("ItemModel", null);
			} catch (CommandSyntaxException ignored) {
			}
		}
		if (model != null && model.startsWith("minecraft:")) {
			Item fromModel = itemById(model);
			if (fromModel != null) return fromModel;
		}
		// Plain vanilla items are named after their registry id (SLIME_BLOCK -> minecraft:slime_block).
		String internalname = json.has("internalname") ? json.get("internalname").getAsString() : "";
		return internalname.matches("[A-Z0-9_]+") ? itemById("minecraft:" + internalname.toLowerCase(Locale.ROOT)) : null;
	}

	private static Item itemById(String itemid) {
		if (itemid == null) return null;
		int state = itemid.indexOf('[');
		if (state >= 0) itemid = itemid.substring(0, state);
		Identifier id = Identifier.tryParse(itemid);
		if (id == null) return null;
		Item item = BuiltInRegistries.ITEM.getValue(id);
		return item == Items.AIR ? null : item;
	}

	private static Map<String, Integer> legacyNumericIdsByName;

	/** 1.8 item name -> numeric id, the inverse of vanilla's ItemIdFix table. */
	private static synchronized Map<String, Integer> legacyNumericIds() {
		if (legacyNumericIdsByName == null) {
			Map<String, Integer> byName = new HashMap<>();
			for (int i = 1; i < 2300; i++) {
				String name = ItemIdFix.getItem(i);
				if (name != null && !name.equals("minecraft:air")) byName.putIfAbsent(name, i);
			}
			legacyNumericIdsByName = byName;
		}
		return legacyNumericIdsByName;
	}

	/**
	 * Returns whether an item model definition is currently loaded. Installed by the client entrypoint (resource
	 * lookups are client-side); until then no custom models are applied.
	 */
	public static volatile java.util.function.Predicate<Identifier> itemModelExists = id -> false;

	/**
	 * Applies an item-model reference (Hypixel's {@code hypixel_skyblock:...} models, or vanilla ones such as
	 * {@code minecraft:slime_block}) when that model is loaded - Hypixel's are only there while its SkyBlock
	 * resource pack is active. Otherwise the stack keeps its base item's look rather than a missing-model cube.
	 */
	private static void applyItemModel(ItemStack stack, String model) {
		if (model == null || model.isEmpty()) return;
		Identifier id = Identifier.tryParse(model);
		if (id != null && itemModelExists.test(id)) {
			stack.set(DataComponents.ITEM_MODEL, id);
		}
	}

	/**
	 * Applies a legacy 1.8.9-format {@code SkullOwner} compound (as embedded in repo item {@code nbttag}s) to a
	 * player-head {@link ItemStack} as a resolved {@link GameProfile} texture, the same way {@link
	 * io.github.moulberry.notenoughupdates.util.Utils#createSkull} does for hardcoded skulls.
	 */
	private void applySkullTexture(ItemStack stack, CompoundTag tag) {
		if (!tag.contains("SkullOwner")) return;
		CompoundTag skullOwner = tag.getCompoundOrEmpty("SkullOwner");

		String textureValue = null;
		String textureSignature = null;
		var properties = skullOwner.getCompoundOrEmpty("Properties");
		var textures = properties.getListOrEmpty("textures");
		if (!textures.isEmpty()) {
			textureValue = textures.getCompoundOrEmpty(0).getStringOr("Value", null);
			textureSignature = textures.getCompoundOrEmpty(0).getStringOr("Signature", null);
		}
		if (textureValue == null || textureValue.isEmpty()) return;
		if (textureSignature != null && textureSignature.isEmpty()) textureSignature = null;

		String idString = skullOwner.getStringOr("Id", "");
		UUID uuid;
		try {
			uuid = idString.isEmpty() ? UUID.randomUUID() : UUID.fromString(idString);
		} catch (IllegalArgumentException ex) {
			uuid = UUID.randomUUID();
		}

		HashMultimap<String, Property> propertiesBacking = HashMultimap.create();
		propertiesBacking.put("textures", new Property("textures", textureValue, textureSignature));
		GameProfile profile = new GameProfile(uuid, "", new PropertyMap(propertiesBacking));
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
	}
}
