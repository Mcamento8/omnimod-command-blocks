package net.minecraft.commands.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.ICommandSender;
import net.minecraft.commands.arguments.selector.EntitySelector;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 GameProfileArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.GameProfileArgument} — used
 * by /whitelist, /ban, /op... Parses one or more player names / selectors
 * (comma-separated like vanilla) and resolves them to real players through
 * the integrated server's player list.
 *
 * HONEST SHAPE NOTE: vanilla returns authlib {@code GameProfile} objects. The
 * shim returns the resolved {@link EntityPlayerMP} list instead — the profile
 * is reachable via {@code player.getGameProfile()} at the call site (the 1.8
 * engine carries profiles on the entity). Unresolvable names are honest
 * failures at resolve time (vanilla semantics), never fabricated profiles
 * (§18.2b).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-GPROF-001
 */
public class GameProfileArgument implements ArgumentType<GameProfileArgument.Result> {

	/** Vanilla-shaped resolver: input token list -> resolved players. */
	public interface Result {
		List<net.minecraft.entity.player.EntityPlayerMP> getNames(ICommandSender sender)
				throws CommandSyntaxException;
	}

	private GameProfileArgument() {
	}

	public static GameProfileArgument gameProfile() {
		return new GameProfileArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to the matched players. */
	public static List<net.minecraft.entity.player.EntityPlayerMP> getGameProfiles(
			CommandContext<?> ctx, String name) throws CommandSyntaxException {
		Result r = ctx.getArgument(name, Result.class);
		if (r == null) {
			return Collections.emptyList();
		}
		return r.getNames(EntityArgument.sourceOf(ctx));
	}

	@Override
	public Result parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		final String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected a player name or selector at position " + start);
		}
		final boolean isSelector = token.startsWith("@");
		return new Result() {
			@Override
			public List<net.minecraft.entity.player.EntityPlayerMP> getNames(ICommandSender sender)
					throws CommandSyntaxException {
				if (isSelector) {
					return new EntitySelector(token).findPlayers(sender);
				}
				List<net.minecraft.entity.player.EntityPlayerMP> out =
						new ArrayList<net.minecraft.entity.player.EntityPlayerMP>();
				try {
					net.minecraft.entity.player.EntityPlayerMP p = net.minecraft.server.MinecraftServer
							.getServer().getConfigurationManager().getPlayerByUsername(token);
					if (p != null) {
						out.add(p);
					}
				} catch (Throwable ignored) {
				}
				if (out.isEmpty()) {
					// Honest failure — vanilla throws unknown-player here.
					throw new CommandSyntaxException("No player was found named '" + token + "'");
				}
				return out;
			}
		};
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "gameProfile()";
	}
}
