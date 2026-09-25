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

import io.github.moulberry.notenoughupdates.client.McCompat;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer.ProfileViewerPage;
import io.github.moulberry.notenoughupdates.util.BpvConfig;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The settings screen ({@code /bpv} or the Settings button), styled like the old NEU config menu: a category
 * list on the left, option cards on the right, NEU's toggle switches. Options are saved to {@link BpvConfig}
 * as soon as they change. Hidden tabs still load, since other pages (Level) read their data.
 */
public class SettingsScreen extends Screen {
	private static final int CARD_HEIGHT = 45;
	private static final int CARD_GAP = 5;
	private static final int TOGGLE_WIDTH = 48;
	private static final int TOGGLE_HEIGHT = 14;

	private static final Identifier BAR = Identifier.parse("betterpv:core/bar.png");
	private static final Identifier BAR_1 = Identifier.parse("betterpv:core/bar_1.png");
	private static final Identifier BAR_2 = Identifier.parse("betterpv:core/bar_2.png");
	private static final Identifier BAR_3 = Identifier.parse("betterpv:core/bar_3.png");
	private static final Identifier BAR_ON = Identifier.parse("betterpv:core/bar_on.png");
	private static final Identifier KNOB_OFF = Identifier.parse("betterpv:core/toggle_off.png");
	private static final Identifier KNOB_1 = Identifier.parse("betterpv:core/toggle_1.png");
	private static final Identifier KNOB_2 = Identifier.parse("betterpv:core/toggle_2.png");
	private static final Identifier KNOB_3 = Identifier.parse("betterpv:core/toggle_3.png");
	private static final Identifier KNOB_ON = Identifier.parse("betterpv:core/toggle_on.png");

	private final Screen parent;
	private final List<Category> categories = new ArrayList<>();
	private int selected = 0;
	private int scroll = 0;

