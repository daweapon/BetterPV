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
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.bestiary.BestiaryPage;
import io.github.moulberry.notenoughupdates.profileviewer.trophy.TrophyFishPage;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.PetData;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Port of the Forge 1.8.9 {@code GuiProfileViewer} main screen.
 *
 * <p>API mapping notes (Forge 1.8.9 -&gt; Fabric 26.1.2), in addition to the ones on {@link GuiProfileViewerPage}:
 * <ul>
 *   <li>{@code GuiScreen} -&gt; {@code net.minecraft.client.gui.screens.Screen}.</li>
 *   <li>{@code drawScreen(int, int, float)} -&gt; {@code extractRenderState(GuiGraphicsExtractor, int, int, float)}.
 *   Minecraft no longer draws immediately from this call; it builds up a list of render-state objects on the
 *   passed-in {@code GuiGraphicsExtractor} that get consumed by the GPU renderer afterwards.</li>
 *   <li>{@code mouseClicked(int,int,int) throws IOException} -&gt; {@code boolean mouseClicked(MouseButtonEvent, boolean doubleClick)}.</li>
 *   <li>{@code keyTyped(char,int) throws IOException} -&gt; split into {@code keyPressed(KeyEvent)} and
 *   {@code charTyped(CharacterEvent)}.</li>
 *   <li>The custom {@code GuiElementTextField} player-name search box is replaced with vanilla's
 *   {@code net.minecraft.client.gui.components.EditBox}, added as a normal screen widget via
 *   {@code addRenderableWidget}. This means Screen's default input routing (focus, click-to-focus, typing) is
 *   handled for us instead of needing to be reimplemented; only Enter-to-submit needed a manual hook.</li>
 * </ul>
 *
 * <p><b>TODO(fabric-port) — intentionally simplified vs. the original:</b>
 * <ul>
 *   <li>The custom Gaussian-blur backdrop (a hand-rolled two-pass GLSL shader against a framebuffer, via
 *   {@code cosmetics.ShaderManager}/{@code net.minecraft.client.shader.Shader}) is replaced with vanilla's own
 *   built-in menu blur, applied by {@code Screen#extractBackground} before {@link #extractRenderState} runs, which
 *   blurs the whole window instead of just the panel's bounds, but needs no custom shader work.</li>
 *   <li>The "gold shimmer" maxed-skill-bar shader ({@code renderGoldBar}) is approximated with a few plain
 *   {@code graphics.fill} rectangles forming a looping sweep instead of a custom GLSL shader - see that
 *   method's javadoc.</li>
 *   <li>{@code config.profileViewer.pageLayout}/{@code alwaysShowBingoTab}/{@code showPronounsInPv} and other
 *   {@code NEUConfig} reads are out of scope for this pass (see repo-wide TODOs); this uses the natural
 *   {@link ProfileViewerPage} enum order as a hardcoded layout and always hides the Bingo tab unless the
 *   profile's game mode is actually bingo.</li>
 * </ul>
 */
public class GuiProfileViewer extends net.minecraft.client.gui.screens.Screen {

	public static final Identifier pv_dropdown = Identifier.parse("notenoughupdates:pv_dropdown.png");
	public static final Identifier pv_bg = Identifier.parse("notenoughupdates:pv_bg.png");
	public static final Identifier pv_elements = Identifier.parse("notenoughupdates:pv_elements.png");
	public static final Identifier pv_ironman = Identifier.parse("notenoughupdates:pv_ironman.png");
	public static final Identifier pv_bingo = Identifier.parse("notenoughupdates:pv_bingo.png");
	public static final Identifier pv_stranded = Identifier.parse("notenoughupdates:pv_stranded.png");
	public static final Identifier pv_unknown = Identifier.parse("notenoughupdates:pv_unknown.png");

	public static final java.text.NumberFormat numberFormat = java.text.NumberFormat.getInstance(Locale.US);

	public static ProfileViewerPage currentPage = ProfileViewerPage.BASIC;
	private static int guiLeft;
	private static int guiTop;
	private static ProfileViewer.Profile profile;
	private static String profileId = null;

	public EditBox playerNameTextField;
	private final Map<ProfileViewerPage, GuiProfileViewerPage> pages = new HashMap<>();
	private final java.util.Set<String> loggedPageErrors = new java.util.HashSet<>();
	public int sizeX;
	public int sizeY;
	public float backgroundRotation = 0;
	public long currentTime = 0;
	public long lastTime = 0;
	public long startTime = 0;
	public List<String> tooltipToDisplay = null;
	private boolean profileDropdownSelected = false;
	private boolean showBingoPage;
	private final String initialPlayerName;

	public GuiProfileViewer(ProfileViewer.Profile profile) {
		super(Component.literal("Profile Viewer"));
		GuiProfileViewer.profile = profile;
		GuiProfileViewer.profileId = profile == null ? null : profile.getLatestProfile();

		String name = "";
		if (profile != null && profile.getHypixelProfile() != null && profile.getHypixelProfile().has("displayname")) {
			name = profile.getHypixelProfile().get("displayname").getAsString();
		}
		this.initialPlayerName = name;

		if (currentPage == ProfileViewerPage.LOADING) {
			currentPage = ProfileViewerPage.BASIC;
		}

		pages.put(ProfileViewerPage.BASIC, new BasicPage(this));
		pages.put(ProfileViewerPage.DUNGEON, new DungeonPage(this));
		pages.put(ProfileViewerPage.EXTRA, new ExtraPage(this));
		pages.put(ProfileViewerPage.INVENTORIES, new InventoriesPage(this));
		pages.put(ProfileViewerPage.COLLECTIONS, new CollectionsPage(this));
		pages.put(ProfileViewerPage.PETS, new PetsPage(this));
		pages.put(ProfileViewerPage.MINING, new MiningPage(this));
		pages.put(ProfileViewerPage.BINGO, new BingoPage(this));
		pages.put(ProfileViewerPage.TROPHY_FISH, new TrophyFishPage(this));
		pages.put(ProfileViewerPage.BESTIARY, new BestiaryPage(this));
		pages.put(ProfileViewerPage.FARMING, new PlaceholderPage(this, "Farming"));
		pages.put(ProfileViewerPage.FORAGING, new ForagingPage(this));
		pages.put(ProfileViewerPage.LOADOUTS, new LoadoutsPage(this));
		pages.put(ProfileViewerPage.MUSEUM, new PlaceholderPage(this, "Museum"));
		pages.put(ProfileViewerPage.CHOCOLATE_FACTORY, new PlaceholderPage(this, "Chocolate Factory"));
		pages.put(ProfileViewerPage.RIFT, new PlaceholderPage(this, "Rift"));
	}

	@Override
	protected void init() {
		this.sizeX = 431;
		this.sizeY = 202;
		guiLeft = (this.width - this.sizeX) / 2;
		guiTop = (this.height - this.sizeY) / 2;

		playerNameTextField = new EditBox(
			this.minecraft.font,
			guiLeft + sizeX - 100,
			guiTop + sizeY + 5,
			100,
			20,
			Component.literal("Player name")
		);
		playerNameTextField.setValue(initialPlayerName);
		playerNameTextField.setMaxLength(64);
		this.addRenderableWidget(playerNameTextField);
	}

	private static float getMaxLevelXp(JsonArray levels, int offset, int maxLevel) {
		float xpTotal = 0;
		for (int i = offset; i < offset + maxLevel - 1; i++) {
			xpTotal += levels.get(i).getAsFloat();
		}
		return xpTotal;
	}

	public static PetLevel getPetLevel(String petType, String rarity, float exp) {
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
		float pct;

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

	public static int getGuiLeft() {
		return guiLeft;
	}

	public static int getGuiTop() {
		return guiTop;
	}

	public static ProfileViewer.Profile getProfile() {
		return profile;
	}

	public static String getProfileId() {
		return profileId;
	}

	/** Exposes the protected {@code Screen#font} to page classes that live in a different package (bestiary/trophy). */
	public net.minecraft.client.gui.Font getFont() {
		return this.font;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		currentTime = System.currentTimeMillis();
		if (startTime == 0) startTime = currentTime;

		ProfileViewerPage page = currentPage;
		if (profile == null) {
			page = ProfileViewerPage.INVALID_NAME;
		} else if (profile.getSkyblockProfiles(null) == null) {
			page = profile.getSkyblockProfilesError() != null ? ProfileViewerPage.INVALID_NAME : ProfileViewerPage.LOADING;
		} else if (profile.getLatestProfile() == null) {
			page = ProfileViewerPage.NO_SKYBLOCK;
		}

		if (profileId == null && profile != null && profile.getLatestProfile() != null) {
			profileId = profile.getLatestProfile();
		}

		if (profile != null) {
			profile.getGuildInformation(null); // just to cache the guild info
		}

		JsonObject currProfileInfo = profile != null ? profile.getProfileInformation(profileId) : null;
		showBingoPage =
			currProfileInfo != null && currProfileInfo.has("game_mode") && currProfileInfo.get("game_mode").getAsString().equals("bingo");
		if (!showBingoPage && currentPage == ProfileViewerPage.BINGO) currentPage = ProfileViewerPage.BASIC;

		// No blur call here: vanilla's Screen#extractBackground (run just before this method) already blurs the game
		// world behind the window, and blurring a second time in one frame throws "Can only blur once per frame".

		// Panel background.
		graphics.fill(guiLeft, guiTop, guiLeft + sizeX, guiTop + sizeY, 0x80000000);

		renderTabs(graphics, mouseX, mouseY, false);

		drawWindowBackground(graphics);

		// The selected tab goes over the window so it joins the panel instead of sitting behind its top border.
		renderTabs(graphics, mouseX, mouseY, true);

		if (page != ProfileViewerPage.LOADING) {
			if (profile != null) {
				graphics.fill(guiLeft, guiTop + sizeY + 3, guiLeft + 100, guiTop + sizeY + 23, 0x80000000);
				RenderUtils.drawTexturedRect(graphics, pv_dropdown, guiLeft, guiTop + sizeY + 3, 100, 20, 0, 100 / 200f, 0, 20 / 185f);
				RenderUtils.drawStringCenteredScaledMaxWidth(
					graphics,
					profileId == null ? "" : profileId,
					this.minecraft.font,
					guiLeft + 50,
					guiTop + sizeY + 3 + 10,
					true,
					90,
					0x3FE0D0
				);

				String gameMode = currProfileInfo != null && currProfileInfo.has("game_mode")
					? currProfileInfo.get("game_mode").getAsString()
					: null;
				Identifier modeIcon = switch (gameMode == null ? "" : gameMode) {
					case "ironman" -> pv_ironman;
					case "bingo" -> pv_bingo;
					case "island" -> pv_stranded;
					default -> gameMode != null ? pv_unknown : null;
				};
				if (modeIcon != null) {
					RenderUtils.drawTexturedRect(graphics, modeIcon, guiLeft - 16 - 5, guiTop + sizeY + 5, 16, 16);
				}

				// "Open in Skycrypt" button - click handling (opens https://sky.shiiyu.moe/... via
				// ConfirmLinkScreen, same as a vanilla chat link) lives in mouseClicked below.
				graphics.fill(
					guiLeft + 106, guiTop + sizeY + 3, guiLeft + 106 + 100, guiTop + sizeY + 23, 0x80000000
				);
				RenderUtils.drawTexturedRect(
					graphics, pv_dropdown, guiLeft + 106, guiTop + sizeY + 3, 100, 20, 0, 100 / 200f, 0, 20 / 185f
				);
				RenderUtils.drawStringCenteredScaledMaxWidth(
					graphics,
					"Open in Skycrypt",
					this.minecraft.font,
					guiLeft + 50 + 106,
					guiTop + sizeY + 3 + 10,
					true,
					90,
					0x3FE0D0
				);
			}
		}

		if (pages.containsKey(page)) {
			// A page tripping over unexpected API data shouldn't take the whole game down: show an error in the
			// panel instead, and log the stack trace once per page+error.
			try {
				pages.get(page).drawPage(graphics, mouseX, mouseY, partialTicks);
			} catch (RuntimeException e) {
				if (loggedPageErrors.add(page + ":" + e)) {
					NotEnoughUpdates.LOGGER.error("Profile viewer page {} failed to render", page, e);
				}
				RenderUtils.drawStringCentered(
					graphics, ChatFormatting.RED + "This page hit an error (details in the game log).",
					this.minecraft.font, guiLeft + sizeX / 2f, guiTop + 101, true, 0
				);
			}
		} else {
			switch (page) {
				case LOADING -> RenderUtils.drawStringCentered(
					graphics, ChatFormatting.YELLOW + "Loading player profiles...", this.minecraft.font,
					guiLeft + sizeX / 2f, guiTop + 101, true, 0
				);
				case INVALID_NAME -> {
					String skyblockError = profile != null ? profile.getSkyblockProfilesError() : null;
					String message = skyblockError != null
						? "Hypixel API error: " + skyblockError
						: "Invalid name or API is down!";
					RenderUtils.drawStringCentered(
						graphics, ChatFormatting.RED + message, this.minecraft.font,
						guiLeft + sizeX / 2f, guiTop + 101, true, 0
					);
				}
				case NO_SKYBLOCK -> RenderUtils.drawStringCentered(
					graphics, ChatFormatting.RED + "No skyblock data found!", this.minecraft.font,
					guiLeft + sizeX / 2f, guiTop + 101, true, 0
				);
				default -> {
				}
			}
		}

		lastTime = currentTime;

		super.extractRenderState(graphics, mouseX, mouseY, partialTicks); // draws the player-name EditBox widget

		if (tooltipToDisplay != null) {
			List<String> grayTooltip = new ArrayList<>(tooltipToDisplay.size());
			for (String line : tooltipToDisplay) {
				grayTooltip.add(ChatFormatting.GRAY + line);
			}
			RenderUtils.drawHoveringText(graphics, grayTooltip, mouseX, mouseY);
			tooltipToDisplay = null;
		}
	}

	/**
	 * Draws {@code pv_bg}, leaving out its top border under the selected tab. NEU did this with the depth buffer
	 * (the selected tab was drawn first at a higher z). The selected tab's bottom rows are translucent, so drawing
	 * it over the border can't hide the line.
	 */
	private void drawWindowBackground(GuiGraphicsExtractor graphics) {
		int pressedIndex = visibleTabs().indexOf(currentPage);
		if (pressedIndex < 0) {
			RenderUtils.drawTexturedRect(graphics, pv_bg, guiLeft, guiTop, sizeX, sizeY);
			return;
		}

		int border = 4; // the selected tab is 32px tall from guiTop - 28, so it covers the window's top 4 rows
		int gapStart = Math.min(pressedIndex * 28, sizeX);
		int gapEnd = Math.min(gapStart + 28, sizeX);
		float v = border / (float) sizeY;
		if (gapStart > 0) {
			RenderUtils.drawTexturedRect(graphics, pv_bg, guiLeft, guiTop, gapStart, border,
				0, gapStart / (float) sizeX, 0, v);
		}
		if (gapEnd < sizeX) {
			RenderUtils.drawTexturedRect(graphics, pv_bg, guiLeft + gapEnd, guiTop, sizeX - gapEnd, border,
				gapEnd / (float) sizeX, 1, 0, v);
		}
		RenderUtils.drawTexturedRect(graphics, pv_bg, guiLeft, guiTop + border, sizeX, sizeY - border, 0, 1, v, 1);
	}

	private List<ProfileViewerPage> visibleTabs() {
		List<ProfileViewerPage> tabs = new ArrayList<>();
		for (ProfileViewerPage p : ProfileViewerPage.values()) {
			if (p.stack == null) continue;
			if (p == ProfileViewerPage.BINGO && !showBingoPage) continue;
			tabs.add(p);
		}
		return tabs;
	}

	private void renderTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean renderPressed) {
		List<ProfileViewerPage> tabs = visibleTabs();
		for (int i = 0; i < tabs.size(); i++) {
			boolean pressed = tabs.get(i) == currentPage;
			if (pressed == renderPressed) renderTab(graphics, tabs.get(i).stack, i, pressed);
		}
		if (!renderPressed && currentPage != ProfileViewerPage.LOADING && currentPage != ProfileViewerPage.INVALID_NAME) {
			for (int i = 0; i < tabs.size(); i++) {
				int x = guiLeft + i * 28;
				int y = guiTop - 28;
				if (Utils.isWithinRect(mouseX, mouseY, x, y, 28, 32)) {
					tooltipToDisplay = Collections.singletonList(tabs.get(i).displayName);
				}
			}
		}
	}

	private void renderTab(GuiGraphicsExtractor graphics, ItemStack stack, int xIndex, boolean pressed) {
		int x = guiLeft + xIndex * 28;
		int y = guiTop - 28;

		float uMin = 0;
		float uMax = 28 / 256f;
		float vMin = 20 / 256f;
		float vMax = 51 / 256f;
		if (pressed) {
			vMin = 52 / 256f;
			vMax = 84 / 256f;
			if (xIndex != 0) {
				uMin = 28 / 256f;
				uMax = 56 / 256f;
			}
			graphics.fill(x + 2, y + 2, x + 28 - 2, y + 28, 0x80000000);
		} else {
			graphics.fill(x + 2, y + 4, x + 28 - 2, y + 28, 0x80000000);
		}

		RenderUtils.drawTexturedRect(graphics, pv_elements, x, y, 28, pressed ? 32 : 31, uMin, uMax, vMin, vMax);
		RenderUtils.drawItemStack(graphics, stack, x + 6, y + 9);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();

		if (currentPage != ProfileViewerPage.LOADING && currentPage != ProfileViewerPage.INVALID_NAME) {
			List<ProfileViewerPage> tabs = visibleTabs();
			for (int i = 0; i < tabs.size(); i++) {
				int x = guiLeft + i * 28;
				int y = guiTop - 28;
				if (Utils.isWithinRect(mouseX, mouseY, x, y, 28, 32)) {
					if (currentPage != tabs.get(i)) RenderUtils.playPressSound();
					currentPage = tabs.get(i);
					return true;
				}
			}
		}

		if (pages.containsKey(currentPage) && pages.get(currentPage).mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}

		if (
			profile != null &&
				Utils.isWithinRect(mouseX, mouseY, guiLeft + 106, guiTop + sizeY + 3, 100, 20)
		) {
			String url = "https://sky.shiiyu.moe/stats/" +
				java.net.URLEncoder.encode(initialPlayerName, java.nio.charset.StandardCharsets.UTF_8) +
				(profileId != null ? "/" + java.net.URLEncoder.encode(profileId, java.nio.charset.StandardCharsets.UTF_8) : "");
			net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, url);
			return true;
		}

		if (
			profile != null && !profile.getProfileNames().isEmpty() &&
				Utils.isWithinRect(mouseX, mouseY, guiLeft, guiTop + sizeY + 3, 100, 20)
		) {
			int profileNum = 0;
			for (int index = 0; index < profile.getProfileNames().size(); index++) {
				if (profile.getProfileNames().get(index).equals(profileId)) {
					profileNum = index;
					break;
				}
			}
			profileNum += event.button() == 0 ? 1 : -1;
			if (profileNum >= profile.getProfileNames().size()) profileNum = 0;
			if (profileNum < 0) profileNum = profile.getProfileNames().size() - 1;

			String newProfileId = profile.getProfileNames().get(profileNum);
			if (profileId != null && !profileId.equals(newProfileId)) {
				resetCache();
			}
			profileId = newProfileId;
			return true;
		}

		profileDropdownSelected = false;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (pages.containsKey(currentPage) && pages.get(currentPage).keyPressed(event)) {
			return true;
		}
		if (playerNameTextField.isFocused() && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
			currentPage = ProfileViewerPage.LOADING;
			NotEnoughUpdates.INSTANCE.getProfileViewer().getProfileByName(
				playerNameTextField.getValue(),
				newProfile -> {
					if (newProfile != null) newProfile.resetCache();
					this.minecraft.execute(() -> this.minecraft.setScreen(new GuiProfileViewer(newProfile)));
				}
			);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (pages.containsKey(currentPage) && pages.get(currentPage).charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pages.containsKey(currentPage) && pages.get(currentPage).mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean handled = super.mouseReleased(event);
		if (pages.containsKey(currentPage)) {
			pages.get(currentPage).mouseReleased(event.x(), event.y(), event.button());
		}
		return handled;
	}

	public void renderXpBar(
		GuiGraphicsExtractor graphics,
		String skillName,
		ItemStack stack,
		int x,
		int y,
		int xSize,
		ProfileViewer.Level levelObj,
		int mouseX,
		int mouseY
	) {
		float level = levelObj.level;
		int levelFloored = (int) Math.floor(level);

		RenderUtils.renderAlignedString(graphics, skillName, ChatFormatting.WHITE.toString() + levelFloored, x + 14, y - 4, xSize - 20);

		if (levelObj.maxed) {
			renderGoldBar(graphics, x, y + 6, xSize);
		} else {
			renderBar(graphics, x, y + 6, xSize, level % 1);
		}

		if (Utils.isWithinRect(mouseX, mouseY, x, y - 4, 120, 17)) {
			String levelStr;
			if (levelObj.maxed) {
				levelStr = ChatFormatting.GOLD + "MAXED!";
			} else {
				int maxXp = (int) levelObj.maxXpForLevel;
				levelStr =
					ChatFormatting.DARK_PURPLE +
						io.github.moulberry.notenoughupdates.core.util.StringUtils.shortNumberFormat(Math.round((level % 1) * maxXp)) +
						"/" +
						io.github.moulberry.notenoughupdates.core.util.StringUtils.shortNumberFormat(maxXp);
			}
			tooltipToDisplay = Utils.createList(levelStr);
		}

		RenderUtils.drawSkillIcon(graphics, stack, x, y - 6);
	}

	/**
	 * A moving rainbow/chroma gradient for maxed skill bars, matching the style common Hypixel SkyBlock mods use
	 * for maxed stats. Drawn as a series of narrow vertical slices, each a different hue computed from its
	 * position along the bar plus a time offset so the gradient slowly scrolls - cheap plain
	 * {@code graphics.fill} rectangles rather than a custom shader (see class-level TODO), and at this bar's
	 * small size (~100px wide, 5px tall) the slices aren't visually distinguishable from a smooth gradient.
	 */
	public void renderGoldBar(GuiGraphicsExtractor graphics, float x, float y, float xSize) {
		int left = Math.round(x);
		int width = Math.max(1, Math.round(xSize));
		int top = Math.round(y);

		// The empty XP bar gives the rounded outline; the rainbow fills its inside (1px in from each edge).
		drawXpBarSprite(graphics, XP_BAR_BACKGROUND, left, top, width, 0, width);

		long period = 3000; // ms for the gradient to scroll through one full hue cycle
		float timeOffset = (currentTime % period) / (float) period;

		int sliceWidth = 2;
		for (int sx = 1; sx < width - 1; sx += sliceWidth) {
			float hue = ((sx / (float) width) + timeOffset) % 1f;
			int rgb = java.awt.Color.HSBtoRGB(hue, 0.7f, 1f) & 0xFFFFFF;
			int sliceLeft = left + sx;
			int sliceRight = Math.min(left + sx + sliceWidth, left + width - 1);
			graphics.fill(sliceLeft, top + 1, sliceRight, top + 4, 0xFF000000 | rgb);
		}
	}

	private static final Identifier XP_BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
	private static final Identifier XP_BAR_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");
	private static final int XP_BAR_SPRITE_WIDTH = 182;

	/** Like the original: the vanilla XP bar, in 5% steps. */
	public void renderBar(GuiGraphicsExtractor graphics, float x, float y, float xSize, float completed) {
		completed = Math.round(Math.max(0, Math.min(1, completed)) / 0.05f) * 0.05f;
		int left = Math.round(x);
		int top = Math.round(y);
		int width = Math.round(xSize);
		drawXpBarSprite(graphics, XP_BAR_BACKGROUND, left, top, width, 0, width);
		drawXpBarSprite(graphics, XP_BAR_PROGRESS, left, top, width, 0, Math.round(width * completed));
	}

	/**
	 * Draws bar pixels [from, to) of a {@code width}-wide bar using a 182px XP bar sprite. As in the original, the
	 * left half comes from the sprite's left end and the right half from its right end, so a shorter bar keeps
	 * both rounded caps instead of being stretched or cut off.
	 */
	private static void drawXpBarSprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int from, int to) {
		if (to <= from) return;
		if (width > XP_BAR_SPRITE_WIDTH) {
			// Wider than the sprite itself: just stretch the covered fraction.
			int u = from * XP_BAR_SPRITE_WIDTH / width;
			int uEnd = to * XP_BAR_SPRITE_WIDTH / width;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, XP_BAR_SPRITE_WIDTH, 5, u, 0, x + from, y, Math.max(1, uEnd - u), 5);
			return;
		}
		int half = width / 2;
		if (from < Math.min(to, half)) {
			int end = Math.min(to, half);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, XP_BAR_SPRITE_WIDTH, 5, from, 0, x + from, y, end - from, 5);
		}
		int start = Math.max(from, half);
		if (start < to) {
			graphics.blitSprite(
				RenderPipelines.GUI_TEXTURED, sprite, XP_BAR_SPRITE_WIDTH, 5,
				XP_BAR_SPRITE_WIDTH - width + start, 0, x + start, y, to - start, 5
			);
		}
	}

	public void resetCache() {
		pages.values().forEach(GuiProfileViewerPage::resetCache);
	}

	public enum ProfileViewerPage {
		LOADING((Item) null, null, null),
		INVALID_NAME((Item) null, null, null),
		NO_SKYBLOCK((Item) null, null, null),
		BASIC(Items.PAPER, "Your Skills", ChatFormatting.BLUE),
		DUNGEON(Blocks.DEAD_BUSH.asItem(), "Dungeoneering", ChatFormatting.YELLOW),
		EXTRA(Items.BOOK, "Profile Stats", ChatFormatting.GRAY),
		INVENTORIES(Blocks.ENDER_CHEST.asItem(), "Storage", ChatFormatting.AQUA),
		COLLECTIONS(Items.PAINTING, "Collections", ChatFormatting.GOLD),
		PETS(Items.BONE, "Pets", ChatFormatting.GREEN),
		MINING(Items.IRON_PICKAXE, "Heart of the Mountain", ChatFormatting.DARK_PURPLE),
		BINGO(Items.FILLED_MAP, "Bingo", ChatFormatting.DARK_RED),
		TROPHY_FISH(Items.FISHING_ROD, "Trophy Fish", ChatFormatting.DARK_AQUA),
		BESTIARY(Items.IRON_SWORD, "Bestiary", ChatFormatting.RED),
		// Tabs from here down don't exist in NEU; they follow SkyBlockPv's tab set and are placeholders for now.
		// Portions of this code are from the SkyBlockPv mod.
		FARMING(Items.WHEAT, "Farming", ChatFormatting.YELLOW),
		// Fig Log's in-game model is stripped spruce log.
		FORAGING(Items.STRIPPED_SPRUCE_LOG, "Foraging", ChatFormatting.DARK_GREEN),
		LOADOUTS(Blocks.BARREL.asItem(), "Loadouts", ChatFormatting.WHITE),
		MUSEUM(Blocks.GOLD_BLOCK.asItem(), "Museum", ChatFormatting.GOLD),
		// Skull textures from SkyBlockPv's repo (meowdding-repo, pv/skull_textures.json).
		CHOCOLATE_FACTORY(
			Utils.createSkull(
				"",
				"e4e1bf9730eb444abb28b18817d43f3e",
				"ewogICJ0aW1lc3RhbXAiIDogMTcxOTkzOTQxMzQ3NCwKICAicHJvZmlsZUlkIiA6ICJlNGUxYmY5NzMwZWI0NDRhYmIyOGIxODgxN2Q0M2YzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSU1PR0FNRVMwMzIxIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzlhODE1Mzk4ZTdkYTg5YjFiYzA4ZjY0NmNhZmM4ZTdiODEzZGEwYmUwZWVjMGNjZTZkM2VmZjUyMDc4MDEwMjYiCiAgICB9CiAgfQp9"
			),
			"Chocolate Factory",
			ChatFormatting.GOLD
		),
		RIFT(
			Utils.createSkull(
				"",
				"d12b997eb6a4484982f415e2571e6f84",
				"ewogICJ0aW1lc3RhbXAiIDogMTY4MTkxMjM5OTYxNCwKICAicHJvZmlsZUlkIiA6ICJkMTJiOTk3ZWI2YTQ0ODQ5ODJmNDE1ZTI1NzFlNmY4NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUd2lybGJlbGwiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjI2MTkyNjA5ZDZjNDZhZGU3M2U4MDdmYzQwZGJjM2ExYTFhZmJiNDU2YWUxNjU3ODViMGZlODM0ZGQxY2I1NyIKICAgIH0KICB9Cn0="
			),
			"Rift",
			ChatFormatting.DARK_PURPLE
		);

		public final ItemStack stack;
		public final String displayName;

		ProfileViewerPage(Item item, String name, ChatFormatting colour) {
			this(item == null ? null : new ItemStack(item), name, colour);
		}

		ProfileViewerPage(ItemStack icon, String name, ChatFormatting colour) {
			if (icon == null) {
				stack = null;
				displayName = null;
			} else {
				stack = icon;
				displayName = name;
				stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(colour));
			}
		}

		public Optional<ItemStack> getItem() {
			return Optional.ofNullable(stack);
		}
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
}
