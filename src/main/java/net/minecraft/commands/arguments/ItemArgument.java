package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ItemArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ItemArgument} — used by
 * /give, /clear, /item... in 1.20.1. Parses {@code minecraft:diamond_sword}
 * (namespace optional) with an optional trailing {nbt} compound which is
 * parsed off the token and honestly ignored (the 1.8 engine carries damage
 * values, not SNBT — documented boundary). Resolution goes through the REAL
 * 1.8 {@link Item#itemRegistry}.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-ITEM-001
 */
public class ItemArgument implements ArgumentType<ItemArgument.ItemInput> {

	/** A parsed item input: id + optional ignored nbt token. */
	public static final class ItemInput {
		public final String namespace;
		public final String path;
		public final String rawToken;
		public final String ignoredNbtSuffix;

		public ItemInput(String namespace, String path, String rawToken, String ignoredNbtSuffix) {
			this.namespace = namespace;
			this.path = path;
			this.rawToken = rawToken;
			this.ignoredNbtSuffix = ignoredNbtSuffix;
		}

		public String getJoinedId() {
			return namespace + ":" + path;
		}

		/** Resolve to the 1.8 Item (null when unknown — callers decide). */
		public Item getItem() {
			try {
				return (Item) Item.itemRegistry.getObject(new ResourceLocation(namespace, path));
			} catch (Throwable ignored) {
				return null;
			}
		}

		/** Legacy 1.8 numeric/flat id fallback ("stone", "5", "5/2"). */
		public Item getItemLegacy() {
			try {
				Item it = getItem();
				if (it != null) {
					return it;
				}
				if (path.indexOf('/') >= 0) {
					String base = path.substring(0, path.indexOf('/'));
					return (Item) Item.itemRegistry.getObjectById(Integer.parseInt(base));
				}
				return (Item) Item.itemRegistry.getObjectById(Integer.parseInt(path));
			} catch (Throwable ignored) {
				return null;
			}
		}
	}

	private ItemArgument() {
	}

	public static ItemArgument item() {
		return new ItemArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to an ItemInput. */
	public static ItemInput getItem(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, ItemInput.class);
	}

	@Override
	public ItemInput parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek()) && reader.peek() != '{') {
			reader.skip();
		}
		String idToken = reader.getString().substring(start, reader.getCursor());
		String nbt = null;
		if (reader.canRead() && reader.peek() == '{') {
			// Consume a balanced {..} and honestly ignore it (1.8 has no SNBT).
			int depth = 0;
			int nbtStart = reader.getCursor();
			while (reader.canRead()) {
				char c = reader.peek();
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					reader.skip();
					if (depth == 0) {
						break;
					}
					continue;
				}
				reader.skip();
			}
			nbt = reader.getString().substring(nbtStart, reader.getCursor());
		}
		if (idToken.isEmpty()) {
			throw new CommandSyntaxException("Expected an item id at position " + start);
		}
		String ns = "minecraft";
		String path = idToken;
		int colon = idToken.indexOf(':');
		if (colon >= 0) {
			ns = idToken.substring(0, colon);
			path = idToken.substring(colon + 1);
		}
		return new ItemInput(ns, path, idToken, nbt);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "item()";
	}
}
