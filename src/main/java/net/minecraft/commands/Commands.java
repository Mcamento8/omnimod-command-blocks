package net.minecraft.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * [Agent Note 2026-08-02] GENERAL: Forge 1.20.1 Commands factory shim.
 *
 * Mirrors {@code net.minecraft.commands.Commands} from Forge 1.20.1. This is
 * the static entry point mods use to build command trees:
 *   Commands.literal("game")                  -> LiteralArgumentBuilder
 *   Commands.argument("player", EntityArgument.player()) -> RequiredArgumentBuilder
 *   Commands.literal("x").requires(s -> s.hasPermission(2))
 *                         .then(Commands.literal("start").executes(ctx -> 1))
 *
 * {@code Commands.literal} delegates to {@link LiteralArgumentBuilder#literal}.
 * {@code Commands.argument} delegates to {@link RequiredArgumentBuilder#argument}.
 * The {@code CommandSelection} enum mirrors Forge's environment selection
 * (ALL/DEDICATED/INTEGRATED) and is accepted but ignored by the bridge.
 *
 * GENERAL — serves every Forge 1.20.1 command mod. No mod id/name hardcode.
 *
 * Doc-ID: MC-CMD-001
 */
public final class Commands {
	private Commands() {
	}

	public static <S> LiteralArgumentBuilder<S> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	public static <S, T> RequiredArgumentBuilder<S, T> argument(String name, Object type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	/**
	 * Forge 1.20.1 command environment selection. Accepted by
	 * {@code RegisterCommandsEvent} but ignored by the OmniMod bridge (which
	 * routes all commands through the 1.8 ServerCommandManager).
	 */
	public enum CommandSelection {
		ALL,
		DEDICATED,
		INTEGRATED
	}
}