	// Layout of the last frame, shared with the click handlers.
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int innerLeft;
	private int innerRight;
	private int innerTop;
	private int innerBottom;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Better PV Settings"));
		this.parent = parent;
		buildCategories();
	}

	// ---- options ----

	private abstract static class Option {
		final String name;
		final String desc;

		Option(String name, String desc) {
			this.name = name;
			this.desc = desc;
		}

		abstract void renderControl(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int mouseX, int mouseY);

		abstract boolean click(int x, int y, int width, int mouseX, int mouseY);
	}

	private static class Toggle extends Option {
		private final BooleanSupplier value;
		private final Consumer<Boolean> setter;
		private long changedAt = -1;

		Toggle(String name, String desc, BooleanSupplier value, Consumer<Boolean> setter) {
			super(name, desc);
			this.value = value;
			this.setter = setter;
		}

		private static int knobX(int x, int y, int width) {
			return x + width / 6 - 24;
		}

		private static int knobY(int y) {
			return y + CARD_HEIGHT - 7 - TOGGLE_HEIGHT;
		}

		@Override
		void renderControl(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
			boolean on = value.getAsBoolean();
			int frame = on ? 36 : 0;
			if (changedAt >= 0) {
				int elapsed = (int) Math.min(36, (System.currentTimeMillis() - changedAt) / 10);
				frame = on ? elapsed : 36 - elapsed;
				if (elapsed >= 36) changedAt = -1;
			}
			Identifier knob = frame < 3 ? KNOB_OFF : frame < 13 ? KNOB_1 : frame < 23 ? KNOB_2 : frame < 33 ? KNOB_3 : KNOB_ON;
			Identifier bar = frame < 3 ? BAR : frame < 13 ? BAR_1 : frame < 23 ? BAR_2 : frame < 33 ? BAR_3 : BAR_ON;
			int bx = knobX(x, y, width);
			int by = knobY(y);
			RenderUtils.drawTexturedRect(graphics, bar, bx, by, TOGGLE_WIDTH, TOGGLE_HEIGHT);
			RenderUtils.drawTexturedRect(graphics, knob, bx + frame, by, 12, TOGGLE_HEIGHT);
		}

		@Override
		boolean click(int x, int y, int width, int mouseX, int mouseY) {
			int bx = knobX(x, y, width);
			int by = knobY(y);
			if (mouseX < bx - 10 || mouseX > bx + TOGGLE_WIDTH + 10 || mouseY < by - 10 || mouseY > by + TOGGLE_HEIGHT + 10) {
				return false;
			}
			setter.accept(!value.getAsBoolean());
			changedAt = System.currentTimeMillis();
			RenderUtils.playPressSound();
			return true;
		}
	}

	/** A box showing the current choice; clicking it moves on to the next one. */
	private static class Cycle extends Option {
		private final Supplier<String> label;
		private final Runnable next;

		Cycle(String name, String desc, Supplier<String> label, Runnable next) {
			super(name, desc);
			this.label = label;
			this.next = next;
		}

		private static int boxWidth(int width) {
			return Math.min(90, width / 3 - 10);
		}

		@Override
		void renderControl(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
			int bw = boxWidth(width);
			int bx = x + width / 6 - bw / 2;
			int by = y + CARD_HEIGHT - 7 - TOGGLE_HEIGHT;
			boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + TOGGLE_HEIGHT;
			drawBox(graphics, bx, by, bw, TOGGLE_HEIGHT, hover ? 0xff303036 : 0xff202026);
			RenderUtils.drawStringCenteredScaledMaxWidth(
				graphics, label.get(), font, bx + bw / 2f, by + TOGGLE_HEIGHT / 2f + 1, true, bw - 6, 0xffff55
			);
		}

		@Override
		boolean click(int x, int y, int width, int mouseX, int mouseY) {
			int bw = boxWidth(width);
			int bx = x + width / 6 - bw / 2;
			int by = y + CARD_HEIGHT - 7 - TOGGLE_HEIGHT;
			if (mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + TOGGLE_HEIGHT) return false;
			next.run();
			RenderUtils.playPressSound();
			return true;
		}
	}

	private static class Category {
		final String name;
		final String desc;
		final List<Option> options = new ArrayList<>();

		Category(String name, String desc) {
			this.name = name;
			this.desc = desc;
		}
	}

	private void buildCategories() {
		Category general = new Category("General", "How the profile viewer opens and what it shows.");
		general.options.add(new Cycle(
			"Opening Tab",
			"The tab the viewer opens on when started from /pv or a chat click. Click to change.",
			SettingsScreen::openingTabLabel,
			SettingsScreen::cycleOpeningTab
		));
		general.options.add(new Toggle(
			"Short Numbers",
			"Show big numbers abbreviated, like 12.3m instead of 12,345,678.",
			BpvConfig::isShortNumbers, BpvConfig::setShortNumbers
		));
		general.options.add(new Toggle(
			"Hide Net Worth",
			"Don't show the net worth (or its breakdown) on the Your Skills tab.",
			BpvConfig::isHideNetWorth, BpvConfig::setHideNetWorth
		));
		general.options.add(new Toggle(
			"Chat Right-Click",
			"Right-click a player's name in chat, while on SkyBlock, to open their profile.",
			BpvConfig::isChatRightClick, BpvConfig::setChatRightClick
		));
		general.options.add(new Toggle(
			"Update Notifications",
			"Tell me in chat when a newer Better PV release is out (checks GitHub once per launch).",
			BpvConfig::isUpdateCheck, BpvConfig::setUpdateCheck
		));
		categories.add(general);

		Category tabs = new Category("Tabs", "Turn tabs on or off. Your Skills is always on.");
		for (ProfileViewerPage page : ProfileViewerPage.values()) {
			if (page.stack == null || page == ProfileViewerPage.BASIC) continue;
			String tab = page.name();
			tabs.options.add(new Toggle(
				page.displayName,
				"Show the " + page.displayName + " tab in the profile viewer.",
				() -> !BpvConfig.isTabHidden(tab),
				on -> {
					BpvConfig.setTabHidden(tab, !on);
					if (!on && BpvConfig.getOpeningTab().equals(tab)) BpvConfig.setOpeningTab("LAST");
				}
			));
		}
		categories.add(tabs);
	}

	private static String openingTabLabel() {
		String tab = BpvConfig.getOpeningTab();
		try {
			if (!tab.equals("LAST")) return ProfileViewerPage.valueOf(tab).displayName;
		} catch (IllegalArgumentException ignored) {
		}
		return "Last used";
	}

	/** Last used -> every enabled tab in order -> back to last used. */
	private static void cycleOpeningTab() {
		List<String> options = new ArrayList<>();
		options.add("LAST");
		for (ProfileViewerPage page : ProfileViewerPage.values()) {
			if (GuiProfileViewer.isTabEnabled(page)) options.add(page.name());
		}
		int next = (options.indexOf(BpvConfig.getOpeningTab()) + 1) % options.size();
		BpvConfig.setOpeningTab(options.get(next));
	}

	// ---- drawing ----

	/** NEU's {@code drawFloatingRectDark}. */
	private static void drawFloatingRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean shadow) {
		drawBox(graphics, x, y, width, height, 0xf0202026);
		if (shadow) {
			graphics.fill(x + width, y + 2, x + width + 2, y + height + 2, 0x70000000);
			graphics.fill(x + 2, y + height, x + width, y + height + 2, 0x70000000);
		}
	}

	private static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int main) {
		graphics.fill(x, y, x + 1, y + height, 0xff303036);
		graphics.fill(x + 1, y, x + width, y + 1, 0xff303036);
		graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xff101016);
		graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, 0xff101016);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, main);
	}

	private static void drawInset(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int lightSide) {
		graphics.fill(left, top, left + 1, bottom, 0xff08080E);
		graphics.fill(left + 1, top, right, top + 1, 0xff08080E);
		graphics.fill(right - 1, top + 1, right, bottom, lightSide);
		graphics.fill(left + 1, bottom - 1, right - 1, bottom, lightSide);
		graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0x6008080E);
	}

	private static int text(int rgb) {
		return RenderUtils.opaque(rgb);
	}

	private int cardWidth() {
		return innerRight - innerLeft - 20;
	}

	private int cardX() {
		return (innerLeft + innerRight - cardWidth()) / 2 - 5;
	}

	private int contentHeight() {
		return categories.get(selected).options.size() * (CARD_HEIGHT + CARD_GAP) + 5;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - (innerBottom - innerTop - 2));
	}

	private void layout() {
		double scale = this.minecraft.getWindow().getGuiScale();
		int xSize = Math.min(this.width - (int) (100 / scale), 500);
		int ySize = Math.min(this.height - (int) (100 / scale), 400);
		panelWidth = xSize;
		panelHeight = ySize;
		panelX = (this.width - xSize) / 2;
		panelY = (this.height - ySize) / 2;
		int padding = 20 / Math.max(2, (int) scale);
		innerLeft = panelX + 149 + padding;
		innerRight = panelX + xSize - 5 - padding;
		innerTop = panelY + 49 + padding;
		innerBottom = panelY + ySize - 5 - padding;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		layout();
		int xSize = panelWidth;
		int ySize = panelHeight;
		int x = panelX;
		int y = panelY;
		Font font = this.font;

		graphics.fill(0, 0, this.width, this.height, 0x60101010);
		drawFloatingRect(graphics, x, y, xSize, ySize, true);
		drawFloatingRect(graphics, x + 5, y + 5, xSize - 10, 20, false);
		RenderUtils.drawStringCenteredScaledMaxWidth(
			graphics, "Better PV by " + ChatFormatting.DARK_PURPLE + "daweapon", font, x + xSize / 2f, y + 15, false, 200, 0xa0a0a0
		);

		// Category list.
		drawFloatingRect(graphics, x + 4, y + 29, 140, ySize - 34, false);
		int padding = innerLeft - (x + 149);
		int catLeft = x + 4 + padding;
		int catRight = x + 144 - padding;
		drawInset(graphics, catLeft, innerTop, catRight, innerBottom, 0xff28282E);
		for (int i = 0; i < categories.size(); i++) {
			String name = categories.get(i).name;
			name = i == selected ? ChatFormatting.DARK_AQUA.toString() + ChatFormatting.UNDERLINE + name : ChatFormatting.GRAY + name;
			RenderUtils.drawStringCenteredScaledMaxWidth(graphics, name, font, x + 75, y + 70 + i * 15, false, 100, 0xffffff);
		}
		RenderUtils.drawStringCenteredScaledMaxWidth(graphics, "Categories", font, x + 75, y + 44, false, 120, 0xa368ef);

		// Options list.
		drawFloatingRect(graphics, x + 149, y + 29, xSize - 154, ySize - 34, false);
		Category category = categories.get(selected);
		graphics.text(font, category.desc, innerLeft + 5, y + 36, text(0xb0b0b0), true);
		drawInset(graphics, innerLeft, innerTop, innerRight, innerBottom, 0xff303036);

		graphics.enableScissor(innerLeft + 1, innerTop + 1, innerRight - 1, innerBottom - 1);
		int cardWidth = cardWidth();
		int cardX = cardX();
		for (int i = 0; i < category.options.size(); i++) {
			int cardY = innerTop + 5 + i * (CARD_HEIGHT + CARD_GAP) - scroll;
			if (cardY + CARD_HEIGHT < innerTop || cardY > innerBottom) continue;
			renderCard(graphics, font, category.options.get(i), cardX, cardY, cardWidth, mouseX, mouseY);
		}
		graphics.disableScissor();

		// Scroll bar.
		int content = contentHeight();
		float barSize = Math.min(1f, (innerBottom - innerTop - 2) / (float) content);
		float barStart = scroll / (float) content;
		int dist = innerBottom - innerTop - 12;
		graphics.fill(innerRight - 10, innerTop + 5, innerRight - 5, innerBottom - 5, 0xff101010);
		graphics.fill(
			innerRight - 9, innerTop + 6 + (int) (dist * barStart),
			innerRight - 6, innerTop + 6 + (int) (dist * Math.min(1f, barStart + barSize)), 0xff303030
		);
	}

	private void renderCard(GuiGraphicsExtractor graphics, Font font, Option option, int x, int y, int width, int mouseX, int mouseY) {
		drawFloatingRect(graphics, x, y, width, CARD_HEIGHT, true);
		RenderUtils.drawStringCenteredScaledMaxWidth(graphics, option.name, font, x + width / 6f, y + 13, true, width / 3 - 10, 0xc0c0c0);

		// Description: wrapped in the right two thirds, shrunk until it fits the card.
		float scale = 1;
		List<FormattedCharSequence> lines = font.split(Component.literal(option.desc), width * 2 / 3 - 10);
		float paragraph = 9 * lines.size() - 1;
		while (paragraph >= CARD_HEIGHT - 10 && scale > 0.4f) {
			scale -= 1 / 8f;
			lines = font.split(Component.literal(option.desc), (int) (width * 2 / 3 / scale - 10));
			paragraph = 9 * scale * lines.size() - scale;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 5 + width / 3f, y + CARD_HEIGHT / 2f - paragraph / 2);
		graphics.pose().scale(scale, scale);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), 0, i * 9, text(0xc0c0c0), false);
		}
		graphics.pose().popMatrix();

		option.renderControl(graphics, font, x, y, width, mouseX, mouseY);
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		if (event.button() != 0) return super.mouseClicked(event, doubleClick);

		// Category list.
		for (int i = 0; i < categories.size(); i++) {
			int centre = panelY + 70 + i * 15;
			if (mouseX >= panelX + 4 && mouseX < panelX + 144 && mouseY >= centre - 7 && mouseY <= centre + 7) {
				if (i != selected) RenderUtils.playPressSound();
				selected = i;
				scroll = 0;
				return true;
			}
		}

		// Scroll bar track: jump there.
		if (mouseX >= innerRight - 12 && mouseX <= innerRight - 3 && mouseY > innerTop + 6 && mouseY < innerBottom - 6) {
			float fraction = (mouseY - innerTop - 6) / (float) (innerBottom - innerTop - 12);
			scroll = Math.max(0, Math.min(maxScroll(), (int) (fraction * contentHeight()) - (innerBottom - innerTop) / 2));
			return true;
		}

		// Option cards.
		if (mouseX > innerLeft && mouseX < innerRight && mouseY > innerTop && mouseY < innerBottom) {
			Category category = categories.get(selected);
			for (int i = 0; i < category.options.size(); i++) {
				int cardY = innerTop + 5 + i * (CARD_HEIGHT + CARD_GAP) - scroll;
				if (category.options.get(i).click(cardX(), cardY, cardWidth(), mouseX, mouseY)) return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY > innerTop && mouseY < innerBottom) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * 30));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		McCompat.setScreen(this.minecraft, parent);
	}
}
