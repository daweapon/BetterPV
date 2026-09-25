package io.github.moulberry.notenoughupdates.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

/**
 * The few Minecraft calls that differ between game versions. Each version has its own copy under
 * {@code versions/<minecraft version>/client/java}; the build compiles the one it targets.
 */
public final class McCompat {
	private McCompat() {
	}

	public static void setScreen(Minecraft client, Screen screen) {
		client.setScreen(screen);
	}

	public static ChatComponent chat(Minecraft client) {
		return client.gui.getChat();
	}

	public static int guiTicks(Minecraft client) {
		return client.gui.getGuiTicks();
	}
}
