package io.github.moulberry.notenoughupdates;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Licensing note on the bundled Hypixel assets: everything under {@code assets/hypixel_skyblock} (item models and
 * textures, about 6 MB) comes from Hypixel's official SkyBlock Resource Pack, copyright Hypixel Inc., and is kept
 * next to its LICENSE file. That licence forbids use in "your own business, product or service" but allows
 * "websites and apps that are Hypixel related ... as long as it is not sold". Better PV is a free, Hypixel-related
 * mod, so it is bundled on that reading. It is a judgement call, not a permission from Hypixel: a distribution
 * site's moderators or Hypixel could disagree. If anyone objects, delete the {@code hypixel_skyblock} folder (and
 * the README credit line); the trophy fish and item icons then fall back to vanilla item textures.
 */
public class NotEnoughUpdates implements ModInitializer {
	public static final String MOD_ID = "betterpv";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String VERSION = "1.0.0";

	// TODO(fabric-port): the Forge version exposed a static NotEnoughUpdates.INSTANCE singleton that GUI/page
	// code reached into from anywhere (e.g. NotEnoughUpdates.INSTANCE.manager). Data-layer classes ported so far
	// (ProfileViewer, PlayerStats, etc.) were updated to consistently use their own injected `manager` field
	// instead. This static instance is kept only so other entrypoints (and future GUI-layer ports, which may
	// still need a global access point) can reach the manager until a cleaner DI story exists.
	public static NotEnoughUpdates INSTANCE;

	public NEUManager manager;

	// TODO(fabric-port): like INSTANCE above, the Forge version exposed a static NotEnoughUpdates.profileViewer
	// singleton used by the GUI/command layer (e.g. NotEnoughUpdates.profileViewer.getProfileByName(...)). Kept
	// here as an instance field for the same reason as INSTANCE: the profile viewer GUI/commands port reaches
	// into it from many call sites and a cleaner DI story wasn't in scope.
	//
	// This is lazily constructed via getProfileViewer() (rather than eagerly here in onInitialize) because
	// ProfileViewer's static initializers build ItemStacks with custom-name data components
	// (getSkillToSkillDisplayMap/getCollectionToCollectionDisplayMap), and onInitialize runs too early in the
	// Fabric bootstrap sequence for that - constructing it eagerly here crashed the game on launch with
	// "NullPointerException: Components not bound yet" (registries/data components aren't bound yet at mod
	// entrypoint time). By the time any command actually uses it, the game has finished bootstrapping.
	private io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer profileViewer;

	@Override
	public void onInitialize() {
		INSTANCE = this;
		LOGGER.info("NotEnoughUpdates (Fabric 26.1.2 port scaffold) initializing");

		Path configRoot = FabricLoader.getInstance().getConfigDir();
		migrateLegacyConfigDir(configRoot.resolve("notenoughupdates"), configRoot.resolve(MOD_ID));
		File configDir = configRoot.resolve(MOD_ID).toFile();
		manager = new NEUManager(this, configDir);
	}

	/** Before the mod id became "betterpv" the config, API key and item repo lived in config/notenoughupdates. */
	private static void migrateLegacyConfigDir(Path legacy, Path current) {
		if (!Files.isDirectory(legacy) || Files.exists(current)) {
			return;
		}
		try {
			Files.move(legacy, current);
			LOGGER.info("Moved the config folder {} to {}", legacy, current);
		} catch (IOException e) {
			LOGGER.warn("Could not move the old config folder {}; starting with a fresh one", legacy, e);
		}
	}

	public io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer getProfileViewer() {
		if (profileViewer == null) {
			profileViewer = new io.github.moulberry.notenoughupdates.profileviewer.ProfileViewer(manager);
		}
		return profileViewer;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
