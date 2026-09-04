package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ResourceOrTagArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ResourceOrTagArgument} —
 * accepts either an id ({@code minecraft:x}) or a tag ({@code #tag}); the tag
 * flag is carried honestly for the caller to expand or reject.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-RESORTAG-001
 */
public class ResourceOrTagArgument implements ArgumentType<ResourceOrTagArgument.Result> {

	public static final class Result {
		public final String namespace;
		public final String path;
		public final boolean tag;

		public Result(String namespace, String path, boolean tag) {
			this.namespace = namespace;
			this.path = path;
			this.tag = tag;
		}

		public String getJoinedId() {
			return namespace + ":" + path;
		}

		public boolean isTag() {
			return tag;
		}
	}

	private ResourceOrTagArgument() {
	}

	public static ResourceOrTagArgument resourceOrTag() {
		return new ResourceOrTagArgument();
	}

	@Override
	public Result parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected a resource or tag at position " + start);
		}
		boolean tag = token.startsWith("#");
		if (tag) {
			token = token.substring(1);
		}
		String ns = "minecraft";
		String path = token;
		int colon = token.indexOf(':');
		if (colon >= 0) {
			ns = token.substring(0, colon);
			path = token.substring(colon + 1);
		}
		return new Result(ns, path, tag);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "resourceOrTag()";
	}
}
