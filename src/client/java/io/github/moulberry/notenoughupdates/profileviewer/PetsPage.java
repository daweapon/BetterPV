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
import io.github.moulberry.notenoughupdates.core.util.StringUtils;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.PetData;
import io.github.moulberry.notenoughupdates.util.RenderUtils;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Rotations;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port of the Forge 1.8.9 {@code PetsPage} ("Pets" tab). Uses {@link Panorama} for its background, like
 * {@link BasicPage}.
 *
 * <p>TODO(fabric-port) — intentionally simplified vs. the original:
 * <ul>
 *   <li>Per-pet item icons are resolved via {@code NEUManager#jsonToStack} against the repo's
 *   {@code <PETTYPE>;<rarityIndex>} item entries (same scheme {@code ItemResolutionQuery} uses), rather than the
 *   original's {@code ItemUtils#createPetItemstackFromPetInfo} (which also applied held-item skin/stat-boost
 *   lore replacements - not ported, see {@link PetData.Rarity} javadoc). Falls back to rarity-tinted initials
 *   text if the repo hasn't been synced or doesn't have the pet.</li>
 *   <li>The selected pet's head is a mouse-following 3D model with its name above it, like the basic page's
 *   player, instead of the original's bobbing 3.5x item icon.</li>
 *   <li>Day/night panorama selection ({@code SBInfo}) wasn't ported in the data layer either (same as
 *   {@link BasicPage}); always uses the "day" variant.</li>
 * </ul>
 */
public class PetsPage implements GuiProfileViewerPage {

	private static final Identifier pv_pets = Identifier.parse("notenoughupdates:pv_pets.png");
	private static final Identifier pv_elements = Identifier.parse("notenoughupdates:pv_elements.png");
	private static final int COLLS_XCOUNT = 5;
	private static final int COLLS_YCOUNT = 4;
	private static final float COLLS_XPADDING = (190 - COLLS_XCOUNT * 20) / (float) (COLLS_XCOUNT + 1);
	private static final float COLLS_YPADDING = (202 - COLLS_YCOUNT * 20) / (float) (COLLS_YCOUNT + 1);

	private final GuiProfileViewer instance;
	private List<JsonObject> sortedPets = null;
	/**
	 * Icons resolved (via {@link #resolvePetIcon}) once, in parallel with {@link #sortedPets}, the same way the
	 * Forge 1.8.9 original's {@code sortedPetsStack} worked. Resolving lazily inside the per-frame draw loop
	 * instead (as this page originally did post-port) called {@code NEUManager#jsonToStack} - which allocates a
	 * fresh {@code ItemStack}/{@code GameProfile} via {@code .copy()} on every cache hit - for every visible pet
	 * slot on every single frame. Since virtually every repo pet icon is a distinct-texture player-head skull,
	 * each of those fresh per-frame objects looks like a brand-new item to the GPU item-icon atlas (keyed by
	 * model identity), forcing a full re-bake (its own 3D render pass) of every pet icon every frame instead of
	 * once - the render-loop regression behind the freeze/native crash this page's real icons were meant to fix.
	 */
	private List<ItemStack> sortedPetIcons = null;
	private int selectedPet = -1;
	private int petsPage = 0;

	/** The sorts of the in-game pets menu's hopper, in its order. Kept when switching profiles, like the game does. */
	private static final String[] SORT_NAMES = {"Rarity", "A to Z", "Z to A", "Pet Exp", "Skill"};
	private static final int SORT_X = 176;
	private static final int SORT_Y = 6;
	private int sortMode = 0;

	/** Wears the selected pet's head for {@link #drawPetHead}; the icon it's wearing is kept to skip re-equipping. */
	private ArmorStand headStand;
	private ItemStack headStandHead;
	/** Height of a worn head's centre above an armor stand's feet, in blocks. */
	private static final float HEAD_CENTRE = 1.75f;

	public PetsPage(GuiProfileViewer instance) {
		this.instance = instance;
	}

	@Override
	public GuiProfileViewer getInstance() {
		return instance;
	}

	@Override
	public void resetCache() {
		petsPage = 0;
		sortedPets = null;
		sortedPetIcons = null;
		selectedPet = -1;
	}

	@Override
	public void drawPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject petsInfo = profile.getPetsInfo(profileId);
		if (petsInfo == null) return;
		JsonObject petsJson = Constants.PETS;
		if (petsJson == null) return;

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

