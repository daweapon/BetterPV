/*
 * Copyright (C) 2023 NotEnoughUpdates contributors
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
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.bestiary.BestiaryData;
import io.github.moulberry.notenoughupdates.profileviewer.farming.Garden;
import io.github.moulberry.notenoughupdates.profileviewer.foraging.AttributesPage;
import io.github.moulberry.notenoughupdates.profileviewer.foraging.HotfPage;
import io.github.moulberry.notenoughupdates.profileviewer.mining.GlacitePage;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Port of NEU's level-breakdown page ({@code profileviewer.level.LevelPage} and its eight {@code *TaskLevel}
 * classes): the SkyBlock level bar plus how much of each category's SkyBlock XP the player has earned. The eight task
 * classes are folded into one class that reads the profile JSON directly (there is no {@code APIDataJson} in this
 * port).
 *
 * <p>Where NEU worked each source out from profile fields (and where its numbers have gone stale), most sources here
 * are read from the profile's {@code leveling.completed_tasks} list against {@code profile_viewer/sblevel_tasks.json}:
 * task id to SkyBlock XP, compiled from the Hypixel wiki's SkyBlock Levels tables via the community dataset
 * github.com/8Doc/SkyblockXP-BAZALRIGHT- (task ids checked against live profiles). Skills, collections, minions, the
 * Museum, attributes, HOTM/HOTF and the other formula-based sources are still worked out from profile data. The main
 * bar's tooltip says how much of the profile's real SkyBlock XP the categories account for, so a source that isn't
 * tracked yet shows up as a gap rather than a wrong number.
 */
public class LevelPage implements GuiProfileViewerPage {

	/** SkyBlock XP of a fully maxed profile (level 620.16): the level bar turns rainbow from here. */
	public static final int MAX_EXPERIENCE = 62_016;

	private static final Identifier pv_levels = Identifier.parse("notenoughupdates:pv_levels.png");
	private static final Identifier pv_elements = Identifier.parse("notenoughupdates:pv_elements.png");

	/** Skill -> level cap; each skill level is worth 5 XP to level 10, 10 to 25, 20 to 50 and 30 to 60. */
	private static final Map<String, Integer> SKILL_CAPS = new LinkedHashMap<>();

	static {
		SKILL_CAPS.put("taming", 60);
		SKILL_CAPS.put("mining", 60);
		SKILL_CAPS.put("foraging", 57);
		SKILL_CAPS.put("enchanting", 60);
		SKILL_CAPS.put("carpentry", 50);
		SKILL_CAPS.put("farming", 60);
		SKILL_CAPS.put("combat", 60);
		SKILL_CAPS.put("fishing", 50);
		SKILL_CAPS.put("alchemy", 50);
		SKILL_CAPS.put("hunting", 50);
	}

	private static final List<String> DUNGEON_CLASSES = List.of("healer", "tank", "mage", "archer", "berserk");
	private static final List<String> SLAYERS = List.of("zombie", "spider", "wolf", "enderman", "blaze", "vampire");
	private static final int[] BOSS_LOW = {25, 50, 100, 150, 250, 1000};
	private static final int[] BOSS_THORN = {25, 50, 150, 250, 400, 1000};
	private static final int[] BOSS_HIGH = {50, 100, 150, 250, 500, 750, 1000};
	private static final int[] HOTF_TIER_XP = {35, 45, 60, 75, 90, 110, 130, 180};
	private static final int[] CENTER_OF_THE_FOREST_XP = {25, 35, 50, 65, 75};

	/** Event perk shop (task id prefix -> name). */
	private static final Map<String, String> EVENT_SHOPS = new LinkedHashMap<>();

	static {
		EVENT_SHOPS.put("SPOOKY_FESTIVAL_", "Spooky Festival");
		EVENT_SHOPS.put("WINTER_", "Season of Jerry");
		EVENT_SHOPS.put("FISHING_FESTIVAL_", "Fishing Festival");
		EVENT_SHOPS.put("NATIONAL_MINING_MONTH_", "Mining Fiesta");
		EVENT_SHOPS.put("MYTHOLOGICAL_RITUAL_", "Mythological Ritual");
		EVENT_SHOPS.put("HARVEST_FEAST_", "Harvest Feast");
	}

	private final GuiProfileViewer instance;

	/** One category bar: its own name/icon/position, XP earned out of the most it can give, and the tooltip lines. */
	private record Task(String name, Supplier<ItemStack> icon, int x, int y, double xp, double max, List<String> lore) {}

	/** Collects a category's tooltip lines and totals. */
	private static final class Lines {
		final List<String> lore = new ArrayList<>();
		double gained;
		double max;

		/** A source worth up to {@code max} XP of which the player has {@code earned}. */
		void add(String name, double earned, double max) {
			lore.add(buildLore(name, earned, max));
			gained += Math.min(earned, max);
			this.max += max;
		}

		/** A source that can't be worked out right now. */
		void unavailable(String name, String why, double max) {
			lore.add("§6" + name + ": " + why);
			this.max += max;
		}
	}

	private String cachedFor;
	private JsonObject cachedMuseum;
	private JsonObject cachedGarden;
	private List<Task> cachedTasks;
	private double cachedAccounted;

	private String loggedFor;
	private String memoFor;
	private int memoBestiary;
	private int[] memoAttributes;

	public LevelPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		cachedFor = null;
		cachedTasks = null;
		memoFor = null;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		BasicPage.drawSideButtons(graphics, instance, mouseX, mouseY);

		JsonObject constant = Constants.SBLEVELS;
		if (constant == null) {
			RenderUtils.drawStringCentered(
				graphics, "§cRepo constant sblevels.json is missing, update the item repo.", instance.getFont(),
				guiLeft + instance.sizeX / 2f, guiTop + 101, true, 0
			);
			return;
		}

