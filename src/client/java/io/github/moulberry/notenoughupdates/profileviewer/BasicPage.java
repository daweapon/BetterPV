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

import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.profileviewer.weight.lily.LilyWeight;
import io.github.moulberry.notenoughupdates.profileviewer.weight.senither.SenitherWeight;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.moulberry.notenoughupdates.util.Utils.roundToNearestInt;

/**
 * Port of the Forge 1.8.9 {@code BasicPage} (the default "Your Skills" tab).
 *
 * <p><b>TODO(fabric-port) — intentionally simplified vs. the original:</b>
 * <ul>
 *   <li>The player model ({@link ProfilePlayerEntity}, drawn with vanilla's inventory-preview helper) has no
 *   rank/name overlay above it.</li>
 *   <li>The active-pet icon and the "Potato King" easter-egg item icons both relied on
 *   {@code NEUManager#jsonToStack(JsonObject)} turning arbitrary Hypixel item JSON into a real, correctly-skinned
 *   {@code ItemStack} (skulls, custom textures, etc.) - that pipeline wasn't ported in the data-layer pass this
 *   GUI port builds on, so those icon renders are skipped (the active pet's name is still shown as text).</li>
 *   <li>Pronoun lookup/display ({@code PronounDB}) wasn't ported in the data-layer pass either, so it's skipped
 *   here too.</li>
 *   <li>The click-and-drag panorama rotation (via LWJGL2 {@code Mouse.isButtonDown} polling, which no longer
 *   exists) is simplified to a constant time-based rotation.</li>
 * </ul>
 */
public class BasicPage implements GuiProfileViewerPage {

	private final GuiProfileViewer instance;

	private static final ItemStack SOCIAL_STACK = Utils.createItemStack(Items.EMERALD, ChatFormatting.DARK_GREEN + "Social");

	/** The SkyBlock-level icon current NEU uses (same skull texture). */
	static final ItemStack SKYBLOCK_LEVEL_SKULL = Utils.createSkull(
		"SkyBlock Level",
		"152de44a-43a3-46e1-badc-66cca2793471",
		"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdkODg1YjMyYjBkZDJkNmI3ZjFiNTgyYTM0MTg2ZjhhNTM3M2M0NjU4OWEyNzM0MjMxMzJiNDQ4YjgwMzQ2MiJ9fX0="
	);

	/** Hypixel's SkyBlock level colours, one per 40 levels (0-39 grey, 40-79 white, ... 480+ dark red). */
	private static final ChatFormatting[] SKYBLOCK_LEVEL_COLOURS = {
		ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.YELLOW, ChatFormatting.GREEN,
		ChatFormatting.DARK_GREEN, ChatFormatting.AQUA, ChatFormatting.DARK_AQUA, ChatFormatting.BLUE,
		ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.RED,
		ChatFormatting.DARK_RED
	};

