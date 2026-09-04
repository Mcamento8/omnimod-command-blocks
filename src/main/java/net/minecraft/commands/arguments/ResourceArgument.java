package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ResourceArgument shim (and
 * siblings ResourceOrTagArgument / ResourceKeyArgument share this token
 * semantics — each keeps its own file per the UCBPP one-shim-per-type rule).
 *
 * Parses a namespaced registry id ({@code minecraft:x}) and stores it. Full
 * registry-membership validation for dynamic mod registries is an honest
 * boundary (the 1.8 layer validates at use time).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-RES-001
 */
public class ResourceArgument implements ArgumentType<ResourceArgument.ResourceToken> {

	public static final class ResourceToken {
		public final String namespace;
		public final String path;

		public ResourceToken(String namespace, String path) {
			this.namespace = namespace;
			this.path = path;
		}

		public String getJoinedId() {
			return namespace + ":" + path;
		}
	}

	private ResourceArgument() {
	}

	public static ResourceArgument resource() {
		return new ResourceArgument();
	}

	@Override
	public ResourceToken parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected a resource location at position " + start);
		}
		String ns = "minecraft";
		String path = token;
		int colon = token.indexOf(':');
		if (colon >= 0) {
			ns = token.substring(0, colon);
			path = token.substring(colon + 1);
		}
		return new ResourceToken(ns, path);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "resource()";
	}
}
