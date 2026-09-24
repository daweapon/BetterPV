package io.github.moulberry.notenoughupdates;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class NotEnoughUpdates implements ModInitializer {
	public static final String MOD_ID = "notenoughupdates";
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

		File configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).toFile();
		manager = new NEUManager(this, configDir);
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
