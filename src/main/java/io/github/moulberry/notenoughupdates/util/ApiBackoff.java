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

package io.github.moulberry.notenoughupdates.util;

import io.github.moulberry.notenoughupdates.NotEnoughUpdates;

import java.util.Locale;

/**
 * Stops key-protected Hypixel requests from hammering a key Hypixel is already unhappy with. The viewer
 * re-asks every few seconds, and repeated failures can get a key or account flagged.
 *
 * <ul>
 *   <li>"Invalid API key" / "violates the API policy": no more requests until the player reopens the viewer
 *   ({@link #reset}).</li>
 *   <li>"Too many requests": wait a minute, doubling with each throttle up to ten minutes.</li>
 *   <li>A success clears the throttle count. Other failures say nothing about the key and are ignored.</li>
 * </ul>
 * While blocked, requests aren't sent; they answer at once with a Hypixel-style error.
 */
public final class ApiBackoff {

	private static final long MIN_THROTTLE_WAIT = 60_000;
	private static final long MAX_THROTTLE_WAIT = 10 * 60_000;

	private static String stoppedCause;
	private static int throttles;
	private static long retryAt;
	private static String throttleCause;

	private ApiBackoff() {
	}

	/** Why key-protected requests are held back right now, or null if one may be sent. */
	public static synchronized String blockedReason() {
		if (stoppedCause != null) {
			return "Stopped API requests after \"" + stoppedCause + "\" - check the API key, then reopen the profile viewer";
		}
		long wait = retryAt - System.currentTimeMillis();
		if (wait > 0) {
			return "Paused API requests for " + (wait + 999) / 1000 + "s after \"" + throttleCause + "\"";
		}
		return null;
	}

	/** Records the answer to a key-protected request. */
	public static synchronized void record(int status, boolean success, String cause) {
		if (success) {
			throttles = 0;
			return;
		}
		String lower = cause == null ? "" : cause.toLowerCase(Locale.ROOT);
		if (lower.contains("invalid api key") || lower.contains("violates the api policy")) {
			if (stoppedCause == null) NotEnoughUpdates.LOGGER.warn("Stopping Hypixel API requests after: {}", cause);
			stoppedCause = cause;
		} else if (status == 429 || lower.contains("too many requests") || lower.contains("throttle")) {
			throttles++;
			long wait = Math.min(MAX_THROTTLE_WAIT, MIN_THROTTLE_WAIT << Math.min(throttles - 1, 10));
			retryAt = System.currentTimeMillis() + wait;
			throttleCause = cause == null || cause.isEmpty() ? "Too many requests" : cause;
			NotEnoughUpdates.LOGGER.warn("Pausing Hypixel API requests for {}s after: {}", wait / 1000, throttleCause);
		}
	}

	/** Lifts a stop (the key may have been fixed); called when a profile is opened. A throttle pause keeps running. */
	public static synchronized void reset() {
		stoppedCause = null;
	}
}