		RenderUtils.drawTexturedRect(graphics, pv_levels, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject member = profile == null ? null : profile.getProfileInformation(profileId);
		if (member == null) return;

		JsonObject museum = profile.getMuseumInfo(profileId);
		JsonObject garden = profile.getGardenInfo(profileId);
		String key = profile.getUuid() + "/" + profileId;
		if (cachedTasks == null || !key.equals(cachedFor) || museum != cachedMuseum || garden != cachedGarden) {
			if (!key.equals(memoFor)) {
				memoFor = key;
				memoBestiary = -1;
				memoAttributes = null;
			}
			List<Task> tasks = computeTasks(profile, profileId, member, museum, garden);
			cachedFor = key;
			cachedMuseum = museum;
			cachedGarden = garden;
			cachedTasks = tasks;
			cachedAccounted = 0;
			for (Task task : tasks) cachedAccounted += Math.min(task.xp(), task.max());
		}

		double experience = Utils.getElementAsInt(Utils.getElement(member, "leveling.experience"), 0);
		List<String> mainTooltip = new ArrayList<>();
		mainTooltip.add("§7Total XP: §e" + GuiProfileViewer.numberFormat.format((long) experience));
		mainTooltip.add("§7Shown in the categories: §e" + GuiProfileViewer.numberFormat.format((long) cachedAccounted));
		double gap = experience - cachedAccounted;
		if (Math.abs(gap) >= 1) {
			mainTooltip.add(
				gap > 0
					? "§7Other sources (not in Hypixel's API): §e" + GuiProfileViewer.numberFormat.format((long) gap)
					: "§7Counted above the profile's XP: §e" + GuiProfileViewer.numberFormat.format((long) -gap)
			);
		}
		renderLevelBar(
			graphics, "Level", BasicPage.SKYBLOCK_LEVEL_SKULL, guiLeft + 163, guiTop + 30, 110, (int) (experience / 100),
			experience % 100, 100, mouseX, mouseY, false, mainTooltip
		);

		for (Task task : cachedTasks) {
			renderLevelBar(
				graphics, task.name(), task.icon().get(), guiLeft + task.x(), guiTop + task.y(), 110, 0,
				task.xp(), task.max(), mouseX, mouseY, true, task.lore()
			);
		}
	}

	private void renderLevelBar(
		GuiGraphicsExtractor graphics, String name, ItemStack stack, int x, int y, int xSize, int level, double xp,
		double max, int mouseX, int mouseY, boolean percentage, List<String> tooltip
	) {
		if (xp < 0) xp = 0;
		double fraction = max <= 0 ? 0 : xp / max;

		String second = "§f" + (percentage ? (int) (Math.min(fraction, 1) * 100) + "%" : String.valueOf(level));
		RenderUtils.renderAlignedString(graphics, "§c" + name, second, x + 14, y - 4, xSize - 20);

		// The level bar turns rainbow at the level cap, like a maxed category.
		if (xp >= max || (!percentage && xp + level * 100 >= MAX_EXPERIENCE)) {
			instance.renderGoldBar(graphics, x, y + 6, xSize);
		} else {
			instance.renderBar(graphics, x, y + 6, xSize, (float) fraction);
		}

		if (Utils.isWithinRect(mouseX, mouseY, x, y - 4, 120, 17)) {
			List<String> lines = new ArrayList<>(tooltip);
			if (!lines.isEmpty()) lines.add("");
			lines.add(
				"§7Progress: §5" + (int) (Math.min(fraction, 1) * 100) + "% §8(" + GuiProfileViewer.numberFormat.format((long) xp) + "/" +
					GuiProfileViewer.numberFormat.format((long) max) + " XP)"
			);
			instance.tooltipToDisplay = lines;
		}

		RenderUtils.drawSkillIcon(graphics, stack, x, y - 6);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		return BasicPage.clickedSideButtons(mouseX, mouseY, mouseButton);
	}

	/** A pv_elements side button; {@code yIndex} is its slot down the left edge of the panel. */
	public static void drawSideButton(GuiGraphicsExtractor graphics, int yIndex, ItemStack itemStack, boolean pressed) {
		int x = GuiProfileViewer.getGuiLeft() - 28;
		int y = GuiProfileViewer.getGuiTop() + yIndex * 28;

		float uMin = 193 / 256f;
		float uMax = 223 / 256f;
		float vMin = 200 / 256f;
		float vMax = 228 / 256f;
		if (pressed) {
			uMin = 224 / 256f;
			uMax = 1f;
			if (yIndex != 0) {
				vMin = 228 / 256f;
				vMax = 1f;
			}
			graphics.fill(x + 2, y + 2, x + 32 - 2, y + 30 - 4, 0x80000000);
		} else {
			graphics.fill(x + 2, y + 2, x + 28 - 2, y + 30 - 4, 0x80000000);
		}

		RenderUtils.drawTexturedRect(graphics, pv_elements, x, y, pressed ? 32 : 28, 28, uMin, uMax, vMin, vMax);
		RenderUtils.drawItemStack(graphics, itemStack, x + 8, y + 7);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Category maths
	// ---------------------------------------------------------------------------------------------------------

	private List<Task> computeTasks(
		ProfileViewer.Profile profile, String profileId, JsonObject member, JsonObject museum, JsonObject garden
	) {
		JsonObject sb = Constants.SBLEVELS;
		Map<String, ProfileViewer.Level> levelling = profile.getSkyblockInfo(profileId);
		Set<String> completed = completedTasks(member);

		logUntrackedTasks(profileId, completed);

		List<Task> tasks = new ArrayList<>();
		tasks.add(guarded("Core Task", () -> core(profile, profileId, member, sb, levelling, completed, museum)));
		tasks.add(guarded("Dungeon Task", () -> dungeon(member, sb, levelling, completed)));
		tasks.add(guarded("Slaying Task", () -> slaying(member, sb, levelling, completed)));
		tasks.add(guarded("Skill Related Task", () -> skillRelated(member, sb, levelling, garden)));
		tasks.add(guarded("Essence", () -> essence(completed)));
		tasks.add(guarded("Misc. Task", () -> misc(member, sb, completed)));
		tasks.add(guarded("Story Task", () -> story(member, sb, completed)));
		tasks.add(guarded("Event Task", () -> event(member, sb, completed)));
		tasks.removeIf(java.util.Objects::isNull);
		return tasks;
	}

	/** Logs (once per profile) completed task ids that no XP table knows, so missing sources can be found from the log. */
	private void logUntrackedTasks(String profileId, Set<String> completed) {
		if (profileId == null || profileId.equals(loggedFor)) return;
		loggedFor = profileId;
		JsonObject groups = PvData.bundled("sblevel_tasks");
		List<String> unknown = new ArrayList<>();
		for (String task : completed) {
			boolean known = task.startsWith("YOTW_STEW_") || task.startsWith("SAFARI_MILESTONE_") || task.startsWith("ABIPHONE_");
			for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
				if (group.getValue().getAsJsonObject().has(task)) known = true;
			}
			if (!known) unknown.add(task);
		}
		java.util.Collections.sort(unknown);
		NotEnoughUpdates.LOGGER.info("Level page: {} completed tasks not in the XP table: {}", unknown.size(), unknown);
	}

