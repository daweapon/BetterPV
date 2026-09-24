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

package io.github.moulberry.notenoughupdates.core.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Trimmed port of the Forge 1.8.9 {@code core.util.StringUtils}. Only the members actually used by the
 * data/logic layer (profileviewer, weight calculators, item resolution) were ported; the original also had
 * {@code trimToWidth}, which required a {@code FontRenderer} and is GUI-only, so it was dropped here.
 *
 * TODO(fabric-port): if GUI code needs trimToWidth again, port it against the new text-rendering API then.
 */
public class StringUtils {
	public static String cleanColour(String in) {
		return in.replaceAll("(?i)\\u00A7.", "");
	}

	public static String cleanColourNotModifiers(String in) {
		return in.replaceAll("(?i)\\u00A7[0-9a-f]", "§r");
	}

	public static int cleanAndParseInt(String str) {
		str = cleanColour(str);
		str = str.replace(",", "");
		return Integer.parseInt(str);
	}

	public static String shortNumberFormat(double n) {
		return shortNumberFormat(n, 0);
	}

	private static final char[] c = new char[] { 'k', 'm', 'b', 't' };

	public static String shortNumberFormat(double n, int iteration) {
		if (n < 1000) {
			if (n % 1 == 0) {
				return Integer.toString((int) n);
			} else {
				return String.format("%.2f", n);
			}
		}

		double d = ((long) n / 100) / 10.0;
		boolean isRound = (d * 10) % 10 == 0;
		return d < 1000 ? (isRound || d > 9.99 ? (int) d * 10 / 10 : d + "") + "" + c[iteration] : shortNumberFormat(d, iteration + 1);
	}

	public static String urlEncode(String something) {
		try {
			return URLEncoder.encode(something, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e); // UTF 8 should always be present
		}
	}

	/**
	 * taken and modified from https://stackoverflow.com/a/23326014/5507634
	 */
	public static String replaceLast(String string, String toReplace, String replacement) {
		int start = string.lastIndexOf(toReplace);
		return string.substring(0, start) + replacement + string.substring(start + toReplace.length());
	}
}
