package com.mojang.brigadier;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier StringReader shim.
 *
 * Mirrors {@code com.mojang.brigadier.StringReader} from Forge 1.20.1 (backed
 * by Brigadier). Argument types parse their input through this reader, so the
 * {@code com.mojang.brigadier.arguments.*} shims can implement the REAL
 * {@code parse(StringReader)} signature mods call. The bridge uses it to walk
 * tokens and keep cursor positions for honest syntax-error reporting.
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-READER-001
 */
public class StringReader {
	public static final char SYNTAX_ESCAPE = '\\';

	private final String string;
	private int cursor;

	public StringReader(String string) {
		this.string = string != null ? string : "";
	}

	public String getString() {
		return string;
	}

	public int getCursor() {
		return cursor;
	}

	public void setCursor(int cursor) {
		this.cursor = Math.max(0, Math.min(cursor, string.length()));
	}

	public int getRemainingLength() {
		return string.length() - cursor;
	}

	public int getTotalLength() {
		return string.length();
	}

	public String getRemaining() {
		return string.substring(cursor);
	}

	public String getRead() {
		return string.substring(0, cursor);
	}

	public boolean canRead() {
		return cursor < string.length();
	}

	public boolean canRead(int length) {
		return cursor + length <= string.length();
	}

	public char peek() {
		return canRead() ? string.charAt(cursor) : '\0';
	}

	public char peek(int offset) {
		return canRead(offset) ? string.charAt(cursor + offset) : '\0';
	}

	public char read() {
		return string.charAt(cursor++);
	}

	public void skip() {
		cursor++;
	}

	public void skipWhitespace() {
		while (canRead() && Character.isWhitespace(peek())) {
			skip();
		}
	}

	public boolean isWhitespace(char c) {
		return Character.isWhitespace(c);
	}

	/** Read an unquoted or quoted string token (Brigadier readString semantics). */
	public String readString() throws CommandSyntaxException {
		skipWhitespace();
		if (canRead() && (peek() == '"' || peek() == '\'')) {
			return readQuotedString(peek());
		}
		return readUnquotedString();
	}

	private String readQuotedString(char quote) throws CommandSyntaxException {
		skip(); // opening quote
		StringBuilder sb = new StringBuilder();
		boolean escaped = false;
		while (canRead()) {
			char c = read();
			if (escaped) {
				sb.append(c);
				escaped = false;
			} else if (c == SYNTAX_ESCAPE) {
				escaped = true;
			} else if (c == quote) {
				return sb.toString();
			} else {
				sb.append(c);
			}
		}
		throw new CommandSyntaxException("Expected trailing quote to end string");
	}

	/**
	 * [Agent Note 2026-08-28] Public in real Brigadier — word-style argument
	 * shims (ScoreHolder/Objective/Particle/...) consume tokens through it.
	 */
	public String readUnquotedString() {
		int start = cursor;
		while (canRead() && !isWhitespace(peek())) {
			skip();
		}
		return string.substring(start, cursor);
	}

	/** Consume the rest of the input (greedy string argument semantics). */
	public String readRemaining() {
		String rest = getRemaining();
		cursor = string.length();
		return rest;
	}

	public int readInt() throws CommandSyntaxException {
		skipWhitespace();
		int start = cursor;
		if (canRead() && (peek() == '-' || peek() == '+')) {
			skip();
		}
		while (canRead() && peek() >= '0' && peek() <= '9') {
			skip();
		}
		String s = string.substring(start, cursor);
		if (s.isEmpty() || s.equals("-") || s.equals("+")) {
			cursor = start;
			throw new CommandSyntaxException("Expected integer at position " + start);
		}
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			cursor = start;
			throw new CommandSyntaxException("Invalid integer '" + s + "' at position " + start);
		}
	}

	/** [Agent Note 2026-08-28] Real 64-bit long parse (was an int delegation — UCBPP audit G12). */
	public long readLong() throws CommandSyntaxException {
		skipWhitespace();
		int start = cursor;
		if (canRead() && (peek() == '-' || peek() == '+')) {
			skip();
		}
		while (canRead() && peek() >= '0' && peek() <= '9') {
			skip();
		}
		String s = string.substring(start, cursor);
		if (s.isEmpty() || s.equals("-") || s.equals("+")) {
			cursor = start;
			throw new CommandSyntaxException("Expected long at position " + start);
		}
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			cursor = start;
			throw new CommandSyntaxException("Invalid long '" + s + "' at position " + start);
		}
	}

	public float readFloat() throws CommandSyntaxException {
		skipWhitespace();
		int start = cursor;
		while (canRead() && (Character.isDigit(peek()) || peek() == '-' || peek() == '+' || peek() == '.'
				|| peek() == 'e' || peek() == 'E')) {
			skip();
		}
		String s = string.substring(start, cursor);
		if (s.isEmpty() || s.equals("-") || s.equals("+")) {
			cursor = start;
			throw new CommandSyntaxException("Expected float at position " + start);
		}
		try {
			return Float.parseFloat(s);
		} catch (NumberFormatException e) {
			cursor = start;
			throw new CommandSyntaxException("Invalid float '" + s + "' at position " + start);
		}
	}

	public double readDouble() throws CommandSyntaxException {
		skipWhitespace();
		int start = cursor;
		while (canRead() && (Character.isDigit(peek()) || peek() == '-' || peek() == '+' || peek() == '.'
				|| peek() == 'e' || peek() == 'E')) {
			skip();
		}
		String s = string.substring(start, cursor);
		if (s.isEmpty() || s.equals("-") || s.equals("+")) {
			cursor = start;
			throw new CommandSyntaxException("Expected double at position " + start);
		}
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			cursor = start;
			throw new CommandSyntaxException("Invalid double '" + s + "' at position " + start);
		}
	}

	public boolean readBoolean() throws CommandSyntaxException {
		skipWhitespace();
		int start = cursor;
		while (canRead() && !isWhitespace(peek())) {
			skip();
		}
		String s = string.substring(start, cursor);
		if (s.equalsIgnoreCase("true")) {
			return true;
		}
		if (s.equalsIgnoreCase("false")) {
			return false;
		}
		throw new CommandSyntaxException("Expected boolean (true|false) at position " + start);
	}

	public void expect(char c) throws CommandSyntaxException {
		if (!canRead() || peek() != c) {
			throw new CommandSyntaxException("Expected '" + c + "' at position " + cursor);
		}
		skip();
	}
}
