package com.mojang.brigadier.exceptions;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier parse/execution exception shim.
 *
 * Mirrors {@code com.mojang.brigadier.exceptions.CommandSyntaxException} from
 * Forge 1.20.1. Mods throw this from command execution lambdas (e.g. invalid
 * arguments, permission failures). It is a checked-style exception declared on
 * {@link com.mojang.brigadier.Command#run}.
 *
 * OmniMod catches this in the 1.8 ICommand bridge and converts the message into
 * a 1.8 chat message so the failure is visible to the player instead of being
 * swallowed. GENERAL — standard Brigadier exception, no mod hardcode.
 *
 * Doc-ID: BRIG-EXC-001
 */
public class CommandSyntaxException extends Exception {
	private static final long serialVersionUID = 1L;

	public CommandSyntaxException(BuiltInExceptionProvider type, String message) {
		super(message != null ? message : "command syntax error");
	}

	public CommandSyntaxException(BuiltInExceptionProvider type, String message, int cursor) {
		super(message != null ? message : "command syntax error");
	}

	public CommandSyntaxException(String message) {
		super(message != null ? message : "command syntax error");
	}

	public CommandSyntaxException() {
		super("command syntax error");
	}

	public int getCursor() {
		return 0;
	}
}
