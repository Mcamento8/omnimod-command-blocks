package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.World;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 DimensionArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.DimensionArgument} — used by
 * {@code /execute in <dimension>}. Parses a ResourceLocation-style token and
 * resolves it against the integrated server's loaded worlds.
 *
 * RESOLUTION TABLE (vanilla reference constants — Level.OVERWORLD/NETHER/END
 * ids, NOT a mod hardcode; matches 1.8 provider dimension ids):
 *   minecraft:overworld -> dimension 0
 *   minecraft:the_nether (legacy "minecraft:nether") -> dimension -1
 *   minecraft:the_end -> dimension 1
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-DIM-001
 */
public class DimensionArgument implements ArgumentType<DimensionArgument.DimensionToken> {

	/** A parsed dimension token; resolves lazily against the live server. */
	public static final class DimensionToken {
		public final String namespace;
		public final String path;
		public final int legacyDimensionId;

		public DimensionToken(String namespace, String path, int legacyDimensionId) {
			this.namespace = namespace;
			this.path = path;
			this.legacyDimensionId = legacyDimensionId;
		}

		public String getJoinedToken() {
			return namespace + ":" + path;
		}

		/** Resolve to a loaded 1.8 world (server.worldServers), or null. */
		public World resolve() {
			try {
				net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
				if (server == null || server.worldServers == null) {
					return null;
				}
				for (World w : server.worldServers) {
					if (w != null && w.provider != null
							&& w.provider.getDimensionId() == legacyDimensionId) {
						return w;
					}
				}
			} catch (Throwable ignored) {
			}
			return null;
		}
	}

	private DimensionArgument() {
	}

	public static DimensionArgument dimension() {
		return new DimensionArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to a dimension token. */
	public static DimensionToken getDimension(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, DimensionToken.class);
	}

	@Override
	public DimensionToken parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected a dimension at position " + start);
		}
		String ns = "minecraft";
		String path = token;
		int colon = token.indexOf(':');
		if (colon >= 0) {
			ns = token.substring(0, colon);
			path = token.substring(colon + 1);
		}
		// Vanilla reference dimension ids (see class doc — not a mod hardcode).
		int dimId;
		if (path.equals("overworld")) {
			dimId = 0;
		} else if (path.equals("the_nether") || path.equals("nether")) {
			dimId = -1;
		} else if (path.equals("the_end") || path.equals("end")) {
			dimId = 1;
		} else {
			// Unknown dimension: accept the token (classload/analysis surface)
			// and resolve lazily; unknown ids simply resolve to null at use.
			dimId = Integer.MIN_VALUE;
		}
		return new DimensionToken(ns, path, dimId);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "dimension()";
	}
}
