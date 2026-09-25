/*
 * Copyright (C) 2024 NotEnoughUpdates contributors
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

package io.github.moulberry.notenoughupdates.profileviewer.hotm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The small Lisp used by {@code constants/hotmlayout.json} for perk costs, stats, items and lore conditions.
 * Current NEU uses the {@code moe.nea.lisp} library; this is a self-contained version of what the file needs:
 * {@code defun}, {@code if}, arithmetic, comparisons, {@code pow}/{@code round}/{@code ceil}/{@code floor},
 * {@code format-int}, {@code list.new}/{@code list.at}, numbers, strings and {@code :ATOM} keywords.
 *
 * Values are {@link Double}, {@link String}, {@link Boolean}, {@link Atom}, {@link List}, {@link Fn}, or
 * {@code null} for nil.
 */
public final class HotmLisp {
	private HotmLisp() {}

	public record Atom(String label) {}

	private record Symbol(String name) {}

	private record Literal(Object value) {}

	private record ListNode(List<Object> items) {}

	public record Program(List<Object> expressions) {}

	@FunctionalInterface
	public interface Fn {
		Object call(List<Object> args);
	}

	public static final class LispError extends RuntimeException {
		public LispError(String message) {
			super(message);
		}
	}

	public static final class Env {
		private final Map<String, Object> values = new HashMap<>();
		private final Env parent;

		private Env(Env parent) {
			this.parent = parent;
		}

		public Env fork() {
			return new Env(this);
		}

		public void set(String name, Object value) {
			values.put(name, value);
		}

		Object get(String name) {
			for (Env env = this; env != null; env = env.parent) {
				if (env.values.containsKey(name)) return env.values.get(name);
			}
			throw new LispError("Unbound symbol " + name);
		}
	}

	public static Program parse(String source) {
		Parser parser = new Parser(source);
		List<Object> expressions = new ArrayList<>();
		parser.skipWhitespace();
		while (!parser.atEnd()) {
			expressions.add(parser.readExpression());
			parser.skipWhitespace();
		}
		return new Program(expressions);
	}

	/** Runs every expression in order and returns the last value (nil for an empty program). */
	public static Object run(Program program, Env env) {
		Object result = null;
		for (Object expression : program.expressions()) result = eval(expression, env);
		return result;
	}

	public static boolean isTruthy(Object value) {
		return value != null && !Boolean.FALSE.equals(value);
	}

	public static Env rootEnv() {
		Env env = new Env(null);
		env.set("true", true);
		env.set("false", false);
		env.set("nil", null);
		env.set("+", (Fn) args -> {
			double sum = 0;
			for (Object arg : args) sum += number(arg);
			return sum;
		});
		env.set("*", (Fn) args -> {
			double product = 1;
			for (Object arg : args) product *= number(arg);
			return product;
		});
		env.set("-", (Fn) args -> {
			if (args.size() == 1) return -number(args.get(0));
			double result = number(args.get(0));
			for (int i = 1; i < args.size(); i++) result -= number(args.get(i));
			return result;
		});
		env.set("/", (Fn) args -> {
			double result = number(args.get(0));
			for (int i = 1; i < args.size(); i++) result /= number(args.get(i));
			return result;
		});
		env.set("lt", (Fn) args -> number(args.get(0)) < number(args.get(1)));
		env.set("gt", (Fn) args -> number(args.get(0)) > number(args.get(1)));
		env.set("=", (Fn) args -> args.get(0) instanceof Double && args.get(1) instanceof Double
			? ((Double) args.get(0)).doubleValue() == (Double) args.get(1)
			: Objects.equals(args.get(0), args.get(1)));
		env.set("pow", (Fn) args -> Math.pow(number(args.get(0)), number(args.get(1))));
		// Kotlin's round() rounds half to even, like Math.rint.
		env.set("round", unary(Math::rint));
		env.set("ceil", unary(Math::ceil));
		env.set("floor", unary(Math::floor));
		env.set("format-int", (Fn) args -> String.format(java.util.Locale.US, "%,d", (long) number(args.get(0))));
		env.set("list.new", (Fn) ArrayList::new);
		env.set("list.at", (Fn) args -> {
			if (!(args.get(0) instanceof List<?> list)) throw new LispError("list.at needs a list");
			int index = (int) number(args.get(1));
			if (index < 0 || index >= list.size()) throw new LispError("list.at index " + index + " out of range");
			return list.get(index);
		});
		return env;
	}

