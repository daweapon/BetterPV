package io.github.moulberry.notenoughupdates.client;

import net.fabricmc.api.ClientModInitializer;
import io.github.moulberry.notenoughupdates.profileviewer.SettingsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.commands.profile.CataCommand;
import io.github.moulberry.notenoughupdates.commands.profile.PeekCommand;
import io.github.moulberry.notenoughupdates.commands.profile.PvCommand;
import io.github.moulberry.notenoughupdates.commands.profile.ViewProfileCommand;
import io.github.moulberry.notenoughupdates.NEUManager;
import io.github.moulberry.notenoughupdates.util.BpvBackend;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class NotEnoughUpdatesClient implements ClientModInitializer {
	private static boolean openSettingsNextTick;

	@Override
	public void onInitializeClient() {
		// Profile viewer GUI commands. See commands.profile.* for the port notes (old ClientCommandBase/
		// ClientCommandHandler -> Fabric API's Brigadier-based client command registration).
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			ViewProfileCommand.register(dispatcher, "neuprofile");
			PeekCommand.register(dispatcher);
			// Old code skipped registering /cata when "skyblockextras" was also loaded, to avoid clashing with
			// that mod's own /cata command.
			if (!FabricLoader.getInstance().isModLoaded("skyblockextras")) {
				CataCommand.register(dispatcher);
			}
		});

		// /pv is registered in a phase after every other mod's (the default phase, and SkyBlockPv's own late phase,
		// which takes /pv over from Skyblocker the same way), so Better PV's /pv is the one that runs.
		Identifier pvPhase = Identifier.fromNamespaceAndPath("betterpv", "pv_command");
		ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, pvPhase);
		ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Identifier.fromNamespaceAndPath("skyblock-pv", "skyblock_pv_command"), pvPhase);
		ClientCommandRegistrationCallback.EVENT.register(pvPhase, (dispatcher, registryAccess) -> PvCommand.register(dispatcher));

		ChatProfileClick.register();

		// /bpv opens the settings screen. Opening it straight from the command would be undone by chat closing
		// afterwards, so the screen is opened on the next tick.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommands.literal("bpv").executes(ctx -> {
				openSettingsNextTick = true;
				return 0;
			})));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openSettingsNextTick) {
				openSettingsNextTick = false;
				McCompat.setScreen(client, new SettingsScreen(null));
			}
		});

		// Lets BpvBackend prove who the player is, using the Mojang-certified profile key pair vanilla uses for
		// chat signing. prepareKeyPair() is asked for on the render thread, where vanilla itself calls it.
		BpvBackend.setKeySource(() -> {
			Minecraft client = Minecraft.getInstance();
			ProfileKeyPair keyPair = CompletableFuture
				.supplyAsync(() -> client.getProfileKeyPairManager().prepareKeyPair(), client)
				.thenCompose(future -> future)
				.get(15, TimeUnit.SECONDS)
				.orElseThrow(() -> new IllegalStateException("Minecraft profile keys are unavailable (offline account?)"));
			ProfilePublicKey.Data data = keyPair.publicKey().data();
			return new BpvBackend.AccountKeys(
				client.getUser().getProfileId().toString().replace("-", ""),
				data.expiresAt().toEpochMilli(),
				data.key().getEncoded(),
				data.keySignature(),
				keyPair.privateKey()
			);
		});

		// Item model definitions live at assets/<namespace>/items/<path>.json; Hypixel's only exist while its
		// SkyBlock resource pack is loaded.
		NEUManager.itemModelExists = id -> Minecraft.getInstance().getResourceManager()
			.getResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json"))
			.isPresent();

		NotEnoughUpdates.LOGGER.info("NotEnoughUpdates client scaffold initialized");
	}
}