		JsonArray pets = petsInfo.get("pets").getAsJsonArray();
		if (sortedPets == null) {
			// Built into locals and published together at the end: if anything throws partway, both fields stay
			// null and the next frame retries, instead of leaving sortedPetIcons shorter than sortedPets.
			List<JsonObject> sortedPets = new ArrayList<>();
			for (int i = 0; i < pets.size(); i++) {
				sortedPets.add(pets.get(i).getAsJsonObject());
			}
			// NOTE: sortedPetIcons (built further below, once sorting is final) is index-aligned with
			// sortedPets, so all sorting must happen before it is populated.
			sortedPets.sort(comparator(sortMode));
			List<ItemStack> sortedPetIcons = new ArrayList<>(sortedPets.size());
			for (JsonObject pet : sortedPets) {
				String petType = pet.get("type").getAsString();
				PetData.Rarity rarity = PetData.Rarity.valueOf(pet.get("tier").getAsString());
				GuiProfileViewer.PetLevel petLevel = GuiProfileViewer.getPetLevel(petType, rarity.name(), pet.get("exp").getAsFloat());
				pet.addProperty("level", petLevel.level);
				pet.addProperty("currentLevelRequirement", petLevel.currentLevelRequirement);
				pet.addProperty("maxXP", petLevel.maxXP);
				// Resolved once here (mirrors the Forge original's sortedPetsStack) rather than every frame in the
				// draw loop below - see the sortedPetIcons javadoc for why re-resolving per frame is dangerous.
				sortedPetIcons.add(resolvePetIcon(petType, rarity));
			}
			this.sortedPetIcons = sortedPetIcons;
			this.sortedPets = sortedPets;
		}

		Panorama.drawPanorama(
			graphics,
			-instance.backgroundRotation,
			guiLeft + 212,
			guiTop + 44,
			81,
			108,
			-0.37f,
			0.6f,
			Panorama.getPanoramasForLocation(location == null ? "dynamic" : location, panoramaIdentifier)
		);

		RenderUtils.drawTexturedRect(graphics, pv_pets, guiLeft, guiTop, instance.sizeX, instance.sizeY);

		RenderUtils.drawStringCentered(
			graphics, ChatFormatting.DARK_PURPLE + "Pets", instance.getFont(), guiLeft + 100, guiTop + 14, true, 4210752
		);

		JsonElement activePetElement = petsInfo.get("active_pet");
		if (selectedPet == -1 && activePetElement != null && activePetElement.isJsonObject()) {
			JsonObject active = activePetElement.getAsJsonObject();
			for (int i = 0; i < sortedPets.size(); i++) {
				if (sortedPets.get(i) == active) {
					selectedPet = i;
					break;
				}
			}
		}

		if (petsPage > 0) {
			RenderUtils.text(graphics, instance.getFont(), "<", guiLeft + 100 - 20 - 6, guiTop + 10, 0xFFFFFF, true);
		}
		if (petsPage < Math.ceil(pets.size() / 20f) - 1) {
			RenderUtils.text(graphics, instance.getFont(), ">", guiLeft + 100 + 20 + 2, guiTop + 10, 0xFFFFFF, true);
		}

		RenderUtils.drawItemStack(graphics, new ItemStack(Items.HOPPER), guiLeft + SORT_X, guiTop + SORT_Y);
		if (Utils.isWithinRect(mouseX, mouseY, guiLeft + SORT_X, guiTop + SORT_Y, 16, 16)) {
			List<String> tooltip = new ArrayList<>();
			tooltip.add(ChatFormatting.GREEN + "Sort");
			tooltip.add("");
			for (int i = 0; i < SORT_NAMES.length; i++) {
				tooltip.add(i == sortMode ? ChatFormatting.AQUA + "▶ " + SORT_NAMES[i] : ChatFormatting.GRAY + "   " + SORT_NAMES[i]);
			}
			tooltip.add("");
			tooltip.add(ChatFormatting.YELLOW + "Click to switch sort!");
			tooltip.add(ChatFormatting.AQUA + "Right-Click to go backwards!");
			instance.tooltipToDisplay = tooltip;
		}