	/** A category that throws (missing repo field, unexpected profile shape) just doesn't draw. */
	private static Task guarded(String name, Supplier<Task> compute) {
		try {
			return compute.get();
		} catch (RuntimeException e) {
			NotEnoughUpdates.LOGGER.warn("Level page: {} failed", name, e);
			return null;
		}
	}

	private Task core(
		ProfileViewer.Profile profile, String profileId, JsonObject member, JsonObject sb,
		Map<String, ProfileViewer.Level> levelling, Set<String> completed, JsonObject museum
	) {
		JsonObject core = sb.getAsJsonObject("core_task");
		Lines lines = new Lines();

		if (levelling == null) {
			lines.unavailable("Skill Level Up", "§cSkills API is disabled!", 8710);
		} else {
			int skillXp = 0;
			for (Map.Entry<String, Integer> skill : SKILL_CAPS.entrySet()) {
				// From the raw XP against the current caps: the profile's own cap fields (taming, foraging) lag behind.
				int reached = skillLevel(Utils.getElementAsFloat(member.get("experience_skill_" + skill.getKey()), 0), skill.getValue());
				for (int i = 1; i <= reached; i++) skillXp += i <= 10 ? 5 : i <= 25 ? 10 : i <= 50 ? 20 : 30;
			}
			lines.add("Skill Level Up", skillXp, 8710);
		}

		double museumMax = 3646;
		if (museum == null) {
			lines.unavailable("Museum Progression", "§7Loading...", museumMax);
		} else if (museum.has("__error")) {
			lines.unavailable("Museum Progression", "§cCouldn't load the museum!", museumMax);
		} else if (museum.isEmpty()) {
			lines.unavailable("Museum Progression", "§cMuseum API is disabled!", museumMax);
		} else {
			lines.add("Museum Progression", Math.min(MuseumPage.experience(museum), museumMax), museumMax);
		}

		int souls = Utils.getElementAsInt(Utils.getElement(member, "fairy_soul.total_collected"), 0) -
			Utils.getElementAsInt(Utils.getElement(member, "fairy_soul.unspent_souls"), 0);
		lines.add("Fairy Soul", souls / 5 * core.get("fairy_souls_xp").getAsInt(), 570);
		lines.add(
			"Accessory Bag", Utils.getElementAsInt(Utils.getElement(member, "accessory_bag_storage.highest_magical_power"), 0), 2122
		);
		lines.add(
			"Pet Score",
			Utils.getElementAsInt(Utils.getElement(member, "leveling.highest_pet_score"), 0) * core.get("pet_score_xp").getAsInt(),
			1563
		);

		// Collection tiers and crafted minions, from the collections summary or, when the player's collection API is
		// off, straight from the profile (which still lists unlocked tiers and crafted minions).
		JsonObject collectionInfo = profile.getCollectionInfo(profileId);
		Map<String, Integer> collectionTiers = collectionInfo != null
			? tiersOf(collectionInfo.getAsJsonObject("collection_tiers")) : tiersOf(member.get("unlocked_coll_tiers"));
		Map<String, Integer> minionTiers = collectionInfo != null
			? tiersOf(collectionInfo.getAsJsonObject("minion_tiers")) : tiersOf(member.get("crafted_generators"));
		if (collectionTiers.isEmpty() && minionTiers.isEmpty()) {
			lines.unavailable("Collections", "§cCollections API is disabled!", 3160);
			lines.unavailable("Craft Minions", "§cCollections API is disabled!", 3164);
		} else {
			int perTier = core.get("collections_xp").getAsInt();
			int collectionXp = 0;
			for (int tier : collectionTiers.values()) collectionXp += Math.max(0, tier) * perTier;
			lines.add("Collections", collectionXp, 3160);

			int minionXp = 0;
			JsonObject minionTable = Constants.MISC.getAsJsonObject("minionXp");
			for (int tier : minionTiers.values()) {
				for (int i = 1; i <= tier; i++) minionXp += Utils.getElementAsInt(minionTable.get(String.valueOf(i)), 0);
			}
			// The first Cobblestone minion tier isn't worth XP, which is why the total is 3,164 and not 3,165.
			lines.add("Craft Minions", Math.max(0, minionXp - 1), 3164);
		}

		addGroup(lines, "Bank Upgrade", "bank_upgrades", completed, id -> true);
		addGroup(lines, "Fast Travel Scroll", "fast_travel", completed, id -> true);

		return new Task("Core Task", () -> new ItemStack(Items.NETHER_STAR), 23, 25, lines.gained, lines.max, lines.lore);
	}

	/** The level {@code xp} reaches on the standard skill table, up to {@code cap}. */
	private static int skillLevel(float xp, int cap) {
		JsonElement table = Constants.LEVELING == null ? null : Constants.LEVELING.get("leveling_xp");
		if (table == null || !table.isJsonArray()) return 0;
		int level = 0;
		float needed = 0;
		for (JsonElement step : table.getAsJsonArray()) {
			needed += step.getAsFloat();
			if (level >= cap || xp < needed) break;
			level++;
		}
		return level;
	}

