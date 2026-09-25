package io.github.moulberry.notenoughupdates.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

/**
 * The few Minecraft calls that differ between game versions. There is one copy of this class per version, in
 * {@code versions/<minecraft version>/client/java}, and the build only compiles the copy for the version it targets.
 */
public final class McCompat {
	private McCompat() {
	}

	public static void setScreen(Minecraft client, Screen screen) {
		client.setScreenAndShow(screen);
	}

	public static ChatComponent chat(Minecraft client) {
		return client.gui.hud.getChat();
	}

	public static int guiTicks(Minecraft client) {
		return client.gui.hud.getGuiTicks();
	}
}