	/** Armor slots in {@code inv_armor} order (boots first). */
	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
	};

	private ProfilePlayerEntity playerEntity;
	private String playerEntityFor;

	private static final ItemStack HOME_STACK = Utils.createItemStack(Items.PAPER, ChatFormatting.GRAY + "Home");
	private static final ItemStack LEVEL_STACK = Utils.createSkull(
		ChatFormatting.GRAY + "Level",
		"152de44a-43a3-46e1-badc-66cca2793471",
		"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdkODg1YjMyYjBkZDJkNmI3ZjFiNTgyYTM0MTg2ZjhhNTM3M2M0NjU4OWEyNzM0MjMxMzJiNDQ4YjgwMzQ2MiJ9fX0="
	);

	private static final ItemStack CRIMSON_STACK = withName(CrimsonIslePage.KUUDRA_KEYS[4].copy(), ChatFormatting.GRAY + "Crimson Isle");

	private final LevelPage levelPage;
	private final CrimsonIslePage crimsonPage;

	public BasicPage(GuiProfileViewer instance) {
		this.instance = instance;
		this.levelPage = new LevelPage(instance);
		this.crimsonPage = new CrimsonIslePage(instance);
	}

	private static ItemStack withName(ItemStack stack, String name) {
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
		return stack;
	}

	private static final ItemStack[] SIDE_STACKS = {HOME_STACK, LEVEL_STACK, CRIMSON_STACK};
	private static final String[] SIDE_NAMES = {"Home", "Level", "Crimson Isle"};

	/** Which side button is pressed: 0 Home, 1 Level, 2 Crimson Isle. */
	private static int activeSidePage() {
		return GuiProfileViewer.onCrimsonPage ? 2 : GuiProfileViewer.onSecondPage ? 1 : 0;
	}

	/** The Home / Level / Crimson Isle buttons down the left edge, shared with the level and Crimson Isle pages. */
	static void drawSideButtons(GuiGraphicsExtractor graphics, GuiProfileViewer instance, int mouseX, int mouseY) {
		int active = activeSidePage();
		// The unpressed buttons first, so the pressed one's wider edge draws over them.
		for (int i = 0; i < SIDE_STACKS.length; i++) {
			if (i != active) LevelPage.drawSideButton(graphics, i, SIDE_STACKS[i], false);
		}
		LevelPage.drawSideButton(graphics, active, SIDE_STACKS[active], true);

		int left = GuiProfileViewer.getGuiLeft() - 28;
		int top = GuiProfileViewer.getGuiTop();
		for (int i = 0; i < SIDE_NAMES.length; i++) {
			if (Utils.isWithinRect(mouseX, mouseY, left, top + i * 28, 28, 28)) {
				instance.tooltipToDisplay = Utils.createList(ChatFormatting.GRAY + SIDE_NAMES[i]);
			}
		}
	}

	/** Switches between the basic, level and Crimson Isle pages when a side button is clicked. */
	static boolean clickedSideButtons(double mouseX, double mouseY, int mouseButton) {
		if (mouseButton != 0) return false;
		int left = GuiProfileViewer.getGuiLeft() - 28;
		int top = GuiProfileViewer.getGuiTop();
		for (int i = 0; i < SIDE_STACKS.length; i++) {
			if (!Utils.isWithinRect((int) mouseX, (int) mouseY, left, top + i * 28, 28, 28)) continue;
			if (i != activeSidePage()) RenderUtils.playPressSound();
			GuiProfileViewer.onSecondPage = i == 1;
			GuiProfileViewer.onCrimsonPage = i == 2;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		if (GuiProfileViewer.onCrimsonPage) return crimsonPage.mouseClicked(mouseX, mouseY, mouseButton);
		if (GuiProfileViewer.onSecondPage) return levelPage.mouseClicked(mouseX, mouseY, mouseButton);
		if (clickedSideButtons(mouseX, mouseY, mouseButton)) return true;

		// Clicking the SkyBlock level panel opens the level breakdown, as in NEU.
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();
		if (mouseButton == 0 && Utils.isWithinRect((int) mouseX, (int) mouseY, guiLeft + 128, guiTop + 49, 88, 64)) {
			RenderUtils.playPressSound();
			GuiProfileViewer.onSecondPage = true;
			return true;
		}
		return false;
	}

	@Override
	public void resetCache() {
		levelPage.resetCache();
		playerEntity = null;
		playerEntityFor = null;
	}

	/** The player's model with their skin and current armor, in the panorama box, turning to follow the mouse. */
	private void drawPlayer(
		GuiGraphicsExtractor graphics, ProfileViewer.Profile profile, String profileId, int guiLeft, int guiTop, int mouseX, int mouseY
	) {
		Minecraft minecraft = Minecraft.getInstance();
		UUID uuid = ProfilePlayerEntity.parseUuid(profile.getUuid());
		if (minecraft.level == null || uuid == null) return;

		String key = uuid + "/" + profileId;
		if (playerEntity == null || !key.equals(playerEntityFor) || playerEntity.level() != minecraft.level) {
			String name = Utils.getElementAsString(Utils.getElement(profile.getHypixelProfile(), "displayname"), "Player");
			ProfilePlayerEntity entity = new ProfilePlayerEntity(minecraft.level, uuid, name);
			JsonElement armor = Utils.getElement(profile.getInventoryInfo(profileId), "inv_armor");
			if (armor != null && armor.isJsonArray()) {
				for (int i = 0; i < Math.min(ARMOR_SLOTS.length, armor.getAsJsonArray().size()); i++) {
					JsonElement item = armor.getAsJsonArray().get(i);
					if (item == null || !item.isJsonObject() || item.getAsJsonObject().isEmpty()) continue;
					ItemStack stack = NotEnoughUpdates.INSTANCE.manager.jsonToStack(item.getAsJsonObject(), false);
					if (stack != null) entity.setItemSlot(ARMOR_SLOTS[i], stack);
				}
			}
			playerEntity = entity;
			playerEntityFor = key;
		}

		// Vanilla gives players within 64 blocks of the camera a name tag, so keep this one far above it.
		if (minecraft.player != null) {
			playerEntity.setPos(minecraft.player.getX(), minecraft.player.getY() + 1000, minecraft.player.getZ());
		}
		// Centred in the box, sized to leave room for the name tag above the head (checked in game: 32 was a little
		// small, 44 overflowed the box).
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			graphics, guiLeft + 23, guiTop + 44, guiLeft + 104, guiTop + 152, 36, 0.0625f, mouseX, mouseY, playerEntity
		);

		JsonObject hypixelProfile = profile.getHypixelProfile();
		if (hypixelProfile != null) {
			String name = Utils.getElementAsString(hypixelProfile.get("displayname"), playerEntity.getGameProfile().name());
			RenderUtils.drawStringCenteredScaledMaxWidth(
				graphics, rankedName(hypixelProfile, name), instance.getFont(), guiLeft + 63.5f, guiTop + 51, true, 81, 0xFFFFFF
			);
		}
	}

	/** "[MVP++] Name" coloured the way Hypixel shows it, from the player endpoint's rank fields. */
	static String rankedName(JsonObject player, String name) {
		String prefix = Utils.getElementAsString(player.get("prefix"), null);
		if (prefix != null) return prefix + " " + name;

		String plus = colour(Utils.getElementAsString(player.get("rankPlusColor"), "RED"), "§c");
		String staff = Utils.getElementAsString(player.get("rank"), "NORMAL");
		String rank = switch (staff) {
			case "ADMIN" -> "§c[ADMIN]";
			case "GAME_MASTER" -> "§2[GM]";
			case "MODERATOR" -> "§2[MOD]";
			case "HELPER" -> "§9[HELPER]";
			case "YOUTUBER" -> "§c[§fYOUTUBE§c]";
			default -> null;
		};
		if (rank == null && "SUPERSTAR".equals(Utils.getElementAsString(player.get("monthlyPackageRank"), "NONE"))) {
			String main = colour(Utils.getElementAsString(player.get("monthlyRankColor"), "GOLD"), "§6");
			rank = main + "[MVP" + plus + "++" + main + "]";
		}
		if (rank == null) {
			String packageRank = Utils.getElementAsString(player.get("newPackageRank"),
				Utils.getElementAsString(player.get("packageRank"), "NONE"));
			rank = switch (packageRank) {
				case "VIP" -> "§a[VIP]";
				case "VIP_PLUS" -> "§a[VIP§6+§a]";
				case "MVP" -> "§b[MVP]";
				case "MVP_PLUS" -> "§b[MVP" + plus + "+§b]";
				default -> null;
			};
		}
		if (rank == null) return "§7" + name;
		// The name takes the colour of the rank's opening bracket.
		return rank + " " + rank.substring(0, 2) + name;
	}

	/** A Hypixel colour name ("DARK_GREEN") as a § code. */
	private static String colour(String name, String fallback) {
		ChatFormatting formatting = ChatFormatting.getByName(name.toLowerCase(java.util.Locale.ROOT));
		return formatting != null && formatting.isColor() ? formatting.toString() : fallback;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	private static final Map<String, String> NETWORTH_NAMES = Map.ofEntries(
		Map.entry("inv_armor", "Armor"), Map.entry("inv_contents", "Inventory"),
		Map.entry("ender_chest_contents", "Ender Chest"), Map.entry("backpack_contents", "Backpacks"),
		Map.entry("talisman_bag", "Accessory Bag"), Map.entry("wardrobe_contents", "Wardrobe"),
		Map.entry("equippment_contents", "Equipment"), Map.entry("personal_vault_contents", "Personal Vault"),
		Map.entry("fishing_bag", "Fishing Bag"), Map.entry("potion_bag", "Potion Bag"), Map.entry("quiver", "Quiver"),
		Map.entry("candy_inventory_contents", "Candy Bag"), Map.entry("loadout_equipment", "Equipment Sets"),
		Map.entry("loadout_armor", "Armor Sets"), Map.entry("pets", "Pets"), Map.entry("sacks", "Sacks"),
		Map.entry("museum", "Museum"), Map.entry("bank", "Bank"), Map.entry("purse", "Purse")
	);

	/** The net worth split by source, biggest first, then the joke IRL-money line. */
	private static List<String> networthTooltip(Map<String, Long> breakdown, long networth) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add(ChatFormatting.GREEN + "Net Worth: " + ChatFormatting.GOLD + GuiProfileViewer.numberFormat.format(networth));
		breakdown.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.forEach(entry -> tooltip.add(
				ChatFormatting.GRAY + " " + NETWORTH_NAMES.getOrDefault(entry.getKey(), entry.getKey()) + ": " +
					ChatFormatting.GOLD + GuiProfileViewer.numberFormat.format(entry.getValue()) + ChatFormatting.DARK_GRAY + " (" +
					(networth > 0 ? Math.round(entry.getValue() * 1000.0 / networth) / 10.0 : 0) + "%)"
			));
		try {
			double cookies = networth / NotEnoughUpdates.INSTANCE.manager.auctionManager.getBazaarInfo("BOOSTER_COOKIE").get("avg_buy").getAsDouble();
			String irl = Long.toString(Math.round(((cookies * 325) / 675) * 4.99));
			tooltip.add("");
			tooltip.add(ChatFormatting.GREEN + "In IRL money: " + ChatFormatting.DARK_GREEN + "$" + ChatFormatting.GOLD + irl);
			tooltip.add(ChatFormatting.GRAY + "Item prices provided by SkyCofl");
			tooltip.add(ChatFormatting.DARK_GRAY + "(This is a joke, please don't trade real money)");
		} catch (Exception ignored) {
		}
		return tooltip;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font fr = instance.getFont();
		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (GuiProfileViewer.onCrimsonPage) {
			crimsonPage.drawPage(graphics, mouseX, mouseY, partialTicks);
			return;
		}
		if (GuiProfileViewer.onSecondPage) {
			levelPage.drawPage(graphics, mouseX, mouseY, partialTicks);
			return;
		}
		drawSideButtons(graphics, instance, mouseX, mouseY);

		String location = null;
		JsonObject status = profile.getPlayerStatus();
		if (status != null && status.has("mode")) {
			location = status.get("mode").getAsString();
		}

		instance.backgroundRotation += (instance.currentTime - instance.lastTime) / 400f;
		instance.backgroundRotation %= 360;

		// See Panorama#currentDayNightIdentifier javadoc: approximated from real local time, not the actual
		// in-game Skyblock clock.
		String panoramaIdentifier = Panorama.currentDayNightIdentifier();

		Panorama.drawPanorama(
			graphics,
			-instance.backgroundRotation,
			guiLeft + 23,
			guiTop + 44,
			81,
			108,
			0.37f,
			0.8f,
			Panorama.getPanoramasForLocation(location == null ? "unknown" : location, panoramaIdentifier)
		);

		RenderUtils.drawTexturedRect(graphics, pv_basic, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		drawPlayer(graphics, profile, profileId, guiLeft, guiTop, mouseX, mouseY);

		long networth = io.github.moulberry.notenoughupdates.util.BpvConfig.isHideNetWorth()
			? -1 : profile.getNetWorthInBackground(profileId);
		if (networth > 0) {
			RenderUtils.drawStringCentered(
				graphics,
				ChatFormatting.GREEN + "Net Worth: " + ChatFormatting.GOLD + GuiProfileViewer.numberFormat.format(networth),
				fr,
				guiLeft + 63,
				guiTop + 38,
				true,
				0
			);
			int labelWidth = fr.width("Net Worth: " + GuiProfileViewer.numberFormat.format(networth));
			if (Utils.isWithinRect(mouseX, mouseY, guiLeft + 63 - labelWidth / 2, guiTop + 33, labelWidth, fr.lineHeight + 2)) {
				instance.tooltipToDisplay = networthTooltip(profile.getNetWorthBreakdown(profileId), networth);
			}
		}

		if (status != null) {
			JsonElement onlineElement = Utils.getElement(status, "online");
			boolean online = onlineElement != null && onlineElement.isJsonPrimitive() && onlineElement.getAsBoolean();
			JsonObject player = profile.getHypixelProfile();
			JsonElement lastLoginElement = Utils.getElement(player, "lastLogin");
			JsonElement lastLogoutElement = Utils.getElement(player, "lastLogout");
			JsonElement onlineSetting = Utils.getElement(player, "settings.apiSettings.onlineStatus");
			boolean timestampsHidden = (lastLoginElement == null || lastLoginElement.isJsonNull())
				&& (lastLogoutElement == null || lastLogoutElement.isJsonNull());
			boolean settingDisabled = onlineSetting != null && onlineSetting.isJsonPrimitive()
				&& onlineSetting.getAsJsonPrimitive().isBoolean() && !onlineSetting.getAsBoolean();
			// Hypixel redacts both timestamps when the player disables the Online Status API setting.
			boolean apiOff = !online && (timestampsHidden || settingDisabled);
			String statusStr = online ? ChatFormatting.GREEN + "ONLINE"
				: apiOff ? ChatFormatting.YELLOW + "API OFF" : ChatFormatting.RED + "OFFLINE";
			RenderUtils.drawStringCentered(graphics, statusStr, fr, guiLeft + 63, guiTop + 160, true, 0);
		}

		JsonObject profileInfo = profile.getProfileInformation(profileId);
		if (profileInfo == null) return;

		Map<String, ProfileViewer.Level> skyblockInfo = profile.getSkyblockInfo(profileId);

		JsonObject petsInfo = profile.getPetsInfo(profileId);
		if (petsInfo != null) {
			JsonElement activePetElement = petsInfo.get("active_pet");
			if (activePetElement != null && activePetElement.isJsonObject()) {
				// Active pet's item icon, bobbing beside the panorama like the original. Repo pet items are keyed
				// "<TYPE>;<rarity index>"; any rarity's icon will do.
				String type = activePetElement.getAsJsonObject().get("type").getAsString();
				for (int i = 0; i < 6; i++) {
					JsonObject item = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(type + ";" + i);
					if (item == null) continue;
					ItemStack stack = NotEnoughUpdates.INSTANCE.manager.jsonToStack(item);
					float y = guiTop + 82 + 15 * (float) Math.sin(((instance.currentTime - instance.startTime) / 800f) % (2 * Math.PI));
					graphics.pose().pushMatrix();
					graphics.pose().translate(guiLeft + 20, y);
					graphics.pose().scale(1.5f, 1.5f);
					RenderUtils.drawItemStack(graphics, stack, 0, 0);
					graphics.pose().popMatrix();
					break;
				}
			}
		}

		// Middle panel: SkyBlock level with Social under it, as in current NEU (which dropped the old stat list:
		// those numbers were estimates that no longer match the game).
		drawSkyblockLevel(graphics, profileInfo, guiLeft, guiTop, mouseX, mouseY);
		if (skyblockInfo != null && skyblockInfo.containsKey("social")) {
			instance.renderXpBar(
				graphics, SOCIAL_STACK.getHoverName().getString(), SOCIAL_STACK, guiLeft + 132, guiTop + 124, 80,
				skyblockInfo.get("social"), mouseX, mouseY
			);
		}

		if (skyblockInfo != null) {
			int position = 0;
			// Two columns; with Hunting that's 17 entries, so 9 rows at an 18px pitch (the original had 8 at 21px).
			int rows = (ProfileViewer.getSkillToSkillDisplayMap().size() + 1) / 2;
			for (Map.Entry<String, ItemStack> entry : ProfileViewer.getSkillToSkillDisplayMap().entrySet()) {
				if (entry.getValue() == null || entry.getKey() == null) {
					position++;
					continue;
				}

				int yPosition = position % rows;
				int xPosition = position / rows;

				String skillName = entry.getValue().getHoverName().getString();

				ProfileViewer.Level levelObj = skyblockInfo.get(entry.getKey());
				float level = levelObj.level;
				int levelFloored = (int) Math.floor(level);

				int x = guiLeft + 237 + 86 * xPosition;
				int y = guiTop + 24 + (rows > 8 ? 18 : 21) * yPosition;

				RenderUtils.renderAlignedString(graphics, skillName, ChatFormatting.WHITE.toString() + levelFloored, x + 14, y - 4, 60);

				if (levelObj.maxed) {
					instance.renderGoldBar(graphics, x, y + 6, 80);
				} else {
					instance.renderBar(graphics, x, y + 6, 80, level % 1);
				}

				if (Utils.isWithinRect(mouseX, mouseY, x, y - 4, 80, 17)) {
					List<String> tooltip = new ArrayList<>();
					tooltip.add(skillName);
					if (levelObj.maxed) {
						tooltip.add(ChatFormatting.GRAY + "Progress: " + ChatFormatting.GOLD + "MAXED!");
					} else {
						int maxXp = (int) levelObj.maxXpForLevel;
						tooltip.add(
							ChatFormatting.GRAY +
								"Progress: " +
								ChatFormatting.DARK_PURPLE +
								StringUtils.shortNumberFormat(Math.round((level % 1) * maxXp)) +
								"/" +
								StringUtils.shortNumberFormat(maxXp)
						);
					}
					tooltip.add(ChatFormatting.GRAY + "Total XP: " + ChatFormatting.DARK_PURPLE + GuiProfileViewer.numberFormat.format((int) levelObj.totalXp));
					instance.tooltipToDisplay = tooltip;
				}

				RenderUtils.drawSkillIcon(graphics, entry.getValue(), x, y - 6);

				position++;
			}
		} else {
			RenderUtils.drawStringCentered(graphics, ChatFormatting.RED + "Skills API not enabled!", fr, guiLeft + 322, guiTop + 101, true, 0);
		}

		renderWeight(graphics, mouseX, mouseY, skyblockInfo, profileInfo);
	}

	/**
	 * SkyBlock level (100 XP per level, from leveling.experience), laid out as in current NEU's BasicPage: the
	 * coloured level number over the SkyBlock-level skull (both 1.5x), then "n/100" and the progress bar, inside
	 * the middle box of pv_basic.png. (Clicking it opens the level-breakdown page, {@link LevelPage}.)
	 */
	private void drawSkyblockLevel(
		GuiGraphicsExtractor graphics, JsonObject profileInfo, int guiLeft, int guiTop, int mouseX, int mouseY
	) {
		JsonElement experienceElement = Utils.getElement(profileInfo, "leveling.experience");
		if (experienceElement == null) return;
		int experience = Utils.getElementAsInt(experienceElement, 0);
		int level = experience / 100;
		int progress = experience % 100;
		ChatFormatting colour = SKYBLOCK_LEVEL_COLOURS[Math.min(level / 40, SKYBLOCK_LEVEL_COLOURS.length - 1)];
		Font font = instance.getFont();

		int sbLevelX = guiLeft + 162;
		int sbLevelY = guiTop + 74;

		graphics.pose().pushMatrix();
		graphics.pose().translate(sbLevelX, sbLevelY);
		graphics.pose().scale(1.5f, 1.5f);
		RenderUtils.drawItemStack(graphics, SKYBLOCK_LEVEL_SKULL, 0, 0);
		graphics.pose().popMatrix();

		graphics.pose().pushMatrix();
		graphics.pose().translate(sbLevelX + 9, sbLevelY - 12);
		graphics.pose().scale(1.5f, 1.5f);
		RenderUtils.drawStringCentered(graphics, colour.toString() + level, font, 0, 0, true, 0);
		graphics.pose().popMatrix();

		// The bar turns rainbow at the level cap.
		if (experience >= LevelPage.MAX_EXPERIENCE) {
			instance.renderGoldBar(graphics, sbLevelX - 30, sbLevelY + 30, 80);
		} else {
			instance.renderBar(graphics, sbLevelX - 30, sbLevelY + 30, 80, progress / 100f);
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(sbLevelX - 30, sbLevelY + 20);
		graphics.pose().scale(0.9f, 0.9f);
		RenderUtils.text(graphics, font, ChatFormatting.YELLOW.toString() + progress + "/100", 0, 0, 0xFFFFFF, true);
		graphics.pose().popMatrix();

		if (Utils.isWithinRect(mouseX, mouseY, guiLeft + 128, guiTop + 49, 88, 64)) {
			instance.tooltipToDisplay = Utils.createList(
				colour + "SkyBlock Level " + level,
				ChatFormatting.GRAY + "Progress: " + ChatFormatting.YELLOW + progress + "/100 XP",
				ChatFormatting.GRAY + "Total XP: " + ChatFormatting.YELLOW + GuiProfileViewer.numberFormat.format(experience)
			);
		}
	}

	private void renderWeight(
		GuiGraphicsExtractor graphics, int mouseX, int mouseY, Map<String, ProfileViewer.Level> skyblockInfo, JsonObject profileInfo
	) {
		if (skyblockInfo == null) return;

		if (Constants.WEIGHT == null || Utils.getElement(Constants.WEIGHT, "lily.skills.overall") == null ||
			!Utils.getElement(Constants.WEIGHT, "lily.skills.overall").isJsonPrimitive()) {
			return;
		}

		Font fr = instance.getFont();
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		SenitherWeight senitherWeight = new SenitherWeight(skyblockInfo);
		LilyWeight lilyWeight = new LilyWeight(skyblockInfo, profileInfo);

		RenderUtils.drawStringCentered(
			graphics,
			ChatFormatting.GREEN + "Senither Weight: " + ChatFormatting.GOLD + GuiProfileViewer.numberFormat.format(roundToNearestInt(senitherWeight.getTotalWeight().getRaw())),
			fr,
			guiLeft + 63,
			guiTop + 18,
			true,
			0
		);

		RenderUtils.drawStringCentered(
			graphics,
			ChatFormatting.GREEN + "Lily Weight: " + ChatFormatting.GOLD + GuiProfileViewer.numberFormat.format(roundToNearestInt(lilyWeight.getTotalWeight().getRaw())),
			fr,
			guiLeft + 63,
			guiTop + 28,
			true,
			0
		);
	}

	private static final net.minecraft.resources.Identifier pv_basic = net.minecraft.resources.Identifier.parse("betterpv:pv_basic.png");
}