	/**
	 * Highest tier per name from either a {name: tier} object or a list of "NAME_TIER" strings (as
	 * {@code unlocked_coll_tiers} and {@code crafted_generators} are stored).
	 */
	private static Map<String, Integer> tiersOf(JsonElement source) {
		Map<String, Integer> tiers = new LinkedHashMap<>();
		if (source == null) return tiers;
		if (source.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject().entrySet()) {
				tiers.put(entry.getKey(), Utils.getElementAsInt(entry.getValue(), 0));
			}
		} else if (source.isJsonArray()) {
			for (JsonElement entry : source.getAsJsonArray()) {
				if (!entry.isJsonPrimitive()) continue;
				String id = entry.getAsString();
				int split = id.lastIndexOf('_');
				if (split <= 0) continue;
				try {
					tiers.merge(id.substring(0, split), Integer.parseInt(id.substring(split + 1)), Math::max);
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return tiers;
	}

	private Task dungeon(JsonObject member, JsonObject sb, Map<String, ProfileViewer.Level> levelling, Set<String> completed) {
		JsonObject dungeon = sb.getAsJsonObject("dungeon_task");
		Lines lines = new Lines();

		if (levelling == null || !levelling.containsKey("catacombs")) {
			lines.unavailable("Catacombs Level Up", "§cSkills API is disabled!", dungeon.get("catacombs_level_up").getAsInt());
			lines.unavailable("Class Level Up", "§cSkills API is disabled!", dungeon.get("class_level_up").getAsInt());
		} else {
			int levelXp = 0;
			for (int i = 1; i <= (int) levelling.get("catacombs").level; i++) levelXp += i < 40 ? 20 : 40;
			lines.add("Catacombs Level Up", levelXp, dungeon.get("catacombs_level_up").getAsInt());

			int classXp = 0;
			for (String className : DUNGEON_CLASSES) {
				ProfileViewer.Level level = levelling.get(className);
				if (level != null) classXp += Math.min((int) level.level, 50) * dungeon.get("class_xp").getAsInt();
			}
			lines.add("Class Level Up", classXp, dungeon.get("class_level_up").getAsInt());
		}
		addGroup(lines, "Complete Dungeons", "floors", completed, id -> true);

		return new Task("Dungeon Task", () -> PvData.item("WITHER_RELIC"), 23, 55, lines.gained, lines.max, lines.lore);
	}

	private Task slaying(JsonObject member, JsonObject sb, Map<String, ProfileViewer.Level> levelling, Set<String> completed) {
		JsonObject slaying = sb.getAsJsonObject("slaying_task");
		Lines lines = new Lines();

		JsonArray slayerTable = slaying.getAsJsonArray("slayer_level_up_xp");
		if (levelling == null) {
			lines.unavailable("Slayer Level Up", "§cSkills API is disabled!", slaying.get("slayer_level_up").getAsInt());
		} else {
			int slayerLevelXp = 0;
			for (String slayer : SLAYERS) {
				ProfileViewer.Level level = levelling.get(slayer);
				if (level == null) continue;
				for (int i = 0; i < (int) level.level && i < slayerTable.size(); i++) slayerLevelXp += slayerTable.get(i).getAsInt();
			}
			lines.add("Slayer Level Up", slayerLevelXp, slaying.get("slayer_level_up").getAsInt());
		}

		// Dungeon boss collections: master floors count double towards the floor's total.
		Map<String, Double> completions = new LinkedHashMap<>();
		addCompletions(completions, objectAt(member, "dungeons.dungeon_types.catacombs.tier_completions"), 1);
		addCompletions(completions, objectAt(member, "dungeons.dungeon_types.master_catacombs.tier_completions"), 2);
		JsonArray dungeonCollectionXp = slaying.getAsJsonObject("boss_collections_xp").getAsJsonArray("dungeon_collection_xp");
		int bossCollectionXp = 0;
		for (int floor = 1; floor <= 7; floor++) {
			Double kills = completions.get(String.valueOf(floor));
			if (kills == null) continue;
			int[] thresholds = floor <= 3 ? BOSS_LOW : floor == 4 ? BOSS_THORN : BOSS_HIGH;
			for (int i = 0; i < thresholds.length && i < dungeonCollectionXp.size(); i++) {
				if (kills >= thresholds[i]) bossCollectionXp += dungeonCollectionXp.get(i).getAsInt();
			}
		}
		// Kuudra's boss collection counts each tier's completions weighted by the tier (Basic 1 ... Infernal 5).
		int kuudraCollection = 0;
		JsonObject kuudraTiers = objectAt(member, "nether_island_player_data.kuudra_completed_tiers");
		if (kuudraTiers != null) {
			String[] names = {"none", "hot", "burning", "fiery", "infernal"};
			for (int i = 0; i < names.length; i++) {
				kuudraCollection += (i + 1) * Utils.getElementAsInt(kuudraTiers.get(names[i]), 0);
			}
		}
		int[] kuudraThresholds = {10, 100, 500, 2000, 5000};
		int[] kuudraRewards = {10, 15, 20, 25, 30};
		for (int i = 0; i < kuudraThresholds.length; i++) {
			if (kuudraCollection >= kuudraThresholds[i]) bossCollectionXp += kuudraRewards[i];
		}
		lines.add("Boss Collections", bossCollectionXp, slaying.get("boss_collections").getAsInt());

		lines.add("Bestiary Progress", bestiaryXp(member, slaying), 5635);

		int mythMax = slaying.get("mythological_kills").getAsInt();
		lines.add(
			"Mythological Kills", Math.min(mythMax, Utils.getElementAsInt(Utils.getElement(member, "player_stats.mythos.kills"), 0) / 100),
			mythMax
		);

		addGroup(lines, "Slay Dragons", "dragons", completed, id -> true);
		addGroup(lines, "Defeat Slayers", "slayers", completed, id -> true);
		addGroup(lines, "Defeat Kuudra", "bosses", completed, id -> id.startsWith("KILL_KUUDRA_"));
		addGroup(lines, "Defeat Arachne", "bosses", completed, id -> id.startsWith("KILL_ARACHNE_"));

		return new Task("Slaying Task", () -> new ItemStack(Items.GOLDEN_SWORD), 23, 85, lines.gained, lines.max, lines.lore);
	}

	private static void addCompletions(Map<String, Double> into, JsonObject completions, int weight) {
		if (completions == null) return;
		for (Map.Entry<String, JsonElement> entry : completions.entrySet()) {
			into.merge(entry.getKey(), entry.getValue().getAsDouble() * weight, Double::sum);
		}
	}

	/** SkyBlock XP for bestiary progress: 1 per family tier plus a bonus per 100 tiers (worked out once per profile). */
	private int bestiaryXp(JsonObject member, JsonObject slaying) {
		if (!slaying.has("bestiary_family_xp") || !slaying.has("bestiary_milestone_xp")) return 0;
		if (memoBestiary < 0) {
			List<BestiaryData.Category> categories = BestiaryData.parseBestiaryData(member);
			int tiers = BestiaryData.calculateTotalBestiaryTiers(categories);
			memoBestiary = tiers * slaying.get("bestiary_family_xp").getAsInt() +
				tiers / 100 * slaying.get("bestiary_milestone_xp").getAsInt();
			// The repo's bestiary list is a few tiers short of the game's, so every mob in it maxed counts as everything.
			if (!categories.isEmpty() && allMaxed(categories)) memoBestiary = 5635;
		}
		return memoBestiary;
	}

	private static boolean allMaxed(List<BestiaryData.Category> categories) {
		for (BestiaryData.Category category : categories) {
			for (BestiaryData.Mob mob : category.mobs()) {
				if (!mob.levelData().maxLevel()) return false;
			}
			if (!allMaxed(category.subCategories())) return false;
		}
		return true;
	}

	private Task skillRelated(JsonObject member, JsonObject sb, Map<String, ProfileViewer.Level> levelling, JsonObject garden) {
		JsonObject skill = sb.getAsJsonObject("skill_related_task");
		JsonObject mining = skill.getAsJsonObject("mining");
		JsonObject farming = skill.getAsJsonObject("farming");
		JsonObject fishing = skill.getAsJsonObject("fishing");
		Set<String> completed = completedTasks(member);
		Lines lines = new Lines();

		// Heart of the Mountain: tier XP plus what the three powders are worth.
		JsonArray hotmTable = mining.getAsJsonArray("hotm_xp");
		double hotmMax = 0;
		for (JsonElement tier : hotmTable) hotmMax += tier.getAsInt();
		hotmMax += powderXp(12_500_000, 2400.0, 3.75, 12_500_000.0) + 2 * powderXp(20_000_000, 2500.0, 4.25, 20_000_000.0);
		if (levelling == null || !levelling.containsKey("hotm")) {
			lines.unavailable("Heart of the Mountain", "§cSkills API is disabled!", hotmMax);
		} else {
			double hotmXp = 0;
			int hotmLevel = (int) levelling.get("hotm").level;
			for (int i = 1; i <= hotmLevel && i <= hotmTable.size(); i++) hotmXp += hotmTable.get(i - 1).getAsInt();
			hotmXp += powderXp(powderTotal(member, "mithril"), 2400.0, 3.75, 12_500_000.0);
			hotmXp += powderXp(powderTotal(member, "gemstone"), 2500.0, 4.25, 20_000_000.0);
			hotmXp += powderXp(powderTotal(member, "glacite"), 2500.0, 4.25, 20_000_000.0);
			lines.add("Heart of the Mountain", hotmXp, hotmMax);
		}

		int commissionXp = 0;
		int commissionMax = 0;
		JsonArray commissionTable = mining.getAsJsonArray("commission_milestone_xp");
		for (JsonElement tier : commissionTable) commissionMax += tier.getAsInt();
		JsonElement tutorial = Utils.getElement(member, "objectives.tutorial");
		if (tutorial != null && tutorial.isJsonArray()) {
			for (JsonElement entry : tutorial.getAsJsonArray()) {
				if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) continue;
				for (int i = 1; i <= commissionTable.size(); i++) {
					if (entry.getAsString().equals("commission_milestone_reward_skyblock_xp_tier_" + i)) {
						commissionXp += commissionTable.get(i - 1).getAsInt();
					}
				}
			}
		}
		lines.add("Commission Milestones", commissionXp, commissionMax);

		int nucleusXp = Math.min(50, Utils.getElementAsInt(Utils.getElement(member, "leveling.completions.NUCLEUS_RUNS"), 0)) *
			mining.get("crystal_nucleus_xp").getAsInt();
		lines.add("Crystal Nucleus", nucleusXp, 200);

		int anitaXp = (Utils.getElementAsInt(Utils.getElement(member, "jacobs_contest.perks.double_drops"), 0) +
			Utils.getElementAsInt(Utils.getElement(member, "jacobs_contest.perks.farming_level_cap"), 0)) *
			farming.get("anita_shop_upgrades_xp").getAsInt();
		lines.add("Anita's Shop Upgrade", anitaXp, farming.get("anita_shop_upgrades").getAsInt());

		int potmXp = 0;
		int potmMax = 0;
		JsonArray potmTable = mining.getAsJsonArray("potm_xp");
		for (JsonElement tier : potmTable) potmMax += tier.getAsInt();
		// Peak of the Mountain was renamed Core of the Mountain; the old API called it special_0.
		int potm = Math.max(
			Utils.getElementAsInt(Utils.getElement(member, "mining_core.nodes.core_of_the_mountain"), 0),
			Utils.getElementAsInt(Utils.getElement(member, "mining_core.nodes.special_0"), 0)
		);
		for (int i = 1; i <= potm && i <= potmTable.size(); i++) potmXp += potmTable.get(i - 1).getAsInt();
		lines.add("Peak of the Mountain", potmXp, potmMax);

		JsonElement fossils = Utils.getElement(member, "glacite_player_data.fossils_donated");
		lines.add(
			"Fossil Research",
			(fossils != null && fossils.isJsonArray() ? fossils.getAsJsonArray().size() : 0) * mining.get("fossil_research_xp").getAsInt(),
			mining.get("fossil_research").getAsInt()
		);

		addGroup(lines, "Trophy Fish", "trophy_fish", completed, id -> true);
		addGroup(lines, "Rock Milestone", "milestones", completed, id -> id.startsWith("ROCK_"));
		addGroup(lines, "Dolphin Milestone", "milestones", completed, id -> id.startsWith("DOLPHIN_"));

		// Heart of the Forest: tiers plus whispers, which count like powder.
		int hotfTier = HotfPage.tier(member);
		int hotfXp = 0;
		int hotfMax = 0;
		for (int i = 0; i < HOTF_TIER_XP.length; i++) {
			hotfMax += HOTF_TIER_XP[i];
			if (i < hotfTier) hotfXp += HOTF_TIER_XP[i];
		}
		// Both kinds of whispers (forest and desert) count like powder.
		double whispersXp = 0;
		for (String kind : new String[]{"forest", "desert"}) {
			whispersXp += powderXp(
				Utils.getElementAsFloat(Utils.getElement(member, "foraging_core.whispers." + kind + ".total"), 0), 2400.0, 3.75, 12_500_000.0
			);
		}
		lines.add("Heart of the Forest", hotfXp + whispersXp, hotfMax + 2 * powderXp(12_500_000, 2400.0, 3.75, 12_500_000.0));
		int centerXp = 0;
		int centerMax = 0;
		int center = Utils.getElementAsInt(Utils.getElement(member, "skill_tree.nodes.foraging.center_of_the_forest"), 0);
		for (int i = 0; i < CENTER_OF_THE_FOREST_XP.length; i++) {
			centerMax += CENTER_OF_THE_FOREST_XP[i];
			if (i < center) centerXp += CENTER_OF_THE_FOREST_XP[i];
		}
		lines.add("Center of the Forest", centerXp, centerMax);

		String gardenStatus = garden == null ? "§7Loading..." : Garden.status(garden) == null ? null : "§cNo garden data!";
		if (gardenStatus != null) {
			lines.unavailable("Garden Level", gardenStatus, 140);
		} else {
			lines.add("Garden Level", 10 * Math.max(0, Garden.level(PvData.getLong(garden, "garden_experience")) - 1), 140);
		}

		if (gardenStatus == null) addGarden(lines, garden);

		// Tree gift milestones: 4 XP for each milestone tier claimed on any of the three trees.
		int giftTiers = 0;
		if (Utils.getElement(member, "foraging.tree_gifts.milestone_tier_claimed") instanceof JsonObject claimed) {
			for (JsonElement tier : claimed.asMap().values()) giftTiers += Utils.getElementAsInt(tier, 0);
		}
		lines.add("Tree Gift Milestones", Math.min(giftTiers, 21) * 4, 84);

		// Farming chips: 1 XP to unlock a chip, 15 to upgrade it to epic (level 11) and 25 to legendary (level 16).
		// Sowdust: 1 XP per million spent on chip levels (up to 250 million).
		int chipXp = 0;
		double sowdustSpent = 0;
		List<Long> chipCosts = PvData.cumulative(Garden.repo("chips"));
		if (Utils.getElement(member, "player_data.garden_chips") instanceof JsonObject chips) {
			for (JsonElement level : chips.asMap().values()) {
				int reached = Utils.getElementAsInt(level, 0);
				if (reached >= 1) chipXp += 1;
				if (reached >= 11) chipXp += 15;
				if (reached >= 16) chipXp += 25;
				if (reached >= 1 && !chipCosts.isEmpty()) sowdustSpent += chipCosts.get(Math.min(reached - 1, chipCosts.size() - 1));
			}
		}
		lines.add("Farming Chips", Math.min(chipXp, 410), 410);
		lines.add("Sowdust", Math.min(Math.floor(sowdustSpent / 1_000_000), 250), 250);

		// Corpse milestones: 5 XP for the first tier, 10 for the second and so on up to 35 for the seventh.
		int corpseTier = GlacitePage.corpseMilestone(member);
		lines.add("Corpse Milestones", 5 * corpseTier * (corpseTier + 1) / 2, 140);

		return new Task(
			"Skill Related Task", () -> new ItemStack(Items.DIAMOND_SWORD), 23, 115, lines.gained, lines.max, lines.lore
		);
	}

	/** How many of the repo's cumulative milestone thresholds the garden's counter has passed. */
	private static int milestonesReached(JsonObject garden, String counterPath, String repoPath) {
		long value = PvData.getLong(garden, counterPath);
		List<Long> thresholds = PvData.cumulative(Garden.repo(repoPath));
		int reached = 0;
		for (int i = 1; i < thresholds.size(); i++) {
			if (thresholds.get(i) <= value) reached++;
		}
		return reached;
	}

	/** Garden sources worth 1 XP per crop upgrade and milestone level, 5 per plot and Greenhouse upgrade, and so on. */
	private static void addGarden(Lines lines, JsonObject garden) {
		int cropUpgrades = 0;
		int cropMilestones = 0;
		for (Garden.Crop crop : Garden.CROPS) {
			cropUpgrades += (int) PvData.getLong(garden, "crop_upgrade_levels." + crop.key());
			long collected = PvData.getLong(garden, "resources_collected." + crop.key());
			List<Long> brackets = PvData.cumulative(Garden.repo("crop_milestones." + crop.key()));
			int milestone = 0;
			for (int i = 0; i < brackets.size(); i++) {
				if (brackets.get(i) <= collected) milestone = i;
			}
			cropMilestones += milestone;
		}
		lines.add("Crop Upgrades", Math.min(cropUpgrades, 117), 117);
		lines.add(
			"Unique Visitors",
			Math.min(milestonesReached(garden, "commission_data.unique_npcs_served", "misc.unique_visitors_served_milestone"), 16) * 3, 48
		);
		lines.add(
			"Offers Accepted",
			Math.min(milestonesReached(garden, "commission_data.total_completed", "misc.offers_accepted_milestone"), 30) * 3, 90
		);
		lines.add("Garden Crop Milestones", Math.min(cropMilestones, 598), 598);

		JsonElement plots = garden.get("unlocked_plots_ids");
		lines.add("Plots Unlocked", Math.min(24, plots != null && plots.isJsonArray() ? plots.getAsJsonArray().size() : 0) * 5, 120);

		// Each composter upgrade level is worth 1 XP (levels 1-7), 2 (8-13), 3 (14-19) or 4 (20-25).
		int composterXp = 0;
		for (String upgrade : new String[]{"speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction"}) {
			int level = (int) PvData.getLong(garden, "composter_data.upgrades." + upgrade);
			for (int i = 1; i <= Math.min(level, 25); i++) composterXp += i <= 7 ? 1 : i <= 13 ? 2 : i <= 19 ? 3 : 4;
		}
		lines.add("Composter Upgrades", composterXp, 305);

		int greenhouse = 0;
		if (garden.get("garden_upgrades") instanceof JsonObject upgrades) {
			for (JsonElement level : upgrades.asMap().values()) {
				if (level.isJsonPrimitive() && level.getAsJsonPrimitive().isNumber()) greenhouse += level.getAsInt();
			}
		}
		lines.add("Greenhouse Upgrades", Math.min(greenhouse, 20) * 5, 100);
	}

	/**
	 * Total powder of one type ever earned, whichever way the profile stores it: as the total, or as what's left plus
	 * what was spent.
	 */
	private static double powderTotal(JsonObject member, String type) {
		JsonObject core = objectAt(member, "mining_core");
		if (core == null) return 0;
		double held = Utils.getElementAsFloat(core.get("powder_" + type), 0);
		if (Utils.getElementAsBoolean(core.get("powder_is_total"), false)) return held;
		return held + Utils.getElementAsFloat(core.get("powder_spent_" + type), 0);
	}

	/** SkyBlock XP for a powder total: linear up to 600k, then a square-root curve up to the cap (NEU's formula). */
	private static double powderXp(double total, double perXp, double factor, double cap) {
		double linear = 600_000.0;
		double under = Math.min(linear, total);
		double over = Math.max(0, Math.min(total, cap) - linear);
		double base = Math.floor(under / perXp);
		double excess = Math.floor(factor * (Math.sqrt(1 + 8 * Math.sqrt((1_758_267.0 / cap) * over + 9)) - 3));
		return base + excess;
	}

	private Task essence(Set<String> completed) {
		JsonObject tasks = PvData.bundled("sblevel_tasks").getAsJsonObject("essence");
		if (tasks == null) return null;
		Lines lines = new Lines();

		// Group the perk-level tasks by their shop: "WITHER_ESSENCE_PERMANENT_HEALTH_3" is in the Wither shop.
		Map<String, double[]> shops = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> task : tasks.entrySet()) {
			int split = task.getKey().indexOf("_ESSENCE_");
			if (split < 0) continue;
			double[] totals = shops.computeIfAbsent(task.getKey().substring(0, split), k -> new double[2]);
			totals[1] += task.getValue().getAsInt();
			if (completed.contains(task.getKey())) totals[0] += task.getValue().getAsInt();
		}
		for (Map.Entry<String, double[]> shop : shops.entrySet()) {
			String name = ChatFormatting.stripFormatting(PvData.itemName("ESSENCE_" + shop.getKey()));
			// The Safari shop's tasks add up to 2 more than the game's total.
			double max = shop.getKey().equals("SAFARI") ? 395 : shop.getValue()[1];
			lines.add(name, Math.min(shop.getValue()[0], max), max);
		}
		return new Task("Essence", () -> PvData.item("ESSENCE_WITHER"), 299, 25, lines.gained, lines.max, lines.lore);
	}