		for (
			int i = petsPage * 20;
			i < Math.min(petsPage * 20 + 20, sortedPets.size());
			i++
		) {
			JsonObject pet = sortedPets.get(i);
			if (pet != null) {
				String petType = pet.get("type").getAsString();
				PetData.Rarity rarity = PetData.Rarity.valueOf(pet.get("tier").getAsString());

				int xIndex = (i % 20) % COLLS_XCOUNT;
				int yIndex = (i % 20) / COLLS_XCOUNT;

				float x = 5 + COLLS_XPADDING + (COLLS_XPADDING + 20) * xIndex;
				float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * yIndex;

				if (i == selectedPet) {
					RenderUtils.drawTexturedRect(graphics, pv_elements, guiLeft + x, guiTop + y, 20, 20, 0, 20 / 256f, 20 / 256f, 40 / 256f);
				} else {
					RenderUtils.drawTexturedRect(graphics, pv_elements, guiLeft + x, guiTop + y, 20, 20, 0, 20 / 256f, 0, 20 / 256f);
				}

				ItemStack petIcon = sortedPetIcons.get(i);
				if (petIcon != null) {
					RenderUtils.drawItemStack(graphics, petIcon, (int) (guiLeft + x + 2), (int) (guiTop + y + 2));
				} else {
					String initials = petInitials(petType);
					RenderUtils.drawStringCentered(
						graphics, rarity.chatFormatting + initials, instance.getFont(), guiLeft + x + 10, guiTop + y + 10, true, 0
					);
				}

				if (mouseX > guiLeft + x && mouseX < guiLeft + x + 20) {
					if (mouseY > guiTop + y && mouseY < guiTop + y + 20) {
						float level = pet.get("level").getAsFloat();
						instance.tooltipToDisplay = petTooltip(pet, (int) Math.floor(level));
					}
				}
			}
		}

