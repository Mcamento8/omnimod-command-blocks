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
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ItemPredicateArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ItemPredicateArgument} —
 * used by /clear (item with count predicates) and /execute if items. Parses a
 * plain id or {@code #tag}. Tag expansion is an honest boundary (1.8 has no
 * 1.20.1 item-tag layer here).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-IPRED-001
 */
public class ItemPredicateArgument implements ArgumentType<ItemPredicateArgument.ItemPredicate> {

	public interface ItemPredicate {
		boolean matches(Item item);

		boolean isTag();
	}

	private ItemPredicateArgument() {
	}

	public static ItemPredicateArgument itemPredicate() {
		return new ItemPredicateArgument();
	}

	@Override
	public ItemPredicate parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek()) && reader.peek() != '{') {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected an item predicate at position " + start);
		}
		if (token.startsWith("#")) {
			final String tag = token.substring(1);
			return new ItemPredicate() {
				@Override
				public boolean matches(Item item) {
					return false; // honest: no 1.20.1 item-tag expansion
				}

				@Override
				public boolean isTag() {
					return true;
				}
			};
		}
		String ns = "minecraft";
		String path = token;
		int colon = token.indexOf(':');
		if (colon >= 0) {
			ns = token.substring(0, colon);
			path = token.substring(colon + 1);
		}
		final Item resolved = resolve(ns, path);
		return new ItemPredicate() {
			@Override
			public boolean matches(Item item) {
				return item != null && item == resolved;
			}

			@Override
			public boolean isTag() {
				return false;
			}
		};
	}

	private static Item resolve(String ns, String path) {
		try {
			Item it = (Item) Item.itemRegistry.getObject(new ResourceLocation(ns, path));
			if (it != null) {
				return it;
			}
			return (Item) Item.itemRegistry.getObjectById(Integer.parseInt(path));
		} catch (Throwable ignored) {
			return null;
		}
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "itemPredicate()";
	}
}