	private Task misc(JsonObject member, JsonObject sb, Set<String> completed) {
		JsonObject misc = sb.getAsJsonObject("miscellaneous_task");
		Lines lines = new Lines();

		lines.add(
			"Accessory Bag Upgrades",
			Utils.getElementAsInt(Utils.getElement(member, "accessory_bag_storage.bag_upgrades_purchased"), 0) *
				misc.get("accessory_bag_upgrades_xp").getAsInt(),
			198
		);
		JsonElement powers = Utils.getElement(member, "accessory_bag_storage.unlocked_powers");
		lines.add(
			"Unlocking Powers",
			(powers != null && powers.isJsonArray() ? powers.getAsJsonArray().size() : 0) * misc.get("unlocking_powers_xp").getAsInt(),
			330
		);
		JsonElement attributes = Utils.getElement(member, "attributes.stacks");
		if (attributes == null) {
			lines.unavailable("Attribute Levels", "§cNo attribute data!", 0);
		} else {
			if (memoAttributes == null) memoAttributes = AttributesPage.totalLevels(member);
			lines.add("Attribute Levels", memoAttributes[0], memoAttributes[1]);
		}

		int consumableXp = count(member, "player_data.reaper_peppers_eaten", misc, "reaper_peppers_xp") +
			count(member, "rift.castle.grubber_stacks", misc, "mcgrubber_burger_xp") +
			count(member, "experimentation.serums_drank", misc, "metaphysical_serum_xp") +
			count(member, "winter_player_data.refined_jyrre_uses", misc, "refined_jyrre_xp") +
			count(member, "garden_player_data.larva_consumed", misc, "wriggling_larva_xp") +
			count(member, "events.easter.refined_dark_cacao_truffles", misc, "refined_dark_cacao_truffles_xp");
		// Isopod husks and bee saliva: 2 XP per use, up to 5 uses each.
		consumableXp += Math.min(5, Utils.getElementAsInt(Utils.getElement(member, "player_data.isopod_husks_eaten"), 0)) * 2;
		consumableXp += Math.min(5, Utils.getElementAsInt(Utils.getElement(member, "player_data.bee_saliva_eaten"), 0)) * 2;
		lines.add("Consumable Items", consumableXp, misc.get("consumable_items").getAsInt() + 20);

		JsonElement trophies = Utils.getElement(member, "rift.gallery.secured_trophies");
		lines.add(
			"Timecharms", (trophies != null && trophies.isJsonArray() ? trophies.getAsJsonArray().size() : 0) * misc.get("timecharm_xp").getAsInt(),
			misc.get("timecharm").getAsInt()
		);

		int relayXp = 0;
		JsonObject abiphone = objectAt(member, "nether_island_player_data.abiphone");
		if (abiphone != null) {
			relayXp = (Utils.getElementAsInt(Utils.getElement(abiphone, "operator_chip.repaired_index"), -1) + 1) *
				misc.get("unlocking_relays_xp").getAsInt();
		}
		lines.add("Upgraded Relays", relayXp, 45);

		addGroup(lines, "Abiphone Contacts", "abiphone", completed, id -> true);
		addGroup(lines, "The Dojo", "dojo", completed, id -> true);
		addGroup(lines, "Harp Songs", "harp", completed, id -> true);
		addGroup(lines, "Community Shop", "community_shop", completed, id -> true);
		addGroup(lines, "Personal Bank Upgrades", "personal_bank", completed, id -> true);
		addGroup(lines, "Reputation", "reputation", completed, id -> true);
		addGroup(lines, "Carrolyn's Exportable Crops", "carrolyn", completed, id -> true);
		// Each Safari biome's milestones are worth 5 XP, except the last (tier 10), which is worth 10.
		int safariXp = 0;
		for (String task : completed) {
			if (task.startsWith("SAFARI_MILESTONE_")) safariXp += task.endsWith("_10") ? 10 : 5;
		}
		lines.add("Safari Milestones", Math.min(safariXp, 220), 220);

		JsonElement family = Utils.getElement(member, "foraging.fish_family");
		lines.add("Fish Family", Math.min(15, family != null && family.isJsonArray() ? family.getAsJsonArray().size() : 0) * 3, 45);

		return new Task("Misc. Task", () -> new ItemStack(Items.FILLED_MAP), 299, 55, lines.gained, lines.max, lines.lore);
	}