	private static Fn unary(Function<Double, Double> function) {
		return args -> function.apply(number(args.get(0)));
	}

	private static double number(Object value) {
		if (value instanceof Double number) return number;
		throw new LispError("Expected a number, got " + value);
	}

	private static Object eval(Object expression, Env env) {
		if (expression instanceof Literal literal) return literal.value();
		if (expression instanceof Symbol symbol) return env.get(symbol.name());
		ListNode node = (ListNode) expression;
		List<Object> items = node.items();
		if (items.isEmpty()) return null;

		if (items.get(0) instanceof Symbol head) {
			switch (head.name()) {
				case "if" -> {
					if (items.size() < 3) throw new LispError("if needs a condition and a branch");
					if (isTruthy(eval(items.get(1), env))) return eval(items.get(2), env);
					return items.size() > 3 ? eval(items.get(3), env) : null;
				}
				case "defun" -> {
					String name = ((Symbol) items.get(1)).name();
					List<String> params = new ArrayList<>();
					for (Object param : ((ListNode) items.get(2)).items()) params.add(((Symbol) param).name());
					List<Object> body = items.subList(3, items.size());
					Env closure = env;
					Fn function = args -> {
						Env local = closure.fork();
						for (int i = 0; i < params.size(); i++) local.set(params.get(i), i < args.size() ? args.get(i) : null);
						Object result = null;
						for (Object bodyExpression : body) result = eval(bodyExpression, local);
						return result;
					};
					env.set(name, function);
					return function;
				}
				default -> {
				}
			}
		}

		Object callee = eval(items.get(0), env);
		if (!(callee instanceof Fn function)) throw new LispError("Not a function: " + items.get(0));
		List<Object> args = new ArrayList<>(items.size() - 1);
		for (int i = 1; i < items.size(); i++) args.add(eval(items.get(i), env));
		return function.call(args);
	}

	private static final class Parser {
		private final String source;
		private int pos;

		Parser(String source) {
			this.source = source;
		}

		boolean atEnd() {
			return pos >= source.length();
		}

		void skipWhitespace() {
			while (!atEnd() && Character.isWhitespace(source.charAt(pos))) pos++;
		}

		Object readExpression() {
			skipWhitespace();
			if (atEnd()) throw new LispError("Unexpected end of input");
			char c = source.charAt(pos);
			if (c == '(') {
				pos++;
				List<Object> items = new ArrayList<>();
				while (true) {
					skipWhitespace();
					if (atEnd()) throw new LispError("Unclosed (");
					if (source.charAt(pos) == ')') {
						pos++;
						return new ListNode(items);
					}
					items.add(readExpression());
				}
			}
			if (c == ')') throw new LispError("Unexpected )");
			if (c == '"') return new Literal(readString());

			int start = pos;
			while (!atEnd() && !Character.isWhitespace(source.charAt(pos)) && "()\"".indexOf(source.charAt(pos)) < 0) pos++;
			String token = source.substring(start, pos);
			if (token.startsWith(":")) return new Literal(new Atom(token.substring(1)));
			if (token.matches("-?\\d+(\\.\\d+)?")) return new Literal(Double.parseDouble(token));
			return new Symbol(token);
		}

		private String readString() {
			pos++; // opening quote
			StringBuilder builder = new StringBuilder();
			while (!atEnd()) {
				char c = source.charAt(pos++);
				if (c == '"') return builder.toString();
				if (c == '\\' && !atEnd()) c = source.charAt(pos++);
				builder.append(c);
			}
			throw new LispError("Unclosed string");
		}
	}
}
