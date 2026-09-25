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

import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Port of the Forge 1.8.9 {@code CollectionsPage} ("Collections" tab: collection grid + minion tier grid, with a
 * category sidebar and paging).
 *
 * <p>TODO(fabric-port) — intentionally simplified vs. the original:
 * <ul>
 *   <li>Minion tier icons are rendered via {@code NEUManager#jsonToStack} against the repo's
 *   {@code <MINION>_GENERATOR_<tier>} entries (clamped to tier 1 for not-yet-unlocked minions so the base icon
 *   still shows); silently skipped (tier-completion background/roman-numeral text still shown) if the repo
 *   hasn't been synced or doesn't have that entry.</li>
 *   <li>The page-left/page-right arrow icons (drawn from the vanilla resource-pack-selector texture in the
 *   original) are simplified to plain "&lt;"/"&gt;" text in the same click regions.</li>
 * </ul>
 */
public class CollectionsPage implements GuiProfileViewerPage {

	private static final Identifier pv_cols = Identifier.parse("betterpv:pv_cols.png");
	private static final Identifier pv_elements = Identifier.parse("betterpv:pv_elements.png");
	private static final int COLLS_XCOUNT = 5;
	private static final int COLLS_YCOUNT = 4;
	private static final float COLLS_XPADDING = (190 - COLLS_XCOUNT * 20) / (float) (COLLS_XCOUNT + 1);
	private static final float COLLS_YPADDING = (202 - COLLS_YCOUNT * 20) / (float) (COLLS_YCOUNT + 1);
	private static final String[] romans = new String[] {
		"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
		"XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XIX", "XX",
	};
	private static final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

	private final GuiProfileViewer instance;
	private ItemStack selectedCollectionCategory = null;
	private int page = 0;
	private int maxPage = 0;
	/**
	 * Caches minion tier icons resolved via {@code NEUManager#jsonToStack}, keyed by
	 * {@code <MINION>_GENERATOR_<tier>}. Without this, every visible minion slot would call {@code jsonToStack}
	 * (which allocates a fresh {@code ItemStack}/{@code GameProfile} via {@code .copy()} on every cache hit)
	 * every single frame, defeating the GPU item-icon atlas's per-item caching and forcing a full re-bake of every
	 * skull icon every frame - see the identical fix/rationale in {@code InventoriesPage#resolvedIconCache}. A new
	 * {@code CollectionsPage} instance is created per profile-viewer screen open, so this never needs explicit
	 * invalidation.
	 */
	private final java.util.Map<String, ItemStack> minionIconCache = new java.util.HashMap<>();

	public CollectionsPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	private static List<String> withoutNulls(List<String> list) {
		if (list == null) return null;
		List<String> result = new ArrayList<>(list);
		result.removeIf(Objects::isNull);
		return result;
	}

	/**
	 * Slot behind a collection or minion: NEU's grey slot, filled gold from the bottom by how close it is to max
	 * (NEU tinted the same texture with 255, 185, 0).
	 */
	private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, float completedness) {
		int gold = Math.round(20 * Math.max(0, Math.min(1, completedness)));
		if (gold < 20) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, pv_elements, x, y, 0, 0, 20, 20 - gold, 256, 256, 0xFFFFFFFF);
		}
		if (gold > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, pv_elements, x, y + 20 - gold, 0, 20 - gold, 20, gold, 256, 256, 0xFFFFB900);
		}
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		RenderUtils.drawTexturedRect(graphics, pv_cols, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		JsonObject collectionInfo = GuiProfileViewer.getProfile().getCollectionInfo(GuiProfileViewer.getProfileId());
		if (collectionInfo == null) {
			RenderUtils.drawStringCentered(
				graphics, ChatFormatting.RED + "Collection API not enabled!", instance.getFont(),
				guiLeft + 134, guiTop + 101, true, 0
			);
			return;
		}

		JsonObject resourceCollectionInfo = NotEnoughUpdates.INSTANCE.getProfileViewer().getResourceCollectionInformation();
		if (resourceCollectionInfo == null) return;

		int collectionCatSize = ProfileViewer.getCollectionCatToCollectionMap().size();
		int collectionCatYSize = (int) (162f / (collectionCatSize - 1 + 0.0000001f));
		{
			int yIndex = 0;
			for (ItemStack stack : ProfileViewer.getCollectionCatToCollectionMap().keySet()) {
				if (selectedCollectionCategory == null) selectedCollectionCategory = stack;
				if (stack == selectedCollectionCategory) {
					RenderUtils.drawTexturedRect(
						graphics, pv_elements, guiLeft + 7, guiTop + 10 + collectionCatYSize * yIndex, 20, 20,
						20 / 256f, 0, 20 / 256f, 0
					);
					RenderUtils.drawItemStack(graphics, stack, guiLeft + 10, guiTop + 13 + collectionCatYSize * yIndex);
				} else {
					RenderUtils.drawTexturedRect(
						graphics, pv_elements, guiLeft + 7, guiTop + 10 + collectionCatYSize * yIndex, 20, 20,
						0, 20 / 256f, 0, 20 / 256f
					);
					RenderUtils.drawItemStack(graphics, stack, guiLeft + 9, guiTop + 12 + collectionCatYSize * yIndex);
				}
				yIndex++;
			}
		}

		// The minion list has null placeholders (collections without a minion); drop them so the grid packs.
		List<String> collections = withoutNulls(ProfileViewer.getCollectionCatToCollectionMap().get(selectedCollectionCategory));
		List<String> minions = withoutNulls(ProfileViewer.getCollectionCatToMinionMap().get(selectedCollectionCategory));

		maxPage = Math.max((collections != null ? collections.size() : 0) / 20, (minions != null ? minions.size() : 0) / 20);

		if (maxPage != 0) {
			if (page > 0) {
				RenderUtils.text(graphics, instance.getFont(), "<", guiLeft + 100 - 20 - 6, guiTop + 10, 0xFFFFFF, true);
			}
			if (page < 1) {
				RenderUtils.text(graphics, instance.getFont(), ">", guiLeft + 100 + 20 + 250 - 2, guiTop + 10, 0xFFFFFF, true);
			}
		}

		RenderUtils.drawStringCentered(
			graphics, selectedCollectionCategory.getHoverName().getString() + " Collections", instance.getFont(),
			guiLeft + 134, guiTop + 14, true, 4210752
		);

		JsonObject minionTiers = collectionInfo.get("minion_tiers").getAsJsonObject();
		JsonObject collectionTiers = collectionInfo.get("collection_tiers").getAsJsonObject();
		JsonObject maxAmounts = collectionInfo.get("max_amounts").getAsJsonObject();
		JsonObject totalAmounts = collectionInfo.get("total_amounts").getAsJsonObject();
		JsonObject personalAmounts = collectionInfo.get("personal_amounts").getAsJsonObject();

		if (collections != null) {
			for (int i = page * 20, j = 0; i < Math.min((page + 1) * 20, collections.size()); i++, j++) {
				String collection = collections.get(i);
				if (collection != null) {
					ItemStack collectionItem = ProfileViewer.getCollectionToCollectionDisplayMap().get(collection);
					if (collectionItem != null) {
						int xIndex = j % COLLS_XCOUNT;
						int yIndex = j / COLLS_XCOUNT;

						float x = 39 + COLLS_XPADDING + (COLLS_XPADDING + 20) * xIndex;
						float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * yIndex;

						String tierString;
						int tier = (int) Utils.getElementAsFloat(collectionTiers.get(collection), 0);
						if (tier > 20 || tier < 0) {
							tierString = String.valueOf(tier);
						} else {
							tierString = romans[tier];
						}
						float amount = Utils.getElementAsFloat(totalAmounts.get(collection), 0);
						float maxAmount = Utils.getElementAsFloat(maxAmounts.get(collection), 0);
						int tierStringColour = new Color(128, 128, 128, 255).getRGB();
						float completedness = 0;
						if (maxAmount > 0) {
							completedness = amount / maxAmount;
						}
						completedness = Math.min(1, completedness);
						if (maxAmounts.has(collection) && completedness >= 1) {
							tierStringColour = new Color(255, 215, 0).getRGB();
						}

						drawSlot(graphics, guiLeft + (int) x, guiTop + (int) y, completedness);
						RenderUtils.drawItemStack(graphics, collectionItem, guiLeft + (int) x + 2, guiTop + (int) y + 2);

						if (mouseX > guiLeft + (int) x + 2 && mouseX < guiLeft + (int) x + 18) {
							if (mouseY > guiTop + (int) y + 2 && mouseY < guiTop + (int) y + 18) {
								List<String> tooltip = new ArrayList<>();
								tooltip.add(
									collectionItem.getHoverName().getString() +
										" " +
										(completedness >= 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY) +
										tierString
								);
								tooltip.add(
									"Collected: " + numberFormat.format(Utils.getElementAsFloat(personalAmounts.get(collection), 0))
								);
								tooltip.add("Total Collected: " + numberFormat.format(amount));
								instance.tooltipToDisplay = tooltip;
							}
						}

						if (tier >= 0) {
							RenderUtils.drawStringCentered(
								graphics, tierString, instance.getFont(), guiLeft + x + 10, guiTop + y - 4, true, tierStringColour
							);
						}

						RenderUtils.drawStringCentered(
							graphics, StringUtils.shortNumberFormat(amount) + "", instance.getFont(),
							guiLeft + x + 10, guiTop + y + 26, true, new Color(128, 128, 128, 255).getRGB()
						);
					}
				}
			}
		}

		RenderUtils.drawStringCentered(
			graphics, selectedCollectionCategory.getHoverName().getString() + " Minions", instance.getFont(),
			guiLeft + 326, guiTop + 14, true, 4210752
		);

		if (minions != null) {
			for (int i = page * 20, j = 0; i < Math.min((page + 1) * 20, minions.size()); i++, j++) {
				String minion = minions.get(i);
				if (minion != null) {
					JsonObject misc = Constants.MISC;
					float MAX_MINION_TIER = Utils.getElementAsFloat(Utils.getElement(misc, "minions." + minion + "_GENERATOR"), 11);

					int tier = (int) Utils.getElementAsFloat(minionTiers.get(minion), 0);

					int xIndex = j % COLLS_XCOUNT;
					int yIndex = j / COLLS_XCOUNT;

					float x = 231 + COLLS_XPADDING + (COLLS_XPADDING + 20) * xIndex;
					float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * yIndex;

					String tierString;
					if (tier - 1 >= romans.length || tier - 1 < 0) {
						tierString = String.valueOf(tier);
					} else {
						tierString = romans[tier - 1];
					}

					int tierStringColour = new Color(128, 128, 128, 255).getRGB();
					float completedness = tier / MAX_MINION_TIER;

					completedness = Math.min(1, completedness);
					if (completedness >= 1) {
						tierStringColour = new Color(255, 215, 0).getRGB();
					}

					drawSlot(graphics, guiLeft + (int) x, guiTop + (int) y, completedness);

					String minionIconInternalName = minion + "_GENERATOR_" + Math.max(tier, 1);
					ItemStack minionIcon = minionIconCache.computeIfAbsent(minionIconInternalName, name -> {
						JsonObject json = NotEnoughUpdates.INSTANCE.manager.getItemInformation().get(name);
						return json != null ? NotEnoughUpdates.INSTANCE.manager.jsonToStack(json) : null;
					});
					if (minionIcon != null) {
						RenderUtils.drawItemStack(
							graphics, minionIcon,
							(int) (guiLeft + x + 2), (int) (guiTop + y + 2)
						);
					}

					if (mouseX > guiLeft + (int) x + 2 && mouseX < guiLeft + (int) x + 18) {
						if (mouseY > guiTop + (int) y + 2 && mouseY < guiTop + (int) y + 18) {
							// The repo has no minion names, so use the minion item's own (e.g. "Clay Minion XI").
							String name = minionIcon != null && tier > 0 ? minionIcon.getHoverName().getString()
								: Utils.getElementAsString(Utils.getElement(misc, "minions." + minion + "_NAME"), minion) + " " + tierString;
							instance.tooltipToDisplay = Utils.createList(name);
						}
					}

					if (tier >= 0) {
						RenderUtils.drawStringCentered(
							graphics, tierString, instance.getFont(), guiLeft + x + 10, guiTop + y - 4, true, tierStringColour
						);
					}
				}
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		ItemStack stack = null;
		Iterator<ItemStack> items = ProfileViewer.getCollectionCatToCollectionMap().keySet().iterator();
		int key = event.key();
		// Matches the original's intentional (buggy but kept for fidelity) fallthrough switch: pressing a lower
		// number selects further into the iterator than pressing a higher one.
		switch (key) {
			case GLFW.GLFW_KEY_5:
			case GLFW.GLFW_KEY_KP_5:
				if (items.hasNext()) stack = items.next();
			case GLFW.GLFW_KEY_4:
			case GLFW.GLFW_KEY_KP_4:
				if (items.hasNext()) stack = items.next();
			case GLFW.GLFW_KEY_3:
			case GLFW.GLFW_KEY_KP_3:
				if (items.hasNext()) stack = items.next();
			case GLFW.GLFW_KEY_2:
			case GLFW.GLFW_KEY_KP_2:
				if (items.hasNext()) stack = items.next();
			case GLFW.GLFW_KEY_1:
			case GLFW.GLFW_KEY_KP_1:
				if (items.hasNext()) stack = items.next();
				break;
			default:
				return false;
		}
		if (stack != null) {
			selectedCollectionCategory = stack;
			page = 0;
		}
		RenderUtils.playPressSound();
		return true;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (maxPage != 0) {
			if (mouseY > guiTop + 6 && mouseY < guiTop + 22) {
				if (mouseX > guiLeft + 100 - 15 - 12 && mouseX < guiLeft + 100 - 20) {
					if (page > 0) {
						page--;
						return;
					}
				} else if (mouseX > guiLeft + 100 + 15 + 250 && mouseX < guiLeft + 100 + 20 + 12 + 250) {
					if (page < 1) {
						page++;
						return;
					}
				}
			}
		}

		int collectionCatSize = ProfileViewer.getCollectionCatToCollectionMap().size();
		int collectionCatYSize = (int) (162f / (collectionCatSize - 1 + 0.0000001f));
		int yIndex = 0;
		for (ItemStack stack : ProfileViewer.getCollectionCatToCollectionMap().keySet()) {
			if (mouseX > guiLeft + 7 && mouseX < guiLeft + 7 + 20) {
				if (mouseY > guiTop + 10 + collectionCatYSize * yIndex && mouseY < guiTop + 10 + collectionCatYSize * yIndex + 20) {
					selectedCollectionCategory = stack;
					page = 0;
					RenderUtils.playPressSound();
					return;
				}
			}
			yIndex++;
		}
	}

	@Override
	public void resetCache() {
	}
}