	private static int count(JsonObject member, String path, JsonObject misc, String xpKey) {
		if (!misc.has(xpKey)) return 0;
		return Utils.getElementAsInt(Utils.getElement(member, path), 0) * misc.get(xpKey).getAsInt();
	}

	private Task story(JsonObject member, JsonObject sb, Set<String> completed) {
		JsonObject story = sb.getAsJsonObject("story_task");
		Lines lines = new Lines();

		addGroup(lines, "Complete Objectives", "objectives", completed, id -> true);

		JsonObject objectives = objectAt(member, "objectives");
		int riftDone = 0;
		if (objectives != null) {
			for (JsonElement name : story.getAsJsonArray("rift_guide_names")) {
				JsonElement objective = objectives.get(name.getAsString());
				if (
					objective != null && objective.isJsonObject() &&
						"COMPLETE".equals(Utils.getElementAsString(objective.getAsJsonObject().get("status"), ""))
				) {
					riftDone++;
				}
			}
		}
		// The repo's list is one entry short of the game's 120 guide tasks, so a fully done list counts as everything.
		int riftXp = riftDone >= story.getAsJsonArray("rift_guide_names").size()
			? 360 : riftDone * story.get("rift_guide_xp").getAsInt();
		lines.add("Rift Guide", riftXp, 360);

		return new Task("Story Task", () -> new ItemStack(Items.FILLED_MAP), 299, 85, lines.gained, lines.max, lines.lore);
	}