		if (selectedPet >= 0 && selectedPet < sortedPets.size()) {
			JsonObject pet = sortedPets.get(selectedPet);
			String petType = pet.get("type").getAsString();
			PetData.Rarity rarity = PetData.Rarity.valueOf(pet.get("tier").getAsString());
			String colouredName = rarity.chatFormatting + formatPetName(petType);

			float level = pet.get("level").getAsFloat();

			drawPetHead(graphics, sortedPetIcons.get(selectedPet), guiLeft, guiTop, mouseX, mouseY);
			// Name above the head, the way the basic page names the player model.
			RenderUtils.drawStringCenteredScaledMaxWidth(
				graphics, colouredName, instance.getFont(),
				guiLeft + 252.5f, guiTop + 51, true, 81, 0xFFFFFF
			);

			float currentLevelRequirement = pet.get("currentLevelRequirement").getAsFloat();
			float exp = pet.get("exp").getAsFloat();
			float maxXP = pet.get("maxXP").getAsFloat();

			RenderUtils.renderAlignedString(
				graphics, colouredName, ChatFormatting.WHITE + "Level " + (int) Math.floor(level), guiLeft + 319, guiTop + 28, 98
			);

			// A max-level pet has no next level: show MAX with rainbow bars, like maxed skills.
			boolean maxed = exp >= maxXP;
			if (maxed) {
				instance.renderGoldBar(graphics, guiLeft + 319, guiTop + 38, 98);
			} else {
				instance.renderBar(graphics, guiLeft + 319, guiTop + 38, 98, (float) Math.floor(level) / 100f);
			}
			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.YELLOW + "To Next LVL",
				maxed ? ChatFormatting.GOLD + "MAX" : ChatFormatting.WHITE.toString() + (int) (level % 1 * 100) + "%",
				guiLeft + 319,
				guiTop + 46,
				98
			);
			if (maxed) {
				instance.renderGoldBar(graphics, guiLeft + 319, guiTop + 56, 98);
			} else {
				instance.renderBar(graphics, guiLeft + 319, guiTop + 56, 98, level % 1);
			}

			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.YELLOW + "To Max LVL",
				ChatFormatting.WHITE.toString() + Math.min(100, (int) (exp / maxXP * 100)) + "%",
				guiLeft + 319,
				guiTop + 64,
				98
			);
			if (maxed) {
				instance.renderGoldBar(graphics, guiLeft + 319, guiTop + 74, 98);
			} else {
				instance.renderBar(graphics, guiLeft + 319, guiTop + 74, 98, exp / maxXP);
			}

			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.YELLOW + "Total XP",
				ChatFormatting.WHITE + StringUtils.shortNumberFormat(exp),
				guiLeft + 319,
				guiTop + 125,
				98
			);
			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.YELLOW + "Current LVL XP",
				ChatFormatting.WHITE + StringUtils.shortNumberFormat((level % 1) * currentLevelRequirement),
				guiLeft + 319,
				guiTop + 143,
				98
			);
			RenderUtils.renderAlignedString(
				graphics,
				ChatFormatting.YELLOW + "Required LVL XP",
				ChatFormatting.WHITE + StringUtils.shortNumberFormat(currentLevelRequirement),
				guiLeft + 319,
				guiTop + 161,
				98
			);
		}
	}

	/** Builds the old NEU-style pet hover: scaled stats/perks plus the held item's name and effect lore. */
	private static List<String> petTooltip(JsonObject pet, int level) {
		String type = pet.get("type").getAsString();
		String tier = pet.get("tier").getAsString();
		String rarityId = PlayerStats.MINION_RARITY_TO_NUM.get(tier);
		JsonObject repoPet = rarityId == null ? null : io.github.moulberry.notenoughupdates.NotEnoughUpdates.INSTANCE.manager
			.getItemInformation().get(type + ";" + rarityId);
		if (repoPet == null) return Utils.createList(formatPetName(type) + " (Lvl " + level + ")");

		Map<String, String> replacements = petReplacements(type, tier, level);
		String heldId = Utils.getElementAsString(pet.get("heldItem"), null);
		if (heldId != null) {
			applyPetBoost(replacements, PlayerStats.PET_STAT_BOOSTS.get(heldId), false);
			applyPetBoost(replacements, PlayerStats.PET_STAT_BOOSTS_MULT.get(heldId), true);
		}

		List<String> tooltip = new ArrayList<>();
		tooltip.add(replace(repoPet.get("displayname").getAsString(), replacements));
		for (JsonElement line : repoPet.getAsJsonArray("lore")) {
			String text = replace(line.getAsString(), replacements);
			String clean = ChatFormatting.stripFormatting(text);
			if (clean != null && (clean.contains("Right-click to add this pet") || clean.contains("Click to view recipe"))) continue;
			tooltip.add(text);
		}

		if (heldId != null) {
			JsonObject held = io.github.moulberry.notenoughupdates.NotEnoughUpdates.INSTANCE.manager
				.getItemInformation().get(heldId);
			if (held != null) {
				tooltip.add("");
				tooltip.add(ChatFormatting.GOLD + "Held Item: " + held.get("displayname").getAsString());
				boolean description = false;
				int blanks = 0;
				for (JsonElement line : held.getAsJsonArray("lore")) {
					String text = line.getAsString();
					if (ChatFormatting.stripFormatting(text).trim().isEmpty()) {
						blanks++;
						if (description) break;
					} else if (blanks >= 2) {
						description = true;
						tooltip.add(text);
					}
				}
			}
		}
		tooltip.add("");
		tooltip.add(ChatFormatting.AQUA + "MAX LEVEL");
		tooltip.add(ChatFormatting.GRAY + StringUtils.shortNumberFormat(pet.get("exp").getAsFloat()) + " XP");
		return tooltip;
	}

	private static Map<String, String> petReplacements(String type, String tier, int level) {
		Map<String, String> replacements = new java.util.HashMap<>();
		replacements.put("LVL", Integer.toString(level));
		JsonElement petData = Utils.getElement(Constants.PETNUMS, type + "." + tier);
		if (!(petData instanceof JsonObject data) || !data.has("1") || !data.has("100")) return replacements;
		JsonObject min = data.getAsJsonObject("1");
		JsonObject max = data.getAsJsonObject("100");
		float minMix = (100 - level) / 99f;
		float maxMix = (level - 1) / 99f;
		JsonArray otherMin = min.getAsJsonArray("otherNums");
		JsonArray otherMax = max.getAsJsonArray("otherNums");
		for (int i = 0; i < otherMax.size(); i++) {
			double value = Math.floor((otherMin.get(i).getAsDouble() * minMix + otherMax.get(i).getAsDouble() * maxMix) * 10) / 10;
			replacements.put(Integer.toString(i), compact(value));
		}
		for (Map.Entry<String, JsonElement> entry : max.getAsJsonObject("statNums").entrySet()) {
			double value = Math.floor((min.getAsJsonObject("statNums").get(entry.getKey()).getAsDouble() * minMix
				+ entry.getValue().getAsDouble() * maxMix) * 10) / 10;
			replacements.put(entry.getKey(), compact(value));
		}
		return replacements;
	}

	private static void applyPetBoost(Map<String, String> replacements, Map<String, Float> boosts, boolean multiply) {
		if (boosts == null) return;
		for (Map.Entry<String, Float> boost : boosts.entrySet()) {
			String key = boost.getKey().toUpperCase(Locale.ROOT);
			String original = replacements.get(key);
			if (original == null) continue;
			double value = Double.parseDouble(original);
			replacements.put(key, compact(Math.floor(multiply ? value * boost.getValue() : value + boost.getValue())));
		}
	}

	private static String replace(String text, Map<String, String> replacements) {
		for (Map.Entry<String, String> entry : replacements.entrySet()) {
			text = text.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return text;
	}

	private static String compact(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
	}

	/**
	 * The selected pet's head as a 3D model in the panorama box, turning to follow the mouse like the basic page's
	 * player model. It's worn by an invisible armor stand (which only draws what it wears), drawn with the same
	 * vanilla inventory-preview helper; the stand is never added to the world.
	 */
	private void drawPetHead(GuiGraphicsExtractor graphics, ItemStack head, int guiLeft, int guiTop, int mouseX, int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || head == null) return;
		if (headStand == null || headStand.level() != minecraft.level) {
			headStand = new ArmorStand(minecraft.level, 0, 0, 0);
			headStand.setInvisible(true);
			headStandHead = null;
		}
		if (headStandHead != head) {
			headStand.setItemSlot(EquipmentSlot.HEAD, head);
			headStandHead = head;
		}
		// Far above the camera, like the player model, so it's lit by full sky light.
		headStand.setPos(minecraft.player.getX(), minecraft.player.getY() + 1000, minecraft.player.getZ());

		int x0 = guiLeft + 212, y0 = guiTop + 60, x1 = guiLeft + 293, y1 = guiTop + 152;
		// The armor stand renderer ignores the body/head angles the helper sets, so aim it the same way here: the
		// body faces the mouse a little and the head (its pose is relative to the body) the rest of the way.
		float yaw = (float) Math.atan(((x0 + x1) / 2f - mouseX) / 40f) * 20;
		float pitch = (float) Math.atan(((y0 + y1) / 2f - mouseY) / 40f) * 20;
		headStand.setYRot(180 + yaw);
		headStand.yRotO = 180 + yaw;
		headStand.setHeadPose(new Rotations(-pitch, yaw, 0));
		// The helper centres the point HEAD_CENTRE blocks up the stand in the box.
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			graphics, x0, y0, x1, y1, 50, HEAD_CENTRE - headStand.getBbHeight() / 2, mouseX, mouseY, headStand
		);
	}

	/** Re-sorts the pets and their icons together, keeping the selected pet selected and going back to page 1. */
	private void resort() {
		JsonObject selected = selectedPet >= 0 && selectedPet < sortedPets.size() ? sortedPets.get(selectedPet) : null;
		Map<JsonObject, ItemStack> icons = new IdentityHashMap<>();
		for (int i = 0; i < sortedPets.size(); i++) icons.put(sortedPets.get(i), sortedPetIcons.get(i));

		List<JsonObject> pets = new ArrayList<>(sortedPets);
		pets.sort(comparator(sortMode));
		List<ItemStack> petIcons = new ArrayList<>(pets.size());
		for (JsonObject pet : pets) petIcons.add(icons.get(pet));
		sortedPetIcons = petIcons;
		sortedPets = pets;
		selectedPet = selected == null ? -1 : pets.indexOf(selected);
		petsPage = 0;
	}

	private static Comparator<JsonObject> comparator(int sortMode) {
		// Highest rarity first, then most exp, which is the game's default order and every other sort's tiebreak.
		Comparator<JsonObject> byRarity = Comparator.comparingInt(PetsPage::rarityRank).reversed()
			.thenComparing(Comparator.comparingDouble((JsonObject pet) -> pet.get("exp").getAsDouble()).reversed());
		Comparator<JsonObject> byName = Comparator.comparing(pet -> formatPetName(pet.get("type").getAsString()));
		return switch (SORT_NAMES[sortMode]) {
			case "A to Z" -> byName.thenComparing(byRarity);
			case "Z to A" -> byName.reversed().thenComparing(byRarity);
			case "Pet Exp" -> Comparator.comparingDouble((JsonObject pet) -> pet.get("exp").getAsDouble()).reversed()
				.thenComparing(byRarity);
			case "Skill" -> Comparator.comparing(PetsPage::petSkill).thenComparing(byRarity);
			default -> byRarity;
		};
	}

	private static int rarityRank(JsonObject pet) {
		String rank = PlayerStats.MINION_RARITY_TO_NUM.get(pet.get("tier").getAsString());
		return rank == null ? -1 : Integer.parseInt(rank);
	}

	/** The pet's skill from the repo's pets.json (COMBAT, MINING, ...); unknown pets sort last. */
	private static String petSkill(JsonObject pet) {
		JsonElement skill = Utils.getElement(Constants.PETS, "pet_types." + pet.get("type").getAsString());
		return skill != null && skill.isJsonPrimitive() ? skill.getAsString() : "~";
	}

	private static String formatPetName(String petType) {
		return WordUtils.capitalizeFully(petType.replace("_", " "));
	}

	/**
	 * Resolves the repo item icon for a pet, using the same {@code <PETTYPE>;<rarityIndex>} internal-name scheme
	 * {@link io.github.moulberry.notenoughupdates.util.ItemResolutionQuery#resolveInternalName} uses for pets
	 * found via NBT. Returns {@code null} (letting callers fall back to the rarity-tinted initials) if the repo
	 * hasn't been synced or doesn't have this pet.
	 */
	private static net.minecraft.world.item.ItemStack resolvePetIcon(String petType, PetData.Rarity rarity) {
		String petId = petType.toUpperCase(Locale.ROOT) + ";" + rarity.petId;
		JsonObject petJson = io.github.moulberry.notenoughupdates.NotEnoughUpdates.INSTANCE.manager
			.getItemInformation().get(petId);
		if (petJson == null) return null;
		return io.github.moulberry.notenoughupdates.NotEnoughUpdates.INSTANCE.manager.jsonToStack(petJson);
	}

	private static String petInitials(String petType) {
		String[] words = petType.replace("_", " ").toLowerCase(Locale.US).split(" ");
		StringBuilder sb = new StringBuilder();
		for (String word : words) {
			if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0)));
		}
		return sb.length() > 3 ? sb.substring(0, 3) : sb.toString();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		if (sortedPets == null) return false;
		if (Utils.isWithinRect((int) mouseX, (int) mouseY,
			GuiProfileViewer.getGuiLeft() + SORT_X, GuiProfileViewer.getGuiTop() + SORT_Y, 16, 16)) {
			if (mouseButton != 0 && mouseButton != 1) return false;
			sortMode = Math.floorMod(sortMode + (mouseButton == 0 ? 1 : -1), SORT_NAMES.length);
			resort();
			RenderUtils.playPressSound();
			return true;
		}
		for (int i = petsPage * 20; i < Math.min(petsPage * 20 + 20, sortedPets.size()); i++) {
			int xIndex = (i % 20) % COLLS_XCOUNT;
			int yIndex = (i % 20) / COLLS_XCOUNT;

			float x = 5 + COLLS_XPADDING + (COLLS_XPADDING + 20) * xIndex;
			float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * yIndex;

			int guiLeft = GuiProfileViewer.getGuiLeft();
			int guiTop = GuiProfileViewer.getGuiTop();
			if (mouseX > guiLeft + x && mouseX < guiLeft + x + 20) {
				if (mouseY > guiTop + y && mouseY < guiTop + y + 20) {
					selectedPet = i;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int mouseButton) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (mouseY > guiTop + 6 && mouseY < guiTop + 22) {
			if (mouseX > guiLeft + 100 - 15 - 12 && mouseX < guiLeft + 100 - 20) {
				if (petsPage > 0) {
					petsPage--;
				}
			} else if (mouseX > guiLeft + 100 + 15 && mouseX < guiLeft + 100 + 20 + 12) {
				if (sortedPets != null && petsPage < Math.ceil(sortedPets.size() / 20f) - 1) {
					petsPage++;
				}
			}
		}
	}
}
