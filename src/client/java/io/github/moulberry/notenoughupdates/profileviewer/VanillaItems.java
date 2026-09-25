package io.github.moulberry.notenoughupdates.profileviewer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Dyes, stained glass panes and banners by their registry id. Minecraft 26.2 moved the per-colour {@code Items.X_DYE}
 * fields into colour collections, so looking the items up by id keeps one source tree building on every version.
 */
public final class VanillaItems {
	public static final Item LIME_DYE = item("lime_dye");
	public static final Item GRAY_DYE = item("gray_dye");
	public static final Item GREEN_DYE = item("green_dye");
	public static final Item RED_DYE = item("red_dye");
	public static final Item BLACK_STAINED_GLASS_PANE = item("black_stained_glass_pane");
	public static final Item GREEN_STAINED_GLASS_PANE = item("green_stained_glass_pane");
	public static final Item LIGHT_GRAY_STAINED_GLASS_PANE = item("light_gray_stained_glass_pane");
	public static final Item LIME_STAINED_GLASS_PANE = item("lime_stained_glass_pane");
	public static final Item RED_STAINED_GLASS_PANE = item("red_stained_glass_pane");
	public static final Item YELLOW_STAINED_GLASS_PANE = item("yellow_stained_glass_pane");
	public static final Item COPPER_BLOCK = item("copper_block");
	public static final Item WHITE_BANNER = item("white_banner");

	private VanillaItems() {
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}
}