	private Task event(JsonObject member, JsonObject sb, Set<String> completed) {
		Lines lines = new Lines();

		lines.add(
			"Mining Fiesta",
			Math.min(Utils.getElementAsInt(Utils.getElement(member, "leveling.mining_fiesta_ores_mined"), 0), 1_000_000) / 5_000, 200
		);
		lines.add(
			"Fishing Festival",
			Math.min(Utils.getElementAsInt(Utils.getElement(member, "leveling.fishing_festival_sharks_killed"), 0), 5_000) / 50, 100
		);

		addGroup(lines, "Spooky Festival", "festival", completed, id -> true);
		addGroup(lines, "Jacob's Farming Contest", "jacob", completed, id -> true);
		addCount(lines, "Year of the Witch Stews", "YOTW_STEW_", 5, 105, completed);
		for (Map.Entry<String, String> shop : EVENT_SHOPS.entrySet()) {
			addGroup(lines, shop.getValue() + " Shop", "event_perks", completed, id -> id.startsWith(shop.getKey()));
		}

		// Hoppity's Hunt: prestiges of the Chocolate Factory, and each Mythic and Divine rabbit found.
		JsonObject easter = objectAt(member, "events.easter");
		int prestige = Math.min(5, Math.max(0, Utils.getElementAsInt(Utils.getElement(easter, "chocolate_level"), 1) - 1));
		lines.add("Chocolate Factory Prestige", prestige * 25, 125);
		lines.add("Mythic Chocolate Rabbits", rabbitsFound(easter, "MYTHIC") * 10, rabbitCount("MYTHIC") * 10);
		lines.add("Divine Chocolate Rabbits", rabbitsFound(easter, "DIVINE") * 10, rabbitCount("DIVINE") * 10);

		return new Task("Event Task", () -> new ItemStack(Items.CLOCK), 299, 115, lines.gained, lines.max, lines.lore);
	}

