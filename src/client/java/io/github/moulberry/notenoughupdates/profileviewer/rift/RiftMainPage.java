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

// Portions of this code are from the SkyBlockPv mod.

package io.github.moulberry.notenoughupdates.profileviewer.rift;

import io.github.moulberry.notenoughupdates.profileviewer.VanillaItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewerPage;
import io.github.moulberry.notenoughupdates.profileviewer.PvData;
import io.github.moulberry.notenoughupdates.profileviewer.PvUi;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main sub-page of the rift tab, as SkyBlockPv's {@code MainRiftScreen}: motes, visits, Enigma souls, Montezuma's
 * cats, the Wither Cage eyes and the timecharms. Player data is {@code rift}, {@code player_stats.rift} and
 * {@code currencies.motes_purse}; the cats, eyes and timecharms come from SkyBlockPv's repo ({@code rift.json}).
 */
public class RiftMainPage implements GuiProfileViewerPage {

	private static final int ENIGMA_SOULS = 52;
	private static final int GRUBBER_STACKS = 5;
	private static final int GAP = 12;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.US);

	private record Line(String text, List<String> tooltip) {
	}

	private final GuiProfileViewer instance;
	private final ItemStack grayDye = new ItemStack(VanillaItems.GRAY_DYE);

	public RiftMainPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	static JsonObject repo() {
		return PvData.bundled("rift");
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		Font font = instance.getFont();
		JsonObject profileInfo = GuiProfileViewer.getProfile().getProfileInformation(GuiProfileViewer.getProfileId());
		if (profileInfo == null) return;
		if (!(profileInfo.get("rift") instanceof JsonObject rift)) {
			PvUi.centred(graphics, font, instance, "§cThis profile hasn't been to the Rift yet!");
			return;
		}

		List<Line> information = information(profileInfo, rift);
		int infoWidth = font.width("Information") + 8;
		for (Line line : information) infoWidth = Math.max(infoWidth, font.width(line.text()) + 8);
		JsonArray trophies = repo().get("trophies") instanceof JsonArray array ? array : new JsonArray();
		int charmsWidth = trophies.size() * 18;
		int height = PvUi.linesHeight(information.size());
		int x = GuiProfileViewer.getGuiLeft() + (instance.sizeX - infoWidth - GAP - charmsWidth) / 2;
		int y = GuiProfileViewer.getGuiTop() + (instance.sizeY - height) / 2;

		PvUi.title(graphics, font, "Information", x, y, infoWidth);
		int rowY = y + PvUi.TITLE + 3;
		for (Line line : information) {
			RenderUtils.text(graphics, font, line.text(), x + 4, rowY, 0xFFFFFF, true);
			if (line.tooltip() != null && !line.tooltip().isEmpty()
				&& Utils.isWithinRect(mouseX, mouseY, x + 4, rowY - 1, font.width(line.text()), PvUi.ROW)) {
				instance.tooltipToDisplay = line.tooltip();
			}
			rowY += PvUi.ROW;
		}

		x += infoWidth + GAP;
		PvUi.title(graphics, font, "Timecharms", x, y, charmsWidth);
		JsonArray secured = Utils.getElement(rift, "gallery.secured_trophies") instanceof JsonArray array ? array : new JsonArray();
		for (int i = 0; i < trophies.size(); i++) {
			String id = Utils.getElementAsString(Utils.getElement(trophies.get(i), "id"), "");
			String itemId = "RIFT_TROPHY_" + id.toUpperCase(Locale.ROOT);
			JsonObject found = null;
			for (JsonElement element : secured) {
				if (element instanceof JsonObject trophy && id.equals(Utils.getElementAsString(trophy.get("type"), ""))) found = trophy;
			}
			if (!PvUi.slot(graphics, found != null ? PvData.item(itemId) : grayDye, x + i * 18, y + PvUi.TITLE + 3, mouseX, mouseY)) continue;
			List<String> tooltip = new ArrayList<>();
			tooltip.add(PvData.repoItem(itemId) != null ? PvData.itemName(itemId)
				: "§d" + Utils.getElementAsString(Utils.getElement(trophies.get(i), "name"), id));
			tooltip.addAll(PvData.itemLore(itemId));
			if (found != null) {
				tooltip.add("");
				tooltip.add("§7Found after §a" + PvData.asLong(found.get("visits"), 0) + " §7visits");
				tooltip.add("§7Timestamp: §a" + DATE.format(Instant.ofEpochMilli(PvData.asLong(found.get("timestamp"), 0)).atZone(ZoneId.systemDefault())));
			} else {
				tooltip.add("");
				tooltip.add("§cNot found yet!");
			}
			instance.tooltipToDisplay = tooltip;
		}
	}

	private static List<Line> information(JsonObject profileInfo, JsonObject rift) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line("§7Motes: §d" + PvData.format(PvData.getLong(profileInfo, "currencies.motes_purse")), null));
		lines.add(new Line("§7Lifetime Motes: §d" + PvData.format(PvData.getLong(profileInfo, "player_stats.rift.lifetime_motes_earned")), null));
		lines.add(new Line("§7Visits: §d" + PvData.format(PvData.getLong(profileInfo, "player_stats.rift.visits")), null));
		lines.add(new Line("§7Time sitting with Ävaeìkx: §5" + duration(PvData.getLong(rift, "village_plaza.lonely.seconds_sitting")), null));

		int souls = Utils.getElement(rift, "enigma.found_souls") instanceof JsonArray array ? array.size() : 0;
		lines.add(new Line("§7Enigma Souls: " + (souls >= ENIGMA_SOULS ? "§5" : "§d") + souls + "§5/" + ENIGMA_SOULS, null));
		lines.add(progress("Found Cats", "Missing Cats", strings(Utils.getElement(rift, "dead_cats.found_cats")), repo().get("montezuma")));
		lines.add(progress("Unlocked Eyes", "Locked Eyes", strings(Utils.getElement(rift, "wither_cage.killed_eyes")), repo().get("eyes")));

		long grubber = PvData.getLong(rift, "castle.grubber_stacks");
		lines.add(new Line("§7Grubber Stacks: " + (grubber >= GRUBBER_STACKS ? "§5" : "§d") + grubber + "§5/" + GRUBBER_STACKS, null));
		return lines;
	}

	/** "Found x/y", with the ones still missing in the tooltip. */
	private static Line progress(String name, String missingName, Set<String> have, JsonElement all) {
		List<String> missing = new ArrayList<>();
		int total = 0;
		if (all instanceof JsonArray array) {
			total = array.size();
			for (JsonElement element : array) {
				if (!have.contains(element.getAsString())) missing.add("§5" + PvData.titleCase(element.getAsString()));
			}
		}
		int count = total - missing.size();
		List<String> tooltip = null;
		if (!missing.isEmpty()) {
			tooltip = new ArrayList<>();
			tooltip.add("§7" + missingName + " (" + missing.size() + "):");
			tooltip.addAll(missing);
		}
		return new Line("§7" + name + ": §5" + count + "/" + total, tooltip);
	}

	private static Set<String> strings(JsonElement element) {
		Set<String> set = new HashSet<>();
		if (element instanceof JsonArray array) for (JsonElement each : array) set.add(each.getAsString());
		return set;
	}

	private static String duration(long seconds) {
		long hours = seconds / 3600;
		long minutes = seconds / 60 % 60;
		StringBuilder text = new StringBuilder();
		if (hours > 0) text.append(hours).append("h, ");
		if (hours > 0 || minutes > 0) text.append(minutes).append("min, ");
		return text.append(seconds % 60).append("s").toString();
	}
}
