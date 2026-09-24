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

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The fake player drawn on the basic page, replacing the Forge original's {@code EntityOtherPlayerMP}. It's never
 * added to the world. Its skin comes from the player's Mojang profile (fetched once per UUID in the background, the
 * same lookup the game uses for player heads); until that arrives it shows the default skin.
 */
public class ProfilePlayerEntity extends RemotePlayer {
	/** Mojang profiles with skin textures, by UUID. A null value means the fetch is still running or failed. */
	private static final Map<UUID, GameProfile> TEXTURED_PROFILES = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> REQUESTED = new ConcurrentHashMap<>();

	private GameProfile skinProfile;
	private Supplier<PlayerSkin> skin;

	public ProfilePlayerEntity(ClientLevel level, UUID uuid, String name) {
		super(level, new GameProfile(uuid, name));
		// Show every outer skin layer (hat, jacket, sleeves, trousers), which the server would normally sync.
		getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
		requestProfile(uuid);
	}

	@Override
	public PlayerSkin getSkin() {
		GameProfile textured = TEXTURED_PROFILES.get(getUUID());
		if (textured != null && textured != skinProfile) {
			skinProfile = textured;
			skin = Minecraft.getInstance().getSkinManager().createLookup(textured, false);
		}
		return skin != null ? skin.get() : super.getSkin();
	}

	private static void requestProfile(UUID uuid) {
		if (REQUESTED.putIfAbsent(uuid, true) != null) return;
		CompletableFuture.runAsync(() -> {
			try {
				ProfileResult result = Minecraft.getInstance().services().sessionService().fetchProfile(uuid, true);
				if (result != null) TEXTURED_PROFILES.put(uuid, result.profile());
			} catch (RuntimeException e) {
				// No skin then; the default one stays.
			}
		});
	}

	/** Parses Mojang's undashed UUID form, or returns null. */
	public static UUID parseUuid(String uuid) {
		if (uuid == null) return null;
		try {
			return UUID.fromString(uuid.length() == 32
				? uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")
				: uuid);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
