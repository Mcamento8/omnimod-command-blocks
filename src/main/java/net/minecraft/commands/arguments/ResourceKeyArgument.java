package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ResourceKeyArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ResourceKeyArgument} — a
 * typed registry key token (e.g. dimension/level keys). Stores the token;
 * resolution to engine objects happens at use time.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-RESKEY-001
 */
public class ResourceKeyArgument implements ArgumentType<ResourceArgument.ResourceToken> {

	private ResourceKeyArgument() {
	}

	public static ResourceKeyArgument resourceKey() {
		return new ResourceKeyArgument();
	}

	@Override
	public ResourceArgument.ResourceToken parse(StringReader reader) throws CommandSyntaxException {
		return ResourceArgument.resource().parse(reader);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "resourceKey()";
	}
}