	private static int rabbitCount(String rarity) {
		JsonElement names = Utils.getElement(PvData.bundled("chocolate_factory"), "rabbits." + rarity);
		return names != null && names.isJsonArray() ? names.getAsJsonArray().size() : 0;
	}

	private static int rabbitsFound(JsonObject easter, String rarity) {
		JsonElement names = Utils.getElement(PvData.bundled("chocolate_factory"), "rabbits." + rarity);
		if (easter == null || names == null || !names.isJsonArray()) return 0;
		int found = 0;
		for (JsonElement name : names.getAsJsonArray()) {
			if (Utils.getElementAsInt(Utils.getElement(easter, "rabbits." + name.getAsString().toLowerCase(Locale.ROOT)), 0) > 0) {
				found++;
			}
		}
		return found;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------------------

	/**
	 * A line for a group of tasks in {@code sblevel_tasks.json}: the XP of those the player has completed, out of the
	 * XP of all of them.
	 */
	private static void addGroup(Lines lines, String name, String group, Set<String> completed, Predicate<String> filter) {
		JsonObject tasks = PvData.bundled("sblevel_tasks").getAsJsonObject(group);
		if (tasks == null) return;
		double earned = 0;
		double max = 0;
		for (Map.Entry<String, JsonElement> task : tasks.entrySet()) {
			if (!filter.test(task.getKey())) continue;
			int xp = task.getValue().getAsInt();
			max += xp;
			if (completed.contains(task.getKey())) earned += xp;
		}
		if (max > 0) lines.add(name, earned, max);
	}

	/** A line worth {@code perTask} XP for each completed task whose id starts with {@code prefix}, up to {@code max}. */
	private static void addCount(Lines lines, String name, String prefix, int perTask, int max, Set<String> completed) {
		int done = 0;
		for (String task : completed) {
			if (task.startsWith(prefix)) done++;
		}
		lines.add(name, Math.min(done * perTask, max), max);
	}

	private static Set<String> completedTasks(JsonObject member) {
		Set<String> tasks = new HashSet<>();
		JsonElement list = Utils.getElement(member, "leveling.completed_tasks");
		if (list != null && list.isJsonArray()) {
			for (JsonElement task : list.getAsJsonArray()) {
				if (task.isJsonPrimitive()) tasks.add(task.getAsString());
			}
		}
		return tasks;
	}

	private static JsonObject objectAt(JsonObject root, String path) {
		JsonElement element = Utils.getElement(root, path);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	/** One tooltip line: "Name: 42% (xp/max XP)", green once complete. */
	static String buildLore(String name, double gained, double gainful) {
		String gainedText = GuiProfileViewer.numberFormat.format((long) gained);
		String gainfulText = GuiProfileViewer.numberFormat.format((long) gainful);
		int percentage = gainful <= 0 ? 0 : (int) (gained / gainful * 100);
		return "§6" + name + ": " + (gained >= gainful ? "§a" : "§e") + percentage + "% §8(" + gainedText + "/" + gainfulText + " XP)";
	}
}
